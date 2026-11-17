/**
 * 专业诊断工作台 ViewModel：把持久 Run 结果、已登记引用与运行事件确定性投影为界面对象。
 * 纯函数：无 fetch、无模型调用、无数据库写入；不生成概率/评分，不推导已执行检查，
 * 不把建议/不确定点/限制关联到单条判断；AUTHORIZED 只说明来源已授权，不代表现场已核验。
 */
import type {
  AgentResult,
  AnswerCategory,
  Certainty,
  MessageView,
  Reference,
  RunEvent,
  RunStatus,
  RunView
} from './api'

/** 引用来源类型的受控中文标签；未知类型只给安全通用标签，不猜测具体含义。 */
const SOURCE_TYPE_LABELS: Readonly<Record<string, string>> = {
  KNOWLEDGE_DOCUMENT: '知识文档',
  CONFIGURATION: '配置快照',
  FIELD_OBSERVATION: '现场观测'
}

/** 答案类别 → 判断区语义标题：只有 POSSIBLE_CAUSES 才把 claims 列为候选原因。 */
const CATEGORY_LABELS: Readonly<Record<AnswerCategory, string>> = {
  KNOWLEDGE: '资料断言',
  POSSIBLE_CAUSES: '候选原因',
  CONFIRMED_FINDING: '服务端核验结论',
  HUMAN_REVIEW: '待人工核验'
}

/** certainty 原样语义：推测 / 资料支持 / 服务端已核验；未知值回退为原样码值。 */
const CERTAINTY_LABELS: Readonly<Record<Certainty, string>> = {
  POSSIBLE: '可能',
  SUPPORTED: '资料支持',
  CONFIRMED: '服务端已核验'
}

/** 建议、不确定点与限制没有判断级关联字段，统一标注为“本轮”内容。 */
export const ROUND_FIELDS_NOTE = '建议、不确定点与限制均为本轮内容，未逐条关联到单个判断'

export interface ReferenceView {
  referenceId: string
  title: string
  sourceType: string
  sourceTypeLabel: string
  sourceId: string
  sourceVersion: string
  chunkId: string
  page: number | null
  section: string | null
  excerpt: string
  dataNature: string
  /** 实际定位说明：有页码用页码，否则用章节；两者皆缺为 null，不补写缺失定位。 */
  locatingNote: string | null
}

export interface ClaimView {
  /** claims 原顺序中的下标；选中键为 `${runId}:${index}`，仅对当前快照有效。 */
  index: number
  key: string
  text: string
  certainty: Certainty
  certaintyLabel: string
  referenceIds: string[]
  /** 按 ID 在已取得引用中连接到的真实引用，保持 claim.referenceIds 的顺序。 */
  references: ReferenceView[]
  /** 关联 ID 中未在已取得引用找到的部分；有值时界面提示“引用暂不可用”。 */
  missingReferenceIds: string[]
  /** '当前无已关联引用'（本判断没有引用）或 '引用暂不可用'（部分未取得）；否则 null。 */
  referenceNote: string | null
}

/** 运行事件的保守投影：只展示服务端白名单已有的安全字段，缺失不补写，不推测未记录步骤。 */
export interface RunEventView {
  seq: number
  kind: 'tool' | 'model' | 'other'
  title: string
  detail: string
  success: boolean | null
}

export interface DiagnosisViewModel {
  runId: string | null
  status: RunStatus | null
  failureCode: string | null
  /** 顶部任务区：本轮目标消息与关联设备（无设备为 null，界面显示“未关联”）。 */
  taskQuestion: string | null
  selectedAssetId: string | null
  category: AnswerCategory | null
  categoryLabel: string | null
  /** 判断区标题：按 category 命名；无结果时为 null。 */
  claimsTitle: string | null
  claims: ClaimView[]
  summary: string | null
  suggestions: string[]
  uncertainties: string[]
  limitations: string[]
  roundFieldsNote: string
  riskLevel: string | null
  safetyDisposition: string | null
  /** 受控安全提示；无对应处置时为空串。 */
  safetyNotice: string
  dataNature: string | null
  /** AUTHORIZED 不等于真实现场已核验；其余数据性质不附加说明。 */
  dataNatureNote: string
  /** 本轮已取回资料（经 /references 或结果内引用取得），逐引用保留自身 dataNature。 */
  references: ReferenceView[]
  events: RunEventView[]
}

export interface DiagnosisViewModelInput {
  /** 当前 Run；run 为 null 时按尚无结果处理。 */
  run: RunView | null
  /** 当前 Run 的 goalMessageId 对应消息；仅取展示文本与所选设备。 */
  goalMessage?: MessageView | null
  /** 已取得的登记引用（运行中/失败轮经 /references 读取）；缺省回退到 result.references。 */
  references?: Reference[]
  /** 已回读的公开运行事件；只映射白名单安全字段。 */
  events?: RunEvent[]
}

export function toReferenceView(reference: Reference): ReferenceView {
  return {
    referenceId: reference.referenceId,
    title: reference.title,
    sourceType: reference.sourceType,
    sourceTypeLabel: SOURCE_TYPE_LABELS[reference.sourceType] ?? '其他来源',
    sourceId: reference.sourceId,
    sourceVersion: reference.sourceVersion,
    chunkId: reference.chunkId,
    page: reference.page,
    section: reference.section,
    excerpt: reference.excerpt,
    dataNature: reference.dataNature,
    locatingNote: reference.page != null
      ? '第 ' + reference.page + ' 页'
      : reference.section ? '章节：' + reference.section : null
  }
}

