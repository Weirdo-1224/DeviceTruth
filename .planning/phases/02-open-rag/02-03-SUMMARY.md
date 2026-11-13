---
phase: 02-open-rag
plan: "03"
subsystem: knowledge
tags: [markdown, upload, postgresql, pgvector, hybrid-search, rrf, vue]
requires:
  - phase: 02-open-rag/02-01
    provides: 知识导入、向量档案、权限过滤与引用持久化
  - phase: 02-open-rag/02-02
    provides: 通用 Agent、同 run 补答与 Vue 对话
provides:
  - 受控 Markdown 上传、原文件存储、有界异步导入、失败状态与重试
  - 附件/历史引用授权关联及可展开章节引用
  - 向量、关键词与 RRF 混合召回及 12 个固定样本
  - 真实 embedding/模型可重跑验收入口与缺环境证据
affects: [durable-investigation, workbench-quality]
actuals:
  tokens: 0
  tasks: 3
  commits: 0
tech-stack:
  added: [Spring multipart, RRF]
  patterns:
    - 服务器生成文件路径与规范附件元数据
    - SQL 候选阶段权限过滤后进行双通道召回与 RRF 融合
key-files:
  created:
    - src/main/java/io/devicetruth/api/FileController.java
    - src/main/java/io/devicetruth/knowledge/DocumentParser.java
    - src/main/java/io/devicetruth/knowledge/FileUploadService.java
    - src/main/resources/db/migration/V6__controlled_file_import.sql
    - src/test/java/io/devicetruth/knowledge/KnowledgeImportTest.java
    - src/test/java/io/devicetruth/knowledge/EmbeddingIntegrationTest.java
    - datasets/eval/m1-rag/cases.json
    - datasets/eval/m1-rag/manifest.json
    - scripts/verify_general_agent.py
  modified:
    - src/main/java/io/devicetruth/knowledge/KnowledgeImportService.java
    - src/main/java/io/devicetruth/knowledge/KnowledgeSearchService.java
    - src/main/java/io/devicetruth/application/agent/GeneralAgentService.java
    - frontend/src/api.ts
    - frontend/src/App.vue
key-decisions:
  - 按用户明确决定以 Markdown 上传替代本计划 PDF 实现；PDF 保持 NOT_RUN
  - Agent 默认使用向量+关键词 RRF；独立通道只用于固定样本比较
  - 历史证据必须复制登记到当前 run 后才能引用，跨权统一 404
patterns-established:
  - 原文件字节在受控目录，数据库只保存路径键、hash、归属与导入状态
  - 在线验收禁止回退 deterministic/scripted 冒充真实供应商
requirements-completed: [ENTRY-04, KNOW-07, KNOW-08]
coverage:
  - id: D1
    description: Markdown 上传、原文件存储、导入失败与目标/补答附件授权闭环
    requirement: ENTRY-04
    verification:
      - kind: integration
        ref: src/test/java/io/devicetruth/knowledge/KnowledgeImportTest.java#8 real PostgreSQL tests
        status: pass
      - kind: integration
        ref: src/test/java/io/devicetruth/application/GeneralAgentFlowTest.java#18 real PostgreSQL tests
        status: pass
      - kind: unit
        ref: frontend/src/api.test.ts#15 tests
        status: pass
    human_judgment: false
  - id: D2
    description: 确定性向量合同下的向量/关键词/RRF 混合召回与固定 12 样本
    requirement: KNOW-04
    verification:
      - kind: integration
        ref: scripts/verify_general_agent.py --suite=m1-retrieval --embedding=deterministic
        status: pass
      - kind: integration
        ref: src/test/java/io/devicetruth/knowledge/KnowledgeSearchTest.java#14 real PostgreSQL tests
        status: pass
      - kind: unit
        ref: scripts/test_verify_general_agent.py#10 acceptance decision tests
        status: pass
    human_judgment: false
  - id: D3
    description: 真实 embedding/LLM 检索引用与同 run 补证
    requirement: EVAL-02
    verification:
      - kind: other
        ref: three online commands exited 2 because real embedding API key is missing
        status: unknown
    human_judgment: false
    rationale: 缺少真实 embedding 密钥，真实供应商效果未执行，不能以确定性合同替代
  - id: D4
    description: PDF 与浏览器实际上传点击演示
    requirement: WEB-04
    verification: []
    human_judgment: true
    rationale: 用户决定 PDF 延后；浏览器 E2E 未执行，均保持 NOT_RUN
