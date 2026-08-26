<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppIcon from '@/components/AppIcon.vue'
import ToastContainer from '@/components/ToastContainer.vue'
import BackToTop from '@/components/BackToTop.vue'
import FloatingAssistant from '@/components/FloatingAssistant.vue'
import { authApi } from '@/utils/api'

const router = useRouter()
const route = useRoute()
const currentNickname = ref(localStorage.getItem('yuqing_nickname') || '管理员')
const currentRole = ref(localStorage.getItem('yuqing_role') || 'ADMIN')
const refreshKey = ref(0)
const mobileMenuOpen = ref(false)
const isMobile = ref(false)

const primaryItems = [
  { path: '/dashboard', label: '舆情总览' },
  { path: '/monitoring', label: '舆情监测' },
  { path: '/events', label: '事件管理' },
  { path: '/reports', label: '报告中心' },
]

const quickItems = [
  { key: 'alerts', label: '风险预警', icon: 'bell', path: '/events?risk=高' },
  { key: 'reviews', label: '待复核任务', icon: 'clipboard-check', path: '/monitoring?reviewStatus=待复核&sortBy=risk' },
  { key: 'assistant', label: '智能助手', icon: 'bot' },
  { key: 'data', label: '数据管理', icon: 'database', path: '/data' },
  { key: 'settings', label: '系统设置', icon: 'settings', path: '/settings' },
]

const activePrimary = computed(() => {
  if (route.path.startsWith('/monitoring')) return '/monitoring'
  if (route.path.startsWith('/events')) return '/events'
  if (route.path.startsWith('/reports')) return '/reports'
  return route.path === '/dashboard' ? '/dashboard' : ''
})

function checkMobile() {
  isMobile.value = window.innerWidth < 768
  if (!isMobile.value) mobileMenuOpen.value = false
}

onMounted(() => {
  checkMobile()
  window.addEventListener('resize', checkMobile)
})

onUnmounted(() => window.removeEventListener('resize', checkMobile))

function navigate(path: string) {
  router.push(path)
  mobileMenuOpen.value = false
}

function openQuick(item: typeof quickItems[number]) {
  if (item.key === 'assistant') {
    window.dispatchEvent(new CustomEvent('yuqing:open-assistant'))
  } else if (item.path) {
    navigate(item.path)
  }
  mobileMenuOpen.value = false
}

function isQuickActive(item: typeof quickItems[number]) {
  if (item.key === 'data') return route.path.startsWith('/data')
  if (item.key === 'settings') return route.path.startsWith('/settings')
  if (item.key === 'alerts') return route.path.startsWith('/events') && route.query.risk === '高'
  if (item.key === 'reviews') return route.path.startsWith('/monitoring') && route.query.reviewStatus === '待复核'
  return false
}

async function logout() {
  try {
    await authApi.logout()
  } catch {
    // 本地退出不依赖后端响应
  } finally {
    localStorage.removeItem('yuqing_token')
    localStorage.removeItem('yuqing_nickname')
    localStorage.removeItem('yuqing_role')
    localStorage.removeItem('yuqing_permissions')
    router.replace('/login')
  }
}
</script>

