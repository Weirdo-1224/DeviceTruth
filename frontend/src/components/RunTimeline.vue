<script setup lang="ts">
import type { RunEvent } from '../api'
import UiIcon from './UiIcon.vue'

defineProps<{
  events: readonly RunEvent[]
  live: boolean
  status: string
  duration: string
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
  RUN_LATE_RESPONSE: '迟到响应',
  RUN_PHASE: '收口',
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
    case 'RUN_PHASE':
      return data.reason === 'NO_PROGRESS' ? '连续调查没有新增信息，进入最终回答' : '预算接近上限，进入最终回答'
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
  <section class="timeline">
    <header>
      <h2><UiIcon name="activity" /> 调查过程</h2>
      <span class="timeline-status">{{ status }}<span v-if="live" class="live-badge"> · 实时</span></span>
      <span v-if="duration" class="duration">耗时 {{ duration }}</span>
      <slot />
    </header>
    <p v-if="!events.length" class="hint">等待运行事件…</p>
    <ol v-else class="timeline-list">
      <li v-for="event in events" :key="event.seq" class="timeline-item" :data-seq="event.seq" :data-type="event.type">
        <span class="event-marker" :class="event.type === 'RUN_TOOL' && event.data?.success !== true ? 'failure' : badgeClass(event.type)">
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
.timeline { border: 1px solid #dcdffc; border-radius: 12px; overflow: hidden; background: #f9f9fe; }
header { display: flex; gap: 9px; align-items: center; flex-wrap: wrap; padding: 9px 13px; background: #f1f1fc; }
h2 { font-size: 14px; margin: 0; }
h2 svg { color: var(--accent); margin-right: 4px; }
.timeline-status, .duration, time { color: var(--muted); font-size: 12px; }
.duration { margin-left: auto; }
.timeline-list { list-style: none; margin: 0; padding: 9px 13px; }
.timeline-item { display: grid; position: relative; grid-template-columns: 17px minmax(0, 1fr) auto; align-items: start; column-gap: 10px; padding: 3px 0 6px; }
.timeline-item:not(:last-child)::before { content: ""; position: absolute; width: 1px; top: 19px; bottom: -3px; left: 8px; background: #d1d6eb; }
.event-marker { z-index: 1; display: grid; place-items: center; width: 17px; height: 17px; margin-top: 1px; background: #e6e4ff; color: var(--accent); border-radius: 50%; font-size: 11px; }
.event-marker.tool, .event-marker.answer { background: #299963; color: #fff; }
.event-marker.warn { background: #fff0ce; color: var(--warning); }
.event-marker.failure { background: #fce1e4; color: #a02e3a; }
.event-content { overflow-wrap: anywhere; }
.event-content p, .timeline > p { color: var(--muted); margin: 2px 0 0; line-height: 1.4; font-size: 12px; }
.timeline > p { padding: 9px 13px; }
time { margin-top: 2px; white-space: nowrap; font-variant-numeric: tabular-nums; }
</style>
