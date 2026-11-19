# 验收记录：improve-agent-result-availability

日期：2026-11-19

## 交付内容

- 通用动作新增 `CALL_TOOLS`，每次最多 3 个独立只读查询；Skill 激活与参考资料读取仍只能单步执行。
- 宿主按连续无新增信息、最后一步和最终回答时间预留进入 `FINALIZING`，禁止继续调查。
- 真实模型单次等待取固定 45 秒与 Run 剩余时间的较小值，协议重试共享剩余预算。
- 已有工具执行记录时，模型、协议、预算或答案审核失败可交付显式 `limited` 的 `HUMAN_REVIEW` 结果；首次模型失败仍保留真实技术失败。
- 有限结果不生成候选故障原因，只保留真实引用、原失败原因和人工核验要求。引用、设备匹配、未核验不确诊、危险操作和 `SIMULATED` 门禁未放宽。
- 新增 `RUN_PHASE`/`RUN_DEGRADED` 白名单事件及前端时间线显示，不改变 Run/Result API 形状。

## 自动化验证

| 项目 | 实际命令 | 结果 |
| --- | --- | --- |
| 后端全量 | 使用一次性 PG 和测试专用凭据执行 `mvn -q test` | 51 个 suite，423 项；0 失败、0 错误、4 跳过 |
| 前端单测 | `cd frontend && npm run test -- --run` | 9 个文件，101 项全部通过 |
| 前端构建 | `cd frontend && npm run build` | 通过，含 `vue-tsc --noEmit` |
| OpenSpec | `openspec validate "improve-agent-result-availability" --strict` | 通过 |
| 可运行 JAR | `mvn -q -DskipTests package` | 通过；测试已在上一项执行，未重复运行 |
| 差异检查 | `git diff --check` | 无代码空白错误，仅有 LF→CRLF 提示 |

Maven 实际使用 Java 21.0.13、Maven 3.9.9。PG 集成测试使用临时 `pgvector/pgvector:pg16` 容器，测试完成后已删除；没有读取本地私有凭据。

## 失败与修复历史（保留）

1. 初次批量协议测试发现嵌套 `CallTool` 受动作多态规则影响，合法批次无法反序列化；改为无 `type` 的 `ToolCall` 批次元素后通过。
2. 无进展初版把首次空结果和 Map 键顺序变化误判为有效进展；改为确定性状态指纹并排除空结果后通过。
3. 直接运行 `RunRecoveryTest` 曾因缺少 `DEVICETRUTH_TEST_PG_BASE_URL` 初始化失败；随后使用一次性 PG 执行，未记为通过。
4. 第一次全量 Maven 为 423 项、8 失败、0 错误、4 跳过：3 项为危险/未核验/伪造引用旧失败预期，2 项为追问提前收口，2 项为流式事件数量，1 项为启动交接预期。逐项更新有限结果与新增事件断言，并将 scripted 的关键信息追问提前到首次检索后。
5. 第二次全量 Maven 全部通过，结果见上表。
6. 首次 Docker 构建复用了旧 JAR 缓存；随后先执行 `package` 更新 JAR，再重建后端，确认 JAR 含 `ActionProposal$CallTools`。

## 部署状态

- 执行 `docker compose --profile product up -d --build backend` 重建真实 product 后端。
- `http://127.0.0.1:8080/api/health` 返回 `{"status":"UP","dataNature":"SIMULATED"}`，容器 healthy。
- `http://127.0.0.1:5173/` 返回 HTTP 200，供用户手动操作。
- 未修改历史失败 Run，包括此前的 `TOTAL_TIMEOUT`、模型传输超时和协议失败记录。

## 未验证项与边界

- 按用户要求，没有执行浏览器模拟点击或移动端检查；真实端到端操作由用户手动验收。
- 本次未调用外部真实大模型生成新 Run，因此尚不能证明真实模型一定会主动使用 `CALL_TOOLS`，也不能宣称线上慢响应已完全消除。部署保留原有真实模型配置，健康探针不等同于真模可用。
- 协议替身、离线脚本、真实 PG 和外部真实模型分别属于不同验证层级，本记录不混用。
- 资产、观测和本次结果仍为 `SIMULATED`；未验证真实现场设备。
- 变更未归档、未提交 Git。


## 后续修复：反复出现 INVALID_ANSWER（2026-11-19）

用户在真实操作 Run `e61f2dc7-38bf-4df7-960b-f010a97704a0` 时反复看到统一人工复核结果。该 Run 的批量工具查询成功，但最终答案被 `INVALID_ANSWER` 拒绝并降级；另一次 Run `eb1a9572-c8ff-4969-9d81-4697c6b18c63` 的原因为 `UNCONFIRMED_FINDING`，两者不是同一错误。

本次按用户要求放宽非安全的展示分类约束：

- `DIRECT + kind=UNKNOWN` 不再否决整份答案，服务端将 `relevance` 保守改为 `UNKNOWN`，不列入直接候选。
- `UNRELATED` 的 `CAUSE/CONCLUSION` 保留为背景信息，不再单独触发 `INVALID_ANSWER`。
- 引用归属、设备对象、危险操作、未核验确诊及 `SIMULATED` 规则保持不变。
- 提示词进一步明确未知类型不得搭配 DIRECT 相关性。

验证：`AnswerGuardTest` 和 `AgentToolFeedbackTest` 定向通过后，再次执行全量 Maven，51 个 suite、423 项，0 失败、0 错误、4 跳过；前端 101 项及构建再次通过；OpenSpec 严格校验通过。已重新执行 `mvn -q -DskipTests package` 和 `docker compose --profile product up -d --build backend`，健康检查返回 `UP`。临时 PG 已删除。

未验证项：本次仍未代替用户执行真实模型端到端操作，需要用户重新提交同类问题确认不再因上述分类组合进入统一降级结果。未核验确诊和危险操作仍可能按设计形成有限结果。
