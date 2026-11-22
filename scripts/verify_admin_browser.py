#!/usr/bin/env python3
"""Real Chrome + current Java classes + random PG database. No external model call."""
import json
import os
from pathlib import Path
import subprocess
import threading
import time
import urllib.error
import urllib.request
import uuid
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from playwright.sync_api import sync_playwright, expect

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'outputs/validation/platform-admin/browser'
OUT.mkdir(parents=True, exist_ok=True)
DATABASE = 'devicetruth_test_' + uuid.uuid4().hex

def sql(command):
    subprocess.run(['docker','exec','devicetruth-postgres-1','psql','-U','devicetruth','-d','postgres','-v','ON_ERROR_STOP=1','-c',command],check=True,stdout=subprocess.DEVNULL)

def main():
    env = os.environ.copy()
    env.update(SPRING_DATASOURCE_URL='jdbc:postgresql://127.0.0.1:54329/'+DATABASE, SPRING_DATASOURCE_USERNAME='devicetruth')
    env['SPRING_DATASOURCE_PASSWORD'] = subprocess.run(['docker','exec','devicetruth-postgres-1','printenv','POSTGRES_PASSWORD'],check=True,capture_output=True,text=True).stdout.rstrip('\n')
    state = OUT / 'connection.json'
    state.unlink(missing_ok=True)
    classpath = str(ROOT/'outputs/validation/platform-admin/maven/test-classes') + ':' + str(ROOT/'outputs/validation/platform-admin/maven/classes') + ':' + Path('/tmp/devicetruth-admin-classpath.txt').read_text().strip()
    sql('CREATE DATABASE '+DATABASE)
    process = None
    ui = None
    try:
        with (OUT/'server.log').open('w') as log:
            process = subprocess.Popen(['/opt/homebrew/opt/openjdk@21/bin/java','-cp',classpath,'io.devicetruth.server.AdminBrowserFixture',str(state)],cwd=ROOT,env=env,stdout=log,stderr=subprocess.STDOUT)
        for _ in range(150):
            if state.exists(): break
            if process.poll() is not None: raise RuntimeError('Browser fixture failed; inspect server.log')
            time.sleep(.2)
        fixture = json.loads(state.read_text())
        class Proxy(SimpleHTTPRequestHandler):
            def __init__(self,*args,**kwargs): super().__init__(*args,directory=ROOT/'frontend/dist',**kwargs)
            def log_message(self,*args): pass
            def do_GET(self):
                if self.path.startswith('/api/'): self.proxy()
                else:
                    if self.path.startswith('/admin'): self.path='/index.html'
                    super().do_GET()
            def do_POST(self): self.proxy()
            def do_PATCH(self): self.proxy()
            def proxy(self):
                size=int(self.headers.get('Content-Length','0'))
                request=urllib.request.Request(fixture['backend']+self.path,data=self.rfile.read(size) if size else None,headers={k:v for k,v in self.headers.items() if k.lower() not in {'host','connection','accept-encoding','content-length'}},method=self.command)
                try: response=urllib.request.urlopen(request,timeout=30)
                except urllib.error.HTTPError as error: response=error
                with response:
                    body=response.read(); self.send_response(response.code)
                    for key,value in response.headers.items():
                        if key.lower() in {'set-cookie','content-type','x-request-id'}: self.send_header(key,value)
                    self.send_header('Content-Length',str(len(body)));self.end_headers()
                    try: self.wfile.write(body)
                    except BrokenPipeError: pass
        ui=ThreadingHTTPServer(('127.0.0.1',0),Proxy)
        threading.Thread(target=ui.serve_forever,daemon=True).start(); origin=f'http://127.0.0.1:{ui.server_port}'
        checks=[]
        with sync_playwright() as playwright:
            browser=playwright.chromium.launch(channel='chrome',headless=True)
            context=browser.new_context(viewport={'width':1600,'height':1000});page=context.new_page();errors=[]
            page.on('pageerror',lambda e:errors.append(str(e)))
            page.goto(origin+'/admin/users');expect(page.get_by_role('heading',name='登录 DeviceTruth')).to_be_visible()
            page.locator('.admin-login').get_by_label('邮箱',exact=True).fill('admin@isolated.example');page.locator('.admin-login').get_by_label('密码',exact=True).fill('isolated-browser-password-123');page.get_by_role('button',name='登录',exact=True).click()
            expect(page.get_by_role('button',name='创建账号',exact=True)).to_be_enabled();checks.append('real-session-admin-access')
            page.get_by_role('button',name='创建账号',exact=True).click();dialog=page.get_by_role('dialog')
            dialog.get_by_label('初始密码').fill('temporary-browser-secret-123');page.keyboard.press('Escape')
            expect(page.get_by_role('button',name='创建账号',exact=True)).to_be_focused();checks.append('escape-and-focus-return')
            page.get_by_role('button',name='创建账号',exact=True).click();expect(dialog.get_by_label('初始密码')).to_have_value('')
            dialog.get_by_label('邮箱',exact=True).fill('created@isolated.example');dialog.get_by_label('名称',exact=True).fill('浏览器开户');dialog.get_by_label('初始密码').fill('temporary-browser-secret-123');dialog.get_by_role('button',name='确认创建').click()
            expect(page.get_by_role('cell',name='created@isolated.example',exact=True)).to_be_visible();checks.append('real-controlled-account-creation')
            page.get_by_label('邮箱 / 名称',exact=True).fill('created@');page.get_by_role('button',name='应用筛选',exact=True).click();expect(page.locator('tbody tr')).to_have_count(1);checks.append('filter-clears-cursor-and-real-query')
            page.get_by_role('button',name='创建账号',exact=True).click();late=page.get_by_role('dialog');late.get_by_label('邮箱',exact=True).fill('delayed@isolated.example');late.get_by_label('名称',exact=True).fill('晚到响应测试');late.get_by_label('初始密码').fill('temporary-browser-secret-123')
            page.evaluate("""() => { const original=window.fetch.bind(window); window.__adminOriginalFetch=original; window.__adminLateReceived=false; window.fetch=(input,options)=>{if(input==='/api/admin/users'&&options?.method==='POST')return original(input,options).then(()=>{window.__adminLateReceived=true;return new Promise(resolve=>setTimeout(()=>resolve(new Response(JSON.stringify({code:'AUTHENTICATION_REQUIRED',requestId:'protocol-late-fixture'}),{status:401,headers:{'Content-Type':'application/json'}})),900))});return original(input,options)} }""")
            late.get_by_role('button',name='确认创建',exact=True).click();page.wait_for_function('window.__adminLateReceived === true');page.keyboard.press('Escape');page.get_by_role('link',name='运行与用量',exact=True).click();page.wait_for_timeout(1100);expect(page.get_by_role('heading',name='运行与用量',exact=True)).to_be_visible();expect(page.locator('.admin-login')).to_have_count(0);page.evaluate('window.fetch = window.__adminOriginalFetch');checks.append('late-write-error-discard-protocol-fault')
            for width,height in [(1600,1000),(1366,768),(390,844)]:
                page.set_viewport_size({'width':width,'height':height})
                for key,label in [('users','用户管理'),('runs','运行与用量'),('knowledge','知识库管理'),('skills','Skill 管理'),('system','系统与审计')]:
                    page.get_by_role('link',name=label,exact=True).click();expect(page.get_by_role('heading',name=label,exact=True)).to_be_visible()
                    if key=='users': expect(page.get_by_role('cell',name='admin@isolated.example',exact=True)).to_be_visible()
                    if key=='runs': expect(page.get_by_role('heading',name='没有匹配记录',exact=True)).to_be_visible()
                    if key=='knowledge': page.get_by_label('管理目标空间').select_option(fixture['workspaceId']);expect(page.get_by_role('cell',name='模拟检修手册',exact=True)).to_be_visible()
                    if key=='skills': expect(page.get_by_role('cell',name='automotive-diagnosis',exact=True)).to_be_visible()
                    if key=='system': expect(page.get_by_role('cell',name='APPLICATION',exact=True)).to_be_visible()
                    page.screenshot(path=str(OUT/f'{key}-{width}x{height}.png'),full_page=True)
                    assert page.evaluate('document.documentElement.scrollWidth <= innerWidth'),f'Page overflow: {key} {width}'
                checks.append(f'five-pages-{width}x{height}')
            page.set_viewport_size({'width':1366,'height':768});page.get_by_role('link',name='Skill 管理',exact=True).click();page.get_by_role('button',name='空间授权',exact=True).click();page.get_by_label('管理目标空间').select_option(fixture['workspaceId']);expect(page.get_by_role('button',name='开通 Skill',exact=True)).to_be_enabled();page.get_by_role('button',name='开通 Skill',exact=True).click();page.wait_for_timeout(500);page.screenshot(path=str(OUT/'skill-install-dialog.png'));(OUT/'skill-dialog-state.txt').write_text(page.locator('dialog[open]').all_inner_texts().__str__())
            install=page.get_by_role('dialog');install.locator('select').select_option(index=1);install.get_by_role('button',name='确认开通').click();expect(page.get_by_role('cell',name='AVAILABLE',exact=True)).to_be_visible();checks.append('real-skill-installation')
            page.get_by_role('button',name='详情',exact=True).click();page.get_by_role('button',name='停用安装',exact=True).click();page.get_by_role('button',name='确认变更',exact=True).click();expect(page.get_by_role('cell',name='DISABLED',exact=True)).to_be_visible();checks.append('real-skill-disable')
            page.get_by_role('link',name='用户管理',exact=True).click();expect(page.get_by_role('cell',name='created@isolated.example',exact=True)).to_be_visible()
            row=page.get_by_role('row').filter(has=page.get_by_role('cell',name='created@isolated.example',exact=True))
            row.get_by_role('button',name='详情',exact=True).click();page.get_by_role('button',name='停用账号',exact=True).click();page.get_by_role('button',name='确认变更',exact=True).click();expect(row.get_by_role('cell',name='DISABLED',exact=True)).to_be_visible()
            row.get_by_role('button',name='详情',exact=True).click();page.get_by_role('button',name='恢复账号',exact=True).click();page.get_by_role('button',name='确认变更',exact=True).click();expect(row.get_by_role('cell',name='ACTIVE',exact=True)).to_be_visible();checks.append('real-account-disable-restore')
            page.get_by_role('link',name='知识库管理',exact=True).click();page.get_by_label('管理目标空间').select_option(fixture['workspaceId']);expect(page.get_by_role('cell',name='模拟检修手册',exact=True)).to_be_visible();page.get_by_role('button',name='导入已收集知识',exact=True).click()
            upload=page.get_by_role('dialog');upload.get_by_label('标题',exact=True).fill('浏览器已收集资料');upload.locator('select').select_option('S-EV-SRC01');upload.locator('input[type=file]').set_input_files({'name':'EV-0001.md','mimeType':'text/markdown','buffer':(ROOT/'datasets/knowledge/curated/automotive/fault_diagnosis/EV-0001.md').read_bytes()});upload.get_by_role('button',name='确认导入',exact=True).click()
            expect(page.get_by_role('cell',name='PARSED',exact=True)).to_be_visible(timeout=30000);checks.append('managed-import-real-api-and-status-poll')
            page.get_by_role('button',name='空间知识',exact=True).click();expect(page.get_by_role('cell',name='浏览器已收集资料',exact=True)).to_be_visible()
            docrow=page.get_by_role('row').filter(has=page.get_by_role('cell',name='浏览器已收集资料',exact=True));docrow.get_by_role('button',name='详情',exact=True).click();page.get_by_role('button',name='停用文档',exact=True).click();page.get_by_role('button',name='确认变更',exact=True).click();expect(docrow.get_by_role('cell',name='DISABLED',exact=True)).to_be_visible()
            docrow.get_by_role('button',name='详情',exact=True).click();page.get_by_role('button',name='恢复文档',exact=True).click();page.get_by_role('button',name='确认变更',exact=True).click();expect(docrow.get_by_role('cell',name='ENABLED',exact=True)).to_be_visible();checks.append('knowledge-disable-restore')
            page.get_by_role('link',name='系统与审计',exact=True).click();page.get_by_role('button',name='管理审计',exact=True).click();page.get_by_label('审计操作者 ID',exact=True).fill(fixture['adminId']);page.get_by_label('审计结果').select_option('SUCCESS');page.get_by_role('button',name='应用筛选',exact=True).click();expect(page.get_by_role('cell',name='USER_CREATE',exact=True).first).to_be_visible();checks.append('real-audit-actor-and-result-filter')
            page.get_by_role('button',name='退出',exact=True).click();expect(page.get_by_role('heading',name='登录 DeviceTruth')).to_be_visible()
            page.locator('.admin-login').get_by_label('邮箱',exact=True).fill('member@isolated.example');page.locator('.admin-login').get_by_label('密码',exact=True).fill('isolated-browser-password-123');page.get_by_role('button',name='登录',exact=True).click();expect(page.get_by_role('heading',name='无平台管理权限',exact=True)).to_be_visible();page.screenshot(path=str(OUT/'permission-denied.png'),full_page=True);checks.append('ordinary-member-denied-after-account-switch')
            assert not errors,errors
            browser.close()
        (OUT/'report.json').write_text(json.dumps({'checks':checks,'passed':len(checks),'javascriptErrors':errors,'database':'random isolated PostgreSQL 16','reasoner':'openai configured; model calls: 0','embedding':'deterministic fixture','materialNature':'SIMULATED initial fixture; REAL collected EV-0001 import; no field data','viewportScreenshots':15,'screenshots':17},ensure_ascii=False,indent=2))
        print('Browser checks:',len(checks),'screenshots: 17 (15 viewport + 2 dialog/permission)')
    finally:
        if ui: ui.shutdown();ui.server_close()
        if process: process.terminate();process.wait(timeout=15)
        sql('DROP DATABASE '+DATABASE+' WITH (FORCE)')

if __name__=='__main__': main()
