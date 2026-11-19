import type { ConversationView } from './api'

export async function ensureConversation(
  current: ConversationView | null,
  create: () => Promise<ConversationView>
): Promise<ConversationView> {
  return current ?? create()
}
