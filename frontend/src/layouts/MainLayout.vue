<script setup lang="ts">
import { computed, ref, onMounted, onUnmounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAppStore } from '@/stores/app'
import AppIcon from '@/components/AppIcon.vue'
import ToastContainer from '@/components/ToastContainer.vue'
import BackToTop from '@/components/BackToTop.vue'
import FloatingAssistant from '@/components/FloatingAssistant.vue'
import { authApi } from '@/utils/api'

const router = useRouter()
const route = useRoute()
const appStore = useAppStore()
const currentNickname = ref(localStorage.getItem('yuqing_nickname') || '管理员')

const menuItems = computed(() => {
  const mainLayoutRoute = router.options.routes.find((route) => route.path === '/')
  return mainLayoutRoute?.children?.filter((route) => !route.meta?.hidden) ?? []
})

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

async function logout() {
  try {
    await authApi.logout()
  } catch {
    // 本地退出不依赖后端响应
  } finally {
    localStorage.removeItem('yuqing_token')
    localStorage.removeItem('yuqing_nickname')
    router.replace('/login')
  }
}
</script>

<template>
  <div
    :class="[
      'model-layout flex h-screen',
      appStore.sidebarCollapsed ? 'md:gap-0' : 'md:gap-4',
    ]"
  >
    <!-- 移动端遮罩层 -->
    <div
      v-if="isMobile && sidebarOpen"
      @click="sidebarOpen = false"
      class="fixed inset-0 bg-black/40 z-40 md:hidden"
    ></div>

    <!-- 侧边栏 · 深色渐变 -->
    <aside
      :class="[
        'model-sidebar flex flex-col text-white overflow-hidden',
        /* 移动端：固定定位 + 滑入滑出 */
        'fixed inset-y-0 left-0 z-50 w-60',
        sidebarOpen ? 'translate-x-0' : '-translate-x-full',
        /* 桌面端：相对定位，始终可见 */
        'md:relative md:translate-x-0 md:flex-shrink-0',
        appStore.sidebarCollapsed
          ? 'md:w-0 md:opacity-0 md:-translate-x-4 md:pointer-events-none'
          : 'md:w-60 md:opacity-100 md:translate-x-0',
      ]"
      style="background: linear-gradient(180deg, #1e1b4b 0%, #0f172a 55%, #0b1220 100%);"
    >
      <!-- Logo 区 -->
      <div
        :class="[
          'flex items-center h-20 border-b border-white/10 flex-shrink-0',
          appStore.sidebarCollapsed && !isMobile ? 'justify-center px-3' : 'px-4',
        ]"
      >
        <div class="relative w-11 h-11 bg-white/10 border border-white/15 flex items-center justify-center flex-shrink-0 shadow-lg shadow-black/15 text-cyan-300">
          <span class="absolute inset-1 border border-cyan-400/20"></span>
          <AppIcon name="shield" :size="24" />
          <span class="absolute right-1.5 bottom-1.5 w-1.5 h-1.5 bg-emerald-400 ring-2 ring-slate-900"></span>
        </div>
        <div v-if="!appStore.sidebarCollapsed || isMobile" class="ml-3.5 min-w-0 overflow-hidden">
          <div class="text-[18px] font-bold text-white whitespace-nowrap tracking-[0.04em] leading-tight">校园安全舆情</div>
          <div class="text-[13px] text-slate-300 whitespace-nowrap leading-tight mt-1 tracking-wider">智能研判与预警平台</div>
        </div>
      </div>

      <!-- 导航 -->
      <nav class="flex-1 py-5 overflow-y-auto px-4">
        <div
          v-for="item in menuItems"
          :key="item.path"
          @click="navigate(`/${item.path}`)"
          :class="[
            'flex items-center justify-center gap-3 px-4 py-3.5 cursor-pointer transition-all duration-200 mb-1.5 group relative',
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
          <span class="flex-shrink-0 w-6 flex items-center justify-center">
            <AppIcon :name="String(item.meta?.icon)" :size="21" />
          </span>
          <span
            v-if="!appStore.sidebarCollapsed || isMobile"
            class="text-[17px] font-medium whitespace-nowrap tracking-wide"
          >
            {{ item.meta?.title }}
          </span>
        </div>
      </nav>

    </aside>

    <!-- 右侧内容区 -->
    <div class="flex-1 flex flex-col overflow-hidden">
      <!-- 顶栏 · 玻璃拟态 -->
      <header
        class="app-header relative h-16 flex items-center justify-between flex-shrink-0 border border-white/60 bg-white/70 backdrop-blur-xl shadow-sm shadow-slate-200/50"
        :style="{
          paddingLeft: isMobile ? '20px' : '80px',
          paddingRight: isMobile ? '20px' : '48px',
        }"
      >
        <button
          class="sidebar-model-toggle hidden md:flex absolute left-6 top-1/2 -translate-y-1/2 w-9 h-9 items-center justify-center text-slate-500 hover:text-slate-900 hover:bg-slate-100/80 cursor-pointer"
          type="button"
          :title="appStore.sidebarCollapsed ? '展开侧边栏' : '收起侧边栏'"
          :aria-label="appStore.sidebarCollapsed ? '展开侧边栏' : '收起侧边栏'"
          :aria-expanded="!appStore.sidebarCollapsed"
          @click="appStore.toggleSidebar()"
        >
          <AppIcon
            :name="appStore.sidebarCollapsed ? 'panel-left-open' : 'panel-left-close'"
            :size="20"
          />
        </button>
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
          <h1 class="text-xl font-semibold text-slate-900 tracking-wide">
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
          <div class="flex items-center gap-2">
            <div class="w-7 h-7 rounded-full bg-gradient-to-br from-brand-500 to-accent-500 flex items-center justify-center text-xs text-white font-medium">
              {{ currentNickname.slice(0, 1) }}
            </div>
            <span class="hidden sm:inline">{{ currentNickname }}</span>
            <button
              class="p-1.5 text-slate-500 hover:text-rose-600 hover:bg-rose-50 transition-colors cursor-pointer"
              title="退出登录"
              aria-label="退出登录"
              @click="logout"
            >
              <AppIcon name="logout" :size="17" />
            </button>
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

    <!-- 全局悬浮智能助手 -->
    <FloatingAssistant />
  </div>
</template>

<style scoped>
.model-layout {
  transition: column-gap 320ms cubic-bezier(0.22, 1, 0.36, 1);
}

.model-sidebar {
  transition:
    width 320ms cubic-bezier(0.22, 1, 0.36, 1),
    transform 320ms cubic-bezier(0.22, 1, 0.36, 1),
    opacity 180ms ease;
  will-change: width, transform, opacity;
}

.sidebar-model-toggle {
  transition:
    color 160ms ease,
    background-color 160ms ease,
    transform 220ms ease;
}

.sidebar-model-toggle:hover {
  transform: translateY(-50%) scale(1.06);
}

.sidebar-model-toggle:active {
  transform: translateY(-50%) scale(0.94);
}

.app-header {
  /* 与桌面端主体内容的起始位置保持一致，且不依赖构建时生成的工具类 */
  padding-left: 80px;
  padding-right: 48px;
}

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
