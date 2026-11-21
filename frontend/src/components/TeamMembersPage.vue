<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { workspaceRequest, type WorkspaceView } from '../api'
interface Member { userId: string; displayName: string; email: string; role: 'ADMIN' | 'COLLABORATOR' | 'VIEWER' }
interface Invitation { invitationId: string; email: string; role: string; status: string; deliveryStatus: string }
const props = defineProps<{ space: WorkspaceView }>()
const emit = defineEmits<{ changed: []; members: [names: Record<string, string>] }>()
const roleLabels: Record<string, string> = { ADMIN: '管理员', COLLABORATOR: '协作成员', VIEWER: '只读成员' }
const invitationLabels: Record<string, string> = { PENDING: '待接受', ACCEPTED: '已加入', REVOKED: '已撤销', EXPIRED: '已过期' }
const deliveryLabels: Record<string, string> = { PENDING: '等待投递', ACCEPTED_BY_PROVIDER: '邮件服务已接受', FAILED: '投递失败' }
const members = ref<Member[]>([]), invitations = ref<Invitation[]>([])
const loading = ref(false), busy = ref(false), error = ref(''), notice = ref(''), name = ref(props.space.name)
const email = ref(''), role = ref('COLLABORATOR'), revision = ref(0)
async function load(): Promise<void> {
  if (loading.value) return
  loading.value = true; error.value = ''
  try {
    members.value = await workspaceRequest(`/${props.space.workspaceId}/members`)
    emit('members', Object.fromEntries(members.value.map(member => [member.userId, member.displayName])))
    if (props.space.kind === 'TEAM' && props.space.role === 'ADMIN') invitations.value = await workspaceRequest(`/${props.space.workspaceId}/invitations`)
  } catch (e) { error.value = e instanceof Error ? e.message : '成员读取失败' }
  finally { loading.value = false; revision.value++ }
}
async function change(suffix: string, method = 'POST', body?: object): Promise<void> {
  if (busy.value || props.space.role !== 'ADMIN') return
  busy.value = true; error.value = ''; notice.value = ''
  try { await workspaceRequest(`/${props.space.workspaceId}${suffix}`, method, body); notice.value = '已保存'; emit('changed'); await load() }
  catch (e) { error.value = e instanceof Error ? e.message : '操作失败'; name.value = props.space.name; revision.value++ }
  finally { busy.value = false }
}
onMounted(() => load())
</script>
<template>
  <section><header class="dt-pagehead"><div class="dt-title-row"><div><p>{{ space.name }} / 团队成员</p><h1>{{ space.name }}</h1></div><span class="dt-tag">{{ roleLabels[space.role] }}</span></div></header>
    <main class="dt-page-main">
      <p v-if="space.role === 'VIEWER'" class="dt-readonly">你以只读成员身份查看：可阅读调查、讨论、资料和报告，不能提交信息或管理团队。</p>
      <div class="dt-section-head"><h2>团队成员</h2><span>{{ members.length }} 位成员</span></div>
      <div class="dt-page-card"><p v-if="loading && !members.length">正在读取成员…</p>
        <div class="dt-table-scroll"><table class="dt-table"><thead><tr><th>成员</th><th>邮箱</th><th>角色</th><th v-if="space.role === 'ADMIN'">操作</th></tr></thead><tbody>
          <tr v-for="member in members" :key="member.userId"><td><span class="dt-avatar" aria-hidden="true">{{ member.displayName.slice(0, 1) }}</span> {{ member.displayName }}</td><td>{{ member.email }}</td><td>
            <select v-if="space.role === 'ADMIN'" :key="member.userId + member.role + revision" :value="member.role" :disabled="busy" :aria-label="member.displayName + '的角色'" @change="change('/members/' + member.userId, 'PATCH', { role: ($event.target as HTMLSelectElement).value })"><option value="ADMIN">管理员</option><option value="COLLABORATOR">协作成员</option><option value="VIEWER">只读成员</option></select>
            <span v-else class="dt-tag">{{ roleLabels[member.role] }}</span>
          </td><td v-if="space.role === 'ADMIN'"><button type="button" class="dt-link" :disabled="busy" @click="change('/members/' + member.userId, 'DELETE')">移除成员</button></td></tr>
        </tbody></table></div>
        <div v-if="!loading && !members.length && !error" class="dt-empty"><h3>当前空间没有可显示的成员</h3></div>
      </div>
      <p class="dt-help">管理员管理成员与团队设置；协作成员可共同推进调查；只读成员可查看调查、资料与报告。</p>
      <template v-if="space.role === 'ADMIN'">
        <section class="dt-page-card dt-section"><h2>邀请成员</h2><p class="dt-help">通过邮箱指定受邀人及加入角色。邮件服务接受不代表对方已经收到或加入。</p>
          <form class="dt-form-grid" @submit.prevent="change('/invitations', 'POST', { email, role })"><label class="dt-field">邮箱<input v-model="email" type="email" required></label><label class="dt-field">角色<select v-model="role"><option value="COLLABORATOR">协作成员</option><option value="VIEWER">只读成员</option><option value="ADMIN">管理员</option></select></label><button class="dt-button dt-primary" :disabled="busy">创建邀请</button></form>
        </section>
        <section v-if="invitations.length" class="dt-section"><h2>邀请状态</h2><article v-for="invite in invitations" :key="invite.invitationId" class="dt-list-row"><div><strong>{{ invite.email }}</strong><p>{{ roleLabels[invite.role] }} · {{ invitationLabels[invite.status] ?? invite.status }} · {{ deliveryLabels[invite.deliveryStatus] ?? invite.deliveryStatus }}</p></div><div class="dt-actions"><button class="dt-button" :disabled="busy" @click="change('/invitations/' + invite.invitationId + '/resend')">重发</button><button class="dt-button" :disabled="busy" @click="change('/invitations/' + invite.invitationId + '/revoke')">撤销</button></div></article></section>
        <details class="dt-team-settings"><summary>团队设置</summary><form @submit.prevent="change('', 'PATCH', { name })"><label class="dt-field">团队名称<input v-model="name" required maxlength="100"></label><button class="dt-button" :disabled="busy">保存名称</button></form></details>
      </template>
      <p v-if="notice" role="status">{{ notice }}</p><p v-if="error" role="alert">{{ error }}</p>
    </main>
  </section>
</template>
