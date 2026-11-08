# DeviceTruth V0.2｜跨会话状态快照（State）

> 此文件只记录**最新有效状态**；每个里程碑通过 Codex Review 后更新。不要复制聊天记录、完整测试日志或长篇 Review。

## 1. 当前总体状态

- 计划：S0 → S1；后续 S2 → S3 → S4，不调整。
- 当前阶段：**S0 已完成并关闭**（M0 PASS）。
- 当前里程碑：**M0 = DONE**（2026-11-08，`reviews/M0.md` #2 = PASS；未解决 P0=0、P1=0、P2=0）。
- 下一里程碑：**M1（S1）**，仍是 `TODO`，本会话未开始任何实现。
- 文档规格状态：**FROZEN**（M0 Review #2 PASS 后由 FROZEN_CANDIDATE 转入，变更表已记录；此后改规格须走 `spec.md` §10 并经用户批准）。
- 当前开发基线 commit：**`14952b6`（main）→ 本轮用户授权后建立本地 checkpoint commit**（M0 交接提交，subject：`docs: 记录 M0 通过、冻结 V0.2 spec 并交接至 M1`；哈希请用 `git log -1 --format=%H` 复核，本文不内嵌以免自相矛盾）。无 tag、无 stash、**未 push**。
- 工作树状态：本轮交付 6 份文档（5 改 + 新增 `reviews/M0.md`）加 `.gitignore` 一行（忽略 `outputs/baselines/`）；**代码零改动**。`outputs/baselines/M0/` 另存提交前补丁与哈希，仅作提交前对照，已被忽略。
- 是否完成 S1：**否**。

## 2. 里程碑进度

| ID | 状态 | Review | 交付摘要 |
|---|---|---|---|
| M0 | **DONE** | `reviews/M0.md`（#1 CHANGES_REQUIRED → #2 PASS） | `S0_AUDIT.md` 审计 + `spec.md` 冻结 + Review P1/P2 修复 |
| M1 | TODO | 未执行 | 待双入口/Catalog/Session/Router |
| M2 | TODO | 未执行 | 待 Query/Goal→Core |
| M3 | TODO | 未执行 | 待异步 Run/Events + S1 最终验收 |

## 3. 已确定的**设计意图**（S1 契约已冻结，实现待 M1–M3）

- 保留 V0.1 自研 `TaskOrchestrator`/Guard/证据机制，不使用 ReActAgent 替代循环。
- S1 用 HTTP 进入自然语言路由，再分别执行受限 QUERY / DIAGNOSE；CLARIFY 和 UNSUPPORTED 不伪造执行。
- CLI 默认 JAR Main 保留；Web 单独 Main，装配集中在 `io.devicetruth.server`（现网事实：`core/`、`adapter/`、`app/` 无 Spring 组件注解）。
- 模拟数据/业务时间与请求时间分离；每任务独立 Reasoner；最终事件结束后可读。
- S2 再做 Hypothesis 专业调查闭环、补证恢复；S3/S4 不提前。

## 4. M0 收尾已核实的事实（细节见 `reviews/M0.md` §8.4、`S0_AUDIT.md`）

- Review #2 实跑：`mvn -q '-Dtest=AcceptanceTest,RuntimeTest' test` 退出 0（**9 项**，0 失败/0 错误/0 跳过）；`WHITELIST_PROVENANCE=PASS 5+8+15=28 distinct values`；两份真实 B 报告均为 **34 条事件 / 8 类**；`Start-Class=io.devicetruth.app.DeviceTruthApplication`；`git diff 14952b6 -- src pom.xml scripts datasets skills .mvn .vscode` 为空。
- 首审同代码基线：`mvn -q clean test` 退出 0（**42 项**）、`mvn -q package` 退出 0、CLI A/B/C/D 退出 0（7/7/7/6 步；A/B RESOLVED、C CONFLICT、D NEED_EVIDENCE）、`--reasoner=invalid` 退出 2、E 负例 1 项通过、`python scripts/verify_cli.py` 退出 0。**9 项与 42 项不混用，本轮未重跑全量/打包。**
- 交接时自查（2026-11-08）：`git diff 14952b6 -- src pom.xml scripts datasets skills .mvn .vscode` 仍为空，`HEAD=14952b6`，分支 `main`，无 tag/stash → 上述门禁证据对当前代码继续有效。
- 环境：项目内 JDK `21.0.13.0.2`、Maven 3.9.9（UTF-8）。**默认 `java` 是 17**，每次必须显式 `JAVA_HOME`，Git Bash 下需 POSIX 路径才能让 `PATH` 前置生效。
- `pom.xml`：parent 3.5.16、`java.version=21`、agentscope 2.0.4；**无 `spring-boot-starter-web`**，`spring-boot-maven-plugin` **未配置 `mainClass`**（当前靠自动探测 CLI 主类）→ M1 首项工作。
- Core 事实（未变）：`TaskOrchestrator.run(DiagnosisRequest,Budget,Reasoner)`；`reportPath` 目录 UUID **≠** `state().taskId()`；源码识别 **11 类**事件、scripted B 实跑触发 **8 类**；`Reasoner.Context` 曾整体进入 `REASON_INPUT.data`。
- Repository 只有 `profile/history/search/get/query`，**无** `domainOf/listAssets/listSymptoms/exists`；`CapabilityGateway.execute` 需完整诊断 Scope，事实查询不得借用。
- 数据：A–D 四场景同 `domain=automotive-demo`、同 `symptomCode=COMM_TIMEOUT`、同 `asOf=2026-11-03T04:00:00Z`；`CAR-B-FIELD-1` `observedAt=2026-11-03T03:00:00Z`、`validTo=2026-11-04T00:00:00Z`。

