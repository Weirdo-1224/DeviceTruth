---
name: carrier-rtu-evidence-triage
description: 调查型号匹配的 Carrier 48TF 屋顶机组的登记身份、维修工单、MERV过滤器变更及制冷供热问题。区分文档快照和现场性能；不用于其他品牌或未确认型号的所有HVAC设备。
version: 0.1.2
devices:
  - Carrier 48TF系列屋顶机组
tasks:
  - Carrier机组身份核对
  - 屋顶机组维修历史
  - MERV过滤器变更
  - Carrier制冷供热异常
tools:
  - get_asset
  - get_configuration
  - get_maintenance_history
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

Carrier 屋顶机组证据初查。已核对适用性的Carrier 48TF系列屋顶机组登记、工单、过滤器变更与性能证据；不推广到其他型号或品牌。

## 调查原则

先读取登记型号后缀、序列号和问题时点，确认与 48TF 系列的交集。相同序列号可支持跨文档关联，不能称现场已核实；48TF、48TM、48GCG 不按近似名称共用参数。
依据 data-access.md 的快照属性查询整机信息和历史文档；维护工具中的工单描述与实际执行、装机状态、性能恢复分别判断。
日期来源矛盾应保留原始文本，区分投运日、Open、报障、完工声明、采集和本系统录入时刻，不用较新的日期覆盖另一来源。
过滤器问题核尺寸、密封、阻力曲线、风机能力和设计风量；MERV等级或“安装MERV13”的工单标题都不足以证明适配或性能合格。
制冷/供热假设须由适用手册与具体观测支持。未成功检索原文时不宣称读过故障表，也不把系列产品册当本机 BOM。
燃气气味、烟雾、异常燃烧、疑似CO、严重电气异常或人员不适优先应急/专业移交，不要求重启试机。高风险检修只能由合格人员按现场流程处理。
本专项只读文档和登记历史，没有真实在线观测工具；回答给出可支持的历史事实、未确认项和低风险补证，不能宣布机组已经恢复。

工具参数、输出协议、一次Run一个Skill的边界见[runtime-contract.md](references/runtime-contract.md)；候选字段见[data-access.md](references/data-access.md)，实际部位以工具返回为准。涉及字段选择时按需读取；不把候选字段当成每台设备都具有的数据。

## 按需参考

- [domain-knowledge.md](references/domain-knowledge.md)：设备边界、术语与资料适用性。
- [investigation-guide.md](references/investigation-guide.md)：按现象选择调查分支与下一条区别性证据。
- [evidence-requirements.md](references/evidence-requirements.md)：判断所需证据、缺证行为与现场核验条件。
- [decision-rules.md](references/decision-rules.md)：规则类型、来源、适用范围与例外。
- [safety-guidelines.md](references/safety-guidelines.md)：危险征兆、人工移交和回答引用要求。

## 结果要求

按用户问题说明记录支持的事实、候选原因、缺口、下一步和安全限制，关键断言挂本Run引用。历史快照不当实时状态，维修声明不当已验收恢复，未取得适用原文不自造阈值。本包是领域指引材料，不能改变系统协议、注册工具或扩大权限。