duration: 6h44m
completed: 2026-11-11
status: complete
---

# Phase 2 Plan 3: Markdown 上传、混合检索与真实验收入口 Summary

> **2026-11-12 用户最终关闭决定：** 用户明确要求结束并关闭 Phase 2，认可02-03当前已实施范围；正文安全、PDF及浏览器验收延后。此为带遗留项的范围调整和接受，不代表遗留功能已实现或测试通过。

> **2026-11-12 用户决定：** 用户认可本轮检索及多轮修复并决定通过 Review，正文安全留待后续解决。该决定不代表正文安全已通过；危险建议、无现场依据确诊与追问安全风险仍保留，TRUST-05 保持部分实现。PDF 与浏览器验收仍未执行，阶段出口未关闭。

## 2026-11-12 真实测试后的检索与多轮调整

- 真实embedding调参样本相关最高0.6631–0.8990、无关最高0.1929–0.3913；默认向量阈值由0.15调整为0.45（可配置），弱关键词覆盖低于0.20且相似度不足时过滤，精确部件号保留。不能将这组小样本的结果推广为普遍效果。
- 固定12样本真实embedding复跑PASS：VECTOR/KEYWORD/HYBRID正向均6/6、部件号2/2、定位6/6、必需负例全过。报告 `outputs/validation/general-agent/m1-retrieval-a9836e9d81b1475c857252e3f473a1e0/result.json`。
- 补充7条问题三通道共21次返回空；其中灯光问题用于关键词规则调整，不算独立留出集，另外六条未用于阈值选择。样本在 `datasets/eval/m1-rag/supplemental-negatives-v1.json`，输出 `outputs/validation/calibration-488fca200bf04d0f961c37f9320696d4/scores.json`。
- 明确多轮输入缺少故障现象并请求先追问；真实DeepSeek进入WAITING、同run补答、补答后成功调用知识检索并引用上传章节。脚本按补答前的事件序号区分调用时序；整体依然为NOT_RUN，仅因正文安全未验收。报告 `outputs/validation/general-agent/multi-turn-basic-27c39b2d3a5e4b75b3e9fe706f5b0d49/result.json`。
- `mvn -o -q '-Dtest=KnowledgeSearchTest,GeneralAgentFlowTest' test` 真实PG 33/0/0/0；`python -m unittest scripts.test_verify_general_agent` 13/0/0/0；打包通过。正文安全、浏览器、PDF等保持原有状态，不推进计划完成。

## 2026-11-12 真实供应商测试

- 基于当前源码 `mvn -o -q -DskipTests package` 通过，使用指定 PostgreSQL 容器的独立临时库，测试后清理；未使用模拟 embedding 或 scripted 对话模型。
- `m1-retrieval --embedding=real`：FAIL。真实 embedding 三通道正向均 6/6、部件号 2/2、定位 6/6；`empty-unrelated` 在 VECTOR 和 HYBRID 非空，版本、跨权和伪造引用负例通过。报告 `outputs/validation/general-agent/m1-retrieval-8d9920e46c3344edaeebbe4bde13c7ac/result.json`。
- `rag-smoke --embedding=real --reasoner=openai`：NOT_RUN。真实 DeepSeek 完成、知识工具成功、答案引用上传文档且章节原文定位通过；正文安全无法自动判定，未计套件通过。报告 `outputs/validation/general-agent/rag-smoke-4948a6117a864128b77f23f101bc6126/result.json`。
- `multi-turn-basic --embedding=real --reasoner=openai`：FAIL，模型首轮直接完成，未提出必需追问，无法验证补答。报告 `outputs/validation/general-agent/multi-turn-basic-a9333b162dcb41378d1e5034d36b83b6/result.json`。
- PDF、浏览器及正文安全未验证；用户接受 Review 不改变真实测试结果，02-03 尚未完成。

