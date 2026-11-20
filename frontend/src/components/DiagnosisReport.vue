<script setup lang="ts">
import { computed } from 'vue'
import UiIcon from './UiIcon.vue'
import AnswerReviewPanel from './AnswerReviewPanel.vue'
import { REPORT_STATUS_LABELS, canRetryReportReferences, reportReferenceNumbers } from '../reportView'
import type { RunView } from '../api'
import type { ClaimView, DiagnosisViewModel, ReferenceView } from '../diagnosisViewModel'

const props = defineProps<{
  run: RunView
  model: DiagnosisViewModel
  references: ReferenceView[]
  referencesState: 'loading' | 'ready' | 'empty' | 'unavailable' | 'denied' | 'notFound' | 'error'
  referencesNote: string
  failureText?: string
}>()

const emit = defineEmits<{
  close: []
  print: []
  openReference: [referenceId: string]
  retryReferences: []
}>()

const statusLabel = computed(() => REPORT_STATUS_LABELS[props.run.status])
const referenceNumbers = computed(() => reportReferenceNumbers(props.references))
const claimGroups = computed(() => [
  { key: 'direct', title: '直接相关候选原因', claims: props.model.directCauses },
  { key: 'context', title: '背景观测与上下文', claims: props.model.contextClaims },
  { key: 'other', title: '其他与未知判断', claims: props.model.otherClaims }
].filter(group => group.claims.length))

function referenceLabel(referenceId: string): string {
  const number = referenceNumbers.value.get(referenceId)
  return number ? `[${number}]` : '[引用不可用]'
}

function claimReferenceLabels(claim: ClaimView): string {
  return claim.referenceIds.map(referenceLabel).join(' ')
}
</script>

<template>
  <article class="diagnosis-report pro-card" aria-labelledby="diagnosis-report-title">
    <header class="report-header">
      <div>
        <p class="report-eyebrow">INVESTIGATION REPORT / 调查报告</p>
        <h1 id="diagnosis-report-title" tabindex="-1">设备检修诊断报告</h1>
        <p class="report-run">Run ID：{{ run.runId }}</p>
      </div>
      <div class="pro-report-actions">
        <button type="button" class="pro-outline" @click="emit('close')">返回工作台</button>
        <button type="button" class="pro-outline" @click="emit('print')"><UiIcon name="file" /> 打印 / 另存为 PDF</button>
      </div>
    </header>

    <section class="report-meta" aria-label="调查信息">
      <dl>
        <div><dt>调查目标</dt><dd>{{ model.taskQuestion || '（本轮目标消息不可用）' }}</dd></div>
        <div><dt>实际排查对象</dt><dd>{{ model.selectedAssetId || model.assetResolutionLabel }}</dd></div>
        <div v-if="model.changedFromAssetId"><dt>对象变更</dt><dd>{{ model.changedFromAssetId }} → {{ model.selectedAssetId || model.assetResolutionLabel }}</dd></div>
        <div><dt>对象状态</dt><dd>{{ model.assetResolutionLabel }}</dd></div>
        <div><dt>业务时点</dt><dd>{{ new Date(run.asOf).toLocaleString('zh-CN', { hour12: false }) }}</dd></div>
        <div><dt>Run 状态</dt><dd>{{ statusLabel }}</dd></div>
        <div><dt>风险 / 数据性质</dt><dd>{{ model.riskLevel || '未返回' }} / {{ model.dataNature || '未返回' }}</dd></div>
      </dl>
    </section>

    <section v-if="run.status === 'FAILED'" class="report-state" role="alert">
      <h2>本次没有得到答案</h2>
      <p>{{ failureText || ('失败原因码：' + (run.failureCode ?? '未返回原因码')) }}</p>
      <p v-if="failureText && run.failureCode" class="hint">原因码：{{ run.failureCode }}</p>
    </section>
    <section v-else-if="run.status === 'CANCELLED'" class="report-state">
      <h2>本次调查已取消</h2>
      <p>取消前的消息、追问与已登记来源仍保留。</p>
    </section>
    <section v-else-if="run.status !== 'COMPLETED'" class="report-state">
      <h2>{{ statusLabel }}</h2>
      <p v-if="run.pendingQuestion">{{ run.pendingQuestion.question }}</p>
      <p v-else>当前尚未形成完成结论，报告仅展示已持久化的信息。</p>
    </section>
    <template v-else>
      <section class="report-conclusion">
        <h2>当前结论</h2>
        <p class="report-short">{{ model.shortConclusion }}</p>
        <p class="report-summary">{{ model.summary || '未返回摘要' }}</p>
      </section>
      <p v-if="model.safetyNotice" class="safety">{{ model.safetyNotice }}</p>
      <p v-if="model.dataNatureNote" class="report-note">{{ model.dataNatureNote }}</p>
      <AnswerReviewPanel :review="model.review" @open-reference="emit('openReference', $event)" />

      <section class="report-section">
        <h2>建议行动与人工核验</h2>
        <h3>建议行动</h3>
        <ol v-if="model.suggestions.length"><li v-for="(item, index) in model.suggestions" :key="index">{{ item }}</li></ol>
        <p v-else class="hint">无</p>
        <h3>不确定点</h3>
        <ul v-if="model.uncertainties.length"><li v-for="(item, index) in model.uncertainties" :key="index">{{ item }}</li></ul>
        <p v-else class="hint">无</p>
        <h3>限制</h3>
        <ul v-if="model.limitations.length"><li v-for="(item, index) in model.limitations" :key="index">{{ item }}</li></ul>
        <p v-else class="hint">无</p>
      </section>

      <section v-for="group in claimGroups" :key="group.key" class="report-section">
        <h2>{{ group.title }}（{{ group.claims.length }}）</h2>
        <ol class="report-claims">
          <li v-for="claim in group.claims" :key="claim.key">
            <span class="certainty" :class="{ possible: claim.certainty === 'POSSIBLE' }">{{ claim.certaintyLabel }}</span>
            {{ claim.text }}
            <span v-if="claim.referenceIds.length" class="report-citations">{{ claimReferenceLabels(claim) }}</span>
            <small v-if="claim.referenceNote" class="hint">（{{ claim.referenceNote }}）</small>
          </li>
        </ol>
      </section>
      <p v-if="!model.claims.length" class="report-state">本轮没有结构化判断。</p>
    </template>

    <section class="report-section report-sources">
      <h2>来源与引用（{{ references.length }}）</h2>
      <p v-if="referencesNote" class="hint">{{ referencesNote }}</p>
      <button v-if="canRetryReportReferences(referencesState)" type="button" class="text-button pro-report-actions"
              @click="emit('retryReferences')">重试读取来源</button>
      <p v-if="referencesState === 'empty' && !referencesNote" class="hint">本轮没有已登记来源。</p>
      <ol>
        <li v-for="(reference, index) in references" :key="reference.referenceId" class="report-source">
          <h3>[{{ index + 1 }}] 《{{ reference.title }}》</h3>
          <p>{{ reference.sourceTypeLabel }} · 版本 {{ reference.sourceVersion }} · {{ reference.locatingNote || '未提供页码或章节' }} · {{ reference.dataNature }}</p>
          <p v-if="reference.excerpt" class="source-excerpt">{{ reference.excerpt }}</p>
        </li>
      </ol>
      <p class="report-note">来源可追溯，不等于现场核验。AUTHORIZED 仅说明来源已授权；FIELD_OBSERVATION 不证明接入真机。</p>
    </section>
  </article>
