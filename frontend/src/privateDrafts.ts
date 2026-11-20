import { ref, watch, type Ref } from 'vue'
/** 私人草稿仅存当前浏览器会话，键绑定账号/空间/调查/输入目标。 */
export function draftKey(context: string, conversation: string | null, target = 'free'): string {
  return 'devicetruth.draft:' + context + ':' + (conversation ?? 'new') + ':' + target
}
export function readDraft(key: string): string {
  try { return sessionStorage.getItem(key) ?? '' } catch { return '' }
}
export function writeDraft(key: string, value: string): void {
  try { if (value) sessionStorage.setItem(key, value); else sessionStorage.removeItem(key) } catch { /* 存储被禁时仍可在当前页面编辑 */ }
}
export function clearAccountDrafts(accountId: string): void {
  try {
    for (const key of Object.keys(sessionStorage)) if (key.startsWith('devicetruth.draft:' + accountId + ':')) sessionStorage.removeItem(key)
  } catch { /* 不影响服务端退出 */ }
}

export function usePrivateDraft(key: Readonly<Ref<string>>) {
  const value = ref(readDraft(key.value))
  watch(value, text => writeDraft(key.value, text), { flush: 'sync' })
  watch(key, next => { value.value = readDraft(next) }, { flush: 'sync' })
  return value
}

export function listDrafts(prefix: string): { key: string; text: string }[] {
  try { return Object.keys(sessionStorage).filter(key => key.startsWith(prefix)).map(key => ({key,text:readDraft(key)})).filter(item => item.text) } catch { return [] }
}
