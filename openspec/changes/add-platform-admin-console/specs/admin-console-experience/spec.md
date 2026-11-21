## ADDED Requirements

### Requirement: 五页独立管理结构

前端 SHALL 按 page-structure.md 提供用户、运行与用量、知识、Skill、系统与审计五页，独立目录/client/样式，复用统一登录。入口 SHALL 与产品负责人协调，不擅自重写用户 App/api/timeline。

#### Scenario: 平台管理员进入后台
- **WHEN** 有权限用户完成登录并打开管理入口
- **THEN** 展示五项导航；运行统计合并在运行与用量内，团队管理与预览身份切换不混入后台

### Requirement: 权限就绪与操作反馈明确

页面 SHALL 区分加载/空/失败/无权/操作中/冲突/未就绪。写操作 SHALL 明确目标、影响和原因，回读真实状态；前端 MUST NOT 以演示数据或按钮隐藏替代后端权限/生命周期校验。

#### Scenario: 写接口未接通或状态冲突
- **WHEN** capability 显示未就绪，或提交返回版本冲突
- **THEN** 页面显示真实原因，不改本地状态为成功；刷新版本后由用户再次明确提交

### Requirement: 脱敏呈现和未知用量

页面 SHALL 使用管理 DTO，未知/部分用量、版本不匹配、未发布和停用 SHALL 分开表示；MUST NOT 发起用户私密详情读取或内容 SSE 订阅。

#### Scenario: 用量和依赖无证据
- **WHEN** 费用缺失或模型依赖没有检测证据
- **THEN** 页面显示未知/覆盖不足，不展示零费用或全绿健康示例

### Requirement: 账号和管理空间切换隔离

前端 SHALL 按 account/targetWorkspace/query generation 隔离缓存，切换/退出取消旧请求、清详情/秘密草稿并丢弃晚到响应。未选择目标空间 MUST NOT 默认管理员个人空间。

#### Scenario: 空间切换后旧请求返回
- **WHEN** A 空间详情请求未完成便切到 B 或退出
- **THEN** A 的晚到数据不更新当前页面，旧请求/连接和不该保留的表单被清理

### Requirement: 窄屏与键盘可用

五页 SHALL 在 1600×1000、1366×768、390×844 可用，表格局部滚动且无整页横溢，确认框 SHALL 支持 Escape、焦点管理和关闭后焦点回归。

#### Scenario: 键盘确认和窄屏表格
- **WHEN** 用户仅用键盘打开/关闭确认框并在窄屏查看列表
- **THEN** 焦点可见且回到触发按钮，表格可局部滚动，主要操作可达
