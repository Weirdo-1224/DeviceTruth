"""05-01 SSE + run-timeline browser acceptance: packaged server + real PostgreSQL + system Chrome.

Scenarios (playwright drives frontend/dist through a local static server whose /api proxy
streams SSE chunk-by-chunk, forwards Last-Event-ID, and can sever all in-flight SSE upstreams):
a) submit a question -> timeline events appear live via SSE; reply on the page -> 答案 event
   arrives and RUN_TERMINAL closes the timeline;
b) disconnect: sever the live SSE connection at the proxy and abort reconnects while the run is
   waiting, send the reply via API so the server keeps writing events, then release the route ->
   EventSource reconnects with Last-Event-ID=k and the page backfills the missed events in order,
   exactly once each (native EventSource resume, no page reload);
c) while the old run is still waiting, switch to a fresh conversation -> the page closes the old
   subscription; the old run then completes server-side, and its late events must not add
   anything to the new conversation's page (no new items, no answer badge, no errors);
d) run-less conversation cleanup (re-verify §8.6): entering a fresh conversation without any run
   clears the old timeline immediately; late events of the old run (advanced via API afterwards)
   must not appear on the new conversation's page;
e) permanent failure: return HTTP 409 for the stream while the run completes via API.
   The page clears its live badge and stops requesting the stream. Reload restores history.

Evidence (console, network incl. Last-Event-ID headers, timeline snapshots, screenshots)
lands under outputs/validation/sse-browser-<hex>/.
Requires JAVA_HOME pointing at JDK 21 and DEVICETRUTH_TEST_PG_* credentials.
"""
import json
import socket
import sys
import threading
import time
import uuid
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from threading import Thread
import urllib.error
import urllib.request

from verify_general_agent import ROOT, Server, expect, import_manual

QUESTION = "新能源汽车充电中断，没有故障码，应该先查什么资料？"
REPLY = "直流快充，SOC 约 40%"
FRONTEND = ROOT / "frontend" / "dist"
UI_URL = ""


class ConnectionDropper:
    """记录在途 SSE（浏览器连接, 上游响应），可一键全部断开（等价前端代理把活跃连接打断）。"""

    def __init__(self):
        self._lock = threading.Lock()
        self._entries = []

    def register(self, connection, upstream):
        with self._lock:
            self._entries.append((connection, upstream))

    def unregister(self, connection, upstream):
        with self._lock:
            self._entries = [e for e in self._entries if e != (connection, upstream)]

    def drop_all(self):
        with self._lock:
            snapshot = list(self._entries)
        for connection, upstream in snapshot:
            for target, action in ((connection, socket.SHUT_RDWR), (upstream, None)):
                try:
                    if action is not None:
                        target.shutdown(action)
                    target.close()
                except Exception:
                    pass
        return len(snapshot)


CONNECTIONS = ConnectionDropper()
PROXY_STREAM_LOG = []


def wait_until(predicate, timeout_ms, desc):
    deadline = time.time() + timeout_ms / 1000
    while time.time() < deadline:
        value = predicate()
        if value:
            return value
        time.sleep(0.2)
    raise RuntimeError(f"wait_until timeout: {desc}")


class Evidence:
    def __init__(self):
        self.console = []
        self.network = []
        self.pageerrors = []
        self.timelines = {}
        self.tag = "-"

    def set_tag(self, tag):
        self.tag = tag

    def attach(self, page):
        page.on("console", lambda msg: self.console.append(
            f"[{self.tag}] {time.strftime('%H:%M:%S')} {msg.type}: {msg.text}"))
        page.on("pageerror", lambda err: self.pageerrors.append(f"[{self.tag}] {err}"))
        page.on("request", lambda req: self.network.append({
            "tag": self.tag, "ts": time.strftime("%H:%M:%S"), "kind": "request",
            "method": req.method, "url": req.url,
            "lastEventId": req.headers.get("last-event-id"),
        }) if "/stream" in req.url or "/events" in req.url else None)
        page.on("response", lambda resp: self.network.append({
            "tag": self.tag, "ts": time.strftime("%H:%M:%S"), "kind": "response",
            "status": resp.status, "url": resp.url,
        }) if "/stream" in resp.url or "/events" in resp.url else None)

    def snapshot_timeline(self, page, name):
        items = page.locator(".timeline-item")
        data = []
        for i in range(items.count()):
            item = items.nth(i)
            data.append({
                "seq": item.locator(".seq").inner_text(),
                "badge": item.locator(".badge").inner_text(),
                "detail": item.locator(".detail").inner_text(),
            })
        state = {
            "items": data,
            "liveBadge": page.locator(".timeline .live-badge").count() > 0,
            "endedBadge": page.locator(".timeline .ended-badge").count() > 0,
        }
        self.timelines[name] = state
        return state

    def stream_requests(self):
        return [n for n in self.network if n["kind"] == "request" and "/stream" in n["url"]]


