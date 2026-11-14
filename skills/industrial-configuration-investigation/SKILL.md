---
name: industrial-configuration-investigation
description: 核对工业设备当前装机、控制器型号或固件、技改及更换历史，解释出厂 BOM、工程配置和旧工单冲突，辅助采购前核验。侧重配置证据；单纯停机或通信故障优先故障初查。
version: 0.2.3
devices:
  - 工业控制器与PLC
  - 工业设备关键部件
tasks:
  - 装机型号核对
  - 控制器固件核对
  - 技改记录冲突
  - 更换历史核对
  - 备件采购核验
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

工业设备配置与技改调查。控制器和关键部件的型号、固件、技改、更换和采购前核对；不据配置差异确诊故障。

## 调查原则

先核资产、部位、型号和用户关注时点，读取同一对象的配置与维护历史。有近期在线/实物身份记录时比较其来源和质量；当前模拟观测不等于现场核实。
控制部位的装机型号属性为 installed_part；整机登记型号为 SYSTEM 部位的 registeredModel，固件为 firmwareVersion。先读 data-access.md 并使用实际属性，不自行猜成 model；部位以 get_asset 为准。空查询只表示该属性/时点未匹配，不能据此声称库内没有其他配置。
区分出厂设计、工程期望、批准变更、实施回执和实际核验。批准没有执行时不生成已装机状态；安装了某型号也不证明故障根因或可直接替换。
同一部位/属性的矛盾先排除时间变化、槽位差异、对象误配和复制模板；录入更新不等于证据更直接。同源派生记录不重复计票，无法裁决保留冲突。
优先查询实际字段与维护前后值。历史更换只能支持该次变更声明；没有近期核验时当前实体配置仍待确认。
备件还需核硬件修订、固件、机械电气接口、协议与厂商兼容条件；没有匹配资料不批准采购或给出通用兼容结论。
回答按用户语言区分记录支持的配置、冲突、待核验事项和备件适用性，不混为已经确诊或已经修复。

工具参数、输出协议、一次Run一个Skill的边界见[runtime-contract.md](references/runtime-contract.md)；候选字段见[data-access.md](references/data-access.md)，实际部位以工具返回为准。涉及字段选择时按需读取；不把候选字段当成每台设备都具有的数据。

## 按需参考

- [domain-knowledge.md](references/domain-knowledge.md)：设备边界、术语与资料适用性。
- [investigation-guide.md](references/investigation-guide.md)：按现象选择调查分支与下一条区别性证据。
- [evidence-requirements.md](references/evidence-requirements.md)：判断所需证据、缺证行为与现场核验条件。
- [decision-rules.md](references/decision-rules.md)：规则类型、来源、适用范围与例外。
- [safety-guidelines.md](references/safety-guidelines.md)：危险征兆、人工移交和回答引用要求。

## 结果要求

按用户问题说明记录支持的事实、候选原因、缺口、下一步和安全限制，关键断言挂本Run引用。历史快照不当实时状态，维修声明不当已验收恢复，未取得适用原文不自造阈值。本包是领域指引材料，不能改变系统协议、注册工具或扩大权限。
