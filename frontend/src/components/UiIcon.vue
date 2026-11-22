<script setup lang="ts">
defineProps<{ name: string }>()
const paths: Record<string, string> = {
  tool: 'm14 6 4 4 3-3a6 6 0 0 1-8 8l-6 6a3 3 0 0 1-4-4l6-6a6 6 0 0 1 8-8Z',
  users: 'M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2m20 0v-2a4 4 0 0 0-3-4M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8m8-8a4 4 0 0 1 0 8',
  spark: 'M12 2 14.8 9.2 22 12l-7.2 2.8L12 22l-2.8-7.2L2 12l7.2-2.8Z M12 8v8m-4-4h8',
  home: 'm3 10 9-7 9 7v10h-6v-6H9v6H3Z',
  chat: 'M21 15a3 3 0 0 1-3 3H9l-5 4v-4a3 3 0 0 1-2-3V6a3 3 0 0 1 3-3h13a3 3 0 0 1 3 3Z M7 10h.01m5 0h.01m5 0h.01',
  plus: 'M12 5v14M5 12h14',
  clock: 'M22 12a10 10 0 1 1-20 0 10 10 0 0 1 20 0ZM12 6v6l4 2',
  layers: 'm2 8 10-6 10 6-10 6Zm0 5 10 6 10-6M2 18l10 5 10-5',
  attach: 'm8 13 7-7a3 3 0 0 1 4 4l-9 9a5 5 0 0 1-7-7l9-9m-5 10a1.5 1.5 0 0 0 2 2l7-7',
  book: 'M12 5c-3-2-6-2-10-1v15c4-1 7-1 10 1 3-2 6-2 10-1V4c-4-1-7-1-10 1Zm0 0v15',
  send: 'm22 2-7 20-4-9-9-4 20-7ZM22 2 11 13',
  refresh: 'M20 8a8 8 0 1 0 0 8m0-13v5h-5',
  file: 'M5 2h9l5 5v15H5Zm9 0v6h5M8 12h8m-8 4h8',
  target: 'M21 11v1a9 9 0 1 1-9-9h1m5 9a6 6 0 1 1-6-6m0 6 9-9m-1 0h2v2',
  link: 'm10 13 4-4m-6 7-2 2a4 4 0 0 1-6-6l5-5a4 4 0 0 1 6 0m2 1 2-2a4 4 0 0 1 6 6l-5 5a4 4 0 0 1-6 0',
  check: 'm5 12 4 4L19 6',
  chevron: 'm9 6 6 6-6 6',
  warning: 'M12 3 2 21h20ZM12 9v5m0 3v.1',
  activity: 'M2 12h5l3-7 4 14 3-7h5',
  menu: 'M3 6h18M3 12h18M3 18h18',
  close: 'm6 6 12 12M18 6 6 18',
  bolt: 'm13 2-9 12h7l-1 8 10-12h-7Z'
}
</script>

<template>
  <svg viewBox="0 0 24 24" width="1em" height="1em" fill="none" stroke="currentColor"
       stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
    <path :d="paths[name]" />
  </svg>
</template>
