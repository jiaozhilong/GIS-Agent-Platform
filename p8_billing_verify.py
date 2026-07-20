#!/usr/bin/env python3
"""
P8-1 计费纵深 集成验证
- 注册用户 → 提权超管 → 设置组织月度配额
- 经 psql 种入带 token 的 pipeline_runs（超过告警阈值）
- 手动巡检 POST /api/billing/quota/check 触发超限告警
- 断言：审计日志 BILLING_QUOTA_WARN + 站内通知 QUOTA 已写入
- 断言：账单生成 GET/POST /api/billing/invoices 聚合正确
- 断言：普通用户可见本组织配额、但无权设置；匿名被拒
纯标准库实现，无外部依赖。
"""
import json
import os
import subprocess
import time
import urllib.request
import urllib.error

BASE = "http://localhost:8088"
DB = dict(host="localhost", port=5432, user="gisagent", password="gisagent", dbname="gisagent")

passed = 0
failed = 0


def check(name, cond, extra=""):
    global passed, failed
    if cond:
        passed += 1
        print(f"[PASS] {name} {extra}")
    else:
        failed += 1
        print(f"[FAIL] {name} {extra}")


def req(method, path, token=None, body=None):
    url = BASE + path
    data = json.dumps(body).encode() if body is not None else None
    r = urllib.request.Request(url, data=data, method=method)
    r.add_header("Content-Type", "application/json")
    if token:
        r.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(r, timeout=20) as resp:
            return resp.status, json.loads(resp.read().decode() or "{}")
    except urllib.error.HTTPError as e:
        try:
            return e.code, json.loads(e.read().decode() or "{}")
        except Exception:
            return e.code, {}


def psql(sql):
    env = dict(os.environ, PGPASSWORD=DB["password"])
    cmd = ["psql", "-h", DB["host"], "-p", str(DB["port"]),
           "-U", DB["user"], "-d", DB["dbname"], "-tA", "-c", sql]
    return subprocess.run(cmd, env=env, capture_output=True, text=True).stdout.strip()


def register(username, password="Test@1234"):
    st, body = req("POST", "/api/auth/register",
                   body={"username": username, "password": password})
    if st == 200 and body.get("token"):
        return body["token"], body.get("userId")
    st, body = req("POST", "/api/auth/login",
                   body={"username": username, "password": password})
    return (body.get("token"), body.get("userId")) if st == 200 and body.get("token") else (None, None)


def create_project(token, name):
    import tempfile
    fd, path = tempfile.mkstemp(suffix=".txt")
    with os.fdopen(fd, "w") as f:
        f.write("测试需求文档内容")
    boundary = "----p8boundary"
    with open(path, "rb") as f:
        file_data = f.read()
    os.unlink(path)
    CRLF = b"\r\n"
    body = b""
    for field, val in (("name", name), ("templateId", "full_solution")):
        body += b"--" + boundary.encode() + CRLF
        body += ('Content-Disposition: form-data; name="%s"' % field).encode() + CRLF + CRLF
        body += val.encode() + CRLF
    body += b"--" + boundary.encode() + CRLF
    body += b'Content-Disposition: form-data; name="file"; filename="req.txt"' + CRLF
    body += b"Content-Type: text/plain" + CRLF + CRLF
    body += file_data + CRLF
    body += b"--" + boundary.encode() + b"--" + CRLF
    url = BASE + "/api/projects"
    r = urllib.request.Request(url, data=body, method="POST")
    r.add_header("Content-Type", "multipart/form-data; boundary=" + boundary)
    r.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(r, timeout=20) as resp:
            return resp.status, json.loads(resp.read().decode() or "{}")
    except urllib.error.HTTPError as e:
        try:
            return e.code, json.loads(e.read().decode() or "{}")
        except Exception:
            return e.code, {}


def seed_run(pid, inp, out, tot, ts):
    psql(
        f"INSERT INTO pipeline_runs (project_id, status, input_tokens, "
        f"output_tokens, total_tokens, finished_at, created_at) VALUES "
        f"({pid}, 'SUCCESS', {inp}, {out}, {tot}, '{ts}', '{ts}');"
    )


def teardown(admin_uid, normal_uid, pid, month):
    psql(f"DELETE FROM pipeline_runs WHERE project_id = {pid};")
    psql(f"DELETE FROM projects WHERE name IN ('p8_proj','p8_proj_n');")
    psql(f"DELETE FROM users WHERE username IN ('p8_admin','p8_normal');")
    psql(f"DELETE FROM usage_quotas WHERE organization_id = 1;")
    psql(f"DELETE FROM invoices WHERE period_month = '{month}';")
    # 还原提权账号角色
    psql(f"UPDATE users SET role='USER' WHERE id = {admin_uid};")


