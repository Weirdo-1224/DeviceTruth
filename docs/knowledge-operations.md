# 知识库部署与运维

以下命令从项目根目录运行。需要 JDK 21、Maven 3.9+、Python ≥3.9，以及 PostgreSQL 16 + pgvector。运行正式模型时使用部署者自己的供应商密钥。

## 1. 获取代码与知识数据

Git 仓库包含 343 篇正文、必要来源原件、冻结清单和 8 个领域 Skill，克隆后无需获取开发者的数据库或验收报告：

```bash
python3 scripts/knowledge_data.py verify
mvn clean package
```

需要独立交付数据时：

```bash
python3 scripts/knowledge_data.py pack --output outputs/packages/devicetruth-knowledge-v1.tar.gz
```

同时交付 `.tar.gz` 与 `.tar.gz.sha256`。接收者核验压缩包 SHA-256，解压后执行：

```bash
python3 scripts/knowledge_data.py verify --root /path/to/extracted-package
```

包内保留原始目录结构，包括 8 个来源 Skill 正文；应使用匹配的代码与 Skill 版本。服务 JAR 和 Docker 镜像不包含原始 PDF、评测题或历史报告，知识导入后由数据库提供检索正文。

## 2. 配置自己的环境

复制 `.env.example` 为 `.env` 并填写。Docker Compose 自动读取 `.env`；直接运行 Java/Python 时需将变量导出到当前进程环境。Unix shell 可在确认该文件为自己维护的配置后使用 `set -a`、`source .env`、`set +a`；PowerShell 使用 `$env:变量名='值'`。

| 配置 | 用途 |
| --- | --- |
| `SPRING_DATASOURCE_URL` | Java JDBC 地址，例如 `jdbc:postgresql://127.0.0.1:54329/devicetruth` |
| `SPRING_DATASOURCE_USERNAME` / `SPRING_DATASOURCE_PASSWORD` | 数据库账号与密码；不写入命令参数或 Git |
| `DEVICETRUTH_OWNER_ID` / `DEVICETRUTH_PROJECT_ID` | 导入和产品服务使用同一身份，默认 `local-owner/local-project` |
| `DEVICETRUTH_AGENT_REASONER=openai` | 使用 OpenAI 兼容模型，另配 `OPENAI_BASE_URL/MODEL/API_KEY` |
| `DEVICETRUTH_EMBEDDING_*` | 默认 `openai` 协议、DashScope compatible-mode、`text-embedding-v4`、1024 维、revision=1 |
| `DEVICETRUTH_EMBEDDING_API_KEY` | embedding 专用密钥；为空时可复用 `DASHSCOPE_API_KEY` |
| `DEVICETRUTH_KNOWLEDGE_MODE` | 示例配置 `HYBRID_DUAL`，未配置时兼容默认 `LEGACY`；支持 `LEGACY/DENSE/BM25/IDENTIFIER/HYBRID_TRIPLE` |
| `DEVICETRUTH_KNOWLEDGE_LUCENE_ROOT` | 索引目录，默认 `outputs/knowledge-lucene`，必须可写 |

模型也可从已忽略的 `devicetruth.local.properties` 读取。为了保持导入与服务档案一致，建议部署时统一使用环境变量，不复制开发者的私有配置。

使用项目 Compose 起新数据库时，先确保固定版本数据库镜像已存在（`pull_policy: never`），再运行：

```bash
docker compose --profile standalone-db up -d postgres
```

数据库应为专用数据库。`import` 会运行 Flyway 迁移；已有环境升级前按运维规范备份。默认命令不会激活或替换现有知识版本。

## 3. 校验、导入与费用控制

```bash
python3 scripts/knowledge_admin.py preflight
```

此命令只核验本地正文、来源原件哈希和切片，输出 release ID、343 篇、682 个切片及输入 token 保守上界，不连接模型或数据库。

导入需要显式指定预算与自己账号的每千 token 单价。下面的变量由部署者根据当前供应商价格填写，运维程序不查询价格或账户账单：

