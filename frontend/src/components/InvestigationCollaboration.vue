<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { accessContextKey, productRequest, uploadInvestigationAttachment, type FileView } from '../api'
import { draftKey, usePrivateDraft } from '../privateDrafts'
import FilePicker from './FilePicker.vue'
import { dataNatureLabel, displayTime, memberLabel, parseStatusLabel } from '../presentation'
const props = defineProps<{conversationId: string; readOnly: boolean; mode: 'discussion' | 'materials'; memberNames?: Record<string,string>; refreshToken?: string}>()
interface Discussion { discussionId: string; version: number; authorName: string; content: string; createdAt: string }
const emit = defineEmits<{quote: [discussion: Discussion]}>()
const discussions = ref<Discussion[]>([]), files = ref<FileView[]>([])
const draft = usePrivateDraft(computed(() => draftKey(accessContextKey(), props.conversationId, 'discussion')))
const moreFiles = ref(false), loading = ref(false), pageSize = 20
const error = ref(''), busy = ref(false), cursor = ref<number | null>(null), original = ref(''), originalTitle = ref('')
const publication = ref<FileView | null>(null), title = ref(''), applicability = ref(''), notice = ref('')
async function loadDiscussions(more = false): Promise<void> {
  const page = await productRequest<{items: Discussion[]; nextCursor: number | null}>(`conversations/${props.conversationId}/discussions?after=${more ? cursor.value ?? 0 : 0}&limit=100`)
  discussions.value = more ? [...discussions.value, ...page.items] : page.items
  cursor.value = page.nextCursor
}
async function loadFiles(more = false): Promise<void> {
  const page = await productRequest<FileView[]>(`conversations/${props.conversationId}/attachments?limit=${pageSize}&offset=${more ? files.value.length : 0}`)
  files.value = more ? [...files.value, ...page] : page
  moreFiles.value = page.length === pageSize
}
async function load(more = false): Promise<void> {
  if (loading.value) return
  loading.value = true; error.value = ''
  try { if (props.mode === 'discussion') await loadDiscussions(more); else await loadFiles(more) }
  catch(e) { error.value = e instanceof Error ? e.message : '读取失败' }
  finally { loading.value = false }
}
async function post(): Promise<void> {
  if (props.mode !== 'discussion' || props.readOnly || busy.value || !draft.value.trim()) return
  busy.value = true; error.value = ''
  try { await productRequest(`conversations/${props.conversationId}/discussions`, 'POST', {content: draft.value}); draft.value = ''; notice.value = '讨论已发表。'; await loadDiscussions() }
  catch(e) { error.value = e instanceof Error ? e.message : '发表失败' }
  finally { busy.value = false }
}
async function upload(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement, file = input.files?.[0]
  input.value = ''
  if (!file || props.readOnly || busy.value) return
  if (!/\.(md|txt|pdf)$/i.test(file.name)) { error.value = '请选择 MD/TXT；PDF 可上传但 OCR 尚未实现。'; return }
  busy.value = true; error.value = ''
  try { await uploadInvestigationAttachment(props.conversationId, file); await loadFiles(); notice.value = '附件已上传，未发起分析，也未自动发布到知识库。' }
  catch(e) { error.value = e instanceof Error ? e.message : '上传失败' }
  finally { busy.value = false }
}
async function content(file: FileView): Promise<void> {
  original.value = ''; originalTitle.value = file.originalName; error.value = ''
  try { const result = await productRequest<{content: string}>(`files/${file.fileId}/content`); original.value = result.content }
  catch(e) { error.value = e instanceof Error ? e.message : '无法读取原文' }
}
async function retry(file: FileView): Promise<void> {
  if (props.readOnly || busy.value) return
  busy.value = true
  try { await productRequest(`files/${file.fileId}/retry`, 'POST'); await loadFiles() }
  catch(e) { error.value = e instanceof Error ? e.message : '重试失败' }
  finally { busy.value = false }
}
function beginPublish(file: FileView): void {
  publication.value = file; title.value = file.title || file.originalName; applicability.value = ''; original.value = ''
}
async function publish(): Promise<void> {
  if (!publication.value || props.readOnly || busy.value) return
  busy.value = true; error.value = ''
  try { await productRequest(`files/${publication.value.fileId}/publications`, 'POST', {title: title.value, applicability: applicability.value}); publication.value = null; notice.value = '已发布到当前空间知识库，原附件与数据性质保持不变。' }
  catch(e) { error.value = e instanceof Error ? e.message : '发布失败' }
  finally { busy.value = false }
}
watch(() => [props.refreshToken, props.mode], () => { original.value = ''; publication.value = null; void load() })
onMounted(() => load())
</script>
<template>
  <section v-if="mode === 'discussion'" class="dt-discussion" :aria-busy="loading || busy" aria-label="团队讨论">
    <div class="dt-section-head"><h2>团队讨论</h2><span class="dt-tag">不自动提交给 Agent</span></div>
    <p class="dt-help">在这里商量和交流。需要参与分析的内容，引用到调查补充后再确认提交。</p>
    <button type="button" class="dt-link" :disabled="loading" @click="load()">刷新讨论</button>
    <p v-if="loading" role="status">正在读取讨论…</p>
    <article v-for="item in discussions" :key="item.discussionId" class="dt-record">
      <div class="dt-section-head"><strong>{{ item.authorName || '历史作者未知' }}</strong><time :datetime="item.createdAt" :title="item.createdAt">{{ displayTime(item.createdAt) }}</time></div>
      <p>{{ item.content }}</p><button v-if="!readOnly" type="button" class="dt-link" @click="emit('quote', item)">引用到调查补充 →</button>
    </article>
    <p v-if="!loading && !discussions.length" class="dt-empty">还没有讨论，先分享你的看法。</p>
    <button type="button" class="dt-button" :disabled="loading" v-if="cursor !== null" @click="load(true)">更多讨论</button>
    <form v-if="!readOnly" class="dt-section" @submit.prevent="post"><label class="dt-field">发表讨论<textarea id="discussion-content" aria-describedby="discussion-content-help" v-model="draft" required maxlength="8000" placeholder="交流看法或商量检查安排，不会直接触发分析…"></textarea><small id="discussion-content-help" class="field-help">请填写讨论内容，不超过 8000 个字符。</small></label><div class="dt-section-head"><span>团队可见 · 用户讨论未核实</span><button class="dt-button dt-primary" :disabled="busy || !draft.trim()">{{ busy ? '正在发表…' : '发表讨论' }}</button></div></form>
  </section>
  <section v-else class="dt-materials" :aria-busy="loading || busy" aria-label="调查附件">
    <p v-if="loading" role="status">正在读取附件…</p><p v-if="busy" role="status">正在处理，请稍候…</p>
    <p class="dt-help">附件仅服务当前调查，不会自动进入其他调查的检索。上传本身不发起分析。</p>
    <article v-for="file in files" :key="file.fileId" class="dt-record">
      <strong>{{ file.originalName }}</strong><p>{{ parseStatusLabel(file.parseStatus) }}</p>
      <small :title="file.dataNature">版本 {{ file.documentVersion ?? '尚未解析' }} · {{ dataNatureLabel(file.dataNature) }}<br>上传者：{{ memberLabel(file.uploadedByUserId, memberNames) }}</small>
      <details class="dt-technical"><summary>附件技术详情</summary><p>解析状态原值：{{ file.parseStatus }}</p><p v-if="file.failureCode">失败原因码：{{ file.failureCode }}</p><p>上传者标识：{{ file.uploadedByUserId ?? '未记录' }}</p><p>数据性质原值：{{ file.dataNature ?? '未返回' }}</p></details>
      <div class="dt-actions"><button type="button" class="dt-link" @click="content(file)">查看原文</button><button v-if="!readOnly && file.parseStatus === 'FAILED'" type="button" class="dt-link" :disabled="busy" @click="retry(file)">重试解析</button><button v-if="!readOnly && file.parseStatus === 'PARSED'" type="button" class="dt-link" @click="beginPublish(file)">发布到空间知识库</button></div>
    </article>
    <p v-if="!loading && !files.length" class="dt-empty">还没有调查附件。支持 MD/TXT；PDF/OCR 尚未实现。</p>
    <button type="button" class="dt-button" v-if="moreFiles" :disabled="loading" @click="load(true)">更多附件</button>
    <div v-if="!readOnly" class="dt-section"><FilePicker ariaLabel="上传调查附件" accept=".md,.txt,.pdf,text/markdown,text/plain,application/pdf" :disabled="busy" @change="upload" /><p class="dt-help">支持 MD / TXT；PDF 可保存，暂不能解析为可检索文本。</p></div>
    <section v-if="originalTitle" class="dt-section"><div class="dt-section-head"><h3>附件原文</h3><button type="button" class="dt-link" @click="originalTitle = ''; original = ''">收起</button></div><h4>{{ originalTitle }}</h4><pre v-if="original" class="dt-original">{{ original }}</pre><p v-else>正在读取或原文不可用。</p></section>
    <form v-if="publication" class="dt-section" @submit.prevent="publish"><h3>确认发布到当前空间</h3><p class="dt-help">同空间其他调查将可检索；未核实内容不会变成现场事实。</p><label class="dt-field">资料名称<input id="investigationcollaboration-title" v-model="title" aria-describedby="investigationcollaboration-title-help" required maxlength="500"><small id="investigationcollaboration-title-help" class="field-help">请填写此字段。</small></label><label class="dt-field">适用设备与条件<textarea id="publication-applicability" aria-describedby="publication-applicability-help" v-model="applicability" required maxlength="2000"></textarea><small id="publication-applicability-help" class="field-help">请填写适用设备与条件，不超过 2000 个字符。</small></label><div class="dt-actions"><button class="dt-button dt-primary" :disabled="busy">{{ busy ? '正在发布…' : '确认发布' }}</button><button type="button" class="dt-button" @click="publication = null">取消</button></div></form>
    <button type="button" class="dt-link" :disabled="loading" @click="load()">刷新附件</button>
  </section>
  <p v-if="notice" class="dt-feedback-success" role="status">{{ notice }}</p><p v-if="error" role="alert">{{ error }} <button type="button" class="dt-link" :disabled="loading" @click="load()">重新读取{{ mode === 'discussion' ? '讨论' : '附件' }}</button></p>
</template>
