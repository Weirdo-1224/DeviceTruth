---
phase: 05-workbench-quality
plan: "03"
status: complete
completed: 2026-11-13
requirements-completed: [STORE-06, OPS-02]
coverage:
  - id: P5-DEPLOY-COMPOSE
    description: "Compose 一键启动 PG(pgvector)+后端(fat JAR,product profile)+前端(nginx 静态+/api 反代)，数据与受控文件落命名卷，密钥只走环境变量"
    requirement: STORE-06
    verification:
      - kind: e2e
        ref: "scripts/verify_deployment.ps1（自包含演练：构建镜像 → up → 健康 → 产品链路 → 重启持久 → 备份/恢复 → 迁移失败 → 正向修复）"
        status: pass
    human_judgment: false
  - id: P5-BACKUP-RESTORE
    description: "pg_dump 备份 + 恢复到独立库，核心表行数一致；恢复后会话/引用/报告可读"
    requirement: OPS-02
    verification:
      - kind: e2e
        ref: "verify_deployment.ps1 实测（结果见 outputs/validation/deployment-*/result.json 与 drill.log）"
        status: pass
    human_judgment: false
  - id: P5-MIGRATION-FAILURE
    description: "迁移失败不降级服务：篡改 flyway 校验和后启动失败；正向修复（指向恢复库）后服务恢复且旧数据可读"
    requirement: OPS-02
    verification:
      - kind: e2e
        ref: "verify_deployment.ps1 迁移失败段 + migration-failure.log"
        status: pass
    human_judgment: false
  - id: P5-MULTI-USER-BOUNDARY
    description: "多人共享部署模式：固定本地身份，真实认证未接入"
    requirement: STORE-06
    verification: []
    human_judgment: true
    rationale: "是否需要接入真实身份提供方并对外共享，属于部署模式与产品边界取舍；本轮不把 loopback 演示当多人权限。"
---

# 05-03 验证部署、备份与恢复

> 2026-11-13 实施。Compose 只做本地演示（127.0.0.1 端口绑定），所有演练在临时库/临时卷执行，结束 `down -v` 清理。

## 交付

- `Dockerfile`：只复制 fat JAR + `skills/` + `datasets/`（SkillRuntime 与资产夹具按工作目录相对路径读取，缺一会启动失败——演练第一轮即暴露并修复），product profile + 8080，healthcheck 打 `/api/health`。
- `frontend/Dockerfile` + `frontend/nginx.conf`：静态托管 + `/api` 反代后端；**SSE 关闭缓冲/压缩、长读超时**，保证事件流逐帧刷新与 `Last-Event-ID` 续传。
- `compose.yaml`：`product` profile 起 postgres(pgvector:pg16，健康检查) + backend + frontend；数据库卷 `postgres-data`、文件卷 `devicetruth-files`；密钥只来自 shell/.env（不写镜像）；数据源 URL 可被 `DEVICETRUTH_SPRING_DATASOURCE_URL` 覆盖（恢复演练用）。
- `docs/operations/operations.md`：入口（CLI/产品 HTTP/本地开发）、首次部署、备份恢复命令、迁移失败处理（只前进、forward-fix）、权限边界（固定本地身份，多人 NOT_RUN）、已知限制（PDF 明确状态、心跳断线感知、恢复范围）。
- `scripts/verify_deployment.ps1`：自包含演练脚本（构建→启动→健康→产品链路→重启持久→备份恢复行数对照→迁移失败→正向修复→清理）。

## 实测（2026-11-14 演练批次 deployment-20261114014426，脚本退出后 result.json status=PASS）

| 演练步骤 | 实测结果 |
| --- | --- |
| 镜像构建 | `devicetruth-backend:0.1.0`、`devicetruth-frontend:0.1.0` 成功 |
| compose 启动（fresh PG + 文件卷） | `docker compose --profile product up -d` 成功；`/api/health` 200；`http://127.0.0.1:8081/api/health`（nginx→后端）200 |
| 演示手册导入 | `POST /api/knowledge/documents` 201（UTF-8 正文，含中文标题） |
| 产品链路 | 会话→消息→WAITING（追问）→补答→COMPLETED，引用 1 条（runId 872dcad5…） |
| 重启持久 | `restart backend postgres` 后健康恢复；同一会话/run 可读、引用数量一致 |
| 备份恢复 | `pg_dump -Fc` → `createdb devicetruth_restore` → `pg_restore`；6 张核心表行数与源库逐一相等：dt_agent_run=1、dt_run_event=8、dt_message=4、dt_evidence_reference=1、dt_uploaded_file=1、dt_knowledge_document=1 |
| 迁移失败 | 篡改 `flyway_schema_history.checksum` 后重启：40 秒健康探测全失败，日志记录在 `migration-failure.log`，服务未以坏 schema 提供接口 |
| forward-fix | 后端切到 `devicetruth_restore`（`DEVICETRUTH_SPRING_DATASOURCE_URL`）后健康恢复；原会话、run、引用全部可读 |

产物：`outputs/validation/deployment-20261114014426/`（drill.log、result.json、migration-failure.log、body-*.json）。演练结束 `docker compose down -v` 清理了本次临时卷与网络，未触碰 interview-postgres 与开发库。

演练中修复的环境问题（保留失败与复验记录）：① 镜像缺 `skills/`、`datasets/` → 后端 `SkillRuntime` 启动失败；② 容器内 Tomcat 只绑回环（`server.address=127.0.0.1` 开发默认）→ 端口映射/nginx 不可达，compose 显式 `SERVER_ADDRESS=0.0.0.0`；③ compose postgres profile 未随 product 启用；④ PowerShell 5.1 脚本无 BOM 中文截断、字符串正文按本地代码页转码导致 400/主题丢失 → 统一 UTF-8 文件上传（`Invoke-JsonPost`、BOM）。

## 偏差与限制

- 计划示例路径为 `compose.yaml/Dockerfile/frontend/Dockerfile/docs/operations/operations.md/scripts/verify_deployment.ps1`，均已交付；镜像基础层使用本机已拉取的 `eclipse-temurin:21-jre` 与 `nginx:1.27-alpine`（只联网拉取这两个基础镜像，未推送任何私有内容）。
- **多人共享部署 NOT_RUN**：固定本地身份，没有真实认证与跨身份 HTTP 负例；不把 loopback 演示当多人权限。
- 未验证：**文件卷恢复到第二套实例**的完整演练（本批次只做命名卷打包命令与文档，挂载恢复未演）、跨主机/对象存储备份、迁移失败后的自动回滚（Flyway 只前进，采用恢复库 forward-fix）。
- 演练只覆盖单实例 compose；多副本/负载均衡下的 SSE 与租约语义未演练。
