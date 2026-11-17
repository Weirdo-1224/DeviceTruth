<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import UiIcon from './UiIcon.vue'
import ClaimList from './ClaimList.vue'
import ClaimDetail from './ClaimDetail.vue'
import EvidenceDrawer from './EvidenceDrawer.vue'
import { buildDiagnosisViewModel } from '../diagnosisViewModel'
import { createClaimSelection } from '../claimSelection'
import { selectContextRefs } from '../api'
import type { AnswerCategory, MessageView, Reference, RunView, SkillSelection } from '../api'

/**
 * 专业诊断工作台主体（对齐设计稿三栏：判断列表 | 判断详情 | 证据详情）。
 * 全部内容来自持久 Run 与已登记引用的确定性投影（diagnosisViewModel），不发起模型调用；
 * 判断选择只切换查看焦点（claimSelection，零请求）；证据栏只读展示已登记引用。
 * 深入调查是显式新操作：经 plan 事件把继承清单与可用性交给 App 的底部操作栏渲染，
 * 点击后由 App 提交选中判断原文与继承引用开启新 Run（无 replyTo）。
 */
const props = defineProps<{
  run: RunView
  goalMessage?: MessageView | undefined
  references: Reference[]
  skill?: SkillSelection | null
  failureText?: string
  /** App 全局提交中：深入调查同样防重复点击。 */
  busy?: boolean
}>()

const emit = defineEmits<{
  /** 当前选中判断的深入调查计划：底部操作栏按钮的可用性、继承清单与提交载荷。 */
  plan: [payload: {
    enabled: boolean
    inheritedCount: number
    omittedCount: number
    inheritedTitles: string[]
    payload: {
      claimText: string
      category: AnswerCategory
      contextRefs: string[]
      selectedAssetId: string | null
    } | null
  }]
}>()

const model = computed(() => buildDiagnosisViewModel({ run: props.run, goalMessage: props.goalMessage ?? null, references: props.references }))

// 判断选中：仅当前快照内的查看焦点；Run 或结果替换时重置并默认选中第一条。
const selection = createClaimSelection()
watch(
  () => model.value.claims.map((claim) => claim.key).join('|'),
  () => selection.sync(model.value.claims.length),
  { immediate: true }
)
const selectedClaim = computed(() => {
  const index = selection.selectedIndex.value
  return index === null ? null : model.value.claims[index] ?? null
})

// 深入调查计划：contextRefs 取所选判断关联且本轮已取得的引用，按原顺序去重、最多 10 条。
const investigation = computed(() => {
  const claim = selectedClaim.value
  if (!claim) return null
  const available = new Map(model.value.references.map((reference) => [reference.referenceId, reference]))
  const inheritedIds = claim.referenceIds.filter((id) => available.has(id))
  const chosen = selectContextRefs(inheritedIds)
  return {
    claim,
    contextRefs: chosen.referenceIds,
    omittedCount: chosen.omittedCount,
    inheritedRefs: chosen.referenceIds.map((id) => available.get(id)!)
  }
})

const canInvestigate = computed(() =>
  props.run.status === 'COMPLETED' && investigation.value !== null && !props.busy)

watch(
  [canInvestigate, investigation],
  () => {
    const plan = investigation.value
    emit('plan', {
      enabled: canInvestigate.value,
      inheritedCount: plan?.inheritedRefs.length ?? 0,
      omittedCount: plan?.omittedCount ?? 0,
      inheritedTitles: plan?.inheritedRefs.map((ref) => ref.title) ?? [],
      // 选择动作零提交：载荷只在按钮点击时由 App 使用。
      payload: canInvestigate.value && plan
        ? {
            claimText: plan.claim.text,
            category: model.value.category ?? 'KNOWLEDGE',
            contextRefs: plan.contextRefs,
            selectedAssetId: model.value.selectedAssetId
          }
        : null
    })
  },
  { immediate: true }
)

// 证据详情栏：只读展示某条已登记引用；关闭不改变判断选中状态。
const openReferenceId = ref<string | null>(null)
const openReference = computed(() => model.value.references.find((ref) => ref.referenceId === openReferenceId.value) ?? null)
function toggleReference(referenceId: string): void {
  openReferenceId.value = openReferenceId.value === referenceId ? null : referenceId
}
function closeDrawer(): void {
  openReferenceId.value = null
}

const statusLabels: Record<string, string> = {
  QUEUED: '排队中',
  RUNNING: '执行中',
  WAITING_FOR_USER: '等待补充信息',
  COMPLETED: '已完成',
  FAILED: '执行失败',
  CANCELLED: '已取消'
}
const statusLabel = computed(() => statusLabels[props.run.status] ?? props.run.status)
const isActive = computed(() => ['QUEUED', 'RUNNING'].includes(props.run.status))

const roundFields = computed(() => [
  { title: '不确定点', icon: 'activity', items: model.value.uncertainties },
  { title: '限制', icon: 'warning', items: model.value.limitations }
] as const)
</script>