def main():
    month = time.strftime("%Y-%m", time.gmtime())
    now_iso = time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())

    # 清理可能残留的调试数据
    psql("DELETE FROM pipeline_runs WHERE project_id IN (SELECT id FROM projects WHERE name LIKE 'p8%');")
    psql("DELETE FROM projects WHERE name LIKE 'p8%';")
    psql("DELETE FROM users WHERE username LIKE 'p8%';")
    psql("DELETE FROM usage_quotas WHERE organization_id = 1;")
    psql(f"DELETE FROM invoices WHERE period_month = '{month}';")

    # ---- 超管账号 ----
    admin_token, admin_uid = register("p8_admin")
    check("注册/登录 p8_admin 200", bool(admin_token), f"uid={admin_uid}")
    # 提权为超管以调用配额/账单管理接口
    psql(f"UPDATE users SET role='SUPER_ADMIN', organization_id=1 WHERE id={admin_uid};")

    # ---- 设置组织配额（org=1, 上限 1000, 阈值 50%）----
    st, q = req("PUT", "/api/billing/quota", admin_token,
                {"organizationId": 1, "tokenLimit": 1000, "warnThreshold": 50})
    check("PUT /api/billing/quota 200", st == 200, f"status={st} body={q}")
    check("配额 tokenLimit=1000", q.get("tokenLimit") == 1000, f"tokenLimit={q.get('tokenLimit')}")
    check("配额 warnThreshold=50", q.get("warnThreshold") == 50, f"warnThreshold={q.get('warnThreshold')}")

    # ---- 查询配额 ----
    st, qg = req("GET", "/api/billing/quota?orgId=1", admin_token)
    check("GET /api/billing/quota 200", st == 200, f"status={st}")
    check("查询到配额 tokenLimit=1000", qg and qg.get("tokenLimit") == 1000, f"quota={qg}")

    # ---- 普通用户视角：可见本组织配额，但无权设置 ----
    normal_token, normal_uid = register("p8_normal")
    psql(f"UPDATE users SET organization_id=1 WHERE id={normal_uid};")
    st, qn = req("GET", "/api/billing/quota", normal_token)
    check("普通用户 GET 本组织配额 200", st == 200, f"status={st}")
    st, _ = req("PUT", "/api/billing/quota", normal_token,
                {"organizationId": 1, "tokenLimit": 100, "warnThreshold": 50})
    check("普通用户 PUT 配额 被拒(403)", st == 403, f"status={st}")

    # ---- 种入超过阈值的用量（input=600000, output=200000, total=800000 → 80000% >= 50%）----
    _, pj = create_project(admin_token, "p8_proj")
    pid = pj.get("id")
    check("p8_admin 建项目 200", pid is not None, f"pid={pid}")
    psql(f"UPDATE projects SET organization_id=1 WHERE id={pid};")
    seed_run(pid, 600000, 200000, 800000, now_iso)

    # ---- 手动巡检触发超限告警 ----
    st, ck = req("POST", "/api/billing/quota/check?orgId=1", admin_token)
    check("POST /api/billing/quota/check 200", st == 200, f"status={st} body={ck}")

    # ---- 断言审计日志 ----
    audit_cnt = psql(
        "SELECT count(*) FROM audit_logs WHERE action='BILLING_QUOTA_WARN' AND target_id=1;")
    check("审计日志写入 BILLING_QUOTA_WARN", audit_cnt == "1", f"count={audit_cnt}")

    # ---- 断言站内通知（发给超管）----
    notif_cnt = psql(
        f"SELECT count(*) FROM notifications WHERE type='QUOTA' AND user_id={admin_uid};")
    check("站内通知 QUOTA 已发给超管", notif_cnt.isdigit() and int(notif_cnt) >= 1, f"count={notif_cnt}")

    # ---- 账单生成 ----
    st, gen = req("POST", "/api/billing/invoices/generate", admin_token)
    check("POST /api/billing/invoices/generate 200", st == 200, f"status={st} body={gen}")
    check("生成覆盖 >=1 组织", gen.get("orgCount", 0) >= 1, f"orgCount={gen.get('orgCount')}")
    check("生成合计费用 > 0", (gen.get("totalCost") or 0) > 0, f"totalCost={gen.get('totalCost')}")

    # ---- 账单查询并断言 org=1 聚合正确 ----
    st, invs = req("GET", "/api/billing/invoices", admin_token)
    check("GET /api/billing/invoices 200", st == 200, f"status={st}")
    invs = invs if isinstance(invs, list) else []
    org1 = next((i for i in invs if i.get("organizationId") == 1), None)
    check("账单含 org=1", org1 is not None, f"invoices={invs}")
    if org1:
        check("org=1 账期=本月", org1.get("periodMonth") == month, f"period={org1.get('periodMonth')}")
        check("org=1 totalTokens=800000", org1.get("totalTokens") == 800000,
              f"total={org1.get('totalTokens')}")
        # 费用 = 600000/1000*0.001 + 200000/1000*0.002 = 0.6 + 0.4 = 1.0
        check("org=1 estimatedCost≈1.0", abs((org1.get("estimatedCost") or 0) - 1.0) < 0.01,
              f"cost={org1.get('estimatedCost')}")
        check("org=1 状态 DRAFT", org1.get("status") == "DRAFT", f"status={org1.get('status')}")

    # ---- 匿名访问被拒 ----
    st, _ = req("GET", "/api/billing/quota")
    check("未登录 拒绝(401/403)", st in (401, 403), f"status={st}")

    teardown(admin_uid, normal_uid, pid, month)
    print(f"\n==== P8-1 计费纵深 结果: {passed} PASS / {failed} FAIL ====")
    return failed == 0


if __name__ == "__main__":
    ok = main()
    raise SystemExit(0 if ok else 1)
