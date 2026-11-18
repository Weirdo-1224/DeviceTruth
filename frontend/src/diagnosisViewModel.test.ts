import { describe, expect, it } from 'vitest'
import { buildDiagnosisViewModel, buildInvestigationMessage, previewOf, PREVIEW_LENGTH, ROUND_FIELDS_NOTE, toReferenceView } from './diagnosisViewModel'
import type { AgentResult, MessageView, Reference, RunEvent, RunView } from './api'

const RUN_ID = '11111111-1111-4111-8111-111111111111'

function reference(overrides: Partial<Reference> = {}): Reference {
  return {
    referenceId: 'aaaaaaa1-1111-4111-8111-111111111111',
    sourceType: 'KNOWLEDGE_DOCUMENT',
    sourceId: 'DOC-1',
    sourceVersion: '2026-11-01T00:00:00Z',
    chunkId: 'chunk-1',
    title: '厂商维修手册',
    page: null,
    section: '4.2 充电中断',
    excerpt: '充电口红灯常亮且充电停止时，优先检查交流供电与充电枪连接。',
    dataNature: 'AUTHORIZED',
    ...overrides
  }
}

function result(overrides: Partial<AgentResult> = {}): AgentResult {
  return {
    summary: '充电中断疑似与供电或充电口连接有关。',
    category: 'POSSIBLE_CAUSES',
    claims: [],
    suggestions: ['由具备资质人员现场确认故障码。'],
    uncertainties: ['尚未读取实时故障码。'],
    limitations: ['本次未进行现场核验。'],
    references: [],
    riskLevel: 'HIGH',
    safetyDisposition: 'REQUIRES_QUALIFIED_PERSONNEL',
    dataNature: 'SIMULATED',
    ...overrides
  }
}

function run(overrides: Partial<RunView> = {}): RunView {
  return {
    runId: RUN_ID,
    conversationId: '22222222-2222-4222-8222-222222222222',
    goalMessageId: '44444444-4444-4444-8444-444444444444',
    status: 'COMPLETED',
    version: 3,
    requestedAt: '2026-11-17T00:00:00Z',
    asOf: '2026-11-17T00:00:10Z',
    dataNature: 'SIMULATED',
    pendingQuestion: null,
    result: result(),
    failureCode: null,
    ...overrides
  }
}

