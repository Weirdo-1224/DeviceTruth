<script setup lang="ts">
import type { AnswerReviewView } from '../answerReviewView'
defineProps<{ review: AnswerReviewView }>()
const emit = defineEmits<{ openReference: [referenceId: string] }>()
</script>

<template>
  <section class="answer-review" aria-label="逐项复核">
    <p v-if="!review.available || !review.units.length" class="review-history">{{ review.heading }}</p>
    <details v-else :open="review.limited">
      <summary>{{ review.heading }}</summary>
      <ul>
        <li v-for="unit in review.units" :key="unit.id">
          <div class="review-labels"><span>{{ unit.nature }}</span><strong :class="{ pending: unit.disposition !== 'RETAINED' }">{{ unit.status }}</strong></div>
          <p>{{ unit.text }}</p>
          <p v-if="unit.reason" class="review-reason">{{ unit.reason }} · {{ unit.id }}</p>
          <div v-for="(source, index) in unit.sources" :key="index" class="review-source">
            <button v-if="source.readable" type="button" @click="emit('openReference', source.id)">{{ source.label }}</button>
            <span v-else>{{ source.label }}</span>
            <q>{{ source.quote }}</q>
          </div>
        </li>
      </ul>
    </details>
  </section>
</template>

<style scoped>
.answer-review { margin-block: 16px; color: var(--text-2, #425466); font-size: 14px; line-height: 1.65; }
summary { cursor: pointer; font-weight: 600; color: var(--text-1, #172b4d); }
ul { list-style: none; padding: 0; margin: 12px 0 0; }
li { padding-block: 12px; border-top: 1px solid var(--line-soft, #dde4eb); break-inside: avoid; }
p { margin: 5px 0; overflow-wrap: anywhere; }
.review-labels { display: flex; flex-wrap: wrap; gap: 8px 16px; align-items: baseline; }
.review-labels strong { color: var(--accent-2, #276951); }
.review-labels .pending { color: #936411; }
.review-reason, .review-history { color: var(--text-3, #67778a); }
.review-source { display: flex; flex-wrap: wrap; gap: 4px 10px; margin-top: 6px; }
.review-source button { color: var(--accent-2, #276951); background: transparent; border: 0; padding: 0; text-decoration: underline; font: inherit; cursor: pointer; }
q { overflow-wrap: anywhere; }
@media print { details > ul { display: block !important; } summary { list-style: none; } .review-source button { text-decoration: none; } }
</style>
