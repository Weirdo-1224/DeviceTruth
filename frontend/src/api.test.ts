import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  loadCsrf,
  cancelRun,
  readAsset,
  readRunSkill,
  ApiError,
  describeFailure,
  listAssets,
  listConversationSummaries,
  listConversations,
  newRequestKey,
  openRunEventStream,
  readRun,
  readReferences,
  readRunEvents,
  replyToQuestion,
  selectContextRefs,
  sendMessage,
  uploadMarkdown,
  waitForFile,
  waitForRun,
  type RunEvent,
  type RunView
} from './api'
import { createRunTimelineController } from './timeline'

const RUN_ID = '11111111-1111-4111-8111-111111111111'
const CONVERSATION_ID = '22222222-2222-4222-8222-222222222222'
const QUESTION_ID = '33333333-3333-4333-8333-333333333333'

interface Recorded {
  url: string
  init: RequestInit
}

let calls: Recorded[] = []

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' }
  })
}

function runView(overrides: Partial<RunView> = {}): RunView {
  return {
    runId: RUN_ID,
    conversationId: CONVERSATION_ID,
    goalMessageId: '44444444-4444-4444-8444-444444444444',
    status: 'QUEUED',
    version: 0,
    requestedAt: '2026-11-11T00:00:00Z',
    asOf: '2026-11-11T00:00:00Z',
    dataNature: 'SIMULATED',
    pendingQuestion: null,
    result: null,
    failureCode: null,
    ...overrides
  }
}

function stubFetch(...responses: Response[]): void {
  let index = 0
  const mock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    calls.push({ url: String(input), init: init ?? {} })
    const response = responses[Math.min(index, responses.length - 1)]
    index += 1
    return response
  })
  vi.stubGlobal('fetch', mock)
}

/** 按 URL 决定响应，便于同一时间线里区分不同 run/游标的历史回读。 */
function stubFetchByUrl(handler: (url: string) => Response): void {
  vi.stubGlobal(
    'fetch',
    vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      calls.push({ url: String(input), init: init ?? {} })
      return handler(String(input))
    })
  )
}

function bodyOf(call: Recorded): Record<string, unknown> {
  return JSON.parse(String(call.init.body)) as Record<string, unknown>
}

