<script setup lang="ts">
import { watch } from 'vue'
import { useRoute } from 'vue-router'
import { useAppStore } from '@/stores/app'

const route = useRoute()
const appStore = useAppStore()

watch(
  () => route.meta?.title,
  (title) => {
    if (title && typeof title === 'string') {
      appStore.setPageTitle(title)
    }
  },
  { immediate: true }
)
</script>

<template>
  <router-view />
</template>
