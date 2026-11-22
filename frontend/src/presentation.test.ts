import { describe, expect, it } from 'vitest'
import { createSSRApp } from 'vue'
import { renderToString } from 'vue/server-renderer'
import { dataNatureLabel, riskLabel, safetyLabel, parseStatusLabel, categoryLabel, modelOutcomeLabel, verificationLabel, displayTime, memberLabel, roundLabel } from './presentation'
import FilePicker from './components/FilePicker.vue'
import RunTimeline from './components/RunTimeline.vue'

describe('工作台中文展示不改变真实语义', () => {
  it('区分模拟、授权和未知数据性质', () => {
    expect(dataNatureLabel('SIMULATED')).toBe('模拟数据')
    expect(dataNatureLabel('AUTHORIZED')).toBe('已授权资料')
    expect(dataNatureLabel('FUTURE')).toBe('数据性质未明确')
    expect(dataNatureLabel(null)).toBe('数据性质未明确')
  })
  it.each([['LOW','低风险'],['MEDIUM','中风险'],['HIGH','高风险'],['PROHIBITED','禁止操作']])('风险 %s 保持处置强度', (code,label) => expect(riskLabel(code)).toBe(label))
  it('安全处置与答案类别分别翻译，不把完成当确诊', () => {
    expect(safetyLabel('REQUIRES_QUALIFIED_PERSONNEL')).toBe('需具备资质人员处理')
    expect(safetyLabel('REQUIRES_FIELD_VERIFICATION')).toBe('需现场核验')
    expect(safetyLabel('HUMAN_REVIEW_REQUIRED')).toBe('需人工复核')
    expect(categoryLabel('KNOWLEDGE')).toBe('资料答复')
    expect(categoryLabel('HUMAN_REVIEW')).toBe('待人工核验')
    expect(safetyLabel('FUTURE')).toBe('安全处置未明确')
  })
  it('文件已上传/已解析/PDF不可解析是不同状态', () => {
    expect(parseStatusLabel('UPLOADED')).toBe('等待解析')
    expect(parseStatusLabel('PROCESSING')).toBe('解析中')
    expect(parseStatusLabel('PARSED')).toBe('解析完成')
    expect(parseStatusLabel('OCR_REQUIRED')).toContain('不支持文本解析')
    expect(parseStatusLabel('FAILED')).toBe('解析失败')
  })
  it('观测不是核实，未知核实状态不升级为现场已核实', () => {
    expect(verificationLabel('FIELD_VERIFIED')).toBe('现场已核实')
    expect(verificationLabel('DOCUMENTED')).toBe('资料记载')
    expect(verificationLabel('FUTURE')).toBe('核实状态未明确')
  })
  it('成员名字未加载与原作者未记录分别表达', () => {
    expect(memberLabel('u',{u:'张工'})).toBe('张工')
    expect(memberLabel('u',{})).toBe('成员名称暂不可用')
    expect(memberLabel(null,{},'历史发布人未知')).toBe('历史发布人未知')
  })
  it('只按真实列表定位待答轮，不借用查看轮', () => {
    expect(roundLabel('waiting',[{runId:'history'},{runId:'waiting'}])).toBe('第 2 轮调查')
    expect(roundLabel('unknown',[{runId:'history'}])).toBe('指定调查轮次')
  })
  it('时间本地化，未知时间保留，版本原值不在此转换', () => {
    expect(displayTime('2026-11-22T07:00:00Z')).toContain('2026')
    expect(displayTime('2026-11-22T07:00:00Z')).not.toContain('T07:00')
    expect(displayTime(null)).toBe('未记录时间')
    expect(displayTime('invalid-date')).toBe('invalid-date')
  })
  it('模型格式失败仍是可见的失败说明', () => {
    expect(modelOutcomeLabel('INVALID_JSON_OR_SCHEMA')).toBe('输出格式不符合要求')
    expect(modelOutcomeLabel('VALID_PROPOSAL')).toBe('提案已接收')
  })
  it('统一上传外观仍使用单文件原生输入，保留格式和禁用条件', async () => {
    const html=await renderToString(createSSRApp(FilePicker,{accept:'.md,.txt,.pdf',ariaLabel:'上传调查附件',disabled:true}))
    expect(html).toContain('添加附件'); expect(html).toContain('type="file"'); expect(html).toContain('accept=".md,.txt,.pdf"'); expect(html).toContain('disabled'); expect(html).not.toContain('multiple')
  })
  it('事件默认说明使用中文，技术详情保留原始结果码', async () => {
    const html=await renderToString(createSSRApp(RunTimeline,{events:[{seq:1,type:'RUN_MODEL',recordedAt:'2026-11-22T07:00:00Z',data:{model:'test-model',outcome:'INVALID_JSON_OR_SCHEMA'}}],live:false,status:'已完成',duration:''}))
    const description=html.match(/<p class="detail"[^>]*>(.*?)<\/p>/s)?.[1]
    expect(description).toContain('输出格式不符合要求'); expect(description).not.toContain('INVALID_JSON_OR_SCHEMA')
    expect(html).toContain('INVALID_JSON_OR_SCHEMA'); expect(html).toContain('test-model'); expect(html).toContain('技术详情')
  })
})
