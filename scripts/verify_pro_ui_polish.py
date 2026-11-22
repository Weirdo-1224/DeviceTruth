"""UI 展示回归：协议替身隔离布局/交互，不代表真实模型或数据库验收。"""
from copy import deepcopy
from http.server import ThreadingHTTPServer
from threading import Thread
from pathlib import Path
import json
from playwright.sync_api import sync_playwright, expect
from pro_workbench_fixture import Handler, RUN1, REF_A, REF_B, REF_C, WAITING_QUESTION, message, events

OUT = Path(__file__).resolve().parent.parent / 'outputs/validation/pro-ui-final'
OUT.mkdir(parents=True, exist_ok=True)
server = ThreadingHTTPServer(('127.0.0.1', 0), Handler)
Thread(target=server.serve_forever, daemon=True).start()
base = f'http://127.0.0.1:{server.server_port}'
mode = 'new'
calls = []
checks = []
def check(condition, label):
    assert condition, label
    checks.append(label)

def sample():
    r = deepcopy(RUN1)
    r.update(investigatedAssetId='CAR-B', assetResolution='SELECTED', executionActorUserId='u')
    for i, claim in enumerate(r['result']['claims']):
        claim.update(kind='CAUSE' if i < 2 else 'OBSERVATION', relevance='DIRECT' if i < 2 else 'CONTEXT')
    if mode in ('running', 'waiting'):
        r.update(status='RUNNING' if mode == 'running' else 'WAITING_FOR_USER', result=None,
                 pendingQuestion=WAITING_QUESTION if mode == 'waiting' else None)
    return r

def route_api(route):
    path = route.request.url.split('/api/')[1].split('?')[0]
    calls.append((route.request.method, path, route.request.post_data))
    r = sample()
    conv = {'conversationId':'conv-1','version':5,'createdAt':'2026-11-22T07:00:00Z','updatedAt':'2026-11-22T07:00:00Z'}
    status = 200
    if path == 'auth/me': body = {'userId':'u','displayName':'检修工程师','email':'ui@example.test'}
    elif path == 'auth/csrf': body = {'headerName':'X-CSRF-TOKEN','token':'ui-test'}
    elif path == 'workspaces': body = [{'workspaceId':'w','kind':'TEAM' if mode in ('team','sidebar') else 'PERSONAL','role':'ADMIN','name':'个人工作空间'}]
    elif path.endswith('/members'): body = [{'userId':'u','displayName':'检修工程师'}]
    elif path == 'assets': body = ['CAR-B','SC0245B']
    elif path == 'conversations/summaries':
        item=dict(conv,title='充电中断调查',latestRunStatus=r['status'],activeRunCount=int(mode in ('running','waiting')),waitingRunCount=int(mode == 'waiting'),runCount=1,investigatedAssetId='CAR-B')
        items=[] if mode == 'new' else [item]
        if mode == 'sidebar': items=[dict(item,conversationId=f'sidebar-{i}',title=f'新能源汽车直流充电中断调查与绝缘检测记录核对 · {i+1}') for i in range(20)]
        body={'items':items, 'nextCursor':'sidebar-next' if mode == 'sidebar' else None}
    elif path == 'conversations/conv-1':
        runs=[r]
        messages=[dict(message('m1',1,'USER','充电中断调查','CAR-B'),actorUserId='u')]
        if mode == 'history':
            runs.append(dict(r,runId='run-live',status='RUNNING',result=None,goalMessageId='m-live'))
            messages.append(message('m-live',2,'USER','继续核查','CAR-B'))
        body={'conversation':conv,'messages':messages,'runs':runs}
    elif path.endswith('/discussions'): body={'items':[{'discussionId':'d1','version':1,'authorUserId':'u','authorName':'检修工程师','createdAt':'2026-11-22T07:00:00Z','content':'现场反馈充电中断，请核对记录。'}],'nextCursor':None}
    elif path.endswith('/events'): body = events('run-1')[:2] + [{'seq':3,'type':'RUN_PHASE','recordedAt':'2026-11-22T07:00:00Z','data':{'phase':'REVIEWING','reason':'ANSWER_REVIEW'}}]
    elif path.endswith('/references'): body = [REF_A, REF_B, REF_C]
    elif path == 'runs/run-1': body = r
    elif path == 'runs/run-live': body=dict(r,runId='run-live',status='RUNNING',result=None,goalMessageId='m-live')
    elif path.endswith('/stream'): return route.fulfill(status=200,content_type='text/event-stream',body=': connected\n\n')
    elif path.endswith('/messages'):
        body = dict(r,status='RUNNING',version=6,pendingQuestion=None,result=None); status=202
    else: body = {'code':'NOT_FOUND'}; status=404
    route.fulfill(status=status,content_type='application/json',body=json.dumps(body,ensure_ascii=False))

