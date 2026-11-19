import { describe, expect, it, vi } from 'vitest'
import { ensureConversation } from './draftSubmission'
import type { ConversationView } from './api'

const created: ConversationView = {
  conversationId: 'conv-new', version: 0,
  createdAt: '2026-11-18T00:00:00Z', updatedAt: '2026-11-18T00:00:00Z'
}

describe('新调查首次提交', () => {
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
