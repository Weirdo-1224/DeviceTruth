# Phase 01 API／SDK 能力覆盖与验收证据

> 2026-11-10本轮验收补记：独立Review初审发现HTTP JSON宽松转换，复用严格mapper修复后独立复审clean；当前clean test/package均154/0/0/0（PG8、旧HTTP16），打包CLI与真实PG跨Java进程读回通过，见[01-VALIDATION](01-VALIDATION.md)。新增原始JSON回归方法为`PersistentConversationTest.malformedJsonIsRejectedWithoutPersistingCoercedOrAmbiguousInput`；另补GET消息成功及contextRefs拒绝的断言。逐项用户验收见[01-UAT](01-UAT.md)，尚待确认；出口后提交Git并仅执行02-01已获授权，02-02/02-03不执行。下方51项/PG7项等为先前覆盖文档核验的历史记录，不冒充本轮结果。

核对日期：2026-11-10（Asia/Shanghai）。范围：M0 的 [01-01](01-01-PLAN.md)／[01-02](01-02-PLAN.md)，以及它们继承的 CLI、HTTP 和模型连接；以当前工作树为准。需求依据为 [V1.0 产品设计](../../../docs/DeviceTruth_V1.0_通用设备检修Agent_完整产品设计方案_PostgreSQL.md) §7.3／§8、[ROADMAP](../../ROADMAP.md) Phase 1、[CONTRACTS](CONTRACTS.md) 和 [ADR-runtime](ADR-runtime.md)。API 路径按实际 Controller 核对，不把设计建议当成已发布接口。

**结论：本阶段已接入持久会话、原始消息、QUEUED run、事务与归属约束；继承 AgentScope 单步 Model API 和旧诊断链。通用 Agent 执行、RAG、生产追问恢复和 SSE 尚未接入。** 文档自定义覆盖检查与 GSD `verify:pre` 门禁分别记录；两者通过也不等于 M0 独立 Review、阶段关闭或用户逐项验收通过，不授权执行 02-01。

## 1. 状态与证据口径

| 状态 | 在本文中的含义 |
|---|---|
| 已接入 | 存在生产入口／装配和可定位的行为测试；必须同时读本行的范围限制 |
| 仅原型验证 | 只在 `src/test` 比较候选方案，不进入生产运行链 |
| 后续接入 | 本阶段明确不接入，已有后续计划和理由；不得记为本阶段实现 |
| 明确不采用 | 当前架构或 V1.0 范围排除，理由和替代责任明确；重新选择须另行决策 |

证据分两期：**本次复测**为 2026-11-10 的 11 个 suite、51 项测试；**历史已验证**为 2026-11-09 的真实 PG 7 项、全量 153 项、package／CLI 和打包 JAR 跨进程读取，见 [01-02-SUMMARY](01-02-SUMMARY.md) 与 [VALIDATION](../../VALIDATION.md)。本次未配置 PG 测试环境，没有重跑 PG、全量构建或打包重启，不能将历史结果写成本次通过。未接入能力没有行为测试的，明确记“未验证”；源码扫描只证明当前装配／引用范围。

### 1.1 GSD 规范决策矩阵

GSD 1.20.1 只识别首列为 `capability`、决策为 `INTEGRATE`／`OPT-OUT` 的三列表；下文中文状态表用于详细说明，不能代替此表。此矩阵逐项映射 §2–5 已列出的集成面，不改变原有选型或实施范围。`INTEGRATE` 表示本阶段纳入的能力范围，不表示本次已重新执行其测试；`OPT-OUT` 表示本阶段不接入所列生产能力，具体理由、后续归属及原型边界保留在 reason 与详细表中。

