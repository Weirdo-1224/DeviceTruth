/** 仅用于展示：保留协议值，未知值不推断为正常或已核实。 */
const natureLabels: Record<string, string> = { SIMULATED: '模拟数据', AUTHORIZED: '已授权资料' }
const riskLabels: Record<string, string> = { LOW: '低风险', MEDIUM: '中风险', HIGH: '高风险', PROHIBITED: '禁止操作' }
const safetyLabels: Record<string, string> = { NORMAL: '常规安全要求', REQUIRES_QUALIFIED_PERSONNEL: '需具备资质人员处理', REQUIRES_FIELD_VERIFICATION: '需现场核验', HUMAN_REVIEW_REQUIRED: '需人工复核', PROHIBITED: '禁止操作' }
const parseLabels: Record<string, string> = { UPLOADED: '等待解析', PROCESSING: '解析中', PARSED: '解析完成', FAILED: '解析失败', OCR_REQUIRED: 'PDF 暂不支持文本解析' }
const categoryLabels: Record<string, string> = { KNOWLEDGE: '资料答复', POSSIBLE_CAUSES: '候选原因', CONFIRMED_FINDING: '服务端核验结论', HUMAN_REVIEW: '待人工核验' }
const outcomeLabels: Record<string, string> = { VALID_PROPOSAL: '提案已接收', VALID_SEMANTIC_REVIEW: '答复复核完成', INVALID_JSON_OR_SCHEMA: '输出格式不符合要求', TRANSPORT_ERROR: '模型连接失败', TIMEOUT: '模型调用超时' }
export const dataNatureLabel = (value?: string | null): string => natureLabels[value ?? ''] ?? '数据性质未明确'
export const riskLabel = (value?: string | null): string => riskLabels[value ?? ''] ?? '风险未报告'
export const safetyLabel = (value?: string | null): string => safetyLabels[value ?? ''] ?? '安全处置未明确'
export const parseStatusLabel = (value?: string | null): string => parseLabels[value ?? ''] ?? '解析状态未明确'
export const categoryLabel = (value?: string | null): string => categoryLabels[value ?? ''] ?? '结果类别未明确'
export const modelOutcomeLabel = (value?: string | null): string => outcomeLabels[value ?? ''] ?? '模型返回状态未明确'
export const verificationLabel = (value: string): string => value === 'FIELD_VERIFIED' ? '现场已核实' : value === 'DOCUMENTED' ? '资料记载' : '核实状态未明确'

export function displayTime(value?: string | null): string {
  if (!value) return '未记录时间'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString('zh-CN', { hour12: false })
}
export function memberLabel(id: string | null | undefined, names: Record<string, string> = {}, missing = '历史作者未知'): string {
  return id ? names[id]?.trim() || '成员名称暂不可用' : missing
}
/** 只使用持久轮次列表的位置；未知目标不冒充当前查看轮。 */
export function roundLabel(runId: string, runs: readonly { runId: string }[]): string {
  const index = runs.findIndex(run => run.runId === runId)
  return index >= 0 ? `第 ${index + 1} 轮调查` : '指定调查轮次'
}