def timeline_seqs(state):
    return [int(item["seq"].lstrip("#")) for item in state["items"]]


def assert_seqs_one_to_n(state, n, desc):
    seqs = timeline_seqs(state)
    if seqs != list(range(1, n + 1)):
        raise RuntimeError(f"{desc}: 页面时间线 seq 应为 1..{n}，实际 {seqs}")
    return seqs


def serve_frontend(product, proxy_log):
    """frontend/dist 静态服务 + /api 流式代理（SSE 必须逐块转发，缓冲转发会破坏实时性）；
    代理侧记录每个 /stream 请求的 Last-Event-ID（浏览器真实发出的续传游标）。"""

    class Frontend(SimpleHTTPRequestHandler):
        def __init__(self, *args, **kwargs):
            super().__init__(*args, directory=str(FRONTEND), **kwargs)

        def do_GET(self):
            if self.path.startswith("/api/"):
                self.proxy(stream=True)
            else:
                super().do_GET()

        def do_POST(self):
            self.proxy(stream=False)

        def log_message(self, *_):
            pass

        def proxy(self, stream):
            length = int(self.headers.get("Content-Length", "0"))
            headers = {key: value for key, value in self.headers.items()
                       if key.lower() in {"accept", "content-type", "idempotency-key", "last-event-id"}}
            if self.path.endswith("/stream"):
                proxy_log.append({
                    "ts": time.strftime("%H:%M:%S"),
                    "path": self.path,
                    "lastEventId": self.headers.get("Last-Event-ID"),
                })
            request = urllib.request.Request(
                product.base + self.path,
                data=self.rfile.read(length) if length else None,
                headers=headers, method=self.command)
            try:
                response = urllib.request.urlopen(request, timeout=None if stream else 60)
            except urllib.error.HTTPError as error:
                response = error
            except (ConnectionResetError, BrokenPipeError, ConnectionAbortedError):
                return
            with response:
                self.send_response(response.code)
                content_type = response.headers.get("Content-Type")
                if content_type:
                    self.send_header("Content-Type", content_type)
                self.end_headers()
                try:
                    if stream:
                        CONNECTIONS.register(self.connection, response)
                        try:
                            while True:
                                chunk = response.read1(8192)
                                if not chunk:
                                    break
                                self.wfile.write(chunk)
                                self.wfile.flush()
                        finally:
                            CONNECTIONS.unregister(self.connection, response)
                    else:
                        self.wfile.write(response.read())
                except Exception:
                    pass

    server = ThreadingHTTPServer(("127.0.0.1", 0), Frontend)
    thread = Thread(target=server.serve_forever, daemon=True)
    thread.start()
    return server, thread


def conversation_id(page):
    return page.evaluate("localStorage.getItem('devicetruth.conversation-id')")


def latest_run(product, page):
    cid = conversation_id(page)
    detail = expect(product.request("GET", f"/api/conversations/{cid}"), 200)
    return cid, detail["runs"][-1]


def run_events(product, run_id):
    return expect(product.request("GET", f"/api/runs/{run_id}/events"), 200)


def reply_via_api(product, cid, run_id, question):
    return expect(product.request("POST", f"/api/conversations/{cid}/messages", {
        "schemaVersion": 1, "userMessage": REPLY,
        "replyTo": {"runId": run_id, "pendingQuestionId": question["pendingQuestionId"],
                    "questionVersion": question["questionVersion"]},
    }, {"Idempotency-Key": str(uuid.uuid4())}), 202)


