/**
 * 查看轮与活动目标的分离判定（F08）。
 * 纯函数：只根据持久 Run 列表得出「所看轮」「活动目标」「多个未决轮」与各操作的许可条件，
 * 按钮、键盘与处理函数共用同一组判定，避免视图与提交目标不一致。
 * 活动目标只来自会话 Run 列表里真实存在的非终态 Run，不由查看轮或输入框推导。
 */
import type { RunView } from './api'

export const UNSETTLED_STATUSES: readonly string[] = ['QUEUED', 'RUNNING']
export const ACTIVE_STATUSES: readonly string[] = ['QUEUED', 'RUNNING', 'WAITING_FOR_USER']

export interface RunTargets {
  /** 所看轮：固定选择的历史轮，否则按服务器顺序的最新轮。 */
  viewed: RunView | null
  /** 会话内真实存在的非终态轮，保持服务端顺序；正常只有一个。 */
  activeRuns: RunView[]
  /** 活动目标：仅在唯一时给出；多个未决轮时为 null，必须由用户明确选择。 */
  active: RunView | null
  /** 所选活动目标（用户显式指定）失效时，等待补答的轮次；仅在唯一时给出。 */
  waiting: RunView | null
}

export function pickActiveRun(runs: readonly RunView[], preferredId: string | null | undefined): RunView | null {
  const pending = runs.filter((item) => ACTIVE_STATUSES.includes(item.status))
  if (preferredId) {
    const chosen = pending.find((item) => item.runId === preferredId)
    if (chosen) return chosen
  }
  return pending.length === 1 ? pending[0] : null
}

export function resolveRunTargets(runs: readonly RunView[], pinnedRunId: string | null,
                                  preferredActiveId: string | null = null): RunTargets {
  const viewed = (pinnedRunId ? runs.find((item) => item.runId === pinnedRunId) : null) ?? runs.at(-1) ?? null
  const activeRuns = runs.filter((item) => ACTIVE_STATUSES.includes(item.status))
  const waitingRuns = activeRuns.filter((item) => item.status === 'WAITING_FOR_USER')
  const active = pickActiveRun(runs, preferredActiveId)
  return {
    viewed,
    activeRuns,
    active,
    waiting: active?.status === 'WAITING_FOR_USER' ? active : waitingRuns.length === 1 ? waitingRuns[0] : null
  }
}

export interface RunPermissions {
  /** 普通发送：无未决轮、无等待问题且当前没有在途提交。 */
  canSend: boolean
  /** 普通发送按钮/键盘被禁用但草稿需要保留的原因；空串表示允许。 */
  sendBlockedReason: string
  /** 深入调查：无未决轮且所看轮为 COMPLETED 且选中了判断。 */
  canInvestigate: boolean
  /** 取消：只针对明确的活动目标（唯一非终态轮）。 */
  canCancel: boolean
  /** 补答：存在明确的等待目标且有草稿。 */
  canReply: boolean
}

export function runPermissions(input: {
  viewed: RunView | null
  activeRuns: readonly RunView[]
  active: RunView | null
  waiting: RunView | null
  busy: boolean
  hasDraft: boolean
  hasReplyDraft: boolean
  investigationEnabled: boolean
}): RunPermissions {
  const multiple = input.activeRuns.length > 1
  const blocked = input.busy ? '正在提交上一项操作' : multiple ? '存在多个未决轮，请先逐轮定位' : ''
  const sendBlockedReason = blocked
  return {
    canSend: sendBlockedReason === '' && input.activeRuns.length === 0 && input.hasDraft,
    sendBlockedReason,
    canInvestigate: sendBlockedReason === '' && input.activeRuns.length === 0 && input.viewed?.status === 'COMPLETED'
      && input.investigationEnabled,
    canCancel: !input.busy && input.active !== null,
    canReply: !input.busy && Boolean(input.waiting?.pendingQuestion) && input.hasReplyDraft
  }
}
