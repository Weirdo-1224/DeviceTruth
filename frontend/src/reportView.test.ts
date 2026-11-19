import { describe, expect, it } from 'vitest'
import { canRetryReportReferences, REPORT_STATUS_LABELS, reportReferenceNumbers } from './reportView'
import { toReferenceView } from './diagnosisViewModel'
import type { Reference } from './api'

function reference(id: string): Reference {
  return {
    referenceId: id, sourceType: 'KNOWLEDGE_DOCUMENT', sourceId: 'DOC-1', sourceVersion: '1.0',
    chunkId: 'chunk-1', title: '维修手册', page: null, section: '4.2', excerpt: '摘录', dataNature: 'SIMULATED'
  }
}

describe('独立报告投影', () => {
  it('各 Run 状态使用真实状态文案，不生成完成结论', () => {
    expect(REPORT_STATUS_LABELS.WAITING_FOR_USER).toBe('等待补充信息')
    expect(REPORT_STATUS_LABELS.FAILED).toBe('执行失败')
    expect(REPORT_STATUS_LABELS.CANCELLED).toBe('已取消')
    expect(REPORT_STATUS_LABELS.COMPLETED).toBe('已完成')
  })

  it('引用编号按登记顺序稳定，重试状态明确', () => {
    const numbers = reportReferenceNumbers([toReferenceView(reference('ref-a')), toReferenceView(reference('ref-b'))])
    expect(numbers.get('ref-a')).toBe(1)
    expect(numbers.get('ref-b')).toBe(2)
    expect(numbers.get('missing')).toBeUndefined()
    expect(canRetryReportReferences('error')).toBe(true)
    expect(canRetryReportReferences('notFound')).toBe(true)
    expect(canRetryReportReferences('ready')).toBe(false)
  })
})