| capability | decision | reason |
|---|---|---|
| API-01 POST /api/conversations | INTEGRATE | M0 创建持久会话；服务器固定身份；详见 §2、PG-01 |
| API-02 GET /api/conversations | INTEGRATE | M0 仅列当前身份会话；详见 §2、PG-05 |
| API-03 GET /api/conversations/{id} | INTEGRATE | M0 读取持久消息与 run 摘要；存储重开不等于执行恢复；PG-01、PG-05 |
| API-04 GET /api/conversations/{id}/messages | INTEGRATE | M0 按归属读消息；本轮在PG-01补成功响应与会话明细消息一致的断言；见01-VALIDATION |
| API-05 POST /api/conversations/{id}/messages | INTEGRATE | 仅 M0 原文、QUEUED run、首事件的原子保存；PG-01–07；不含 worker 或补答 |
| API-06 GET /api/runs/{id} | INTEGRATE | 仅 M0 QUEUED 摘要；没有执行进度或答案；PG-01、PG-05 |
| API-07 GET /api/runs/{id}/events | OPT-OUT | M0 无执行事件链；03-02 补持久事件基础，05-01 接公开历史回放；未验证该路由 |
| API-08 GET /api/runs/{id}/stream | OPT-OUT | M0 无 SSE 装配；05-01 接安全事件和断线续传；未验证 |
| API-09 POST /api/files | OPT-OUT | M0 无受控上传和文件授权；02-03 负责上传、校验与解引用；未验证 |
| API-10 POST /api/knowledge/import | OPT-OUT | M0 无知识导入与 embedding；02-01 接基础检索，02-03 补 PDF 和混合检索；未验证 |
| API-11 GET /api/assets/{id} | OPT-OUT | M0 仅保存可空资产 ID；03-01 接资产来源、时间与权限；未验证 |
| INPUT-01 自由原文、可空资产与时间 | INTEGRATE | 仅保存与输入校验；不做 Catalog 准入；§3、PG-01、PG-07 |
| INPUT-02 服务端 owner/project 与安全错误 | INTEGRATE | 仅本地固定身份、归属过滤和错误白名单；真实认证留 05-03；§3、PG-05、PG-06 |
| STORE-ATOMIC 消息、run、首事件与版本事务 | INTEGRATE | M0 同事务保存和故障回滚；§3、PG-02–06 |
| REQUEST-KEY 同会话重复键拒绝 | INTEGRATE | M0 仅保证重复键 409 与单个 run；完整幂等重放留 03-03；PG-02、PG-03 |
| INPUT-REFERENCES 非空附件与上下文引用解引用 | OPT-OUT | M0 整体拒绝，避免静默丢弃；02-03 接授权解引用；§3 保留分支测试限制 |
| INPUT-REPLY 生产追问与同 run 补答 | OPT-OUT | M0 replyTo 被整体拒绝；02-02 接正常路径；测试原型不能当生产实现 |
| RESULT-GENERAL 通用工具动作、回答、引用与风险结果 | OPT-OUT | M0 RunView 仅有摘要；02-01／02-02 接通用协议与校验；原型只覆盖子集 |
| RUNTIME-WORKER 执行、检查点、累计预算与取消 | OPT-OUT | M0 只登记 QUEUED；02-02 接调度，03-02／03-03 接可靠性；预留字段不代表实现 |
| LEGACY-SESSIONS 旧会话与消息接口 | INTEGRATE | 保留兼容内存会话；不自动迁成产品 PG 会话；§4、LEGACY-01、LEGACY-02 |
| LEGACY-DIAGNOSES 旧诊断与结束后事件接口 | INTEGRATE | 保留旧 Core 有界异步诊断及归属校验；不是产品 SSE；LEGACY-03、LEGACY-04 |
| LEGACY-ENTRY 健康检查与 CLI／HTTP 双入口 | INTEGRATE | 保留独立入口和 CLI 无数据库运行；测试日期与范围见 §4、SUMMARY |
| LEGACY-ASSET-TOOLS 模拟资产配置与历史读取 | INTEGRATE | 只读 JSON fixture，不代表 PG 资产 API 或真实设备读取；GATE-01、LEGACY-02 |
| LEGACY-EVIDENCE-TOOLS 模拟证据搜索、读取与规则查询 | INTEGRATE | 保留网关只读能力，不是向量检索或标准 Skill；GATE-01、LEGACY-05 |
| UNSAFE-TOOLS 任意工具、跨权读取与设备写入 | OPT-OUT | 只允许注册、只读且通过范围校验的工具；模型不能自行扩权；GATE-02、GATE-03、SPIKE-01 |
| SDK-01 Model、Msg、GenerateOptions 与 JSON response | INTEGRATE | 保留受控单步模型调用，SDK 工具列表为空；§5、SDK-TEST-01 |
| SDK-02 DashScopeChatModel／Qwen | INTEGRATE | 保留旧模型适配；fake 验证不代表真实在线质量；SDK-TEST-01、SDK-TEST-03 |
| SDK-03 OpenAIChatModel／兼容 Chat Completions | INTEGRATE | 保留旧模型适配与本机 HTTP 合同；未验证真实供应商；SDK-TEST-02 |
| SDK-04 单步超时、重试与配置读取 | INTEGRATE | 保留受控尝试、超时及配置兼容；不启用自动备用模型或静默降级；SDK-TEST-01–04 |
| SDK-05 ReActAgent／Toolkit／Hook 生产主循环 | OPT-OUT | 仅比较原型验证；ADR 选择演进 TaskOrchestrator，02-02 改造；不引入生产双循环 |
| SDK-06 SDK Memory／stateStore 权威状态 | OPT-OUT | 权威状态由应用模型与 PG 事务承担；SDK 持久恢复未验证，不替代 03-02／03-03 |
| SDK-07 SDK 原生工具自动执行、meta tools 与 Skill 代码执行 | OPT-OUT | Gateway 统一授权和执行；04-01 接领域指引；不开放任意脚本 |
| SDK-08 token streaming／SDK 原始事件直出 | OPT-OUT | M0 收集完整文本；05-01 接应用白名单事件；原始 SDK 事件不直接公开 |
| SDK-09 SDK embedding／RAG／MCP／文件或 shell 扩展 | OPT-OUT | 02-01 通过服务端受控适配接知识能力；MCP 和通用文件执行不在本阶段需要范围 |
| SDK-10 Spring AI Alibaba 或另一套 Agent 框架 | OPT-OUT | 本轮只保留一套产品循环；未有新增框架降低成本的证据；重新选型须另作 ADR |
| DB-01 Spring JDBC、Hikari 与 Flyway | INTEGRATE | product 显式装配、参数化 SQL 与事务；不混入 ORM；§5、PG-01–06 |
| DB-02 PostgreSQL／pgvector 基础 | INTEGRATE | 仅 M0 会话、消息、run、event 与 vector 扩展；向量检索未实现，留 02-01／02-03 |
| DB-03 Testcontainers、另拉 PG 与全栈 Compose | OPT-OUT | 用户指定复用 interview-postgres；随机独立测试库；全栈部署留 05-03，备用 Compose 不代表已交付 |

