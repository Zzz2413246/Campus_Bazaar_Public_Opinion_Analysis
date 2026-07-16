<script setup lang="ts">
withDefaults(defineProps<{
  size?: 'sm' | 'md' | 'lg'
  text?: string
}>(), {
  size: 'md',
  text: '数据加载中...',
})
</script>

<template>
  <div class="flex items-center justify-center gap-2.5 py-8 text-sm text-slate-400">
  <!-- 根据size使用w-4 h-4 / w-6 h-6 / w-8 h-8，border-2，border-slate-200，border-t-brand-500，spin动画 -->
    <span :class="['border-2 border-slate-200 border-t-brand-500 animate-spin', size === 'sm' ? 'w-4 h-4' : size === 'lg' ? 'w-8 h-8' : 'w-6 h-6']"></span>
    <span v-if="text">{{ text }}</span>
  </div>
</template>
