"""Render the handbook's five reference categories without embedding device facts."""
from pathlib import Path

STANDARD_REFERENCES = {
    'domain-knowledge.md': '设备边界、术语与资料适用性',
    'investigation-guide.md': '按现象选择调查分支与下一条区别性证据',
    'evidence-requirements.md': '判断所需证据、缺证行为与现场核验条件',
    'decision-rules.md': '规则类型、来源、适用范围与例外',
    'safety-guidelines.md': '危险征兆、人工移交和回答引用要求',
}

# Each tuple describes a reusable distinction, not an expected answer or asset.
PROFILES = {
    'ev-charging-triage': dict(
        scope='充电口、车桩连接和授权、预约与限值；不承担动力电池安全评估或日常耗材保养。',
        terms='交流与直流是不同充电路径；灯色、闪烁形态、充电阶段、车侧与桩侧提示分别记录。额定功率与本次实际功率分开。',
        branches=[('插枪未开始或中途停止', '同次车枪会话的灯色、灯态、状态、已有提示和维修史', '设置、授权、连接、车端与站端分别列候选'),
                  ('预约、充电上限或功率疑问', '设置的有效时点、当地时区、SOC、限值及工况', '旧设置或另一会话不能解释当前状态'),
                  ('处理后是否恢复', '操作记录、可比较的独立会话和验收窗口', '可用替代路径不等于原桩修复')],
        rules=[('charging-matrix.md', '充电调查矩阵', '充电异常初查', '灯色和AC/DC差异用于提出竞争解释，不单独确诊车载充电机。', '缺少同车同工况或适用手册时保持未知。'),
               ('charging-matrix.md', '充电调查矩阵', '设置与恢复调查', '预约和限值按设置与会话时点解释，处理动作和恢复验证分别判断。', '仅有历史快照时不能确认当前设置或恢复。')],
        hazard='烟雾、焦糊味、火花、异常发热或高压相关风险',
        prohibited='拆装高压部件、反复试充、绕过锁止或保护、改写控制参数'),
    'ev-maintenance-inspection': dict(
        scope='轮胎与胎压、制动、滤芯、雨刮的维护证据核对；充电和动力电池风险另属专项。',
        terms='轮位、冷胎条件、标签目标与量具是胎压比较的前提；维护到期、检查、批准、更换和验收是不同状态。',
        branches=[('胎压灯、压力或胎纹变化', '轮位、测量条件、单位、标签目标、轮胎规格与维护记录', '压力变化、TPMS告警和硬件损坏分开'),
                  ('制动或耗材是否到期', '实际投运或上次维护日期、里程、市场与适用维护说明', '年款和经验周期不能直接证明到期或失效'),
                  ('维护后是否改善', '前后记录、相同测量条件、客观判据与持续窗口', '主观反馈或单一轮位不能覆盖整车状态')],
        rules=[('maintenance-matrix.md', '维护证据与验收', '轮胎与胎压', '胎压必须按轮位、单位和条件比较；告警形态不能唯一定位传感器损坏。', '车型标签或测量条件未知时不指定统一目标。'),
               ('maintenance-matrix.md', '维护证据与验收', '维护与验收', '检查、更换及效果验证分别取证，使用条件和市场适用性需单独确认。', '仅有维护计划或主观改善时保留未验证。')],
        hazard='制动能力异常、轮胎损伤或影响驾驶可见度的故障',
        prohibited='危险道路验证、轮胎状态未确认时行车刷新、无规程的制动维修'),
    'ev-powertrain-safety-investigation': dict(
        scope='低压供电、ICCU告警、动力受限、电池热管理和召回适用性；不是高压维修规程。',
        terms='车型公开范围、单车VIN适用性、召回措施执行与故障根因分别判断；表显续航不等于认可检测的容量健康率。',
        branches=[('无法唤醒或低压告警', '原始提示、额定电压、负载与测量条件、访问状态及时间', '旧APP信息或单个电压不能定位电池损坏'),
                  ('ICCU、动力受限或召回', '市场、生产范围、单车适用性、活动号、新旧措施与执行回执', '模拟查询和公开车型清单不证明真实VIN命中'),
                  ('温度、续航或健康疑问', '实际测量、SOC、环境、能耗和认可检测方法', '告警、能耗变化与容量衰退保持竞争解释')],
        rules=[('supply-recall-matrix.md', '供电、召回与健康的区分', '召回与维修记录', '车型范围、单车适用性、措施执行和本次故障分别判断。', '没有真实适用性查询或执行回执时不确认召回或维修。'),
               ('supply-recall-matrix.md', '供电、召回与健康的区分', '供电与健康调查', '低压读数、APP状态、温度提示或续航下降不单独证明某部件损坏。', '额定、测量条件或认可检测缺失时不计算健康率。')],
        hazard='动力明显受限、烟雾、事故浸水、异常温升或电池安全征兆',
        prohibited='开电池包、短接、换高压保险、绕过互锁、刷写或远程复位'),
    'industrial-configuration-investigation': dict(
        scope='控制器和关键部件的型号、固件、技改、更换和采购前核对；不据配置差异确诊故障。',
        terms='出厂BOM、工程期望配置、批准变更、实施声明和实物核验是不同证据层次；兼容性还涉及修订、接口与适用版本。',
        branches=[('旧工单与更换记录不一致', '同一资产、部位、属性的有效区间、来源、前后值与核实状态', '时间变化、对象误配、旧模板与真实冲突分别处理'),
                  ('当前实际装机未知', '铭牌或在线身份的对象绑定、采集时点和核实记录', '登记值或批准记录不能升级为现场事实'),
                  ('采购与备件兼容', '硬件修订、固件、机械电气接口、协议与厂商适用资料', '型号相近或库存可用不等于兼容')],
        rules=[('configuration-investigation.md', '工程、在线与实际装机', '装机与配置比较', '工程配置、在线模块身份和实物装机分别判断；先排除对象与槽位不匹配。', '配置控制模式、身份或时点不明时不裁决装机。'),
               ('compatibility-guide.md', '全文', '采购前适用性核对', '按实际硬件、固件、接口和厂商兼容条件核对，不能凭型号近似批准替代。', '没有适用兼容资料时保留未知，交人工核验。')],
        hazard='带电检查、机械储能、安全回路或控制器更换相关作业',
        prohibited='带电拆装、接线改造、固件写入、绕过保护或远程批准采购替代'),
    'industrial-controller-fault-triage': dict(
        scope='PLC停机、指示灯、远程I/O与PROFINET通信、诊断结果；具体指令条件必须核对系列和固件。',
        terms='工程期望名、在线名、物理身份、链路与过程数据交换分别取证；设备指令部分结果与API分页部分返回不是同一覆盖范围。',
        branches=[('更换后无I/O或画面不更新', '工程期望名、在线名、身份绑定、链路、数据交换和维护前后值', '可扫描、RUN与过程数据正常分开'),
                  ('STOP或ERROR指示', '厂商系列、固件、灯位与形态、具体告警和适用说明', '计划停机、启动配置与硬件损坏保留竞争解释'),
                  ('诊断失败或部分结果', '指令、目标、参数模式、返回码、有效位、覆盖与时间窗口', '调用失败、无权限和未覆盖都不是模块损坏证据')],
        rules=[('profinet-investigation.md', '全文', '远程I/O更换后调查', '比较工程期望名、在线身份、链路与数据交换，不以RUN或可扫描代替通信验证。', '名称已一致而交换仍异常时调整假设，不坚持名称根因。'),
               ('diagnostic-data-semantics.md', '诊断调用、覆盖与时间', '诊断结果解释', '指令参数错误、数据覆盖不足和设备故障分别解释。', '具体返回码含义必须先获得适用官方原文，不由Skill充当引用。')],
        hazard='危险机械、电气、安全PLC或保护回路相关现场',
        prohibited='远程命名、停机、强制复位、下载、参数写入或旁路保护'),
    'carrier-rtu-evidence-triage': dict(
        scope='已核对适用性的Carrier 48TF系列屋顶机组登记、工单、过滤器变更与性能证据；不推广到其他型号或品牌。',
        terms='型号后缀、序列号和地点用于对象关联；系列产品册不是本机BOM。MERV等级、滤器尺寸、阻力与风量是不同指标。',
        branches=[('登记身份或日期不同', '型号后缀、序列号、地点、原始时间口径和文档版本', '相同标识支持文档关联，不证明现场核实'),
                  ('工单写完工或PASSED', '原文、状态字段定义、实施附件、真实执行时间与验收记录', '流程状态、字段文本、实施和性能合格分开'),
                  ('过滤器改造或制冷供热问题', '适用手册、滤器规格与阻力条件、设计风量、同条件测量', '缺少测量时保持性能未知，不靠工单标题确认')],
        rules=[('decision-rules.md', '判断规则及来源', '过滤器适用性', 'MERV等级不等于尺寸或性能验收；风量合格需要适用判据和同条件测量。', '无设备后缀、滤器曲线或测量条件时不确认适配。'),
               ('evidence-requirements.md', '证据需求', '维修闭环', 'Completed或PASSED等文本不能单独证明实际执行和独立验收。', '字段口径或版本冲突需由来源方说明，不能猜测统一含义。')],
        hazard='燃气气味、烟雾、异常燃烧、疑似CO、严重电气异常或人员不适',
        prohibited='重启试机、燃气或电气操作、旁路保护、远程性能验收'),
    'automotive-diagnosis': dict(
        scope='汽车充电与车载通信的综合线索、故障码和历史核对；已有专用充电、维护或电池风险任务优先对应专项。',
        terms='故障码是带对象与时间的观测线索；历史报码、手册中的通用含义和当前实车故障不是同一事实。',
        branches=[('充电与通信线索同时出现', '车辆与控制器身份、版本、原始提示、发生阶段和维护历史', '跨系统相关性不能直接确定因果'),
                  ('通信超时或故障码', '拓扑、参与节点、报码时点、是否当前复现与适用厂商说明', '历史报码不能替代当前观测'),
                  ('配置或维修影响', '同对象版本与更换记录、执行和独立复测', '版本不一致不等于必须升级或部件损坏')],
        rules=[('diagnostic-codes.md', '通信超时类', '车载通信与报码', '先识别节点、对象和报码时间，再按适用说明形成候选解释。', '故障码原文或适用版本缺失时不解释为确定根因。'),
               ('charging-troubleshooting.md', '全文', '充电与维修调查', '历史配置、维修动作和恢复分别取证，手册阈值不作为本车实测值。', '模拟示例与型号词只作为回归指引，不外推真实适用性。')],
        hazard='高压、互锁、热失控、烟雾或车辆危险状态',
        prohibited='高压上电、绝缘检测流程、动力电池拆装、跳过保护或自行刷写'),
    'factory-equipment': dict(
        scope='产线PLC、变频器、伺服和输送单元停机、节拍异常的综合调查；具体通信故障和采购配置另属专项。',
        terms='停机范围、节拍变化、台账配置、实际现场值与更换回执分别核对；不同系列经验值不能直接互换。',
        branches=[('单机、单元或整线停机', '对象、发生时刻、报警、是否可复现、近期维护与工况', '报警和计划停机、保护动作、硬件故障分开'),
                  ('节拍或驱动性能异常', '同工况的前后记录、负载、版本和适用规格', '主观变慢或不同设备比较不能证明某个驱动器损坏'),
                  ('固件或控制器更换', '部位、属性、有效期、实施和现场核验', '台账记录、模板声明和实物装机分别表达')],
        rules=[('controller-maintenance.md', '四类记录各自能证明什么', '配置与维护调查', '出厂、模板、更换声明和现场核验分别解释，不由较新录入时间裁决。', '模拟回归资料不能作为真实设备身份或性能证据。'),
               ('safety-notes.md', '调试与试运行', '停机与运行恢复', '停机原因与安全条件未知时不建议远程复位或重启。', '现场验证由具备资质的人员按厂内流程进行。')],
        hazard='带电检修、机械储能、安全回路或人员处于危险区域',
        prohibited='远程复位试车、参数写入、固件回退、控制器拆换或安全回路旁路'),
}

