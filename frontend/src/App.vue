<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import {
  accessContextKey,
  ApiError,
  cancelRun as cancelRunRequest,
  createConversation,
  describeFailure,
  listAssets,
  listConversationSummaries,
  readConversation,
  readReferences,
  readRun,
  readRunEvents,
  readRunSkill,
  replyToQuestion,
  retryFile,
  sendMessage,
  uploadInvestigationAttachment,
  waitForFile,
  waitForRun,
  type ConversationSummary,
  type ConversationView,
  type FileView,
  type MessageView,
  type RunEvent,
  type RunView,
  type SkillSelection
} from './api'
import InvestigationCollaboration from './components/InvestigationCollaboration.vue'
import RunTimeline from './components/RunTimeline.vue'
import DiagnosisReport from './components/DiagnosisReport.vue'
import RecentInvestigations from './components/RecentInvestigations.vue'
import ProfessionalDiagnosisPage from './components/ProfessionalDiagnosisPage.vue'
import EvidenceComparison from './components/EvidenceComparison.vue'
import EvidenceModal from './components/EvidenceModal.vue'
import UiIcon from './components/UiIcon.vue'
import { createRunTimelineController } from './timeline'
import { buildInvestigationMessage, buildDiagnosisViewModel, toReferenceView, type ReferenceView } from './diagnosisViewModel'
import { resolveRunTargets, runPermissions, UNSETTLED_STATUSES } from './runTargets'
import { createScopedLoader, mergeRunList, mergeRunView, scopedRunKey } from './scope'
import { appendConversationPage, conversationBadge } from './history'
import { ensureConversation } from './draftSubmission'
import { draftKey, usePrivateDraft, writeDraft, listDrafts } from './privateDrafts'
import { createScheduledReads, TERMINAL_EVENT_STATUSES } from './scheduler'

const props = defineProps<{ readOnly?: boolean; actorId?: string; administrator?: boolean; memberNames?: Record<string, string> }>()
const STORAGE_KEY = 'devicetruth.conversation-id:' + accessContextKey()

const conversation = ref<ConversationView | null>(null)
const quoteDraft = usePrivateDraft(computed(() => draftKey(accessContextKey(), conversation.value?.conversationId ?? null, 'quotes')))
const discussionQuotes = computed<{discussionId: string; version: number; content: string; authorName: string}[]>({
  get: () => { try { return JSON.parse(quoteDraft.value || '[]') } catch { return [] } },
  set: value => { quoteDraft.value = JSON.stringify(value) }
})
const conversations = ref<ConversationSummary[]>([])
const historyLoaded = ref(false)
const historyError = ref('')
const historyNextCursor = ref<string | null>(null)
const historyQuery = ref('')
const historyStatus = ref<'' | 'ACTIVE' | 'WAITING_FOR_USER' | 'COMPLETED' | 'FAILED'>('')
const historyLoadingMore = ref(false)
let historyGeneration = 0
const assets = ref<string[]>([])
const selectedAssetId = ref<string>('')
// 持久消息与轮次：服务器返回的顺序即展示与默认跟随顺序。
const messages = ref<MessageView[]>([])
const runs = ref<RunView[]>([])
/** 用户主动查看的历史轮；null 表示跟随服务器排序的最新轮。 */
const pinnedRunId = ref<string | null>(null)
/** 用户明确选择的活动目标（多个未决轮时必须先选）；失效时自动回落到唯一未决轮。 */
const preferredActiveRunId = ref<string | null>(null)

/** 查看轮 / 活动目标 / 多未决轮：按钮、键盘与处理函数共用同一份判定。 */
const targets = computed(() => resolveRunTargets(runs.value, pinnedRunId.value, preferredActiveRunId.value))
const run = computed<RunView | null>(() => targets.value.viewed)
const activeRun = computed<RunView | null>(() => targets.value.active)
const waitingRun = computed<RunView | null>(() => targets.value.waiting)
const activeRunCount = computed(() => targets.value.activeRuns.length)
const viewedIndex = computed(() => runs.value.findIndex((item) => item.runId === run.value?.runId))
const viewedIsActive = computed(() => activeRun.value?.runId === run.value?.runId)
const unsettledActive = computed(() => targets.value.activeRuns.some((item) => UNSETTLED_STATUSES.includes(item.status)))
/** 活动目标状态徽章：只有真实非终态轮才显示为“活动”。 */
const viewedStatusClass = computed(() => {
  const status = run.value?.status ?? ''
  if (UNSETTLED_STATUSES.includes(status)) return 'active'
  if (status === 'WAITING_FOR_USER') return 'waiting'
  if (status === 'FAILED') return 'failed'
  if (status === 'CANCELLED') return 'cancelled'
  return ''
})
const activeLabel = computed(() => {
  const current = activeRun.value
  if (!current) return ''
  return statusLabels[current.status] ?? current.status
})

const draft = usePrivateDraft(computed(() => draftKey(accessContextKey(), conversation.value?.conversationId ?? null)))
const composeMode = ref<'free' | 'investigate' | 'observation'>('free')
const replyDraftStorageKey = computed(() => draftKey(accessContextKey(), conversation.value?.conversationId ?? null, 'reply:' + (waitingRun.value?.runId ?? 'none') + ':' + (waitingRun.value?.pendingQuestion?.pendingQuestionId ?? '') + ':' + (waitingRun.value?.pendingQuestion?.questionVersion ?? '')))
const replyDraft = usePrivateDraft(replyDraftStorageKey)
const discardedDraftRevision = ref(0)
const oldReplyDrafts = computed(() => {
  void runs.value; void replyDraft.value; void discardedDraftRevision.value
  return listDrafts(draftKey(accessContextKey(), conversation.value?.conversationId ?? null, 'reply:')).filter(item => item.key !== replyDraftStorageKey.value && !item.key.includes(':reply:none:'))
})
function discardOldReply(key: string) { writeDraft(key, ''); discardedDraftRevision.value++ }
/** 补答草稿所属的等待目标：切换目标时清空，避免草稿串用。 */
const replyDraftOwner = ref<string | null>(null)
const busy = ref(false)
const resumingRunId = ref<string | null>(null)
const uploading = ref(false)
const errorText = ref('')
const stagedFile = ref<File | null>(null)
const uploadedFile = ref<FileView | null>(null)
const selectedSkill = ref<SkillSelection | null>(null)
const cancelling = ref(false)
const pendingCancellation = ref<RunView | null>(null)
const cancelDialog = ref<HTMLDialogElement | null>(null)
let cancelTrigger: HTMLElement | null = null
watch(pendingCancellation, async target => {
  await nextTick()
  if (target) { cancelDialog.value?.showModal(); document.getElementById('confirm-run-cancel')?.focus() }
  else if (cancelTrigger?.isConnected) cancelTrigger.focus()
  else document.querySelector<HTMLElement>('.pro-task-title')?.focus()
})
function requestCancellation(): void {
  if (!canCancel.value || !activeRun.value) return
  cancelTrigger = document.activeElement instanceof HTMLElement ? document.activeElement : null
  pendingCancellation.value = activeRun.value
  void nextTick(() => document.getElementById('confirm-run-cancel')?.focus())
}
const recordDrawerOpen = ref(false)
const reportOpen = ref(false)
let reportReturnFocus: HTMLElement | null = null
const narrowLayout = window.matchMedia('(max-width: 1100px)')
const smallScreen = ref(narrowLayout.matches)
/** 深入调查载荷：由专业页按所看轮选中判断给出（选择动作零提交）。 */
const investigationPlan = ref<{ claimText: string; category: string; contextRefs: string[]; selectedAssetId: string | null } | null>(null)
const investigationEnabled = ref(false)

