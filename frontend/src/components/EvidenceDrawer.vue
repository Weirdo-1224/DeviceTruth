<script setup lang="ts">
import UiIcon from './UiIcon.vue'
import type { ReferenceView } from '../diagnosisViewModel'

/**
 * 证据详情栏：展示某条已登记引用的真实来源字段（title/sourceType/sourceId/版本/定位/摘录/dataNature）。
 * 三栏布局中的右栏常态面板；缺页码不伪造；用户自述不是引用、不会出现在这里；
 * 关闭只清空当前引用，不改变判断选中状态。
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
  <aside class="evidence-drawer" role="complementary" aria-label="证据详情">
    <template v-if="reference">
      <header class="drawer-head">
        <h3><UiIcon name="file" /> 证据详情</h3>
        <button type="button" class="drawer-close" aria-label="关闭证据抽屉" @click="emit('close')"><UiIcon name="close" /></button>
      </header>
      <h4 class="drawer-title">《{{ reference.title }}》</h4>
      <dl class="drawer-rows">
        <div v-for="[label, value] in rows(reference)" :key="label">
          <dt>{{ label }}</dt>
          <dd>{{ value }}</dd>
        </div>
      </dl>
      <section class="drawer-excerpt">
        <h4>关键内容摘录</h4>
        <p>{{ reference.excerpt || '（该引用未提供摘录）' }}</p>
      </section>
      <p class="drawer-hint">AUTHORIZED 仅说明来源已授权；FIELD_OBSERVATION 不证明接入真机或现场已核实。</p>
    </template>
    <p v-else class="drawer-empty hint">
      <UiIcon name="file" /> 在左侧判断或“调查依据”中选择一条引用，这里显示其来源、定位与摘录。
    </p>
  </aside>
</template>

<style scoped>
.evidence-drawer {
  height: 100%; min-height: 0; overflow-y: auto;
  background: var(--panel, #121c2e); border: 1px solid var(--line-soft, #1d2b44);
  border-radius: 12px; padding: 14px 16px;
  color: var(--text, #e7eefb);
}
.drawer-head { display: flex; align-items: center; justify-content: space-between; }
.drawer-head h3 { margin: 0; font-size: 14px; display: flex; align-items: center; gap: 6px; color: var(--accent-2, #7dd3fc); }
.drawer-close { cursor: pointer; border: 1px solid var(--line, #24344f); background: var(--panel-2, #16233a);
  color: var(--text-2, #9fb0c9); border-radius: 8px; padding: 4px 8px; }
.drawer-close:hover { color: var(--accent-2, #7dd3fc); border-color: var(--primary, #22b8dd); }
.drawer-title { margin: 12px 0; font-size: 16px; line-height: 1.4; }
.drawer-rows { margin: 0; display: grid; gap: 7px; }
.drawer-rows div { display: grid; grid-template-columns: 84px minmax(0, 1fr); gap: 8px; font-size: 12px; }
.drawer-rows dt { color: var(--text-3, #64748f); }
.drawer-rows dd { margin: 0; overflow-wrap: anywhere; color: var(--text-2, #9fb0c9); }
.drawer-excerpt { margin-top: 14px; border-top: 1px solid var(--line-soft, #1d2b44); padding-top: 10px; }
.drawer-excerpt h4 { margin: 0 0 6px; font-size: 13px; color: var(--accent-2, #7dd3fc); }
.drawer-excerpt p { margin: 0; font-size: 13px; line-height: 1.65; background: var(--bg-deep, #080d18);
  border: 1px solid var(--line-soft, #1d2b44); border-radius: 9px; padding: 10px 12px;
  border-left: 3px solid var(--primary, #22b8dd); }
.drawer-hint { margin-top: 12px; font-size: 11px; color: var(--text-3, #64748f); }
.drawer-empty { display: flex; align-items: center; gap: 8px; margin: 8px 0; line-height: 1.6; }
</style>
