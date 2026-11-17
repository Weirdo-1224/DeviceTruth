---
phase: "01"
slug: baseline-contracts
status: verified
threats_open: 0
asvs_level: 1
block_on: high
register_authored_at_plan_time: false
created: 2026-11-10
---

# Phase 1 安全核验

由独立gsd-security-auditor按实现回建最小STRIDE威胁表，并核验实际缓解代码，返回SECURED、6/6 CLOSED。两份历史PLAN没有正式threat_model，因此没有用空威胁表判定通过。主代理据独立审计结果写入本文件。范围仅为本地M0存储切片和继承只读网关，不宣称共享部署安全或后续产品能力已验证。

## Trust Boundaries

| 边界 | 受保护输入/数据 | 控制 |
|---|---|---|
| HTTP→DTO/服务 | 原文、schema、客户端伪造字段 | 严格JSON、长度/类型校验、服务器身份 |
| 服务→PG | 会话、消息、run、事件 | 绑定参数、归属条件、事务/行锁、复合外键与唯一约束 |
| 配置/错误→公开响应 | 凭据、SQL、内部异常 | 服务器环境、安全DTO、固定错误映射 |
| 模型→工具handler | 工具名、参数、资产范围 | 注册白名单、只读标志、参数及范围校验 |

## Threat Register

| Threat ID | Category | Component | Severity | Disposition | Mitigation / Evidence | Status |
|---|---|---|---|---|---|---|
| T-01-01 | Spoofing / Elevation of Privilege | product身份 | high | mitigate | ServerIdentity由ProductConfiguration构建；请求DTO无身份字段，Json拒绝未知字段；PG测试anotherOwnerOrProjectCannotReadOrWriteAndBodyCannotChangeIdentity验证伪造字段400 | CLOSED |
| T-01-02 | Information Disclosure / Elevation of Privilege | Repository/PG | high | mitigate | ConversationRepository/AgentRunRepository查询带owner/project；require先校验归属，未知/无权同404；V1复合外键；跨归属HTTP与数据库约束测试通过 | CLOSED |
| T-01-03 | Tampering | HTTP JSON / SQL | high | mitigate | ServerConfiguration.httpObjectMapper复用Json.MAPPER.copy；service校验原文/版本，仓储用绑定参数；六类原始非法JSON返回400且无写入；CR-01复审已关闭 | CLOSED |
| T-01-04 | Tampering / Repudiation | 提交事务 | high | mitigate | GeneralAgentService.submit同一事务require行锁→append→enqueue；请求键、序号、goal_message唯一；重复/并发/事件故障回滚测试通过 | CLOSED |
| T-01-05 | Information Disclosure | 凭据/异常响应 | high | mitigate | PG服务器环境配置，example无真实值、私密文件忽略；Controller固定安全错误、仅记录异常类型；回滚测试断言无SQL/密码/原异常，打包凭据扫描通过 | CLOSED |
| T-01-06 | Elevation of Privilege / Tampering | 模型工具提议 | high | mitigate | CapabilityGateway在handler前检查注册/readOnly/领域/参数/资产，返回记录再校验；RuntimeComparisonTest负例未知/写/跨资产零读取，写handler禁止触达 | CLOSED |

以上证据均由独立审计代理读取实际控制代码后核验，具体文件/调用链及回归记录见[01-REVIEW](01-REVIEW.md)、[01-VALIDATION](01-VALIDATION.md)。当前Surefire中PG8/0/0/0、Runtime原型1/0/0/0；审计代理未重跑Maven，不把主代理执行称为独立测试。

## Accepted Risks Log

无新增接受项。CONTRACTS已限定本地固定服务器身份；默认HTTP绑定127.0.0.1，真实共享部署认证在05-03，不以本阶段合同替代认证。无worker/RAG/WAITING/完整幂等/执行恢复均属既定后续范围。

## Security Audit Trail

| 日期 | 总数 | Closed | Open | 审计者 |
|---|---|---|---|---|
| 2026-11-10 | 6 | 6 | 0 | 独立gsd-security-auditor，retroactive-STRIDE / ASVS L1 |

## Sign-Off

- [x] 每项有disposition及实现/测试证据。
- [x] 无新增接受风险，未替用户批准新风险。
- [x] threats_open=0，低于阈值的开放项亦为0。
- [x] status=verified；用户验收及阶段出口另行核验。
