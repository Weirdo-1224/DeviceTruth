<script setup lang="ts">
import { nextTick, onUnmounted, ref, watch } from 'vue'
import UiIcon from './UiIcon.vue'
import type { ReferenceView } from '../diagnosisViewModel'

/**
 * 证据详情模态抽屉：三种终态共用，使用浏览器原生 dialog.showModal() 取得模态焦点与 Esc 能力。
 * 只展示服务端已获准返回的字段；缺页码/章节显示未知，不补写有效区间或现场核验。
 * 打开时焦点进入对话框，关闭后回到触发项；触发项已随切轮消失时由页面指定回退目标。
 */
const props = defineProps<{
  open: boolean
  reference: ReferenceView | null
  /** 打开该引用的来源标识；关闭时页面据此恢复焦点。 */
  originKey: string | null
}>()

const emit = defineEmits<{ close: [originKey: string | null] }>()

const dialog = ref<HTMLDialogElement | null>(null)
const closeButton = ref<HTMLButtonElement | null>(null)
const nativeModal = ref(false)
let lastOrigin: string | null = null

const rows = (reference: ReferenceView) => [
  ['来源类型', reference.sourceTypeLabel + '（' + reference.sourceType + '）'],
  ['来源 ID', reference.sourceId],
  ['来源版本', reference.sourceVersion],
  ['切片', reference.chunkId],
  ['定位', reference.locatingNote ?? '未提供页码或章节'],
  ['数据性质', reference.dataNature]
] as const

function openNative(): void {
  const element = dialog.value
  if (!element) return
  if (typeof element.showModal === 'function' && !element.open) {
    element.showModal()
    nativeModal.value = true
  }
}

function closeNative(): void {
  const element = dialog.value
  if (element && nativeModal.value && element.open) {
    nativeModal.value = false
    element.close()
  }
}

watch(
  () => [props.open, props.reference?.referenceId ?? ''] as const,
  async ([open], previous) => {
    if (open) {
      if (!previous?.[0]) lastOrigin = props.originKey
      await nextTick()
      openNative()
      closeButton.value?.focus()
    } else {
      closeNative()
    }
  },
  { immediate: true }
)

function requestClose(): void {
  emit('close', lastOrigin)
}

/**
 * Tab / Shift+Tab 在首尾元素之间循环：浏览器对模态的焦点陷阱实现不完全一致
 * （首个元素 Shift+Tab 可能落到 body），这里显式拦截并回到可操作元素。
 */
function onKeydown(event: KeyboardEvent): void {
  if (event.key !== 'Tab') return
  const element = dialog.value
  if (!element) return
  const focusable = [...element.querySelectorAll<HTMLElement>('button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])')]
    .filter((item) => !item.hasAttribute('disabled'))
  if (!focusable.length) return
  const first = focusable[0]
  const last = focusable[focusable.length - 1]
  const active = document.activeElement as HTMLElement | null
  if (event.shiftKey && (active === first || !active || !element.contains(active))) {
    event.preventDefault()
    last.focus()
  } else if (!event.shiftKey && active === last) {
    event.preventDefault()
    first.focus()
  }
}

function onDialogCancel(event: Event): void {
  // 原生 Esc：阻止默认直接关闭，统一走页面状态，确保触发项焦点恢复
  event.preventDefault()
  requestClose()
}

onUnmounted(closeNative)
</script>

<template>
  <dialog ref="dialog" class="evidence-modal" aria-label="证据详情" @cancel="onDialogCancel"
          @keydown="onKeydown" @close="nativeModal = false">
    <div v-if="reference" class="modal-body">
      <header class="modal-head">
        <h3><UiIcon name="file" /> 证据详情</h3>
        <button ref="closeButton" type="button" class="drawer-close" aria-label="关闭证据抽屉" @click="requestClose">
          <UiIcon name="close" />
        </button>
      </header>
      <h4 class="modal-title">《{{ reference.title }}》</h4>
      <section class="modal-excerpt">
        <h4>关键内容摘录</h4>
        <p>{{ reference.excerpt || '（该引用未提供摘录）' }}</p>
      </section>
      <details class="modal-metadata"><summary>来源版本、定位与标识</summary>
      <dl class="modal-rows">
        <div v-for="[label, value] in rows(reference)" :key="label">
          <dt>{{ label }}</dt>
          <dd>{{ value }}</dd>
        </div>
      </dl>
      </details>
      <footer class="modal-foot">
        <button type="button" class="secondary" @click="requestClose">关闭</button>
      </footer>
      <p class="modal-hint">AUTHORIZED 仅说明来源已授权；FIELD_OBSERVATION 不证明接入真机或现场已核实。</p>
    </div>
    <p v-else class="modal-empty hint">所选来源当前不可用。</p>
  </dialog>
</template>

<style scoped>
.evidence-modal { border: 1px solid var(--line); border-radius: 16px; padding: 0; width: min(620px, 94vw);
  max-height: 86vh; overflow: auto; background: var(--panel); color: var(--text); box-shadow: var(--shadow); }
.evidence-modal::backdrop { background: rgba(20, 24, 50, 0.38); }
.modal-body { padding: 16px 18px; }
.modal-head { display: flex; align-items: center; justify-content: space-between; }
.modal-head h3 { margin: 0; font-size: 18px; display: flex; align-items: center; gap: 6px; color: var(--accent-2); }
.drawer-close { cursor: pointer; border: 1px solid var(--line); background: var(--panel-2);
  color: var(--text-2); border-radius: 8px; padding: 4px 8px; }
.drawer-close:hover { color: var(--accent-2); border-color: var(--primary); }
.modal-title { margin: 16px 0; font-size: 18px; line-height: 1.4; overflow-wrap: anywhere; }
.modal-rows { margin: 0; display: grid; gap: 7px; }
.modal-rows div { display: grid; grid-template-columns: 84px minmax(0, 1fr); gap: 8px; font-size: 14px; }
.modal-rows dt { color: var(--text-3); }
.modal-rows dd { margin: 0; overflow-wrap: anywhere; color: var(--text-2); }
.modal-excerpt { margin-top: 14px; border-top: 1px solid var(--line-soft); padding-top: 10px; }
.modal-excerpt h4 { margin: 0 0 6px; font-size: 15px; color: var(--accent-2); }
.modal-excerpt p { margin: 0; font-size: 16px; line-height: 1.65; background: var(--log-bg);
  border: 1px solid var(--line-soft); border-radius: 9px; padding: 10px 12px; border-left: 3px solid var(--primary); }
.modal-foot { margin-top: 14px; display: flex; justify-content: flex-end; }
.modal-foot .secondary { border: 1px solid var(--line); border-radius: 9px; background: var(--panel-2);
  color: var(--text-2); padding: 7px 14px; font-weight: 600; cursor: pointer; }
.modal-hint { margin-top: 12px; font-size: 14px; color: var(--text-3); }
.modal-metadata { margin-top: 16px; font-size: 15px; }
.modal-metadata summary { cursor: pointer; margin-bottom: 12px; color: var(--text-2); }
</style>