def await_run_status(product, run_id, statuses, timeout_s=30):
    deadline = time.time() + timeout_s
    while time.time() < deadline:
        run = expect(product.request("GET", f"/api/runs/{run_id}"), 200)
        if run["status"] in statuses:
            return run
        time.sleep(0.3)
    raise RuntimeError(f"run {run_id} 未在 {timeout_s}s 内到达 {statuses}")


def submit_question_and_wait(product, page, evidence, snapshot):
    """当前会话提交问题并等到 WAITING + 时间线出现；返回 (cid, runView, k)。"""
    page.locator(".composer textarea").fill(QUESTION)
    page.get_by_role("button", name="发送", exact=True).click()
    page.get_by_role("heading", name="需要补充信息").wait_for(timeout=30_000)
    cid, run = latest_run(product, page)
    run_id = run["runId"]
    wait_until(lambda: page.locator(".timeline-item").count() >= 2, 15_000, "时间线至少出现 2 条事件")
    state = evidence.snapshot_timeline(page, snapshot)
    k = len(run_events(product, run_id))
    assert_seqs_one_to_n(state, k, snapshot)
    return cid, run, k


def scenario_a(product, page, evidence, output):
    """a) 提交问题后时间线随 SSE 实时出现事件；页面补答后答案事件到达并以 RUN_TERMINAL 收尾。"""
    evidence.set_tag("A")
    page.goto(UI_URL)
    page.locator(".history li button").first.wait_for(timeout=15_000)
    cid, run, k = submit_question_and_wait(product, page, evidence, "A-waiting")
    run_id = run["runId"]
    state = evidence.timelines["A-waiting"]
    if not state["liveBadge"]:
        raise RuntimeError(f"A: 等待阶段时间线应为实时态：{state}")
    if not any(item["badge"] == "排队" for item in state["items"]):
        raise RuntimeError(f"A: 等待阶段应包含排队事件：{state}")
    # 复验 §8.1：等待补充迁移必须产出 RUN_WAITING 持久事件，时间线显示「等待补充」条目
    waiting_items = [item for item in state["items"] if item["badge"] == "等待补充"]
    if not waiting_items:
        raise RuntimeError(f"A: 等待阶段应包含「等待补充」条目（RUN_WAITING）：{state}")
    if not any("等待补充信息" in item["detail"] for item in waiting_items):
        raise RuntimeError(f"A: 「等待补充」条目说明应包含等待补充信息：{waiting_items}")

    # 页面内补答（真实用户路径）：后续事件必须经 SSE 实时追加
    page.locator(".question textarea").fill(REPLY)
    page.get_by_role("button", name="提交补充信息").click()
    page.locator(".answer .summary").wait_for(timeout=30_000)
    page.locator(".timeline .ended-badge").wait_for(timeout=15_000)

    n = len(run_events(product, run_id))
    if n <= k:
        raise RuntimeError(f"A: 补答后事件应增加：断点 {k}，终态 {n}")
    state = evidence.snapshot_timeline(page, "A-final")
    assert_seqs_one_to_n(state, n, "A-final")
    if not state["endedBadge"]:
        raise RuntimeError("A: 收到 RUN_TERMINAL 后时间线应显示已结束")
    if not any(item["badge"] == "答案" for item in state["items"]):
        raise RuntimeError(f"A: 最终时间线应包含答案事件：{state}")
    page.screenshot(path=str(output / "screenshot-A-final.png"), full_page=True)
    return {"status": "PASS", "waitingEvents": k, "finalEvents": n,
            "finalSeqs": timeline_seqs(state)}


