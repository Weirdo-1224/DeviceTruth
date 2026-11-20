<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { accessContextKey, productRequest, type FileView } from '../api'
import { draftKey, usePrivateDraft } from '../privateDrafts'
const props = defineProps<{conversationId: string; readOnly: boolean; memberNames?: Record<string,string>; refreshToken?: string}>()
interface Discussion { discussionId: string; version: number; authorName: string; content: string; createdAt: string }
const parseLabels: Record<FileView['parseStatus'],string> = {UPLOADED:'等待解析',PROCESSING:'解析中',PARSED:'解析完成',FAILED:'解析失败',OCR_REQUIRED:'PDF/OCR 尚未实现'}
const emit = defineEmits<{quote: [discussion: Discussion]}>()
const discussions = ref<Discussion[]>([]), files = ref<FileView[]>([])
const draft = usePrivateDraft(computed(() => draftKey(accessContextKey(), props.conversationId, 'discussion')))
const moreFiles = ref(false), loading = ref(false), pageSize = 20
const error = ref(''), busy = ref(false), cursor = ref<number | null>(null), original = ref('')
const publication = ref<FileView | null>(null), title = ref(''), applicability = ref(''), notice = ref('')
async function load(more = false) {
  if (loading.value) return
  loading.value = true; error.value = ''
  try {
    const page = await productRequest<{items: Discussion[]; nextCursor: number | null}>(`conversations/${props.conversationId}/discussions?after=${more ? cursor.value ?? 0 : 0}&limit=100`)
    discussions.value = more ? [...discussions.value, ...page.items] : page.items; cursor.value = page.nextCursor
    if (!more) await loadFiles()
  } catch(e) { error.value = e instanceof Error ? e.message : '读取失败' } finally { loading.value = false }
}
async function loadFiles(more = false) {
  const page = await productRequest<FileView[]>(`conversations/${props.conversationId}/attachments?limit=${pageSize}&offset=${more ? files.value.length : 0}`)
  files.value = more ? [...files.value, ...page] : page
  moreFiles.value = page.length === pageSize
}
async function moreAttachments() {
  if (loading.value) return
  loading.value = true
  try { await loadFiles(true) } catch(e) { error.value = e instanceof Error ? e.message : '附件读取失败' } finally { loading.value = false }
}
async function post() {
  if (props.readOnly || busy.value || !draft.value.trim()) return
  busy.value = true; error.value = ''
  try { await productRequest(`conversations/${props.conversationId}/discussions`, 'POST', {content: draft.value}); draft.value = ''; await load() }
  catch(e) { error.value = e instanceof Error ? e.message : '发表失败' } finally { busy.value = false }
}
async function content(file: FileView) {
  original.value = ''
  try { const result = await productRequest<{content: string}>(`files/${file.fileId}/content`); original.value = result.content }
  catch(e) { error.value = e instanceof Error ? e.message : '无法读取原文' }
}
async function retry(file: FileView) { if (props.readOnly || busy.value) return; try { await productRequest(`files/${file.fileId}/retry`, 'POST'); await load() } catch(e) { error.value = e instanceof Error ? e.message : '重试失败' } }
async function publish() {
  if (!publication.value || props.readOnly || busy.value) return
  busy.value = true
  try { await productRequest(`files/${publication.value.fileId}/publications`, 'POST', {title: title.value, applicability: applicability.value}); publication.value = null; notice.value = '已发布到当前空间知识库，原附件与数据性质保持不变。' }
  catch(e) { error.value = e instanceof Error ? e.message : '发布失败' } finally { busy.value = false }
}
watch(() => props.refreshToken, () => { void load() })
onMounted(() => load())
</script>
<template>
  <section class="collaboration pro-card">
    <h3>团队讨论</h3><p>讨论不会自动交给 Agent；选择引用并正式提交后才参与分析。</p>
    <button :disabled="loading" @click="load()">刷新讨论与附件</button>
    <p v-if="loading" role="status">正在读取讨论与附件…</p><p v-if="!loading && !discussions.length">尚无讨论。</p>
    <article v-for="item in discussions" :key="item.discussionId"><strong>{{ item.authorName || '历史作者未知' }}</strong> · <time>{{ item.createdAt }}</time><p>{{ item.content }}</p><button v-if="!readOnly" @click="emit('quote', item)">引用到正式补充</button></article>
    <button :disabled="loading" v-if="cursor !== null" @click="load(true)">更多讨论</button>
    <form v-if="!readOnly" @submit.prevent="post"><label>发表讨论（未核实）<textarea v-model="draft" required maxlength="8000"></textarea></label><button :disabled="busy || !draft.trim()">发表讨论，不运行分析</button></form>
    <h3>本调查附件</h3><p v-if="!files.length">尚无附件。上传支持 MD/TXT；PDF/OCR 尚未实现。</p>
    <article v-for="file in files" :key="file.fileId"><strong>{{ file.originalName }}</strong> · {{ parseLabels[file.parseStatus] }} · {{ file.failureCode }}<p>版本 {{ file.documentVersion ?? '尚未解析' }} · {{ file.dataNature ?? '数据性质未知' }} · 上传者 {{ file.uploadedByUserId ? memberNames?.[file.uploadedByUserId] ?? file.uploadedByUserId : '历史作者未知' }}</p><button @click="content(file)">查看原文</button><button v-if="!readOnly && file.parseStatus === 'FAILED'" @click="retry(file)">重试解析</button><button v-if="!readOnly && file.parseStatus === 'PARSED'" @click="publication = file; title = file.title || file.originalName; applicability = ''">发布到空间知识库</button></article>
    <button v-if="moreFiles" :disabled="loading" @click="moreAttachments">更多附件</button><pre v-if="original" class="original">{{ original }}</pre>
    <form v-if="publication" @submit.prevent="publish"><h4>确认发布到当前空间</h4><p>同空间其他调查将可检索；未核实内容不会变成现场事实。</p><label>资料名称<input v-model="title" required maxlength="500"></label><label>适用设备与条件<textarea v-model="applicability" required maxlength="2000"></textarea></label><button :disabled="busy">确认发布</button><button type="button" @click="publication = null">取消</button></form>
    <p role="status">{{ notice }}</p><p role="alert">{{ error }}</p>
  </section>
</template>
<style scoped>.collaboration { padding:20px; margin:16px 0; } article { padding:12px 0; border-bottom:1px solid #dce2e9; } textarea { display:block; width:100%; min-height:72px; } button { margin:6px; padding:8px 12px; border:1px solid #cbd5e1; background:#f8fafc; border-radius:7px; font:inherit; color:#24415e; cursor:pointer; } button:disabled { opacity:.5; cursor:default; } textarea,input { font:inherit; padding:10px; border:1px solid #cbd5e1; border-radius:7px; box-sizing:border-box; } button:focus-visible,textarea:focus-visible { outline:2px solid #2468aa; } .original { white-space:pre-wrap; max-height:360px; overflow:auto; } label { display:block; margin:10px 0; }</style>
