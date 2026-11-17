"""Verify Vue recovery against the packaged product server and real PostgreSQL."""

import argparse
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
import json
from pathlib import Path
from threading import Thread
import urllib.error
import urllib.request
import uuid

from verify_general_agent import ROOT, Server, expect, import_manual


def serve_frontend(product_ref):
    """ serve frontend/dist on a random localhost port, proxying /api to the product server.
    product_ref is a zero-arg callable returning the CURRENT Server (callers may restart it);
    returns (httpd, url); caller must shutdown()/server_close() the httpd. """
    frontend = ROOT / "frontend" / "dist"
    if not (frontend / "index.html").is_file():
        raise RuntimeError("Build the Vue frontend before running browser verification.")

    class Frontend(SimpleHTTPRequestHandler):
        def __init__(self, *args, **kwargs):
            super().__init__(*args, directory=frontend, **kwargs)

        def do_GET(self):
            if self.path.startswith("/api/"):
                self.proxy()
            else:
                super().do_GET()

        def do_POST(self):
            self.proxy()

        def log_message(self, *_):
            pass

        def proxy(self):
            length = int(self.headers.get("Content-Length", "0"))
            request = urllib.request.Request(
                product_ref().base + self.path,
                data=self.rfile.read(length) if length else None,
                headers={key: value for key, value in self.headers.items()
                         if key.lower() in {"accept", "content-type", "idempotency-key"}},
                method=self.command,
            )
            try:
                response = urllib.request.urlopen(request, timeout=30)
            except urllib.error.HTTPError as error:
                response = error
            with response:
                self.send_response(response.code)
                self.send_header("Content-Type", response.headers.get("Content-Type", "application/json"))
                self.end_headers()
                try:
                    self.wfile.write(response.read())
                except (BrokenPipeError, ConnectionAbortedError, ConnectionResetError):
                    pass

    ui = ThreadingHTTPServer(("127.0.0.1", 0), Frontend)
    thread = Thread(target=ui.serve_forever, daemon=True)
    thread.start()
    return ui, f"http://127.0.0.1:{ui.server_port}"


def verify(browser_path, output):
    from playwright.sync_api import expect as expect_page, sync_playwright

    owner = "browser-" + uuid.uuid4().hex[:12]
    project = "browser-recovery"

    def start_product():
        server_output = output / ("server-" + uuid.uuid4().hex[:8])
        server_output.mkdir()
        return Server(owner, project, "deterministic", "scripted", True, server_output)

    product = start_product()
    ui = None
    try:
        ui, url = serve_frontend(lambda: product)
        import_manual(product)
        original = "新能源汽车充电中断，没有故障码，先查什么资料？"
        reply = "直流快充，SOC 约 40%"
        profile = output / "browser-profile"

        with sync_playwright() as playwright:
            def open_browser():
                return playwright.chromium.launch_persistent_context(
                    profile, executable_path=browser_path, headless=True,
                )

            with open_browser() as browser:
                page = browser.pages[0]
                page.goto(url)
                page.locator(".composer textarea").fill(original)
                page.get_by_role("button", name="发送", exact=True).click()
                expect_page(page.get_by_role("heading", name="需要补充信息")).to_be_visible(timeout=30_000)
                conversation = page.evaluate("localStorage.getItem('devicetruth.conversation-id')")
                waiting = expect(product.request("GET", f"/api/conversations/{conversation}"), 200)
                assert len(waiting["runs"]) == 1
                run_id = waiting["runs"][0]["runId"]
                question = waiting["runs"][0]["pendingQuestion"]
                assert question["pendingQuestionId"]
                page.reload()
                expect_page(page.get_by_role("heading", name="需要补充信息")).to_be_visible(timeout=30_000)
                expect_page(page.locator(".bubble", has_text=original).first).to_be_visible()

            product.close()
            product = start_product()
            restored = expect(product.request("GET", f"/api/runs/{run_id}"), 200)
            assert restored["status"] == "WAITING_FOR_USER" and restored["pendingQuestion"] == question

            with open_browser() as browser:
                page = browser.pages[0]
                page.goto(url)
                expect_page(page.get_by_role("heading", name="需要补充信息")).to_be_visible(timeout=30_000)
                page.locator(".question textarea").fill(reply)
                page.get_by_role("button", name="提交补充信息").click()
                # 4.1/4.2 专业页选择器：补答沿同一 Run 恢复，终态后页面渲染本轮摘要与判断引用
                expect_page(page.locator(".pro-page .summary")).to_be_visible(timeout=30_000)
                expect_page(page.locator(".claim-detail .citation-ref").first).to_be_visible()
                page.reload()
                expect_page(page.locator(".pro-page .summary")).to_be_visible(timeout=30_000)
                # 补答消息被折叠进“查看前序对话”（报告存在时只展开首条与末条），用附着断言替代可见断言
                expect_page(page.get_by_text(reply, exact=True)).to_have_count(1)
                # 证据抽屉：引用可打开真实摘录（3.2 组件）
                page.locator(".claim-detail .citation-ref").first.click()
                expect_page(page.locator(".evidence-drawer .drawer-excerpt")).to_be_visible()
                page.locator(".evidence-drawer button[aria-label='关闭证据抽屉']").click()
                completed = expect(product.request("GET", f"/api/conversations/{conversation}"), 200)
                assert len(completed["runs"]) == 1
                assert completed["runs"][0]["runId"] == run_id
                assert completed["runs"][0]["status"] == "COMPLETED"
                # 4.2 轮次与会话历史：新调查后回到旧会话，结果仍可回看（已收进“查看调查记录”抽屉）
                page.get_by_role("button", name="查看调查记录").click()
                expect_page(page.locator(".history-list .conversation-link").first).to_be_visible()
                page.get_by_role("button", name="新调查", exact=True).click()
                expect_page(page.locator(".conversation-link")).to_have_count(2)
                expect_page(page.locator(".pro-page")).to_have_count(0)
                # 主视图不应残留原问题（原文仍合法出现在抽屉的会话标题里，故限定主区域断言）
                expect_page(page.locator(".main-scroll").get_by_text(original, exact=True)).to_have_count(0)
                page.locator(".history-list .conversation-link", has_text=original[:12]).last.click()
                expect_page(page.locator(".pro-page .summary")).to_be_visible()
                expect_page(page.locator(".pro-drawer").get_by_text(reply, exact=True)).to_have_count(1)
        return {"status": "PASS", "sameRun": True, "waitingSurvivedRefresh": True,
                "waitingSurvivedBrowserAndServerRestart": True, "answerAndCitationSurvivedRefresh": True,
                "historyRestoredAfterConversationSwitch": True}
    finally:
        if ui is not None:
            ui.shutdown()
            ui.server_close()
        product.close()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--browser-path", type=Path, required=True)
    args = parser.parse_args()
    output = ROOT / "outputs" / "validation" / f"browser-recovery-{uuid.uuid4().hex}"
    output.mkdir(parents=True)
    result = verify(args.browser_path, output)
    (output / "result.json").write_text(json.dumps(result, indent=2), encoding="utf-8")
    print(json.dumps(result | {"result": str(output / "result.json")}))


if __name__ == "__main__":
    main()
