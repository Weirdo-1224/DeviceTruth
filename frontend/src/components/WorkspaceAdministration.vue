<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { workspaceRequest, productRequest, type WorkspaceView } from '../api'
const props = defineProps<{space: WorkspaceView}>()
const emit = defineEmits<{changed: []; members: [names: Record<string,string>]}>()
const roleLabels: Record<string,string> = {ADMIN:'管理员',COLLABORATOR:'协作成员',VIEWER:'只读成员'}
const invitationLabels: Record<string,string> = {PENDING:'待接受',ACCEPTED:'已加入',REVOKED:'已撤销',EXPIRED:'已过期'}
const deliveryLabels: Record<string,string> = {PENDING:'等待投递',ACCEPTED_BY_PROVIDER:'邮件服务已接受',FAILED:'投递失败'}
const pageSize = 20
const moreKnowledge = ref(false), loading = ref(false), revision = ref(0), notice = ref('')
interface Member {userId: string; displayName: string; email: string; role: string}
interface Invitation {invitationId: string; email: string; role: string; status: string; deliveryStatus: string}
const members = ref<Member[]>([]), invitations = ref<Invitation[]>([]), knowledge = ref<{documentId: string; title: string; documentVersion: string; dataNature: string; applicability: string; originalAuthorUserId: string | null; publishedByUserId: string | null; publishedAt: string | null}[]>([])
const error = ref(''), email = ref(''), role = ref('COLLABORATOR'), name = ref(props.space.name), busy = ref(false)
async function load(more = false) {
  if (loading.value) return
  loading.value = true; error.value = ''
  try {
    members.value = await workspaceRequest(`/${props.space.workspaceId}/members`)
    emit('members',Object.fromEntries(members.value.map(member => [member.userId, member.displayName])))
    const page = await productRequest<typeof knowledge.value>(`knowledge/documents?limit=${pageSize}&offset=${more ? knowledge.value.length : 0}`)
    knowledge.value = more ? [...knowledge.value, ...page] : page
    moreKnowledge.value = page.length === pageSize
    if (props.space.kind === 'TEAM' && props.space.role === 'ADMIN') invitations.value = await workspaceRequest(`/${props.space.workspaceId}/invitations`)
  } catch(e) { error.value = e instanceof Error ? e.message : '加载失败' } finally { loading.value = false; revision.value++ }
}
async function change(suffix: string, method = 'POST', body?: object) {
  if (busy.value || props.space.role !== 'ADMIN') return
  busy.value = true; error.value = ''
  try { await workspaceRequest(`/${props.space.workspaceId}${suffix}`, method, body); notice.value = '已保存'; emit('changed'); await load() }
  catch(e) { error.value = e instanceof Error ? e.message : '操作失败'; revision.value++; name.value = props.space.name } finally { busy.value = false }
}
onMounted(() => load())
</script>
<template>
  <details class="space-tools"><summary>空间成员与知识库</summary><button :disabled="loading" @click="load()">刷新</button><p v-if="loading" role="status">正在读取当前空间…</p><p role="status">{{ notice }}</p>
    <h3>成员</h3><p v-for="member in members" :key="member.userId"><span class="member-avatar" aria-hidden="true">{{ member.displayName.slice(0,1) }}</span>{{ member.displayName }} · {{ member.email }} · {{ roleLabels[member.role] ?? member.role }}
      <select :key="member.userId + member.role + revision" v-if="space.kind === 'TEAM' && space.role === 'ADMIN'" :value="member.role" :disabled="busy" :aria-label="member.displayName + '的角色'" @change="change('/members/' + member.userId, 'PATCH', {role: ($event.target as HTMLSelectElement).value})"><option value="ADMIN">管理员</option><option value="COLLABORATOR">协作成员</option><option value="VIEWER">只读成员</option></select>
      <button v-if="space.kind === 'TEAM' && space.role === 'ADMIN'" :disabled="busy" @click="change('/members/' + member.userId, 'DELETE')">移除成员</button></p>
    <template v-if="space.kind === 'TEAM' && space.role === 'ADMIN'">
      <form @submit.prevent="change('', 'PATCH', {name})"><label>团队名称<input v-model="name" required maxlength="100"></label><button :disabled="busy">修改名称</button></form>
      <form @submit.prevent="change('/invitations', 'POST', {email, role})"><label>受邀邮箱<input v-model="email" type="email" required></label><select v-model="role" aria-label="邀请角色"><option value="COLLABORATOR">协作成员</option><option value="VIEWER">只读成员</option><option value="ADMIN">管理员</option></select><button :disabled="busy">创建邀请</button></form>
      <p v-for="invite in invitations" :key="invite.invitationId">{{ invite.email }} · {{ roleLabels[invite.role] ?? invite.role }} · {{ invitationLabels[invite.status] ?? invite.status }} · {{ deliveryLabels[invite.deliveryStatus] ?? invite.deliveryStatus }}<button :disabled="busy" @click="change('/invitations/' + invite.invitationId + '/resend')">重发</button><button :disabled="busy" @click="change('/invitations/' + invite.invitationId + '/revoke')">撤销</button></p><p>交付邮件服务不代表收件人已收到或加入。</p>
    </template>
    <h3>当前空间知识库</h3><p v-if="!knowledge.length">尚无可检索资料。</p><article v-for="doc in knowledge" :key="doc.documentId">{{ doc.title }} · {{ doc.documentVersion }} · {{ doc.dataNature }}<p>{{ doc.applicability }}</p><p>原作者：{{ members.find(m => m.userId === doc.originalAuthorUserId)?.displayName ?? doc.originalAuthorUserId ?? '历史作者未知' }} · 发布人：{{ members.find(m => m.userId === doc.publishedByUserId)?.displayName ?? doc.publishedByUserId ?? '历史发布人未知' }} · {{ doc.publishedAt ?? '历史发布时间未知' }}</p></article><button v-if="moreKnowledge" :disabled="loading" @click="load(true)">更多知识资料</button><p role="alert">{{ error }}</p>
  </details>
</template>
<style scoped>.member-avatar { display:inline-grid; place-items:center; width:28px; height:28px; border-radius:50%; background:#e8edf5; color:#334764; margin-right:8px; } .space-tools { padding:12px 20px; border-bottom:1px solid #dce2e9; background:white; } form { display:flex; flex-wrap:wrap; gap:10px; margin:12px 0; } button,select,input { font:inherit; padding:6px; margin-left:8px; } article { padding:8px; }</style>
