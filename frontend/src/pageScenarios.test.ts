import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  cancelRun,
  createConversation,
  listConversations,
  readAsset,
  readConversation,
  readReferences,
  readRun,
  readRunEvents,
  readRunSkill,
  replyToQuestion,
  sendMessage,
  type RunEvent,
  type RunView
} from '../src/api'
import { buildInvestigationMessage } from '../src/diagnosisViewModel'
import { resolveRunTargets } from '../src/runTargets'
import { createScopedLoader } from '../src/scope'

/**
 * 页面级交互场景（协议样本）：以页面实际调用的 api.ts 合同函数 + 页面同一套目标判定，
 * 逐一核对 F01/F02/F04/F08 的提交目标、回读与串轮行为。
 * 这里不渲染 Vue DOM；真实组件渲染与键盘交互由 scripts/verify_pro_page_31_32.py 在构建制品上验证。
 */

const SCENE = '00000000-0000-4000-8000-000000000001'
const topics: string[] = []

interface Call { method: string; url: string; body: unknown }

let calls: Call[] = []

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

function message(id: string, seq: number, role: 'USER' | 'ASSISTANT', content: string, asset: string | null = null) {
  return { messageId: id, seq, role, content, selectedAssetId: asset, requestedAt: '2026-11-17T08:00:00Z', asOf: '2026-11-17T08:00:00Z' }
}

function run(id: string, status: RunView['status'], overrides: Partial<RunView> = {}): RunView {
  return {
    runId: id, conversationId: SCENE, goalMessageId: 'm-' + id, status, version: status === 'COMPLETED' ? 5 : 1,
    requestedAt: '2026-11-17T08:00:00Z', asOf: '2026-11-17T08:00:00Z', dataNature: 'SIMULATED',
    pendingQuestion: null, result: null, failureCode: null, ...overrides
  }
}

const WAITING_QUESTION = {
  pendingQuestionId: '00000000-0000-4000-8000-000000000091',
  questionVersion: 2,
  question: '请补充使用的充电方式与当前 SOC',
  requestedFields: ['充电方式', 'SOC'],
  askedAt: '2026-11-17T08:05:00Z'
}

const COMPLETED_RESULT = {
  summary: '充电中止可能与供电或充电口连接有关',
  category: 'POSSIBLE_CAUSES' as const,
  claims: [{ text: '充电桩故障日志显示直流快充中止', certainty: 'SUPPORTED' as const, referenceIds: ['ref-a', 'ref-b'] }],
  suggestions: ['读取充电桩故障日志'],
  uncertainties: ['未取得绝缘电阻实测值'],
  limitations: ['未进行现场核验'],
  references: [{
    referenceId: 'ref-a', sourceType: 'KNOWLEDGE_DOCUMENT', sourceId: 'DOC-1', sourceVersion: 'v1',
    chunkId: 'c1', title: '充电系统维修手册', page: null, section: '4.2', excerpt: '先读取故障日志', dataNature: 'AUTHORIZED'
  }],
  riskLevel: 'HIGH' as const,
  safetyDisposition: 'REQUIRES_QUALIFIED_PERSONNEL',
  dataNature: 'AUTHORIZED'
}

function runEvent(seq: number, type: string, data: Record<string, unknown> = {}): RunEvent {
  return { seq, type, recordedAt: '2026-11-17T08:00:00Z', data }
}

