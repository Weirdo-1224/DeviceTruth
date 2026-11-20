<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { clearAccountDrafts, readDraft, writeDraft } from './privateDrafts'
import App from './App.vue'
import WorkspaceAdministration from './components/WorkspaceAdministration.vue'
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
async function acceptInvitation() {
  try { await productRequest('invitations/accept', 'POST', { token: invitationToken }); await loadSpaces(); invitationToken = null; notice.value = '已加入团队，请选择团队空间。' }
  catch(e) { error.value = e instanceof Error ? e.message : '邀请接受失败，请核对登录邮箱或联系管理员重新邀请' }
}
const token = new URLSearchParams(location.hash.slice(1)).get('reset-token')
if (token) { mode.value = 'reset'; history.replaceState(null, '', location.pathname) }
async function loadSpaces() {
  initializing.value = true
  try {
  await loadCsrf()
  spaces.value = await listWorkspaces()
  const remembered = readDraft('devicetruth.workspace:' + account.value?.userId)
  selectSpace(spaces.value.find(s => s.workspaceId === remembered) ?? spaces.value[0] ?? null)
  error.value = ''
  } finally { initializing.value = false }
}
async function retryWorkspace() { try { await loadSpaces() } catch(e) { error.value = e instanceof Error ? e.message : '读取空间失败' } }
function selectSpace(space: WorkspaceView | null) {
  memberNames.value = {}
  selected.value = space
  if (account.value && space) writeDraft('devicetruth.workspace:' + account.value.userId, space.workspaceId)
  setAccessContext(account.value?.userId ?? null, space?.workspaceId ?? null)
}
async function submit() {
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
function clearIdentity() {
  if (account.value) clearAccountDrafts(account.value.userId)
  clearAuthentication(); selected.value = null; account.value = null; spaces.value = []; password.value = ''; confirmPassword.value = ''; mode.value = 'login'; error.value = ''; notice.value = ''
}
async function signOut() {
  try { await logout(); clearIdentity() } catch (e) { error.value = e instanceof Error ? e.message : '退出失败' }
}
let membershipTimer: ReturnType<typeof setInterval> | undefined
let checkingMembership = false
async function refreshMembership() {
  if (!account.value || checkingMembership) return
  checkingMembership = true
  try {
    const latest = await listWorkspaces()
    const current = latest.find(s => s.workspaceId === selected.value?.workspaceId)
    spaces.value = latest
    if (!current || current.role !== selected.value?.role) selectSpace(current ?? latest[0] ?? null)
    else selected.value = current
  } catch(e) { if (e instanceof ApiError && e.status === 401) clearIdentity() }
  finally { checkingMembership = false }
}
const teamName = ref('')
async function createTeam() {
  try {
    const space = await workspaceRequest<WorkspaceView>('', 'POST', { name: teamName.value })
    spaces.value = await listWorkspaces(); selectSpace(space); teamName.value = ''
  } catch(e) { error.value = e instanceof Error ? e.message : '创建失败' }
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
  <p v-if="loading" role="status">正在恢复登录状态…</p>
  <section v-else-if="!account || mode === 'reset'" class="account-card">
    <h1>DeviceTruth</h1><p>登录后，在个人或团队空间继续设备调查。</p>
    <nav aria-label="账号操作"><button @click="mode = 'login'">登录</button><button @click="mode = 'register'">注册</button><button @click="mode = 'recover'">找回密码</button></nav>
    <form @submit.prevent="submit">
      <label v-if="mode !== 'reset'">邮箱<input v-model="email" type="email" autocomplete="username" required></label>
      <label v-if="mode === 'register'">姓名<input v-model="displayName" autocomplete="name" required maxlength="100"></label>
      <label v-if="mode !== 'recover'">密码<input v-model="password" type="password" :autocomplete="mode === 'login' ? 'current-password' : 'new-password'" required minlength="12" maxlength="128"></label>
      <label v-if="mode === 'reset'">再次输入密码<input v-model="confirmPassword" type="password" autocomplete="new-password" required></label>
      <button :disabled="busy">{{ busy ? '正在处理…' : mode === 'register' ? '注册并进入' : mode === 'recover' ? '申请重置邮件' : mode === 'reset' ? '重置密码' : '登录' }}</button>
    </form><p role="status">{{ notice }}</p><p role="alert">{{ error }}</p>
  </section>
  <template v-else>
    <div class="workspace-chrome">
    <header class="account-bar">
      <strong>{{ account.displayName }}</strong>
      <label>当前空间 <select :value="selected?.workspaceId" @change="selectSpace(spaces.find(s => s.workspaceId === ($event.target as HTMLSelectElement).value) ?? null)"><option v-for="space in spaces" :key="space.workspaceId" :value="space.workspaceId">{{ space.name }} · {{ space.role === 'VIEWER' ? '只读' : space.kind === 'PERSONAL' ? '个人' : '团队' }}</option></select></label>
      <form @submit.prevent="createTeam"><input v-model="teamName" aria-label="新团队名称" placeholder="新团队名称" required maxlength="100"><button>创建团队</button></form>
      <button @click="signOut">退出登录</button><p v-if="error" role="alert">{{ error }}</p>
    </header>
    <p v-if="invitationToken">请确认以 {{ account.email }} 接受邮箱邀请。<button @click="acceptInvitation">确认加入团队</button></p><p role="status">{{ notice }}</p>
    <section v-if="!selected" class="account-card" role="status"><h2>正在进入工作区</h2><p>{{ initializing ? '正在读取授权空间…' : '登录已成功，但空间尚未加载完成。' }}</p><button :disabled="initializing" @click="retryWorkspace">重新加载空间</button></section>
    <WorkspaceAdministration @members="names => memberNames = names" @changed="refreshMembership" v-if="selected" :key="selected.workspaceId + selected.role" :space="selected" />
    </div>
    <App :member-names="memberNames" :read-only="selected?.role === 'VIEWER'" :actor-id="account.userId" :administrator="selected?.role === 'ADMIN'" v-if="selected" :key="account.userId + ':' + selected.workspaceId + ':' + selected.role" />
  </template>
</template>
<style scoped>
.account-card { max-width: 420px; margin: 8vh auto; padding: 32px; background: white; border: 1px solid #dce2e9; border-radius: 12px; }
.account-card form, .account-card label { display: grid; gap: 10px; margin-top: 18px; }
input, select, button { font: inherit; padding: 9px 12px; border: 1px solid #b9c4d0; border-radius: 6px; background: white; }
button { cursor: pointer; } button:focus-visible, input:focus-visible, select:focus-visible { outline: 2px solid #2468aa; outline-offset: 2px; }
nav, .account-bar, .account-bar form { display: flex; flex-wrap: wrap; align-items: center; gap: 12px; }
.account-bar { padding: 10px 20px; background: #f4f7fa; border-bottom: 1px solid #dce2e9; }
[role=alert] { color: #ad2633; }
@media(max-width:600px) { .account-card { margin: 16px; padding: 20px; } .account-bar { padding: 10px; } input,select { max-width: 100%; } }
@media print { .workspace-chrome { display:none !important; } }
</style>
