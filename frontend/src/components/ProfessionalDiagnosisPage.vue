<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import UiIcon from './UiIcon.vue'
import ClaimList from './ClaimList.vue'
import ClaimDetail from './ClaimDetail.vue'
import type { ClaimView, DiagnosisViewModel, ReferenceView } from '../diagnosisViewModel'
import type { RunView } from '../api'

/**
 * 专业诊断工作台主体（单焦点工作区）。
 * 全部内容来自持久 Run 与已登记引用的确定性投影（diagnosisViewModel），不发起模型调用；
 * 判断选择只切换查看焦点（claimSelection，零请求）。
 * 完整结果、风险与本轮字段在默认状态可读；建议/不确定点/限制收进“完整结果”按需展开时仍保留原文。
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
  { title: '不确定点', icon: 'activity', items: props.model.uncertainties },
  { title: '限制', icon: 'warning', items: props.model.limitations }
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
    <header v-if="run" class="pro-task-card">
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

    <div class="pro-workspace" :class="{ 'pro-workspace-empty': !run }">
      <nav v-if="run" class="pro-nav pro-card" aria-label="调查方向">
        <h3>调查方向 <span>{{ model.claims.length }} 项</span></h3>
        <p class="hint">按线索查看判断、证据与待核验项。</p>
        <ClaimList v-for="group in claimGroups" :key="group.key" :title="group.title" :claims="group.claims"
                   :selected-index="selectedIndex" :reference-counts="referenceCounts"
                   @select="emit('select', $event)" />
        <p v-if="!model.claims.length" class="hint">本轮暂无结构化判断。</p>
        <button type="button" class="pro-outline pro-nav-history" @click="emit('history')"><UiIcon name="clock" /> 查看 Run 历史</button>
      </nav>
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
          <section v-else-if="run.status !== 'COMPLETED'" class="pro-state pro-card" data-state="active">
            <h3><UiIcon name="activity" /> {{ statusLabel }}</h3>
            <p>{{ run.status === 'WAITING_FOR_USER' ? '请按待答问题补充信息，补答将继续原 Run。' : '正在排队或调查；连接中断后可重新同步持久状态。' }}</p>
          </section>
      <div v-else class="pro-focus pro-card">
        <section class="pro-headline" aria-labelledby="pro-short-conclusion">
          <p class="pro-focus-eyebrow">CURRENT RESULT / 当前结果</p>
          <h2 id="pro-short-conclusion">当前结论</h2>
          <p class="pro-short-conclusion">{{ model.shortConclusion || '本轮未返回结构化结论' }}</p>
          <div v-if="model.nextActions.length" class="pro-next-actions">
            <h3>建议下一步</h3>
            <ol>
              <li v-for="(item, index) in model.nextActions" :key="index">{{ item }}</li>
            </ol>
            <p v-if="model.remainingSuggestionCount" class="hint">
              另有 {{ model.remainingSuggestionCount }} 项建议，可在完整结果中查看。
            </p>
          </div>
        </section>
        <div class="pro-focus-heading">
          <span>CURRENT FOCUS / 当前调查焦点</span>
          <span v-if="selectedIndex !== null">{{ String(selectedIndex + 1).padStart(2, '0') }} / {{ String(model.claims.length).padStart(2, '0') }}</span>
        </div>
        <ClaimDetail :claim="selectedClaim" @toggle-reference="emit('openReference', $event)" />
        <div v-if="selectedIndex === null" class="conclusion">
          <div>
            <h3>本轮摘要</h3>
            <p class="summary">{{ model.summary || '未返回摘要' }}</p>
          </div>
        </div>
        <p v-if="model.safetyNotice" class="safety" role="note">{{ model.safetyNotice }}</p>
        <p v-if="model.dataNatureNote" class="hint">{{ model.dataNatureNote }}</p>

        <div class="pro-round-fields">
          <div v-for="field in roundFields" :key="field.title" class="round-field">
            <h4><UiIcon :name="field.icon" /> {{ field.title }}（本轮）</h4>
            <p v-if="!field.items.length" class="hint">无</p>
            <ul v-else>
              <li v-for="(item, index) in field.items" :key="index">{{ item }}</li>
            </ul>
          </div>
          <p class="hint">{{ model.roundFieldsNote }}</p>
        </div>

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
        <slot name="composer" />
        <slot name="timeline" />
      </div>

    <aside v-if="run" class="pro-sources pro-card">
      <h3 id="pro-evidence-context">证据与上下文 <span>{{ registeredReferences.length }} 条来源</span></h3>
      <p class="hint">来源可追溯，不等同于现场核验。</p>
      <section v-if="terminal" data-testid="registered-sources">
      <p class="hint">{{ referencesNote }}</p>
      <button v-if="referencesState === 'error' || referencesState === 'notFound'" type="button" class="text-button"
              @click="emit('retryReferences')">重试读取来源</button>
      <p v-if="referencesState === 'empty'" class="hint">本轮没有已登记来源。</p>
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
.pro-layout { display: grid; gap: 20px; min-width: 0; }
.pro-task-card {
  display: flex; align-items: center; justify-content: space-between; gap: 14px; flex-wrap: wrap;
}
.pro-task-main { min-width: min(340px, 100%); flex: 1; }
.pro-breadcrumb { margin: 0 0 12px; color: var(--text-3); font-size: 11px; }
.pro-breadcrumb span { margin: 0 6px; }
.pro-task-eyebrow { margin: 0 0 5px; font-size: 11px; font-weight: 800; letter-spacing: 0.09em; color: var(--accent); }
.pro-task-title { margin: 0; font-size: clamp(19px, 1.6vw, 24px); line-height: 1.35; color: var(--text); overflow-wrap: anywhere; }
.pro-task-meta { margin: 6px 0 0; font-size: 12px; color: var(--text-2); display: flex; flex-wrap: wrap; gap: 4px 8px; }
.pro-task-status { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.chip { display: inline-flex; align-items: center; gap: 5px; font-size: 12px; border-radius: 999px;
  padding: 4px 11px; color: var(--accent-2); background: var(--soft); border: 1px solid var(--line); }
.chip.completed { color: var(--ok); background: rgba(52, 211, 153, 0.12); }
.chip.failed, .chip.cancelled { color: var(--danger); background: rgba(248, 113, 113, 0.12); }
.chip.waiting_for_user { color: var(--warn); background: var(--warn-bg); }
.risk-chip { display: inline-flex; align-items: center; gap: 5px; font-size: 12px; font-weight: 700; border-radius: 999px;
  padding: 4px 11px; color: var(--warn); background: var(--warn-bg); border: 1px solid rgba(245, 181, 63, 0.4); }
.risk-chip.LOW { color: var(--ok); background: rgba(52, 211, 153, 0.12); border-color: rgba(52, 211, 153, 0.35); }
.risk-chip.HIGH, .risk-chip.PROHIBITED { color: var(--danger); background: rgba(248, 113, 113, 0.12); border-color: rgba(248, 113, 113, 0.4); }
.safety { margin: 0; background: var(--warn-bg); color: var(--warn); border: 1px solid rgba(245, 181, 63, 0.35);
  border-radius: 10px; padding: 9px 12px; font-size: 13px; line-height: 1.55; }
.pro-state { border: 1px solid var(--line-soft); border-radius: 14px; background: var(--panel); padding: 16px 18px; }
.pro-state h3 { font-size: 15px; margin: 0 0 8px; display: flex; align-items: center; gap: 8px; color: var(--text); }
.pro-state p { margin: 0 0 6px; font-size: 13px; color: var(--text-2); line-height: 1.6; }

.pro-workspace { display: grid; grid-template-columns: 216px minmax(0, 1fr) 236px; gap: 14px; align-items: start; }
.pro-workspace-empty { grid-template-columns: minmax(0, 1fr); max-width: 820px; width: 100%; margin: 0 auto; }
.pro-center { display: grid; gap: 12px; min-width: 0; }
.pro-nav, .pro-sources { min-width: 0; padding: 16px 14px; }
.pro-nav > h3, .pro-sources > h3 { margin: 0; display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between;
  gap: 5px; font-size: 14px; color: var(--text); }
.pro-nav > h3 span, .pro-sources > h3 span, .pro-focus-heading span + span {
  border-radius: 99px; padding: 3px 8px; background: var(--soft); color: var(--accent-2); font-size: 11px;
}
.pro-nav-history { margin-top: 14px; width: 100%; }
.pro-focus { min-width: 0; display: grid; gap: 10px; padding: 20px 22px; }
.pro-headline { border-bottom: 1px solid var(--line); padding-bottom: 12px; }
.pro-focus-eyebrow { margin: 0 0 4px; color: var(--accent); font-size: 11px; font-weight: 800; letter-spacing: 0.07em; }
.pro-headline h2 { margin: 0 0 5px; font-size: 14px; color: var(--text); }
.pro-short-conclusion { margin: 0; font-size: 18px; line-height: 1.55; font-weight: 730; color: var(--text); }
.pro-next-actions { margin-top: 10px; border-radius: 10px; background: var(--soft); padding: 10px 12px; }
.pro-next-actions h3 { margin: 0 0 5px; font-size: 13px; color: var(--accent-2); }
.pro-next-actions ol { margin: 0; padding-left: 20px; font-size: 13px; line-height: 1.6; color: var(--text-2); }
.pro-next-actions .hint { margin: 6px 0 0; }
.pro-focus > .hint { margin: 0; }
.pro-focus-heading { display: flex; justify-content: space-between; align-items: center; gap: 6px; color: var(--accent);
  font-size: 11px; font-weight: 800; letter-spacing: 0.07em; }
.conclusion { border-top: 1px solid var(--line); padding-top: 12px; }
.conclusion h3 { color: var(--accent-2); font-size: 12px; margin: 0 0 4px; letter-spacing: 0.04em; }
.summary { font-size: 13px; margin: 0; line-height: 1.6; color: var(--text-2); overflow-wrap: anywhere; }
.pro-expand { justify-self: start; min-height: 32px; }
.pro-expand svg.rotated { transform: rotate(90deg); }
.pro-round-fields { border-top: 1px solid var(--line); padding-top: 11px; display: grid; grid-template-columns: 1fr 1fr; gap: 10px 14px; }
.pro-round-fields > :first-child, .pro-round-fields > .hint { grid-column: 1 / -1; }
.pro-round-fields .hint { margin: 0; }
.round-field h4 { font-size: 13px; margin: 0 0 4px; display: flex; align-items: center; gap: 6px; color: var(--text); }
.round-field ul, .round-field ol { margin: 0; padding-left: 20px; line-height: 1.6; font-size: 13px; color: var(--text-2); }
.pro-full-result { border-top: 1px solid var(--line-soft); padding-top: 10px; }
.pro-full-result h4, .pro-full-result h5 { font-size: 13px; color: var(--accent-2); margin: 0 0 8px; display: flex; gap: 6px; align-items: center; }
.pro-full-result h5 { margin-top: 12px; }
.pro-full-result dl { display: grid; gap: 6px; margin: 0; }
.pro-full-result dl div { display: grid; grid-template-columns: 84px minmax(0, 1fr); gap: 8px; font-size: 12px; }
.pro-full-result dt { color: var(--text-3); }
.pro-full-result dd { margin: 0; color: var(--text-2); overflow-wrap: anywhere; }
.pro-full-result ol, .pro-full-result ul { margin: 0; padding-left: 20px; line-height: 1.6; font-size: 13px; color: var(--text-2); }
.pro-full-result li { overflow-wrap: anywhere; }
.certainty { font-size: 11px; border-radius: 999px; padding: 1px 8px; font-weight: 700; margin-right: 4px;
  color: var(--accent-2); background: var(--soft); border: 1px solid var(--line); }
.certainty.possible { color: var(--warn); background: var(--warn-bg); border-color: rgba(245, 181, 63, 0.4); }

.pro-context-status { border-top: 1px solid var(--line); padding-top: 12px; margin-top: 12px; }
.pro-context-note { border-radius: 9px; background: var(--warn-bg); color: var(--warn); padding: 9px;
  margin-bottom: 0; font-size: 11px; line-height: 1.6; }
.pro-sources h4, .pro-sources h5 { font-size: 13px; color: var(--accent-2); margin: 0 0 8px; display: flex; gap: 6px; align-items: center; }
.pro-sources h5 { margin-top: 10px; font-size: 12px; color: var(--text-2); }
.citation-ref { cursor: pointer; display: block; width: 100%; text-align: left; border: 1px solid var(--line-soft);
  border-radius: 10px; padding: 8px 11px; margin-bottom: 6px; background: var(--panel-2); color: var(--accent-2);
  font-size: 13px; font-weight: 600; overflow-wrap: anywhere; }
.citation-ref small { display: block; font-weight: 400; color: var(--text-3); font-size: 11px; margin-top: 2px; }
.citation-ref:hover { border-color: var(--primary); }
.other-sources { margin-top: 10px; }
.other-sources summary { cursor: pointer; color: var(--text-2); font-size: 12px; font-weight: 650; margin-bottom: 8px; }

@media (max-width: 1360px) {
  .pro-workspace:not(.pro-workspace-empty) { grid-template-columns: 205px minmax(0, 1fr); }
  .pro-sources { grid-column: 1 / -1; }
}
@media (max-width: 1000px) {
  .pro-workspace:not(.pro-workspace-empty) { grid-template-columns: minmax(0, 1fr); }
  .pro-center { grid-row: 1; }
  .pro-nav { grid-row: 2; }
  .pro-sources { grid-row: 3; }
}
@media (max-width: 680px) {
  .pro-focus { padding: 14px; }
  .pro-round-fields { grid-template-columns: minmax(0, 1fr); }
}
</style>
