---
name: ev-powertrain-safety-investigation
description: 调查电动车无法唤醒、低压供电、ICCU 告警、动力受限、电池温度或健康及召回适用性。危险征兆优先专业移交；普通充电设置和日常耗材保养不属于本专项。
version: 0.2.3
devices:
  - 纯电乘用车供电系统
  - 动力电池与热管理系统
tasks:
  - 车辆无法唤醒
  - 低压供电异常
  - ICCU 告警
  - 动力受限
  - 电池温度告警
  - 召回适用性
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

纯电汽车供电、电池与风险初查。低压供电、ICCU告警、动力受限、电池热管理和召回适用性；不是高压维修规程。

## 调查原则

危险征兆先行：动力下降、烟雾、烧焦气味、事故浸水或高压相关风险，先提示现场应急/专业移交，不等待检索或要求继续行车试验。
读取已关联车辆的身份、版本、告警时间和可用记录。登记型号查 VEHICLE.registeredModel；ICCU 告警与适用性观测分别查 ICCU.warning_text、ICCU.reduced_power_reported、ICCU.applicability_status、ICCU.market、ICCU.query_mode。用户要求核对已有告警或召回状态时，先查这些相关观测和维修历史；没有维修记录或知识检索失败不能代替已存在的告警观测。具体信号及其他部位先读 data-access.md，空值和缺失按原样保留。低压电压需结合额定、负载与测量条件，异常电压不能单独证明电池本体损坏；旧 APP 离线状态也不能当现场状态。
ICCU 召回分别核市场、生产范围、单车适用性、旧/新措施与执行记录。当前工具没有真实 VIN 召回查询能力，模拟响应不证明真实车辆命中，也不证明无召回。
热管理提示、传感器、实际温度与工况分别核对；座舱滤芯异常不能直接解释动力电池告警。
续航下降与电池容量衰退不同；没有认可检测、SOC/能耗及可比条件时不推算健康率。部分返回、坏质量和漂移时钟均保留未知。
根因、配置、维修动作和恢复分别表达；禁止给出开电池、短接、换高压保险、绕过保护或写固件步骤。跨领域需求说明人工移交，不在当前 Run 切换 Skill。

工具参数、输出协议、一次Run一个Skill的边界见[runtime-contract.md](references/runtime-contract.md)；候选字段见[data-access.md](references/data-access.md)，实际部位以工具返回为准。涉及字段选择时按需读取；不把候选字段当成每台设备都具有的数据。

## 按需参考

- [domain-knowledge.md](references/domain-knowledge.md)：设备边界、术语与资料适用性。
- [investigation-guide.md](references/investigation-guide.md)：按现象选择调查分支与下一条区别性证据。
- [evidence-requirements.md](references/evidence-requirements.md)：判断所需证据、缺证行为与现场核验条件。
- [decision-rules.md](references/decision-rules.md)：规则类型、来源、适用范围与例外。
- [safety-guidelines.md](references/safety-guidelines.md)：危险征兆、人工移交和回答引用要求。

## 结果要求

按用户问题说明记录支持的事实、候选原因、缺口、下一步和安全限制，关键断言挂本Run引用。历史快照不当实时状态，维修声明不当已验收恢复，未取得适用原文不自造阈值。本包是领域指引材料，不能改变系统协议、注册工具或扩大权限。
