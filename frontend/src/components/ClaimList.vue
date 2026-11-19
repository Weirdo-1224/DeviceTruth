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
  return claim.referenceIds.reduce((sum, id) => sum + (counts.get(id) ?? 0), 0)
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
            <span class="claim-index">#{{ claim.index + 1 }}</span>
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
.claim-list-box + .claim-list-box { margin-top: 14px; }
.claim-list-box h3 { font-size: 12px; margin: 0 0 8px; display: flex; align-items: center; gap: 5px; color: var(--text-3);
  text-transform: uppercase; letter-spacing: 0.05em; }
.claim-list { list-style: none; margin: 0; padding: 0; display: grid; gap: 8px; }
.claim-select { width: 100%; cursor: pointer; text-align: left; border: 1px solid var(--line-soft);
  border-radius: 11px; padding: 9px 11px; background: var(--panel); display: grid; gap: 5px;
  color: var(--text); transition: border-color 0.15s, box-shadow 0.15s; }
.claim-select:hover { border-color: var(--primary); }
li.active .claim-select { border-color: var(--primary); box-shadow: 0 0 0 1px var(--primary);
  background: var(--soft-selected); }
.claim-head { display: flex; align-items: center; gap: 6px; }
.claim-index { font-size: 11px; color: var(--text-3); }
.certainty { font-size: 11px; border-radius: 999px; padding: 1px 8px; font-weight: 700;
  color: var(--accent-2); background: var(--soft); border: 1px solid var(--line); }
.certainty.possible { color: var(--warn); background: var(--warn-bg); border-color: rgba(245, 181, 63, 0.4); }
.claim-preview { line-height: 1.5; font-size: 13px; color: var(--text); display: -webkit-box;
  -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; overflow-wrap: anywhere; }
.claim-refs-hint { font-size: 11px; color: var(--text-3); }
</style>
