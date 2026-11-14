# DeviceTruth 接口清单（领域开发者协作版）

- 生成方式：静态源码审查（未运行服务、未调用接口）
- 依据：`src/main/java/io/devicetruth/api/` 全部 Controller、`frontend/src/api.ts` 合同、`src/main/resources/db/migration/V1–V16`
- 身份说明：所有 `/api` 接口的 `owner_id`/`project_id` 由服务器配置决定（`devicetruth.product.owner-id/project-id`，默认 `local-owner`/`local-project`），**请求体/参数一律不允许携带身份或权限**，浏览器不持有任何凭据。
- 运行条件：以下接口除标注 legacy 外，均只在 Spring profile=`product` 下注册（`DeviceTruthServerApplication` + `application-product.properties`），默认监听 `127.0.0.1:8080`。

## 一、产品 Agent 接口（AgentConversationController）

| 方法与路径 | 用途 | 请求 | 响应 | 限制与错误 |
| --- | --- | --- | --- | --- |
| `POST /api/conversations` | 新建会话 | 空 JSON 对象 `{}`（必须为空体） | 201 + ConversationView | 带非空字段 400 |
| `GET /api/conversations` | 历史会话列表 | — | ConversationView[] | — |
| `GET /api/conversations/{id}` | 会话详情（消息+runs） | — | ConversationDetail | 跨归属 404 |
| `POST /api/conversations/{id}/messages` | 提交新问题或补答 | 头 `Idempotency-Key: UUID`（必填）；体 `{schemaVersion:1, userMessage, selectedAssetId?, attachments?[{fileId}], asOf?, replyTo?{runId,pendingQuestionId,questionVersion}}` | 202 + RunView（Location: /api/runs/{runId}） | 无 replyTo 开新 run；带 replyTo 必须命中 WAITING 中的 pendingQuestion，版本不符 409；附件必须已 PARSED 否则 409 |
| `GET /api/runs/{id}` | run 状态/结果 | — | RunView（status/pendingQuestion/result/failureCode/usage） | 跨归属 404 |
| `GET /api/runs/{id}/skill` | 本 run 选中的 Skill 公开视图 | — | `{skillId,version,devices[],tasks[]}`（`SkillRuntime.SelectionView`；skillId 即 Skill 名） | 未选 404 `SKILL_NOT_SELECTED` |
| `GET /api/runs/{id}/references` | 本 run 已登记引用 | — | Reference[]（sourceType/sourceId/sourceVersion/excerpt/dataNature 等） | 跨归属 404 |
| `GET /api/runs/{id}/tool-events` | 工具执行轨迹（审计） | — | ToolEvent[] | — |
| `GET /api/runs/{id}/model-events` | 模型尝试审计（无 prompt/密钥/原文） | — | ModelEvent[] | — |
| `GET /api/runs/{id}/events?after=N` | 事件历史回读（seq 升序） | after 缺省返回全部；after=N 返回 seq>N；负数/非数字 400 | PublicEvent[]（白名单字段） | — |
| `GET /api/runs/{id}/stream` | SSE 实时事件流 | 头 `Last-Event-ID` 可断线续传 | text/event-stream（RUN_QUEUED/RUN_TOOL/RUN_MODEL/RUN_WAITING/RUN_ANSWER/RUN_TERMINAL 等） | run 不存在 404；并发订阅冲突 409；队列满 503 |
| `POST /api/runs/{id}/cancel` | 取消 run | — | 200 + RunView | 终态再取消 409 `RUN_ALREADY_TERMINAL` |

## 二、知识库接口（KnowledgeController + FileController）

