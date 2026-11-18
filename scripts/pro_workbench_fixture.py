"""协议替身静态服务器：托管 frontend/dist 并模拟 /api，用于无 PostgreSQL 时的页面交互验证。

仅用于本地页面走查与交互断言，不代表真实服务端能力、持久化或模型行为。
真实 PG/Java 合同验证见 scripts/verify_browser_recovery.py 与 mvn 测试。
"""

import json
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DIST = ROOT / "frontend" / "dist"

ASSET_A = "SC0245B"
C1 = "conv-1"
C1_WAITING = "conv-waiting"
C2 = "conv-2"

LONG_TEXT = ("直流快充过程中充电中止，优先读取充电桩故障日志并核对绝缘检测记录；"
             "严禁在未断电情况下拆卸高压连接器，所有操作须由具备资质人员按厂家规程执行。")


def message(mid, seq, role, content, asset=None, asof="2026-11-17T08:00:00Z"):
    return {"messageId": mid, "seq": seq, "role": role, "content": content, "selectedAssetId": asset,
            "requestedAt": "2026-11-17T08:00:00Z", "asOf": asof}


def reference(rid, title, section, nature="AUTHORIZED", source_id="DOC-1", page=None):
    return {"referenceId": rid, "sourceType": "KNOWLEDGE_DOCUMENT", "sourceId": source_id,
            "sourceVersion": "2026-11-01T00:00:00Z", "chunkId": "chunk-" + rid, "title": title,
            "page": page, "section": section, "excerpt": "直流快充中止时先读取充电桩故障日志，再核对绝缘检测记录。",
            "dataNature": nature}


REF_A = reference("ref-a", "充电系统维修手册", "4.2 直流快充中断")
REF_B = reference("ref-b", "充电系统维修手册", "4.3 绝缘检测")
REF_C = reference("ref-c", "现场观测 CHARGE_PORT", None, nature="SIMULATED", source_id="OBS-1")


def completed_result(asset_note):
    return {
        "summary": LONG_TEXT,
        "category": "POSSIBLE_CAUSES",
        "claims": [
            {"text": "充电桩故障日志显示直流快充中止，且绝缘检测记录告警；需要读取日志并核对绝缘检测记录后再决定处理。" + asset_note + "测试标记", "certainty": "SUPPORTED",
             "referenceIds": ["ref-a", "ref-b"]},
            {"text": "充电枪开锁机构卡滞，但尚未现场核实；禁止带电插拔。", "certainty": "POSSIBLE",
             "referenceIds": ["ref-c"]},
            {"text": "用户反馈充电站提示设备维护中（尚未核实）。", "certainty": "POSSIBLE", "referenceIds": []},
        ],
        "suggestions": ["由具备资质人员读取充电桩故障日志。", "确认高压系统断电后再检查连接器。"],
        "uncertainties": ["尚未取得绝缘电阻实测值。"],
        "limitations": ["本次仅使用已登记资料，未进行现场核验。", "严禁在未断电情况下拆卸高压连接器。"],
        "references": [REF_A, REF_B],
        "riskLevel": "HIGH",
        "safetyDisposition": "REQUIRES_QUALIFIED_PERSONNEL",
        "dataNature": "AUTHORIZED",
    }


def run_view(run_id, status, goal, version=3, asset=None, asof="2026-11-17T08:00:10Z", result=None,
             question=None, failure=None):
    return {"runId": run_id, "conversationId": C1, "goalMessageId": goal, "status": status, "version": version,
            "requestedAt": "2026-11-17T08:00:00Z", "asOf": asof, "dataNature": "SIMULATED",
            "pendingQuestion": question, "result": result, "failureCode": failure, "activeDurationMs": 18000,
            "usageStatus": "KNOWN", "inputTokens": 1200, "outputTokens": 300, "cost": None}


RUN1 = run_view("run-1", "COMPLETED", "m1", asset=ASSET_A, result=completed_result("资产 A 时点 T1"))
RUN2 = run_view("run-2", "COMPLETED", "m3", asset=ASSET_A, asof="2026-11-18T09:00:00Z", result=completed_result("资产 A 时点 T2"))
RUN_FAILED = run_view("run-3", "FAILED", "m5", failure="MODEL_OUTPUT_INVALID_AFTER_RETRY")
RUN_CANCELLED = run_view("run-4", "CANCELLED", "m5")


