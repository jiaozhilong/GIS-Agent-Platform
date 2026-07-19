#!/usr/bin/env python3
"""P7-1 多租户隔离 端到端验证（标准库 + psql，不依赖 requests）。"""
import bcrypt
import json
import subprocess
import urllib.request
import urllib.error
import uuid

BASE = "http://localhost:8088/api"
SA = "test_p7_sa"
UA = "test_p7_ua"
UB = "test_p7_ub"
PW = "P7test@123"

PASS, FAIL = [], []


def psql(sql):
    subprocess.run(["psql", "-h", "localhost", "-U", "gisagent", "-d", "gisagent", "-c", sql],
                   env={**__import__("os").environ, "PGPASSWORD": "gisagent"},
                   capture_output=True, text=True, check=False)


def http(method, path, token=None, body=None, raw=None):
    url = BASE + path
    data = raw if raw is not None else (json.dumps(body).encode() if body is not None else None)
    req = urllib.request.Request(url, data=data, method=method)
    if data is not None and raw is None:
        req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req, timeout=20) as r:
            return r.status, json.loads(r.read().decode() or "{}")
    except urllib.error.HTTPError as e:
        try:
            return e.code, json.loads(e.read().decode() or "{}")
        except Exception:
            return e.code, {}


def multipart_post(path, token, fields, file_tuple):
    boundary = "----p7b" + uuid.uuid4().hex
    parts = []
    for name, value in fields.items():
        parts.append(f'--{boundary}\r\nContent-Disposition: form-data; name="{name}"\r\n\r\n{value}\r\n'.encode())
    fname, content, ctype = file_tuple
    parts.append((f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="{fname}"\r\n'
                  f'Content-Type: {ctype}\r\n\r\n').encode() + content + b"\r\n")
    body = b"".join(parts) + f"--{boundary}--\r\n".encode()
    req = urllib.request.Request(BASE + path, data=body, method="POST")
    req.add_header("Content-Type", f"multipart/form-data; boundary={boundary}")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req, timeout=20) as r:
            return r.status, json.loads(r.read().decode() or "{}")
    except urllib.error.HTTPError as e:
        try:
            return e.code, json.loads(e.read().decode() or "{}")
        except Exception:
            return e.code, {}


def check(name, cond, detail=""):
    (PASS if cond else FAIL).append(name)
    print(f"[{'PASS' if cond else 'FAIL'}] {name}" + (f" — {detail}" if detail else ""))


def setup():
    # 清理上一轮可能残留的测试数据
    psql(f"DELETE FROM users WHERE username IN ('{SA}','{UA}','{UB}');")
    psql("DELETE FROM organizations WHERE slug IN ('p7-org-a','p7-org-b') OR name LIKE 'P7组织%';")
    # 创建 SUPER_ADMIN 测试账号（归属默认组织 id=1）
    h = bcrypt.hashpw(PW.encode(), bcrypt.gensalt()).decode()
    psql(f"INSERT INTO users(username,password,role,enabled,organization_id,created_at,updated_at) "
         f"VALUES ('{SA}','{h}','SUPER_ADMIN',true,1,now(),now());")
    print("== setup done ==")


def teardown(org_a, org_b, proj_ids, team_ids):
    for pid in proj_ids:
        psql(f"DELETE FROM projects WHERE id={pid};")
    for tid in team_ids:
        psql(f"DELETE FROM teams WHERE id={tid};")
    # 按 id 删除；兜底按 slug 删除，防止捕获失败导致残留
    if org_a:
        psql(f"DELETE FROM organizations WHERE id={org_a};")
    if org_b:
        psql(f"DELETE FROM organizations WHERE id={org_b};")
    psql("DELETE FROM organizations WHERE slug IN ('p7-org-a','p7-org-b') OR name LIKE 'P7组织%';")
    psql(f"DELETE FROM users WHERE username IN ('{SA}','{UA}','{UB}');")
    print("== teardown done ==")