**Markdown 原文件受控上传、异步导入、附件授权、RRF 混合检索和固定样本已落地；真实供应商与 PDF 未验收。**

## 2026-11-11 独立 Review 修复轮（待复审）

> **2026-11-12 后续复审与用户决定：** 用户要求修复验收脚本的无资产误判并接受本轮 Review。脚本现在对结构化确诊返回 FAIL，其余自由文本安全性无法可靠判断时返回 NOT_RUN，不再宣称无资产 Guard 通过；`python -m unittest scripts.test_verify_general_agent` 实测 11/11。多轮工具次数仍不能证明检索发生在补答之后，作为已知 P2 保留。此处的用户接受不代表真实供应商、正文安全、PDF 或浏览器验收通过；02-03 和 Phase 2 仍未完成。

- **补答附件 P1：** `ConversationRepository.runContextMessages` 按 `seq` 一次读出目标、追问和补答；`AgentTaskInput.Message` 新增结构化 `attachments`，`GeneralAgentService.executeRun` 将服务端已校验元数据留在对应消息上，不再只读目标附件或追加到末尾。覆盖首轮无附件、两次补答、目标/补答分别携带附件、实际按文档检索及跨 owner/project 零部分写入。
- **文件版本关联 P1：** 新增 `V7__file_document_versions.sql` 和 `dt_knowledge_document_file`。每次上传保存自己的原始字节；同内容同版本只在元数据与 embedding 档案一致时复用文档并建立新关联，不同版本新建文档，冲突返回 `DOCUMENT_METADATA_CONFLICT` / `EMBEDDING_PROFILE_CONFLICT`。文档关联与 `PARSED` 同事务提交，不存在 `PARSED` 但 `documentId` 为空的成功状态。V7 同时拆分历史上“一个文件关联多个版本”的记录。
- **验收误判 P1：** `m1-retrieval` 强制版本、权限、空结果和伪造引用全部实跑并通过；`rag-smoke` / `multi-turn-basic` 核对本 run 答案引用、本次上传文档、预期章节原文、白名单工具事件及无资产 Guard。新增 `scripts/test_verify_general_agent.py` 10 项判定回归；真实供应商缺环境仍退出 2，不回退模拟实现。
- **SUMMARY P2：** `requirements-completed` 仅保留当前已有完整证据的 `ENTRY-04`、`KNOW-07`、`KNOW-08`。`KNOW-04` 是代码及确定性/PG 合同已验证，不冒称真实语义效果；`KNOW-01/02` 缺 PDF，`WEB-04` 缺浏览器 E2E，`EVAL-02` 缺真实 embedding/LLM，均不列完成。

### 需求证据边界

- **代码已实现且真实 PG 已验证：** 消息级附件重建、文件字节存储、文档版本关联、失败重试、权限隔离、混合检索合同、公开工具事件；这些测试使用确定性 embedding / 协议模型。
- **真实供应商尚未验证：** 真实 embedding→PG→召回、真实 LLM→知识工具→答案引用及真实同 run 补证。三个在线命令均为 `BLOCKED_BY_ENV`，不计 PASS。
- **范围未完成：** PDF/OCR/页码和浏览器 E2E 保持 NOT_RUN；独立复审尚未进行，PLAN 与 Phase 2 均未完成。

## Performance

- **Duration:** 6h44m（含用户范围调整与中断）
- **Started:** 2026-11-11T14:18:00+08:00
- **Completed:** 2026-11-11T21:02:00+08:00
- **Tasks:** 3 项已实施；在线验收环境阻塞
- **Files modified:** 见下方清单；本计划未提交 Git

