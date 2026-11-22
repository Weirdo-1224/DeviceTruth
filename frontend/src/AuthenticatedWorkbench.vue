<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { clearAccountDrafts, readDraft, writeDraft } from './privateDrafts'
import App from './App.vue'
import UiIcon from './components/UiIcon.vue'
import { ApiError, clearAuthentication, loadCsrf, authenticate, authAction, currentAccount, productRequest, listWorkspaces, logout, setAccessContext, workspaceRequest, type AccountView, type WorkspaceView } from './api'
const account = ref<AccountView | null>(null)
const spaces = ref<WorkspaceView[]>([])
const selected = ref<WorkspaceView | null>(null)
const memberNames = ref<Record<string,string>>({})
const mode = ref<'login' | 'register' | 'recover' | 'reset'>('login')
const email = ref(''), password = ref(''), displayName = ref(''), confirmPassword = ref('')
const initializing = ref(false)
const error = ref(''), notice = ref(''), loading = ref(true), busy = ref(false)
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
const token = new URLSearchParams(location.hash.slice(1)).get('reset-token')
if (token) { mode.value = 'reset'; history.replaceState(null, '', location.pathname) }
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
async function submit(): Promise<void> {
  if (busy.value) return
  busy.value = true; error.value = ''; notice.value = ''
  try {
    if (mode.value === 'recover') {
      await authAction('password-reset-requests', { email: email.value })
      notice.value = '如果邮箱已注册，将尝试发送重置邮件。未收到可再次申请。'
    } else if (mode.value === 'reset') {
      await authAction('password-resets', { token, password: password.value, confirmPassword: confirmPassword.value })
      clearIdentity(); password.value = ''; confirmPassword.value = ''; mode.value = 'login'; notice.value = '密码已重置，请重新登录。'
    } else {
      account.value = await authenticate(mode.value, { email: email.value, password: password.value, ...(mode.value === 'register' ? { displayName: displayName.value } : {}) })
      password.value = ''; await loadSpaces()
    }
  } catch (e) { error.value = e instanceof Error ? e.message : '操作失败，请重试' }
  finally { busy.value = false }
}
function clearIdentity(): void {
  if (account.value) clearAccountDrafts(account.value.userId)
  clearAuthentication(); selected.value = null; account.value = null; spaces.value = []; memberNames.value = {}; password.value = ''; confirmPassword.value = ''; mode.value = 'login'; error.value = ''; notice.value = ''
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
  try { account.value = await currentAccount(); await loadSpaces() }
  catch(e) { if (!(e instanceof ApiError && e.status === 401)) error.value = e instanceof Error ? e.message : '无法恢复登录状态' }
  finally { loading.value = false }
})
onUnmounted(() => { clearInterval(membershipTimer); window.removeEventListener('devicetruth-auth-expired', clearIdentity); window.removeEventListener('devicetruth-access-revoked', refreshMembership) })
</script>
<template>
  <p v-if="loading" class="auth-loading" role="status">正在恢复登录状态…</p>
  <section v-else-if="!account || mode === 'reset'" class="auth-layout" aria-labelledby="auth-title">
    <div class="auth-entry">
      <header class="auth-brand"><span class="auth-logo"><UiIcon name="target" /></span><div><strong>DeviceTruth</strong><span>AI 设备检修工作台</span></div></header>
      <div class="auth-form">
        <header class="auth-heading">
          <h1 id="auth-title">{{ mode === 'register' ? '创建你的账号' : mode === 'recover' ? '找回密码' : mode === 'reset' ? '设置新密码' : '登录检修工作台' }}</h1>
          <p>{{ mode === 'register' ? '创建个人空间，按需加入团队协作。' : mode === 'recover' ? '输入注册邮箱，申请密码重置邮件。' : mode === 'reset' ? '设置新密码后，重新登录工作台。' : '继续你的设备调查与团队协作。' }}</p>
        </header>
        <form @submit.prevent="submit" :aria-busy="busy">
          <label v-if="mode === 'register'" class="auth-field">显示名称<input v-model="displayName" autocomplete="name" required maxlength="100" :disabled="busy"></label>
          <label v-if="mode !== 'reset'" class="auth-field">邮箱<input v-model="email" type="email" autocomplete="username" required :disabled="busy"></label>
          <div v-if="mode !== 'recover'" class="auth-field">
            <div class="auth-field-heading"><label for="auth-password">{{ mode === 'reset' ? '新密码' : '密码' }}</label><button v-if="mode === 'login'" type="button" class="auth-link" :disabled="busy" @click="mode = 'recover'">忘记密码？</button></div>
            <input id="auth-password" v-model="password" type="password" :autocomplete="mode === 'login' ? 'current-password' : 'new-password'" required minlength="12" maxlength="128" :disabled="busy" :aria-describedby="mode !== 'login' ? 'auth-password-hint' : undefined">
            <small v-if="mode !== 'login'" id="auth-password-hint">使用 12–128 个字符。</small>
          </div>
          <label v-if="mode === 'reset'" class="auth-field">再次输入密码<input v-model="confirmPassword" type="password" autocomplete="new-password" required :disabled="busy"></label>
          <p v-if="notice" class="auth-notice" role="status">{{ notice }}</p>
          <p v-if="error" class="auth-error" role="alert">{{ error }}</p>
          <button class="auth-submit" :disabled="busy">{{ busy ? '正在处理…' : mode === 'register' ? '注册并进入个人空间' : mode === 'recover' ? '申请重置邮件' : mode === 'reset' ? '更新密码并返回登录' : '登录' }}</button>
        </form>
        <nav class="auth-secondary" aria-label="账号操作">
          <template v-if="mode === 'login'">还没有账号？<button type="button" class="auth-link" :disabled="busy" @click="mode = 'register'">创建账号</button></template>
          <template v-else-if="mode === 'register'">已有账号？<button type="button" class="auth-link" :disabled="busy" @click="mode = 'login'">返回登录</button></template>
          <button v-else type="button" class="auth-link" :disabled="busy" @click="mode = 'login'">返回登录</button>
        </nav>
      </div>
    </div>
  </section>
  <section v-else-if="!selected" class="auth-form auth-workspace-loading" role="status"><h2>正在进入工作区</h2><p>{{ initializing ? '正在读取授权空间…' : '登录已成功，但空间尚未加载完成。' }}</p><button class="auth-submit" :disabled="initializing" @click="retryWorkspace">重新加载空间</button><p role="alert">{{ error }}</p></section>
  <App v-else :account="account" :space="selected" :spaces="spaces" :member-names="memberNames" :read-only="selected.role === 'VIEWER'" :actor-id="account.userId" :administrator="selected.role === 'ADMIN'"
    :global-error="error" :notice="notice" :invitation-token="invitationToken" :key="account.userId + ':' + selected.workspaceId + ':' + selected.role"
    @select-space="selectSpaceById" @logout="signOut" @accept-invitation="acceptInvitation" @members="names => memberNames = names" @changed="refreshMembership" @workspace-created="workspaceCreated" />
