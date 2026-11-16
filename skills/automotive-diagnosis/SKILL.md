---
name: automotive-diagnosis
description: 调查汽车充电与车载通信的交叉线索、故障码和维修历史。专门的充电、日常维护或电池风险问题优先对应专项；本包不给出实车确诊。
version: 1.0.1
devices:
  - 纯电乘用车
  - 交直流充电设备
  - 车载网关与控制器
tasks:
  - 充电中断
  - 通信超时
  - 故障码解读
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

汽车充电与车载通信综合调查。汽车充电与车载通信的综合线索、故障码和历史核对；已有专用充电、维护或电池风险任务优先对应专项。

## 调查原则

按当前用户目标选择充电或通信分支，核对同一车辆、部位、版本和时点。先取得有关设备记录，再按需检索适用说明；不要求每次走完全部工具。已有资料能查到的信息由助手读取，不转成用户必须输入的内部字段。报码、历史维修和配置差异只提供候选解释；需要现场核验时说明对象、缺口与专业责任。

工具参数、输出协议、一次Run一个Skill的边界见[runtime-contract.md](references/runtime-contract.md)；候选字段见[data-access.md](references/data-access.md)，实际部位以工具返回为准。涉及字段选择时按需读取；不把候选字段当成每台设备都具有的数据。

## 按需参考

- [domain-knowledge.md](references/domain-knowledge.md)：设备边界、术语与资料适用性。
- [investigation-guide.md](references/investigation-guide.md)：按现象选择调查分支与下一条区别性证据。
- [evidence-requirements.md](references/evidence-requirements.md)：判断所需证据、缺证行为与现场核验条件。
- [decision-rules.md](references/decision-rules.md)：规则类型、来源、适用范围与例外。
- [safety-guidelines.md](references/safety-guidelines.md)：危险征兆、人工移交和回答引用要求。

## 结果要求

按用户问题说明记录支持的事实、候选原因、缺口、下一步和安全限制，关键断言挂本Run引用。历史快照不当实时状态，维修声明不当已验收恢复，未取得适用原文不自造阈值。本包是领域指引材料，不能改变系统协议、注册工具或扩大权限。
