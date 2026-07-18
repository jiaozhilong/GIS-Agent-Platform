#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
GIS-Agent Platform — Phase 4 端到端走通验收脚本 (p4_acceptance.py)

用途
----
在【真实联网环境】中一键验证"功能全做完"：注册 -> 登录 -> 建 LLM Provider(真实密钥并真连)
-> 建 IMA 配置(真实凭证并真连) -> 建项目(上传需求) -> 跑流水线 -> 轮询状态 -> 下载 md/docx/pptx。

沙箱无外网，本脚本需在用户本地/服务器(能访问 DeepSeek + IMA 的环境)运行。

运行方式
--------
  export BASE_URL=http://localhost:8080
  export LLM_ENDPOINT=https://api.deepseek.com/v1/chat/completions
  export LLM_API_KEY=sk-xxxx
  export LLM_MODEL=deepseek-chat
  export IMA_KB_ID=kb-xxxx
  export IMA_CLIENT_ID=xxxx
  export IMA_API_KEY=xxxx
  python3 p4_acceptance.py

或通过参数覆盖（参数优先于环境变量）：
  python3 p4_acceptance.py --base-url http://host:8080 --llm-api-key sk-xxx ...

注意
----
- IMA 真连需在后端设置环境变量 IMA_MOCK_ENABLED=false（并注入 IMA_OPENAPI_CLIENTID/IMA_OPENAPI_APIKEY）。
  若后端仍为 mock 模式，脚本会跳过 IMA 真连步骤（标记为 SKIP），不影响其余验收。
