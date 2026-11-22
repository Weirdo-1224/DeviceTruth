<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import {
  accessContextKey,
  ApiError,
  cancelRun as cancelRunRequest,
  createConversation,
  describeFailure,
  listAssetSummaries,
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
  type AccountView,
  type AssetSummary,
  type ConversationSummary,
  type ConversationView,
  type FileView,
  type MessageView,
  type RunEvent,
  type RunView,
  type SkillSelection,
  type WorkspaceView
} from './api'
import InvestigationCollaboration from './components/InvestigationCollaboration.vue'
import RunTimeline from './components/RunTimeline.vue'
import DiagnosisReport from './components/DiagnosisReport.vue'
import WorkspaceShell, { type WorkspacePage } from './components/WorkspaceShell.vue'
import DeviceArchivePage from './components/DeviceArchivePage.vue'
import KnowledgeLibraryPage from './components/KnowledgeLibraryPage.vue'
import TeamMembersPage from './components/TeamMembersPage.vue'
import CreateTeamPage from './components/CreateTeamPage.vue'
import ProfessionalDiagnosisPage from './components/ProfessionalDiagnosisPage.vue'
import EvidenceComparison from './components/EvidenceComparison.vue'
import EvidenceModal from './components/EvidenceModal.vue'
import UiIcon from './components/UiIcon.vue'
import { createRunTimelineController } from './timeline'
import { buildInvestigationMessage, buildDiagnosisViewModel, toReferenceView, type ReferenceView } from './diagnosisViewModel'
import { resolveRunTargets, runPermissions, UNSETTLED_STATUSES } from './runTargets'
import { createScopedLoader, mergeRunList, mergeRunView, scopedRunKey } from './scope'
import { appendConversationPage, conversationBadge, investigationTabs } from './history'
import { ensureConversation, initialMessage } from './draftSubmission'
import { draftKey, usePrivateDraft, writeDraft, listDrafts } from './privateDrafts'
import { createScheduledReads, TERMINAL_EVENT_STATUSES } from './scheduler'

const props = defineProps<{
  readOnly?: boolean
  actorId?: string
  administrator?: boolean
  memberNames?: Record<string, string>
  account: AccountView
  space: WorkspaceView
  spaces: WorkspaceView[]
  globalError?: string
  notice?: string
  invitationToken?: string | null
}>()
const emit = defineEmits<{
  selectSpace: [workspaceId: string]
  logout: []
  acceptInvitation: []
  members: [names: Record<string, string>]
  changed: []
  workspaceCreated: [space: WorkspaceView]
}>()
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
const assets = ref<AssetSummary[]>([])
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
const activeLabel = computed(() => {
  const current = activeRun.value
  if (!current) return ''
  return statusLabels[current.status] ?? current.status
})