LEGACY_SPECS = [
    dict(id='automotive-diagnosis', version='1.0.1', title='汽车充电与车载通信综合调查',
         description='调查汽车充电与车载通信的交叉线索、故障码和维修历史。专门的充电、日常维护或电池风险问题优先对应专项；本包不给出实车确诊。',
         devices=['纯电乘用车', '交直流充电设备', '车载网关与控制器'],
         tasks=['充电中断', '通信超时', '故障码解读'],
         refs=['charging-troubleshooting.md', 'diagnostic-codes.md'],
         body='按当前用户目标选择充电或通信分支，核对同一车辆、部位、版本和时点。先取得有关设备记录，再按需检索适用说明；不要求每次走完全部工具。已有资料能查到的信息由助手读取，不转成用户必须输入的内部字段。报码、历史维修和配置差异只提供候选解释；需要现场核验时说明对象、缺口与专业责任。'),
    dict(id='factory-equipment', version='1.0.1', title='工厂设备停机与节拍综合调查',
         description='调查产线PLC、变频器、伺服和输送单元停机、节拍异常的跨部件线索。具体通信故障或采购配置优先对应专项，区分台账声明与现场事实。',
         devices=['PLC控制柜', '变频器与伺服驱动', '产线输送单元'],
         tasks=['设备停机', '节拍异常', '控制器固件核对'],
         refs=['controller-maintenance.md', 'safety-notes.md'],
         body='根据停机范围、节拍变化、工况和用户目标选择下一条区别性证据，不强制执行固定步骤。get_asset确认登记部位，get_configuration按精确attribute查时点配置，get_maintenance_history核对更换与前后值；必要时再读取完整signal的模拟观测。不得把一次空查询解释为整机没有数据，也不得把台账值、工程模板或更换声明当作已现场确认。'),
]


