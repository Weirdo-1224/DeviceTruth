<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { productRequest, type WorkspaceView } from '../api'
interface KnowledgeDocument { documentId: string; title: string; documentVersion: string; dataNature: string; ingestStatus: string; applicability: string; originalAuthorUserId: string | null; publishedByUserId: string | null; publishedAt: string | null }
const props = defineProps<{ space: WorkspaceView; memberNames: Record<string, string> }>()
const documents = ref<KnowledgeDocument[]>([]), loading = ref(false), more = ref(false), error = ref('')
const pageSize = 20
async function load(append = false): Promise<void> {
  if (loading.value) return
  loading.value = true; error.value = ''
  try {
    const page = await productRequest<KnowledgeDocument[]>(`knowledge/documents?limit=${pageSize}&offset=${append ? documents.value.length : 0}`)
    documents.value = append ? [...documents.value, ...page] : page
    more.value = page.length === pageSize
  } catch (e) { error.value = e instanceof Error ? e.message : '知识库读取失败' }
  finally { loading.value = false }
}
function author(id: string | null, unknown: string): string { return id ? props.memberNames[id] ?? id : unknown }
onMounted(() => load())
</script>
<template>
  <section><header class="dt-pagehead"><div class="dt-title-row"><div><p>{{ space.name }} / 知识库</p><h1>{{ space.kind === 'TEAM' ? '团队知识库' : '个人知识库' }}</h1></div><span class="dt-tag">{{ documents.length }} 份资料</span></div></header>
    <main class="dt-page-main"><p class="dt-help">这里的资料可供当前空间的调查复用。调查附件需明确发布后才会出现在这里。</p>
      <div class="dt-page-card">
        <p v-if="loading && !documents.length" role="status">正在读取知识库…</p>
        <article v-for="doc in documents" :key="doc.documentId" class="dt-list-row"><div>
          <strong>{{ doc.title }}</strong><p>适用范围：{{ doc.applicability }}</p>
          <p>{{ author(doc.publishedByUserId, '历史发布人未知') }}发布 · 原作者：{{ author(doc.originalAuthorUserId, '历史作者未知') }}</p>
          <p>版本 {{ doc.documentVersion }} · {{ doc.dataNature }} · {{ doc.publishedAt ?? '历史发布时间未知' }}</p>
        </div><span class="dt-tag dt-success">已加入</span></article>
        <div v-if="!loading && !documents.length && !error" class="dt-empty"><h3>知识库还没有资料</h3><p>可从调查附件中选择有复用价值的材料，明确适用范围后发布。</p></div>
        <button v-if="more" type="button" class="dt-button" :disabled="loading" @click="load(true)">{{ loading ? '正在读取…' : '更多资料' }}</button>
        <p v-if="error" role="alert">{{ error }} <button type="button" class="dt-link" @click="load()">重试</button></p>
      </div>
    </main>
  </section>
</template>