## 2. 产品 HTTP API 覆盖（逐条对应设计 §7.3）

以下六个已接入路由均由 `AgentConversationController` 提供，**只在独立 HTTP 的 `product` profile 下装配**。`ServerConfiguration` 显式导入 `ProductConfiguration`，后者创建 JDBC／事务／仓库／服务；默认 legacy HTTP 和 CLI 不装配产品数据库。

| ID | API | 状态 | 实现与实际行为 | 测试证据／本阶段不接入的理由及归属 |
|---|---|---|---|---|
| API-01 | `POST /api/conversations` | 已接入 | `AgentConversationController.create → GeneralAgentService.create → ConversationRepository.create`；空 body 创建，201＋Location | PG-01；仅服务器固定 owner/project，不接客户端身份 |
| API-02 | `GET /api/conversations` | 已接入 | `list → GeneralAgentService.list → ConversationRepository.list`；只列当前身份会话，设计之外的历史列表补充路由 | PG-05；仅本地固定身份授权合同，非真实登录验收 |
| API-03 | `GET /api/conversations/{id}` | 已接入 | `read → GeneralAgentService.read`；事务内读取同一会话版本、消息及 run 摘要 | PG-01、PG-05；重启读回存储，不执行恢复 |
| API-04 | `GET /api/conversations/{id}/messages` | 已接入 | `messages → GeneralAgentService.read`；归属核验后读取原始消息，设计之外的消息列表补充路由 | PG-05；成功消息内容由 PG-01 的详情读取断言覆盖，未单列此 GET 成功路径断言 |
| API-05 | `POST /api/conversations/{id}/messages` | 已接入（M0 切片） | `submit → GeneralAgentService.submit`；必填 UUID `Idempotency-Key`、`schemaVersion=1`、非空原文，资产可空；同事务写消息／QUEUED run／首事件／会话版本，202＋run Location | PG-01–PG-07；尚无 worker，202 仅表示保存成功；附件／contextRefs／补答限制见 §3 |
| API-06 | `GET /api/runs/{id}` | 已接入（摘要） | `run → GeneralAgentService.run → AgentRunRepository.require`；按归属返回持久 QUEUED 摘要、时点及 SIMULATED | PG-01、PG-05；没有运行进度推进或最终答案，不把摘要当通用调查结果 |
| API-07 | `GET /api/runs/{id}/events` | 后续接入 | 只保存 `RUN_QUEUED`，产品 Controller 没有此路由；旧 diagnoses events 不等于新 runs events | M2 的 [03-02](../03-durable-investigation/03-02-PLAN.md) 补每步持久事件／检查点基础，M4 [05-01](../05-workbench-quality/05-01-PLAN.md) 统一公开映射／历史回放；M0 无执行事件链，未验证该 API |
| API-08 | `GET /api/runs/{id}/stream` | 后续接入 | 没有 SSE Controller／流式传输装配 | [05-01](../05-workbench-quality/05-01-PLAN.md)；须复用安全事件、序号和断线续传，不能直接暴露 SDK 流；未验证 |
| API-09 | `POST /api/files` | 后续接入 | 没有上传路由、受控文件库或文件授权解引用 | M1 [02-03](../02-open-rag/02-03-PLAN.md)；先补 MIME／大小／路径／归属／解析失败合同，M0 不接收文件引用；未验证 |
| API-10 | `POST /api/knowledge/import` | 后续接入 | 没有导入路由／KnowledgeTools／embedding 适配器 | [02-01](../02-open-rag/02-01-PLAN.md) 先做 MD/TXT／embedding／权限检索，[02-03](../02-open-rag/02-03-PLAN.md) 补 PDF／混合检索；启用 vector 扩展不等于 RAG；未验证 |
| API-11 | `GET /api/assets/{id}` | 后续接入 | 没有产品资产路由；`selectedAssetId` 仅校验格式并保存，未读取或确认真实资产 | M2 [03-01](../03-durable-investigation/03-01-PLAN.md)；须有主数据、来源／业务时间与权限，不能用 JSON fixture 冒充产品资产 API；未验证 |

## 3. 已接入 API 内部能力与未接入字段

