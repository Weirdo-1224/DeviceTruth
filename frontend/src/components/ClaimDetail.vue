<script setup lang="ts">
import UiIcon from './UiIcon.vue'
import type { ClaimView } from '../diagnosisViewModel'

/**
 * 判断详情：展示选中判断的原文、确定性语义与真实关联引用（调查依据）。
 * 引用卡片只发出 toggle-reference 打开右侧证据详情，不发起新的调查请求；
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
      <header class="detail-head">
        <h3><UiIcon name="file" /> 判断详情</h3>
        <span class="certainty" :class="{ possible: claim.certainty === 'POSSIBLE' }">{{ claim.certaintyLabel }}</span>
      </header>
      <p class="detail-text">{{ claim.text }}</p>
      <p v-if="claim.missingReferenceIds.length" class="hint">（部分引用暂不可用）</p>

      <div v-if="claim.references.length" class="detail-refs">
        <h4><UiIcon name="link" /> 调查依据（{{ claim.references.length }}）</h4>
        <button v-for="reference in claim.references" :key="reference.referenceId" type="button"
                class="citation-ref evidence-card" @click="emit('toggle-reference', reference.referenceId)">
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
      <p v-else class="hint">{{ claim.referenceNote }}</p>
    </template>
    <p v-else class="hint">请在左侧选择一条判断查看原文与关联引用。</p>
  </div>
</template>

<style scoped>
.claim-detail { border: 1px solid var(--line-soft, #1d2b44); border-radius: 12px; padding: 14px 16px;
  background: var(--panel, #121c2e); }
.detail-head { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
.claim-detail h3 { font-size: 13px; margin: 0; display: flex; align-items: center; gap: 5px;
  color: var(--accent-2, #7dd3fc); }
.detail-text { margin: 10px 0 8px; line-height: 1.6; font-size: 14px; color: var(--text, #e7eefb); }
.certainty { font-size: 11px; border-radius: 999px; padding: 2px 9px; font-weight: 700;
  color: var(--accent-2, #7dd3fc); background: var(--soft, rgba(56, 214, 245, 0.1));
  border: 1px solid rgba(56, 214, 245, 0.3); white-space: nowrap; }
.certainty.possible { color: var(--warn, #f5b53f); background: var(--warn-bg, rgba(245, 181, 63, 0.12));
  border-color: rgba(245, 181, 63, 0.4); }
.detail-refs { margin-top: 12px; border-top: 1px solid var(--line-soft, #1d2b44); padding-top: 10px; }
.detail-refs h4 { font-size: 12px; margin: 0 0 8px; display: flex; align-items: center; gap: 5px;
  color: var(--accent-2, #7dd3fc); }
.citation-ref { cursor: pointer; display: flex; align-items: flex-start; gap: 10px; width: 100%;
  border: 1px solid var(--line-soft, #1d2b44); border-radius: 10px; padding: 10px 12px; margin-bottom: 8px;
  background: var(--panel-2, #16233a); color: var(--text, #e7eefb); text-align: left; transition: border-color 0.15s; }
.citation-ref:hover { border-color: var(--primary, #22b8dd); }
.evidence-icon { flex: none; width: 34px; height: 34px; border-radius: 8px; display: grid; place-items: center;
  color: #fda4af; background: rgba(248, 113, 113, 0.12); border: 1px solid rgba(248, 113, 113, 0.25); font-size: 16px; }
.evidence-main { flex: 1; min-width: 0; display: grid; gap: 3px; }
.evidence-main strong { font-size: 13px; }
.evidence-main small { color: var(--text-3, #64748f); font-size: 11px; }
.evidence-excerpt { font-size: 12px; color: var(--text-2, #9fb0c9); line-height: 1.55;
  border-left: 2px solid var(--line, #24344f); padding-left: 8px;
  display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
.evidence-open { flex: none; align-self: center; color: var(--text-3, #64748f); }
</style>
