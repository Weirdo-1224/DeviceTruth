# 设备业务数据补充与验收（2026-11-14）

本批次 `domain-enrichment-20261114` 已实际导入本机 PostgreSQL。用户确认数量要求为五张设备业务表各不少于 100 条；知识按独立内容建设，运行与授权记录由实际行为产生。

## 实际结果

目标：`devicetruth-postgres-1` / `devicetruth`，本机 `127.0.0.1:54329`，归属 `local-owner/local-project`。未修改 Schema、原始桌面资料、Java 业务代码或 Skill 安装配置。

| 表 | 导入前 | 导入后 | 来源与含义 |
|---|---:|---:|---|
| dt_asset | 0 | 166 | 100 台公开登记设备 + EV v0.2 的 33 台模拟车辆 + 工业 v0.7 的 33 台模拟设备 |
| dt_asset_component | 0 | 709 | 原包 609 个部件/调查位置 + 100 个明确标记的整机逻辑节点 |
| dt_asset_configuration | 0 | 1,035 | 585 条公开登记快照声明 + 450 条原包模拟登记/配置声明 |
| dt_asset_maintenance | 0 | 175 | 156 条真实来源 JOB 工单文档快照 + 19 条模拟动作/生命周期记录 |
| dt_asset_observation | 0 | 1,063 | 原包模拟观测、验证读数和诊断返回字段，按部位命名信号 |

总计 3,148 行，每行均有原始文件与定位。没有为达到数量门槛新增虚构设备、维修或读数。未导入旧版独立 PLC 库和内嵌 EV 副本，避免与当前版本混用。

完整来源盘点覆盖 1,564 个文件、1,136 种文件内容，识别 428 个内容相同的副本。实际使用的 436 个来源文件均登记 SHA256；原包的 420 个模拟原始载荷逐一核对来源清单中的 SHA256。

## 字段与事实口径

### 公开登记与工单

原始 `obj_code` 加 `NYC-AMPS-` 前缀成为资产 ID。每台公开登记设备使用 `ASSET` 逻辑节点，其标签为“整机登记对象（逻辑节点，不表示部件BOM）”。它表示配置/工单记录所属整机，不能用作已取得实体部件清单的证明。

| 原始字段 | 目标 attribute | 含义 |
|---|---|---|
| obj_manufact | registryManufacturerSnapshot | 采集时公开台账登记的厂商字符串 |
| obj_manufactmodel | registryModelSnapshot | 台账登记型号，非现场确认型号 |
| obj_serialno | registrySerialNumberSnapshot | 台账登记序列号 |
| obj_desc | registryDescriptionSnapshot | 台账设备描述 |
| obj_commiss | registryCommissionDateTextSnapshot | 原始日期文本，未裁决为实际投运日 |
| obj_status | registryStatusCodeSnapshot | 原始系统状态代码，未推断其现场含义 |

`valid_from` 使用各原始下载响应的 `retrieved_at_utc`，表示**该文档快照的采集时点**；`recorded_at` 为本系统本批次录入时间。它们不替代物理装机、投运或维修执行时间。属性名及 `source_ref` 均保留快照语义，采集之前的查询不会倒推出这些记录已存在。

156 条 JOB 的 `operation=WORK_ORDER_DOCUMENT_SNAPSHOT`。原文记录的描述、状态和原始时间保留在来源追踪中；`source_ref` 同时提供脱敏后的描述与登记状态。`before_value/after_value/observed_at` 均为空，`verification_status=DOCUMENTED`，没有将 `Completed` 当成独立修复验收。`source_type=CONFIGURATION` 在本批次专门表示来源文档声明，不解释为更换已实施。

202 条 IN/AA/STAT 等其他事件不导入维修表。工时与物料明细等关联资料仍留在原始包中；没有将工时、物料出库推断成维修完成或实际 BOM。

这批公开资料按用户本次指令用于本地处理，标为 `AUTHORIZED`，不代表现场核实。未发送到外部 LLM 或 embedding 服务。Carrier 采购附件中的第二种投运日期继续保留在原交接包中，本批次没有混入台账快照或覆盖冲突值。

### EV 与工业模拟数据

沿用原始资产和部位标识、版本与来源，数据保持 `SIMULATED`。将原始登记字段映射为 `registeredManufacturer/registeredModel/softwareVersion/firmwareVersion` 等 ASCII 属性；配置证据保持原字段名。

