#!/usr/bin/env python3
"""Rebuild the eight product Skills using the v2 handbook; preserve desktop originals."""
import csv
import hashlib
import json
from pathlib import Path
from skill_standards import LEGACY_SPECS, STANDARD_REFERENCES, render_standard_skill

ROOT = Path(__file__).resolve().parents[1]
SOURCE = Path('/Users/chiye/Desktop/工业设备')
EV = SOURCE / 'DeviceTruth_EV_v0.1_领域数据与Skills/02_领域Skills'
IND = SOURCE / 'DeviceTruth_Industrial_v0.6_数据扩充版/02_领域Skills'
CARRIER = SOURCE / 'DeviceTruth_Industrial_v0.6_数据扩充版/08_真实领域交接_2026-11-14/skill'
TOOLS = ['get_asset', 'get_configuration', 'get_maintenance_history', 'get_observations',
         'search_knowledge', 'get_document_excerpt']

CONTRACT = '''# 当前产品运行契约

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
'''

EVIDENCE = '''# 来源、时间与证据边界

设备事实来自当前 Run 的受控工具结果和已登记引用。Skill 正文与矩阵只提供调查方法，不能直接证明某设备状态。
sourceId 是原始记录标识，referenceId 才是当前回答可引用的标识。原包 EVR/K/KS/SRC/PUB/SUP 编号仅作为开发来源线索；未实际检索取得的原文不称已经读到。

asOf 是本 Run 的事实查询时点。validFrom/validTo 是声明有效区间，recordedAt 是系统录入时刻，observedAt 是核验/观测发生时间。
当前工具没有独立的“当时已知信息截止”查询能力，不能声称完成双时态历史重放；来源原始设备时钟、独立性与覆盖若未返回则保持未知。
没有失效日期不等于已确认至今仍成立。历史读数必须带日期，不说成实时测量；不同对象、部位、工况或时间的记录不能直接比较。

当前模拟资产与观测保留 SIMULATED。GOOD 是模拟返回的质量，不能升级为现场核验；UNCERTAIN/BAD 需要明确可信性限制。
DOCUMENTED 是资料记载，不能因资料新或数值明确就写成 FIELD_VERIFIED；核实状态按每条工具记录判断。
AUTHORIZED 只表示资料允许在当前环境处理，不等于实体设备已核实。公开登记/工单快照的有效起点是采集时刻，不是安装或施工时刻。

出厂设计、期望配置、申请、批准、实际执行、独立复测与用户反馈分别判断。APPROVED 前缀是批准记录，不能推导部件已更换。
WORK_ORDER_DOCUMENT_SNAPSHOT 只表示来源系统有工单文档；Completed 或 PASSED 字符串、费用、出库记录不能单独证明维修已实施或恢复。
空维修查询只说明当前范围未查到，不能写成从未维修；没有报警读数不能推断没有故障。
未知值保持未知，不填零或正常。未取得专业检测不推算故障原因、健康率、适配性或维修成功。
恢复结论需要关联动作、客观判据、身份和验证窗口；第一次复测失败与曾经通过后复发分开。
'''

