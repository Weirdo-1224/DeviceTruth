<script setup lang="ts">
import type { RunEvent } from '../api'

defineProps<{
  events: readonly RunEvent[]
  live: boolean
}>()

const TYPE_LABELS: Record<string, string> = {
  RUN_QUEUED: '排队',
  RUN_RECOVERED: '恢复',
  RUN_WAITING: '等待补充',
  RUN_TOOL: '检索',
  RUN_MODEL: '模型',
  RUN_ANSWER: '答案',
  RUN_FAILED: '失败',
  RUN_CANCELLED: '取消',
  RUN_LATE_RESPONSE: '迟到响应'
}

function typeLabel(type: string): string {
  return TYPE_LABELS[type] ?? '事件'
}

function badgeClass(type: string): string {
  if (type === 'RUN_TOOL') return 'tool'
  if (type === 'RUN_MODEL') return 'model'
  if (type === 'RUN_ANSWER') return 'answer'
  if (type === 'RUN_FAILED' || type === 'RUN_CANCELLED') return 'failure'
  if (type === 'RUN_RECOVERED' || type === 'RUN_LATE_RESPONSE' || type === 'RUN_WAITING') return 'warn'
  return 'neutral'
}

function asString(value: unknown): string | null {
  return typeof value === 'string' && value.length > 0 ? value : null
}

function asNumber(value: unknown): number | null {
  return typeof value === 'number' && Number.isFinite(value) ? value : null
}

function formatDuration(ms: number): string {
  return ms >= 1000 ? (ms / 1000).toFixed(1) + ' 秒' : ms + ' 毫秒'
}

function formatTime(recordedAt: string): string {
  const parsed = new Date(recordedAt)
  return Number.isNaN(parsed.getTime()) ? recordedAt : parsed.toLocaleTimeString()
}

/** 按契约 §2 的 data 白名单字段渲染一行说明；未知类型只展示事件名，防御未来新增类型。 */
function describeEvent(event: RunEvent): string {
  const data = event.data ?? {}
  switch (event.type) {
    case 'RUN_QUEUED': {
      const status = asString(data.status)
      return status ? `任务进入执行队列（${status}）` : '任务进入执行队列'
    }
    case 'RUN_RECOVERED': {
      const attempts = asNumber(data.attempts)
      return attempts !== null ? `中断后恢复执行（第 ${attempts} 次恢复）` : '中断后恢复执行'
    }
    case 'RUN_WAITING': {
      const questionVersion = asNumber(data.questionVersion)
      return questionVersion !== null ? `等待补充信息（第 ${questionVersion} 版）` : '等待补充信息'
    }
    case 'RUN_TOOL': {
      const toolName = asString(data.toolName) ?? '未知工具'
      const success = data.success === true
      const duration = asNumber(data.durationMs)
      const parts = success
        ? [`检索工具 ${toolName} 成功`]
        : [`检索工具 ${toolName} 失败（${asString(data.errorCode) ?? 'UNKNOWN'}）`]
      if (duration !== null) parts.push(`耗时 ${formatDuration(duration)}`)
      if (data.retryable === true) parts.push('可重试')
      const references = asNumber(data.references)
      if (references !== null) parts.push(`引用 ${references} 条`)
      return parts.join(' · ')
    }
    case 'RUN_MODEL': {
      const attempt = asNumber(data.attempt)
      const model = asString(data.model)
      const duration = asNumber(data.durationMs)
      const outcome = asString(data.outcome)
      const parts = [`模型调用${attempt !== null ? `第 ${attempt} 次` : ''}`]
      if (model) parts.push(model)
      if (outcome) parts.push(outcome)
      if (duration !== null) parts.push(`耗时 ${formatDuration(duration)}`)
      const outputTokens = asNumber(data.outputTokens)
      if (outputTokens !== null) parts.push(`输出 ${outputTokens} tokens`)
      return parts.join(' · ')
    }
    case 'RUN_ANSWER': {
      const category = asString(data.category) ?? 'UNKNOWN'
      const riskLevel = asString(data.riskLevel) ?? 'UNKNOWN'
      const dataNature = asString(data.dataNature)
      const summary = `形成答案（${category} · 风险 ${riskLevel}${dataNature ? ' · ' + dataNature : ''}）`
      const claims = asNumber(data.claims)
      return claims !== null ? `${summary} · 依据 ${claims} 条` : summary
    }
    case 'RUN_FAILED':
      return `执行失败（${asString(data.code) ?? 'UNKNOWN'}）`
    case 'RUN_CANCELLED':
      return `已取消（${asString(data.phase) ?? 'UNKNOWN'}）`
    case 'RUN_LATE_RESPONSE': {
      const status = asString(data.status)
      const code = asString(data.code)
      const inner = [status, code].filter((part): part is string => part !== null).join(' · ')
      return inner ? `迟到的模型响应（${inner}）` : '迟到的模型响应'
    }
    default:
      return `事件 ${event.type}`
  }
}
</script>

<template>
  <section class="card timeline">
    <h2>
      运行时间线
      <span v-if="live" class="live-badge">实时</span>
      <span v-else class="ended-badge">未连接实时流</span>
    </h2>
    <ul class="timeline-list">
      <li v-for="event in events" :key="event.seq" class="timeline-item">
        <span class="seq">#{{ event.seq }}</span>
        <span class="badge" :class="badgeClass(event.type)">{{ typeLabel(event.type) }}</span>
        <span class="detail">{{ describeEvent(event) }}</span>
        <time class="time">{{ formatTime(event.recordedAt) }}</time>
      </li>
    </ul>
  </section>
</template>

<style scoped>
.timeline { border: 1px solid var(--line); border-radius: 10px; padding: 16px; margin: 16px 0; }
.timeline h2 { font-size: 15px; margin: 0 0 12px; }
.timeline-item { overflow-wrap: anywhere; }

.timeline h2 {
  display: flex;
  align-items: center;
  gap: 8px;
}
.live-badge,
.ended-badge {
  font-size: 12px;
  font-weight: 400;
  border-radius: 10px;
  padding: 1px 8px;
}
.live-badge {
  color: #0a7a3d;
  background: #e3f5e9;
}
.ended-badge {
  color: #616e7c;
  background: #eef2f5;
}
.timeline-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
  max-height: 320px;
  overflow-y: auto;
}
.timeline-item {
  display: flex;
  align-items: baseline;
  gap: 8px;
  font-size: 13px;
}
.seq {
  color: #9aa5b1;
  font-variant-numeric: tabular-nums;
  flex: 0 0 auto;
}
.badge {
  flex: 0 0 auto;
  border-radius: 10px;
  padding: 1px 8px;
  font-size: 12px;
  white-space: nowrap;
}
.badge.neutral {
  color: #334e68;
  background: #eef2f5;
}
.badge.tool {
  color: #1d4ed8;
  background: #e0eaff;
}
.badge.model {
  color: #6d28d9;
  background: #efe6fd;
}
.badge.answer {
  color: #0a7a3d;
  background: #e3f5e9;
}
.badge.warn {
  color: #a35c00;
  background: #fdeed3;
}
.badge.failure {
  color: #b42318;
  background: #fde5e3;
}
.detail {
  flex: 1;
  min-width: 0;
}
.time {
  flex: 0 0 auto;
  color: #9aa5b1;
  font-size: 12px;
  font-variant-numeric: tabular-nums;
}
</style>
