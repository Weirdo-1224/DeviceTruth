<script setup lang="ts">
import UiIcon from './UiIcon.vue'
import type { ReferenceView } from '../diagnosisViewModel'

/**
 * 证据抽屉：展示某条已登记引用的真实来源字段（title/sourceType/sourceId/版本/定位/摘录/dataNature）。
 * 缺页码不伪造；用户自述不是引用、不会出现在这里；关闭只收起抽屉，不改变判断选中状态。
 */
defineProps<{
  reference: ReferenceView | null
}>()

const emit = defineEmits<{ (event: 'close'): void }>()

const rows = (reference: ReferenceView) => [
  ['来源类型', reference.sourceTypeLabel + '（' + reference.sourceType + '）'],
  ['来源 ID', reference.sourceId],
  ['来源版本', reference.sourceVersion],
  ['切片', reference.chunkId],
  ['定位', reference.locatingNote ?? '未提供页码或章节'],
  ['数据性质', reference.dataNature]
] as const
</script>

<template>
  <div v-if="reference" class="drawer-scrim" @click.self="emit('close')">
    <aside class="evidence-drawer" role="dialog" aria-label="证据详情">
      <header class="drawer-head">
        <h3><UiIcon name="file" /> 证据详情</h3>
        <button type="button" class="icon-button" aria-label="关闭证据抽屉" @click="emit('close')"><UiIcon name="close" /></button>
      </header>
      <h4 class="drawer-title">《{{ reference.title }}》</h4>
      <dl class="drawer-rows">
        <div v-for="[label, value] in rows(reference)" :key="label">
          <dt>{{ label }}</dt>
          <dd>{{ value }}</dd>
        </div>
      </dl>
      <section class="drawer-excerpt">
        <h4>摘录</h4>
        <p>{{ reference.excerpt || '（该引用未提供摘录）' }}</p>
      </section>
      <p class="hint">AUTHORIZED 仅说明来源已授权；FIELD_OBSERVATION 不证明接入真机或现场已核实。</p>
    </aside>
  </div>
</template>

<style scoped>
.drawer-scrim { position: fixed; inset: 0; background: rgba(23, 24, 54, 0.32); z-index: 60; display: flex; justify-content: flex-end; }
.evidence-drawer { width: min(420px, 92vw); height: 100%; background: #fff; box-shadow: -8px 0 24px rgba(23, 24, 54, 0.18); padding: 16px 18px; overflow-y: auto; }
.drawer-head { display: flex; align-items: center; justify-content: space-between; }
.drawer-head h3 { margin: 0; font-size: 15px; display: flex; align-items: center; gap: 6px; color: var(--accent); }
.icon-button { cursor: pointer; border: 0; background: #eae9ff; color: var(--accent); border-radius: 8px; padding: 4px 8px; }
.drawer-title { margin: 12px 0; font-size: 16px; line-height: 1.4; }
.drawer-rows { margin: 0; display: grid; gap: 7px; }
.drawer-rows div { display: grid; grid-template-columns: 84px minmax(0, 1fr); gap: 8px; font-size: 13px; }
.drawer-rows dt { color: var(--muted); }
.drawer-rows dd { margin: 0; overflow-wrap: anywhere; }
.drawer-excerpt { margin-top: 14px; border-top: 1px solid var(--line); padding-top: 10px; }
.drawer-excerpt h4 { margin: 0 0 6px; font-size: 13px; color: var(--accent); }
.drawer-excerpt p { margin: 0; font-size: 13px; line-height: 1.6; background: #f6f6fd; border-radius: 8px; padding: 9px 11px; }
.hint { margin-top: 12px; font-size: 12px; color: var(--muted); }
</style>
