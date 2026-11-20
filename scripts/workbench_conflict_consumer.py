import verify_workbench_group6_api as a
import json,time,uuid
client=a.Client();client.login('admin@example.com');run=json.loads((a.OUT/'conflict-target.json').read_text(encoding='utf-8'))
(a.OUT/'conflict-consumer-ready').write_text('ready')
deadline=time.time()+300
while not (a.OUT/'conflict-consumer-go').exists() and time.time()<deadline:time.sleep(.01)
assert time.time()<deadline
code,value=client.call('/api/conversations/'+run['conversationId']+'/messages',{'schemaVersion':1,'userMessage':'这是模拟资料问答，没有具体资产或现场测量。请只说明用户报告不能当作实测，不需继续追问。','replyTo':{'runId':run['runId'],'pendingQuestionId':run['pendingQuestion']['pendingQuestionId'],'questionVersion':run['pendingQuestion']['questionVersion']}},workspace=a.service['workspaceId'],key=str(uuid.uuid4()))
(a.OUT/'conflict-consumer-result.json').write_text(json.dumps({'status':code,'runId':value.get('runId'),'code':value.get('code')}),encoding='utf-8')
