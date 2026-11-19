"""专业工作台交互验收（协议替身，无 PostgreSQL 时的补充验证）。

用途：用同目录 pro_workbench_fixture.py 模拟 HTTP 合同，
驱动构建后的 frontend/dist，核对 R0-A 的页面交互断言（查看轮/活动轮、来源抽屉、追问、许可条件、布局）。

边界：
- 协议替身只证明前端按合同消费数据、交互与状态绑定正确；
- 不代表真实服务端持久化、授权、SSE 回放或模型能力（这些由 verify_browser_recovery.py、
  verify_sse_browser.py 与 Java 测试在真实 PG 环境验证）。
- 替身对 /skill、/stream 返回 404，属于替代实现的预期限制。

用法：
    python scripts/verify_pro_workbench_mock.py
"""

import json
import sys
from copy import deepcopy
from http.server import ThreadingHTTPServer
from threading import Thread
from uuid import uuid4

from playwright.sync_api import sync_playwright, expect
from pro_workbench_fixture import CONVERSATIONS, Handler, ROOT, RUN1, WAITING_QUESTION, message

server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
Thread(target=server.serve_forever, daemon=True).start()
BASE = f"http://127.0.0.1:{server.server_port}"
OUT = ROOT / "outputs" / "validation" / f"pro-workbench-mock-{uuid4().hex[:8]}"
OUT.mkdir(parents=True)

errors = []
passed = []


def check(condition, label):
    print(("PASS " if condition else "FAIL ") + label)
    if not condition:
        errors.append(label)
    else:
        passed.append(label)
    return condition


def control(page, references):
    page.evaluate("""async (refs) => {
        await fetch('/_control', {method:'POST', headers:{'Content-Type':'application/json'},
            body: JSON.stringify({references: refs})});
    }""", references)


def open_with(page, conversation_id):
    page.goto(BASE, wait_until="networkidle")
    page.evaluate(f'window.localStorage.setItem("devicetruth.conversation-id", "{conversation_id}")')
    page.reload(wait_until="networkidle")
    page.wait_for_timeout(1200)


def api_calls(page):
    return page.evaluate("""async () => {
        const r = await fetch('/_calls'); return await r.json();
    }""")


def clear_calls(page):
    page.evaluate("""async () => {
        await fetch('/_reset');
    }""")


def view_run(page, index):
    page.get_by_role("button", name="查看调查记录").click()
    page.locator(".run-chip").nth(index).click()


def emit_frame(page, run_id, event_type, payload):
    page.evaluate("""([runId, kind, data]) => {
        const stream = streams.findLast(item => item.url.includes('/' + runId + '/stream') && item.readyState === 1);
        if (!stream) throw Error('No active stream for ' + runId);
        stream.dispatchEvent(new MessageEvent(kind, {data: JSON.stringify(data)}));
    }""", [run_id, event_type, payload])


