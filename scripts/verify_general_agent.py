"""Product API acceptance for RAG, persistent investigation and dual domain Skills."""
import argparse
import json
import os
from pathlib import Path
import shutil
import socket
import subprocess
import sys
import time
import urllib.error
import urllib.request
import uuid

ROOT = Path(__file__).resolve().parent.parent
EVAL = ROOT / "datasets" / "eval" / "m1-rag"
MANUAL = ROOT / "datasets" / "knowledge" / "charging-system-manual.md"
JAR = ROOT / "target" / "devicetruth-core-0.1.0.jar"
EXPECTED_SECTION = "直流快充中断"
EXPECTED_CONTENT = "直流快充过程中充电中止时，先读取充电桩故障日志"
DUAL = ROOT / "datasets" / "v1"
FACTORY_GOAL = ("请做控制器固件核对。查询 moduleVersion，比较 2024-06-01 的出厂记录和当前改造、旧模板，"
                "解释各记录的来源、有效期以及现场核验情况。检查是否接入现场观测。")
FACTORY_CONFIGURATION_SOURCES = {
    "SC0245B-BOM-2024": ("value=D-120A", "source=FACTORY", "validFrom=2024-03-12"),
    "SC0245B-RETROFIT-2025": ("value=D-120B", "source=REPLACEMENT", "validFrom=2025-09-15"),
    "SC0245B-TEMPLATE-2026": ("value=D-120A", "source=CONFIGURATION", "validFrom=2026-02-01"),
}


def free_port():
    with socket.socket() as sock:
        sock.bind(("127.0.0.1", 0))
        return sock.getsockname()[1]


def java_command():
    java = Path(os.environ["JAVA_HOME"]) / "bin" / ("java.exe" if os.name == "nt" else "java") \
        if os.environ.get("JAVA_HOME") else shutil.which("java")
    if not java:
        raise RuntimeError("BLOCKED_BY_ENV: Java not found")
    return str(java)


class Server:
    def __init__(self, owner, project, embedding, reasoner, worker, output, fixture_assets=None):
        self.port = free_port()
        self.base = f"http://127.0.0.1:{self.port}"
        self.output = output
        self.process = None
        environment = os.environ.copy()
        base_url = environment.get("DEVICETRUTH_TEST_PG_BASE_URL")
        if not base_url:
            raise RuntimeError("BLOCKED_BY_ENV: DEVICETRUTH_TEST_PG_BASE_URL is required")
        database = environment.get("DEVICETRUTH_EVAL_DATABASE", "devicetruth")
        environment["SPRING_DATASOURCE_URL"] = base_url + database
        environment["SPRING_DATASOURCE_USERNAME"] = environment.get("DEVICETRUTH_TEST_PG_USERNAME", "")
        environment["SPRING_DATASOURCE_PASSWORD"] = environment.get("DEVICETRUTH_TEST_PG_PASSWORD", "")
        environment["DEVICETRUTH_OWNER_ID"] = owner
        environment["DEVICETRUTH_PROJECT_ID"] = project
        environment["DEVICETRUTH_AGENT_REASONER"] = reasoner
        environment["DEVICETRUTH_AGENT_WORKER_ENABLED"] = str(worker).lower()
        environment["DEVICETRUTH_EMBEDDING_PROVIDER"] = "openai" if embedding == "real" else "deterministic"
        environment["DEVICETRUTH_EMBEDDING_MODEL"] = "text-embedding-v4" if embedding == "real" else "deterministic-char2"
        environment["DEVICETRUTH_FILES_ROOT"] = str(output / "files" / owner)
        fixture_flags = (["--devicetruth.assets.seed-fixtures=true", f"--devicetruth.assets.fixtures={fixture_assets}"]
                         if fixture_assets else [])
        self.log = (output / f"server-{owner}-{project}.log").open("w", encoding="utf-8")
        self.process = subprocess.Popen([
            java_command(), "-Dloader.main=io.devicetruth.server.DeviceTruthServerApplication",
            "-cp", str(JAR), "org.springframework.boot.loader.launch.PropertiesLauncher",
            "--spring.profiles.active=product", f"--server.port={self.port}", "--debug=false",
            "--logging.level.org.springframework=WARN", *fixture_flags
        ], cwd=ROOT, env=environment, stdout=self.log, stderr=subprocess.STDOUT)
        deadline = time.time() + 45
        while time.time() < deadline:
            if self.process.poll() is not None:
                raise RuntimeError(f"Product server exited with {self.process.returncode}")
            try:
                status, _ = self.request("GET", "/api/health")
                if status == 200:
                    return
            except Exception:
                pass
            time.sleep(0.25)
        raise RuntimeError("Product server startup timeout")

    def request(self, method, path, body=None, headers=None):
        data = None
        request_headers = {"Accept": "application/json"}
        if headers:
            request_headers.update(headers)
        if body is not None and not isinstance(body, bytes):
            data = json.dumps(body, ensure_ascii=False).encode("utf-8")
            request_headers["Content-Type"] = "application/json; charset=UTF-8"
        elif body is not None:
            data = body
        request = urllib.request.Request(self.base + path, data=data, headers=request_headers, method=method)
        try:
            with urllib.request.urlopen(request, timeout=150) as response:
                text = response.read().decode("utf-8")
                return response.status, json.loads(text) if text else None
        except urllib.error.HTTPError as error:
            text = error.read().decode("utf-8")
            try:
                payload = json.loads(text) if text else {}
            except json.JSONDecodeError:
                payload = {"message": "non-json error"}
            return error.code, payload

    def close(self):
        if self.process and self.process.poll() is None:
            self.process.terminate()
            try:
                self.process.wait(timeout=10)
            except subprocess.TimeoutExpired:
                self.process.kill()
                self.process.wait(timeout=5)
        self.log.close()


