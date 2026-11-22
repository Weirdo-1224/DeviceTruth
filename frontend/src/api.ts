/**
 * 与 /api 的强类型合同：发送自由消息、回答待答问题、读取持久状态与运行事件。
 * 浏览器通过同源 Cookie 会话与内存 CSRF 访问，不持有模型密钥，也不提交 owner/project/权限。
 * 运行进度用 SSE 订阅（原生 EventSource 断线自动带 Last-Event-ID 重连），轮询作为兜底与终态校准。
 */

export type RunStatus = 'QUEUED' | 'RUNNING' | 'WAITING_FOR_USER' | 'COMPLETED' | 'FAILED' | 'CANCELLED'

const TERMINAL: ReadonlySet<string> = new Set(['WAITING_FOR_USER', 'COMPLETED', 'FAILED', 'CANCELLED'])

export interface PendingQuestion {
  pendingQuestionId: string
  questionVersion: number
  question: string
  requestedFields: string[]
  askedAt: string
}

export type Certainty = 'POSSIBLE' | 'SUPPORTED' | 'CONFIRMED'
export type ClaimKind = 'CAUSE' | 'OBSERVATION' | 'CONCLUSION' | 'OTHER' | 'UNKNOWN'
export type ClaimRelevance = 'DIRECT' | 'CONTEXT' | 'UNRELATED' | 'UNKNOWN'
export type AnswerCategory = 'KNOWLEDGE' | 'POSSIBLE_CAUSES' | 'CONFIRMED_FINDING' | 'HUMAN_REVIEW'
export type RiskLevel = 'LOW' | 'MEDIUM' | 'HIGH' | 'PROHIBITED'
export type AssetResolution = 'SELECTED' | 'CONFIRMED' | 'AMBIGUOUS' | 'UNCONFIRMED'

export interface Claim {
  text: string
  referenceIds: string[]
  certainty: Certainty
  kind?: ClaimKind
  relevance?: ClaimRelevance
}

export interface Reference {
  referenceId: string
  sourceType: string
  sourceId: string
  sourceVersion: string
  chunkId: string
  title: string
  page: number | null
  section: string | null
  excerpt: string
  dataNature: string
}

export interface AgentResult {
  summary: string
  category: AnswerCategory
  claims: Claim[]
  suggestions: string[]
  uncertainties: string[]
  limitations: string[]
  references: Reference[]
  riskLevel: RiskLevel
  safetyDisposition: string
  dataNature: string
  subjectAssetId?: string | null
  assetResolution?: 'CONFIRMED' | 'AMBIGUOUS' | null
  review?: AnswerReview | null
}

export type EvidenceNature = 'DOCUMENT_FACT' | 'USER_REPORT' | 'HISTORICAL_RECORD' | 'DEVICE_INFERENCE' | 'INDEPENDENT_VERIFICATION'
export type UnitDisposition = 'RETAINED' | 'REMOVED' | 'PENDING'
export interface ReviewUnit {
  unitId: string
  position: 'SUMMARY' | 'CLAIM' | 'SUGGESTION' | 'UNCERTAINTY' | 'LIMITATION' | 'PROCEDURE_STEP'
  index: number
  text: string
  nature: EvidenceNature
  subjectId: string | null
  observedAt: string | null
  supports: { sourceId: string; quote: string }[]
  dependsOn: string[]
}
export interface AnswerReview {
  version: number
  units: ReviewUnit[]
  decisions: { unitId: string; disposition: UnitDisposition; rule: string; sourceIds: string[] }[]
  limited: boolean
}

export interface FileView {
  uploadedByUserId?: string | null
  conversationId?: string | null
  usageScope?: string
  dataNature?: string
  fileId: string
  originalName: string
  mime: string
  checksum: string
  byteSize: number
  parseStatus: 'UPLOADED' | 'PROCESSING' | 'PARSED' | 'FAILED' | 'OCR_REQUIRED'
  failureCode: string | null
  documentId: string | null
  title: string | null
  documentVersion: string | null
  domain: string | null
  createdAt: string
}

export interface ConversationView {
  conversationId: string
  version: number
  createdAt: string
  updatedAt: string
}

