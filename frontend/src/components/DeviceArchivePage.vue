<script setup lang="ts">
import type { WorkspaceView } from '../api'
const props = defineProps<{ space: WorkspaceView; assets: string[]; loading: boolean }>()
const emit = defineEmits<{ investigate: [assetId: string] }>()
</script>
<template>
  <section>
    <header class="dt-pagehead"><p>{{ space.name }} / 设备档案</p><h1>设备档案</h1></header>
    <main class="dt-page-main">
      <p class="dt-help">设备按当前空间组织；选择档案不代表现场配置已经核实。</p>
      <div class="dt-page-card">
        <p v-if="loading" role="status">正在读取设备档案…</p>
        <article v-for="asset in assets" :key="asset" class="dt-list-row">
          <div><strong>{{ asset }}</strong><p>设备档案 · 当前授权空间</p><p>现场配置与当前状态需按调查时点核实</p></div>
          <button type="button" class="dt-button" @click="emit('investigate', asset)">以此设备新建调查</button>
        </article>
        <div v-if="!loading && !assets.length" class="dt-empty"><h3>当前空间还没有设备档案</h3><p>不影响开始调查，新调查可以暂不指定设备。</p></div>
      </div>
    </main>
  </section>
</template>