def expect(response, status):
    actual, body = response
    if actual != status:
        if isinstance(body, dict) and body.get("code") in {"EMBEDDING_CONFIGURATION_MISSING", "EMBEDDING_UNAVAILABLE"}:
            raise RuntimeError("BLOCKED_BY_ENV: real embedding failed: " + body["code"])
        raise RuntimeError(f"Expected HTTP {status}, got {actual}: {body}")
    return body


def create_run(server, message, extra=None):
    conversation = expect(server.request("POST", "/api/conversations", {}), 201)
    payload = {"schemaVersion": 1, "userMessage": message}
    if extra:
        payload.update(extra)
    accepted = expect(server.request("POST", f"/api/conversations/{conversation['conversationId']}/messages", payload,
                                     {"Idempotency-Key": str(uuid.uuid4())}), 202)
    return accepted["runId"]


def await_run(server, run_id, timeout=150):
    deadline = time.time() + timeout
    while time.time() < deadline:
        run = expect(server.request("GET", f"/api/runs/{run_id}"), 200)
        if run["status"] in {"WAITING_FOR_USER", "COMPLETED", "FAILED", "CANCELLED"}:
            return run
        time.sleep(0.5)
    raise RuntimeError("Run wait timeout")


def import_document(server, manifest, name, version):
    item = manifest["documents"][name]
    content = (EVAL / item["path"]).read_text("utf-8")
    result = expect(server.request("POST", "/api/knowledge/documents", {
        "title": item["title"], "domain": "automotive-demo", "version": version,
        "filename": Path(item["path"]).name, "mimeType": item["mimeType"], "content": content,
        "dataNature": "SIMULATED"
    }), 201)
    return result["document"]


def upload_markdown(server, manifest):
    item = manifest["documents"]["charging"]
    boundary = "----DeviceTruth" + uuid.uuid4().hex
    content = (EVAL / item["path"]).read_bytes()
    parts = []
    for name, value in [("title", item["title"]), ("domain", "automotive-demo"), ("version", "2026.2")]:
        parts.append(f"--{boundary}\r\nContent-Disposition: form-data; name=\"{name}\"\r\n\r\n{value}\r\n".encode())
    parts.append(f"--{boundary}\r\nContent-Disposition: form-data; name=\"file\"; filename=\"charging.md\"\r\n"
                 "Content-Type: text/markdown\r\n\r\n".encode() + content + b"\r\n")
    parts.append(f"--{boundary}--\r\n".encode())
    accepted = expect(server.request("POST", "/api/files", b"".join(parts),
                                     {"Content-Type": f"multipart/form-data; boundary={boundary}"}), 202)
    deadline = time.time() + 120
    while time.time() < deadline:
        file = expect(server.request("GET", f"/api/files/{accepted['fileId']}"), 200)
        if file["parseStatus"] in {"PARSED", "FAILED", "OCR_REQUIRED"}:
            if file["parseStatus"] != "PARSED" or not file.get("documentId"):
                code = file.get("failureCode") or "MISSING_DOCUMENT_ASSOCIATION"
                if code in {"EMBEDDING_CONFIGURATION_MISSING", "EMBEDDING_UNAVAILABLE"}:
                    raise RuntimeError(f"BLOCKED_BY_ENV: real embedding failed: {code}")
                raise RuntimeError(f"Upload import failed: {code}")
            return file
        time.sleep(0.25)
    raise RuntimeError("File import timeout")


def required_negative(case):
    return case["kind"] in {"empty", "error"} or bool(case.get("forbiddenVersion"))


