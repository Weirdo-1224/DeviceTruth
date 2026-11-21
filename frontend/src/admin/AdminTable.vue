<script setup lang="ts">
import type { Row } from './client'
defineProps<{ rows: Row[]; columns: { key: string; label: string }[]; canInspect?: boolean }>()
defineEmits<{ inspect: [row: Row] }>()
function cell(row: Row, key: string): unknown {
  if (key === 'subjectId' && row.dimension === 'PLATFORM') return '全平台'
  if (key === 'usageStatus' && row.usage && typeof row.usage === 'object') return (row.usage as Row).status
  if (key === 'importOrigin') return row[key] === 'CORPUS_IMPORT' ? '已有知识入库' : row[key] === 'ADMIN_UPLOAD' ? '管理端上传' : row[key]
  return row[key]
}
function value(value: unknown): string {
  if (value === null || value === undefined) return '未知'
  if (typeof value === 'boolean') return value ? '是' : '否'
  if (Array.isArray(value)) return value.length ? value.map(String).join('、') : '—'
  if (typeof value === 'object') return '见详情'
  if (value === 'WAITING_FOR_USER') return '等待补答'
  return String(value)
}
</script>
<template>
  <div class="admin-table-scroll" tabindex="0" aria-label="数据表，可横向滚动">
    <table><thead><tr><th v-for="column in columns" :key="column.key" scope="col">{{ column.label }}</th><th v-if="canInspect" scope="col">操作</th></tr></thead>
      <tbody><tr v-for="(row, i) in rows" :key="String(row.userId ?? row.runId ?? row.documentId ?? row.skillId ?? row.auditId ?? i)">
        <td v-for="column in columns" :key="column.key">{{ value(cell(row, column.key)) }}</td>
        <td v-if="canInspect"><button type="button" @click="$emit('inspect', row)">详情</button></td>
      </tr></tbody>
    </table>
  </div>
</template>
