import json
import os
from pathlib import Path
import sys
import tempfile
import unittest
from unittest import mock

sys.path.insert(0, str(Path(__file__).resolve().parent))
import verify_general_agent as verify


class DualDomainDecisionTest(unittest.TestCase):
    def test_explicit_unconfirmed_installation_can_use_tool_status_and_plain_language(self):
        answer = {"category": "HUMAN_REVIEW",
                  "summary": "installationStatus 为 UNCONFIRMED，台账记录值并不等于现场实际装机。"}
        self.assertTrue(verify.factory_installation_unconfirmed(answer))
        self.assertFalse(verify.factory_installation_unconfirmed(
            dict(answer, category="CONFIRMED_FINDING")))
        self.assertFalse(verify.factory_installation_unconfirmed(
            dict(answer, summary="installationStatus 为 UNCONFIRMED。")))
        self.assertTrue(verify.factory_installation_unconfirmed(
            dict(answer, summary="未取得现场核验，不能确认控制器当前实际固件版本。")))

    def test_maintenance_from_same_source_does_not_replace_configuration_citation(self):
        references = [{"sourceType": "CONFIGURATION", "sourceId": source, "excerpt": ";".join(terms)}
                      for source, terms in verify.FACTORY_CONFIGURATION_SOURCES.items()]
        references.append({"sourceType": "CONFIGURATION", "sourceId": "SC0245B-RETROFIT-2025",
                           "excerpt": "operation=REPLACEMENT;after=D-120B;validFrom=2025-09-15"})
        self.assertTrue(verify.factory_configuration_cited(references))
        self.assertFalse(verify.factory_configuration_cited(
            [reference for reference in references if "value=D-120B" not in reference["excerpt"]]))


class RetrievalDecisionTest(unittest.TestCase):
    def setUp(self):
        self.cases = json.loads((verify.EVAL / "cases.json").read_text("utf-8"))
        self.channels = {"HYBRID": {"coreHits": 6, "partHits": 2, "locatorHits": 6}}
        self.negatives = [{"id": case["id"], "pass": True} for case in self.cases
                          if verify.required_negative(case)]

    def test_all_required_negative_results_pass(self):
        self.assertTrue(verify.retrieval_passed(self.channels, self.negatives, self.cases))

    def test_wrong_version_cannot_pass(self):
        for result in self.negatives:
            if result["id"] == "current-version-only":
                result["pass"] = False
        self.assertFalse(verify.retrieval_passed(self.channels, self.negatives, self.cases))

    def test_missing_required_negative_cannot_pass(self):
        negatives = [result for result in self.negatives if result["id"] != "acl-cross-owner"]
        self.assertFalse(verify.retrieval_passed(self.channels, negatives, self.cases))


