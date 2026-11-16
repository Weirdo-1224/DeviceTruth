# Legacy 与产品 Agent 装配隔离记录（P2-1 第一步）

日期：2026-11-14

## 背景与问题

审查清单 P2 第 1 条：legacy 诊断与产品 Agent 并存于 `TaskOrchestrator`（`run()` 与 `runAgent()`）与 `ServerConfiguration`，增加学习与维护成本。核实发现比描述更进一步：产品容器虽以 `--spring.profiles.active=product` 启动（`Dockerfile:18`），但 `ServerConfiguration` 无 profile 限制，legacy 整套 Bean（场景目录、Router、内存会话、异步诊断）与 legacy 接口（`/api/sessions`、`/api/diagnoses`）在产品部署中同样被装配并暴露，产品链路并不使用。

## 决策

按"短期明确隔离边界、不删除旧代码"处理（审查建议）：

- 隔离方式选 profile 硬边界（`legacy`），而非仅文档约定——让产品部署从机制上就不实例化 legacy 栈，缩小内存占用、启动开销与暴露面。
- 不改 `TaskOrchestrator`：`run()` 保留为离线回归安全网（冻结纪律：只修回归、不加新功能）。
- `/api/health` 原本定义在 legacy `ConversationController`，而产品 Dockerfile healthcheck 与 `scripts/verify_general_agent.py` 依赖它，故拆分为无条件装配的 `HealthController`，两个 profile 都可用。

## 修改内容

- 新增 `server/LegacyConfiguration.java`：`@Profile("legacy")`，承接原 `ServerConfiguration` 中 legacy Bean 组（`JsonRepositories`、`SupportedScenarioCatalog`、`ConversationService`、`RouteValidator`、`TaskRouter`、`QueryService`、`DiagnosisApplicationService`、`capabilityGateway`（standard，@Primary）、`ReasonerFactory`（含 `InitException`）、`DiagnosisRunService`、`ConversationFlowService`）。
- `ServerConfiguration` 只保留共享 `httpObjectMapper` 与全部 `@Profile("product")` Bean。
- `api/ConversationController.java`：加 `@Profile("legacy")`；健康端点迁出。
- 新增 `api/HealthController.java`：`/api/health` 无条件可用。
- 6 个 legacy HTTP 测试（`ServerHttpTest`、`DiagnosisHttpTest`、`QueryHttpTest`、`RouterUnavailableHttpTest`、`ServerAtomicityHttpTest`、`ServerCapacityHttpTest`）的 `@SpringBootTest` 增加 `spring.profiles.active=legacy`。
- `README.md`、`AGENTS.md`：Web 启动命令加 `--spring.profiles.active=legacy`；补充两个 profile 互斥、无 profile 时仅剩健康探针的说明。

## 验证

- `mvn -o test`：389 项，0 失败、0 错误、4 跳过（真实 PG 集成测试经临时容器 `127.0.0.1:54339` 执行，用后已清理）。
- legacy 模式实测（18080）：`/api/health` 200、POST `/api/sessions` 201、`/index.html` 200，产品接口不存在。
- product 模式实测（18081，临时新库）：Flyway 成功应用 17 个迁移、`/api/health` 200、POST `/api/conversations` 201，legacy 接口不存在。

## 后续（第二、三步）

- 冻结纪律：legacy 代码只修回归；共享内核不新增对 legacy 类型的依赖。
- 删除门槛（未满足前不动）：产品 Agent 端到端覆盖 A–E 场景且验收测试迁移到新链路、`verify_cli.py` 改投新链路或退役。