def scenario_b(product, page, evidence, output):
    """b) 断线补齐：把在途 SSE 连接从代理侧全部断开，并中止期间的自动重连（等价页面断线），
    断线期间经 API 补答推进 run，恢复后 EventSource 带 Last-Event-ID 重连，
    页面依序补齐且不重复。"""
    evidence.set_tag("B")
    page.get_by_role("button", name="新会话", exact=True).click()
    page.locator(".history li button").nth(0).wait_for(timeout=10_000)
    cid, run, k = submit_question_and_wait(product, page, evidence, "B-waiting")
    run_id = run["runId"]
    # 等浏览器侧的流真正收到过事件（带 id 的历史回放）再断开：EventSource 只有收到过带 id 的事件
    # 才会在重连时携带 Last-Event-ID；若刚建连就断，浏览器没有可续传的游标（本轮实测踩到的时序坑）。
    time.sleep(1.5)

    # 断开：先中止未来所有 /stream 请求（含 EventSource 自动重连），再切断在途连接
    page.route("**/api/runs/*/stream", lambda route: route.abort())
    dropped = CONNECTIONS.drop_all()
    if dropped < 1:
        raise RuntimeError("B: 代理侧未找到在途 SSE 连接，断线场景不成立")

    reply_via_api(product, cid, run_id, run["pendingQuestion"])
    await_run_status(product, run_id, {"COMPLETED"}, timeout_s=30)

    # 断线期间页面时间线冻结在断点 k（在途连接已断、重连被中止）
    time.sleep(2.5)
    frozen = evidence.snapshot_timeline(page, "B-frozen")
    if timeline_seqs(frozen) != list(range(1, k + 1)):
        raise RuntimeError(f"B: 断线期间页面不应收到新事件：{frozen}")
    page.screenshot(path=str(output / "screenshot-B-disconnected.png"), full_page=True)

    # 恢复：放行 /stream，EventSource 自动带 Last-Event-ID=k 重连，服务端补齐，页面去重后恰好 1..N
    page.unroute_all()
    n = len(run_events(product, run_id))

    wait_until(lambda: len(evidence.snapshot_timeline(page, "B-backfill-check")["items"]) >= n,
               40_000, f"B: 重连后补齐到 {n} 条事件")
    page.locator(".timeline .ended-badge").wait_for(timeout=15_000)
    state = evidence.snapshot_timeline(page, "B-final")
    assert_seqs_one_to_n(state, n, "B-final")

    # 重连确实携带 Last-Event-ID=断点 k（代理侧可见浏览器真实请求头；playwright 的
    # request.headers 不暴露 EventSource 内部附带的该头，故以代理侧记录为准）
    reconnects = [r for r in PROXY_STREAM_LOG
                  if run_id in r["path"] and r["lastEventId"] is not None]
    if not reconnects:
        raise RuntimeError("B: 代理侧未观察到携带 Last-Event-ID 的重连请求："
                           + json.dumps([r for r in PROXY_STREAM_LOG if run_id in r["path"]],
                                        ensure_ascii=False))
    last_ids = {r["lastEventId"] for r in reconnects}
    if last_ids != {str(k)}:
        raise RuntimeError(f"B: 重连 Last-Event-ID 应为 {k}，实际 {last_ids}")
    page.screenshot(path=str(output / "screenshot-B-final.png"), full_page=True)
    return {"status": "PASS", "disconnectAt": k, "finalEvents": n, "droppedConnections": dropped,
            "reconnectLastEventIds": sorted(last_ids), "finalSeqs": timeline_seqs(state)}


