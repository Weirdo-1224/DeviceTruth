import { describe, it, expect } from 'vitest'
import { createSSRApp } from 'vue'
import { renderToString } from 'vue/server-renderer'
import RunTimeline from './components/RunTimeline.vue'
import type { RunEvent } from './api'

const event = (type: string, data: Record<string, unknown>, seq = 1): RunEvent => ({ seq, type, data, recordedAt: '2026-11-22T07:00:00Z' })
const render = (events: RunEvent[], compact = false) => renderToString(createSSRApp(RunTimeline, { events, compact, live: false, status: '执行中', duration: '' }))
describe('真实运行事件的展示语义', () => {
  it.each([['FINALIZING', '准备最终答复'], ['REVIEWING', '复核答复'], ['CORRECTING', '修正答复'], ['FUTURE', '处理阶段未明确']])('按 %s 阶段展示并保留原因码', async (phase, label) => {
    const html = await render([event('RUN_PHASE', { phase, reason: 'ORIGINAL_REASON' })])
    expect(html).toContain(label); expect(html).toContain('ORIGINAL_REASON'); expect(html).not.toContain('预算接近上限')
  })
  it('恢复事件报告执行尝试，答案按真实数组计数', async () => {
    const html = await render([event('RUN_RECOVERED', { attempts: 3 }), event('RUN_ANSWER', { claims: [{ text: '判断' }] }, 2)])
    expect(html).toContain('执行尝试 3'); expect(html).not.toContain('第 3 次恢复'); expect(html).toContain('判断 1 项')
  })
  it('未报告成功状态不展示成功或失败', async () => {
    const html = await render([event('RUN_TOOL', { toolName: 'get_asset' })])
    expect(html).toContain('结果未报告'); expect(html).not.toContain('event-marker failure'); expect(html).not.toContain('event-marker tool')
  })
  it('紧凑概览只展示最近四条，同一输入仍可完整展示', async () => {
    const events = Array.from({ length: 6 }, (_, i) => event('RUN_MODEL', { attempt: i + 1 }, i + 1))
    const html = await render(events, true)
    expect(html).not.toContain('data-seq="1"'); expect(html).toContain('data-seq="6"')
    expect(await render(events)).toContain('data-seq="1"')
  })
})

import AnswerReviewPanel from './components/AnswerReviewPanel.vue'
import ClaimList from './components/ClaimList.vue'
import { buildDiagnosisViewModel } from './diagnosisViewModel'
import type { RunView } from './api'
describe('结果展示保留证据与复核含义', () => {
  it('只有整体复核状态时不生成空的可展开列表', async () => {
    const html = await renderToString(createSSRApp(AnswerReviewPanel, { review: { available: true, limited: true, heading: '本次答案复核未完整完成', units: [] } }))
    expect(html).toContain('本次答案复核未完整完成'); expect(html).not.toContain('<details')
  })
  it('同一引用被多个判断使用时仍只算当前判断的引用数', async () => {
    const run: RunView = { runId: 'r', conversationId: 'c', goalMessageId: 'm', version: 1, status: 'COMPLETED', requestedAt: '', asOf: '', dataNature: 'SIMULATED', pendingQuestion: null, failureCode: null,
      result: { summary: '需要人工核验', category: 'HUMAN_REVIEW', claims: [{ text: '判断', certainty: 'POSSIBLE', referenceIds: ['ref-a'], kind: 'CAUSE', relevance: 'DIRECT' }], suggestions: [], uncertainties: [], limitations: [], references: [], riskLevel: 'HIGH', safetyDisposition: 'REQUIRES_QUALIFIED_PERSONNEL', dataNature: 'SIMULATED' } }
    const model = buildDiagnosisViewModel({ run })
    const html = await renderToString(createSSRApp(ClaimList, { title: '判断', claims: model.claims, selectedIndex: 0, referenceCounts: new Map([['ref-a', 3]]) }))
    expect(html).toContain('1 条引用'); expect(html).not.toContain('3 条引用')
  })
})


import ProfessionalDiagnosisPage from './components/ProfessionalDiagnosisPage.vue'
describe('重构后的状态布局', () => {
  it('执行概览保留完整真实事件，早期失败不会因取最后四条被隐藏', async () => {
    const events = [event('RUN_TOOL', { success: false, toolName: 'search_knowledge', errorCode: 'TIMEOUT' }), ...Array.from({ length: 5 }, (_, i) => event('RUN_MODEL', { attempt: i + 1 }, i + 2))]
    const html = await renderToString(createSSRApp(RunTimeline, { overview: true, events, live: true, status: 'RUNNING', duration: '' }))
    expect(html).toContain('调查活动'); expect(html).toContain('TIMEOUT'); expect(html).toContain('data-seq="1"'); expect(html).toContain('data-seq="6"')
  })
  it.each(['POSSIBLE_CAUSES', 'HUMAN_REVIEW'] as const)('%s 结果以真实摘要为主，不呈现确诊或修复成功', async category => {
    const run: RunView = { runId: 'r', conversationId: 'c', goalMessageId: 'm', version: 1, status: 'COMPLETED', requestedAt: '', asOf: '', dataNature: 'SIMULATED', pendingQuestion: null, failureCode: null,
      result: { summary: '充电中断，现有记录不足以确认原因。', category, claims: [], suggestions: ['读取故障日志'], uncertainties: ['缺实测值'], limitations: ['尚未现场核验'], references: [], riskLevel: 'HIGH', safetyDisposition: 'REQUIRES_QUALIFIED_PERSONNEL', dataNature: 'SIMULATED', review: { version: 2, limited: true, units: [], decisions: [] } } }
    const model = buildDiagnosisViewModel({ run })
    const html = await renderToString(createSSRApp(ProfessionalDiagnosisPage, { run, model, selectedClaim: null, selectedIndex: null, registeredReferences: [], referencesState: 'empty', referencesNote: '', skillLabel: null, embedded: true }))
    expect(html).toContain('充电中断，现有记录不足以确认原因。'); expect(html).toContain('有限结果 · 需人工复核'); expect(html).toContain('高风险'); expect(html).toContain('title="HIGH"'); expect(html).toContain('SIMULATED')
    expect(html).not.toContain('修复成功'); expect(html).not.toContain('pro-nav pro-card')
    expect(html.indexOf('pro-claim-workspace')).toBeLessThan(html.indexOf('pro-next-actions'))
  })
})

