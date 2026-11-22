# Proposal

## Why

当前前端已有设计令牌和局部响应式规则，但全局样式、工作台覆盖、组件 scoped 样式及管理端独立样式并行，导致字号层级、控件规格、语义色和反馈状态不统一。需要在不干扰现有调查业务能力的前提下，建立可复用、可验收的视觉基础，降低跨页面体验分裂和后续样式回归风险。

## What Changes

- 统一登录页、用户工作区、调查与诊断、报告、证据弹窗、设备、知识库、团队页面及管理端的排版层级、间距、圆角、边框和语义色。
- 收敛按钮、输入框、选择框、标签和文字链接的规格，并统一 hover、focus-visible、active、selected、disabled 等交互状态。
- 统一 loading、empty、success、error、retry、busy 的反馈表达，补齐失败、取消、等待补答、执行中等 Run 状态的视觉区分。
- 统一表单禁用与校验反馈，使字段错误与对应输入建立清晰的视觉和无障碍关联。
- 整理重复覆盖和组件内硬编码样式，保留现有页面结构、信息架构、业务文案含义及数据流。
- 建立桌面、窄屏和手机视口，以及空数据、长文本、只读和各类运行状态的浏览器验收矩阵。

## Capabilities

### New Capabilities

- `frontend-visual-system`: 定义前端共享视觉层级、控件外观、交互状态、反馈状态及响应式展示的一致性要求。

### Modified Capabilities

无。当前 `openspec/specs/` 没有可修改的既有能力规格。

## Impact

- 主要影响 `frontend/src/design.css`、`workspace.css`、`pro.css`、相关 Vue 组件的 scoped 样式及 `admin/admin.css`。
- 为建立字段与错误提示的关联，可能局部调整表单模板属性；不改变提交逻辑、状态机或数据合同。
- 不修改后端代码和 HTTP API，不引入 UI 组件库、CSS 框架或其他运行时依赖。
- 验收使用现有 Vitest、`vue-tsc`、Vite 构建及浏览器实际页面；视觉验收不得仅以构建成功代替。
