# Design

## Context

由improve-evidence-aware-answer-guard拆出，原规划任务与失败记录见该变更prior-task-ledger.md及acceptance.md。

## Decisions

规程支持与执行资格分开；查阅不要求预建资产，执行指导须匹配型号、配置、作业和必要前提。用户确认保留未独立核验来源，绑定执行段，版本变化使相关确认失效。沿原ASK_USER/补答三元组和唯一Run状态机实现，不建第二套Agent或资质平台。服务端和组件先交付，第六组实际消费后联合验收；未取得合法材料时仅阻塞相应真实指导验收，不冒充通过。

## Risks

当前未实施，不向普通审核结果暗示已获得现场操作资格。部署与真实库迁移另行授权。
