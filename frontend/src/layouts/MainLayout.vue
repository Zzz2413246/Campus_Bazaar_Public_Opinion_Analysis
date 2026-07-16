<script setup lang="ts">
import { computed, ref, onMounted, onUnmounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAppStore } from '@/stores/app'
import AppIcon from '@/components/AppIcon.vue'
import ToastContainer from '@/components/ToastContainer.vue'
import BackToTop from '@/components/BackToTop.vue'

const router = useRouter()
const route = useRoute()
const appStore = useAppStore()

const menuItems = computed(() =>
  router.options.routes[0].children?.filter((r) => !r.meta?.hidden) ?? []
)

// 移动端侧边栏控制
const sidebarOpen = ref(false)
const isMobile = ref(false)

function checkMobile() {
  isMobile.value = window.innerWidth < 768
  if (!isMobile.value) sidebarOpen.value = false
}

onMounted(() => {
  checkMobile()
  window.addEventListener('resize', checkMobile)
})
onUnmounted(() => {
  window.removeEventListener('resize', checkMobile)
})

function toggleSidebar() {
  if (isMobile.value) {
    sidebarOpen.value = !sidebarOpen.value
  }
}

function navigate(path: string) {
  router.push(path)
  // 移动端点击导航后关闭侧边栏
  if (isMobile.value) {
    sidebarOpen.value = false
  }
}

// 刷新当前页面
function refreshPage() {
  window.location.reload()
}
</script>

