<script setup lang="ts">
import { computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAppStore } from '@/stores/app'
import AppIcon from '@/components/AppIcon.vue'

const router = useRouter()
const route = useRoute()
const appStore = useAppStore()

const menuItems = computed(() =>
  router.options.routes[0].children?.filter((r) => !r.meta?.hidden) ?? []
)

function navigate(path: string) {
  router.push(path)
}
</script>

<template>
  <div class="flex h-screen gap-4">
    <!-- 侧边栏 · 深色渐变 -->
    <aside
      :class="[
        'flex flex-col text-white transition-all duration-300 overflow-hidden flex-shrink-0',
        appStore.sidebarCollapsed ? 'w-[68px]' : 'w-60',
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
        <div v-if="!appStore.sidebarCollapsed" class="ml-3 overflow-hidden">
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
            v-if="!appStore.sidebarCollapsed"
            class="ml-3 text-[15px] whitespace-nowrap"
          >
            {{ item.meta?.title }}
          </span>
        </div>
      </nav>

      <!-- 折叠按钮 -->
      <div class="p-3 border-t border-white/10 flex-shrink-0">
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
        class="h-16 flex items-center justify-between px-6 flex-shrink-0 border border-white/60 bg-white/70 backdrop-blur-xl shadow-sm shadow-slate-200/50"
      >
        <div class="flex items-center gap-3">
          <h1 class="text-lg font-semibold text-slate-800">
            {{ appStore.currentPageTitle }}
          </h1>
        </div>
        <div class="flex items-center gap-5 text-slate-500 text-sm">
          <span class="hidden sm:flex items-center gap-1.5">
            <AppIcon name="clock" :size="15" />
            {{ new Date().toLocaleDateString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit' }) }}
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

      <main class="flex-1 overflow-auto py-6 px-12">
        <router-view />
      </main>
    </div>
  </div>
</template>