| 能力 | 状态／边界 | 实现和测试证据 | 不接入理由／后续责任 |
|---|---|---|---|
| 自由原文／可空资产／时间 | 已接入保存；不做 Catalog 准入，不 trim 原文；最多 8000 Unicode 码点，拒绝 NUL；默认 asOf=requestedAt | `GeneralAgentService.submit`、`ConversationRepository.append`；PG-01、PG-07 | 不代表原文已进入 Core 模型上下文；通用执行在 [02-02](../02-open-rag/02-02-PLAN.md) |
| owner/project 与安全错误 | 已接入服务器固定身份、仓库归属过滤、body 未知身份字段拒绝、404 合并无权／不存在、白名单错误 | `ProductConfiguration.generalAgentService`、两个 Repository、Controller 的异常映射；PG-05、PG-06 | 真实认证在 [05-03](../05-workbench-quality/05-03-PLAN.md)；资产／文档／附件 ACL 尚无全链路验收 |
| 原子保存／数据库约束 | 已接入会话行锁、连续消息 seq、复合归属外键、请求键唯一、run 事件 seq 唯一；故障全回滚 | `GeneralAgentService.submit/read`、V1 SQL、`AgentRunRepository.enqueue`；PG-02–PG-06 | 属应用存储事务，不授予模型写库或设备控制能力 |
| 重复请求 | 已接入同会话重复键返回 `409/DUPLICATE_REQUEST`，并发同键仅一个成功 | `ConversationRepository.hasRequest`、`GeneralAgentService.submit`；PG-02、PG-03 | 完整幂等结果重放／补答去重在 [03-03](../03-durable-investigation/03-03-PLAN.md)，当前不是该可靠性能力完成 |
| attachments／contextRefs | 后续接入；空／缺省可接受，非空整体拒绝 `REFERENCES_NOT_AVAILABLE`，不静默丢弃 | `GeneralAgentService.submit`；PG-07 直接验证非空 attachments 拒绝，contextRefs 分支仅源码核对 | [02-03](../02-open-rag/02-03-PLAN.md) 实现授权文件／上下文解引用；本次不增加导入实现 |
| replyTo／ASK_USER／同 run 补答 | 后续接入；任何非 null replyTo 整体拒绝 `REPLY_NOT_AVAILABLE`；数据库待答字段仅预留 | `GeneralAgentService.submit`、V1 SQL；PG-07；SPIKE-01 只证明测试原型 | [02-02](../02-open-rag/02-02-PLAN.md) 接单进程正常补答、释放 worker 与持久上下文读取；不把 SDK 原型当生产实现 |
| CALL_TOOL／ANSWER／引用与风险结果 | 后续接入通用协议；当前 `RunView` 只有摘要，没有 `AnswerResult` | [CONTRACTS](CONTRACTS.md) §2–4；`GeneralAgentService.RunView`；SPIKE-01 仅覆盖原型子集 | [02-01](../02-open-rag/02-01-PLAN.md)／[02-02](../02-open-rag/02-02-PLAN.md) 接强类型工具和回答校验；legacy 五动作仍兼容 |
| worker／检查点／累计预算／取消 | 后续接入；M0 仅写 QUEUED 和初始 budget，状态枚举／JSONB 字段预留不代表执行能力 | `AgentRunRepository.enqueue`、V1 SQL；PG-01 只验证存储重开 | [02-02](../02-open-rag/02-02-PLAN.md) 接正常调度；[03-02](../03-durable-investigation/03-02-PLAN.md)／[03-03](../03-durable-investigation/03-03-PLAN.md) 接跨进程执行恢复、租约／CAS／取消／累计预算可靠性 |

## 4. 继承入口与工具覆盖

| 入口／能力 | 状态与实现 | 行为证据与限制 |
|---|---|---|
| `POST /api/sessions`、`GET /api/sessions/{sessionId}/messages`、`POST /api/sessions/{sessionId}/messages` | 已接入兼容；`ConversationController → ConversationFlowService`，路由到澄清／查询／诊断 | LEGACY-01、LEGACY-02；旧会话在内存，不会自动迁成产品 PG 会话 |
| `GET /api/diagnoses/{diagnosisId}`、`GET /api/diagnoses/{diagnosisId}/events` | 已接入兼容；`DiagnosisRunService` 有界异步执行旧 Core，按 sessionId 核对归属 | LEGACY-03、LEGACY-04；事件运行中不可读，结束后轮询；不是产品 SSE 或持久恢复 |
| `GET /api/health`、`GET /actuator/health`、CLI 双 Main | 已接入；`DeviceTruthServerApplication` 和 `DeviceTruthApplication.execute` 保留独立入口 | LEGACY-01 验证 API health；历史 01-02 的打包／CLI／health 结果见 SUMMARY，本次未重跑打包；CLI 不依赖产品 PG |
| `asset.profile.get`、`asset.history.query` | 已接入旧 Gateway；读取模拟出厂／更换记录 | GATE-01、LEGACY-02；只读 JSON，非 PG 资产 API／实时设备读取 |
| `evidence.search`、`evidence.get`、`diagnosis.rules.query` | 已接入旧 Gateway；搜索元数据／读取原始证据／查询规则，不由工具确定结论 | GATE-01、LEGACY-05；不是向量检索或标准 Skill；旧参数 `Map<String,String>`＋ID 校验尚不支持自由中文 query／嵌套 filters |
| 任意工具、跨资产／参数越界、写设备 handler | 明确不采用；`CapabilityGateway.execute` 注册白名单、只读／作用域／参数校验；模型只提动作 | GATE-02、GATE-03、SPIKE-01；未知／写／越权 handler 不执行。V1.0 不引入任意 SQL／脚本／设备控制接口 |

## 5. SDK／数据库集成取舍

本节按项目需要的集成面核对，**不是 AgentScope 全部 SDK API 的兼容认证**。固定依赖为 `pom.xml` 的 AgentScope Core／DashScope／OpenAI **2.0.4**；以本地固定版本编译、生产 import 和测试为依据。`docs/research/agent.md` 等调研文档不能证明某项 API 已接入或适用于该固定版本。

