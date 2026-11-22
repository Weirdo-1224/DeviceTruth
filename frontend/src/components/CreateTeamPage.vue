<script setup lang="ts">
import { ref } from 'vue'
import { workspaceRequest, type WorkspaceView } from '../api'
const emit = defineEmits<{ created: [space: WorkspaceView]; cancel: [] }>()
const name = ref(''), busy = ref(false), error = ref('')
async function create(): Promise<void> {
  if (busy.value || !name.value.trim()) return
  busy.value = true; error.value = ''
  try { emit('created', await workspaceRequest<WorkspaceView>('', 'POST', { name: name.value.trim() })) }
  catch (e) { error.value = e instanceof Error ? e.message : '创建团队失败' }
  finally { busy.value = false }
}
</script>
<template>
  <section><header class="dt-pagehead"><p>工作空间 / 创建团队</p><h1>创建团队</h1></header>
    <main class="dt-page-main"><form class="dt-empty-form" @submit.prevent="create">
      <h2>和同事一起推进调查</h2><p class="dt-help">团队拥有独立的调查、设备与知识库。个人内容不会自动转入团队。</p>
      <label class="dt-field">团队名称<input id="createteampage-name" v-model="name" aria-describedby="createteampage-name-help" required maxlength="100" placeholder="例如：华东售后检修组"><small id="createteampage-name-help" class="field-help">请填写名称，不超过 100 个字符。</small></label>
      <div class="dt-actions"><button class="dt-button dt-primary" :disabled="busy">{{ busy ? '正在创建…' : '创建团队' }}</button><button type="button" class="dt-link" @click="emit('cancel')">返回调查</button></div>
      <p v-if="error" role="alert">{{ error }}</p>
    </form></main>
  </section>
</template>
