<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import UiIcon from './UiIcon.vue'
import RecentInvestigations from './RecentInvestigations.vue'
import type { AccountView, WorkspaceView } from '../api'
import { clampSidebarWidth, loadSidebarWidth, saveSidebarWidth, sidebarMaximum, SIDEBAR_DEFAULT, SIDEBAR_MIN, SIDEBAR_MOBILE_BREAKPOINT } from '../sidebarSizing'

export type WorkspacePage = 'investigation' | 'devices' | 'knowledge' | 'members' | 'create-team'
interface HistoryItem { conversationId: string; title: string; assetId: string | null; runCount: number; time: string; badge: string }
const props = defineProps<{
  account: AccountView
  space: WorkspaceView
  spaces: WorkspaceView[]
  page: WorkspacePage
  currentId: string
  readOnly: boolean
  busy: boolean
  items: HistoryItem[]
  historyLoaded: boolean
  historyError: string
  query: string
  status: string
  nextCursor: string | null
  loadingMore: boolean
  compact: boolean
  globalError?: string
  notice?: string
}>()
const emit = defineEmits<{
  navigate: [page: WorkspacePage]
  selectSpace: [workspaceId: string]
  logout: []
  create: []
  select: [conversationId: string]
  retry: []
  search: [query: string]
  filter: [status: string]
  loadMore: []
}>()
const recentsOpen = ref(false)
const preferredWidth = ref(SIDEBAR_DEFAULT)
const viewportWidth = ref(1280)
const sidebarWidth = computed(() => clampSidebarWidth(preferredWidth.value, viewportWidth.value))
const maximumWidth = computed(() => sidebarMaximum(viewportWidth.value))
const mobile = computed(() => viewportWidth.value <= SIDEBAR_MOBILE_BREAKPOINT)
const recentExpanded = computed(() => !mobile.value || recentsOpen.value)
const resizing = ref(false)
let drag: { id: number; x: number; width: number; previous: number; handle: HTMLElement } | null = null

function stopResize(cancelled = false): void {
  if (!drag) return
  const current = drag
  drag = null
  resizing.value = false
  if (cancelled) preferredWidth.value = current.previous
  else saveSidebarWidth(props.account.userId, preferredWidth.value)
  if (current.handle.hasPointerCapture(current.id)) current.handle.releasePointerCapture(current.id)
}
function startResize(event: PointerEvent): void {
  if (mobile.value || event.button !== 0 || drag) return
  const handle = event.currentTarget as HTMLElement
  handle.focus({ preventScroll: true })
  handle.setPointerCapture(event.pointerId)
  drag = { id: event.pointerId, x: event.clientX, width: sidebarWidth.value, previous: preferredWidth.value, handle }
  resizing.value = true
  event.preventDefault()
}
function moveResize(event: PointerEvent): void {
  if (!drag || drag.id !== event.pointerId) return
  preferredWidth.value = clampSidebarWidth(drag.width + event.clientX - drag.x, viewportWidth.value)
}
function endResize(event: PointerEvent, cancelled = false): void {
  if (drag?.id === event.pointerId) stopResize(cancelled)
}
function resetWidth(): void {
  stopResize(true)
  preferredWidth.value = SIDEBAR_DEFAULT
  saveSidebarWidth(props.account.userId, preferredWidth.value)
}
function resizeWithKeyboard(event: KeyboardEvent): void {
  if (mobile.value) return
  if (event.key === 'Escape' && drag) { event.preventDefault(); stopResize(true); return }
  if (event.key === 'Enter') { event.preventDefault(); resetWidth(); return }
  const next = event.key === 'ArrowLeft' ? sidebarWidth.value - 16
    : event.key === 'ArrowRight' ? sidebarWidth.value + 16
    : event.key === 'Home' ? SIDEBAR_MIN : event.key === 'End' ? maximumWidth.value : null
  if (next === null) return
  event.preventDefault()
  preferredWidth.value = clampSidebarWidth(next, viewportWidth.value)
  saveSidebarWidth(props.account.userId, preferredWidth.value)
}
function updateViewport(): void {
  stopResize(true)
  viewportWidth.value = window.innerWidth
}
onMounted(() => {
  preferredWidth.value = loadSidebarWidth(props.account.userId)
  updateViewport()
  window.addEventListener('resize', updateViewport)
})
onUnmounted(() => { stopResize(true); window.removeEventListener('resize', updateViewport) })
const roleLabels = { ADMIN: '管理员', COLLABORATOR: '协作成员', VIEWER: '只读成员' }
const navigation: {id: WorkspacePage; label: string; icon: string; team?: boolean}[] = [
  { id: 'investigation', label: '调查工作台', icon: 'activity' },
  { id: 'devices', label: '设备档案', icon: 'tool' },
  { id: 'knowledge', label: '知识库', icon: 'file' },
  { id: 'members', label: '团队成员', icon: 'users', team: true }
]
function selectSpace(event: Event): void {
  emit('selectSpace', (event.target as HTMLSelectElement).value)
}
</script>

