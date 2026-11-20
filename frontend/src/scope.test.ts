import { describe, expect, it } from 'vitest'
import { scopedRunKey, createScopedLoader, mergeRunList, mergeRunView } from './scope'
import { createScheduledReads } from './scheduler'

describe('所看轮作用域', () => {
  it('切轮后旧代次的回调一律丢弃，A→B→A 也不会覆盖较新读取', () => {
    const loader = createScopedLoader()
    const first = loader.issue('A')
    const second = loader.issue('B')
    const third = loader.issue('A')
    expect(loader.isCurrent(first, 'A')).toBe(false)
    expect(loader.isCurrent(second, 'B')).toBe(false)
    expect(loader.isCurrent(third, 'A')).toBe(true)
    // 作用域键不一致即使代次最新也拒绝
    expect(loader.isCurrent(third, 'B')).toBe(false)
  })

  it('同一轮同版本合并允许字段补齐，旧状态不得覆盖较新状态', () => {
    const base = { runId: 'r1', status: 'WAITING_FOR_USER', version: 3 }
    expect(mergeRunView(base, { runId: 'r1', status: 'WAITING_FOR_USER', version: 3 })).toBe(true)
    expect(mergeRunView(base, { runId: 'r1', status: 'RUNNING', version: 2 })).toBe(false)
    expect(mergeRunView(base, { runId: 'r1', status: 'RUNNING', version: 4 })).toBe(true)
    expect(mergeRunView(base, { runId: 'r2', status: 'QUEUED', version: 0 })).toBe(true)
  })

  it('迟到的会话快照保留新版问题和未出现在快照中的新轮', () => {
    const waiting = { runId: 'waiting', status: 'WAITING_FOR_USER', version: 3 }
    const accepted = { runId: 'accepted', status: 'QUEUED', version: 0 }
    const done = { runId: 'done', status: 'COMPLETED', version: 5 }
    expect(mergeRunList([waiting, accepted], [done, { ...waiting, status: 'RUNNING', version: 2 }]))
      .toEqual([done, waiting, accepted])
  })
})

describe('合并式回读调度', () => {
  it('同一 key 的在途任务被合并，完成后可再次调度', async () => {
    const reads = createScheduledReads()
    let started = 0
    let release: (() => void) | null = null
    const task = () => {
      started += 1
      return new Promise<void>((resolve) => { release = resolve })
    }
    const first = reads.run('resync|r1', task)
    const second = reads.run('resync|r1', task)
    expect(started).toBe(1)
    expect(first).toBe(second)
    expect(reads.pending('resync|r1')).toBe(true)
    release!()
    await first
    expect(reads.pending('resync|r1')).toBe(false)
    const third = reads.run('resync|r1', async () => { started += 1 })
    await third
    expect(started).toBe(2)
  })
})

it('所看来源键包含账号空间调查Run版本',()=>{
  const first=scopedRunKey('account:team','case',{runId:'r',version:1})
  const loader=createScopedLoader(),token=loader.issue(first)
  expect(loader.isCurrent(token,scopedRunKey('account:team','case',{runId:'r',version:2}))).toBe(false)
  expect(loader.isCurrent(token,scopedRunKey('other:team','case',{runId:'r',version:1}))).toBe(false)
  expect(loader.isCurrent(token,first)).toBe(true)
})
