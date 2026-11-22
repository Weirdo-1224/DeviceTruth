<script setup lang="ts">
import { computed, onUnmounted, ref } from 'vue'
import UiIcon from './UiIcon.vue'
import { ApiError, authenticate, authAction, currentAccount, loadCsrf, type AccountView } from '../api'
import { AdminError, createAdminScope, describe, request } from '../admin/client'
import { entryTarget, loginAddress, loginTarget, type LoginRealm } from '../loginRouting'

const props = defineProps<{ initialRealm: LoginRealm }>()
const emit = defineEmits<{ authenticated: [account: AccountView, realm: LoginRealm, target: string] }>()
const realm = ref<LoginRealm>(props.initialRealm)
const mode = ref<'login' | 'register' | 'recover' | 'reset'>('login')
const email = ref(''), password = ref(''), confirmPassword = ref(''), displayName = ref('')
const error = ref(''), notice = ref(''), busy = ref(false), restoring = ref(false)
const targets = { USER: entryTarget('USER'), ADMIN: entryTarget('ADMIN') }
const scope = createAdminScope()
const resetToken = props.initialRealm === 'USER' ? new URLSearchParams(location.hash.slice(1)).get('reset-token') : null
if (resetToken) { mode.value = 'reset'; history.replaceState(null, '', location.pathname + location.search) }
const title = computed(() => mode.value === 'register' ? '创建你的账号' : mode.value === 'recover' ? '找回密码' : mode.value === 'reset' ? '设置新密码' : realm.value === 'ADMIN' ? '登录平台管理端' : '登录检修工作台')
const submitLabel = computed(() => busy.value ? '正在处理…' : mode.value === 'register' ? '注册并进入个人空间' : mode.value === 'recover' ? '申请重置邮件' : mode.value === 'reset' ? '更新密码并返回登录' : realm.value === 'ADMIN' ? '登录管理端' : '登录工作台')

async function switchRealm(next: LoginRealm): Promise<void> {
  if (busy.value || next === realm.value) return
  const token = scope.next()
  realm.value = next; password.value = ''; confirmPassword.value = ''; mode.value = 'login'; error.value = ''; notice.value = ''
  history.replaceState(null, '', loginAddress(next, targets[next]))
  restoring.value = true
  try {
    const account = next === 'ADMIN' ? await request<AccountView>('/api/admin/auth/me', { signal: token.signal }) : await currentAccount()
    if (token.current()) emit('authenticated', account, next, targets[next])
  } catch (e) {
    if (token.current() && !(e instanceof ApiError && e.status === 401) && !(e instanceof AdminError && e.status === 401)) error.value = next === 'ADMIN' ? describe(e) : e instanceof Error ? e.message : '连接失败，请重试。'
  } finally { if (token.current()) restoring.value = false }
}
async function submit(): Promise<void> {
  if (busy.value || restoring.value) return
  busy.value = true; error.value = ''; notice.value = ''; const token = scope.next(); const selected = realm.value
  try {
    if (mode.value === 'recover') {
      await loadCsrf(); await authAction('password-reset-requests', { email: email.value })
      notice.value = '如果邮箱已注册，将尝试发送重置邮件。未收到可再次申请。'
    } else if (mode.value === 'reset') {
      await loadCsrf(); await authAction('password-resets', { token: resetToken, password: password.value, confirmPassword: confirmPassword.value })
      mode.value = 'login'; notice.value = '密码已重置，请重新登录。'
    } else {
      const account = selected === 'ADMIN'
        ? await request<AccountView>('/api/admin/auth/login', { method: 'POST', body: { email: email.value, password: password.value }, signal: token.signal })
        : await authenticate(mode.value, { email: email.value, password: password.value, ...(mode.value === 'register' ? { displayName: displayName.value } : {}) })
      if (token.current()) emit('authenticated', account, selected, loginTarget(selected, targets[selected]))
    }
  } catch (e) {
    if (!token.current()) return
    const code = e instanceof ApiError || e instanceof AdminError ? e.code : ''
    error.value = code === 'LOGIN_REALM_MISMATCH'
      ? selected === 'ADMIN' ? '该账号用于用户端，请切换到用户端登录。' : '这是管理账号，请切换到管理端登录。'
      : selected === 'ADMIN' ? describe(e) : e instanceof Error ? e.message : '连接失败，请重试。'
  } finally {
    password.value = ''; confirmPassword.value = ''
    if (token.current()) busy.value = false
  }
}
onUnmounted(() => { scope.clear(); password.value = ''; confirmPassword.value = '' })
</script>

