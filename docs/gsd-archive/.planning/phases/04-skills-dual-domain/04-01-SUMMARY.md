---
phase: 04-skills-dual-domain
plan: "01"
status: complete
completed: 2026-11-13
requirements-completed: [SKILL-01, SKILL-02, SKILL-04]
coverage:
  - id: P4-SKILL-ACTIVATION
    description: "两个领域的 Skill 发现、按需读取与产品 Agent 激活"
    requirement: SKILL-01
    verification:
      - kind: integration
        ref: "SkillRegistryTest、SkillContextLoaderTest、SkillRuntimeIntegrationTest；2026-11-13 PostgreSQL package 批次"
        status: pass
    human_judgment: false
  - id: P4-SKILL-CHECKPOINT
    description: "选择时保存版本和适用范围，重启后按原 run 恢复并重验许可"
    requirement: SKILL-02
    verification:
      - kind: integration
        ref: "SkillRuntimeIntegrationTest（3/3），包含恢复、撤销和内容变化检查"
        status: pass
    human_judgment: false
  - id: P4-SKILL-AUTHORIZATION
    description: "工具目录与实际执行遵循相同服务器授权；领域材料不增加权限"
    requirement: SKILL-04
    verification:
      - kind: integration
        ref: "SkillRuntimeIntegrationTest、ResourceAuthorizationTest、DualDomainAgentTest"
        status: pass
    human_judgment: false
---

V14 增加安装许可、run 所选 Skill 和已读取参考资料摘要。产品在原 `TaskOrchestrator` 中处理 `activate_skill` 与 `read_skill_reference`，模型只收到当前可用元数据、已激活指引和按需读取的单篇资料。产品在生成目录、执行动作和发布结果前核对许可。HTTP 审计仅返回选中 Skill 标识、版本和适用范围。

本轮以真实 PostgreSQL 执行 `mvn -B -o package`，Skill 集成 3/3。加载器测试有一项符号链接场景因 Windows 权限跳过。真实供应商双域验证由 04-02 继续。
