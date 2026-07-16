<script setup lang="ts">
import { ref } from 'vue'

const props = defineProps<{
  onRefresh: () => void | Promise<void>
}>()

const loading = ref(false)

async function handleClick() {
  if (loading.value) return
  loading.value = true
  try {
    await props.onRefresh()
  } finally {
    // 至少显示300ms加载态避免闪烁
    setTimeout(() => { loading.value = false }, 300)
  }
}
</script>

<template>
  <button
    @click="handleClick"
    :disabled="loading"
    class="btn btn-ghost !py-1.5 !px-3 text-xs inline-flex items-center gap-1.5"
    title="刷新数据"
  >
    <span :class="['inline-block', loading ? 'animate-spin' : '']">↻</span>
    <span>{{ loading ? '刷新中...' : '刷新' }}</span>
  </button>
</template>
