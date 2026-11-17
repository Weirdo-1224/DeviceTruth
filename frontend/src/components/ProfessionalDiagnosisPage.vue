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
 * 专业诊断工作台单页：顶部任务区 + 左侧判断区 + 中央详情区 + 右侧证据抽屉 + 底部协作状态条。
 * 全部内容来自持久 Run 与已登记引用的确定性投影（diagnosisViewModel），不发起模型调用；
 * 判断选择只切换查看焦点（claimSelection，零请求）；证据抽屉只读展示已登记引用。
 * 深入调查是显式新操作：提交选中判断原文与继承引用开启新 Run（无 replyTo），
 * 执行中/未选中/提交中禁用；普通文字继续调查沿用 App 输入框，作为用户报告开新轮。
 * 没有候选原因时按实际类别安全展示，不伪造候选；无个人模式切换与关系图入口。
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
  /** 深入调查选中判断：由 App 组正文并发送，携带继承引用与本轮所选设备。 */
  investigate: [payload: {
    claimText: string
    category: AnswerCategory
    contextRefs: string[]
    selectedAssetId: string | null
  }]
}>()

const model = computed(() => buildDiagnosisViewModel({ run: props.run, goalMessage: props.goalMessage ?? null, references: props.references }))

const statusLabels: Record<string, string> = {
  QUEUED: '排队中',
  RUNNING: '执行中',
  WAITING_FOR_USER: '等待补充信息',
  COMPLETED: '已完成',
  FAILED: '执行失败',
  CANCELLED: '已取消'
}
const statusLabel = computed(() => statusLabels[props.run.status] ?? props.run.status)

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

// 5.1 深入调查：contextRefs 取所选判断关联且本轮已取得的引用，按原顺序去重、最多 10 条；
// 按钮旁明示继承清单与省略数量，无引用也可调查。
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

function requestInvestigation(): void {
  const plan = investigation.value
  if (!canInvestigate.value || !plan) return
  // 选择动作零提交：只有点击本按钮才发请求；幂等键由 App 按“一次明确新操作”生成。
  emit('investigate', {
    claimText: plan.claim.text,
    category: model.value.category ?? 'KNOWLEDGE',
    contextRefs: plan.contextRefs,
    selectedAssetId: model.value.selectedAssetId
  })
}

// 证据抽屉：只读展示某条已登记引用；关闭不改变判断选中状态。
const openReferenceId = ref<string | null>(null)
const openReference = computed(() => model.value.references.find((ref) => ref.referenceId === openReferenceId.value) ?? null)
function toggleReference(referenceId: string): void {
  openReferenceId.value = openReferenceId.value === referenceId ? null : referenceId
}
function closeDrawer(): void {
  openReferenceId.value = null
}

const runRoundFields = computed(() => [
  { title: '本轮建议', icon: 'bolt', items: model.value.suggestions },
  { title: '不确定点', icon: 'activity', items: model.value.uncertainties },
  { title: '限制', icon: 'warning', items: model.value.limitations }
] as const)
</script>