def retrieval_passed(channels, negative_results, cases):
    hybrid = channels["HYBRID"]
    required = {case["id"] for case in cases if required_negative(case)}
    observed = {item["id"] for item in negative_results}
    negatives_ok = required.issubset(observed) and all(item.get("pass") is True for item in negative_results)
    return (hybrid["coreHits"] >= 5 and hybrid["partHits"] == 2
            and hybrid["locatorHits"] == hybrid["coreHits"] and negatives_ok)


def retrieval(main, other_owner, other_project, output, embedding):
    manifest = json.loads((EVAL / "manifest.json").read_text("utf-8"))
    cases = json.loads((EVAL / "cases.json").read_text("utf-8"))
    for name, item in manifest["documents"].items():
        digest = __import__("hashlib").sha256((EVAL / item["path"]).read_bytes()).hexdigest()
        if digest != item["sha256"]:
            raise RuntimeError(f"Corpus hash changed: {name}")
    documents = {
        "main.chargingCurrent": import_document(main, manifest, "charging", "2026.2"),
        "main.chargingLegacy": import_document(main, manifest, "charging", "2025.1"),
        "main.parts": import_document(main, manifest, "parts", "2026.2"),
        "otherOwner.parts": import_document(other_owner, manifest, "parts", "2026.2"),
        "otherProject.parts": import_document(other_project, manifest, "parts", "2026.2")
    }
    run_id = create_run(main, "执行固定 M1 检索样本")
    channels = {channel: {"results": [], "coreHits": 0, "coreTotal": 0, "partHits": 0,
                          "locatorHits": 0, "elapsedMs": 0.0} for channel in ["VECTOR", "KEYWORD", "HYBRID"]}
    negative_results = []
    for case in cases:
        if case["kind"] == "error":
            if case["operation"] == "excerptByReference":
                response = main.request("GET", f"/api/knowledge/references/{uuid.uuid4()}?runId={run_id}")
            else:
                response = main.request("GET", f"/api/knowledge/documents/{uuid.uuid4()}/chunks/{uuid.uuid4()}?runId={run_id}")
            passed = response[0] == 404 and response[1].get("code") == case["expectedError"]
            negative_results.append({"id": case["id"], "pass": passed, "http": response[0],
                                     "code": response[1].get("code")})
            continue
        forbidden_identity = case.get("targetIdentity", "main")
        filters = None
        if case.get("filtersFrom"):
            filters = {"documentId": documents[case["filtersFrom"]]["documentId"]}
        for channel in channels:
            started = time.perf_counter()
            status, outcome = main.request("POST", "/api/knowledge/search", {
                "runId": run_id, "query": case["query"], "filters": filters, "mode": channel, "limit": 5
            })
            elapsed = (time.perf_counter() - started) * 1000
            if status != 200:
                raise RuntimeError(f"Search {case['id']}/{channel} failed: {outcome}")
            hits = outcome["hits"]
            record = {"id": case["id"], "channel": channel, "forbiddenIdentity": forbidden_identity,
                      "elapsedMs": round(elapsed, 2), "documentIds": [hit["documentId"] for hit in hits]}
            if case["kind"] == "empty":
                record["pass"] = not hits
                negative_results.append(record)
                continue
            expected = case["expected"]
            expected_key = "main.chargingCurrent" if expected["document"] == "charging" else "main.parts"
            expected_id = documents[expected_key]["documentId"]
            matched = [hit for hit in hits if hit["documentId"] == expected_id
                       and hit["documentVersion"] == expected["version"]]
            hit = bool(matched)
            locator = any((not expected.get("sectionContains") or expected["sectionContains"] in (hit["section"] or ""))
                          and (not expected.get("contentContains") or expected["contentContains"] in hit["content"])
                          for hit in matched)
            forbidden = case.get("forbiddenVersion")
            record.update({"pass": hit and locator and not any(hit["documentVersion"] == forbidden for hit in hits),
                           "hitAt5": hit, "locatorCorrect": locator})
            channels[channel]["results"].append(record)
            channels[channel]["elapsedMs"] += elapsed
            if required_negative(case):
                negative_results.append(record)
            if case["id"] != "current-version-only":
                channels[channel]["coreTotal"] += 1
                channels[channel]["coreHits"] += int(hit)
                channels[channel]["locatorHits"] += int(hit and locator)
                if case["kind"] == "partNumber":
                    channels[channel]["partHits"] += int(hit)
    passed = retrieval_passed(channels, negative_results, cases)
    return {"status": "PASS" if passed else "FAIL", "suite": "m1-retrieval", "embeddingProfile": embedding,
            "channels": channels, "negativeResults": negative_results,
            "requiredNegativeIds": sorted(case["id"] for case in cases if required_negative(case)),
            "thresholds": manifest["thresholds"], "ui": "NOT_RUN", "pdf": "NOT_RUN"}