DATA_EV = '''# 已入库的车辆字段与信号

用 get_asset 取得实际部位列表，按用户问题取证；不要把下面的候选字段都当作每台车都有的数据。
VEHICLE 部位的登记属性可查 registeredManufacturer、registeredModel、modelYear、market、softwareVersion、vehicleAlias、commissionDate。
这些是原包模拟登记声明，不是当前实车身份验证。softwareVersion 与部分证据中的 software_version 是不同属性，按实际返回值使用。

充电设置配置候选：CHARGE_PORT 的 schedule_enabled、scheduled_start_at、schedule_start_local、site_timezone、soc_pct、charge_limit_pct、charging_state。
配置缺失时查询对应观测，不能以“没有配置”代替“没有状态”。
观测 signal 由部位和字段组成，如 CHARGE_PORT.charge_port_color、CHARGE_PORT.light_pattern、CHARGE_PORT.charging_state、CHARGE_PORT.operator_statement。
按关键信号查询颜色、常亮/闪烁、状态及身份绑定；不要只取默认最近 12 条就声称覆盖全部观测。
身份辅助信号包括 CHARGE_PORT.identity_binding、CHARGE_PORT.observed_vehicle_alias，来源若指向别车应列为身份问题。

胎压/轮胎候选：TIRE-LF.measured_pressures_kpa、TIRE-LF.label_target_kpa、TIRE-LF.cold_soak_hours、TIRE-LF.tpms_flash_duration_s、TIRE-LF.sensor_packet_missing、TIRE-LF.sensor_ids。
某些 value 为 JSON 文本，保留轮位、单位和原字段含义。不要把字典整体当一个压力值；单位换算需说明。
滤芯、雨刮、制动分别查询 get_asset 返回的 CABIN_FILTER、WIPER、BRAKES 部位与对应精确信号。

供电/风险候选：ICCU.warning_text、ICCU.reduced_power_reported、ICCU.applicability_status、ICCU.campaign_id、ICCU.market、ICCU.query_mode；低压、动力电池、BMS 的信号按实际部位与返回范围查询。
这些召回相关记录为模拟响应，不是真实 VIN 查询。当前工具不提供外部召回服务，不把模拟别名用于真实查询。

维护历史使用 get_maintenance_history；保留实际返回的 operation、beforeValue、afterValue、sourceId 与 validFrom。动作回执不等于恢复验证。
'''

DATA_IND = '''# 已入库的工业设备字段与信号

先用 get_asset 确认部位；SYSTEM 常见登记属性为 registeredManufacturer、registeredModel、registeredSerialNumber、productFamily、orderNumber、firmwareVersion、engineeringVersion。
不是每台设备都有全部属性，登记的型号不能证明实体已核实。
PN-01 等通信部位的配置候选为 expected_profinet_name、configured_device_name、device_name、expected_station_id；CPU/控制部位可有 configuration_control_enabled；CTRL-01 等部位可有 installed_part。
部位与属性按 get_asset 和实际工具结果确定，不将所有设备写成同一槽位。

观测 signal 为部位加原字段，如 PN-01.online_profinet_name、PN-01.io_data_exchange、PN-01.link_up、PN-01.hmi_value_updates、PN-01.identity_binding、PN-01.observed_mac、PN-01.registered_mac。
默认最多取最近 12 条；必要时按具体 signal 查询，不能把默认窗口当完整诊断缓冲区。
诊断返回中的 instruction、laddr_type、mode、return_code_hex 等字段也按其实际部位前缀查询；未取得字段不补造厂商状态。

维修历史可包含命名、替换和批准声明。优先核对 sourceId、validFrom、beforeValue/afterValue，与观察到的结果分别引用。
导入时没有将多个反事实模拟世界合并；其他场景文件与评测答案不属于模型可见证据，不从文件路径自行读取。
'''

DATA_CARRIER = '''# 当前公开登记快照字段

本批公开资产使用整机逻辑部位 ASSET；它不表示已获得实际部件 BOM。get_asset 只返回登记 ID 与部位清单，型号和序列号需要进一步查询属性。
get_configuration 的 componentId 使用 ASSET，attribute 按任务选择 registryManufacturerSnapshot、registryModelSnapshot、registrySerialNumberSnapshot、registryDescriptionSnapshot、registryCommissionDateTextSnapshot、registryStatusCodeSnapshot。
属性中的 Snapshot 表示来源系统采集时的登记文本；保留原始值，不将其当作现场铭牌核验或当前设备性能。
validFrom 是公开文档的采集时刻，不能作为物理投运日；投运日期文本需核对原始口径。当前库没有自动合并采购附件里的另一日期，未检索到附件时不声称已比较两份资料。

get_maintenance_history 返回 WORK_ORDER_DOCUMENT_SNAPSHOT；sourceRef 中包含原始工单定位、描述和登记状态，beforeValue/afterValue 为空。
可以说“工单文档记载某项工作”，不能说“已经完成更换并验收恢复”。未查到工单不代表从未维修。

Carrier 规则只用于型号核对后有系列交集的设备；不能推广到另外厂商或所有 NYC-AMPS 设备。
本 Skill 不声明 get_observations：当前观测表只支持模拟数据，没有该真实机组的在线读数。
手册、过滤器参数和适用性需要通过实际成功的知识检索取得；原包 PUB/SUP 编号不能直接作为本次引用。
'''

