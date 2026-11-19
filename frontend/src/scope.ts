/**
 * 所看轮作用域（F08）：每次读取都带一个自增代次，回调写回前必须同时满足
 * 「代次未变」与「作用域键一致」，用于拦截快速切轮、切会话与 A→B→A 的迟到响应。
 */
export interface ScopeToken {
  readonly key: string
  readonly generation: number
}

export interface ScopedLoader {
  /** 当前代次；切轮/切会话/显式重试时递增，旧的在途回调随后一律丢弃。 */
  generation(): number
  /** 为作用域键发一个令牌并递增代次（新的读取开始）。 */
  issue(key: string): ScopeToken
  /** 只有代次最新且若提供 currentKey 时与令牌键一致才为真。 */
  isCurrent(token: ScopeToken, currentKey?: string): boolean
}

export function createScopedLoader(): ScopedLoader {
  let current = 0
  return {
    generation: () => current,
    issue(key) {
      current += 1
      return { key, generation: current }
    },
    isCurrent(token, currentKey) {
      if (token.generation !== current) return false
      return currentKey === undefined || token.key === currentKey
    }
  }
}

/** Run 视图同轮合并：只接受真正推进的版本，等待→执行属于合法推进，不能按状态名排序。 */
export function mergeRunView(previous: { runId: string; status: string; version: number } | undefined,
                             incoming: { runId: string; status: string; version: number }): boolean {
  if (!previous) return true
  if (previous.runId !== incoming.runId) return true
  if (incoming.version > previous.version) return true
  // 同版本同状态只做字段合并（如 activeDurationMs 补齐）；等待态不能覆盖执行态
  return incoming.version === previous.version && previous.status === incoming.status
}

export function mergeRunList<Run extends { runId: string; status: string; version: number }>(
  previous: readonly Run[], incoming: readonly Run[]
): Run[] {
  const known = new Map(previous.map(run => [run.runId, run]))
  const merged = incoming.map(run => {
    const existing = known.get(run.runId)
    known.delete(run.runId)
    return existing && !mergeRunView(existing, run) ? existing : run
  })
  return [...merged, ...known.values()]
}