def no_asset_guard_status(run):
    if run.get("status") != "COMPLETED" or not isinstance(run.get("result"), dict):
        return "NOT_RUN"
    result = run["result"]
    if (result.get("category") == "CONFIRMED_FINDING"
            or any(claim.get("certainty") == "CONFIRMED" for claim in result.get("claims", []))):
        return "FAIL"
    # shortcut: 枚举检查无法证明自由文本未确诊；需要可靠的正文安全判定后才能标记 PASS。
    return "NOT_RUN"


def evaluate_agent(run, references, excerpts, tool_events, uploaded, suite, after_sequence=0):
    result = run.get("result") if isinstance(run.get("result"), dict) else {}
    collected = {reference.get("referenceId"): reference for reference in references}
    answer_references = result.get("references", [])
    used_ids = [reference.get("referenceId") for reference in answer_references]
    answer_scope = bool(used_ids) and all(reference_id in collected for reference_id in used_ids)
    uploaded_references = []
    for answer in answer_references:
        reference = collected.get(answer.get("referenceId"), {})
        if (answer.get("sourceId") == reference.get("sourceId") == uploaded["documentId"]
                and answer.get("sourceVersion") == reference.get("sourceVersion") == uploaded["documentVersion"]):
            uploaded_references.append(reference)
    citation_ok = answer_scope and bool(uploaded_references)
    excerpt_ok = False
    for reference in uploaded_references:
        response = excerpts.get(reference["referenceId"], {})
        body = response.get("body") or {}
        if (response.get("http") == 200 and body.get("documentId") == uploaded["documentId"]
                and EXPECTED_SECTION in (body.get("section") or "")
                and EXPECTED_CONTENT in (body.get("content") or "")):
            excerpt_ok = True
    successful_searches = sum(1 for event in tool_events
                              if event.get("toolName") == "search_knowledge" and event.get("success") is True
                              and event.get("referenceCount", 0) > 0
                              and (suite != "multi-turn-basic" or event.get("sequence", 0) > after_sequence))
    required_searches = 1
    tool_ok = successful_searches >= required_searches
    asset_status = no_asset_guard_status(run)
    completed = run.get("status") == "COMPLETED" and bool(result)
    passed = completed and citation_ok and excerpt_ok and tool_ok and asset_status == "PASS"
    reasons = []
    if not citation_ok:
        reasons.append("ANSWER_DOES_NOT_CITE_UPLOADED_DOCUMENT")
    if not excerpt_ok:
        reasons.append("EXPECTED_SECTION_NOT_READABLE")
    if not tool_ok:
        reasons.append("MISSING_REQUIRED_TOOL_EVIDENCE")
    if asset_status != "PASS":
        reasons.append("NO_ASSET_GUARD_" + asset_status)
    status = ("FAIL" if not (completed and citation_ok and excerpt_ok and tool_ok) or asset_status == "FAIL"
              else "NOT_RUN" if asset_status == "NOT_RUN" else "PASS")
    evidence = {"e2e01FreeEntry": completed, "e2e02Knowledge": citation_ok and tool_ok,
                "e2e03UnknownAsset": asset_status, "e2e06UploadCitation": citation_ok and excerpt_ok,
                "e2e07Basic": status if suite == "multi-turn-basic" else "NOT_RUN"}
    return {"passed": passed, "status": status, "reasons": reasons, "evidence": evidence,
            "toolEvidence": {"successfulSearches": successful_searches, "requiredSearches": required_searches}}


def agent_result(main, suite, run, uploaded, after_sequence=0):
    run_id = run["runId"]
    references = expect(main.request("GET", f"/api/runs/{run_id}/references"), 200)
    tool_events = expect(main.request("GET", f"/api/runs/{run_id}/tool-events"), 200)
    excerpts = {}
    for reference in (run.get("result") or {}).get("references", []):
        if reference.get("sourceId") == uploaded["documentId"]:
            reference_id = reference["referenceId"]
            status, body = main.request("GET", f"/api/knowledge/references/{reference_id}?runId={run_id}")
            excerpts[reference_id] = {"http": status, "body": body}
    evaluation = evaluate_agent(run, references, excerpts, tool_events, uploaded, suite, after_sequence)
    return {"status": evaluation["status"], "suite": suite,
            "run": run, "references": references, "toolEvents": tool_events, "answerExcerpts": excerpts,
            "reasons": evaluation["reasons"], "evidence": evaluation["evidence"],
            "toolEvidence": evaluation["toolEvidence"], "ui": "NOT_RUN", "pdf": "NOT_RUN"}