<template>
  <section class="auth-layout" :aria-label="title">
    <div class="auth-entry">
      <a class="auth-brand" href="/" aria-label="DeviceTruth 首页"><span class="auth-logo"><UiIcon name="target" /></span><span><strong>DeviceTruth</strong><small>AI 设备检修工作台</small></span></a>
      <div class="auth-form">
        <div class="auth-switch" role="group" aria-label="选择登录端">
          <button type="button" :aria-pressed="realm === 'USER'" :disabled="busy" @click="switchRealm('USER')">用户端</button>
          <button type="button" :aria-pressed="realm === 'ADMIN'" :disabled="busy" @click="switchRealm('ADMIN')">管理端</button>
        </div>
        <header class="auth-heading"><h1>{{ title }}</h1><p>{{ mode === 'register' ? '创建账号，进入独立的个人空间。' : mode === 'recover' ? '使用注册邮箱申请密码重置。' : mode === 'reset' ? '更新密码后，请重新登录。' : realm === 'ADMIN' ? '使用专用管理账号，进入平台管理控制台。' : '继续你的设备调查与团队协作。' }}</p></header>
        <form @submit.prevent="submit">
          <label v-if="mode !== 'reset'" class="auth-field">邮箱<input id="loginentry-email" v-model="email" type="email" autocomplete="username" aria-describedby="loginentry-email-help" required :disabled="busy" /><small id="loginentry-email-help" class="field-help">请输入有效的邮箱地址。</small></label>
          <label v-if="mode === 'register'" class="auth-field">显示名称<input id="loginentry-displayName" v-model="displayName" autocomplete="nickname" maxlength="100" aria-describedby="loginentry-displayName-help" required :disabled="busy" /><small id="loginentry-displayName-help" class="field-help">请填写名称，不超过 100 个字符。</small></label>
          <div v-if="mode !== 'recover'" class="auth-field">
            <div class="auth-field-heading"><label for="entry-password">{{ mode === 'reset' ? '新密码' : '密码' }}</label><button v-if="realm === 'USER' && mode === 'login'" type="button" class="auth-link" :disabled="busy" @click="mode = 'recover'; password = ''; error = ''">忘记密码？</button></div>
            <input id="entry-password" v-model="password" type="password" :autocomplete="mode === 'register' || mode === 'reset' ? 'new-password' : 'current-password'" aria-describedby="loginentry-password-help" required minlength="12" maxlength="128" :disabled="busy" /><small id="loginentry-password-help" :class="mode === 'register' || mode === 'reset' ? 'password-help' : 'field-help'">请输入 12–128 个字符的密码。</small>
          </div>
          <label v-if="mode === 'reset'" class="auth-field">再次输入密码<input id="loginentry-confirmPassword" v-model="confirmPassword" type="password" autocomplete="new-password" aria-describedby="loginentry-confirmPassword-help" required :disabled="busy" /><small id="loginentry-confirmPassword-help" class="field-help">请填写此字段。</small></label>
          <p v-if="notice" class="auth-notice" role="status">{{ notice }}</p><p v-if="error" class="auth-error" role="alert">{{ error }}</p>
          <p v-if="restoring" class="auth-restoring" role="status">正在检查{{ realm === 'ADMIN' ? '管理端' : '用户端' }}登录状态…</p>
          <button class="auth-submit" :disabled="busy || restoring">{{ submitLabel }}</button>
        </form>
        <nav v-if="realm === 'USER'" class="auth-secondary" aria-label="账号操作">
          <template v-if="mode === 'login'">还没有账号？<button class="auth-link" :disabled="busy" @click="mode = 'register'; password = ''; error = ''">创建账号</button></template>
          <template v-else-if="mode === 'register'">已有账号？<button class="auth-link" :disabled="busy" @click="mode = 'login'; password = ''; error = ''">返回登录</button></template>
          <button v-else class="auth-link" :disabled="busy" @click="mode = 'login'; password = ''; error = ''">返回登录</button>
        </nav>
        <p v-else class="auth-admin-note">管理账号由平台维护人员开通。如需设置或恢复密码，请联系维护人员。</p>
      </div>
    </div>
  </section>
