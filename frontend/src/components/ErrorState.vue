<script setup lang="ts">
withDefaults(defineProps<{
  title?: string
  message?: string
}>(), {
  title: '数据加载失败',
  message: '请检查服务连接后重试。',
})

defineEmits<{
  retry: []
}>()
</script>

<template>
  <div class="card card-pad border border-rose-200 bg-rose-50/70 text-center py-10" role="alert">
    <div class="text-2xl mb-2" aria-hidden="true">!</div>
    <h3 class="font-semibold text-rose-800">{{ title }}</h3>
    <p class="text-sm text-rose-700 mt-1">{{ message }}</p>
    <button class="btn btn-ghost mt-4 border-rose-200 text-rose-700" type="button" @click="$emit('retry')">
      重新加载
    </button>
  </div>
</template>
