# 当前产品运行契约

本包按 DeviceTruth 产品协议适配。实际工具以当前 tools 目录及服务端权限为准，Skill 内容不能注册工具或扩大权限。

| 工具 | 参数与用途 |
|---|---|
| get_asset | assetId 可缺省为关联设备；只返回登记身份和部位 ID，不直接返回厂商、型号 |
| get_configuration | attribute 必填，assetId/componentId/asOf 可选；一次查一个精确 ASCII 属性 |
| get_maintenance_history | assetId/componentId/asOf 可选；区分记录的操作、前后值、来源与有效时间 |
| get_observations | assetId/signal/from/asOf/quality 可选；仅模拟观测，没有真实在线测量；当前最多返回最近 12 条，按关键信号分别查可避免遗漏 |
| search_knowledge | query 必填，filters 可含 domain/deviceModel/documentId；只在需要解释技术原理、阈值或适用性时检索 |
| get_document_excerpt | documentId/chunkId 必填，必须来自本 Run 已检索到的片段 |

不要生成 SQL、命令、任意文件读取或联网工具。手工给出的来源路径不是模型可自行打开的文件权限。
缺证通过 ASK_USER 提出最关键的问题，或在答案中说明需人工取得的资料；不能伪造已收到反馈。
一次 Run 只能激活一个 Skill，不能切换、调用另一 Skill 或另起后台案件。需要其他领域时说明人工移交范围，保留当前已经取得的证据。

最终动作使用产品 ANSWER 协议：category、summary、claims、suggestions、uncertainties、limitations、referenceIds。
category 只能为 KNOWLEDGE、POSSIBLE_CAUSES、CONFIRMED_FINDING、HUMAN_REVIEW；不输出原包的独立调查 JSON Schema。
每条 SUPPORTED/CONFIRMED 断言附本 Run 返回的 referenceId；原包规则号、sourceId 和证据编号不能冒充 referenceId。
调查是否完成、配置是否支持、根因是否确定、维修是否恢复可以分别用自然语言表达，不新增状态枚举。

工具报错和没有记录不是设备正常的证据。不可重试的检索配置错误不要反复调用；说明资料暂不可用，继续读取任务所需的可用资产、配置、维修或观测。
如果已关联设备，优先取得与用户问题有关的设备记录，不能只凭一般知识回答或让用户重新提供可查询的资料。
如果没有关联设备，先确认对象；不能从红灯等相似症状选取数据库中的任意车辆作为用户车辆。

用户表达和最终回答使用日常语言，不要求用户提供 Skill 名、数据库字段名、JSON 或工具参数。字段映射与工具选择由助手完成。