def conv1():
    messages = [
        message("m1", 1, "USER", "车辆充电中断，请调查原因。", ASSET_A),
        message("m2", 2, "ASSISTANT", "请说明充电方式。"),
        message("m3", 3, "USER", "白天换个时点再查一次。", ASSET_A, "2026-11-18T09:00:00Z"),
        message("m4", 4, "ASSISTANT", "已完成。"),
        message("m5", 5, "USER", "请生成报告。", ASSET_A),
    ]
    runs = [RUN1, RUN2, RUN_FAILED, RUN_CANCELLED]
    return {"conversation": {"conversationId": C1, "version": 5, "createdAt": "2026-11-17T07:00:00Z",
                             "updatedAt": "2026-11-17T09:00:00Z"},
            "messages": messages, "runs": runs}


WAITING_QUESTION = {
    "pendingQuestionId": "q1", "questionVersion": 2,
    "question": "车辆充电中断，请补充：使用的充电方式是什么？当前 SOC 大约多少？",
    "requestedFields": ["充电方式", "SOC"], "askedAt": "2026-11-17T10:00:00Z",
}


def conv_waiting():
    messages = [
        message("w1", 1, "USER", "充电没反应，帮我查一下。", "CAR-B"),
        message("w2", 2, "ASSISTANT", WAITING_QUESTION["question"]),
    ]
    runs = [{"runId": "run-w", "conversationId": C1_WAITING, "goalMessageId": "w1", "status": "WAITING_FOR_USER",
             "version": 2, "requestedAt": "2026-11-17T10:00:00Z", "asOf": "2026-11-17T10:00:00Z",
             "dataNature": "SIMULATED", "pendingQuestion": WAITING_QUESTION, "result": None, "failureCode": None,
             "activeDurationMs": 4000, "usageStatus": "UNKNOWN"}]
    return {"conversation": {"conversationId": C1_WAITING, "version": 2, "createdAt": "2026-11-17T10:00:00Z",
                             "updatedAt": "2026-11-17T10:30:00Z"},
            "messages": messages, "runs": runs}


def conv2():
    return {"conversation": {"conversationId": C2, "version": 1, "createdAt": "2026-11-16T07:00:00Z",
                             "updatedAt": "2026-11-16T08:00:00Z"},
            "messages": [message("n1", 1, "USER", "控制器固件核对。", "SC0245B")],
            "runs": [run_view("run-c2", "COMPLETED", "n1", result=completed_result("会话二的核对"))]}


CONVERSATIONS = {C1: conv1(), C1_WAITING: conv_waiting(), C2: conv2()}
CONVERSATION_LIST = [CONVERSATIONS[C1]["conversation"], CONVERSATIONS[C2]["conversation"],
                     CONVERSATIONS[C1_WAITING]["conversation"]]
ASSETS = ["SC0245B", "CAR-B"]

STATE = {"references": {}, "calls": []}


def events(run_id):
    return [
        {"seq": 1, "type": "RUN_QUEUED", "recordedAt": "2026-11-17T08:00:00Z", "data": {"status": "QUEUED"}},
        {"seq": 2, "type": "RUN_TOOL", "recordedAt": "2026-11-17T08:00:01Z",
         "data": {"toolName": "search_knowledge", "success": True, "durationMs": 120, "references": 2}},
        {"seq": 3, "type": "RUN_ANSWER", "recordedAt": "2026-11-17T08:00:02Z", "data": {"category": "POSSIBLE_CAUSES"}},
    ]


ASSET_VIEW = {
    "assetId": ASSET_A, "ownerId": "o", "projectId": "p", "asOf": "2026-11-17T08:00:10Z",
    "configurations": [{"recordId": "c1", "componentId": "BMS", "attribute": "moduleVersion", "value": "D-120A",
                        "sourceType": "FACTORY", "sourceId": "BOM", "sourceRef": "-", "dataNature": "SIMULATED",
                        "verificationStatus": "DOCUMENTED", "authority": 1, "validFrom": "2024-03-12T00:00:00Z",
                        "validTo": None, "recordedAt": "2024-03-12T00:00:00Z", "observedAt": None}],
    "maintenance": [], "installationStatus": "UNCONFIRMED", "conflict": "NONE",
    "requiredChecks": ["读取绝缘电阻实测值"], "limitations": ["演示数据"], "dataNature": "SIMULATED",
}


