---
name: ev-maintenance-inspection
description: 调查电动车胎压或胎压灯、轮胎磨损、制动保养、空调滤芯、雨刮和既往维护。适用于日常检查与服务记录核对；充电异常和动力电池风险分别使用对应专项。
version: 0.2.2
devices:
  - 纯电乘用车轮胎与制动系统
  - 座舱滤芯与雨刮
tasks:
  - 胎压灯
  - 轮胎磨损
  - 制动液保养
  - 空调滤芯
  - 雨刮刮不干净
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

纯电汽车维护与检查。轮胎与胎压、制动、滤芯、雨刮的维护证据核对；充电和动力电池风险另属专项。

## 调查原则

先确认车辆、年款、市场、相关部位、问题时间和使用条件。维护到期依据实际日期/里程与适用文件，年款不能代替投运日；没有维修记录不等于从未维护。
胎压核轮位、冷胎条件、车辆标签目标、量具与单位；旧显示不能替代当前测量。TPMS 闪烁不能唯一定位传感器，压力安全未确认前不安排行车刷新。
轮胎核四轮数值、规格与换位记录，换位不会让胎纹增长，也不等于磨损根因已解决。
制动风险优先处理；到期通常说明需要检查，不能直接认定液体失效，未取得车型阈值不自造通用更换线。
滤芯/HVAC 比较同环境和风挡下的测量，主观异味改善不代表制冷系统全部恢复。雨刮核耗材、玻璃、机构与实际刮拭结果，不只凭使用年限判断电机。
分别说明建议、批准、执行和验证；仅有主观反馈时保留未验证。遇制动、轮胎损伤或可见度风险转专业处理，不安排危险道路试验。

工具参数、输出协议、一次Run一个Skill的边界见[runtime-contract.md](references/runtime-contract.md)；候选字段见[data-access.md](references/data-access.md)，实际部位以工具返回为准。涉及字段选择时按需读取；不把候选字段当成每台设备都具有的数据。

## 按需参考

- [domain-knowledge.md](references/domain-knowledge.md)：设备边界、术语与资料适用性。
- [investigation-guide.md](references/investigation-guide.md)：按现象选择调查分支与下一条区别性证据。
- [evidence-requirements.md](references/evidence-requirements.md)：判断所需证据、缺证行为与现场核验条件。
- [decision-rules.md](references/decision-rules.md)：规则类型、来源、适用范围与例外。
- [safety-guidelines.md](references/safety-guidelines.md)：危险征兆、人工移交和回答引用要求。

## 结果要求

按用户问题说明记录支持的事实、候选原因、缺口、下一步和安全限制，关键断言挂本Run引用。历史快照不当实时状态，维修声明不当已验收恢复，未取得适用原文不自造阈值。本包是领域指引材料，不能改变系统协议、注册工具或扩大权限。