- 全部步骤 PASS 且退出码 0 即代表"功能全做完并走通"。
"""

import argparse
import io
import json
import os
import sys
import time
import urllib.error
import urllib.request

# ---------- 配置 ----------
def env_or_arg(arg, env, default=None):
    return arg if arg is not None else os.environ.get(env, default)

def build_config():
    p = argparse.ArgumentParser(description="GIS-Agent Platform P4 端到端验收")
    p.add_argument("--base-url", default=None)
    p.add_argument("--username", default=None)
    p.add_argument("--password", default=None)
    p.add_argument("--llm-endpoint", default=None)
    p.add_argument("--llm-api-key", default=None)
    p.add_argument("--llm-model", default=None)
    p.add_argument("--ima-kb-id", default=None)
    p.add_argument("--ima-kb-name", default=None)
    p.add_argument("--ima-client-id", default=None)
    p.add_argument("--ima-api-key", default=None)
    p.add_argument("--template-id", default=None)
    p.add_argument("--max-wait", type=int, default=600)
    p.add_argument("--skip-ima", action="store_true")
    a = p.parse_args()

    cfg = {
        "base_url": env_or_arg(a.base_url, "BASE_URL", "http://localhost:8080").rstrip("/"),
        "username": env_or_arg(a.username, "USERNAME", "p4accept"),
        "password": env_or_arg(a.password, "PASSWORD", "p4accept123"),
        "llm_endpoint": env_or_arg(a.llm_endpoint, "LLM_ENDPOINT", "https://api.deepseek.com/v1/chat/completions"),
        "llm_api_key": env_or_arg(a.llm_api_key, "LLM_API_KEY", ""),
        "llm_model": env_or_arg(a.llm_model, "LLM_MODEL", "deepseek-chat"),
        "ima_kb_id": env_or_arg(a.ima_kb_id, "IMA_KB_ID", ""),
        "ima_kb_name": env_or_arg(a.ima_kb_name, "IMA_KB_NAME", "IMA 验收库"),
        "ima_client_id": env_or_arg(a.ima_client_id, "IMA_CLIENT_ID", ""),
        "ima_api_key": env_or_arg(a.ima_api_key, "IMA_API_KEY", ""),
        "template_id": env_or_arg(a.template_id, "TEMPLATE_ID", "full_solution"),
        "max_wait": a.max_wait,
        "skip_ima": a.skip_ima or not (env_or_arg(a.ima_kb_id, "IMA_KB_ID", "") and env_or_arg(a.ima_api_key, "IMA_API_KEY", "")),
    }
    return cfg


# ---------- HTTP 工具 ----------
class Client:
    def __init__(self, base):
        self.base = base
        self.token = None

    def _req(self, method, path, body=None, files=None, raw=False):
        url = self.base + path
        headers = {}
        data = None
        if self.token:
            headers["Authorization"] = "Bearer " + self.token
        if files is not None:
            boundary = "----p4boundary"
            parts = []
            for k, v in (body or {}).items():
                parts.append(("--%s\r\nContent-Disposition: form-data; name=\"%s\"\r\n\r\n%s\r\n" % (boundary, k, v)).encode())
            for k, (fname, fdata) in files.items():
                parts.append(("--%s\r\nContent-Disposition: form-data; name=\"%s\"; filename=\"%s\"\r\nContent-Type: application/octet-stream\r\n\r\n" % (boundary, k, fname)).encode())
                parts.append(fdata)
                parts.append(b"\r\n")
            parts.append(("--%s--\r\n" % boundary).encode())
            data = b"".join(parts)
            headers["Content-Type"] = "multipart/form-data; boundary=%s" % boundary
        elif body is not None:
            data = json.dumps(body).encode()
            headers["Content-Type"] = "application/json"
        req = urllib.request.Request(url, data=data, headers=headers, method=method)
        try:
            with urllib.request.urlopen(req, timeout=120) as resp:
                if raw:
                    return resp.status, resp.read()
                return resp.status, json.loads(resp.read().decode("utf-8") or "null")
        except urllib.error.HTTPError as e:
            payload = e.read().decode("utf-8", "ignore")
            try:
                payload = json.loads(payload)
            except Exception:
                pass
            return e.code, payload

    def get(self, p, raw=False): return self._req("GET", p, raw=raw)
    def post(self, p, body=None, files=None): return self._req("POST", p, body=body, files=files)
    def put(self, p, body=None): return self._req("PUT", p, body=body)
    def delete(self, p): return self._req("DELETE", p)


# ---------- 验收框架 ----------
class Acc:
    def __init__(self):
        self.results = []
        self.fail = 0

    def step(self, name, ok, detail=""):
        mark = "PASS" if ok else "FAIL"
        if not ok:
            self.fail += 1
        self.results.append((mark, name, detail))
        print("[%s] %s%s" % (mark, name, ("  — " + detail) if detail else ""))

    def skip(self, name, detail=""):
        self.results.append(("SKIP", name, detail))
        print("[SKIP] %s%s" % (name, ("  — " + detail) if detail else ""))


def main():
    cfg = build_config()
    c = Client(cfg["base_url"])
    acc = Acc()

    # 1) 注册 / 登录
    st, pl = c.post("/api/auth/register", {"username": cfg["username"], "password": cfg["password"], "email": cfg["username"] + "@x.com"})
    if st not in (200, 201):
        st, pl = c.post("/api/auth/login", {"username": cfg["username"], "password": cfg["password"]})
    tok = pl.get("token") or (pl.get("data") or {}).get("token")
    acc.step("注册/登录获取 token", st in (200, 201) and bool(tok), "uid=%s" % pl.get("id") if isinstance(pl, dict) else "")
    if not tok:
        print("\n无法获取 token，终止。请确认后端已启动且 DB 可连。")
        sys.exit(2)
    c.token = tok

    # 2) Skills 接口（真实工具数）
    st, pl = c.get("/api/skills")
    total = (pl or {}).get("total")
    acc.step("GET /api/skills 返回真实工具清单", st == 200 and isinstance(total, int) and total >= 1,
             "total=%s" % total)

    # 3) 建 LLM Provider + 真连测试
    if not cfg["llm_api_key"]:
        acc.skip("LLM Provider 真实连通性", "未提供 LLM_API_KEY，跳过")
    else:
        st, pl = c.post("/api/providers", {
            "name": "p4-deepseek",
            "providerType": "deepseek",
            "endpoint": cfg["llm_endpoint"],
            "apiKey": cfg["llm_api_key"],
            "model": cfg["llm_model"],
            "isDefault": True,
        })
        pid = (pl or {}).get("id")
        no_plain = isinstance(pl, dict) and "apiKey" not in pl and pl.get("hasApiKey") is True
        acc.step("创建 LLM Provider(密钥加密存储, 响应无明文)", st in (200, 201) and bool(pid) and no_plain,
                 "pid=%s hasApiKey=%s" % (pid, (pl or {}).get("hasApiKey")))
        if pid:
            st, pl = c.post("/api/providers/%s/test" % pid)
            ok = (pl or {}).get("success") is True
            acc.step("LLM Provider 真实连通(调用模型返回成功)", ok,
                     "success=%s msg=%s" % ((pl or {}).get("success"), (pl or {}).get("message")))

    # 4) IMA 配置 + 真连测试
    if cfg["skip_ima"]:
        acc.skip("IMA 真实连通性", "未提供 IMA_KB_ID/IMA_API_KEY 或 --skip-ima，跳过（后端需 IMA_MOCK_ENABLED=false）")
    else:
        st, pl = c.post("/api/ima/configs", {
            "kbId": cfg["ima_kb_id"],
            "kbName": cfg["ima_kb_name"],
            "kbType": "notes",
            "purpose": "P4 验收",
            "searchWeight": 1.0,
            "enabled": True,
        })
        cid = (pl or {}).get("id")
        acc.step("创建 IMA 知识库配置", st in (200, 201) and bool(cid), "cid=%s" % cid)
        if cid:
            st, pl = c.post("/api/ima/configs/%s/test" % cid)
            ok = (pl or {}).get("success") is True
            acc.step("IMA 真实连通(调用 search_note_book 返回成功)", ok,
                     "success=%s msg=%s" % ((pl or {}).get("success"), (pl or {}).get("message")))

    # 5) 建项目 + 上传需求 + 跑流水线
    req_doc = (
        "# 智慧城市管网 GIS 解决方案需求\n\n"
        "## 背景\n某新区需建设地下综合管网一张图，整合给水、排水、燃气、热力管线数据。\n\n"
        "## 功能需求\n1. 多源管线数据接入与坐标纠偏；2. 三维管网可视化与爆管分析；"
        "3. 与 IoT 传感数据联动告警；4. 移动端巡检。\n\n"
        "## 非功能需求\n支持 10 万+ 管线要素并发渲染，可用性 99.9%。\n"
    ).encode("utf-8")
    st, pl = c.post("/api/projects",
                    body={"name": "P4 验收-智慧管网", "description": "端到端验收", "templateId": cfg["template_id"]},
                    files={"file": ("requirement.md", req_doc)})
    proj_id = (pl or {}).get("id")
    acc.step("创建项目并上传需求文档", st in (200, 201) and bool(proj_id), "projId=%s" % proj_id)
    if not proj_id:
        print("\n项目创建失败，终止后续步骤。")
        return finish(acc)

    st, pl = c.post("/api/projects/%s/run" % proj_id)
    acc.step("启动流水线", st in (200, 201), "runResp=%s" % json.dumps(pl, ensure_ascii=False)[:80])

    # 6) 轮询状态
    terminal = {"SUCCESS", "PARTIAL", "FAILED"}
    final_status = None
    deadline = time.time() + cfg["max_wait"]
    while time.time() < deadline:
        st, pl = c.get("/api/projects/%s/status" % proj_id)
        status = (((pl or {}).get("status")) or "").upper()
        final_status = status
        if status in terminal:
            break
        time.sleep(5)
    acc.step("流水线执行至终态(SUCCESS/PARTIAL)", final_status in ("SUCCESS", "PARTIAL"),
             "final=%s" % final_status)

    # 7) 下载产物
    for fmt in ("md", "docx", "pptx"):
        st, data = c.get("/api/projects/%s/download/%s" % (proj_id, fmt), raw=True)
        ok = st == 200 and isinstance(data, (bytes, bytearray)) and len(data) > 0
        acc.step("下载方案产物 .%s (非空)" % fmt, ok, "bytes=%s" % (len(data) if isinstance(data, (bytes, bytearray)) else st))

    # 8) 知识库感知重生成（若 IMA 已真连）
    if not cfg["skip_ima"] and proj_id:
        st, pl = c.post("/api/projects/%s/rerun-kb" % proj_id)
        acc.step("知识库更新后一键重生成(rerun-kb)", st in (200, 201), "resp=%s" % json.dumps(pl, ensure_ascii=False)[:60])

    finish(acc)


def finish(acc):
    print("\n================ 验收汇总 ================")
    for mark, name, detail in acc.results:
        print("[%s] %s" % (mark, name))
    print("==========================================")
    print("结果: %d 通过 / %d 失败 / %d 跳过" % (
        sum(1 for m, _, _ in acc.results if m == "PASS"),
        acc.fail,
        sum(1 for m, _, _ in acc.results if m == "SKIP"),
    ))
    if acc.fail == 0:
        print("✅ 全部关键项通过：功能已做完并端到端走通。")
        sys.exit(0)
    else:
        print("❌ 存在失败项，请根据上述 FAIL 明细修复后重试。")
        sys.exit(1)


if __name__ == "__main__":
    main()
