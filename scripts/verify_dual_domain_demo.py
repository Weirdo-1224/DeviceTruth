"""7.1 双域（工业 + 汽车）真模专业页演示验收。

真模（openai→DeepSeek）+ 真实 embedding（DashScope text-embedding-v4）+ 真实 PG 隔离评测库；
演示知识库 = 项目自制演示手册（1.2 修订口径：渠道 AUTHORIZED，文件自身 SIMULATED 演示标注）。
证据：两域在同一张专业页各完成一条真模+演示资料调查（Skill/工具事件/引用回读）、
取消与刷新浏览器覆盖；ASK_USER 协议路径由 verify_browser_recovery.py 单列。
"""

import argparse
import json
import os
import time
import uuid

from verify_browser_recovery import serve_frontend
from verify_general_agent import ROOT, Server, expect

KNOWLEDGE_DOCS = [
    ("汽车", "skills/automotive-diagnosis/references/charging-troubleshooting.md",
     "充电系统故障排查指南（automotive-diagnosis）"),
    ("汽车", "datasets/knowledge/charging-system-manual.md", "充电系统维修手册"),
    ("工业", "skills/factory-equipment/references/controller-maintenance.md",
     "PLC控制器维护手册（factory-equipment）"),
    ("工业", "skills/factory-equipment/references/domain-knowledge.md",
     "产线控制领域知识（factory-equipment）"),
]

AUTO_QUESTION = "新能源汽车充电中断，没有故障码，先查什么资料？"
FACTORY_QUESTION = ("请做控制器固件核对。查询 moduleVersion，比较 2024-06-01 的出厂记录"
                    "和当前改造、旧模板，说明现场核验情况。")
FACTORY_ASSET = "SC-0245-B"


def import_doc(product, path, title):
    boundary = uuid.uuid4().hex
    content = (ROOT / path).read_bytes()
    parts = []
    def field(name, value):
        parts.append(f"--{boundary}\r\nContent-Disposition: form-data; name=\"{name}\"\r\n\r\n{value}\r\n".encode())
    field("title", title)
    field("version", "1.0")
    field("domain", "general")
    field("dataNature", "AUTHORIZED")
    parts.append(f"--{boundary}\r\nContent-Disposition: form-data; name=\"file\"; filename=\"doc.md\"\r\n"
                 f"Content-Type: text/markdown\r\n\r\n".encode())
    body = b"".join(parts) + content + f"\r\n--{boundary}--\r\n".encode()
    status, payload = product.request("POST", "/api/files", body,
                                      {"Content-Type": f"multipart/form-data; boundary={boundary}"})
    if status not in (200, 201, 202):
        raise RuntimeError(f"knowledge import failed for {title}: {status} {payload}")
    file_id = payload["fileId"]
    for _ in range(120):
        status, payload = product.request("GET", f"/api/files/{file_id}")
        if payload["parseStatus"] in ("PARSED", "FAILED", "OCR_REQUIRED"):
            if payload["parseStatus"] != "PARSED":
                raise RuntimeError(f"knowledge import {title} ended {payload['parseStatus']}")
            return
        time.sleep(1)
    raise RuntimeError(f"knowledge import {title} timeout")


