---
phase: 01-baseline-contracts
plan: "02"
subsystem: persistent-conversations
tags: [postgresql, pgvector, flyway, jdbc, product-profile]
requires: ["01-01"]
provides:
  - "product专用会话/消息/QUEUED运行持久API"
  - "真实PG迁移、事务、归属和重启读取验收"
  - "legacy CLI与数据库装配隔离"
affects: ["02-01", "02-02", "03-02"]
tech-stack:
  added: ["Spring JDBC 6.2.19", "PostgreSQL JDBC 42.7.11", "Flyway 11.7.2", "HikariCP 6.3.3"]
  patterns: [server-owned-identity, atomic-message-run-event, product-only-datasource]
key-files:
  created:
    - compose.yaml
    - .env.example
    - src/main/resources/application-product.properties
    - src/main/resources/db/migration/V1__agent_conversations.sql
    - src/main/java/io/devicetruth/server/ProductConfiguration.java
    - src/main/java/io/devicetruth/application/agent/GeneralAgentService.java
    - src/main/java/io/devicetruth/adapter/persistence/ConversationRepository.java
    - src/main/java/io/devicetruth/adapter/persistence/AgentRunRepository.java
    - src/main/java/io/devicetruth/api/AgentConversationController.java
    - src/test/java/io/devicetruth/server/PersistentConversationTest.java
  modified:
    - pom.xml
    - src/main/java/io/devicetruth/app/DeviceTruthApplication.java
    - src/main/java/io/devicetruth/server/DeviceTruthServerApplication.java
    - src/main/java/io/devicetruth/server/ServerConfiguration.java
    - src/test/java/io/devicetruth/server/ServerHttpTest.java
    - README.md
key-decisions:
  - "按用户指定复用interview-postgres：PG16.15/pgvector0.8.7；独立devicetruth库，不改interview_guide"
  - "真实测试使用随机devicetruth_test_*数据库，测试后仅清理自身数据库；不拉镜像、不依赖Testcontainers"
  - "M0只登记QUEUED，不创建Agent worker；旧接口继续兼容"
requirements-completed: [STORE-01, STORE-02, STORE-05]
requirements-partial: [WEB-06, TRUST-06]
completed: "2026-11-09"
review_status: independent_review_passed
coverage:
  - id: P1-PERSISTENCE
    description: "真实PG迁移及自由原文/可空资产/QUEUED原子保存，归属/重复并发/严格JSON/回滚断言通过"
    requirement: STORE-02
    verification:
      - kind: integration
        ref: "src/test/java/io/devicetruth/server/PersistentConversationTest.java（2026-11-10 phase1-policy-package再次实际执行8/0/0/0）"
        status: pass
      - kind: integration
        ref: "src/test/java/io/devicetruth/server/ServerHttpTest.java（2026-11-10本轮16/0/0/0）"
        status: pass
    human_judgment: false
  - id: P1-RESTART
    description: "打包JAR两次独立Java进程读回同原文和同run QUEUED，CLI在不可连接DB下仍可运行"
    requirement: STORE-05
    verification:
      - kind: e2e
        ref: "python outputs/validation/phase1-policy-packaged-check.py（2026-11-10再次实际执行；phase1-policy-packaged-persistence.json）"
        status: pass
      - kind: integration
        ref: "PersistentConversationTest#freeMessageIsAtomicQueuedAndSurvivesServiceRestart（2026-11-10本轮）"
        status: pass
    human_judgment: false
---

# 01-02 完成记录

> 2026-11-10阶段验收更新：独立Review发现CR-01，真实HTTP/PG复现数字正文错误202；HTTP Bean改用Json.MAPPER.copy()并增加原始JSON回归后独立复审clean。全量clean test/package均154/0/0/0（PG8、旧HTTP16），CLI及新JAR跨进程同run QUEUED读回通过；原文/资产/归属/事务边界不变。见[01-REVIEW](01-REVIEW.md)、[01-VALIDATION](01-VALIDATION.md)、[01-UAT](01-UAT.md)。逐项用户确认尚未完成，不关闭Phase 1；出口满足后提交Git并仅执行02-01的授权已记录。下文保留原实施历史。