def scenario_c(product, page, evidence, output):
    """c) 旧 run 等待时切换会话：页面关闭旧订阅；旧 run 迟到事件不得更新新页面。"""
    evidence.set_tag("C")
    page.get_by_role("button", name="新会话", exact=True).click()
    page.locator(".history li button").nth(0).wait_for(timeout=10_000)
    cid, run, k = submit_question_and_wait(product, page, evidence, "C-waiting")
    run_id = run["runId"]
    before = timeline_seqs(evidence.timelines["C-waiting"])
    streams_before = len(evidence.stream_requests())

    # 切换会话：页面必须关闭旧订阅（network 里不得再出现旧 run 的新 stream 请求）
    page.get_by_role("button", name="新会话", exact=True).click()
    wait_until(lambda: conversation_id(page) != cid, 10_000, "C: 会话已切换")

    # 旧 run 在页面已切换后完成：迟到事件只允许存在于服务端
    reply_via_api(product, cid, run_id, run["pendingQuestion"])
    await_run_status(product, run_id, {"COMPLETED"}, timeout_s=30)
    time.sleep(3)

    after = evidence.snapshot_timeline(page, "C-final")
    new_streams = evidence.stream_requests()[streams_before:]
    old_run_streams = [r for r in new_streams if run_id in r["url"]]
    errors = [line for line in evidence.pageerrors if "C" in line]
    if conversation_id(page) == cid:
        raise RuntimeError("C: 页面应已切换到新会话")
    # 复验 §8.6：无 run 的新会话时间线必须已被 clear（而非残留旧 run 的条目）
    if timeline_seqs(after):
        raise RuntimeError(f"C: 新会话时间线应为空（旧 run 条目不得残留）：{before} -> {after}")
    if after["liveBadge"]:
        raise RuntimeError("C: 新会话不应显示实时徽标")
    if any(item["badge"] == "答案" for item in after["items"]):
        raise RuntimeError(f"C: 新页面不应出现旧 run 的答案事件：{after}")
    if old_run_streams:
        raise RuntimeError(f"C: 切换后不应再为旧 run 建立 stream 请求：{old_run_streams}")
    # 终态历史回读（主代理修复的接线）：切回旧会话，其最新 run 已终态，
    # 时间线必须回读 PG 历史且各显示一次（不残留新会话的时间线，也不重复）
    history_buttons = page.locator(".history li button")
    for i in range(history_buttons.count()):
        if history_buttons.nth(i).evaluate("el => !el.classList.contains('active')"):
            history_buttons.nth(i).click()
            break
    else:
        raise RuntimeError("C: 历史列表中没有可切回的会话")
    n_old = len(run_events(product, run_id))
    # 等 refollow 真正回读完历史（ended-badge 在旧视图就已可见，不能当作回读完成的信号）
    wait_until(lambda: len(timeline_seqs(evidence.snapshot_timeline(page, "C-terminal-history-pending")))
               >= n_old, 15_000, f"C: 终态历史回读应补齐到 {n_old} 条")
    state_old = evidence.snapshot_timeline(page, "C-terminal-history")
    assert_seqs_one_to_n(state_old, n_old, "C-terminal-history")
    if not any(item["badge"] == "答案" for item in state_old["items"]):
        raise RuntimeError(f"C: 终态历史回读应包含答案事件：{state_old}")
    page.screenshot(path=str(output / "screenshot-C-terminal-history.png"), full_page=True)
    if page.locator(".error").count() > 0:
        raise RuntimeError("C: 切换后页面不应报错：" + page.locator(".error").inner_text())
    if errors:
        raise RuntimeError("C: 页面不应有未捕获异常：" + "; ".join(errors))
    page.screenshot(path=str(output / "screenshot-C-final.png"), full_page=True)
    return {"status": "PASS", "oldRunEvents": len(run_events(product, run_id)),
            "timelineSeqsBefore": before, "timelineSeqsAfter": timeline_seqs(after),
            "oldRunStreamsAfterSwitch": len(old_run_streams),
            "terminalHistorySeqs": timeline_seqs(state_old)}


