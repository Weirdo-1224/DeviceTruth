<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import UiIcon from './UiIcon.vue'
import AnswerReviewPanel from './AnswerReviewPanel.vue'
import ClaimList from './ClaimList.vue'
import ClaimDetail from './ClaimDetail.vue'
import type { ClaimView, DiagnosisViewModel, ReferenceView } from '../diagnosisViewModel'
import type { RunView } from '../api'

/**
 * 专业诊断工作台主体（单焦点工作区）。
 * 全部内容来自持久 Run 与已登记引用的确定性投影（diagnosisViewModel），不发起模型调用；
 * 判断选择只切换查看焦点（claimSelection，零请求）。
 * 结果摘要、判断与引用、本轮建议及边界分区展示；完整结果按需展开。
 * 证据详情统一经 EvidenceModal（由 App 挂载在状态分支之外）按需展开，三种终态共用。
 */
const props = defineProps<{
  run: RunView | null
  model: DiagnosisViewModel
  selectedClaim: ClaimView | null
  selectedIndex: number | null
  registeredReferences: ReferenceView[]
  /** 本轮登记来源读取状态：loading / ready / empty / unavailable / denied / notFound / error。 */
  referencesState: 'loading' | 'ready' | 'empty' | 'unavailable' | 'denied' | 'notFound' | 'error'
  referencesNote: string
  skillLabel: string | null
  failureText?: string
  embedded?: boolean
}>()

const emit = defineEmits<{
  select: [index: number]
  openReference: [referenceId: string]
  retryReferences: []
  history: []
  asset: []
}>()

const statusLabels: Record<string, string> = {
  QUEUED: '排队中',
  RUNNING: '执行中',
  WAITING_FOR_USER: '等待补充信息',
  COMPLETED: '已完成',
  FAILED: '执行失败',
  CANCELLED: '已取消'
}
const statusLabel = computed(() => props.run ? statusLabels[props.run.status] ?? props.run.status : '未开始')
const terminal = computed(() => ['COMPLETED', 'FAILED', 'CANCELLED'].includes(props.run?.status ?? ''))

/** 完整结果展开状态：只在用户主动展开时显示该段落，不改变任何持久数据。 */
const resultExpanded = ref(false)
watch(() => props.run?.runId, () => { resultExpanded.value = false })
const registeredIds = computed(() => new Set(props.registeredReferences.map((ref) => ref.referenceId)))
const directIds = computed(() => new Set(props.selectedClaim?.referenceIds))
const directReferences = computed(() => props.registeredReferences.filter(reference => directIds.value.has(reference.referenceId)))
const otherRegistered = computed(() => props.registeredReferences.filter(reference => !directIds.value.has(reference.referenceId)))
const citedMissing = computed(() => {
  const list = new Set<string>()
  for (const claim of props.model.claims) for (const id of claim.missingReferenceIds) if (!registeredIds.value.has(id)) list.add(id)
  return [...list]
})

function describeReference(reference: ReferenceView): string {
  const parts = [reference.sourceTypeLabel, '版本 ' + reference.sourceVersion]
  if (reference.locatingNote) parts.push(reference.locatingNote)
  parts.push(reference.dataNature)
  return parts.join(' · ')
}

const referenceCounts = computed(() => {
  const counts = new Map<string, number>()
  for (const claim of props.model.claims) {
    for (const id of claim.referenceIds) counts.set(id, (counts.get(id) ?? 0) + 1)
  }
  return counts
})

const roundFields = computed(() => [
  { title: '仍需确认', icon: 'activity', items: props.model.uncertainties },
  { title: '适用边界与数据限制', icon: 'warning', items: props.model.limitations }
])
const claimGroups = computed(() => [
  { key: 'direct', title: '直接相关候选原因', claims: props.model.directCauses },
  { key: 'context', title: '背景观测与上下文', claims: props.model.contextClaims },
  { key: 'other', title: '其他与未知判断', claims: props.model.otherClaims }
].filter(group => group.claims.length))
</script>