| ID | 能力／API | 状态 | 实现与证据 | 明确不接入的理由／后续责任 |
|---|---|---|---|---|
| SDK-01 | `Model`、`Msg`、`GenerateOptions`、`ResponseFormat.jsonObject` | 已接入 | `AgentScopeStep.complete` 构造 system/user 消息，`model.stream(..., List.of(), ...)` 只订阅一次并收集 TextBlock；SDK-TEST-01 | SDK 得到的工具列表为空，动作 JSON 由应用解析；模型不能自行执行工具 |
| SDK-02 | `DashScopeChatModel`／Qwen | 已接入旧模型适配 | `AgentScopeStep.qwen`、`QwenReasoner`、`StructuredReasoner`；SDK-TEST-01、SDK-TEST-03 | 本次只用 fake completion 验协议／失败；未发真实 Qwen 请求，不宣称在线质量通过 |
| SDK-03 | `OpenAIChatModel`／Chat Completions 兼容端点 | 已接入旧模型适配 | `AgentScopeStep.openai`、`OpenAiReasoner`；SDK-TEST-02 的本机 HTTP 验路径、JSON 与场景 B 全链 | 可配置兼容供应商，但本次未验真实 OpenAI／DeepSeek 服务；产品 QUEUED API 尚未调用它 |
| SDK-04 | 超时／重试／配置读取 | 已接入受控单步 | SDK `ExecutionConfig.maxAttempts(1)`、45 秒边界；应用层非法动作最多纠正一次；`ModelConfiguration` 文件非空值优先、环境补缺；SDK-TEST-01–SDK-TEST-04 | 明确不启用自动备用模型／静默 scripted 降级，避免掩盖技术失败与重复预算；缺 key 明确失败 |
| SDK-05 | `ReActAgent`／`Toolkit`／`AgentTool`／`ToolUseBlock`／Hook | 仅原型验证；明确不采用为生产主循环 | `RuntimeComparisonTest.execute` 真正构建候选并桥接 Gateway；SPIKE-01 六样例×两候选；[ADR](ADR-runtime.md) 只选演进 TaskOrchestrator | 两候选均能运行；演进方向复用既有安全提交边界、适配成本较低。不能再嵌套 SDK 循环，生产通用改造留 02-02 |
| SDK-06 | SDK Memory／stateStore／文件会话存储作为权威状态 | 明确不采用 | 生产入口用应用 Repository／StateManager；SPIKE-01 用测试 Journal JSON 重建，没有用 SDK stateStore | owner/project、消息／引用／预算的原子保存由 PG 应用模型承担；未验证 SDK 持久恢复，也不以它代替 03-02／03-03 |
| SDK-07 | SDK 原生工具自动执行／meta tools／动态 Skill／Skill 代码执行 | 明确不采用为产品工具入口 | 生产 `AgentScopeStep.complete` 传空工具列表；候选显式关闭 meta/task/dynamic skills/skill code；SDK-TEST-01、SPIKE-01 | Gateway 统一授权和执行；标准 SKILL.md 指引由 [04-01](../04-skills-dual-domain/04-01-PLAN.md) 接入，不授权任意脚本 |
| SDK-08 | token streaming／SDK 原始事件直出 | 本阶段不接入；原始事件直出明确不采用 | 供应商 builder 为 `stream(false)`，调用后收集文本；公开事件由应用白名单映射；LEGACY-04 | SDK `Model.stream` 返回类型不等于 HTTP SSE 已实现；05-01 接应用安全流，SDK 增量生成未验证 |
| SDK-09 | SDK embedding／RAG／MCP／文件系统或 shell 执行扩展 | 本阶段不接入 | `pom.xml` 只声明 Core 和两个模型扩展；生产使用面见 SDK-01–04；无相关产品装配／行为测试 | embedding／知识工具在 02-01 通过服务端受控适配交付；MCP／通用文件执行非本阶段需要，不因 SDK 有扩展便开放工具权限 |
| SDK-10 | Spring AI Alibaba 或另一套 Agent 框架 | 明确不采用于本轮实现 | `pom.xml` 未引入，唯一循环决策见 ADR；无运行测试 | 没有证据证明额外框架降低当前成本，避免两套循环；重新选型须另作 ADR 与迁移验证 |
| DB-01 | Spring JDBC／JdbcTemplate／TransactionTemplate／Hikari／Flyway | 已接入 product | `ProductConfiguration.productDataSource/productFlyway/generalAgentService`、两个 Repository、V1 SQL；PG-01–PG-06 | Spring JDBC 6.2.19、PG JDBC 42.7.11、Flyway 11.7.2、Hikari 6.3.3 为 01-02 锁定结果；统一参数化 SQL，不混入 ORM 栈 |
| DB-02 | PostgreSQL／pgvector | 已接入数据库基础；向量检索后续接入 | V1 执行 `CREATE EXTENSION IF NOT EXISTS vector`；PG-01 验 PG16.15／vector0.8.7 和一次成功迁移／服务重开 | 只有会话／消息／run／event 表，无知识向量表／embedding／召回；真实 RAG 按 02-01／02-03 验收 |
| DB-03 | Testcontainers／另拉 PG 镜像／全栈 Compose | 本阶段明确不采用该测试环境方案；全栈部署后续接入 | `PersistentConversationTest.start/stop` 在指定 PG 建／删自身随机测试库；`compose.yaml` 仅备用 `standalone-db`、固定镜像、`pull_policy: never`；历史 PG 验证见 01-02 SUMMARY | 用户已指定复用 interview-postgres，不另起第二 PG；05-03 才交付完整部署。Testcontainers 未保留依赖，本次未运行 Docker |

