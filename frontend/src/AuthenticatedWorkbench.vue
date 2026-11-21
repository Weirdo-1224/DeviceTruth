<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { clearAccountDrafts, readDraft, writeDraft } from './privateDrafts'
import App from './App.vue'
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
  <section v-else-if="!account || mode === 'reset'" class="auth-layout">
    <div class="auth-story"><div class="auth-brand"><span>D</span><strong>DeviceTruth</strong></div><div><h1>把一个人的排查，<br>变成团队可接续的调查。</h1><p>当前判断、资料依据、下一步，<br>围绕同一项设备问题清楚展开。</p><ul><li>01　个人排查与团队协作，空间分明</li><li>02　现场补充有作者，分析过程可追溯</li><li>03　讨论、调查附件与知识库各有归属</li></ul></div><small>面向一线维修人员与设备工程师</small></div>
    <div class="auth-form"><h2>{{ mode === 'register' ? '创建你的账号' : mode === 'recover' ? '找回密码' : mode === 'reset' ? '设置新密码' : '登录 DeviceTruth' }}</h2><p>{{ mode === 'register' ? '先拥有个人空间，再按需创建或加入团队。' : '回到你的调查，或与同事继续排查。' }}</p>
      <nav aria-label="账号操作"><button type="button" @click="mode = 'login'">登录</button><button type="button" @click="mode = 'register'">注册</button><button type="button" @click="mode = 'recover'">找回密码</button></nav>
      <form @submit.prevent="submit">
        <label v-if="mode === 'register'">显示名称<input v-model="displayName" autocomplete="name" required maxlength="100"></label>
        <label v-if="mode !== 'reset'">邮箱<input v-model="email" type="email" autocomplete="username" required></label>
        <label v-if="mode !== 'recover'">密码<input v-model="password" type="password" :autocomplete="mode === 'login' ? 'current-password' : 'new-password'" required minlength="12" maxlength="128"></label>
        <label v-if="mode === 'reset'">再次输入密码<input v-model="confirmPassword" type="password" autocomplete="new-password" required></label>
        <button class="auth-submit" :disabled="busy">{{ busy ? '正在处理…' : mode === 'register' ? '注册并进入个人空间' : mode === 'recover' ? '申请重置邮件' : mode === 'reset' ? '更新密码并返回登录' : '登录' }}</button>
      </form><p role="status">{{ notice }}</p><p role="alert">{{ error }}</p><small>会话由服务器 Cookie 保存，浏览器不存储口令或会话令牌。</small>
    </div>
  </section>
  <section v-else-if="!selected" class="auth-form auth-workspace-loading" role="status"><h2>正在进入工作区</h2><p>{{ initializing ? '正在读取授权空间…' : '登录已成功，但空间尚未加载完成。' }}</p><button class="auth-submit" :disabled="initializing" @click="retryWorkspace">重新加载空间</button><p role="alert">{{ error }}</p></section>
  <App v-else :account="account" :space="selected" :spaces="spaces" :member-names="memberNames" :read-only="selected.role === 'VIEWER'" :actor-id="account.userId" :administrator="selected.role === 'ADMIN'"
    :global-error="error" :notice="notice" :invitation-token="invitationToken" :key="account.userId + ':' + selected.workspaceId + ':' + selected.role"
    @select-space="selectSpaceById" @logout="signOut" @accept-invitation="acceptInvitation" @members="names => memberNames = names" @changed="refreshMembership" @workspace-created="workspaceCreated" />
</template>
<style scoped>
.auth-loading { padding: 32px; }
.auth-layout { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); min-height: 100dvh; background: white; }
.auth-story { padding: 43px 40px; display: flex; flex-direction: column; justify-content: space-between; gap: 40px; background: #eef3fa; color: #202b3d; }
.auth-brand { display: flex; align-items: center; gap: 10px; font-size: 18px; }
.auth-brand span { display: grid; place-items: center; width: 30px; height: 30px; border-radius: 6px; background: #203047; color: white; }
.auth-story h1 { margin: 0 0 15px; font-size: 31px; line-height: 1.5; }
.auth-story p { color: #60718b; }
.auth-story ul { padding: 0; margin: 30px 0 0; list-style: none; color: #425a7a; font-size: 13px; line-height: 2.4; }
.auth-story small { color: #60718b; }
.auth-form { max-width: 480px; width: 100%; margin: auto; padding: 52px 38px; }
.auth-form h2 { margin-bottom: 8px; }
.auth-form > p { color: #637084; }
.auth-form nav { display: flex; gap: 14px; margin: 18px 0; }
.auth-form nav button { border: 0; background: none; color: #245cc5; cursor: pointer; padding: 4px 0; }
.auth-form form, .auth-form label { display: grid; gap: 10px; }
.auth-form label { margin-top: 15px; font-size: 13px; }
.auth-form input { padding: 10px 11px; border: 1px solid #cbd4df; border-radius: 5px; font: inherit; }
.auth-submit { margin-top: 18px; padding: 10px 14px; border: 1px solid #245cc5; border-radius: 5px; background: #245cc5; color: white; cursor: pointer; font: inherit; }
.auth-form small { display: block; margin-top: 22px; color: #637084; }
[role=alert] { color: #b33636; }
.auth-workspace-loading { max-width: 520px; min-height: 70dvh; }
@media(max-width:600px) { .auth-layout { grid-template-columns: minmax(0, 1fr); } .auth-story { padding: 24px; gap: 20px; } .auth-story h1 { font-size: 25px; } .auth-story ul, .auth-story > small { display: none; } .auth-form { padding: 28px 24px; } }
@media print { .auth-layout, .auth-workspace-loading { display: none; } }
</style>
