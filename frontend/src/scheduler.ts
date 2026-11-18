/**
 * 调度的回读任务：同一 key 同时只允许一项在途（合并重复信号），外部可以取消全部。
 * 只用于页面侧的有界回读，不持有任何持久状态。
 */
export interface ScheduledReads {
  run(key: string, task: () => Promise<void>): Promise<void>
  cancelAll(): void
  pending(key: string): boolean
}

export function createScheduledReads(): ScheduledReads {
  const inFlight = new Map<string, Promise<void>>()
  let cancelled = false
  return {
    run(key, task) {
      const existing = inFlight.get(key)
      if (existing) return existing
      cancelled = false
      const started = (async () => {
        try {
          await task()
        } finally {
          inFlight.delete(key)
        }
      })()
      inFlight.set(key, started)
      return started
    },
    cancelAll() {
      cancelled = true
      inFlight.clear()
    },
    pending(key) {
      return !cancelled && inFlight.has(key)
    }
  }
}

export const TERMINAL_EVENT_STATUSES: ReadonlySet<string> = new Set(['COMPLETED', 'FAILED', 'CANCELLED'])
export const WAITING_STATUS = 'WAITING_FOR_USER'