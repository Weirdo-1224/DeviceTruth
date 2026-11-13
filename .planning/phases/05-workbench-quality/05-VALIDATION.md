# Phase 5 VALIDATION（证据索引与口径）

> 本轮所有结论只引用实际执行过的命令与产物；跨轮次证据标注批次，不重复计为“又跑了一次”。

## 1. Java / 后端

| 证据 | 批次/命令 | 结果 |
| --- | --- | --- |
| 全量离线测试 | `mvn -o test`（2026-11-13 23:47 批次，日志 `outputs/validation/full-test-final.log`） | 360 run / 359 pass / 0 fail / 0 error / 1 skip，EXIT=0 |
| SSE 集成（含复验 5 项） | `mvn -o '-Dtest=AgentStreamTest' test` | 12/12 |
| 答案守卫 | `mvn -o '-Dtest=AnswerGuardTest' test` | 25/25 |
| Agent 会话流 | `mvn -o '-Dtest=GeneralAgentFlowTest' test` | 19/19 |
| 知识导入/PDF 状态 | `mvn -o '-Dtest=KnowledgeImportTest' test` | 8/8 |
| 打包 | `mvn -q -o -DskipTests package` | 成功，`target/devicetruth-core-0.1.0.jar` |
| 旧 CLI | `python scripts/verify_cli.py` | A–D + 缺密钥 FAILED 不降级 + 私有配置，全 PASS |

## 2. 前端

| 证据 | 命令 | 结果 |
| --- | --- | --- |
| 单测 | `cd frontend && npm run test -- --run` | 36/36 |
| 类型与构建 | `cd frontend && npm run build` | vue-tsc 通过、Vite 构建成功 |

## 3. SSE 浏览器端到端

| 证据 | 批次 | 结果 |
| --- | --- | --- |
| A 实时（含 RUN_WAITING）/ B 断线按 `Last-Event-ID=5` 补齐 / C 切换隔离 / D 无 run 清理 / E 永久失败后历史回读 | `outputs/validation/sse-browser-808e290f3a99/result.json`（审查修复后代码，2026-11-13 23:24） | 5/5 PASS，无 page error |

## 4. 评测矩阵（14 例）

| 口径 | 批次 | 汇总 |
| --- | --- | --- |
| 离线（scripted + deterministic，隔离库） | `outputs/validation/v1-e2e-ba30cbde55ff/results.json` | PASS 6 / COVERED_BY_JUNIT 4 / NOT_RUN 4 / FAIL 0 |
| 在线（DeepSeek `deepseek-flash` + DashScope `text-embedding-v4`，仅冻结语料） | `outputs/validation/v1-e2e-81d4d43797d2/results.json` | PASS 9 / COVERED_BY_JUNIT 4 / FAIL 1（E2E-07 模型首轮直接作答） |

## 5. 部署与恢复

| 证据 | 批次 | 结果 |
| --- | --- | --- |
| compose 构建/启动/健康/nginx 代理/手册导入/产品链路/重启持久/备份恢复 6 表行数/迁移失败阻断/forward-fix | `outputs/validation/deployment-20261114014426/`（drill.log、result.json、migration-failure.log） | 全 PASS；result.json status=PASS |

## 6. 口径说明

- “PASS”仅指该批次实际执行并命中断言；`COVERED_BY_JUNIT`/`NOT_RUN` 不折算为通过。
- 模拟数据（SIMULATED）与真实供应商（ONLINE_PROVIDER）分开记录；真实设备/现场核验没有执行。
- 部署演练在隔离临时卷与临时库执行，结束 `down -v`；未触碰 interview-postgres 开发库。
- 本轮未重跑 2026-11-13 之前的测试批次；历史报告只作参考，不计入本轮统计。