export interface ConversationSummary {
  conversationId: string
  title: string
  investigatedAssetId: string | null
  latestRunStatus: RunStatus
  activeRunCount: number
  waitingRunCount: number
  runCount: number
  updatedAt: string
}

export interface ConversationSummaryPage {
  items: ConversationSummary[]
  nextCursor: string | null
}

export interface ConversationSummaryQuery {
  q?: string
  assetId?: string
  status?: 'ACTIVE' | RunStatus
  cursor?: string
  limit?: number
}

export interface MessageView {
  actorUserId?: string | null
  discussionQuotes?: {discussionId: string; version: number; authorName: string; content: string; createdAt: string; dataNature: string}[]
  messageId: string
  seq: number
  role: 'USER' | 'ASSISTANT'
  content: string
  selectedAssetId: string | null
  requestedAt: string
  asOf: string
}

export interface RunView {
  executionActorUserId?: string | null
  runId: string
  conversationId: string
  goalMessageId: string
  status: RunStatus
  version: number
  requestedAt: string
  asOf: string
  investigatedAssetId?: string | null
  assetResolution?: AssetResolution
  changedFromAssetId?: string | null
  dataNature: string
  pendingQuestion: PendingQuestion | null
  result: AgentResult | null
  /** FAILED 时的稳定原因码；服务器只公开白名单错误码，不含异常正文或配置值。 */
  failureCode: string | null
  /** 累计执行时长（毫秒，不含等待用户的时间）；03-03 起由持久层返回。 */
  activeDurationMs?: number | null
  /** 供应商用量可得性；无 usage 时为 UNKNOWN，Token/成本为 null，不编造数值。 */
  usageStatus?: string | null
  inputTokens?: number | null
  outputTokens?: number | null
  cost?: number | null
}

export interface ConversationDetail {
  conversation: ConversationView
  messages: MessageView[]
  runs: RunView[]
}

/** 已提交的补答关联：三个字段缺一不可，服务器会再次校验归属与等待状态。 */
export interface ReplyTarget {
  runId: string
  pendingQuestionId: string
  questionVersion: number
}

export interface ApiErrorBody {
  code: string
  message: string
  retryable: boolean
  traceId: string
}

export class ApiError extends Error {
  readonly code: string
  readonly status: number
  readonly retryable: boolean
  readonly traceId: string | null

  constructor(code: string, status: number, message: string, retryable = false, traceId: string | null = null) {
    super(message || code)
    this.name = 'ApiError'
    this.code = code
    this.status = status
    this.retryable = retryable
    this.traceId = traceId
  }
}

/** 失败原因码 → 用户可读说明；只使用服务器公开的稳定错误码，不回显异常或配置值。 */
const FAILURE_LABELS: Readonly<Record<string, string>> = {
  MODEL_CONFIGURATION_MISSING: '服务端模型未配置或配置无效，请联系管理员',
  MODEL_CONFIGURATION_UNREADABLE: '服务端模型配置无法读取，请联系管理员',
  DASHSCOPE_API_KEY_MISSING: '服务端缺少模型密钥，请联系管理员',
  OPENAI_API_KEY_MISSING: '服务端缺少模型密钥，请联系管理员',
  OPENAI_BASE_URL_MISSING: '服务端模型地址未配置，请联系管理员',
  OPENAI_MODEL_MISSING: '服务端模型名称未配置，请联系管理员',
  OPENAI_CONFIGURATION_INVALID: '服务端模型配置无效，请联系管理员',
  MODEL_TRANSPORT_OR_TIMEOUT: '模型调用超时或网络失败，可稍后重试',
  MODEL_OUTPUT_INVALID_AFTER_RETRY: '模型输出不符合协议，本次回答已停止',
  REASONER_FAILURE: '模型调用失败，本次回答已停止',
  QUEUE_REJECTED: '执行队列已满，请稍后重试',
  BUDGET_EXHAUSTED: '执行预算已用尽，未生成结论',
  TOTAL_TIMEOUT: '本次调查的累计执行时间已用尽',
  SKILL_UNAVAILABLE: '本次调查使用的领域指引已停用，请重新发起调查',
  SKILL_CHANGED: '本次调查使用的领域指引已变更，请重新发起调查',
  INVALID_CITATION: '答案引用无法核实，已拒绝',
  UNCONFIRMED_FINDING: '缺少现场核验，不能给出确诊结论',
  UNSAFE_ANSWER: '答案包含不安全的设备操作建议，已拒绝',
  INVALID_ANSWER: '答案格式不符合协议，已拒绝',
  SUBJECT_MISMATCH: '答案中的排查设备与本轮确认对象不一致，已拒绝',
  PERSIST_FAILURE: '保存结果失败，请重试',
  AGENT_RUN_FAILURE: '服务内部错误，请重试',
  RECOVERY_ATTEMPT_LIMIT: '任务多次中断且恢复次数已达上限，已停止；请重新发起调查'
}

