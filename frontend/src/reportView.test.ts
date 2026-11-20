import { describe, expect, it, vi } from 'vitest'
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

it('历史报告使用原引用版本与性质，不读取新发布内容或再次请求模型',()=>{
  const fetch=vi.fn(()=>{throw new Error('report must be local projection')});vi.stubGlobal('fetch',fetch)
  try {
    const old=toReferenceView(reference('old-ref'))
    const published=toReferenceView({...reference('new-ref'),sourceVersion:'2.0',title:'后来发布的资料'})
    expect(reportReferenceNumbers([old]).get('old-ref')).toBe(1)
    expect(reportReferenceNumbers([published]).get('old-ref')).toBeUndefined()
    expect(old.sourceVersion).toBe('1.0');expect(old.dataNature).toBe('SIMULATED');expect(old.title).toBe('维修手册')
    expect(fetch).not.toHaveBeenCalled()
  } finally { vi.unstubAllGlobals() }
})