用户授权执行下一项01-02，随后明确指定使用既有`interview-postgres`。本计划T1/T2/T3已实现并验证；没有执行02-01或其他后续计划，没有Git提交/推送或产品部署。工作起点HEAD为`dbb8b2e567db68a1b8d78a07bbd3b7a03c245bb0`，开始时有上一轮6份规划文档未提交修改，均已保留并按新结果继续更新。

## 实际交付

| 任务 | 交付与证据 | 结果 |
|---|---|---|
| T1 数据库与迁移 | Spring JDBC/PG/Flyway/Hikari依赖；V1迁移；product显式装配；备用Compose | 真实PG空库迁移，扩展vector启用；服务重复启动只校验V1；CLI不创建数据源 |
| T2 自由消息与任务 | 新conversations/runs API；服务器身份；ConversationRepository/AgentRunRepository；事务服务 | 原文/可空资产保存；消息、QUEUED运行、事件及会话版本同事务；重复键409；归属拒绝不泄漏 |
| T3 重启与兼容 | PersistentConversationTest 7项；新JAR两进程重启；CLI校验 | 测试与打包153/0/0/0；真实进程重启保留同一任务；不可连接DB环境下CLI B仍完成 |

## 数据库选择、范围与凭据

- 用户指定容器`interview-postgres`，镜像`pgvector/pgvector:pg16`，实测PostgreSQL **16.15**、pgvector **0.8.7**，端口5432。固定镜像摘要`sha256:7b822b0aac60967beb1ea5e576b8602c94c300a157d187f385ae3e0da199b90a`。
- 原`interview_guide`库仅执行了只读表数量基线检查（public表17张）；没有在该库执行DDL/DML。用户指定后启动既有容器，新建独立`devicetruth`库用于打包演示；未复用或更改原业务表。
- 集成测试每次在同一实例创建`devicetruth_test_<32位随机UUID>`空库，完成后关闭连接并删除本轮自身测试库。不会清空任意既有数据库，也不需要新容器/镜像。
- 凭据从已授权容器配置读取后仅用于内存/子进程环境，不打印、不保存到仓库/报告；不读取真实模型私有文件。README给出服务端环境注入方式。
- Compose作为独立环境的备用资产，只有显式`standalone-db` profile才启动，固定上述本地镜像摘要且`pull_policy: never`；本次只运行config校验，没有启动第二个PG实例。

## 实际命令与结果

Maven为3.9.9、JDK21.0.13.0.2；新增依赖第一次使用联网`mvn -q -U -DskipTests test-compile`成功，未用离线缓存替代首次依赖获取。数据库测试前通过当前进程环境提供`DEVICETRUTH_TEST_PG_BASE_URL=jdbc:postgresql://127.0.0.1:5432/`及用户名/密码，复现方式见README。

| 实际命令 | 退出码 | 结果 |
|---|---|---|
| 初次PG17镜像pull/Testcontainers测试 | 失败/停止 | 拉取无进展导致ContainerFetchException，业务测试未执行；随后按用户要求停止拉取并改用既有interview实例 |
| `mvn -q '-Dtest=PersistentConversationTest' test`（interview实例） | 0 | 7 tests / 0 failures / 0 errors / 0 skipped |
| `mvn -q test` | 0 | 30 suites，153/0/0/0；原146项及新增7项均运行 |
| `mvn -q package` | 0 | 同上153项通过，重建JAR |
| `docker compose --profile standalone-db config --quiet` | 0 | 使用临时环境密码安静校验，未打印密码/未启动容器 |
| `python outputs/validation/01-02-packaged-check.py` | 0 | 新JAR：201会话、202消息/QUEUED、两次独立Java进程读回一致、health UP；CLI注入不可连接DB且启用product仍RESOLVED/B规则；密码扫描通过 |
| `python scripts/verify_cli.py` | 0 | help、A/B规则、C冲突、D缺证、非法参数、缺Key不降级及假Key本机HTTP合同通过 |
| `git diff --check` | 0 | 业务及规划文件无空白错误；生成报告/日志均忽略 |