def scenario_d(product, page, evidence, output):
    """d) 无 run 会话清理（复验 §8.6）：进入没有 run 的新会话后旧时间线立即消失；
    此后旧 run 若有迟到事件（再经 API 推进到终态），新会话页面不得出现旧数据。"""
    evidence.set_tag("D")
    page.get_by_role("button", name="新会话", exact=True).click()
    page.locator(".history li button").nth(0).wait_for(timeout=10_000)
    cid_old, run_old, k = submit_question_and_wait(product, page, evidence, "D-waiting")
    run_id_old = run_old["runId"]
    if not evidence.timelines["D-waiting"]["items"]:
        raise RuntimeError("D: 旧会话等待阶段应有时间线事件")

    # 进入没有 run 的新会话：旧时间线必须立即消失（clear：关流 + generation + runId 双保险）
    page.get_by_role("button", name="新会话", exact=True).click()
    wait_until(lambda: conversation_id(page) != cid_old, 10_000, "D: 会话已切换")
    wait_until(lambda: page.locator(".timeline-item").count() == 0, 5_000, "D: 新会话不应残留旧时间线条目")
    if page.locator(".timeline").count() != 0:
        raise RuntimeError("D: 无 run 的新会话不应渲染时间线卡片")
    state = evidence.snapshot_timeline(page, "D-cleared")
    if state["items"] or state["liveBadge"]:
        raise RuntimeError(f"D: 新会话时间线应为空且非实时：{state}")
    streams_mark = len(evidence.stream_requests())

    # 旧 run 迟到事件：经 API 推进到终态；新会话页面不得出现任何旧数据
    reply_via_api(product, cid_old, run_id_old, run_old["pendingQuestion"])
    await_run_status(product, run_id_old, {"COMPLETED"}, timeout_s=30)
    time.sleep(3)
    after = evidence.snapshot_timeline(page, "D-after-late-events")
    new_streams = evidence.stream_requests()[streams_mark:]
    old_run_streams = [r for r in new_streams if run_id_old in r["url"]]
    if after["items"]:
        raise RuntimeError(f"D: 新会话不得出现旧 run 的时间线条目：{after}")
    if old_run_streams:
        raise RuntimeError(f"D: 切换后不应再为旧 run 建立 stream 请求：{old_run_streams}")
    errors = [line for line in evidence.pageerrors if "[D]" in line]
    if errors:
        raise RuntimeError("D: 页面不应有未捕获异常：" + "; ".join(errors))
    page.screenshot(path=str(output / "screenshot-D-final.png"), full_page=True)
    return {"status": "PASS", "oldRunEvents": len(run_events(product, run_id_old)),
            "timelineItemsAfterLateEvents": len(after["items"]),
            "oldRunStreamsAfterSwitch": len(old_run_streams)}


def scenario_e(product, page, evidence, output):
    """HTTP 409 令原生 EventSource 进入 CLOSED，页面停订后可通过刷新恢复历史。"""
    evidence.set_tag("E")
    page.get_by_role("button", name="新会话", exact=True).click()
    page.locator(".history li button").nth(0).wait_for(timeout=10_000)

    page.route("**/api/runs/*/stream", lambda route: route.fulfill(
        status=409, json={"code": "STREAM_CAPACITY"}))

    page.locator(".composer textarea").fill(QUESTION)
    page.get_by_role("button", name="发送", exact=True).click()
    page.get_by_role("heading", name="需要补充信息").wait_for(timeout=30_000)
    cid, run = latest_run(product, page)
    run_id = run["runId"]
    def stream_attempts():
        return [n for n in evidence.network
                if n["kind"] == "request" and "/stream" in n["url"] and run_id in n["url"]]

    page.locator(".timeline li").first.wait_for(timeout=15_000)
    wait_until(lambda: bool(stream_attempts()) and not page.locator(".timeline .live-badge").count(),
               15_000, "E: HTTP 409 后应停止实时展示")
    frozen_items = list(evidence.snapshot_timeline(page, "E-frozen-at-waiting")["items"])

    # 阻断窗口内 run 照常完成：事件只写在服务端，页面时间线冻结在阻断前快照（无流帧可收）
    reply_via_api(product, cid, run_id, run["pendingQuestion"])
    await_run_status(product, run_id, {"COMPLETED"}, timeout_s=30)
    time.sleep(2)
    if evidence.snapshot_timeline(page, "E-during-block")["items"] != frozen_items:
        raise RuntimeError("E: 阻断期间页面时间线不应变化（无新流帧）")

    attempts_at_limit = len(stream_attempts())
    page.unroute_all()
    time.sleep(5)
    if len(stream_attempts()) != attempts_at_limit:
        raise RuntimeError("E: 放行后不应再出现 /stream 请求（永久放弃后不得重连）")
    after_release = evidence.snapshot_timeline(page, "E-after-release")
    if after_release["items"] != frozen_items:
        raise RuntimeError(f"E: 永久失败后页面不得补齐时间线：{frozen_items} -> {after_release['items']}")
    if after_release["liveBadge"]:
        raise RuntimeError("E: 永久失败后不应显示「实时」徽标")
    page.screenshot(path=str(output / "screenshot-E-permanent-failure.png"), full_page=True)

    # 刷新后走历史回读：时间线完整 1..N、含答案与已结束徽标
    page.reload()
    page.locator(".history li button").first.wait_for(timeout=15_000)
    n = len(run_events(product, run_id))
    wait_until(lambda: len(evidence.snapshot_timeline(page, "E-history-pending")["items"]) >= n,
               20_000, f"E: 刷新后历史回读应补齐 {n} 条事件")
    state = evidence.snapshot_timeline(page, "E-history")
    assert_seqs_one_to_n(state, n, "E-history")
    if not state["endedBadge"]:
        raise RuntimeError("E: 历史回读后时间线应显示已结束")
    if not any(item["badge"] == "答案" for item in state["items"]):
        raise RuntimeError(f"E: 历史回读应包含答案事件：{state}")
    errors = [line for line in evidence.pageerrors if "[E]" in line]
    if errors:
        raise RuntimeError("E: 页面不应有未捕获异常：" + "; ".join(errors))
    page.screenshot(path=str(output / "screenshot-E-history.png"), full_page=True)
    return {"status": "PASS", "streamAttemptsBeforeGiveUp": attempts_at_limit,
            "frozenItemsAtWaiting": len(frozen_items),
            "timelineItemsBeforeReload": len(after_release["items"]),
            "finalEvents": n, "finalSeqs": timeline_seqs(state)}