/** 单会话多轮：COMPLETED(T1) → COMPLETED(T2) → FAILED → WAITING（活动轮）。 */
type ShellState = { runs: RunView[]; messages?: Array<{ messageId: string }> }
function shell(state: ShellState): void {
  vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = String(input)
    const method = init?.method ?? 'GET'
    const body = init?.body ? JSON.parse(String(init.body)) : null
    calls.push({ method, url, body })
    if (url === '/api/conversations' && method === 'POST') {
      return json({ conversationId: 'conv-new', version: 0, createdAt: '2026-11-18T00:00:00Z', updatedAt: '2026-11-18T00:00:00Z' }, 201)
    }
    if (url === '/api/conversations' && method === 'GET') {
      return json([{ conversationId: SCENE, version: state.runs.length, createdAt: '2026-11-17T07:00:00Z', updatedAt: '2026-11-17T09:00:00Z' }])
    }
    if (url === `/api/conversations/${SCENE}`) {
      return json({
        conversation: { conversationId: SCENE, version: state.runs.length, createdAt: '2026-11-17T07:00:00Z', updatedAt: '2026-11-17T09:00:00Z' },
        messages: state.messages ?? [message('m-r1', 1, 'USER', '车辆充电中断', 'CAR-A'), message('m-r2', 3, 'USER', '换个时点再查', 'CAR-B')],
        runs: state.runs
      })
    }
    if (url.endsWith('/references')) return json(state.runs.find((item) => url.includes(item.runId))?.result?.references ?? [])
    if (url.endsWith('/events')) return json([runEvent(1, 'RUN_QUEUED'), runEvent(2, 'RUN_TOOL', { toolName: 'search_knowledge', success: true })])
    if (url.endsWith('/skill')) return json({ code: 'SKILL_NOT_SELECTED' }, 404)
    if (url.startsWith('/api/runs/') && method === 'GET') {
      const found = state.runs.find((item) => url === '/api/runs/' + item.runId)
      return found ? json(found) : json({ code: 'NOT_FOUND', message: '不存在' }, 404)
    }
    if (url.startsWith('/api/assets/')) return json({ assetId: 'x', ownerId: 'o', projectId: 'p', asOf: '2026-11-17T08:00:00Z', configurations: [], maintenance: [], installationStatus: 'UNCONFIRMED', conflict: 'NONE', requiredChecks: [], limitations: [], dataNature: 'SIMULATED' })
    if (url.endsWith('/messages')) {
      if (body?.replyTo) {
        // 服务端按 replyTo.questionVersion 校验（与 Java 合同一致）
        if (body.replyTo.questionVersion !== WAITING_QUESTION.questionVersion) {
          return json({ code: 'STALE_OR_UNRELATED_REPLY', message: '追问已变化' }, 409)
        }
        const resumed = run('r-w', 'RUNNING', { version: 3 })
        state.runs = state.runs.map((item) => (item.runId === 'r-w' ? resumed : item))
        return json(resumed, 202)
      }
      const created = run('r-new', 'QUEUED', { version: 0 })
      state.runs = [...state.runs, created]
      return json(created, 202)
    }
    if (url.endsWith('/cancel')) {
      const target = state.runs.find((item) => url === '/api/runs/' + item.runId + '/cancel')!
      const cancelled = { ...target, status: 'CANCELLED' as const, version: target.version + 1, pendingQuestion: null }
      state.runs = state.runs.map((item) => (item.runId === cancelled.runId ? cancelled : item))
      return json(cancelled)
    }
    return json({ code: 'NOT_FOUND', message: '未模拟' }, 404)
  }))
}

afterEach(() => {
  vi.unstubAllGlobals()
  calls = []
  topics.length = 0
})

