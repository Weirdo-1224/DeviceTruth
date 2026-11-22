<script setup lang="ts">
import UiIcon from './UiIcon.vue'
defineProps<{ accept: string; ariaLabel: string; disabled?: boolean }>()
const emit = defineEmits<{ change: [event: Event] }>()
</script>
<template>
  <label class="file-picker dt-button" :class="{ disabled }">
    <UiIcon name="attach" /><span>添加附件</span>
    <input class="visually-hidden" type="file" :accept="accept" :aria-label="ariaLabel" :disabled="disabled" @change="emit('change', $event)">
  </label>
</template>
<style scoped>
.file-picker { display: inline-flex; align-items: center; gap: 8px; width: fit-content; max-width: 100%; margin: 0; font-size: 16px; cursor: pointer; color: var(--accent); }
.file-picker:focus-within { outline: 2px solid var(--primary); outline-offset: 3px; }
.file-picker.disabled { opacity: .5; cursor: not-allowed; }
</style>