function safetyNoticeOf(disposition: string | null): string {
  if (disposition === 'REQUIRES_QUALIFIED_PERSONNEL') {
    return '涉及高风险操作：必须由具备资质的专业人员按厂家规程处理，本系统不提供操作步骤'
  }
  if (disposition === 'PROHIBITED') return '该请求涉及被禁止的设备操作，系统不提供执行指引'
  return ''
}

function dataNatureNoteOf(dataNature: string | null): string {
  return dataNature === 'AUTHORIZED' ? '资料来源已授权，不代表已完成现场核验' : ''
}

function toClaimViews(result: AgentResult, runId: string | null, available: ReadonlyMap<string, ReferenceView>): ClaimView[] {
  return result.claims.map((claim, index) => {
    const references: ReferenceView[] = []
    const missingReferenceIds: string[] = []
    for (const referenceId of claim.referenceIds) {
      const found = available.get(referenceId)
      if (found) references.push(found)
      else missingReferenceIds.push(referenceId)
    }
    const referenceNote = claim.referenceIds.length === 0
      ? '当前无已关联引用'
      : missingReferenceIds.length > 0 ? '引用暂不可用' : null
    return {
      index,
      key: (runId ?? 'run') + ':' + index,
      text: claim.text,
      certainty: claim.certainty,
      certaintyLabel: CERTAINTY_LABELS[claim.certainty] ?? claim.certainty,
      referenceIds: [...claim.referenceIds],
      references,
      missingReferenceIds,
      referenceNote
    }
  })
}

function toEventViews(events: RunEvent[]): RunEventView[] {
  return events.map((event) => {
    if (event.type === 'RUN_TOOL') {
      const data = event.data
      const toolName = typeof data.toolName === 'string' ? data.toolName : '未记录工具'
      const success = typeof data.success === 'boolean' ? data.success : null
      const parts: string[] = []
      if (typeof data.durationMs === 'number') parts.push('耗时 ' + data.durationMs + ' ms')
      if (typeof data.references === 'number') parts.push('引用 ' + data.references + ' 条')
      if (typeof data.errorCode === 'string' && data.errorCode) parts.push('错误码 ' + data.errorCode)
      return { seq: event.seq, kind: 'tool', title: toolName, detail: parts.join(' · '), success }
    }
    if (event.type === 'RUN_MODEL') {
      const data = event.data
      const model = typeof data.model === 'string' ? data.model : '未记录模型'
      const parts: string[] = []
      if (typeof data.attempt === 'number') parts.push('第 ' + data.attempt + ' 次')
      if (typeof data.outcome === 'string') parts.push(data.outcome)
      if (typeof data.durationMs === 'number') parts.push('耗时 ' + data.durationMs + ' ms')
      return { seq: event.seq, kind: 'model', title: model, detail: parts.join(' · '), success: null }
    }
    return { seq: event.seq, kind: 'other', title: event.type, detail: '', success: null }
  })
}

export function buildDiagnosisViewModel(input: DiagnosisViewModelInput): DiagnosisViewModel {
  const run = input.run
  const result = run?.result ?? null
  const availableReferences = new Map<string, ReferenceView>()
  for (const reference of input.references ?? result?.references ?? []) {
    availableReferences.set(reference.referenceId, toReferenceView(reference))
  }
  return {
    runId: run?.runId ?? null,
    status: run?.status ?? null,
    failureCode: run?.failureCode ?? null,
    taskQuestion: input.goalMessage?.content ?? null,
    selectedAssetId: input.goalMessage?.selectedAssetId ?? null,
    category: result?.category ?? null,
    categoryLabel: result ? CATEGORY_LABELS[result.category] : null,
    claimsTitle: result ? CATEGORY_LABELS[result.category] : null,
    claims: result ? toClaimViews(result, run?.runId ?? null, availableReferences) : [],
    summary: result?.summary ?? null,
    suggestions: result ? [...result.suggestions] : [],
    uncertainties: result ? [...result.uncertainties] : [],
    limitations: result ? [...result.limitations] : [],
    roundFieldsNote: ROUND_FIELDS_NOTE,
    riskLevel: result?.riskLevel ?? null,
    safetyDisposition: result?.safetyDisposition ?? null,
    safetyNotice: safetyNoticeOf(result?.safetyDisposition ?? null),
    dataNature: result?.dataNature ?? null,
    dataNatureNote: dataNatureNoteOf(result?.dataNature ?? null),
    references: [...availableReferences.values()],
    events: toEventViews(input.events ?? [])
  }
}

/**
 * 深入调查请求正文：用户可见，不含模型生成的目的或隐藏指令。
 * 仅 POSSIBLE_CAUSES 类别的 claims 称为“候选判断（尚未确诊）”，其余类别统称“判断”。
 */
export function buildInvestigationMessage(claimText: string, category: AnswerCategory): string {
  const label = category === 'POSSIBLE_CAUSES' ? '候选判断（尚未确诊）' : '判断'
  return `请深入调查以下${label}：${claimText}。请结合继承资料重新取证，说明依据、不确定点和建议。`
}