class AgentDecisionTest(unittest.TestCase):
    def setUp(self):
        self.document_id = "11111111-1111-1111-1111-111111111111"
        self.reference_id = "22222222-2222-2222-2222-222222222222"
        self.uploaded = {"documentId": self.document_id, "documentVersion": "2026.2"}
        self.reference = {"referenceId": self.reference_id, "sourceId": self.document_id,
                          "sourceVersion": "2026.2"}
        self.run = {"runId": "run-a", "status": "COMPLETED", "result": {
            "category": "POSSIBLE_CAUSES", "references": [self.reference],
            "claims": [{"certainty": "SUPPORTED", "referenceIds": [self.reference_id]}]
        }}
        self.excerpts = {self.reference_id: {"http": 200, "body": {
            "documentId": self.document_id, "section": "直流快充中断",
            "content": "直流快充过程中充电中止时，先读取充电桩故障日志并记录中止时间。"
        }}}
        self.tools = [{"toolName": "search_knowledge", "success": True, "referenceCount": 1, "sequence": 1},
                      {"toolName": "search_knowledge", "success": True, "referenceCount": 1, "sequence": 2}]

    def evaluate(self, suite="multi-turn-basic"):
        return verify.evaluate_agent(self.run, [self.reference], self.excerpts, self.tools,
                                     self.uploaded, suite)

    def test_normal_answer_requires_text_review(self):
        for suite in ("multi-turn-basic", "rag-smoke"):
            outcome = self.evaluate(suite)
            self.assertFalse(outcome["passed"])
            self.assertEqual("NOT_RUN", outcome["status"])
            self.assertEqual("NOT_RUN", outcome["evidence"]["e2e03UnknownAsset"])

    def test_free_text_diagnosis_never_passes_structured_check(self):
        for field in ("summary", "suggestions", "uncertainties", "limitations"):
            with self.subTest(field=field):
                self.run["result"][field] = ("已确认车辆电池故障" if field == "summary"
                                             else ["已确认车辆电池故障"])
                self.assertFalse(self.evaluate()["passed"])
                self.assertEqual("NOT_RUN", self.evaluate()["evidence"]["e2e03UnknownAsset"])
                del self.run["result"][field]
        self.run["result"]["claims"][0]["text"] = "已确认车辆电池故障"
        self.assertFalse(self.evaluate()["passed"])

    def test_answer_that_does_not_cite_uploaded_document_cannot_pass(self):
        other = {"referenceId": self.reference_id, "sourceId": "33333333-3333-3333-3333-333333333333",
                 "sourceVersion": "2026.2"}
        self.run["result"]["references"] = [other]
        outcome = verify.evaluate_agent(self.run, [other], self.excerpts, self.tools, self.uploaded,
                                        "multi-turn-basic")
        self.assertFalse(outcome["passed"])
        self.assertIn("ANSWER_DOES_NOT_CITE_UPLOADED_DOCUMENT", outcome["reasons"])

    def test_no_asset_guard_failure_cannot_pass(self):
        self.run["result"]["category"] = "CONFIRMED_FINDING"
        outcome = self.evaluate()
        self.assertFalse(outcome["passed"])
        self.assertEqual("FAIL", outcome["evidence"]["e2e03UnknownAsset"])

    def test_confirmed_claim_without_asset_cannot_pass(self):
        self.run["result"]["claims"][0]["certainty"] = "CONFIRMED"
        self.assertFalse(self.evaluate()["passed"])

    def test_missing_required_tool_evidence_cannot_pass(self):
        self.tools = []
        outcome = self.evaluate()
        self.assertFalse(outcome["passed"])
        self.assertIn("MISSING_REQUIRED_TOOL_EVIDENCE", outcome["reasons"])

    def test_only_search_before_reply_does_not_prove_search_after_reply(self):
        outcome = verify.evaluate_agent(self.run, [self.reference], self.excerpts, self.tools,
                                        self.uploaded, "multi-turn-basic", after_sequence=2)
        self.assertIn("MISSING_REQUIRED_TOOL_EVIDENCE", outcome["reasons"])

    def test_unreadable_expected_section_cannot_pass(self):
        self.excerpts[self.reference_id]["body"]["content"] = "无关章节"
        self.assertFalse(self.evaluate()["passed"])


class EnvironmentDecisionTest(unittest.TestCase):
    def test_missing_embedding_configuration_is_blocked(self):
        with self.assertRaisesRegex(RuntimeError, "BLOCKED_BY_ENV"):
            verify.expect((503, {"code": "EMBEDDING_CONFIGURATION_MISSING"}), 201)

    def test_missing_environment_is_blocked_with_exit_two(self):
        with tempfile.TemporaryDirectory() as directory, \
                mock.patch.object(verify, "ROOT", Path(directory)), \
                mock.patch.dict(os.environ, {}, clear=True), \
                mock.patch.object(sys, "argv", ["verify_general_agent.py", "--suite=rag-smoke",
                                                "--embedding=real"]):
            code = verify.main()
            reports = list((Path(directory) / "outputs").rglob("result.json"))
            self.assertEqual(2, code)
            self.assertEqual(1, len(reports))
            self.assertEqual("BLOCKED_BY_ENV", json.loads(reports[0].read_text("utf-8"))["status"])


if __name__ == "__main__":
    unittest.main()