/** 失败说明：未知原因码原样显示码值（服务器已保证是白名单错误码）。 */
export function describeFailure(code: string | null | undefined): string {
  if (!code) return '执行失败，但没有返回原因码，请刷新后重试'
  return FAILURE_LABELS[code] ?? '执行失败（' + code + '）'
}

export function newRequestKey(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') return crypto.randomUUID()
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (char) => {
    const random = (Math.random() * 16) | 0
    const value = char === 'x' ? random : (random & 0x3) | 0x8
    return value.toString(16)
  })
}

export interface AccountView { userId: string; email: string; displayName: string }
export interface WorkspaceView { workspaceId: string; kind: 'PERSONAL' | 'TEAM'; name: string; role: 'ADMIN' | 'COLLABORATOR' | 'VIEWER' }
let workspaceId: string | null = null
let accountId: string | null = null
let contextGeneration = 0
const scopedStreams = new Set<() => void>()
let csrfGeneration = 0
let csrf: { headerName: string; token: string } | null = null
export function setAccessContext(account: string | null, workspace: string | null): void {
  for (const close of scopedStreams) close()
  scopedStreams.clear()
  accountId = account
  workspaceId = workspace
  contextGeneration++
}
export function accessContextKey(): string { return `${accountId ?? 'anonymous'}:${workspaceId ?? 'personal'}` }
export function clearAuthentication(): void {
  csrfGeneration++
  csrf = null
  setAccessContext(null, null)
}
export async function loadCsrf(): Promise<void> {
  const generation = csrfGeneration
  const response = await fetch('/api/auth/csrf', { credentials: 'same-origin' })
  if (!response.ok) throw new ApiError('CSRF_UNAVAILABLE', response.status, '无法取得安全凭证，请重试')
  const token = await response.json()
  if (generation !== csrfGeneration) throw new ApiError('ACCESS_CONTEXT_CHANGED', 0, '登录状态已变化，请重新操作')
  csrf = token
}
export function currentAccount(): Promise<AccountView> { return call('/api/auth/me', { method: 'GET' }) }
export async function authenticate(mode: 'login' | 'register', input: {email: string; password: string; displayName?: string}): Promise<AccountView> {
  await loadCsrf()
  const account = await call<AccountView>('/api/auth/' + mode, { method: 'POST', body: JSON.stringify(input) })
  csrfGeneration++
  csrf = null
  return account
}
export async function logout(): Promise<void> {
  await call('/api/auth/logout', { method: 'POST' })
  clearAuthentication()
}
export function authAction(action: 'password-reset-requests' | 'password-resets', input: object): Promise<unknown> {
  return call('/api/auth/' + action, { method: 'POST', body: JSON.stringify(input) })
}
export function listWorkspaces(): Promise<WorkspaceView[]> { return call('/api/workspaces', { method: 'GET' }) }
export function workspaceRequest<T>(suffix: string, method = 'GET', body?: object): Promise<T> {
  return call<T>('/api/workspaces' + suffix, { method, ...(body ? { body: JSON.stringify(body) } : {}) })
}

