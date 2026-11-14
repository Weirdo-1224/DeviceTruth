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
  readRunSkill,
  replyToQuestion,
  sendMessage,
  uploadMarkdown,
  waitForFile,
  waitForRun,
  type ConversationView,
  type FileView,
  type MessageView,
  type RunEvent,
  type RunView,
  type SkillSelection
} from './api'
import RunTimeline from './components/RunTimeline.vue'
import DiagnosisReport from './components/DiagnosisReport.vue'
import EvidenceComparison from './components/EvidenceComparison.vue'
import UiIcon from './components/UiIcon.vue'
import { createRunTimelineController } from './timeline'

const STORAGE_KEY = 'devicetruth.conversation-id'

const conversation = ref<ConversationView | null>(null)
const conversations = ref<ConversationView[]>([])
const assets = ref<string[]>([])
const selectedAssetId = ref<string>('')
const messages = ref<MessageView[]>([])
const run = ref<RunView | null>(null)
const draft = ref('')
const replyDraft = ref('')
const busy = ref(false)
const resumingRunId = ref<string | null>(null)
const uploading = ref(false)
const errorText = ref('')
const uploadedFile = ref<FileView | null>(null)
const openReferenceId = ref<string | null>(null)
const selectedSkill = ref<SkillSelection | null>(null)
const cancelling = ref(false)
const navigationOpen = ref(false)
const evidenceOpen = ref(false)
const evidenceTab = ref<'evidence' | 'assets' | 'events'>('evidence')
const historyTitles = ref<Record<string, string>>({})
const isTerminal = computed(() => ['COMPLETED', 'FAILED', 'CANCELLED'].includes(run.value?.status ?? ''))
const taskTitle = computed(() => messages.value.find(message => message.role === 'USER')?.content || '新调查')
const reportMessageId = computed(() => {
  const last = messages.value.at(-1)
  return last?.role === 'ASSISTANT' && last.content === run.value?.result?.summary ? last.messageId : null
})
const foldedMessages = computed(() => reportMessageId.value ? messages.value.slice(1, -1) : [])
const shownMessages = computed(() => foldedMessages.value.length
  ? [messages.value[0], messages.value[messages.value.length - 1]]
  : messages.value)
