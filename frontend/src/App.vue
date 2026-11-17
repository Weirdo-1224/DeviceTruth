<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import {
  ApiError,
  cancelRun as cancelRunRequest,
  createConversation,
  describeFailure,
  listAssets,
  listConversations,
  readConversation,
  readReferences,
  readRunSkill,
  replyToQuestion,
  sendMessage,
  uploadMarkdown,
  waitForFile,
  waitForRun,
  type ConversationView,
  type FileView,
  type MessageView,
  type Reference,
  type RunEvent,
  type RunView,
  type SkillSelection,
  type AnswerCategory
} from './api'
import RunTimeline from './components/RunTimeline.vue'
import ProfessionalDiagnosisPage from './components/ProfessionalDiagnosisPage.vue'
import EvidenceComparison from './components/EvidenceComparison.vue'
import UiIcon from './components/UiIcon.vue'
import { createRunTimelineController } from './timeline'
import { buildInvestigationMessage } from './diagnosisViewModel'

const STORAGE_KEY = 'devicetruth.conversation-id'

const conversation = ref<ConversationView | null>(null)
const conversations = ref<ConversationView[]>([])
const assets = ref<string[]>([])
const selectedAssetId = ref<string>('')
const messages = ref<MessageView[]>([])
// 轮次表：保留 readConversation 返回的全部 runs；run 为当前查看轮（用户固定或跟随最新轮）。
const runs = ref<RunView[]>([])
/** 用户主动查看的历史轮次；null 表示跟随最新轮。新提交会清除固定并切到新轮，刷新不强制覆盖主动选择。 */
const pinnedRunId = ref<string | null>(null)
const latestRun = computed(() => runs.value.at(-1) ?? null)
const run = computed<RunView | null>(() =>
  runs.value.find((item) => item.runId === pinnedRunId.value) ?? latestRun.value)
/** 活动轮（最新轮）是否已到业务终态；取消/补答等操作始终指向活动轮，历史轮只读。 */
const activityTerminal = computed(() =>
  ['WAITING_FOR_USER', 'COMPLETED', 'FAILED', 'CANCELLED'].includes(latestRun.value?.status ?? ''))

/** 用服务器返回的 RunView 更新本地轮次表（按 runId 覆盖或追加），迟到响应由调用方按会话/runId 把关。 */
function upsertRun(view: RunView): void {
  const index = runs.value.findIndex((item) => item.runId === view.runId)
  if (index >= 0) runs.value[index] = view
  else runs.value.push(view)
}
const draft = ref('')
const replyDraft = ref('')
const busy = ref(false)
const resumingRunId = ref<string | null>(null)
const uploading = ref(false)
const errorText = ref('')
const uploadedFile = ref<FileView | null>(null)
const selectedSkill = ref<SkillSelection | null>(null)
const cancelling = ref(false)
const historyTitles = ref<Record<string, string>>({})
/** 调查记录抽屉：会话/轮次/消息/时间线/资产与依据统一收纳（设计稿顶栏“查看调查记录”）。 */
const recordDrawerOpen = ref(false)
/** 底部操作栏“深入调查”计划：由专业页按当前选中判断回传（选择动作零提交）。 */
const investigationPlan = ref<{
  enabled: boolean
  inheritedCount: number
  omittedCount: number
  inheritedTitles: string[]
  payload: { claimText: string; category: AnswerCategory; contextRefs: string[]; selectedAssetId: string | null } | null
} | null>(null)
const taskTitle = computed(() => messages.value.find(message => message.role === 'USER')?.content || '新调查')
const goalMessage = computed(() => messages.value.find(message => message.messageId === run.value?.goalMessageId))
/**
 * 本轮已登记引用：有结果时直接用 result.references；失败/取消轮没有结果，
 * 经 /references 回读已取回证据。run 切换后以 runId 丢弃迟到响应。
 */
