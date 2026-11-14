<script setup lang="ts">
import { computed } from 'vue'
import UiIcon from './UiIcon.vue'
import type { AgentResult, Reference, RunStatus } from '../api'

/**
 * 结构化诊断报告：全部内容来自持久 run 结果与已登记引用，只呈现真实证据。
 * 依据与候选原因按 certainty 分开；没有现场核验时不得把候选原因说成已确诊。
 */
const props = defineProps<{
  status: RunStatus
  result: AgentResult | null
  failureCode: string | null
  failureText?: string
  references: Reference[]

}>()

const emit = defineEmits<{ (event: 'toggle-reference', referenceId: string): void }>()

const facts = computed(() => (props.result?.claims ?? []).filter((claim) => claim.certainty !== 'POSSIBLE'))
const possibleCauses = computed(() => (props.result?.claims ?? []).filter((claim) => claim.certainty === 'POSSIBLE'))
const manualItems = computed(() => {
  const result = props.result
  if (!result) return []
  const items = [...result.uncertainties, ...result.limitations]
  if (result.category === 'HUMAN_REVIEW') items.unshift('本次结论需要人工复核后再执行任何现场操作')
  return items
})
const safetyNotice = computed(() => {
  const disposition = props.result?.safetyDisposition ?? ''
  if (disposition === 'REQUIRES_QUALIFIED_PERSONNEL') return '涉及高风险操作：必须由具备资质的专业人员按厂家规程处理，本系统不提供操作步骤'
  if (disposition === 'PROHIBITED') return '该请求涉及被禁止的设备操作，系统不提供执行指引'
  return ''
})
const categoryLabels: Record<AgentResult['category'], string> = {
  KNOWLEDGE: '资料答复',
  POSSIBLE_CAUSES: '候选原因',
  CONFIRMED_FINDING: '已核实结论',
  HUMAN_REVIEW: '待人工核验'
}
function certaintyLabel(certainty: string): string {
  return certainty === 'CONFIRMED' ? '已核实' : certainty === 'SUPPORTED' ? '资料支持' : '可能原因'
}

function referenceLabel(referenceId: string): string {
  const index = props.references.findIndex(reference => reference.referenceId === referenceId)
  return index >= 0 ? '引用 ' + (index + 1) : '#' + referenceId.slice(0, 8)
}
</script>

<template>
  <section v-if="status === 'FAILED'" class="card report failure" role="alert">
    <h2>本次没有得到答案</h2>
    <p>{{ failureText || ('失败原因码：' + (failureCode ?? '未返回原因码')) }}</p>
    <p v-if="failureText && failureCode" class="hint">原因码：{{ failureCode }}</p>
    <p class="hint">可以在输入框重新描述问题后再次发起调查；历史消息与已取回的引用仍保留。</p>
  </section>

  <section v-else-if="status === 'CANCELLED'" class="card report cancelled">
    <h2>本次调查已取消</h2>
    <p class="hint">取消前的消息、追问与时间线仍保存在服务器，可随时重新发起调查。</p>
  </section>

  <section v-else-if="!result" class="card report waiting">
    <h2>诊断报告</h2>
    <p class="hint">
      {{ status === 'WAITING_FOR_USER' ? '等待你补充信息后继续生成结论。'
        : status === 'QUEUED' || status === 'RUNNING' ? '调查进行中，完成后这里会显示结构化报告。'
        : '还没有执行过调查。' }}
    </p>
  </section>

  <section v-else class="card report">
    <header class="report-header">
      <h2><UiIcon name="file" /> 诊断报告</h2>
      <span class="badge" :class="{ review: result.category === 'HUMAN_REVIEW' }">
        {{ categoryLabels[result.category] }} · 风险 {{ result.riskLevel }} · {{ result.dataNature }}
      </span>
    </header>
    <div class="conclusion">
      <UiIcon name="target" />
      <div><h3>本次调查结论</h3><p class="summary">{{ result.summary }}</p></div>
    </div>
    <p v-if="safetyNotice" class="safety" role="note">{{ safetyNotice }}</p>

    <div v-if="references.length > 0" class="section">
      <h3><UiIcon name="link" /> 关键资料（{{ references.length }}）</h3>
      <ul class="references">
        <li v-for="reference in references" :key="reference.referenceId">
          <button type="button" class="link citation" @click="emit('toggle-reference', reference.referenceId)">
            《{{ reference.title }}》{{ reference.section ?? '' }}
            <span v-if="reference.page">（第 {{ reference.page }} 页）</span>
          </button>
        </li>
      </ul>
    </div>

    <div v-if="possibleCauses.length > 0" class="section">
      <h3><UiIcon name="target" /> 候选原因（{{ possibleCauses.length }}）</h3>
      <ul>
        <li v-for="(claim, index) in possibleCauses" :key="index">
          <span class="certainty possible">{{ certaintyLabel(claim.certainty) }}</span>
          {{ claim.text }}
          <button v-for="id in claim.referenceIds" :key="id" type="button" class="citation-ref" @click="emit('toggle-reference', id)">{{ referenceLabel(id) }}</button>
        </li>
      </ul>
    </div>

    <div v-if="manualItems.length > 0" class="section">
      <h3><UiIcon name="check" /> 人工核验与限制（{{ manualItems.length }}）</h3>
      <ol class="manual">
        <li v-for="(item, index) in manualItems" :key="index">{{ item }}</li>
      </ol>
    </div>

    <div v-if="result.suggestions.length > 0" class="section">
      <h3><UiIcon name="bolt" /> 建议行动</h3>
      <ul>
        <li v-for="(suggestion, index) in result.suggestions" :key="index">{{ suggestion }}</li>
      </ul>
    </div>

    <details v-if="facts.length > 0" class="fact-details">
      <summary><UiIcon name="file" /> 已知事实与依据（{{ facts.length }}）</summary>
      <ul>
        <li v-for="(claim, index) in facts" :key="index">
          <span class="certainty supported">{{ certaintyLabel(claim.certainty) }}</span>
          {{ claim.text }}
          <button v-for="id in claim.referenceIds" :key="id" type="button" class="citation-ref" @click="emit('toggle-reference', id)">{{ referenceLabel(id) }}</button>
        </li>
      </ul>
    </details>
  </section>
