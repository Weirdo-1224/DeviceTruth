---
phase: 05-workbench-quality
plan: "02"
status: complete
completed: 2026-11-13
requirements-completed: [WEB-05, OPS-04, EVAL-01, EVAL-03, EVAL-04]
coverage:
  - id: P5-WORKBENCH-REPORT
    description: "诊断报告：已知事实/候选原因/建议/人工核验/引用（版本+章节+页码）与空/等待/失败/取消状态"
    requirement: WEB-05
    verification:
      - kind: unit
        ref: "frontend Vitest 36/36（含取消/POST 与资产查询映射新增用例）+ npm run build（vue-tsc）"
        status: pass
      - kind: e2e
        ref: "浏览器错误/断线场景与旧页面回归：scripts/verify_sse_browser.py 五场景 PASS（报告卡片渲染于同一 dist）"
        status: pass
    human_judgment: false
  - id: P5-WORKBENCH-FACTS
    description: "事实冲突对照：按组件+属性展示来源/有效期/核实状态，冲突行高亮；无记录明确提示；数据只来自只读资产接口"
    requirement: WEB-05
    verification:
      - kind: unit
        ref: "frontend api.test.ts readAsset 用例（含 404 映射）"
        status: pass
      - kind: integration
        ref: "verify_v1_e2e.py E2E-05/E2E-10（资产组合视图多来源与未确认；变体冲突判定由 DualDomainAgentTest）"
        status: pass
    human_judgment: false
  - id: P5-E2E-MATRIX
    description: "v1-cases 14 例逐项状态（PASS/FAIL/NOT_RUN/COVERED_BY_JUNIT）+ 原始证据"
    requirement: EVAL-01
    verification:
      - kind: e2e
        ref: "scripts/verify_v1_e2e.py（隔离临时库，scripted+deterministic）：PASS 6、COVERED_BY_JUNIT 4、NOT_RUN 4、FAIL 0；见 ACCEPTANCE-RESULTS.md 与 outputs/validation/v1-e2e-*"
        status: pass
    human_judgment: false
  - id: P5-DEFERRED-SAFETY
    description: "02-02 复审保留的 P1×3/P2×1：混合句危险动作、未核验确诊、追问绕过安全检查、否定性误拒"
    requirement: EVAL-03
    verification:
      - kind: unit
        ref: "AnswerGuardTest 25/25（新增 4 组正负例：原文复现三条 + 追问边界）"
        status: pass
      - kind: integration
        ref: "GeneralAgentFlowTest 19/19（新增 dangerousFollowUpQuestionIsRejectedBeforeItReachesTheUser：ACTION_REJECTED 有界重试后 FAILED/UNSAFE_ANSWER，追问不落库）"
        status: pass
    human_judgment: false
  - id: P5-PDF-STATUS
    description: "PDF 明确状态：登记文件但 OCR_REQUIRED/PDF_NOT_SUPPORTED，无正文/页码/文档，重试不进入无限循环"
    requirement: EVAL-04
    verification:
      - kind: integration
        ref: "KnowledgeImportTest 8/8（PDF 202→OCR_REQUIRED、documentId null、重试保持；其它类型仍 415）"
        status: pass
    human_judgment: false
---

# 05-02 完善工作台并运行完整质量评测

> 2026-11-13 实施与验收。05-02 原计划三个任务：工作台（前端）、评测矩阵、正式离线/PG/UI/真实模型验收。本轮的评测结论基于隔离临时库的产品 HTTP 实测与既有 JUnit 证据分层记录。

## 交付

### 1) 工作台（前端，主代理实现；原子代理配额用尽）

- 新增 `frontend/src/components/DiagnosisReport.vue`：结构化诊断报告——摘要、类别/风险/数据性质徽标、已知事实（SUPPORTED/CONFIRMED）、候选原因（POSSIBLE）、人工核验与限制、建议行动、引用列表（标题/版本/章节/页码，点击展开原文）；失败/取消/等待/空数据都有明确状态文案；高风险显示服务器受控安全提示。
- 新增 `frontend/src/components/EvidenceComparison.vue`：选择资产后读取 `GET /api/assets/{id}?asOf=`，按组件+属性分组展示来源（出厂/改造/旧模板/现场核验）、有效期、核实状态，冲突行高亮，并展示装机判断/冲突状态/待核验项与维修记录；无记录明确“未读取到该资产的配置记录”。
- `App.vue`：报告卡片替换原浅层答案展示；资产事实对照随选择联动；运行中/等待中显示“取消本次调查”（`POST /api/runs/{id}/cancel`）；FAILED 显示可读原因与重试引导；状态栏显示当前 run 的领域 Skill（`GET /api/runs/{id}/skill`）。SSE 时间线、历史恢复、追问/补答、上传流程保持可用。
- `api.ts`：新增 `cancelRun`、`readRunSkill`（SKILL_NOT_SELECTED→null）、资产组合类型与 `readAsset`，保持既有 SSE 合同不变。
- 前端实测：`npm run test -- --run` **36/36**、`npm run build`（vue-tsc）通过。

