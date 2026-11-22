import { describe, expect, it } from 'vitest'
import { loginAddress, loginTarget } from './loginRouting'

describe('两端登录返回路径', () => {
  it('保留实际同端页面并区分默认首页', () => {
    expect(loginTarget('ADMIN', '/admin/knowledge')).toBe('/admin/knowledge')
    expect(loginTarget('USER', '/')).toBe('/')
    expect(loginAddress('ADMIN', '/admin/skills')).toBe('/admin/login?returnTo=%2Fadmin%2Fskills')
    expect(loginAddress('USER')).toBe('/login')
  })
  it.each(['https://evil.test', '//evil.test', '/\\evil.test', '/%2f%2fevil.test', '/%252f%252fevil.test', '/admin/login', '/api/auth/logout', '/admin/unknown', '/admin/users?password=secret', '/admin/users#token', '/admin/users\n'])('管理端拒绝非法返回路径 %j', value => {
    expect(loginTarget('ADMIN', value)).toBe('/admin/users')
  })
  it('不能跨端跳转', () => {
    expect(loginTarget('USER', '/admin/knowledge')).toBe('/')
    expect(loginTarget('ADMIN', '/')).toBe('/admin/users')
  })
})
