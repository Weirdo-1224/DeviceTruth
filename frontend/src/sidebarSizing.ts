/** 侧栏布局偏好；不包含身份凭据或业务数据。 */
export const SIDEBAR_MIN = 200
export const SIDEBAR_MAX = 380
export const SIDEBAR_DEFAULT = 224
export const SIDEBAR_MOBILE_BREAKPOINT = 760

export function sidebarMaximum(viewportWidth: number): number {
  return Math.min(SIDEBAR_MAX, Math.max(SIDEBAR_MIN, viewportWidth - 520))
}

export function clampSidebarWidth(value: number, viewportWidth = Infinity): number {
  return Math.round(Math.min(sidebarMaximum(viewportWidth), Math.max(SIDEBAR_MIN, Number.isFinite(value) ? value : SIDEBAR_DEFAULT)))
}

const storageKey = (accountId: string) => 'devicetruth.sidebar-width:' + accountId

export function loadSidebarWidth(accountId: string): number {
  try {
    const saved = localStorage.getItem(storageKey(accountId))
    return clampSidebarWidth(saved?.trim() ? Number(saved) : SIDEBAR_DEFAULT)
  } catch { return SIDEBAR_DEFAULT }
}

export function saveSidebarWidth(accountId: string, width: number): void {
  try { localStorage.setItem(storageKey(accountId), String(clampSidebarWidth(width))) }
  catch { /* 存储不可用时仍可在当前页面调整宽度。 */ }
}
