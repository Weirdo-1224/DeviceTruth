import verify_workbench_group6_api as api
from verify_workbench_group6_api import Client,OUT,service,check,wait
import json,uuid,urllib.parse
api.RESULT_NAME='lifecycle-checks.json'
admin=Client();admin.login('admin@example.com');collab=Client();collab.login('colleague@example.com');team=service['workspaceId']
stage=json.loads((OUT/'api-stage.json').read_text(encoding='utf-8'))
_,detail=admin.call('/api/conversations/'+stage['waitingConversation'],workspace=team)
check('browser reply preserves original run ID, then one explicit cancelled round',len(detail['runs'])==2 and detail['runs'][0]['runId']==stage['waitingRun'] and detail['runs'][0]['status']=='COMPLETED' and detail['runs'][1]['status']=='CANCELLED')
check('reply author and quoted discussion persist',any(m.get('actorUserId')==service['colleague'] and m.get('discussionQuotes') for m in detail['messages']))
(OUT/'browser-reply-detail.json').write_text(json.dumps(detail,ensure_ascii=False,indent=2),encoding='utf-8')
original=json.loads((OUT/'waiting-run.json').read_text(encoding='utf-8'))
collab.call('/api/conversations/'+stage['waitingConversation']+'/messages',{'schemaVersion':1,'userMessage':'过期补答不能变成新运行','replyTo':{'runId':original['runId'],'pendingQuestionId':original['pendingQuestion']['pendingQuestionId'],'questionVersion':original['pendingQuestion']['questionVersion']}},workspace=team,key=str(uuid.uuid4()),expected=409)
_,conversation=admin.call('/api/conversations',{},workspace=team,expected=201)
cid=conversation['conversationId']
_,run=admin.call('/api/conversations/'+cid+'/messages',{'schemaVersion':1,'expectedConversationVersion':0,'userMessage':'请先询问我本次要排查什么设备，然后等待我回答。不要直接给结论。'},workspace=team,key=str(uuid.uuid4()),expected=202)
run=wait(admin,run['runId']);check('cancel target is actual real-model waiting run',run['status']=='WAITING_FOR_USER')
collab.call('/api/runs/'+run['runId']+'/cancel',{},expected=403)
admin.call('/api/runs/'+run['runId']+'/cancel',{},expected=200)
_,after=admin.call('/api/runs/'+run['runId']);check('administrator cancellation persists',after['status']=='CANCELLED')
q=urllib.parse.urlencode({'q':'协议夹具','limit':20})
_,page=admin.call('/api/conversations/summaries?'+q,workspace=team)
check('real summaries first page bounded with cursor',len(page['items'])==20 and bool(page['nextCursor']))
_,second=admin.call('/api/conversations/summaries?'+q+'&cursor='+urllib.parse.quote(page['nextCursor']),workspace=team)
check('real summaries second page without duplicate IDs',len(second['items'])==4 and not ({i['conversationId'] for i in page['items']}&{i['conversationId'] for i in second['items']}))
_,spaces=collab.call('/api/workspaces');newteam=next(w['workspaceId'] for w in spaces if w['name']=='新建空团队验收')
_,own=collab.call('/api/conversations/summaries',workspace=newteam)
check('new team investigation remains in new team',len(own['items'])==1)
_,newdetail=collab.call('/api/conversations/'+own['items'][0]['conversationId'],workspace=newteam)
check('staged TXT uploaded on first explicit submission only',len(newdetail['runs'])==1 and newdetail['runs'][0]['status']=='COMPLETED')
_,model=admin.call('/api/runs/'+stage['mainRun']+'/model-events');(OUT/'report-model-count.json').write_text(json.dumps({'run':stage['mainRun'],'count':len(model)}),encoding='utf-8')
print(json.dumps({'checks':len(api.checks),'passed':sum(c['pass'] for c in api.checks)}))
