<script setup lang="ts">
import UiIcon from './UiIcon.vue'
import { ref, watch } from 'vue'
import { USER_REPORT_NOTE } from '../diagnosisViewModel'

const props = defineProps<{
  items: { conversationId: string; title: string; assetId: string | null; runCount: number; time: string; badge: string }[]
  currentId: string
  readOnly?: boolean
  busy: boolean
  historyLoaded: boolean
  historyError: string
  query: string
  status: string
  nextCursor: string | null
  loadingMore: boolean
}>()
const emit = defineEmits<{
  create: []
  retry: []
  select: [conversationId: string]
  search: [query: string]
  filter: [status: string]
  loadMore: []
}>()
const searchText = ref(props.query)
watch(() => props.query, value => { searchText.value = value })
</script>

<template>
  <aside class="pro-sidebar" aria-label="近期调查">
    <button type="button" class="primary new-conversation" :disabled="busy || readOnly" @click="emit('create')">
      <UiIcon name="plus" /> 新调查
    </button>
    <h2 class="side-label">近期调查</h2>
    <form class="history-search" @submit.prevent="emit('search', searchText)">
      <input v-model="searchText" type="search" aria-label="搜索调查" placeholder="搜索问题或设备" />
      <button type="submit" class="text-button" aria-label="搜索"><UiIcon name="target" /></button>
    </form>
    <label class="history-filter">
      <span class="visually-hidden">按状态筛选</span>
      <select :value="status" aria-label="按状态筛选" @change="emit('filter', ($event.target as HTMLSelectElement).value)">
        <option value="">全部状态</option>
        <option value="WAITING_FOR_USER">待补答</option>
        <option value="ACTIVE">进行中</option>
        <option value="COMPLETED">已完成</option>
        <option value="FAILED">失败</option>
      </select>
    </label>
    <p v-if="!historyLoaded" class="hint side-state">正在读取近期调查…</p>
    <p v-else-if="historyError" class="hint side-state" role="alert">
      读取失败：{{ historyError }}
      <button type="button" class="text-button" @click="emit('retry')">重试</button>
    </p>
    <p v-else-if="!items.length" class="hint side-state">{{ query || status ? '没有符合条件的调查。' : '还没有历史会话。' }}</p>
    <nav v-else class="history-list" aria-label="近期调查列表">
      <button v-for="item in items" :key="item.conversationId" type="button" class="conversation-link"
              :data-conversation-id="item.conversationId"
              :class="{ active: item.conversationId === currentId }"
              :aria-current="item.conversationId === currentId ? 'true' : undefined"
              :disabled="busy" @click="emit('select', item.conversationId)">
        <UiIcon name="chat" />
        <span class="history-text">
          <strong>{{ item.title }}</strong>
          <small>{{ item.assetId ? item.assetId + ' · ' : '' }}{{ item.time }} · {{ item.runCount }} 轮</small>
        </span>
        <span v-if="item.badge === 'waiting'" class="badge waiting">待补答</span>
        <span v-else-if="item.badge === 'running'" class="badge running">进行中</span>
        <span v-else-if="item.badge === 'failed'" class="badge failed">失败</span>
        <span v-else-if="item.badge === 'cancelled'" class="badge cancelled">已取消</span>
        <span v-else-if="item.conversationId === currentId" class="history-check"><UiIcon name="check" /></span>
      </button>
    </nav>
    <button v-if="nextCursor" type="button" class="secondary history-more" :disabled="loadingMore" @click="emit('loadMore')">
      {{ loadingMore ? '正在加载…' : '加载更多' }}
    </button>
    <slot name="workspace" />
    <p class="side-note">{{ USER_REPORT_NOTE }}</p>
  </aside>
</template>

<style scoped>
.history-search { display: flex; align-items: center; gap: 4px; margin: 0 4px 6px; }
.history-search input { min-width: 0; flex: 1; border: 1px solid var(--line); border-radius: 8px;
  background: var(--panel-2); color: var(--text); padding: 7px 8px; font-size: 12px; }
.history-search button { flex: none; padding: 6px; }
.history-filter { display: block; margin: 0 4px 7px; }
.history-filter select { width: 100%; border: 1px solid var(--line); border-radius: 8px;
  background: var(--panel-2); color: var(--text-2); padding: 6px 8px; font-size: 12px; }
.history-more { width: 100%; margin-top: 7px; }
</style>