async function call<T>(path: string, init: RequestInit, requestKey?: string): Promise<T> {
  const generation = contextGeneration
  const selectedWorkspace = workspaceId
  if (!['GET', 'HEAD'].includes(init.method ?? 'GET') && !csrf) await loadCsrf()
  if (generation !== contextGeneration) throw new ApiError('ACCESS_CONTEXT_CHANGED', 0, '空间已切换，已停止旧请求')
  const headers = new Headers(init.headers)
  if (selectedWorkspace && !path.startsWith('/api/auth/')) headers.set('X-Workspace-ID', selectedWorkspace)
  if (!['GET', 'HEAD'].includes(init.method ?? 'GET') && csrf) headers.set(csrf.headerName, csrf.token)
  headers.set('Accept', 'application/json')
  if (init.body !== undefined && !(init.body instanceof FormData)) headers.set('Content-Type', 'application/json')
  if (requestKey) headers.set('Idempotency-Key', requestKey)
  const response = await fetch(path, { ...init, headers, credentials: 'same-origin' })
  const text = await response.text()
  const payload = text.length > 0 ? safeJson(text) : null
  if (generation !== contextGeneration) throw new ApiError('ACCESS_CONTEXT_CHANGED', 0, '空间已切换，已忽略旧响应')
  if (!response.ok) {
    if (response.status === 403) { csrfGeneration++; csrf = null }
    if (response.status === 401 && path !== '/api/auth/login' && path !== '/api/auth/register') {
      clearAuthentication()
      if (typeof window !== 'undefined') window.dispatchEvent(new Event('devicetruth-auth-expired'))
    }
    const body = payload as Partial<ApiErrorBody> | null
    throw new ApiError(
      body?.code ?? 'HTTP_' + response.status,
      response.status,
      body?.message ?? ({ INVALID_CREDENTIALS: '邮箱或密码不正确', INVITATION_EMAIL_MISMATCH: '当前登录邮箱与受邀邮箱不匹配，请切换账号', MAIL_UNAVAILABLE: '邮件服务尚不可用，请联系管理员后重试', LAST_WORKSPACE_ADMIN: '团队必须保留至少一位管理员', CONVERSATION_VERSION_CONFLICT: '调查已有更新，请同步后重新确认', CONVERSATION_BUSY: '当前调查仍有未结束的运行', AUTH_RATE_LIMITED: '操作过于频繁，请稍后重试' } as Record<string,string>)[body?.code ?? ''] ?? ('请求未完成（' + (body?.code ?? response.status) + '）'),
      body?.retryable ?? false,
      body?.traceId ?? null
    )
  }
  return payload as T
}

function safeJson(text: string): unknown {
  try {
    return JSON.parse(text)
  } catch {
    return null
  }
}

export function createConversation(): Promise<ConversationView> {
  return call<ConversationView>('/api/conversations', { method: 'POST', body: '{}' })
}

/** 历史会话列表：刷新/重开浏览器后凭它找回会话，不依赖原页面线程。 */
export function listConversations(): Promise<ConversationView[]> {
  return call<ConversationView[]>('/api/conversations', { method: 'GET' })
}

export function listConversationSummaries(query: ConversationSummaryQuery = {}): Promise<ConversationSummaryPage> {
  const params = new URLSearchParams()
  if (query.q?.trim()) params.set('q', query.q.trim())
  if (query.assetId) params.set('assetId', query.assetId)
  if (query.status) params.set('status', query.status)
  if (query.cursor) params.set('cursor', query.cursor)
  if (query.limit != null) params.set('limit', String(query.limit))
  const suffix = params.size ? '?' + params.toString() : ''
  return call<ConversationSummaryPage>('/api/conversations/summaries' + suffix, { method: 'GET' })
}

/** 已登记资产清单（服务器身份范围内）；下拉选择可空，不指定设备也能问答。 */
export function listAssets(): Promise<string[]> {
  return call<string[]>('/api/assets', { method: 'GET' })
}

/** 设备档案概要：编号 + 展示名 + 种类 + 型号，空串表示该维度无已登记事实。 */
export interface AssetSummary { assetId: string; displayName: string; deviceType: string; model: string }
export function listAssetSummaries(): Promise<AssetSummary[]> {
  return call<AssetSummary[]>('/api/assets/summaries', { method: 'GET' })
}

export function readConversation(conversationId: string): Promise<ConversationDetail> {
  return call<ConversationDetail>('/api/conversations/' + encodeURIComponent(conversationId), { method: 'GET' })
}

