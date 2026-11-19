import { describe, expect, it } from 'vitest'
import { appendConversationPage, conversationBadge } from './history'
import type { ConversationSummary } from './api'

function summary(overrides: Partial<ConversationSummary> = {}): ConversationSummary {
  return {
    conversationId: 'conv-1', title: '空调不转', investigatedAssetId: 'EV-DEMO-017',
    latestRunStatus: 'COMPLETED', activeRunCount: 0, waitingRunCount: 0, runCount: 1,
    updatedAt: '2026-11-18T00:00:00Z', ...overrides
  }
}

describe('会话发现投影', () => {
  it('非当前会话也按服务端计数显示待答、进行中及终态', () => {
    expect(conversationBadge(summary({ latestRunStatus: 'WAITING_FOR_USER', activeRunCount: 1, waitingRunCount: 1 }))).toBe('waiting')
    expect(conversationBadge(summary({ latestRunStatus: 'RUNNING', activeRunCount: 1 }))).toBe('running')
    expect(conversationBadge(summary({ latestRunStatus: 'FAILED' }))).toBe('failed')
    expect(conversationBadge(summary({ latestRunStatus: 'CANCELLED' }))).toBe('cancelled')
    expect(conversationBadge(summary())).toBe('')
  })

  it('加载更多按会话去重并采用最新游标', () => {
    const first = summary()
    const updated = summary({ title: '更新后的标题', runCount: 2 })
    const second = summary({ conversationId: 'conv-2', title: '另一调查' })
    const merged = appendConversationPage([first], { items: [updated, second], nextCursor: 'next-2' })
    expect(merged.items.map(item => item.conversationId)).toEqual(['conv-1', 'conv-2'])
    expect(merged.items[0].title).toBe('更新后的标题')
    expect(merged.nextCursor).toBe('next-2')
    expect(appendConversationPage(merged.items, { items: [], nextCursor: null }).nextCursor).toBeNull()
  })
})