<template>
  <section v-if="run.status === 'FAILED'" class="card pro-page" role="alert">
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

  <section v-else-if="run.status === 'CANCELLED'" class="card pro-page">
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

  <section v-else-if="run.status === 'QUEUED' || run.status === 'RUNNING' || run.status === 'WAITING_FOR_USER'" class="card pro-page">
    <h2><UiIcon name="activity" /> 调查进行中</h2>
    <div class="pro-meta">
      <span class="chip">状态 {{ statusLabel }}</span>
      <span class="chip">{{ model.dataNature ?? run.dataNature }}</span>
      <span v-if="skill" class="chip">领域包 {{ skill.skillId }} v{{ skill.version }}</span>
    </div>
    <p class="hint">
      {{ run.status === 'WAITING_FOR_USER'
        ? '等待你补充信息：请使用下方“需要补充信息”问题区补答，补答沿同一 Run 继续，不会新增轮次。'
        : '正在排队或取证，过程事件见上方时间线；连接中断会回读服务器状态，不会把断流当作失败。' }}
    </p>
  </section>

  <section v-else-if="!model.summary" class="card pro-page">
    <h2><UiIcon name="file" /> 专业诊断工作台</h2>
    <p class="hint">本轮还没有结构化结果。</p>
  </section>

  <section v-else class="card pro-page">
    <header class="pro-header">
      <div class="pro-task">
        <span class="eyebrow">当前任务</span>
        <h2>{{ model.taskQuestion ?? '（本轮目标消息不可用）' }}</h2>
        <div class="pro-meta">
          <span class="chip"><UiIcon name="layers" /> {{ model.selectedAssetId ?? '未关联设备' }}</span>
          <span class="chip">状态 {{ statusLabel }}</span>
          <span class="chip">风险 {{ model.riskLevel }}</span>
          <span class="chip">{{ model.dataNature }}</span>
          <span v-if="skill" class="chip">领域包 {{ skill.skillId }} v{{ skill.version }}</span>
        </div>
      </div>
    </header>

    <p v-if="model.safetyNotice" class="safety" role="note">{{ model.safetyNotice }}</p>
    <p v-if="model.dataNatureNote" class="hint">{{ model.dataNatureNote }}</p>

    <div class="pro-grid">
      <ClaimList :title="model.claimsTitle" :claims="model.claims"
                 :selected-index="selection.selectedIndex.value" @select="selection.select" />

      <div class="pro-detail">
        <div class="conclusion">
          <UiIcon name="target" />
          <div>
            <h3>本轮摘要</h3>
            <p class="summary">{{ model.summary }}</p>
          </div>
        </div>
        <ClaimDetail :claim="selectedClaim" @toggle-reference="toggleReference" />
        <div v-for="field in runRoundFields" :key="field.title" class="round-field">
          <h4><UiIcon :name="field.icon" /> {{ field.title }}（本轮）</h4>
          <p v-if="!field.items.length" class="hint">无</p>
          <ul v-else>
            <li v-for="(item, index) in field.items" :key="index">{{ item }}</li>
          </ul>
        </div>
        <p class="hint">{{ model.roundFieldsNote }}</p>
        <div v-if="model.references.length" class="round-field">
          <h4><UiIcon name="link" /> 本轮已取回资料（{{ model.references.length }}）</h4>
          <ul>
            <li v-for="reference in model.references" :key="reference.referenceId">
              《{{ reference.title }}》 · {{ reference.sourceTypeLabel }} · {{ reference.dataNature }}
              <span v-if="reference.locatingNote"> · {{ reference.locatingNote }}</span>
            </li>
          </ul>
        </div>
      </div>
    </div>

    <footer class="pro-footer">
      <div class="investigate">
        <button type="button" class="primary investigate-button" :disabled="!canInvestigate"
                @click="requestInvestigation">
          <UiIcon name="target" /> {{ busy ? '正在提交…' : '深入调查选中判断' }}
        </button>
        <span v-if="investigation" class="hint">
          <template v-if="investigation.inheritedRefs.length">
            将继承 {{ investigation.inheritedRefs.length }} 条引用：{{ investigation.inheritedRefs.map((ref) => '《' + ref.title + '》').join('、') }}
          </template>
          <template v-else>选中判断暂无已关联引用，仍可直接深入调查</template>
          <template v-if="investigation.omittedCount">；另有 {{ investigation.omittedCount }} 条超出上限已省略</template>
        </span>
        <span v-else class="hint">选中一条判断后可深入调查；普通文字请在下方输入框继续调查。</span>
      </div>
      <span><UiIcon name="chat" /> 现场补充将作为“用户报告”在下方输入框提交并开启新一轮调查，不升级为核验事实。</span>
    </footer>
  </section>

  <EvidenceDrawer :reference="openReference" @close="closeDrawer" />
</template>

<style scoped>
.pro-page { border: 1px solid #dcdffc; border-radius: 12px; padding: 14px 16px; background: #f9f9fe; overflow-wrap: anywhere; }
.pro-page > h2 { font-size: 19px; margin: 0 0 8px; display: flex; align-items: center; gap: 6px; }
.pro-header { padding-bottom: 10px; border-bottom: 1px solid var(--line); }
.eyebrow { font-size: 12px; color: var(--muted); }
.pro-task h2 { margin: 4px 0 8px; font-size: 18px; line-height: 1.4; }
.pro-meta { display: flex; flex-wrap: wrap; gap: 6px; }
.chip { display: inline-flex; align-items: center; gap: 4px; font-size: 12px; border-radius: 7px; padding: 2px 8px; color: var(--accent); background: #eae9ff; }
.safety { background: #fff2dc; color: #783f13; border-radius: 7px; padding: 7px 9px; margin: 10px 0 0; font-size: 13px; }
.pro-grid { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); gap: 14px; margin-top: 12px; }
@media (max-width: 860px) { .pro-grid { grid-template-columns: minmax(0, 1fr); } }
.pro-detail h4 { font-size: 13px; margin: 0 0 6px; display: flex; align-items: center; gap: 4px; color: var(--accent); }
.conclusion { display: flex; align-items: flex-start; gap: 10px; background: #eeedfc; border-radius: 8px; padding: 9px 12px; }
.conclusion > svg { color: var(--primary); font-size: 20px; margin-top: 2px; }
.conclusion h3 { color: var(--accent); font-size: 13px; margin: 0 0 3px; }
.summary { font-size: 15px; font-weight: 700; margin: 0; line-height: 1.48; }
.round-field { border-top: 1px solid var(--line); padding: 8px 2px 0; margin-top: 8px; }
.round-field ul { margin: 0; padding-left: 18px; line-height: 1.5; font-size: 13px; }
.pro-footer { border-top: 1px solid var(--line); margin-top: 12px; padding-top: 8px; font-size: 12px; color: var(--muted); display: flex; flex-direction: column; gap: 6px; }
.investigate { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; }
.investigate-button { display: inline-flex; align-items: center; gap: 5px; }
.investigate .hint { flex: 1 1 260px; }
.failure-evidence { margin: 10px 0; border: 1px solid var(--line); border-radius: 8px; padding: 9px 11px; background: #fff; }
.failure-evidence h3 { font-size: 13px; margin: 0 0 6px; display: flex; align-items: center; gap: 4px; color: var(--accent); }
.citation-ref { cursor: pointer; display: block; border: 0; padding: 3px 0; background: none; color: var(--accent); font-weight: 650; text-align: left; }
.citation-ref small { display: block; font-weight: 400; color: var(--muted); }
.citation-ref:hover { text-decoration: underline; }
</style>