<template>
  <!-- 失败轮：安全失败卡片 + 已取回证据 -->
  <section v-if="run.status === 'FAILED'" class="card pro-page pro-state" data-state="failed" role="alert">
    <h2><UiIcon name="warning" /> 本次没有得到答案</h2>
    <p>{{ failureText || ('失败原因码：' + (run.failureCode ?? '未返回原因码')) }}</p>
    <p v-if="failureText && run.failureCode" class="hint">原因码：{{ run.failureCode }}</p>
    <div v-if="model.references.length" class="failure-evidence">
      <h3><UiIcon name="link" /> 本轮已取回证据（{{ model.references.length }}）</h3>
      <button v-for="reference in model.references" :key="reference.referenceId" type="button"
              class="citation-ref" @click="toggleReference(reference.referenceId)">
        《{{ reference.title }}》{{ reference.locatingNote ?? '' }}
        <small>{{ reference.sourceTypeLabel }} · {{ reference.dataNature }}</small>
      </button>
    </div>
    <p class="hint">可以在下方输入框重新描述问题后再次发起调查；历史消息与已取回的引用仍保留。</p>
  </section>

  <section v-else-if="run.status === 'CANCELLED'" class="card pro-page pro-state">
    <h2><UiIcon name="close" /> 本次调查已取消</h2>
    <div v-if="model.references.length" class="failure-evidence">
      <h3><UiIcon name="link" /> 本轮已取回证据（{{ model.references.length }}）</h3>
      <button v-for="reference in model.references" :key="reference.referenceId" type="button"
              class="citation-ref" @click="toggleReference(reference.referenceId)">
        《{{ reference.title }}》{{ reference.locatingNote ?? '' }}
        <small>{{ reference.sourceTypeLabel }} · {{ reference.dataNature }}</small>
      </button>
    </div>
    <p class="hint">取消前的消息、追问与时间线仍保存在服务器，可在下方输入框重新发起调查。</p>
  </section>

  <section v-else-if="isActive || run.status === 'WAITING_FOR_USER'" class="card pro-page pro-state">
    <h2><UiIcon name="activity" /> 调查进行中</h2>
    <div class="pro-meta">
      <span class="chip">状态 {{ statusLabel }}</span>
      <span class="chip">{{ model.dataNature ?? run.dataNature }}</span>
      <span v-if="skill" class="chip">领域包 {{ skill.skillId }} v{{ skill.version }}</span>
    </div>
    <p class="hint">
      {{ run.status === 'WAITING_FOR_USER'
        ? '等待你补充信息：请使用下方“需要补充信息”问题区补答，补答沿同一 Run 继续，不会新增轮次。'
        : '正在排队或取证，过程事件见“查看调查记录”；连接中断会回读服务器状态，不会把断流当作失败。' }}
    </p>
  </section>

  <section v-else-if="!model.summary" class="card pro-page pro-state">
    <h2><UiIcon name="file" /> 专业诊断工作台</h2>
    <p class="hint">本轮还没有结构化结果。</p>
  </section>

  <section v-else class="pro-page pro-grid3">
    <!-- 左栏：判断列表 -->
    <div class="pro-claims-col">
      <ClaimList :title="model.claimsTitle" :claims="model.claims"
                 :selected-index="selection.selectedIndex.value" @select="selection.select" />
      <p v-if="investigation" class="pro-inherit-hint hint">
        <template v-if="investigation.inheritedRefs.length">
          深入调查将继承 {{ investigation.inheritedRefs.length }} 条引用
        </template>
        <template v-else>选中判断暂无已关联引用，仍可直接深入调查</template>
        <template v-if="investigation.omittedCount">；另有 {{ investigation.omittedCount }} 条超出上限已省略</template>
      </p>
    </div>

    <!-- 中栏：判断详情 -->
    <div class="pro-detail-col">
      <div class="conclusion">
        <UiIcon name="target" />
        <div>
          <h3>本轮摘要</h3>
          <p class="summary">{{ model.summary }}</p>
        </div>
        <span v-if="model.riskLevel" class="risk-chip" :class="model.riskLevel">
          <UiIcon name="warning" /> 风险 {{ model.riskLevel }}
        </span>
      </div>
      <p v-if="model.safetyNotice" class="safety" role="note">{{ model.safetyNotice }}</p>
      <p v-if="model.dataNatureNote" class="hint">{{ model.dataNatureNote }}</p>

      <ClaimDetail :claim="selectedClaim" @toggle-reference="toggleReference" />

      <div v-if="model.suggestions.length" class="round-field">
        <h4><UiIcon name="bolt" /> 建议核验（本轮）</h4>
        <ol class="verify-steps">
          <li v-for="(item, index) in model.suggestions" :key="index">{{ item }}</li>
        </ol>
      </div>
      <div v-for="field in roundFields" :key="field.title" class="round-field">
        <h4><UiIcon :name="field.icon" /> {{ field.title }}（本轮）</h4>
        <p v-if="!field.items.length" class="hint">无</p>
        <ul v-else>
          <li v-for="(item, index) in field.items" :key="index">{{ item }}</li>
        </ul>
      </div>
      <p class="hint">{{ model.roundFieldsNote }}</p>
    </div>

    <!-- 右栏：证据详情 -->
    <div class="pro-evidence-col">
      <EvidenceDrawer :reference="openReference" @close="closeDrawer" />
    </div>
  </section>
