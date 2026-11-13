# DeviceTruth 运维说明（05-03）

本文件给出本地演示部署、备份恢复与迁移失败处理的实测步骤。所有命令只在独立测试库/测试卷执行，不作用于生产数据。

## 1. 入口

| 入口 | 命令 | 说明 |
| --- | --- | --- |
| CLI（fat JAR） | `java -jar target/devicetruth-core-0.1.0.jar --scenario=B --reasoner=scripted` | 不依赖数据库，A–D 场景与报告 |
| 产品 HTTP | `docker compose --profile product up -d` | PostgreSQL(pgvector) + 后端(8080) + 前端(8081) |
| 本地开发 | `mvn -q package` 后 `java -cp target/devicetruth-core-0.1.0.jar ...` + `cd frontend && npm run dev` | 前端代理 `/api` 到 8080 |

镜像构建只复制 `target/devicetruth-core-0.1.0.jar` 与 `frontend/dist`，**不包含任何密钥**；供应商密钥通过环境变量或本机被忽略的 `.env` 注入。

## 2. 首次部署

```bash
# 1) 构建产物（宿主）
mvn -q package                     # 后端 fat JAR（含全部测试）
cd frontend && npm ci && npm run build && cd ..

# 2) 配置（只在当前 shell，绝不提交）
export DEVICETRUTH_DB_PASSWORD='<本地演示口令>'
export DEVICETRUTH_OWNER_ID=local-owner DEVICETRUTH_PROJECT_ID=local-project
export DEVICETRUTH_AGENT_REASONER=scripted     # 或 openai/qwen + 对应密钥
export DEVICETRUTH_EMBEDDING_PROVIDER=deterministic
# 需要真实模型时再按需设置：DASHSCOPE_API_KEY / OPENAI_API_KEY / OPENAI_BASE_URL / OPENAI_MODEL

# 3) 启动与健康检查
docker compose --profile product up -d --build
curl -fsS http://127.0.0.1:8080/api/health
curl -fsS http://127.0.0.1:8081/ | head -3
```

卷：`devicetruth-files`（受控上传原文件，容器 `/data/files`）、`postgres-data`（数据库）。两者都是命名卷，`docker compose down` 不删除，只有 `down -v` 才会清除。

## 3. 备份与恢复（实测脚本 `scripts/verify_deployment.ps1`）

```powershell
# 备份：数据库逻辑备份 + 文件卷打包
docker compose --profile product exec -T postgres pg_dump -U devicetruth -d devicetruth -Fc -f /tmp/devicetruth.dump
docker compose --profile product cp postgres:/tmp/devicetruth.dump ./outputs/backup/devicetruth.dump
docker run --rm -v devicetruth_devicetruth-files:/data -v ${PWD}/outputs/backup:/backup alpine \
  tar -czf /backup/files.tgz -C /data .

# 恢复：导入到全新数据库并对照行数/哈希，文件卷解包到新卷后启动第二套实例
docker compose --profile product exec -T postgres createdb -U devicetruth devicetruth_restore
docker compose --profile product cp ./outputs/backup/devicetruth.dump postgres:/tmp/restore.dump
docker compose --profile product exec -T postgres pg_restore -U devicetruth -d devicetruth_restore --no-owner /tmp/restore.dump
```

校验：`dt_agent_run` / `dt_run_event` / `dt_message` / `dt_evidence_reference` / `dt_uploaded_file` 行数与备份一致；`dt_uploaded_file.checksum` 与恢复后的文件 SHA-256 一致；恢复库启动的产品实例能读到原会话、引用与报告，能对同一 run 继续追问。

## 4. 迁移失败与回退

Flyway 迁移只前进（`V1..V16`），**不支持可逆 DDL**。实测的迁移失败场景（在临时库）：

1. 已升级到最新版本的库里手动改坏一条 `flyway_schema_history` 校验和，重启后端。
2. 观察：启动失败且日志明确 `Validate failed: Migrations have failed validation`，服务不监听端口，前端显示不可用——不会带着不一致的 schema 提供服务。
3. 处理：**正向修复（forward-fix）**——恢复备份 `pg_restore` 到新库并把后端指向该库，或修复导致校验和变化的那个迁移文件后重新发布（禁止手改历史迁移）；然后重新启动并确认 `/api/health` 与旧会话可读。
4. 演练记录（命令、退出码、日志片段）保存在 `outputs/validation/deployment-*/`。

## 5. 权限与多人部署边界

当前产品使用**服务器固定本地身份**（`DEVICETRUTH_OWNER_ID/PROJECT_ID`），只适合同机演示；服务层已对资产、文档、附件、run、引用做归属校验，但**没有真实认证**。多人/对外共享部署必须先接入身份提供方（另行设计），并补 HTTP 层的身份负例；在此之前该部署模式标记 NOT_RUN，不能把 loopback 演示当成多人权限。

## 6. 已知限制

- PDF 上传返回明确状态 `OCR_REQUIRED / PDF_NOT_SUPPORTED`（保留文件记录，不产生正文或页码）；OCR/版面解析未实现。
- SSE 连接断开由心跳写失败感知（最长约两个 15 秒周期），订阅额度随之回收。
- 恢复演练覆盖数据库与受控文件卷；不覆盖外部对象存储或多节点一致性。