def agent_smoke(main, suite, output):
    manifest = json.loads((EVAL / "manifest.json").read_text("utf-8"))
    uploaded = upload_markdown(main, manifest)
    question = ("我的新能源汽车充电出了问题。请先问我具体发生了什么，等我补充后再根据手册分析。"
                if suite == "multi-turn-basic" else
                "新能源汽车充电中断，没有故障码。请依据上传手册说明应先查什么，不要虚构设备现状。")
    run_id = create_run(main, question, {"attachments": [{"fileId": uploaded["fileId"]}]})
    first = await_run(main, run_id)
    if first["status"] == "FAILED" and first.get("failureCode") in {
            "DASHSCOPE_API_KEY_MISSING", "OPENAI_API_KEY_MISSING", "OPENAI_BASE_URL_MISSING",
            "OPENAI_MODEL_MISSING", "OPENAI_CONFIGURATION_INVALID", "MODEL_CONFIGURATION_MISSING",
            "MODEL_TRANSPORT_OR_TIMEOUT"}:
        raise RuntimeError(f"BLOCKED_BY_ENV: real model failed: {first.get('failureCode')}")
    if suite == "rag-smoke":
        return agent_result(main, suite, first, uploaded)
    if first["status"] != "WAITING_FOR_USER" or not first.get("pendingQuestion"):
        return {"status": "FAIL", "suite": suite, "reason": "MODEL_DID_NOT_ASK_REQUIRED_QUESTION",
                "run": first, "evidence": {"e2e01FreeEntry": False, "e2e02Knowledge": False,
                                           "e2e03UnknownAsset": no_asset_guard_status(first),
                                           "e2e06UploadCitation": False, "e2e07Basic": "FAIL"}}
    question = first["pendingQuestion"]
    before_events = expect(main.request("GET", f"/api/runs/{run_id}/tool-events"), 200)
    before_sequence = max((event.get("sequence", 0) for event in before_events), default=0)
    replied = expect(main.request("POST", f"/api/conversations/{first['conversationId']}/messages", {
        "schemaVersion": 1, "userMessage": "直流快充过程中充电中断，没有故障码，SOC 约 40%，请结合手册分析。",
        "attachments": [{"fileId": uploaded["fileId"]}],
        "replyTo": {"runId": run_id, "pendingQuestionId": question["pendingQuestionId"],
                    "questionVersion": question["questionVersion"]}
    }, {"Idempotency-Key": str(uuid.uuid4())}), 202)
    if replied["runId"] != run_id:
        raise RuntimeError("Reply created a different run")
    completed = await_run(main, run_id)
    report = agent_result(main, suite, completed, uploaded, before_sequence)
    report["waiting"] = first
    report["restartAndConcurrency"] = "NOT_RUN"
    return report


def import_manual(server):
    content = MANUAL.read_text("utf-8")
    result = expect(server.request("POST", "/api/knowledge/documents", {
        "title": "充电系统检修手册（演示资料）", "domain": "automotive-demo", "version": "2026.1",
        "filename": "manual.md", "mimeType": "text/markdown", "content": content,
        "dataNature": "SIMULATED"}), 201)
    return result["document"]