describe('diagnosisViewModel 映射', () => {
  it('POSSIBLE_CAUSES 中的 SUPPORTED 判断保留在候选原因并标示资料支持', () => {
    const authorized = reference()
    const simulated = reference({
      referenceId: 'bbbbbbb2-2222-4222-8222-222222222222',
      sourceType: 'FIELD_OBSERVATION',
      title: '模拟观测 CHARGE_PORT.charge_port_color',
      dataNature: 'SIMULATED'
    })
    const model = buildDiagnosisViewModel({
      run: run({
        result: result({
          claims: [
            { text: '模拟观测显示充电口红灯常亮、充电停止。', certainty: 'SUPPORTED', referenceIds: [simulated.referenceId, authorized.referenceId] },
            { text: '交流供电进线接触不良。', certainty: 'POSSIBLE', referenceIds: [] }
          ],
          references: [simulated, authorized]
        })
      })
    })
    expect(model.category).toBe('POSSIBLE_CAUSES')
    expect(model.claimsTitle).toBe('候选原因')
    // 保留全部 claims 及原顺序，不按 certainty 拆走 SUPPORTED
    expect(model.claims.map((claim) => claim.text)).toEqual([
      '模拟观测显示充电口红灯常亮、充电停止。',
      '交流供电进线接触不良。'
    ])
    expect(model.claims[0].certaintyLabel).toBe('资料支持')
    expect(model.claims[1].certaintyLabel).toBe('可能')
    expect(model.claims[0].referenceNote).toBeNull()
    // 模拟/授权混合：逐引用保留各自 dataNature，整体沿用服务端值
    expect(model.claims[0].references.map((ref) => ref.dataNature)).toEqual(['SIMULATED', 'AUTHORIZED'])
    expect(model.dataNature).toBe('SIMULATED')
    expect(model.references).toHaveLength(2)
  })

  it('KNOWLEDGE 类别的判断显示为资料断言，不写成候选故障原因', () => {
    const model = buildDiagnosisViewModel({
      run: run({
        result: result({
          category: 'KNOWLEDGE',
          claims: [
            { text: '该车型充电口红灯表示充电异常中断。', certainty: 'POSSIBLE', referenceIds: [] },
            { text: '厂商手册记载需先检查供电与枪线。', certainty: 'SUPPORTED', referenceIds: [] }
          ]
        })
      })
    })
    expect(model.claimsTitle).toBe('资料断言')
    expect(model.claims).toHaveLength(2)
    expect(model.claims.map((claim) => claim.certaintyLabel)).toEqual(['可能', '资料支持'])
  })

  it('空 claims 安全展示摘要、风险与本轮建议，不伪造候选原因', () => {
    const model = buildDiagnosisViewModel({ run: run({ result: result({ claims: [] }) }) })
    expect(model.claims).toEqual([])
    expect(model.summary).toBe('充电中断疑似与供电或充电口连接有关。')
    expect(model.riskLevel).toBe('HIGH')
    expect(model.safetyNotice).toContain('具备资质')
    expect(model.suggestions).toEqual(['由具备资质人员现场确认故障码。'])
    expect(model.uncertainties).toEqual(['尚未读取实时故障码。'])
    expect(model.limitations).toEqual(['本次未进行现场核验。'])
    expect(model.roundFieldsNote).toBe(ROUND_FIELDS_NOTE)
    // 不生成概率、已执行检查或判断级建议关联
    expect(model).not.toHaveProperty('probability')
    expect(model).not.toHaveProperty('executedChecks')
    expect(model.claims.every((claim) => !('suggestions' in claim))).toBe(true)
  })

  it('缺引用给出说明：无关联引用与引用暂不可用都不创建虚构来源', () => {
    const known = reference()
    const model = buildDiagnosisViewModel({
      run: run({
        result: result({
          claims: [
            { text: '没有任何引用的判断。', certainty: 'POSSIBLE', referenceIds: [] },
            { text: '部分引用缺失的判断。', certainty: 'SUPPORTED', referenceIds: [known.referenceId, 'ccccccc3-3333-4333-8333-333333333333'] }
          ],
          references: [known]
        })
      })
    })
    expect(model.claims[0].referenceNote).toBe('当前无已关联引用')
    expect(model.claims[1].referenceNote).toBe('引用暂不可用')
    expect(model.claims[1].references.map((ref) => ref.referenceId)).toEqual([known.referenceId])
    expect(model.claims[1].missingReferenceIds).toEqual(['ccccccc3-3333-4333-8333-333333333333'])
  })

  it('AUTHORIZED 不代表现场已核验，受控提示与逐引用性质分开', () => {
    const model = buildDiagnosisViewModel({
      run: run({ result: result({ dataNature: 'AUTHORIZED', references: [reference()] }) })
    })
    expect(model.dataNature).toBe('AUTHORIZED')
    expect(model.dataNatureNote).toContain('不代表已完成现场核验')
    expect(model.references[0].dataNature).toBe('AUTHORIZED')
    const simulated = buildDiagnosisViewModel({ run: run() })
    expect(simulated.dataNatureNote).toBe('')
  })

  it('选中键为 runId:claimIndex，仅标识当前快照', () => {
    const model = buildDiagnosisViewModel({
      run: run({
        result: result({ claims: [{ text: '判断一', certainty: 'POSSIBLE', referenceIds: [] }, { text: '判断二', certainty: 'POSSIBLE', referenceIds: [] }] })
      })
    })
    expect(model.claims.map((claim) => claim.key)).toEqual([RUN_ID + ':0', RUN_ID + ':1'])
  })

  it('目标消息只映射展示文本与所选设备', () => {
    const goalMessage: MessageView = {
      messageId: '44444444-4444-4444-8444-444444444444',
      seq: 1,
      role: 'USER',
      content: '车辆充电中断，请调查原因。',
      selectedAssetId: 'CAR-B',
      requestedAt: '2026-11-17T00:00:00Z',
      asOf: '2026-11-17T00:00:00Z'
    }
    const model = buildDiagnosisViewModel({ run: run(), goalMessage })
    expect(model.taskQuestion).toBe('车辆充电中断，请调查原因。')
    expect(model.selectedAssetId).toBe('CAR-B')
    const unlinked = buildDiagnosisViewModel({ run: run(), goalMessage: { ...goalMessage, selectedAssetId: null } })
    expect(unlinked.selectedAssetId).toBeNull()
  })

  it('引用视图原样映射字段，缺页码不伪造、未知来源类型给安全通用标签', () => {
    const withSection = toReferenceView(reference())
    expect(withSection.page).toBeNull()
    expect(withSection.locatingNote).toBe('章节：4.2 充电中断')
    const withPage = toReferenceView(reference({ page: 12, section: null }))
    expect(withPage.locatingNote).toBe('第 12 页')
    expect(toReferenceView(reference({ page: null, section: null })).locatingNote).toBeNull()
    const unknown = toReferenceView(reference({ sourceType: 'SOMETHING_NEW' }))
    expect(unknown.sourceTypeLabel).toBe('其他来源')
    expect(unknown.sourceType).toBe('SOMETHING_NEW')
    // 无 fileId/downloadUrl，不能凭 sourceId 编造下载链接
    expect(withSection).not.toHaveProperty('downloadUrl')
    expect(withSection.sourceId).toBe('DOC-1')
    expect(withSection.sourceVersion).toBe('2026-11-01T00:00:00Z')
    expect(withSection.excerpt).toContain('充电口红灯')
  })

  it('事件只映射白名单安全字段，缺失不补写', () => {
    const events: RunEvent[] = [
      { seq: 1, type: 'RUN_TOOL', recordedAt: '2026-11-17T00:00:01Z', data: { toolName: 'get_observations', success: true, durationMs: 19, references: 9, errorCode: '', retryable: false } },
      { seq: 2, type: 'RUN_MODEL', recordedAt: '2026-11-17T00:00:02Z', data: { model: 'deepseek-flash', attempt: 2, outcome: 'VALID_PROPOSAL', durationMs: 3128, inputTokens: 3598, outputTokens: 383, retryable: false } },
      { seq: 3, type: 'RUN_TOOL', recordedAt: '2026-11-17T00:00:03Z', data: { toolName: 'search_knowledge', success: false, errorCode: 'EMBEDDING_CONFIGURATION_MISSING', retryable: false } },
      { seq: 4, type: 'RUN_ANSWER', recordedAt: '2026-11-17T00:00:04Z', data: { category: 'POSSIBLE_CAUSES' } }
    ]
    const model = buildDiagnosisViewModel({ run: run(), events })
    expect(model.events).toHaveLength(4)
    expect(model.events[0]).toMatchObject({ kind: 'tool', title: 'get_observations', success: true })
    expect(model.events[0].detail).toContain('耗时 19 ms')
    expect(model.events[0].detail).toContain('引用 9 条')
    expect(model.events[1]).toMatchObject({ kind: 'model', title: 'deepseek-flash', success: null })
    expect(model.events[1].detail).toContain('VALID_PROPOSAL')
    // 失败工具展示错误码；缺耗时不补写
    expect(model.events[2].detail).toContain('EMBEDDING_CONFIGURATION_MISSING')
    expect(model.events[2].detail).not.toContain('耗时')
    // 非工具/模型事件不给 data 细节，不推测未记录步骤
    expect(model.events[3]).toMatchObject({ kind: 'other', title: 'RUN_ANSWER', detail: '', success: null })
  })

  it('run 为 null 时按尚无结果处理，不产生虚构内容', () => {
    const model = buildDiagnosisViewModel({ run: null })
    expect(model.runId).toBeNull()
    expect(model.status).toBeNull()
    expect(model.claims).toEqual([])
    expect(model.claimsTitle).toBeNull()
    expect(model.summary).toBeNull()
    expect(model.references).toEqual([])
    expect(model.events).toEqual([])
  })
})

