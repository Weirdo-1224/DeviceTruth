# 02-02 独立复审（2026-11-11）

**结论：用户要求带已知问题通过本计划。** 这是对计划推进的明确决定，不表示以下问题已经修复、安全测试通过或 Phase 2 已完成。不得将此记录用于证明系统可安全指导真实设备操作。

## 未关闭问题

1. **P1 — 危险建议仍可输出。** `AnswerGuard.recommendsControlAction` 将同一子句中任意禁止词视作放行条件；独立 Java 探针向 `target/classes` 提交“不要短接互锁端子，随后短接高压互锁端子。”得到 `allowed=true`。高风险固定提示只是附加内容，模型正文仍原样返回。需要服务端受控输出并验证肯定操作与否定提示混合的变体。
2. **P1 — 未核验的确定性故障结论仍可输出。** `AnswerGuard.assertsDeviceDefect` 依赖设备指代及缓和词；独立探针提交“该设备可能有多种故障，充电控制器已经损坏。”得到 `allowed=true`。其他复现包括“故障原因是充电控制器损坏”。需要让现场事实以服务端校验过的结构和证据形成展示，知识资料不得冒充现场核验。
3. **P1 — 追问绕过答案安全检查。** `TaskOrchestrator.runAgent` 的 `ASK_USER` 直接保存问题和 requestedFields；前端展示问题。模型可以在追问中夹带设备操作指令；此项为源码调用链确认，未独立运行危险追问探针。需要对所有可展示的追问字段应用相同的安全边界并补端到端负例。
4. **P2 — 否定性描述被误拒。** “尚未确诊为控制器损坏”包含 `确诊为`，被误判为已确认；独立守卫探针返回 `UNCONFIRMED_FINDING`。需要区分否定性说明。

## 实际复审验证

- PostgreSQL 16.15、JDK 21：`mvn -o -q test`，229 项，失败 0、错误 0、跳过 0；现有测试未覆盖上述反例。
- `frontend: npm.cmd run test -- --run`：12/12；`npm.cmd run build`：通过。
- GSD `verify-summary .planning/phases/02-open-rag/02-02-SUMMARY.md`：`passed=true`、引用文件2/2；`commits_exist=false`（遵循不自动提交）、`self_check=not_found`。首次将代码块中的整条 Python 命令误识别为文件，调整命令展示后重跑通过。
- 临时 Java 守卫探针使用本轮编译的 `target/classes`，进程退出 0；编译有缺少 Jackson 注解类的警告，仍打印两个 `allowed=true`。否定性说明探针同样退出 0。
- 真实外部模型、真实 embedding、浏览器 E2E、真实现场与跨进程恢复本轮未验证。上一轮验证的 105 条 QUEUED 交接回归包含在本次全量测试中。

按用户要求接受问题并推进，不代表 `TRUST-05` 完整实现。下次处理相关安全能力时需先处理上述问题；02-03 尚未获实施授权。
