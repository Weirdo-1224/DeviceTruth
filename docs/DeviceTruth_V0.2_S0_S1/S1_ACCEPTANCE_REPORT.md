# DeviceTruth V0.2｜S1 最终验收报告

> 状态：**NOT_STARTED（待 R07 实测）**。全部测试项均初始化为 NOT_RUN，禁止将示例当作实际验收。

## 1. 验收元数据

- 验收时间：待实际测试填写
- 仓库分支/commit：待填写
- JDK/Maven：待填写
- 设备/操作系统：待填写
- CLI JAR 实际名称：待填写
- Server 真实启动命令/端口：待填写
- Router 模式：待填写（offline / online，必须分开测试）
- Core Reasoner 模式：待填写（scripted / online，必须分开测试）
- 模型连接是否真实可用：待填写，不记录 API Key
- 证据文件/报告存放位置：待填写

## 2. 基线与新增测试总览

| 项目 | 真实命令 | 退出码与统计 | 产物/备注 |
|---|---|---|---|
| `mvn test` | 待填 | NOT_RUN | 不得用历史 42 代替 |
| `mvn package` | 待填 | NOT_RUN | 待填 |
| 原 CLI A/B/C/D | 待填 | NOT_RUN | 必须实际复跑 |
| 未知工具/伪造证据 E 负例 | 待填 | NOT_RUN | 不以 CLI E 命令替代 |
| Server 启动+运行 | 待填 | NOT_RUN | 提供日志 |
| HTTP 端到端 | 待填 | NOT_RUN | 提供请求和真实响应 |
| online Router | 待填 | NOT_RUN | 无网络/Key 记 BLOCKED_BY_ENV |
| online Core Reasoner | 待填 | NOT_RUN | 无网络/Key 记 BLOCKED_BY_ENV |

## 3. TC-01~TC-20 验收矩阵

状态只允许：`PASS / FAIL / NOT_RUN / BLOCKED_BY_ENV`。每个 PASS 必须有具体证据链接、命令/日志或测试方法，不得凭开发者自述判断。

| TC | 验收内容 | 状态 | 命令/请求/测试类与证据 | 失败或环境阻碍 |
|---|---|---|---|---|
| 01 | Session 201 + 合法 UUID，可查消息 | NOT_RUN | 待填 | 待填 |
| 02 | CAR-B profile 查询与来源 | NOT_RUN | 待填 | 待填 |
| 03 | CAR-B history 查询，不编造 | NOT_RUN | 待填 | 待填 |
| 04 | DIAGNOSE 真实进入 Core | NOT_RUN | 待填 | 待填 |
| 05 | 202/Location/QUEUED，GET 真实结果 | NOT_RUN | 待填 | 待填 |
| 06 | 运行中无事件，结束后白名单事件 | NOT_RUN | 待填 | 待填 |
| 07 | “这台设备”无上下文 CLARIFY | NOT_RUN | 待填 | 待填 |
| 08 | 未登记 SN-0245 无证据串用 | NOT_RUN | 待填 | 待填 |
| 09 | 未支持故障/危险操作边界 | NOT_RUN | 待填 | 待填 |
| 10 | 两会话并发，Reasoner/归属隔离 | NOT_RUN | 待填 | 待填 |
| 11 | timeout/非法 JSON/Core FAILED | NOT_RUN | 待填 | 待填 |
| 12 | 原 CLI A/B/C/D 与 E 负例 | NOT_RUN | 待填 | 待填 |
| 13 | requestedAt≠diagnosisAsOf 且时间有效 | NOT_RUN | 待填 | 待填 |
| 14 | 出厂记录不能冒充当前控制器 | NOT_RUN | 待填 | 待填 |
| 15 | evidenceId/sourceRef/SIMULATED | NOT_RUN | 待填 | 待填 |
| 16 | 事件/错误/日志不泄漏机密 | NOT_RUN | 待填 | 待填 |
| 17 | 超容量 429/503，无幽灵 QUEUED | NOT_RUN | 待填 | 待填 |
| 18 | Web/CLI 双入口独立工作 | NOT_RUN | 待填 | 待填 |
| 19 | 无 API Key 离线通路 | NOT_RUN | 待填 | 待填 |
| 20 | 真实在线 Router + online Reasoner | NOT_RUN | 待填 | 待填 |

## 4. 关键完整请求链（必须贴真实证据）

```text
POST /api/sessions
    → sessionId = [实际 ID]
POST /api/sessions/{sessionId}/messages
    → route=DIAGNOSE / diagnosisId=[实际 ID] / HTTP 202
GET /api/diagnoses/{diagnosisId}?sessionId=...
    → [QUEUED/RUNNING/COMPLETED/FAILED 的实际记录]
GET /api/diagnoses/{diagnosisId}/events?sessionId=...
    → [实际事件序列和白名单来源]
Core 对应调用/RunReport 文件与任务 ID： [实际路径或内部引用，不公开 API]
```

- 对普通 QUERY 另附 `ASSET_PROFILE`、`MAINTENANCE_HISTORY` 的真实请求/响应及引用字段。
- 对在线模型另附实际模型调用 trace 摘要（脱敏），没有则记录 `BLOCKED_BY_ENV`。

## 5. 实际新增/修改文件

- 待实际 Git diff 生成，不填猜测路径。

## 6. 当前能力与边界结论（验收后再填写）

- 能否证明中文 HTTP 已真实驱动已有 Core：**未验证**。
- 离线/CI 结果：**未验证**。
- 在线 Router/Reasoner 结果：**未验证**。
- 原 CLI、安全/证据回归：**未验证**。
- 尚未实现且不阻塞 S1 的能力：S2 Hypothesis/补证恢复、S3 双域 Skill、S4 Vue/实时轨迹。

## 7. Codex 最终 Review

- R07 复审文件：`reviews/R07.md`（待生成）
- 结论：**PENDING**
- 主要阻塞项：待填
- 残留非阻塞项：待填
- 用户确认：待填
