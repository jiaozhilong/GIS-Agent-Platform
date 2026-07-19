#!/usr/bin/env python3
"""
P7-3 用量计费 集成验证
- 注册用户 → 建项目 → 经 psql 种入带 token 的 pipeline_runs
- 断言 GET /api/usage/summary 的聚合（totals / byUser / byProject / byDay）
- 普通用户仅见自己；超管 all=true 见全平台
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


def create_project(token, name):
    """建项目：multipart/form-data（name + templateId + file 必填）"""
    import tempfile
    fd, path = tempfile.mkstemp(suffix=".txt")
    with os.fdopen(fd, "w") as f:
        f.write("测试需求文档内容")
    boundary = "----p73boundary"
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
    # 已存在则登录
    st, body = req("POST", "/api/auth/login",
                   body={"username": username, "password": password})
    return (body.get("token"), body.get("userId")) if st == 200 and body.get("token") else (None, None)


def setup():
    u1, uid1 = register("p73_user1")
    u2, uid2 = register("p73_user2")
    check("注册/登录 u1 200", bool(u1), f"uid={uid1}")
    check("注册/登录 u2 200", bool(u2), f"uid={uid2}")
    # 各自建一个项目（multipart）
    _, p1 = create_project(u1, "p73-proj1")
    _, p2 = create_project(u2, "p73-proj2")
    pid1 = p1.get("id")
    pid2 = p2.get("id")
    check("u1 建项目 200", pid1 is not None, f"pid={pid1}")
    check("u2 建项目 200", pid2 is not None, f"pid={pid2}")
    return dict(u1=u1, uid1=uid1, u2=u2, uid2=uid2, pid1=pid1, pid2=pid2)


def seed_runs(pid, rows):
    """rows: list of (input, output, total, finished_at_iso)"""
    for i, o, t, ts in rows:
        psql(
            f"INSERT INTO pipeline_runs (project_id, status, input_tokens, "
            f"output_tokens, total_tokens, finished_at, created_at) VALUES "
            f"({pid}, 'SUCCESS', {i}, {o}, {t}, '{ts}', '{ts}');"
        )


def teardown(pid1, pid2):
    psql(f"DELETE FROM pipeline_runs WHERE project_id IN ({pid1}, {pid2});")
    psql("DELETE FROM projects WHERE name IN ('p73-proj1','p73-proj2');")
    psql("DELETE FROM users WHERE username IN ('p73_user1','p73_user2');")
    # 清理调试残留（手动 curl 建的项目/run），保证 all 视图可确定性断言
    psql("DELETE FROM pipeline_runs WHERE project_id IN (SELECT id FROM projects WHERE name LIKE 'p73_dbg%');")
    psql("DELETE FROM projects WHERE name LIKE 'p73_dbg%';")
    psql("DELETE FROM users WHERE username='p73_probe';")


def main():
    # 清理可能残留的调试数据，保证 all 视图断言可确定
    psql("DELETE FROM pipeline_runs WHERE project_id IN (SELECT id FROM projects WHERE name LIKE 'p73%');")
    psql("DELETE FROM projects WHERE name LIKE 'p73%';")
    psql("DELETE FROM users WHERE username LIKE 'p73%';")
    s = setup()
    today = time.strftime("%Y-%m-%d", time.gmtime())
    yesterday = time.strftime("%Y-%m-%d", time.gmtime(time.time() - 86400))
    now_iso = time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())
    yest_iso = time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime(time.time() - 86400))

    # u1: 今天 2 次运行 (100/50/150, 200/100/300) ; 昨天 1 次 (50/25/75)
    seed_runs(s["pid1"], [(100, 50, 150, now_iso),
                           (200, 100, 300, now_iso),
                           (50, 25, 75, yest_iso)])
    # u2: 今天 1 次 (400/200/600)
    seed_runs(s["pid2"], [(400, 200, 600, now_iso)])

    # ---- 普通用户 u1 仅见自己 ----
    st, sum1 = req("GET", "/api/usage/summary", s["u1"])
    check("u1 summary 200", st == 200, f"status={st}")
    tot = sum1.get("totals", {})
    check("u1 仅统计自己项目(tot=525)", tot.get("totalTokens") == 525,
          f"total={tot.get('totalTokens')}")
    check("u1 runs=3", tot.get("runs") == 3, f"runs={tot.get('runs')}")
    check("u1 inputTokens=350", tot.get("inputTokens") == 350,
          f"in={tot.get('inputTokens')}")
    check("u1 scope=self", sum1.get("scope") == "self",
          f"scope={sum1.get('scope')}")
    # byUser 应只含 u1
    byuser = sum1.get("byUser", [])
    check("u1 byUser 仅自己", len(byuser) == 1 and byuser[0]["userId"] == s["uid1"],
          f"byUser={byuser}")
    # byDay 今天聚合 (150+300=450)
    byday = {d["date"]: d["totalTokens"] for d in sum1.get("byDay", [])}
    check("u1 今天 token=450", byday.get(today) == 450, f"byDay={byday}")
    check("u1 昨天 token=75", byday.get(yesterday) == 75, f"byDay={byday}")

    # ---- 时间窗过滤：只看今天 ----
    st, sum_t = req("GET", f"/api/usage/summary?from={today}&to={today}", s["u1"])
    tt = sum_t.get("totals", {})
    check("u1 时间窗(今天) total=450", tt.get("totalTokens") == 450,
          f"total={tt.get('totalTokens')}")

    # ---- 超管 all=true 见全平台 ----
    # 取一个已有超管账号（库内首账号），若当前无超管则用 u1 提权不便，改用直接查库确认角色
    # 这里用 psql 把 u1 临时提为超管验证 all 视图，验证后还原
    psql(f"UPDATE users SET role='SUPER_ADMIN' WHERE id={s['uid1']};")
    st, sum_all = req("GET", "/api/usage/summary?all=true", s["u1"])
    check("超管 all summary 200", st == 200, f"status={st}")
    tot_all = sum_all.get("totals", {})
    check("超管 all scope=all", sum_all.get("scope") == "all",
          f"scope={sum_all.get('scope')}")
    byuser_all = sum_all.get("byUser", [])
    ids_all = [b["userId"] for b in byuser_all]
    check("超管 all byUser 含 u1 与 u2",
          s["uid1"] in ids_all and s["uid2"] in ids_all,
          f"ids={ids_all}")
    # 全平台总量 >= u1(525)+u2(600)；存量 run token 为 NULL 不计入，仅校验两测试用户合计
    u1_tok = next((b["totalTokens"] for b in byuser_all if b["userId"] == s["uid1"]), None)
    u2_tok = next((b["totalTokens"] for b in byuser_all if b["userId"] == s["uid2"]), None)
    check("超管 all 含 u1=525 与 u2=600", u1_tok == 525 and u2_tok == 600,
          f"u1={u1_tok} u2={u2_tok}")
    check("超管 all 总量 >= 1125", tot_all.get("totalTokens", 0) >= 1125,
          f"total={tot_all.get('totalTokens')}")
    psql(f"UPDATE users SET role='USER' WHERE id={s['uid1']};")

    # ---- 未登录 401/403（匿名访问受保护端点，Spring 返回 403）----
    st, _ = req("GET", "/api/usage/summary")
    check("未登录 拒绝(401/403)", st in (401, 403), f"status={st}")

    teardown(s["pid1"], s["pid2"])
    print(f"\n==== P7-3 结果: {passed} PASS / {failed} FAIL ====")
    return failed == 0


if __name__ == "__main__":
    ok = main()
    raise SystemExit(0 if ok else 1)