<template>
  <section class="pro-layout" :class="{ 'pro-page': run }" :data-run-status="run?.status" :data-run-id="run?.runId">
    <!-- 顶部任务与运行信息：任务卡承载目标、资产与时点 -->
    <header v-if="run && !embedded" class="pro-task-card">
      <div class="pro-task-main">
        <p class="pro-breadcrumb">专业诊断 <span aria-hidden="true">›</span> 调查任务</p>
        <p class="pro-task-eyebrow">INVESTIGATION / 诊断工作台</p>
        <h1 class="pro-task-title" tabindex="-1">{{ model.taskQuestion || '（本轮目标消息不可用）' }}</h1>
        <p class="pro-task-meta">
          设备 <strong data-testid="viewed-asset">{{ model.selectedAssetId || model.assetResolutionLabel }}</strong>
          <template v-if="model.selectedAssetId"> · {{ model.assetResolutionLabel }}</template>
          <template v-if="model.changedFromAssetId"> · 原对象 {{ model.changedFromAssetId }}</template>
          · 业务时点 <time :datetime="run.asOf">{{ new Date(run.asOf).toLocaleString('zh-CN', { hour12: false }) }}</time>
          <template v-if="model.dataNature"> · 数据性质 {{ model.dataNature }}</template>
          <template v-if="skillLabel"> · {{ skillLabel }}</template>
          <template v-if="run.activeDurationMs != null"> · 耗时 {{ Math.round(run.activeDurationMs / 1000) }} 秒</template>
        </p>
      </div>
      <div class="pro-task-status">
        <span class="chip" :class="run.status.toLowerCase()">{{ statusLabel }}</span>
        <span v-if="model.riskLevel" class="risk-chip" :class="model.riskLevel"><UiIcon name="warning" /> 风险 {{ model.riskLevel }}</span>
        <button type="button" class="pro-outline" @click="emit('history')"><UiIcon name="clock" /> 历史 Run</button>
      </div>
    </header>

    <div class="pro-workspace" :class="{ 'pro-workspace-empty': !run, 'pro-workspace-embedded': embedded }">
      <div class="pro-center">
        <slot v-if="!run" name="empty" />
        <template v-else>
          <section v-if="run.status === 'FAILED'" class="pro-state pro-card" data-state="failed" role="alert">
            <h3><UiIcon name="warning" /> 本次没有得到答案</h3>
            <p>{{ failureText || ('失败原因码：' + (run.failureCode ?? '未返回原因码')) }}</p>
            <p v-if="failureText && run.failureCode" class="hint">原因码：{{ run.failureCode }}</p>
            <p class="hint">可重新描述问题发起调查，历史消息和已登记来源仍可查看。</p>
          </section>
          <section v-else-if="run.status === 'CANCELLED'" class="pro-state pro-card" data-state="cancelled">
            <h3><UiIcon name="close" /> 本次调查已取消</h3>
            <p class="hint">取消前的消息、追问与事件仍可查看。</p>
          </section>
          <section v-else-if="run.status !== 'COMPLETED' && !embedded" class="pro-state pro-card" data-state="active">
            <h3><UiIcon name="activity" /> {{ statusLabel }}</h3>
            <p>{{ run.status === 'WAITING_FOR_USER' ? '请按待答问题补充信息，补答将继续原 Run。' : '正在排队或调查；连接中断后可重新同步持久状态。' }}</p>
          </section>
      <div v-if="run.status === 'COMPLETED'" class="pro-focus">
        <section class="pro-headline" aria-labelledby="pro-short-conclusion">
          <p class="pro-focus-eyebrow"><UiIcon name="spark" /> 调查结论 <span>执行已结束</span></p>
          <h2 id="pro-short-conclusion" class="visually-hidden">当前结论</h2>
          <p class="pro-short-conclusion">{{ model.summary || '本轮未返回摘要' }}</p>
          <div class="pro-result-labels"><span v-if="model.review.limited" class="risk-chip">有限结果 · 需人工复核</span><span class="chip">{{ model.categoryLabel }}</span><span v-if="model.riskLevel" class="risk-chip" :class="model.riskLevel">风险 {{ model.riskLevel }}</span><span v-if="model.dataNature" class="chip">{{ model.dataNature }}</span></div>
          <p class="hint">{{ model.shortConclusion }}</p>
        </section>
        <div class="pro-claim-workspace">
          <nav v-if="model.claims.length" class="pro-nav pro-card" aria-label="调查方向">
            <h3>判断导航 <span>{{ model.claims.length }} 项</span></h3>

            <ClaimList v-for="group in claimGroups" :key="group.key" :title="group.title" :claims="group.claims"
                       :selected-index="selectedIndex" :reference-counts="referenceCounts"
                       @select="emit('select', $event)" />
            <button type="button" class="pro-outline pro-nav-history" @click="emit('history')"><UiIcon name="clock" /> 查看 Run 历史</button>
          </nav>
          <div class="pro-claim-content">
            <div class="pro-focus-heading">
              <span>当前判断与依据</span>
              <span v-if="selectedIndex !== null">{{ String(selectedIndex + 1).padStart(2, '0') }} / {{ String(model.claims.length).padStart(2, '0') }}</span>
            </div>
            <ClaimDetail :claim="selectedClaim" @toggle-reference="emit('openReference', $event)" />
          </div>
        </div>
        <section class="pro-follow-through" aria-label="本轮建议与适用边界">
          <div v-if="model.suggestions.length" class="pro-next-actions">
            <h3><UiIcon name="tool" /> 本轮建议</h3>
            <ol>
              <li v-for="(item, index) in model.suggestions" :key="index">{{ item }}</li>
            </ol>
          </div>
          <p v-if="model.safetyNotice" class="safety" role="note">{{ model.safetyNotice }}</p>
          <p v-if="model.dataNatureNote" class="hint">{{ model.dataNatureNote }}</p>
          <AnswerReviewPanel v-if="run?.result" :review="model.review" @open-reference="emit('openReference', $event)" />

          <div class="pro-round-fields">
            <div v-for="field in roundFields" :key="field.title" class="round-field">
              <h4><UiIcon :name="field.icon" /> {{ field.title }}（本轮）</h4>
              <p v-if="!field.items.length" class="hint">本轮未列出</p>
              <ul v-else>
                <li v-for="(item, index) in field.items" :key="index">{{ item }}</li>
              </ul>
            </div>
            <p class="hint">{{ model.roundFieldsNote }}</p>
          </div>

        </section>
        <button id="pro-full-report" type="button" class="text-button pro-expand" :aria-expanded="resultExpanded"
                @click="resultExpanded = !resultExpanded">
          <UiIcon name="chevron" :class="{ rotated: resultExpanded }" />
          {{ resultExpanded ? '收起完整结果' : '展开完整结果' }}
        </button>
        <div v-if="resultExpanded" class="pro-full-result" data-testid="full-result">
          <h4><UiIcon name="file" /> 完整结果</h4>
          <dl>
            <div><dt>摘要</dt><dd>{{ model.summary ?? '（未返回摘要）' }}</dd></div>
            <div><dt>类别</dt><dd>{{ model.categoryLabel ?? '（未返回）' }}</dd></div>
            <div><dt>数据性质</dt><dd>{{ model.dataNature ?? '（未返回）' }}</dd></div>
            <div><dt>安全处置</dt><dd>{{ model.safetyDisposition ?? '（未返回）' }}</dd></div>
          </dl>
          <h5>判断原文（{{ model.claims.length }}）</h5>
          <p v-if="!model.claims.length" class="hint">无判断。</p>
          <ol v-else>
            <li v-for="claim in model.claims" :key="claim.key">
              <span class="certainty" :class="{ possible: claim.certainty === 'POSSIBLE' }">{{ claim.certaintyLabel }}</span>
              {{ claim.text }}
              <small v-if="claim.referenceIds.length">（{{ claim.referenceIds.length }} 条引用）</small>
            </li>
          </ol>
          <h5>建议行动（全部）</h5>
          <ul>
            <li v-for="(item, index) in model.suggestions" :key="index">{{ item }}</li>
            <li v-if="!model.suggestions.length" class="hint">无</li>
          </ul>
          <h5>限制与不确定点</h5>
          <ul>
            <li v-for="(item, index) in [...model.limitations, ...model.uncertainties]" :key="index">{{ item }}</li>
            <li v-if="!model.limitations.length && !model.uncertainties.length" class="hint">无</li>
          </ul>
        </div>
      </div>
        </template>
        <slot name="timeline" />
        <slot name="composer" />
      </div>

    <aside v-if="run && !embedded" class="pro-sources pro-card">
      <h3 id="pro-evidence-context">证据与上下文 <span>{{ registeredReferences.length }} 条来源</span></h3>
      <p class="hint">来源可追溯，不等同于现场核验。</p>
      <section v-if="terminal" data-testid="registered-sources">
      <p v-if="referencesNote" class="hint">{{ referencesNote }}</p>
      <button v-if="referencesState === 'error' || referencesState === 'notFound'" type="button" class="text-button"
              @click="emit('retryReferences')">重试读取来源</button>
      <p v-if="referencesState === 'empty' && !referencesNote" class="hint">本轮没有已登记来源。</p>
      <template v-if="directReferences.length">
        <h5>该焦点直接引用（{{ directReferences.length }}）</h5>
        <button v-for="reference in directReferences" :key="reference.referenceId" type="button"
                class="citation-ref" :data-evidence-id="reference.referenceId"
                @click="emit('openReference', reference.referenceId)">
          《{{ reference.title }}》
          <small>{{ describeReference(reference) }}</small>
        </button>
      </template>
      <details v-if="otherRegistered.length" class="other-sources">
        <summary>本轮其他已登记来源（{{ otherRegistered.length }}）</summary>
        <button v-for="reference in otherRegistered" :key="reference.referenceId" type="button"
                class="citation-ref" :data-evidence-id="reference.referenceId"
                @click="emit('openReference', reference.referenceId)">
          《{{ reference.title }}》
          <small>{{ describeReference(reference) }}</small>
        </button>
      </details>
      <p v-if="citedMissing.length" class="hint">有 {{ citedMissing.length }} 条引用未在本轮已登记来源中返回，暂不可用。</p>
      </section>
      <div class="pro-context-status">
        <h4>Run 状态</h4>
        <p class="hint">执行：{{ statusLabel }}</p>
        <button v-if="model.selectedAssetId" type="button" class="pro-outline" @click="emit('asset')">查看设备快照</button>
        <p v-else class="hint">{{ model.assetResolutionLabel }}，不显示未经确认的设备快照</p>
        <p class="pro-context-note">资料记载、用户报告与模拟观察不能直接确认现场配置。</p>
      </div>
    </aside>
    </div>
  </section>