def investigation(main, output):
    """M2 调查链路验收（scripted + 确定性 embedding，不依赖真实模型密钥）：
    多轮补证闭环、持久请求键幂等、取消终态与迟到响应拒绝、耗时/用量审计不编造。"""
    import_manual(main)
    evidence = {}
    # 1) 多轮调查：追问→等待→同 run 补答→完成，答案引用可回溯，工具事件带耗时
    conversation = expect(main.request("POST", "/api/conversations", {}), 201)
    cid = conversation["conversationId"]
    accepted = expect(main.request("POST", f"/api/conversations/{cid}/messages", {
        "schemaVersion": 1, "userMessage": "新能源汽车充电中断，没有故障码，先查什么资料？"},
        {"Idempotency-Key": str(uuid.uuid4())}), 202)
    run_id = accepted["runId"]
    first = await_run(main, run_id)
    multi_turn_ok = first["status"] == "WAITING_FOR_USER" and bool(first.get("pendingQuestion"))
    question = first.get("pendingQuestion") or {}
    replied = expect(main.request("POST", f"/api/conversations/{cid}/messages", {
        "schemaVersion": 1, "userMessage": "直流快充，SOC 约 40%",
        "replyTo": {"runId": run_id, "pendingQuestionId": question.get("pendingQuestionId"),
                    "questionVersion": question.get("questionVersion")}},
        {"Idempotency-Key": str(uuid.uuid4())}), 202)
    same_run = replied["runId"] == run_id
    completed = await_run(main, run_id)
    references = expect(main.request("GET", f"/api/runs/{run_id}/references"), 200)
    tool_events = expect(main.request("GET", f"/api/runs/{run_id}/tool-events"), 200)
    answer = completed.get("result") or {}
    collected = {ref.get("referenceId") for ref in references}
    used_ids = [ref.get("referenceId") for ref in answer.get("references", [])]
    citation_ok = bool(used_ids) and all(ref_id in collected for ref_id in used_ids)
    audit_ok = (completed.get("status") == "COMPLETED" and bool(answer)
                and all(event.get("durationMs", -1) >= 0 for event in tool_events)
                and completed.get("activeDurationMs", 0) > 0
                and completed.get("usageStatus") == "UNKNOWN")
    evidence["multiTurn"] = {"sameRun": same_run, "waiting": multi_turn_ok, "citations": citation_ok,
                             "audit": audit_ok, "toolEvents": len(tool_events)}
    # 2) 幂等：同 Idempotency-Key 重复提交 → 409 DUPLICATE_REQUEST，不产生第二个 run
    conv2 = expect(main.request("POST", "/api/conversations", {}), 201)
    cid2 = conv2["conversationId"]
    payload = {"schemaVersion": 1, "userMessage": "同键重复提交同一问题"}
    dup_key = str(uuid.uuid4())
    expect(main.request("POST", f"/api/conversations/{cid2}/messages", payload,
                        {"Idempotency-Key": dup_key}), 202)
    dup_status, dup_body = main.request("POST", f"/api/conversations/{cid2}/messages", payload,
                                        {"Idempotency-Key": dup_key})
    detail = expect(main.request("GET", f"/api/conversations/{cid2}"), 200)
    evidence["idempotency"] = {"http": dup_status, "code": (dup_body or {}).get("code"),
                               "runs": len(detail.get("runs", []))}
    idempotent_ok = dup_status == 409 and (dup_body or {}).get("code") == "DUPLICATE_REQUEST" \
        and len(detail.get("runs", [])) == 1
    # 3) 取消：WAITING 取消 → CANCELLED；补答 409；再取消 409 RUN_ALREADY_TERMINAL
    conv3 = expect(main.request("POST", "/api/conversations", {}), 201)
    cid3 = conv3["conversationId"]
    accepted3 = expect(main.request("POST", f"/api/conversations/{cid3}/messages", {
        "schemaVersion": 1, "userMessage": "新能源汽车充电中断，没有故障码，先查什么资料？"},
        {"Idempotency-Key": str(uuid.uuid4())}), 202)
    run3 = accepted3["runId"]
    waiting3 = await_run(main, run3)
    cancel_status, cancelled = main.request("POST", f"/api/runs/{run3}/cancel")
    q3 = waiting3.get("pendingQuestion") or {}
    reply_status, reply_body = main.request("POST", f"/api/conversations/{cid3}/messages", {
        "schemaVersion": 1, "userMessage": "取消后的补答不应被接受",
        "replyTo": {"runId": run3, "pendingQuestionId": q3.get("pendingQuestionId"),
                    "questionVersion": q3.get("questionVersion")}},
        {"Idempotency-Key": str(uuid.uuid4())})
    recancel_status, recancel_body = main.request("POST", f"/api/runs/{run3}/cancel")
    cancel_ok = (cancel_status == 200 and (cancelled or {}).get("status") == "CANCELLED"
                 and reply_status == 409 and (reply_body or {}).get("code") == "STALE_OR_UNRELATED_REPLY"
                 and recancel_status == 409 and (recancel_body or {}).get("code") == "RUN_ALREADY_TERMINAL")
    evidence["cancel"] = {"cancelHttp": cancel_status, "status": (cancelled or {}).get("status"),
                          "replyHttp": reply_status, "replyCode": (reply_body or {}).get("code"),
                          "recancelHttp": recancel_status, "recancelCode": (recancel_body or {}).get("code")}
    passed = multi_turn_ok and same_run and completed.get("status") == "COMPLETED" and citation_ok \
        and audit_ok and idempotent_ok and cancel_ok
    return {"status": "PASS" if passed else "FAIL", "suite": "investigation",
            "evidence": evidence, "realModel": "NOT_RUN", "ui": "NOT_RUN", "pdf": "NOT_RUN"}


