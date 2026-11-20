import type { AnswerReview, EvidenceNature, UnitDisposition } from './api'

const NATURE: Record<EvidenceNature, string> = {
  DOCUMENT_FACT: '文档记载', USER_REPORT: '用户报告 · 未独立核验', HISTORICAL_RECORD: '历史记录',
  DEVICE_INFERENCE: '依记录推断', INDEPENDENT_VERIFICATION: '独立核验'
}
const STATUS: Record<UnitDisposition, string> = { RETAINED: '已保留', REMOVED: '未采纳', PENDING: '待确认' }
const RULES: Record<string, string> = {
  UNSUPPORTED: '现有证据不足', SUBJECT_MISMATCH: '排查对象不一致', TIME_MISMATCH: '记录时点不一致',
  TIME_OUTSIDE_SNAPSHOT: '超出本轮时点', INVALID_CITATION: '来源不在本轮范围', UNSUPPORTED_QUOTE: '原文不支持该项',
  VERIFICATION_SOURCE_REQUIRED: '缺少独立核验记录', CURRENT_DEVICE_EVIDENCE_REQUIRED: '缺少当前设备记录',
  EVIDENCE_REQUIRED: '需补充依据', USER_SOURCE_REQUIRED: '缺少对应用户记录', DEPENDENCY_REMOVED: '依赖内容未成立',
  PROCEDURE_REQUIRED: '需适用规程', PREREQUISITE_REQUIRED: '需确认执行前提'
}
export interface AnswerReviewView {
  available: boolean
  limited: boolean
  heading: string
  units: { id: string; text: string; nature: string; status: string; disposition: UnitDisposition; reason: string;
    sources: { id: string; label: string; quote: string; readable: boolean }[] }[]
}
export function answerReviewView(review: AnswerReview | null | undefined, referenceIds: readonly string[]): AnswerReviewView {
  if (review?.version === 2) return {
    available: true, limited: review.limited,
    heading: review.limited ? '本次答案复核未完整完成' : '已完成结论、来源与高风险建议审核', units: []
  }
  if (!review || review.version !== 1) return {
    available: false, limited: false, heading: '该结果没有新版逐项复核记录', units: []
  }
  const decisions = new Map(review.decisions.map(item => [item.unitId, item]))
  const registered = new Set(referenceIds)
  const units = review.units.map(unit => {
    const decision = decisions.get(unit.unitId)
    const disposition = decision?.disposition ?? 'PENDING'
    return {
      id: unit.unitId, text: disposition === 'RETAINED' ? unit.text : '该项内容未采纳',
      nature: NATURE[unit.nature] ?? '来源性质未知', status: STATUS[disposition] ?? '待确认', disposition,
      reason: disposition === 'RETAINED' ? '' : (RULES[decision?.rule ?? ''] ?? '需进一步复核'),
      sources: disposition !== 'RETAINED' ? [] : unit.supports.map(support => ({
        id: support.sourceId, label: registered.has(support.sourceId) ? '查看来源'
          : support.sourceId.startsWith('message.') ? '本轮正式用户记录' : '来源暂不可用',
        quote: support.quote, readable: registered.has(support.sourceId)
      }))
    }
  })
  const pending = units.filter(unit => unit.disposition !== 'RETAINED').length
  return { available: true, limited: review.limited || pending > 0,
    heading: pending ? `已保留独立有效内容，${pending} 项未采纳或待确认` : '逐项来源与复核结果', units }
}
