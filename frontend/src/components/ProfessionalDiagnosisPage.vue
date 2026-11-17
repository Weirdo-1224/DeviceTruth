<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import UiIcon from './UiIcon.vue'
import ClaimList from './ClaimList.vue'
import ClaimDetail from './ClaimDetail.vue'
import EvidenceDrawer from './EvidenceDrawer.vue'
import { buildDiagnosisViewModel } from '../diagnosisViewModel'
import { createClaimSelection } from '../claimSelection'
import type { MessageView, Reference, RunView, SkillSelection } from '../api'

/**
 * 专业诊断工作台单页：顶部任务区 + 左侧判断区 + 中央详情区 + 右侧证据抽屉 + 底部协作状态条。
 * 全部内容来自持久 Run 与已登记引用的确定性投影（diagnosisViewModel），不发起模型调用；
 * 判断选择只切换查看焦点（claimSelection，零请求）；证据抽屉只读展示已登记引用。
 * 没有候选原因时按实际类别安全展示，不伪造候选；普通文字继续调查沿用 App 输入框。
 * 无个人模式切换与关系图入口。
 */
const props = defineProps<{
  run: RunView
  goalMessage?: MessageView | undefined
  references: Reference[]
  skill?: SkillSelection | null
  failureText?: string
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
      <span><UiIcon name="chat" /> 可在下方输入框以普通文字继续调查；执行中或未结束时输入按既有规则保留草稿。</span>
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
.round-field { border-top: 1px solid var(--line); padding: 8px 2px 0; margin-top: 8px; }
.round-field ul { margin: 0; padding-left: 18px; line-height: 1.5; font-size: 13px; }
.pro-footer { border-top: 1px solid var(--line); margin-top: 12px; padding-top: 8px; font-size: 12px; color: var(--muted); display: flex; gap: 6px; align-items: center; }
.failure-evidence { margin: 10px 0; border: 1px solid var(--line); border-radius: 8px; padding: 9px 11px; background: #fff; }
.failure-evidence h3 { font-size: 13px; margin: 0 0 6px; display: flex; align-items: center; gap: 4px; color: var(--accent); }
.citation-ref { cursor: pointer; display: block; border: 0; padding: 3px 0; background: none; color: var(--accent); font-weight: 650; text-align: left; }
.citation-ref small { display: block; font-weight: 400; color: var(--muted); }
.citation-ref:hover { text-decoration: underline; }
</style>
