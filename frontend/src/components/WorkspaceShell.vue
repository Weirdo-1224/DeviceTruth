<script setup lang="ts">
import { ref } from 'vue'
import RecentInvestigations from './RecentInvestigations.vue'
import type { AccountView, WorkspaceView } from '../api'

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
const roleLabels = { ADMIN: '管理员', COLLABORATOR: '协作成员', VIEWER: '只读成员' }
const navigation: {id: WorkspacePage; label: string; icon: string; team?: boolean}[] = [
  { id: 'investigation', label: '调查工作台', icon: '▦' },
  { id: 'devices', label: '设备档案', icon: '□' },
  { id: 'knowledge', label: '知识库', icon: '▤' },
  { id: 'members', label: '团队成员', icon: '◎', team: true }
]
function selectSpace(event: Event): void {
  emit('selectSpace', (event.target as HTMLSelectElement).value)
}
</script>

<template>
  <div class="dt-shell">
    <header class="dt-topbar">
      <div class="dt-brand"><span class="dt-logo" aria-hidden="true">D</span><strong>DeviceTruth</strong><span>检修工作台</span></div>
      <div class="dt-user">
        <span class="dt-avatar dt-me" aria-hidden="true">{{ account.displayName.slice(0, 1) }}</span>
        <span>{{ account.displayName }}</span>
        <span class="dt-tag">{{ space.kind === 'PERSONAL' ? '个人账号' : roleLabels[space.role] }}</span>
        <button type="button" class="dt-link" @click="emit('logout')">退出</button>
      </div>
    </header>
    <div v-if="globalError || notice" class="dt-global-feedback">
      <p v-if="globalError" role="alert">{{ globalError }}</p><p v-if="notice" role="status">{{ notice }}</p>
    </div>
    <div class="dt-body">
      <aside class="dt-sidebar" aria-label="工作空间导航">
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
              <span aria-hidden="true">{{ item.icon }}</span>{{ item.label }}
            </button>
          </template>
        </nav>
        <details class="dt-recents" :open="!compact || recentsOpen">
          <summary @click.prevent="compact && (recentsOpen = !recentsOpen)">近期调查 <span>{{ items.length }}</span></summary>
          <RecentInvestigations :show-create="false" :read-only="readOnly" :items="items" :current-id="currentId" :busy="busy"
            :history-loaded="historyLoaded" :history-error="historyError" :query="query" :status="status"
            :next-cursor="nextCursor" :loading-more="loadingMore" @create="emit('create')" @retry="emit('retry')"
            @select="emit('select', $event)" @search="emit('search', $event)" @filter="emit('filter', $event)" @load-more="emit('loadMore')" />
        </details>
        <footer class="dt-side-footer">
          <button type="button" class="dt-link" @click="emit('navigate', 'create-team')">＋ 创建团队</button>
          <small>{{ space.kind === 'TEAM' ? '本空间的调查对团队成员可见' : '个人调查仅自己可见' }}</small>
        </footer>
      </aside>
      <div class="dt-work"><slot /></div>
    </div>
  </div>
</template>

