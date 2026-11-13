# V1.0 最小通用契约（schemaVersion = 1）

冻结日期：2026-11-09。范围是后续实现的输入、动作、来源、安全与保存边界；**本文件不代表这些 DTO/API/PG 行为已经实现**。当前实现差异见末表；运行机制选择见 [ADR-runtime.md](ADR-runtime.md)，测试原型范围见 [RUNTIME-SPIKE.md](RUNTIME-SPIKE.md)。需求基准为指定 V1.0-Draft.1 §4/§5/§7；旧结构化协议只用于 legacy。

## 1. 用户输入与服务器身份

```text
AgentTaskInput {
  schemaVersion: 1
  conversationId: UUID                  // 必填，且属于当前服务器身份
  userMessage: string                   // 必填，非空白，最多 8000 Unicode code points
  selectedAssetId?: string              // 可空；标识字段单独校验，不适用于自然语言
  attachments?: FileReference[]         // 默认 []，仅已授权、已登记的文件 ID
  contextRefs?: ContextReference[]      // 默认 []，已有报告/证据/文档 ID，不收任意路径/URL
  asOf?: Instant                        // 查询业务时点；未给时由服务器固定为 requestedAt
  replyTo?: {runId, pendingQuestionId, questionVersion}
}
ServerContext {ownerId, projectId, principalId, permissions, requestedAt, traceId}
```

- 原文按 UTF-8 原样保存并贯穿模型上下文；空白检查可用副本，不用抽取结果替代原文。数量/大小上限由 API 配置控制，超限在写消息/排任务之前整体拒绝。
- `domain/modelName/faultCode/symptomCode` 仅为可选线索；缺少资产、故障码、Catalog 条目或规则均不拒绝设备问题。未知资产可进行通用问答/追问，不创建虚构资产身份。
- `ownerId/projectId/permissions/requestedAt` **不是客户端或模型字段**；请求中冒传按未知字段拒绝。UUID 是定位符，不是认证凭据。多项目切换必须先经过服务器授权。
- 本地 Demo 可固定服务器身份；测试至少两种身份/项目。共享部署要真实认证后才能宣称隔离验收通过。
- 首次读取 conversation、run、asset、file、document、chunk、report 时检查 owner/project 与授权关系；附件/contextRefs 无权则整体拒绝；检索在形成候选集之前过滤，不在返回模型后补过滤。
- 无 selectedAssetId 时仍允许知识检索；资产工具必须要求明确且已授权的 assetId。模型建议资产不能扩展已授权范围。
- `FileReference={fileId}`；`ContextReference={kind: REPORT|EVIDENCE|DOCUMENT, id, version?}`。由服务端解引用并核验归属、版本、可读状态，不允许模型提交实体对象冒充来源。

## 2. 单步动作与工具协议

```text
CALL_TOOL {type, toolName, arguments: JsonObject, purpose?}
ASK_USER  {type, question: string, requestedFields?: string[]}
ANSWER    {type, answer: AnswerDraft}
```

每次模型尝试只接受一个动作，严格类型/必填/未知字段/枚举/重复键校验；拒绝多动作批次。模型不填写 owner、权限、待答问题 ID、状态版本或最终安全判定。非法输出最多一次纠正尝试，两次都计预算，仍失败为 `FAILED/MODEL_OUTPUT_INVALID`，不伪装为缺证。

`ToolDescriptor={name, description, inputSchema, outputSchema, domain, readOnly}`。
`ToolContext={ServerContext, conversationId, runId, selectedAssetId?, requestedAt, asOf, allowedTools}`。

- 目录和可执行范围均为“已注册 ∩ 服务器授权 ∩ 已激活 Skill 绑定（如有）”。Skill 内容/工具结果/附件全是不可信数据，不能提升权限。
- Gateway 逐工具验证 Schema：中文 `query` 允许空格与自然语言；`filters` 是有类型嵌套对象；UUID/资产/部件标识保留严格校验。禁止把旧 `Map<String,String> + Checks.id` 直接作为所有新工具合同。
- 参数化数据库查询由工具适配器构造；不收任意 SQL、脚本、文件系统路径或进程命令。所有设备能力只读；写 PG 消息/审计是应用行为，不授予设备控制权限。
- `ToolResult={success, requestId, toolName, data?, references: SourceReference[], error?, dataNature}`；success 与 error 互斥；未接入、空命中、无权、过期、歧义各有明确语义。
- 同一模型动作最多一次受控工具调用；服务器先核验工具返回的归属、时间、来源/版本，再纳入可引用集合。失败结果不可当成功证据。

