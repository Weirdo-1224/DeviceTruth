# 第 3 组：身份、空间与执行接力

## 登录后选择空间，不再使用固定 HTTP 身份

集合和创建路由使用 `X-Workspace-ID`；未提供时只默认当前账号的个人空间。会话、Run、文件、文档等 UUID 路由先从数据库找到实际空间，再校验当前成员，Header 如果存在必须匹配。未认领的历史资源没有 workspace，不会自动对注册者开放。资产编号不是全局 UUID，按选择的空间查询。

`HttpAccess` 只用于 HTTP，返回服务器解析的 AccessScope；四个产品控制器不再从配置取固定 owner/project。匿名 401、已知空间无写权 403、跨空间或不存在 404。上传/知识导入保留旧的显式空间知识用途，调查附件分层仍在第 5 组实施。知识检索会登记引用，因此只读账号不能用 POST 绕过写限制；已登记来源可以按权限 GET 阅读。

调查摘要游标包裹空间和查询/资产/状态筛选，跨空间或不同筛选复用返回 INVALID_CURSOR；底层仓储过滤始终保留 owner/project。报告继续由持久 Run/结果/来源读取，不增加模型调用。

## Run 从持久身份恢复，不借用浏览器线程

`GeneralAgentService.forScope` 建立不可变身份的轻量视图，共享原服务的唯一 TaskOrchestrator、有界 executor、待派发集合和心跳设施，不为每个空间另开线程池。旧构造器和内部入口保留固定 ServerIdentity 合同。

创建调查绑定 workspace/created_by；提交绑定消息 actor、Run initiated/execution actor，并写执行段输入记录；补答更新当前 execution actor，保留原 initiated actor。worker 调度时使用 Run 仓储的 persistedScope 重建身份；product 启动与周期恢复仅扫描已绑定空间的活跃归属；未认领旧 Run 不默认执行，旧内部 recoverQueued 入口仍可供明确维护用途恢复。恢复过程，以原租约/CAS 处理，不读取 HttpAccess、请求 ThreadLocal 或当前页面选择。第 4 组继续补齐讨论引用、附件版本快照和单未决轮/期望版本控制，本阶段不冒称这些已接通。

`ToolContext` 新增 actorUserId/workspaceId，原八参数构造仍可用。SkillRuntime 共享只读 registry，按存储归属读取安装和选择，默认安装只使用服务器策略；不会复制他空间的授权，ON CONFLICT 不覆盖已停用记录。运行选择仍写原 dt_run_skill，重启沿原版本和检查点恢复。

## 后台授权与浏览器会话生命周期分开

后台每步/模型返回后经 claimStopCode 检查当前 execution actor 是否仍有 ADMIN/COLLABORATOR；工具事务和最终结果采纳再锁定/检查成员关系。撤权后不执行后续工具、不采纳迟到问题/答案，宿主保留失败码 AUTHORIZATION_REVOKED。账号退出只撤销浏览器会话，不冒充取消后台 Run；账号全局停用由管理端后续服务对接，不在本组添加平台管理功能。

SSE 建立前解析 Run 的实际空间，以 Cookie 会话认证，不要求 Header、不在 URL 携带认证凭证。订阅捕获稳定的 session ID、actor 和 workspace；每批/每条业务事件、终态及 15 秒心跳前复查持久 Session 和成员资格，失败后清理订阅、停止输出。浏览器退出/会话过期会终止旧流；成员移除会终止该空间流，降为 VIEWER 仍可读但后台执行权限失效。客户端切换关闭旧订阅和缓存隔离仍由 6.6 实施。

## 团队邀请接口

- 管理员 GET/POST `/api/workspaces/{id}/invitations`；创建输入 `{email,role}`。
- 管理员 POST `/api/workspaces/{id}/invitations/{invitationId}/resend` 或 `/revoke`。
- 已登录用户 POST `/api/invitations/accept`，输入 `{token}`；新用户先正常注册再接受。

V22 保存摘要、邮箱、空间、受控角色、有效期、创建人、接受人及状态。默认 7 天，可通过 `devicetruth.auth.invitation-token-lifetime` 配置正 Duration。凭证只通过 SMTP 邮件发送，列表不返回；邀请 PENDING/ACCEPTED/REVOKED/EXPIRED 与邮件 PENDING/ACCEPTED_BY_PROVIDER/FAILED 分开。投递失败不授予资格；重发旋转旧凭证。匹配邮箱且有效凭证在空间锁内原子加入，已有成员 ON CONFLICT DO NOTHING，不提升原角色；重放不再次授予权限。邀请无效 400，邮箱不匹配 403，已消费/撤销/过期 409。重发与成员变更采用相同空间锁顺序。

## 隔离回归如何衔接旧数据夹具

原 product HTTP 回归已改用 AuthenticatedProductTestSupport：实际登录并发送 Cookie/CSRF。该测试助手只允许随机 `devicetruth_test_` 数据库，显式把原固定身份夹具的空个人空间绑定到原测试存储键，并认领原内部生成的测试会话；历史作者仍为空。它不参与产品代码，不提供匿名 fallback，也不代替第 7 组的正式维护认领命令。

状态机/授权回归使用明确 scripted、确定性 embedding 或阻塞提案替身；真实数据库与 HTTP/SSE 已实际执行。真实 SMTP 收件、外部模型、前端完整协作体验和部署分别在后续验收项记录，不把这些协议证据当作现场或模型验收。