V1.0 另明确不接任意设备控制、多 Agent 产品编排、Kafka／Redis、独立向量服务和完整工单／IoT 平台；DOCX／OCR／图像／语音／真实 OBD／PLC 留后续范围。依据见 [PROJECT Scope](../../PROJECT.md) 和 [REQUIREMENTS 明确后置](../../REQUIREMENTS.md)，不能在本阶段表中将它们写为“已接入”。

## 6. 实现定位与测试断言

生产源码入口：

- 产品装配：[ProductConfiguration](../../../src/main/java/io/devicetruth/server/ProductConfiguration.java)、[ServerConfiguration](../../../src/main/java/io/devicetruth/server/ServerConfiguration.java)。
- 产品 HTTP／事务：[AgentConversationController](../../../src/main/java/io/devicetruth/api/AgentConversationController.java)、[GeneralAgentService](../../../src/main/java/io/devicetruth/application/agent/GeneralAgentService.java)。
- PG：[ConversationRepository](../../../src/main/java/io/devicetruth/adapter/persistence/ConversationRepository.java)、[AgentRunRepository](../../../src/main/java/io/devicetruth/adapter/persistence/AgentRunRepository.java)、[V1 migration](../../../src/main/resources/db/migration/V1__agent_conversations.sql)。
- 模型：[AgentScopeStep](../../../src/main/java/io/devicetruth/adapter/AgentScopeStep.java)、[QwenReasoner](../../../src/main/java/io/devicetruth/adapter/QwenReasoner.java)、[OpenAiReasoner](../../../src/main/java/io/devicetruth/adapter/OpenAiReasoner.java)、[StructuredReasoner](../../../src/main/java/io/devicetruth/adapter/StructuredReasoner.java)、[ModelConfiguration](../../../src/main/java/io/devicetruth/adapter/ModelConfiguration.java)、[pom.xml](../../../pom.xml)。
- legacy：[ConversationController](../../../src/main/java/io/devicetruth/api/ConversationController.java)、[TaskOrchestrator](../../../src/main/java/io/devicetruth/core/runtime/TaskOrchestrator.java)、[CapabilityGateway](../../../src/main/java/io/devicetruth/core/capability/CapabilityGateway.java)、[RunEventViews](../../../src/main/java/io/devicetruth/application/RunEventViews.java)。

下表 ID 对应前述矩阵。“历史 PG”表示真实数据库测试已有 2026-11-09 记录、本次未重跑；“本次”表示包含在 51 项复测中。精确写到断言，避免以测试类存在代替验证结论。