beforeEach(async () => {
  vi.stubGlobal('fetch', vi.fn(async () => jsonResponse({ headerName: 'X-CSRF-TOKEN', token: 'test-csrf' })))
  await loadCsrf()
  calls = []
})

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('api 合同', () => {
  it('发送自由消息时只提交原文与协议版本，并带幂等键', async () => {
    stubFetch(jsonResponse(runView(), 202))
    const accepted = await sendMessage(CONVERSATION_ID, '新能源汽车充电中断，没有故障码', 'key-new-1')

    expect(calls).toHaveLength(1)
    expect(calls[0].url).toBe('/api/conversations/' + CONVERSATION_ID + '/messages')
    expect(calls[0].init.method).toBe('POST')
    const headers = new Headers(calls[0].init.headers)
    expect(headers.get('Idempotency-Key')).toBe('key-new-1')
    expect(headers.get('Content-Type')).toBe('application/json')
    expect(bodyOf(calls[0])).toEqual({ schemaVersion: 1, userMessage: '新能源汽车充电中断，没有故障码' })
    expect(bodyOf(calls[0]).replyTo).toBeUndefined()
    expect(accepted.runId).toBe(RUN_ID)
    expect(accepted.status).toBe('QUEUED')
  })

  it('补答提交 runId/pendingQuestionId/questionVersion 并沿用同一个 run', async () => {
    stubFetch(jsonResponse(runView({ status: 'QUEUED', version: 3 }), 202))
    const accepted = await replyToQuestion(
      CONVERSATION_ID,
      { runId: RUN_ID, pendingQuestionId: QUESTION_ID, questionVersion: 1 },
      '直流快充，SOC 约 40%',
      'key-reply-1'
    )

    // 只发一次请求，且不是“新建会话”：补答不能偷偷开启新 run
    expect(calls).toHaveLength(1)
    expect(calls[0].url).toBe('/api/conversations/' + CONVERSATION_ID + '/messages')
    expect(calls[0].url).not.toBe('/api/conversations')
    const headers = new Headers(calls[0].init.headers)
    expect(headers.get('Idempotency-Key')).toBe('key-reply-1')
    expect(bodyOf(calls[0])).toEqual({
      schemaVersion: 1,
      userMessage: '直流快充，SOC 约 40%',
      replyTo: { runId: RUN_ID, pendingQuestionId: QUESTION_ID, questionVersion: 1 }
    })
    expect(accepted.runId).toBe(RUN_ID)
  })

  it('旧问题或不匹配的补答以 STALE_OR_UNRELATED_REPLY 暴露，不会新建 run', async () => {
    stubFetch(
      jsonResponse(
        { code: 'STALE_OR_UNRELATED_REPLY', message: '请求无法处理。', retryable: false, traceId: 'trace-1' },
        409
      )
    )
    await expect(
      replyToQuestion(CONVERSATION_ID, { runId: RUN_ID, pendingQuestionId: QUESTION_ID, questionVersion: 2 }, '直流快充')
    ).rejects.toMatchObject({ code: 'STALE_OR_UNRELATED_REPLY', status: 409, traceId: 'trace-1' })
    expect(calls).toHaveLength(1)
  })

  it('缺少关联字段的补答以 REPLY_TARGET_REQUIRED 暴露', async () => {
    stubFetch(
      jsonResponse({ code: 'REPLY_TARGET_REQUIRED', message: '请求无法处理。', retryable: false, traceId: 'trace-2' }, 400)
    )
    await expect(
      replyToQuestion(CONVERSATION_ID, { runId: RUN_ID, pendingQuestionId: '', questionVersion: 1 }, '补答')
    ).rejects.toBeInstanceOf(ApiError)
  })

  it('轮询在 WAITING_FOR_USER 停止，并把每次状态交给界面', async () => {
    stubFetch(
      jsonResponse(runView({ status: 'QUEUED' })),
      jsonResponse(
        runView({
          status: 'WAITING_FOR_USER',
          pendingQuestion: {
            pendingQuestionId: QUESTION_ID,
            questionVersion: 1,
            question: '这次充电中断发生在交流慢充还是直流快充？',
            requestedFields: ['充电方式'],
            askedAt: '2026-11-11T00:00:01Z'
          }
        })
      )
    )
    const seen: string[] = []
    const finished = await waitForRun(RUN_ID, { sleep: async () => {}, onUpdate: (run) => seen.push(run.status) })

    expect(seen).toEqual(['QUEUED', 'WAITING_FOR_USER'])
    expect(calls).toHaveLength(2)
    expect(calls[0].url).toBe('/api/runs/' + RUN_ID)
    expect(finished.pendingQuestion?.pendingQuestionId).toBe(QUESTION_ID)
  })

  it('轮询在 CANCELLED 同样停止（取消是终态，不继续空等）', async () => {
    stubFetch(
      jsonResponse(runView({ status: 'RUNNING' })),
      jsonResponse(runView({ status: 'CANCELLED' }))
    )
    const seen: string[] = []
    const finished = await waitForRun(RUN_ID, { sleep: async () => {}, onUpdate: (run) => seen.push(run.status) })

    expect(seen).toEqual(['RUNNING', 'CANCELLED'])
    expect(finished.status).toBe('CANCELLED')
    expect(calls).toHaveLength(2)
  })

  it('readReferences 回读失败/取消轮已登记证据，字段原样返回', async () => {
    const registered = [
      {
        referenceId: 'ref-failed-1',
        sourceType: 'KNOWLEDGE_DOCUMENT',
        sourceId: 'doc-9',
        sourceVersion: '2026.1',
        chunkId: 'chunk-9',
        title: '维修手册',
        page: null,
        section: '4.2 充电中断',
        excerpt: '红灯常亮优先检查供电。',
        dataNature: 'AUTHORIZED'
      }
    ]
    stubFetch(jsonResponse(registered))
    const refs = await readReferences(RUN_ID)

    expect(calls[0].url).toBe('/api/runs/' + RUN_ID + '/references')
    expect(refs).toEqual(registered)
    expect(refs[0].page).toBeNull()
    expect(refs[0].section).toBe('4.2 充电中断')
  })

  it('轮询到 COMPLETED 时返回答案与引用', async () => {
    stubFetch(
      jsonResponse(
        runView({
          status: 'COMPLETED',
          result: {
            summary: '资料建议先读取充电桩故障日志。',
            category: 'POSSIBLE_CAUSES',
            claims: [{ text: '先读取充电桩故障日志', referenceIds: ['ref-1'], certainty: 'SUPPORTED' }],
            suggestions: ['记录中断时间'],
            uncertainties: [],
            limitations: ['不代表本车当前配置'],
            references: [
              {
                referenceId: 'ref-1',
                sourceType: 'KNOWLEDGE_DOCUMENT',
                sourceId: 'doc-1',
                sourceVersion: '2026.1',
                chunkId: 'chunk-1',
                title: '充电系统检修手册（演示资料）',
                page: null,
                section: '3 直流快充中断',
                excerpt: '直流快充过程中充电中断…',
                dataNature: 'SIMULATED'
              }
            ],
            riskLevel: 'MEDIUM',
            safetyDisposition: 'NORMAL',
            dataNature: 'SIMULATED'
          }
        })
      )
    )
    const finished = await waitForRun(RUN_ID, { sleep: async () => {} })
    expect(finished.status).toBe('COMPLETED')
    expect(finished.result?.references).toHaveLength(1)
    expect(finished.result?.references[0].section).toBe('3 直流快充中断')
  })

  it('轮询到 FAILED 时返回失败状态而不是静默重试', async () => {
    stubFetch(jsonResponse(runView({ status: 'FAILED' })))
    const failed = await waitForRun(RUN_ID, { sleep: async () => {} })
    expect(failed.status).toBe('FAILED')
    expect(failed.result).toBeNull()
    expect(calls).toHaveLength(1)
  })

  it('失败原因码映射成可读说明，未知码原样显示，不泄露内部细节', () => {
    expect(describeFailure('MODEL_CONFIGURATION_MISSING')).toContain('模型未配置')
    expect(describeFailure('MODEL_TRANSPORT_OR_TIMEOUT')).toContain('超时')
    expect(describeFailure('QUEUE_REJECTED')).toContain('队列已满')
    expect(describeFailure('UNCONFIRMED_FINDING')).toContain('现场核验')
    expect(describeFailure('UNSAFE_ANSWER')).toContain('不安全')
    expect(describeFailure('SOME_NEW_CODE')).toContain('SOME_NEW_CODE')
    expect(describeFailure(null)).toContain('没有返回原因码')
  })

  it('失败运行把原因码透传给页面，而不是只有“执行失败”', async () => {
    stubFetch(jsonResponse(runView({ status: 'FAILED', failureCode: 'MODEL_CONFIGURATION_MISSING' })))
    const failed = await waitForRun(RUN_ID, { sleep: async () => {} })
    expect(failed.failureCode).toBe('MODEL_CONFIGURATION_MISSING')
    expect(describeFailure(failed.failureCode)).toContain('模型未配置')
  })

  it('超时只抛 RUN_WAIT_TIMEOUT，不重发消息也不新建 run', async () => {
    stubFetch(jsonResponse(runView({ status: 'QUEUED' })))
    await expect(waitForRun(RUN_ID, { timeoutMs: 0, sleep: async () => {} })).rejects.toMatchObject({
      code: 'RUN_WAIT_TIMEOUT'
    })
    expect(calls).toHaveLength(1)
  })

  it('会话切换时取消轮询：抛出 RUN_WAIT_CANCELLED 且不再发起新请求', async () => {
    const controller = new AbortController()
    stubFetchByUrl(() => jsonResponse(runView({ status: 'RUNNING' })))
    const promise = waitForRun(RUN_ID, {
      intervalMs: 1,
      signal: controller.signal,
      sleep: async () => { controller.abort() }
    })
    await expect(promise).rejects.toMatchObject({ code: 'RUN_WAIT_CANCELLED' })
    expect(calls.filter((call) => call.url === '/api/runs/' + RUN_ID)).toHaveLength(1)
  })

  it('读取运行与引用都按 runId 定位，并保留统一排查对象', async () => {
    stubFetch(jsonResponse(runView({
      status: 'RUNNING', investigatedAssetId: 'EV-DEMO-017', assetResolution: 'CONFIRMED',
      changedFromAssetId: 'OFFICE-1'
    })), jsonResponse([]))
    const current = await readRun(RUN_ID)
    expect(current.status).toBe('RUNNING')
    expect(current.investigatedAssetId).toBe('EV-DEMO-017')
    expect(current.assetResolution).toBe('CONFIRMED')
    expect(current.changedFromAssetId).toBe('OFFICE-1')
    expect(await readReferences(RUN_ID)).toEqual([])
    expect(calls.map((call) => call.url)).toEqual(['/api/runs/' + RUN_ID, '/api/runs/' + RUN_ID + '/references'])
  })

  it('Markdown 上传使用 multipart 且不覆盖浏览器边界 Content-Type', async () => {
    stubFetch(jsonResponse({
      fileId: 'file-1', originalName: 'manual.md', mime: 'text/markdown', checksum: 'a'.repeat(64),
      byteSize: 20, parseStatus: 'PARSED', failureCode: null, documentId: 'doc-1', title: '手册',
      documentVersion: '1.0', domain: 'general', createdAt: '2026-11-11T00:00:00Z'
    }, 202))
    const result = await uploadMarkdown(new File(['# 章节\n正文'], 'manual.md', { type: 'text/markdown' }))

    expect(result.fileId).toBe('file-1')
    expect(calls[0].url).toBe('/api/files')
    expect(calls[0].init.body).toBeInstanceOf(FormData)
    expect(new Headers(calls[0].init.headers).get('Content-Type')).toBeNull()
    const form = calls[0].init.body as FormData
    expect(form.get('version')).toBe('1.0')
    expect(form.get('file')).toBeInstanceOf(File)
  })

  it('文件导入返回失败状态而不是伪装完成', async () => {
    stubFetch(jsonResponse({ fileId: 'file-1', parseStatus: 'FAILED', failureCode: 'INVALID_TEXT_ENCODING' }))
    const failed = await waitForFile('file-1')
    expect(failed.parseStatus).toBe('FAILED')
    expect(failed.failureCode).toBe('INVALID_TEXT_ENCODING')
    expect(calls[0].url).toBe('/api/files/file-1')
  })

  it('发送消息只提交已授权文件 fileId 引用', async () => {
    stubFetch(jsonResponse(runView(), 202))
    await sendMessage(CONVERSATION_ID, '结合附件回答', 'key-attachment', ['file-1'])
    expect(bodyOf(calls[0])).toEqual({
      schemaVersion: 1,
      userMessage: '结合附件回答',
      attachments: [{ fileId: 'file-1' }]
    })
  })

  it('深入调查提交选中判断的继承引用：仅 referenceId、无 replyTo、保留原设备', async () => {
    stubFetch(jsonResponse(runView({ runId: '55555555-5555-4555-8555-555555555555' }), 202))
    await sendMessage(
      CONVERSATION_ID,
      '请深入调查以下候选判断（尚未确诊）：充电中断与 BMS 有关。',
      'key-investigate-1',
      [],
      'CAR-B',
      ['ref-a', 'ref-b']
    )

    expect(calls).toHaveLength(1)
    const headers = new Headers(calls[0].init.headers)
    expect(headers.get('Idempotency-Key')).toBe('key-investigate-1')
    expect(bodyOf(calls[0])).toEqual({
      schemaVersion: 1,
      userMessage: '请深入调查以下候选判断（尚未确诊）：充电中断与 BMS 有关。',
      selectedAssetId: 'CAR-B',
      contextRefs: [{ referenceId: 'ref-a' }, { referenceId: 'ref-b' }]
    })
    expect(bodyOf(calls[0]).replyTo).toBeUndefined()
  })

  it('深入调查不传 contextRefs 时请求体保持旧合同，兼容原有调用', async () => {
    stubFetch(jsonResponse(runView(), 202))
    await sendMessage(CONVERSATION_ID, '普通补充现场信息', 'key-plain')
    expect(bodyOf(calls[0])).toEqual({ schemaVersion: 1, userMessage: '普通补充现场信息' })
  })

  it('selectContextRefs 保持原顺序去重', () => {
    expect(selectContextRefs(['ref-1', 'ref-2', 'ref-1', '', 'ref-3', 'ref-2'])).toEqual({
      referenceIds: ['ref-1', 'ref-2', 'ref-3'],
      omittedCount: 0
    })
  })

  it('selectContextRefs 最多前 10 条并给出省略数量，供按钮旁明示', () => {
    const ids = Array.from({ length: 13 }, (_, index) => 'ref-' + index)
    const selection = selectContextRefs(ids)
    expect(selection.referenceIds).toEqual(ids.slice(0, 10))
    expect(selection.omittedCount).toBe(3)
  })

  it('selectContextRefs 对空判断安全返回空清单，无引用也可调查', () => {
    expect(selectContextRefs([])).toEqual({ referenceIds: [], omittedCount: 0 })
  })

  it('每次请求都生成不同的幂等键', () => {
    const first = newRequestKey()
    const second = newRequestKey()
    expect(first).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i)
    expect(first).not.toBe(second)
  })

  it('会话摘要支持搜索、设备、状态与游标参数，并保留非当前会话待答状态', async () => {
    stubFetch(jsonResponse({
      items: [{ conversationId: CONVERSATION_ID, title: '空调不转', investigatedAssetId: 'EV-DEMO-017',
        latestRunStatus: 'WAITING_FOR_USER', activeRunCount: 1, waitingRunCount: 1, runCount: 2,
        updatedAt: '2026-11-18T00:00:00Z' }], nextCursor: 'next-1'
    }))
    const page = await listConversationSummaries({
      q: '空调 故障', assetId: 'EV-DEMO-017', status: 'WAITING_FOR_USER', cursor: 'cursor/1', limit: 20
    })
    const url = new URL(calls[0].url, 'http://local')
    expect(url.pathname).toBe('/api/conversations/summaries')
    expect(url.searchParams.get('q')).toBe('空调 故障')
    expect(url.searchParams.get('assetId')).toBe('EV-DEMO-017')
    expect(url.searchParams.get('status')).toBe('WAITING_FOR_USER')
    expect(url.searchParams.get('cursor')).toBe('cursor/1')
    expect(url.searchParams.get('limit')).toBe('20')
    expect(page.items[0].waitingRunCount).toBe(1)
    expect(page.items[0].latestRunStatus).toBe('WAITING_FOR_USER')
    expect(page.nextCursor).toBe('next-1')
  })

  it('历史会话列表按服务器身份返回，用于刷新/重开后的恢复', async () => {
    stubFetch(
      jsonResponse([
        { conversationId: CONVERSATION_ID, version: 3, createdAt: '2026-11-11T00:00:00Z', updatedAt: '2026-11-12T00:00:00Z' }
      ])
    )
    const list = await listConversations()
    expect(calls).toHaveLength(1)
    expect(calls[0].url).toBe('/api/conversations')
    expect(calls[0].init.method).toBe('GET')
    expect(list).toHaveLength(1)
    expect(list[0].conversationId).toBe(CONVERSATION_ID)
  })

  it('已登记资产清单由服务器提供，客户端不带身份参数', async () => {
    stubFetch(jsonResponse(['CAR-B']))
    const assets = await listAssets()
    expect(calls).toHaveLength(1)
    expect(calls[0].url).toBe('/api/assets')
    expect(assets).toEqual(['CAR-B'])
  })

  it('选择设备后新问题带 selectedAssetId，不选择时不提交该字段', async () => {
    stubFetch(jsonResponse(runView(), 202), jsonResponse(runView(), 202))
    await sendMessage(CONVERSATION_ID, 'CAR-B 的充电配置是什么', 'key-asset', [], 'CAR-B')
    expect(bodyOf(calls[0])).toEqual({
      schemaVersion: 1,
      userMessage: 'CAR-B 的充电配置是什么',
      selectedAssetId: 'CAR-B'
    })

    await sendMessage(CONVERSATION_ID, '没有设备的通用问题', 'key-no-asset')
    expect(bodyOf(calls[1])).toEqual({ schemaVersion: 1, userMessage: '没有设备的通用问题' })
  })
})


