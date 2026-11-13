/**
 * 与 /api 的强类型合同：发送自由消息、回答待答问题、读取持久状态。
 * 浏览器不持有任何凭据，也不提交 owner/project/权限：身份始终由服务器决定。
 * 本阶段只用安全轮询；SSE 与断线续传留到 05-01。
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
export type AnswerCategory = 'KNOWLEDGE' | 'POSSIBLE_CAUSES' | 'CONFIRMED_FINDING' | 'HUMAN_REVIEW'
export type RiskLevel = 'LOW' | 'MEDIUM' | 'HIGH' | 'PROHIBITED'

export interface Claim {
  text: string
  referenceIds: string[]
  certainty: Certainty
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
}

export interface FileView {
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

export interface MessageView {
  messageId: string
  seq: number
  role: 'USER' | 'ASSISTANT'
  content: string
  selectedAssetId: string | null
  requestedAt: string
  asOf: string
}

export interface RunView {
  runId: string
  conversationId: string
  goalMessageId: string
  status: RunStatus
  version: number
  requestedAt: string
  asOf: string
  dataNature: string
  pendingQuestion: PendingQuestion | null
  result: AgentResult | null
  /** FAILED 时的稳定原因码；服务器只公开白名单错误码，不含异常正文或配置值。 */
  failureCode: string | null
  /** 累计执行时长（毫秒，不含等待用户的时间）；03-03 起由持久层返回。 */
  activeDurationMs?: number | null
  /** 供应商用量可得性；无 usage 时为 UNKNOWN，Token/成本为 null，不编造数值。 */
  usageStatus?: string | null
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
  INVALID_CITATION: '答案引用无法核实，已拒绝',
  UNCONFIRMED_FINDING: '缺少现场核验，不能给出确诊结论',
  UNSAFE_ANSWER: '答案包含不安全的设备操作建议，已拒绝',
  INVALID_ANSWER: '答案格式不符合协议，已拒绝',
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

async function call<T>(path: string, init: RequestInit, requestKey?: string): Promise<T> {
  const headers = new Headers(init.headers)
  headers.set('Accept', 'application/json')
  if (init.body !== undefined && !(init.body instanceof FormData)) headers.set('Content-Type', 'application/json')
  if (requestKey) headers.set('Idempotency-Key', requestKey)
  const response = await fetch(path, { ...init, headers })
  const text = await response.text()
  const payload = text.length > 0 ? safeJson(text) : null
  if (!response.ok) {
    const body = payload as Partial<ApiErrorBody> | null
    throw new ApiError(
      body?.code ?? 'HTTP_' + response.status,
      response.status,
      body?.message ?? '请求无法处理',
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

/** 已登记资产清单（服务器身份范围内）；下拉选择可空，不指定设备也能问答。 */
export function listAssets(): Promise<string[]> {
  return call<string[]>('/api/assets', { method: 'GET' })
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
  const deadline = Date.now() + timeoutMs
  for (;;) {
    const file = await readFile(fileId)
    if (['PARSED', 'FAILED', 'OCR_REQUIRED'].includes(file.parseStatus)) return file
    if (Date.now() >= deadline) throw new ApiError('FILE_WAIT_TIMEOUT', 0, '等待文件导入超时')
    await new Promise((resolve) => setTimeout(resolve, 500))
  }
}

/** 发送一条新的自由消息：没有 replyTo，服务器据此开启新的 run。assetId 可空。 */
export function sendMessage(
  conversationId: string,
  userMessage: string,
  requestKey = newRequestKey(),
  attachmentIds: string[] = [],
  selectedAssetId: string | null = null
): Promise<RunView> {
  return call<RunView>(
    '/api/conversations/' + encodeURIComponent(conversationId) + '/messages',
    {
      method: 'POST',
      body: JSON.stringify({
        schemaVersion: 1,
        userMessage,
        ...(attachmentIds.length > 0 ? { attachments: attachmentIds.map((fileId) => ({ fileId })) } : {}),
        ...(selectedAssetId ? { selectedAssetId } : {})
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
  requestKey = newRequestKey()
): Promise<RunView> {
  return call<RunView>(
    '/api/conversations/' + encodeURIComponent(conversationId) + '/messages',
    {
      method: 'POST',
      body: JSON.stringify({
        schemaVersion: 1,
        userMessage,
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
}

/**
 * 安全轮询：直到 WAITING_FOR_USER / COMPLETED / FAILED 才停止。
 * 轮询不会创建新 run，也不会重发消息；超时只抛出 RUN_WAIT_TIMEOUT，由界面提示刷新重试。
 */
export async function waitForRun(runId: string, options: WaitOptions = {}): Promise<RunView> {
  const timeoutMs = options.timeoutMs ?? 60_000
  const intervalMs = options.intervalMs ?? 1_000
  const sleep = options.sleep ?? ((ms: number) => new Promise<void>((resolve) => setTimeout(resolve, ms)))
  const deadline = Date.now() + timeoutMs
  for (;;) {
    const run = await readRun(runId)
    options.onUpdate?.(run)
    if (TERMINAL.has(run.status)) return run
    if (Date.now() >= deadline) throw new ApiError('RUN_WAIT_TIMEOUT', 0, '等待运行结果超时')
    await sleep(intervalMs)
  }
}