<template>
  <div class="dt-shell" :class="{ 'dt-resizing': resizing }" :style="{ '--sidebar-width': sidebarWidth + 'px' }">
    <div v-if="globalError || notice" class="dt-global-feedback">
      <p v-if="globalError" role="alert">{{ globalError }}</p><p v-if="notice" role="status">{{ notice }}</p>
    </div>
    <div class="dt-body">
      <div class="dt-sidebar-frame">
      <aside class="dt-sidebar" aria-label="工作空间导航">
        <div class="dt-brand"><span class="dt-logo" aria-hidden="true"><UiIcon name="target" /></span><div><strong>DeviceTruth</strong><small>AI 设备检修工作台</small></div></div>

        <div>
          <label class="dt-side-label" for="dt-workspace">当前工作空间</label>
          <select id="dt-workspace" class="dt-space-select" :value="space.workspaceId" @change="selectSpace">
            <option v-for="item in spaces" :key="item.workspaceId" :value="item.workspaceId">{{ item.name }}</option>
          </select>
        </div>
        <button type="button" class="dt-button dt-primary dt-new" :disabled="busy || readOnly" @click="emit('create')">＋ 新建调查</button>
        <nav class="dt-nav" aria-label="空间功能">
          <template v-for="item in navigation" :key="item.id">
            <button v-if="!item.team || space.kind === 'TEAM'" type="button" :aria-current="page === item.id ? 'page' : undefined" @click="emit('navigate', item.id)">
              <UiIcon :name="item.icon" />{{ item.label }}
            </button>
          </template>
        </nav>
        <section class="dt-recents">
          <button type="button" class="dt-recents-toggle" :aria-expanded="recentExpanded" @click="mobile && (recentsOpen = !recentsOpen)">近期调查 <span>{{ items.length }} 条已加载</span></button>
          <div v-show="recentExpanded" class="dt-recents-content">
          <RecentInvestigations :show-create="false" :read-only="readOnly" :items="items" :current-id="currentId" :busy="busy"
            :history-loaded="historyLoaded" :history-error="historyError" :query="query" :status="status"
            :next-cursor="nextCursor" :loading-more="loadingMore" @create="emit('create')" @retry="emit('retry')"
            @select="emit('select', $event)" @search="emit('search', $event)" @filter="emit('filter', $event)" @load-more="emit('loadMore')" />
          </div>
        </section>
        <footer class="dt-side-footer">
          <button type="button" class="dt-link" @click="emit('navigate', 'create-team')">＋ 创建团队</button>
          <a class="dt-link" href="/admin/users">平台管理</a>
          <small>{{ space.kind === 'TEAM' ? '本空间的调查对团队成员可见' : '个人调查仅自己可见' }}</small>
          <div class="dt-user">
            <span class="dt-avatar dt-me" aria-hidden="true">{{ account.displayName.slice(0, 1) }}</span>
            <span class="dt-user-name" :title="account.displayName">{{ account.displayName }}</span>
            <span class="dt-tag">{{ space.kind === 'PERSONAL' ? '个人账号' : roleLabels[space.role] }}</span>
            <button type="button" class="dt-link" @click="emit('logout')">退出</button>
          </div>
        </footer>
      </aside>
      <div class="dt-sidebar-resizer" role="separator" aria-orientation="vertical" aria-label="调整侧栏宽度"
        tabindex="0" :aria-valuemin="SIDEBAR_MIN" :aria-valuemax="maximumWidth" :aria-valuenow="sidebarWidth"
        :aria-valuetext="sidebarWidth + ' 像素'" title="拖动调整宽度；双击或按 Enter 恢复默认；方向键微调，Esc 取消拖动"
        @pointerdown="startResize" @pointermove="moveResize" @pointerup="endResize($event)"
        @pointercancel="endResize($event, true)" @lostpointercapture="endResize($event)"
        @keydown="resizeWithKeyboard" @dblclick="resetWidth" />
      </div>
      <div class="dt-work"><slot /></div>
    </div>
  </div>
