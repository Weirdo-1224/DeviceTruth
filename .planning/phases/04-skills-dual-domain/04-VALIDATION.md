---
phase: 04-skills-dual-domain
status: validated
---

2026-11-13 在 PostgreSQL 16.15 独立测试库应用 V1–V15，干净构建和全量 Maven 343 项，342 通过、1 跳过。SkillRuntimeIntegrationTest 3/3，DualDomainAgentTest 4/4，打包双域 HTTP 17/17，前端 18/18 及生产构建通过，旧 CLI 通过。

观察到有模拟记录时引用写入未执行成功，原因是插入 SQL 缺少 `citation_excerpt` 对应的占位符。修复后用真实 PostgreSQL 验证引用登记、回读、质量过滤和未来时点排除。符号链接场景受 Windows 权限限制跳过。真实 DeepSeek 与 DashScope 的同批双域结果复核 17/17；Python 断言回归 15/15。

完整批次与证据见 [04-ACCEPTANCE](04-ACCEPTANCE.md)。