</template>

<style scoped>
.pro-page { color: var(--text, #e7eefb); }
.pro-state.card {
  border: 1px solid var(--line-soft, #1d2b44); border-radius: 14px;
  background: linear-gradient(180deg, var(--panel, #121c2e), rgba(18, 28, 46, 0.6));
  padding: 22px 24px; max-width: 1500px; margin: 0 auto;
}
.pro-state > h2 { font-size: 18px; margin: 0 0 10px; display: flex; align-items: center; gap: 8px; }
.pro-meta { display: flex; flex-wrap: wrap; gap: 8px; margin: 8px 0; }
.chip { display: inline-flex; align-items: center; gap: 5px; font-size: 12px; border-radius: 999px;
  padding: 3px 10px; color: var(--accent-2, #7dd3fc); background: var(--soft, rgba(56, 214, 245, 0.1));
  border: 1px solid rgba(56, 214, 245, 0.25); }

/* 三栏 */
.pro-grid3 {
  display: grid; grid-template-columns: minmax(240px, 300px) minmax(0, 1fr) minmax(280px, 340px);
  gap: 14px; max-width: 1500px; margin: 0 auto; align-items: start;
}
@media (max-width: 1100px) { .pro-grid3 { grid-template-columns: minmax(220px, 280px) minmax(0, 1fr); } }
.pro-claims-col, .pro-detail-col { min-width: 0; }
.pro-inherit-hint { margin: 10px 2px 0; line-height: 1.6; }

/* 中栏 */
.conclusion { display: flex; align-items: flex-start; gap: 12px; position: relative;
  background: linear-gradient(135deg, rgba(56, 214, 245, 0.12), rgba(56, 214, 245, 0.03));
  border: 1px solid rgba(56, 214, 245, 0.28); border-radius: 12px; padding: 14px 16px; }
.conclusion > svg { color: var(--accent, #38d6f5); font-size: 22px; margin-top: 2px; }
.conclusion h3 { color: var(--accent-2, #7dd3fc); font-size: 12px; margin: 0 0 4px; letter-spacing: 0.04em; }
.summary { font-size: 15px; font-weight: 700; margin: 0; line-height: 1.5; }
.risk-chip { position: absolute; top: 12px; right: 14px; display: inline-flex; align-items: center; gap: 5px;
  font-size: 12px; font-weight: 700; border-radius: 999px; padding: 4px 10px;
  color: var(--warn, #f5b53f); background: var(--warn-bg, rgba(245, 181, 63, 0.12));
  border: 1px solid rgba(245, 181, 63, 0.4); }
.risk-chip.LOW { color: var(--ok, #34d399); background: rgba(52, 211, 153, 0.1); border-color: rgba(52, 211, 153, 0.35); }
.risk-chip.HIGH, .risk-chip.PROHIBITED { color: #fca5a5; background: rgba(248, 113, 113, 0.1); border-color: rgba(248, 113, 113, 0.4); }
.safety { background: var(--warn-bg, rgba(245, 181, 63, 0.12)); color: var(--warn, #f5b53f);
  border: 1px solid rgba(245, 181, 63, 0.35); border-radius: 9px; padding: 8px 11px; margin: 10px 0 0; font-size: 13px; }

.round-field { border-top: 1px solid var(--line-soft, #1d2b44); padding: 10px 2px 0; margin-top: 10px; }
.round-field h4 { font-size: 13px; margin: 0 0 8px; display: flex; align-items: center; gap: 6px;
  color: var(--accent-2, #7dd3fc); }
.round-field ul { margin: 0; padding-left: 18px; line-height: 1.6; font-size: 13px; color: var(--text-2, #9fb0c9); }
.verify-steps { margin: 0; padding-left: 22px; line-height: 1.6; font-size: 13px; color: var(--text-2, #9fb0c9); }
.verify-steps li { margin-bottom: 6px; }

.failure-evidence { margin: 12px 0; border: 1px solid var(--line-soft, #1d2b44); border-radius: 10px;
  padding: 12px 14px; background: var(--bg-deep, #080d18); }
.failure-evidence h3 { font-size: 13px; margin: 0 0 8px; display: flex; align-items: center; gap: 6px;
  color: var(--accent-2, #7dd3fc); }
.citation-ref { cursor: pointer; display: block; border: 0; padding: 5px 0; background: none;
  color: var(--accent-2, #7dd3fc); font-weight: 650; font-size: 13px; text-align: left; }
.citation-ref small { display: block; font-weight: 400; color: var(--text-3, #64748f); }
.citation-ref:hover { text-decoration: underline; }
</style>
