<script setup lang="ts">
import { computed } from 'vue'
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
  activeReferenceId: string | null
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
const sourceTypeLabels: Record<string, string> = {
  KNOWLEDGE_DOCUMENT: '知识资料',
  CONFIGURATION: '配置记录',
  FIELD_OBSERVATION: '现场核验'
}

function certaintyLabel(certainty: string): string {
  return certainty === 'CONFIRMED' ? '已核实' : certainty === 'SUPPORTED' ? '资料支持' : '可能原因'
}

function referenceLabel(referenceId: string): string {
  const match = props.references.find((reference) => reference.referenceId === referenceId)
  return match ? '#' + referenceId.slice(0, 8) : '#' + referenceId.slice(0, 8)
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
    <h2>诊断报告 <span class="badge">{{ result.category }} · 风险 {{ result.riskLevel }} · {{ result.dataNature }}</span></h2>
    <p class="summary">{{ result.summary }}</p>
    <p v-if="safetyNotice" class="safety" role="note">{{ safetyNotice }}</p>

    <div v-if="facts.length > 0" class="section">
      <h3>已知事实与依据（{{ facts.length }}）</h3>
      <ul>
        <li v-for="(claim, index) in facts" :key="index">
          <span class="certainty supported">{{ certaintyLabel(claim.certainty) }}</span>
          {{ claim.text }}
          <span class="refs">{{ claim.referenceIds.map(referenceLabel).join(' ') }}</span>
        </li>
      </ul>
    </div>

    <div v-if="possibleCauses.length > 0" class="section">
      <h3>候选原因（{{ possibleCauses.length }}）</h3>
      <ul>
        <li v-for="(claim, index) in possibleCauses" :key="index">
          <span class="certainty possible">{{ certaintyLabel(claim.certainty) }}</span>
          {{ claim.text }}
          <span class="refs">{{ claim.referenceIds.map(referenceLabel).join(' ') }}</span>
        </li>
      </ul>
    </div>

    <div v-if="manualItems.length > 0" class="section">
      <h3>人工核验与限制（{{ manualItems.length }}）</h3>
      <ul class="manual">
        <li v-for="(item, index) in manualItems" :key="index">{{ item }}</li>
      </ul>
    </div>

    <div v-if="result.suggestions.length > 0" class="section">
      <h3>建议行动</h3>
      <ul>
        <li v-for="(suggestion, index) in result.suggestions" :key="index">{{ suggestion }}</li>
      </ul>
    </div>

    <div v-if="references.length > 0" class="section">
      <h3>引用来源（{{ references.length }}）</h3>
      <ul class="references">
        <li v-for="reference in references" :key="reference.referenceId">
          <button
            type="button"
            class="link citation"
            @click="emit('toggle-reference', reference.referenceId)"
          >
            《{{ reference.title }}》{{ reference.section ?? '' }}
            <span v-if="reference.page">（第 {{ reference.page }} 页）</span>
          </button>
          · {{ sourceTypeLabels[reference.sourceType] ?? reference.sourceType }}
          · 版本 {{ reference.sourceVersion }} · {{ reference.dataNature }}
          <p v-if="activeReferenceId === reference.referenceId" class="excerpt">{{ reference.excerpt }}</p>
        </li>
      </ul>
    </div>
  </section>
</template>

<style scoped>
.report h2 {
  font-size: 16px;
  margin: 0 0 8px;
}
.badge {
  font-size: 12px;
  font-weight: 400;
  color: #334e68;
  background: #eef2f5;
  border-radius: 10px;
  padding: 1px 8px;
}
.summary {
  font-size: 15px;
  margin: 0 0 8px;
}
.safety {
  border-left: 3px solid #f0b7b7;
  background: #fff7f7;
  padding: 6px 10px;
  font-size: 13px;
  margin: 8px 0;
}
.section h3 {
  font-size: 14px;
  margin: 12px 0 4px;
}
.section ul {
  margin: 0;
  padding-left: 18px;
}
.certainty {
  font-size: 12px;
  border-radius: 10px;
  padding: 1px 6px;
  margin-right: 6px;
}
.certainty.supported {
  color: #1d4ed8;
  background: #e0eaff;
}
.certainty.possible {
  color: #a35c00;
  background: #fdeed3;
}
.refs {
  color: #616e7c;
  font-size: 12px;
}
.manual li {
  color: #616e7c;
}
.references .excerpt {
  color: #616e7c;
  font-size: 13px;
  margin: 4px 0 8px;
}
.failure {
  border-color: #f0b7b7;
  background: #fff7f7;
}
.cancelled {
  border-color: #cbd2d9;
  background: #f8f9fa;
}
.waiting {
  border-color: #b7d3f0;
  background: #f6faff;
}
</style>
