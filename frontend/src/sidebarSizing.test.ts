import { afterEach, describe, expect, it, vi } from 'vitest'
import { clampSidebarWidth, loadSidebarWidth, saveSidebarWidth, sidebarMaximum } from './sidebarSizing'

afterEach(() => vi.unstubAllGlobals())
describe('侧栏宽度与布局偏好', () => {
  it('限制到 200–380px 并取整', () => {
    expect(clampSidebarWidth(100)).toBe(200)
    expect(clampSidebarWidth(999)).toBe(380)
    expect(clampSidebarWidth(265.7)).toBe(266)
    expect(clampSidebarWidth(NaN)).toBe(224)
  })
  it('中等窗口为正文保留至少 520px', () => {
    expect(sidebarMaximum(800)).toBe(280)
    expect(clampSidebarWidth(380, 800)).toBe(280)
    expect(sidebarMaximum(1280)).toBe(380)
  })
  it.each([null, '', 'not-a-number', 'Infinity'])('无效偏好 %s 使用默认宽度', saved => {
    vi.stubGlobal('localStorage', { getItem: () => saved })
    expect(loadSidebarWidth('u')).toBe(224)
  })
  it('按账号保存，恢复合法数值且不读写其他存储', () => {
    const values = new Map<string, string>()
    vi.stubGlobal('localStorage', { getItem: (key: string) => values.get(key) ?? null, setItem: (key: string, value: string) => values.set(key, value) })
    saveSidebarWidth('a', 320)
    expect(loadSidebarWidth('a')).toBe(320)
    expect(loadSidebarWidth('b')).toBe(224)
    expect([...values.keys()]).toEqual(['devicetruth.sidebar-width:a'])
  })
  it('存储被禁不影响当前页面调整', () => {
    vi.stubGlobal('localStorage', { getItem: () => { throw Error('blocked') }, setItem: () => { throw Error('blocked') } })
    expect(loadSidebarWidth('u')).toBe(224)
    expect(() => saveSidebarWidth('u', 300)).not.toThrow()
  })
})