| 方法与路径 | 用途 | 请求 | 响应 | 限制与错误 |
| --- | --- | --- | --- | --- |
| `POST /api/files` | 上传 Markdown/PDF 文件（multipart） | 表单字段：`file`（必填，`.md` 且 MIME 为 text/markdown/text/x-markdown）、`title?`、`domain`（默认 general）、`deviceModel?`、`version`（默认 1.0）、`dataNature`（默认 SIMULATED，可 AUTHORIZED） | 202 + FileView（parseStatus=UPLOADED） | >1MB 413 `DOCUMENT_TOO_LARGE`；类型不符 415 `UNSUPPORTED_FILE_TYPE` |
| `GET /api/files/{fileId}` | 查询导入状态 | — | FileView（parseStatus/parseError/documentId/title/domain 等） | 跨归属 404 |
| `POST /api/files/{fileId}/retry` | 失败重试导入 | — | 202 + FileView | 仅 FAILED/OCR_REQUIRED 可重试，否则 409 `IMPORT_NOT_RETRYABLE` |
| `POST /api/knowledge/documents` | 直接导入 Markdown/纯文本内容（JSON 体，不走文件） | `{title, domain, deviceModel?, version, filename, mimeType("text/markdown"/"text/plain"), content(≤40万字符), dataNature}` | 201 + ImportResult（document 元数据 + reused） | 类型不符 415；>1MB 413；元数据冲突 409 `DOCUMENT_METADATA_CONFLICT` |
| `GET /api/knowledge/documents/{documentId}` | 文档元数据 | — | DocumentMetadata（含 ingestStatus/embeddingProfile/chunkCount） | 404 |
| `GET /api/knowledge/tools` | 服务端知识工具合同（联调用） | — | ToolCatalog | — |
| `POST /api/knowledge/search` | 以某 run 身份检索知识库 | `{runId(必填), query(≤2000字符), filters?{domain?,deviceModel?,documentId?}, limit?, mode?("VECTOR"/"KEYWORD"/"HYBRID")}` | `{runId, hits[], empty}`；每个 hit 自动登记为本 run 的 KNOWLEDGE_DOCUMENT 引用 | runId 必须属于本归属否则 404；mode 非法 400 |
| `GET /api/knowledge/references/{referenceId}?runId=` | 按引用读原文片段 | — | Excerpt（content 截断 2000 字符 + 章节/版本/dataNature） | 非本 run 取回引用 404/409 `INVALID_CITATION` |
| `GET /api/knowledge/documents/{documentId}/chunks/{chunkId}?runId=` | 按文档+切片读原文 | — | Excerpt | 同上：必须本 run 已通过 search 取回 |

## 三、设备事实只读接口（AssetController）

| 方法与路径 | 用途 | 请求参数 | 响应 | 限制 |
| --- | --- | --- | --- | --- |
| `GET /api/assets` | 已登记设备 ID 清单 | — | `["SC-0245-B", ...]`（字符串数组） | — |
| `GET /api/assets/{assetId}` | 组合视图（身份+配置+维修+装机/冲突判定） | `asOf?`（ISO-8601，缺省=当前时刻） | AssetComposite | 未登记/跨归属/非法 ID 一律 404（不区分存在性） |
| `GET /api/assets/{assetId}/configuration` | 配置事实查询 | `componentId?`, `attribute?`, `asOf?` | ConfigurationOutcome（entries/installationStatus/conflict/requiredChecks） | 同上 404/400 |
| `GET /api/assets/{assetId}/maintenance` | 维修历史查询 | `componentId?`, `asOf?` | MaintenanceOutcome | 同上 |

> 注意：这些是**运维/联调直查接口**，不登记 run 引用；只有 Agent 工具（`get_asset` 等）在真实 run 内调用才会写 `dt_evidence_reference`。

## 四、legacy 诊断接口（ConversationController，旧链路）

| 方法与路径 | 用途 |
| --- | --- |
| `POST /api/sessions` | 创建 legacy 诊断会话（场景白名单驱动） |
| `GET /api/sessions/{sessionId}/messages` | legacy 会话消息 |
| `POST /api/sessions/{sessionId}/messages` | 提交 legacy 场景消息 |
| `GET /api/diagnoses/{diagnosisId}` | legacy 诊断结果 |
| `GET /api/diagnoses/{diagnosisId}/events` | legacy 诊断事件 |
| `GET /api/health` | 健康检查 |

> legacy 链路与产品 Agent 是两条独立链路；`src/main/resources/static/index.html` 为 legacy 演示前端。新工作一律走第一节接口。

## 五、Agent 工具合同（模型经 CapabilityGateway 调用，非 HTTP）

工具注册于 `ServerConfiguration` 的 `agentCapabilityGateway`；全部只读。默认 Skill 白名单 `devicetruth.skills.allowed-tools` = `search_knowledge, get_document_excerpt, get_asset, get_configuration, get_maintenance_history, get_observations`（`generate_diagnosis_report` 与 Skill 系统工具不在默认白名单内）。

