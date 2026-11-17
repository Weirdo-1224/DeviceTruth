<script setup lang="ts">
import UiIcon from './UiIcon.vue'
import type { ClaimView } from '../diagnosisViewModel'

/**
 * 判断列表：按类别标题展示当前结果的全部判断（ViewModel 已保证语义与顺序）。
 * 点击只发出 select 事件切换查看焦点，不发起任何请求。
 */
defineProps<{
  title: string | null
  claims: ClaimView[]
  selectedIndex: number | null
}>()

const emit = defineEmits<{ (event: 'select', index: number): void }>()
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
                @click="emit('select', claim.index)">
          <span class="certainty" :class="{ possible: claim.certainty === 'POSSIBLE' }">{{ claim.certaintyLabel }}</span>
          <span class="claim-text">{{ claim.text }}</span>
          <span class="claim-refs-hint">
            <template v-if="claim.references.length">{{ claim.references.length }} 条引用</template>
            <template v-else>{{ claim.referenceNote }}</template>
          </span>
        </button>
      </li>
    </ul>
  </div>
</template>

<style scoped>
.claim-list-box h3 { font-size: 13px; margin: 0 0 6px; display: flex; align-items: center; gap: 4px; color: var(--accent); }
.claim-list { list-style: none; margin: 0; padding: 0; display: grid; gap: 8px; }
.claim-select { width: 100%; cursor: pointer; text-align: left; border: 1px solid var(--line); border-radius: 8px; padding: 8px 10px; background: #fff; display: grid; gap: 5px; }
.claim-select:hover { border-color: var(--primary); }
li.active .claim-select { border-color: var(--primary); box-shadow: 0 0 0 1px var(--primary); }
.certainty { justify-self: start; font-size: 12px; border-radius: 7px; padding: 2px 7px; color: var(--accent); background: #eae9ff; }
.certainty.possible { color: var(--warning); background: #ffefca; }
.claim-text { margin: 0; line-height: 1.5; font-size: 14px; }
.claim-refs-hint { font-size: 12px; color: var(--muted); }
</style>