<style scoped>
.dt-shell { min-height: 100dvh; color: var(--text); background: var(--bg); font: 14px/1.65 "Segoe UI", "Microsoft YaHei", sans-serif; }
.dt-topbar { min-height: 64px; padding: 12px 22px; display: flex; align-items: center; justify-content: space-between; gap: 15px; background: var(--panel); border-bottom: 1px solid var(--line); }
.dt-brand, .dt-user { display: flex; align-items: center; gap: 9px; flex-wrap: wrap; }
.dt-brand strong { font-size: 18px; letter-spacing: -.4px; }
.dt-brand > span:last-child { padding-left: 12px; margin-left: 4px; border-left: 1px solid var(--line); color: var(--text-3); font-size: 12px; }
.dt-logo { display: grid; place-items: center; width: 30px; height: 30px; border-radius: 6px; background: #203047; color: white; font-weight: 600; }
.dt-user { font-size: 12px; }
.dt-avatar { display: inline-grid; place-items: center; flex: none; width: 28px; height: 28px; border-radius: 50%; background: #e8edf4; color: #4b5c73; font-size: 12px; }
.dt-me { background: var(--soft); color: var(--accent); }
.dt-tag { display: inline-flex; padding: 2px 7px; border-radius: 4px; background: var(--bg); color: var(--text-2); font-size: 11px; }
.dt-link { border: 0; padding: 2px 0; background: none; color: var(--accent); cursor: pointer; font: inherit; font-size: 12px; text-align: left; }
.dt-link:hover { text-decoration: underline; }
.dt-global-feedback { padding: 8px 22px; background: var(--soft); font-size: 12px; }
.dt-global-feedback p { margin: 0; }
.dt-global-feedback [role=alert] { color: var(--danger); }
.dt-body { display: grid; grid-template-columns: 204px minmax(0, 1fr); min-height: calc(100dvh - 64px); }
.dt-sidebar { min-width: 0; padding: 17px 14px; display: flex; flex-direction: column; gap: 16px; background: #f9fafc; border-right: 1px solid var(--line); }
.dt-side-label { display: block; margin-bottom: 5px; color: var(--text-3); font-size: 11px; }
.dt-space-select { width: 100%; padding: 8px 9px; border: 1px solid #cbd4df; border-radius: 5px; background: var(--panel); color: var(--text); font-size: 13px; }
.dt-button { min-height: 35px; padding: 8px 13px; border: 1px solid #d3dbe6; border-radius: 5px; background: var(--panel); color: var(--text); cursor: pointer; font: inherit; font-size: 13px; }
.dt-primary { border-color: #245cc5; background: #245cc5; color: white; }
.dt-new { width: 100%; }
.dt-button:disabled { opacity: .48; cursor: not-allowed; }
.dt-nav { display: flex; flex-direction: column; gap: 3px; }
.dt-nav button { display: flex; align-items: center; gap: 9px; padding: 9px 11px; border: 0; border-radius: 5px; background: none; color: #536176; cursor: pointer; font: inherit; font-size: 13px; text-align: left; }
.dt-nav button[aria-current=page] { background: var(--soft); color: var(--accent); font-weight: 600; }
.dt-nav button > span { width: 16px; text-align: center; }
.dt-recents { min-height: 0; }
.dt-recents > summary { display: flex; justify-content: space-between; cursor: pointer; color: var(--text-3); font-size: 12px; }
.dt-side-footer { margin-top: auto; padding: 14px 5px 0; display: flex; flex-direction: column; gap: 9px; border-top: 1px solid var(--line); }
.dt-side-footer small { color: var(--text-3); }
.dt-work { min-width: 0; background: var(--bg); }
.dt-recents :deep(.pro-sidebar) { display: flex; padding: 0; border: 0; border-radius: 0; background: transparent; }
.dt-recents :deep(.side-label) { display: none; }
.dt-recents :deep(.side-note) { display: none; }
.dt-recents :deep(.history-list) { max-height: min(38vh, 360px); }
@media (max-width: 880px) {
  .dt-body { grid-template-columns: 180px minmax(0, 1fr); }
  .dt-topbar { padding-inline: 16px; }
}
@media (max-width: 600px) {
  .dt-topbar { padding: 12px 16px; align-items: flex-start; }
  .dt-brand > span:last-child { display: none; }
  .dt-user { justify-content: flex-end; }
  .dt-body { grid-template-columns: minmax(0, 1fr); }
  .dt-sidebar { padding: 13px 16px; gap: 12px; border-right: 0; border-bottom: 1px solid var(--line); }
  .dt-nav { flex-direction: row; flex-wrap: wrap; }
  .dt-nav button { padding: 6px 8px; font-size: 12px; }
  .dt-new { width: auto; }
  .dt-recents > summary { display: flex; justify-content: space-between; cursor: pointer; color: var(--text-3); font-size: 12px; }
  .dt-side-footer { margin-top: 0; padding-top: 8px; }
  .dt-side-footer small { display: none; }
}
@media print { .dt-topbar, .dt-sidebar, .dt-global-feedback { display: none !important; } .dt-body { display: block; } }
</style>