def dual_case_evidence(server, run, skill_id, tools_required):
    run_id = run["runId"]
    skill_status, skill = server.request("GET", f"/api/runs/{run_id}/skill")
    references = expect(server.request("GET", f"/api/runs/{run_id}/references"), 200)
    events = expect(server.request("GET", f"/api/runs/{run_id}/tool-events"), 200)
    tools = {event.get("toolName") for event in events if event.get("success") is True}
    answer = run.get("result") or {}
    used = answer.get("references", [])
    retrieved = {reference.get("referenceId") for reference in references}
    evidence = {
        "completed": run["status"] == "COMPLETED",
        "correctSkill": skill_status == 200 and skill.get("skillId") == skill_id and skill.get("version") == next(
            line.split(":", 1)[1].strip() for line in (ROOT / "skills" / skill_id / "SKILL.md").read_text().splitlines()
            if line.startswith("version:")),
        "toolCalls": tools_required <= tools,
        "citationsInRun": bool(used) and all(reference.get("referenceId") in retrieved for reference in used),
        "uncertain": answer.get("category") != "CONFIRMED_FINDING" and bool(answer.get("uncertainties")),
        "simulated": answer.get("dataNature") == "SIMULATED",
        "missingObservationDeclared": any(phrase in json.dumps(answer, ensure_ascii=False) for phrase in (
            "尚未读取", "没有读取", "未接入", "未读取", "未取得现场观测")),
    }
    return evidence, skill, answer, events


def factory_configuration_cited(references):
    return all(any(reference.get("sourceType") == "CONFIGURATION" and reference.get("sourceId") == source
                   and all(term in (reference.get("excerpt") or "") for term in terms)
                   for reference in references) for source, terms in FACTORY_CONFIGURATION_SOURCES.items())


def factory_installation_unconfirmed(answer):
    if answer.get("category") == "CONFIRMED_FINDING":
        return False
    text = json.dumps(answer, ensure_ascii=False)
    if any(phrase in text for phrase in (
            "仍待现场核验", "尚未现场", "无法确认当前", "无法确认实际", "不能确认当前", "未有现场", "未核实",
            "不能确认设备当前", "不能确认当前装机", "不能确认控制器当前实际固件版本")):
        return True
    return ("UNCONFIRMED" in text
            and any(phrase in text for phrase in ("并不等于现场实际装机", "不等于现场实际装机",
                                                  "不能据此判定设备已升级", "不能代表实际装机",
                                                  "不是现场读取核实")))


