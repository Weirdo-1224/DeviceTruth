# 测试地图

## 本轮事实

进程内切换 JDK21 后执行 mvn -o -q test，退出 0。Surefire 28 个 suite，145 tests、0 failures、0 errors、0 skipped。按包：core 27、adapter 13、app 2、application 68、server 35。

初次尝试 dot-source scripts/use-java21.ps1 被执行策略拦截，Maven 随后仍用 Java17，Surefire 报 class version 65/61，退出 1；直接设置当前进程 JAVA_HOME/PATH 后通过。没有改全局策略或业务源码。

## 现有覆盖

- Protocol/Gateway：严格 JSON、非法工具、跨资产、只读与输出归属。
- Evidence/Guard：时间边界、替代/冲突、缺证、引用不匹配、失效依赖。
- Runtime/Acceptance：单步提交、步数/重复错误、A/B/C/D 结果、E 伪造引用/工具、全量发现防提前结案。
- Extensibility：扰动 JSON、ID 改名、假工业域 IN 条件；不是完整工业产品故事。
- Adapter：本机 HTTP SDK 契约、模型配置、错误/重试；本机 HTTP 不是真实 LLM。
- Application/Server：资产 token 边界、四路由、受限查询、成对消息原子性、有界异步/拒绝、状态/归属、公开字段与事件脱敏。

## 缺失与后续

无 PG/Flyway/向量/解析/知识引用/标准 Skill/Vue/SSE/多轮恢复测试。新建测试名在 PLAN 中标为拟新增；不能说当前 145 项覆盖新版所有 E2E。

本轮不跑 package/打包 CLI/外部模型，不将旧 JAR 当当前验证。后续触碰持久化或 Runtime 要跑旧全套与打包 CLI，在线另报，不删旧拒绝用例迎合新 API；旧用例保留 legacy，新行为单建测试。

