import { describe, expect, it } from 'vitest'
import design from './design.css?raw'
import workspace from './workspace.css?raw'
import pro from './pro.css?raw'
import admin from './admin/admin.css?raw'
import login from './components/LoginEntry.vue?raw'
import collaboration from './components/InvestigationCollaboration.vue?raw'
import report from './components/DiagnosisReport.vue?raw'

function contrast(a: string, b: string): number {
  const luminance = (hex: string) => {
    const rgb = hex.match(/[\da-f]{2}/gi)!.map(part => parseInt(part, 16) / 255)
      .map(value => value <= .04045 ? value / 12.92 : ((value + .055) / 1.055) ** 2.4)
    return rgb[0]! * .2126 + rgb[1]! * .7152 + rgb[2]! * .0722
  }
  const x = luminance(a), y = luminance(b)
  return (Math.max(x, y) + .05) / (Math.min(x, y) + .05)
}

describe('共享视觉源合同（不替代浏览器检查）', () => {
  it('用户与管理入口使用同一基线，页面不重定义主题', () => {
    expect(design).toContain('--control-height: 44px')
    expect(design).toContain('--font-small: 14px')
    expect(workspace).not.toMatch(/--(?:primary|text|bg|danger):/)
    expect(pro).not.toMatch(/--(?:primary|text|bg|danger):/)
    expect(admin).toContain('--admin-purple:var(--primary)')
  })
  it('共享样式不再用低于14px的业务字号', () => {
    for (const css of [design, workspace, pro, admin]) {
      expect(css).not.toMatch(/font-size:\s*(?:10|11|12|13)px/)
    }
  })
  it('语义文字在对应背景上的令牌对比度达到4.5', () => {
    for (const [foreground, background] of [
      ['#637084', '#f7f9fd'], ['#b33636', '#fff3f3'], ['#187a52', '#edf7f1'],
      ['#915b12', '#fff7e9'], ['#ffffff', '#245de0'], ['#59677b', '#edf0f6']
    ]) expect(contrast(foreground!, background!)).toBeGreaterThanOrEqual(4.5)
  })
  it('动画降级和禁用填充在共享入口可用', () => {
    expect(design).toContain('prefers-reduced-motion: reduce')
    expect(workspace).toContain('background: var(--disabled-bg)')
    expect(admin).toContain('button.primary:disabled')
  })
  it('原生字段提示关联与错误展示选择器存在', () => {
    expect(login).toContain('aria-describedby="loginentry-email-help"')
    expect(login).toContain('id="loginentry-email-help"')
    expect(collaboration).toContain('aria-describedby="publication-applicability-help"')
    expect(collaboration).toContain('id="publication-applicability-help"')
    expect(workspace).toContain(':user-invalid ~ .field-help')
  })
  it('报告沿用实际持久状态区分失败与取消', () => {
    expect(report).toContain(':data-run-status="run.status"')
    expect(report).toContain('[data-run-status="FAILED"] .report-state')
    expect(report).toContain('[data-run-status="CANCELLED"] .report-state')
  })
})