def verify(browser_path, reasoner, embedding, output):
    from playwright.sync_api import expect as expect_page, sync_playwright

    def complete_round(page, answer):
        """等待本轮完成；真模可能先发 ASK_USER，命中问题区就用可见补答沿同一 Run 继续。"""
        expect_page(page.locator(".pro-page .summary, .question h2")).to_be_visible(timeout=300_000)
        asked = page.locator(".question h2").is_visible()
        if asked:
            page.locator(".question textarea").fill(answer)
            page.get_by_role("button", name="提交补充信息").click()
            expect_page(page.locator(".pro-page .summary")).to_be_visible(timeout=300_000)
        return asked

    owner = "d7-" + uuid.uuid4().hex[:12]
    project = "dual-domain"
    product = Server(owner, project, embedding, reasoner, True, output,
                     fixture_assets=f"{FACTORY_ASSET},CAR-B")
    ui = None
    evidence = {"reasoner": reasoner, "embedding": embedding, "domains": {}}
    try:
        for _, path, title in KNOWLEDGE_DOCS:
            import_doc(product, path, title)
        ui, url = serve_frontend(lambda: product)

        with sync_playwright() as playwright:
            with playwright.chromium.launch_persistent_context(
                    output / "browser-profile", executable_path=browser_path, headless=True) as browser:
                page = browser.pages[0]
                page.goto(url)

                # ---- 汽车域：真模 + 演示知识库，专业页全流程 ----
                page.locator(".composer textarea").fill(AUTO_QUESTION)
                page.get_by_role("button", name="发送", exact=True).click()
                auto_asked = complete_round(page, "直流快充，SOC 约 40%，私人充电桩。")
                auto_conversation = page.evaluate("localStorage.getItem('devicetruth.conversation-id')")
                auto = expect(product.request("GET", f"/api/conversations/{auto_conversation}"), 200)
                auto_run = auto["runs"][-1]
                assert auto_run["status"] == "COMPLETED" and auto_run["result"], auto_run["status"]
                auto_skill = expect(product.request("GET", f"/api/runs/{auto_run['runId']}/skill"), 200)
                auto_tools = expect(product.request("GET", f"/api/runs/{auto_run['runId']}/tool-events"), 200)
                auto_refs = expect(product.request("GET", f"/api/runs/{auto_run['runId']}/references"), 200)
                tool_names = {t.get("toolName") for t in auto_tools}
                assert auto_refs, "汽车域调查没有任何已登记引用"
                evidence["domains"]["automotive"] = {
                    "runId": auto_run["runId"], "category": auto_run["result"]["category"],
                    "claims": len(auto_run["result"]["claims"]), "references": len(auto_refs),
                    "skill": auto_skill.get("skillId"), "tools": sorted(tool_names),
                    "dataNature": auto_run["result"]["dataNature"], "askUserTriggered": auto_asked}

                # 刷新覆盖：结果与引用在刷新后仍在
                page.reload()
                expect_page(page.locator(".pro-page .summary")).to_be_visible(timeout=30_000)
                evidence["refreshSurvives"] = True

                # ---- 取消覆盖：同会话再开一轮并在执行中取消 ----
                page.locator(".composer textarea").fill("再补充调查：充电中断前是否有异常提示音？")
                page.get_by_role("button", name="发送", exact=True).click()
                expect_page(page.get_by_role("button", name="取消调查")).to_be_visible(timeout=60_000)
                page.get_by_role("button", name="取消调查").click()
                expect_page(page.locator(".pro-page")).to_contain_text("已取消", timeout=60_000)
                cancelled = expect(product.request(
                    "GET", f"/api/conversations/{auto_conversation}"), 200)["runs"][-1]
                assert cancelled["status"] == "CANCELLED", cancelled["status"]
                evidence["cancelRound"] = {"runId": cancelled["runId"], "status": "CANCELLED"}

                # ---- 工业域：关联 SC-0245-B 后真模固件核对 ----
                page.get_by_role("button", name="新调查", exact=True).click()
                page.get_by_role("button", name="相关资产").click()
                page.locator("#asset").select_option(FACTORY_ASSET)
                page.locator(".composer textarea").fill(FACTORY_QUESTION)
                page.get_by_role("button", name="发送", exact=True).click()
                factory_asked = complete_round(page, "以 2026-11-13 08:00 时点为准，优先采用出厂记录。")
                factory_conversation = page.evaluate("localStorage.getItem('devicetruth.conversation-id')")
                factory = expect(product.request("GET", f"/api/conversations/{factory_conversation}"), 200)
                factory_run = factory["runs"][-1]
                assert factory_run["status"] == "COMPLETED" and factory_run["result"], factory_run["status"]
                factory_skill = expect(product.request("GET", f"/api/runs/{factory_run['runId']}/skill"), 200)
                factory_tools = expect(product.request("GET", f"/api/runs/{factory_run['runId']}/tool-events"), 200)
                factory_refs = expect(product.request("GET", f"/api/runs/{factory_run['runId']}/references"), 200)
                factory_tool_names = {t.get("toolName") for t in factory_tools}
                assert "get_configuration" in factory_tool_names, factory_tool_names
                assert factory_refs, "工业域调查没有任何已登记引用"
                evidence["domains"]["industrial"] = {
                    "runId": factory_run["runId"], "category": factory_run["result"]["category"],
                    "claims": len(factory_run["result"]["claims"]), "references": len(factory_refs),
                    "skill": factory_skill.get("skillId"), "tools": sorted(factory_tool_names),
                    "asset": FACTORY_ASSET, "dataNature": factory_run["result"]["dataNature"],
                    "askUserTriggered": factory_asked}
        return {"status": "PASS", **evidence}
    finally:
        if ui is not None:
            ui.shutdown()
            ui.server_close()
        product.close()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--browser-path", required=True)
    parser.add_argument("--reasoner", default=os.environ.get("DEVICETRUTH_D4_REASONER", "openai"),
                        choices=["openai", "qwen"])
    parser.add_argument("--embedding", default=os.environ.get("DEVICETRUTH_D4_EMBEDDING", "real"),
                        choices=["real", "deterministic"])
    args = parser.parse_args()
    for name in ("DEVICETRUTH_TEST_PG_BASE_URL", "DEVICETRUTH_TEST_PG_USERNAME",
                 "DEVICETRUTH_TEST_PG_PASSWORD", "DEVICETRUTH_EVAL_DATABASE"):
        if not os.environ.get(name):
            raise RuntimeError(f"BLOCKED_BY_ENV: {name} is required")
    if not os.environ.get("OPENAI_API_KEY"):
        raise RuntimeError("BLOCKED_BY_ENV: OPENAI_API_KEY is required")
    output = ROOT / "outputs" / "validation" / f"dual-domain-{uuid.uuid4().hex[:8]}"
    output.mkdir(parents=True)
    result = verify(args.browser_path, args.reasoner, args.embedding, output)
    (output / "result.json").write_text(json.dumps(result, indent=2), encoding="utf-8")
    print(json.dumps(result | {"result": str(output / "result.json")}, ensure_ascii=False))


if __name__ == "__main__":
    main()
