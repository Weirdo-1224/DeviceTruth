<script setup lang="ts">
import UiIcon from './UiIcon.vue'
import type { ClaimView } from '../diagnosisViewModel'

/**
 * 判断详情：中央焦点只展示选中判断原文、确定性语义与真实关联引用（调查依据）。
 * 引用卡片只发出 toggle-reference 打开证据模态抽屉，不发起新的调查请求；
 * 无引用时显示说明，不创建虚构来源。未选中时提示完整结果与来源仍可查看。
 */
defineProps<{
  claim: ClaimView | null
}>()

const emit = defineEmits<{ (event: 'toggle-reference', referenceId: string): void }>()
</script>

<template>
  <div class="claim-detail">
    <template v-if="claim">
      <header class="detail-head">
        <h3><UiIcon name="file" /> 判断详情</h3>
        <span class="certainty" :class="{ possible: claim.certainty === 'POSSIBLE' }">{{ claim.certaintyLabel }}</span>
      </header>
      <p class="detail-text">{{ claim.text }}</p>
      <p v-if="claim.missingReferenceIds.length" class="hint">（部分引用暂不可用）</p>

      <div v-if="claim.references.length" class="detail-refs">
        <h4><UiIcon name="link" /> 调查依据（{{ claim.references.length }}）</h4>
        <div class="evidence-grid">
        <button v-for="reference in claim.references" :key="reference.referenceId" type="button"
                class="citation-ref evidence-card" :data-evidence-id="reference.referenceId"
                @click="emit('toggle-reference', reference.referenceId)">
          <span class="evidence-icon"><UiIcon name="file" /></span>
          <span class="evidence-main">
            <strong>《{{ reference.title }}》</strong>
            <small>{{ reference.sourceTypeLabel }} · 版本 {{ reference.sourceVersion }}
              <template v-if="reference.locatingNote"> · {{ reference.locatingNote }}</template>
              · {{ reference.dataNature }}</small>
            <span v-if="reference.excerpt" class="evidence-excerpt">{{ reference.excerpt }}</span>
          </span>
          <UiIcon name="chevron" class="evidence-open" />
        </button>
        </div>
      </div>
      <p v-else class="hint">{{ claim.referenceNote }}</p>
    </template>
    <p v-else class="hint">本轮没有默认候选焦点；请查看上方结论、下一步或下方完整结果。</p>
  </div>
</template>

<style scoped>
.claim-detail { min-width: 0; }
.detail-head { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
.claim-detail h3 { font-size: 13px; margin: 0; display: flex; align-items: center; gap: 5px;
  color: var(--accent-2); }
.detail-text { margin: 8px 0; line-height: 1.5; font-size: 18px; font-weight: 730; color: var(--text); white-space: pre-wrap; }
.certainty { font-size: 11px; border-radius: 999px; padding: 2px 9px; font-weight: 700;
  color: var(--accent-2); background: var(--soft);
  border: 1px solid var(--line); white-space: nowrap; }
.certainty.possible { color: var(--warn); background: var(--warn-bg);
  border-color: rgba(245, 181, 63, 0.4); }
.detail-refs { margin-top: 12px; border-top: 1px solid var(--line-soft); padding-top: 10px; }
.detail-refs h4 { font-size: 12px; margin: 0 0 8px; display: flex; align-items: center; gap: 5px;
  color: var(--accent-2); }
.evidence-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(min(215px, 100%), 1fr)); gap: 9px; }
.citation-ref { cursor: pointer; display: flex; align-items: flex-start; gap: 10px; width: 100%;
  border: 1px solid var(--line-soft); border-radius: 10px; padding: 10px 12px;
  background: var(--panel-2); color: var(--text); text-align: left; transition: border-color 0.15s; }
.citation-ref:hover { border-color: var(--primary); }
.evidence-icon { flex: none; width: 34px; height: 34px; border-radius: 8px; display: grid; place-items: center;
  color: var(--accent); background: var(--soft); border: 1px solid var(--line); font-size: 16px; }
.evidence-main { flex: 1; min-width: 0; display: grid; gap: 3px; }
.evidence-main strong { font-size: 13px; }
.evidence-main small { color: var(--text-3); font-size: 11px; }
.evidence-excerpt { font-size: 12px; color: var(--text-2); line-height: 1.55;
  border-left: 2px solid var(--line); padding-left: 8px;
  display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
.evidence-open { flex: none; align-self: center; color: var(--text-3); }
</style>
