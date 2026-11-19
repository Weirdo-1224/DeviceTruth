import type { RunStatus } from './api'
import type { ReferenceView } from './diagnosisViewModel'

export const REPORT_STATUS_LABELS: Readonly<Record<RunStatus, string>> = {
  QUEUED: '排队中',
  RUNNING: '执行中',
  WAITING_FOR_USER: '等待补充信息',
  COMPLETED: '已完成',
  FAILED: '执行失败',
  CANCELLED: '已取消'
}

export function reportReferenceNumbers(references: ReferenceView[]): ReadonlyMap<string, number> {
  return new Map(references.map((reference, index) => [reference.referenceId, index + 1]))
}

export function canRetryReportReferences(state: string): boolean {
  return state === 'error' || state === 'notFound' || state === 'denied'
}
