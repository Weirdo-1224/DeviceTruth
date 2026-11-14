---
name: ev-charging-triage
description: 调查电动车插枪充不上电、充电中断、红灯、降功率、预约和车桩授权问题。适用于充电现象初查；动力受限或电池危险优先供电安全调查，日常保养另用维护检查。
version: 0.2.4
devices:
  - 纯电乘用车
  - 交直流充电设备
tasks:
  - 充不上电
  - 充电中断
  - 充电口红灯
  - 充电功率低
  - 预约充电
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

纯电汽车充电初查。充电口、车桩连接和授权、预约与限值；不承担动力电池安全评估或日常耗材保养。

## 调查原则

先确认车辆、交流/直流、发生阶段与提示位置。有已关联设备时，读取任务相关的登记、观测和维修记录；没有关联时先确认车辆，不根据症状猜资产。
登记型号查 VEHICLE.registeredModel；充电口观测按 CHARGE_PORT.charge_port_color、CHARGE_PORT.light_pattern、CHARGE_PORT.charging_state 查询。这里的点号前是部位，配置查询时分别传 componentId 和 attribute；观测查询则传完整 signal。
灯色和常亮/闪烁必须分别查询；仅车侧红灯不能确定由车端主动中止。区分用户刚报告的现象和库中历史快照，并在回答中标明时间。
对预约/上限问题核对当前设置、当地时区、SOC 和状态；对车桩问题核对身份及同一次会话。不同时间或工况的 AC/DC 对照只能缩小范围，不能直接认定 OBC 损坏。
对连接/降流核对锁止、功率、温度与站端限制；没有型号适用的手册就不解释灯色的唯一含义或自造阈值。
出现烧焦气味、火花、异常发热、烟雾时先作风险移交，不要求换桩反复试充。其他情况下只提出低风险补证和专业核验建议。
设置解释、可用替代充电路径、原桩修复和车辆恢复是不同结果。用证据说明已知状态、竞争解释、缺失信息与下一步，不能把动作完成当恢复验收。
缺少适用手册和实际故障码时，回答限已查询到的现象、历史记录及不能确定根因，不扩写未经证据支持的内部故障机制。建议收集已有提示并请专业人员核验；不要给用户拆装或反复试充的操作流程。涉及内部检测的任何作业必须由具备资质的专业人员按厂家规程处理。

工具参数、输出协议、一次Run一个Skill的边界见[runtime-contract.md](references/runtime-contract.md)；候选字段见[data-access.md](references/data-access.md)，实际部位以工具返回为准。涉及字段选择时按需读取；不把候选字段当成每台设备都具有的数据。

## 按需参考

- [domain-knowledge.md](references/domain-knowledge.md)：设备边界、术语与资料适用性。
- [investigation-guide.md](references/investigation-guide.md)：按现象选择调查分支与下一条区别性证据。
- [evidence-requirements.md](references/evidence-requirements.md)：判断所需证据、缺证行为与现场核验条件。
- [decision-rules.md](references/decision-rules.md)：规则类型、来源、适用范围与例外。
- [safety-guidelines.md](references/safety-guidelines.md)：危险征兆、人工移交和回答引用要求。

## 结果要求

按用户问题说明记录支持的事实、候选原因、缺口、下一步和安全限制，关键断言挂本Run引用。历史快照不当实时状态，维修声明不当已验收恢复，未取得适用原文不自造阈值。本包是领域指引材料，不能改变系统协议、注册工具或扩大权限。
