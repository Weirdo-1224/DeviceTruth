<script setup lang="ts">
import UiIcon from './UiIcon.vue'
import { USER_REPORT_NOTE } from '../diagnosisViewModel'

defineProps<{
  items: { conversationId: string; title: string; time: string; badge: string }[]
  currentId: string
  busy: boolean
  historyLoaded: boolean
  historyError: string
}>()
const emit = defineEmits<{ create: []; retry: []; select: [conversationId: string] }>()
</script>

<template>
  <aside class="pro-sidebar" aria-label="近期调查">
    <button type="button" class="primary new-conversation" :disabled="busy" @click="emit('create')">
      <UiIcon name="plus" /> 新调查
    </button>
    <h2 class="side-label">近期调查</h2>
    <p v-if="!historyLoaded" class="hint side-state">正在读取近期调查…</p>
    <p v-else-if="historyError" class="hint side-state" role="alert">
      读取失败：{{ historyError }}
      <button type="button" class="text-button" @click="emit('retry')">重试</button>
    </p>
    <p v-else-if="!items.length" class="hint side-state">还没有历史会话。</p>
    <nav v-else class="history-list" aria-label="近期调查列表">
      <button v-for="item in items" :key="item.conversationId" type="button" class="conversation-link"
              :data-conversation-id="item.conversationId"
              :class="{ active: item.conversationId === currentId }"
              :aria-current="item.conversationId === currentId ? 'true' : undefined"
              :disabled="busy" @click="emit('select', item.conversationId)">
        <UiIcon name="chat" />
        <span class="history-text">
          <strong>{{ item.title }}</strong>
          <small>{{ item.time }}</small>
        </span>
        <span v-if="item.badge === 'waiting'" class="badge waiting">待补答</span>
        <span v-else-if="item.badge === 'running'" class="badge running">进行中</span>
        <span v-else-if="item.badge === 'failed'" class="badge failed">失败</span>
        <span v-else-if="item.badge === 'cancelled'" class="badge cancelled">已取消</span>
        <span v-else-if="item.conversationId === currentId" class="history-check"><UiIcon name="check" /></span>
      </button>
    </nav>
    <slot name="workspace" />
    <p class="side-note">{{ USER_REPORT_NOTE }}</p>
  </aside>
</template>