```bash
python3 scripts/knowledge_admin.py import \
  --budget-cny "$IMPORT_BUDGET_CNY" \
  --rate-cny-per-1000-tokens "$EMBEDDING_PRICE_CNY_PER_1000_TOKENS"
```

成功后输出 `READY_INACTIVE` 和 release ID。费用账本在 `outputs/knowledge-admin/`，按数据库、版本及 endpoint 隔离；每次 HTTP 尝试先预留预算，重试和未知收费保守计入。中断后使用同一命令、预算及账本目录续跑，已导入正文与已存向量会复用。保留账本，不删除账本以绕过预算。导入完成后再增加相同调用，不会重新向量化全部知识。

可选参数：`--jar` 指定构建 JAR，`--knowledge-root` 指向数据包中的 `datasets/knowledge`，`--state-root` 指定持久费用账本目录。JDK 通过 `JAVA_HOME` 或 `PATH` 发现，不依赖 Homebrew、Docker 容器名或历史验收回执。

## 4. 激活、回滚与索引重建

```bash
python3 scripts/knowledge_admin.py status
python3 scripts/knowledge_admin.py activate --release-id <导入输出的UUID>
python3 scripts/knowledge_admin.py rollback --release-id <需要停用的UUID>
python3 scripts/knowledge_admin.py build-index --release-id <需要重建索引的UUID>
```

激活先构建并核对指定版本的 CJK 词法索引，再在数据库事务内切换可见版本。同一 owner/project 同时只有一个正式版本激活。`rollback` 停用指定版本，普通检索不再返回它，但历史运行引用继续可读；若需恢复此前版本，对此前版本执行 `activate`。

索引是可重建缓存，数据库是知识与引用的权威来源。标准产品服务在查询前自动准备缺失的索引和查询向量，因此迁移或换机器不需要复制开发者本机索引。管理员索引目录与服务不同也可正常使用，服务会在自己的可写目录重建。不要手工混放不同 owner/project/profile 的索引文件。

## 5. 标准产品启动与 Skill

```bash
java -Dloader.main=io.devicetruth.server.DeviceTruthServerApplication \
  -cp target/devicetruth-core-0.1.0.jar \
  org.springframework.boot.loader.launch.PropertiesLauncher \
  --spring.profiles.active=product --server.port=8080
```

前端开发运行 `cd frontend`、`npm ci`、`npm run dev`。容器部署先构建 JAR 和 `frontend/dist`，然后：

```bash
docker compose --profile product up -d --build
```

首次启动从仓库 `skills/` 安装 8 个默认版本，包括 6 个设备专项 Skill。安装与权限保存在 PostgreSQL，重启不会恢复已经被禁用或缩小的许可。模型可选择 Skill、读取参考资料、检索知识并引用章节；读取原文仍须属于当前运行已获取的引用。Carrier 等 Skill 未声明的工具继续受原权限限制。

可设置 `DEVICETRUTH_ASSETS_SEED_FIXTURES=true`、`DEVICETRUTH_ASSETS_FIXTURES=SC-0245-B`，添加工厂演示资产。其他实际资产及业务记录通过自己的受控数据导入提供，不从知识文献推断生成。多人共享上线需配置真实认证，当前默认身份属于单用户部署模式。

## 6. 故障检查

- 知识检索为空：检查 owner/project、release 是否激活、服务与导入的 embedding 档案是否一致。
- 只有词法结果：查看检索 trace 中 `channelFailures`，检查 embedding 密钥、网络和模型额度。
- `EMBEDDING_BUDGET_STOP`：保留账本，核对单价、已用费用和剩余预算后由运维调整策略。
- `IMPORT_EMBEDDING_PROFILE_MISMATCH`：用与该 release 相同的模型、维度和修订操作；换模型需导入独立版本。
- 缺少 Skill：确认从项目根目录启动，或设置 `DEVICETRUTH_SKILLS_ROOT` 指向匹配版本的目录。

开发计划、历史报告、评测代码、人工验收专用启动器、数据库备份、运行缓存与凭据不属于上述运行依赖。
