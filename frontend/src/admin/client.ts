export interface Page<T> { items: T[]; nextCursor: string | null; snapshotAt: string }
export interface Feature { feature: string; state: 'READY' | 'PENDING_INTEGRATION' | 'UNCONFIGURED'; reasonCode: string | null }
export interface Access { userId: string; grantRevision: number; capabilities: string[]; features: Feature[] }
export interface Workspace { workspaceId: string; kind: string; name: string; capabilities: string[] }
export type Row = Record<string, unknown>
export class AdminError extends Error {
  constructor(public status: number, public code: string, public requestId: string | null) { super(code) }
}
export async function request<T>(path: string, options: { method?: string; body?: unknown; signal?: AbortSignal } = {}): Promise<T> {
  const method = options.method ?? 'GET'
  const headers: Record<string, string> = { Accept: 'application/json' }
  if (method !== 'GET') {
    const csrf = await fetch('/api/admin/auth/csrf', { credentials: 'same-origin', signal: options.signal })
    if (!csrf.ok) throw new AdminError(csrf.status, 'AUTHENTICATION_REQUIRED', null)
    const token = await csrf.json() as { headerName: string; token: string }
    headers[token.headerName] = token.token
    if (!(options.body instanceof FormData)) headers['Content-Type'] = 'application/json'
  }
  const response = await fetch(path, { method, headers, credentials: 'same-origin', signal: options.signal,
    body: options.body === undefined ? undefined : options.body instanceof FormData ? options.body : JSON.stringify(options.body) })
  const body = await response.json() as T & { code?: string; requestId?: string }
  if (!response.ok) throw new AdminError(response.status, body.code ?? 'ADMIN_REQUEST_FAILED', body.requestId ?? response.headers.get('X-Request-ID'))
  return body
}
export function queryPath(path: string, values: Record<string, string>): string {
  const query = new URLSearchParams(Object.entries(values).filter(([, value]) => value !== ''))
  return path + (query.size ? '?' + query.toString() : '')
}
/** 同时隔离账号、目标空间、筛选以及 A→B→A 的晚到响应。 */
export function createAdminScope() {
  let generation = 0
  let controller = new AbortController()
  return {
    next() { controller.abort(); controller = new AbortController(); const current = ++generation
      return { signal: controller.signal, current: () => current === generation && !controller.signal.aborted } },
    clear() { generation++; controller.abort() }
  }
}
export const messages: Record<string, string> = {
  AUTHENTICATION_REQUIRED: '登录已失效，请重新登录。', ACCOUNT_DISABLED: '账号已停用。',
  PLATFORM_ADMIN_REQUIRED: '无平台管理权限。团队管理员资格不能用于平台管理。',
  RESOURCE_NOT_FOUND: '目标不存在或没有该空间的管理权限。', INVALID_CURSOR: '分页已失效，请刷新列表。',
  INTEGRATION_NOT_READY: '此操作的执行接入尚未就绪。', STATE_VERSION_CONFLICT: '状态已变化，请刷新后重试。',
  LAST_PLATFORM_ADMIN: '至少保留一位有效平台管理员。', SKILL_HAS_UNRESOLVED_RUNS: '仍有未决任务使用此 Skill，请先按原产品权限处理任务。',
  SKILL_VERSION_UNAVAILABLE: '安装版本与当前加载版本不匹配。', EMAIL_ALREADY_REGISTERED: '此邮箱已注册，请在列表中定位。',
  ADMIN_AUDIT_UNAVAILABLE: '审计暂不可用，操作已回滚。', INVALID_TIME_RANGE: '请选择带时区、不超过 31 天的时间窗。',
  INVALID_ADMIN_INPUT: '请检查输入字段、筛选和分页参数。', INVALID_ACCOUNT_INPUT: '请填写有效邮箱、名称及 12–128 位密码。',
  INSTALLATION_ALREADY_EXISTS: '此版本已安装，请使用状态操作。', INVALID_CREDENTIALS: '邮箱或密码不正确。'
}
export function describe(error: unknown): string {
  return error instanceof AdminError ? (messages[error.code] ?? '请求失败，请稍后重试。') + (error.requestId ? ` 关联编号：${error.requestId}` : '') : '连接失败，请检查服务并重试。'
}