describe('深入调查请求正文', () => {
  it('候选原因类别称“候选判断（尚未确诊）”，正文用户可见且不带隐藏指令', () => {
    const message = buildInvestigationMessage('充电中断与 BMS 绝缘检测有关', 'POSSIBLE_CAUSES')
    expect(message).toBe('请深入调查以下候选判断（尚未确诊）：充电中断与 BMS 绝缘检测有关。' +
      '请结合继承资料重新取证，说明依据、不确定点和建议。')
  })

  it('非候选原因类别按实际语义统称“判断”，不伪造候选口径', () => {
    const message = buildInvestigationMessage('手册建议先读取故障日志', 'KNOWLEDGE')
    expect(message).toContain('请深入调查以下判断：手册建议先读取故障日志。')
    expect(message).not.toContain('候选')
  })
})

describe('调查导航短预览', () => {
  it('短句原样返回，长句按码点截断并补省略号且不改写原文', () => {
    expect(previewOf('短判断')).toBe('短判断')
    const emoji = '🔧'.repeat(PREVIEW_LENGTH + 3)
    const preview = previewOf(emoji)
    expect(Array.from(preview)).toHaveLength(PREVIEW_LENGTH + 1)
    expect(preview.endsWith('…')).toBe(true)
    // 不切断代理对：截断后仍是完整码点序列
    expect([...preview].every((char) => char === '🔧' || char === '…')).toBe(true)
  })

  it('claim 同时给出原文与短预览，含否定与安全用语的原文不被改写', () => {
    const text = '严禁在未断电情况下拆卸高压连接器；这是由具备资质人员按厂家规程执行的强制要求，不得省略。'
    const model = buildDiagnosisViewModel({
      run: run({ result: result({ claims: [{ text, certainty: 'SUPPORTED', referenceIds: [] }] }) })
    })
    const claim = model.claims[0]
    expect(claim.text).toBe(text)
    expect(claim.preview.length).toBeLessThan(text.length)
    expect(claim.preview).toContain('严禁在未断电情况下')
    expect(claim.preview.endsWith('…')).toBe(true)
  })

  it('KNOWLEDGE 与 POSSIBLE_CAUSES 分别得到不同的类别标题与相同 certainty 语义', () => {
    const claims = [{ text: '判断一', certainty: 'SUPPORTED' as const, referenceIds: [] }]
    const knowledge = buildDiagnosisViewModel({ run: run({ result: result({ category: 'KNOWLEDGE', claims }) }) })
    const causes = buildDiagnosisViewModel({ run: run({ result: result({ category: 'POSSIBLE_CAUSES', claims }) }) })
    expect(knowledge.claimsTitle).toBe('资料断言')
    expect(causes.claimsTitle).toBe('候选原因')
    expect(knowledge.claims[0].certaintyLabel).toBe('资料支持')
    expect(causes.claims[0].certaintyLabel).toBe('资料支持')
    expect(knowledge).not.toHaveProperty('probability')
  })

  it('空 claims 时仍保留摘要、建议与安全限制，供完整结果展示', () => {
    const model = buildDiagnosisViewModel({ run: run({ result: result({ claims: [] }) }) })
    expect(model.claims).toEqual([])
    expect(model.summary).toBeTruthy()
    expect(model.limitations).toContain('本次未进行现场核验。')
    expect(model.suggestions.length).toBe(1)
  })
})