function runEvent(seq: number, type: string, data: Record<string, unknown> = {}): RunEvent {
  return { seq, type, recordedAt: '2026-11-13T12:00:00Z', data }
}

/** 假 EventSource：实现 api.ts 的 EventSourceLike，由测试手动派发服务端帧。 */
class FakeEventSource {
  static instances: FakeEventSource[] = []

  readonly url: string
  closed = false
  readyState = 0
  onmessage: ((event: { data: string }) => void) | null = null
  onerror: ((event: unknown) => void) | null = null
  private readonly listeners = new Map<string, Array<(event: { data: string }) => void>>()

  constructor(url: string) {
    this.url = url
    FakeEventSource.instances.push(this)
  }

  addEventListener(type: string, listener: (event: { data: string }) => void): void {
    const list = this.listeners.get(type) ?? []
    list.push(listener)
    this.listeners.set(type, list)
  }

  emit(type: string, data: unknown): void {
    const message = { data: typeof data === 'string' ? data : JSON.stringify(data) }
    for (const listener of this.listeners.get(type) ?? []) listener(message)
  }

  /** 模拟连接永久关闭（如服务端结束流）：readyState=CLOSED 且浏览器不再重连。 */
  failPermanently(): void {
    this.readyState = 2
    this.onerror?.(new Error('connection closed'))
  }