| 工具 | 必填参数 | 可选参数 | 关键输出 | 备注 |
| --- | --- | --- | --- | --- |
| `get_asset` | — | `assetId`（缺省用 run 选定设备） | assetId/ownerId/projectId/componentIds/asOf/dataNature | 越权统一 ASSET_NOT_FOUND |
| `get_configuration` | `attribute`（字母开头标识符） | `assetId`, `componentId`, `asOf` | entries[]（来源/有效期/核实状态/authority）+ installationStatus + conflict + requiredChecks + limitations | 无时点内 FIELD_VERIFIED 必为 UNCONFIRMED；每行登记 CONFIGURATION 引用 |
| `get_maintenance_history` | — | `assetId`, `componentId`, `asOf` | entries[]（before/after、来源、核实状态） | 同上登记 MAINTENANCE 引用 |
| `get_observations` | — | `assetId`, `signal`, `from`, `asOf`, `quality(GOOD/UNCERTAIN/BAD)` | 最多 12 条模拟观测 + status(NOT_INTEGRATED/NO_MATCHING_DATA/SIMULATED_DATA) | 只读 SIMULATED；时间不得超出 run 的 asOf |
| `search_knowledge` | `query`（≤2000字符） | `filters{domain,deviceModel,documentId}`, `limit`(1–50) | hits[]（referenceId/documentId/version/chunkId/section/similarity） | 归属+档案过滤先于召回；自动登记引用 |
| `get_document_excerpt` | `documentId`, `chunkId`（UUID） | — | 原文片段（≤2000 字符）+ 引用 | 只能读本 run 已取回片段 |
| `generate_diagnosis_report` | — | — | 本 run 聚合报告 | 不在默认 Skill 白名单 |
| `activate_skill` / `read_skill_reference` | `skillId` / `name` | — | Skill 视图 / 单篇 reference 全文 | 系统工具，随激活注入 |

## 六、错误码速查（各 Controller 白名单）

- 400 `INVALID_REQUEST`：请求体/参数不合法
- 404 `NOT_FOUND_OR_DENIED` / `ASSET_NOT_FOUND`：不存在或跨归属（刻意不区分）
- 409 类：`INVALID_CITATION`（引用无法核实）、`EMBEDDING_DIMENSION_MISMATCH`、`EMBEDDING_PROFILE_CONFLICT`、`DOCUMENT_NEEDS_REBUILD`、`DOCUMENT_METADATA_CONFLICT`、`IMPORT_NOT_RETRYABLE`、`RUN_ALREADY_TERMINAL`
- 413 `DOCUMENT_TOO_LARGE`；415 `UNSUPPORTED_FILE_TYPE`
- 503 `EMBEDDING_UNAVAILABLE` / `EMBEDDING_CONFIGURATION_MISSING` / SSE 容量
- run 失败码（前端 `describeFailure` 白名单）：`MODEL_CONFIGURATION_MISSING`、`DASHSCOPE_API_KEY_MISSING`、`OPENAI_*`、`MODEL_TRANSPORT_OR_TIMEOUT`、`MODEL_OUTPUT_INVALID_AFTER_RETRY`、`QUEUE_REJECTED`、`BUDGET_EXHAUSTED`、`TOTAL_TIMEOUT`、`SKILL_UNAVAILABLE`、`SKILL_CHANGED`、`INVALID_CITATION`、`UNCONFIRMED_FINDING`、`UNSAFE_ANSWER`、`INVALID_ANSWER`、`PERSIST_FAILURE`、`AGENT_RUN_FAILURE`、`RECOVERY_ATTEMPT_LIMIT`

## 七、当前不存在（勿编造）的接口

- 设备结构化数据导入 API（`dt_asset*` 只能靠夹具 Seeder 或手工 SQL）
- Skill 安装/停用/热更新管理 API（只能改 `devicetruth.skills.installations` 配置 + 重启）
- 观测数据导入 API（主代码无写入 `dt_asset_observation` 的通道，仅测试直写 SQL）
- 知识文档删除/替换 API（只能通过新 version 重新上传）
- 多用户认证/项目隔离 API（owner/project 是服务器单身份常量）
