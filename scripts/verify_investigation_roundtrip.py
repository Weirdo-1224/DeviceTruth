"""D4 深入调查与补答往返的真实模型浏览器验收（变更 devicetruth-pro-workbench-mvp 5.1/5.2）。

真模 + 真实 embedding + 真实 PG + 隔离评测库；Playwright 驱动真实 Chrome 走打包后的前端。
收集六项核心证据中的浏览器可验部分：
  1. 选择已有判断只切 UI、零提交；
  2. 深入调查在同一会话创建新 Run（不同 runId，无 replyTo）；
  3. 新 Run 重新登记继承引用（来源一致但引用 ID 全新）；
  4. 新结果展示且旧轮仍可回看；
  5. 普通现场补充作为用户报告开启后续 Run；
  6. ASK_USER 补答恢复同一 Run：真模未保证触发，若本轮未触发则记录 NOT_TRIGGERED，
     由 verify_browser_recovery.py 的协议替身路径单独覆盖，不冒充真模验收。
"""

import argparse
import json
import os
import uuid

from verify_browser_recovery import serve_frontend
from verify_general_agent import ROOT, Server, expect, import_manual


def verify(browser_path, reasoner, embedding, output):
    from playwright.sync_api import expect as expect_page, sync_playwright

    owner = "d4-" + uuid.uuid4().hex[:12]
    project = "pro-workbench"
    product = Server(owner, project, embedding, reasoner, True, output)
    ui = None
    evidence = {"reasoner": reasoner, "embedding": embedding}
    try:
        import_manual(product)
        ui, url = serve_frontend(lambda: product)

        question = "新能源汽车充电中断，没有故障码，先查什么资料？"
        field_note = "现场补充：充电中断发生在直流快充阶段，SOC 约 40%。"

        with sync_playwright() as playwright:
            with playwright.chromium.launch_persistent_context(
                    output / "browser-profile", executable_path=browser_path, headless=True) as browser:
                page = browser.pages[0]
                page.goto(url)
                # 1) 首轮真模调查：完成轮渲染摘要与判断
                page.locator(".composer textarea").fill(question)
                page.get_by_role("button", name="发送", exact=True).click()
                expect_page(page.locator(".pro-page .summary")).to_be_visible(timeout=300_000)
                expect_page(page.locator(".claim-list .claim-select").first).to_be_visible(timeout=30_000)

                conversation_id = page.evaluate("localStorage.getItem('devicetruth.conversation-id')")
                first = expect(product.request("GET", f"/api/conversations/{conversation_id}"), 200)
                assert len(first["runs"]) == 1, first["runs"]
                run1 = first["runs"][0]
                assert run1["status"] == "COMPLETED" and run1["result"], run1["status"]
                claims = run1["result"]["claims"]
                claim_refs = [rid for claim in claims for rid in claim["referenceIds"]]
                run1_refs = expect(product.request("GET", f"/api/runs/{run1['runId']}/references"), 200)
                assert claim_refs, "首轮判断没有可继承引用，无法验证继承分支"
                evidence["run1"] = {"runId": run1["runId"], "category": run1["result"]["category"],
                                    "claims": len(claims), "references": len(run1_refs)}

                # 2) 选择判断只切 UI：点击前后 Run 数量不变（零提交）
                page.locator(".claim-list .claim-select").nth(1 if len(claims) > 1 else 0).click()
                after_select = expect(product.request("GET", f"/api/conversations/{conversation_id}"), 200)
                assert len(after_select["runs"]) == 1
                expect_page(page.locator(".pro-footer .hint", has_text="将继承").or_(
                    page.locator(".pro-footer .hint", has_text="仍可直接深入调查"))).to_be_visible()
                evidence["selectionZeroSubmit"] = True

                # 3) 深入调查：同会话新 Run、继承引用、新结果展示
                page.get_by_role("button", name="深入调查选中判断").click()
                expect_page(page.locator(".run-chip")).to_have_count(2, timeout=300_000)
                expect_page(page.locator(".pro-page .summary")).to_be_visible(timeout=300_000)
                second = expect(product.request("GET", f"/api/conversations/{conversation_id}"), 200)
                assert len(second["runs"]) == 2, [r["status"] for r in second["runs"]]
                run2 = second["runs"][1]
                assert run2["runId"] != run1["runId"]
                assert run2["status"] == "COMPLETED" and run2["result"], run2["status"]
                assert run2["goalMessageId"] != run1["goalMessageId"]
                run2_refs = expect(product.request("GET", f"/api/runs/{run2['runId']}/references"), 200)
                run1_ids = {ref["referenceId"] for ref in run1_refs}
                run2_ids = {ref["referenceId"] for ref in run2_refs}
                run1_sources = {ref["sourceId"] for ref in run1_refs}
                inherited = [ref for ref in run2_refs if ref["sourceId"] in run1_sources]
                assert inherited, "新 Run 没有继承任何已授权来源引用"
                assert not (run2_ids & run1_ids), "新 Run 复用了旧引用 ID"
                evidence["run2"] = {"runId": run2["runId"], "references": len(run2_refs),
                                    "inheritedSources": len({ref['sourceId'] for ref in inherited}),
                                    "newReferenceIds": True}

                # 4) 旧轮回看：切回第 1 轮仍展示旧摘要
                page.locator(".run-chip").first.click()
                expect_page(page.locator(".pro-page .summary")).to_have_text(run1["result"]["summary"])
                evidence["oldRoundViewable"] = True

                # 5) 普通现场补充：作为用户报告开启第 3 个 Run（无候选也不伪造，入口始终可用）。
                # 真模可能在本轮发出 ASK_USER：两种终局都要验收——COMPLETED 直接断言；
                # WAITING_FOR_USER 则继续完成证据 6（三元组补答恢复同一 Run）。
                page.locator(".composer textarea").fill(field_note)
                page.get_by_role("button", name="发送", exact=True).click()
                expect_page(page.locator(".run-chip")).to_have_count(3, timeout=300_000)
                expect_page(page.locator(".pro-page .summary, .question h2")).to_be_visible(timeout=300_000)
                third = expect(product.request("GET", f"/api/conversations/{conversation_id}"), 200)
                assert len(third["runs"]) == 3
                run3 = third["runs"][2]
                assert run3["runId"] not in {run1["runId"], run2["runId"]}
                goal = [m for m in third["messages"] if m["messageId"] == run3["goalMessageId"]]
                assert goal and field_note in goal[0]["content"], "现场补充未作为用户报告进入新 Run"
                evidence["run3"] = {"runId": run3["runId"], "fieldNoteAsUserReport": True}

                # 6) ASK_USER：真模触发时完成三元组补答验收；恢复同一 Run 且轮数不增加。
                if run3["status"] == "WAITING_FOR_USER":
                    evidence["askUser"] = "REAL_MODEL_TRIGGERED"
                    expect_page(page.get_by_role("heading", name="需要补充信息")).to_be_visible()
                    answer = "VIN：DEMO-EV-004；充电桩编号：PILE-12（现场抄录）。"
                    page.locator(".question textarea").fill(answer)
                    page.get_by_role("button", name="提交补充信息").click()
                    expect_page(page.locator(".pro-page .summary")).to_be_visible(timeout=300_000)
                    answered = expect(product.request("GET", f"/api/conversations/{conversation_id}"), 200)
                    assert len(answered["runs"]) == 3, "补答不得新增调查轮次"
                    resumed = answered["runs"][2]
                    assert resumed["runId"] == run3["runId"], "补答必须沿同一 Run 恢复"
                    assert resumed["status"] == "COMPLETED" and resumed["result"], resumed["status"]
                    assert resumed["pendingQuestion"] is None
                    evidence["askUserReply"] = {"sameRunId": True, "runCountUnchanged": True,
                                                "completedAfterReply": True}
                else:
                    assert run3["status"] == "COMPLETED" and run3["result"], run3["status"]
                    evidence["askUser"] = "NOT_TRIGGERED_PROTOCOL_SURROGATE"
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
    parser.add_argument("--embedding", default=os.environ.get("DEVICETRUTH_D4_EMBEDDING", "deterministic"),
                        choices=["real", "deterministic"])
    args = parser.parse_args()
    for name in ("DEVICETRUTH_TEST_PG_BASE_URL", "DEVICETRUTH_TEST_PG_USERNAME",
                 "DEVICETRUTH_TEST_PG_PASSWORD", "DEVICETRUTH_EVAL_DATABASE"):
        if not os.environ.get(name):
            raise RuntimeError(f"BLOCKED_BY_ENV: {name} is required")
    if not os.environ.get("OPENAI_API_KEY"):
        raise RuntimeError("BLOCKED_BY_ENV: OPENAI_API_KEY is required")
    output = ROOT / "outputs" / "validation" / f"investigation-roundtrip-{uuid.uuid4().hex[:8]}"
    output.mkdir(parents=True)
    result = verify(args.browser_path, args.reasoner, args.embedding, output)
    (output / "result.json").write_text(json.dumps(result, indent=2), encoding="utf-8")
    print(json.dumps(result | {"result": str(output / "result.json")}, ensure_ascii=False))


if __name__ == "__main__":
    main()