## 3. 来源、时间与引用

```text
SourceReference {
  referenceId, runId, ownerId, projectId, sourceType,
  sourceId, sourceVersion, dataNature: SIMULATED|AUTHORIZED,
  documentId?, chunkId?, locator?: {page?, section?},
  assetId?, componentId?, validFrom?, validTo?, recordedAt?, observedAt?,
  asOf?, verificationStatus, retrievedAt, integrityStatus
}
```

- `sourceType` 至少区分 `KNOWLEDGE_DOCUMENT/FACTORY/MAINTENANCE/CONFIGURATION/FIELD_OBSERVATION/USER_STATEMENT`；文档引用含 document/version/chunk，page/section 至少其一；结构记录含 sourceId、资产与适用业务时间。
- `requestedAt` 是服务器收到本条消息的时间；`asOf` 是调查针对的业务时点；`recordedAt` 是来源入账时间；`observedAt` 是实际现场观测时间。不能拿最新入账时间自动覆盖业务现状。时间统一 ISO-8601 Instant，区间为 `[validFrom, validTo)`，未知端点为 null，不填猜测值。
- 用户补充是 `USER_STATEMENT/UNVERIFIED`，即使写“已换为 B”也不能升级为现场已核实。知识手册只支持一般原理，不支持“这台设备当前装 B”。
- 模型只提交 `referenceId`；引用必须属于本 run 已成功取回且当前身份仍可读取的集合。恢复时重验授权、撤回/过期/版本；保留历史引用身份，失效标记及原因不删历史，不自动替换为最新版。
- 普通无资料回答可说明“不知道/需要资料”，不得附伪造引用。声称有依据的断言必须绑定有效引用；空检索不是模型故障，不能编造文档/页码。

## 4. 回答与风险检查

```text
AnswerDraft {
  category: KNOWLEDGE|POSSIBLE_CAUSES|CONFIRMED_FINDING|HUMAN_REVIEW,
  summary, claims: [{text, referenceIds, certainty: POSSIBLE|SUPPORTED|CONFIRMED}],
  suggestions: string[], uncertainties: string[], limitations: string[], referenceIds: string[]
}
AnswerResult {schemaVersion, runId, conversationId, status, category, summary,
              claims, suggestions, uncertainties, limitations, references,
              riskLevel, safetyDisposition, dataNature}
```

风险由服务器根据请求、工具与答案内容评估，不能相信模型自报低风险；缺失风险信息按较严格策略处理。

| 风险/任务 | 可结束条件 | 缺证或冲突时 |
|---|---|---|
| LOW：手册解释/一般知识 | 引用与适用版本有效；可承认未知，无需模拟规则/全维修史 | 说明局限或追问，允许不确定回答 |
| MEDIUM：可能原因/安全自查 | 区分事实与推测，有引用及低风险边界 | 不宣称唯一根因，列需核验项 |
| HIGH：关键配置确认/派工依据 | 明确授权资产、asOf、来源、有效期、现场核实、无未决关键冲突 | 降为 HUMAN_REVIEW 或 ASK_USER，不输出 CONFIRMED |
| PROHIBITED：设备控制/危险外部执行 | V1.0 默认不执行，不注册可用控制能力 | 拒绝操作承诺，提供人工处理边界 |

运行 `COMPLETED` 只表示调查已产生有效回答，不等于故障排除。技术失败是 `FAILED`，无答案；用户等待是 `WAITING_FOR_USER`，不是调查终局。原 `RESOLVED/NEED_EVIDENCE/CONFLICT/ESCALATE` 仅留 legacy 结果，不套用于所有通用问答。

## 5. run、追问与原子保存（M1 必须实现）

`AgentRun={schemaVersion, runId, conversationId, ownerId, projectId, goalMessageId, status, version, requestedAt, asOf, pendingQuestion?, budget, resultRef?, createdAt, updatedAt}`。

`PendingQuestion={pendingQuestionId, questionVersion, question, requestedFields, askedAt, answeredByMessageId?}`：问题 ID 由服务器生成；版本从 1 单调递增，已消费问题不可再次激活；一个 run 至多一条待答问题。

```text
首次提交 -> QUEUED -> RUNNING
RUNNING --ASK_USER--> WAITING_FOR_USER
WAITING_FOR_USER --关联补答--> QUEUED -> RUNNING
RUNNING --有效 ANSWER--> COMPLETED
执行技术失败 -> FAILED
取消 -> CANCELLED（M2 可靠性实现）
```

