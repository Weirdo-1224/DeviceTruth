"""第六组隔离 HTTP 验收；只接受测试宿主生成的随机库/端口，邮件为本地传输替身。"""
import concurrent.futures, http.cookiejar, json, time, urllib.request, urllib.error, uuid
from pathlib import Path
OUT = Path('outputs/validation/workbench-group6')
service = json.loads((OUT / 'service.json').read_text(encoding='utf-8'))
BASE = service['url']
assert BASE.startswith('http://127.0.0.1:') and BASE.rsplit(':',1)[1] not in ('8080','8081')
assert service['database'].startswith('devicetruth_test_')
checks=[]
RESULT_NAME='api-checks.json'
def check(name, condition):
    checks.append({'name':name,'pass':bool(condition)})
    (OUT/RESULT_NAME).write_text(json.dumps(checks,ensure_ascii=False,indent=2),encoding='utf-8')
    assert condition,name
class Client:
    def __init__(self): self.opener=urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
    def call(self,path,body=None,method=None,workspace=None,key=None,expected=None):
        headers={'Accept':'application/json'}
        if workspace: headers['X-Workspace-ID']=workspace
        if key: headers['Idempotency-Key']=key
        if body is not None:
            _,csrf=self.call('/api/auth/csrf')
            headers[csrf['headerName']]=csrf['token'];headers['Content-Type']='application/json'
        req=urllib.request.Request(BASE+path,data=None if body is None else json.dumps(body).encode(),headers=headers,method=method or ('GET' if body is None else 'POST'))
        try:
            with self.opener.open(req,timeout=40) as response: code=response.status;payload=response.read()
        except urllib.error.HTTPError as error: code=error.code;payload=error.read()
        value=json.loads(payload) if payload else None
        if expected is not None: check(path+' expected '+str(expected)+' actual '+str(code),code==expected)
        return code,value
    def login(self,email,password='isolated-browser-password-123'):
        return self.call('/api/auth/login',{'email':email,'password':password},expected=200)[1]
def wait(client,run):
    deadline=time.time()+200
    while time.time()<deadline:
        _,value=client.call('/api/runs/'+run)
        if value['status'] not in ('QUEUED','RUNNING'):return value
        time.sleep(.5)
    raise AssertionError('real model timeout')
def verify_core():
    global RESULT_NAME
    RESULT_NAME='api-core-checks.json'
    admin=Client();admin.login('admin@example.com')
    collab=Client();collab.login('colleague@example.com')
    viewer=Client();viewer.login('viewer@example.com')
    team=service['workspaceId']
    _,page=admin.call('/api/conversations/summaries',workspace=team)
    main=next(item for item in page['items'] if '模拟手册知识问答' in item['title'])['conversationId']
    _,detail=admin.call('/api/conversations/'+main,workspace=team)
    first=detail['runs'][0];rid=first['runId']
    check('browser real answer completed',first['status']=='COMPLETED' and first['result']['review']['version']==2)
    _,models=admin.call('/api/runs/'+rid+'/model-events')
    _,refs=admin.call('/api/runs/'+rid+'/references')
    check('real deepseek model and review',any(m['model']=='deepseek-flash' and m['outcome']=='VALID_SEMANTIC_REVIEW' for m in models))
    check('actual references retained',len(refs)>0)
    check('discussion quote keeps server author and nature',any(m.get('discussionQuotes') for m in detail['messages']))
    (OUT/'browser-main-run.json').write_text(json.dumps({'run':first,'modelEvents':models,'references':refs,'messages':detail['messages']},ensure_ascii=False,indent=2),encoding='utf-8')
    viewer.call('/api/conversations',{},workspace=team,expected=403)
    viewer.call('/api/conversations/'+main+'/discussions',{'content':'viewer forbidden'},workspace=team,expected=403)
    viewer.call('/api/conversations/'+main+'/messages',{'schemaVersion':1,'userMessage':'viewer forbidden','expectedConversationVersion':detail['conversation']['version']},workspace=team,key=str(uuid.uuid4()),expected=403)
    collab.call('/api/workspaces/'+team,{'name':'forbidden'},method='PATCH',workspace=team,expected=403)
    viewer.call('/api/runs/'+rid,workspace=team,expected=200)
    _,files=admin.call('/api/conversations/'+main+'/attachments',workspace=team)
    file=files[0]
    viewer.call('/api/files/'+file['fileId']+'/publications',{'title':'bad','applicability':'bad'},workspace=team,expected=403)
    _,docs=admin.call('/api/knowledge/documents',workspace=team)
    check('publication visible, original simulated nature',any(d['dataNature']=='SIMULATED' and d['originalAuthorUserId']==service['admin'] for d in docs))
    # 过期版本不开始模型；原草稿由前端合同/浏览器验证。
    admin.call('/api/conversations/'+main+'/messages',{'schemaVersion':1,'userMessage':'stale','expectedConversationVersion':0},workspace=team,key=str(uuid.uuid4()),expected=409)
    # 同一调查两账号竞争，使用独立Cookie和相同已看到的版本。
    _,conversation=admin.call('/api/conversations',{},workspace=team,expected=201)
    cid=conversation['conversationId']
    body={'schemaVersion':1,'expectedConversationVersion':0,'userMessage':'请先向我询问一个你必须知道的关键问题，等待我回答后再继续。本次检修对象和现象还没提供，请不要猜测或直接回答。'}
    with concurrent.futures.ThreadPoolExecutor(2) as pool:
        futures=[pool.submit(c.call,'/api/conversations/'+cid+'/messages',body,None,team,str(uuid.uuid4())) for c in (admin,collab)]
        responses=[f.result() for f in futures]
    check('two actors compete, only one run accepted',sorted(code for code,_ in responses)==[202,409])
    run=next(value for code,value in responses if code==202);run=wait(admin,run['runId'])
    check('real model asks clarification',run['status']=='WAITING_FOR_USER')
    (OUT/'waiting-run.json').write_text(json.dumps(run,ensure_ascii=False,indent=2),encoding='utf-8')
    (OUT/'api-stage.json').write_text(json.dumps({'mainConversation':main,'mainRun':rid,'waitingConversation':cid,'waitingRun':run['runId']},ensure_ascii=False,indent=2),encoding='utf-8')