/* 展示用常量与作用域守卫：必须在任何 immediate watch 之前初始化。 */
const statusLabels: Record<string, string> = {
  QUEUED: '排队中',
  RUNNING: '检索与回答中',
  WAITING_FOR_USER: '等待你补充信息',
  COMPLETED: '已完成',
  FAILED: '执行失败',
  CANCELLED: '已取消'
}
const shortTime = new Intl.DateTimeFormat('zh-CN', { hour: '2-digit', minute: '2-digit', hour12: false })
const dateTime = new Intl.DateTimeFormat('zh-CN', { month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit', hour12: false })
function formatTime(value: string): string {
  const parsed = new Date(value)
  return Number.isNaN(parsed.getTime()) ? value : dateTime.format(parsed)
}

const sessionReads = createScopedLoader()
const skillReads = createScopedLoader()
const referenceReads = createScopedLoader()
const eventReads = createScopedLoader()
const scheduledReads = createScheduledReads()
const pollControllers = new Map<string, AbortController>()
let sceneGeneration = 0

const sceneKey = computed(() => conversation.value?.conversationId ?? '')
const viewedScopeKey = computed(() => scopedRunKey(accessContextKey(), sceneKey.value, run.value))

/** 所看轮的目标消息：只按该轮自己的 goalMessageId 匹配，不跟随输入框资产。 */
const goalMessage = computed(() => messages.value.find(message => message.messageId === run.value?.goalMessageId) ?? null)
/** 设备快照所用资产与业务时点：统一使用所看轮的权威对象，歧义/未确认不打开快照。 */
const viewedAssetId = computed(() => {
  const current = run.value
  const resolution = current?.assetResolution ?? (goalMessage.value?.selectedAssetId ? 'SELECTED' : 'UNCONFIRMED')
  return resolution === 'SELECTED' || resolution === 'CONFIRMED'
    ? current?.investigatedAssetId ?? goalMessage.value?.selectedAssetId ?? null : null
})
const viewedAsOf = computed(() => run.value?.asOf ?? null)

const registeredSources = ref<Awaited<ReturnType<typeof readReferences>>>([])
const diagnosisModel = computed(() => buildDiagnosisViewModel({
  run: run.value,
  goalMessage: goalMessage.value,
  references: registeredSources.value
}))

/** 判断选中：只切换查看焦点，零请求；换轮或结果替换时回到第一条。 */
const selectedClaimIndex = ref<number | null>(null)
const investigationDraft = usePrivateDraft(computed(() => draftKey(accessContextKey(), conversation.value?.conversationId ?? null, 'investigate:' + run.value?.runId + ':' + selectedClaimIndex.value)))
const selectedClaimOfPage = computed(() =>
  selectedClaimIndex.value === null ? null : diagnosisModel.value.claims[selectedClaimIndex.value] ?? null)
const composerText = computed({
  get: () => composeMode.value === 'investigate' ? investigationDraft.value : draft.value,
  set: value => {
    if (composeMode.value === 'investigate') investigationDraft.value = value
    else draft.value = value
  }
})
function selectClaim(index: number): void {
  if (index >= 0 && index < diagnosisModel.value.claims.length) selectedClaimIndex.value = index
}

/* ---------------- 所看终态轮的已登记来源 ---------------- */
type ReferencesState = 'idle' | 'loading' | 'ready' | 'empty' | 'unavailable' | 'denied' | 'notFound' | 'error'
const registeredReferences = computed(() => registeredSources.value.map(toReferenceView))
const referencesState = ref<ReferencesState>('idle')
const referencesNote = ref('')

function referencesNoteOf(state: ReferencesState): string {
  switch (state) {
    case 'loading': return '正在读取本轮已登记来源…'
    case 'empty': return '本轮没有已登记来源。'
    case 'unavailable': return '所选来源不可用或已超出查看范围；其余已获准来源仍可用。'
    case 'denied': return '无权访问本轮已登记来源。'
    case 'notFound': return '来源不可用或无权访问（服务端不区分两者）。'
    case 'error': return '来源读取失败，可重试；本轮诊断状态不受影响。'
    default: return ''
  }
}

const openedReferenceId = ref<string | null>(null)
const openReferenceKey = ref<string | null>(null)
let evidenceTrigger: HTMLElement | null = null
const openedReference = computed<ReferenceView | null>(() => {
  if (!openedReferenceId.value) return null
  return registeredReferences.value.find((reference) => reference.referenceId === openedReferenceId.value) ?? null
})

async function loadRegisteredReferences(runId: string): Promise<void> {
  const token = referenceReads.issue(viewedScopeKey.value)
  referencesState.value = 'loading'
  referencesNote.value = referencesNoteOf('loading')
  try {
    const loaded = await readReferences(runId)
    if (!referenceReads.isCurrent(token, viewedScopeKey.value)) return
    registeredSources.value = [...new Map(loaded.map(reference => [reference.referenceId, reference])).values()]
    referencesState.value = registeredReferences.value.length ? 'ready' : 'empty'
  } catch (error) {
    if (!referenceReads.isCurrent(token, viewedScopeKey.value)) return
    if (error instanceof ApiError && (error.status === 401 || error.status === 403)) referencesState.value = 'denied'
    else if (error instanceof ApiError && (error.status === 404 || error.code === 'NOT_FOUND_OR_DENIED')) referencesState.value = 'notFound'
    else referencesState.value = 'error'
  }
  if (referencesState.value === 'denied' || referencesState.value === 'notFound') registeredSources.value = []
  if (openedReferenceId.value && !openedReference.value) {
    closeReference()
    restoreFocusAfterEvidence()
  }
  referencesNote.value = referencesNoteOf(referencesState.value)
}

function openReference(referenceId: string): void {
  if (!registeredReferences.value.some(reference => reference.referenceId === referenceId)) return
  evidenceTrigger = document.activeElement instanceof HTMLElement ? document.activeElement : null
  openedReferenceId.value = referenceId
  openReferenceKey.value = referenceId
}
function closeReference(): void {
  const originKey = openedReferenceId.value
  openedReferenceId.value = null
  openReferenceKey.value = originKey
}
function restoreFocusAfterEvidence(restoreOrigin = true): void {
  const trigger = evidenceTrigger
  evidenceTrigger = null
  void nextTick(() => {
    const origin = restoreOrigin && trigger?.isConnected && trigger.dataset.evidenceId ? trigger : null
    const task = document.querySelector<HTMLElement>('.pro-task-title') ?? document.querySelector<HTMLElement>('.pro-title')
    ;(origin ?? task)?.focus?.()
  })
}

// 切轮：旧轮来源与证据同步退出，再按所看轮读取（结果自带引用优先，否则读已登记来源）
watch(
  [viewedScopeKey, () => run.value?.status],
  () => {
    referenceReads.issue('')
    registeredSources.value = []
    referencesState.value = 'idle'
    referencesNote.value = ''
    if (openedReferenceId.value) {
      closeReference()
      restoreFocusAfterEvidence(false)
    }
    const current = run.value
    if (!current) return
    if (!TERMINAL_EVENT_STATUSES.has(current.status)) return
    void loadRegisteredReferences(current.runId)
  },
  { immediate: true }
)

watch(
  () => [run.value?.runId ?? '', diagnosisModel.value.claims.length, diagnosisModel.value.defaultClaimIndex] as const,
  ([runId, count, defaultIndex], previous) => {
    if (count <= 0) {
      selectedClaimIndex.value = null
      return
    }
    const current = selectedClaimIndex.value
    if (previous?.[0] !== runId || current === null || current >= count) selectedClaimIndex.value = defaultIndex
  },
  { immediate: true }
)

// 深入调查载荷：只用所看轮选中判断的原文与已取得的合法引用
watch(
  [selectedClaimOfPage, run, registeredReferences],
  () => {
    const claim = selectedClaimOfPage.value
    const current = run.value
    if (!claim || !current || current.status !== 'COMPLETED') {
      investigationPlan.value = null
      investigationEnabled.value = false
      return
    }
    const available = new Set(registeredReferences.value.map((reference) => reference.referenceId))
    const seen = new Set<string>()
    const contextRefs: string[] = []
    for (const id of claim.referenceIds) {
      if (!available.has(id) || seen.has(id) || contextRefs.length >= 10) continue
      seen.add(id)
      contextRefs.push(id)
    }
    investigationPlan.value = {
      claimText: claim.text,
      category: diagnosisModel.value.category ?? 'KNOWLEDGE',
      contextRefs,
      selectedAssetId: viewedAssetId.value
    }
    investigationEnabled.value = true
  },
  { immediate: true }
)

// 所看轮选中的领域 Skill：随会话/轮次变化重新读取，迟到响应按代次丢弃
watch(
  () => sceneKey.value + '|' + (run.value?.runId ?? ''),
  async () => {
    const token = skillReads.issue(sceneKey.value + '|' + (run.value?.runId ?? ''))
    selectedSkill.value = null
    const runId = run.value?.runId
    if (!runId) return
    try {
      const skill = await readRunSkill(runId)
      if (skillReads.isCurrent(token, sceneKey.value + '|' + (run.value?.runId ?? ''))) selectedSkill.value = skill
    } catch {
      if (skillReads.isCurrent(token, sceneKey.value + '|' + (run.value?.runId ?? ''))) selectedSkill.value = null
    }
  },
  { immediate: true }
)
const skillLabel = computed(() => (selectedSkill.value ? '领域包 ' + selectedSkill.value.skillId + ' v' + selectedSkill.value.version : null))

/* ---------------- SSE：一条主订阅随时间线控制器 ---------------- */
let streamScene = 0
const timeline = createRunTimelineController({
  onError() {
    const runId = timeline.snapshot().runId
    if (runId && streamScene === sceneGeneration) {
      errorText.value = '实时更新已断开，仍可刷新调查状态。'
      void resyncRun(runId)
    }
  }
})
const timelineState = ref(timeline.snapshot())
const historicalEvents = ref<RunEvent[]>([])
const streamRunId = computed(() => activeRun.value?.runId ?? null)
const viewingStream = computed(() => Boolean(run.value && run.value.runId === streamRunId.value
  && run.value.runId === timelineState.value.runId))
const timelineEvents = computed(() => viewingStream.value ? [...timelineState.value.events] : historicalEvents.value)
const timelineLive = computed(() => viewingStream.value && timelineState.value.live)

function syncTimelineView(): void {
  timelineState.value = timeline.snapshot()
}
timeline.onChange(syncTimelineView)

function followActive(): void {
  streamScene = sceneGeneration
  if (streamRunId.value) void timeline.follow(streamRunId.value)
  else timeline.clear()
}

watch([sceneKey, streamRunId], followActive)
watch([sceneKey, () => run.value?.runId, () => run.value?.status, streamRunId],
  async ([conversationId, runId, , streamedRun]) => {
    const token = eventReads.issue(conversationId + '|' + runId)
    historicalEvents.value = []
    if (!runId || runId === streamedRun) return
    try {
      const events = await readRunEvents(runId)
      if (eventReads.isCurrent(token, sceneKey.value + '|' + run.value?.runId)) historicalEvents.value = events
    } catch {
      if (eventReads.isCurrent(token)) historicalEvents.value = []
    }
  })
/** 已处理过的 SSE 信号（runId:seq），重放旧事件不重复回读。 */
const handledSignals = new Set<string>()
watch(
  timelineState,
  () => {
    const current = timelineState.value.runId
    if (!current || streamScene !== sceneGeneration || current !== streamRunId.value) return
    for (const event of timelineState.value.events) {
      if (event.type !== 'RUN_WAITING') continue
      const signal = current + ':' + event.seq
      if (handledSignals.has(signal)) continue
      handledSignals.add(signal)
      // 流数据只作通知：按持久 Run 回读后再改界面状态
      void resyncRun(current)
    }
    const terminalSignal = current + ':terminal'
    if (timelineState.value.terminalStatus && !handledSignals.has(terminalSignal)) {
      handledSignals.add(terminalSignal)
      void resyncRun(current, true)
    }
  },
  { deep: false }
)

/* ---------------- 持久回读与安全轮询 ---------------- */
function upsertRun(view: RunView): void {
  const index = runs.value.findIndex((item) => item.runId === view.runId)
  if (index < 0) {
    runs.value = [...runs.value, view]
    return
  }
  if (!mergeRunView(runs.value[index], view)) return
  const next = [...runs.value]
  next[index] = view
  runs.value = next
}

function isCurrentScene(conversationId: string, generation: number): boolean {
  return sceneKey.value === conversationId && sceneGeneration === generation
}

async function refresh(): Promise<void> {
  const conversationId = conversation.value?.conversationId
  if (!conversationId) return
  const generation = sceneGeneration
  const token = sessionReads.issue('detail|' + conversationId)
  let detail
  try { detail = await readConversation(conversationId) } catch(e) {
    if (e instanceof ApiError && (e.status === 403 || e.status === 404)) {
      startNewConversation(); window.dispatchEvent(new Event('devicetruth-access-revoked'))
    }
    throw e
  }
  if (!isCurrentScene(conversationId, generation) || !sessionReads.isCurrent(token, 'detail|' + conversationId)) return
  applyDetail(detail.runs, detail.messages, detail.conversation)
}

function applyDetail(nextRuns: RunView[], nextMessages: MessageView[], nextConversation: ConversationView): void {
  const previous = conversation.value
  const sameScene = previous?.conversationId === nextConversation.conversationId
  if (!sameScene) {
    sceneGeneration += 1
    abortAllTracking()
    timeline.clear()
    handledSignals.clear()
    pinnedRunId.value = null
    preferredActiveRunId.value = null
    uploadedFile.value = null
    stagedFile.value = null
    uploading.value = false
    composeMode.value = 'free'
  }
  if (!sameScene || nextConversation.version >= previous.version) {
    conversation.value = nextConversation
    messages.value = nextMessages
  }
  runs.value = sameScene ? mergeRunList(runs.value, nextRuns) : nextRuns
  if (preferredActiveRunId.value && !runs.value.some((item) => item.runId === preferredActiveRunId.value
      && !TERMINAL_EVENT_STATUSES.has(item.status))) {
    preferredActiveRunId.value = null
  }
  ensureTracking()
}

/** SSE 信号触发的单轮回读：同一 Run 合并，只接受不早于已知版本的结果。 */
async function resyncRun(runId: string, terminal = false): Promise<void> {
  const conversationId = conversation.value?.conversationId
  if (!conversationId) return
  const generation = sceneGeneration
  await scheduledReads.run(`${generation}|${runId}|${terminal}`, async () => {
    try {
      const view = await readRun(runId)
      if (!isCurrentScene(conversationId, generation)) return
      upsertRun(view)
      await refresh()
      void refreshHistory()
    } catch {
      // 回读失败不改写界面状态，等待失效时由有界轮询与手动同步兜底
    }
  })
}

/** 有界轮询：非终态轮一个 Run 一条跟踪通道；终态或取消后停止。 */
function ensureTracking(): void {
  for (const item of runs.value) {
    if (!UNSETTLED_STATUSES.includes(item.status)) {
      pollControllers.get(item.runId)?.abort()
      pollControllers.delete(item.runId)
      if (resumingRunId.value === item.runId) resumingRunId.value = null
      continue
    }
    if (pollControllers.has(item.runId)) continue
    void track(item.runId)
  }
}

async function track(runId: string): Promise<void> {
  if (pollControllers.has(runId)) return
  const conversationId = conversation.value?.conversationId
  if (!conversationId) return
  const generation = sceneGeneration
  const controller = new AbortController()
  pollControllers.set(runId, controller)
  resumingRunId.value = runId
  try {
    const finished = await waitForRun(runId, {
      signal: controller.signal,
      onUpdate: (view) => {
        if (isCurrentScene(conversationId, generation) && !controller.signal.aborted) upsertRun(view)
      }
    })
    if (!isCurrentScene(conversationId, generation) || controller.signal.aborted) return
    upsertRun(finished)
    await refresh()
    void refreshHistory()
  } catch (error) {
    if (isCurrentScene(conversationId, generation) && !controller.signal.aborted) {
      errorText.value = describeError(error)
    }
  } finally {
    if (pollControllers.get(runId) === controller) {
      pollControllers.delete(runId)
      if (resumingRunId.value === runId) resumingRunId.value = null
    }
  }
}

const permissions = computed(() => runPermissions({
  readOnly: props.readOnly, actorUserId: props.actorId, administrator: props.administrator,
  viewed: run.value,
  activeRuns: targets.value.activeRuns,
  active: activeRun.value,
  waiting: waitingRun.value,
  busy: busy.value,
  hasDraft: draft.value.trim().length > 0,
  hasReplyDraft: replyDraft.value.trim().length > 0,
  investigationEnabled: investigationEnabled.value
}))
const canSend = computed(() => composeMode.value === 'investigate'
  ? permissions.value.canInvestigate && composerText.value.trim().length > 0
  : permissions.value.canSend && !uploading.value)
const canInvestigate = computed(() => permissions.value.canInvestigate)
const canCancel = computed(() => permissions.value.canCancel)

const investigateTitle = computed(() => {
  if (activeRunCount.value > 0) return '当前还有未结束的调查轮，完成后才能深入调查'
  if (!investigationEnabled.value) return '完成一轮并选中判断后可深入调查'
  const plan = investigationPlan.value
  return plan && plan.contextRefs.length
    ? `将继承 ${plan.contextRefs.length} 条关联引用开启新一轮调查`
    : '选中判断暂无已关联引用，仍可直接深入调查'
})
const sendHint = computed(() => {
  if (permissions.value.sendBlockedReason) return permissions.value.sendBlockedReason + '，输入已保留为草稿。'
  if (unsettledActive.value) return '等待中的调查轮会继续执行，结束后再发送新的描述。'
  if (waitingRun.value) return '请先回答上面的问题，再继续新的描述。'
  return ''
})

function describeError(error: unknown): string {
  if (error instanceof ApiError) return `${error.code}：${error.message}`
  return error instanceof Error ? error.message : '未知错误'
}

/* ---------------- 会话与轮次操作 ---------------- */
async function openConversation(conversationId: string): Promise<void> {
  if (busy.value || cancelling.value) return
  if (!conversationId || conversationId === conversation.value?.conversationId) {
    recordDrawerOpen.value = false
    return
  }
  busy.value = true
  errorText.value = ''
  try {
    const detail = await readConversation(conversationId)
    applyDetail(detail.runs, detail.messages, detail.conversation)
    rememberConversation(detail.conversation.conversationId)
    recordDrawerOpen.value = false
  } catch (error) {
    errorText.value = describeError(error)
    if (error instanceof ApiError && error.status === 409) {
      try { await refresh(); errorText.value = '调查状态已变化，草稿已保留。请查看最新状态后重新确认提交。' }
      catch { errorText.value = '提交未被接受，草稿已保留；最新状态读取失败，请点击刷新后再确认。' }
    }
  } finally {
    busy.value = false
  }
}

function startNewConversation(): void {
  if (busy.value || cancelling.value) return
  sceneGeneration += 1
  abortAllTracking()
  timeline.clear()
  handledSignals.clear()
  conversation.value = null
  messages.value = []
  runs.value = []
  pinnedRunId.value = null
  preferredActiveRunId.value = null
  selectedClaimIndex.value = null
  uploadedFile.value = null
  stagedFile.value = null
  uploading.value = false
  composeMode.value = 'free'
  errorText.value = ''
  reportOpen.value = false
  try {
    window.localStorage.removeItem(STORAGE_KEY)
  } catch {
    // 本地存储不可用时只影响下次自动找回，不影响本次草稿
  }
  recordDrawerOpen.value = false
}

function abortAllTracking(): void {
  for (const controller of pollControllers.values()) controller.abort()
  pollControllers.clear()
  resumingRunId.value = null
  scheduledReads.cancelAll()
}

function rememberConversation(conversationId: string): void {
  try {
    window.localStorage.setItem(STORAGE_KEY, conversationId)
  } catch {
    // 本地存储不可用时仅影响“重开自动找回”，刷新恢复仍可用
  }
}

async function refreshHistory(): Promise<void> {
  const generation = ++historyGeneration
  try {
    const page = await listConversationSummaries({
      q: historyQuery.value || undefined, status: historyStatus.value || undefined, limit: 20
    })
    if (generation !== historyGeneration) return
    conversations.value = page.items
    historyNextCursor.value = page.nextCursor
    historyError.value = ''
  } catch (error) {
    if (generation === historyGeneration) historyError.value = describeError(error)
  } finally {
    if (generation === historyGeneration) historyLoaded.value = true
  }
}

async function searchHistory(query: string): Promise<void> {
  historyQuery.value = query.trim()
  await refreshHistory()
}

async function filterHistory(status: string): Promise<void> {
  historyStatus.value = status as typeof historyStatus.value
  await refreshHistory()
}

async function loadMoreHistory(): Promise<void> {
  if (!historyNextCursor.value || historyLoadingMore.value) return
  historyLoadingMore.value = true
  const generation = historyGeneration
  try {
    const page = await listConversationSummaries({
      q: historyQuery.value || undefined, status: historyStatus.value || undefined,
      cursor: historyNextCursor.value, limit: 20
    })
    if (generation !== historyGeneration) return
    const merged = appendConversationPage(conversations.value, page)
    conversations.value = merged.items
    historyNextCursor.value = merged.nextCursor
    historyError.value = ''
  } catch (error) {
    if (generation === historyGeneration) historyError.value = describeError(error)
  } finally {
    historyLoadingMore.value = false
  }
}

const historyItems = computed(() => conversations.value.map(item => ({
  conversationId: item.conversationId,
  title: item.title,
  assetId: item.investigatedAssetId,
  runCount: item.runCount,
  time: dateTime.format(new Date(item.updatedAt)),
  badge: conversationBadge(item)
})))

function selectViewedRun(runId: string): void {
  if (runId !== run.value?.runId) composeMode.value = 'free'
  pinnedRunId.value = runId
  if (targets.value.activeRuns.some(item => item.runId === runId)) preferredActiveRunId.value = runId
  recordDrawerOpen.value = false
}
function goToActiveRun(): void {
  if (activeRun.value) selectViewedRun(activeRun.value.runId)
}
function focusWaitingRun(): void {
  if (waitingRun.value) selectViewedRun(waitingRun.value.runId)
  void nextTick(() => (questionField.value ?? draftInput.value)?.focus())
}

/* ---------------- 上传 ---------------- */
async function upload(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file || props.readOnly) return
  input.value = ''
  if (!(/\.(md|txt)$/i.test(file.name)) || !file.size || file.size > 1_000_000) {
    errorText.value = '请选择不超过 1,000,000 字节的非空 MD/TXT 文件。'
    return
  }
  if (!conversation.value) { stagedFile.value = file; return }
  await importAttachment(async () => (await uploadInvestigationAttachment(conversation.value!.conversationId, file)).fileId)
}

async function importAttachment(start: () => Promise<string>): Promise<void> {
  if (uploading.value) return
  const conversationId = conversation.value?.conversationId ?? ''
  const generation = sceneGeneration
  uploading.value = true
  errorText.value = ''
  try {
    const fileId = await start()
    if (!isCurrentScene(conversationId, generation)) return
    const imported = await waitForFile(fileId)
    if (!isCurrentScene(conversationId, generation)) return
    uploadedFile.value = imported
    if (imported.parseStatus !== 'PARSED')
      errorText.value = `文件导入失败（${imported.failureCode ?? imported.parseStatus}）`
  } catch (error) {
    if (isCurrentScene(conversationId, generation)) errorText.value = describeError(error)
  } finally {
    if (isCurrentScene(conversationId, generation)) uploading.value = false
  }
}

function retryAttachment(): void {
  const file = uploadedFile.value
  if (file?.parseStatus === 'FAILED') void importAttachment(async () => (await retryFile(file.fileId)).fileId)
}

function submitOnEnter(event: KeyboardEvent, action: () => Promise<void>): void {
  if (event.shiftKey || event.isComposing || event.keyCode === 229) return
  event.preventDefault()
  if (!event.repeat) void action()
}

/* ---------------- 提交：普通消息 / 深入调查 / 补答 ---------------- */
function enterNewRun(accepted: RunView): void {
  upsertRun(accepted)
  pinnedRunId.value = null
  preferredActiveRunId.value = accepted.runId
  const generation = sceneGeneration
  void refresh().catch(error => {
    if (isCurrentScene(accepted.conversationId, generation)) errorText.value = describeError(error)
  })
  ensureTracking()
  void refreshHistory()
}

async function submit(): Promise<void> {
  if (!canSend.value) return
  if (composeMode.value === 'investigate') return investigateCurrent()
  busy.value = true
  errorText.value = ''
  try {
    let attachments = uploadedFile.value?.parseStatus === 'PARSED' ? [uploadedFile.value.fileId] : []
    const submittedDraftKey = draftKey(accessContextKey(), conversation.value?.conversationId ?? null)
    const submittedDraft = draft.value
    const current = await ensureConversation(conversation.value, createConversation)
    conversation.value = current
    draft.value = submittedDraft
    rememberConversation(current.conversationId)
    if (stagedFile.value) {
      uploading.value = true
      const uploaded = await uploadInvestigationAttachment(current.conversationId, stagedFile.value)
      uploadedFile.value = await waitForFile(uploaded.fileId)
      if (uploadedFile.value.parseStatus !== 'PARSED') throw new ApiError(uploadedFile.value.failureCode ?? 'FILE_NOT_READY', 409, '附件尚未解析成功，请处理附件后再提交')
      attachments = [uploaded.fileId]
      stagedFile.value = null
      uploading.value = false
    }
    const accepted = await sendMessage(
      current.conversationId,
      composeMode.value === 'observation' ? '现场观察（用户报告，未经核实）：\n' + draft.value : draft.value,
      undefined,
      attachments,
      selectedAssetId.value || null,
      [],
      current.version,
      discussionQuotes.value.map(({discussionId, version}) => ({discussionId, version}))
    )
    draft.value = ''
    writeDraft(submittedDraftKey, '')
    discussionQuotes.value = []
    uploadedFile.value = null
    composeMode.value = 'free'
    enterNewRun(accepted)
  } catch (error) {
    errorText.value = describeError(error)
    if (error instanceof ApiError && error.status === 409) {
      try { await refresh(); errorText.value = '调查状态已变化，草稿已保留。请查看最新状态后重新确认提交。' }
      catch { errorText.value = '提交未被接受，草稿已保留；最新状态读取失败，请点击刷新后再确认。' }
    }
  } finally {
    busy.value = false
    uploading.value = false
  }
}

async function investigateCurrent(): Promise<void> {
  const plan = investigationPlan.value
  if (!canInvestigate.value || !conversation.value || !plan || !composerText.value.trim()) return
  busy.value = true
  errorText.value = ''
  try {
    const accepted = await sendMessage(
      conversation.value.conversationId,
      composerText.value,
      undefined,
      [],
      plan.selectedAssetId,
      plan.contextRefs,
      conversation.value.version,
      discussionQuotes.value.map(({discussionId, version}) => ({discussionId, version}))
    )
    investigationDraft.value = ''
    discussionQuotes.value = []
    composeMode.value = 'free'
    enterNewRun(accepted)
  } catch (error) {
    errorText.value = describeError(error)
    if (error instanceof ApiError && error.status === 409) {
      try { await refresh(); errorText.value = '调查状态已变化，草稿已保留。请查看最新状态后重新确认提交。' }
      catch { errorText.value = '提交未被接受，草稿已保留；最新状态读取失败，请点击刷新后再确认。' }
    }
  } finally {
    busy.value = false
  }
}

/**
 * 补答：始终使用等待目标自己的 runId/pendingQuestionId/questionVersion 三元组。
 * 版本过期或目标不匹配时保留草稿、回读最新问题，绝不把草稿转成普通新消息。
 */
async function answerQuestion(): Promise<void> {
  const target = waitingRun.value
  const question = target?.pendingQuestion
  if (!permissions.value.canReply || !conversation.value || !target || !question) return
  busy.value = true
  errorText.value = ''
  try {
    const accepted = await replyToQuestion(
      conversation.value.conversationId,
      { runId: target.runId, pendingQuestionId: question.pendingQuestionId, questionVersion: question.questionVersion },
      replyDraft.value,
      undefined,
      discussionQuotes.value.map(({discussionId, version}) => ({discussionId, version}))
    )
    replyDraft.value = ''
    discussionQuotes.value = []
    replyDraftOwner.value = null
    preferredActiveRunId.value = accepted.runId
    enterNewRun(accepted)
  } catch (error) {
    if (error instanceof ApiError && (error.code === 'STALE_OR_UNRELATED_REPLY' || error.code === 'REPLY_TARGET_REQUIRED')) {
      errorText.value = `${error.code}：追问已变化，草稿已保留，请核对上方最新问题后重新提交`
      try {
        await refresh()
      } catch {
        // 回读失败时保留原提示，草稿不丢
      }
    } else {
      errorText.value = describeError(error)
    }
  } finally {
    busy.value = false
  }
}

async function cancelActive(): Promise<void> {
  const current = pendingCancellation.value
  if (!current || cancelling.value) return
  if (!canCancel.value || activeRun.value?.runId !== current.runId) {
    pendingCancellation.value = null; errorText.value = '活动轮或权限已变化，请重新核对取消目标'; return
  }
  cancelling.value = true
  errorText.value = ''
  try {
    const updated = await cancelRunRequest(current.runId)
    upsertRun(updated)
    await refresh()
    void refreshHistory()
  } catch (error) {
    // 终态竞争：409 后以持久状态为准回读，不假设取消成功
    if (error instanceof ApiError && error.status === 409) {
      errorText.value = '该调查已结束或状态已变化，已为你回读最新状态'
      try {
        await refresh()
      } catch {
        // 回读失败保留原提示
      }
    } else {
      errorText.value = describeError(error)
    }
  } finally {
    cancelling.value = false
    pendingCancellation.value = null
  }
}

function retrySync(): void {
  void manualSync()
}

async function manualSync(): Promise<void> {
  errorText.value = ''
  try {
    await refresh()
    followActive()
    void refreshHistory()
  } catch (error) {
    errorText.value = describeError(error)
  }
}

/* ---------------- 焦点 ---------------- */
const draftInput = ref<HTMLTextAreaElement | null>(null)
const questionField = ref<HTMLTextAreaElement | null>(null)
function focusFieldNote(): void {
  if (waitingRun.value) {
    focusWaitingRun()
    return
  }
  composeMode.value = 'observation'
  void nextTick(() => draftInput.value?.focus())
}

function focusInvestigation(): void {
  const plan = investigationPlan.value
  if (!canInvestigate.value || !plan) return
  if (!investigationDraft.value) investigationDraft.value = buildInvestigationMessage(plan.claimText, plan.category as never)
  composeMode.value = 'investigate'
  void nextTick(() => draftInput.value?.focus())
}

function openRecord(section?: string): void {
  recordDrawerOpen.value = true
  if (section) void nextTick(() => document.getElementById(section)?.focus())
}

function showReport(): void {
  if (!run.value) {
    openRecord('pro-history')
    return
  }
  reportReturnFocus = document.activeElement instanceof HTMLElement ? document.activeElement : null
  reportOpen.value = true
  recordDrawerOpen.value = false
  void nextTick(() => document.getElementById('diagnosis-report-title')?.focus())
}

function closeReport(): void {
  reportOpen.value = false
  const target = reportReturnFocus
  reportReturnFocus = null
  void nextTick(() => {
    if (target?.isConnected) target.focus()
    else document.querySelector<HTMLElement>('.pro-task-title')?.focus()
  })
}

function printReport(): void {
  window.print()
}

watch([() => run.value?.runId, selectedClaimIndex], () => {
  if (composeMode.value === 'investigate') composeMode.value = 'free'
})

// 补答草稿按等待目标隔离：目标变化时清空，避免把别的问题的草稿提交给新目标
watch(
  () => waitingRun.value?.runId ?? null,
  (runId) => {
    if (replyDraftOwner.value !== runId) {
      replyDraftOwner.value = runId
    }
  }
)
watch(
  () => replyDraft.value,
  () => {
    if (waitingRun.value) replyDraftOwner.value = waitingRun.value.runId
  }
)

const onKeydown = (event: KeyboardEvent): void => {
  if (event.key === 'Escape') pendingCancellation.value = null
  if (event.key === 'Escape' && recordDrawerOpen.value && !openedReferenceId.value) recordDrawerOpen.value = false
}
const onLayoutChange = (event: MediaQueryListEvent): void => { smallScreen.value = event.matches }

let collaborationSync: ReturnType<typeof setInterval> | undefined
let syncingCollaboration = false
onMounted(async () => {
  collaborationSync = setInterval(async () => {
    if (!conversation.value || busy.value || syncingCollaboration) return
    syncingCollaboration = true
    try { const version = conversation.value.version; await refresh(); if (conversation.value?.version !== version) void refreshHistory() } catch (e) { errorText.value = describeError(e) }
    finally { syncingCollaboration = false }
  }, 5000)
  narrowLayout.addEventListener('change', onLayoutChange)
  window.addEventListener('keydown', onKeydown)
  try {
    let remembered: string | null = null
    try {
      remembered = window.localStorage.getItem(STORAGE_KEY)
    } catch {
      remembered = null
    }
    if (remembered) {
      try {
        const detail = await readConversation(remembered)
        applyDetail(detail.runs, detail.messages, detail.conversation)
        await refresh()
      } catch {
        startNewConversation()
      }
    }
    assets.value = await listAssets()
    await refreshHistory()
  } catch (error) {
    errorText.value = describeError(error)
  }
})

onUnmounted(() => {
  clearInterval(collaborationSync)
  sceneGeneration += 1
  narrowLayout.removeEventListener('change', onLayoutChange)
  window.removeEventListener('keydown', onKeydown)
  timeline.close()
  abortAllTracking()
})
</script>

<template>
  <main class="pro-light">
    <div v-if="smallScreen && recordDrawerOpen" class="pro-drawer-scrim" @click="recordDrawerOpen = false"></div>

    <!-- 顶栏：品牌 / 模式 / 任务标题 / 查看轮与活动轮 / 操作 -->
    <header class="pro-topbar">
      <div class="pro-brand">
        <span class="brand-mark"><UiIcon name="spark" /></span>
        <span>DeviceTruth</span>
      </div>
      <span class="pro-mode-chip">专业工作台</span>
      <nav class="pro-topnav" aria-label="专业工作台导航">
        <a href="#pro-workspace" aria-current="page">诊断工作台</a>
        <button type="button" @click="openRecord('pro-assets')">设备资料</button>
        <button type="button" @click="openRecord('pro-events')">调查过程</button>
        <button type="button" @click="showReport">报告与历史</button>
      </nav>
      <div class="pro-top-actions">
      <span class="pro-status" :class="viewedStatusClass" data-testid="viewed-status">
        <span class="dot"></span>{{ run ? statusLabels[run.status] ?? run.status : '未开始' }}
        <template v-if="run && viewedIndex >= 0"> · 所看第 {{ viewedIndex + 1 }} 轮</template>
      </span>
      <span v-if="activeRun" class="pro-status active" data-testid="active-status">
        <span class="dot"></span>
        活动第 {{ runs.findIndex(item => item.runId === activeRun?.runId) + 1 }} 轮 · {{ activeLabel }}
      </span>
      <button v-if="activeRun && !viewedIsActive" type="button" class="secondary" @click="goToActiveRun">
        <UiIcon name="target" /> 回到活动轮
      </button>
      <span v-if="activeRunCount > 1" class="pro-status waiting" role="alert" data-testid="multiple-active">
        存在 {{ activeRunCount }} 个未决轮，请逐轮定位
      </span>
        <button v-if="activeRun" class="secondary" :disabled="!canCancel || cancelling" @click="requestCancellation">
          {{ cancelling ? '正在取消…' : '取消调查' }}
        </button>
        <button v-if="!busy && conversation" class="secondary" aria-label="刷新" title="刷新" @click="manualSync">
          <UiIcon name="refresh" />
        </button>
        <button class="secondary" :aria-expanded="recordDrawerOpen" @click="recordDrawerOpen = !recordDrawerOpen">
          <UiIcon name="menu" /> 查看调查记录
        </button>
        <button v-if="run" type="button" class="pro-outline pro-mobile-report" @click="showReport"><UiIcon name="file" /> 报告</button>
        <a class="pro-outline pro-mobile-compose" :href="waitingRun ? '#pending-question-title' : '#pro-composer'">
          <UiIcon name="chat" /> 继续调查
        </a>
      </div>
    </header>

    <div class="pro-shell">
      <!-- 桌面常驻「近期调查」；窄屏由顶部“查看调查记录”打开同一列表 -->
      <RecentInvestigations :read-only="props.readOnly" v-if="!smallScreen" :items="historyItems" :current-id="sceneKey" :busy="busy || cancelling" :history-loaded="historyLoaded"
                            :history-error="historyError" :query="historyQuery" :status="historyStatus" :next-cursor="historyNextCursor"
                            :loading-more="historyLoadingMore"
                            @create="startNewConversation" @retry="refreshHistory" @select="openConversation"
                            @search="searchHistory" @filter="filterHistory" @load-more="loadMoreHistory">

      </RecentInvestigations>

      <section id="pro-workspace" class="pro-main">
        <div class="main-scroll">
          <p v-if="errorText" class="error" role="alert">
            {{ errorText }}
            <button type="button" class="text-button" @click="retrySync">重新同步</button>
          </p>
          <ProfessionalDiagnosisPage v-if="!reportOpen" :run="run"
                                     :model="diagnosisModel"
                                     :selected-claim="selectedClaimOfPage"
                                     :selected-index="selectedClaimIndex"
                                     :registered-references="registeredReferences"
                                     :references-state="referencesState === 'idle' ? 'loading' : referencesState"
                                     :references-note="referencesNote"
                                     :skill-label="skillLabel"
                                     :failure-text="describeFailure(run?.failureCode)"
                                     @select="selectClaim"
                                     @open-reference="openReference"
                                     @history="openRecord('pro-history')"
                                     @asset="openRecord('pro-assets')"
                                     @retry-references="run && loadRegisteredReferences(run.runId)">
            <template #empty>
          <div class="empty pro-card">
            <div class="empty-icon"><UiIcon name="spark" /></div>
            <h1 class="pro-title" tabindex="-1">开始一次新调查</h1>
            <p>在下方输入设备现象，助手会查找资料并整理可核验的线索。</p>
          </div>
            </template>
            <template #composer>

        <!-- 待答正文：始终展示持久 pendingQuestion，与消息记录/展开状态无关 -->
        <section v-if="waitingRun?.pendingQuestion" ref="waitingNotice" class="question" data-testid="pending-question"
                 :data-run-id="waitingRun.runId" role="region" aria-label="需要补充信息">
          <h2 id="pending-question-title"><UiIcon name="warning" /> 需要补充信息</h2>
          <p class="question-owner">
             待答轮：第 {{ runs.findIndex(item => item.runId === waitingRun?.runId) + 1 }} 轮
            <template v-if="waitingRun?.runId !== run?.runId">（当前正在查看其他轮次）</template>
          </p>
          <p class="question-text">{{ waitingRun.pendingQuestion.question }}</p>
          <p v-if="waitingRun.pendingQuestion.requestedFields.length" class="hint">
            需要：{{ waitingRun.pendingQuestion.requestedFields.join('、') }}
          </p>
          <textarea ref="questionField" v-model="replyDraft" :disabled="props.readOnly || busy" rows="3" aria-label="补充信息" placeholder="按问题补充信息"
                    @keydown.enter="submitOnEnter($event, answerQuestion)"></textarea>
          <button class="primary" :disabled="!permissions.canReply" @click="answerQuestion">提交补充信息</button>
        </section>

        <section v-if="oldReplyDrafts.length" class="pro-card old-reply-drafts"><h3>未提交的旧追问草稿</h3><p>追问已被其他成员处理或已变化。以下内容仍是你的私人草稿，不会自动改成新分析。</p><article v-for="item in oldReplyDrafts" :key="item.key"><label>旧追问草稿<textarea :value="item.text" readonly rows="3" aria-label="未提交的旧追问草稿"></textarea></label><button type="button" @click="discardOldReply(item.key)">丢弃这份草稿</button></article></section>
        <InvestigationCollaboration v-if="conversation" :key="conversation.conversationId" :conversation-id="conversation.conversationId" :read-only="!!props.readOnly" :member-names="props.memberNames" :refresh-token="uploadedFile?.fileId + (uploadedFile?.parseStatus ?? '')" @quote="q => { if (!discussionQuotes.some(item => item.discussionId === q.discussionId) && discussionQuotes.length < 10) discussionQuotes = [...discussionQuotes, q] }" />
        <section v-if="discussionQuotes.length" aria-label="待提交讨论引用"><h3>待提交的讨论（未核实）</h3><article v-for="q in discussionQuotes" :key="q.discussionId"><strong>{{ q.authorName }}</strong><p>{{ q.content }}</p><button @click="discussionQuotes = discussionQuotes.filter(item => item.discussionId !== q.discussionId)">移除引用</button></article></section>
        <p v-if="props.readOnly" role="status">当前为只读成员，可查看调查与报告。</p>
        <!-- 底部操作栏：深入调查 / 补充现场观察 / 追加调查要求 -->
        <footer id="pro-composer" class="pro-action-wrap pro-card">
          <form class="pro-action-bar composer" @submit.prevent="submit">
            <div class="pro-compose-head">
              <h3>继续调查</h3><span v-if="activeRun?.executionActorUserId" class="hint">本轮发起人：{{ activeRun.executionActorUserId === props.actorId ? '我' : props.memberNames?.[activeRun.executionActorUserId] ?? activeRun.executionActorUserId }}</span>
              <div class="pro-compose-modes" role="group" aria-label="输入模式">
              <button type="button" :disabled="props.readOnly" :aria-pressed="composeMode === 'free'" @click="composeMode = 'free'">自由提问</button>
            <button type="button" :aria-pressed="composeMode === 'investigate'" :disabled="!canInvestigate" :title="investigateTitle"
                    @click="focusInvestigation">
              <UiIcon name="target" /> 深入调查
            </button>
            <button type="button" :disabled="props.readOnly" :aria-pressed="composeMode === 'observation'" title="以未核实的用户报告开启新一轮调查"
                    @click="focusFieldNote">
              <UiIcon name="chat" /> 补充现场观察
            </button>
              </div>
            </div>
            <div class="pro-action-input">
              <textarea ref="draftInput" v-model="composerText" rows="3" maxlength="7980"
                        :disabled="busy || props.readOnly"
                        aria-label="向 Agent 追加调查要求"
                        :placeholder="composeMode === 'observation' ? '描述新增观察、读数和获取时间…' : '记录设备现象，或补充你希望调查的问题…'"
                        @keydown.enter="submitOnEnter($event, submit)"></textarea>
            </div>
            <div class="pro-compose-actions">
            <label class="upload-label" :class="{ disabled: uploading || composeMode === 'investigate' }" title="添加 Markdown 资料">
              <input class="visually-hidden" type="file" aria-label="添加 Markdown 资料"
                     accept=".md,.txt,text/markdown,text/plain" :disabled="props.readOnly || uploading || busy || composeMode === 'investigate'" @change="upload" />
              <UiIcon name="attach" /> 添加附件
            </label>
            <label class="pro-asset-chip" title="关联设备（仅用于新的普通提交）">
              <UiIcon name="layers" />
              <select id="asset" v-model="selectedAssetId" :disabled="props.readOnly || busy || composeMode === 'investigate'" aria-label="关联设备">
                <option value="">不指定设备</option>
                <option v-for="asset in assets" :key="asset" :value="asset">{{ asset }}</option>
              </select>
            </label>
            <button type="submit" class="pro-send" :disabled="!canSend" aria-label="发送">
              <UiIcon name="send" /> {{ composeMode === 'observation' ? '提交观察' : '提交调查' }}
            </button>
            </div>
          </form>
          <p v-if="composeMode === 'observation'" class="pro-action-hint">作为未核实的用户报告开启新一轮，不写入设备事实。可随附已导入资料。</p>
          <p v-if="composeMode === 'investigate'" class="pro-action-hint">{{ investigateTitle }}。不随附此输入区中的文件。</p>
          <p v-if="sendHint" class="pro-action-hint" role="status">
            {{ sendHint }}
            <template v-if="resumingRunId && resumingRunId === activeRun?.runId">正在恢复调查…</template>
          </p>
          <p v-if="stagedFile" class="pro-action-hint" role="status">待发送附件：{{ stagedFile.name }}（首次提交时上传，仅用于本调查）<button type="button" @click="stagedFile = null">移除待发送附件</button></p>
          <p v-if="uploading" class="pro-action-hint" role="status">正在上传并导入，请稍候。</p>
          <p v-if="uploadedFile?.parseStatus === 'PARSED'" class="pro-action-hint" role="status">
            <span class="attachment-chip"><UiIcon name="file" /> {{ uploadedFile.originalName }}（仅自由提问或观察时随附）</span>
            <button type="button" class="text-button" :disabled="uploading || busy" @click="uploadedFile = null">移除附件</button>
          </p>
          <p v-if="uploadedFile && uploadedFile.parseStatus !== 'PARSED'" class="pro-action-hint" role="status">
            {{ uploadedFile.originalName }}：{{ uploadedFile.parseStatus }}
            <span v-if="uploadedFile.failureCode">（{{ uploadedFile.failureCode }}）</span>
            <button v-if="uploadedFile.parseStatus === 'FAILED'" type="button" class="text-button" :disabled="uploading || busy" @click="retryAttachment">重试导入</button>
          </p>
          <p class="pro-disclaimer">回答可能包含 SIMULATED 演示数据。知识引用仅反映资料记载，设备现状需现场核验。</p>
        </footer>
            </template>
            <template #timeline>
              <details v-if="run" class="pro-process pro-card" :key="run.runId">
                <summary><UiIcon name="clock" /> 本轮 Agent 调查过程 <span>{{ timelineEvents.length }} 条事件</span></summary>
                <RunTimeline v-if="timelineEvents.length" :events="timelineEvents" :live="timelineLive"
                             :status="statusLabels[run.status] ?? run.status" :duration="''" />
                <p v-else class="hint">暂无运行事件。</p>
              </details>
            </template>
          </ProfessionalDiagnosisPage>
          <DiagnosisReport v-else-if="run" :run="run" :model="diagnosisModel" :references="registeredReferences"
                           :references-state="referencesState === 'idle' ? 'loading' : referencesState"
                           :references-note="referencesNote" :failure-text="describeFailure(run.failureCode)"
                           @close="closeReport" @print="printReport" @open-reference="openReference"
                           @retry-references="loadRegisteredReferences(run.runId)" />
        </div>
      </section>
    </div>

    <!-- 调查记录抽屉：会话 / 轮次 / 消息 / 时间线 / 设备快照 / 已登记来源 -->
    <aside v-if="recordDrawerOpen" class="pro-drawer open" role="complementary" aria-label="调查记录">
      <div class="pro-drawer-head">
        <h2><UiIcon name="menu" /> 调查记录</h2>
        <button class="secondary" :disabled="busy" aria-label="刷新记录" @click="manualSync">
          <UiIcon name="refresh" />
        </button>
        <button v-if="!smallScreen" class="secondary" :disabled="props.readOnly || busy" @click="startNewConversation"><UiIcon name="plus" /> 新调查</button>
        <button class="icon-button" aria-label="关闭调查记录" @click="recordDrawerOpen = false">
          <UiIcon name="close" />
        </button>
      </div>
      <div class="pro-drawer-body">
        <RecentInvestigations :read-only="props.readOnly" v-if="smallScreen" :items="historyItems" :current-id="sceneKey" :busy="busy || cancelling" :history-loaded="historyLoaded"
                            :history-error="historyError" :query="historyQuery" :status="historyStatus" :next-cursor="historyNextCursor"
                            :loading-more="historyLoadingMore"
                            @create="startNewConversation" @retry="refreshHistory" @select="openConversation"
                            @search="searchHistory" @filter="filterHistory" @load-more="loadMoreHistory" />
        <template v-if="run">
          <h3 id="pro-history" tabindex="-1">调查轮次</h3>
          <p v-if="!runs.length" class="hint">本会话还没有调查轮次。</p>
          <nav v-else class="run-picker" aria-label="调查轮次">
            <button v-for="(item, index) in runs" :key="item.runId" type="button" class="run-chip"
                    :class="{ active: item.runId === run?.runId, activity: item.runId === activeRun?.runId }"
                    :aria-pressed="item.runId === run?.runId"
                    @click="selectViewedRun(item.runId)">
              第 {{ index + 1 }} 轮 · {{ statusLabels[item.status] ?? item.status }}
              <template v-if="item.runId === activeRun?.runId"> · 活动</template>
              <template v-else-if="item.runId !== run?.runId"> · 查看</template>
            </button>
          </nav>
          <p v-if="waitingRun" class="hint">
            <button type="button" class="text-button" @click="focusWaitingRun">
              <UiIcon name="warning" /> 定位待答轮（第 {{ runs.findIndex(item => item.runId === waitingRun?.runId) + 1 }} 轮）
            </button>
          </p>
          <p class="hint">
            所看轮目标：{{ goalMessage?.content ?? '（不可用）' }}
            <template v-if="viewedAssetId"> · 设备 {{ viewedAssetId }}</template>
            · {{ formatTime(run.requestedAt) }}
            <template v-if="run.activeDurationMs != null"> · 耗时 {{ Math.round(run.activeDurationMs / 1000) }} 秒</template>
          </p>

          <h3>会话消息</h3>
          <div v-if="!messages.length" class="hint">暂无消息。</div>
          <article v-for="message in messages" :key="message.messageId" class="message">
            <strong>{{ message.role === 'USER' ? (message.actorUserId === props.actorId ? '我' : message.actorUserId ? props.memberNames?.[message.actorUserId] ?? '成员 ' + message.actorUserId.slice(0, 8) : '历史作者未知') + '（用户报告，未核实）' : 'DeviceTruth Agent' }}</strong>
            <time :datetime="message.requestedAt">{{ shortTime.format(new Date(message.requestedAt)) }}</time>
            <p v-if="message.role === 'USER'" class="bubble">{{ message.content }}</p>
            <p v-else>{{ message.content }}</p><blockquote v-for="quote in message.discussionQuotes" :key="quote.discussionId"><strong>{{ quote.authorName }}</strong> · {{ quote.createdAt }} · 未核实讨论<p>{{ quote.content }}</p></blockquote>
          </article>

          <h3 id="pro-events" tabindex="-1">运行时间线</h3>
          <RunTimeline v-if="timelineEvents.length" :events="timelineEvents" :live="timelineLive"
                       :status="run ? statusLabels[run.status] ?? run.status : ''" :duration="''" />
          <p v-else class="hint">暂无运行事件。</p>

          <h3 id="pro-assets" tabindex="-1">设备快照（所看轮：{{ viewedAssetId ?? '未关联' }}）</h3>
          <p class="hint">业务时点 {{ viewedAsOf ? formatTime(viewedAsOf) : '未返回' }}；该轮按此资产与时点查询，底部选择只影响新提交。</p>
          <EvidenceComparison v-if="viewedAssetId" :key="(run?.runId ?? '') + '|' + viewedAssetId + '|' + (viewedAsOf ?? '')"
                              :asset-id="viewedAssetId" :as-of="viewedAsOf" />
          <p v-else class="hint">该轮未关联设备。</p>

          <h3>本轮已登记来源（{{ registeredReferences.length }}）</h3>
          <p class="hint">{{ referencesNote }}</p>
          <p v-if="referencesState === 'empty'" class="hint">本轮没有已登记来源。</p>
        </template>
        <p v-else id="pro-history" tabindex="-1" class="hint">当前会话还没有调查轮次。</p>
      </div>
    </aside>

    <dialog ref="cancelDialog" v-if="pendingCancellation" class="cancel-confirm" aria-labelledby="cancel-dialog-title" @cancel.prevent="!cancelling && (pendingCancellation = null)">
      <h2 id="cancel-dialog-title">确认取消此轮调查？</h2><p>目标 Run：{{ pendingCancellation.runId }}</p><p>取消后保留已有输入、来源与历史，不会自动提交草稿。</p>
      <button id="confirm-run-cancel" type="button" :disabled="cancelling" @click="cancelActive">{{ cancelling ? '正在取消…' : '确认取消此轮' }}</button><button type="button" :disabled="cancelling" @click="pendingCancellation = null">保留运行</button>
    </dialog>
    <!-- 证据模态抽屉：三种终态共用，挂载在状态分支之外 -->
    <EvidenceModal :open="openedReferenceId !== null && !!openedReference" :reference="openedReference"
                   :origin-key="openReferenceKey"
                   @close="closeReference(); restoreFocusAfterEvidence()" />
  </main>
</template>

<style src="./pro.css"></style>

<style scoped>
.old-reply-drafts { padding:18px; margin:14px 0; } .old-reply-drafts textarea { width:100%; font:inherit; }
.cancel-confirm { position:fixed; z-index:1000; inset:30% max(16px,calc((100vw - 520px)/2)) auto; padding:24px; background:white; border:1px solid #cbd5e1; border-radius:12px;  overflow-wrap:anywhere; } .cancel-confirm::backdrop { background:#12203366; } .cancel-confirm button { margin:8px; padding:10px; font:inherit; }
</style>