1. 首次提交在一个 PG 事务写用户消息、run/QUEUED 与初始事件，全部同 owner/project；事务提交之后才调度。M0（01-02）到此为止，不启动 worker。
2. ASK_USER 在一个 PG 事务写 assistant 问题消息、pendingQuestion ID/版本、调查摘要、可重建的已提交工具输出/引用、累计预算、WAITING 状态及事件。失败全部回滚；提交成功后退出执行调用、释放 worker，不在原线程等待用户。
3. 客户端补答提交 `replyTo` 三元组。服务器校验 conversation/run 归属、WAITING、question ID/版本未消费；不完整关联返回 `REPLY_TARGET_REQUIRED`，错配/已消费/过期返回 `STALE_OR_UNRELATED_REPLY`，不得偷偷创建另一个 run。
4. 补答事务同时写用户消息、附件/context 引用、消费问题、run/QUEUED 与事件；同 runId 不变。失败不消费问题。没有 replyTo 的普通消息启动新任务，不自动猜测等待问题。
5. 下一次 worker 从 PG 读消息、摘要、有效引用及待答/已答记录，新建 Context；不能依赖原 Java Context/SDK Memory/线程。M1 只承诺单进程正常补证路径：新上下文有原始目标、补充回答、之前来源与累计步数，可继续检索/回答。
6. 读会话、run、消息/问题、报告、引用均以持久层为准，每次再次授权；事件序号单调，`(run_id, seq)` 唯一。公共读取走安全 DTO，原始 Prompt/堆栈/本地路径不可公开。

## 6. M2 检查点与执行预算

预留 `RunCheckpoint={schemaVersion, runId, runVersion, status, goalMessageId, messageCursor, contextSummary, referenceIds, toolOutputRefs, pendingQuestion, budget, lastCommittedEventSeq, evidenceValidation, modelProfileVersion, skillVersions}`。

- M2（03-02/03-03）才交付 PG 检查点跨进程重建、启动扫描 QUEUED/WAITING/RUNNING、租约所有者/期限、CAS、防并发恢复、迟到模型结果处理、请求/补答幂等与取消/超时。M1 的原子条件更新不等于这一整套可靠性验收。
- 等待态原样保留；遗留 RUNNING 只能按已提交检查点恢复，或明确 `FAILED/RECOVERY_UNSAFE`。未提交外部动作不能伪称已完成。不得让幽灵队列永久停留而不报告。
- `Budget={limits:{maxSteps,maxRepeatedActions,maxActiveDurationMs,maxTokens?,maxCost?}, used:{steps,modelAttempts,toolCalls,activeDurationMs,inputTokens?,outputTokens?,cost?}, usageStatus: KNOWN|PARTIAL|UNKNOWN}`。
- 同 run 多次续跑共用累积账，不因 Context/线程/进程重建而清零；错误尝试计入执行次数；等待用户不占 worker/活跃执行时长，单独记录等待时长。Token/成本缺数据填 null + UNKNOWN，不写 0 冒充免费；有总期限时明确为服务器固定 deadline。
- budget 耗尽不是硬件确诊；有足够安全依据可生成有限总结，否则停止为结构化预算错误/人工处理，不能继续无限重试。
- schemaVersion 不认识或缺失时禁止盲反序列化；采用显式迁移或 `SCHEMA_VERSION_UNSUPPORTED`，保留原快照。数据库 Flyway 版本、协议版本、模型/embedding profile 版本分别记录，不混为一个数字。

## 7. 错误与公开事件

`ErrorDto={code, message, retryable, traceId}`：message 由服务器白名单映射，不回显模型正文、异常、请求头、配置值或文件路径。

| code | 语义/公开响应 |
|---|---|
| INVALID_REQUEST / INVALID_ARGUMENTS | 请求/工具参数不合法；HTTP 400 |
| NOT_FOUND_OR_DENIED | 公开对象不存在或无权读取统一 HTTP 404；内部审计可区分 DENIED |
| NOT_FOUND / DATA_STALE / AMBIGUOUS | 工具数据不存在/过期/歧义；保持语义不混成模型失败 |
| UNKNOWN_CAPABILITY / NOT_READ_ONLY / DENIED | Gateway 拒绝；无重试扩权，不执行 handler |
| STALE_OR_UNRELATED_REPLY | 补答关联失效；HTTP 409，状态不变 |
| REPLY_TARGET_REQUIRED | 补答缺关联字段；HTTP 400 |
| INVALID_CITATION / UNSAFE_ANSWER | 拒绝答案，可有界修正；不能保存为确认结论 |
| MODEL_CONFIGURATION_MISSING / MODEL_TRANSPORT_OR_TIMEOUT / MODEL_OUTPUT_INVALID | 技术失败；不自动降级 scripted，不当作诊断结果 |
| QUEUE_REJECTED / BUDGET_EXHAUSTED / RECOVERY_UNSAFE / SCHEMA_VERSION_UNSUPPORTED | 生命周期/预算/恢复失败；持久终态及安全原因 |