SPECS = [
    dict(id='ev-charging-triage', root=EV, version='0.2.4', title='纯电汽车充电初查',
         description='调查电动车插枪充不上电、充电中断、红灯、降功率、预约和车桩授权问题。适用于充电现象初查；动力受限或电池危险优先供电安全调查，日常保养另用维护检查。',
         devices=['纯电乘用车', '交直流充电设备'], tasks=['充不上电', '充电中断', '充电口红灯', '充电功率低', '预约充电'], data=DATA_EV,
         refs=['charging-matrix.md'], body='''先确认车辆、交流/直流、发生阶段与提示位置。有已关联设备时，读取任务相关的登记、观测和维修记录；没有关联时先确认车辆，不根据症状猜资产。
登记型号查 VEHICLE.registeredModel；充电口观测按 CHARGE_PORT.charge_port_color、CHARGE_PORT.light_pattern、CHARGE_PORT.charging_state 查询。这里的点号前是部位，配置查询时分别传 componentId 和 attribute；观测查询则传完整 signal。
灯色和常亮/闪烁必须分别查询；仅车侧红灯不能确定由车端主动中止。区分用户刚报告的现象和库中历史快照，并在回答中标明时间。
对预约/上限问题核对当前设置、当地时区、SOC 和状态；对车桩问题核对身份及同一次会话。不同时间或工况的 AC/DC 对照只能缩小范围，不能直接认定 OBC 损坏。
对连接/降流核对锁止、功率、温度与站端限制；没有型号适用的手册就不解释灯色的唯一含义或自造阈值。
出现烧焦气味、火花、异常发热、烟雾时先作风险移交，不要求换桩反复试充。其他情况下只提出低风险补证和专业核验建议。
设置解释、可用替代充电路径、原桩修复和车辆恢复是不同结果。用证据说明已知状态、竞争解释、缺失信息与下一步，不能把动作完成当恢复验收。
缺少适用手册和实际故障码时，回答限已查询到的现象、历史记录及不能确定根因，不扩写未经证据支持的内部故障机制。建议收集已有提示并请专业人员核验；不要给用户拆装或反复试充的操作流程。涉及内部检测的任何作业必须由具备资质的专业人员按厂家规程处理。'''),
    dict(id='ev-maintenance-inspection', root=EV, version='0.2.2', title='纯电汽车维护与检查',
         description='调查电动车胎压或胎压灯、轮胎磨损、制动保养、空调滤芯、雨刮和既往维护。适用于日常检查与服务记录核对；充电异常和动力电池风险分别使用对应专项。',
         devices=['纯电乘用车轮胎与制动系统', '座舱滤芯与雨刮'], tasks=['胎压灯', '轮胎磨损', '制动液保养', '空调滤芯', '雨刮刮不干净'], data=DATA_EV,
         refs=['maintenance-matrix.md'], body='''先确认车辆、年款、市场、相关部位、问题时间和使用条件。维护到期依据实际日期/里程与适用文件，年款不能代替投运日；没有维修记录不等于从未维护。
胎压核轮位、冷胎条件、车辆标签目标、量具与单位；旧显示不能替代当前测量。TPMS 闪烁不能唯一定位传感器，压力安全未确认前不安排行车刷新。
轮胎核四轮数值、规格与换位记录，换位不会让胎纹增长，也不等于磨损根因已解决。
制动风险优先处理；到期通常说明需要检查，不能直接认定液体失效，未取得车型阈值不自造通用更换线。
滤芯/HVAC 比较同环境和风挡下的测量，主观异味改善不代表制冷系统全部恢复。雨刮核耗材、玻璃、机构与实际刮拭结果，不只凭使用年限判断电机。
分别说明建议、批准、执行和验证；仅有主观反馈时保留未验证。遇制动、轮胎损伤或可见度风险转专业处理，不安排危险道路试验。'''),
    dict(id='ev-powertrain-safety-investigation', root=EV, version='0.2.3', title='纯电汽车供电、电池与风险初查',
         description='调查电动车无法唤醒、低压供电、ICCU 告警、动力受限、电池温度或健康及召回适用性。危险征兆优先专业移交；普通充电设置和日常耗材保养不属于本专项。',
         devices=['纯电乘用车供电系统', '动力电池与热管理系统'], tasks=['车辆无法唤醒', '低压供电异常', 'ICCU 告警', '动力受限', '电池温度告警', '召回适用性'], data=DATA_EV,
         refs=['supply-recall-matrix.md'], body='''危险征兆先行：动力下降、烟雾、烧焦气味、事故浸水或高压相关风险，先提示现场应急/专业移交，不等待检索或要求继续行车试验。
读取已关联车辆的身份、版本、告警时间和可用记录。登记型号查 VEHICLE.registeredModel；ICCU 告警与适用性观测分别查 ICCU.warning_text、ICCU.reduced_power_reported、ICCU.applicability_status、ICCU.market、ICCU.query_mode。用户要求核对已有告警或召回状态时，先查这些相关观测和维修历史；没有维修记录或知识检索失败不能代替已存在的告警观测。具体信号及其他部位先读 data-access.md，空值和缺失按原样保留。低压电压需结合额定、负载与测量条件，异常电压不能单独证明电池本体损坏；旧 APP 离线状态也不能当现场状态。
ICCU 召回分别核市场、生产范围、单车适用性、旧/新措施与执行记录。当前工具没有真实 VIN 召回查询能力，模拟响应不证明真实车辆命中，也不证明无召回。
热管理提示、传感器、实际温度与工况分别核对；座舱滤芯异常不能直接解释动力电池告警。
续航下降与电池容量衰退不同；没有认可检测、SOC/能耗及可比条件时不推算健康率。部分返回、坏质量和漂移时钟均保留未知。
根因、配置、维修动作和恢复分别表达；禁止给出开电池、短接、换高压保险、绕过保护或写固件步骤。跨领域需求说明人工移交，不在当前 Run 切换 Skill。'''),
    dict(id='industrial-configuration-investigation', root=IND, version='0.2.3', title='工业设备配置与技改调查',
         description='核对工业设备当前装机、控制器型号或固件、技改及更换历史，解释出厂 BOM、工程配置和旧工单冲突，辅助采购前核验。侧重配置证据；单纯停机或通信故障优先故障初查。',
         devices=['工业控制器与PLC', '工业设备关键部件'], tasks=['装机型号核对', '控制器固件核对', '技改记录冲突', '更换历史核对', '备件采购核验'], data=DATA_IND,
         refs=['evidence-semantics.md', 'configuration-investigation.md', 'compatibility-guide.md', 'source-notes.md'], body='''先核资产、部位、型号和用户关注时点，读取同一对象的配置与维护历史。有近期在线/实物身份记录时比较其来源和质量；当前模拟观测不等于现场核实。
控制部位的装机型号属性为 installed_part；整机登记型号为 SYSTEM 部位的 registeredModel，固件为 firmwareVersion。先读 data-access.md 并使用实际属性，不自行猜成 model；部位以 get_asset 为准。空查询只表示该属性/时点未匹配，不能据此声称库内没有其他配置。
区分出厂设计、工程期望、批准变更、实施回执和实际核验。批准没有执行时不生成已装机状态；安装了某型号也不证明故障根因或可直接替换。
同一部位/属性的矛盾先排除时间变化、槽位差异、对象误配和复制模板；录入更新不等于证据更直接。同源派生记录不重复计票，无法裁决保留冲突。
优先查询实际字段与维护前后值。历史更换只能支持该次变更声明；没有近期核验时当前实体配置仍待确认。
备件还需核硬件修订、固件、机械电气接口、协议与厂商兼容条件；没有匹配资料不批准采购或给出通用兼容结论。
回答按用户语言区分记录支持的配置、冲突、待核验事项和备件适用性，不混为已经确诊或已经修复。'''),
    dict(id='industrial-controller-fault-triage', root=IND, version='0.4.3', title='工业 PLC 故障初查',
         description='调查 PLC 停机、STOP/ERROR 灯、远程 I/O 无数据、更换后通信异常和诊断调用失败。具体指令解释限核对适用性的 Siemens S7-1200；纯粹型号、固件及采购核对另用配置调查。',
         devices=['PLC与工业控制柜', '远程I/O与PROFINET设备'], tasks=['PLC停机', 'STOP灯', 'ERROR灯', '远程I/O没数据', '更换后通信异常', '诊断返回码异常'], data=DATA_IND,
         refs=['fault-evidence-matrix.md', 'diagnostic-data-semantics.md', 'profinet-investigation.md', 'replacement-closed-loop.md', 'safety-boundaries.md', 'community-case-evidence.md', 'synthetic-dataset-usage.md'], body='''先核资产、厂商、订货号、固件、故障时点与原始现象。读取相关配置、维护和精确观测；不同厂商、系列与固件不能套用同一灯义或指令条件。
字段映射先读 data-access.md。工程名称查实际通信部位的 expected_profinet_name；常见 PN-01 观测为 PN-01.online_profinet_name、PN-01.io_data_exchange、PN-01.link_up、PN-01.hmi_value_updates。观测必须使用部位加字段的完整 signal，NO_MATCHING_DATA 只说明本次条件未匹配，不能扩大为整台设备没有观测。
更换后无 I/O：比较工程期望名、已绑定身份的在线名、链路和数据交换。站点 RUN 或能扫到不代表过程数据正常；名称相同或修正后仍异常时改变调查方向。
STOP：区分计划停止、配置控制及启动相关诊断，不把所有 STOP 当硬件损坏。ERROR/供电疑点核灯位、颜色、闪烁和具体版本。
诊断调用错误与设备故障分开；只有获得指令、目标类型、模式和有效输出时才解释返回码。部分覆盖外的设备仍未知，不从工具失败推断模块损坏。
时钟漂移、窗口缺失、无权限和空记录分别处理；原始设备时间未由工具返回时不声称已校时或重建完整时间线。
配置、根因、执行与恢复分开。复测失败继续调查；恢复仅限有独立证据的窗口。危险机械、电气或安全 PLC 场景交专业人员，不提供停机、命名、复位、下载或旁路操作步骤。'''),
    dict(id='carrier-rtu-evidence-triage', root=CARRIER, version='0.1.2', title='Carrier 屋顶机组证据初查',
         description='调查型号匹配的 Carrier 48TF 屋顶机组的登记身份、维修工单、MERV过滤器变更及制冷供热问题。区分文档快照和现场性能；不用于其他品牌或未确认型号的所有HVAC设备。',
         devices=['Carrier 48TF系列屋顶机组'], tasks=['Carrier机组身份核对', '屋顶机组维修历史', 'MERV过滤器变更', 'Carrier制冷供热异常'], data=DATA_CARRIER,
         refs=['domain-knowledge.md', 'investigation-guide.md', 'evidence-requirements.md', 'decision-rules.md', 'safety-guidelines.md'], body='''先读取登记型号后缀、序列号和问题时点，确认与 48TF 系列的交集。相同序列号可支持跨文档关联，不能称现场已核实；48TF、48TM、48GCG 不按近似名称共用参数。
依据 data-access.md 的快照属性查询整机信息和历史文档；维护工具中的工单描述与实际执行、装机状态、性能恢复分别判断。
日期来源矛盾应保留原始文本，区分投运日、Open、报障、完工声明、采集和本系统录入时刻，不用较新的日期覆盖另一来源。
过滤器问题核尺寸、密封、阻力曲线、风机能力和设计风量；MERV等级或“安装MERV13”的工单标题都不足以证明适配或性能合格。
制冷/供热假设须由适用手册与具体观测支持。未成功检索原文时不宣称读过故障表，也不把系列产品册当本机 BOM。
燃气气味、烟雾、异常燃烧、疑似CO、严重电气异常或人员不适优先应急/专业移交，不要求重启试机。高风险检修只能由合格人员按现场流程处理。
本专项只读文档和登记历史，没有真实在线观测工具；回答给出可支持的历史事实、未确认项和低风险补证，不能宣布机组已经恢复。''')
]


