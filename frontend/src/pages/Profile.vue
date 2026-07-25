<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import AppIcon from '@/components/AppIcon.vue'
import LoadingSpinner from '@/components/LoadingSpinner.vue'
import { auditApi, authApi } from '@/utils/api'
import { toast } from '@/utils/toast'

const router = useRouter()
const loading = ref(true)
const profile = ref<any>({ permissions: [] })
const activities = ref<any[]>([])
const activityTotal = ref(0)
const loadError = ref('')
const currentPassword = ref('')
const newPassword = ref('')
const confirmPassword = ref('')
const passwordSaving = ref(false)

const permissionNames: Record<string, string> = {
  VIEW_DATA: '查看舆情数据',
  REVIEW_POST: '人工复核帖子',
  MANAGE_EVENT: '处置安全事件',
  MANAGE_SETTINGS: '维护系统设置',
  MANAGE_DATA: '管理与重新分析数据',
  VIEW_AUDIT: '查看操作审计',
}

const remainingText = computed(() => {
  const seconds = Number(profile.value.remainingSeconds || 0)
  if (seconds <= 0) return '即将失效'
  const hours = Math.floor(seconds / 3600)
  const minutes = Math.floor(seconds % 3600 / 60)
  return `${hours}小时${minutes}分钟`
})

function unwrap(value: any) {
  if (value && typeof value === 'object' && value.data !== undefined && (value.code !== undefined || value.success !== undefined)) return value.data
  return value
}

function formatTime(value: unknown) {
  if (!value) return '-'
  const date = new Date(String(value))
  return Number.isNaN(date.getTime()) ? String(value) : date.toLocaleString('zh-CN')
}

async function loadProfile() {
  loading.value = true
  loadError.value = ''
  try {
    const [profileResult, activityResult]: any[] = await Promise.all([
      authApi.profile(),
      auditApi.mine({ page: 1, size: 8 }),
    ])
    profile.value = unwrap(profileResult) || {}
    const activity = unwrap(activityResult) || {}
    activities.value = Array.isArray(activity.data) ? activity.data : []
    activityTotal.value = Number(activity.total || 0)
  } catch {
    loadError.value = '个人信息加载失败，请检查服务连接后重试'
  } finally {
    loading.value = false
  }
}

async function logout() {
  try {
    await authApi.logout()
  } finally {
    localStorage.removeItem('yuqing_token')
    localStorage.removeItem('yuqing_nickname')
    localStorage.removeItem('yuqing_role')
    localStorage.removeItem('yuqing_permissions')
    router.replace('/login')
  }
}

async function changePassword() {
  if (!currentPassword.value || newPassword.value.length < 8) {
    toast.error('请填写当前密码，新密码至少需要 8 个字符')
    return
  }
  if (newPassword.value !== confirmPassword.value) {
    toast.error('两次输入的新密码不一致')
    return
  }
  passwordSaving.value = true
  try {
    await authApi.changePassword(currentPassword.value, newPassword.value)
    currentPassword.value = ''
    newPassword.value = ''
    confirmPassword.value = ''
    toast.success('密码已修改，其他会话已退出')
  } catch (error: any) {
    toast.error(error?.response?.data?.message || '密码修改失败')
  } finally {
    passwordSaving.value = false
  }
}

onMounted(loadProfile)
</script>

