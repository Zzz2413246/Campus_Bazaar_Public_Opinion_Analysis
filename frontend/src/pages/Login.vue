<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppIcon from '@/components/AppIcon.vue'
import { authApi } from '@/utils/api'

const route = useRoute()
const router = useRouter()
const nickname = ref('管理员')
const password = ref('')
const showPassword = ref(false)
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
    localStorage.setItem('yuqing_role', result.role || 'ADMIN')
    localStorage.setItem('yuqing_permissions', JSON.stringify(result.permissions || []))
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/dashboard'
    router.replace(redirect)
  } catch (error: any) {
    errorMessage.value = error?.response?.status === 401
      ? '昵称或密码错误'
      : error?.response?.status === 429
        ? (error?.response?.data?.message || '登录失败次数过多，请稍后重试')
        : '暂时无法登录，请检查后端服务'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <main class="login-page min-h-screen flex items-center justify-center px-5 py-8">
    <div class="login-shell w-full max-w-[1080px] min-h-[640px] grid lg:grid-cols-[1.08fr_0.92fr] bg-white border border-white/70 shadow-2xl shadow-slate-900/15 overflow-hidden">
      <section class="brand-panel relative overflow-hidden text-white px-8 py-9 sm:px-12 sm:py-11 lg:px-14 lg:py-12 flex flex-col">
        <div class="brand-grid" aria-hidden="true"></div>
        <div class="relative z-10 flex items-center gap-4">
          <div class="relative w-14 h-14 border border-cyan-300/30 bg-cyan-300/10 flex items-center justify-center text-cyan-300 shadow-lg shadow-cyan-950/20">
            <span class="absolute inset-1.5 border border-cyan-300/15"></span>
            <AppIcon name="shield" :size="30" />
            <span class="absolute right-1.5 bottom-1.5 w-2 h-2 rounded-full bg-emerald-400 ring-2 ring-slate-950"></span>
          </div>
          <div>
            <h1 class="text-[22px] sm:text-[24px] font-bold tracking-[0.08em]">校园安全舆情</h1>
            <p class="text-sm text-slate-300 mt-1 tracking-wide">智能研判与预警平台</p>
          </div>
        </div>

        <div class="brand-content relative z-10 my-auto py-12 lg:py-0">
          <span class="brand-kicker inline-flex items-center gap-2 px-3 py-1.5 text-xs tracking-[0.16em] text-cyan-200 border border-cyan-300/20 bg-cyan-300/5">
            <span class="w-1.5 h-1.5 rounded-full bg-emerald-400 animate-pulse"></span>
            PUBLIC OPINION INTELLIGENCE
          </span>
          <h2 class="mt-7 text-[32px] sm:text-[38px] leading-[1.28] font-bold tracking-wide">
            让校园舆情<br />更早被看见，更快被响应
          </h2>
          <p class="brand-description mt-5 max-w-[470px] text-[15px] leading-7 text-slate-300">
            汇聚校园公开信息，辅助完成风险识别、趋势研判与事件处置，为校园安全管理提供清晰可靠的数据支持。
          </p>

          <div class="feature-list mt-9 grid sm:grid-cols-3 gap-3">
            <div class="feature-item"><AppIcon name="radar" :size="19" /><span>实时监测</span></div>
            <div class="feature-item"><AppIcon name="activity" :size="19" /><span>智能研判</span></div>
            <div class="feature-item"><AppIcon name="siren" :size="19" /><span>风险预警</span></div>
          </div>
        </div>

        <p class="brand-footer relative z-10 text-xs text-slate-500 tracking-wide">校园公开舆情数据仅供管理研判使用</p>
      </section>

      <section class="form-panel flex items-center px-7 py-10 sm:px-12 lg:px-14">
        <form class="w-full max-w-[390px] mx-auto" @submit.prevent="login">
          <div class="mb-9">
            <div class="text-xs font-semibold tracking-[0.18em] text-indigo-600 mb-3">WELCOME BACK</div>
            <h2 class="text-[28px] font-bold text-slate-900 tracking-wide">登录管理平台</h2>
            <p class="text-[15px] text-slate-500 mt-2">使用管理员账号进入舆情分析系统</p>
          </div>

          <label class="block mb-5">
            <span class="block text-sm font-medium text-slate-700 mb-2.5">账号昵称</span>
            <span class="input-wrap">
              <AppIcon name="user" :size="19" />
              <input v-model="nickname" autocomplete="username" autofocus placeholder="请输入账号昵称" aria-label="账号昵称" />
            </span>
          </label>

          <label class="block mb-5">
            <span class="block text-sm font-medium text-slate-700 mb-2.5">登录密码</span>
            <span class="input-wrap">
              <AppIcon name="lock" :size="19" />
              <input v-model="password" :type="showPassword ? 'text' : 'password'" autocomplete="current-password" placeholder="请输入登录密码" aria-label="登录密码" />
              <button type="button" class="password-toggle" :aria-label="showPassword ? '隐藏密码' : '显示密码'" :title="showPassword ? '隐藏密码' : '显示密码'" @click="showPassword = !showPassword">
                <AppIcon :name="showPassword ? 'eye-off' : 'eye'" :size="18" />
              </button>
            </span>
          </label>

          <div v-if="errorMessage" role="alert" class="mb-5 px-3.5 py-3 flex items-start gap-2.5 text-sm text-rose-700 bg-rose-50 border border-rose-200">
            <AppIcon name="alert-triangle" :size="17" class="mt-0.5 flex-shrink-0" />
            {{ errorMessage }}
          </div>

          <button type="submit" class="login-button w-full h-12 flex items-center justify-center gap-2.5 bg-indigo-600 text-white text-[15px] font-semibold hover:bg-indigo-700 disabled:opacity-60 disabled:cursor-not-allowed transition-all cursor-pointer" :disabled="loading">
            <span v-if="loading" class="w-4 h-4 rounded-full border-2 border-white/40 border-t-white animate-spin"></span>
            <template v-else><span>进入平台</span><AppIcon name="arrow-right" :size="18" /></template>
            <span v-if="loading">正在验证...</span>
          </button>

          <div class="test-account mt-6 px-4 py-3.5 flex items-center gap-3 bg-slate-50 border border-slate-200 text-sm text-slate-600">
            <span class="w-8 h-8 flex items-center justify-center bg-indigo-50 text-indigo-600 flex-shrink-0"><AppIcon name="user" :size="16" /></span>
            <div class="min-w-0">
              <div class="text-xs text-slate-400 mb-0.5">演示账号</div>
              <div><span class="font-medium text-slate-800">管理员</span><span class="mx-2 text-slate-300">/</span><span class="font-medium text-slate-800">123456</span></div>
            </div>
          </div>

          <p class="mt-8 text-center text-xs text-slate-400">登录即表示您同意遵守平台数据安全规范</p>
        </form>
      </section>
    </div>
  </main>
</template>

<style scoped>
.login-page {
  background:
    radial-gradient(circle at 8% 12%, rgba(79, 70, 229, 0.18), transparent 31%),
    radial-gradient(circle at 92% 88%, rgba(6, 182, 212, 0.14), transparent 29%),
    linear-gradient(135deg, #eef2ff 0%, #f8fafc 48%, #ecfeff 100%);
}

.login-shell { animation: login-in 280ms ease-out; }
.brand-panel {
  padding: 48px 56px;
  background: linear-gradient(145deg, #0f172a 0%, #1e1b4b 48%, #164e63 130%);
}
.form-panel { padding: 56px; }
.brand-panel::after {
  content: ''; position: absolute; width: 340px; height: 340px; right: -150px; bottom: -140px;
  border-radius: 50%; background: rgba(34, 211, 238, 0.08); border: 1px solid rgba(103, 232, 249, 0.12);
}
.brand-grid {
  position: absolute; inset: 0; opacity: 0.12;
  background-image: linear-gradient(rgba(255,255,255,.16) 1px, transparent 1px), linear-gradient(90deg, rgba(255,255,255,.16) 1px, transparent 1px);
  background-size: 44px 44px; mask-image: linear-gradient(to bottom right, black, transparent 76%);
}
.feature-item {
  display: flex; align-items: center; gap: 9px; min-height: 48px; padding: 0 13px; color: #cbd5e1;
  font-size: 13px; border: 1px solid rgba(255,255,255,.1); background: rgba(255,255,255,.045);
}
.feature-item svg { color: #67e8f9; }
.input-wrap {
  display: flex; align-items: center; height: 50px; padding: 0 15px; color: #94a3b8;
  border: 1px solid #cbd5e1; background: #fff; transition: border-color 160ms ease, box-shadow 160ms ease;
}
.input-wrap:focus-within { color: #4f46e5; border-color: #6366f1; box-shadow: 0 0 0 3px rgba(99,102,241,.12); }
.input-wrap input { flex: 1; min-width: 0; height: 100%; margin-left: 11px; color: #1e293b; font-size: 15px; outline: none; background: transparent; }
.input-wrap input::placeholder { color: #94a3b8; }
.password-toggle { display: inline-flex; align-items: center; justify-content: center; width: 34px; height: 34px; margin-right: -7px; color: #94a3b8; cursor: pointer; }
.password-toggle:hover { color: #4f46e5; background: #eef2ff; }
.login-button:hover { box-shadow: 0 8px 20px rgba(79,70,229,.22); transform: translateY(-1px); }
.login-button:active { transform: translateY(0); }

@keyframes login-in { from { opacity: 0; transform: translateY(12px); } to { opacity: 1; transform: translateY(0); } }
@media (max-width: 1023px) {
  .login-shell { max-width: 620px; }
  .brand-panel { min-height: 330px; padding: 42px 48px; }
  .form-panel { padding: 48px; }
  .brand-footer { display: none; }
}
@media (max-width: 639px) {
  .login-page { align-items: flex-start; padding: 0; }
  .login-shell { min-height: 100vh; border: 0; }
  .brand-panel { min-height: 250px; padding: 28px 24px; }
  .brand-panel h2 { font-size: 26px; }
  .brand-description, .feature-list, .brand-kicker { display: none; }
  .brand-content { padding: 38px 0 12px; }
  .form-panel { padding: 36px 24px; }
}
@media (prefers-reduced-motion: reduce) { .login-shell { animation: none; } .login-button { transition: none; } }
</style>