</template>
<style scoped>
.auth-layout, .auth-loading { min-height: 100dvh; background: #f7f9fd; color: #18253b; }
.auth-layout { display: grid; place-items: center; padding: 40px 20px; }
.auth-loading { display: grid; place-items: center; margin: 0; font-size: 14px; }
.auth-entry { width: min(100%, 440px); }
.auth-brand { display: flex; justify-content: center; align-items: center; gap: 12px; margin-bottom: 28px; }
.auth-logo { display: grid; place-items: center; flex: none; width: 36px; height: 40px; border-radius: 11px; background: #245de0; color: white; font-size: 24px; }
.auth-brand strong { display: block; font-size: 22px; letter-spacing: -.6px; }
.auth-brand div > span { display: block; margin-top: 2px; font-size: 12px; color: #65748b; }
.auth-form { width: 100%; padding: 32px; border: 1px solid #e1e7f0; border-radius: 14px; background: white; box-shadow: 0 8px 32px #2a4b850a; }
.auth-heading { margin-bottom: 28px; text-align: center; }
.auth-heading h1 { margin: 0 0 10px; font-size: 25px; font-weight: 650; letter-spacing: -.5px; line-height: 1.4; }
.auth-heading p { margin: 0; color: #65748b; font-size: 14px; line-height: 1.6; }
.auth-form form { display: grid; gap: 20px; }
.auth-field { display: grid; gap: 8px; font-size: 14px; color: #34445d; }
.auth-field-heading { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.auth-form input { min-width: 0; width: 100%; min-height: 44px; padding: 10px 12px; border: 1px solid #d6deeb; border-radius: 8px; background: #fff; color: #18253b; font: inherit; }
.auth-form input:hover:enabled { border-color: #b6c5dc; }
.auth-form input:focus-visible { border-color: #245de0; outline: 2px solid #245de033; outline-offset: 2px; }
.auth-field small { font-size: 12px; line-height: 1.5; color: #65748b; }
.auth-submit { display: block; width: 100%; min-height: 44px; padding: 11px 16px; border: 1px solid #245de0; border-radius: 8px; background: #245de0; color: white; cursor: pointer; font: inherit; font-size: 14px; font-weight: 600; }
.auth-submit:hover:enabled { background: #194bbb; border-color: #194bbb; }
.auth-link { padding: 2px 0; border: 0; background: none; color: #245de0; font: inherit; font-size: 13px; cursor: pointer; }
.auth-link:hover:enabled { text-decoration: underline; }
.auth-submit:focus-visible, .auth-link:focus-visible { outline: 2px solid #245de0; outline-offset: 4px; }
.auth-submit:disabled, .auth-link:disabled { opacity: .55; cursor: not-allowed; }
.auth-secondary { display: flex; justify-content: center; align-items: baseline; flex-wrap: wrap; gap: 6px; margin-top: 24px; padding-top: 22px; border-top: 1px solid #edf0f6; font-size: 13px; color: #65748b; }
.auth-notice, .auth-error { padding: 11px 12px; margin: 0; border-radius: 8px; font-size: 13px; line-height: 1.6; overflow-wrap: anywhere; }
.auth-notice { background: #edf3ff; color: #245cc5; }
.auth-error, [role=alert] { background: #fff2f0; color: #b33636; }
.auth-workspace-loading { max-width: 440px; margin: 12vh auto; color: #18253b; }
.auth-workspace-loading p { color: #65748b; font-size: 14px; line-height: 1.6; }
.auth-workspace-loading h2 { margin: 0 0 12px; font-size: 22px; }
@media (max-width: 480px) { .auth-layout { padding: 24px 16px; } .auth-form { padding: 28px 24px; } .auth-heading h1 { font-size: 23px; } .auth-workspace-loading { width: calc(100% - 32px); } }
@media print { .auth-layout, .auth-workspace-loading { display: none; } }
</style>