<template>
  <div class="page large-detail-page">
    <LoadingSpinner v-if="loading" />

    <div v-if="loadError" class="flex items-center justify-between gap-4 border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700" role="alert">
      <span class="flex items-center gap-2"><AppIcon name="alert-triangle" :size="17" />{{ loadError }}</span>
      <button class="font-medium hover:text-rose-900 cursor-pointer" @click="loadProfile">重新加载</button>
    </div>

    <div class="flex items-center justify-between gap-4 mb-5">
      <div>
        <h2 class="text-xl font-semibold text-slate-900">个人中心</h2>
        <p class="text-sm text-slate-500 mt-1">查看当前账号、权限、会话状态和个人操作记录</p>
      </div>
      <button class="btn btn-ghost" @click="router.push('/dashboard')">
        <AppIcon name="arrow-left" :size="16" /> 返回首页
      </button>
    </div>

    <section class="profile-hero">
      <div class="profile-avatar">{{ String(profile.nickname || '管').slice(0, 1) }}</div>
      <div class="min-w-0">
        <div class="flex flex-wrap items-center gap-3">
          <h3 class="text-2xl font-bold text-white">{{ profile.nickname || '管理员' }}</h3>
          <span class="px-2.5 py-1 text-xs border border-cyan-300/30 bg-cyan-300/10 text-cyan-200">
            {{ profile.roleLabel || '系统管理员' }}
          </span>
        </div>
        <p class="text-sm text-slate-300 mt-2">{{ profile.accountType || '本地管理账号' }} · 校园安全舆情分析平台</p>
      </div>
      <button class="ml-auto btn border-white/20 text-white hover:bg-white/10" @click="logout">
        <AppIcon name="logout" :size="16" /> 退出登录
      </button>
    </section>

    <div class="grid grid-cols-1 xl:grid-cols-3 gap-5 mt-5">
      <section class="card card-pad xl:col-span-2">
        <h3 class="section-title mb-4">账号权限</h3>
        <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
          <div v-for="permission in profile.permissions || []" :key="permission" class="permission-item">
            <span class="permission-check"><AppIcon name="check" :size="15" /></span>
            <div>
              <div class="text-sm font-medium text-slate-800">{{ permissionNames[permission] || permission }}</div>
              <div class="text-xs text-slate-400 mt-0.5">权限代码：{{ permission }}</div>
            </div>
          </div>
        </div>
      </section>

      <section class="card card-pad">
        <h3 class="section-title mb-4">会话安全</h3>
        <dl class="space-y-3 text-sm">
          <div class="profile-field"><dt>当前状态</dt><dd class="text-emerald-600">登录有效</dd></div>
          <div class="profile-field"><dt>登录时间</dt><dd>{{ formatTime(profile.createdAt) }}</dd></div>
          <div class="profile-field"><dt>最近活动</dt><dd>{{ formatTime(profile.lastActiveAt) }}</dd></div>
          <div class="profile-field"><dt>失效时间</dt><dd>{{ formatTime(profile.expiresAt) }}</dd></div>
          <div class="profile-field"><dt>剩余时间</dt><dd>{{ remainingText }}</dd></div>
        </dl>
        <div class="mt-4 p-3 border border-amber-100 bg-amber-50 text-xs text-amber-800 leading-relaxed">
          当前会话默认有效 {{ profile.sessionHours || 24 }} 小时。公共设备使用后请主动退出登录。
        </div>
      </section>
    </div>

    <section class="card card-pad mt-5">
      <h3 class="section-title">修改登录密码</h3>
      <p class="text-xs text-slate-500 mt-1">新密码至少 8 个字符；修改成功后会使其他设备上的会话失效。</p>
      <div class="grid grid-cols-1 md:grid-cols-3 gap-4 mt-4">
        <input v-model="currentPassword" type="password" autocomplete="current-password" class="input w-full" placeholder="当前密码" />
        <input v-model="newPassword" type="password" autocomplete="new-password" class="input w-full" placeholder="新密码（至少8位）" />
        <input v-model="confirmPassword" type="password" autocomplete="new-password" class="input w-full" placeholder="再次输入新密码" @keyup.enter="changePassword" />
      </div>
      <div class="flex justify-end mt-4">
        <button class="btn btn-primary" type="button" :disabled="passwordSaving" @click="changePassword">
          {{ passwordSaving ? '修改中...' : '确认修改密码' }}
        </button>
      </div>
    </section>

    <section class="card card-pad mt-5">
      <div class="flex items-center justify-between gap-3 mb-4">
        <div>
          <h3 class="section-title">最近操作</h3>
          <p class="text-xs text-slate-500 mt-1">只显示当前账号产生的关键操作记录</p>
        </div>
        <span class="badge badge-neutral">累计 {{ activityTotal }} 条</span>
      </div>
      <div class="overflow-x-auto border border-slate-200">
        <table class="table w-full">
          <thead><tr><th>时间</th><th>操作</th><th>业务对象</th><th>结果</th></tr></thead>
          <tbody>
            <tr v-for="item in activities" :key="item.id">
              <td class="whitespace-nowrap text-slate-500">{{ formatTime(item.createdAt) }}</td>
              <td class="font-medium text-slate-800">{{ item.actionName }}</td>
              <td class="text-slate-600">{{ item.targetType }}<span v-if="item.targetId"> · {{ item.targetId }}</span></td>
              <td><span :class="['badge', item.status === '成功' ? 'badge-success' : 'badge-high']">{{ item.status }}</span></td>
            </tr>
            <tr v-if="!activities.length"><td colspan="4" class="py-7 text-center text-slate-400">当前账号暂无关键操作记录</td></tr>
          </tbody>
        </table>
      </div>
    </section>
  </div>
</template>

<style scoped>
.profile-hero { display:flex; align-items:center; gap:18px; padding:28px 32px; background:linear-gradient(120deg,#172554,#312e81 58%,#155e75); box-shadow:0 12px 28px rgba(30,41,59,.14); }
.profile-avatar { display:flex; align-items:center; justify-content:center; width:64px; height:64px; flex:0 0 auto; border:1px solid rgba(165,243,252,.35); background:rgba(34,211,238,.12); color:#cffafe; font-size:25px; font-weight:700; }
.permission-item { display:flex; align-items:center; gap:12px; padding:14px; border:1px solid #e2e8f0; background:#f8fafc; }
.permission-check { display:inline-flex; align-items:center; justify-content:center; width:30px; height:30px; flex:0 0 auto; background:#ecfdf5; color:#059669; }
.profile-field { display:flex; justify-content:space-between; gap:16px; padding-bottom:10px; border-bottom:1px solid #f1f5f9; }
.profile-field dt { color:#64748b; }.profile-field dd { margin:0; color:#334155; text-align:right; }
@media (max-width:640px) { .profile-hero { align-items:flex-start; padding:22px; flex-wrap:wrap; }.profile-hero button { margin-left:82px; } }
</style>