Surefire实际XML统计153项，新增7项验证：原文/Unicode/可空资产与服务重开；重复提交；并发同键及连续序号；事件插入失败使消息/run/版本全部回滚；跨owner/project及body身份伪造；数据库复合外键/事件序号；非法/未支持输入整体拒绝。

打包演示会话`ecbc60a5-b6d6-488f-baf1-041dd465bdce`，run `d65bc93a-0c1e-441d-b827-b0441559f34a`；重启后保持QUEUED，原文未改变。CLI无DB场景B报告`b0cd2960-ac3b-4486-9646-fd400d1ebf81`。最终verify_cli的B报告`b4f6b119-d4cd-4787-a4df-3fcc6254dcf4`，7步34事件；A为7/34，C为7/35，D为6/29。E安全负例由全量AcceptanceTest覆盖。

原始日志、临时验收脚本与跨进程摘要位于已忽略的`outputs/validation/01-02-*`；它们不作为业务源码提交。测试输出可按README命令重建，代码/迁移/测试保留在src。新增测试不是镜像成功即PASS，真实HTTP/事务/重启断言均已执行。

## 关键代码与真实链路

以“充电经常中断，没有故障码，先看哪些资料？”为例：客户端先创建conversation，再带`Idempotency-Key`提交`schemaVersion=1/userMessage`，资产可以不提供。`AgentConversationController`不接收客户端身份字段；`GeneralAgentService.submit`先校验消息/版本和可支持字段，再用服务器固定身份核对会话。

事务内由`ConversationRepository.require(..., true)`锁住会话行，分配消息序号并原样保存文字；`AgentRunRepository.enqueue`写QUEUED运行和第一条安全事件。消息、任务、事件、会话版本任一失败全部回滚。响应202只表示存储完成，Location指向可查询的run；当前不启动推理器或worker。

GET会话/消息/任务都按服务器owner/project从PG读取。关闭整个Java进程后，新进程通过`ProductConfiguration`创建Hikari数据源并先运行Flyway校验，再装配同一服务，因此读回同一会话/run，不依赖旧Java对象。数据库的同归属复合外键及请求键/事件序号唯一约束同时保护应用边界。

`DeviceTruthApplication`和独立Server禁用自动数据库装配，由ServerConfiguration只在product profile下显式引入ProductConfiguration。默认legacy HTTP与CLI继续原链路；CLI使用JSON/原Gateway/EvidenceManager/DecisionGuard，与新PG事务入口分开，设备能力仍只读。

## 偏差及后续边界

- 最初未先盘点本地镜像就选择PG17并拉取，这是本轮环境准备失误；保留失败记录，用户纠正后使用既有PG16实例，不再下载。取消了临时引入的Testcontainers依赖，真实测试改为显式配置的独立PG测试库；版本和Compose同步固定。
- 为保证数据库按product且只在HTTP入口装配，新增ProductConfiguration并修改两个Main的自动装配排除。旧ServerHttpTest明确使用scripted，避免读取开发机模型配置/调用外部模型；测试数未删减。
- WEB-06仍部分实现：本轮只有conversations/runs存储接口，files/assets及完整Agent交互留后续。STORE-02在M0消息/QUEUED切片内完成，不能据此宣称运行中工具/WAITING事件全链路已完成。
- 附件/context引用非空及replyTo当前整体拒绝；请求键重复返回409，不是M2的完整幂等重放。资产字段只是线索，未查询/确认真实资产。普通自由Agent、RAG、embedding、WAITING续跑、跨进程执行恢复、Vue/SSE均未实现/未验收。
- 独立Review未运行，M0两个计划虽已完成，阶段出口仍待Review；不自动进入02-01，不虚构阶段或在线验收通过。
