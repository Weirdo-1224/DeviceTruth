<script setup lang="ts">
import UiIcon from './UiIcon.vue'
import type { ClaimView } from '../diagnosisViewModel'

/**
 * 判断详情：展示选中判断的原文、确定性语义与真实关联引用。
 * 引用按钮只发出 toggle-reference 打开证据抽屉，不发起新的调查请求；
 * 无引用时显示说明，不创建虚构来源。未选中时提示先选择判断。
 */
defineProps<{
  claim: ClaimView | null
}>()

const emit = defineEmits<{ (event: 'toggle-reference', referenceId: string): void }>()
</script>

<template>
  <div class="claim-detail">
    <template v-if="claim">
      <h3><UiIcon name="file" /> 判断详情</h3>
      <p class="detail-text">{{ claim.text }}</p>
      <p class="detail-meta">
        <span class="certainty" :class="{ possible: claim.certainty === 'POSSIBLE' }">{{ claim.certaintyLabel }}</span>
        <span v-if="claim.missingReferenceIds.length" class="hint">（部分引用暂不可用）</span>
      </p>
      <div v-if="claim.references.length" class="detail-refs">
        <h4><UiIcon name="link" /> 已关联引用（{{ claim.references.length }}）</h4>
        <button v-for="reference in claim.references" :key="reference.referenceId" type="button"
                class="citation-ref" @click="emit('toggle-reference', reference.referenceId)">
          《{{ reference.title }}》{{ reference.locatingNote ?? '' }}
          <small>{{ reference.sourceTypeLabel }} · {{ reference.dataNature }}</small>
        </button>
      </div>
      <p v-else class="hint">{{ claim.referenceNote }}</p>
    </template>
    <p v-else class="hint">请在左侧选择一条判断查看原文与关联引用。</p>
  </div>
</template>

<style scoped>
.claim-detail { border: 1px solid var(--line); border-radius: 8px; padding: 10px 12px; background: #fff; }
.claim-detail h3 { font-size: 13px; margin: 0 0 6px; display: flex; align-items: center; gap: 4px; color: var(--accent); }
.detail-text { margin: 0 0 8px; line-height: 1.55; font-size: 14px; }
.detail-meta { margin: 0 0 8px; display: flex; align-items: center; gap: 6px; }
.certainty { font-size: 12px; border-radius: 7px; padding: 2px 7px; color: var(--accent); background: #eae9ff; }
.certainty.possible { color: var(--warning); background: #ffefca; }
.detail-refs h4 { font-size: 12px; margin: 0 0 5px; display: flex; align-items: center; gap: 4px; color: var(--accent); }
.citation-ref { cursor: pointer; display: block; border: 0; padding: 3px 0; background: none; color: var(--accent); font-weight: 650; text-align: left; }
.citation-ref small { display: block; font-weight: 400; color: var(--muted); }
.citation-ref:hover { text-decoration: underline; }
</style>