`source_id` 使用 `EV02:` 或 `IND07:` 命名空间，避免跨包重号。模拟动作保留原本的前后值；批准/计划事件采用带状态前缀的 operation，前后实施值为空，避免把批准写成执行。

模拟观测的 signal 为 `<component_id>.<原始字段名>`，防止不同部位的同名信号混合。GOOD/UNCERTAIN/BAD 是原始模拟返回的质量映射，仍然不构成真实测量或现场确认。当前为 GOOD 878、UNCERTAIN 181、BAD 4；输出无效标 BAD，部分返回、时钟不可靠和用户描述等保留 UNCERTAIN。

全部配置/维修记录采用 `DOCUMENTED`，`authority=1`，表示本批次不擅自提升来源优先级。没有产生 `FIELD_VERIFIED`。原包中为 null 的值保留为未知并记录排除原因，没有转成 0、正常或通过。

### 去重与场景隔离

完整文件哈希识别副本后，仅选当前版本。工业旧场景 IND-01～IND-04 共用 `SN-0245`，但各自具有互斥证据；保留 IND-01 的完整世界，另外 11 条反事实证据不并入该资产，也没有克隆资产来凑数量。原资料仍在。

稳定 UUID 与业务唯一键支持重复导入；若同一身份对应的业务内容已经不同，整个导入事务失败，不静默覆盖。数据库唯一约束、原始来源定位和整批回读共同校验。

去掉代理 UUID/来源编号等因素后，配置仍有 1,034 组不同业务内容，观测有 979 组。少量相同读数来自原始数据的不同模拟时点或独立记录，保留其时序与来源；没有复制记录来扩大样本数。同一设备的多个文档工单分别以原始工单编号、描述、状态和原文定位区分。

## 执行与验证

```bash
# 仅构建及预检查，不访问数据库
python3 -B scripts/enrich_device_data.py

# 先备份，再以单事务幂等导入五表；不更新或删除既有数据
python3 -B scripts/enrich_device_data.py --apply

# 数据库/API/真实产品运行链路验收，使用离线 scripted 推理
python3 -B scripts/verify_enriched_data.py
```

后两条命令需要当前 Docker 的本地访问权限。验收脚本临时启动产品服务，自动选择 JDK 21，结束后关闭；数据库凭据仅在进程内传递，不写入文件。脚本没有修改全局 Java 设置。

实际执行两次导入：第一次五表新增 3,148 行；第二次五表新增均为 0。实际产品验收 **30/30 通过，0 失败，0 跳过**，覆盖来源哈希、行数、幂等、关联、数据性质、场景隔离、时间边界、404、Skill 激活、配置/观测工具及当前 Run 引用。没有重跑 Maven 全量测试；未修改 Java 业务代码。

实际产品链路：用户问题关联资产 → scripted 选择现有 Skill → Gateway 查询 PostgreSQL 配置与模拟观测 → 当前 Run 登记证据引用 → AnswerGuard 检查答案 → 返回带模拟标记的结果。两次运行均完成：

- EV：`96d5c8f1-9977-4524-933b-87379c3f06a7`
- 工业：`2b4c38a3-4b2e-4d51-bac7-9c2361c50763`

没有执行真实外部模型、现场设备诊断、性能压测或备份恢复演练；本次不把它们记为通过。

## 本地交付物

完整内容位于 `outputs/validation/domain-enrichment-20261114/`（本地数据与验收证据，不提交 Git）：

- `source-inventory.json`：全部输入文件与重复内容识别。
- `source-manifest.json`：本批次使用来源的 SHA256。
- `data.json`、`provenance.json`：规范化五表记录和逐行原文定位。
- `exclusions.json`、`validation.json`：未知值、反事实场景、非 JOB 事件等排除明细及数量。
- `import-result.json`、`reimport-result.json`：首次事务和幂等复跑结果。
- `product-verification.json`：30 项检查与两次真实产品运行的事件、引用、答案。
- `before-import.sql`：首次写入前完整数据库备份；后续 `before-reimport-*.sql` 为复跑前备份。

备份已实际生成，未演练恢复。导入不会删除既有数据；已有来源发生纠错时需明确新版本/撤回口径，不能用重复执行覆盖历史。

知识库与新场景 Skill 保持下一阶段建设，不为满足五表数量制造文档、安装许可或运行记录。后续可从本批资产和工单问题出发，匹配有适用范围的厂商手册，再验证真实模型的检索与引用。