try:
    with sync_playwright() as p:
        browser = p.chromium.launch(channel='chrome',headless=True)
        page = browser.new_page(viewport={'width':1280,'height':720})
        errors=[]
        page.on('pageerror', lambda error: errors.append(str(error)))
        page.route('**/api/**',route_api)
        page.goto(base)
        expect(page.get_by_role('button',name='创建并开始调查')).to_be_visible()
        box=page.get_by_role('button',name='创建并开始调查').bounding_box()
        check(box['y']+box['height']<=720,'1280×720 创建主操作在首屏')
        check(page.locator('.dt-page-card').count()==0,'初始页没有重复空状态卡')
        check(page.locator('.dt-create-form .dt-device-field').bounding_box()['y'] == page.get_by_role('textbox',name='调查标题').locator('..').bounding_box()['y'],'桌面新建页设备与标题并排')
        page.screenshot(path=OUT/'initial.png',full_page=True)
        mode='sidebar'; page.reload()
        handle=page.get_by_role('separator',name='调整侧栏宽度')
        expect(handle).to_have_attribute('aria-valuenow','224')
        handle.focus(); page.keyboard.press('End'); expect(handle).to_have_attribute('aria-valuenow','380')
        page.keyboard.press('Home'); expect(handle).to_have_attribute('aria-valuenow','200')
        page.keyboard.press('ArrowRight'); expect(handle).to_have_attribute('aria-valuenow','216')
        page.keyboard.press('Enter'); expect(handle).to_have_attribute('aria-valuenow','224')
        check(True,'侧栏方向键/Home/End及Enter恢复默认')
        box=handle.bounding_box(); x=box['x']+box['width']/2
        page.mouse.move(x,300); page.mouse.down(); page.mouse.move(x+96,300,steps=8)
        expect(page.locator('.dt-shell')).to_have_class('dt-shell dt-resizing')
        page.mouse.up(); expect(handle).to_have_attribute('aria-valuenow','320')
        check('dt-resizing' not in page.locator('.dt-shell').get_attribute('class'),'拖拽调宽并释放拖动状态')
        page.reload(); expect(handle).to_have_attribute('aria-valuenow','320')
        check(True,'刷新保留账号侧栏宽度')
        box=handle.bounding_box(); x=box['x']+box['width']/2
        page.mouse.move(x,300); page.mouse.down(); page.mouse.move(x+40,300,steps=4)
        page.keyboard.press('Escape'); page.mouse.up(); expect(handle).to_have_attribute('aria-valuenow','320')
        check(True,'Escape取消拖拽并恢复原宽度')
        box=handle.bounding_box(); x=box['x']+box['width']/2
        page.mouse.move(x,300); page.mouse.down(); page.mouse.move(0,300,steps=5); page.mouse.up()
        expect(handle).to_have_attribute('aria-valuenow','200')
        box=handle.bounding_box(); page.mouse.move(box['x']+5,300); page.mouse.down(); page.mouse.move(700,300,steps=8); page.mouse.up()
        expect(handle).to_have_attribute('aria-valuenow','380')
        check(True,'拖拽受最小/最大宽度约束')
        check(page.locator('.dt-sidebar').evaluate('(e)=>e.scrollHeight <= e.clientHeight'),'长历史侧栏本身不出现第二条滚动条')
        check(page.locator('.history-list').evaluate('(e)=>e.scrollHeight > e.clientHeight'),'20条调查仅在近期列表内滚动')
        check(page.get_by_role('button',name='加载更多',exact=True).is_visible(),'加载更多保持可见')
        check(page.locator('.dt-side-footer').bounding_box()['y']+page.locator('.dt-side-footer').bounding_box()['height']<=720,'底部账号不被历史列表挤出首屏')
        page.screenshot(path=OUT/'sidebar-resized.png',full_page=True)
        page.set_viewport_size({'width':1280,'height':600})
        check(page.locator('.dt-sidebar').evaluate('(e)=>e.scrollHeight <= e.clientHeight'),'600px高度侧栏无外层滚动')
        check(page.locator('.history-list').evaluate('(e)=>e.clientHeight > 0'),'600px高度仍可滚动调查列表')
        check(page.locator('.dt-side-footer').bounding_box()['y']+page.locator('.dt-side-footer').bounding_box()['height']<=600,'600px高度账号仍在首屏')
        page.set_viewport_size({'width':800,'height':720})
        expect(handle).to_have_attribute('aria-valuemax','280'); expect(handle).to_have_attribute('aria-valuenow','280')
        check(page.locator('.dt-work').bounding_box()['width']>=520,'中等视口保留正文最小宽度')
        page.set_viewport_size({'width':1280,'height':720}); expect(handle).to_have_attribute('aria-valuenow','380')
        handle.dblclick(); expect(handle).to_have_attribute('aria-valuenow','224')
        check(True,'双击恢复默认宽度')
        page.get_by_text('搜索与筛选',exact=True).click()
        expect(page.get_by_role('searchbox',name='搜索调查')).to_be_visible()
        check(page.locator('.dt-sidebar').evaluate('(e)=>e.scrollHeight <= e.clientHeight'),'展开搜索筛选仍无嵌套外层滚动')
        page.set_viewport_size({'width':375,'height':812}); expect(handle).not_to_be_visible()
        check(page.evaluate('document.documentElement.scrollWidth <= innerWidth'),'窄屏关闭拖拽且无横向溢出')
        page.get_by_role('button',name='近期调查').click()
        expect(page.get_by_role('navigation',name='近期调查列表')).to_be_visible()
        page.set_viewport_size({'width':1280,'height':720})
        for state in ('completed','running','waiting'):
            mode=state
            page.reload(); expect(page.locator('[data-conversation-id="conv-1"]')).to_be_visible()
            page.locator('[data-conversation-id="conv-1"]').click()
            expect(page.locator('.pro-page')).to_be_visible()
            if state=='completed':
                expect(page.locator('.pro-short-conclusion')).to_have_text(sample()['result']['summary'])
                check(page.locator('.pro-nav').bounding_box()['y'] < page.locator('.dt-composer').bounding_box()['y'],'候选导航在补充表单之前')
                check(not page.locator('.dt-composer').evaluate('(e)=>e.open'),'完成后补充调查默认收起')
                check(page.locator('.pro-next-actions').bounding_box()['y'] > page.locator('.pro-claim-workspace').bounding_box()['y'],'本轮建议排在判断与证据之后')
                before=len([c for c in calls if c[0]=='POST'])
                page.locator('.claim-select').nth(1).click()
                expect(page.locator('.detail-text')).to_have_text(sample()['result']['claims'][1]['text'])
                page.locator('.claim-select').first.click()
                page.locator('.claim-detail [data-evidence-id]').first.click()
                expect(page.get_by_role('dialog',name='证据详情')).to_be_visible()
                expect(page.locator('.modal-excerpt')).to_contain_text(REF_A['excerpt'])
                page.keyboard.press('Escape')
                check(page.locator('.claim-detail [data-evidence-id]').first.evaluate('(e)=>e===document.activeElement'),'Esc 关闭引用并恢复焦点')
                check(len([c for c in calls if c[0]=='POST'])==before,'切换判断及引用不产生写请求')
                page.get_by_role('button',name='查看报告',exact=True).click()
                expect(page.locator('.diagnosis-report')).to_be_visible()
                page.emulate_media(media='print')
                check(page.locator('.dt-sidebar').evaluate('(e)=>getComputedStyle(e).display')=='none','报告打印隐藏导航')
                page.pdf(path=OUT/'report.pdf')
                page.emulate_media(media='screen')
                page.get_by_role('button',name='返回工作台').click()
                page.locator('.dt-composer > summary').click()
                page.get_by_role('button',name='补充现场观察',exact=True).click()
                expect(page.get_by_role('textbox',name='向 Agent 追加调查要求')).to_be_focused()
                page.locator('.dt-composer > summary').click()
            elif state=='running':
                expect(page.locator('.timeline.overview')).to_contain_text('复核答复')
                check(page.locator('.pro-nav').count()==0,'执行中没有空判断列表')
                check(not page.locator('.dt-composer').evaluate('(e)=>e.open'),'执行中草稿默认收起')
                check(page.locator('.pro-state[data-state="active"]').count()==0,'执行态没有重复占位状态卡')
                page.screenshot(path=OUT/'running-overview.png',full_page=True)
                page.locator('.dt-composer > summary').click()
                check(page.locator('.dt-composer').evaluate('(e)=>e.open'),'执行中可展开私人草稿')
                check(not page.get_by_role('button',name='提交并继续分析').is_enabled(),'运行中不能提交新分析')
            else:
                expect(page.get_by_test_id('pending-question')).to_contain_text(WAITING_QUESTION['question'])
                check(page.locator('.dt-composer').evaluate('(e)=>e.open'),'等待补答时表单自动展开')
                check(page.evaluate('document.documentElement.scrollWidth <= innerWidth'),'等待补答卡不造成横向溢出')
                page.evaluate('window.scrollTo(0,0)')
                page.screenshot(path=OUT/'waiting-question.png',full_page=True)
                page.get_by_role('textbox',name='补充信息',exact=True).fill('直流快充，SOC 35%')
                page.get_by_role('button',name='提交补答并继续').click()
                page.wait_for_timeout(200)
                sent=[json.loads(c[2]) for c in calls if c[0]=='POST' and c[1].endswith('/messages')][-1]
                check(sent['replyTo']=={'runId':'run-1','pendingQuestionId':'q1','questionVersion':2},'补答仍指向原 Run 和问题版本')
            if state == 'running': page.locator('.dt-composer > summary').click()
            page.evaluate('window.scrollTo(0,0)'); page.wait_for_timeout(100)
            page.screenshot(path=OUT/f'{state}.png',full_page=True)
        mode='history'; page.reload(); page.locator('[data-conversation-id="conv-1"]').click()
        page.get_by_role('tab',name='分析记录').click()
        page.get_by_role('button',name='查看这一轮结果').first.click()
        expect(page.locator('.dt-history-active')).to_contain_text('第 2 轮仍有未决调查')
        check(page.locator('.pro-page').get_attribute('data-run-id')=='run-1','历史结果与活动轮分别绑定')
        page.get_by_role('button',name='取消活动第 2 轮',exact=True).click()
        expect(page.locator('.cancel-confirm')).to_contain_text('run-live')
        page.get_by_role('button',name='保留运行').click()
        check(not page.locator('.cancel-confirm').count(),'取消确认指向活动轮且可保留运行')
        mode='team'; page.reload(); page.locator('[data-conversation-id="conv-1"]').click()
        page.get_by_role('tab',name='团队讨论').click()
        page.get_by_role('button',name='引用到调查补充 →').click()
        expect(page.get_by_role('textbox',name='向 Agent 追加调查要求')).to_be_focused()
        check(page.locator('.dt-composer').evaluate('(e)=>e.open'),'讨论引用自动展开已折叠的补充调查')
        expect(page.locator('.dt-composer')).to_contain_text('现场反馈充电中断，请核对记录。')
        page.locator('.dt-composer > summary').click()
        mode='completed'; page.reload(); page.locator('[data-conversation-id="conv-1"]').click()
        for width in (941,600):
            page.set_viewport_size({'width':width,'height':893}); page.wait_for_timeout(100)
            check(page.evaluate('document.documentElement.scrollWidth <= innerWidth'),f'{width}px 无横向溢出')
            page.screenshot(path=OUT/f'result-{width}.png',full_page=True)
        check(not errors,'浏览器无运行时异常')
        browser.close()
    (OUT/'result.json').write_text(json.dumps({'passed':len(checks),'failed':0,'checks':checks,'evidence':'协议替身；非真实模型/数据库验收'},ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps({'passed':len(checks),'output':str(OUT)},ensure_ascii=False))
finally:
    server.shutdown()