def main():
    setup()
    org_a = org_b = None
    proj_ids, team_ids = [], []
    try:
        # 1. SUPER_ADMIN 登录
        s, d = http("POST", "/auth/login", body={"username": SA, "password": PW})
        check("SUPER_ADMIN 登录 200", s == 200, f"status={s}")
        sa_token = d.get("token")

        # 2. 列出组织
        s, d = http("GET", "/admin/organizations", token=sa_token)
        check("列出组织 200", s == 200 and "organizations" in d, f"status={s} total={d.get('total')}")

        # 3. 创建两个组织
        s, da = http("POST", "/admin/organizations", token=sa_token, body={"name": "P7组织A"})
        org_a = da.get("id")
        s, db = http("POST", "/admin/organizations", token=sa_token, body={"name": "P7组织B"})
        org_b = db.get("id")
        check("创建两个组织 200", s == 200 and org_a and org_b, f"orgA={org_a} orgB={org_b}")

        # 4. 把两个测试用户放进不同组织
        h = bcrypt.hashpw(PW.encode(), bcrypt.gensalt()).decode()
        psql(f"INSERT INTO users(username,password,role,enabled,organization_id,created_at,updated_at) "
             f"VALUES ('{UA}','{h}','USER',true,{org_a},now(),now());")
        psql(f"INSERT INTO users(username,password,role,enabled,organization_id,created_at,updated_at) "
             f"VALUES ('{UB}','{h}','USER',true,{org_b},now(),now());")

        # 5. 两个用户登录，token 带各自 orgId
        s, dua = http("POST", "/auth/login", body={"username": UA, "password": PW})
        ua_token = dua.get("token")
        check("用户A 登录且 orgId=orgA", s == 200 and dua.get("orgId") == org_a, f"orgId={dua.get('orgId')} expect={org_a}")
        s, dub = http("POST", "/auth/login", body={"username": UB, "password": PW})
        ub_token = dub.get("token")
        check("用户B 登录且 orgId=orgB", s == 200 and dub.get("orgId") == org_b, f"orgId={dub.get('orgId')} expect={org_b}")

        # 6. 用户A 在 orgA 建项目
        s, pd = multipart_post("/projects", ua_token,
                               {"name": "A的项目", "templateId": "tpl-test"},
                               ("req.txt", "需求内容".encode("utf-8"), "text/plain"))
        check("用户A 建项目 200", s == 200 and pd.get("id"), f"status={s} id={pd.get('id')}")
        proj_a = pd.get("id")
        if proj_a:
            proj_ids.append(proj_a)

        # 7. 用户A 在 orgA 建团队
        s, td = http("POST", "/teams", token=ua_token, body={"name": "A的团队"})
        team_a = td.get("id")
        if team_a:
            team_ids.append(team_a)
        check("用户A 建团队 200", s in (200, 201) and team_a, f"status={s} id={team_a}")

        # 8. 用户B（orgB）列出项目 -> 应看不到 orgA 的项目（隔离）
        s, dl = http("GET", "/projects", token=ub_token)
        visible = [p for p in dl if isinstance(p, dict)]
        check("用户B 跨组织看不到 A 的项目", s == 200 and all(p.get("id") != proj_a for p in visible),
              f"status={s} 可见数={len(visible)}")

        # 9. 用户B 直接访问 A 的项目 -> 403
        s, _ = http("GET", f"/projects/{proj_a}", token=ub_token)
        check("用户B 访问 A 项目被拒 403", s == 403, f"status={s}")

        # 10. 用户A 自己能看到
        s, dla = http("GET", "/projects", token=ua_token)
        own = [p for p in dla if isinstance(p, dict)]
        check("用户A 能看到自己的项目", s == 200 and any(p.get("id") == proj_a for p in own), f"可见数={len(own)}")

        # 11. 用户B 访问 A 的团队 -> 403
        s, _ = http("GET", f"/teams/{team_a}", token=ub_token)
        check("用户B 访问 A 团队被拒 403", s == 403, f"status={s}")

        # 12. SUPER_ADMIN 把用户B 改到 orgA：同组织内应可协作
        s, _ = http("POST", f"/admin/users/{dub.get('userId')}/organization",
                    token=sa_token, body={"organizationId": org_a})
        check("SUPER_ADMIN 改用户B 组织 200", s == 200, f"status={s}")
        s, dub2 = http("POST", "/auth/login", body={"username": UB, "password": PW})
        ub_token2 = dub2.get("token")
        check("用户B 重新登录 orgId 变 orgA", dub2.get("orgId") == org_a, f"orgId={dub2.get('orgId')}")
        # 13. 同组织后 userA 可邀请 userB 加入 orgA 的团队（跨组织时会被拒，见步骤11 的 403 访问）
        s, _ = http("POST", f"/teams/{team_a}/members", token=ua_token,
                    body={"username": UB, "role": "MEMBER"})
        check("同组织内可邀请用户B 入队 200/201", s in (200, 201), f"status={s}")
        # 14. userB 切换组织后可见 A 的团队（组织门控随归属生效）
        s, dlb = http("GET", "/teams", token=ub_token2)
        teams = [t for t in dlb if isinstance(t, dict)]
        check("用户B 切换组织后可见 A 的团队", s == 200 and any(t.get("id") == team_a for t in teams),
              f"可见数={len(teams)}")

    finally:
        teardown(org_a, org_b, proj_ids, team_ids)

    print(f"\n==== P7-1 结果: {len(PASS)} PASS / {len(FAIL)} FAIL ====")
    if FAIL:
        print("FAILED:", FAIL)
        raise SystemExit(1)


if __name__ == "__main__":
    main()
