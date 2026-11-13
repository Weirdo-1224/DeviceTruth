---
phase: "03"
slug: "durable-investigation"
status: secured
threats_open: 0
asvs_level: 1
created: "2026-11-13"
---

# Phase 03 — Security

> Per-phase security contract: threat register, accepted risks, and audit trail.

---

## Trust Boundaries

| Boundary | Description | Data Crossing |
|----------|-------------|---------------|
| HTTP API | 会话/消息/补答/run/资产/知识接口，服务器固定身份 | 用户消息、资产线索、引用、审计事件 |
| 工具网关 | 模型只能提议动作，工具只读 + 授权范围 + 参数白名单 | 工具参数、引用登记、工具结果白名单 |
| PG 持久化 | 多版本迁移，owner/project 复合隔离 | 会话、run、资产台账、事实、引用、审计事件 |
| Vue 前端 | 历史会话/恢复轮询/设备选择 | 消息、答案、引用展示 |
| 文件/附件 | 受控原文件存储与就绪附件校验 | Markdown 原文件、附件上下文 |
| Skill 内容 | 不可信材料（04-01 才接入 Runtime） | SKILL.md 正文与 references |

---

## Threat Register

| Threat ID | Category | Component | Severity | Disposition | Mitigation | Status |
|-----------|----------|-----------|----------|-------------|------------|--------|
| T03-01 | Spoofing/InfoDisclosure | HTTP API | high | mitigate | 服务器固定身份（`ProductConfiguration.java:51-55`），请求体不得提供 owner/project | closed |
| T03-02 | InfoDisclosure（跨租户枚举） | HTTP API / PG | high | mitigate | 每查询带 owner/project 条件，跨归属统一 404（`AgentRunRepository.java:101-147`、`AssetRegistryRepository.java:50-56`、`ResourceAuthorizationTest` 8 项） | closed |
| T03-03 | Tampering（重复提交/重放） | HTTP API | high | mitigate | 持久幂等键 + 会话行锁 + 同事务建消息与 run（`GeneralAgentService.java:201-210,229-237`；`AgentLifecycleTest`） | closed |
| T03-04 | Elevation（模型越权调工具/扩参） | 工具网关 | high | mitigate | 网关强制只读 + 授权 + 参数白名单，工具侧二次强类型校验（`CapabilityGateway.java:57-83`、`AssetTools.java:219-233,412-424`） | closed |
| T03-05 | Tampering（口述升级为"现场已核实"） | PG / 工具 | high | mitigate | DB CHECK 强制 FIELD_VERIFIED ⇒ FIELD_CONFIRMATION + observed_at（`V8:52,83`、`AssetSnapshotImporter.java:75-78`） | closed |
| T03-06 | Tampering（跨租户资产串接） | PG | medium | mitigate | 复合外键 + 快照一致性校验 + 插入式幂等导入（`V8:27,54-56`、`AssetRegistryRepository.java:119-172`） | closed |
| T03-07 | SQL Injection | PG | high | mitigate | 全部新查询参数化；动态 SQL 仅追加固定 `AND col=?`（`AssetRegistryRepository`、`AgentRunRepository`、`AssetFactReferenceWriter`） | closed |
| T03-08 | Elevation/DoS（双 worker 幽灵 RUNNING） | 运行时/PG | high | mitigate | 执行租约 + version CAS + attempts 有界，恢复只收过期租约（`AgentRunRepository.java:155-215`、`V12`、`RunRecoveryService.java:40-93`） | closed |
| T03-09 | Tampering（迟到响应覆盖终态） | 运行时 | high | mitigate | 终态一律 CAS `WHERE status='RUNNING'`；persist 行锁内重查（`GeneralAgentService.java:398-448`、`AgentRunRepository.java:284-325`） | closed |
| T03-10 | InfoDisclosure（密钥/Prompt/模型原文入审计与日志） | 审计/日志 | high | mitigate | RUN_MODEL/TOOL_RESULT/REASON_INPUT 白名单载荷，日志只打类型名（`GeneralAgentService.java:450-461`、`Reasoner.java:16`） | closed |
| T03-11 | InfoDisclosure/Elevation（报告工具越界聚合） | 工具网关 | high | mitigate | 零参数只读 + 本 run 白名单视图 + 跨身份统一 REPORT_UNAVAILABLE（`AgentReportTools.java:46-96,133-143`） | closed |
| T03-12 | Elevation（伪造附件/引用跨租户） | 附件/引用导入 | high | mitigate | readyAttachments 校验 + importReference owner/project 等值连接（`GeneralAgentService.java:243-263`、`AgentRunRepository.java:409-430`） | closed |
| T03-13 | DoS（幽灵任务/无限重试/无限上下文） | 运行时 | medium | mitigate | 有界线程池/队列拒绝即 FAILED；恢复对账 TOTAL_TIMEOUT；重试上限（`GeneralAgentService.java:295-321`、`TaskOrchestrator.java:309-339`） | closed |
| T03-14 | Tampering（夹具/未授权数据写库） | 持久化/启动 | medium | mitigate | Seeder 默认关闭、仅运维开启、目标取服务器身份、插入式幂等（`ProductConfiguration.java:110-123`、`AssetFixtureSeeder.java:35-53`） | closed |

*Status: open · closed — only open threats at or above workflow.security_block_on (high) count toward threats_open*
*本寄存器为 retroactive-STRIDE（三个 PLAN 均无 `<threat_model>` 块，由 gsd-security-auditor 从实现重建并逐条验证，证据 file:line 见审计返回）*

---

## Accepted Risks Log

| Risk ID | Threat Ref | Rationale | Accepted By | Date |
|---------|------------|-----------|-------------|------|
| AR-03-01 | T03-01/T03-02 | 固定本地身份为单用户本地模式假设，真实多人认证明确推迟到 05-03 接入后验收（03-01-PLAN 验收条件原文声明）；共享部署前该假设失效须重新审计 | 计划级声明（03-01-PLAN） | 2026-11-13 |

*非本阶段实现缺口，归类为计划内声明的可接受风险。*

---

## Unregistered Flags（非阻塞建议）

| Flag | 说明 | Severity |
|------|------|----------|
| U1 | 取消接口对不存在/跨身份 run 返回 409 RUN_ALREADY_TERMINAL，与终态语义混用；建议后续统一为 404 | low |

---

## Security Audit Trail

| Audit Date | Threats Total | Closed | Open | Run By |
|------------|---------------|--------|------|--------|
| 2026-11-13 | 14 | 14 | 0 | gsd-security-auditor（retroactive-STRIDE，ASVS L1） |
