---
status: complete
phase: 04-skills-dual-domain
source: [04-01-SUMMARY.md, 04-02-SUMMARY.md]
started: 2026-11-13
updated: 2026-11-13
---

## Tests

### 1. 领域指引、许可和恢复
expected: 两域发现并启用正确版本。工具执行遵守服务端许可。撤销与内容变化在恢复和提交时拒绝。
result: pass
source: automated
verification: SkillRuntimeIntegrationTest 3/3；Skill 文件读取 25/26（1 项 Windows 符号链接跳过）。

### 2. 产品双域与观测
expected: 汽车同 run 补答并读回章节，工厂区分来源、时点与现场核验。观测受归属、时点和质量约束，结果可引用。
result: pass
source: automated
verification: 打包产品 HTTP 双域脚本 17/17；真实 PostgreSQL DualDomainAgentTest 4/4。

### 3. 真实供应商双域对照
expected: DeepSeek 模型与 DashScope 向量服务实跑双域，检查实际 Skill、引用、时点、风险和多轮结果。
result: pass
source: automated
verification: 用户授权后运行 DeepSeek + DashScope，汽车 9/9、工厂 8/8。原始结果保留，工厂最后一项用修正措辞断言复核原答案，其他断言均为在线批次原始结果。详见 04-ACCEPTANCE。
