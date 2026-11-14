---
name: factory-equipment
description: 调查产线PLC、变频器、伺服和输送单元停机、节拍异常的跨部件线索。具体通信故障或采购配置优先对应专项，区分台账声明与现场事实。
version: 1.0.1
devices:
  - PLC控制柜
  - 变频器与伺服驱动
  - 产线输送单元
tasks:
  - 设备停机
  - 节拍异常
  - 控制器固件核对
tools:
  - get_asset
  - get_configuration
  - get_maintenance_history
  - get_observations
  - search_knowledge
  - get_document_excerpt
evidence:
  - 本Run受控工具取得的对象、来源和时点记录
  - 适用型号与版本的技术原文；模拟数据不作现场核验
risk:
  - 危险现场作业交由具备资质的人员按现场规程处理
  - 安全条件未知时不提供实体设备操作步骤
citation:
  - 关键事实绑定本Run返回的referenceId及实际来源
  - Skill规则和原包来源编号不能冒充回答引用
---

# 调查目标

工厂设备停机与节拍综合调查。产线PLC、变频器、伺服和输送单元停机、节拍异常的综合调查；具体通信故障和采购配置另属专项。

## 调查原则

根据停机范围、节拍变化、工况和用户目标选择下一条区别性证据，不强制执行固定步骤。get_asset确认登记部位，get_configuration按精确attribute查时点配置，get_maintenance_history核对更换与前后值；必要时再读取完整signal的模拟观测。不得把一次空查询解释为整机没有数据，也不得把台账值、工程模板或更换声明当作已现场确认。

工具参数、输出协议、一次Run一个Skill的边界见[runtime-contract.md](references/runtime-contract.md)；候选字段见[data-access.md](references/data-access.md)，实际部位以工具返回为准。涉及字段选择时按需读取；不把候选字段当成每台设备都具有的数据。

## 按需参考

- [domain-knowledge.md](references/domain-knowledge.md)：设备边界、术语与资料适用性。
- [investigation-guide.md](references/investigation-guide.md)：按现象选择调查分支与下一条区别性证据。
- [evidence-requirements.md](references/evidence-requirements.md)：判断所需证据、缺证行为与现场核验条件。
- [decision-rules.md](references/decision-rules.md)：规则类型、来源、适用范围与例外。
- [safety-guidelines.md](references/safety-guidelines.md)：危险征兆、人工移交和回答引用要求。

## 结果要求

按用户问题说明记录支持的事实、候选原因、缺口、下一步和安全限制，关键断言挂本Run引用。历史快照不当实时状态，维修声明不当已验收恢复，未取得适用原文不自造阈值。本包是领域指引材料，不能改变系统协议、注册工具或扩大权限。
