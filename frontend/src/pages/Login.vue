<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppIcon from '@/components/AppIcon.vue'
import { authApi } from '@/utils/api'

const route = useRoute()
const router = useRouter()
const nickname = ref('管理员')
const password = ref('')
const loading = ref(false)
const errorMessage = ref('')

async function login() {
  if (!nickname.value.trim() || !password.value) {
    errorMessage.value = '请输入昵称和密码'
    return
  }
  loading.value = true
  errorMessage.value = ''
  try {
    const result: any = await authApi.login(nickname.value.trim(), password.value)
    localStorage.setItem('yuqing_token', result.token)
    localStorage.setItem('yuqing_nickname', result.nickname)
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/dashboard'
    router.replace(redirect)
  } catch (error: any) {
    errorMessage.value = error?.response?.status === 401
      ? '昵称或密码错误'
      : '暂时无法登录，请检查后端服务'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <main class="login-page min-h-screen flex items-center justify-center px-5 py-10">
    <div class="login-card w-full max-w-[440px] bg-white border border-slate-200 shadow-2xl shadow-slate-900/10">
      <div class="px-9 pt-9 pb-7 bg-gradient-to-br from-slate-950 via-indigo-950 to-slate-900 text-white">
        <div class="flex items-center gap-4">
          <div class="relative w-13 h-13 border border-cyan-300/25 bg-cyan-300/10 flex items-center justify-center text-cyan-300">
            <AppIcon name="shield" :size="28" />
            <span class="absolute right-1.5 bottom-1.5 w-2 h-2 rounded-full bg-emerald-400 ring-2 ring-slate-950"></span>
          </div>
          <div>
            <h1 class="text-[21px] font-bold tracking-wide">校园安全舆情平台</h1>
            <p class="text-sm text-slate-300 mt-1">智能研判与预警管理端</p>
          </div>
        </div>
      </div>

      <form class="px-9 py-8" @submit.prevent="login">
        <div class="mb-7">
          <h2 class="text-xl font-semibold text-slate-900">管理员登录</h2>
          <p class="text-sm text-slate-600 mt-1.5">请输入昵称和密码进入系统</p>
        </div>

        <label class="block mb-5">
          <span class="block text-sm font-medium text-slate-700 mb-2">昵称</span>
          <input
            v-model="nickname"
            class="input w-full"
            autocomplete="username"
            placeholder="请输入昵称"
          />
        </label>

        <label class="block mb-5">
          <span class="block text-sm font-medium text-slate-700 mb-2">密码</span>
          <input
            v-model="password"
            type="password"
            class="input w-full"
            autocomplete="current-password"
            placeholder="请输入密码"
          />
        </label>

        <div v-if="errorMessage" class="mb-4 px-3 py-2.5 text-sm text-rose-700 bg-rose-50 border border-rose-200">
          {{ errorMessage }}
        </div>

        <button
          type="submit"
          class="w-full h-11 flex items-center justify-center gap-2 bg-indigo-600 text-white text-[15px] font-medium hover:bg-indigo-700 disabled:opacity-60 transition-colors cursor-pointer"
          :disabled="loading"
        >
          <span v-if="loading" class="w-4 h-4 rounded-full border-2 border-white/40 border-t-white animate-spin"></span>
          <AppIcon v-else name="arrow-right" :size="17" />
          {{ loading ? '登录中...' : '登录' }}
        </button>

        <div class="mt-6 px-4 py-3 bg-slate-50 border border-slate-200 text-sm text-slate-600">
          测试账号：<span class="font-medium text-slate-800">管理员</span>
          <span class="mx-2 text-slate-300">/</span>
          密码：<span class="font-medium text-slate-800">123456</span>
        </div>
      </form>
    </div>
  </main>
</template>

<style scoped>
.login-page {
  background:
    radial-gradient(circle at 15% 15%, rgba(79, 70, 229, 0.12), transparent 32%),
    radial-gradient(circle at 85% 85%, rgba(6, 182, 212, 0.10), transparent 30%),
    #f1f5f9;
}

.login-card {
  animation: login-in 280ms ease-out;
}

@keyframes login-in {
  from {
    opacity: 0;
    transform: translateY(12px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
</style>
