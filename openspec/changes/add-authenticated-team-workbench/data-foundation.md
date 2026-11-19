# 数据基础实施记录

## V19 新增表

| 表 | 用途与约束 |
|---|---|
| `dt_account` | UUID 账号；邮箱必须 trim/小写且唯一；口令编码与显示名称分开存储 |
| `dt_workspace` | PERSONAL/TEAM；个人账号关联唯一；owner/project 键对唯一；个人空间必须关联账号，团队不能使用个人账号关联 |
| `dt_workspace_member` | 空间与账号复合主键；ADMIN/COLLABORATOR/VIEWER；两端外键 |
| `spring_session` | JDBC Session 主记录、唯一会话标识、过期时间及 principal 索引 |
| `spring_session_attributes` | 会话属性字节；会话删除时级联清理 |

V1–V18 不改写。V19 不回填旧归属，不创建默认账号/成员，不认领历史数据。注册原子创建个人空间、个人成员约束及最后管理员保护由后续事务服务接通；仅此迁移不构成可用认证产品。Session 依赖与配置仍属于 2.1，尚未启用。

## 隔离验证入口

`AccountWorkspaceMigrationTest` 只读取 `DEVICETRUTH_TEST_PG_BASE_URL`、`DEVICETRUTH_TEST_PG_USERNAME` 和 `DEVICETRUTH_TEST_PG_PASSWORD`，不读取产品数据库配置。base URL 必须是仅含 host/port、以 `/` 结尾的 JDBC PostgreSQL 地址。测试账号须能创建隔离数据库，实例须支持 pgvector。

每个测试创建随机 `devicetruth_test_<UUID>` 数据库，结束后只删除自己成功创建的库。一个测试从空库迁移并检查唯一约束、成员外键及 Session 级联；另一个迁移到 V18、插入历史会话，再升级检查原归属保留且未生成账号/空间。

运行：`mvn -q '-Dtest=AccountWorkspaceMigrationTest' test`。恢复 Docker 隔离环境后最新结果为 3/3 通过；早期缺配置及连接失败保留在 acceptance 记录。

## V20 作者、协作和材料基础

- 会话增加可空 workspace/创建人，消息及事件增加可空 actor；Run 增加初始发起人、当前执行段 actor 和输入快照。历史不回填作者。
- `dt_run_input_segment` 按 run/段号保存正式输入快照；`dt_investigation_discussion` 独立存讨论，调查内 seq 唯一、版本固定为 1。
- 上传文件增加上传人、调查关联和用途；旧入口默认 SPACE_KNOWLEDGE，CONVERSATION_ATTACHMENT 必须关联同归属调查。
- `dt_knowledge_publication` 绑定同归属空间/文档版本，空间内文档唯一，保留发布人、原作者、名称与适用范围。发布资格、解析状态和不可变写入在后续服务接通。
- 所有新增资源关联使用原 owner/project 复合外键。升级测试保留两个历史 QUEUED Run、原消息和未知作者，验证讨论重复序号拒绝；更广泛历史引用/附件服务回归由后续任务继续覆盖。

## 可信作用域调用约定

`AccessScope` 区分 actor、workspace、服务器角色和存储键；它不是请求 DTO。`WorkspaceScopeResolver.resolve` 的 actor 只能来自认证层，空间 Header 只提供 workspace UUID；不接收客户端 owner/project/role。无空间选择时只取该账号个人空间，不回退演示身份。

UUID 资源路由先从资源元数据取得 workspace，再调用 `resolveResource`；Header 不匹配返回 404。匿名 401、无成员资格 404、只读写入 403。`requireWrite` 重新查询成员而非信任旧 scope，因此降权/移除立即影响下一次检查。组件尚未装入 HTTP/worker，接线由 2.1、3.3–3.5 完成；原 ServerIdentity 内部构造保持不变。

当前真实 PG 测试覆盖个人默认、两团队隔离、Header 与资源不匹配、匿名、成员降权与移除。角色只从成员查询返回，不存在接受客户端角色字段的入口。

## V23 协作尝试轮号

新增 dt_agent_run.round_number，按全部历史尝试编号，调查内唯一且非空。入队沿会话锁取 max+1，取消不回收编号。执行快照和作者的第 4 组接线已完成，见 discussion-api.md 与 acceptance.md 最新记录。