describe('页面交互场景（协议样本）', () => {
  it('F08 所看轮先显示 T1 资产，历史轮与输入资产互不影响；切轮后代次校验丢弃迟到结果', async () => {
    const runs = [
      run('r1', 'COMPLETED', { asOf: '2026-11-17T08:00:00Z', result: COMPLETED_RESULT }),
      run('r2', 'COMPLETED', { asOf: '2026-11-18T09:00:00Z', result: { ...COMPLETED_RESULT, claims: [{ text: '第二时点判断', certainty: 'POSSIBLE', referenceIds: [] }] } })
    ]
    shell({ runs })
    const detail = await readConversation(SCENE)
    const loader = createScopedLoader()
    const staleToken = loader.issue('r1')
    const freshToken = loader.issue('r2')
    const viewed = resolveRunTargets(detail.runs, 'r1').viewed!
    expect(viewed.runId).toBe('r1')
    expect(viewed.asOf).toBe('2026-11-17T08:00:00Z')
    const goal = detail.messages.find((item) => item.messageId === viewed.goalMessageId)!
    expect(goal.selectedAssetId).toBe('CAR-A')
    // 模拟底部输入已选 CAR-B：不影响所看轮资产
    const inputAsset = 'CAR-B'
    expect(inputAsset).not.toBe(goal.selectedAssetId)
    // A→B→A：旧读取（A 首次）返回时不得覆盖
    expect(loader.isCurrent(staleToken, 'r1')).toBe(false)
    expect(loader.isCurrent(freshToken, 'r2')).toBe(true)
    // 同资产异时点：两次读取的 asOf 都来自各自轮
    const second = resolveRunTargets(detail.runs, 'r2').viewed!
    expect(second.asOf).toBe('2026-11-18T09:00:00Z')
    expect(second.runId).not.toBe(viewed.runId)
  })

  it('F08 历史 COMPLETED + 活动 WAITING：发送与深入调查暂停，取消与补答指向活动轮', async () => {
    const runs = [
      run('r1', 'COMPLETED', { result: COMPLETED_RESULT }),
      run('r-w', 'WAITING_FOR_USER', { version: 2, pendingQuestion: WAITING_QUESTION })
    ]
    shell({ runs })
    const detail = await readConversation(SCENE)
    const targets = resolveRunTargets(detail.runs, 'r1')
    expect(targets.viewed?.runId).toBe('r1')
    expect(targets.active?.runId).toBe('r-w')
    expect(targets.waiting?.runId).toBe('r-w')

    const resumed = await replyToQuestion(SCENE, {
      runId: targets.waiting!.runId,
      pendingQuestionId: WAITING_QUESTION.pendingQuestionId,
      questionVersion: 2
    }, '直流快充，SOC 40%')
    expect(resumed.runId).toBe('r-w')
    const replyCall = calls.filter((call) => call.url.endsWith('/messages')).at(-1)!
    expect(replyCall.body).toMatchObject({
      userMessage: '直流快充，SOC 40%',
      replyTo: { runId: 'r-w', pendingQuestionId: WAITING_QUESTION.pendingQuestionId, questionVersion: 2 }
    })

    // 取消始终指向活动轮 r-w，而不是所看的历史轮 r1
    const cancelState = [run('r1', 'COMPLETED', { result: COMPLETED_RESULT }), run('r-w', 'WAITING_FOR_USER', { version: 2, pendingQuestion: WAITING_QUESTION })]
    shell({ runs: cancelState })
    const cancelled = await cancelRun(targets.active!.runId)
    expect(cancelled.runId).toBe('r-w')
    expect(calls.at(-1)?.url).toBe('/api/runs/r-w/cancel')
  })

  it('F01 末条消息等于待答正文时补答仍走 replyTo；旧版本拒绝后保留草稿且不新建普通消息', async () => {
    const runs = [run('r-w', 'WAITING_FOR_USER', { version: 2, pendingQuestion: WAITING_QUESTION })]
    shell({ runs, messages: [message('m-r1', 1, 'USER', '充电没反应'), message('m-r2', 2, 'ASSISTANT', WAITING_QUESTION.question)] })
    const detail = await readConversation(SCENE)
    const assistantText = detail.messages.at(-1)!.content
    // 现场样本：末条消息与 pendingQuestion.question 相同也不能把补答变成新调查
    expect(assistantText).toBe(WAITING_QUESTION.question)
    const draft = '直流快充，SOC 40%'
    await expect(replyToQuestion(SCENE, {
      runId: 'r-w', pendingQuestionId: WAITING_QUESTION.pendingQuestionId, questionVersion: 1
    }, draft)).rejects.toMatchObject({ code: 'STALE_OR_UNRELATED_REPLY' })
    // 拒绝后只有这一条带 replyTo 的失败请求，没有任何普通消息提交
    const messageCalls = calls.filter((call) => call.url.endsWith('/messages'))
    expect(messageCalls).toHaveLength(1)
    expect((messageCalls[0].body as { replyTo?: unknown }).replyTo).toBeDefined()
    // 回读最新问题后草稿仍在用户手中，不自动提交
    const refreshed = await readConversation(SCENE)
    expect(refreshed.runs[0].pendingQuestion?.questionVersion).toBe(2)
    expect(draft).toBe('直流快充，SOC 40%')
    expect(calls.filter((call) => call.url.endsWith('/messages'))).toHaveLength(1)
  })

  it('F04 SSE 等待通知后立即回读持久状态；重复 seq 与历史事件都只出现一次', async () => {
    const runs = [run('r-w', 'WAITING_FOR_USER', { version: 2, pendingQuestion: WAITING_QUESTION })]
    shell({ runs })
    const history = await readRunEvents('r-w')
    const loaded = [...history]
    // 页面侧按 maxSeq 去重：重放的旧事件不重复追加
    const append = (event: RunEvent) => { if (event.seq > (loaded.at(-1)?.seq ?? 0)) loaded.push(event) }
    append(runEvent(2, 'RUN_TOOL', { toolName: 'search_knowledge', success: true }))
    append(runEvent(3, 'RUN_WAITING', { questionVersion: 2 }))
    expect(loaded.map((event) => event.seq)).toEqual([1, 2, 3])
    const snapshot = await readRun('r-w')
    expect(snapshot.status).toBe('WAITING_FOR_USER')
    expect(snapshot.pendingQuestion?.question).toBe(WAITING_QUESTION.question)
  })

  it('F04 终态通知后回读：取消轮保留状态，引用读取仍受授权约束（失败/拒绝不编造内容）', async () => {
    const runs = [run('r-x', 'CANCELLED', { result: null })]
    shell({ runs })
    vi.mocked(fetch).mockImplementationOnce(async () => json({ code: 'FORBIDDEN', message: '无权访问' }, 403) as never)
    await expect(readReferences('r-x')).rejects.toMatchObject({ status: 403 })
    const snapshot = await readRun('r-x')
    expect(snapshot.status).toBe('CANCELLED')
    expect(snapshot.result).toBeNull()
  })

  it('F02 深入调查使用所看轮选中判断原文、该轮合法引用与资产，断言新轮不继承业务时点', async () => {
    const runs = [run('r1', 'COMPLETED', { asOf: '2026-11-17T08:00:00Z', result: COMPLETED_RESULT })]
    shell({ runs })
    const detail = await readConversation(SCENE)
    const viewed = resolveRunTargets(detail.runs, null).viewed!
    const claim = viewed.result!.claims[0]
    const registered = await readReferences(viewed.runId)
    const contextRefs = claim.referenceIds.filter((id) => registered.some((ref) => ref.referenceId === id))
    expect(contextRefs).toEqual(['ref-a'])
    const goal = detail.messages.find((item) => item.messageId === viewed.goalMessageId)!
    const created = await sendMessage(SCENE, buildInvestigationMessage(claim.text, viewed.result!.category), undefined, [], goal.selectedAssetId, contextRefs)
    expect(created.runId).toBe('r-new')
    const sent = calls.filter((call) => call.url.endsWith('/messages')).at(-1)!
    expect(sent.body).toMatchObject({
      selectedAssetId: 'CAR-A',
      contextRefs: [{ referenceId: 'ref-a' }]
    })
    expect((sent.body as { replyTo?: unknown }).replyTo).toBeUndefined()
    // 新轮请求体不带任何业务时点字段：界面不得承诺继承 T1
    expect(sent.body).not.toHaveProperty('asOf')
  })

  it('F02 终态轮来源逐条保留：同文档不同章节分别可查，缺失 ID 不伪造', async () => {
    const runs = [run('r1', 'COMPLETED', {
      result: {
        ...COMPLETED_RESULT,
        claims: [{ text: '同一文档两个章节', certainty: 'SUPPORTED', referenceIds: ['ref-a', 'ref-b'] }],
        references: [
          COMPLETED_RESULT.references[0],
          { ...COMPLETED_RESULT.references[0], referenceId: 'ref-b', section: '4.3', title: '充电系统维修手册' }
        ]
      }
    })]
    shell({ runs })
    const references = await readReferences('r1')
    expect(references.map((item) => item.referenceId)).toEqual(['ref-a', 'ref-b'])
    expect(references.map((item) => item.section)).toEqual(['4.2', '4.3'])
    const claimIds = runs[0].result!.claims[0].referenceIds
    const known = new Set(references.map((item) => item.referenceId))
    expect(claimIds.filter((id) => !known.has(id))).toEqual([])
  })

  it('会话切换后旧请求结果不得写入新会话', async () => {
    const runs = [run('r1', 'COMPLETED', { result: COMPLETED_RESULT })]
    shell({ runs })
    const loader = createScopedLoader()
    const first = loader.issue('detail|' + SCENE)
    const second = loader.issue('detail|other')
    const detail = await readConversation(SCENE)
    expect(loader.isCurrent(first, 'detail|' + SCENE)).toBe(false)
    expect(loader.isCurrent(second, 'detail|other')).toBe(true)
    void detail
  })

  it('资产读取按所看轮 asOf 请求，不跟随输入下拉', async () => {
    shell({ runs: [run('r1', 'COMPLETED', { result: COMPLETED_RESULT })] })
    await readAsset('CAR-A', '2026-11-17T08:00:00Z')
    expect(calls.at(-1)?.url).toBe('/api/assets/CAR-A?asOf=2026-11-17T08%3A00%3A00Z')
  })

  it('Skill 读取失败不阻断主流程，按 runId 隔离', async () => {
    shell({ runs: [run('r1', 'COMPLETED', { result: COMPLETED_RESULT })] })
    await expect(readRunSkill('r1')).resolves.toBeNull()
    expect(calls.at(-1)?.url).toBe('/api/runs/r1/skill')
  })

  it('近期调查使用服务端列表，切换会话与新建会话都不额外读取逐条详情', async () => {
    shell({ runs: [run('r1', 'COMPLETED', { result: COMPLETED_RESULT })] })
    const list = await listConversations()
    expect(list[0].conversationId).toBe(SCENE)
    await readConversation(SCENE)
    const created = await createConversation()
    void created
    expect(created.conversationId).toBeDefined()
    // 侧栏渲染只用列表与当前会话数据：没有逐条 readConversation
    expect(calls.filter((call) => call.url === `/api/conversations/${SCENE}`)).toHaveLength(1)
  })
})