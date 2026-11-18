/* DeviceTruth V0.3 – simulated UI interactions. NO network or persistence. */
(() => {
  'use strict';
  const $ = (selector, root = document) => root.querySelector(selector);
  const $$ = (selector, root = document) => Array.from(root.querySelectorAll(selector));
  const focus = [
    {
      title: '型号与配置记录冲突',
      description: '用户口述的控制器型号为 D-120A，设备登记资料记录为 D-120B。两者存在冲突，但不能据此确认现场当前安装型号。',
      badges: [['资料冲突', 'amber'], ['现场未确认', 'gray']],
      facts: [
        ['用户口述', 'D-120A', '来源：任务描述 · 尚未由现场照片支持', '未核实', 'amber'],
        ['登记资料', 'D-120B', '来源：设备配置记录 · 需核对生效时间', '文档记载', '']
      ],
      steps: ['由具备资质的人员核实控制器铭牌、设备实物照片和当前固件版本。', '核对维护记录与配置资料的适用设备、变更日期及来源。'],
      placeholder: '围绕型号冲突继续提问，例如：请核对型号记录的生效时间与维修变更关系...'
    },
    {
      title: '诊断缓冲区与报警',
      description: '当前已知症状为 ERROR 指示灯闪烁，但尚未取得经过核实的报警代码及诊断缓冲区数据，不能直接把该现象归因为特定故障。',
      badges: [['诊断数据缺失', 'amber'], ['待补现场读数', 'gray']],
      facts: [
        ['用户描述', 'ERROR 闪烁', '来源：任务描述 · 未包含现场照片或采集日志', '未核实', 'amber'],
        ['诊断缓冲区', '未取得', '来源：当前示例资料 · 无法核实报警码', '资料缺口', '']
      ],
      steps: ['在符合安全规程的前提下，请合格人员读取 PLC 诊断缓冲区与报警信息。', '补充报警发生时间、运行状态及报警截图，并核对手册对应版本。'],
      placeholder: '围绕报警现象继续提问，例如：怎样确认需要采集哪些诊断缓冲区字段...'
    },
    {
      title: '维护手册与维修历史',
      description: '现有维修历史与技术资料尚未证明适用于现场当前控制器及固件版本。建议先核实设备实际版本，再匹配手册和变更记录。',
      badges: [['适用性待核验', 'amber'], ['版本未知', 'gray']],
      facts: [
        ['维修历史', '有更换线索', '来源：历史变更记录 · 不等于当前安装状态', '待核对', 'amber'],
        ['维护手册', '版本待匹配', '来源：当前示例资料 · 尚未确认适用版本', '待匹配', '']
      ],
      steps: ['收集维修单的部件编号、实际完成日期以及更换后核验记录。', '依据现场铭牌和固件版本匹配正确维护手册，再判断历史建议是否适用。'],
      placeholder: '围绕维修历史继续提问，例如：现有维修单能否证明实际已完成更换...'
    }
  ];
  let selectedFocus = 0;
  let composeMode = 'investigate';
  let lastModalTrigger = null;
  let toastTimer;
  function createIcon(id, className = 'icon sm') {
    const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
    svg.setAttribute('class', className);
    const use = document.createElementNS('http://www.w3.org/2000/svg', 'use');
    use.setAttribute('href', '#i-' + id);
    svg.appendChild(use);return svg;
  }
  function flash(message) {
    const node = $('#toast');
    node.textContent = message;
    node.classList.add('show');clearTimeout(toastTimer);
    toastTimer = setTimeout(() => node.classList.remove('show'), 4300);
  }
  function dialog(title, body) {
    lastModalTrigger = document.activeElement;
    $('#modal-title').textContent = title;
    $('#modal-body').textContent = body;
    $('#overlay').classList.add('show');
    $('#modal-close').focus();
  }
  function closeDialog() {
    $('#overlay').classList.remove('show');
    if(lastModalTrigger && typeof lastModalTrigger.focus === 'function')lastModalTrigger.focus();
  }
  function setView(view) {
    $$('.view').forEach(node => node.classList.toggle('active', node.id === view));
    $$('.previewtab').forEach(node => node.classList.toggle('active', node.dataset.view === view));
    location.hash = view;
    window.scrollTo({top: 0, behavior: 'instant'});
  }
  function appendFact(info) {
    const [label, value, desc, flag, variant] = info;
    const card = document.createElement('div'); card.className = 'fact';
    const head = document.createElement('div'); head.className = 'flabel';head.textContent = label;
    const mark = document.createElement('span');mark.className = 'flag ' + variant;mark.textContent = flag;head.appendChild(mark);
    const v = document.createElement('div');v.className = 'fvalue';v.textContent = value;
    const d = document.createElement('div');d.className = 'fdesc';d.textContent = desc;
    card.append(head,v,d);return card;
  }
  function setFocus(index) {
    selectedFocus = index;
    const item = focus[index];
    $$('.focus-item').forEach((button,i) => button.classList.toggle('active', i===index));
    $('#focus-index').textContent = String(index+1).padStart(2,'0')+' / 03';
    $('#focus-title').textContent = item.title;
    $('#focus-desc').textContent = item.description;
    $('#focus-badges').replaceChildren(...item.badges.map(([name,variant])=>{
      const el=document.createElement('span');el.className='tag '+variant;el.textContent=name;return el;
    }));
    $('#facts-grid').replaceChildren(...item.facts.map(appendFact));
    $('#next-list').replaceChildren(...item.steps.map((s,i)=>{
      const li=document.createElement('li'),number=document.createElement('span'),text=document.createElement('span');
      number.className='stepcheck';number.textContent=i+1;text.textContent=s;li.append(number,text);return li;
    }));
    if(composeMode==='investigate')$('#pro-input').placeholder=item.placeholder;
  }
  function changeComposeMode(mode) {
    composeMode=mode;
    $$('.composer-mode').forEach(button=>button.classList.toggle('active',button.dataset.composeMode===mode));
    const ta=$('#pro-input');
    ta.placeholder=mode==='observe'?'描述现场新增观察，如铭牌文字、报警读数及获取时间；提交后仍视为未核实信息...':focus[selectedFocus].placeholder;
    $('#pro-send').lastChild.textContent=mode==='observe'?'提交观察':'提交调查';
  }
  function submitPro() {
    const field=$('#pro-input');const value=field.value.trim();
    if(!value){flash('请先输入调查内容或现场观察信息。');field.focus();return;}
    const label=composeMode==='observe'?'现场观察（未核实）':'调查请求';
    const attached=$('#pro-file').files?.[0];
    const display=$('#pro-submitted');display.textContent='演示已记录 '+label+'：'+value.slice(0,75)+(value.length>75?'…':'')+(attached?' · 附件：'+attached.name:'')+'。未提交到后端，未创建新 Run。';display.classList.add('show');field.value='';
    if(attached){$('#pro-file').value='';$('#pro-attachment').textContent='';}
    flash('已在原型中显示提交状态；实际接入后才会发起 Agent 请求。');
  }
  const evidence = {
    configuration:['设备配置记录 · 示例','资料类型：配置记录 / DOCUMENTED\n示例摘录：登记型号 D-120B\n适用性：需核对设备标识、记录生效时间和变更历史。\n证据限制：配置记录只能说明资料内容，不能单独证明目前安装部件。\n\n真实接入：显示后端返回的 Reference ID、来源、引用片段、有效时间和打开原文入口。'],
    maintenance:['维修变更记录 · 示例','资料类型：历史维修记录\n示例线索：可能存在控制器更换记录；本原型不提供真实工单 ID。\n适用性：需核对更换是否实施、变更日期、工单验收与当前设备。\n证据限制：历史工单并不自动证明当前现场状态。\n\n真实接入：关联维护记录 API，保留证据时间与来源性质。'],
    observation:['用户与现场观察 · 示例','资料类型：用户描述 / 未核实观察\n现有口述：D-120A、ERROR 指示灯闪烁。\n当前缺口：现场铭牌、采集时间、带来源的照片、固件和报警数据。\n证据限制：不应把用户口述或模拟观测直接写成已核实现场事实。\n\n真实接入：用户授权后附加观察记录，与权威事实分层展示。']
  };
  function proNavigation(target) {
    if(target==='workspace')return flash('已位于诊断工作台。');
    if(target==='records')return dialog('设备资料 · 页面映射','设计稿中该入口应展示设备资料、维修记录和配置变更历史。\n\n本原型不读取真实设备数据。正式实现请核对当前 Asset / Configuration / Maintenance 接口，不要仅弹出提示代替页面。');
    if(target==='events') {$('#pro-timeline').open=true;$('#pro-timeline').scrollIntoView({block:'center',behavior:'smooth'});return;}
    if(target==='report')return dialog('报告与归档 · 页面映射','目标页面：完整调查报告、关联 Run、引用证据、限制说明、审核状态及导出入口。\n\n本原型只呈现交互方向，尚无正式报告导出功能。正式落地请复用已有 AgentResult / Run 结构，并对写入与导出做权限校验。');
  }
  const adminModules={
    users:['用户与权限','目标包含账号、角色、组织/租户边界、授权记录与权限审计。当前设计仅定义页面入口，不能绕过服务端身份校验直接增加后台写操作。'],
    runs:['Agent 运行记录','目标包含跨会话的 Run 列表、状态筛选、耗时、错误事件、Trace 与权限范围内的调查详情。真实的服务端分页与聚合 API 需在当前项目中核查。'],
    knowledge:['知识资料管理','目标包含导入、分组、索引状态、资料版本、引用适用时间、发布审核与失效标记。已有检索能力不代表已实现网页发布、撤回和权限隔离。'],
    skills:['Skill 管理','目标包含 Skill Registry、安装与版本、授权范围、启用/停用、执行日志与审计。当前仅规划工作台，不演示真实启停行为。'],
    assets:['设备业务数据','目标包含设备资产、配置版本、维修记录、证据来源和时点状态。请以项目实际数据库表与 API 为准，不要据本图虚构字段和数据。'],
    audit:['模型、权限与审计','目标包含模型路由、配额、用量统计、服务健康、访问日志与审计日志。缺失的 Token usage 不得当作 0；管理员写操作必须服务端鉴权。']
  };
  function setAdminNav(target) {
    $$('#admin [data-admin-nav]').forEach(btn=>btn.classList.toggle('active',btn.dataset.adminNav===target));
    const overview=target==='overview';
    $('#admin-overview').hidden=!overview;$('#admin-module').hidden=overview;
    const title=overview?'运营总览':adminModules[target][0];
    $('#admin-title').textContent=title;$('#admin-crumb').textContent=title;
    $('#admin-desc').textContent=overview?'从 Run 记录、知识与 Skill 数据底座出发，建设可审计的运营管理能力。':'模块结构展示 · 实际页面与管理 API 尚需按项目现状实现。';
    if(!overview){$('#admin-module-title').textContent=title;$('#admin-module-desc').textContent=adminModules[target][1];}
    window.scrollTo({top:0,behavior:'smooth'});
  }
  function consumerNav(target){
    if(target==='chat')return flash('已位于智能诊断。');
    if(target==='equipment')return dialog('我的设备 · 页面映射','建议展示用户主动登记的设备及历史维修记录，并区分公共示例与用户私有数据。\n\n真实设备新增、修改和读取操作需要完善身份认证与数据授权。');
    if(target==='history')return dialog('历史记录 · 页面映射','建议展示会话时间、设备种类、当前阶段、待补充问题和详细 Run。\n\n本原型仅显示示例会话；真实记录需要调用会话 API。');
  }
  function sendConsumer(){
    const field=$('#consumer-input'),value=field.value.trim();
    if(!value){flash('请先描述问题或选择一条追问答案。');field.focus();return;}
    $('#consumer-user-message').firstChild.textContent=value;
    const display=$('#consumer-submitted');display.textContent='演示已更新提问：'+value.slice(0,85)+(value.length>85?'…':'')+'。未向实际 Agent 发送。';display.classList.add('show');field.value='';flash('已更新示例对话。当前只是 UI 演示，没有生成新的模型回答。');
  }
  function handleAction(action){
    const lookup={
      'new-pro':['新建检修任务 · 交互边界','目标流程：创建新 Conversation/Run → 选择设备和任务 → 新 Run 按授权上下文执行。\n\n在正式系统中，应保留现有 Run 与证据来源；本原型不会真正创建任务。'],
      'history':['历史 Run · 示例','Run #08 · 型号记录核验：阶段结论已生成 / 待现场核验。\nRun #07 · 历史资料检索：已完成（演示）。\n\n正式列表应展示每次 Run 的独立结果、时间、工具调用和 Reference；不能合并成虚构的持续状态。'],
      'case-current':['当前调查','已选择当前示例：SN-0245 · PLC 控制柜型号记录核验。'],
      'case-other':['历史调查 · 示例','这是仅用于导航设计的另一条演示会话。正式页面应该从后端读取对应会话、Run 和授权的证据。'],
      'evidence-all':['证据说明与可信状态','当前三类演示证据分别是：配置记录（文档记载）、历史维修记录（待核实生效情况）、用户口述（未现场确认）。\n\n它们不能通过“多数投票”或统一置信度自动转为真实配置。每条正式引用需要保留可追溯的 Reference ID、来源性质和有效时点。'],
      'consumer-new':['开启新咨询','目标：创建新会话并清空本地展示。此处仅显示演示说明，不会覆盖旧会话数据。'],
      'consumer-case':['当前演示会话','已选中新能源汽车充电中断示例。正式使用时需要从服务端读取该会话。'],
      'consumer-other':['历史演示会话','该示例仅代表导航设计，不会伪造历史消息。'],
      'consumer-sources':['参考资料说明','当前页面未连接真实知识检索，无法提供具备来源 ID 的引用。\n\n正式实现应展示实际检索来源、适用版本、引用片段及限制，而不是生成虚构参考链接。'],
      'admin-policy':['平台管理的授权边界','1. 管理员须通过服务端 RBAC / 组织权限校验。\n2. 用户私有资料不应默认暴露在运营报表中。\n3. 知识发布、Skill 启停和配额调整应记录操作人与审计日志。\n4. KPI 接口未就绪时应展示“未配置/未采集”，不得将缺失值当作 0。']
    };
    if(action==='file-pro')return $('#pro-file').click();
    const item=lookup[action];if(item)dialog(item[0],item[1]);
  }
  document.addEventListener('click', (ev)=>{
    const btn=ev.target.closest('button');if(!btn)return;
    if(btn.dataset.view){setView(btn.dataset.view);return;}
    if(btn.dataset.focus){setFocus(Number(btn.dataset.focus));return;}
    if(btn.dataset.composeMode){changeComposeMode(btn.dataset.composeMode);return;}
    if(btn.dataset.evidence){dialog(...evidence[btn.dataset.evidence]);return;}
    if(btn.dataset.proNav){proNavigation(btn.dataset.proNav);return;}
    if(btn.dataset.adminNav){setAdminNav(btn.dataset.adminNav);return;}
    if(btn.dataset.consumerNav){consumerNav(btn.dataset.consumerNav);return;}
    if(btn.dataset.device){$$('.device-choice').forEach(b=>b.classList.toggle('active',b===btn));flash('已切换演示设备类型；实际问题与答案需要对应领域知识支持。');return;}
    if(btn.dataset.answer){$('#consumer-input').value=btn.dataset.answer;$('#consumer-input').focus();return;}
    if(btn.dataset.action)handleAction(btn.dataset.action);
  });
  $('#pro-send').addEventListener('click',submitPro);
  $('#pro-input').addEventListener('keydown',(ev)=>{if(ev.key==='Enter'&&!ev.shiftKey&&!ev.isComposing){ev.preventDefault();submitPro();}});
  $('#pro-file').addEventListener('change',(ev)=>{$('#pro-attachment').textContent=ev.target.files?.[0]?.name||'';flash('附件仅在浏览器本地选择；当前演示不会上传文件。');});
  $('#consumer-form').addEventListener('submit',(ev)=>{ev.preventDefault();sendConsumer();});
  $('#consumer-input').addEventListener('keydown',(ev)=>{if(ev.key==='Enter'&&!ev.shiftKey&&!ev.isComposing){ev.preventDefault();sendConsumer();}});
  $('#modal-close').addEventListener('click',closeDialog);
  $('#modal-ok').addEventListener('click',closeDialog);
  $('#overlay').addEventListener('click',ev=>{if(ev.target===$('#overlay'))closeDialog();});
  document.addEventListener('keydown',(ev)=>{if(ev.key==='Escape')closeDialog();});
  const hash=location.hash.slice(1);
  if(['consumer','pro','admin'].includes(hash))setView(hash);
  else history.replaceState(null,'','#pro');
})();
