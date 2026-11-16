---
name: industrial-controller-fault-triage
description: 调查 PLC 停机、STOP/ERROR 灯、远程 I/O 无数据、更换后通信异常和诊断调用失败。具体指令解释限核对适用性的 Siemens S7-1200；纯粹型号、固件及采购核对另用配置调查。
version: 0.4.3
devices:
  - PLC与工业控制柜
  - 远程I/O与PROFINET设备
tasks:
  - PLC停机
  - STOP灯
  - ERROR灯
  - 远程I/O没数据
  - 更换后通信异常
  - 诊断返回码异常
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

工业 PLC 故障初查。PLC停机、指示灯、远程I/O与PROFINET通信、诊断结果；具体指令条件必须核对系列和固件。

## 调查原则

先核资产、厂商、订货号、固件、故障时点与原始现象。读取相关配置、维护和精确观测；不同厂商、系列与固件不能套用同一灯义或指令条件。
字段映射先读 data-access.md。工程名称查实际通信部位的 expected_profinet_name；常见 PN-01 观测为 PN-01.online_profinet_name、PN-01.io_data_exchange、PN-01.link_up、PN-01.hmi_value_updates。观测必须使用部位加字段的完整 signal，NO_MATCHING_DATA 只说明本次条件未匹配，不能扩大为整台设备没有观测。
更换后无 I/O：比较工程期望名、已绑定身份的在线名、链路和数据交换。站点 RUN 或能扫到不代表过程数据正常；名称相同或修正后仍异常时改变调查方向。
STOP：区分计划停止、配置控制及启动相关诊断，不把所有 STOP 当硬件损坏。ERROR/供电疑点核灯位、颜色、闪烁和具体版本。
诊断调用错误与设备故障分开；只有获得指令、目标类型、模式和有效输出时才解释返回码。部分覆盖外的设备仍未知，不从工具失败推断模块损坏。
时钟漂移、窗口缺失、无权限和空记录分别处理；原始设备时间未由工具返回时不声称已校时或重建完整时间线。
配置、根因、执行与恢复分开。复测失败继续调查；恢复仅限有独立证据的窗口。危险机械、电气或安全 PLC 场景交专业人员，不提供停机、命名、复位、下载或旁路操作步骤。

工具参数、输出协议、一次Run一个Skill的边界见[runtime-contract.md](references/runtime-contract.md)；候选字段见[data-access.md](references/data-access.md)，实际部位以工具返回为准。涉及字段选择时按需读取；不把候选字段当成每台设备都具有的数据。

## 按需参考

- [domain-knowledge.md](references/domain-knowledge.md)：设备边界、术语与资料适用性。
- [investigation-guide.md](references/investigation-guide.md)：按现象选择调查分支与下一条区别性证据。
- [evidence-requirements.md](references/evidence-requirements.md)：判断所需证据、缺证行为与现场核验条件。
- [decision-rules.md](references/decision-rules.md)：规则类型、来源、适用范围与例外。
- [safety-guidelines.md](references/safety-guidelines.md)：危险征兆、人工移交和回答引用要求。

## 结果要求

按用户问题说明记录支持的事实、候选原因、缺口、下一步和安全限制，关键断言挂本Run引用。历史快照不当实时状态，维修声明不当已验收恢复，未取得适用原文不自造阈值。本包是领域指引材料，不能改变系统协议、注册工具或扩大权限。