const draft = usePrivateDraft(computed(() => draftKey(accessContextKey(), conversation.value?.conversationId ?? null)))
const newTitle = usePrivateDraft(computed(() => draftKey(accessContextKey(), conversation.value?.conversationId ?? null, 'title')))
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
const reportOpen = ref(false)
const currentPage = ref<WorkspacePage>('investigation')
const activeTab = ref<'overview' | 'discussion' | 'records' | 'process'>('overview')
const tabs = computed(() => investigationTabs(props.space.kind === 'TEAM'))
const materialDrawer = ref<'sources' | 'attachments' | 'asset' | null>(null)
let reportReturnFocus: HTMLElement | null = null
const narrowLayout = window.matchMedia('(max-width: 600px)')
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
  : permissions.value.canSend && !uploading.value && (!!conversation.value || !!newTitle.value.trim()))
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
    return
  }
  busy.value = true
  errorText.value = ''
  try {
    const detail = await readConversation(conversationId)
    applyDetail(detail.runs, detail.messages, detail.conversation)
    rememberConversation(detail.conversation.conversationId)
    currentPage.value = 'investigation'
    activeTab.value = 'overview'
    materialDrawer.value = null
    void nextTick(() => window.scrollTo({top: 0}))
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
  currentPage.value = 'investigation'
  activeTab.value = 'overview'
  materialDrawer.value = null
  void nextTick(() => window.scrollTo({top: 0}))
  try {
    window.localStorage.removeItem(STORAGE_KEY)
  } catch {
    // 本地存储不可用时只影响下次自动找回，不影响本次草稿
  }
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
const currentTitle = computed(() => messages.value.find(message => message.role === 'USER')?.content.split('\n')[0]
  ?? conversations.value.find(item => item.conversationId === sceneKey.value)?.title
  ?? (newTitle.value.trim() || '新建调查'))
const currentCreator = computed(() => {
  const author = messages.value.find(message => message.role === 'USER')?.actorUserId
  return author ? author === props.actorId ? props.account.displayName : props.memberNames?.[author] ?? author : '历史作者未知'
})
const currentRound = computed(() => viewedIndex.value >= 0 ? viewedIndex.value + 1 : '—')
const activeActorName = computed(() => {
  const actor = activeRun.value?.executionActorUserId
  return actor ? actor === props.actorId ? props.account.displayName : props.memberNames?.[actor] ?? actor : '历史执行人未知'
})

function selectViewedRun(runId: string): void {
  if (runId !== run.value?.runId) composeMode.value = 'free'
  pinnedRunId.value = runId
  if (targets.value.activeRuns.some(item => item.runId === runId)) preferredActiveRunId.value = runId
}
function goToActiveRun(): void {
  if (activeRun.value) selectViewedRun(activeRun.value.runId)
  activeTab.value = 'overview'
}
function focusWaitingRun(): void {
  if (waitingRun.value) selectViewedRun(waitingRun.value.runId)
  void nextTick(() => revealComposer(questionField.value ?? draftInput.value))
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
    const submittedTitleKey = draftKey(accessContextKey(), conversation.value?.conversationId ?? null, 'title')
    const submittedDraft = draft.value
    const initialTitle = conversation.value ? '' : newTitle.value.trim()
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
    const messageText = composeMode.value === 'observation' ? '现场观察（用户报告，未经核实）：\n' + draft.value : draft.value
    const accepted = await sendMessage(
      current.conversationId,
      initialMessage(initialTitle, messageText),
      undefined,
      attachments,
      selectedAssetId.value || null,
      [],
      current.version,
      discussionQuotes.value.map(({discussionId, version}) => ({discussionId, version}))
    )
    draft.value = ''
    newTitle.value = ''
    writeDraft(submittedTitleKey, '')
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
function revealComposer(field: HTMLTextAreaElement | null): void {
  const panel = field?.closest<HTMLDetailsElement>('.dt-composer')
  if (panel) panel.open = true
  field?.focus()
}
function focusFieldNote(): void {
  if (waitingRun.value) {
    focusWaitingRun()
    return
  }
  composeMode.value = 'observation'
  void nextTick(() => revealComposer(draftInput.value))
}

function focusInvestigation(): void {
  const plan = investigationPlan.value
  if (!canInvestigate.value || !plan) return
  if (!investigationDraft.value) investigationDraft.value = buildInvestigationMessage(plan.claimText, plan.category as never)
  composeMode.value = 'investigate'
  void nextTick(() => revealComposer(draftInput.value))
}

function openRecord(): void {
  currentPage.value = 'investigation'
  activeTab.value = 'records'
  reportOpen.value = false
}
function navigate(page: WorkspacePage): void {
  currentPage.value = page
  reportOpen.value = false
  materialDrawer.value = null
  void nextTick(() => window.scrollTo({top: 0}))
}
function showRun(runId: string, tab: 'overview' | 'process' = 'overview'): void {
  selectViewedRun(runId)
  activeTab.value = tab
  reportOpen.value = false
}
function toggleMaterialDrawer(drawer: 'sources' | 'attachments' | 'asset'): void {
  materialDrawer.value = materialDrawer.value === drawer ? null : drawer
}
function investigateAsset(assetId: string): void {
  selectedAssetId.value = assetId
  startNewConversation()
}
function quoteForAnalysis(quote: {discussionId: string; version: number; content: string; authorName: string}): void {
  if (props.readOnly) return
  if (!discussionQuotes.value.some(item => item.discussionId === quote.discussionId) && discussionQuotes.value.length < 10) {
    discussionQuotes.value = [...discussionQuotes.value, quote]
  }
  activeTab.value = 'overview'
  materialDrawer.value = null
  void nextTick(() => revealComposer(waitingRun.value ? questionField.value : draftInput.value))
}

function showReport(): void {
  if (!run.value) {
    openRecord()
    return
  }
  reportReturnFocus = document.activeElement instanceof HTMLElement ? document.activeElement : null
  currentPage.value = 'investigation'
  materialDrawer.value = null
  reportOpen.value = true
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
  if (event.key === 'Escape' && materialDrawer.value && !openedReferenceId.value) materialDrawer.value = null
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
    assets.value = await listAssetSummaries()
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
  <main class="pro-light team-app">
    <WorkspaceShell :account="props.account" :space="props.space" :spaces="props.spaces" :page="currentPage"
      :current-id="sceneKey" :read-only="!!props.readOnly" :busy="busy || cancelling" :items="historyItems"
      :history-loaded="historyLoaded" :history-error="historyError" :query="historyQuery" :status="historyStatus"
      :next-cursor="historyNextCursor" :loading-more="historyLoadingMore" :compact="smallScreen"
      :global-error="props.globalError" :notice="props.notice"
      @navigate="navigate" @select-space="emit('selectSpace', $event)" @logout="emit('logout')" @create="startNewConversation"
      @select="openConversation" @retry="refreshHistory" @search="searchHistory" @filter="filterHistory" @load-more="loadMoreHistory">
      <section v-if="props.invitationToken" class="dt-run-banner" aria-label="团队邀请确认">
        请确认以 {{ props.account.email }} 接受邮箱邀请。<button type="button" class="dt-button" @click="emit('acceptInvitation')">确认加入团队</button>
      </section>
      <DeviceArchivePage v-if="currentPage === 'devices'" :space="props.space" :assets="assets" :loading="!historyLoaded" @investigate="investigateAsset" />
      <KnowledgeLibraryPage v-else-if="currentPage === 'knowledge'" :key="'knowledge:' + props.space.workspaceId" :space="props.space" :member-names="props.memberNames ?? {}" />
      <TeamMembersPage v-else-if="currentPage === 'members'" :key="'members:' + props.space.workspaceId + ':' + props.space.role" :space="props.space"
        @members="emit('members', $event)" @changed="emit('changed')" />
      <CreateTeamPage v-else-if="currentPage === 'create-team'" @created="emit('workspaceCreated', $event)" @cancel="navigate('investigation')" />
      <section v-else class="dt-investigation" :class="{ 'dt-investigation-new': !conversation }">
        <header v-if="conversation" class="dt-pagehead dt-investigation-head">
          <p class="dt-crumb">{{ props.space.name }} / {{ props.space.kind === 'TEAM' ? '团队调查' : '我的调查' }}</p>
          <div class="dt-title-row"><h1 class="pro-task-title" tabindex="-1">{{ currentTitle }}</h1><button v-if="conversation" type="button" class="dt-button" @click="showReport">查看报告</button></div>
          <div class="dt-meta">
            <button v-if="viewedAssetId" type="button" class="dt-link" @click="toggleMaterialDrawer('asset')">{{ viewedAssetId }}</button><span v-else-if="conversation">未指定设备</span>
            <span v-if="conversation">当前查看第 {{ currentRound }} 轮</span><span v-if="conversation">{{ currentCreator }}创建</span>
            <span v-if="run?.dataNature" class="dt-tag">{{ run.dataNature }}</span>
            <span v-if="props.space.kind === 'TEAM'" class="dt-people">{{ Object.keys(props.memberNames ?? {}).length }} 位团队成员</span>
            <span v-if="activeRun && !viewedIsActive" class="dt-tag dt-warning">活动轮：{{ activeLabel }}</span>
            <button v-if="activeRun && !viewedIsActive" type="button" class="dt-link" @click="goToActiveRun">回到活动轮</button>
            <button v-if="conversation" type="button" class="dt-link" @click="manualSync">刷新</button>
          </div>
        </header>
        <div v-if="conversation && !reportOpen" class="dt-tabbar">
          <div class="dt-tabs" role="tablist" aria-label="调查内容">
            <button v-for="tab in tabs" :key="tab.id" type="button" role="tab" :id="'dt-tab-' + tab.id" aria-controls="dt-active-panel" :aria-selected="activeTab === tab.id" @click="activeTab = tab.id">{{ tab.label }}</button>
          </div>
          <button type="button" class="dt-link" :aria-expanded="!!materialDrawer" @click="toggleMaterialDrawer('sources')">资料与证据 <span class="dt-tag">{{ referencesState === 'idle' ? '未读取' : referencesState === 'loading' ? '读取中' : referencesState === 'ready' || referencesState === 'empty' ? registeredReferences.length : '暂不可用' }}</span></button>
        </div>
        <div class="dt-content-grid" :class="{'dt-drawer-open': materialDrawer && !reportOpen}">
          <main class="dt-main" id="dt-active-panel" role="tabpanel">
            <p v-if="errorText" class="error" role="alert">{{ errorText }} <button type="button" class="dt-link" @click="retrySync">重新同步</button></p>
            <p v-if="props.readOnly" class="dt-readonly">你以只读成员身份查看：可阅读调查、讨论、资料和报告，不能提交信息或发起分析。</p>
            <p v-if="activeRunCount > 1" class="dt-readonly" role="alert">存在 {{ activeRunCount }} 个未决轮，请先在分析记录中逐轮定位。</p>
            <DiagnosisReport v-if="reportOpen && run" :run="run" :model="diagnosisModel" :references="registeredReferences"
              :references-state="referencesState === 'idle' ? 'loading' : referencesState" :references-note="referencesNote"
              :failure-text="describeFailure(run.failureCode)" @close="closeReport" @print="printReport"
              @open-reference="openReference" @retry-references="loadRegisteredReferences(run.runId)" />
            <template v-else>
              <section v-if="!conversation || activeTab === 'overview'" class="dt-overview" aria-labelledby="dt-tab-overview">
                <section v-if="activeRun" class="dt-run-banner" :class="viewedIsActive ? 'dt-agent-hero' : 'dt-history-active'" aria-label="活动调查状态">
                  <div class="dt-agent-heading">
                    <span v-if="viewedIsActive" class="dt-agent-mark"><UiIcon :name="activeRun.status === 'WAITING_FOR_USER' ? 'chat' : 'activity'" /></span>
                    <div>
                      <p class="dt-eyebrow">{{ viewedIsActive ? 'DEVICETRUTH AGENT' : '正在查看历史结果' }}</p>
                      <h2>{{ viewedIsActive ? (activeRun.status === 'WAITING_FOR_USER' ? '等待你补充信息' : activeRun.status === 'QUEUED' ? '调查已进入队列' : 'Agent 正在调查') : `第 ${runs.findIndex(item => item.runId === activeRun?.runId) + 1} 轮仍有未决调查` }}</h2>
                      <p>{{ activeRun.status === 'WAITING_FOR_USER' ? '回答下方问题，继续同一轮调查。' : '调查活动实时记录；你仍可查看资料、参与讨论和准备私人草稿。' }}</p>
                    </div>
                    <button v-if="canCancel" type="button" class="dt-button dt-cancel" :disabled="cancelling" @click="requestCancellation">{{ cancelling ? '正在请求取消…' : viewedIsActive ? '取消本轮' : `取消活动第 ${runs.findIndex(item => item.runId === activeRun?.runId) + 1} 轮` }}</button>
                  </div>
                  <dl v-if="viewedIsActive" class="dt-run-facts">
                    <div><dt>调查对象</dt><dd>{{ diagnosisModel.selectedAssetId || diagnosisModel.assetResolutionLabel }}</dd></div>
                    <div><dt>当前轮次</dt><dd>第 {{ currentRound }} 轮 <span>{{ activeLabel }}</span></dd></div>
                    <div><dt>提交时间</dt><dd>{{ formatTime(activeRun.requestedAt) }}</dd></div>
                    <div><dt>累计执行用时</dt><dd>{{ activeRun.activeDurationMs != null ? Math.round(activeRun.activeDurationMs / 1000) + ' 秒' : '未报告' }}</dd></div>
                  </dl>
                  <p class="dt-run-owner">执行人：{{ activeActorName }}<span v-if="!viewedIsActive"> · 当前查看第 {{ currentRound }} 轮</span></p>
                </section>
                <ProfessionalDiagnosisPage embedded :run="run" :model="diagnosisModel" :selected-claim="selectedClaimOfPage"
                  :selected-index="selectedClaimIndex" :registered-references="registeredReferences"
                  :references-state="referencesState === 'idle' ? 'loading' : referencesState" :references-note="referencesNote"
                  :skill-label="skillLabel" :failure-text="describeFailure(run?.failureCode)"
                  @select="selectClaim" @open-reference="openReference" @history="openRecord"
                  @asset="toggleMaterialDrawer('asset')" @retry-references="run && loadRegisteredReferences(run.runId)">
                  <template #timeline>
                    <RunTimeline v-if="run && viewedIsActive && run.status !== 'WAITING_FOR_USER'" overview class="dt-activity-feed" :events="timelineEvents" :live="timelineLive" :status="statusLabels[run.status] ?? run.status" :duration="''">
                      <button type="button" class="dt-link" @click="activeTab = 'process'">查看完整过程</button>
                    </RunTimeline>
                  </template>
                  <template #composer>
                    <details class="dt-composer" :class="{ 'dt-composer-new': !conversation }" :open="!conversation || !runs.length || !!waitingRun" aria-label="补充调查信息">
                      <summary>{{ waitingRun ? '待你补充 · 继续本轮调查' : activeRun ? '准备下一轮补充（私人草稿）' : conversation ? '补充调查（私人草稿）' : '新建检修调查' }}</summary>
                      <div class="dt-composer-body">
                      <header v-if="!conversation" class="dt-create-intro"><span class="dt-create-symbol"><UiIcon name="tool" /></span><p class="dt-eyebrow">{{ props.space.name }} · {{ props.space.kind === 'TEAM' ? '团队可见' : '仅自己可见' }}</p><h1 class="pro-task-title" tabindex="-1">开始一次设备检修调查</h1><p>描述设备现象，Agent 将检索资料、核对记录，提供有依据的检修建议。</p></header>
                      <div v-if="conversation" class="dt-section-head"><h3>{{ waitingRun ? '回答本轮问题' : conversation ? '提交给 Agent' : '描述设备问题' }}</h3><span>{{ props.account.displayName }}提交 · {{ props.space.kind === 'TEAM' ? '团队可见' : '仅自己可见' }}</span></div>
                      <p v-if="conversation" class="dt-help">{{ conversation ? '提交现场描述、检查结果或问题；团队讨论需引用后才会提交给 Agent。' : '填写现象与已有检查，首次提交后开始调查。支持 MD / TXT 附件。' }}</p>
                      <div v-if="discussionQuotes.length" aria-label="待提交讨论引用">
                        <article v-for="quote in discussionQuotes" :key="quote.discussionId" class="dt-quote"><div class="dt-section-head"><strong>引用 {{ quote.authorName }} 的讨论</strong><button type="button" class="dt-link" @click="discussionQuotes = discussionQuotes.filter(item => item.discussionId !== quote.discussionId)">移除引用</button></div><p>{{ quote.content }}</p><span>用户讨论 · 未现场核实</span></article>
                      </div>
                      <section v-if="waitingRun?.pendingQuestion" class="question" data-testid="pending-question" :data-run-id="waitingRun.runId" aria-label="需要补充信息">
                        <h2 id="pending-question-title"><UiIcon name="warning" /> 需要补充信息</h2>
                        <p class="question-owner">待答 Run：{{ waitingRun.runId }}<template v-if="waitingRun.runId !== run?.runId">（当前正在查看其他轮次）</template></p>
                        <p class="question-text">{{ waitingRun.pendingQuestion.question }}</p>
                        <p v-if="waitingRun.pendingQuestion.requestedFields.length" class="hint">需要：{{ waitingRun.pendingQuestion.requestedFields.join('、') }}</p>
                        <textarea ref="questionField" v-model="replyDraft" :disabled="props.readOnly || busy" rows="3" aria-label="补充信息" placeholder="按问题补充信息" @keydown.enter="submitOnEnter($event, answerQuestion)"></textarea>
                        <button type="button" class="dt-button dt-primary" :disabled="!permissions.canReply" @click="answerQuestion">提交补答并继续</button>
                      </section>
                      <form v-else :class="{ 'dt-create-form': !conversation }" @submit.prevent="submit">
                        <label class="dt-field dt-device-field">排查设备（可选）<select v-model="selectedAssetId" :disabled="props.readOnly || busy || composeMode === 'investigate'" aria-label="关联设备"><option value="">暂不指定设备</option><option v-for="asset in assets" :key="asset.assetId" :value="asset.assetId">{{ asset.assetId }}{{ asset.displayName ? ' · ' + asset.displayName : '' }}{{ asset.deviceType ? '（' + asset.deviceType + '）' : '' }}</option></select></label>
                        <label v-if="!conversation" class="dt-field">调查标题<input v-model="newTitle" required maxlength="200" :disabled="busy || props.readOnly" placeholder="例如：车载座舱空调不转"></label>
                        <div v-if="conversation" class="dt-actions" role="group" aria-label="输入模式">
                          <button type="button" class="dt-button" :disabled="props.readOnly" :aria-pressed="composeMode === 'free'" @click="composeMode = 'free'">自由提问</button>
                          <button type="button" class="dt-button" :aria-pressed="composeMode === 'investigate'" :disabled="!canInvestigate" :title="investigateTitle" @click="focusInvestigation">深入调查</button>
                          <button type="button" class="dt-button" :disabled="props.readOnly" :aria-pressed="composeMode === 'observation'" @click="focusFieldNote">补充现场观察</button>
                        </div>
                        <label class="dt-field dt-description-field">{{ composeMode === 'observation' ? '现场观察（未核实）' : '现象、已有检查与调查要求' }}<textarea ref="draftInput" v-model="composerText" rows="3" maxlength="7980" :disabled="busy || props.readOnly" aria-label="向 Agent 追加调查要求" placeholder="描述出现条件、已有检查结果，或你希望继续调查的问题…" @keydown.enter="submitOnEnter($event, submit)"></textarea></label>
                        <div class="dt-input-footer">
                          <label class="dt-attachment-action"><UiIcon name="link" /> 添加附件<input class="visually-hidden" type="file" aria-label="添加 Markdown 资料" accept=".md,.txt,text/markdown,text/plain" :disabled="props.readOnly || uploading || busy || composeMode === 'investigate'" @change="upload"></label>

                          <span>{{ activeRun ? '草稿仅自己可见，不加入正在运行的分析' : conversation ? '提交后发起新一轮分析' : 'MD / TXT · 仅用于本调查' }}</span>
                          <button type="submit" class="dt-button dt-primary" :disabled="!canSend"><UiIcon name="send" /> {{ composeMode === 'observation' ? '提交观察' : conversation ? '提交并继续分析' : '创建并开始调查' }}</button>
                        </div>
                      </form>
                      <section v-if="oldReplyDrafts.length" class="dt-page-card dt-section"><h3>未提交的旧追问草稿</h3><p class="dt-help">追问已被其他成员处理或已变化。以下内容仍是你的私人草稿，不会自动改成新分析。</p><article v-for="item in oldReplyDrafts" :key="item.key"><label class="dt-field">旧追问草稿<textarea :value="item.text" readonly rows="3"></textarea></label><button type="button" class="dt-link" @click="discardOldReply(item.key)">丢弃这份草稿</button></article></section>
                      <p v-if="composeMode === 'investigate'" class="dt-help">{{ investigateTitle }}。不随附此输入区中的文件。</p>
                      <p v-if="sendHint" class="dt-help" role="status">{{ sendHint }}<template v-if="resumingRunId && resumingRunId === activeRun?.runId">正在同步本轮状态…</template></p>
                      <p v-if="stagedFile" class="dt-help" role="status">待发送附件：{{ stagedFile.name }}（首次提交时上传，仅用于本调查）<button type="button" class="dt-link" @click="stagedFile = null">取消本次附带</button></p>
                      <p v-if="uploading" class="dt-help" role="status">正在上传并导入，请稍候。</p>
                      <p v-if="uploadedFile" class="dt-help" role="status">{{ uploadedFile.originalName }}：{{ uploadedFile.parseStatus }}<span v-if="uploadedFile.failureCode">（{{ uploadedFile.failureCode }}）</span><button v-if="uploadedFile.parseStatus === 'FAILED'" type="button" class="dt-link" :disabled="uploading || busy" @click="retryAttachment">重试导入</button><button v-if="uploadedFile.parseStatus === 'PARSED'" type="button" class="dt-link" :disabled="uploading || busy" @click="uploadedFile = null">取消本次附带</button></p>
                      <div v-if="conversation" class="dt-section-head dt-section"><h3>资料与信息缺口</h3><button type="button" class="dt-link" @click="toggleMaterialDrawer('sources')">查看来源</button></div>
                      <p v-if="conversation" class="dt-help">{{ registeredReferences.length ? `本轮已登记 ${registeredReferences.length} 条来源。` : referencesState === 'idle' ? '尚未读取本轮来源清单。' : referencesState === 'empty' ? '本轮没有已登记来源。' : referencesNote || '来源清单暂不可用。' }}来源登记不等于现场核实。<button v-if="conversation" type="button" class="dt-link" @click="toggleMaterialDrawer('attachments')">管理调查附件</button></p>
                      <p class="dt-help">资料可能包含 SIMULATED 演示数据；资料记载与现场核验分别标识。</p>
                      </div>
                    </details>
                  </template>
                </ProfessionalDiagnosisPage>
              </section>
              <section v-else-if="activeTab === 'discussion'" aria-labelledby="dt-tab-discussion">
                <InvestigationCollaboration v-if="conversation" :key="'discussion:' + conversation.conversationId" mode="discussion" :conversation-id="conversation.conversationId" :read-only="!!props.readOnly" :member-names="props.memberNames" @quote="quoteForAnalysis" />
              </section>
              <section v-else-if="activeTab === 'records'" aria-labelledby="dt-tab-records">
                <div class="dt-section-head"><h2>正式提交与分析结果</h2><span>团队讨论不计入此处</span></div>
                <article v-for="message in messages" :key="message.messageId" class="dt-record">
                  <div class="dt-section-head"><strong>{{ message.role === 'USER' ? (message.actorUserId === props.actorId ? '我' : message.actorUserId ? props.memberNames?.[message.actorUserId] ?? message.actorUserId : '历史作者未知') + '提交补充' : 'DeviceTruth Agent' }}</strong><time>{{ formatTime(message.requestedAt) }}</time></div>
                  <p>{{ message.content }}</p><span v-if="message.role === 'USER'" class="dt-tag">用户提供 · 未现场核实</span>
                  <blockquote v-for="quote in message.discussionQuotes" :key="quote.discussionId" class="dt-quote"><strong>{{ quote.authorName }}</strong> · {{ quote.createdAt }} · 未核实讨论<p>{{ quote.content }}</p></blockquote>
                </article>
                <p v-if="!messages.length" class="dt-empty">还没有正式提交。</p>
                <div class="dt-section-head dt-section"><h2>调查轮次</h2><span>{{ runs.length }} 轮</span></div>
                <article v-for="(item, index) in runs" :key="item.runId" class="dt-record"><div class="dt-section-head"><strong>第 {{ index + 1 }} 轮 · {{ statusLabels[item.status] ?? item.status }}</strong><small>{{ item.executionActorUserId ? (props.memberNames?.[item.executionActorUserId] ?? item.executionActorUserId) : '历史执行人未知' }}执行 · {{ formatTime(item.requestedAt) }}</small></div><p>Run：{{ item.runId }}</p><button type="button" class="dt-link" @click="showRun(item.runId)">查看这一轮结果</button></article>
              </section>
              <section v-else aria-labelledby="dt-tab-process">
                <div class="dt-section-head"><h2>调查过程</h2><span class="dt-tag">{{ activeRun ? '有运行进行中' : '当前无运行' }}</span></div>
                <nav class="dt-run-picker" aria-label="选择过程轮次"><button v-for="(item, index) in runs" :key="item.runId" type="button" :aria-pressed="item.runId === run?.runId" @click="showRun(item.runId, 'process')">第 {{ index + 1 }} 轮 · {{ statusLabels[item.status] ?? item.status }}</button></nav>
                <div v-if="run" class="dt-process"><p>Run：{{ run.runId }}</p><p>业务时点：{{ formatTime(run.asOf) }}<template v-if="run.activeDurationMs != null"> · 累计执行用时 {{ Math.round(run.activeDurationMs / 1000) }} 秒</template></p><RunTimeline v-if="timelineEvents.length" :events="timelineEvents" :live="timelineLive" :status="statusLabels[run.status] ?? run.status" :duration="''" /><p v-else class="dt-empty">暂无运行事件。</p><details class="dt-technical"><summary>技术详情与原始原因码</summary><p>状态：{{ run.status }} · 原因码：{{ run.failureCode ?? '无' }}</p><p>数据性质：{{ run.dataNature }}</p><p>执行结束与故障查清分别表达，保留调查来源与记录。</p></details></div><p v-else class="dt-empty">尚无调查过程。</p>
              </section>
            </template>
          </main>
          <aside v-if="conversation && materialDrawer && !reportOpen" class="dt-material-drawer" aria-label="资料与证据">
            <div class="dt-drawer-head"><h2>{{ materialDrawer === 'sources' ? '资料与证据' : materialDrawer === 'attachments' ? '调查附件' : '排查设备' }}</h2><button type="button" class="dt-link" @click="materialDrawer = null">收起</button></div>
            <template v-if="materialDrawer === 'sources'">
              <p class="dt-help">当前查看第 {{ currentRound }} 轮<template v-if="referencesState === 'ready' || referencesState === 'empty'"> · 已登记 {{ registeredReferences.length }} 条工具来源</template></p><p class="dt-help">{{ referencesNote }}</p><button v-if="referencesState === 'error' || referencesState === 'notFound'" type="button" class="dt-link" @click="run && loadRegisteredReferences(run.runId)">重试读取来源</button>
              <button v-for="reference in registeredReferences" :key="reference.referenceId" type="button" class="dt-source-button" :data-evidence-id="reference.referenceId" @click="openReference(reference.referenceId)">《{{ reference.title }}》<small>{{ reference.sourceTypeLabel }} · 版本 {{ reference.sourceVersion }} · {{ reference.dataNature }}</small></button>
              <p v-if="!registeredReferences.length" class="dt-empty">{{ referencesState === 'idle' ? '尚未读取本轮来源清单。' : referencesState === 'empty' ? '本轮没有已登记来源。' : referencesNote || '来源清单暂不可用。' }}</p><div class="dt-section-head dt-section"><h3>本次调查附件</h3><button type="button" class="dt-link" @click="materialDrawer = 'attachments'">管理附件</button></div><p class="dt-help">讨论和用户附件不会因出现在界面里就变成已核实的现场证据。</p>
            </template>
            <InvestigationCollaboration v-else-if="materialDrawer === 'attachments'" :key="'materials:' + conversation.conversationId" mode="materials" :conversation-id="conversation.conversationId" :read-only="!!props.readOnly" :member-names="props.memberNames" :refresh-token="uploadedFile?.fileId + (uploadedFile?.parseStatus ?? '')" />
            <template v-else><h3>{{ viewedAssetId ?? '未指定设备' }}</h3><p class="dt-help">业务时点 {{ viewedAsOf ? formatTime(viewedAsOf) : '未返回' }}。该轮按此资产与时点查询，底部选择只影响新提交。</p><EvidenceComparison v-if="viewedAssetId" :key="(run?.runId ?? '') + '|' + viewedAssetId + '|' + (viewedAsOf ?? '')" :asset-id="viewedAssetId" :as-of="viewedAsOf" /><p v-else class="dt-empty">该轮未关联设备。</p></template>
          </aside>
        </div>
      </section>
    </WorkspaceShell>
    <dialog ref="cancelDialog" v-if="pendingCancellation" class="cancel-confirm" aria-labelledby="cancel-dialog-title" @cancel.prevent="!cancelling && (pendingCancellation = null)">
      <h2 id="cancel-dialog-title">确认取消此轮调查？</h2><p>目标 Run：{{ pendingCancellation.runId }}</p><p>取消后保留已有输入、来源与历史，不会自动提交草稿。</p><button id="confirm-run-cancel" type="button" :disabled="cancelling" @click="cancelActive">{{ cancelling ? '正在取消…' : '确认取消此轮' }}</button><button type="button" :disabled="cancelling" @click="pendingCancellation = null">保留运行</button>
    </dialog>
    <EvidenceModal :open="openedReferenceId !== null && !!openedReference" :reference="openedReference" :origin-key="openReferenceKey" @close="closeReference(); restoreFocusAfterEvidence()" />
  </main>
</template>

<style src="./pro.css"></style>

<style scoped>
.old-reply-drafts { padding:18px; margin:14px 0; } .old-reply-drafts textarea { width:100%; font:inherit; }
.cancel-confirm { position:fixed; z-index:1000; inset:30% max(16px,calc((100vw - 520px)/2)) auto; padding:24px; background:white; border:1px solid #cbd5e1; border-radius:12px;  overflow-wrap:anywhere; } .cancel-confirm::backdrop { background:#12203366; } .cancel-confirm button { margin:8px; padding:10px; font:inherit; }
</style>