</template>

<style scoped>
.auth-layout { min-height: 100dvh; display: grid; place-items: center; padding: 36px 20px; background: var(--bg); color: var(--text); }
.auth-entry { width: min(100%, 440px); }
.auth-brand { display: flex; justify-content: center; align-items: center; gap: 12px; margin-bottom: 28px; color: inherit; text-decoration: none; }
.auth-logo { display: grid; place-items: center; width: 40px; height: 40px; border-radius: 11px; background: var(--primary); color: white; }
.auth-logo svg { width: 26px; height: 26px; }
.auth-brand strong { display: block; font-size: 22px; letter-spacing: -.6px; }
.auth-brand small { display: block; margin-top: 3px; font-size: 14px; color: var(--text-3); }
.auth-form { padding: 30px 32px; border: 1px solid var(--line); border-radius: 16px; background: white; box-shadow: 0 8px 32px #2a4b850a; }
.auth-switch { display: grid; grid-template-columns: 1fr 1fr; gap: 4px; padding: 4px; margin-bottom: 28px; background: #eef2f8; border-radius: 10px; }
.auth-switch button { min-height: 44px; border: 0; border-radius: 7px; background: transparent; color: var(--text-3); font: inherit; font-size: 16px; cursor: pointer; }
.auth-switch button[aria-pressed=true] { background: white; color: var(--primary); font-weight: 650; box-shadow: 0 1px 4px #223d6a16; }
.auth-heading { margin-bottom: 26px; text-align: center; }
.auth-heading h1 { margin: 0 0 10px; font-size: 25px; font-weight: 650; line-height: 1.4; letter-spacing: -.5px; }
.auth-heading p { margin: 0; color: var(--text-3); font-size: 16px; line-height: 1.6; }
.auth-form form { display: grid; gap: 20px; }
.auth-field { display: grid; gap: 8px; font-size: 16px; color: #34445d; }
.auth-field-heading { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.auth-form input { box-sizing: border-box; min-width: 0; width: 100%; min-height: var(--control-height); padding: 10px 12px; border: 1px solid var(--line); border-radius: var(--control-radius); background: white; color: var(--text); font: inherit; }
.auth-field small, .auth-admin-note { font-size: 14px; line-height: 1.6; color: var(--text-3); }
.auth-submit { width: 100%; min-height: var(--control-height); padding: 11px 16px; border: 1px solid var(--primary); border-radius: var(--control-radius); background: var(--primary); color: white; font: inherit; font-size: 16px; font-weight: 600; cursor: pointer; }
.auth-submit:hover:enabled { background: var(--primary-deep); }
.auth-link { padding: 2px 0; border: 0; background: none; color: var(--primary); font: inherit; font-size: 15px; cursor: pointer; }
.auth-link:hover:enabled { text-decoration: underline; }
button:disabled { opacity: 1; background: var(--disabled-bg); color: var(--disabled-text); cursor: not-allowed; }
button:focus-visible, input:focus-visible, a:focus-visible { outline: 2px solid var(--primary); outline-offset: 3px; }
.auth-secondary, .auth-admin-note { margin: 24px 0 0; padding-top: 20px; border-top: 1px solid var(--line-soft); }
.auth-secondary { display: flex; justify-content: center; align-items: baseline; flex-wrap: wrap; gap: 6px; font-size: 15px; color: var(--text-3); }
.auth-admin-note { text-align: center; }
.auth-notice, .auth-error { margin: 0; padding: 11px 12px; border-radius: var(--control-radius); font-size: 15px; line-height: 1.6; overflow-wrap: anywhere; }
.auth-error { color: var(--danger); background: var(--danger-bg); }.auth-notice { color: var(--success); background: var(--success-bg); }
.auth-restoring { margin: 0; color: var(--text-3); font-size: 14px; }
@media(max-width:480px) { .auth-layout { padding: 24px 16px; }.auth-form { padding: 24px; }.auth-heading h1 { font-size: 23px; } }
.auth-form input:user-invalid { border-color: var(--danger); background: var(--danger-bg); }
.auth-form input:disabled, .auth-switch button:disabled, .auth-submit:disabled { background: var(--disabled-bg); color: var(--disabled-text); border-color: var(--line); }
</style>
