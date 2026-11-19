# 第四组实施交接提示词

请使用 `$openspec-apply-change` 继续实施已确认的变更 `add-authenticated-team-workbench`。

工作目录：`D:\Java_Project\DeviceTruth`。
变更目录：`D:\Java_Project\DeviceTruth\openspec\changes\add-authenticated-team-workbench`。

本会话授权范围是 **完整完成 tasks.md 第 4 组（4.1–4.5），统一验证并交付后再返回**，不要每做完一项就结束会话。不要重新提案、重新讨论已确认原型，也不要自动推进第 5–8 组或其他旧变更。确有阻塞或必须改变规格时再集中提出。

## 先恢复上下文

1. 阅读仓库 `AGENTS.md` 及 OpenSpec apply skill，检查当前 Git 状态和最新提交，保留其他人的修改。
2. 执行 `openspec status --change add-authenticated-team-workbench --json`、`openspec instructions apply --change add-authenticated-team-workbench --json`，读取其列出的全部上下文。
3. 阅读 proposal、design、五份 specs、tasks、prototype/workbench.html，以及 acceptance.md、data-foundation.md、account-api.md、workspace-api.md、authorization-runtime.md。
4. 第 1–3 组已经完成，进度 **11/33**；第 4 组尚未勾选。当前最大 Flyway 版本是 **V22**，追加迁移前重新核对实际版本，不修改 V1–V22。

## 第 4 组要交付的行为

- **4.1 讨论**：调查内不可变讨论、真实作者/时间和分页。发表讨论不创建 Run，不自动进入模型历史、摘要、知识索引或证据。
- **4.2 显式引用与快照**：消息 DTO 保持 schemaVersion=1、contextRefs 和原字段兼容，追加讨论引用。服务器解析 `{discussionId,version}` 并保存作者/时间/原文/未核实性质，拒绝伪造和跨调查引用。建立真正用于执行与恢复的正式输入、讨论和附件版本快照，运行后新增材料不得注入当前段。
- **4.3 串行与冲突**：只对新认证 scope 收紧普通提交，复用会话行锁，先判原幂等键，再判期望版本和任一 QUEUED/RUNNING/WAITING。至多一次 enqueue/dispatch，冲突 409、不自动换键或排队，轮号包含取消尝试且单调。旧内部多轮合同保留，旧多未决记录不能删除。
- **4.4 协作补答**：沿原 runId/pendingQuestionId/questionVersion 三元组和 CAS，只恢复同一 Run；两成员竞争仅一次成功，失败方不得转新消息。记录补答及执行段 actor，固定本段输入/材料，重启不改变排查对象与时点。
- **4.5 取消**：只有有写权限的当前执行段发起人或本空间管理员可以取消；普通同事不能取消他人执行段。保留输入、引用、结果和历史，拒绝迟到响应；作者字段按安全白名单展示，取消轮号不能复用。

## 必须复用的现状和注意点

- `HttpAccess` 已按登录 principal、空间成员和资源元数据解析 AccessScope；集合无 Header 只默认个人空间，UUID 资源路径先找实际空间再授权。禁止匿名固定身份 fallback。
- `GeneralAgentService.forScope` 是固定身份轻量视图，共享原 TaskOrchestrator、有界 executor、pendingDispatch 和心跳；不是新主循环。worker 的 executionService 从持久 Run 恢复 scope/actor，不依赖请求 ThreadLocal。
- 已保存调查 workspace/创建人、Run initiated/execution actor、消息 actor、input_snapshot 和 dt_run_input_segment。**现有 snapshot() 只是第 3 组写下的基础记录，不代表第 4 组的讨论/附件快照和执行读取已完成。请阅读实际实现并接通完整合同。**
- 已有会话锁、原 UUID 幂等键、补答三元组、版本 CAS、租约/检查点、SSE 去重续传与有限结果；保留它们及原 Guard/来源语义，不重写第二套状态机。
- product 的 `recoverAuthenticatedQueued` 只自动恢复已绑定空间任务；旧内部 `recoverQueued` 仍能用于明确的系统维护范围，不自动认领旧数据。
- `ToolContext` 已兼容增加 actor/workspace；SkillRuntime 按空间安装/选择，服务器默认安装不会覆盖停用状态，不复制他空间许可。
- 当前撤权检查和 SSE 的 Session/成员复核已经接通，补答/取消改动不能破坏 AUTHORIZATION_REVOKED 或退出后旧流停止输出。
- 旧 `/api/files` 和 `/api/knowledge/*` 仍是显式空间知识导入；不要因本组快照而把旧入口静默改成必须有调查 ID。调查附件上传/发布分层是第 5 组，按设计处理本组必要依赖，不偷偷扩大或缩小任务。
- 前端认证/协作接线在第 6 组，当前源码不能视为完整浏览器产品验收通过。

