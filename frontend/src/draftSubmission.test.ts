import { describe, expect, it, vi } from 'vitest'
import { ensureConversation, initialMessage } from './draftSubmission'
import { createConversation, loadCsrf, sendMessage, setAccessContext, type ConversationView } from './api'

const created: ConversationView = {
  conversationId: 'conv-new', version: 0,
  createdAt: '2026-11-18T00:00:00Z', updatedAt: '2026-11-18T00:00:00Z'
}

describe('新调查首次提交', () => {
  it('标题与首条问题一起提交，空标题不添加额外行', () => {
    expect(initialMessage(' 座舱空调不转 ', '送风没有反应')).toBe('座舱空调不转\n送风没有反应')
    expect(initialMessage(' ', '送风没有反应')).toBe('送风没有反应')
  })
  it('空草稿不创建会话，首次提交才创建一次', async () => {
    const factory = vi.fn(async () => created)
    expect(factory).not.toHaveBeenCalled()
    const current = await ensureConversation(null, factory)
    expect(current).toEqual(created)
    expect(factory).toHaveBeenCalledTimes(1)
  })

  it('创建后发送失败保留同一会话，重试不重复创建', async () => {
    const factory = vi.fn(async () => created)
    const current = await ensureConversation(null, factory)
    await expect(Promise.reject(new Error('send failed'))).rejects.toThrow('send failed')
    const retried = await ensureConversation(current, factory)
    expect(retried.conversationId).toBe('conv-new')
    expect(factory).toHaveBeenCalledTimes(1)
  })

  it('已有会话直接复用，不调用创建接口', async () => {
    const factory = vi.fn(async () => created)
    expect((await ensureConversation(created, factory)).conversationId).toBe('conv-new')
    expect(factory).not.toHaveBeenCalled()
  })
})

it('团队首次有效提交复用认证空间，携带创建返回的期望版本且不要求设备',async()=>{
  const calls:{path:string;init?:RequestInit}[]=[]
  vi.stubGlobal('fetch',vi.fn(async(path,init)=>{
    calls.push({path:String(path),init})
    return new Response(JSON.stringify(String(path).endsWith('/csrf')?{headerName:'X-CSRF-TOKEN',token:'c'}:String(path)==='/api/conversations'?created:{runId:'accepted'}),{status:200})
  }))
  try {
    setAccessContext('account','team');await loadCsrf()
    const current=await ensureConversation(null,createConversation)
    await sendMessage(current.conversationId,'未指定设备也可以问答','key',[],null,[],current.version)
    expect(calls.filter(c=>c.path==='/api/conversations')).toHaveLength(1)
    const submitted=calls.find(c=>c.path.endsWith('/messages'))!
    expect(new Headers(submitted.init?.headers).get('X-Workspace-ID')).toBe('team')
    expect(JSON.parse(String(submitted.init?.body))).toEqual({schemaVersion:1,userMessage:'未指定设备也可以问答',expectedConversationVersion:0})
  } finally { setAccessContext(null,null);vi.unstubAllGlobals() }
})
