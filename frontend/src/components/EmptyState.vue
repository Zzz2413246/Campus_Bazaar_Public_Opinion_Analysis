<script setup lang="ts">
withDefaults(defineProps<{
  text?: string
  hint?: string
}>(), {
  text: '暂无数据',
  hint: '',
})
</script>

<template>
  <div class="card card-pad text-center py-16">
    <div class="text-4xl mb-3 text-slate-200">📭</div>
    <p class="text-sm text-slate-400">{{ text }}</p>
    <p v-if="hint" class="text-xs text-slate-300 mt-1">{{ hint }}</p>
  </div>
</template>