</template>

<style scoped>
.diagnosis-report { max-width: 1040px; margin: 0 auto; padding: 24px 28px; color: var(--text); }
.report-header { display: flex; align-items: flex-start; justify-content: space-between; gap: 14px; flex-wrap: wrap;
  border-bottom: 1px solid var(--line); padding-bottom: 16px; }
.report-eyebrow { margin: 0 0 5px; color: var(--accent); font-size: 11px; font-weight: 800; letter-spacing: 0.07em; }
.report-header h1 { margin: 0; font-size: 24px; }
.report-run { margin: 6px 0 0; color: var(--text-3); font-size: 11px; overflow-wrap: anywhere; }
.pro-report-actions { display: flex; gap: 8px; flex-wrap: wrap; }
.report-meta { padding: 14px 0; }
.report-meta dl { display: grid; gap: 6px; margin: 0; }
.report-meta dl div { display: grid; grid-template-columns: 110px minmax(0, 1fr); gap: 10px; font-size: 13px; }
.report-meta dt { color: var(--text-3); }
.report-meta dd { margin: 0; color: var(--text-2); overflow-wrap: anywhere; }
.report-conclusion { border-radius: 12px; background: var(--soft); padding: 14px 16px; }
.report-conclusion h2, .report-section h2, .report-state h2 { margin: 0 0 8px; font-size: 16px; color: var(--accent-2); }
.report-short { margin: 0 0 8px; font-size: 18px; font-weight: 730; line-height: 1.5; }
.report-summary { margin: 0; color: var(--text-2); line-height: 1.7; white-space: pre-wrap; }
.report-section { border-top: 1px solid var(--line); margin-top: 18px; padding-top: 16px; }
.report-section h3 { margin: 10px 0 5px; font-size: 13px; color: var(--text); }
.report-section ol, .report-section ul { margin: 0; padding-left: 22px; color: var(--text-2); line-height: 1.65; }
.report-section li + li { margin-top: 5px; }
.report-claims li { overflow-wrap: anywhere; }
.certainty { display: inline-block; border-radius: 999px; padding: 1px 8px; margin-right: 5px; font-size: 11px;
  color: var(--accent-2); background: var(--soft); }
.certainty.possible { color: var(--warn); background: var(--warn-bg); }
.report-citations { color: var(--accent-2); white-space: nowrap; }
.report-state { border: 1px solid var(--line); border-radius: 10px; padding: 14px; margin-top: 14px; }
.safety { border-radius: 10px; background: var(--warn-bg); color: var(--warn); padding: 10px 12px; line-height: 1.6; }
.report-note { border-radius: 9px; background: var(--warn-bg); color: var(--warn); padding: 9px; font-size: 12px; line-height: 1.6; }
.report-source { break-inside: avoid; }
.report-source h3 { overflow-wrap: anywhere; }
.source-excerpt { border-left: 3px solid var(--primary); background: var(--log-bg); padding: 9px 11px; white-space: pre-wrap; }
.hint { color: var(--text-3); font-size: 12px; }
@media (max-width: 680px) {
  .diagnosis-report { padding: 16px 14px; }
  .report-header h1 { font-size: 21px; }
  .report-meta dl div { grid-template-columns: 88px minmax(0, 1fr); }
}
</style>
