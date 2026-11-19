# Spec Delta

## Purpose

让专业用户准确看到 Agent 等待回答的实际问题，并在刷新、打开历史或问题版本变化后仍能定位到原 Run。补答沿用已有消息合同和服务端校验，将用户输入保留为现场陈述。

## ADDED Requirements

### Requirement: 待答正文始终随活动问题可见

会话中存在 WAITING_FOR_USER 时，工作台 SHALL 在主工作台展示当前持久 pendingQuestion 的完整正文和适用的待补字段，不依赖消息记录是否打开或末条消息是否重复其正文。浏览历史时 SHALL 标明待答轮并保留定位入口。

#### Scenario: 同一问题也是最新消息
- **WHEN** 当前 Run 等待用户，末条会话消息正文与 pendingQuestion.question 完全相同且记录抽屉关闭
- **THEN** 主页面仍展示完整追问正文和补答入口

#### Scenario: 刷新后浏览历史
- **WHEN** 用户刷新含待答轮的会话，随后选择另一历史轮
- **THEN** 主页面仍可读实际待答正文、识别其所属轮并定位回该轮；所看历史保留自己的结果和来源

### Requirement: 补答与当前持久问题一一对应

工作台 SHALL 将回复提交给当前明确的等待目标，携带 runId、pendingQuestionId、questionVersion，成功后继续原 Run。问题已变化或服务端拒绝旧版本时 SHALL 保留草稿并回读最新状态，禁止把草稿自动提交为新调查。

#### Scenario: 使用当前版本补答
- **WHEN** 用户从等待卡片或现场观察入口进入已有问题并提交答复
- **THEN** 请求携带该目标的完整 replyTo，原 Run 恢复且会话 Run 数不增加，回复保留为用户陈述

#### Scenario: 提交时问题版本已失效
- **WHEN** 服务端拒绝已失效的问题版本
- **THEN** 保存已有输入，展示当前服务端状态及新问题正文（如仍待答），重新提交需用户明确确认，不增加普通消息或新 Run
