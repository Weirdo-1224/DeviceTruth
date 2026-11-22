<script setup lang="ts">
import UiIcon from './UiIcon.vue'
import type { ClaimView } from '../diagnosisViewModel'

/**
 * 调查方向导航：只展示确定性短预览与类别（certaintyLabel），按 claims 原顺序排列。
 * 点击只发出 select 事件切换查看焦点，不发起任何请求；原文保留在中央焦点与完整结果。
 */
defineProps<{
  title: string | null
  claims: ClaimView[]
  selectedIndex: number | null
  referenceCounts?: ReadonlyMap<string, number>
}>()

const emit = defineEmits<{ (event: 'select', index: number): void }>()

function referenceCount(claim: ClaimView, counts?: ReadonlyMap<string, number>): number {
  if (!counts) return claim.references.length
  return new Set(claim.referenceIds).size
}
</script>

<template>
  <div class="claim-list-box">
    <h3><UiIcon name="target" /> {{ title }}（{{ claims.length }}）</h3>
    <p v-if="!claims.length" class="hint">
      本轮没有结构化判断；详情区仍显示摘要、风险与本轮建议，可在底部输入框以普通文字继续调查。
    </p>
    <ul v-else class="claim-list">
      <li v-for="claim in claims" :key="claim.key" :class="{ active: claim.index === selectedIndex }">
        <button type="button" class="claim-select" :aria-pressed="claim.index === selectedIndex"
                :title="claim.text" @click="emit('select', claim.index)">
          <span class="claim-head">
            <span class="certainty" :class="{ possible: claim.certainty === 'POSSIBLE' }">{{ claim.certaintyLabel }}</span>
            <span class="claim-index">{{ String(claim.index + 1).padStart(2, '0') }}</span>
          </span>
          <span class="claim-preview">{{ claim.preview }}</span>
          <span class="claim-refs-hint">
            <template v-if="referenceCount(claim, referenceCounts)">{{ referenceCount(claim, referenceCounts) }} 条引用</template>
            <template v-else>{{ claim.referenceNote }}</template>
          </span>
        </button>
      </li>
    </ul>
  </div>
</template>

<style scoped>
.claim-list-box + .claim-list-box { margin-top: 22px; }
.claim-list-box h3 { display: flex; align-items: center; gap: 5px; font-size: 14px; font-weight: 500; color: var(--text-3); margin: 0 0 10px; }
.claim-list { list-style: none; margin: 0; padding: 0; display: grid; gap: 6px; }
.claim-select { width: 100%; cursor: pointer; text-align: left; border: 1px solid transparent; border-radius: 8px; padding: 12px; background: transparent; display: grid; gap: 7px; color: var(--text); }
.claim-select:hover { background: var(--soft); }
li.active .claim-select { border-color: #d6e2ff; background: var(--soft-selected); color: var(--accent); }
.claim-head { display: flex; align-items: center; gap: 8px; }
.claim-index { order: -1; display: grid; place-items: center; width: 23px; height: 23px; font-size: 14px; border-radius: 6px; background: #e7ecf5; color: var(--text-2); }
li.active .claim-index { background: var(--accent); color: white; }
.certainty { font-size: 14px; color: var(--text-3); }
.certainty.possible { color: var(--warn); }
.claim-preview { line-height: 1.6; font-size: 16px; font-weight: 550; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; overflow-wrap: anywhere; }
.claim-refs-hint { font-size: 14px; color: var(--text-3); }
</style>
