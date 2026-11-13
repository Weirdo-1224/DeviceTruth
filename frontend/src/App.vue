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
const isTerminal = computed(() => ['COMPLETED', 'FAILED', 'CANCELLED'].includes(run.value?.status ?? ''))
const taskTitle = computed(() => messages.value.find(message => message.role === 'USER')?.content.slice(0, 36) || '新调查')
watch(() => conversation.value?.conversationId, () => { evidenceOpen.value = false; navigationOpen.value = false; openReferenceId.value = null })
function showReference(id: string): void {
  openReferenceId.value = id
  evidenceOpen.value = true
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
  <main class="workbench" :class="{ 'evidence-visible': evidenceOpen }" @keydown.esc="navigationOpen = false; evidenceOpen = false">
    <div v-if="navigationOpen || evidenceOpen" class="scrim" @click="navigationOpen = false; evidenceOpen = false"></div>
    <aside class="sidebar" :class="{ open: navigationOpen }" aria-label="会话导航">
      <div class="brand"><span class="brand-mark">◈</span> DeviceTruth <button class="mobile-close" aria-label="关闭导航" @click="navigationOpen = false">×</button></div>
      <button class="new-conversation" :disabled="busy" @click="startNewConversation">＋　新调查</button>
      <p class="side-label">工作区</p><div class="nav-current">◫　调查工作台</div><p class="side-label">历史会话</p>
      <p v-if="conversations.length === 0" class="hint">还没有历史会话。</p>
      <div class="history-list"><button v-for="item in conversations" :key="item.conversationId" class="conversation-link" :class="{ active: item.conversationId === conversation?.conversationId }" :aria-current="item.conversationId === conversation?.conversationId ? 'true' : undefined" :disabled="busy" @click="openConversation(item.conversationId)">{{ new Date(item.updatedAt).toLocaleString() }} 的会话</button></div>
      <p class="sidebar-foot">设备检修助手</p>
    </aside>
    <section class="main-panel">
      <header class="topbar"><div class="top-title"><button class="menu-button" aria-label="打开会话导航" @click="navigationOpen = true">☰</button><div><span class="eyebrow">工作区 / 调查工作台</span><h1>{{ taskTitle }}</h1></div><span class="status-badge">{{ statusLabel }}</span></div><button class="secondary" :aria-expanded="evidenceOpen" @click="evidenceOpen = !evidenceOpen">☷ 依据</button></header>
      <div class="main-scroll"><div class="content">
        <p v-if="errorText" class="error" role="alert">{{ errorText }}</p>
        <div class="run-actions"><span v-if="selectedSkill" class="hint">领域包：{{ selectedSkill.name }} v{{ selectedSkill.version }}</span><span v-if="resumingRunId && run?.runId === resumingRunId" class="hint">正在恢复调查…</span><button v-if="run && ['QUEUED', 'RUNNING', 'WAITING_FOR_USER'].includes(run.status)" class="text-button" :disabled="cancelling" @click="cancelCurrentRun">{{ cancelling ? '正在取消…' : '取消调查' }}</button><button v-if="!busy && conversation" class="text-button" @click="refresh">刷新</button></div>
        <section class="messages"><div v-if="messages.length === 0" class="empty"><div class="empty-icon">✦</div><h2>开始一次新调查</h2><p>描述设备现象，助手会查找资料并整理可核验的线索。</p></div><article v-for="message in messages" :key="message.messageId" class="message"><span class="avatar" :class="{ user: message.role === 'USER' }">{{ message.role === 'USER' ? '我' : '✦' }}</span><div class="message-body"><strong>{{ message.role === 'USER' ? '我' : 'DeviceTruth Agent' }}</strong><p :class="{ bubble: message.role === 'USER' }">{{ message.content }}</p></div></article></section>
        <section v-if="pendingQuestion" class="card question"><h2>需要补充信息</h2><p>{{ pendingQuestion.question }}</p><p v-if="pendingQuestion.requestedFields.length" class="hint">需要：{{ pendingQuestion.requestedFields.join('、') }}</p><textarea v-model="replyDraft" rows="3" aria-label="补充信息" placeholder="按问题补充信息"></textarea><button class="primary" :disabled="busy || replyDraft.trim().length === 0" @click="answerQuestion">提交补充信息</button></section>
        <RunTimeline v-if="timelineEvents.length > 0 && !isTerminal" :events="timelineEvents" :live="timelineLive" />
        <DiagnosisReport v-if="run" :status="run?.status ?? 'COMPLETED'" :result="run?.result ?? null" :failure-code="run?.failureCode ?? null" :failure-text="failureText" :references="run?.result?.references ?? []" :active-reference-id="openReferenceId" @toggle-reference="showReference" />
        <details v-if="timelineEvents.length > 0 && isTerminal" class="timeline-details" :key="run?.runId"><summary>查看完整调查过程（{{ timelineEvents.length }} 条）</summary><RunTimeline :events="timelineEvents" :live="timelineLive" /></details>
      </div></div>
      <div class="composer-wrap"><div class="composer"><textarea v-model="draft" rows="2" :disabled="busy || pendingQuestion !== null" aria-label="描述设备问题" placeholder="描述设备现象，或继续追问…"></textarea><div class="composer-actions"><label class="upload-label">添加 Markdown 资料<input type="file" accept=".md,text/markdown" :disabled="uploading" @change="upload" /></label><button class="primary" :disabled="!canSend || pendingQuestion !== null" @click="submit">发送 ↑</button></div><p v-if="uploading" class="hint">正在上传并导入，请稍候。</p><p v-if="uploadedFile" class="hint">{{ uploadedFile.originalName }}：{{ uploadedFile.parseStatus }}<span v-if="uploadedFile.parseStatus === 'PARSED'">，将随下一条问题作为检索范围线索。</span><span v-else-if="uploadedFile.failureCode">（{{ uploadedFile.failureCode }}）</span></p><p v-if="pendingQuestion" class="hint">请先回答上面的问题，再继续新的描述。</p></div><p class="disclaimer">回答可能包含 SIMULATED 演示数据；知识引用只说明资料记载，不代表本台设备当前配置。</p></div>
    </section>
    <aside class="evidence-panel" :class="{ open: evidenceOpen }" aria-label="调查依据"><div class="evidence-header"><div><h2>调查依据</h2><p class="hint">设备记录与来源</p></div><button class="close-button" aria-label="关闭依据" @click="evidenceOpen = false">×</button></div><section class="asset-picker"><label for="asset">关联设备（可选）</label><select id="asset" v-model="selectedAssetId" :disabled="busy"><option value="">不指定设备</option><option v-for="asset in assets" :key="asset" :value="asset">{{ asset }}</option></select><p class="hint">不指定设备也可以先做资料问答。</p></section><EvidenceComparison v-if="selectedAssetId" :asset-id="selectedAssetId" :as-of="run?.asOf ?? null" /><p v-else class="hint">选择设备后查看配置和维修记录。</p><section v-if="run?.result?.references.length" class="evidence-references"><h3>引用来源</h3><div v-for="reference in run.result.references" :id="'reference-' + reference.referenceId" :key="reference.referenceId" class="reference"><button class="text-button" :aria-expanded="openReferenceId === reference.referenceId" @click="openReferenceId = openReferenceId === reference.referenceId ? null : reference.referenceId">《{{ reference.title }}》{{ reference.section ?? '' }}</button><p class="hint">{{ reference.sourceType }} · 版本 {{ reference.sourceVersion }} · {{ reference.dataNature }}</p><p v-if="openReferenceId === reference.referenceId">{{ reference.excerpt }}</p></div></section></aside>
  </main>
</template>
<style scoped>
.workbench{height:100dvh;display:grid;grid-template-columns:224px minmax(0,1fr);color:var(--ink)}.workbench.evidence-visible{grid-template-columns:224px minmax(0,1fr) 340px}.sidebar,.evidence-panel{background:#fff;overflow:auto}.sidebar{border-right:1px solid var(--line);padding:22px 14px;display:flex;flex-direction:column}.brand{font-size:16px;font-weight:750;display:flex;align-items:center;gap:9px;margin:0 8px 25px}.brand-mark,.avatar{display:grid;place-items:center;border-radius:9px;width:30px;height:30px;background:var(--primary);color:white}.new-conversation,.secondary{border:1px solid var(--line);border-radius:8px;background:white;padding:9px 12px;color:var(--ink);cursor:pointer}.new-conversation{text-align:left}.side-label,.eyebrow{color:var(--muted);font-size:12px}.side-label{margin:26px 10px 9px;font-weight:700}.nav-current,.conversation-link{padding:10px;border-radius:8px;font-size:13px}.nav-current{color:#556176;font-weight:600}.conversation-link.active{background:var(--soft);color:#414db6;font-weight:700}.history-list{overflow:auto}.conversation-link{display:block;width:100%;text-align:left;border:0;background:transparent;color:#556176;cursor:pointer}.conversation-link.active{background:var(--soft);color:#414db6}.sidebar-foot{margin-top:auto;color:var(--muted);font-size:12px}.main-panel{min-width:0;background:white;display:flex;flex-direction:column;overflow:hidden}.topbar{height:68px;flex:none;border-bottom:1px solid var(--line);display:flex;align-items:center;justify-content:space-between;gap:12px;padding:0 28px}.top-title{display:flex;align-items:center;gap:12px;min-width:0}.topbar h1{font-size:14px;margin:2px 0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;max-width:38vw}.status-badge{white-space:nowrap;background:#f0f1ff;color:#4753b6;border-radius:20px;padding:4px 10px;font-size:12px}.main-scroll{flex:1;overflow:auto;padding:28px 40px}.content,.composer{max-width:760px;margin:auto}.run-actions{display:flex;gap:12px;align-items:center;min-height:26px}.hint{color:var(--muted);font-size:13px;line-height:1.5}.text-button{border:0;background:none;color:#4857bc;cursor:pointer;padding:3px}.error{border:1px solid #efc2c2;background:#fff5f5;color:#9d3333;padding:10px;border-radius:8px}.empty{text-align:center;padding:80px 15px;color:var(--muted)}.empty h2{color:var(--ink);font-size:22px}.empty-icon{display:grid;place-items:center;width:48px;height:48px;background:var(--soft);color:var(--primary);border-radius:15px;margin:auto;font-size:22px}.message{display:flex;gap:12px;margin:20px 0}.avatar{flex:none;background:var(--soft);color:var(--primary);font-size:13px}.avatar.user{background:#edf0f4;color:#596579}.message-body{min-width:0;flex:1;font-size:14px}.message-body p{white-space:pre-wrap;overflow-wrap:anywhere;line-height:1.7}.bubble{background:#f5f6fa;padding:12px 15px;border-radius:3px 12px 12px 12px}.card,.timeline-details{border:1px solid var(--line);border-radius:10px;padding:16px;margin:16px 0}.card h2{font-size:15px}.question{background:#f8f9ff}.timeline-details summary{cursor:pointer;font-weight:650}.composer-wrap{border-top:1px solid var(--line);padding:12px 40px 18px}.composer{border:1px solid #dfe3eb;border-radius:11px;padding:12px}.composer textarea,.question textarea{width:100%;border:0;resize:vertical;min-height:45px}.question textarea{border:1px solid var(--line);border-radius:8px;padding:8px}.composer-actions{display:flex;align-items:center;justify-content:space-between}.primary{background:var(--primary);color:white;border:0;border-radius:7px;padding:8px 14px;cursor:pointer}.primary:disabled,button:disabled{opacity:.55;cursor:not-allowed}.conversation-link.active:disabled{opacity:1}.upload-label{color:var(--muted);font-size:13px;cursor:pointer}.upload-label input{display:block;max-width:220px;font-size:12px;margin-top:4px}.disclaimer{text-align:center;color:var(--muted);font-size:12px;margin:8px 0 0}.evidence-panel{display:none;border-left:1px solid var(--line);padding:22px 20px}.evidence-visible .evidence-panel{display:block}.evidence-header{display:flex;align-items:start;justify-content:space-between}.evidence-header h2{font-size:15px;margin:0}.close-button,.mobile-close,.menu-button{border:0;background:none;cursor:pointer;font-size:20px;color:var(--muted)}.mobile-close,.menu-button{display:none}.asset-picker,.evidence-references{border-top:1px solid var(--line);padding:18px 0}.asset-picker label,.evidence-references h3{display:block;font-size:13px;font-weight:700;margin-bottom:10px}.asset-picker select{max-width:100%;padding:7px;border:1px solid var(--line);border-radius:7px}.reference{padding:10px 0;border-bottom:1px solid var(--line);overflow-wrap:anywhere}.scrim{display:none}@media(max-width:1100px){.workbench.evidence-visible{grid-template-columns:224px minmax(0,1fr)}.evidence-visible .evidence-panel{position:fixed;z-index:4;right:0;top:0;bottom:0;width:min(360px,90vw);box-shadow:-10px 0 30px #17233d22}.scrim{display:block;position:fixed;inset:0;background:#19233866;z-index:3}}@media(max-width:680px){.workbench,.workbench.evidence-visible{display:flex}.main-panel{width:100%}.sidebar{display:none;position:fixed;z-index:4;inset:0 auto 0 0;width:260px;box-shadow:10px 0 30px #17233d22}.sidebar.open{display:flex}.menu-button,.mobile-close{display:block}.mobile-close{margin-left:auto}.topbar{padding:0 16px}.main-scroll{padding:20px 16px}.composer-wrap{padding:10px 16px}.topbar h1{max-width:35vw}}
</style>
