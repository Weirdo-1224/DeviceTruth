# 集成地图

- [AgentScopeStep](../../src/main/java/io/devicetruth/adapter/AgentScopeStep.java)：DashScope qwen-plus 与 OpenAI-compatible；JSON object、单补全、45s、SDK maxAttempts=1、空工具列表。StructuredReasoner 仅对非法协议纠正一次。
- [ModelConfiguration](../../src/main/java/io/devicetruth/adapter/ModelConfiguration.java)：当前私有 UTF-8 properties 非空值优先、环境补缺、首次推理懒加载。本轮未打开私有配置值。
- [OnlineRouterCompletion](../../src/main/java/io/devicetruth/server/OnlineRouterCompletion.java) / LlmTaskRouter：独立模型分类，之后仍由 RouteValidator 白名单复核。默认 router offline、reasoner openai，不能说默认全程离线。
- 数据集经 JsonRepositories 从 classpath 加载 A–D；没有外部设备、数据库或知识检索集成。
- RunReportWriter 将报告写 outputs/runs，HTTP 不公开 reportPath；无对象存储。
- HTTP 为 Spring MVC；/api/health 自有端点；Actuator 依赖确实存在，本轮测试启动日志可见 /actuator 基路径，具体生产暴露策略需后续确认。
- scripts/verify_cli.py 本机隔离缺配置与模拟模型，verify_online.py 则真实调用选定供应商；二者验收等级不同。
- 无认证提供商、消息代理、PG、pgvector、SSE、标准 Skill、外部搜索服务集成；不因 docs/research 存 SDK 源码就认为已接入新运行能力。

