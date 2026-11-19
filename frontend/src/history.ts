import type { ConversationSummary, ConversationSummaryPage } from './api'

export type HistoryBadge = '' | 'waiting' | 'running' | 'failed' | 'cancelled'

export function conversationBadge(item: ConversationSummary): HistoryBadge {
  if (item.waitingRunCount > 0) return 'waiting'
  if (item.activeRunCount > 0) return 'running'
  if (item.latestRunStatus === 'FAILED') return 'failed'
  if (item.latestRunStatus === 'CANCELLED') return 'cancelled'
  return ''
}

export function appendConversationPage(current: ConversationSummary[], page: ConversationSummaryPage): ConversationSummaryPage {
  const merged = new Map(current.map(item => [item.conversationId, item]))
  for (const item of page.items) merged.set(item.conversationId, item)
  return { items: [...merged.values()], nextCursor: page.nextCursor }
}
