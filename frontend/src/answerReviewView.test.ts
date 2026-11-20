import { describe, expect, it } from 'vitest'
import { createSSRApp } from 'vue'
import { renderToString } from '@vue/server-renderer'
import AnswerReviewPanel from './components/AnswerReviewPanel.vue'
import { answerReviewView } from './answerReviewView'
import type { AnswerReview } from './api'

const review: AnswerReview = {
  version: 1, limited: true,
  units: [
    { unitId: 'claim.0', position: 'CLAIM', index: 0, text: '本手册版本已确认为2026.1', nature: 'DOCUMENT_FACT',
      subjectId: null, observedAt: null, supports: [{ sourceId: 'r1', quote: '版本2026.1' }], dependsOn: [] },
    { unitId: 'claim.1', position: 'CLAIM', index: 1, text: '不应显示的被拒绝草稿', nature: 'DEVICE_INFERENCE',
      subjectId: null, observedAt: null, supports: [{ sourceId: 'r2', quote: '不应显示的片段' }], dependsOn: [] },
    { unitId: 'claim.2', position: 'CLAIM', index: 2, text: '用户报告看到E1', nature: 'USER_REPORT',
      subjectId: null, observedAt: null, supports: [{ sourceId: 'message.0', quote: '看到E1' }], dependsOn: [] }
  ],
  decisions: [
    { unitId: 'claim.0', disposition: 'RETAINED', rule: 'SUPPORTED', sourceIds: ['r1'] },
    { unitId: 'claim.1', disposition: 'REMOVED', rule: 'CURRENT_DEVICE_EVIDENCE_REQUIRED', sourceIds: [] },
    { unitId: 'claim.2', disposition: 'RETAINED', rule: 'SUPPORTED', sourceIds: ['message.0'] }
  ]
}

describe('逐项来源与局部处置', () => {
  it('v2整体审核显示交付状态而非逐句分类', async () => {
    const view = answerReviewView({ version: 2, units: [], decisions: [], limited: false }, [])
    expect(view.available).toBe(true)
    expect(view.units).toEqual([])
    const html = await renderToString(createSSRApp(AnswerReviewPanel, { review: view }))
    expect(html).toContain('已完成结论、来源与高风险建议审核')
    expect(html).not.toContain('来源性质未知')
  })
  it('历史结果不升级为新版通过', () => {
    expect(answerReviewView(null, []).available).toBe(false)
    expect(answerReviewView({ ...review, version: 3 }, []).available).toBe(false)
  })
  it('只为实际可读引用提供打开操作，用户记录保持未核验', () => {
    const result = answerReviewView(review, ['r1'])
    expect(result.units[0]?.sources[0]?.readable).toBe(true)
    expect(result.units[2]?.nature).toContain('未独立核验')
    expect(result.units[2]?.sources[0]?.readable).toBe(false)
    expect(result.units[1]?.text).not.toContain('不应显示')
    expect(result.units[1]?.sources).toEqual([])
  })
  it('实际组件渲染显示保留和待确认，隐藏被拒原文', async () => {
    const html = await renderToString(createSSRApp(AnswerReviewPanel, { review: answerReviewView(review, ['r1']) }))
    expect(html).toContain('本手册版本已确认为2026.1')
    expect(html).toContain('缺少当前设备记录')
    expect(html).toContain('本轮正式用户记录')
    expect(html).not.toContain('不应显示的被拒绝草稿')
    expect(html).not.toContain('不应显示的片段')
  })
  it('历史组件仍可渲染且没有新核验结论', async () => {
    const html = await renderToString(createSSRApp(AnswerReviewPanel, { review: answerReviewView(null, []) }))
    expect(html).toContain('没有新版逐项复核记录')
    expect(html).not.toContain('已保留')
  })
})