| 证据 ID | 测试文件／方法 | 证明什么及不证明什么 | 时期 |
|---|---|---|---|
| PG-01 | [PersistentConversationTest](../../../src/test/java/io/devicetruth/server/PersistentConversationTest.java)：`freeMessageIsAtomicQueuedAndSurvivesServiceRestart` | 201／202／Location、原文／emoji／空资产／默认时点、首事件、同 JVM 服务关闭再开后内容不变、迁移未重复执行及真实 PG/vector 版本；不证明执行恢复 | 历史 PG；独立 Java 进程重启另见 01-02 SUMMARY 的打包验收 |
| PG-02 | 同上：`duplicateSubmissionDoesNotCreateAnotherMessageOrRun` | 重复键 409、消息与 run 不增加；不证明响应重放 | 历史 PG |
| PG-03 | 同上：`concurrentRequestsKeepOneRunPerKeyAndConsecutiveMessageSequence` | 并发同键只建一个 run、不同键保持连续消息序号；不证明 worker 并发恢复 | 历史 PG |
| PG-04 | 同上：`eventFailureRollsBackMessageRunAndConversationVersion` | 注入事件写失败，消息／run／版本全部回滚，随后重试成功 | 历史 PG |
| PG-05 | 同上：`anotherOwnerOrProjectCannotReadOrWriteAndBodyCannotChangeIdentity` | 跨 owner／project 读写 404、列表隔离、body 冒传身份字段 400；不证明真实登录认证 | 历史 PG |
| PG-06 | 同上：`databaseRejectsCrossOwnerLinksAndDuplicateEventSequence` | 数据库拒绝跨归属外键和重复事件序号 | 历史 PG |
| PG-07 | 同上：`invalidOrUnavailableInputIsRejectedBeforeAnyWrite` | 超长／空白／缺请求键／错 schema／附件／补答整体拒绝；8000 emoji 边界原样保存；未直接覆盖非空 contextRefs、NUL 与显式 asOf 分支 | 历史 PG |
| SDK-TEST-01 | [AgentScopeStepTest](../../../src/test/java/io/devicetruth/adapter/AgentScopeStepTest.java)：`oneSubscriptionReturnsOneJsonProposalWithoutExecutingTools`、`missingKeyFailsExplicitly`、`modelTimeoutIsBounded` | 单次订阅、工具列表为空、单次 SDK 尝试、缺 key 失败与超时有界；fake Model，非在线质量 | 本次 3 项 |
| SDK-TEST-02 | [OpenAiReasonerTest](../../../src/test/java/io/devicetruth/adapter/OpenAiReasonerTest.java)：`sendsChatCompletionsJsonAndPreservesConfiguredBasePaths`、`missingAndInvalidConfigurationFailsBeforeAnyRequest`、`invalidActionsRetryOnceAndPersistentFailureStops`、`httpFailuresDoNotRetryOrExposeProviderBodyInReports`、`localHttpModelDrivesScenarioBThroughGatewayEvidenceAndGuard` | 本机假 key HTTP 合同、路径／配置、一次纠正、技术失败脱敏、模型→Gateway→Evidence→Guard；非外部供应商 | 本次 5 项 |
| SDK-TEST-03 | [QwenReasonerTest](../../../src/test/java/io/devicetruth/adapter/QwenReasonerTest.java)：`malformedOutputRetriesExactlyOnceAndUsesCurrentStateProjection`、`persistentInvalidJsonAndTransportFailureAreTechnicalFailures` | 当前快照与纠正／失败语义；注入 completion，不是真实 DashScope | 本次 2 项 |
| SDK-TEST-04 | [ModelConfigurationTest](../../../src/test/java/io/devicetruth/adapter/ModelConfigurationTest.java)：`absentFileKeepsEnvironmentSupport`、`fileOverridesEnvironmentAndBlankValuesFallBack`、`unreadableOrMalformedFilesFailWithoutExposingContents` | 临时配置文件验证优先级／补缺／失败不泄漏，不读取真实私有文件 | 本次 3 项 |
| SPIKE-01 | [RuntimeComparisonTest](../../../src/test/java/io/devicetruth/spike/RuntimeComparisonTest.java)：`bothCandidatesRebuildAndResumeTheSameRunThroughTheGateway`；[固定 cases.json](../../../src/test/resources/runtime-comparison/cases.json) | 六样例×两候选：充电追问／重建与引用、未知工具、写工具、跨资产、参数错误、伪造引用；无真实 RAG／生产 WAITING／PG 检查点 | 本次 1 个 JUnit，内部 12 次执行；历史两次 clean 字节一致见 [RUNTIME-SPIKE](RUNTIME-SPIKE.md) |
| GATE-01 | [GatewayTest](../../../src/test/java/io/devicetruth/core/GatewayTest.java)：`fixturesAndFiveReadOnlyCapabilitiesReturnRawRecordsAndRules` | 五只读工具返回原始模拟资料／规则，不预先给模型最终结论 | 本次 |
| GATE-02 | 同上：`unknownCrossAssetUnknownEvidenceTraversalAndWrongParameterAreRejected` | 未知工具／跨资产／非法路径与参数拒绝 | 本次 |
| GATE-03 | 同上：`nonReadOnlyHandlerNeverExecutesAndAdapterFailureIsStructured` | 写 handler 不执行、适配器失败结构化 | 本次；GATE-01–03 共 3 项 |
| LEGACY-01 | [ServerHttpTest](../../../src/test/java/io/devicetruth/server/ServerHttpTest.java)：包括 `healthEndpointIsUpAndMarkedSimulated`、`createSessionReturns201WithRealUuid`、`newSessionHasEmptyMessageList`、`diagnoseIsAcceptedWith202AndPersistsPairedMessages` | 旧会话、查询／诊断／澄清入口、安全参数与 SIMULATED；显式 scripted | 本次 16 项 |
| LEGACY-02 | [QueryHttpTest](../../../src/test/java/io/devicetruth/server/QueryHttpTest.java)：包括 `profileQueryReturns200WithRealFactorySource`、`historyQueryReturnsReplacementRecordsOnly` | profile／history／消息及无记录／未知资产语义；“real source”为 fixture 来源，非真实设备 | 本次 5 项 |
| LEGACY-03 | [DiagnosisHttpTest](../../../src/test/java/io/devicetruth/server/DiagnosisHttpTest.java)：包括 `diagnosisRunsRealCoreAndExposesRealResultAndEvents`、`eventsAreUnavailableWhileRunningAndForUnknownOrForeignRuns` | 旧异步 Core 结果、结束后事件、session 归属与 202 合同 | 本次 4 项 |
| LEGACY-04 | [DiagnosisSafetyMappingTest](../../../src/test/java/io/devicetruth/server/DiagnosisSafetyMappingTest.java)：包括 `escalateReasonNeverReachesPublicViewButStaysInCoreReport`、`unknownFailureCodeIsWithheld` | 公开字段／错误白名单，保留内部报告与公开结果的边界 | 本次 4 项 |
| LEGACY-05 | [AcceptanceTest](../../../src/test/java/io/devicetruth/core/AcceptanceTest.java)：包括 `casesABCDProduceEvidenceConstrainedOutcomes`、`caseEUnknownToolAndForgedEvidenceNeverResolve` | A/B 受证据约束结案、C 冲突、D 缺证、E 未知工具／伪造证据不能结案；不是新版全部 E2E 验收 | 本次 5 项 |

## 7. 本阶段需求对照与验收边界