</template>

<style scoped>
.report { border: 1px solid #dcdffc; border-radius: 12px; padding: 12px 15px; background: #f9f9fe; overflow-wrap: anywhere; }
.report-header { display: flex; align-items: center; justify-content: space-between; gap: 8px; flex-wrap: wrap; padding-bottom: 9px; border-bottom: 1px solid var(--line); }
.report h2 { font-size: 20px; margin: 0; }
.report-header h2 svg { color: var(--primary); margin-right: 6px; }
.badge, .certainty, .citation-ref { font-size: 12px; border-radius: 7px; padding: 2px 7px; color: var(--accent); background: #eae9ff; }
.badge.review, .certainty.possible { color: var(--warning); background: #ffefca; }
.conclusion { display: flex; align-items: flex-start; gap: 11px; background: #eeedfc; border-radius: 8px; padding: 9px 12px; margin: 10px 0; }
.conclusion > svg { color: var(--primary); font-size: 22px; margin-top: 2px; }
.conclusion h3 { color: var(--accent); font-size: 13px; margin: 0 0 3px; }
.summary { font-size: 16px; font-weight: 700; margin: 0; line-height: 1.48; }
.safety { background: #fff2dc; color: #783f13; border-radius: 7px; padding: 7px 9px; margin: 8px 0; font-size: 13px; }
.section { display: grid; grid-template-columns: minmax(137px, 23%) minmax(0, 1fr); gap: 9px; border-top: 1px solid var(--line); padding: 7px 3px; }
.section h3 { font-size: 13px; margin: 2px 0 0; }
.section h3 svg { color: var(--accent); margin-right: 4px; }
.section ul, .section ol { margin: 0; padding-left: 18px; line-height: 1.5; }
.section li + li { margin-top: 2px; }
.fact-details { border-top: 1px solid var(--line); padding: 7px 3px 0; }
.fact-details summary { cursor: pointer; font-size: 13px; font-weight: 700; }
.fact-details summary svg { color: var(--accent); margin-right: 4px; }
.fact-details ul { margin: 7px 0 0; padding-left: 18px; line-height: 1.5; }
.section .manual { list-style: none; padding-left: 0; counter-reset: manual; }
.manual li { display: flex; align-items: baseline; gap: 7px; counter-increment: manual; }
.manual li::before { content: counter(manual); flex: none; min-width: 18px; border-radius: 50%; background: #e7e5fc; color: var(--accent); text-align: center; font-size: 11px; line-height: 18px; }
.certainty { white-space: nowrap; margin-right: 4px; }
.citation-ref, .link { cursor: pointer; border: 0; color: var(--accent); }
.citation-ref { margin-left: 4px; }
.link { padding: 0; background: none; text-align: left; font-weight: 650; }
.link:hover, .citation-ref:hover { text-decoration: underline; }
.references { font-size: 13px; color: var(--muted); }
.failure { border-color: #f2c8cd; background: #fff4f4; }
.failure h2 { color: #9d303c; }
.cancelled { border-color: var(--line); background: #f6f6fb; }
@media (max-width: 680px) {
  .section { grid-template-columns: minmax(0, 1fr); gap: 5px; }
}
</style>