公共 `RunEventView={runId, seq, type, recordedAt, safePayload}`：type 仅状态变化、工具开始/完成/错误、引用接受/失效、追问、回答、安全错误等白名单；safePayload 逐字段构造。SDK 原始事件和 Reasoner 输入不是公开事件。SSE 与历史复用同一映射（M4），本计划不实现 SSE。

## 8. 服务端配置与 legacy 兼容

> 2026-11-12 用户调整：embedding 模型配置和密钥可复用已忽略的 `devicetruth.local.properties`；显式 embedding 环境变量优先于文件，PG 配置仍走服务端环境。以下原决策作为历史保留。

- 新 PG 用 Spring 服务端环境注入 `SPRING_DATASOURCE_URL/USERNAME/PASSWORD`；embedding 用服务端 `DEVICETRUTH_EMBEDDING_BASE_URL/API_KEY/MODEL/DIMENSION/PROFILE_VERSION`（02-01 实现并验证 profile），不放浏览器、Skill、附件、报告或已提交配置；本计划不选 PG 镜像版本/向量维度。
- 现有模型 `ModelConfiguration` 保留已授权的本地私有文件兼容：`devicetruth.local.properties` 非空值优先、环境补缺；仅 legacy 模型读取，文件可选，不打印/提交/打包。新 PG/embedding 配置不复用该文件；后续通用模型连接可复用现有供应商适配但不可扩大私有文件覆盖范围。本计划不读取真实私密值，不改加载优先级。
- legacy CLI 保留 `DiagnosisRequest`、五动作、`DecisionGuard`、JSON fixtures 和原输出；E 只作测试。普通问答不经过 Catalog/全量调查门禁。
- 采用演进既有受控循环方向；GeneralAgentService 是应用入口，TaskOrchestrator 演进提供通用单步/等待边界，StructuredDiagnosisAdapter/legacy 策略保留旧严格行为；不让 SDK ReAct 与产品循环相互嵌套。生产改造在 02-02，实现前细化适配，不在 01-01 抢做。

## 9. 契约覆盖与当前差异

| 合同 | 覆盖需求 | 当前源码与后续计划 |
|---|---|---|
| §1 自由目标/可空资产/原文/附件 | ENTRY-01–05、TRUST-04 | 当前 MessageRequest 无引用，DiagnosisRequest 四字段必填，原文不到 Core；01-02 持久入口，02-02 自由 Agent，02-03 附件 |
| §2 动作/工具/权限 | AGENT-01/02、TOOL-01/02、TRUST-01 | 当前 Reasoner.Context 绑定 DiagnosisState，ActionProposal 五动作，Gateway 全参数 ID 校验；02-01/02-02 改造 |
| §3 来源/时间 | STORE-03、KNOW-05/06/08、TRUST-02 | 当前 SourceView 无 document/version/locator，证据无项目 ACL；02-01 起实现，03-01 扩资产时序 |
| §4 风险/回答 | AGENT-05/08、TRUST-05 | 当前 DecisionGuard 强制模拟规则/调查完整性；02-02 引入风险策略，保留 legacy |
| §5 同 run 等待/补答 | AGENT-03、STORE-02/05、WEB-06 | 当前 DiagnosisRunService 内存队列，RequestInformation 结束；01-02 保存 QUEUED，02-02 真正基本补答 |
| §6 检查点/预算 | AGENT-04/06/07、STORE-04、OPS-01/03 | 当前 StateManager 仅内存版本；03-02/03-03 交付可靠性；Hypothesis 可选 |
| §7 公开数据 | TRUST-03、WEB-03 | 复用 SafePublicFields/RunEventViews；05-01 实现 SSE |
| §8 配置/legacy | BASE-01/02、TRUST-06 | 本轮保留旧依赖/配置、跑回归；新凭据仅服务端，后续实现时测脱敏 |

审阅方式：逐条对照 REQUIREMENTS 及上述实际 DTO；原型只验证状态与网关机制。PG 原子性、生产自由入口、真实检索、风险策略及在线质量均不能用本文件或原型替代验收。