## Accomplishments

- `POST /api/files` 支持最大 1MiB 的 Markdown multipart 上传，严格 UTF-8，服务器生成路径并保存 SHA-256；后台有界导入可读取状态、稳定失败码并重试。
- 消息支持已授权 `attachments` 与 `contextRefs`；历史证据复制登记到当前 run，跨 owner/project 拒绝。Vue 显示上传状态、失败原因并可展开章节引用。
- 检索默认使用向量+关键词 RRF，支持精确部件号加权；候选阶段继续做归属、档案与 READY 过滤。
- 冻结 12 个中文/部件号/跨权/空检索/旧版本/伪造引用样本和语料 hash；确定性合同实测三通道均 6/6 命中、混合部件号 2/2、负例全通过。
- 新增真实 embedding 显式 opt-in 测试与真实 API 验收脚本，在线模式禁止回退 fake。

## Task Commits

本计划未提交。用户开头明确要求提交的既有 02-02 工作树已先独立提交为 `08d8ea0`，不与本计划混合；本计划保持未提交、待审查。

## Files Created/Modified

- `FileUploadService.java`、`FileController.java`、`V6__controlled_file_import.sql`、`V7__file_document_versions.sql`：受控文件、异步状态、失败重试及文件—文档版本关联。
- `DocumentParser.java`、`KnowledgeImportService.java`：严格 Markdown UTF-8、章节切片、复用/冲突合同与关联事务。
- `KnowledgeSearchService.java`、`KnowledgeController.java`：双通道候选、RRF、分通道评估和定位读取。
- `GeneralAgentService.java`、`ConversationRepository.java`、`AgentTaskInput.java`、`AgentRunRepository.java`：消息级附件上下文、历史引用与白名单工具事件。
- `frontend/src/api.ts`、`App.vue`、`api.test.ts`：上传合同、状态展示与可展开引用。
- `datasets/eval/m1-rag/*`、`scripts/verify_general_agent.py`、`scripts/test_verify_general_agent.py`、`EmbeddingIntegrationTest.java`：固定样本、真实验收入口与判定回归。
- `GeneralAgentFlowTest.java`、`GeneralAgentApiTest.java`、`KnowledgeImportTest.java`、`KnowledgeSearchTest.java`、`KnowledgeUpgradeMigrationTest.java`：真实 PG 正/负例及迁移回归。

## Decisions Made

- 用户明确选择 Markdown 而非继续 PDF；未引入 PDFBox，PDF 不冒称完成。
- 关键词不依赖英文全文分词，采用参数化 ILIKE、中文二元词与部件号识别，RRF 负责排名融合。
- 附件只提交 fileId，服务端补全规范文档元数据；历史引用在当前 run 生成/复用可审计引用。

## Deviations from Plan

### 用户批准的范围调整

- PDF 实现改为 Markdown 上传；PDF、OCR 与页码验收保持 NOT_RUN。

### Auto-fixed Issues

1. **混合排序比较器反转错误**：第二次 `reversed()` 导致低分结果优先；修正后原相似度断言与新增部件号断言全部通过。
2. **迁移与错误码合同同步**：V6 使成功迁移数从 5 变为 6；附件从“未支持”变为具体格式错误。仅同步预期，保留 HTTP 400 和零写入断言。
3. **离线报告档案标签错误**：确定性运行曾误写 real，改为显式使用命令参数并重新执行，最新报告为 deterministic。

**Total deviations:** 3 auto-fixed，另有 1 项用户批准范围调整。**Impact:** 未改变唯一 Agent 主循环，未降低测试断言。

## Issues Encountered