<template>
  <div class="shell h-screen overflow-hidden">
    <header class="topbar">
      <button class="mobile-menu-button xl:hidden" aria-label="打开导航" @click="mobileMenuOpen = true">
        <AppIcon name="menu" :size="21" />
      </button>

      <button class="brand" type="button" @click="navigate('/dashboard')">
        <span class="brand-mark"><AppIcon name="shield" :size="21" /></span>
        <span class="brand-copy">
          <strong>校园安全舆情平台</strong>
          <small>智能研判 · 风险预警</small>
        </span>
      </button>

      <nav class="primary-nav hidden xl:flex" aria-label="主要导航">
        <button
          v-for="item in primaryItems"
          :key="item.path"
          type="button"
          :class="['primary-nav-item', activePrimary === item.path ? 'primary-nav-item-active' : '']"
          @click="navigate(item.path)"
        >{{ item.label }}</button>
      </nav>

      <div class="topbar-actions">
        <span class="topbar-date hidden lg:flex">
          <AppIcon name="calendar" :size="15" />
          {{ new Date().toLocaleDateString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit' }) }}
        </span>
        <button class="topbar-icon" title="刷新当前页面" aria-label="刷新当前页面" @click="refreshKey += 1">
          <AppIcon name="refresh" :size="17" />
        </button>
        <button class="account-button" title="进入个人中心" @click="navigate('/profile')">
          <span class="account-avatar">{{ currentNickname.slice(0, 1) }}</span>
          <span class="hidden sm:block account-copy"><strong>{{ currentNickname }}</strong><small>{{ currentRole === 'ADMIN' ? '管理员' : currentRole }}</small></span>
        </button>
        <button class="topbar-icon hover-danger" title="退出登录" aria-label="退出登录" @click="logout">
          <AppIcon name="logout" :size="17" />
        </button>
      </div>
    </header>

    <div class="workspace">
      <aside class="utility-rail hidden md:flex" aria-label="快捷工具">
        <div class="utility-rail-label">快捷</div>
        <button
          v-for="item in quickItems"
          :key="item.key"
          type="button"
          :class="['utility-button', isQuickActive(item) ? 'utility-button-active' : '']"
          :title="item.label"
          :aria-label="item.label"
          @click="openQuick(item)"
        >
          <AppIcon :name="item.icon" :size="24" />
          <span class="utility-tooltip">{{ item.label }}</span>
        </button>
        <span class="rail-spacer"></span>
        <span class="service-state" title="系统运行正常"><i></i></span>
      </aside>

      <main class="app-main flex-1 overflow-auto">
        <router-view v-slot="{ Component }">
          <Transition name="page" mode="out-in">
            <component :is="Component" :key="`${route.fullPath}-${refreshKey}`" />
          </Transition>
        </router-view>
      </main>
    </div>

    <div v-if="mobileMenuOpen" class="mobile-overlay xl:hidden" @click.self="mobileMenuOpen = false">
      <aside class="mobile-drawer">
        <div class="mobile-drawer-header">
          <span class="brand-mark"><AppIcon name="shield" :size="20" /></span>
          <div><strong>校园安全舆情平台</strong><small>智能研判 · 风险预警</small></div>
          <button aria-label="关闭导航" @click="mobileMenuOpen = false"><AppIcon name="x" :size="20" /></button>
        </div>
        <div class="mobile-section-title">主要功能</div>
        <button v-for="item in primaryItems" :key="item.path" :class="['mobile-nav-item', activePrimary === item.path ? 'mobile-nav-item-active' : '']" @click="navigate(item.path)">{{ item.label }}</button>
        <div class="mobile-section-title">快捷工具</div>
        <button v-for="item in quickItems" :key="item.key" class="mobile-nav-item flex items-center gap-3" @click="openQuick(item)"><AppIcon :name="item.icon" :size="18" />{{ item.label }}</button>
      </aside>
    </div>

    <ToastContainer />
    <BackToTop />
    <FloatingAssistant />
  </div>
</template>

<style scoped>
.shell { display: flex; flex-direction: column; color: #263247; background: #f4f6fa; }
.topbar { position: relative; z-index: 40; display: flex; height: 72px; flex: 0 0 72px; align-items: stretch; border-bottom: 1px solid #e5eaf1; background: rgba(255,255,255,.97); box-shadow: 0 1px 8px rgba(15,23,42,.025); }
.brand { display: flex; width: 268px; flex: 0 0 268px; align-items: center; padding: 0 22px; text-align: left; cursor: pointer; }
.brand-mark { display: inline-flex; width: 40px; height: 40px; flex: 0 0 40px; align-items: center; justify-content: center; color: #fff; border-radius: 11px; background: #244a9b; box-shadow: 0 6px 15px rgba(36,74,155,.18); }
.brand-copy { display: flex; min-width: 0; margin-left: 13px; flex-direction: column; }
.brand-copy strong { color: #172238; font-size: 16px; line-height: 22px; font-weight: 700; white-space: nowrap; }
.brand-copy small { margin-top: 1px; color: #8b96a8; font-size: 10.5px; line-height: 16px; letter-spacing: .04em; white-space: nowrap; }
.primary-nav { position: absolute; top: 0; bottom: 0; left: 50%; display: none; width: min(680px, 46vw); grid-template-columns: repeat(4, minmax(0, 1fr)); column-gap: 12px; transform: translateX(-50%); }
.primary-nav-item { position: relative; display: flex; min-width: 0; align-items: center; justify-content: center; padding: 0 18px; color: #536074; font-size: 16px; line-height: 24px; font-weight: 500; letter-spacing: .015em; cursor: pointer; transition: color .18s ease, background .18s ease; }
.primary-nav-item:hover { color: #244a9b; background: #fafbfe; }
.primary-nav-item::after { position: absolute; right: 24px; bottom: 0; left: 24px; height: 3px; border-radius: 3px 3px 0 0; background: #315fd0; content: ''; opacity: 0; transform: scaleX(.4); transition: .2s ease; }
.primary-nav-item-active { color: #244a9b; font-weight: 650; }
.primary-nav-item-active::after { opacity: 1; transform: scaleX(1); }
.topbar-actions { display: flex; min-width: 0; margin-left: auto; align-items: center; gap: 6px; padding: 0 18px; }
.topbar-date { align-items: center; gap: 7px; margin-right: 8px; color: #748096; font-size: 12px; }
.topbar-icon { display: inline-flex; width: 36px; height: 36px; align-items: center; justify-content: center; color: #718097; border-radius: 9px; cursor: pointer; transition: .16s ease; }
.topbar-icon:hover { color: #244a9b; background: #f2f5fa; }
.hover-danger:hover { color: #dc4558; background: #fff2f4; }
.account-button { display: flex; min-height: 42px; align-items: center; gap: 9px; margin: 0 2px; padding: 4px 8px 4px 5px; border-radius: 10px; cursor: pointer; }
.account-button:hover { background: #f5f7fb; }
.account-avatar { display: inline-flex; width: 32px; height: 32px; align-items: center; justify-content: center; color: #fff; border-radius: 10px; background: #315fd0; font-size: 12px; font-weight: 700; }
.account-copy { text-align: left; }
.account-copy strong,.account-copy small { display: block; white-space: nowrap; }
.account-copy strong { color: #3b4658; font-size: 12px; line-height: 17px; }
.account-copy small { color: #9aa4b3; font-size: 10px; line-height: 14px; }
.workspace { display: flex; min-height: 0; flex: 1; }
.utility-rail { position: relative; z-index: 20; width: 88px; flex: 0 0 88px; align-items: center; flex-direction: column; padding: 18px 0 18px; border-right: 1px solid #e4e9f0; background: #fff; box-shadow: 3px 0 14px rgba(15,23,42,.02); }
.utility-rail-label { margin: 1px 0 14px; color: #98a3b4; font-size: 11px; line-height: 18px; font-weight: 650; letter-spacing: .12em; }
.utility-button { position: relative; display: inline-flex; width: 54px; height: 54px; margin-bottom: 13px; align-items: center; justify-content: center; color: #748197; border-radius: 14px; cursor: pointer; transition: .18s ease; }
.utility-button:hover { color: #244a9b; background: #f1f5fb; }
.utility-button-active { color: #fff; background: #315fd0; box-shadow: 0 7px 16px rgba(49,95,208,.2); }
.utility-tooltip { position: absolute; z-index: 100; left: 64px; padding: 7px 10px; color: #fff; border-radius: 7px; background: #172238; font-size: 12px; white-space: nowrap; opacity: 0; pointer-events: none; transform: translateX(-4px); transition: .16s ease; }
.utility-button:hover .utility-tooltip { opacity: 1; transform: translateX(0); }
.rail-spacer { flex: 1; }
.service-state { display: flex; width: 38px; height: 38px; align-items: center; justify-content: center; border-radius: 11px; background: #f0faf6; }
.service-state i { width: 8px; height: 8px; border-radius: 999px; background: #13a873; box-shadow: 0 0 0 5px rgba(19,168,115,.11); }
.app-main { padding: 26px 30px 34px; background: radial-gradient(circle at 90% 0%,rgba(49,95,208,.035),transparent 24rem),#f4f6fa; }
.mobile-menu-button { width: 48px; flex: 0 0 48px; align-items: center; justify-content: center; color: #536074; }
.mobile-overlay { position: fixed; z-index: 90; inset: 0; background: rgba(15,23,42,.38); }
.mobile-drawer { width: min(310px,86vw); height: 100%; padding: 16px; background: #fff; box-shadow: 16px 0 40px rgba(15,23,42,.18); }
.mobile-drawer-header { display: flex; align-items: center; gap: 11px; padding-bottom: 16px; border-bottom: 1px solid #e8ecf2; }
.mobile-drawer-header div { min-width: 0; flex: 1; }
.mobile-drawer-header strong,.mobile-drawer-header small { display: block; }
.mobile-drawer-header strong { color: #172238; font-size: 15px; }
.mobile-drawer-header small { margin-top: 2px; color: #8c97a8; font-size: 10px; }
.mobile-section-title { padding: 19px 10px 7px; color: #9da7b6; font-size: 10px; font-weight: 650; letter-spacing: .1em; }
.mobile-nav-item { display: flex; width: 100%; min-height: 44px; align-items: center; padding: 0 12px; color: #596679; border-radius: 9px; font-size: 14px; text-align: left; }
.mobile-nav-item-active { color: #244a9b; background: #edf3ff; font-weight: 650; }
.page-enter-active,.page-leave-active { transition: opacity .18s ease,transform .18s ease; }
.page-enter-from { opacity: 0; transform: translateY(6px); }
.page-leave-to { opacity: 0; transform: translateY(-5px); }
@media (max-width: 1320px) { .primary-nav { width: min(600px, 44vw); column-gap: 6px; } .primary-nav-item { padding: 0 12px; font-size: 15px; } }
@media (max-width: 1120px) { .primary-nav { position: static; width: min(500px, 43vw); margin-left: 12px; column-gap: 2px; transform: none; } .primary-nav-item { padding: 0 7px; font-size: 14px; } }
@media (max-width: 1050px) { .brand { width: 238px; flex-basis: 238px; padding-left: 16px; } .brand-copy strong { font-size: 15px; } }
@media (max-width: 767px) { .topbar { height: 64px; flex-basis: 64px; } .brand { width: auto; flex: 1; padding: 0 6px; } .brand-mark { width: 35px; height: 35px; flex-basis: 35px; } .brand-copy { margin-left: 10px; } .brand-copy strong { font-size: 14px; } .brand-copy small { display: none; } .topbar-actions { padding: 0 8px 0 0; } .app-main { padding: 16px; } }
@media (min-width: 1280px) { .primary-nav { display: grid; } }
</style>