## 与管理端开发者的边界

对方负责五个管理模块及平台运营能力。本 change 负责账号/认证/空间/产品授权底座和共同调查。不新增平台用户管理页、平台 ADMIN 角色或 `/api/admin/*`，不另建账号体系。空间 ADMIN 不等于平台管理员。共享方法一次一个负责人，不覆盖其他人的并行修改。

## 验证环境与已有证据

- Java 21、Maven 3.9.9；先 `mvn -version` 确认。PowerShell 不支持 Bash 的 `{a,b}` 路径展开，读取文本使用显式 UTF-8。
- 已有临时隔离容器 `dt-workbench-migration-test`，pgvector/PG16，绑定 `127.0.0.1:54339`、用户 `dt_test`，无持久卷、host trust，只用于本机隔离验证。新会话先用 Docker 检查实际状态，访问引擎/本机端口若被沙箱拒绝，按工具权限机制申请，不把它误判为缺数据库。
- 测试环境变量：`DEVICETRUTH_TEST_PG_BASE_URL=jdbc:postgresql://127.0.0.1:54339/`、`DEVICETRUTH_TEST_PG_USERNAME=dt_test`；password 使用该 trust 测试实例所需的任意非空测试值，不读取产品库密码。各测试创建并清理随机 devicetruth_test_ 数据库。
- 产品库容器 `devicetruth-postgres-1` 使用 54329，**不要用于本轮写入/迁移验证**。现有 backend/frontend 未重启，运行库未升级、真实资料未认领。
- `AuthenticatedProductTestSupport` 已把原固定身份 HTTP 夹具迁移成真实 Cookie/CSRF 登录，只允许随机测试库，并显式认领旧测试夹具；它不是产品匿名后门，也不是正式认领工具。新测试可复用同类真实认证路径。
- 第 1–3 组最新定向有效证据汇总 **98 项：通过 96、失败 0、错误 0、跳过 2**，不是一次全量套件。完整命令和失败/修复历史见 acceptance.md。两项跳过是 RunRecoveryTest 的显式 opt-in 真实模型测试，不能计为通过。
- 状态竞争、模型等待中撤权等确定性测试可以明确使用协议/阻塞替身；真实 PG 必须实际运行。日常 product 联调默认配置好的真实外部模型，不静默降级；真实模型/embedding、SMTP 收件、模拟资料和现场数据分别记录。真实收件/TLS、完整浏览器及外部模型验收尚未完成。

### 交接后新增的真实模型补验（2026-11-19）

用户随后要求先测真实模型、邮件暂缓。AuthenticatedRealModelTest 已实际调用 `deepseek-flash`，Run `c96da39d-263f-4b35-84f8-c30fdab28ab0`；认证团队→真实模型→工具→持久来源→成员回读/外部拒绝链路通过，1 项 JUnit 通过。但答案降为 HUMAN_REVIEW，原因为 UNCONFIRMED_FINDING，正常 KNOWLEDGE 答案质量未通过。5 次模型尝试中 1 次格式错误重试、4 次合法提案，3 次成功工具执行、4 条来源。embedding 为 deterministic，资料为 SIMULATED。详情见 acceptance.md 最新补验条目及忽略目录中的脱敏报告。

这一补测及记录是在提交 8575b83 之后增加的；新会话须检查实际 Git 工作区。不要把该问题遗失，也不要把它默认为已修复或因此自动扩大第四组；若用户要求修复答案质量，先读取拒绝条件并用定向证据区分模型草案问题和 Guard 过度拦截。邮件收件和完整前端仍未验收。

### 独立 Guard 变更与排期

用户已通过逐项讨论确认新的 Guard 产品边界，另建 `improve-evidence-aware-answer-guard`，详见该 change 的 proposal/design/specs/tasks。当前只完成规划，15 项实施任务均未执行。推荐顺序：本第四组 → 第五组 → Guard 服务端/协议/组件 → 第六组接线，再联合浏览器验收。第四组不要混做 Guard 或自行放宽安全规则；第五组交付后应提示按独立 Guard change apply，不自动切换变更或勾选其任务。Guard 不必等工作台 33 项全完成才实施。

## 完成方式

按 tasks 的依赖顺序实施 4.1–4.5，补齐必要调用方、恢复与错误路径。按实际证据更新 tasks.md 和 acceptance.md，未验证的行为不得勾选；保留原失败历史，不靠改断言或重定义旧入口合同迁就实现。只重跑受改动影响的检查，复用仍有效的证据。

真实运行库认领、部署、破坏性操作及 Git 推送需另行授权；本会话只实施和隔离验证。整组完成后，用通俗中文解释“成员讨论→显式引用/正式提交→串行裁决→原 Run 主循环/同轮补答→持久结果和作者”的实际链路，列出执行命令、真实测试统计和未验证项，并给出剩余问题及下一组入口。