export function readRun(runId: string): Promise<RunView> {
  return call<RunView>('/api/runs/' + encodeURIComponent(runId), { method: 'GET' })
}

export function readReferences(runId: string): Promise<Reference[]> {
  return call<Reference[]>('/api/runs/' + encodeURIComponent(runId) + '/references', { method: 'GET' })
}

/** 公开运行事件（历史回读与 SSE 共用同一映射）；data 为按类型白名单字段，不含 prompt/密钥/原始输出。 */
export interface RunEvent {
  seq: number
  type: string
  recordedAt: string
  data: Record<string, unknown>
}

/** 历史回读：按 seq 升序；afterSeq 只取 seq > afterSeq 的增量，用于订阅前先补历史。 */
export function readRunEvents(runId: string, afterSeq?: number): Promise<RunEvent[]> {
  const path = '/api/runs/' + encodeURIComponent(runId) + '/events'
  return call<RunEvent[]>(afterSeq === undefined ? path : path + '?after=' + afterSeq, { method: 'GET' })
}

/** EventSource 的最小结构替身：生产用原生 EventSource 包装，测试注入假实现。 */
export interface EventSourceLike {
  readonly readyState?: number
  onmessage: ((event: { data: string }) => void) | null
  onerror: ((event: unknown) => void) | null
  addEventListener(type: string, listener: (event: { data: string }) => void): void
  close(): void
}

export interface RunStreamHandle {
  close(): void
}

export interface RunStreamOptions {
  /** 已收到的最大 seq（初次为 0）；页面用它做幂等去重，重连后服务端会按 Last-Event-ID 补齐历史。 */
  lastSeq: number
  onEvent: (event: RunEvent) => void
  onTerminal?: (status: string) => void
  onError?: (error: unknown) => void
  eventSourceFactory?: (url: string) => EventSourceLike
  /**
   * 连续 onerror 达到该阈值、且期间没有收到任何成功事件时判为永久失败（触发 onError 并 close）。
   * 默认 8：浏览器拦截/断连窗口（约 5–15 秒、原生重试间隔约 3 秒）只会产生几次失败重试，
   * 默认阈值不会误杀；测试可调小。未达阈值的瞬时断线仍交给原生重连，按游标补齐。
   */
  maxRetryErrors?: number
}

/** SSE 会按 event: 字段分发到具名监听器，这里登记契约里的全部事件类型（含 RUN_WAITING 与 RUN_TERMINAL）。 */
const STREAM_EVENT_TYPES: readonly string[] = [
  'RUN_QUEUED',
  'RUN_RECOVERED',
  'RUN_WAITING',
  'RUN_TOOL',
  'RUN_MODEL',
  'RUN_ANSWER',
  'RUN_FAILED',
  'RUN_CANCELLED',
  'RUN_LATE_RESPONSE',
  'RUN_PHASE',
  'RUN_DEGRADED',
  'RUN_TERMINAL'
]

function defaultEventSourceFactory(url: string): EventSourceLike {
  const source = new EventSource(url)
  let onmessage: ((event: { data: string }) => void) | null = null
  let onerror: ((event: unknown) => void) | null = null
  // openRunEventStream 会在创建后给包装器赋值 onerror；必须用 getter/setter 实时转发到原生连接，
  // 否则原生 error 事件永远到不了页面侧的错误处理（§8.5）。
  return {
    get readyState() {
      return source.readyState
    },
    get onmessage() {
      return onmessage
    },
    set onmessage(handler) {
      onmessage = handler
      source.onmessage = handler ? (event) => handler({ data: event.data as string }) : null
    },
    get onerror() {
      return onerror
    },
    set onerror(handler) {
      onerror = handler
      source.onerror = handler ? (event) => handler(event) : null
    },
    addEventListener: (type, listener) => {
      source.addEventListener(type, (event) => listener({ data: (event as MessageEvent).data as string }))
    },
    close: () => source.close()
  }
}

