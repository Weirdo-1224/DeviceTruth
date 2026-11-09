# 目录地图

| 路径 | 现有职责 |
|---|---|
| src/main/java/io/devicetruth/app | CLI 参数、入口与手工装配 |
| src/main/java/io/devicetruth/server | 独立 HTTP Main、Bean 装配、在线 Router 连接 |
| src/main/java/io/devicetruth/api | Controller / DTO / 统一异常映射 |
| src/main/java/io/devicetruth/application | 内存会话、路由、固定查询、诊断映射、异步执行、安全视图 |
| src/main/java/io/devicetruth/core | capability/contract/decision/evidence/reasoning/runtime/state |
| src/main/java/io/devicetruth/adapter | JSON 仓储、模型适配与配置、报告 |
| src/main/resources | application.properties、logback.xml、原生 static/index.html |
| src/test/java/io/devicetruth | app/adapter/application/core/server，28 个 Java 测试类 |
| datasets | scenarios.json、fixtures/a–d，每份为同构模拟证据记录 |
| skills/automotive-demo | 仅 rules.json，不是 SKILL.md |
| scripts | Java21 辅助、打包 CLI/在线验证 |
| docs | V0.1、V0.2、V1.0 与 SDK 调研归档 |
| .planning | 本轮新增的当前规划与持久化交接，不能当业务实现 |
| target / outputs / .m2 / .tools | 构建、运行证据、依赖与 JDK；不提交产物或凭据 |

当前 52 个主 Java 文件；Maven 未拆模块。没有 frontend、db/migration、Compose、Skill loader、knowledge 模块；后续目录名为规划建议，必须与实际实现同步。

