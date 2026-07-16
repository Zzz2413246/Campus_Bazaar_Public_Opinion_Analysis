<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'

const visible = ref(false)

function handleScroll() {
  visible.value = window.scrollY > 300
}

function scrollToTop() {
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

onMounted(() => window.addEventListener('scroll', handleScroll))
onUnmounted(() => window.removeEventListener('scroll', handleScroll))
</script>

<template>
  <Transition name="backtop">
    <button
      v-if="visible"
      @click="scrollToTop"
      class="fixed bottom-6 right-6 z-50 w-11 h-11 flex items-center justify-center bg-white border border-slate-200 shadow-lg hover:bg-slate-50 hover:border-brand-300 transition-colors"
      title="回到顶部"
    >
      <span class="text-lg text-slate-600">↑</span>
    </button>
  </Transition>
</template>

<style scoped>
.backtop-enter-active, .backtop-leave-active {
  transition: all 0.3s ease;
}
.backtop-enter-from, .backtop-leave-to {
  opacity: 0;
  transform: translateY(20px);
}
</style>
