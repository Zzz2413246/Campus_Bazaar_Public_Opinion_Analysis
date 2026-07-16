<script setup lang="ts">
import { toast } from '@/utils/toast'

// 各类型 toast 的样式与图标配置
const typeConfig = {
  success: { bg: 'bg-emerald-50 border-emerald-200', text: 'text-emerald-700', icon: '✓' },
  error: { bg: 'bg-rose-50 border-rose-200', text: 'text-rose-700', icon: '✕' },
  warning: { bg: 'bg-amber-50 border-amber-200', text: 'text-amber-700', icon: '⚠' },
  info: { bg: 'bg-sky-50 border-sky-200', text: 'text-sky-700', icon: 'ℹ' },
}
</script>

<template>
  <div class="fixed top-20 right-4 z-[9999] space-y-2 pointer-events-none">
    <TransitionGroup name="toast">
      <div
        v-for="t in toast.toasts"
        :key="t.id"
        :class="['pointer-events-auto flex items-center gap-2.5 px-4 py-3 border shadow-md min-w-[260px] max-w-[400px] cursor-pointer', typeConfig[t.type].bg]"
        @click="toast.remove(t.id)"
      >
        <span :class="['font-bold text-base', typeConfig[t.type].text]">{{ typeConfig[t.type].icon }}</span>
        <span class="text-sm text-slate-700 flex-1">{{ t.message }}</span>
      </div>
    </TransitionGroup>
  </div>
</template>

<style scoped>
.toast-enter-active,
.toast-leave-active {
  transition: all 0.3s ease;
}
.toast-enter-from {
  opacity: 0;
  transform: translateX(120%);
}
.toast-leave-to {
  opacity: 0;
  transform: translateX(120%);
}
</style>
