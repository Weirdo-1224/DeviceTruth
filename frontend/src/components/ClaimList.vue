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
.claim-list-box h3 { font-size: 13px; margin: 0 0 8px; display: flex; align-items: center; gap: 5px; color: var(--accent-2, #7dd3fc); }
.claim-list { list-style: none; margin: 0; padding: 0; display: grid; gap: 10px; }
.claim-select { width: 100%; cursor: pointer; text-align: left; border: 1px solid var(--line-soft, #1d2b44);
  border-radius: 11px; padding: 11px 12px; background: var(--panel, #121c2e); display: grid; gap: 6px;
  color: var(--text, #e7eefb); transition: border-color 0.15s, box-shadow 0.15s; }
.claim-select:hover { border-color: var(--primary, #22b8dd); }
li.active .claim-select { border-color: var(--primary, #22b8dd);
  box-shadow: 0 0 0 1px var(--primary, #22b8dd), 0 8px 22px rgba(34, 184, 221, 0.16);
  background: linear-gradient(160deg, rgba(56, 214, 245, 0.1), var(--panel, #121c2e) 55%); }
.certainty { justify-self: start; font-size: 11px; border-radius: 999px; padding: 2px 8px; font-weight: 700;
  color: var(--accent-2, #7dd3fc); background: var(--soft, rgba(56, 214, 245, 0.1));
  border: 1px solid rgba(56, 214, 245, 0.3); }
.certainty.possible { color: var(--warn, #f5b53f); background: var(--warn-bg, rgba(245, 181, 63, 0.12));
  border-color: rgba(245, 181, 63, 0.4); }
.claim-text { margin: 0; line-height: 1.55; font-size: 13px; color: var(--text, #e7eefb); }
.claim-refs-hint { font-size: 12px; color: var(--text-3, #64748f); display: flex; align-items: center; gap: 5px; }
</style>
