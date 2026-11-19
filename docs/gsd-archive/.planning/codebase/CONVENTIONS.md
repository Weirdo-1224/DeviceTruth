# 规范与协作地图

- UTF-8、四空格 Java 缩进；部分历史 Core 使用紧凑写法，新增时沿周边风格，不全仓格式化。
- PascalCase 类型、camelCase 方法变量、UPPER_SNAKE_CASE 常量；多用 record、sealed interface、不可变集合。
- Checks 做显式契约验证；Json.MAPPER 严格反序列化。ActionProposal sealed 多态协议，非法字段/类型不能松绑来迁就模型。
- Core 模型仅提议，工具只读、来源带 SIMULATED；事实与原始证据分存，旧证据不可改写；技术失败与诊断结果区分。
- 服务端负责模型模式，客户端不可提交 API Key/provider/model 等；公开 DTO 不输出内部报告/Prompt。
- JUnit5/AssertJ，测试名表达行为；改协议/证据/Guard 补有意义回归。
- 需求变更以新版设计和用户最新指令为准；旧 FROZEN 适用于旧交付，V1.0 变更在新规划登记。
- 使用短、范围明确的中文 commit 信息；本轮没有 commit；生成报告、缓存与真实凭据不提交。
- 开发后必须用通俗中文解释输入如何经过关键方法形成输出，报告实际命令、结果和未验证项。
- 发现 AGENTS 历史 Git 摘要、密钥环境变量规则与当前实现漂移，记录在 INGEST-CONFLICTS，不据此擅改业务行为。