class Handler(SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=str(DIST), **kwargs)

    def log_message(self, *_):
        pass

    def send_json(self, payload, status=200):
        body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=UTF-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        path = self.path.split("?")[0]
        STATE["calls"].append("GET " + self.path)
        if path == "/_calls":
            return self.send_json(STATE["calls"])
        if path == "/_reset":
            STATE["calls"].clear()
            return self.send_json({"ok": True})
        parts = [p for p in path.split("/") if p]
        if path == "/api/conversations":
            return self.send_json(CONVERSATION_LIST)
        if path == "/api/assets":
            return self.send_json(ASSETS)
        if path.startswith("/api/assets/"):
            return self.send_json(ASSET_VIEW)
        if len(parts) == 3 and parts[0] == "api" and parts[1] == "conversations":
            conv = CONVERSATIONS.get(parts[2])
            return self.send_json(conv) if conv else self.send_json({"code": "NOT_FOUND"}, 404)
        if len(parts) == 4 and parts[1] == "runs" and parts[3] == "events":
            return self.send_json(events(parts[2]))
        if len(parts) == 4 and parts[1] == "runs" and parts[3] == "skill":
            return self.send_json({"code": "SKILL_NOT_SELECTED"}, 404)
        if len(parts) == 4 and parts[1] == "runs" and parts[3] == "references":
            run_id = parts[2]
            mode = STATE["references"].get(run_id, "unknown")
            if mode == "error":
                return self.send_json({"code": "INTERNAL", "message": "模拟故障"}, 500)
            if mode == "denied":
                return self.send_json({"code": "FORBIDDEN", "message": "无权访问"}, 403)
            if mode in ("merged", "unavailable"):
                return self.send_json({"code": "NOT_FOUND_OR_DENIED", "message": "不可用"}, 404)
            refs = {"run-1": [REF_A, REF_B, REF_C], "run-2": [REF_A], "run-3": [REF_B],
                    "run-4": [REF_A, REF_B, REF_C]}.get(run_id, [])
            return self.send_json(refs)
        if len(parts) == 3 and parts[1] == "runs":
            run = next((r for c in CONVERSATIONS.values() for r in c["runs"] if r["runId"] == parts[2]), None)
            return self.send_json(run) if run else self.send_json({"code": "NOT_FOUND"}, 404)
        if path.startswith("/api/"):
            return self.send_json({"code": "NOT_FOUND"}, 404)
        return super().do_GET()

    def do_POST(self):
        length = int(self.headers.get("Content-Length", "0"))
        raw = self.rfile.read(length) if length else b""
        payload = json.loads(raw.decode("utf-8")) if raw else {}
        path = self.path.split("?")[0]
        STATE["calls"].append("POST " + path + " " + json.dumps(payload, ensure_ascii=False))
        parts = [p for p in path.split("/") if p]
        if path == "/api/conversations":
            conv = conv2()
            return self.send_json({"conversationId": "conv-new", "version": 0,
                                   "createdAt": "2026-11-18T00:00:00Z", "updatedAt": "2026-11-18T00:00:00Z"})
        if path == "/_control":
            STATE["references"].update(payload.get("references", {}))
            return self.send_json({"ok": True})
        if path == "/_calls":
            return self.send_json(STATE["calls"])
        if len(parts) == 4 and parts[1] == "conversations" and parts[3] == "messages":
            if payload.get("replyTo"):
                if payload.get("questionVersion") == 1:
                    self.send_json({"code": "STALE_OR_UNRELATED_REPLY", "message": "追问已变化"}, 409)
                    return None
                return self.send_json(run_view("run-w", "RUNNING", "w1", version=3))
            return self.send_json(run_view("run-new", "QUEUED", "m-new", version=0))
        if len(parts) == 3 and parts[1] == "runs" and parts[2] and parts[0] == "api":
            status = "CANCELLED" if parts[2] != "done" else None
            return self.send_json(run_view(parts[2], status or "COMPLETED", "m1", version=9,
                                           result=completed_result("取消后")))
        if path.startswith("/api/"):
            return self.send_json({"code": "NOT_FOUND"}, 404)
        return self.send_json({}, 404)

