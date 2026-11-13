import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  ApiError,
  describeFailure,
  listAssets,
  listConversations,
  newRequestKey,
  readRun,
  readReferences,
  replyToQuestion,
  sendMessage,
  uploadMarkdown,
  waitForFile,
  waitForRun,
  type RunView
} from './api'

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

function bodyOf(call: Recorded): Record<string, unknown> {
  return JSON.parse(String(call.init.body)) as Record<string, unknown>
}

beforeEach(() => {
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

  it('读取运行与引用都按 runId 定位', async () => {
    stubFetch(jsonResponse(runView({ status: 'RUNNING' })), jsonResponse([]))
    expect((await readRun(RUN_ID)).status).toBe('RUNNING')
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

  it('每次请求都生成不同的幂等键', () => {
    const first = newRequestKey()
    const second = newRequestKey()
    expect(first).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i)
    expect(first).not.toBe(second)
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
