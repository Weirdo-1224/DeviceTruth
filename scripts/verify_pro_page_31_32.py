"""用真实 PostgreSQL、脚本化推理及既有双域样本复验专业页。"""

import json
from uuid import uuid4

from playwright.sync_api import expect as on_page, sync_playwright

from verify_browser_recovery import serve_frontend
from verify_general_agent import FACTORY_GOAL, ROOT, Server, await_run, expect, import_manual


def seed(product, text, asset):
    conversation = expect(product.request("POST", "/api/conversations", {}), 201)
    cid = conversation["conversationId"]
    response = product.request("POST", f"/api/conversations/{cid}/messages",
                               {"schemaVersion": 1, "userMessage": text, "selectedAssetId": asset},
                               {"Idempotency-Key": str(uuid4())})
    run = await_run(product, expect(response, 202)["runId"], timeout=30)
    assert run["status"] == "COMPLETED", run["status"]
    refs = expect(product.request("GET", f"/api/runs/{run['runId']}/references"), 200)
    assert refs and run["result"]["claims"]
    return cid, run, refs


def main():
    output = ROOT / "outputs" / "validation" / f"pro-page-dual-{uuid4().hex[:8]}"
    output.mkdir(parents=True)
    product = Server("pro-page-" + uuid4().hex[:8], "pro-page-dual", "deterministic", "scripted",
                     True, output, fixture_assets="SC-0245-B,CAR-B")
    ui = None
    evidence = {}
    try:
        import_manual(product)
        samples = [
            ("automotive", "候选原因", seed(product, "新能源汽车充电中断，使用直流快充，如何排查？", "CAR-B")),
            ("industrial", "待人工核验", seed(product, FACTORY_GOAL, "SC-0245-B")),
        ]
        ui, url = serve_frontend(lambda: product)
        with sync_playwright() as playwright:
            browser = playwright.chromium.launch(channel="chrome", headless=True)
            page = browser.new_page(viewport={"width": 1440, "height": 950})
            posts = []
            page.on("request", lambda request: posts.append(request.url) if request.method == "POST" else None)
            page.add_init_script(f"localStorage.setItem('devicetruth.conversation-id', '{samples[0][2][0]}')")
            page.goto(url)
            for domain, label, (cid, run, refs) in samples:
                result = run["result"]
                sidebar_item = page.locator(f".pro-sidebar [data-conversation-id='{cid}']")
                sidebar_item.click()
                on_page(sidebar_item).to_have_attribute("aria-current", "true")
                panel = page.locator(f".pro-page[data-run-id='{run['runId']}']")
                on_page(panel.locator(".risk-chip")).to_contain_text(result["riskLevel"])
                on_page(panel.locator(".pro-nav")).to_contain_text(label)
                claims = panel.locator("button.claim-select")
                on_page(claims).to_have_count(len(result["claims"]))
                before = len(posts)
                if len(result["claims"]) > 1:
                    claims.nth(1).click()
                    on_page(panel.locator(".detail-text")).to_have_text(result["claims"][1]["text"])
                    claims.first.click()
                on_page(panel.locator(".detail-text")).to_have_text(result["claims"][0]["text"])
                on_page(panel.locator(".pro-sources")).to_be_visible()
                on_page(page.get_by_role("dialog", name="证据详情")).not_to_be_visible()
                page.get_by_role("button", name="展开完整结果").click()
                on_page(panel.get_by_test_id("full-result")).to_contain_text(label)
                reference = next(item for item in refs if item["referenceId"] in result["claims"][0]["referenceIds"])
                opener = panel.locator(f"[data-evidence-id='{reference['referenceId']}']").first
                opener.click()
                dialog = page.get_by_role("dialog", name="证据详情")
                on_page(dialog.locator(".modal-excerpt")).to_contain_text(reference["excerpt"])
                if reference.get("section"):
                    on_page(dialog).to_contain_text(reference["section"])
                page.keyboard.press("Shift+Tab")
                assert dialog.evaluate("(element) => element.contains(document.activeElement)")
                page.get_by_role("button", name="关闭证据抽屉").click()
                on_page(opener).to_be_focused()
                page.get_by_role("button", name="查看调查记录").click()
                on_page(page.locator(".pro-drawer [data-type='RUN_ANSWER']")).to_be_visible()
                page.get_by_role("button", name="关闭调查记录").click()
                skill = expect(product.request("GET", f"/api/runs/{run['runId']}/skill"), 200)
                on_page(panel).to_contain_text(skill["skillId"])
                assert len(posts) == before
                page.screenshot(path=output / f"{domain}.png", full_page=True)
                evidence[domain] = {"runId": run["runId"], "category": result["category"], "risk": result["riskLevel"],
                                    "references": len(refs), "skill": skill["skillId"]}
                investigate = page.get_by_role("button", name="深入调查")
                on_page(investigate).to_be_enabled()
                if domain == "industrial":
                    investigate.click()
                    assert len(posts) == before
                    with page.expect_response(lambda response: response.request.method == "POST"
                                              and response.url.endswith("/messages")) as accepted:
                        page.get_by_role("button", name="发送", exact=True).click()
                    assert accepted.value.status == 202
                    payload = accepted.value.request.post_data_json
                    assert result["claims"][0]["text"] in payload["userMessage"]
                    assert payload["selectedAssetId"] == "SC-0245-B"
                    assert {item["referenceId"] for item in payload["contextRefs"]} == set(result["claims"][0]["referenceIds"])
                    assert "asOf" not in payload
                    detail = expect(product.request("GET", f"/api/conversations/{cid}"), 200)
                    assert len(detail["runs"]) == 2 and detail["runs"][0]["runId"] == run["runId"]
            browser.close()
        report = {"status": "PASS", "domains": evidence, "historicalInvestigationCreatedNewRun": True}
        (output / "result.json").write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
        print(json.dumps(report | {"evidenceDir": str(output)}, ensure_ascii=False))
    finally:
        if ui:
            ui.shutdown()
            ui.server_close()
        product.close()


if __name__ == "__main__":
    main()
