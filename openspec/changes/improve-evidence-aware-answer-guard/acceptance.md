# 规划与验收记录

2026-11-19，阶段：完成用户边界讨论并创建独立规划，尚未实施。

## 已确认的产品决定

1. 区分资料事实明确与当前设备确诊，不以一个现场核验布尔值限制所有陈述。
2. 优先局部处理问题及依赖，保留独立成立内容；不机械添加“可能”，不能可靠拆分才整份处理。
3. 开放有适用规程支持的高风险具体指导，而非遇到操作词一律拒绝。
4. 没有适用规程不得由模型补写高风险流程。
5. 信息不完整可以查阅规程，但不能包装成当前设备已可执行。
6. 用户现场记录可支持强度相称的判断，不要求必须系统独立采集；来源性质保留。
7. 首版人员/现场前提可由用户明确确认，不另建资质平台；未知/否定前提不能默认为满足。
8. 关键词仅定位疑点，语义按具体陈述复核，权限/引用/来源硬边界保留。

## 既有真实证据

真实模型 `deepseek-flash` 的 Run `c96da39d-263f-4b35-84f8-c30fdab28ab0`：5 次模型尝试、3 次成功工具调用、4 条来源，最终 COMPLETED/HUMAN_REVIEW，原因为 UNCONFIRMED_FINDING。此为问题证据，不是本优化的通过证据；没有原草案定位前，不认定为已证实误拦。原脱敏报告位于 `outputs/validation/authenticated-real-model/`，完整补验记录在工作台 change 的 acceptance.md。

## 执行顺序与责任

推荐第四组 → 第五组 → 本变更的服务端/协议/组件 → 第六组消费 → 联合验收关闭本变更。第四、五组 owner 保持原 Run/材料合同，Guard owner 实施本方案与组件；第六组 owner 负责页面组合和实际消费，不并发改同一执行状态机。所有阶段需各自实际验收，不自动执行或勾选别的 change。无需等工作台 33 项全部完成才开始 Guard。

本次只创建 proposal、三份 specs、design 和 tasks，未修改 Guard、提示词、运行时、前端或数据库。未运行新的业务测试/外部模型调用，不部署、不认领真实数据。任务全部未完成；规划结构检查结果在交付时记录。

## 实际规划检查

- `openspec status --change improve-evidence-aware-answer-guard`：4/4 工件存在，proposal/specs/design/tasks 完整。
- `openspec validate improve-evidence-aware-answer-guard --strict`：valid，退出 0。
- `openspec instructions apply --change improve-evidence-aware-answer-guard --json`：15 项实施任务，完成 0，剩余 15，CLI 状态 ready；ready 表示工件齐备，不代表第四/五组前置交付或实施授权已完成。

两项规划完整性/结构检查通过；业务测试执行 0，不将它们折算为产品验收。
