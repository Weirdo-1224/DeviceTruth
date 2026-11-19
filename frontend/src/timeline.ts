/**
 * 运行时间线控制器：App.vue 的页面侧状态机。
 * 职责（契约 05-01 §5）：先读历史再开流；维护 maxSeq，只追加 seq > maxSeq 的事件（按 seq 幂等）；
 * 收到 RUN_TERMINAL 后关闭订阅但保留展示；切换 run 先 close() 旧订阅，迟到的旧 run 事件不得更新页面。
 * 不依赖 Vue，用 onChange 通知页面刷新，便于 Vitest 直接驱动。
 */

import {
  openRunEventStream,
  readRunEvents,
  type EventSourceLike,
  type RunEvent,
  type RunStreamHandle
} from './api'

export interface TimelineSnapshot {
  runId: string | null
  events: readonly RunEvent[]
  maxSeq: number
  /** true=流仍在订阅中；收到 RUN_TERMINAL、出错或 close() 后为 false。 */
  live: boolean
  terminalStatus: string | null
}

export interface RunTimelineController {
  /**
   * 切换/开始跟踪某个 run：先关闭旧订阅，清空展示，读历史后开流。
   * 同一 run 的流仍在订阅时不重复开流；流已收尾（终态或断线）时重新对齐历史并续接。
   */
  follow(runId: string): Promise<void>
  /** 关闭订阅但保留已展示的时间线（terminal 触发时控制器内部也会走到等价状态）。 */
  close(): void
  /**
   * 清空全部展示状态（切到无 run 的会话时使用，§8.6）：关流、generation+1、runId=null，
   * 事件/maxSeq/terminalStatus 重置并通知页面。迟到的旧事件由 generation 丢弃 + runId 为空双保险拦截。
   */
  clear(): void
  snapshot(): TimelineSnapshot
  onChange(listener: () => void): void
}

export interface RunTimelineOptions {
  eventSourceFactory?: (url: string) => EventSourceLike
  /** 历史加载失败、流异常收尾等问题的统一出口；页面只做轻提示，轮询仍兜底。 */
  onError?: (error: unknown) => void
}

export function createRunTimelineController(options: RunTimelineOptions = {}): RunTimelineController {
  let runId: string | null = null
  let generation = 0
  let events: RunEvent[] = []
  let maxSeq = 0
  let live = false
  let terminalStatus: string | null = null
  let handle: RunStreamHandle | null = null
  const listeners = new Set<() => void>()

  function emit(): void {
    for (const listener of [...listeners]) listener()
  }

  function snapshot(): TimelineSnapshot {
    return { runId, events: [...events], maxSeq, live, terminalStatus }
  }

  function closeStream(): void {
    if (handle) {
      handle.close()
      handle = null
    }
  }

  function append(event: RunEvent): void {
    // 幂等：重连补齐或服务端重发时只追加真正新的（seq 单调递增，按契约至少一次、按序到达）
    if (typeof event.seq !== 'number' || event.seq <= maxSeq) return
    events = [...events, event]
    maxSeq = event.seq
  }

  async function follow(targetRunId: string): Promise<void> {
    if (runId === targetRunId && live) return
    closeStream()
    generation += 1
    const generationAtStart = generation
    runId = targetRunId
    events = []
    maxSeq = 0
    live = true
    terminalStatus = null
    emit()
    try {
      const history = await readRunEvents(targetRunId)
      if (generationAtStart !== generation) return
      for (const event of history) append(event)
      emit()
      handle = openRunEventStream(targetRunId, {
        lastSeq: maxSeq,
        eventSourceFactory: options.eventSourceFactory,
        onEvent: (event) => {
          if (generationAtStart !== generation) return
          append(event)
          emit()
        },
        onTerminal: (status) => {
          if (generationAtStart !== generation) return
          live = false
          terminalStatus = status
          closeStream()
          emit()
        },
        onError: (error) => {
          if (generationAtStart !== generation) return
          live = false
          closeStream()
          emit()
          options.onError?.(error)
        }
      })
      emit()
    } catch (error) {
      if (generationAtStart !== generation) return
      live = false
      emit()
      options.onError?.(error)
    }
  }

  function close(): void {
    generation += 1
    closeStream()
    if (live) {
      live = false
      emit()
    }
  }

  function clear(): void {
    generation += 1
    closeStream()
    runId = null
    events = []
    maxSeq = 0
    live = false
    terminalStatus = null
    emit()
  }

  return {
    follow,
    close,
    clear,
    snapshot,
    onChange(listener) {
      listeners.add(listener)
    }
  }
}
