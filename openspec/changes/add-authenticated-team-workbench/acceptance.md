# 规划与验收记录

日期：2026-11-19（Asia/Shanghai）。阶段：OpenSpec 提案完成，实施尚未开始。

## 用户确认与输入

用户已确认一线维修/工程师、调查驱动、电脑优先、克制工程风格，以及邮箱密码注册、个人/团队双空间、共同接续调查、讨论显式引用、单调查串行、附件明确发布的设计。完整原型交付后，用户回复“ok，我认同这个设计，可以开始openspec”。

认可原型已保存在 `prototype/workbench.html`；上一阶段原型 49 项断言和原始失败/修复历史保留在 `prototype/review-history.md`，不计入本轮后端验收。

## 本次实际检查

| 检查 | 实际结果 |
|---|---|
| `openspec status --change add-authenticated-team-workbench` | 4/4 规划工件 complete：proposal、specs、design、tasks |
| `openspec validate add-authenticated-team-workbench --strict` | valid，退出码 0 |
| 工件结构核对 | 5 个 capability spec、30 项 requirement、54 个 scenario，与 proposal 一致 |
| 实施状态核对 | 33 项实施任务全部未完成，完成数 0 |
| 范围核对 | 仅新增本 change 目录中的规划及原型快照；未修改业务源码、旧变更或运行配置 |

两项 OpenSpec 规划检查均通过：通过 2、失败 0、错误 0、跳过 0。结构与任务数量是文档核对，不折算业务测试数量。

## 未执行的验收

本轮业务测试执行数为 0；未运行 Maven/JUnit、前端 Vitest/build、产品浏览器、PG 迁移、真实 SMTP、embedding 或外部模型调用，也未执行数据认领/部署。相应实现与验证均在 tasks 中保持未完成；没有据原型或健康探针将它们标为通过。

## 实施前明确的合同变化

- product HTTP 将从匿名固定身份转为登录身份，属于 proposal 标注的 BREAKING 行为；CLI、legacy 和原内部用法保留。
- 新认证调查采用单未决 Run，团队普通提交增加期望版本，原补答三元组和幂等合同保留；旧多个未决轮不删除。
- 新调查级上传默认附件作用域；旧显式知识导入用途保留，不强迫旧知识上传调用携带调查 ID。
- 不能把原型的预览身份、模拟运行、内存角色判定当作服务端实现。Spring/PG/邮件配置需在实施中落地并分别验收。

## 后续入口

用户已追加 apply 授权，先按第 1–3 组落地账号/空间/后台作用域，再推进共同调查、资料范围和前端真服务接入。原有其他变更及未完成可选任务保持独立。

## 2026-11-19 首次实施：1.1 部分实现，隔离 PG 验证阻塞

- 新增 `V19__account_workspace_session.sql`，含账号、个人/团队空间、成员和 JDBC Session 表与索引；未修改 V1–V18，未连接或认领运行库。
- 新增 `AccountWorkspaceMigrationTest`，覆盖空库约束及 V18 历史会话升级；完整历史 Run/引用夹具仍待补充。数据字典和隔离测试入口见 `data-foundation.md`。
- `mvn -version`：Maven 3.9.9，Microsoft JDK 21.0.13。
- `mvn -q '-Dtest=AccountWorkspaceMigrationTest' test`：退出 1；总数 2，通过 0，断言失败 0，错误 2，跳过 0。两项均在建库前报 `Missing test configuration: DEVICETRUTH_TEST_PG_BASE_URL`。当前进程也未提供测试用户名/密码；源码及测试编译已完成，迁移 SQL 尚未在 PG 执行。
- 1.1 保持未勾选；整体 0/33，第 1–3 组里程碑未达到。未执行全量测试、前端构建/浏览器、真实模型、embedding、SMTP 或部署；没有切换产品入口。
- 恢复条件：提供专用隔离 PostgreSQL/pgvector 实例的上述三项环境配置（不要把密码写入此记录或聊天），允许测试账号创建随机测试库。恢复后先运行迁移测试并补齐历史关联反例，再继续 1.2/1.3 和账号授权链路。

## 2026-11-19 Docker 隔离验证与第 1 组完成

用户提示查 Docker 后，确认本机运行 `devicetruth-postgres-1`（54329）。未提取其凭据，未操作其运行库；复用本机 pgvector 镜像启动独立 `dt-workbench-migration-test`，仅绑定 `127.0.0.1:54339`、无持久卷。该容器使用 trust 仅供本机临时测试，不是部署配置。每项测试在其中创建/清理随机数据库，容器保留供下一组测试复用，完成后需移除。

失败及修复历史：普通沙箱 Docker pipe 权限不足，获工具授权后成功；首次普通沙箱 Maven 连接失败（2 项均连接错误），授权连接后 2/2 通过。扩展 V20 历史多个未决 Run 和讨论重复序号断言后 2/2 再通过。加入真实 PG 作用域测试后最新执行 3 项，通过 3、失败 0、错误 0、跳过 0；命令仍为 `mvn -q '-Dtest=AccountWorkspaceMigrationTest' test`。

新增 V20、AccessScope 和 WorkspaceScopeResolver；数据字典/调用约定见 `data-foundation.md`。作用域测试合并在 AccountWorkspaceMigrationTest 中复用隔离建库，不另建 WorkspaceScopeTest。检查个人默认空间、两团队隔离、资源/Header 不一致、匿名、降权与移除。V18 升级测试保存旧消息和两个 QUEUED Run，并断言历史作者/空间保持 NULL。

第 1 组任务 1.1–1.3 已完成，OpenSpec 3/33；第 1–3 组首个产品里程碑仍未达到。组件尚未接入 HTTP/worker，未开放半成品认证接口。第 2 组依赖预取时普通沙箱 DNS 失败，授权下载后 `mvn -q -U -DskipTests compile` 成功；尚未保留 Security 依赖到 pom，避免默认自动配置在专用安全链落地前影响 legacy/health。

下一项是 2.1 注册/登录及 product 专用安全链，然后继续密码恢复/邮件和第 3 组全链授权。原隔离 PG 阻塞已解除，不再需要用户提供数据库配置。尚未执行全量回归、前端、真实模型、SMTP 或部署验收。