def dual_domain(main, embedding, reasoner):
    manual = import_manual(main)
    case = json.loads((DUAL / "automotive" / "charging-case.json").read_text("utf-8"))
    conversation = expect(main.request("POST", "/api/conversations", {}), 201)["conversationId"]
    messages = f"/api/conversations/{conversation}/messages"
    goal = case["goalText"]
    if reasoner != "scripted":
        goal += "我还没有提供充电方式，也没说是不是只在特定充电桩发生。请先在一个问题里问清这两点，等我补充后再根据资料分析。"
    car_id = expect(main.request("POST", messages, {"schemaVersion": 1, "userMessage": goal},
                                 {"Idempotency-Key": str(uuid.uuid4())}), 202)["runId"]
    car = await_run(main, car_id)
    asked = []
    same_run = True
    if car["status"] == "WAITING_FOR_USER":
        question = car.get("pendingQuestion") or {}
        asked.append(question.get("question", ""))
        clarification = case["turns"][1]["content"]
        if reasoner != "scripted":
            clarification += ("请先检索手册的“直流快充中断”章节，并在答案中引用该章节。"
                              "再用只读观测工具核对有无可读现场观测，即使没有资产编号也请核对。"
                              "最后把手册分析和观测结果一并告诉我。")
        reply = expect(main.request("POST", messages, {
            "schemaVersion": 1, "userMessage": clarification,
            "replyTo": {"runId": car_id, "pendingQuestionId": question["pendingQuestionId"],
                        "questionVersion": question["questionVersion"]}}, {"Idempotency-Key": str(uuid.uuid4())}), 202)
        same_run = reply["runId"] == car_id
        car = await_run(main, car_id)
    car_evidence, car_skill, car_answer, car_events = dual_case_evidence(main, car, case["skillId"],
                                                                       {"search_knowledge", "get_observations"})
    car_evidence["sameRunClarification"] = same_run and bool(asked) and all(
        any(term in asked[0] for term in topic["matchKeywords"])
        for topic in case["expected"]["clarification"]["mustAskTopics"])
    car_evidence["manualSectionReadable"] = False
    for citation in car_answer.get("references", []):
        if citation.get("sourceId") != manual["documentId"]:
            continue
        excerpt = expect(main.request("GET", f"/api/knowledge/references/{citation['referenceId']}?runId={car_id}"), 200)
        if EXPECTED_SECTION in (excerpt.get("section") or "") and bool(excerpt.get("content")):
            car_evidence["manualSectionReadable"] = True

    factory_id = create_run(main, FACTORY_GOAL, {"selectedAssetId": "SC-0245-B", "asOf": "2026-11-13T08:00:00Z"})
    factory = await_run(main, factory_id)
    factory_evidence, factory_skill, factory_answer, factory_events = dual_case_evidence(
        main, factory, "factory-equipment", {"get_configuration", "get_observations"})
    factory_evidence["threeSourcesAndTimes"] = factory_configuration_cited(factory_answer.get("references", []))
    factory_evidence["currentInstallationUnconfirmed"] = factory_installation_unconfirmed(factory_answer)
    passed = all(car_evidence.values()) and all(factory_evidence.values())
    return {"status": "PASS" if passed else "FAIL", "suite": "dual-domain",
            "realModel": ("PASS" if passed else "FAIL") if reasoner != "scripted" else "NOT_RUN",
            "realEmbedding": ("PASS" if passed else "FAIL") if embedding == "real" else "NOT_RUN",
            "automotive": {"evidence": car_evidence, "skill": car_skill, "run": car, "toolEvents": car_events,
                           "questions": asked},
            "factory": {"evidence": factory_evidence, "skill": factory_skill, "run": factory,
                        "toolEvents": factory_events}}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--suite", choices=["m1-retrieval", "rag-smoke", "multi-turn-basic", "investigation", "dual-domain"],
                        required=True)
    parser.add_argument("--embedding", choices=["real", "deterministic"], required=True)
    parser.add_argument("--reasoner", choices=["qwen", "openai", "scripted"], default="qwen")
    args = parser.parse_args()
    output = ROOT / "outputs" / "validation" / "general-agent" / f"{args.suite}-{uuid.uuid4().hex}"
    output.mkdir(parents=True, exist_ok=True)
    report = {"status": "NOT_RUN", "suite": args.suite, "embedding": args.embedding, "reasoner": args.reasoner}
    servers = []
    try:
        offline_suites = {"m1-retrieval", "investigation"}
        if args.suite == "dual-domain" and args.reasoner == "scripted":
            offline_suites.add("dual-domain")
        if args.suite not in offline_suites and args.embedding != "real":
            raise RuntimeError("BLOCKED_BY_ENV: online Agent acceptance requires --embedding=real")
        if not JAR.exists():
            raise RuntimeError("BLOCKED_BY_ENV: package the current JAR first")
        for name in ["DEVICETRUTH_TEST_PG_BASE_URL", "DEVICETRUTH_TEST_PG_USERNAME", "DEVICETRUTH_TEST_PG_PASSWORD"]:
            if not os.environ.get(name):
                raise RuntimeError(f"BLOCKED_BY_ENV: {name} is required")
        if args.suite == "dual-domain" and not os.environ.get("DEVICETRUTH_EVAL_DATABASE"):
            raise RuntimeError("BLOCKED_BY_ENV: set DEVICETRUTH_EVAL_DATABASE to an isolated scratch database for dual-domain")
        owner = "eval-" + uuid.uuid4().hex[:12]
        worker = args.suite != "m1-retrieval"
        main_server = Server(owner, "m1-rag", args.embedding, args.reasoner, worker, output,
                             "SC-0245-B" if args.suite == "dual-domain" else None)
        servers.append(main_server)
        if args.suite == "m1-retrieval":
            other_owner = Server(owner + "-other", "m1-rag", args.embedding, "scripted", False, output)
            other_project = Server(owner, "other-project", args.embedding, "scripted", False, output)
            servers.extend([other_owner, other_project])
            report = retrieval(main_server, other_owner, other_project, output, args.embedding)
        elif args.suite == "investigation":
            report = investigation(main_server, output)
        elif args.suite == "dual-domain":
            report = dual_domain(main_server, args.embedding, args.reasoner)
        else:
            report = agent_smoke(main_server, args.suite, output)
        report.update({"embedding": args.embedding, "reasoner": args.reasoner})
        code = 0 if report["status"] == "PASS" else 2 if report["status"] == "NOT_RUN" else 1
    except Exception as error:
        message = str(error)
        report["status"] = "BLOCKED_BY_ENV" if message.startswith("BLOCKED_BY_ENV") else "FAIL"
        report["error"] = message
        code = 2 if report["status"] == "BLOCKED_BY_ENV" else 1
    finally:
        for server in reversed(servers):
            server.close()
        report["credentialsIncluded"] = False
        (output / "result.json").write_text(json.dumps(report, ensure_ascii=False, indent=2), "utf-8")
    print(json.dumps({"status": report["status"], "result": str(output / "result.json")}, ensure_ascii=False))
    return code


if __name__ == "__main__":
    sys.exit(main())
