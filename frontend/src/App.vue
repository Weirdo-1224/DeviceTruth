<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
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

async function submit(): Promise<void> {
  if (!canSend.value || !conversation.value) return
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
  if (!conversation.value || !question || !run.value || replyDraft.value.trim().length === 0) return
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
  <main class="page">
    <header>
      <h1>DeviceTruth 设备检修助手</h1>
      <p class="hint">
        可以直接描述现象，不需要故障码；没有资产编号也可以先做资料问答。所有回答标注为 SIMULATED 演示数据，
        知识引用只说明资料记载，不代表本台设备当前配置。刷新或重开浏览器后会从历史列表找回本会话。
      </p>
    </header>

    <div class="layout">
      <aside class="history card">
        <div class="history-head">
          <h2>历史会话</h2>
          <button type="button" class="link" :disabled="busy" @click="startNewConversation">新会话</button>
        </div>
        <p v-if="conversations.length === 0" class="hint">还没有历史会话。</p>
        <ul>
          <li v-for="item in conversations" :key="item.conversationId">
            <button
              type="button"
              class="link conversation-link"
              :class="{ active: item.conversationId === conversation?.conversationId }"
              :disabled="busy"
              @click="openConversation(item.conversationId)"
            >
              {{ new Date(item.updatedAt).toLocaleString() }} 的会话
            </button>
          </li>
        </ul>
      </aside>

      <section class="content">
        <p v-if="errorText" class="error" role="alert">{{ errorText }}</p>

        <section class="status">
          <span>运行状态：{{ statusLabel }}</span>
          <span v-if="selectedSkill" class="skill-badge">
            领域包：{{ selectedSkill.name }} v{{ selectedSkill.version }}
          </span>
          <span v-if="resumingRunId && run?.runId === resumingRunId" class="hint">正在从服务器恢复进行中的调查…</span>
          <button
            v-if="run && (run.status === 'QUEUED' || run.status === 'RUNNING' || run.status === 'WAITING_FOR_USER')"
            type="button"
            class="link"
            :disabled="cancelling"
            @click="cancelCurrentRun"
          >
            {{ cancelling ? '正在取消…' : '取消本次调查' }}
          </button>
          <button v-if="!busy && conversation" type="button" class="link" @click="refresh">刷新</button>
        </section>

        <RunTimeline v-if="timelineEvents.length > 0" :events="timelineEvents" :live="timelineLive" />

        <section class="card picker">
          <label>
            关联设备（可选）：
            <select v-model="selectedAssetId" :disabled="busy">
              <option value="">不指定设备</option>
              <option v-for="asset in assets" :key="asset" :value="asset">{{ asset }}</option>
            </select>
          </label>
          <p class="hint">选择设备后，新问题会带上资产编号；不选择也可以先做资料问答。</p>
        </section>

        <EvidenceComparison v-if="selectedAssetId" :asset-id="selectedAssetId" :as-of="run?.asOf ?? null" />

        <section class="card upload">
          <h2>上传 Markdown 资料</h2>
          <input type="file" accept=".md,text/markdown" :disabled="uploading" @change="upload" />
          <p v-if="uploading" class="hint">正在上传并导入，请稍候。</p>
          <p v-if="uploadedFile" class="hint">
            {{ uploadedFile.originalName }}：{{ uploadedFile.parseStatus }}
            <span v-if="uploadedFile.parseStatus === 'PARSED'">，将随下一条问题作为检索范围线索。</span>
            <span v-else-if="uploadedFile.failureCode">（{{ uploadedFile.failureCode }}）</span>
          </p>
        </section>

        <section class="messages">
          <p v-if="messages.length === 0" class="empty">还没有消息，先描述一下设备现象。</p>
          <article v-for="message in messages" :key="message.messageId" :class="['bubble', message.role.toLowerCase()]">
            <span class="role">{{ message.role === 'USER' ? '我' : '助手' }}</span>
            <p>{{ message.content }}</p>
          </article>
        </section>

        <section v-if="pendingQuestion" class="card question">
          <h2>需要补充信息</h2>
          <p>{{ pendingQuestion.question }}</p>
          <p v-if="pendingQuestion.requestedFields.length > 0" class="hint">
            需要：{{ pendingQuestion.requestedFields.join('、') }}
          </p>
          <textarea v-model="replyDraft" rows="3" placeholder="按问题补充，例如：直流快充，SOC 约 40%"></textarea>
          <button type="button" :disabled="busy || replyDraft.trim().length === 0" @click="answerQuestion">
            提交补充信息
          </button>
        </section>

        <DiagnosisReport
          :status="run?.status ?? 'COMPLETED'"
          :result="run?.result ?? null"
          :failure-code="run?.failureCode ?? null"
          :failure-text="failureText"
          :references="run?.result?.references ?? []"
          :active-reference-id="openReferenceId"
          @toggle-reference="openReferenceId = openReferenceId === $event ? null : $event"
        />

        <section class="composer">
          <textarea
            v-model="draft"
            rows="3"
            :disabled="busy || pendingQuestion !== null"
            placeholder="例如：新能源汽车充电中断，没有故障码，应该先查什么资料？"
          ></textarea>
          <button type="button" :disabled="!canSend || pendingQuestion !== null" @click="submit">发送</button>
          <p v-if="pendingQuestion" class="hint">请先回答上面的问题，再继续新的描述。</p>
        </section>
      </section>
    </div>
  </main>
</template>

<style scoped>
.page {
  max-width: 1024px;
  margin: 0 auto;
  padding: 24px 16px 64px;
  font-family: system-ui, -apple-system, 'Segoe UI', 'Microsoft YaHei', sans-serif;
  color: #1f2933;
  line-height: 1.6;
}
header h1 {
  font-size: 22px;
  margin: 0 0 8px;
}
.hint {
  color: #616e7c;
  font-size: 13px;
  margin: 4px 0;
}
.error {
  border: 1px solid #f0b7b7;
  background: #fff5f5;
  color: #97266d;
  padding: 8px 12px;
  border-radius: 6px;
}
.status {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 14px;
  color: #334e68;
  margin: 16px 0;
}
.layout {
  display: flex;
  gap: 16px;
  align-items: flex-start;
}
.history {
  flex: 0 0 220px;
  position: sticky;
  top: 16px;
}
.history-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.history ul {
  list-style: none;
  margin: 8px 0 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
  max-height: 60vh;
  overflow-y: auto;
}
.conversation-link {
  font-size: 13px;
  color: #334e68;
}
.conversation-link.active {
  color: #2563eb;
  font-weight: 600;
}
.content {
  flex: 1;
  min-width: 0;
}
.picker select {
  margin-left: 8px;
  padding: 4px 8px;
  border: 1px solid #cbd2d9;
  border-radius: 6px;
  font: inherit;
}
.link {
  background: none;
  border: none;
  color: #2563eb;
  cursor: pointer;
  font-size: 13px;
  padding: 0;
}
.messages {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-bottom: 16px;
}
.bubble {
  border-radius: 8px;
  padding: 10px 12px;
  background: #f5f7fa;
}
.bubble.user {
  background: #e8f0fe;
}
.bubble .role {
  font-size: 12px;
  color: #616e7c;
}
.bubble p {
  margin: 4px 0 0;
  white-space: pre-wrap;
}
.empty {
  color: #9aa5b1;
  font-size: 14px;
}
.card {
  border: 1px solid #d9e2ec;
  border-radius: 8px;
  padding: 14px;
  margin-bottom: 16px;
}
.card h2 {
  font-size: 16px;
  margin: 0 0 8px;
}
.card h3 {
  font-size: 14px;
  margin: 12px 0 4px;
}
.question {
  border-color: #b7d3f0;
  background: #f6faff;
}
.failure {
  border-color: #f0b7b7;
  background: #fff7f7;
}
.summary {
  font-size: 15px;
  margin: 0 0 8px;
}
.refs {
  color: #616e7c;
  font-size: 12px;
}
.references .excerpt {
  color: #616e7c;
  font-size: 13px;
  margin: 4px 0 8px;
}
textarea {
  width: 100%;
  box-sizing: border-box;
  border: 1px solid #cbd2d9;
  border-radius: 6px;
  padding: 8px;
  font: inherit;
  resize: vertical;
}
button {
  margin-top: 8px;
  border: none;
  border-radius: 6px;
  background: #2563eb;
  color: #fff;
  padding: 8px 16px;
  font-size: 14px;
  cursor: pointer;
}
button:disabled {
  background: #b6c2cf;
  cursor: not-allowed;
}
.skill-badge {
  font-size: 12px;
  color: #334e68;
  background: #eef2f5;
  border-radius: 10px;
  padding: 1px 8px;
}
.composer {
  margin-top: 8px;
}
</style>