const references = ref<Reference[]>([])
watch(
  () => (run.value ? run.value.runId + ':' + run.value.status : ''),
  async () => {
    const current = run.value
    if (!current) {
      references.value = []
      return
    }
    if (current.result) {
      references.value = current.result.references ?? []
      return
    }
    if (current.status === 'FAILED' || current.status === 'CANCELLED') {
      try {
        const loaded = await readReferences(current.runId)
        if (run.value?.runId === current.runId) references.value = loaded
      } catch {
        if (run.value?.runId === current.runId) references.value = []
      }
      return
    }
    references.value = []
  },
  { immediate: true }
)
const sources = computed(() => [...new Map(references.value.map(reference => [
  JSON.stringify([reference.sourceType, reference.sourceId, reference.sourceVersion]), reference
])).values()])
const activeDuration = computed(() => {
  const ms = run.value?.activeDurationMs
  if (ms == null) return ''
  if (ms < 1000) return ms + ' 毫秒'
  const seconds = Math.round(ms / 1000)
  return seconds < 60 ? seconds + ' 秒' : Math.floor(seconds / 60) + ' 分 ' + seconds % 60 + ' 秒'
})
const shortTime = new Intl.DateTimeFormat('zh-CN', { hour: '2-digit', minute: '2-digit', hour12: false })
const dateTime = new Intl.DateTimeFormat('zh-CN', { month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit', hour12: false })

watch([conversation, messages], ([current, currentMessages]) => {
  const title = currentMessages.find(message => message.role === 'USER')?.content
  if (current && title) historyTitles.value[current.conversationId] = title
})

// 运行时间线：SSE 订阅 + 历史补齐 + maxSeq 幂等去重都在控制器里；错误只降级为轮询兜底，不覆盖页面错误提示
const timeline = createRunTimelineController()
const timelineEvents = ref<RunEvent[]>([])
const timelineLive = ref(false)

function syncTimelineView(): void {
  const state = timeline.snapshot()
  timelineEvents.value = [...state.events]
  timelineLive.value = state.live
}

timeline.onChange(syncTimelineView)

/**
 * run 或其状态变化时对齐时间线：
 * 指向另一个 run（切换会话/任务，含选中历史里的终态 run）一律重新 follow；
 * 同 run 但视图未完整收尾（流已断且从未收到 RUN_TERMINAL）时也重新 follow——
 * 典型场景：流提前断开后 run 在别的页面推进到终态，切回时必须回读补齐历史。
 * 当前会话没有任何 run 时 clear()：旧时间线不再残留，迟到事件由 generation 与 runId 双保险丢弃。
 * follow 内部先关旧订阅；迟到的旧 run 事件由控制器按 generation 丢弃，不污染当前页面。
 */
watch(
  () => (run.value ? run.value.runId + ':' + run.value.status : ''),
  () => {
    const current = run.value
    if (!current) {
      timeline.clear()
      return
    }
    const state = timeline.snapshot()
    const incomplete = !state.live && state.terminalStatus === null
    if (state.runId !== current.runId || incomplete) {
      void timeline.follow(current.runId)
    }
  }
)

// SSE 只是通知；RUN_TERMINAL 流收尾后回读权威状态（Run/消息/引用由 PG 校准），断流不当作失败。
watch(
  () => timeline.snapshot().terminalStatus,
  (terminal, previous) => {
    if (terminal && terminal !== previous && conversation.value) void refresh()
  }
)

/** 当前 run 选中的领域 Skill（无选择时为空）；随 run 切换重新读取，失败不影响主流程。 */
watch(
  () => run.value?.runId ?? '',
  async (runId) => {
    if (!runId) {
      selectedSkill.value = null
      return
    }
    try {
      const skill = await readRunSkill(runId)
      if (run.value?.runId === runId) selectedSkill.value = skill
    } catch {
      if (run.value?.runId === runId) selectedSkill.value = null
    }
  },
  { immediate: true }
)

/** 取消运行：QUEUED/WAITING 立即生效，RUNNING 协作式收尾；终态再取消会得到 409。始终指向活动轮（最新轮）。 */
async function cancelCurrentRun(): Promise<void> {
  const current = latestRun.value
  if (!current || cancelling.value) return
  cancelling.value = true
  errorText.value = ''
  try {
    const updated = await cancelRunRequest(current.runId)
    if (latestRun.value?.runId === current.runId) upsertRun(updated)
    await refresh()
  } catch (error) {
    errorText.value = describeError(error)
  } finally {
    cancelling.value = false
  }
}

const statusLabels: Record<string, string> = {
  QUEUED: '排队中',
  RUNNING: '检索与回答中',
  WAITING_FOR_USER: '等待你补充信息',
  COMPLETED: '已完成',
  FAILED: '执行失败',
  CANCELLED: '已取消'
}

const statusLabel = computed(() => (run.value ? statusLabels[run.value.status] ?? run.value.status : '未开始'))
/** 顶栏状态徽章的语义色：进行中脉冲、等待琥珀、失败/取消红色、完成绿色。 */
const statusClass = computed(() => {
  const status = run.value?.status ?? ''
  if (['QUEUED', 'RUNNING'].includes(status)) return 'active'
  if (status === 'WAITING_FOR_USER') return 'waiting'
  if (status === 'FAILED') return 'failed'
  if (status === 'CANCELLED') return 'cancelled'
  return ''
})
// 补答/输入禁用永远看活动轮（最新轮）：查看历史旧轮不解除等待态约束，也不把旧轮当作可补答对象。
const pendingQuestion = computed(() => latestRun.value?.pendingQuestion ?? null)
const failureText = computed(() =>
  run.value && run.value.status === 'FAILED' ? describeFailure(run.value.failureCode) : ''
)
// 活动轮执行中保留输入草稿，但禁止另开并发提交或深入调查；结束后草稿原样可发。
const activityActive = computed(() => ['QUEUED', 'RUNNING'].includes(latestRun.value?.status ?? ''))
const canSend = computed(() => !busy.value && !activityActive.value && draft.value.trim().length > 0)
const investigateTitle = computed(() => {
  const plan = investigationPlan.value
  if (!plan || !plan.enabled) return '完成一轮并选中判断后可深入调查'
  const omitted = plan.omittedCount ? `，另有 ${plan.omittedCount} 条超出上限已省略` : ''
  return plan.inheritedCount
    ? `将继承 ${plan.inheritedCount} 条引用：${plan.inheritedTitles.map(t => '《' + t + '》').join('、')}${omitted}`
    : `选中判断暂无已关联引用，仍可直接深入调查${omitted}`
})

function describeError(error: unknown): string {
  if (error instanceof ApiError) return `${error.code}：${error.message}`
  return error instanceof Error ? error.message : '未知错误'
}

async function refresh(): Promise<void> {
  const conversationId = conversation.value?.conversationId
  if (!conversationId) return
  const detail = await readConversation(conversationId)
  if (conversation.value?.conversationId !== conversationId) return
  conversation.value = detail.conversation
  messages.value = detail.messages
  runs.value = detail.runs
  // 用户固定查看某历史轮时，刷新不强制跳回最新；未固定或固定的轮次已不在列表时跟随最新轮。
  if (!pinnedRunId.value || !detail.runs.some((item) => item.runId === pinnedRunId.value)) {
    pinnedRunId.value = null
  }
  const latest = detail.runs.length > 0 ? detail.runs[detail.runs.length - 1] : null
  // 刷新/重开时如果最新 run 还在排队或执行，继续安全轮询；PG 是唯一权威，等待态也会随轮询恢复
  if (latest && (latest.status === 'QUEUED' || latest.status === 'RUNNING')
      && !busy.value && resumingRunId.value !== latest.runId) {
    resumingRunId.value = latest.runId
    try {
      await track(latest.runId)
    } catch (error) {
      if (conversation.value?.conversationId === conversationId) errorText.value = describeError(error)
    } finally {
      if (resumingRunId.value === latest.runId) resumingRunId.value = null
    }
  }
}

/** 切换历史会话：PG 权威，消息、待答问题与结果都能找回，不依赖原线程。 */
async function openConversation(conversationId: string): Promise<void> {
  if (!conversationId || conversationId === conversation.value?.conversationId) return
  busy.value = true
  errorText.value = ''
  try {
    const detail = await readConversation(conversationId)
    conversation.value = detail.conversation
    messages.value = detail.messages
    runs.value = detail.runs
    pinnedRunId.value = null
    rememberConversation(detail.conversation.conversationId)
    busy.value = false
    await refresh()
  } catch (error) {
    errorText.value = describeError(error)
  } finally {
    busy.value = false
  }
}

async function startNewConversation(): Promise<void> {
  busy.value = true
  errorText.value = ''
  try {
    const created = await createConversation()
    conversation.value = created
    rememberConversation(created.conversationId)
    messages.value = []
    runs.value = []
    pinnedRunId.value = null
    await refreshHistory()
  } catch (error) {
    errorText.value = describeError(error)
  } finally {
    busy.value = false
  }
}

function rememberConversation(conversationId: string): void {
  try {
    window.localStorage.setItem(STORAGE_KEY, conversationId)
  } catch {
    // 本地存储不可用时仅影响“重开自动找回”，刷新恢复仍可用
  }
}

async function refreshHistory(): Promise<void> {
  conversations.value = await listConversations()
}

/** 轮询直到 WAITING_FOR_USER/COMPLETED/FAILED；运行结束后再从持久层刷新消息。 */
async function track(runId: string): Promise<void> {
  const conversationId = conversation.value?.conversationId
  // 会话切换即作废；查看轮可固定到历史轮，进度更新始终写入轮次表而不强制切视图。
  const isCurrent = () => conversation.value?.conversationId === conversationId
  const finished = await waitForRun(runId, {
    onUpdate: (view) => {
      if (isCurrent()) upsertRun(view)
    }
  })
  if (!isCurrent()) return
  upsertRun(finished)
  await refresh()
}

async function upload(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  uploading.value = true
  errorText.value = ''
  uploadedFile.value = null
  try {
    const accepted = await uploadMarkdown(file)
    uploadedFile.value = await waitForFile(accepted.fileId)
    if (uploadedFile.value.parseStatus !== 'PARSED')
      errorText.value = `文件导入失败（${uploadedFile.value.failureCode ?? uploadedFile.value.parseStatus}）`
  } catch (error) {
    errorText.value = describeError(error)
  } finally {
    uploading.value = false
    input.value = ''
  }
}

function submitOnEnter(event: KeyboardEvent, action: () => Promise<void>): void {
  if (event.shiftKey || event.isComposing || event.keyCode === 229) return
  event.preventDefault()
  if (!event.repeat) void action()
}

async function submit(): Promise<void> {
  if (!canSend.value || !conversation.value || pendingQuestion.value || activityActive.value) return
  busy.value = true
  errorText.value = ''
  try {
    const attachments = uploadedFile.value?.parseStatus === 'PARSED' ? [uploadedFile.value.fileId] : []
    const accepted = await sendMessage(
      conversation.value.conversationId,
      draft.value,
      undefined,
      attachments,
      selectedAssetId.value || null
    )
    draft.value = ''
    upsertRun(accepted)
    pinnedRunId.value = null
    await refresh()
    await track(accepted.runId)
  } catch (error) {
    errorText.value = describeError(error)
  } finally {
    busy.value = false
  }
}

/** 底部操作栏“深入调查”：显式新操作，无 replyTo，服务器开新 Run；正文含选中判断原文。 */
async function investigateCurrent(): Promise<void> {
  const payload = investigationPlan.value?.payload
  if (!conversation.value || busy.value || !activityTerminal.value || pendingQuestion.value || !payload) return
  busy.value = true
  errorText.value = ''
  try {
    const message = buildInvestigationMessage(payload.claimText, payload.category)
    const accepted = await sendMessage(
      conversation.value.conversationId,
      message,
      undefined,
      [],
      payload.selectedAssetId,
      payload.contextRefs
    )
    upsertRun(accepted)
    pinnedRunId.value = null
    await refresh()
    await track(accepted.runId)
  } catch (error) {
    errorText.value = describeError(error)
  } finally {
    busy.value = false
  }
}

/** “补充现场观察”：聚焦底部输入框，输入内容按既有普通消息路径作为用户报告开启新一轮。 */
const draftInput = ref<HTMLTextAreaElement | null>(null)
function focusFieldNote(): void {
  void nextTick(() => draftInput.value?.focus())
}

/** 补答：用活动轮（最新轮）的 pendingQuestionId/questionVersion 关联，沿同一个 run 继续。 */
async function answerQuestion(): Promise<void> {
  const question = pendingQuestion.value
  const target = latestRun.value
  if (busy.value || !conversation.value || !question || !target || replyDraft.value.trim().length === 0) return
  busy.value = true
  errorText.value = ''
  try {
    const accepted = await replyToQuestion(
      conversation.value.conversationId,
      {
        runId: target.runId,
        pendingQuestionId: question.pendingQuestionId,
        questionVersion: question.questionVersion
      },
      replyDraft.value
    )
    replyDraft.value = ''
    upsertRun(accepted)
    pinnedRunId.value = null
    await refresh()
    await track(accepted.runId)
  } catch (error) {
    // 等待态变化/问题版本冲突时保留草稿、回读当前问题，绝不静默转成普通新消息。
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

onMounted(async () => {
  try {
    // 先尝试从本地记录找回上次会话；找不回（或首次使用）就新建，历史列表始终可从服务器拉取
    let remembered: string | null = null
    try {
      remembered = window.localStorage.getItem(STORAGE_KEY)
    } catch {
      remembered = null
    }
    if (remembered) {
      try {
        const detail = await readConversation(remembered)
        conversation.value = detail.conversation
        await refresh()
      } catch {
        conversation.value = await createConversation()
        rememberConversation(conversation.value.conversationId)
      }
    } else {
      conversation.value = await createConversation()
      rememberConversation(conversation.value.conversationId)
    }
    assets.value = await listAssets()
    await refreshHistory()
  } catch (error) {
    errorText.value = describeError(error)
  }
})

onUnmounted(() => {
  timeline.close()
})
</script>

<template>
  <main class="pro-dark" @keydown.esc="recordDrawerOpen = false">
    <div v-if="recordDrawerOpen" class="pro-drawer-scrim" @click="recordDrawerOpen = false"></div>

    <!-- 顶栏：品牌 / 模式 / 任务标题 / 运行状态 / 操作 -->
    <header class="pro-topbar">
      <div class="pro-brand">
        <span class="brand-mark"><UiIcon name="spark" /></span>
        <span>DeviceTruth</span>
      </div>
      <span class="pro-mode-chip"><UiIcon name="target" /> 专业诊断</span>
      <h1 class="pro-title" :title="taskTitle">{{ taskTitle }}</h1>
      <span class="pro-status" :class="statusClass">
        <span class="dot"></span>{{ statusLabel }}
        <template v-if="selectedSkill"> · {{ selectedSkill.skillId }} v{{ selectedSkill.version }}</template>
      </span>
      <div class="pro-top-actions">
        <button v-if="latestRun && !activityTerminal" class="secondary" :disabled="cancelling" @click="cancelCurrentRun">
          {{ cancelling ? '正在取消…' : '取消调查' }}
        </button>
        <button v-if="!busy && conversation" class="secondary" aria-label="刷新" title="刷新" @click="refresh">
          <UiIcon name="refresh" />
        </button>
        <button class="secondary" :aria-expanded="recordDrawerOpen" @click="recordDrawerOpen = !recordDrawerOpen">
          <UiIcon name="menu" /> 查看调查记录
        </button>
      </div>
    </header>

    <!-- 主体：三栏工作台 + 追问卡片 + 底部操作栏 -->
    <section class="pro-body">
      <div class="main-scroll">
        <p v-if="errorText" class="error" role="alert">{{ errorText }}</p>
        <div v-if="!run" class="empty">
          <div class="empty-icon"><UiIcon name="spark" /></div>
          <h2>开始一次新调查</h2>
          <p>在下方输入设备现象，助手会查找资料并整理可核验的线索。</p>
        </div>
        <ProfessionalDiagnosisPage v-else :run="run" :goal-message="goalMessage"
                                   :references="references" :skill="selectedSkill" :failure-text="failureText"
                                   :busy="busy" @plan="investigationPlan = $event" />
      </div>

      <!-- ASK_USER：等待问题时显示在操作栏上方 -->
      <section v-if="pendingQuestion" class="question">
        <h2><UiIcon name="warning" /> 需要补充信息</h2>
        <p v-if="messages.at(-1)?.content !== pendingQuestion.question">{{ pendingQuestion.question }}</p>
        <p v-if="pendingQuestion.requestedFields.length" class="hint">需要：{{ pendingQuestion.requestedFields.join('、') }}</p>
        <textarea v-model="replyDraft" rows="3" aria-label="补充信息" placeholder="按问题补充信息"
                  @keydown.enter="submitOnEnter($event, answerQuestion)"></textarea>
        <button class="primary" :disabled="busy || !replyDraft.trim()" @click="answerQuestion">提交补充信息</button>
      </section>

      <!-- 底部操作栏：深入调查 / 补充现场观察 / 追加调查要求 -->
      <footer class="pro-action-wrap">
        <form class="pro-action-bar composer" @submit.prevent="submit">
          <button type="button" class="primary" :disabled="busy || !investigationPlan?.enabled"
                  :title="investigateTitle" @click="investigateCurrent">
            <UiIcon name="target" /> 深入调查
          </button>
          <button type="button" class="secondary" title="在输入框补充现场观察，将作为用户报告开启新一轮调查"
                  @click="focusFieldNote">
            <UiIcon name="chat" /> 补充现场观察
          </button>
          <label class="upload-label" :class="{ disabled: uploading }" title="添加 Markdown 资料">
            <input class="visually-hidden" type="file" aria-label="添加 Markdown 资料"
                   accept=".md,text/markdown" :disabled="uploading" @change="upload" />
            <UiIcon name="attach" />
          </label>
          <div class="pro-action-input">
            <textarea ref="draftInput" v-model="draft" rows="2"
                      :disabled="busy || pendingQuestion !== null || activityActive"
                      aria-label="向 Agent 追加调查要求" placeholder="向 Agent 追加调查要求…（如：检查相关参数配置、询问同类案例等）"
                      @keydown.enter="submitOnEnter($event, submit)"></textarea>
            <button type="submit" class="pro-send" :disabled="!canSend || pendingQuestion !== null" aria-label="发送">
              <UiIcon name="send" />
            </button>
          </div>
          <label class="pro-asset-chip" title="关联设备（可选）">
            <UiIcon name="layers" />
            <select id="asset" v-model="selectedAssetId" :disabled="busy" aria-label="关联设备">
              <option value="">不指定设备</option>
              <option v-for="asset in assets" :key="asset" :value="asset">{{ asset }}</option>
            </select>
          </label>
        </form>
        <p v-if="activityActive" class="pro-action-hint" role="status">
          本轮正在执行，输入已保留为草稿，结束后再发送；等待问题请在上方问题区补答。
          <template v-if="resumingRunId === run?.runId">正在恢复调查…</template>
        </p>
        <p v-else-if="pendingQuestion" class="pro-action-hint">请先回答上面的问题，再继续新的描述。</p>
        <p v-if="uploading" class="pro-action-hint" role="status">正在上传并导入，请稍候。</p>
        <p v-if="uploadedFile?.parseStatus === 'PARSED'" class="pro-action-hint" role="status">
          <span class="attachment-chip"><UiIcon name="file" /> {{ uploadedFile.originalName }}（将随下一条问题作为检索范围线索）</span>
        </p>
        <p v-if="uploadedFile && uploadedFile.parseStatus !== 'PARSED'" class="pro-action-hint" role="status">
          {{ uploadedFile.originalName }}：{{ uploadedFile.parseStatus }}
          <span v-if="uploadedFile.failureCode">（{{ uploadedFile.failureCode }}）</span>
        </p>
        <p class="pro-disclaimer">回答可能包含 SIMULATED 演示数据。知识引用仅反映资料记载，设备现状需现场核验。</p>
      </footer>
    </section>

    <!-- 调查记录抽屉：会话 / 轮次 / 消息 / 时间线 / 设备与依据 -->
    <aside class="pro-drawer" role="complementary" aria-label="调查记录" :class="{ open: recordDrawerOpen }">
      <div class="pro-drawer-head">
        <h2><UiIcon name="menu" /> 调查记录</h2>
        <button class="secondary" :disabled="busy" aria-label="刷新记录" @click="refresh(); refreshHistory()">
          <UiIcon name="refresh" />
        </button>
        <button class="secondary" :disabled="busy" @click="startNewConversation"><UiIcon name="plus" /> 新调查</button>
        <button class="icon-button" aria-label="关闭调查记录" @click="recordDrawerOpen = false">
          <UiIcon name="close" />
        </button>
      </div>
      <div class="pro-drawer-body">
        <h3>历史会话</h3>
        <p v-if="!conversations.length" class="hint">还没有历史会话。</p>
        <div class="history-list">
          <button v-for="item in conversations" :key="item.conversationId" class="conversation-link"
                  :data-conversation-id="item.conversationId"
                  :class="{ active: item.conversationId === conversation?.conversationId }"
                  :aria-current="item.conversationId === conversation?.conversationId ? 'true' : undefined"
                  :disabled="busy" @click="openConversation(item.conversationId)">
            <UiIcon name="chat" />
            <span class="history-text">
              <strong>{{ historyTitles[item.conversationId] || (item.version === 0 ? '新调查' : '调查会话') }}</strong>
              <small>{{ dateTime.format(new Date(item.updatedAt)) }}</small>
            </span>
            <span v-if="item.conversationId === conversation?.conversationId && run?.status === 'COMPLETED'" class="history-check">
              <UiIcon name="check" />
            </span>
          </button>
        </div>

        <template v-if="run">
          <h3>调查轮次</h3>
          <p v-if="!runs.length" class="hint">本会话还没有调查轮次。</p>
          <nav v-else class="run-picker" aria-label="调查轮次">
            <button v-for="(item, index) in runs" :key="item.runId" type="button" class="run-chip"
                    :class="{ active: item.runId === run?.runId }"
                    :aria-pressed="item.runId === run?.runId"
                    @click="pinnedRunId = item.runId">
              第 {{ index + 1 }} 轮 · {{ statusLabels[item.status] ?? item.status }}
            </button>
          </nav>
          <p v-if="run" class="hint">
            目标消息：{{ goalMessage?.content ?? '（不可用）' }}
            <template v-if="goalMessage?.selectedAssetId"> · 设备 {{ goalMessage.selectedAssetId }}</template>
            · {{ dateTime.format(new Date(run.requestedAt)) }}
            <template v-if="activeDuration"> · 耗时 {{ activeDuration }}</template>
          </p>

          <h3>会话消息</h3>
          <div v-if="!messages.length" class="hint">暂无消息。</div>
          <article v-for="message in messages" :key="message.messageId" class="message">
            <strong>{{ message.role === 'USER' ? '我' : 'DeviceTruth Agent' }}</strong>
            <time :datetime="message.requestedAt">{{ shortTime.format(new Date(message.requestedAt)) }}</time>
            <p v-if="message.role === 'USER'" class="bubble">{{ message.content }}</p>
            <p v-else>{{ message.content }}</p>
          </article>

          <h3>运行时间线</h3>
          <RunTimeline v-if="timelineEvents.length" :events="timelineEvents" :live="timelineLive"
                       :status="statusLabel" :duration="activeDuration" />
          <p v-else class="hint">暂无运行事件。</p>

          <h3>设备快照</h3>
          <EvidenceComparison v-if="selectedAssetId" :asset-id="selectedAssetId" :as-of="run?.asOf ?? null" />
          <p v-else class="hint">未关联设备；可在底部操作栏选择。</p>

          <template v-if="references.length">
            <h3>调查依据（{{ sources.length }} 项来源）</h3>
            <div v-for="source in sources" :key="source.referenceId" class="hint">
              《{{ source.title }}》 · {{ source.sourceType }} · 版本 {{ source.sourceVersion }} · {{ source.dataNature }}
            </div>
          </template>
        </template>
      </div>
    </aside>
  </main>
</template>

<style src="./pro.css"></style>
<style src="./workbench.css"></style>

<style scoped>
/* 抽屉默认隐藏，open 时滑入 */
.pro-drawer { transform: translateX(102%); transition: transform 0.22s ease; pointer-events: none; }
.pro-drawer.open { transform: translateX(0); pointer-events: auto; }
.icon-button { border: 1px solid var(--line); background: var(--panel-2); color: var(--text-2);
  border-radius: 8px; padding: 6px 9px; cursor: pointer; }
.icon-button:hover { color: var(--accent-2); border-color: var(--primary); }
</style>