const goalMessage = computed(() => messages.value.find(message => message.messageId === run.value?.goalMessageId))
const references = computed(() => run.value?.result?.references ?? [])
const sources = computed(() => [...new Map(references.value.map(reference => [
  JSON.stringify([reference.sourceType, reference.sourceId, reference.sourceVersion]), reference
])).values()])
const visibleEvents = computed(() => timelineEvents.value.slice(-3))
const activeDuration = computed(() => {
  const ms = run.value?.activeDurationMs
  if (ms == null) return ''
  if (ms < 1000) return ms + ' 毫秒'
  const seconds = Math.round(ms / 1000)
  return seconds < 60 ? seconds + ' 秒' : Math.floor(seconds / 60) + ' 分 ' + seconds % 60 + ' 秒'
})
const safetyNotice = computed(() => {
  const disposition = run.value?.result?.safetyDisposition
  if (disposition === 'REQUIRES_QUALIFIED_PERSONNEL') return '涉及高风险操作，必须由具备资质的专业人员按厂家规程处理。'
  if (disposition === 'PROHIBITED') return '该请求涉及被禁止的设备操作，系统不提供执行指引。'
  return ''
})
const shortTime = new Intl.DateTimeFormat('zh-CN', { hour: '2-digit', minute: '2-digit', hour12: false })
const dateTime = new Intl.DateTimeFormat('zh-CN', { month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit', hour12: false })

watch([conversation, messages], ([current, currentMessages]) => {
  const title = currentMessages.find(message => message.role === 'USER')?.content
  if (current && title) historyTitles.value[current.conversationId] = title
})
watch(() => conversation.value?.conversationId, () => {
  evidenceOpen.value = false
  navigationOpen.value = false
  evidenceTab.value = 'evidence'
  openReferenceId.value = null
})
watch(() => run.value?.runId, () => { openReferenceId.value = null })

function openEvidence(tab: typeof evidenceTab.value): void {
  evidenceTab.value = tab
  evidenceOpen.value = true
}

function showReference(id: string): void {
  openReferenceId.value = id
  openEvidence('evidence')
  void nextTick(() => document.getElementById('reference-' + id)?.scrollIntoView({ block: 'nearest' }))
}


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

/** 取消运行：QUEUED/WAITING 立即生效，RUNNING 协作式收尾；终态再取消会得到 409。 */
async function cancelCurrentRun(): Promise<void> {
  const current = run.value
  if (!current || cancelling.value) return
  cancelling.value = true
  errorText.value = ''
  try {
    const updated = await cancelRunRequest(current.runId)
    if (run.value?.runId === current.runId) run.value = updated
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
const pendingQuestion = computed(() => run.value?.pendingQuestion ?? null)
const failureText = computed(() =>
  run.value && run.value.status === 'FAILED' ? describeFailure(run.value.failureCode) : ''
)
const canSend = computed(() => !busy.value && draft.value.trim().length > 0)

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
  const latest = detail.runs.length > 0 ? detail.runs[detail.runs.length - 1] : null
  run.value = latest
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
    run.value = detail.runs.length ? detail.runs[detail.runs.length - 1] : null
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
    run.value = null
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
  const isCurrent = () => conversation.value?.conversationId === conversationId && run.value?.runId === runId
  const finished = await waitForRun(runId, {
    onUpdate: (view) => {
      if (isCurrent()) run.value = view
    }
  })
  if (!isCurrent()) return
  run.value = finished
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
  if (!canSend.value || !conversation.value || pendingQuestion.value) return
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
    run.value = accepted
    await refresh()
    await track(accepted.runId)
  } catch (error) {
    errorText.value = describeError(error)
  } finally {
    busy.value = false
  }
}

/** 补答：用当前 run 的 pendingQuestionId/questionVersion 关联，沿同一个 run 继续。 */
async function answerQuestion(): Promise<void> {
  const question = pendingQuestion.value
  if (busy.value || !conversation.value || !question || !run.value || replyDraft.value.trim().length === 0) return
  busy.value = true
  errorText.value = ''
  try {
    const accepted = await replyToQuestion(
      conversation.value.conversationId,
      {
        runId: run.value.runId,
        pendingQuestionId: question.pendingQuestionId,
        questionVersion: question.questionVersion
      },
      replyDraft.value
    )
    replyDraft.value = ''
    run.value = accepted
    await refresh()
    await track(accepted.runId)
  } catch (error) {
    errorText.value = describeError(error)
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
  <main class="workbench" @keydown.esc="navigationOpen = false; evidenceOpen = false">
    <div v-if="navigationOpen || evidenceOpen" class="scrim"
         @click="navigationOpen = false; evidenceOpen = false"></div>

    <aside class="sidebar" :class="{ open: navigationOpen }" aria-label="会话导航">
      <div class="brand">
        <span class="brand-mark"><UiIcon name="spark" /></span>
        <div><strong>DeviceTruth</strong><small>设备真相 · AI 检修助手</small></div>
        <button class="icon-button mobile-close" aria-label="关闭导航" @click="navigationOpen = false">
          <UiIcon name="close" />
        </button>
      </div>
      <button class="primary new-conversation" :disabled="busy" @click="startNewConversation">
        <UiIcon name="plus" /> 新调查
      </button>
      <nav class="workspace-nav" aria-label="工作区">
        <span class="nav-current" aria-current="page"><UiIcon name="home" /> 调查工作台</span>
      </nav>

      <h2 class="side-label">历史会话</h2>
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
            <small>{{ dateTime.format(new Date(item.updatedAt)) }}<template v-if="item.conversationId === conversation?.conversationId && run"> · {{ statusLabel }}</template></small>
          </span>
          <span v-if="item.conversationId === conversation?.conversationId && run?.status === 'COMPLETED'" class="history-check"><UiIcon name="check" /></span>
        </button>
      </div>
      <div class="sidebar-foot">
        <span class="avatar user">DT</span>
        <div>DeviceTruth<small>设备检修工作台</small></div>
      </div>
    </aside>

    <header class="topbar">
      <button class="icon-button menu-button" aria-label="打开会话导航" @click="navigationOpen = true"><UiIcon name="menu" /></button>
      <div class="top-title">
        <div class="eyebrow">工作区 / 调查工作台</div>
        <div class="title-row">
          <h1 :title="taskTitle">{{ taskTitle }}</h1>
          <span v-if="run" class="status-badge" :class="run.status">
            <UiIcon v-if="run.status === 'COMPLETED'" name="check" /> {{ statusLabel }}
          </span>
        </div>
        <div v-if="run" class="run-meta">
          <span v-if="goalMessage?.selectedAssetId"><UiIcon name="layers" /> {{ goalMessage.selectedAssetId }}</span>
          <span><UiIcon name="clock" /> {{ dateTime.format(new Date(run.requestedAt)) }}</span>
          <span v-if="activeDuration">耗时 {{ activeDuration }}</span>
          <span v-if="selectedSkill">领域包 {{ selectedSkill.skillId }} v{{ selectedSkill.version }}</span>
          <span v-if="resumingRunId === run.runId">正在恢复调查…</span>
        </div>
      </div>
      <div class="top-actions">
        <button v-if="run && !isTerminal" class="secondary" :disabled="cancelling" @click="cancelCurrentRun">
          {{ cancelling ? '正在取消…' : '取消调查' }}
        </button>
        <button v-if="!busy && conversation" class="secondary" aria-label="刷新" title="刷新" @click="refresh">
          <UiIcon name="refresh" /><span class="action-label">刷新</span>
        </button>
        <button class="secondary evidence-toggle" :aria-expanded="evidenceOpen"
                @click="evidenceOpen = !evidenceOpen"><UiIcon name="layers" /> 依据</button>
      </div>
    </header>

    <section class="main-panel" aria-label="Agent 工作台">
      <div class="main-scroll">
        <p v-if="errorText" class="error" role="alert">{{ errorText }}</p>
        <section class="messages" aria-label="会话消息">
          <div v-if="!messages.length" class="empty">
            <div class="empty-icon"><UiIcon name="spark" /></div>
            <h2>开始一次新调查</h2>
            <p>描述设备现象，助手会查找资料并整理可核验的线索。</p>
          </div>
          <template v-for="(message, index) in shownMessages" :key="message.messageId">
            <details v-if="index === 1 && foldedMessages.length" class="earlier-messages">
              <summary>查看前序对话（{{ foldedMessages.length }} 条）</summary>
              <article v-for="earlier in foldedMessages" :key="earlier.messageId" class="earlier-message">
                <strong>{{ earlier.role === 'USER' ? '我' : 'DeviceTruth Agent' }}</strong>
                <time :datetime="earlier.requestedAt">{{ shortTime.format(new Date(earlier.requestedAt)) }}</time>
                <p>{{ earlier.content }}</p>
              </article>
            </details>
            <article class="message" :class="{ 'report-message': message.messageId === reportMessageId }">
              <span class="avatar" :class="{ user: message.role === 'USER' }">
                <template v-if="message.role === 'USER'">我</template><UiIcon v-else name="spark" />
              </span>
              <div class="message-body">
                <div v-if="message.role === 'ASSISTANT'" class="agent-name">DeviceTruth Agent <span>AI</span></div>
                <p v-if="message.role === 'USER'" class="bubble">{{ message.content }}</p>
                <p v-else-if="message.messageId !== reportMessageId">{{ message.content }}</p>
              </div>
              <time :datetime="message.requestedAt">{{ shortTime.format(new Date(message.requestedAt)) }}</time>
            </article>
          </template>
        </section>

        <div v-if="run && (visibleEvents.length || !isTerminal)" class="investigation">
          <RunTimeline :key="run.runId" :events="visibleEvents" :live="timelineLive"
                       :status="statusLabel" :duration="activeDuration">
            <button v-if="timelineEvents.length > visibleEvents.length" class="text-button full-events"
                    @click="openEvidence('events')">完整记录 <UiIcon name="chevron" /></button>
          </RunTimeline>
        </div>

        <section v-if="pendingQuestion" class="question">
          <h2>需要补充信息</h2>
          <p v-if="messages.at(-1)?.content !== pendingQuestion.question">{{ pendingQuestion.question }}</p>
          <p v-if="pendingQuestion.requestedFields.length" class="hint">需要：{{ pendingQuestion.requestedFields.join('、') }}</p>
          <textarea v-model="replyDraft" rows="3" aria-label="补充信息" placeholder="按问题补充信息"
                    @keydown.enter="submitOnEnter($event, answerQuestion)"></textarea>
          <button class="primary" :disabled="busy || !replyDraft.trim()" @click="answerQuestion">提交补充信息</button>
        </section>
        <DiagnosisReport v-if="run && isTerminal" :status="run.status" :result="run.result"
                         :failure-code="run.failureCode" :failure-text="failureText"
                         :references="references" @toggle-reference="showReference" />
      </div>

      <form class="composer-wrap" @submit.prevent="submit">
        <div class="composer">
          <textarea v-model="draft" rows="2" :disabled="busy || pendingQuestion !== null"
                    aria-label="描述设备问题" placeholder="描述设备现象，或继续追问…"
                    @keydown.enter="submitOnEnter($event, submit)"></textarea>
          <div class="composer-actions">
            <div class="composer-tools">
              <label class="upload-label" :class="{ disabled: uploading }" title="添加 Markdown 资料">
                <input class="visually-hidden" type="file" aria-label="添加 Markdown 资料"
                       accept=".md,text/markdown" :disabled="uploading" @change="upload" />
                <UiIcon name="attach" />
              </label>
              <button type="button" class="composer-tool" @click="openEvidence('assets')">
                <UiIcon name="layers" /> {{ selectedAssetId || '关联设备' }} <UiIcon name="chevron" />
              </button>
              <span v-if="uploadedFile?.parseStatus === 'PARSED'" class="attachment-chip" role="status"
                    :title="uploadedFile.originalName + '，将随下一条问题作为检索范围线索。'">
                <UiIcon name="file" /> <span>{{ uploadedFile.originalName }}</span>
              </span>
            </div>
            <button type="submit" class="primary" :disabled="!canSend || pendingQuestion !== null"><UiIcon name="send" /> 发送</button>
          </div>
          <p v-if="uploading" class="hint" role="status">正在上传并导入，请稍候。</p>
          <p v-if="uploadedFile && uploadedFile.parseStatus !== 'PARSED'" class="hint" role="status">
            {{ uploadedFile.originalName }}：{{ uploadedFile.parseStatus }}
            <span v-if="uploadedFile.failureCode">（{{ uploadedFile.failureCode }}）</span>
          </p>
          <p v-if="pendingQuestion" class="hint">请先回答上面的问题，再继续新的描述。</p>
        </div>
        <p class="disclaimer">回答可能包含 SIMULATED 演示数据。知识引用仅反映资料记载，设备现状需现场核验。</p>
      </form>
    </section>

    <aside class="evidence-panel" :class="{ open: evidenceOpen }" aria-label="调查依据">
      <nav class="evidence-tabs" aria-label="依据视图">
        <button :aria-current="evidenceTab === 'evidence' ? 'true' : undefined" @click="evidenceTab = 'evidence'">调查依据</button>
        <button :aria-current="evidenceTab === 'assets' ? 'true' : undefined" @click="evidenceTab = 'assets'">相关资产</button>
        <button :aria-current="evidenceTab === 'events' ? 'true' : undefined" @click="evidenceTab = 'events'">运行记录</button>
        <button class="icon-button close-button" aria-label="关闭依据" @click="evidenceOpen = false"><UiIcon name="close" /></button>
      </nav>
      <div class="evidence-scroll">
        <template v-if="evidenceTab === 'evidence'">
          <section class="evidence-card">
            <h2><UiIcon name="link" /> 本次涉及的关键资料 <small v-if="sources.length">{{ sources.length }} 项</small></h2>
            <p v-if="!sources.length" class="hint">本次调查暂无已登记的引用资料。</p>
            <button v-for="source in sources" :key="source.referenceId" class="source-entry" @click="showReference(source.referenceId)">
              <span class="source-icon"><UiIcon name="file" /></span>
              <span><strong>{{ source.title }}</strong><small>版本 {{ source.sourceVersion }} · {{ source.dataNature }}</small></span>
              <UiIcon name="chevron" />
            </button>
          </section>
          <section v-if="safetyNotice" class="evidence-card caution">
            <h2><UiIcon name="warning" /> 注意事项</h2><p>{{ safetyNotice }}</p>
          </section>
          <section v-if="references.length" class="evidence-card">
            <h2><UiIcon name="file" /> 引用摘录（{{ references.length }}）</h2>
            <div v-for="reference in references" :id="'reference-' + reference.referenceId" :key="reference.referenceId"
                 class="reference" :class="{ expanded: openReferenceId === reference.referenceId }">
              <button :aria-expanded="openReferenceId === reference.referenceId"
                      @click="openReferenceId = openReferenceId === reference.referenceId ? null : reference.referenceId">
                <span class="quote-mark" aria-hidden="true">“</span>
                <span class="quote-text">{{ reference.excerpt || reference.title }}</span>
                <UiIcon name="chevron" />
              </button>
              <p class="quote-source">来自 <strong>{{ reference.title }}</strong><span v-if="reference.section"> · {{ reference.section }}</span></p>
              <p v-if="openReferenceId === reference.referenceId" class="hint quote-meta">
                {{ reference.sourceType }} · 版本 {{ reference.sourceVersion }} · {{ reference.dataNature }}
                <span v-if="reference.page"> · 第 {{ reference.page }} 页</span>
              </p>
            </div>
          </section>
        </template>

        <template v-else-if="evidenceTab === 'assets'">
          <section class="evidence-card asset-picker">
            <h2><UiIcon name="layers" /> <label for="asset">关联设备（可选）</label></h2>
            <select id="asset" v-model="selectedAssetId" :disabled="busy">
              <option value="">不指定设备</option>
              <option v-for="asset in assets" :key="asset" :value="asset">{{ asset }}</option>
            </select>
            <p v-if="!selectedAssetId" class="hint">选择设备后查看配置和维修记录。</p>
          </section>
          <EvidenceComparison v-if="selectedAssetId" :asset-id="selectedAssetId" :as-of="run?.asOf ?? null" />
        </template>

        <template v-else>
          <RunTimeline v-if="timelineEvents.length" :events="timelineEvents" :live="timelineLive"
                       :status="statusLabel" :duration="activeDuration" />
          <section v-else class="evidence-card"><h2><UiIcon name="activity" /> 运行记录</h2><p class="hint">暂无运行事件。</p></section>
        </template>
      </div>
    </aside>
  </main>
</template>

<style scoped src="./workbench.css"></style>