/**
 * 打开 /api/runs/{id}/stream 的 SSE 订阅。
 * RUN_TERMINAL 到达后自动 close()；连接永久关闭（readyState=CLOSED）且未收到终态时上报 onError 并停止。
 * 其余错误（readyState=CONNECTING）交给浏览器原生重连，服务端按 Last-Event-ID 补齐历史，页面按 seq 去重；
 * 连续 onerror 达 maxRetryErrors（默认 8）且期间无成功事件时判为永久失败，同样上报 onError 并停止。
 */
export function openRunEventStream(runId: string, options: RunStreamOptions): RunStreamHandle {
  const factory = options.eventSourceFactory ?? defaultEventSourceFactory
  const source = factory('/api/runs/' + encodeURIComponent(runId) + '/stream')
  const maxRetryErrors = options.maxRetryErrors ?? 8
  let closed = false
  let terminalSeen = false
  let retryErrors = 0

  const close = (): void => {
    if (closed) return
    closed = true
    source.close()
    scopedStreams.delete(close)
  }
  scopedStreams.add(close)

  for (const type of STREAM_EVENT_TYPES) {
    source.addEventListener(type, (message) => {
      if (closed) return
      // 收到任何服务端帧都说明连接可用：重置连续错误计数（§8.5）
      retryErrors = 0
      if (type === 'RUN_TERMINAL') {
        let status = 'UNKNOWN'
        try {
          status = String((JSON.parse(message.data) as { status?: unknown }).status ?? 'UNKNOWN')
        } catch {
          // 终态帧解析失败按 UNKNOWN 处理，仍要收尾关闭
        }
        terminalSeen = true
        try {
          options.onTerminal?.(status)
        } finally {
          close()
        }
        return
      }
      let event: RunEvent
      try {
        event = JSON.parse(message.data) as RunEvent
      } catch (error) {
        options.onError?.(error)
        return
      }
      if (typeof event.seq !== 'number' || typeof event.type !== 'string') {
        options.onError?.(new Error('事件格式不符合协议'))
        return
      }
      options.onEvent(event)
    })
  }

  source.onerror = (error) => {
    if (closed) return
    // 原生 EventSource 永久关闭（如服务端结束流）时不会再重连：未收到终态说明异常收尾
    if (source.readyState === 2 && !terminalSeen) {
      try {
        options.onError?.(error)
      } finally {
        close()
      }
      return
    }
    // 瞬时断线：原生仍处于 CONNECTING 会自动重连。只有连续失败累计到阈值且期间无任何成功事件，
    // 才放弃重连并上报永久失败（页面控制器据此把 live 置 false，退回轮询兜底）
    retryErrors += 1
    if (retryErrors >= maxRetryErrors) {
      try {
        options.onError?.(error)
      } finally {
        close()
      }
    }
  }

  return { close }
}

export function uploadMarkdown(file: File, title?: string, version = '1.0'): Promise<FileView> {
  const form = new FormData()
  form.append('file', file)
  if (title?.trim()) form.append('title', title.trim())
  form.append('version', version)
  form.append('domain', 'general')
  return call<FileView>('/api/files', { method: 'POST', body: form })
}

export function readFile(fileId: string): Promise<FileView> {
  return call<FileView>('/api/files/' + encodeURIComponent(fileId), { method: 'GET' })
}

export function retryFile(fileId: string): Promise<FileView> {
  return call<FileView>('/api/files/' + encodeURIComponent(fileId) + '/retry', { method: 'POST' })
}

export async function waitForFile(fileId: string, timeoutMs = 60_000): Promise<FileView> {
  const context = contextGeneration
  const deadline = Date.now() + timeoutMs
  for (;;) {
    if (context !== contextGeneration) throw new ApiError('ACCESS_CONTEXT_CHANGED', 0, '已停止旧空间的附件等待')
    const file = await readFile(fileId)
    if (['PARSED', 'FAILED', 'OCR_REQUIRED'].includes(file.parseStatus)) return file
    if (Date.now() >= deadline) throw new ApiError('FILE_WAIT_TIMEOUT', 0, '等待文件导入超时')
    await new Promise((resolve) => setTimeout(resolve, 500))
  }
}

/** 深入调查可继承的引用上限，与服务器合同一致：每项仅 referenceId，超出即拒绝。 */
export const CONTEXT_REF_LIMIT = 10