<template>
  <div class="flex h-screen gap-4">
    <!-- 移动端遮罩层 -->
    <div
      v-if="isMobile && sidebarOpen"
      @click="sidebarOpen = false"
      class="fixed inset-0 bg-black/40 z-40 md:hidden"
    ></div>

    <!-- 侧边栏 · 深色渐变 -->
    <aside
      :class="[
        'flex flex-col text-white overflow-hidden sidebar-transition',
        /* 移动端：固定定位 + 滑入滑出 */
        'fixed inset-y-0 left-0 z-50 w-60',
        sidebarOpen ? 'translate-x-0' : '-translate-x-full',
        /* 桌面端：相对定位，始终可见 */
        'md:relative md:translate-x-0 md:flex-shrink-0',
        appStore.sidebarCollapsed ? 'md:w-[68px]' : 'md:w-60',
      ]"
      style="background: linear-gradient(180deg, #1e1b4b 0%, #0f172a 55%, #0b1220 100%);"
    >
      <!-- Logo 区 -->
      <div
        class="flex items-center h-16 px-5 border-b border-white/10 flex-shrink-0"
      >
        <div class="w-9 h-9 rounded-xl bg-gradient-to-br from-brand-500 to-accent-500 flex items-center justify-center flex-shrink-0 shadow-lg shadow-brand-600/30 text-white">
          <AppIcon name="shield" :size="20" />
        </div>
        <div v-if="!appStore.sidebarCollapsed || isMobile" class="ml-3 overflow-hidden">
          <div class="text-base font-semibold whitespace-nowrap tracking-wide leading-tight">校园安全舆情</div>
          <div class="text-xs text-slate-400 whitespace-nowrap leading-tight mt-0.5">智能研判平台</div>
        </div>
      </div>

      <!-- 导航 -->
      <nav class="flex-1 py-4 overflow-y-auto px-3">
        <div
          v-for="item in menuItems"
          :key="item.path"
          @click="navigate(`/${item.path}`)"
          :class="[
            'flex items-center px-3.5 py-3 cursor-pointer transition-all duration-200 mb-1 group relative',
            route.path === `/${item.path}`
              ? 'bg-gradient-to-r from-brand-600 to-brand-500 text-white shadow-lg shadow-brand-600/30'
              : 'text-slate-400 hover:bg-white/5 hover:text-white',
          ]"
        >
          <!-- 激活态左侧指示条 -->
          <span
            v-if="route.path === `/${item.path}`"
            class="absolute left-0 top-1/2 -translate-y-1/2 w-1 h-6 bg-accent-400 rounded-r-full"
          ></span>
          <span class="flex-shrink-0 w-5 flex items-center justify-center">
            <AppIcon :name="String(item.meta?.icon)" :size="20" />
          </span>
          <span
            v-if="!appStore.sidebarCollapsed || isMobile"
            class="ml-3 text-[15px] whitespace-nowrap"
          >
            {{ item.meta?.title }}
          </span>
        </div>
      </nav>

      <!-- 桌面端折叠按钮（移动端隐藏） -->
      <div class="p-3 border-t border-white/10 flex-shrink-0 hidden md:block">
        <button
          @click="appStore.toggleSidebar()"
          class="w-full flex items-center justify-center py-2 text-slate-500 hover:text-white transition-colors cursor-pointer hover:bg-white/5"
        >
          <AppIcon :name="appStore.sidebarCollapsed ? 'chevron-right' : 'chevron-left'" :size="16" />
          <span v-if="!appStore.sidebarCollapsed" class="ml-2 text-xs">收起</span>
        </button>
      </div>
    </aside>

    <!-- 右侧内容区 -->
    <div class="flex-1 flex flex-col overflow-hidden">
      <!-- 顶栏 · 玻璃拟态 -->
      <header
        class="h-16 flex items-center justify-between px-4 md:px-6 flex-shrink-0 border border-white/60 bg-white/70 backdrop-blur-xl shadow-sm shadow-slate-200/50"
      >
        <div class="flex items-center gap-3">
          <!-- 汉堡菜单按钮（仅移动端显示） -->
          <button
            @click="toggleSidebar"
            class="md:hidden p-2 -ml-2 text-slate-600 hover:text-slate-800 transition-colors cursor-pointer"
            aria-label="菜单"
          >
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <line x1="3" y1="6" x2="21" y2="6"></line>
              <line x1="3" y1="12" x2="21" y2="12"></line>
              <line x1="3" y1="18" x2="21" y2="18"></line>
            </svg>
          </button>
          <h1 class="text-lg font-semibold text-slate-800">
            {{ appStore.currentPageTitle }}
          </h1>
        </div>
        <div class="flex items-center gap-5 text-slate-500 text-sm">
          <span class="hidden sm:flex items-center gap-1.5">
            <AppIcon name="clock" :size="15" />
            {{ new Date().toLocaleDateString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit' }) }}
          </span>
          <!-- 刷新按钮 -->
          <span
            @click="refreshPage"
            class="cursor-pointer hover:text-slate-700 transition-colors"
            title="刷新"
          >
            <AppIcon name="refresh" :size="18" />
          </span>
          <span class="relative cursor-pointer hover:text-slate-700 transition-colors">
            <AppIcon name="bell" :size="18" />
            <span class="absolute -top-0.5 -right-0.5 w-2 h-2 bg-rose-500 rounded-full ring-2 ring-white"></span>
          </span>
          <div class="flex items-center gap-2 cursor-pointer hover:text-slate-700 transition-colors">
            <div class="w-7 h-7 rounded-full bg-gradient-to-br from-brand-500 to-accent-500 flex items-center justify-center text-xs text-white font-medium">
              管
            </div>
            <span class="hidden sm:inline">管理员</span>
          </div>
        </div>
      </header>

      <main class="flex-1 overflow-auto py-6 px-4 md:px-12">
        <router-view v-slot="{ Component }">
          <Transition name="page" mode="out-in">
            <component :is="Component" />
          </Transition>
        </router-view>
      </main>
    </div>

    <!-- 全局 Toast 提示容器 -->
    <ToastContainer />

    <!-- 回到顶部 -->
    <BackToTop />
  </div>
</template>

<style scoped>
/* 路由切换过渡动画（补充 style.css 中未定义的 leave 样式） */
.page-enter-active,
.page-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}
.page-enter-from {
  opacity: 0;
  transform: translateY(8px);
}
.page-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}
</style>