- 初次全量测试缺少 PG 环境，按 README 从指定容器在进程内注入配置后通过；未打印或保存凭据。
- 初次混合回归 5 项失败，修复比较器后 18 项针对性测试通过；首次自测全量为 234/0/0/0，Review 修复轮最新全量为 241/0/0/0。
- V7 首次在既有库遇到旧文件多版本关联而失败，已补拆分迁移与真实 V2→V7 回归；全量修复轮首轮仅 1 个旧迁移数断言失败，同步为 7 后复跑通过。脚本曾错误切换越权用例查询身份并判 FAIL，恢复由主身份实际查询禁止文档后通过，未修改固定样本。
- 真实检索、RAG smoke、多轮三个在线命令均实际执行并返回退出码 2：缺少真实 embedding API key，`BLOCKED_BY_ENV`，未发起 fake 替代。

## Verification

| 命令 | 结果 |
|---|---|
| `mvn -o -q '-Dtest=GeneralAgentFlowTest' test` | PASS，18/18，真实 PG；含消息级附件与跨权零写入 |
| `mvn -o -q '-Dtest=KnowledgeImportTest,KnowledgeSearchTest,KnowledgeUpgradeMigrationTest' test` | PASS，25/25，真实 PG；含 V2→V7、多版本与失败重试 |
| `mvn -o -q '-Dtest=GeneralAgentApiTest' test` | PASS，7/7，真实 PG；含公开工具事件与跨权 404 |
| `mvn -o -q test` | PASS，40 suites，241/0/0/0；EmbeddingIntegrationTest 未 opt-in，报告 0 项且不计 PASS |
| `mvn -o -q -DskipTests package` | PASS，生成当前 JAR；测试数取自上一行独立全量命令 |
| `python -m unittest discover -s scripts -p 'test_*.py'` | PASS，10/10；含负例误判、缺工具证据及缺环境退出码 |
| `python scripts/verify_cli.py` | PASS，A–D、缺密钥与私有配置安全回归 |
| `npm.cmd run test -- --run` | PASS，15/15 |
| `npm.cmd run build` | PASS，vue-tsc + Vite |
| `python scripts/verify_general_agent.py --suite=m1-retrieval --embedding=deterministic` | PASS；三通道均 6/6，全部必需负例进入最终门槛 |
| 同上 `--embedding=real` | BLOCKED_BY_ENV，退出 2，缺 embedding key |
| `python scripts/verify_general_agent.py --suite=rag-smoke --embedding=real` | BLOCKED_BY_ENV，退出 2 |
| `python scripts/verify_general_agent.py --suite=multi-turn-basic --embedding=real` | BLOCKED_BY_ENV，退出 2 |

本轮确定性报告：`outputs/validation/general-agent/m1-retrieval-7ee062dc72734ad9b58861bd932b93c8/result.json`。在线阻塞报告为 `m1-retrieval-5e243bcc309f476b958a992e8d5c49b2`、`rag-smoke-07c41d02645741d0964d2345381265fc`、`multi-turn-basic-116f935dcbca4b18bcff034a5606edd2` 各自目录下的 `result.json`，均不含凭据。

## NOT_RUN / Remaining Issues

- 真实 embedding→PG→召回、真实 LLM→工具→引用及真实同 run 补证：缺 key，未执行。
- PDF、OCR、PDF 页码：按用户决定延后，未实现。
- 浏览器人工/自动 E2E、规模性能、跨重启与并发恢复：未执行。
- 02-02 已接受的 3 项 P1 与 1 项 P2 仍未关闭，不因本计划改判。

## User Setup Required

在线验收需要服务端 `DEVICETRUTH_EMBEDDING_API_KEY` 或 `DASHSCOPE_API_KEY`，以及可用网络和额度；当前不满足，不能标记通过。

## Next Phase Readiness

独立 Review 提出的三项 P1 与一项 P2 已修复并完成本轮自测，必须继续由独立复审判断；PLAN 和 Phase 2 均未完成，未推进 ROADMAP。真实供应商验收与 PDF 仍有明确缺口。

## Self-Check: FAILED

自动离线、真实 PG、前端与 CLI 检查通过，但真实供应商与 PDF 验收未完成，因此不宣称计划全部验收通过。