</template>

<style scoped>
.pro-layout, .pro-center { display: grid; gap: 20px; min-width: 0; }
.pro-workspace { display: grid; grid-template-columns: minmax(0, 1fr) 250px; gap: 20px; align-items: start; }
.pro-workspace-empty, .pro-workspace-embedded { grid-template-columns: minmax(0, 1fr); }
.pro-focus { min-width: 0; display: grid; gap: 20px; }
.pro-headline { padding: 22px 26px; border-radius: 12px; background: #edf3ff; border-left: 4px solid var(--accent); }
.pro-focus-eyebrow { display: flex; gap: 8px; align-items: center; margin: 0 0 10px; color: var(--accent); font-size: 15px; font-weight: 650; }
.pro-focus-eyebrow span { margin-left: auto; color: var(--text-3); font-size: 14px; font-weight: 400; }
.pro-short-conclusion { margin: 0; font-size: 22px; line-height: 1.55; font-weight: 650; color: var(--text); white-space: pre-wrap; }
.pro-result-labels { display: flex; flex-wrap: wrap; gap: 8px; margin: 14px 0 8px; }
.chip, .risk-chip { display: inline-flex; align-items: center; gap: 5px; font-size: 14px; border-radius: 6px; padding: 3px 9px; color: var(--accent); background: #ffffffb8; }
.risk-chip { color: var(--warn); background: var(--warn-bg); font-weight: 600; }
.risk-chip.LOW { color: var(--ok); background: #e8f4ed; }
.risk-chip.HIGH, .risk-chip.PROHIBITED { color: var(--danger); background: #fff0ef; }
.pro-headline > .hint { margin: 0; }
.pro-claim-workspace { display: grid; grid-template-columns: 238px minmax(0, 1fr); background: var(--panel); border: 1px solid var(--line); border-radius: 12px; overflow: hidden; }
.pro-claim-workspace:not(:has(.pro-nav)) { grid-template-columns: minmax(0, 1fr); }
.pro-claim-workspace .pro-nav { padding: 20px 14px; background: #f9fbff; border: 0; border-right: 1px solid var(--line); border-radius: 0; box-shadow: none; }
.pro-nav > h3, .pro-sources > h3 { display: flex; justify-content: space-between; gap: 8px; margin: 0 0 18px; font-size: 16px; }
.pro-nav > h3 span { color: var(--text-3); font-size: 14px; font-weight: 400; }
.pro-nav-history { width: 100%; margin-top: 16px; }
.pro-claim-content { min-width: 0; padding: 22px 24px; }
.pro-focus-heading { display: flex; justify-content: space-between; gap: 8px; margin-bottom: 16px; color: var(--text-3); font-size: 14px; }
.pro-follow-through { padding: 22px 26px; border-top: 1px solid var(--line); }
.pro-next-actions { padding: 0 0 18px; }
.pro-next-actions h3 { display: flex; align-items: center; gap: 8px; margin: 0 0 10px; font-size: 16px; }
.pro-next-actions ol { margin: 0; padding-left: 23px; font-size: 16px; line-height: 1.8; color: var(--text-2); }
.safety { margin: 0 0 12px; padding: 12px 14px; border-radius: 8px; color: var(--warn); background: var(--warn-bg); font-size: 16px; line-height: 1.65; }
.pro-round-fields { display: grid; grid-template-columns: 1fr 1fr; gap: 20px; margin-top: 20px; }
.round-field h4 { display: flex; align-items: center; gap: 7px; margin: 0 0 8px; font-size: 16px; }
.round-field ul { padding-left: 20px; margin: 0; color: var(--text-2); font-size: 16px; line-height: 1.75; }
.pro-round-fields > .hint { grid-column: 1 / -1; margin: 0; font-size: 14px; }
.pro-expand { justify-self: start; margin-left: 26px; font-size: 15px; }
.pro-expand svg.rotated { transform: rotate(90deg); }
.pro-full-result { padding: 24px; background: var(--panel); border: 1px solid var(--line); border-radius: 10px; }
.pro-full-result h4, .pro-full-result h5 { font-size: 16px; margin: 14px 0 8px; }
.pro-full-result dl { margin: 0; display: grid; gap: 8px; }
.pro-full-result dl div { display: grid; grid-template-columns: 90px minmax(0, 1fr); gap: 12px; font-size: 16px; }
.pro-full-result dt { color: var(--text-3); } .pro-full-result dd { margin: 0; overflow-wrap: anywhere; }
.pro-full-result ol, .pro-full-result ul { padding-left: 22px; font-size: 16px; line-height: 1.75; }
.certainty { font-size: 14px; padding: 2px 7px; background: var(--soft); color: var(--accent); border-radius: 5px; }
.certainty.possible { color: var(--warn); background: var(--warn-bg); }
.pro-state { padding: 24px; border: 1px solid var(--line); border-radius: 12px; background: var(--panel); }
.pro-state h3 { margin: 0 0 10px; font-size: 20px; } .pro-state p { font-size: 16px; line-height: 1.7; }
.pro-task-card { display: flex; justify-content: space-between; align-items: center; gap: 16px; flex-wrap: wrap; }
.pro-task-title { font-size: 24px; margin: 0; } .pro-task-meta, .pro-breadcrumb { color: var(--text-3); font-size: 15px; }
.pro-task-status { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; }
.pro-task-eyebrow { color: var(--accent); font-size: 14px; }
.pro-sources { padding: 20px; min-width: 0; } .pro-sources h4, .pro-sources h5 { margin: 16px 0 8px; font-size: 16px; }
.citation-ref { display: block; width: 100%; padding: 10px; margin-bottom: 8px; border: 1px solid var(--line); border-radius: 8px; background: var(--panel-2); color: var(--accent); text-align: left; cursor: pointer; overflow-wrap: anywhere; }
.citation-ref small { display: block; color: var(--text-3); font-size: 14px; margin-top: 4px; }
.other-sources summary { cursor: pointer; font-size: 15px; } .pro-context-status { margin-top: 18px; border-top: 1px solid var(--line); }
.pro-context-note { color: var(--warn); font-size: 15px; line-height: 1.65; }
@media (max-width: 1100px) { .pro-workspace { grid-template-columns: minmax(0, 1fr); } .pro-claim-workspace { grid-template-columns: 205px minmax(0, 1fr); } .pro-claim-content { padding: 20px; } }
@media (max-width: 800px) {
  .pro-claim-workspace { grid-template-columns: minmax(0, 1fr); }
  .pro-claim-workspace .pro-nav { border-right: 0; border-bottom: 1px solid var(--line); }
}
@media (max-width: 600px) {
  .pro-headline, .pro-follow-through { padding: 18px; } .pro-short-conclusion { font-size: 19px; }
  .pro-round-fields { grid-template-columns: minmax(0, 1fr); } .pro-claim-content { padding: 18px; }
}
</style>