</template>

<style scoped>
.dt-shell { min-height: 100dvh; color: var(--text); background: var(--bg); font: inherit; }
.dt-body { display: grid; grid-template-columns: var(--sidebar-width, 224px) minmax(0, 1fr); min-height: 100dvh; }
.dt-sidebar { line-height: 1.5; min-width: 0; padding: 20px 14px 16px; display: flex; flex-direction: column; gap: 14px; background: var(--panel-2); border-right: 1px solid var(--line); }
.dt-brand { display: flex; align-items: center; gap: 10px; margin-bottom: 4px; }
.dt-brand strong { font-size: 20px; letter-spacing: -.6px; }
.dt-brand small { display: block; color: var(--text-3); font-size: 14px; margin-top: 1px; }
.dt-logo { display: grid; place-items: center; flex: none; width: 36px; height: 40px; border-radius: 11px; background: var(--primary); color: white; font-size: 24px; }
.dt-side-label { display: block; margin-bottom: 7px; color: var(--text-3); font-size: 14px; }
.dt-space-select { width: 100%; padding: 9px 10px; border: 1px solid var(--line); border-radius: 8px; background: var(--panel); color: var(--text); font-size: 15px; }
.dt-new { width: 100%; margin-top: -10px; font-weight: 600; }
.dt-nav { display: flex; flex-direction: column; gap: 4px; }
.dt-nav button { display: flex; align-items: center; gap: 10px; padding: 9px 12px; border: 0; border-radius: 8px; background: none; color: var(--text-2); cursor: pointer; font: inherit; text-align: left; }
.dt-nav button:hover { background: var(--soft); }
.dt-nav button[aria-current=page] { background: var(--soft-selected); color: var(--accent); font-weight: 650; }
.dt-nav svg { font-size: 17px; }
.dt-recents { min-height: 0; flex: 1; display: flex; flex-direction: column; gap: 8px; }
.dt-sidebar > .dt-recents { flex-shrink: 1; }
.dt-recents-content { flex: 1; min-height: 0; }
.dt-sidebar > * { flex-shrink: 0; }
.dt-recents-toggle { display: flex; justify-content: space-between; gap: 8px; flex: none; border: 0; padding: 0; background: none; color: var(--text-3); font: inherit; font-size: 14px; text-align: left; cursor: default; }
.dt-side-footer { margin-top: 0; padding-top: 12px; display: flex; flex-direction: column; gap: 10px; border-top: 1px solid var(--line); }
.dt-side-footer small { line-height: 1.5; color: var(--text-3); font-size: 14px; }
.dt-user-name { min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; max-width: 100%; }
.dt-user { display: flex; align-items: center; gap: 7px; flex-wrap: wrap; padding-top: 12px; font-size: 15px; }
.dt-avatar { display: inline-grid; place-items: center; width: 30px; height: 30px; border-radius: 50%; background: var(--soft); color: var(--accent); }
.dt-global-feedback { padding: 10px 24px; background: var(--soft); }
.dt-global-feedback p { margin: 0; }
.dt-global-feedback [role=alert] { color: var(--danger); }
.dt-work { min-width: 0; }
.dt-recents :deep(.pro-sidebar) { display: flex; height: 100%; min-height: 0; overflow: hidden; padding: 0; border: 0; border-radius: 0; background: transparent; }
.dt-recents :deep(.side-label), .dt-recents :deep(.side-note) { display: none; }
.dt-recents :deep(.history-list) { flex: 1; min-height: 0; max-height: none; overflow-y: auto; overscroll-behavior: contain; scrollbar-gutter: stable; }
.dt-recents :deep(.history-controls), .dt-recents :deep(.history-more) { flex-shrink: 0; }
.dt-recents :deep(.side-state) { overflow-y: auto; }
.dt-sidebar-resizer { position: absolute; right: -5px; top: 0; bottom: 0; width: 10px; z-index: 5; cursor: col-resize; touch-action: none; }
.dt-sidebar-resizer::after { content: ''; position: absolute; inset: 0 4px; background: transparent; }
.dt-sidebar-resizer:hover::after, .dt-sidebar-resizer:focus-visible::after, .dt-resizing .dt-sidebar-resizer::after { background: var(--accent); }
.dt-sidebar-resizer:focus-visible { outline: none; }
.dt-sidebar-resizer:focus-visible::after { box-shadow: 0 0 0 2px #245de033; }
.dt-resizing, .dt-resizing :deep(*) { cursor: col-resize !important; user-select: none !important; }
@media (min-width: 761px) { .dt-sidebar-frame { position: sticky; top: 0; height: 100dvh; min-height: 0; } .dt-sidebar { height: 100%; overflow: hidden; } }
@media (max-width: 1100px) { .dt-sidebar { padding-inline: 12px; } .dt-brand strong { font-size: 18px; } }
@media (max-width: 760px) {
  .dt-body { grid-template-columns: minmax(0, 1fr); }
  .dt-sidebar-resizer { display: none; }
  .dt-recents-toggle { cursor: pointer; }
  .dt-recents :deep(.pro-sidebar) { height: auto; overflow: visible; }
  .dt-recents :deep(.history-list) { max-height: 40dvh; }

  .dt-sidebar { padding: 14px 16px; display: grid; grid-template-columns: 1fr 1fr; gap: 12px; border-right: 0; border-bottom: 1px solid var(--line); }
  .dt-brand { grid-column: 1 / -1; } .dt-brand small { display: none; }
  .dt-nav { flex-direction: row; flex-wrap: wrap; grid-column: 1 / -1; gap: 4px; }
  .dt-nav button { padding: 8px 10px; font-size: 15px; }
  .dt-new { margin: 0; align-self: end; }
  .dt-recents, .dt-side-footer { grid-column: 1 / -1; }
  .dt-side-footer { flex-direction: row; flex-wrap: wrap; align-items: center; padding-top: 10px; }
  .dt-side-footer > small { display: none; } .dt-user { margin-left: auto; padding: 0; }
}
@media (min-width: 761px) and (max-height: 720px) { .dt-sidebar { padding-block: 12px; gap: 8px; } .dt-brand small, .dt-side-footer > small { display: none; } .dt-side-footer { gap: 6px; } .dt-user { padding-top: 6px; } }
@media (min-width: 761px) and (max-height: 640px) {
  .dt-sidebar { gap: 6px; }
  .dt-brand { margin-bottom: 0; } .dt-logo { width: 30px; height: 32px; font-size: 22px; }
  .dt-side-label { position: absolute; width: 1px; height: 1px; overflow: hidden; clip-path: inset(50%); }
  .dt-nav button { padding-block: 6px; line-height: 1.5; }
  .dt-user { padding-top: 0; }
}
@media (min-width: 761px) and (max-height: 480px) { .dt-sidebar { overflow-y: auto; } .dt-sidebar > .dt-recents { flex: none; } .dt-recents-content { flex: none; } .dt-recents :deep(.pro-sidebar), .dt-recents :deep(.history-list) { height: auto; overflow: visible; max-height: none; } }
@media print { .dt-sidebar-frame, .dt-sidebar-resizer, .dt-sidebar, .dt-global-feedback { display: none !important; } .dt-body { display: block; } }
</style>
