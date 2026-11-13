# Phase 03 acceptance - 2026-11-13 closeout (user decision)

## 2026-11-13 Phase 3 → Phase 4 补验

- 修复总时限越过模型返回边界的问题。真实 PostgreSQL 的 `AgentLifecycleTest` 17/17，包含迟到追问和答案不得落库、同一任务补答后用量累计、取消、容量和幂等。有效租约版本在提交时再次比对。
- 使用本机 OpenAI 兼容 HTTP 替身，确认返回的 Token、十进制费用和显式零值可读取。未报告的字段保持空值，跨尝试部分报告标记 `PARTIAL`。替身结果只验证协议和落库。
- 实际 Chrome + Vue + 打包 HTTP + PostgreSQL 验证同一 run。刷新、重开浏览器和重启服务后均可找回待答问题，补答后显示原文引用，切换历史会话可恢复答案。证据在 `outputs/validation/browser-recovery-ab03f90c59984713a8a4300d6a451ea1/result.json`。
- 打包 HTTP investigation 套件通过。同 run 补答、引用回读、幂等和取消均有独立断言。证据在 `outputs/validation/general-agent/investigation-709c971755db48dbad01d5a450d96746/result.json`。
- 最终 `mvn -B -o package` 使用真实 PostgreSQL 16.15 临时库，343 项，342 通过、0 失败、0 错误、1 跳过。Windows 当前权限不支持 Skill 符号链接场景。旧 CLI、前端 18/18 及生产构建、Python 验收脚本 13/13 均通过。
- 用户随后明确授权，实际执行 DeepSeek 模型与 DashScope embedding 的 `multi-turn-basic`。同一 run 从 `WAITING_FOR_USER` 补答至 `COMPLETED`，补答后检索及上传手册章节回读成功。用量从 2,515 / 614 Token 累计至 20,129 / 4,224，状态 `REPORTED`，供应商未报告费用。原脚本将正文判断保留为 `NOT_RUN`，所以整组原始状态仍为 `NOT_RUN`。单独复核了本次模拟答案正文：明确只给可能原因、保留现场未知、将高压检查转给资质人员。该复核仅覆盖这条答案。证据在 `outputs/validation/general-agent/multi-turn-basic-763b0848174d42b482dd38b89cea7810/result.json`。保留原关闭决定及 Phase 5 遗留项。

以下为原阶段关闭记录。

Phase CLOSED by explicit user decision with recorded leftovers (same policy as Phase 2).
Final state: plans 03-01 / 03-02 / 03-03 all executed, summarized, security-audited, validated and verified.

## Final counts (actual execution evidence)

- Full regression (real PG, orchestrator re-run after wave 2 and wave 3): 317/0/0/1 then 329/0/0/1 (final).
- Frontend contract tests 18/18; vue-tsc + Vite build pass.
- CLI verification `verify_cli.py` PASS; `verify_general_agent.py --suite=investigation` PASS.
- Per-plan: AssetToolTest 25/25; ResourceAuthorizationTest 8/8; RunRecoveryTest 9/9; AgentReportToolTest 4/4; AgentLifecycleTest 15/15 (incl. 3 Nyquist gap tests, ba7bf4f).
- Regression gate (prior phases 1/2): PersistentConversationTest 8/8, ServerHttpTest 16/16, RuntimeComparisonTest 1/1 — all green inside the final full suite.
- Security: 03-SECURITY.md retroactive-STRIDE, 14/14 threats CLOSED, threats_open 0 (accepted risk AR-03-01: fixed local identity until 05-03).
- Validation: 03-VALIDATION.md, nyquist_compliant true after 1 gap group fixed (AGENT-04 budget path).
- Verification: 03-VERIFICATION.md status gaps_found, score 8/9 must-haves; verifier independently re-ran key behavior tests on a temporary PG role (dropped after).

## Gap kept open (not fixed, explicitly deferred)

- TOOL-03 DATA_STALE / AMBIGUOUS error codes: declared in AssetFacts.FailureCode but never produced by tool paths. REQUIREMENTS.md already records "partial"; deferral now explicitly scheduled to Phase 05 (质量交付) per user close decision.

## Human verification items (NOT_RUN, must not be read as passed)

1. Browser two-round Q&A / refresh / restart demo (WEB-02 D6) — code + contract tests green, runtime UX not human-tested.
2. Real model (qwen/openai) + real embedding link — no key/quota this run; usage stays null/UNKNOWN, no fabricated numbers.
3. PDF/OCR import — not implemented, scheduled to Phase 05.
4. Real multi-user authentication — 05-03 (accepted risk AR-03-01).

## Not executed this closeout

- Phase-level code review (03-REVIEW.md): dispatched once, stopped by user mid-run; per-plan review for 03-01 exists (03-01-REVIEW.md PASS). 03-02/03-03 reviewed indirectly via security auditor + verifier spot-checks, but no dedicated adversarial code review artifact. Deferred; may be run later via $gsd-code-review 3.

History before this closeout is preserved in git (03-01 acceptance text, SUMMARY/REVIEW/UAT files).
