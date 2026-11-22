<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import AdminTable from './AdminTable.vue'
import LoginEntry from '../components/LoginEntry.vue'
import { type LoginRealm, loginTarget, entryTarget } from '../loginRouting'
import { fieldLabel } from './labels'
import { AdminError, createAdminScope, describe, queryPath, request, type Access, type Page, type Row, type Workspace } from './client'
import './admin.css'
const pages = [
  { id: 'users', label: '用户管理', eyebrow: 'ACCOUNT OPERATIONS', description: '统一账号、个人空间与受控开户。' },
  { id: 'runs', label: '运行与用量', eyebrow: 'RUN OPERATIONS', description: '全平台脱敏运行记录与已报告用量。' },
  { id: 'knowledge', label: '知识库管理', eyebrow: 'KNOWLEDGE OPERATIONS', description: '在明确授权的目标空间内管理知识元数据。' },
  { id: 'skills', label: 'Skill 管理', eyebrow: 'SKILL OPERATIONS', description: '查看实际部署内容与空间安装授权。' },
  { id: 'system', label: '系统与审计', eyebrow: 'SYSTEM OPERATIONS', description: '依赖检测范围、管理操作记录与运维说明。' }
]
const active = ref(pages.find(p => location.pathname.endsWith('/' + p.id))?.id ?? 'users')
const page = computed(() => pages.find(p => p.id === active.value)!)
const identity = ref<{ userId: string; email: string; displayName: string } | null>(null)
const access = ref<Access | null>(null)
const phase = ref<'loading' | 'ready' | 'login' | 'denied' | 'error'>('loading')
const error = ref(''); const loading = ref(false); const busy = ref(false)
const rows = ref<Row[]>([]); const nextCursor = ref<string | null>(null); const snapshot = ref('')
const workspaces = ref<Workspace[]>([]); const workspaceCursor = ref<string | null>(null); const target = ref('')
const q = ref(''); const status = ref(''); const role = ref(''); const tab = ref('records')
const from = ref(''); const to = ref(''); const timezone = ref('Asia/Shanghai')
const availability = ref(''); const ingest = ref(''); const groupBy = ref('NONE')
const runFilter = ref<Record<string, string>>({ workspaceId: '', initiatedByUserId: '', executionActorUserId: '', failureCode: '' })
const auditFilter = ref<Record<string, string>>({ actorUserId: '', action: '', targetType: '', targetId: '', result: '', requestId: '' })
const quotaDimension = ref('PLATFORM'); const quotaSubject = ref(''); const quotaResource = ref('')
const quotaResources = ['RUN_CREATE','ACTIVE_RUN','UNRESOLVED_RUN','UPLOAD_BYTES','IMPORT_JOB','RETRIEVAL','EMBEDDING_TOKENS','SSE_CONNECTION','STORAGE_BYTES']
const detail = ref<Row | null>(null); const detailTitle = ref(''); const detailDialog = ref<HTMLDialogElement | null>(null)
const uploadDialog = ref<HTMLDialogElement | null>(null); const uploadSources = ref<Row[]>([]); const uploadSource = ref(''); const uploadFile = ref<File | null>(null); const uploadTitle = ref(''); const uploadVersion = ref('admin-2026.1')
const installDialog = ref<HTMLDialogElement | null>(null); const installCatalog = ref<Row[]>([]); const installSkill = ref('')
const createDialog = ref<HTMLDialogElement | null>(null); const createEmail = ref(''); const createName = ref(''); const initialPassword = ref('')
const selected = ref<Row | null>(null); const confirmDialog = ref<HTMLDialogElement | null>(null); const reason = ref('OPS_REQUEST')
const scope = createAdminScope(); const authScope = createAdminScope(); const detailScope = createAdminScope(); const writeScope = createAdminScope()
const currentFeature = computed(() => active.value === 'users' ? 'USER_STATUS' : active.value === 'knowledge' ? 'KNOWLEDGE_WRITE' : 'SKILL_WRITE')
const feature = computed(() => access.value?.features.find(f => f.feature === currentFeature.value))
const canWrite = computed(() => feature.value?.state === 'READY' && (!['knowledge','skills'].includes(active.value) || (workspaces.value.find(w => w.workspaceId === target.value)?.capabilities.includes(active.value === 'knowledge' ? 'KNOWLEDGE_MANAGE' : 'SKILL_MANAGE') ?? false)))
const targetOptions = computed(() => workspaces.value.filter(w => w.capabilities.some(c => c.startsWith(active.value === 'knowledge' ? 'KNOWLEDGE_' : 'SKILL_'))))
const needsTarget = computed(() => active.value === 'knowledge' || active.value === 'skills' && tab.value === 'installations')
const columns = computed(() => {
  let fields: string[] = []
  if (active.value === 'users') fields = ['email:邮箱', 'displayName:名称', 'userId:账号 ID', 'platformRole:平台角色', 'status:账号状态', 'createdAt:创建时间']
  if (active.value === 'runs') fields = tab.value === 'quotas' ? ['dimension:归属维度', 'subjectId:归属对象', 'resource:资源', 'configuredLimit:上限', 'consumed:已消耗', 'reserved:占用', 'remaining:剩余', 'unit:单位', 'periodStart:周期开始', 'periodEnd:周期结束', 'timezone:时区', 'policyVersion:策略版本', 'enforcementState:执行状态', 'reconciliationState:对账状态', 'measuredAt:更新时间'] : ['runId:运行 ID', 'workspaceId:空间 ID', 'initiatedByUserId:发起人 ID', 'executionActorUserId:执行者 ID', 'status:状态', 'requestedAt:提交时间', 'activeDurationMs:活跃毫秒', 'wallDurationMs:总历时毫秒', 'models:模型', 'usageStatus:用量状态', 'failureCode:失败码']
  if (active.value === 'knowledge') fields = tab.value === 'files' ? ['fileId:文件 ID', 'importOrigin:导入方式', 'processingStatus:处理状态', 'documentId:文档 ID', 'errorCode:原因', 'retryable:可重试'] : ['title:标题', 'domain:领域', 'models:型号', 'version:版本', 'sourceNature:来源性质', 'accessNature:使用依据', 'ingestStatus:入库状态', 'availability:可用状态', 'publicationStatus:发布状态', 'releaseActive:发布集激活']
  if (active.value === 'skills') fields = tab.value === 'installations' ? ['skillId:Skill', 'version:安装版本', 'registryVersion:加载版本', 'enabled:启用', 'availability:可用性', 'revision:管理版本', 'effectiveTools:实际工具'] : ['name:名称', 'registryVersion:加载版本', 'description:说明', 'devices:设备', 'declaredTools:声明工具']
  if (active.value === 'system') fields = tab.value === 'audit' ? ['occurredAt:时间', 'actorKind:操作者类型', 'actorUserId:操作者 ID', 'action:动作', 'result:结果', 'targetId:目标', 'requestId:关联编号'] : ['name:依赖', 'status:状态', 'checkedAt:检测时间', 'reasonCode:证据范围']
  return fields.map(field => { const [key, label] = field.split(':'); return { key: key!, label: label! } })
})
const tabs = computed(() => active.value === 'runs' ? [{ id: 'records', label: '运行记录' }, { id: 'usage', label: '用量统计' }, { id: 'quotas', label: '额度与占用' }] : active.value === 'knowledge' ? [{ id: 'records', label: '空间知识' }, { id: 'files', label: '导入任务' }] : active.value === 'skills' ? [{ id: 'records', label: '部署目录' }, { id: 'installations', label: '空间授权' }] : active.value === 'system' ? [{ id: 'records', label: '依赖状态' }, { id: 'audit', label: '管理审计' }, { id: 'ops', label: '运维说明' }] : [])
let pollTimer: ReturnType<typeof setTimeout> | undefined
const usage = ref<Row | null>(null)
function invalidate() { if (pollTimer) clearTimeout(pollTimer); scope.clear(); detailScope.clear(); writeScope.clear(); busy.value = false; rows.value = []; nextCursor.value = null; detail.value = null; usage.value = null; initialPassword.value = ''; selected.value = null; detailDialog.value?.close(); createDialog.value?.close(); confirmDialog.value?.close(); installDialog.value?.close(); installCatalog.value = []; installSkill.value = ''; uploadDialog.value?.close(); uploadFile.value = null; uploadSources.value = []; uploadSource.value = ''; uploadTitle.value = ''; createEmail.value = ''; createName.value = '' }
async function handleFailure(e: unknown) {
  if (e instanceof AdminError && [401, 403].includes(e.status)) {
    invalidate(); authScope.clear(); access.value = null; workspaces.value = []; target.value = ''; identity.value = null
    phase.value = e.status === 401 ? 'login' : 'denied'
  }
  error.value = describe(e)
}
async function loadWorkspaces(more = false) {
  const token = authScope.next()
  try {
    const result = await request<Page<Workspace>>(queryPath('/api/admin/workspaces', { limit: '100', cursor: more ? workspaceCursor.value ?? '' : '' }), { signal: token.signal })
    if (!token.current()) return
    workspaces.value = more ? [...workspaces.value, ...result.items] : result.items; workspaceCursor.value = result.nextCursor
  } catch (e) { if (token.current()) await handleFailure(e) }
}
async function restore() {
  invalidate(); const token = authScope.next(); phase.value = 'loading'; error.value = ''; resetFilters(); target.value = ''; workspaces.value = []; identity.value = null; access.value = null
  try {
    const me = await request<typeof identity.value>('/api/admin/auth/me', { signal: token.signal })
    const permission = await request<Access>('/api/admin/access', { signal: token.signal })
    if (!token.current()) return
    identity.value = me; access.value = permission; phase.value = 'ready'
    if (location.pathname === '/admin/login') { history.replaceState(null, '', entryTarget('ADMIN')); active.value = pages.find(p => location.pathname.endsWith('/' + p.id))?.id ?? 'users' }
    await loadWorkspaces(); await load()
  } catch (e) { if (token.current()) { phase.value = 'error'; await handleFailure(e) } }
}
async function load(more = false) {
  if (pollTimer) clearTimeout(pollTimer)
  const token = scope.next(); loading.value = true; error.value = ''; detailScope.clear(); detail.value = null; detailDialog.value?.close()
  if (!more) { rows.value = []; nextCursor.value = null; usage.value = null }
  if (needsTarget.value && !target.value) { loading.value = false; return }
  const query: Record<string, string> = { limit: '20', cursor: more ? nextCursor.value ?? '' : '' }
  let path = ''
  if (active.value === 'users') { path = '/api/admin/users'; Object.assign(query, { q: q.value, status: status.value, platformRole: role.value }) }
  if (active.value === 'runs') {
    if (tab.value === 'quotas') { path = '/api/admin/quotas'; Object.assign(query, { dimension: quotaDimension.value, resource: quotaResource.value, subjectId: quotaDimension.value === 'PLATFORM' ? '' : quotaSubject.value }) }
    else { path = tab.value === 'usage' ? '/api/admin/usage/summary' : '/api/admin/runs'; Object.assign(query, { from: from.value, to: to.value }); if (tab.value === 'usage') { delete query.limit; delete query.cursor; query.timezone = timezone.value; query.groupBy = groupBy.value; query.workspaceId = runFilter.value.workspaceId ?? ''; query.initiatedByUserId = runFilter.value.initiatedByUserId ?? '' } else Object.assign(query, runFilter.value, { status: status.value }) }
  }
  if (active.value === 'knowledge') { path = '/api/admin/knowledge/' + (tab.value === 'files' ? 'files' : 'documents'); query.workspaceId = target.value; if (tab.value !== 'files') Object.assign(query, { q: q.value, availability: availability.value, ingestStatus: ingest.value }); else query.processingStatus = status.value }
  if (active.value === 'skills') path = tab.value === 'installations' ? `/api/admin/workspaces/${target.value}/skill-installations` : '/api/admin/skills'
  if (active.value === 'system') { path = tab.value === 'audit' ? '/api/admin/audit-events' : '/api/admin/system/health'; if (tab.value !== 'audit') { delete query.limit; delete query.cursor } else Object.assign(query, auditFilter.value, { from: from.value, to: to.value }) }
  if (tab.value === 'ops') { loading.value = false; return }
  try {
    const me = await request<{ userId: string }>('/api/admin/auth/me', { signal: token.signal })
    if (!token.current()) return
    if (me.userId !== identity.value?.userId) { await restore(); return }
    const result = await request<Page<Row> & { components?: Row[] } & Row>(queryPath(path, query), { signal: token.signal })
    if (!token.current()) return
    if (active.value === 'runs' && tab.value === 'usage') usage.value = result
    else { rows.value = more ? [...rows.value, ...(result.items ?? [])] : result.items ?? result.components ?? []; nextCursor.value = result.nextCursor ?? null; snapshot.value = result.snapshotAt ?? '' }
  } catch (e) { if (token.current()) await handleFailure(e) }
  finally { if (token.current()) { loading.value = false; if (!error.value && active.value === 'knowledge' && tab.value === 'files' && rows.value.some(r => ['UPLOADED','PROCESSING'].includes(String(r.processingStatus)))) pollTimer = setTimeout(() => { void load() }, 1500) } }
}
function resetFilters() { q.value = ''; status.value = ''; role.value = ''; from.value = ''; to.value = ''; availability.value = ''; ingest.value = ''; groupBy.value = 'NONE'; quotaDimension.value = 'PLATFORM'; quotaSubject.value = ''; quotaResource.value = ''; for (const key of Object.keys(runFilter.value)) runFilter.value[key] = ''; for (const key of Object.keys(auditFilter.value)) auditFilter.value[key] = '' }
async function navigate(id: string) { invalidate(); resetFilters(); active.value = id; tab.value = 'records'; q.value = ''; status.value = ''; role.value = ''; target.value = ''; history.pushState(null, '', '/admin/' + id); if (phase.value === 'ready') await load() }
async function inspect(row: Row) {
  const token = detailScope.next(); detail.value = null; detailTitle.value = String(row.email ?? row.title ?? row.name ?? row.runId ?? row.skillId ?? '详情')
  let path = active.value === 'runs' ? `/api/admin/runs/${row.runId}` : active.value === 'skills' && tab.value !== 'installations' ? `/api/admin/skills/${row.skillId}` : active.value === 'knowledge' && tab.value !== 'files' ? queryPath(`/api/admin/knowledge/documents/${row.documentId}`, { workspaceId: target.value }) : ''
  try {
    const result = path ? await request<Row>(path, { signal: token.signal }) : row
    if (!token.current()) return
    detail.value = result; selected.value = row; detailDialog.value?.showModal()
  } catch (e) { if (token.current()) await handleFailure(e) }
}
async function create() {
  if (busy.value) return; const token = writeScope.next(); busy.value = true; error.value = ''
  try { await request('/api/admin/users', { method: 'POST', body: { email: createEmail.value, displayName: createName.value, initialPassword: initialPassword.value }, signal: token.signal }); if (!token.current()) return; createDialog.value?.close(); createEmail.value = ''; createName.value = ''; await load() }
  catch (e) { if (token.current()) await handleFailure(e) } finally { if (token.current()) { initialPassword.value = ''; busy.value = false } }
}
async function signedIn(_account: unknown, realm: LoginRealm, target: string) {
  if (realm === 'USER') { location.assign(loginTarget(realm, target)); return }
  history.replaceState(null, '', loginTarget(realm, target))
  active.value = pages.find(p => location.pathname.endsWith('/' + p.id))?.id ?? 'users'
  await restore()
}
async function logout() { try { await request('/api/admin/auth/logout', { method: 'POST', body: {} }) } catch (e) { error.value = describe(e) } finally { invalidate(); authScope.clear(); access.value = null; identity.value = null; workspaces.value = []; target.value = ''; phase.value = 'login'; history.replaceState(null, '', '/admin/login') } }
async function openUpload() {
  const token = detailScope.next()
  try { const sources = await request<Page<Row>>(queryPath('/api/admin/knowledge/sources', { workspaceId: target.value, limit: '100' }), { signal: token.signal }); if (!token.current()) return; uploadSources.value = sources.items; uploadFile.value = null; uploadSource.value = ''; uploadDialog.value?.showModal() } catch (e) { if (token.current()) await handleFailure(e) }
}
function chooseFile(event: Event) { uploadFile.value = (event.target as HTMLInputElement).files?.[0] ?? null }
async function upload() {
  const source = uploadSources.value.find(s => s.sourceId === uploadSource.value)
  if (!source || !uploadFile.value || busy.value) return
  const token = writeScope.next(); busy.value = true
  const data = new FormData(); data.append('workspaceId', target.value); data.append('file', uploadFile.value); data.append('title', uploadTitle.value || uploadFile.value.name); data.append('version', uploadVersion.value); data.append('sourceId', uploadSource.value); data.append('licenseCode', String((source.allowedLicenseCodes as string[])[0]))
  try { await request('/api/admin/knowledge/imports', { method: 'POST', body: data, signal: token.signal }); if (!token.current()) return; uploadDialog.value?.close(); tab.value = 'files'; await load() } catch (e) { if (token.current()) await handleFailure(e) } finally { if (token.current()) { busy.value = false; uploadFile.value = null } }
}
async function retryImport(row: Row) {
  if (busy.value || !canWrite.value) return; const token = writeScope.next(); busy.value = true
  try { await request(`/api/admin/knowledge/files/${row.fileId}/retry`, { method: 'POST', body: { workspaceId: target.value, expectedVersion: row.revision, reasonCode: reason.value }, signal: token.signal }); if (token.current()) await load() } catch (e) { if (token.current()) await handleFailure(e) } finally { if (token.current()) busy.value = false }
}
async function openInstall() {
  const token = detailScope.next()
  try { const catalog = await request<Page<Row>>('/api/admin/skills?limit=100', { signal: token.signal }); if (!token.current()) return; installCatalog.value = catalog.items; installSkill.value = ''; installDialog.value?.showModal() } catch (e) { if (token.current()) await handleFailure(e) }
}
async function install() {
  const skill = installCatalog.value.find(s => s.skillId === installSkill.value)
  if (!skill || busy.value || !target.value) return
  const token = writeScope.next(); busy.value = true
  try { await request(`/api/admin/workspaces/${target.value}/skill-installations`, { method: 'POST', body: { skillId: skill.skillId, version: skill.registryVersion, reasonCode: reason.value }, signal: token.signal }); if (!token.current()) return; installDialog.value?.close(); await load() } catch (e) { if (token.current()) await handleFailure(e) } finally { if (token.current()) busy.value = false }
}
async function readAttempts(kind: string) {
  if (!selected.value?.runId) return
  const token = detailScope.next()
  try { const attempts = await request<Page<Row>>(`/api/admin/runs/${selected.value.runId}/attempts?kind=${kind}`, { signal: token.signal }); if (token.current()) detail.value = { ...detail.value, attempts: attempts.items, attemptsComplete: !attempts.nextCursor } } catch (e) { if (token.current()) await handleFailure(e) }
}
async function readReference(name: string) {
  if (!selected.value?.skillId) return
  const token = detailScope.next()
  try { const reference = await request<Row>(`/api/admin/skills/${selected.value.skillId}/references/${encodeURIComponent(name)}`, { signal: token.signal }); if (token.current()) detail.value = { ...detail.value, reference: reference.bodyMarkdown } } catch (e) { if (token.current()) await handleFailure(e) }
}
async function confirmStatus() {
  if (!selected.value || busy.value || !canWrite.value) return
  const row = selected.value; const token = writeScope.next(); busy.value = true
  try {
    const isUser = active.value === 'users'; const isKnowledge = active.value === 'knowledge'
    const path = isKnowledge ? `/api/admin/knowledge/documents/${row.documentId}/status` : isUser ? `/api/admin/users/${row.userId}/status` : `/api/admin/workspaces/${target.value}/skill-installations/${row.skillId}/versions/${row.version}/status`
    await request(path, { method: 'PATCH', body: isKnowledge ? { workspaceId: target.value, status: row.availability === 'ENABLED' ? 'DISABLED' : 'ENABLED', expectedVersion: row.revision, reasonCode: reason.value } : isUser ? { status: row.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE', expectedVersion: row.statusVersion, reasonCode: reason.value } : { enabled: !row.enabled, expectedVersion: row.revision, reasonCode: reason.value }, signal: token.signal })
    if (!token.current()) return
    confirmDialog.value?.close(); detailDialog.value?.close(); await load()
  } catch (e) { if (!token.current()) return; await handleFailure(e); if (e instanceof AdminError && e.code === 'STATE_VERSION_CONFLICT') { confirmDialog.value?.close(); await load() } } finally { if (token.current()) busy.value = false }
}
function detailText(value: unknown): string { if (value === null || value === undefined) return '未知'; if (typeof value === 'object') { if (Array.isArray(value)) return value.map(detailText).join('\n') || '—'; return Object.entries(value).map(([k, v]) => `${fieldLabel(k)}：${detailText(v)}`).join('\n') } return String(value) }
function popstate() { invalidate(); active.value = pages.find(p => location.pathname.endsWith('/' + p.id))?.id ?? 'users'; tab.value = 'records'; void load() }
watch(() => JSON.stringify([q.value, status.value, role.value, from.value, to.value, timezone.value, availability.value, ingest.value, groupBy.value, runFilter.value, auditFilter.value, quotaDimension.value, quotaSubject.value, quotaResource.value]), () => { scope.clear(); detailScope.clear(); nextCursor.value = null; rows.value = []; usage.value = null; loading.value = false; detail.value = null; detailDialog.value?.close() }, { flush: 'sync' })
watch([target, tab], () => { invalidate(); if (phase.value === 'ready') void load() })
onMounted(() => { document.title = 'DeviceTruth · 平台管理'; void restore(); window.addEventListener('popstate', popstate) })
onUnmounted(() => { invalidate(); authScope.clear(); window.removeEventListener('popstate', popstate) })
async function openCreate() { initialPassword.value = ''; await nextTick(); createDialog.value?.showModal() }
</script>

<template>
  <LoginEntry v-if="phase === 'login'" initial-realm="ADMIN" @authenticated="signedIn" />
  <div v-else class="dt-admin">
    <aside class="admin-sidebar"><a class="admin-brand" href="/">✳ <span>DeviceTruth<small>平台管理控制台</small></span></a>
      <nav aria-label="管理端导航"><a v-for="item in pages" :key="item.id" :href="'/admin/' + item.id" :aria-current="active === item.id ? 'page' : undefined" @click.prevent="navigate(item.id)">{{ item.label }}</a></nav>
      <div class="admin-sidebar-footer">专用管理身份 · 独立会话<br><a href="/">← 返回工作台</a></div>
    </aside>
    <div class="admin-main"><header class="admin-topbar"><span>运营空间 <strong>/ {{ page.label }}</strong></span><div><span v-if="identity">{{ identity.displayName }} · {{ identity.email }}</span><button v-if="identity" type="button" @click="logout">退出</button></div></header>
      <main><div class="admin-page-heading"><div><p class="admin-eyebrow">{{ page.eyebrow }}</p><h1>{{ page.label }}</h1><p>{{ page.description }}</p></div><button v-if="phase === 'ready'" type="button" @click="load()">刷新数据</button></div>
        <p v-if="error" class="admin-alert" role="alert">{{ error }}</p>
        <p v-if="phase === 'loading'" role="status">正在恢复登录与管理权限…</p>

        <section v-else-if="phase === 'denied'" class="admin-card"><h2>无平台管理权限</h2><p>请通过可信维护入口为已有账号配置平台角色。团队 ADMIN 不授予后台权限。</p><a href="/">返回工作台</a><button type="button" @click="restore">重新核验</button></section>
        <section v-else-if="phase === 'error'" class="admin-card"><p>管理服务暂不可用。</p><button @click="restore">重试</button></section>
        <template v-else-if="phase === 'ready'">
          <div v-if="tabs.length" class="admin-tabs" role="group" aria-label="页内视图"><button v-for="item in tabs" :key="item.id" :aria-pressed="tab === item.id" @click="tab = item.id">{{ item.label }}</button></div>
          <div v-if="feature && ['users', 'knowledge', 'skills'].includes(active)" class="admin-notice"><strong>{{ feature.state === 'READY' ? '操作能力已接通' : '写能力尚未开放' }}</strong> · {{ feature.state === 'READY' ? '每次操作均重新核验权限并记录审计。' : '持续授权或执行路径仍待接入；当前可读取的资料来自真实接口。' }}</div>
          <section v-if="needsTarget" class="admin-card admin-target"><label>管理目标空间<select v-model="target"><option value="">请选择明确授权的空间</option><option v-for="w in targetOptions" :key="w.workspaceId" :value="w.workspaceId">{{ w.name }} · {{ w.workspaceId }}</option></select></label><button v-if="workspaceCursor" @click="loadWorkspaces(true)">加载更多授权空间</button><p>此目标与工作台当前空间分别选择；未选择时不读取个人空间。</p><button v-if="active === 'knowledge' && target" :disabled="!canWrite || loading || !targetOptions.find(w => w.workspaceId === target)?.capabilities.includes('KNOWLEDGE_MANAGE')" @click="openUpload">导入已收集知识</button><button v-if="active === 'skills' && target" :disabled="!canWrite || loading || !targetOptions.find(w => w.workspaceId === target)?.capabilities.includes('SKILL_MANAGE')" @click="openInstall">开通 Skill</button></section>
          <form v-if="['users','knowledge','runs'].includes(active) && tab !== 'quotas'" class="admin-filters" @submit.prevent="load()">
            <label v-if="active === 'users' || active === 'knowledge' && tab !== 'files'">{{ active === 'users' ? '邮箱 / 名称' : '知识标题' }}<input v-model="q" maxlength="100" placeholder="输入关键词"></label>
            <label v-if="active === 'users' || active === 'runs' && tab === 'records'">状态<select v-model="status"><option value="">全部状态</option><option v-for="s in active === 'users' ? ['ACTIVE','DISABLED'] : ['QUEUED','RUNNING','WAITING_FOR_USER','COMPLETED','FAILED','CANCELLED']" :key="s">{{ s }}</option></select></label>
            <label v-if="active === 'users'">平台角色<select v-model="role"><option value="">全部角色</option><option>PLATFORM_ADMIN</option><option>NONE</option></select></label>
            <template v-if="active === 'knowledge' && tab !== 'files'"><label>文档可用性<select v-model="availability"><option value="">全部</option><option>ENABLED</option><option>DISABLED</option></select></label><label>入库状态<select v-model="ingest"><option value="">全部</option><option v-for="s in ['READY','FAILED','NEEDS_REBUILD','STAGED']" :key="s">{{ s }}</option></select></label></template>
            <label v-if="active === 'knowledge' && tab === 'files'">文件处理状态<select v-model="status"><option value="">全部</option><option v-for="s in ['UPLOADED','PROCESSING','PARSED','FAILED','OCR_REQUIRED']" :key="s">{{ s }}</option></select></label>
            <template v-if="active === 'runs'"><label>起始时间（含偏移）<input v-model="from" placeholder="默认最近 7 天"></label><label>结束时间（含偏移）<input v-model="to" placeholder="2026-11-19T23:00:00+08:00"></label><label v-if="tab === 'usage'">时区<input v-model="timezone"></label><label v-if="tab === 'usage'">用量分组<select v-model="groupBy"><option>NONE</option><option>DAY</option><option>WORKSPACE</option></select></label></template>
            <template v-if="active === 'runs'"><label>运行空间 ID<input v-model="runFilter.workspaceId" maxlength="36"></label><label>初始发起人 ID<input v-model="runFilter.initiatedByUserId" maxlength="36"></label><label v-if="tab === 'records'">执行者 ID<input v-model="runFilter.executionActorUserId" maxlength="36"></label><label v-if="tab === 'records'">稳定失败码<input v-model="runFilter.failureCode" maxlength="64"></label></template>
            <button type="submit" class="primary">应用筛选</button><button v-if="active === 'users'" type="button" :disabled="access?.features.find(f => f.feature === 'USER_CREATE')?.state !== 'READY'" @click="openCreate">创建账号</button>
          </form>
          <form v-if="active === 'runs' && tab === 'quotas'" class="admin-filters" @submit.prevent="load()"><label>额度归属<select v-model="quotaDimension"><option>PLATFORM</option><option>ACCOUNT</option><option>WORKSPACE</option></select></label><label v-if="quotaDimension !== 'PLATFORM'">归属对象 ID<input v-model="quotaSubject" maxlength="36" required></label><label>资源类型<select v-model="quotaResource"><option value="">全部资源</option><option v-for="resource in quotaResources" :key="resource">{{ resource }}</option></select></label><button class="primary">应用筛选</button></form>
          <form v-if="active === 'system' && tab === 'audit'" class="admin-filters" @submit.prevent="load()"><label>审计起始时间（含偏移）<input v-model="from" placeholder="默认最近 7 天"></label><label>审计结束时间（含偏移）<input v-model="to"></label><label>审计操作者 ID<input v-model="auditFilter.actorUserId" maxlength="36"></label><label>审计动作<input v-model="auditFilter.action" maxlength="64"></label><label>审计对象类型<input v-model="auditFilter.targetType" maxlength="32"></label><label>审计对象 ID<input v-model="auditFilter.targetId" maxlength="128"></label><label>审计结果<select v-model="auditFilter.result"><option value="">全部</option><option v-for="s in ['SUCCESS','NO_CHANGE','FAILURE','DENIED']" :key="s">{{ s }}</option></select></label><label>审计关联编号<input v-model="auditFilter.requestId" maxlength="36"></label><button class="primary">应用筛选</button></form>
          <section v-if="tab === 'ops'" class="admin-card admin-ops"><h2>运维入口</h2><p>平台角色和空间范围通过可信维护工具授予，网页不提供扩权接口。</p><p>部署前先备份数据库及文件存储，在隔离库验证增量迁移；已生效的账号停用与撤权必须由兼容版本持续执行。</p><p>UNKNOWN 表示没有足够检测证据。依赖状态页不发起付费模型探测。</p><p>配额策略与对账未接通前，不据未知字段推断剩余额度。历史知识迁移与当前编号不兼容的库需独立制定迁移方案。</p></section>
          <p v-if="active === 'runs' && tab === 'quotas'" class="admin-notice">当前未启用执行配额。策略安装仅配置阈值；占用与剩余在计量接通前保持未知。</p><section v-if="tab !== 'ops'" class="admin-card admin-data"><div class="admin-data-heading"><h2>{{ tabs.find(t => t.id === tab)?.label ?? '账号列表' }}</h2><span v-if="snapshot">快照 {{ snapshot }}</span></div>
            <p v-if="loading && !rows.length" role="status">正在读取…</p>
            <template v-else-if="usage"><div class="admin-metrics"><div><span>运行数（按 ID）</span><strong>{{ usage.runCount }}</strong></div><div><span>模型尝试</span><strong>{{ usage.modelAttemptCount }}</strong></div><div><span>归属状态</span><strong>{{ usage.attributionStatus }}</strong></div></div><p>以下为已报告小计。未知不计零；金额须有可信币种后才能汇总，等待补答不计失败。</p><dl class="admin-detail"><template v-for="(value, key) in usage" :key="key"><dt>{{ fieldLabel(String(key)) }}</dt><dd>{{ detailText(value) }}</dd></template></dl></template>
            <template v-else-if="rows.length"><AdminTable :rows="rows" :columns="columns" :can-inspect="!['quotas'].includes(tab)" @inspect="inspect"/><button v-if="nextCursor" class="admin-more" :disabled="loading" @click="load(true)">{{ loading ? '加载中…' : '加载更多' }}</button></template>
            <div v-else-if="!error" class="admin-empty"><span>○</span><h3>{{ needsTarget && !target ? '请选择管理目标空间' : '没有匹配记录' }}</h3><p>{{ active === 'knowledge' && tab === 'files' ? '此空间暂无匹配的管理导入或已有知识入库记录；调查附件不会自动列入此处。' : '可调整筛选或刷新，权限始终由当前服务端状态决定。' }}</p></div>
          </section>
        </template>
      </main><footer>DeviceTruth · 管理操作可追溯 · 业务内容按原空间权限访问</footer>
    </div>
    <dialog ref="createDialog" @close="initialPassword = ''" @cancel="initialPassword = ''"><form @submit.prevent="create"><h2>创建普通账号</h2><p v-if="error" role="alert">{{ error }}</p><p>同时创建空个人空间。管理员当前会话保持不变。</p><label>邮箱<input v-model="createEmail" type="email" required maxlength="254"></label><label>名称<input v-model="createName" required maxlength="100"></label><label>初始密码<input v-model="initialPassword" type="password" autocomplete="new-password" minlength="12" maxlength="128" required></label><p>密码仅用于本次请求，提交结束立即清空；不承诺首次改密。</p><div class="admin-dialog-actions"><button type="button" :disabled="busy" @click="createDialog?.close()">取消</button><button class="primary" :disabled="busy">{{ busy ? '创建中…' : '确认创建' }}</button></div></form></dialog>
    <dialog ref="uploadDialog"><form @submit.prevent="upload"><h2>导入已收集知识</h2><p v-if="error" role="alert">{{ error }}</p><p>仅接受既有资料清单中校验值匹配的 Markdown，最大 1 MB。保留原来源性质和本地研究使用范围，受理成功不等于处理完成。</p><label>标题<input v-model="uploadTitle" maxlength="500"></label><label>版本<input v-model="uploadVersion" required></label><label for="admin-source">受控来源</label><select id="admin-source" v-model="uploadSource" required><option value="">请选择来源登记</option><option v-for="source in uploadSources" :key="String(source.sourceId)" :value="source.sourceId">{{ source.displayLabel }} · {{ source.sourceNature }}</option></select><label>Markdown 文件<input type="file" accept=".md,text/markdown" required @change="chooseFile"></label><div class="admin-dialog-actions"><button type="button" :disabled="busy" @click="uploadDialog?.close()">取消</button><button class="primary" :disabled="busy || !uploadSources.length || !uploadFile">确认导入</button></div></form></dialog>
    <dialog ref="installDialog"><form @submit.prevent="install"><h2>为空间开通 Skill</h2><p v-if="error" role="alert">{{ error }}</p><p>目标空间：{{ target }}。版本来自当前实际加载目录，工具权限由服务端计算。</p><label for="admin-install-skill">Skill</label><select id="admin-install-skill" v-model="installSkill" required><option value="">请选择当前部署版本</option><option v-for="s in installCatalog" :key="String(s.skillId)" :value="s.skillId">{{ s.name }} · {{ s.registryVersion }}</option></select><div class="admin-dialog-actions"><button type="button" :disabled="busy" @click="installDialog?.close()">取消</button><button class="primary" :disabled="busy || !canWrite">确认开通</button></div></form></dialog>
    <dialog ref="detailDialog"><div class="admin-data-heading"><h2>{{ detailTitle }}</h2><button @click="detailDialog?.close()">关闭</button></div><div v-if="active === 'runs'"><button @click="readAttempts('MODEL')">模型尝试</button> <button @click="readAttempts('TOOL')">工具尝试</button></div><div v-if="active === 'skills' && Array.isArray(detail?.references)"><button v-for="r in (detail?.references as Row[])" :key="String(r.name)" @click="readReference(String(r.name))">{{ r.name }}</button></div><dl v-if="detail" class="admin-detail"><template v-for="(value, key) in detail" :key="key"><dt>{{ fieldLabel(String(key)) }}</dt><dd :class="{ 'admin-markdown': key === 'bodyMarkdown' }">{{ detailText(value) }}</dd></template></dl><button v-if="selected && (active === 'users' || active === 'skills' && tab === 'installations' || active === 'knowledge' && tab === 'records')" :disabled="!canWrite" @click="confirmDialog?.showModal()">{{ active === 'knowledge' ? (selected.availability === 'ENABLED' ? '停用文档' : '恢复文档') : active === 'users' ? (selected.status === 'ACTIVE' ? '停用账号' : '恢复账号') : (selected.enabled ? '停用安装' : '启用安装') }}</button><button v-if="active === 'knowledge' && tab === 'files' && selected?.retryable" :disabled="busy || !canWrite" @click="retryImport(selected)">重试原导入</button></dialog>
    <dialog ref="confirmDialog"><form @submit.prevent="confirmStatus"><h2>确认状态变更</h2><p v-if="error" role="alert">{{ error }}</p><p>目标：{{ selected?.email ?? selected?.title ?? selected?.skillId }}</p><p v-if="active === 'users'">停用会撤销全部会话及后续执行资格。恢复不恢复旧会话、不自动重放任务。</p><p v-else-if="active === 'knowledge'">停用阻止新检索、新附件关联和新引用，已有历史证据保留。恢复仍需通过处理、来源及 release 条件。</p><p v-else>存在已选中此 Skill 的未决任务时操作被拒绝。本页不能代为取消用户任务。</p><label>原因<select v-model="reason"><option v-for="r in ['OPS_REQUEST','SECURITY_RESPONSE','ACCOUNT_RECOVERY','DATA_OUTDATED','PERMISSION_CHANGE','OTHER']" :key="r">{{ r }}</option></select></label><div class="admin-dialog-actions"><button type="button" :disabled="busy" @click="confirmDialog?.close()">取消</button><button class="primary" :disabled="busy || !canWrite">确认变更</button></div></form></dialog>
  </div>
</template>
