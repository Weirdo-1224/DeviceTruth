<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { dataNatureLabel, verificationLabel } from '../presentation'
import { ApiError, readAsset, type AssetComposite, type ConfigurationEntry } from '../api'

/**
 * 事实冲突对照：按组件+属性归组展示配置记录的来源、有效期与核实状态，并标出冲突。
 * 数据只来自服务器只读接口；知识/文档记载不等于现场核实，未读取到记录时明确说明。
 */
const props = defineProps<{ assetId: string; asOf?: string | null }>()

const asset = ref<AssetComposite | null>(null)
const loading = ref(false)
const errorText = ref('')

const SOURCE_LABELS: Record<string, string> = {
  FACTORY: '出厂记录',
  REPLACEMENT: '改造记录',
  CONFIGURATION: '旧模板',
  FIELD_OBSERVATION: '现场观测',
  MAINTENANCE: '维修记录',
  DOCUMENT: '资料记载'
}

interface Group {
  key: string
  componentId: string
  attribute: string
  entries: ConfigurationEntry[]
  conflicting: boolean
}

const groups = computed<Group[]>(() => {
  const map = new Map<string, ConfigurationEntry[]>()
  for (const entry of asset.value?.configurations ?? []) {
    const key = entry.componentId + '|' + entry.attribute
    const list = map.get(key) ?? []
    list.push(entry)
    map.set(key, list)
  }
  return [...map.entries()].map(([key, entries]) => {
    const [componentId, attribute] = key.split('|')
    const values = new Set(entries.map((entry) => entry.value))
    return { key, componentId, attribute, entries, conflicting: values.size > 1 }
  })
})

function sourceLabel(type: string): string {
  return SOURCE_LABELS[type] ?? '来源类型未明确'
}

function day(value: string | null): string {
  if (!value) return '—'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleDateString()
}

function describeError(error: unknown): string {
  if (error instanceof ApiError) return `${error.code}：${error.message}`
  return error instanceof Error ? error.message : '读取资产记录失败'
}

async function load(): Promise<void> {
  const assetId = props.assetId
  if (!assetId) {
    asset.value = null
    return
  }
  loading.value = true
  errorText.value = ''
  try {
    const loaded = await readAsset(assetId, props.asOf ?? undefined)
    if (props.assetId === assetId) asset.value = loaded
  } catch (error) {
    if (props.assetId === assetId) {
      asset.value = null
      errorText.value = describeError(error)
    }
  } finally {
    if (props.assetId === assetId) loading.value = false
  }
}

watch(() => [props.assetId, props.asOf], load, { immediate: true })
</script>

<template>
  <section v-if="assetId" class="card comparison">
    <h2>事实对照：{{ assetId }}</h2>
    <p v-if="loading" class="hint">正在读取该资产的记录…</p>
    <p v-else-if="errorText" class="error" role="alert">{{ errorText }}</p>
    <template v-else-if="asset">
      <p class="hint">
        数据性质 {{ dataNatureLabel(asset.dataNature) }} · 时点 {{ day(asset.asOf) }} ·
        装机判断 <strong>{{ asset.installationStatus === 'FIELD_CONFIRMED' ? '现场已确认' : '未确认' }}</strong> ·
        冲突 <strong :class="{ conflict: asset.conflict === 'CONFLICT' }">{{ asset.conflict === 'CONFLICT' ? '存在' : '无' }}</strong>
      </p>
      <p v-if="groups.length === 0" class="hint">未读取到该资产的配置记录。</p>
      <div v-else class="table-scroll"><table class="facts">
        <thead>
          <tr>
            <th>组件 / 属性</th>
            <th>取值</th>
            <th>来源</th>
            <th>核实状态</th>
            <th>有效期自</th>
            <th>记录时间</th>
          </tr>
        </thead>
        <tbody>
          <template v-for="group in groups" :key="group.key">
            <tr v-for="(entry, index) in group.entries" :key="entry.recordId" :class="{ conflict: group.conflicting }">
              <td>
                <span v-if="index === 0">{{ group.componentId }} / {{ group.attribute }}</span>
              </td>
              <td>{{ entry.value }}</td>
              <td :title="entry.sourceType">{{ sourceLabel(entry.sourceType) }}</td>
              <td>
                <span :class="['verify', entry.verificationStatus === 'FIELD_VERIFIED' ? 'field' : 'documented']" :title="entry.verificationStatus">
                  {{ verificationLabel(entry.verificationStatus) }}
                </span>
              </td>
              <td>{{ day(entry.validFrom) }}</td>
              <td>{{ day(entry.recordedAt) }}</td>
            </tr>
          </template>
        </tbody>
      </table></div>
      <p v-if="asset.requiredChecks.length > 0" class="hint">
        还需核验：{{ asset.requiredChecks.join('；') }}
      </p>
      <div v-if="asset.maintenance.length > 0" class="maintenance">
        <h3>维修记录</h3>
        <ul>
          <li v-for="entry in asset.maintenance" :key="entry.recordId" :title="entry.sourceType">
            {{ entry.componentId }} · {{ entry.operation }}：
            {{ entry.beforeValue ?? '—' }} → {{ entry.afterValue ?? '—' }}
            · {{ sourceLabel(entry.sourceType) }} · 有效期自 {{ day(entry.validFrom) }}
          </li>
        </ul>
      </div>
    </template>
  </section>
</template>

<style scoped>
.comparison { padding: 16px 0; border-top: 1px solid var(--line); overflow-wrap: anywhere; }
.table-scroll { overflow-x: auto; }
.facts { min-width: 680px; }

.comparison h2 {
  font-size: 16px;
  margin: 0 0 8px;
}
.facts {
  width: 100%;
  border-collapse: collapse;
  font-size: 16px;
}
.facts th,
.facts td {
  border-bottom: 1px solid #eef2f5;
  padding: 9px 8px;
  text-align: left;
  vertical-align: top;
}
.facts tr.conflict {
  background: var(--warn-bg);
}
.conflict {
  color: var(--warn);
}
.verify {
  font-size: 14px;
  border-radius: 10px;
  padding: 1px 6px;
}
.verify.field {
  color: var(--success);
  background: var(--success-bg);
}
.verify.documented {
  color: var(--text-2);
  background: #eef2f5;
}
.maintenance h3 {
  font-size: 16px;
  margin: 12px 0 4px;
}
.maintenance ul {
  margin: 0;
  padding-left: 18px;
  font-size: 16px;
  color: var(--text-2);
}
.hint {
  color: var(--text-3);
  font-size: 16px;
  margin: 4px 0;
}
.error {
  border: 1px solid var(--danger);
  background: var(--danger-bg);
  color: var(--danger);
  padding: 8px 12px;
  border-radius: 6px;
}
</style>
