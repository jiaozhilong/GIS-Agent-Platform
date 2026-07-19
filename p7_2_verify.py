#!/usr/bin/env python3
"""P7-2 SSO / OIDC 登录 端到端验证（mock IdP，标准库）。"""
import json
import urllib.request
import urllib.error
import urllib.parse

BASE = "http://localhost:8088"
PASS, FAIL = [], []


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None  # 不自动跟随，便于逐级捕获 Location


OPENER = urllib.request.build_opener(NoRedirect)


def get(url, token=None):
    req = urllib.request.Request(url, method="GET")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with OPENER.open(req, timeout=20) as r:
            return r.status, r.headers.get("Location"), r.read().decode() or "{}"
    except urllib.error.HTTPError as e:
        return e.code, e.headers.get("Location"), e.read().decode() or "{}"


def me(token):
    s, _, b = get(BASE + "/api/users/me", token)
    try:
        return s, json.loads(b)
    except Exception:
        return s, {}


def check(name, cond, detail=""):
    (PASS if cond else FAIL).append(name)
    print(f"[{'PASS' if cond else 'FAIL'}] {name}" + (f" — {detail}" if detail else ""))


def flow_once():
    """走完一次 SSO：authorize -> mock/authorize -> callback -> 提取 sso_token"""
    s1, loc1, _ = get(BASE + "/api/sso/authorize")
    if s1 != 302 or not loc1:
        return None, f"authorize 期望302, got {s1} loc={loc1}"
    s2, loc2, _ = get(loc1)
    if s2 != 302 or not loc2:
        return None, f"mock/authorize 期望302, got {s2} loc={loc2}"
    s3, loc3, _ = get(loc2)  # callback 内部换码+取用户信息，最终 302 回前端带 token
    if s3 != 302 or not loc3:
        return None, f"callback 期望302, got {s3} loc={loc3}"
    q = urllib.parse.urlparse(loc3).query
    params = urllib.parse.parse_qs(q)
    token = (params.get("sso_token") or [None])[0]
    if not token:
        return None, f"callback 未回带 sso_token, loc={loc3}"
    return token, ""


def main():
    # 前置清理：确保测试邮箱用户不存在
    import subprocess, os
    subprocess.run(["psql", "-h", "localhost", "-U", "gisagent", "-d", "gisagent", "-c",
                    "DELETE FROM users WHERE email='sso.user@example.com';"],
                   env={**os.environ, "PGPASSWORD": "gisagent"}, capture_output=True, text=True, check=False)

    try:
        # 1. config 可见
        s, _, b = get(BASE + "/api/sso/config")
        enabled = (json.loads(b).get("enabled") is True) if b else False
        check("SSO config 启用可见 200", s == 200 and enabled, f"status={s} enabled={enabled}")

        # 2. 首次 SSO 登录（邮箱不存在 -> 新建用户）
        tok1, err = flow_once()
        check("首次 SSO 全链路拿回 sso_token", tok1 is not None, err)
        if tok1:
            s, u1 = me(tok1)
            check("SSO token 可访问 /users/me 200", s == 200 and u1.get("email") == "sso.user@example.com",
                  f"status={s} email={u1.get('email')}")
            uid1 = u1.get("userId")
            check("SSO 新建用户 role=USER", u1.get("role") == "USER", f"role={u1.get('role')}")

            # 3. 再次 SSO 登录（同邮箱 -> 关联已有，不重复建号）
            tok2, err2 = flow_once()
            check("二次 SSO 全链路拿回 sso_token", tok2 is not None, err2)
            if tok2:
                s, u2 = me(tok2)
                check("二次 SSO 同一邮箱复用同一用户（不重复建号）",
                      s == 200 and u2.get("userId") == uid1,
                      f"uid1={uid1} uid2={u2.get('userId')}")
    finally:
        subprocess.run(["psql", "-h", "localhost", "-U", "gisagent", "-d", "gisagent", "-c",
                        "DELETE FROM users WHERE email='sso.user@example.com';"],
                       env={**os.environ, "PGPASSWORD": "gisagent"}, capture_output=True, text=True, check=False)
        print("== teardown done ==")

    print(f"\n==== P7-2 结果: {len(PASS)} PASS / {len(FAIL)} FAIL ====")
    if FAIL:
        print("FAILED:", FAIL)
        raise SystemExit(1)


if __name__ == "__main__":
    main()
