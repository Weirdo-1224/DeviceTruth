export type LoginRealm = 'USER' | 'ADMIN'
export const loginHome = (realm: LoginRealm): string => realm === 'ADMIN' ? '/admin/users' : '/'
const adminPages = new Set(['/admin/users', '/admin/runs', '/admin/knowledge', '/admin/skills', '/admin/system'])

/** 只接受实际存在的端内页面；不接收 URL、编码路径或认证动作。 */
export function loginTarget(realm: LoginRealm, value: string | null | undefined): string {
  if (!value || /[%\\\u0000-\u0020?#]/.test(value)) return loginHome(realm)
  return (realm === 'ADMIN' ? adminPages.has(value) : value === '/') ? value : loginHome(realm)
}
export function loginAddress(realm: LoginRealm, target?: string): string {
  const base = realm === 'ADMIN' ? '/admin/login' : '/login'
  const safe = loginTarget(realm, target)
  return safe === loginHome(realm) ? base : `${base}?returnTo=${encodeURIComponent(safe)}`
}
export function entryTarget(realm: LoginRealm): string {
  const proposed = new URLSearchParams(location.search).get('returnTo')
  return loginTarget(realm, proposed ?? location.pathname)
}