def main():
    provenance = []
    ledger = []
    handbook = Path('/Users/chiye/Downloads/DeviceTruth_工作手册_v2.0/DeviceTruth_工作手册_v2.0_真实数据驱动版.md')
    handbook_hash = hashlib.sha256(handbook.read_bytes()).hexdigest()
    # Resolve all original inputs before writing any managed files.
    originals = {}
    for spec in SPECS:
        original = spec['root'] / spec['id']
        originals[spec['id']] = [(f, f.read_bytes()) for f in sorted(original.rglob('*')) if f.is_file()]
    for spec in [*SPECS, *LEGACY_SPECS]:
        target = ROOT / 'skills' / spec['id']
        refs = target / 'references'
        refs.mkdir(parents=True, exist_ok=True)
        source_files = []
        origin_bytes = dict(originals.get(spec['id'], []))
        for file, content in origin_bytes.items():
            source_files.append(dict(path=str(file.relative_to(SOURCE)), sha256=hashlib.sha256(content).hexdigest()))
        for name in spec['refs'] if 'root' in spec else []:
            content = origin_bytes[spec['root'] / spec['id'] / 'references' / name].decode('utf-8')
            if name in {'evidence-semantics.md', 'synthetic-dataset-usage.md'}:
                content = EVIDENCE
            if name == 'source-notes.md':
                content = '# 原包来源编号\n\n原包 K/KS 编号是开发来源线索，尚非已入库文档或当前 Run 引用。厂商目录、配置控制、模块信息的适用性需由实际成功检索核对；社区经验仅作候选方向。完整文件出处与 SHA256 见 docs/domain-skill-sources.json。\n'
            (refs / name).write_text(content, encoding='utf-8')
        contract = CONTRACT
        if spec['id'] == 'carrier-rtu-evidence-triage':
            contract = '\n'.join(line for line in CONTRACT.split('\n') if not line.startswith('| get_observations |'))
        (refs / 'runtime-contract.md').write_text(contract, encoding='utf-8')
        (refs / 'evidence-guidance.md').write_text(EVIDENCE, encoding='utf-8')
        data = spec.get('data', DATA_EV if spec['id'] == 'automotive-diagnosis' else DATA_IND)
        (refs / 'data-access.md').write_text(data, encoding='utf-8')
        tools = [t for t in TOOLS if t != 'get_observations' or spec['id'] != 'carrier-rtu-evidence-triage']
        rules = render_standard_skill(target, {**spec, 'tools': tools})
        for rule in rules:
            if rule['rule_id'] == 'M-01':
                source_path, source_hash = str(handbook), handbook_hash
                status = 'PROJECT_POLICY; text checked; not manufacturer specification'
            elif 'root' in spec:
                file = spec['root'] / spec['id'] / 'references' / rule['source']
                source_path = str(file.relative_to(SOURCE))
                source_hash = hashlib.sha256(origin_bytes[file]).hexdigest()
                status = 'ORIGINAL_GUIDANCE; source pointer checked; professional review pending'
            else:
                file = refs / rule['source']
                source_path = str(file.relative_to(ROOT))
                source_hash = hashlib.sha256(file.read_bytes()).hexdigest()
                status = 'LEGACY_SIMULATED_GUIDANCE; professional review pending'
            ledger.append(dict(skill_id=spec['id'], version=spec['version'], rule_id=rule['rule_id'],
                               rule_type=rule['kind'], source_path=source_path, source_section=rule['section'],
                               source_sha256=source_hash, scope=rule['scope'], review_status=status, reviewer='UNASSIGNED'))
        if 'root' in spec:
            provenance.append(dict(skill_id=spec['id'], adapted_version=spec['version'], original_files=source_files,
                                   copied_matrices=[n for n in spec['refs'] if n not in STANDARD_REFERENCES],
                                   replaced=['frontmatter', 'runtime contract', 'data mapping', 'evidence semantics', 'five handbook reference categories'],
                                   omitted=['original output JSON schemas/templates (not supported by product runtime)']))
    (ROOT / 'docs/domain-skill-sources.json').write_text(json.dumps(provenance, ensure_ascii=False, indent=2) + '\n')
    with (ROOT / 'docs/skill-rule-sources.csv').open('w', encoding='utf-8', newline='') as file:
        writer = csv.DictWriter(file, fieldnames=list(ledger[0])); writer.writeheader(); writer.writerows(ledger)
    print('Normalized', len(SPECS) + len(LEGACY_SPECS), 'product Skills; desktop originals unchanged')


if __name__ == '__main__':
    main()
