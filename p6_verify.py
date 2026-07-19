#!/usr/bin/env python3
"""P6 用户管理与权限管理 端到端验证（不依赖 requests，仅标准库 + psql）。"""
import bcrypt
import json
import subprocess
import urllib.request
import urllib.error

BASE = "http://localhost:8088/api"
SA_USER = "test_p6_sa"
NM_USER = "test_p6_user"
PW = "P6test@123"

PASS = []
FAIL = []


def psql(sql):
    subprocess.run(
        ["psql", "-h", "localhost", "-U", "gisagent", "-d", "gisagent", "-c", sql],
        env={"PGPASSWORD": "gisagent", **__import__("os").environ},
        capture_output=True, text=True, check=False,
    )


def http(method, path, token=None, body=None):
    url = BASE + path
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(url, data=data, method=method)
    if data is not None:
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


def check(name, cond, detail=""):
    (PASS if cond else FAIL).append(name)
    print(f"[{'PASS' if cond else 'FAIL'}] {name}" + (f" — {detail}" if detail else ""))


def setup():
    h = bcrypt.hashpw(PW.encode(), bcrypt.gensalt()).decode()
    psql(f"DELETE FROM users WHERE username IN ('{SA_USER}','{NM_USER}');")
    psql(f"INSERT INTO users(username,password,role,enabled,created_at,updated_at) "
         f"VALUES ('{SA_USER}','{h}','SUPER_ADMIN',true,now(),now());")
    psql(f"INSERT INTO users(username,password,role,enabled,created_at,updated_at) "
         f"VALUES ('{NM_USER}','{h}','USER',true,now(),now());")
    print("== setup done ==")


def teardown():
    psql(f"DELETE FROM users WHERE username IN ('{SA_USER}','{NM_USER}');")
    print("== teardown done ==")


def main():
    setup()
    try:
        # 1. 普通用户登录
        s, d = http("POST", "/auth/login", body={"username": NM_USER, "password": PW})
        check("普通用户登录 200", s == 200, f"status={s}")
        nm_token = d.get("token")
        check("普通用户 role=USER", d.get("role") == "USER", f"role={d.get('role')}")

        # 2. 普通用户访问管理员接口 -> 403
        s, _ = http("GET", "/admin/users", token=nm_token)
        check("非管理员访问 /admin/users 被拒绝(403)", s == 403, f"status={s}")

        # 3. /api/users/me
        s, d = http("GET", "/users/me", token=nm_token)
        check("/users/me 200", s == 200 and d.get("username") == NM_USER, f"status={s}")

        # 4. 修改密码（原密码错误 -> 400）
        s, _ = http("POST", "/users/me/password", token=nm_token,
                    body={"oldPassword": "wrong", "newPassword": "P6test@456"})
        check("错误原密码改密 400", s == 400, f"status={s}")
        # 改密成功
        s, _ = http("POST", "/users/me/password", token=nm_token,
                    body={"oldPassword": PW, "newPassword": PW})
        check("正确原密码改密 200", s == 200, f"status={s}")

        # 5. 通知中心
        s, d = http("GET", "/notifications", token=nm_token)
        check("/notifications 200", s == 200 and "unread" in d, f"status={s} unread={d.get('unread')}")
        s, _ = http("POST", "/notifications/read-all", token=nm_token)
        check("/notifications/read-all 200", s == 200, f"status={s}")

        # 6. 审计日志
        s, d = http("GET", "/audit?limit=20", token=nm_token)
        check("/audit 200", s == 200 and "logs" in d, f"status={s} count={len(d.get('logs', []))}")

        # 7. 超级管理员登录
        s, d = http("POST", "/auth/login", body={"username": SA_USER, "password": PW})
        check("超级管理员登录 200", s == 200 and d.get("role") == "SUPER_ADMIN", f"status={s} role={d.get('role')}")
        sa_token = d.get("token")

        # 8. 管理员列出用户
        s, d = http("GET", "/admin/users", token=sa_token)
        check("/admin/users 列出用户", s == 200 and len(d.get("users", [])) > 0,
              f"status={s} total={d.get('total')}")

        # 9. 修改普通用户角色 -> ADMIN
        s, d = http("POST", f"/admin/users/{{id}}/role".replace("{id}", "0"), token=sa_token)  # placeholder
        # 重新取 nm 用户 id
        _, dl = http("GET", "/admin/users", token=sa_token)
        nm_id = next((u["id"] for u in dl.get("users", []) if u["username"] == NM_USER), None)
        s, d = http("POST", f"/admin/users/{nm_id}/role", token=sa_token, body={"role": "ADMIN"})
        check("修改角色 -> ADMIN 200", s == 200 and d.get("role") == "ADMIN", f"status={s} role={d.get('role')}")

        # 10. 禁用再启用
        s, d = http("POST", f"/admin/users/{nm_id}/toggle-enabled", token=sa_token)
        check("禁用账号 200", s == 200 and d.get("enabled") is False, f"status={s} enabled={d.get('enabled')}")
        s, d = http("POST", f"/admin/users/{nm_id}/toggle-enabled", token=sa_token)
        check("重新启用 200", s == 200 and d.get("enabled") is True, f"status={s} enabled={d.get('enabled')}")

        # 11. 不能禁用自己
        sa_id = next((u["id"] for u in dl.get("users", []) if u["username"] == SA_USER), None)
        if sa_id:
            s, _ = http("POST", f"/admin/users/{sa_id}/toggle-enabled", token=sa_token)
            check("禁止禁用自己 400", s == 400, f"status={s}")
            s, _ = http("POST", f"/admin/users/{sa_id}/role", token=sa_token, body={"role": "USER"})
            check("禁止取消自己超管 400", s == 400, f"status={s}")

    finally:
        teardown()

    print(f"\n==== P6 结果: {len(PASS)} PASS / {len(FAIL)} FAIL ====")
    if FAIL:
        print("FAILED:", FAIL)
        raise SystemExit(1)


if __name__ == "__main__":
    main()
