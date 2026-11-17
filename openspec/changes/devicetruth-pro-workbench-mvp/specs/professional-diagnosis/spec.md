# Spec Delta

## Purpose

使专业检修人员在统一工作台阅读 Agent 的结构化诊断、选择判断并核对真实引用。工业与汽车沿用同一套展示规则，清楚区分候选原因、资料支持、现场陈述和模拟资料，防止界面夸大结论可信度。

## ADDED Requirements

### Requirement: 结构化诊断保持原有语义

专业工作台 SHALL 展示摘要、判断、建议、不确定点、限制、风险与数据性质。只有 category 为 POSSIBLE_CAUSES 时才将 claims 列为候选原因；其他类别显示资料断言、结论或人工核验项。certainty 原样表达推测、资料支持或服务端已核验语义，建议不代表已执行检查。未输出候选原因时 SHALL 安全展示现有结果，不伪造候选，并在允许新消息的状态保留普通文字继续调查入口。

#### Scenario: 候选原因中的资料支持

- **WHEN** 答案类别为 POSSIBLE_CAUSES 且某条 claim 的 certainty 为 SUPPORTED
- **THEN** 该条位于候选原因列表并标示“资料支持”，不显示为当前设备已确诊

#### Scenario: 一般知识不冒充故障原因

- **WHEN** 答案类别为 KNOWLEDGE 且包含 POSSIBLE 或 SUPPORTED 的 claims
- **THEN** 显示为资料断言，保留确定性标签，不将列表标题写为候选故障原因

#### Scenario: 空结果和风险

- **WHEN** 答案没有 claims 或没有 suggestions，并包含高风险提示或人工核验限制
- **THEN** 工作台显示摘要、空项说明及真实风险和限制，不编造候选原因、检查步骤或已完成核验

#### Scenario: 真实模型没有输出候选原因

- **WHEN** 真实模型返回非 POSSIBLE_CAUSES 类别或空 claims 的已完成结果
- **THEN** 按实际类别展示已有内容，允许用户以普通文字提交继续调查，不为启用深入调查而补造候选原因

### Requirement: 判断选择只改变查看焦点

工作台 SHALL 允许用户选择当前结果的一条判断，展示其原文和关联引用；选择本身不得提交消息或调用模型。建议、不确定点与限制若没有判断级关联字段 SHALL 标为“本轮”内容，不伪造逐判断关联。

#### Scenario: 切换判断

- **WHEN** 用户点击另一条判断
- **THEN** 中央详情切换为该判断，网络中没有新增调查提交，引用列表仅包含该判断关联的真实引用

#### Scenario: 判断没有引用

- **WHEN** 选中的判断未关联引用或关联 ID 无法在已取得引用中找到
- **THEN** 显示“当前无已关联引用”或“引用暂不可用”，不创建虚构来源

### Requirement: 证据详情区分来源与数据性质

工作台 SHALL 提供可关闭的证据详情，展示引用实际提供的标题、来源类型、来源 ID、版本、章节或页码、摘录及 dataNature。文档记载、配置快照、现场观测类型和用户报告 SHALL 清楚区分；AUTHORIZED 仅说明来源已授权，FIELD_OBSERVATION 类型本身不证明接入真机或现场已核实。

#### Scenario: 授权手册与模拟观测并存

- **WHEN** 一条引用为 AUTHORIZED 知识文档，另一条为 SIMULATED 现场观测
- **THEN** 分别展示来源和各自数据性质，整体结果沿用服务端 dataNature，不把观测标为实时真机数据

#### Scenario: 来源缺少定位信息

- **WHEN** 引用没有页码或仅有 Markdown 章节
- **THEN** 展示实际章节与摘录，省略缺失定位，不虚构 PDF/XLS 文件类型、下载地址或结构化冲突标记

#### Scenario: 用户自述与已核验事实分开

- **WHEN** 用户补充“现场看到控制器型号为 B”，尚无服务端核验证据
- **THEN** 补充显示为用户报告，不能由界面改写成 FIELD_VERIFIED、CONFIRMED 或已确诊