export interface ContextRefSelection {
  /** 去重后的 referenceId，保持原顺序，最多 CONTEXT_REF_LIMIT 条。 */
  referenceIds: string[]
  /** 超出上限被省略的数量；界面须明示省略而不是静默截断。 */
  omittedCount: number
}

/** 从选中判断的引用 ID 构造继承清单：保持原顺序去重，最多前 10 条，其余计入省略数。 */
export function selectContextRefs(referenceIds: readonly string[]): ContextRefSelection {
  const seen = new Set<string>()
  const selected: string[] = []
  let omittedCount = 0
  for (const id of referenceIds) {
    if (!id || seen.has(id)) continue
    seen.add(id)
    if (selected.length < CONTEXT_REF_LIMIT) selected.push(id)
    else omittedCount += 1
  }
  return { referenceIds: selected, omittedCount }
}

/**
 * 发送一条新的自由消息：没有 replyTo，服务器据此开启新的 run。assetId 可空。
 * contextRefs 为深入调查继承的引用 ID（仅 ID，不含身份/权限），原有调用默认空列表。
 */
export function sendMessage(
  conversationId: string,
  userMessage: string,
  requestKey = newRequestKey(),
  attachmentIds: string[] = [],
  selectedAssetId: string | null = null,
  contextRefs: readonly string[] = [],
  expectedConversationVersion?: number,
  discussionQuotes: readonly {discussionId: string; version: number}[] = []
): Promise<RunView> {
  return call<RunView>(
    '/api/conversations/' + encodeURIComponent(conversationId) + '/messages',
    {
      method: 'POST',
      body: JSON.stringify({
        schemaVersion: 1,
        userMessage,
        ...(discussionQuotes.length ? { discussionQuotes } : {}),
        ...(expectedConversationVersion !== undefined ? { expectedConversationVersion } : {}),
        ...(attachmentIds.length > 0 ? { attachments: attachmentIds.map((fileId) => ({ fileId })) } : {}),
        ...(selectedAssetId ? { selectedAssetId } : {}),
        ...(contextRefs.length > 0 ? { contextRefs: contextRefs.map((referenceId) => ({ referenceId })) } : {})
      })
    },
    requestKey
  )
}

/** 回答待答问题：必须带 runId/pendingQuestionId/questionVersion，沿用同一个 run。 */
export function replyToQuestion(
  conversationId: string,
  target: ReplyTarget,
  userMessage: string,
  requestKey = newRequestKey(),
  discussionQuotes: readonly {discussionId: string; version: number}[] = []
): Promise<RunView> {
  return call<RunView>(
    '/api/conversations/' + encodeURIComponent(conversationId) + '/messages',
    {
      method: 'POST',
      body: JSON.stringify({
        schemaVersion: 1,
        userMessage,
        ...(discussionQuotes.length ? { discussionQuotes } : {}),
        replyTo: {
          runId: target.runId,
          pendingQuestionId: target.pendingQuestionId,
          questionVersion: target.questionVersion
        }
      })
    },
    requestKey
  )
}

export interface WaitOptions {
  timeoutMs?: number
  intervalMs?: number
  sleep?: (ms: number) => Promise<void>
  onUpdate?: (run: RunView) => void
  /** 会话切换或轮次已到终态时取消：抛 RUN_WAIT_CANCELLED，且不再发起新请求。 */
  signal?: AbortSignal
}

/**
 * 安全轮询：直到 WAITING_FOR_USER / COMPLETED / FAILED 才停止。
 * 轮询不会创建新 run，也不会重发消息；超时只抛出 RUN_WAIT_TIMEOUT，由界面提示刷新重试。
 */
