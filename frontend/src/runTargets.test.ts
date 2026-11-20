import { describe, expect, it } from 'vitest'
import { pickActiveRun, resolveRunTargets, runPermissions } from './runTargets'
import type { RunView } from './api'

const BASE_AS_OF = '2026-11-17T08:00:00Z'

function run(id: string, status: RunView['status'], version = 1, extra: Partial<RunView> = {}): RunView {
  return {
    runId: id,
    conversationId: 'conv-1',
    goalMessageId: 'm-' + id,
    status,
    version,
    requestedAt: BASE_AS_OF,
    asOf: BASE_AS_OF,
    dataNature: 'SIMULATED',
    pendingQuestion: null,
    result: null,
    failureCode: null,
    ...extra
  }
}

const question = {
  pendingQuestionId: 'q1',
  questionVersion: 1,
  question: '请补充充电方式',
  requestedFields: ['充电方式'],
  askedAt: BASE_AS_OF
}

describe('查看轮与活动目标分离', () => {
  it('未固定时跟随服务器排序的最新轮，固定历史轮后保持该轮', () => {
    const runs = [run('r1', 'COMPLETED'), run('r2', 'COMPLETED'), run('r3', 'WAITING_FOR_USER', 2, { pendingQuestion: question })]
    expect(resolveRunTargets(runs, null).viewed?.runId).toBe('r3')
    const pinned = resolveRunTargets(runs, 'r1')
    expect(pinned.viewed?.runId).toBe('r1')
    // 活动目标独立于所看轮
    expect(pinned.active?.runId).toBe('r3')
    expect(pinned.waiting?.runId).toBe('r3')
  })

  it('已完成的 VIEWED 不再是活动轮，活动状态只看真实非终态轮', () => {
    const runs = [run('r1', 'COMPLETED'), run('r2', 'RUNNING')]
    const targets = resolveRunTargets(runs, 'r1')
    expect(targets.active?.runId).toBe('r2')
    expect(targets.activeRuns.map((item) => item.runId)).toEqual(['r2'])
    expect(targets.waiting).toBeNull()
  })

  it('多个未决轮不自动选目标，等待目标也只在唯一时给出', () => {
    const runs = [
      run('r1', 'QUEUED'),
      run('r2', 'WAITING_FOR_USER', 1, { pendingQuestion: question }),
      run('r3', 'WAITING_FOR_USER', 1, { pendingQuestion: question })
    ]
    const targets = resolveRunTargets(runs, null)
    expect(targets.activeRuns).toHaveLength(3)
    expect(targets.active).toBeNull()
    expect(targets.waiting).toBeNull()
    // 用户明确选择后才给出活动目标
    const selected = resolveRunTargets(runs, null, 'r2')
    expect(selected.active?.runId).toBe('r2')
    expect(selected.waiting?.runId).toBe('r2')
    expect(runPermissions({
      ...selected, busy: false, hasDraft: true, hasReplyDraft: true, investigationEnabled: true
    })).toMatchObject({ canSend: false, canInvestigate: false, canCancel: true, canReply: true })
  })

  it('取消/补答/深入调查/普通发送的许可条件与目标一一对应', () => {
    const runs = [run('done', 'COMPLETED')]
    const idle = resolveRunTargets(runs, null)
    const idlePermissions = runPermissions({
      viewed: idle.viewed, activeRuns: idle.activeRuns, active: idle.active, waiting: idle.waiting,
      busy: false, hasDraft: true, hasReplyDraft: false, investigationEnabled: true
    })
    expect(idlePermissions).toMatchObject({ canSend: true, canInvestigate: true, canCancel: false, canReply: false })

    const waitingRuns = [run('done', 'COMPLETED'), run('w', 'WAITING_FOR_USER', 2, { pendingQuestion: question })]
    const waiting = resolveRunTargets(waitingRuns, 'done')
    const waitingPermissions = runPermissions({
      viewed: waiting.viewed, activeRuns: waiting.activeRuns, active: waiting.active, waiting: waiting.waiting,
      busy: false, hasDraft: true, hasReplyDraft: true, investigationEnabled: true
    })
    // 查看历史期间：普通发送与深入调查都不可用，取消与补答指向活动等待轮
    expect(waitingPermissions).toMatchObject({ canSend: false, canInvestigate: false, canCancel: true, canReply: true })

    const running = resolveRunTargets([run('done', 'COMPLETED'), run('x', 'RUNNING')], 'done')
    expect(runPermissions({
      viewed: running.viewed, activeRuns: running.activeRuns, active: running.active, waiting: running.waiting,
      busy: false, hasDraft: true, hasReplyDraft: false, investigationEnabled: true
    })).toMatchObject({ canSend: false, canInvestigate: false, canCancel: true, canReply: false })
  })

  it('多个未决轮时普通发送、深入调查与取消全部暂停，草稿保留', () => {
    const runs = [run('a', 'QUEUED'), run('b', 'RUNNING')]
    const targets = resolveRunTargets(runs, null)
    const permissions = runPermissions({
      viewed: targets.viewed, activeRuns: targets.activeRuns, active: targets.active, waiting: targets.waiting,
      busy: false, hasDraft: true, hasReplyDraft: false, investigationEnabled: true
    })
    expect(permissions.canSend).toBe(false)
    expect(permissions.canInvestigate).toBe(false)
    expect(permissions.canCancel).toBe(false)
    expect(permissions.sendBlockedReason).toContain('多个未决轮')
  })

  it('pickActiveRun 优先采用用户显式选择，失效时回落到唯一未决轮', () => {
    const runs = [run('a', 'QUEUED'), run('done', 'COMPLETED')]
    expect(pickActiveRun(runs, 'a')?.runId).toBe('a')
    expect(pickActiveRun(runs, 'missing')?.runId).toBe('a')
  })
})

describe('团队按钮与处理函数共用角色许可',()=>{
  const active = {runId:'active',status:'WAITING_FOR_USER',executionActorUserId:'initiator',pendingQuestion:{pendingQuestionId:'q',questionVersion:1}} as RunView
  const input={viewed:active,activeRuns:[active],active,waiting:active,busy:false,hasDraft:true,hasReplyDraft:true,investigationEnabled:false}
  it('只读即使存在草稿也不可发送、补答、取消',()=>{
    expect(runPermissions({...input,readOnly:true,actorUserId:'initiator'})).toMatchObject({canSend:false,canReply:false,canCancel:false,canInvestigate:false})
  })
  it('普通同事可补答但不能取消他人轮，管理员可取消',()=>{
    expect(runPermissions({...input,actorUserId:'colleague'})).toMatchObject({canReply:true,canCancel:false})
    expect(runPermissions({...input,actorUserId:'colleague',administrator:true}).canCancel).toBe(true)
    expect(runPermissions({...input,actorUserId:'initiator'}).canCancel).toBe(true)
  })
})