| 需求 | 本阶段覆盖结论 | 对应实现／证据 |
|---|---|---|
| BASE-01 | 保留 Java21／Boot3.5.16／AgentScope2.0.4；单步模型连接复用；只选择一个产品循环方向，通用生产改造未完成 | SDK-01–SDK-05、SDK-TEST-01–04、SPIKE-01、ADR |
| BASE-02 | 保留旧 A–D／E 与旧 HTTP 行为；本次复测覆盖核心及 HTTP 子集，完整打包／CLI 沿用注明日期的历史证据 | LEGACY-01–05、GATE-01–03、01-02 SUMMARY |
| STORE-01 | M0 会话／消息／run／event schema 与 product JDBC 已接；不等于完整产品数据域 | DB-01、DB-02、PG-01、PG-06 |
| STORE-02 | 仅 M0 消息／QUEUED／首事件切片已接；执行中工具／WAITING／结果持久化尚缺 | API-01–06、PG-01–04；后续 02-02／03-02 |
| STORE-05 | M0 服务重开及历史新 JAR 跨进程读回已验；不等于崩溃任务恢复 | PG-01、01-02 SUMMARY；后续 03-02／03-03 |
| TRUST-06 | **部分实现**；旧模型配置脱敏、新 PG 服务端注入已覆盖；上传隔离／真实认证／embedding 全链仍待接入 | SDK-TEST-02／04、PG-05／06、DB-01；后续 02-01／02-03／05-03 |
| WEB-06 | **部分实现**；conversations／runs 六路由已接、legacy 保留；files／assets／执行／流接口未接 | API-01–11、LEGACY-01–04；不升级为全部完成 |

## 8. 本次检查、复现命令与验收顺序

**GSD 门禁补验（2026-11-10）：** 首次运行 `check api-coverage-verify-pre` 返回 `block=true`、`matrix is empty`；原中文表未满足解析器要求，并非没有覆盖内容。补充 §1.1 的规范三列表后，重跑返回 `passed=true`、`block=false`，识别 38 项能力（21 项 INTEGRATE、17 项带理由 OPT-OUT）。同时重跑 `python outputs/validation/01-coverage-check.py` 通过：129 个本地链接、36 处测试方法引用、109 份业务文件摘要未变；`git diff --check` 退出 0。此补验仅修改覆盖文档与接续记录，未重跑 Maven、PG、CLI 或 UI；脚本显示的 51 项测试来自下述已有 Surefire 结果，不是本次新增测试执行。用户验收和独立 Review 仍待进行。

本次只补文档与验收入口，不修改业务源码、测试、依赖、迁移、数据或规则，不运行后续 PLAN。实际命令与结果：

| 检查 | 实际命令／方法 | 本次结果 |
|---|---|---|
| 环境 | `mvn -version` | Maven3.9.9／Microsoft JDK21.0.13.0.2 |
| 针对性回归 | 下方 Maven 命令；读取此次 11 个 suite 的 Surefire XML，不混算其他历史 XML | 退出 0；**51 tests／0 failures／0 errors／0 skipped**；SDK、HTTP、核心均为离线 fake／scripted 或本机 HTTP |
| 覆盖文档检查 | `python outputs/validation/01-coverage-check.py`；逐项核对矩阵 ID／证据 ID、11 条产品路由的已接／未接集合、源码方法与测试方法存在、本地链接／UTF-8、需求映射、设计及 fixture hash、业务文件快照 | 退出 0；详细摘要 `outputs/validation/01-coverage-check.json`（忽略目录）；源码扫描不代替缺失的行为测试 |
| 空白与差异范围 | `git diff --check`；对比本次核验前后的业务文件摘要 | 退出 0；仅 COVERAGE 及相关规划入口／验证记录变化；既有 01-02 工作树修改保留 |
| PG／package／CLI／真实在线／UI | 本次未执行 | PG 环境变量缺失；历史 PG 等结果按 §1 引用；其余未验证，不记 PASS |

```powershell
mvn -q '-Dtest=RuntimeComparisonTest,AgentScopeStepTest,OpenAiReasonerTest,QwenReasonerTest,ModelConfigurationTest,AcceptanceTest,GatewayTest,ServerHttpTest,DiagnosisHttpTest,QueryHttpTest,DiagnosisSafetyMappingTest' test
```

如需重跑真实 PG：先按根 [README](../../../README.md) 设置 `DEVICETRUTH_TEST_PG_BASE_URL/USERNAME/PASSWORD`，再执行 `mvn -q '-Dtest=PersistentConversationTest' test`；只使用指定 PG 的随机独立测试库，不拉取新镜像。本次的覆盖检查脚本／摘要及测试日志属于本地校验产物；可提交的长期行为验证资产是 §6 的测试源码与固定输入，历史完成记录不改写。

**验收顺序：** 先核对本覆盖表及检查结果，再逐项用户验收；用户尚未确认的项目不预填通过。独立 Review 和阶段出口仍单独保留待办，M0 阶段仍不勾完成，后续实施仍需新的明确授权。

以“充电经常中断，没有故障码，先查哪些资料？”为例：当前产品请求经过 Controller 校验，再由 `GeneralAgentService.submit` 锁定服务器身份下的会话；两个 Repository 将原文、QUEUED run 和首事件一起提交 PG，返回 202。GET 从 PG 读回同一原文和任务；**此链到存储即止，没有推理、检索或维修答案**。旧场景 B 则经 `DeviceTruthApplication.execute → TaskOrchestrator.run → Reasoner.propose → CapabilityGateway.execute → EvidenceManager／DecisionGuard → StateManager → RunReportWriter`，形成模拟诊断结果；两条链在本阶段的用途及验收结论分别记录。