### 2) 评测矩阵（`scripts/verify_v1_e2e.py`）

- 读取冻结的 `datasets/eval/v1-cases.json`（14 例、评分边界、身份、夹具、Skill 版本），在**隔离临时库**起产品服务（scripted + deterministic embedding，模拟数据）逐例执行并记录 PASS/FAIL/NOT_RUN/COVERED_BY_JUNIT 与证据。
- 离线结果（`outputs/validation/v1-e2e-ba30cbde55ff/results.json`）：**PASS 6**（01 无故障码入口、02 知识检索含版本/章节、03 缺设备信息不确诊、04 CAR-B 模拟场景、07 同 run 多轮补证、09 跨身份 404）、**COVERED_BY_JUNIT 4**（05 三来源冲突变体、08 取消/恢复、10 时点切换、11 模型/工具故障码）、**NOT_RUN 4**（06 Markdown 版本定位与在线语义、12 高风险路径、13 双域按 run 切换、14 完整真实模型链路）。原始服务器日志与结果同目录。

### 3) 延期项（安全 + PDF）

- `AnswerGuard` 结构性关闭 02-02 复审保留项：① 子句切分加入逗号/顿号，禁止词只在自身子句有效——“不要短接互锁端子，随后短接高压互锁端子”拒绝；② 新增“确定性完成时态”断言（已经损坏/故障原因是/确定是…），前句的“可能”不能软化后句的确诊；③ 确诊表述逐子句判定且先判软化词——“尚未确诊为控制器损坏”不再误拒，“已确诊为…”仍拒绝；④ 资质边界句只放行名词性列举（“任何涉及…的操作，都必须由…”），祈使动作（请/立即/随后…）仍拒绝。
- `TaskOrchestrator` ASK_USER：追问 question/requestedFields 过同一安全边界（`AnswerGuard.unsafeFollowUp`），越界记录 ACTION_REJECTED 并按有界重试纠正，超限 FAILED/UNSAFE_ANSWER，追问永不落库。
- PDF：上传登记后立即置 `OCR_REQUIRED / PDF_NOT_SUPPORTED`（mime=application/pdf），不派发解析、不产生正文或页码；重试保持明确状态；其它不支持类型仍 415；解析入口加防御，非 Markdown 记录绝不进入 Markdown 解析。

## 实际命令与结果

| 命令 | 结果 |
| --- | --- |
| `cd frontend && npm run test -- --run` | 36/36 |
| `cd frontend && npm run build` | 通过 |
| `mvn -o '-Dtest=AnswerGuardTest' test` | 25/25 |
| `mvn -o '-Dtest=GeneralAgentFlowTest' test` | 19/19 |
| `mvn -o '-Dtest=KnowledgeImportTest' test` | 8/8 |
| `mvn -o -DskipTests package` | 成功 |
| `python scripts/verify_v1_e2e.py --embedding deterministic --reasoner scripted`（隔离库） | PASS，summary PASS 6 / COVERED 4 / NOT_RUN 4 / FAIL 0 |

## 偏差与后续

- 评测脚本原设计为 `verify_general_agent.py --suite=all`；实际新增独立 `verify_v1_e2e.py`（不改动既有套件语义，直接读冻结的 v1-cases）。计划文件路径偏差已记录。
- 工作台计划里的 `frontend/src/components/DiagnosisReport.vue`、`EvidenceComparison.vue` 已按计划创建；`frontend/src/api.ts` 类型为工作台服务，未引入新后端接口。
- 资产冲突变体（三来源冲突、时点切换）需要导入快照，产品 HTTP 没有导入接口，因此由 DualDomainAgentTest 的变体用例覆盖，脚本以 COVERED_BY_JUNIT 记录，未冒充 PASS。
- 在线真实供应商的 14 例执行在本轮以独立命令尝试（见 ACCEPTANCE-RESULTS 在线段）：仅发送冻结评测语料，不含个人信息；结果按实际记录。
