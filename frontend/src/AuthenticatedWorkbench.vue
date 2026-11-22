<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { clearAccountDrafts, readDraft, writeDraft } from './privateDrafts'
import App from './App.vue'
import LoginEntry from './components/LoginEntry.vue'
import { type LoginRealm, loginTarget } from './loginRouting'
import { ApiError, clearAuthentication, loadCsrf, currentAccount, productRequest, listWorkspaces, logout, setAccessContext, workspaceRequest, type AccountView, type WorkspaceView } from './api'
const account = ref<AccountView | null>(null)
const spaces = ref<WorkspaceView[]>([])
const selected = ref<WorkspaceView | null>(null)
const memberNames = ref<Record<string,string>>({})
const resetting = ref(new URLSearchParams(location.hash.slice(1)).has('reset-token'))
const initializing = ref(false)
const error = ref(''), notice = ref(''), loading = ref(true)
let invitationToken = new URLSearchParams(location.hash.slice(1)).get('invitation-token')
if (invitationToken) history.replaceState(null, '', location.pathname)
let memberGeneration = 0
async function loadMemberNames(space: WorkspaceView | null): Promise<void> {
  const generation = ++memberGeneration
  memberNames.value = {}
  if (!space) return
  try {
    const members = await workspaceRequest<{userId: string; displayName: string}[]>(`/${space.workspaceId}/members`)
    if (generation === memberGeneration && selected.value?.workspaceId === space.workspaceId) memberNames.value = Object.fromEntries(members.map(member => [member.userId, member.displayName]))
  } catch { /* 页面仍可使用，成员管理页会显示具体读取错误 */ }
}
async function acceptInvitation(): Promise<void> {
  try { await productRequest('invitations/accept', 'POST', { token: invitationToken }); await loadSpaces(); invitationToken = null; notice.value = '已加入团队，请选择团队空间。' }
  catch(e) { error.value = e instanceof Error ? e.message : '邀请接受失败，请核对登录邮箱或联系管理员重新邀请' }
}
async function loadSpaces(): Promise<void> {
  initializing.value = true
  try {
    await loadCsrf()
    spaces.value = await listWorkspaces()
    const remembered = readDraft('devicetruth.workspace:' + account.value?.userId)
    selectSpace(spaces.value.find(s => s.workspaceId === remembered) ?? spaces.value[0] ?? null)
    error.value = ''
  } finally { initializing.value = false }
}
async function retryWorkspace(): Promise<void> { try { await loadSpaces() } catch(e) { error.value = e instanceof Error ? e.message : '读取空间失败' } }
function selectSpace(space: WorkspaceView | null): void {
  selected.value = space
  if (account.value && space) writeDraft('devicetruth.workspace:' + account.value.userId, space.workspaceId)
  setAccessContext(account.value?.userId ?? null, space?.workspaceId ?? null)
  void loadMemberNames(space)
}
function selectSpaceById(workspaceId: string): void { selectSpace(spaces.value.find(space => space.workspaceId === workspaceId) ?? null) }
async function signedIn(value: AccountView, realm: LoginRealm, target: string): Promise<void> {
  if (realm === 'ADMIN') { location.assign(loginTarget(realm, target)); return }
  account.value = value; resetting.value = false
  history.replaceState(null, '', loginTarget(realm, target))
  try { await loadSpaces() } catch (e) { error.value = e instanceof Error ? e.message : '读取空间失败' }
}
function clearIdentity(): void {
  if (account.value) clearAccountDrafts(account.value.userId)
  clearAuthentication(); selected.value = null; account.value = null; spaces.value = []; memberNames.value = {}; error.value = ''; notice.value = ''
}
async function signOut(): Promise<void> {
  try { await logout(); clearIdentity() } catch (e) { error.value = e instanceof Error ? e.message : '退出失败' }
}
let membershipTimer: ReturnType<typeof setInterval> | undefined
let checkingMembership = false
async function refreshMembership(): Promise<void> {
  if (!account.value || checkingMembership) return
  checkingMembership = true
  try {
    const latest = await listWorkspaces()
    const current = latest.find(s => s.workspaceId === selected.value?.workspaceId)
    spaces.value = latest
    if (!current || current.role !== selected.value?.role || current.name !== selected.value?.name) selectSpace(current ?? latest[0] ?? null)
    else selected.value = current
  } catch(e) { if (e instanceof ApiError && e.status === 401) clearIdentity() }
  finally { checkingMembership = false }
}
async function workspaceCreated(space: WorkspaceView): Promise<void> {
  spaces.value = await listWorkspaces(); selectSpace(space); notice.value = '团队已创建，调查、设备和知识库均为独立空空间。'
}
onMounted(async () => {
  window.addEventListener('devicetruth-auth-expired', clearIdentity)
  window.addEventListener('devicetruth-access-revoked', refreshMembership)
  membershipTimer = setInterval(() => { void refreshMembership() }, 10000)
  try { if (!resetting.value) { account.value = await currentAccount(); if (location.pathname === '/login') history.replaceState(null, '', '/'); await loadSpaces() } }
  catch(e) { if (!(e instanceof ApiError && e.status === 401)) error.value = e instanceof Error ? e.message : '无法恢复登录状态' }
  finally { loading.value = false }
})
onUnmounted(() => { clearInterval(membershipTimer); window.removeEventListener('devicetruth-auth-expired', clearIdentity); window.removeEventListener('devicetruth-access-revoked', refreshMembership) })
</script>
<template>
  <p v-if="loading" class="auth-loading" role="status">正在恢复登录状态…</p>
  <LoginEntry v-else-if="!account || resetting" initial-realm="USER" @authenticated="signedIn" />
  <section v-else-if="!selected" class="auth-form auth-workspace-loading" role="status"><h2>正在进入工作区</h2><p>{{ initializing ? '正在读取授权空间…' : '登录已成功，但空间尚未加载完成。' }}</p><button class="auth-submit" :disabled="initializing" @click="retryWorkspace">重新加载空间</button><p role="alert">{{ error }}</p></section>
  <App v-else :account="account" :space="selected" :spaces="spaces" :member-names="memberNames" :read-only="selected.role === 'VIEWER'" :actor-id="account.userId" :administrator="selected.role === 'ADMIN'"
    :global-error="error" :notice="notice" :invitation-token="invitationToken" :key="account.userId + ':' + selected.workspaceId + ':' + selected.role"
    @select-space="selectSpaceById" @logout="signOut" @accept-invitation="acceptInvitation" @members="names => memberNames = names" @changed="refreshMembership" @workspace-created="workspaceCreated" />
</template>
<style scoped>
.auth-loading { min-height: 100dvh; display: grid; place-items: center; margin: 0; background: #f7f9fd; color: #18253b; font-size: 16px; }
.auth-workspace-loading { width: min(440px, calc(100% - 32px)); box-sizing: border-box; margin: 12vh auto; padding: 32px; border: 1px solid #e1e7f0; border-radius: 14px; background: white; color: #18253b; }
.auth-workspace-loading h2 { margin: 0 0 12px; font-size: 22px; }.auth-workspace-loading p { color: #65748b; font-size: 16px; line-height: 1.6; }
.auth-submit { width: 100%; min-height: 44px; padding: 11px 16px; border: 0; border-radius: 8px; background: #245de0; color: white; font: inherit; cursor: pointer; }
.auth-submit:disabled { opacity: .55; }.auth-submit:focus-visible { outline: 2px solid #245de0; outline-offset: 4px; }
@media print { .auth-workspace-loading { display: none; } }
</style>
