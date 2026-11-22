<script setup lang="ts">
import { computed } from 'vue'
import type { RunEvent } from '../api'
import UiIcon from './UiIcon.vue'

const props = defineProps<{
  overview?: boolean
  compact?: boolean
  events: readonly RunEvent[]
  live: boolean
  status: string
  duration: string
}>()

const visibleEvents = computed(() => props.compact ? props.events.slice(-4) : props.events)

const TYPE_LABELS: Record<string, string> = {
  RUN_QUEUED: '排队',
  RUN_RECOVERED: '恢复',
  RUN_WAITING: '等待补充',
  RUN_TOOL: '检索',
  RUN_MODEL: '模型',
  RUN_ANSWER: '答案',
  RUN_FAILED: '失败',
  RUN_CANCELLED: '取消',
  RUN_LATE_RESPONSE: '迟到响应',
  RUN_PHASE: '答复阶段',
  RUN_DEGRADED: '有限结果',
  RUN_TERMINAL: '运行结束'
}

const TOOL_LABELS: Record<string, string> = {
  search_knowledge: '检索相关资料',
  get_document_excerpt: '读取资料原文',
  get_asset: '查询设备档案',
  get_configuration: '核对设备配置',
  get_maintenance_history: '查询维修记录',
  get_observations: '检查设备观测',
  activate_skill: '加载领域 Skill',
  read_skill_reference: '读取 Skill 参考资料',
  generate_diagnosis_report: '整理诊断报告'
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
      return attempts !== null ? `中断后恢复执行（执行尝试 ${attempts}）` : '中断后恢复执行'
    }
    case 'RUN_WAITING': {
      const questionVersion = asNumber(data.questionVersion)
      return questionVersion !== null ? `等待补充信息（第 ${questionVersion} 版）` : '等待补充信息'
    }
    case 'RUN_TOOL': {
      const toolName = asString(data.toolName) ?? '未知工具'
      const success = data.success
      const duration = asNumber(data.durationMs)
      const parts = success === true
        ? [`检索工具 ${toolName} 成功`]
        : success === false ? [`工具 ${toolName} 失败（${asString(data.errorCode) ?? 'UNKNOWN'}）`] : [`工具 ${toolName} 结果未报告`]
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
      const claims = Array.isArray(data.claims) ? data.claims.length : null
      return claims !== null ? `${summary} · 判断 ${claims} 项` : summary
    }
    case 'RUN_PHASE': {
      const phase = asString(data.phase) ?? 'UNKNOWN'
      const labels: Record<string, string> = { FINALIZING: '准备最终答复', REVIEWING: '复核答复', CORRECTING: '修正答复' }
      const reason = asString(data.reason)
      return (labels[phase] ?? `处理中（${phase}）`) + (reason ? ` · ${reason}` : '')
    }
    case 'RUN_DEGRADED':
      return `形成有限结果（${asString(data.code) ?? 'UNKNOWN'}）`
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
  <section class="timeline" :class="{ compact, overview }">
    <header>
      <h2><UiIcon name="activity" /> {{ overview ? '调查活动' : '调查过程' }}</h2>
      <span v-if="!compact && !overview" class="timeline-status">{{ status }}<span v-if="live" class="live-badge"> · 实时</span></span>
      <span v-if="duration" class="duration">累计执行用时 {{ duration }}</span>
      <slot />
    </header>
    <p v-if="!events.length" class="hint">等待运行事件…</p>
    <ol v-else class="timeline-list">
      <li v-for="event in visibleEvents" :key="event.seq" class="timeline-item" :data-seq="event.seq" :data-type="event.type">
        <span class="event-marker" :class="event.type === 'RUN_TOOL' ? (event.data?.success === true ? 'tool' : event.data?.success === false ? 'failure' : 'neutral') : badgeClass(event.type)">
          <UiIcon :name="event.type === 'RUN_ANSWER' || (event.type === 'RUN_TOOL' && event.data?.success === true) ? 'check' : 'activity'" />
        </span>
        <div class="event-content">
          <strong>{{ TOOL_LABELS[asString(event.data?.toolName) ?? ''] ?? typeLabel(event.type) }}</strong>
          <p class="detail">{{ describeEvent(event) }}</p>
        </div>
        <time :datetime="event.recordedAt">{{ formatTime(event.recordedAt) }}</time>
      </li>
    </ol>
  </section>
</template>

<style scoped>
.timeline { border: 1px solid var(--line); border-radius: 12px; overflow: hidden; background: var(--panel); }
header { display: flex; gap: 12px; align-items: center; flex-wrap: wrap; padding: 16px 22px; border-bottom: 1px solid var(--line-soft); }
h2 { display: flex; align-items: center; gap: 8px; font-size: 16px; margin: 0; } h2 svg { color: var(--accent); }
.timeline-status, .duration, time { color: var(--text-3); font-size: 14px; }
.duration { margin-left: auto; }
.overview header > :last-child { margin-left: auto; }
.timeline-list { list-style: none; margin: 0; padding: 18px 22px; }
.timeline-item { display: grid; position: relative; grid-template-columns: 30px minmax(0, 1fr) auto; align-items: start; gap: 14px; padding: 4px 0 24px; }
.timeline-item:last-child { padding-bottom: 4px; }
.timeline-item:not(:last-child)::before { content: ''; position: absolute; width: 2px; top: 32px; bottom: -3px; left: 14px; background: var(--line); }
.event-marker { z-index: 1; display: grid; place-items: center; width: 30px; height: 30px; background: var(--soft); color: var(--accent); border-radius: 50%; font-size: 16px; }
.event-marker.tool, .event-marker.answer { background: #e8f5ee; color: var(--ok); }
.event-marker.warn { background: var(--warn-bg); color: var(--warn); }
.event-marker.failure { background: #fff0ef; color: var(--danger); }
.event-content { min-width: 0; overflow-wrap: anywhere; }
.event-content strong { display: block; padding-top: 2px; font-size: 16px; font-weight: 600; }
.event-content p, .timeline > p { color: var(--text-2); margin: 5px 0 0; line-height: 1.7; font-size: 16px; }
.timeline > p { padding: 20px 22px; margin: 0; }
time { margin-top: 5px; white-space: nowrap; font-variant-numeric: tabular-nums; }
.overview .timeline-list { max-height: 560px; overflow-y: auto; }
.overview .timeline-item:last-child .event-content { background: var(--soft); border-radius: 8px; padding: 8px 12px; margin-top: -4px; }
.compact .timeline-list { padding: 14px 18px; }
@media (max-width: 600px) { .timeline-item { grid-template-columns: 30px minmax(0, 1fr); gap: 8px 12px; } time { grid-column: 2; margin: 0; } header, .timeline-list { padding-inline: 16px; } }
</style>