def render_standard_skill(target: Path, spec: dict) -> list[dict]:
    """Write actual instructions; rule rows are returned for the development ledger."""
    profile = PROFILES[spec['id']]
    refs = target / 'references'
    refs.mkdir(parents=True, exist_ok=True)
    supplemental = [name for name in spec['refs'] if name not in STANDARD_REFERENCES]
    extra = '\n'.join(f'- [{name}]({name})：涉及该分支时查阅，来源线索不等于本Run引用。' for name in supplemental)
    (refs / 'domain-knowledge.md').write_text(
        f"# 领域与设备范围\n\n{profile['scope']}\n\n## 术语与关系\n\n{profile['terms']}\n\n"
        '## 资料适用性\n\n核对厂商、完整型号、市场、硬件/固件与文档版本后使用技术条件。'
        '本包不存储某台设备事实，不给所有设备指定统一阈值；需引用的手册原文应由知识工具取得。'
        '原包规则的来源指针见decision-rules.md；未取得原文及专业复核时不称厂商硬条件。\n\n'
        + (f'## 补充调查资料\n\n{extra}\n' if extra else ''), encoding='utf-8')

    table = '\n'.join(f'| {trigger} | {check} | {limit} |' for trigger, check, limit in profile['branches'])
    (refs / 'investigation-guide.md').write_text(
        '# 动态调查指南\n\n根据用户目标、已取得证据与关键缺口选择分支；不是必须逐项执行的流程。'
        '没有唯一对象时先追问身份，不能按相似症状选任意资产。\n\n'
        '| 触发线索 | 优先取得的区别性证据 | 判断边界 |\n|---|---|---|\n' + table + '\n\n'
        '已有关联设备时先用可用记录回答用户的记录核对请求；技术阈值或适用性需要原文时再检索。'
        '先查[data-access.md](data-access.md)了解候选字段，精确参数和一次Run一个Skill见[runtime-contract.md](runtime-contract.md)。'
        '一次NO_MATCHING_DATA只说明本次条件未匹配。配置错误不可重试时说明缺口，继续利用可读记录。\n\n'
        '补答后只更新新增证据支持的部分；历史恢复窗口、当前异常与复发分别判断。'
        '最关键缺口影响结论时追问一个自然语言问题，不让用户填写工具参数或Skill名称。\n', encoding='utf-8')

    evidence = '\n'.join(
        f'| {trigger} | {check}；对象、来源和业务时点 | 涉及技术阈值/适用性时取得相应型号与版本的原文 | 对象绑定、同条件测量或适用原文可能未返回 | {limit}；缺证保留未知，必要时追问 | M-01、D-01/D-02 |'
        for trigger, check, limit in profile['branches'])
    (refs / 'evidence-requirements.md').write_text(
        '# 证据要求\n\n| 判断类型 | 需要的业务事实 | 需要的技术原文 | 可能缺失的证据 | 不足时行为 | 规则ID |\n'
        '|---|---|---|---|---|---|\n' + evidence + '\n\n'
        '## 来源和现场核验\n\n'
        'AUTHORIZED是使用授权，不等于FIELD_VERIFIED。DOCUMENTED只支持资料记载；'
        'SIMULATED和GOOD质量标记不能升级为真实现场核验。批准、执行、测量与独立验收分开。'
        '空维修查询不证明从未维修，未查到告警不证明设备正常。时间与引用边界详见'
        '[evidence-guidance.md](evidence-guidance.md)。\n', encoding='utf-8')

    rules = [dict(rule_id='M-01', kind='项目约束', source='HANDBOOK §1/§5.3/§5.4',
                  section='设备事实、技术原文与调查方法分层', scope=profile['scope'],
                  text='设备事实从当前Run受控工具取得；原文只解释适用技术条件，不能裁决当前装机或已修复。',
                  exception='缺少可核实来源时保留未知，不能由Skill补造事实。')]
    for i, (file, section, scope, rule, exception) in enumerate(profile['rules'], 1):
        rules.append(dict(rule_id=f'D-{i:02}', kind='调查建议（沿用原包，待专业复核）',
                          source=file, section=section, scope=scope, text=rule, exception=exception))
    rule_table = '\n'.join(f"| {v['rule_id']} | {v['kind']} | {v['source']} / {v['section']} | {v['scope']} | {v['text']} | {v['exception']} | 未指定；待专业复核 |" for v in rules)
    (refs / 'decision-rules.md').write_text(
        '# 领域规则与适用条件\n\n'
        '| 规则ID | 类型 | 来源与章节 | 适用范围 | 规则内容 | 例外条件 | 复核人 |\n|---|---|---|---|---|---|---|\n'
        + rule_table + '\n\n'
        'HANDBOOK为项目工作手册；D类来源文件指原包中的同名参考文件，文件摘要与原始路径登记在开发台账'
        'docs/skill-rule-sources.csv。此表只记录调查方法，不声称已完成厂商原文或专业人员复核。'
        '适用性和数值条件仍需成功检索取得的原文；文件名、规则ID和原包sourceId都不是answer referenceId。\n', encoding='utf-8')

    (refs / 'safety-guidelines.md').write_text(
        '# 安全边界与移交\n\n'
        '| 风险情形 | 来源/章节 | 禁止动作或限制 | 核验责任 | 回答要求 |\n|---|---|---|---|---|\n'
        f"| {profile['hazard']} | HANDBOOK §5.4；项目只读边界 | {profile['prohibited']} | 现场具备资质的人员及现场应急流程 | 优先安全移交，不以补证或试机拖延 |\n"
        '| 当前安全条件、身份或资质未知 | HANDBOOK §5.3/§5.4 | 不给实体设备操作步骤或替代作业许可 | 设备责任人与合格专业人员 | 明确缺口和现场核验范围 |\n\n'
        '## 引用与表达\n\n'
        '事实性断言使用本Run工具返回的referenceId，带来源、版本、部位与时点；SUPPORTED/CONFIRMED不能空引用。'
        '文档标题与章节按真实返回值展示，不编造页码或工单号。Skill本身只提供调查方法。'
        '回答区分记录支持的事实、候选解释、证据缺口、建议和安全限制；保留模拟与文档快照属性。'
        '未经服务器核验不宣称当前装机、确定根因、已执行维修或性能已恢复。\n', encoding='utf-8')

    manifest = [f"name: {spec['id']}", f"description: {spec['description']}", f"version: {spec['version']}"]
    for key, values in [('devices', spec['devices']), ('tasks', spec['tasks']), ('tools', spec['tools']),
                        ('evidence', ['本Run受控工具取得的对象、来源和时点记录', '适用型号与版本的技术原文；模拟数据不作现场核验']),
                        ('risk', ['危险现场作业交由具备资质的人员按现场规程处理', '安全条件未知时不提供实体设备操作步骤']),
                        ('citation', ['关键事实绑定本Run返回的referenceId及实际来源', 'Skill规则和原包来源编号不能冒充回答引用'])]:
        manifest.extend([key + ':', *['  - ' + value for value in values]])
    entry_links = '\n'.join(f'- [{name}](references/{name})：{purpose}。' for name, purpose in STANDARD_REFERENCES.items())
    body = (f"# 调查目标\n\n{spec['title']}。{profile['scope']}\n\n"
            '## 调查原则\n\n' + spec['body'] + '\n\n'
            '工具参数、输出协议、一次Run一个Skill的边界见[runtime-contract.md](references/runtime-contract.md)；'
            '候选字段见[data-access.md](references/data-access.md)，实际部位以工具返回为准。'
            '涉及字段选择时按需读取；不把候选字段当成每台设备都具有的数据。\n\n'
            '## 按需参考\n\n' + entry_links + '\n\n'
            '## 结果要求\n\n'
            '按用户问题说明记录支持的事实、候选原因、缺口、下一步和安全限制，关键断言挂本Run引用。'
            '历史快照不当实时状态，维修声明不当已验收恢复，未取得适用原文不自造阈值。'
            '本包是领域指引材料，不能改变系统协议、注册工具或扩大权限。\n')
    (target / 'SKILL.md').write_text('---\n' + '\n'.join(manifest) + '\n---\n\n' + body, encoding='utf-8')
    return rules