export async function waitForRun(runId: string, options: WaitOptions = {}): Promise<RunView> {
  const context = contextGeneration
  const timeoutMs = options.timeoutMs ?? 60_000
  const intervalMs = options.intervalMs ?? 1_000
  const sleep = options.sleep ?? ((ms: number) => new Promise<void>((resolve) => setTimeout(resolve, ms)))
  const deadline = Date.now() + timeoutMs
  for (;;) {
    if (context !== contextGeneration) throw new ApiError('ACCESS_CONTEXT_CHANGED', 0, '已停止旧空间的运行等待')
    if (options.signal?.aborted) throw new ApiError('RUN_WAIT_CANCELLED', 0, '已停止等待该运行')
    const run = await readRun(runId)
    if (context !== contextGeneration) throw new ApiError('ACCESS_CONTEXT_CHANGED', 0, '已停止旧空间的运行等待')
    if (options.signal?.aborted) throw new ApiError('RUN_WAIT_CANCELLED', 0, '已停止等待该运行')
    options.onUpdate?.(run)
    if (TERMINAL.has(run.status)) return run
    if (Date.now() >= deadline) throw new ApiError('RUN_WAIT_TIMEOUT', 0, '等待运行结果超时')
    await sleep(intervalMs)
  }
}

/** 取消运行：QUEUED/WAITING 立即 CANCELLED，RUNNING 协作式收尾；终态再取消 409 RUN_ALREADY_TERMINAL。 */
export function cancelRun(runId: string): Promise<RunView> {
  return call<RunView>('/api/runs/' + encodeURIComponent(runId) + '/cancel', { method: 'POST' })
}

/** 本 run 选中的领域 Skill（版本与适用范围）；未选择时服务器返回 404 SKILL_NOT_SELECTED，这里归一为 null。
 *  合同与后端 SkillRuntime.SelectionView 一致：skillId 即 Skill 名（目录名同名），适用范围分 devices/tasks。 */
export interface SkillSelection {
  skillId: string
  version: string
  devices: string[]
  tasks: string[]
}

export async function readRunSkill(runId: string): Promise<SkillSelection | null> {
  try {
    return await call<SkillSelection>('/api/runs/' + encodeURIComponent(runId) + '/skill', { method: 'GET' })
  } catch (error) {
    if (error instanceof ApiError && error.code === 'SKILL_NOT_SELECTED') return null
    throw error
  }
}

/** 配置记录：来源、核实状态与有效期分开表达；知识/文档记载不等于现场核实。 */
export interface ConfigurationEntry {
  recordId: string
  componentId: string
  attribute: string
  value: string
  sourceType: string
  sourceId: string
  sourceRef: string
  dataNature: string
  verificationStatus: 'DOCUMENTED' | 'FIELD_VERIFIED'
  authority: number
  validFrom: string
  validTo: string | null
  recordedAt: string
  observedAt: string | null
}

export interface MaintenanceEntry {
  recordId: string
  componentId: string
  operation: string
  beforeValue: string | null
  afterValue: string | null
  sourceType: string
  sourceId: string
  sourceRef: string
  dataNature: string
  verificationStatus: 'DOCUMENTED' | 'FIELD_VERIFIED'
  authority: number
  validFrom: string
  validTo: string | null
  recordedAt: string
  observedAt: string | null
}

/** 资产组合视图（指定时点）：身份范围 + 全部配置/维修记录 + 装机与冲突判断。 */
export interface AssetComposite {
  assetId: string
  ownerId: string
  projectId: string
  asOf: string
  configurations: ConfigurationEntry[]
  maintenance: MaintenanceEntry[]
  installationStatus: 'FIELD_CONFIRMED' | 'UNCONFIRMED'
  conflict: 'NONE' | 'CONFLICT'
  requiredChecks: string[]
  limitations: string[]
  dataNature: string
}

export function readAsset(assetId: string, asOf?: string): Promise<AssetComposite> {
  const query = asOf ? '?asOf=' + encodeURIComponent(asOf) : ''
  return call<AssetComposite>('/api/assets/' + encodeURIComponent(assetId) + query, { method: 'GET' })
}

export function productRequest<T>(path: string, method = 'GET', body?: object): Promise<T> {
  return call<T>('/api/' + path, { method, ...(body ? { body: JSON.stringify(body) } : {}) })
}
export function uploadInvestigationAttachment(conversationId: string, file: File): Promise<FileView> {
  const body = new FormData(); body.append('file', file)
  return call('/api/conversations/' + encodeURIComponent(conversationId) + '/attachments', { method: 'POST', body })
}
