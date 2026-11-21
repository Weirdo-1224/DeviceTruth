import type { ConversationView } from './api'

export function initialMessage(title: string, message: string): string {
  const normalized = title.trim()
  return normalized ? normalized + '\n' + message : message
}

export async function ensureConversation(
  current: ConversationView | null,
  create: () => Promise<ConversationView>
): Promise<ConversationView> {
  return current ?? create()
}