  /** 模拟瞬时断线：readyState 保持 CONNECTING（0），原生 EventSource 会自动按 Last-Event-ID 重连。 */
  failTransiently(): void {
    this.onerror?.(new Error('connection interrupted'))
  }

  close(): void {
    this.closed = true
    this.readyState = 2
  }
}

/**
 * 假原生 EventSource：记录赋值到原生对象上的 handler，验证默认工厂的 getter/setter 确实把
 * 包装器 onerror 转发到原生 error 事件（§8.5）。openRunEventStream 不传 eventSourceFactory 时
 * defaultEventSourceFactory 会 new 全局 EventSource，测试用 vi.stubGlobal 换成这个类。
 */
class FakeNativeEventSource {
  static instances: FakeNativeEventSource[] = []

  readonly url: string
  closed = false
  readyState = 0
  onmessage: ((event: { data: string }) => void) | null = null
  onerror: ((event: unknown) => void) | null = null

  constructor(url: string) {
    this.url = url
    FakeNativeEventSource.instances.push(this)
  }

  addEventListener(): void {
    // 具名事件监听注册与否不影响本替身要验证的 onerror 转发
  }

  close(): void {
    this.closed = true
    this.readyState = 2
  }
}

describe('运行事件与时间线', () => {
  beforeEach(() => {
    FakeEventSource.instances = []
    FakeNativeEventSource.instances = []
  })

  it('历史回读按游标请求 /events，返回按 seq 升序的事件', async () => {
    stubFetchByUrl((url) => {
      if (url === '/api/runs/' + RUN_ID + '/events')
        return jsonResponse([runEvent(1, 'RUN_QUEUED', { status: 'QUEUED' }), runEvent(2, 'RUN_TOOL', { toolName: 'search_knowledge', success: true })])
      if (url === '/api/runs/' + RUN_ID + '/events?after=1') return jsonResponse([runEvent(2, 'RUN_TOOL', { toolName: 'search_knowledge', success: true }), runEvent(3, 'RUN_MODEL', { attempt: 1, outcome: 'OK', durationMs: 800 })])
      return jsonResponse({ code: 'NOT_FOUND', message: '请求无法处理。', retryable: false, traceId: 'trace-events' }, 404)
    })

    const all = await readRunEvents(RUN_ID)
    expect(calls[0].url).toBe('/api/runs/' + RUN_ID + '/events')
    expect(all.map((event) => event.seq)).toEqual([1, 2])
    expect(all[1].data.toolName).toBe('search_knowledge')

    const incremental = await readRunEvents(RUN_ID, 1)
    expect(calls[1].url).toBe('/api/runs/' + RUN_ID + '/events?after=1')
    expect(incremental.map((event) => event.seq)).toEqual([2, 3])
  })

  it('run 不存在时历史回读以 ApiError 暴露，而不是返回空时间线', async () => {
    stubFetch(jsonResponse({ code: 'NOT_FOUND', message: '请求无法处理。', retryable: false, traceId: 'trace-404' }, 404))
    await expect(readRunEvents(RUN_ID)).rejects.toMatchObject({ code: 'NOT_FOUND', status: 404 })
    expect(calls[0].url).toBe('/api/runs/' + RUN_ID + '/events')
  })

  it('openRunEventStream 订阅 /stream，按事件类型派发且保持到达顺序', () => {
    const seen: RunEvent[] = []
    const handle = openRunEventStream(RUN_ID, {
      lastSeq: 0,
      onEvent: (event) => seen.push(event),
      eventSourceFactory: (url) => new FakeEventSource(url)
    })
    const source = FakeEventSource.instances[0]
    expect(source.url).toBe('/api/runs/' + RUN_ID + '/stream')

    source.emit('RUN_TOOL', runEvent(1, 'RUN_TOOL', { toolName: 'search_knowledge', success: true, durationMs: 120 }))
    source.emit('RUN_MODEL', runEvent(2, 'RUN_MODEL', { model: 'qwen-max', attempt: 1, outcome: 'OK', durationMs: 800 }))
    source.emit('RUN_TOOL', runEvent(3, 'RUN_TOOL', { toolName: 'search_knowledge', success: false, errorCode: 'TOOL_TIMEOUT', retryable: true }))

    expect(seen.map((event) => event.seq)).toEqual([1, 2, 3])
    expect(seen[2].data.errorCode).toBe('TOOL_TIMEOUT')
    handle.close()
    expect(source.closed).toBe(true)
  })

  it('收到 RUN_TERMINAL 后回调终态并自动关闭流', () => {
    const terminals: string[] = []
    openRunEventStream(RUN_ID, {
      lastSeq: 0,
      onEvent: () => {},
      onTerminal: (status) => terminals.push(status),
      eventSourceFactory: (url) => new FakeEventSource(url)
    })
    const source = FakeEventSource.instances[0]

    source.emit('RUN_TERMINAL', { status: 'COMPLETED' })

    expect(terminals).toEqual(['COMPLETED'])
    expect(source.closed).toBe(true)
  })

  it('流被永久关闭且未收到终态时上报 onError 并停止', () => {
    const errors: unknown[] = []
    openRunEventStream(RUN_ID, {
      lastSeq: 0,
      onEvent: () => {},
      onError: (error) => errors.push(error),
      eventSourceFactory: (url) => new FakeEventSource(url)
    })
    const source = FakeEventSource.instances[0]

    source.failPermanently()

    expect(errors).toHaveLength(1)
    expect(source.closed).toBe(true)
  })

  it('先补历史再开流；断线重连补齐时，重复 seq 不重复追加', async () => {
    const history = Array.from({ length: 12 }, (_, index) =>
      runEvent(index + 1, 'RUN_TOOL', { toolName: 'search_knowledge', success: true, durationMs: 100 })
    )
    stubFetchByUrl((url) => (url === '/api/runs/' + RUN_ID + '/events' ? jsonResponse(history) : jsonResponse([], 404)))
    const controller = createRunTimelineController({ eventSourceFactory: (url) => new FakeEventSource(url) })

    await controller.follow(RUN_ID)
    const source = FakeEventSource.instances[0]
    expect(controller.snapshot().events.map((event) => event.seq)).toEqual(history.map((event) => event.seq))

    // 模拟断线重连：服务端按 Last-Event-ID 补齐，把已收事件重放后再发新事件 13、14
    const replayed = [...history, runEvent(13, 'RUN_MODEL', { attempt: 2, outcome: 'OK', durationMs: 900 }), runEvent(14, 'RUN_ANSWER', { category: 'POSSIBLE_CAUSES', riskLevel: 'LOW', dataNature: 'SIMULATED' })]
    for (const event of replayed) source.emit(event.type, event)
    expect(controller.snapshot().events.map((event) => event.seq)).toEqual(Array.from({ length: 14 }, (_, index) => index + 1))
    expect(controller.snapshot().maxSeq).toBe(14)

    // 至少一次语义下服务端可能再重发旧事件：仍不重复渲染
    source.emit('RUN_TOOL', history[4])
    source.emit('RUN_MODEL', replayed[12])
    expect(controller.snapshot().events.map((event) => event.seq)).toEqual(Array.from({ length: 14 }, (_, index) => index + 1))
  })

  it('收到 RUN_TERMINAL 后停止订阅但保留时间线展示', async () => {
    stubFetchByUrl((url) => (url === '/api/runs/' + RUN_ID + '/events' ? jsonResponse([runEvent(1, 'RUN_QUEUED', { status: 'QUEUED' })]) : jsonResponse([], 404)))
    const controller = createRunTimelineController({ eventSourceFactory: (url) => new FakeEventSource(url) })

    await controller.follow(RUN_ID)
    const source = FakeEventSource.instances[0]
    source.emit('RUN_ANSWER', runEvent(2, 'RUN_ANSWER', { category: 'CONFIRMED_FINDING', riskLevel: 'MEDIUM', dataNature: 'SIMULATED' }))
    source.emit('RUN_TERMINAL', { status: 'COMPLETED' })

    const snapshot = controller.snapshot()
    expect(snapshot.live).toBe(false)
    expect(snapshot.terminalStatus).toBe('COMPLETED')
    expect(snapshot.events.map((event) => event.seq)).toEqual([1, 2])
    expect(source.closed).toBe(true)
  })

  it('切换 run 时关闭旧流，旧流迟到事件不更新当前页面', async () => {
    const otherRunId = '99999999-9999-4999-8999-999999999999'
    stubFetchByUrl((url) => {
      if (url === '/api/runs/' + RUN_ID + '/events') return jsonResponse([runEvent(1, 'RUN_TOOL', { toolName: 'search_knowledge', success: true })])
      if (url === '/api/runs/' + otherRunId + '/events') return jsonResponse([runEvent(1, 'RUN_QUEUED', { status: 'QUEUED' })])
      return jsonResponse([], 404)
    })
    const controller = createRunTimelineController({ eventSourceFactory: (url) => new FakeEventSource(url) })

    await controller.follow(RUN_ID)
    const firstSource = FakeEventSource.instances[0]
    await controller.follow(otherRunId)
    expect(firstSource.closed).toBe(true)
    expect(controller.snapshot().runId).toBe(otherRunId)

    // 旧 run 的迟到事件必须被忽略，当前 run 的增量正常追加
    firstSource.emit('RUN_TOOL', runEvent(2, 'RUN_TOOL', { toolName: 'late', success: true }))
    const secondSource = FakeEventSource.instances[1]
    secondSource.emit('RUN_MODEL', runEvent(2, 'RUN_MODEL', { attempt: 1, outcome: 'OK' }))

    const snapshot = controller.snapshot()
    expect(snapshot.runId).toBe(otherRunId)
    expect(snapshot.events.map((event) => event.type)).toEqual(['RUN_QUEUED', 'RUN_MODEL'])
  })

  it('重复跟随同一进行中的 run 不重复开流', async () => {
    stubFetchByUrl((url) => (url === '/api/runs/' + RUN_ID + '/events' ? jsonResponse([runEvent(1, 'RUN_QUEUED', { status: 'QUEUED' })]) : jsonResponse([], 404)))
    const controller = createRunTimelineController({ eventSourceFactory: (url) => new FakeEventSource(url) })

    await controller.follow(RUN_ID)
    await controller.follow(RUN_ID)

    expect(FakeEventSource.instances).toHaveLength(1)
    expect(calls.filter((call) => call.url.endsWith('/events'))).toHaveLength(1)
  })

  it('历史加载失败时保持关闭并透传错误，不开流也不伪装成功', async () => {
    stubFetch(jsonResponse({ code: 'NOT_FOUND', message: '请求无法处理。', retryable: false, traceId: 'trace-history' }, 404))
    const errors: unknown[] = []
    const controller = createRunTimelineController({
      eventSourceFactory: (url) => new FakeEventSource(url),
      onError: (error) => errors.push(error)
    })

    await controller.follow(RUN_ID)

    expect(controller.snapshot().live).toBe(false)
    expect(controller.snapshot().events).toEqual([])
    expect(errors).toHaveLength(1)
    expect(FakeEventSource.instances).toHaveLength(0)
  })

  it('默认事件源工厂把包装器 onerror 经 getter/setter 转发到原生 error 事件', () => {
    vi.stubGlobal('EventSource', FakeNativeEventSource)
    const errors: unknown[] = []
    openRunEventStream(RUN_ID, {
      lastSeq: 0,
      onEvent: () => {},
      onError: (error) => errors.push(error),
      maxRetryErrors: 1
    })
    const native = FakeNativeEventSource.instances[0]
    expect(native.url).toBe('/api/runs/' + RUN_ID + '/stream')
    // openRunEventStream 给包装器赋值 onerror 后，原生连接上必须能读到同一个 handler
    expect(native.onerror).not.toBeNull()

    // 触发原生 error：handler 经包装器转发生效，且达阈值后 close 也转发到原生连接
    native.onerror?.(new Error('boom'))
    expect(errors).toHaveLength(1)
    expect(native.closed).toBe(true)
  })

  it('连续 onerror 达默认阈值 8 且期间无成功事件时判永久失败：控制器置 live=false 并关流', async () => {
    stubFetchByUrl((url) => (url === '/api/runs/' + RUN_ID + '/events' ? jsonResponse([]) : jsonResponse([], 404)))
    const errors: unknown[] = []
    const controller = createRunTimelineController({
      eventSourceFactory: (url) => new FakeEventSource(url),
      onError: (error) => errors.push(error)
    })

    await controller.follow(RUN_ID)
    const source = FakeEventSource.instances[0]
    expect(controller.snapshot().live).toBe(true)

    for (let i = 0; i < 7; i++) source.failTransiently()
    expect(errors).toHaveLength(0)
    expect(controller.snapshot().live).toBe(true)

    source.failTransiently()
    expect(errors).toHaveLength(1)
    expect(controller.snapshot().live).toBe(false)
    expect(source.closed).toBe(true)
  })

  it('阈值内的瞬时错误后收到成功事件会重置计数，不误报永久失败', () => {
    const errors: unknown[] = []
    const seen: RunEvent[] = []
    openRunEventStream(RUN_ID, {
      lastSeq: 0,
      onEvent: (event) => seen.push(event),
      onError: (error) => errors.push(error),
      maxRetryErrors: 2,
      eventSourceFactory: (url) => new FakeEventSource(url)
    })
    const source = FakeEventSource.instances[0]

    source.failTransiently()
    source.emit('RUN_TOOL', runEvent(1, 'RUN_TOOL', { toolName: 'search_knowledge', success: true }))
    source.failTransiently()

    expect(errors).toHaveLength(0)
    expect(seen.map((event) => event.seq)).toEqual([1])
    expect(source.closed).toBe(false)
  })

  it('RUN_WAITING 经具名监听器派发到 onEvent，且非终态不关闭流', () => {
    const seen: RunEvent[] = []
    openRunEventStream(RUN_ID, {
      lastSeq: 0,
      onEvent: (event) => seen.push(event),
      eventSourceFactory: (url) => new FakeEventSource(url)
    })
    const source = FakeEventSource.instances[0]

    source.emit('RUN_WAITING', runEvent(1, 'RUN_WAITING', { questionVersion: 2 }))

    expect(seen).toHaveLength(1)
    expect(seen[0].type).toBe('RUN_WAITING')
    expect(seen[0].data.questionVersion).toBe(2)
    expect(source.closed).toBe(false)
  })

  it('clear() 后快照全空并通知页面，迟到的旧事件被忽略', async () => {
    stubFetchByUrl((url) =>
      url === '/api/runs/' + RUN_ID + '/events'
        ? jsonResponse([runEvent(1, 'RUN_QUEUED', { status: 'QUEUED' })])
        : jsonResponse([], 404)
    )
    const controller = createRunTimelineController({ eventSourceFactory: (url) => new FakeEventSource(url) })
    let changes = 0
    controller.onChange(() => {
      changes += 1
    })

    await controller.follow(RUN_ID)
    const source = FakeEventSource.instances[0]
    source.emit('RUN_TOOL', runEvent(2, 'RUN_TOOL', { toolName: 'search_knowledge', success: true }))
    expect(controller.snapshot().events.map((event) => event.seq)).toEqual([1, 2])

    controller.clear()
    const cleared = controller.snapshot()
    expect(cleared.runId).toBeNull()
    expect(cleared.events).toEqual([])
    expect(cleared.maxSeq).toBe(0)
    expect(cleared.live).toBe(false)
    expect(cleared.terminalStatus).toBeNull()
    expect(source.closed).toBe(true)
    expect(changes).toBeGreaterThan(0)

    // 旧流迟到事件：generation 已变 + runId 已空，双保险下不会填回展示
    source.emit('RUN_MODEL', runEvent(3, 'RUN_MODEL', { attempt: 1, outcome: 'OK' }))
    expect(controller.snapshot().events).toEqual([])
  })

  it('onChange 快照反映新增事件与终态，页面据此响应式更新而不是读非响应式对象', async () => {
    const history = [runEvent(1, 'RUN_QUEUED', { status: 'QUEUED' })]
    stubFetchByUrl((url) => (url.includes('/events') ? jsonResponse(history) : jsonResponse([])))
    const controller = createRunTimelineController({ eventSourceFactory: (url) => new FakeEventSource(url) })
    const snapshots: Array<{ runId: string | null; count: number; terminal: string | null }> = []
    controller.onChange(() => {
      const state = controller.snapshot()
      snapshots.push({ runId: state.runId, count: state.events.length, terminal: state.terminalStatus })
    })
    await controller.follow(RUN_ID)
    const source = FakeEventSource.instances.at(-1)!
    source.emit('RUN_WAITING', runEvent(2, 'RUN_WAITING', { questionVersion: 1 }))
    source.emit('RUN_TERMINAL', { status: 'WAITING_FOR_USER' })
    expect(snapshots.at(-1)).toEqual({ runId: RUN_ID, count: 2, terminal: 'WAITING_FOR_USER' })
    expect(controller.snapshot().live).toBe(false)
  })

  it('同一 run 流已断线后再次 follow 只做历史回读，不重复追加已展示的 seq', async () => {
    const history = [runEvent(1, 'RUN_QUEUED', { status: 'QUEUED' }), runEvent(2, 'RUN_TOOL', { toolName: 'x', success: true })]
    stubFetchByUrl((url) => (url.includes('/events') ? jsonResponse(history) : jsonResponse([])))
    const controller = createRunTimelineController({ eventSourceFactory: (url) => new FakeEventSource(url) })
    await controller.follow(RUN_ID)
    FakeEventSource.instances.at(-1)!.failPermanently()
    expect(controller.snapshot().live).toBe(false)
    await controller.follow(RUN_ID)
    expect(controller.snapshot().events.map((event) => event.seq)).toEqual([1, 2])
    expect(FakeEventSource.instances).toHaveLength(2)
  })

  it('SSE 先于下一次轮询通知 WAITING：按持久 Run 回读可立即得到等待态与待答问题', async () => {
    stubFetchByUrl((url) => {
      if (url === '/api/runs/' + RUN_ID) {
        return jsonResponse(runView({
          status: 'WAITING_FOR_USER',
          version: 2,
          pendingQuestion: { pendingQuestionId: QUESTION_ID, questionVersion: 1, question: '请补充充电方式', requestedFields: ['充电方式'], askedAt: '2026-11-17T00:00:00Z' }
        }))
      }
      return jsonResponse([])
    })
    const controller = createRunTimelineController({ eventSourceFactory: (url) => new FakeEventSource(url) })
    /** 模拟页面侧回读：收到 RUN_WAITING 就立刻按提示的顺序读取持久 Run。 */
    const signals: string[] = []
    const observed: string[] = []
    controller.onChange(() => {
      const state = controller.snapshot()
      for (const event of state.events) {
        const key = event.type + ':' + event.seq
        if (signals.includes(key)) continue
        signals.push(key)
        if (event.type === 'RUN_WAITING') observed.push('waiting-signal')
      }
    })
    await controller.follow(RUN_ID)
    const source = FakeEventSource.instances.at(-1)!
    source.emit('RUN_WAITING', runEvent(1, 'RUN_WAITING', { questionVersion: 1 }))
    expect(observed).toEqual(['waiting-signal'])
    // 无需增量事件：立即回读持久 Run 即可得到等待态
    const view = await readRun(RUN_ID)
    expect(view.status).toBe('WAITING_FOR_USER')
    expect(view.pendingQuestion?.question).toBe('请补充充电方式')
    // 重放同一事件不会产生第二条时间线记录
    source.emit('RUN_WAITING', runEvent(1, 'RUN_WAITING', { questionVersion: 1 }))
    expect(controller.snapshot().events).toHaveLength(1)
  })

  it('取消运行：POST /cancel 返回更新后的 RunView；终态再取消把 409 映射为 ApiError', async () => {
    stubFetchByUrl((url) =>
      url === '/api/runs/' + RUN_ID + '/cancel'
        ? jsonResponse({ runId: RUN_ID, status: 'CANCELLED' })
        : jsonResponse({ code: 'RUN_ALREADY_TERMINAL', message: '已结束', retryable: false, traceId: 't' }, 409)
    )
    const updated = await cancelRun(RUN_ID)
    expect(updated.status).toBe('CANCELLED')
    stubFetchByUrl(() => jsonResponse(
      { code: 'RUN_ALREADY_TERMINAL', message: '已结束', retryable: false, traceId: 't' }, 409))
    await expect(cancelRun(RUN_ID)).rejects.toMatchObject({ code: 'RUN_ALREADY_TERMINAL', status: 409 })
  })

  it('领域 Skill：已选择时返回版本与适用范围，SKILL_NOT_SELECTED 归一为 null', async () => {
    stubFetchByUrl((url) =>
      url === '/api/runs/' + RUN_ID + '/skill'
        ? jsonResponse({ skillId: 'automotive-diagnosis', version: '1.0.0',
            devices: ['car'], tasks: ['diagnosis'] })
        : jsonResponse({ code: 'SKILL_NOT_SELECTED', message: '未选择', retryable: false, traceId: 't' }, 404)
    )
    const skill = await readRunSkill(RUN_ID)
    expect(skill?.version).toBe('1.0.0')
    expect(skill?.skillId).toBe('automotive-diagnosis')
    expect(skill?.devices).toEqual(['car'])
    expect(skill?.tasks).toEqual(['diagnosis'])
    expect(await readRunSkill('22222222-2222-4222-8222-222222222222')).toBeNull()
  })

  it('资产组合视图：按 assetId 读取配置/维修记录，404 映射为 ApiError', async () => {
    stubFetchByUrl((url) =>
      url === '/api/assets/CAR-B'
        ? jsonResponse({
            assetId: 'CAR-B', ownerId: 'o', projectId: 'p', asOf: '2026-11-13T00:00:00Z',
            configurations: [{ recordId: 'r1', componentId: 'BMS', attribute: 'moduleVersion', value: 'D-120A',
              sourceType: 'FACTORY', sourceId: 's1', sourceRef: 'ref', dataNature: 'SIMULATED',
              verificationStatus: 'DOCUMENTED', authority: 1, validFrom: '2024-03-12T00:00:00Z', validTo: null,
              recordedAt: '2024-03-12T00:00:00Z', observedAt: null }],
            maintenance: [], installationStatus: 'UNCONFIRMED', conflict: 'CONFLICT',
            requiredChecks: ['现场核验'], limitations: ['模拟数据'], dataNature: 'SIMULATED'
          })
        : jsonResponse({ code: 'NOT_FOUND_OR_DENIED', message: '不存在', retryable: false, traceId: 't' }, 404)
    )
    const asset = await readAsset('CAR-B')
    expect(asset.configurations[0]?.value).toBe('D-120A')
    expect(asset.conflict).toBe('CONFLICT')
    await expect(readAsset('UNKNOWN')).rejects.toMatchObject({ code: 'NOT_FOUND_OR_DENIED' })
  })
})
