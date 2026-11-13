---
phase: 04-skills-dual-domain
status: passed
score: 4/4
verified: 2026-11-13
covered_files: [".planning/phases/04-skills-dual-domain/04-01-PLAN.md",".planning/phases/04-skills-dual-domain/04-01-SUMMARY.md",".planning/phases/04-skills-dual-domain/04-02-PLAN.md",".planning/phases/04-skills-dual-domain/04-02-SUMMARY.md","frontend/src/App.vue","frontend/src/api.ts","scripts/test_verify_general_agent.py","scripts/verify_browser_recovery.py","scripts/verify_general_agent.py","skills/automotive-diagnosis/SKILL.md","skills/factory-equipment/SKILL.md","src/main/java/io/devicetruth/adapter/AgentModelConnections.java","src/main/java/io/devicetruth/adapter/AgentScopeStep.java","src/main/java/io/devicetruth/adapter/StructuredAgentReasoner.java","src/main/java/io/devicetruth/adapter/persistence/AgentRunRepository.java","src/main/java/io/devicetruth/adapter/persistence/SkillInstallationRepository.java","src/main/java/io/devicetruth/api/AgentConversationController.java","src/main/java/io/devicetruth/application/agent/AssetSnapshotImporter.java","src/main/java/io/devicetruth/application/agent/AssetTools.java","src/main/java/io/devicetruth/application/agent/GeneralAgentService.java","src/main/java/io/devicetruth/application/agent/ObservationTools.java","src/main/java/io/devicetruth/core/capability/AgentToolTypes.java","src/main/java/io/devicetruth/core/capability/CapabilityGateway.java","src/main/java/io/devicetruth/core/reasoning/GeneralReasoner.java","src/main/java/io/devicetruth/core/reasoning/Reasoner.java","src/main/java/io/devicetruth/core/reasoning/ScriptedAgentReasoner.java","src/main/java/io/devicetruth/core/runtime/TaskOrchestrator.java","src/main/java/io/devicetruth/server/ProductConfiguration.java","src/main/java/io/devicetruth/server/ServerConfiguration.java","src/main/java/io/devicetruth/skill/SkillContextLoader.java","src/main/java/io/devicetruth/skill/SkillRuntime.java","src/main/resources/application-product.properties","src/main/resources/db/migration/V14__skill_installation.sql","src/main/resources/db/migration/V15__asset_observation.sql","src/test/java/io/devicetruth/adapter/AgentScopeStepTest.java","src/test/java/io/devicetruth/application/AgentLifecycleTest.java","src/test/java/io/devicetruth/application/AgentReportToolTest.java","src/test/java/io/devicetruth/application/DualDomainAgentTest.java","src/test/java/io/devicetruth/application/GeneralAgentFlowTest.java","src/test/java/io/devicetruth/application/SkillRuntimeIntegrationTest.java","src/test/java/io/devicetruth/knowledge/KnowledgeUpgradeMigrationTest.java","src/test/java/io/devicetruth/server/GeneralAgentApiTest.java","src/test/java/io/devicetruth/server/PersistentConversationTest.java","src/test/java/io/devicetruth/skill/SkillRegistryTest.java"]
covered_digest: "v4:sha256:cc0deddb19eadac8e0ea9752eddc358bdfae2207956c80558f4fece5eb37424d"
---

| 目标 | 结果 |
| --- | --- |
| 两域指引发现、激活、版本及工具绑定 | 本地通过。真实 PostgreSQL 检查选中版本和恢复权限。 |
| 不可信资料无法扩大服务器工具权限 | 本地通过。Skill、网关和产品只读负例已运行。 |
| 同一产品运行链路完成双域样例 | 离线通过。实际 HTTP 与 PostgreSQL，汽车 9 项、工厂 8 项。 |
| 真实模型和向量服务双域对照 | DeepSeek + DashScope 同批在线结果复核 17/17。工厂一项判定用修正后的规则在原始答案上复核。 |

符号链接负例因 Windows 权限跳过，保留为未验证。证据及所有在线失败记录见 [04-ACCEPTANCE](04-ACCEPTANCE.md)。