## 5. 未解决的风险与限制

- **`spec.md` §4.8 安全映射只在契约层存在**（M0-P1-01 = CLOSED 指契约，不指实现）。Core 仍会把模型自由 `reason` 写进 `stopReason`/`diagnosisSummary`/`NEED-AGENT` 描述/`TERMINATED`。M3 必须实现 28 项白名单过滤并用“合成路径 + `PROMPT_FRAGMENT`”合法提案做负例，证明 GET 结果与 `/events` 不回显；Core 内部报告保留原值。
- **在线一律 `BLOCKED_BY_ENV`**：本轮交接环境未检测到 `DASHSCOPE_API_KEY`（只判断存在性，未读取值），也未执行 `verify_online.py` 或任何真实 Qwen/OpenAI 兼容调用。历史在线结论仅作 `[HISTORICAL]`，不构成 S1 在线证据。
- **Server 完全未验证**：`DeviceTruthServerApplication`、健康端点、真实启动命令与端口都不存在，M1 必须实测并留日志；`mainClass` 显式化与 `Start-Class` 回归同属 M1。
- **`HypothesisStatus.SUPPORTED` 在主源码中从未被赋值**（实际只有 `OPEN`/`INVALIDATED`）→ 任何文档/答复不得宣称假设驱动 O-H-E-D 闭环；已登记 S2。
- 未解决 P2：**0**（M0-P2-01 措辞已 CLOSED）。以下为非 Review 项、但下一里程碑必须遵守的约束：M1 临时 `501` 分支在 M2/M3 完成后必须有测试证明不可达；M3 队列拒绝须同时移除注册项，避免永久 `QUEUED`；`ExecutionStatus.FAILED` 不得包装成 `COMPLETED`。
- 工作树在 M1 开始时：M0 文档已由 checkpoint commit 收进 HEAD，`git status` 应为空。M1 的 diff 基线 = 该 checkpoint commit（`git diff <checkpoint>..HEAD`），**不要**再用 `git diff 14952b6...HEAD` 作为交付范围（那圈的是 M0 文档）。

## 6. 下一里程碑交接卡（开发者开始工作时首先读取）

```text
MILESTONE_ID: M1
PHASE: S1
PRECONDITION: M0 = DONE（reviews/M0.md #2 PASS），spec.md = FROZEN
BASELINE: 本地 checkpoint commit = 本交接提交（subject: `docs: 记录 M0 通过、冻结 V0.2 spec 并交接至 M1`）
          定位：git log -1 --format=%H；HEAD 的父提交仍是 `14952b6`
          旧口径 `git diff 14952b6 -- <路径>` 圈的是 M0 文档，不再代表 M1 交付
          提交前对照物（已被忽略、不入库）：outputs/baselines/M0/ 的 M0-delivery.patch、
          untracked/reviews_M0.md、SHA256SUMS.txt（可 sha256sum -c 自校验）
          未 push、无 tag、无 stash
READ_FIRST（本轮只需这些，不要全仓扫描）:
  1. docs/DeviceTruth_V0.2_S0_S1/state.md（本文件）
  2. docs/DeviceTruth_V0.2_S0_S1/task.md §3（M1 必做项 + M0 冻结补充）
  3. docs/DeviceTruth_V0.2_S0_S1/spec.md §3/§4/§6/§7（含 §4.2 症状别名、§4.8 安全映射）
  4. docs/DeviceTruth_V0.2_S0_S1/S0_AUDIT.md §C/§F/§K（源码事实与偏差）
  5. pom.xml
  6. src/main/java/io/devicetruth/app/DeviceTruthApplication.java
  7. src/main/java/io/devicetruth/core/capability/Repositories.java
  8. src/main/java/io/devicetruth/adapter/JsonRepositories.java
  9. datasets/scenarios.json
GOAL:
  双 Main（CLI 仍为默认 JAR Main，mainClass 显式化 + Start-Class 回归）
  + spring-boot-starter-web + loopback Server 真实启动与健康端点
  + SupportedScenarioCatalog（只从打包 scenarios.json 派生，未知 SN-0245 不映射到 CAR-B）
  + Session API（201/合法 UUID/线程安全有界）
  + 四类路由 QUERY/DIAGNOSE/CLARIFY/UNSUPPORTED（含 429/503/404 契约）
MUST_PROVE:
  - 真实启动日志/端口/健康端点；`Start-Class` 打包核对
  - 原 CLI A/B/C/D 与既有测试回归未退化
  - 未实现路径只能是明确 `501 NOT_IMPLEMENTED`，不得伪造 200/DTO
DON'T:
  - 不改 Guard/Orchestrator/EvidenceManager，不新增第二套 Agent 编排
  - 不放宽扫描范围到 io.devicetruth 全包；core/adapter/app 不加 Spring 组件注解
  - 不伪造 Repository 的 domainOf/listAssets/listSymptoms/exists
  - 不宣称已完成在线模型验收、Server 已通过或假设驱动闭环
  - 不发起未经授权的 git reset/clean/commit/push/tag
```

## 7. 本文更新规则

每次 Codex Review `PASS` 后更新：里程碑状态、Review 文件路径、有效事实、遗留 P2/阻塞、下一个 `MILESTONE_ID` 和下一轮需读文件；然后在用户授权下建立本地 checkpoint。`CHANGES_REQUIRED` 时保持同一里程碑，不推进。**任何 Review 报告只证明其真实审查过的范围。**