def main():
    global UI_URL
    if not (FRONTEND / "index.html").is_file():
        raise RuntimeError("Build the Vue frontend (frontend/dist) before running SSE browser acceptance.")
    output = ROOT / "outputs" / "validation" / f"sse-browser-{uuid.uuid4().hex[:12]}"
    output.mkdir(parents=True, exist_ok=True)
    evidence = Evidence()
    server_output = output / "server"
    server_output.mkdir()
    product = Server("sse-browser-" + uuid.uuid4().hex[:10], "sse-browser", "deterministic", "scripted",
                     True, server_output)
    ui = None
    thread = None
    result = {"status": "NOT_RUN", "scenarios": {}, "evidenceDir": str(output)}
    try:
        ui, thread = serve_frontend(product, PROXY_STREAM_LOG)
        UI_URL = f"http://127.0.0.1:{ui.server_port}"
        import_manual(product)

        from playwright.sync_api import sync_playwright
        with sync_playwright() as playwright:
            browser = playwright.chromium.launch(channel="chrome", headless=True)
            try:
                page = browser.new_context().new_page()
                evidence.attach(page)
                result["scenarios"]["A-live-timeline"] = scenario_a(product, page, evidence, output)
                result["scenarios"]["B-reconnect-backfill"] = scenario_b(product, page, evidence, output)
                result["scenarios"]["C-switch-conversation"] = scenario_c(product, page, evidence, output)
                result["scenarios"]["D-runless-conversation-clears-timeline"] = scenario_d(
                    product, page, evidence, output)
                result["scenarios"]["E-permanent-failure-gives-up-then-history"] = scenario_e(
                    product, page, evidence, output)
            finally:
                browser.close()

        result["pageErrors"] = evidence.pageerrors
        result["status"] = "PASS" if all(
            s.get("status") == "PASS" for s in result["scenarios"].values()) and not evidence.pageerrors else "FAIL"
    except Exception as error:
        result["status"] = "FAIL"
        result["error"] = f"{type(error).__name__}: {error}"
    finally:
        if ui is not None:
            ui.shutdown()
            ui.server_close()
            thread.join(timeout=5)
        product.close()
        (output / "console.log").write_text("\n".join(evidence.console) + "\n", encoding="utf-8")
        with (output / "network.jsonl").open("w", encoding="utf-8") as fh:
            for line in evidence.network:
                fh.write(json.dumps(line, ensure_ascii=False) + "\n")
        (output / "timeline.json").write_text(json.dumps(evidence.timelines, ensure_ascii=False, indent=2),
                                              encoding="utf-8")
        with (output / "proxy-stream.jsonl").open("w", encoding="utf-8") as fh:
            for line in PROXY_STREAM_LOG:
                fh.write(json.dumps(line, ensure_ascii=False) + "\n")
        result["consoleTail"] = evidence.console[-20:]
        (output / "result.json").write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps({"status": result["status"], "result": str(output / "result.json")}, ensure_ascii=False))
    return 0 if result["status"] == "PASS" else 1


if __name__ == "__main__":
    sys.exit(main())
