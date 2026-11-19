# DeviceTruth — 通用设备检修 Agent

DeviceTruth 提供设备检修对话、知识检索与章节引用、标准 Skill、资产记录查询、调查历史和实时进度。模型提出动作，服务器校验权限、执行只读工具并登记证据，随后输出可追溯的回答。

技术栈：Java 21、Maven 3.9+、Spring Boot 3.5.16、AgentScope Java 2.0.4、PostgreSQL 16 + pgvector、Vue 3 + TypeScript + Vite。前端需要 Node ≥20.19、npm ≥10。

## 在新机器上运行

仓库包含 8 个领域 Skill、343 篇正式知识及导入所需原件。知识数据单独管理，服务 JAR 与镜像只携带运行所需的演示夹具和 Skill。部署者需要自己的数据库和模型供应商密钥；数据库内容与私有配置不会从开发者机器自动复制。

1. 安装 JDK 21、Maven、Python ≥3.9 和 Node，确认 `java -version`、`mvn -version` 均使用 Java 21。
2. 复制 `.env.example` 为 `.env`，填写自己的数据库密码、DeepSeek/OpenAI 兼容模型配置及 DashScope embedding 密钥。
3. 启动 PostgreSQL、构建并导入知识，步骤见 [知识库部署与运维](docs/knowledge-operations.md)。
4. 启动标准 `product` 服务和前端。首次启动安装 8 个默认 Skill，之后保留数据库中已停用或缩小权限的设置。

本项目当前使用服务器固定的 owner/project 身份，适合单用户或受控内网部署。多人共享上线仍需接入真实认证。模拟资产和观测保留 `SIMULATED` 标记。

## 构建与测试

```bash
mvn test
mvn clean package
python3 scripts/knowledge_data.py verify
java -jar target/devicetruth-core-0.1.0.jar --scenario=B --reasoner=scripted
```

`.mvn/maven.config` 将依赖缓存放在项目内 `.m2/repository`。真实 PostgreSQL 测试需设置 `DEVICETRUTH_TEST_PG_BASE_URL`（仅主机、端口和末尾斜杠）、`DEVICETRUTH_TEST_PG_USERNAME`、`DEVICETRUTH_TEST_PG_PASSWORD`；测试创建并清理自己的随机测试库，账号需具备建库权限。外部真实模型测试为单独启用项。

```bash
cd frontend
npm ci
npm run test -- --run
npm run build
npm run dev
```

开发前端默认将 `/api` 代理至 `127.0.0.1:8080`。

## 启动标准服务

将 `.env.example` 中的变量导出至进程环境（Python 运维入口和直接启动 JVM 不自动读取 `.env`），或使用已被 Git 忽略的 `devicetruth.local.properties` 配置模型。数据库凭据通过环境提供。

```bash
java -Dloader.main=io.devicetruth.server.DeviceTruthServerApplication \
  -cp target/devicetruth-core-0.1.0.jar \
  org.springframework.boot.loader.launch.PropertiesLauncher \
  --spring.profiles.active=product --server.port=8080
```

`GET /api/health` 为进程健康检查。产品接口包括 `/api/conversations`、`/api/runs/{id}`、运行事件/SSE、引用与 Skill，以及 `/api/knowledge/*`、`/api/files`。上传支持 Markdown；PDF 上传尚未实现，正式知识中的 PDF 原件已预先整理为可导入正文。

Docker Compose 会读取项目 `.env`：先完成知识导入，再运行 `docker compose --profile product up -d --build`。前端默认 `http://127.0.0.1:8081`。数据库镜像当前配置 `pull_policy: never`，首次运行需先获取 `compose.yaml` 中固定版本的镜像。更多备份、恢复和故障处理见 [运维说明](docs/operations.md)。

## 知识与 Skill

- 示例环境配置使用 Dense + Lucene BM25（CJK、20 候选、RRF60、等权、Exact）；`DEVICETRUTH_KNOWLEDGE_MODE=LEGACY` 可切回旧模式。
- 模型产生的实际查询词先在数据库事务外获取向量，正式工具步骤只读缓存、重新校验可见性并登记引用。缺失的词法索引根据数据库内容重建，无需复制本机索引。
- 知识导入生成 `READY_INACTIVE` 版本；显式激活后才对普通产品检索可见。导入、激活、回滚均通过 [通用运维入口](docs/knowledge-operations.md) 操作。
- `skills/` 包含汽车、工厂设备及 6 个专项 Skill。配置和格式见 [领域 Skill](docs/domain-skills.md)、[Skill 规范](docs/skill-normalization.md)。权限取服务器许可与 Skill 声明的交集。
- 真实知识使用 `text-embedding-v4`、1024 维及固定档案修订。更换模型或维度需要建立匹配的新知识版本，不能混用原向量。

可选设置 `DEVICETRUTH_ASSETS_SEED_FIXTURES=true`、`DEVICETRUTH_ASSETS_FIXTURES=SC-0245-B`，在自己的数据库中加入随仓库提供的工厂演示资产。已有设备业务数据应通过受控导入或备份迁移管理。

密钥、本地数据库备份、运行日志、查询缓存、Lucene 索引和验收产物不提交到 Git。开发历史与实验记录在本地保留，平台运行不依赖这些记录。
