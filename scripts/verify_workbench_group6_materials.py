"""同一隔离真服务上构造分页和失败资料；无模型请求，不冒充现场数据。"""
import verify_workbench_group6_api as api
api.RESULT_NAME='material-api-checks.json'
from verify_workbench_group6_api import Client,OUT,BASE,service,check,checks
import json,time,uuid,urllib.request
admin=Client();admin.login('admin@example.com');team=service['workspaceId']
stage=json.loads((OUT/'api-stage.json').read_text(encoding='utf-8'));cid=stage['mainConversation']
def upload(name,content,mime='text/plain'):
    _,csrf=admin.call('/api/auth/csrf')
    boundary='dt'+uuid.uuid4().hex
    body=(f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="{name}"\r\nContent-Type: {mime}\r\n\r\n'.encode()+content+f'\r\n--{boundary}--\r\n'.encode())
    req=urllib.request.Request(BASE+'/api/conversations/'+cid+'/attachments',data=body,headers={'Content-Type':'multipart/form-data; boundary='+boundary,'X-Workspace-ID':team,csrf['headerName']:csrf['token']},method='POST')
    with admin.opener.open(req,timeout=40) as response:value=json.load(response)
    for _ in range(100):
        _,value=admin.call('/api/files/'+value['fileId'])
        if value['parseStatus'] in ('PARSED','FAILED','OCR_REQUIRED'):return value
        time.sleep(.1)
    raise AssertionError('file parsing timeout')
for i in range(21):
    file=upload('page-'+str(i)+'.txt',('第六组模拟资料 '+str(i)+'：仅为分页和范围显示验收，不代表现场事实。').encode())
    check('parse '+str(i),file['parseStatus']=='PARSED')
    admin.call('/api/files/'+file['fileId']+'/publications',{'title':'分页模拟资料 '+str(i),'applicability':'仅适用界面分页验证，不适用现场检修。'},workspace=team,expected=200)
bad=upload('invalid-utf8.txt',b'\xff\xfe\xfd')
check('invalid UTF8 explicit FAILED',bad['parseStatus']=='FAILED')
admin.call('/api/files/'+bad['fileId']+'/retry',{},expected=202)
pdf=upload('unsupported.pdf',b'%PDF-1.4\nnot-supported', 'application/pdf')
check('PDF explicit OCR_REQUIRED',pdf['parseStatus']=='OCR_REQUIRED')
_,page=admin.call('/api/conversations/'+cid+'/attachments?offset=20&limit=20',workspace=team)
check('attachments second page accessible',len(page)>=2)
_,docs=admin.call('/api/knowledge/documents?offset=20&limit=20',workspace=team)
check('knowledge second page accessible',len(docs)>=2)
(OUT/'material-fixtures.json').write_text(json.dumps({'conversation':cid,'failedFile':bad,'unsupportedPdf':pdf,'checks':checks},ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps({'checks':len(checks),'passed':sum(c['pass'] for c in checks)}))