with sync_playwright() as p:
    browser = p.chromium.launch(channel="chrome", headless=True)
    page = browser.new_page(viewport={"width": 1440, "height": 900})
    page.add_init_script("""window.streams = [];
        window.EventSource = class extends EventTarget {
            constructor(url) { super(); this.url = url; this.readyState = 1; window.streams.push(this); }
            close() { this.readyState = 2; }
        };""")
    console_errors = []
    page.on("pageerror", lambda exc: console_errors.append(str(exc)))
    # 协议替身对 /skill（未选择）与 /stream（无 SSE）返回 404，属替代实现的预期限制；
    # 只把页面脚本异常与其它控制台错误计入失败。
    def on_console(msg):
        if msg.type != "error":
            return
        text = msg.text
        if "Failed to load resource" in text:
            return
        console_errors.append(text)
    page.on("console", on_console)

    # ---------- 5.3 桌面常驻近期调查 ----------
    open_with(page, "conv-1")
    sidebar = page.locator(".pro-sidebar")
    check(sidebar.is_visible(), "5.3 桌面侧栏常驻可见")
    links = page.locator(".pro-sidebar .conversation-link")
    check(links.count() == 3, f"5.3 近期调查列出全部会话（{links.count()}）")
    order = page.evaluate("[...document.querySelectorAll('.pro-sidebar .conversation-link')].map(e => e.dataset.conversationId)")
    check(order == ["conv-waiting", "conv-1", "conv-2"], f"5.3 按更新时间倒序 {order}")
    check(page.locator(".pro-sidebar .conversation-link.active").count() == 1, "5.3 当前会话有选中标记")
    check(page.get_by_role("button", name="新调查").first.is_visible(), "5.3 新调查入口可见")
    check(page.locator(".pro-drawer").count() == 0, "5.3 无需打开记录抽屉即可看到近期调查")

    # ---------- 1.1/1.2 查看轮与活动轮 ----------
    viewed = page.locator(".pro-task-card")
    check("请生成报告" in viewed.inner_text(), "1.2 默认查看服务端排序末位的轮次")
    # 抽屉选第 1 轮（资产 A/T1），资产快照与来源跟随
    page.get_by_role("button", name="查看调查记录").click()
    page.wait_for_timeout(300)
    page.locator(".run-chip").first.click()
    page.wait_for_timeout(800)
    check("请调查原因" in page.locator(".pro-task-card").inner_text(), "1.2 选第 1 轮后目标卡显示该轮目标消息")
    check("资产 A 时点 T1" in page.locator(".detail-text").inner_text(), "1.2 选第 1 轮后显示该轮判断原文")
    check(page.locator("[data-testid='viewed-status']").inner_text().strip().endswith("所看第 1 轮")
          or "第 1 轮" in page.locator("[data-testid='viewed-status']").inner_text(), "1.1 顶栏标明所看第 1 轮")
    # 输入下拉改资产不影响历史资产
    page.select_option("#asset", "CAR-B")
    page.wait_for_timeout(300)
    check(page.locator("[data-testid='viewed-asset']").inner_text().strip() == "SC0245B",
          "1.2 输入下拉资产不影响所看轮资产")
    # ---------- 5.1/5.2 导航短预览、完整结果 ----------
    nav = page.locator(".pro-nav")
    nav_text = nav.inner_text()
    check("资料支持" in nav_text, "5.1 导航保留 certainty 类别")
    previews = page.evaluate("[...document.querySelectorAll('.claim-preview')].map(e => e.textContent)")
    full_first = previews[0] if previews else ""
    check("测试标记" not in full_first, f"5.1 导航短预览不含被截断的尾部 {previews}")
    detail = page.locator(".claim-detail .detail-text").inner_text()
    check("需要读取日志并核对绝缘检测记录后再决定处理" in detail and "测试标记" in detail,
          "5.1 中央焦点保留原文（未截断）")
    check(page.locator(".pro-sources").is_visible() and not page.locator("dialog.evidence-modal").is_visible(),
          "V0.3 常驻轻量证据上下文，详情按需打开")
    check("风险 HIGH" in page.locator(".pro-task-card").inner_text(), "5.2 风险等级默认可见")
    full = page.locator("[data-testid='full-result']")
    check(full.count() == 0, "5.2 完整结果默认收起")
    clear_calls(page)
    page.locator("button.claim-select").nth(1).click()
    page.wait_for_timeout(200)
    check(page.locator("button.claim-select").nth(1).get_attribute("aria-pressed") == "true", "5.1 切换焦点生效")
    calls = api_calls(page)
    check(not [c for c in calls if c.startswith("POST /api")], "5.1 切换焦点零 POST")
    page.get_by_role("button", name="展开完整结果").click()
    page.wait_for_timeout(200)
    full_text = page.locator("[data-testid='full-result']").inner_text()
    check("严禁在未断电情况下拆卸高压连接器" in full_text, "5.2 完整结果含限制原文")
    check(page.get_by_role("button", name="收起完整结果").is_visible(), "5.2 可再次收起")

    # ---------- 4.1/4.2 来源抽屉 ----------
    sources = page.locator("[data-testid='registered-sources']")
    check(sources.is_visible(), "4.1 完成轮显示本轮来源入口")
    check("该焦点直接引用（1）" in sources.inner_text(), "4.1 切换焦点后仅标记该判断的直接引用")
    check("本轮其他已登记来源" in sources.inner_text(), "4.1 区分全部登记来源")
    page.locator("[data-evidence-id='ref-a']").first.click()
    page.wait_for_timeout(400)
    dialog = page.locator("dialog.evidence-modal")
    check(dialog.is_visible(), "4.2 证据模态抽屉打开")
    dtext = dialog.inner_text()
    for token in ["来源类型", "来源 ID", "来源版本", "数据性质", "关键内容摘录"]:
        check(token in dtext, f"4.2 抽屉字段存在：{token}")
    check("章节：4.2 直流快充中断" in dtext, "4.2 缺页码显示章节")
    focused_in = page.evaluate("document.activeElement && document.activeElement.closest('dialog') !== null")
    check(focused_in, "4.2 打开时焦点进入对话框")
    page.keyboard.press("Shift+Tab")
    tab_in = page.evaluate("document.activeElement && document.activeElement.closest('dialog') !== null")
    check(tab_in, "4.2 Shift+Tab 仍留在对话框")
    for _ in range(6):
        page.keyboard.press("Tab")
    page.keyboard.press("Escape")
    page.wait_for_timeout(300)
    check(page.locator("dialog.evidence-modal").count() == 0 or not page.locator("dialog.evidence-modal").is_visible(),
          "4.2 Esc 关闭抽屉")
    back = page.evaluate("document.activeElement && document.activeElement.getAttribute('data-evidence-id')")
    check(back == "ref-a", f"4.2 关闭后焦点回到触发项（{back}）")
    page.screenshot(path=str(OUT / "desktop-1440.png"), full_page=True)
    page.get_by_role("button", name="收起完整结果").click()
    page.locator("button.claim-select").first.click()
    page.select_option("#asset", "")
    for width, height in ((1600, 1000), (1366, 768), (1360, 768), (390, 844)):
        page.set_viewport_size({"width": width, "height": height})
        page.evaluate("scrollTo(0, 0)")
        layout = page.evaluate("""() => {
            const rect = sel => document.querySelector(sel).getBoundingClientRect().toJSON();
            return {nav: rect('.pro-nav'), center: rect('.pro-center'),
                    sources: rect('.pro-sources'), composer: rect('.pro-action-wrap'),
                    overflow: document.documentElement.scrollWidth > innerWidth};
        }""")
        check(not layout["overflow"], f"V0.3 {width}px 页面无横向溢出")
        check(abs(layout["center"]["x"] - layout["composer"]["x"]) < 1, f"V0.3 {width}px 输入对齐焦点")
        if width > 1360:
            check(layout["sources"]["x"] > layout["center"]["x"] > layout["nav"]["x"]
                  and layout["sources"]["y"] == layout["nav"]["y"], f"V0.3 {width}px 三列靠顶")
        else:
            check(layout["sources"]["y"] >= layout["center"]["bottom"], f"V0.3 {width}px 证据下移")
        if width != 1360:
            page.screenshot(path=str(OUT / f"pro-{width}x{height}.png"))
        if width == 1600:
            page.screenshot(path=str(OUT / "pro-1600x1000-full.png"), full_page=True)
        if width == 390:
            page.screenshot(path=str(OUT / "pro-390x844-full.png"), full_page=True)
    page.set_viewport_size({"width": 1440, "height": 900})
    clear_calls(page)
    page.get_by_role("button", name="深入调查", exact=True).click()
    composer = page.locator(".composer textarea")
    check("需要读取日志" in composer.input_value(), "V0.3 深入调查预填当前焦点")
    check(page.locator('input[type=file]').is_disabled(), "V0.3 深入模式不静默发送附件")
    page.get_by_role("button", name="补充现场观察", exact=True).click()
    composer.fill("记录了两次中断，具体原因尚不明确")
    composer.press("Shift+Enter")
    check(composer.input_value().endswith("\n"), "V0.3 Shift+Enter 保留换行")
    check(not any(call.startswith("POST /api") for call in api_calls(page)), "V0.3 切模式和换行均不提交")
    request_payloads = []

    def capture_submission(route):
        request_payloads.append(route.request.post_data_json)
        route.fulfill(status=409, json={"code": "TEST_RESPONSE", "message": "保留草稿"})

    page.route("**/api/conversations/*/messages", capture_submission)
    composer.press("Enter")
    expect(page.get_by_role("alert").filter(has_text="TEST_RESPONSE")).to_be_visible()
    check(request_payloads[0]["userMessage"].startswith("现场观察（用户报告，未经核实）：")
          and "contextRefs" not in request_payloads[0] and "replyTo" not in request_payloads[0],
          "V0.3 观察只按未核实用户消息提交")
    page.get_by_role("button", name="深入调查", exact=True).click()
    composer.fill(composer.input_value() + "；继续核对记录")
    page.get_by_role("button", name="发送", exact=True).click()
    expect(page.get_by_role("alert").filter(has_text="TEST_RESPONSE")).to_be_visible()
    check(request_payloads[1]["userMessage"].endswith("继续核对记录")
          and request_payloads[1]["selectedAssetId"] == "SC0245B"
          and request_payloads[1]["contextRefs"], "V0.3 深入发送可编辑文本和所看轮资产、引用")
    page.unroute("**/api/conversations/*/messages", capture_submission)
    page.get_by_role("button", name="自由提问", exact=True).click()
    page.locator('input[type=file]').set_input_files({"name": "bad.pdf", "mimeType": "application/pdf", "buffer": b"x"})
    expect(page.get_by_role("alert").filter(has_text=".md")).to_be_visible()
    check(not any(call.startswith("POST /api/files") for call in api_calls(page)), "V0.3 拒绝不支持文件且不发上传")

    control(page, {'run-2': 'denied'})
    page.get_by_role("button", name="查看调查记录").click()
    page.locator(".run-chip").nth(1).click()
    expect(sources.get_by_text("无权访问本轮已登记来源。")).to_be_visible()
    check(page.locator("[data-evidence-id]").count() == 0, "4.1 成功轮读取 403 后所有来源入口均消失")

    # ---------- 4.1 读取失败/拒绝/404 ----------
    control(page, {'run-3': 'error'})
    page.get_by_role("button", name="查看调查记录").click()
    page.locator(".run-chip").nth(2).click()
    page.wait_for_timeout(900)
    body = page.locator("main").inner_text()
    check("本次没有得到答案" in body, "4.1 失败轮安全卡片")
    check("重试读取来源" in body, "4.1 读取失败给出重试入口")
    control(page, {'run-4': 'merged'})
    page.get_by_role("button", name="查看调查记录").click()
    page.wait_for_timeout(300)
    page.locator(".run-chip").nth(3).click(force=True)
    page.wait_for_timeout(1200)
    body = page.locator("main").inner_text()
    check("本次调查已取消" in body, "4.1 取消轮可辨认")
    check("来源不可用或无权访问" in body, "4.1 合并 404 给出来源不可用提示")
    check(page.locator("[data-testid='registered-sources'] .citation-ref").count() == 0,
          "4.1 被拒绝时不显示旧轮来源内容")
    # ---------- 1.3 许可条件（存在未决轮时） ----------
    open_with(page, "conv-waiting")
    page.wait_for_timeout(500)
    check(page.get_by_role("button", name="提交补充信息").is_visible(), "2.1 待答区可见")
    check(page.get_by_role("button", name="深入调查").is_disabled(), "1.3 待答轮时深入调查禁用")
    check(page.get_by_role("button", name="发送").is_disabled(), "1.3 待答时普通发送不可用")
    check(page.get_by_role("button", name="取消调查").is_visible(), "1.3 有活动轮时可取消")
    page.locator(".question textarea").fill("补充：直流快充，SOC 40%")
    page.locator(".question textarea").press("Enter")
    page.wait_for_timeout(600)
    calls = api_calls(page)
    reply_call = [c for c in calls if "POST /api/conversations/conv-waiting/messages" in c]
    check(reply_call and '"replyTo"' in reply_call[-1], "2.2 补答携带 replyTo 三元组")
    check(reply_call and '"questionVersion": 2' in reply_call[-1], "2.2 补答使用当前问题版本")
    normal = [c for c in reply_call if '"userMessage"' in c and '"replyTo"' not in c]
    check(not normal, "2.2 不会落入普通发送")

    # ---------- 5.3 切换会话 ----------
    page.locator(".conversation-link[data-conversation-id='conv-2']").click()
    page.wait_for_timeout(1000)
    check("会话二的核对" in page.locator(".pro-task-card").inner_text() or
          "会话二的核对" in page.locator(".pro-focus").inner_text(), "5.3 切换会话后任务卡更新")
    check(page.locator(".conversation-link[data-conversation-id='conv-2']").first.get_attribute("aria-current") == "true",
          "5.3 新会话标为当前")

    # ---------- 5.4 窄屏 ----------
    for width, height, name in [(1000, 800, "tablet-1000"), (390, 844, "mobile-390")]:
        page.set_viewport_size({"width": width, "height": height})
        page.wait_for_timeout(400)
        overflow = page.evaluate("document.documentElement.scrollWidth > window.innerWidth + 1")
        check(not overflow, f"5.4 {name} 无页面级横向溢出")
        check(not page.locator(".pro-sidebar").is_visible(), f"5.4 {name} 侧栏收起")
        check(page.get_by_role("button", name="查看调查记录").is_visible(), f"5.4 {name} 顶部记录入口可见")
        page.screenshot(path=str(OUT / f"{name}.png"), full_page=True)
        page.get_by_role("button", name="查看调查记录").click()
        list_view = page.get_by_role("navigation", name="近期调查列表")
        expect(list_view).to_be_visible()
        current = list_view.locator("[aria-current=true]")
        check(current.count() == 1, f"5.3 {name} 标明当前会话")
        target = "conv-1" if width == 1000 else "conv-2"
        list_view.locator(f"[data-conversation-id='{target}']").click()
        expect(page.locator(".pro-drawer")).to_have_count(0)
        check(page.evaluate("localStorage.getItem('devicetruth.conversation-id')") == target,
              f"5.3 {name} 从记录入口切换近期会话")
    page.set_viewport_size({"width": 1440, "height": 900})
    cid = "conv-waiting"
    baseline = deepcopy(CONVERSATIONS[cid])
    prior = deepcopy(RUN1) | {"conversationId": cid, "goalMessageId": "prior"}
    baseline["runs"].insert(0, prior)
    baseline["messages"].insert(0, message("prior", 0, "USER", "已完成的历史调查", "SC0245B"))
    pending = baseline["runs"][-1]
    scenario = deepcopy(baseline)
    scenario["runs"][-1].update(status="RUNNING", version=1, pendingQuestion=None)
    scenario["messages"].pop()
    scenario["conversation"]["version"] = 1
    conversation_path = "**/api/conversations/" + cid
    run_path = "**/api/runs/run-w"
    page.route(conversation_path, lambda route: route.fulfill(json=scenario))
    reads = {"count": 0}
    durable = deepcopy(scenario["runs"][-1])

    def read_durable(route):
        reads["count"] += 1
        route.fulfill(json=durable)

    page.route(run_path, read_durable)
    open_with(page, cid)
    page.wait_for_function("streams.some(stream => stream.url.includes('/run-w/stream'))")
    view_run(page, 0)
    check(page.evaluate("streams.filter(stream => stream.readyState === 1).map(stream => stream.url)")
          == ["/api/runs/run-w/stream"], "3.1 浏览历史时仍订阅活动轮，不订阅历史轮")
    active_stream = page.evaluate("streams.length")
    durable.update(pending)
    scenario.update(deepcopy(baseline))
    notice = {"seq": 8, "type": "RUN_WAITING", "recordedAt": pending["requestedAt"], "data": {}}
    emit_frame(page, "run-w", "RUN_WAITING", notice)
    expect(page.get_by_test_id("pending-question")).to_contain_text(WAITING_QUESTION["question"])
    check(page.get_by_test_id("viewed-status").inner_text().endswith("所看第 1 轮"),
          "3.1 等待回读后保留历史选择")
    previous_reads = reads["count"]
    emit_frame(page, "run-w", "RUN_WAITING", notice)
    page.wait_for_timeout(250)
    check(reads["count"] == previous_reads and page.evaluate("streams.length") == active_stream,
          "3.1 重复 seq 不重读、不重开流")
    scenario["conversation"]["version"] = 1
    scenario["runs"][-1].update(status="RUNNING", version=1, pendingQuestion=None)
    scenario["messages"].pop()
    with page.expect_response(lambda response: response.url.endswith("/api/conversations/" + cid)):
        page.get_by_role("button", name="刷新", exact=True).click()
    check(page.get_by_test_id("pending-question").is_visible(), "3.2 旧会话快照不覆盖已知待答")
    page.get_by_role("button", name="查看调查记录").click()
    check(page.locator(".pro-drawer-body").get_by_text(WAITING_QUESTION["question"], exact=True).is_visible(),
          "3.2 旧会话快照不删除已显示消息")
    page.get_by_role("button", name="关闭调查记录").click()

    for terminal in ("COMPLETED", "FAILED", "CANCELLED"):
        if terminal != "COMPLETED":
            scenario.update(deepcopy(baseline))
            durable.update(pending)
            open_with(page, cid)
            page.wait_for_function("streams.some(stream => stream.url.includes('/run-w/stream'))")
            view_run(page, 0)
        durable.update(status=terminal, version=5, pendingQuestion=None)
        scenario["runs"][-1] = deepcopy(durable)
        scenario["conversation"]["version"] = 5
        emit_frame(page, "run-w", "RUN_TERMINAL", {"status": terminal})
        expect(page.get_by_test_id("active-status")).to_have_count(0)
        check(page.get_by_test_id("viewed-status").inner_text().endswith("所看第 1 轮"),
              f"3.1 活动轮 {terminal} 回读后保留历史")

    scenario.update(deepcopy(baseline))
    durable.update(pending)
    second = deepcopy(pending) | {"runId": "run-w-other"}
    second["pendingQuestion"].update(pendingQuestionId="q-other", question="第二轮独立问题")
    scenario["runs"].append(second)
    open_with(page, cid)
    expect(page.get_by_test_id("multiple-active")).to_be_visible()
    view_run(page, 2)
    expect(page.get_by_test_id("pending-question")).to_contain_text("第二轮独立问题")
    check(page.get_by_role("button", name="取消调查").is_enabled(),
          "1.1 多未决轮明确选择后可取消目标轮")
    field = page.get_by_label("补充信息", exact=True)
    field.fill("待保留的现场陈述")

    def reject_stale(route):
        second["version"] += 1
        second["pendingQuestion"].update(questionVersion=3, question="第二轮更新后的问题")
        scenario["conversation"]["version"] += 1
        route.fulfill(status=409, json={"code": "STALE_OR_UNRELATED_REPLY", "message": "追问已变化"})

    page.route("**/api/conversations/" + cid + "/messages", reject_stale)
    with page.expect_request(lambda request: request.method == "POST" and request.url.endswith("/messages")) as sent:
        field.press("Enter")
    check(sent.value.post_data_json.get("replyTo") == {
        "runId": "run-w-other", "pendingQuestionId": "q-other", "questionVersion": 2
    }, "2.2 多待答按显式所选轮发送三元组")
    expect(page.get_by_test_id("pending-question")).to_contain_text("第二轮更新后的问题")
    check(field.input_value() == "待保留的现场陈述", "2.2 过期后回读新问题并保留草稿")
    check(page.get_by_role("button", name="发送", exact=True).is_disabled(), "1.3 多未决时不允许普通发送")
    page.route("**/api/runs/run-w-other/cancel", lambda route: route.fulfill(
        status=409, json={"code": "RUN_STATE_CONFLICT", "message": "状态已变化"}))
    with page.expect_request(lambda request: request.method == "POST" and request.url.endswith("/cancel")) as cancelled:
        page.get_by_role("button", name="取消调查").click()
    check(cancelled.value.url.endswith("/run-w-other/cancel"), "1.3 明确只对所选未决轮发起取消")
    expect(page.locator(".error")).to_contain_text("回读最新状态")
    control(page, {"run-3": "ready", "run-4": "ready"})
    open_with(page, "conv-1")
    page.set_viewport_size({"width": 390, "height": 844})
    for index, state, evidence_id in ((2, "failed", "ref-b"), (3, "cancelled", "ref-c")):
        view_run(page, index)
        check(page.locator(f".pro-page [data-state='{state}']").is_visible(), f"4.2 窄屏 {state} 状态可辨认")
        opener = page.locator(f"[data-evidence-id='{evidence_id}']").first
        opener.click()
        expect(page.get_by_role("dialog", name="证据详情")).to_be_visible()
        check(page.locator(".modal-excerpt p").is_visible(), f"4.2 窄屏可读 {state} 轮的登记来源")
        page.get_by_role("button", name="关闭证据抽屉").click()
        expect(opener).to_be_focused()
    page.get_by_role("button", name="查看调查记录").click()
    page.screenshot(path=str(OUT / "mobile-390-records.png"), full_page=True)
    check(not console_errors, f"无控制台错误 {console_errors[:3]}")
    browser.close()

server.shutdown()
server.server_close()
result = {"passed": len(passed), "failed": len(errors), "errors": errors, "evidenceDir": str(OUT)}
(OUT / "result.json").write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
print(json.dumps(result, ensure_ascii=False))
sys.exit(1 if errors else 0)
