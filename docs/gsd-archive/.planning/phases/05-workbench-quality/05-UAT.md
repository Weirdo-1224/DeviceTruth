# Phase 5 UAT 记录

> 自动化技术项按项目验收策略由代理实际运行并记录（见 05-ACCEPTANCE.md、ACCEPTANCE-RESULTS.md、各 SUMMARY）。
> 本文件只保留需要产品/业务/设计判断的集中确认项，不替代技术测试。

## 自动通过（source: automated）

- 05-01：SSE 实时/历史/续传、白名单、容量与线程隔离、前端时间线、浏览器五场景。
- 05-02：工作台报告/事实对照/取消/Skill、14 例 E2E 矩阵（离线+在线）、安全四项、PDF 明确状态。
- 05-03：Compose 启动、重启持久、备份恢复行数一致、迁移失败不降级、正向修复可读旧数据。

## 集中人工确认

1. **多人共享部署（result: pending）**：当前只交付固定本地身份的本地演示部署；真实认证与跨身份 HTTP 负例未接入（NOT_RUN）。请确认是否接受本阶段以本地演示部署交付、真实认证另行排期。

2. **E2E-07 产品行为边界（result: pass, source: user）**：用户 2026-11-14 明确“首轮必须追问不算硬约束”。已按决定修改冻结用例定义（`datasets/eval/v1-cases.json` E2E-07 增加 `boundaryDecision` 与分支 A/B 判定：直接作答必须带引用/未使用资料说明 + limitations/uncertainties 且不确诊；产生追问时仍要求同 run 补答与引用回读），并同步修改 `scripts/verify_v1_e2e.py` 的判定逻辑后重跑，结果见 ACCEPTANCE-RESULTS。该修改是边界澄清，不是事后改分：判定只会更严（直接作答需要来源与限制），不会放宽既有违例。