def verify_accounts():
    global RESULT_NAME
    RESULT_NAME='account-checks.json'
    suffix=uuid.uuid4().hex[:6]
    admin=Client();admin.login('admin@example.com')
    viewer=Client();viewer.login('viewer@example.com')
    team=service['workspaceId']
    # 邀请邮件由测试传输捕获，真实 HTTP 接受仍校验邮箱。
    _,invite=admin.call('/api/workspaces/'+team+'/invitations',{'email':'fresh-api-'+suffix+'@example.com','role':'COLLABORATOR'},workspace=team,expected=200)
    token=(OUT/('mailbox/fresh-api-'+suffix+'_example.com.txt')).read_text(encoding='utf-8').split('invitation-token=')[1].strip()
    viewer.call('/api/invitations/accept',{'token':token},expected=403)
    fresh=Client();fresh.call('/api/auth/register',{'email':'fresh-api-'+suffix+'@example.com','password':'isolated-browser-password-123','displayName':'新同事'},expected=200)
    fresh.call('/api/invitations/accept',{'token':token},expected=200)
    _,spaces=fresh.call('/api/workspaces',expected=200)
    check('registered invitee retains personal and joins team',len(spaces)==2)
    # 恢复凭证只存在本地测试邮箱，不输出到验收摘要。
    fresh.call('/api/auth/password-reset-requests',{'email':'fresh-api-'+suffix+'@example.com'},expected=200)
    reset=(OUT/('mailbox/fresh-api-'+suffix+'_example.com.txt')).read_text(encoding='utf-8').split('reset-token=')[1].strip()
    anon=Client();anon.call('/api/auth/password-resets',{'token':reset,'password':'replacement-password-456','confirmPassword':'mismatch-password-123'},expected=400)
    anon.call('/api/auth/password-resets',{'token':reset,'password':'replacement-password-456','confirmPassword':'replacement-password-456'},expected=200)
    fresh.call('/api/auth/me',expected=401)
    anon.call('/api/auth/password-resets',{'token':reset,'password':'replacement-password-456','confirmPassword':'replacement-password-456'},expected=400)
    anon.login('fresh-api-'+suffix+'@example.com','replacement-password-456')
    print(json.dumps({'checks':len(checks),'passed':sum(c['pass'] for c in checks)},ensure_ascii=False))

if __name__=='__main__':
    import sys
    if '--accounts-only' in sys.argv: verify_accounts()
    else: verify_core(); verify_accounts()
