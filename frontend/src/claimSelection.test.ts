import { describe, expect, it } from 'vitest'
import { createClaimSelection } from './claimSelection'
import { buildDiagnosisViewModel } from './diagnosisViewModel'
import type { AgentResult, RunView } from './api'

const RUN_ID = '11111111-1111-4111-8111-111111111111'

function runWith(claims: AgentResult['claims'], referenceIds: string[]): RunView {
  const result: AgentResult = {
    summary: '摘要',
    category: 'POSSIBLE_CAUSES',
    claims,
    suggestions: ['建议一'],
    uncertainties: [],
    limitations: [],
    references: referenceIds.map((referenceId, i) => ({
      referenceId,
      sourceType: 'KNOWLEDGE_DOCUMENT',
      sourceId: 'DOC-' + i,
      sourceVersion: '1.0',
      chunkId: 'chunk-' + i,
      title: '资料 ' + i,
      page: null,
      section: '章节 ' + i,
      excerpt: '摘录 ' + i,
      dataNature: 'AUTHORIZED'
    })),
    riskLevel: 'MEDIUM',
    safetyDisposition: '',
    dataNature: 'SIMULATED'
  }
  return {
    runId: RUN_ID,
    conversationId: '22222222-2222-4222-8222-222222222222',
    goalMessageId: '44444444-4444-4444-8444-444444444444',
    status: 'COMPLETED',
    version: 1,
    requestedAt: '2026-11-17T00:00:00Z',
    asOf: '2026-11-17T00:00:00Z',
    dataNature: 'SIMULATED',
    pendingQuestion: null,
    result,
    failureCode: null
  }
}

describe('claimSelection 判断选中', () => {
  it('初始无选中；sync 后默认第一条，无判断时为 null', () => {
    const selection = createClaimSelection()
    expect(selection.selectedIndex.value).toBeNull()
    selection.sync(3)
    expect(selection.selectedIndex.value).toBe(0)
    selection.reset()
    selection.sync(0)
    expect(selection.selectedIndex.value).toBeNull()
  })

  it('默认焦点使用直接候选下标，没有直接候选时保持未选中', () => {
    const selection = createClaimSelection()
    selection.sync(3, 1)
    expect(selection.selectedIndex.value).toBe(1)
    selection.reset()
    selection.sync(3, null)
    expect(selection.selectedIndex.value).toBeNull()
  })

  it('选择只改变下标：切换两条判断不触发任何请求（纯状态，无 fetch/模型调用面）', () => {
    const selection = createClaimSelection()
    selection.sync(2)
    selection.select(1)
    expect(selection.selectedIndex.value).toBe(1)
    selection.select(0)
    expect(selection.selectedIndex.value).toBe(0)
    selection.select(-1)
    expect(selection.selectedIndex.value).toBe(0)
  })

  it('claims 数量收缩时越界回退到第一条；结果替换后 reset+sync 回到默认', () => {
    const selection = createClaimSelection()
    selection.sync(3)
    selection.select(2)
    selection.sync(2)
    expect(selection.selectedIndex.value).toBe(0)
    selection.reset()
    selection.sync(3)
    expect(selection.selectedIndex.value).toBe(0)
  })
})

describe('判断与引用关联（ViewModel 连接）', () => {
  it('选中判断的引用仅包含该判断关联的真实引用，按原顺序连接', () => {
    const claims: AgentResult['claims'] = [
      { text: '判断A', certainty: 'SUPPORTED', referenceIds: ['r2', 'r1'] },
      { text: '判断B', certainty: 'POSSIBLE', referenceIds: ['r3'] }
    ]
    const model = buildDiagnosisViewModel({ run: runWith(claims, ['r1', 'r2', 'r3']) })
    expect(model.claims[0].references.map((ref) => ref.referenceId)).toEqual(['r2', 'r1'])
    expect(model.claims[1].references.map((ref) => ref.referenceId)).toEqual(['r3'])
    expect(model.claims[0].referenceNote).toBeNull()
    // 选中键可用于快照内定位
    expect(model.claims[1].key).toBe(RUN_ID + ':1')
  })

  it('关联 ID 未取得时给出“引用暂不可用”，选择该判断也不产生虚构来源', () => {
    const claims: AgentResult['claims'] = [
      { text: '判断A', certainty: 'SUPPORTED', referenceIds: ['missing-1'] }
    ]
    const model = buildDiagnosisViewModel({ run: runWith(claims, ['r1']) })
    expect(model.claims[0].references).toEqual([])
    expect(model.claims[0].referenceNote).toBe('引用暂不可用')
    expect(model.claims[0].missingReferenceIds).toEqual(['missing-1'])
  })
})
