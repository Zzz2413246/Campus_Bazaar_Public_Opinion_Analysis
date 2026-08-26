<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import BaseChart from '../components/BaseChart.vue'
import AppIcon from '../components/AppIcon.vue'
import LoadingSpinner from '@/components/LoadingSpinner.vue'
import ErrorState from '@/components/ErrorState.vue'
import TrendAnalysis from '@/pages/Trends.vue'
import { toast } from '@/utils/toast'
import { trendLineOption, donutOption, sparklineOption, palette } from '../utils/chartTheme'
import { useCountUp } from '../utils/useCountUp'
import { dashboardApi } from '@/utils/api'

const loading = ref(false)
const loadError = ref('')
const router = useRouter()

const stats = ref([
  { label: '总帖子数', value: 0, change: '暂无数据', trend: 'up', icon: 'message-square', accent: 'brand', spark: [], sparkColor: '#6366f1' },
  { label: '安全事件', value: 0, change: '暂无数据', trend: 'good', icon: 'siren', accent: 'amber', spark: [], sparkColor: '#f59e0b' },
  { label: '高风险事件', value: 0, change: '暂无数据', trend: 'warn', icon: 'alert-triangle', accent: 'rose', spark: [], sparkColor: '#f43f5e' },
  { label: '整体情绪', value: 0, textValue: '暂无数据', change: '', trend: 'up', icon: 'smile', accent: 'emerald', spark: [], sparkColor: '#10b981' },
])

// 数字计数动画（仅对数值型统计）
const countPosts = useCountUp(() => stats.value[0].value as number)
const countEvents = useCountUp(() => stats.value[1].value as number)
const countHigh = useCountUp(() => stats.value[2].value as number)

const accentMap: Record<string, string> = {
  brand: 'from-brand-500 to-brand-400 shadow-brand-500/30',
  amber: 'from-amber-500 to-amber-400 shadow-amber-500/30',
  rose: 'from-rose-500 to-rose-400 shadow-rose-500/30',
  emerald: 'from-emerald-500 to-emerald-400 shadow-emerald-500/30',
}

const recentEvents = ref<any[]>([])
const alerts = ref<any[]>([])

const workbench = ref<any>({
  summary: {
    pendingReviewCount: 0,
    activeAlertCount: 0,
    dueSoonCount: 0,
    overdueCount: 0,
    dataIssueCount: 0,
  },
  pendingReviews: [],
  deadlineEvents: [],
  dailyChanges: [],
  dataStatus: {
    status: '检查中',
    latestDataAt: '',
    ageHours: -1,
    missingContentCount: 0,
    message: '正在读取数据状态',
  },
})

const trendData = ref<any>(null)
const categoryDistribution = ref<any>(null)
const trendRange = ref<'7d' | '30d' | 'custom'>('7d')
const today = new Date()
const thirtyDaysAgo = new Date(today)
thirtyDaysAgo.setDate(today.getDate() - 29)
const formatDateInput = (date: Date) => `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
const trendStartDate = ref(formatDateInput(thirtyDaysAgo))
const trendEndDate = ref(formatDateInput(today))
const trendRangeLabel = computed(() => trendRange.value === '7d' ? '近 7 天' : trendRange.value === '30d' ? '近 30 天' : `${trendStartDate.value} 至 ${trendEndDate.value}`)

const riskBadge = (r: string) => r === '高' ? 'badge-high' : r === '中' ? 'badge-medium' : 'badge-low'
const riskDot = (r: string) => r === '高' ? 'dot-high' : r === '中' ? 'dot-medium' : 'dot-low'

// 近7天舆情趋势：API 数据覆盖主题函数的默认序列
const trendOpt = computed(() => {
  const base = trendLineOption() as any
  const td = trendData.value
  if (td) {
    const s = base.series as any[]
    if (Array.isArray(td.posts)) s[0].data = td.posts
    if (Array.isArray(td.events)) s[1].data = td.events
    if (Array.isArray(td.highRisk)) s[2].data = td.highRisk
    if (Array.isArray(td.labels ?? td.days)) base.xAxis.data = td.labels ?? td.days
  } else {
    base.series.forEach((series: any) => { series.data = [] })
  }
  return base
})

// 议题分布：API 数据覆盖环形图
const donutOpt = computed(() => {
  const cd = categoryDistribution.value
  const categoryCount = Array.isArray(cd) ? cd.length : 0
  const base = donutOption(categoryCount) as any
  base.series[0].data = []
  if (Array.isArray(cd) && cd.length) {
    const sorted = [...cd].sort((a: any, b: any) => Number(b.value ?? b.count ?? 0) - Number(a.value ?? a.count ?? 0))
    const topItems = sorted.slice(0, 6)
    const otherValue = sorted.slice(6).reduce((sum: number, item: any) => sum + Number(item.value ?? item.count ?? 0), 0)
    const displayItems = otherValue > 0 ? [...topItems, { name: '其他议题', value: otherValue }] : topItems
    base.series[0].data = displayItems.map((item: any, i: number) => ({
      value: item.value ?? item.count ?? 0,
      name: item.name ?? item.category ?? '未知',
      itemStyle: { color: palette[i % palette.length] },
    }))
  }
  return base
})

const trendCoverage = computed(() => {
  const timedPosts = Number(trendData.value?.timedPosts || 0)
  const totalPosts = Number(trendData.value?.totalPosts || 0)
  return totalPosts ? `已按真实时间统计 ${timedPosts.toLocaleString()} / ${totalPosts.toLocaleString()} 条` : ''
})

function unwrap(res: any) {
  if (res && typeof res === 'object' && (res.code !== undefined || res.success !== undefined) && res.data !== undefined) return res.data
  return res
}

// 加载仪表盘数据，isManual 标记是否为手动刷新（仅在手动刷新时弹出成功提示）
function trendParams() {
  if (trendRange.value === '30d') return { days: 30 }
  if (trendRange.value === 'custom') return { startDate: trendStartDate.value, endDate: trendEndDate.value }
  return { days: 7 }
}

async function loadDashboard(isManual = false) {
  loading.value = true
  loadError.value = ''
  try {
    const res: any = await dashboardApi.get(trendParams())
    const d = unwrap(res) || {}
    // 统计卡片 · 后端返回 stats 数组，每项含 label/value/change/trend/icon/accent/spark/sparkColor/textValue
    if (Array.isArray(d.stats) && d.stats.length) {
      stats.value = d.stats.map((s: any) => ({
        label: s.label ?? '',
        value: s.value ?? 0,
        change: s.change ?? '',
        trend: s.trend ?? 'up',
        icon: s.icon ?? 'message-square',
        accent: s.accent ?? 'brand',
        spark: Array.isArray(s.spark) ? s.spark : [],
        sparkColor: s.sparkColor ?? '#6366f1',
        textValue: s.textValue,
      }))
    }
    // 最近事件
    if (Array.isArray(d.recentEvents)) {
      recentEvents.value = d.recentEvents.map((e: any) => ({
        id: e.id ?? '',
        time: e.time ?? e.createdAt ?? e.date ?? '',
        title: e.title ?? e.name ?? e.summary ?? '',
        category: e.category ?? e.type ?? '',
        risk: e.risk ?? e.riskLevel ?? '低',
        status: e.status ?? '待研判',
      }))
    }
    // 实时预警
    if (Array.isArray(d.alerts)) {
      alerts.value = d.alerts.map((a: any) => ({
        id: a.id ?? '',
        title: a.title ?? a.name ?? '',
        meta: a.meta ?? a.desc
          ?? [a.postCount != null ? `${a.postCount}条讨论` : '', a.date ?? ''].filter(Boolean).join(' · '),
        risk: a.risk ?? a.riskLevel ?? '中',
        date: a.date ?? '',
        postCount: a.postCount ?? 0,
        triggerSummary: a.triggerSummary ?? '',
        matchedRuleCount: Number(a.matchedRuleCount ?? 0),
      }))
    }
    if (d.workbench && typeof d.workbench === 'object') {
      workbench.value = {
        summary: { ...workbench.value.summary, ...(d.workbench.summary || {}) },
        pendingReviews: Array.isArray(d.workbench.pendingReviews) ? d.workbench.pendingReviews : [],
        deadlineEvents: Array.isArray(d.workbench.deadlineEvents) ? d.workbench.deadlineEvents : [],
        dailyChanges: Array.isArray(d.workbench.dailyChanges) ? d.workbench.dailyChanges : [],
        dataStatus: { ...workbench.value.dataStatus, ...(d.workbench.dataStatus || {}) },
      }
    }
    // 图表数据
    if (d.trendData) trendData.value = d.trendData
    if (d.categoryDistribution) categoryDistribution.value = d.categoryDistribution
    // 仅手动刷新时提示成功
    if (isManual) {
      toast.success('数据已更新')
    }
  } catch (err) {
    console.warn('Dashboard 数据加载失败', err)
    loadError.value = '总览数据暂时无法获取，请检查后端服务后重试。'
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadDashboard(false)
})

function openAlert(id: string) {
  if (id) router.push(`/events/${id}`)
}

function selectTrendRange(range: '7d' | '30d') {
  trendRange.value = range
  loadDashboard()
}

function applyCustomTrendRange() {
  if (!trendStartDate.value || !trendEndDate.value) return toast.error('请选择完整的起止日期')
  if (trendStartDate.value > trendEndDate.value) return toast.error('开始日期不能晚于结束日期')
  trendRange.value = 'custom'
  loadDashboard()
}

function openPendingReviews() {
  router.push({ path: '/monitoring', query: { reviewStatus: '待复核', sortBy: 'risk' } })
}

function openEvent(id?: string) {
  if (id) router.push(`/events/${id}`)
  else router.push('/events')
}

function formatDateTime(value: unknown) {
  if (!value) return '暂无记录'
  const date = new Date(String(value))
  if (Number.isNaN(date.getTime())) return String(value)
  return date.toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}

function changeText(item: any) {
  const difference = Number(item.difference || 0)
  if (difference > 0) return `较昨日 +${difference}${item.unit || ''}`
  if (difference < 0) return `较昨日 ${difference}${item.unit || ''}`
  return '较昨日持平'
}

function changeClass(item: any) {
  return item.direction === 'up' ? 'text-rose-600 bg-rose-50'
    : item.direction === 'down' ? 'text-emerald-600 bg-emerald-50'
      : 'text-slate-600 bg-slate-100'
}
</script>

<template>
  <div class="page">
    <!-- 加载状态 -->
    <LoadingSpinner v-if="loading" />
    <ErrorState
      v-else-if="loadError"
      title="舆情总览加载失败"
      :message="loadError"
      @retry="loadDashboard(false)"
    />
    <!-- 统计卡片 -->
    <div class="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-5">
      <div
        v-for="(s, idx) in stats"
        :key="s.label"
        class="stat-card card-hover"
      >
        <!-- 图标徽标 -->
        <div :class="['absolute right-5 top-5 w-10 h-10 rounded-xl bg-gradient-to-br flex items-center justify-center text-white shadow-lg', accentMap[s.accent]]">
          <AppIcon :name="s.icon" :size="20" />
        </div>
        <div class="text-sm text-slate-500">{{ s.label }}</div>
        <div class="text-3xl font-bold text-slate-800 mt-1.5 tracking-tight">
          <template v-if="idx === 0">{{ countPosts.toLocaleString() }}</template>
          <template v-else-if="idx === 1">{{ countEvents }}</template>
          <template v-else-if="idx === 2">{{ countHigh }}</template>
          <template v-else>{{ s.textValue }}</template>
        </div>
        <div class="flex items-center gap-1 text-sm text-slate-400 mt-2">
          <span v-if="s.trend === 'up'" class="text-emerald-500"><AppIcon name="arrow-up" :size="14" /></span>
          <span v-else-if="s.trend === 'good'" class="text-emerald-500"><AppIcon name="arrow-down" :size="14" /></span>
          <span v-else class="text-rose-500"><AppIcon name="alert-triangle" :size="14" /></span>
          {{ s.change }}
        </div>
        <!-- 迷你 sparkline -->
        <div class="h-10 mt-3 -mx-1">
          <BaseChart :option="sparklineOption(s.spark, s.sparkColor)" height="100%" />
        </div>
      </div>
    </div>

    <!-- 今日工作台：把需要处理的事项放在图表之前 -->
    <section class="card card-pad border-t-4 border-t-brand-500">
      <div class="flex items-start justify-between gap-4 flex-wrap mb-5">
        <div>
          <h2 class="section-title !text-[18px]">今日工作台</h2>
          <p class="section-sub mt-1">先处理待复核、风险预警和临近截止事项，再查看趋势分析。</p>
        </div>
        <button
          class="inline-flex items-center gap-2 px-3 py-2 text-sm border"
          :class="workbench.dataStatus.status === '正常'
            ? 'text-emerald-700 bg-emerald-50 border-emerald-100'
            : 'text-amber-800 bg-amber-50 border-amber-200'"
          @click="router.push('/data')"
        >
          <span :class="['w-2 h-2 rounded-full', workbench.dataStatus.status === '正常' ? 'bg-emerald-500' : 'bg-amber-500']"></span>
          数据状态：{{ workbench.dataStatus.status }}
        </button>
      </div>

      <div class="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-3">
        <button class="workbench-task" @click="openPendingReviews">
          <span class="workbench-icon bg-brand-50 text-brand-600"><AppIcon name="radar" :size="20" /></span>
          <span class="min-w-0 text-left">
            <span class="block text-2xl font-bold text-slate-900">{{ workbench.summary.pendingReviewCount }}</span>
            <span class="block text-sm font-medium text-slate-700 mt-0.5">待复核帖子</span>
            <span class="block text-xs text-slate-500 mt-1">按风险优先进入队列</span>
          </span>
          <AppIcon name="arrow-right" :size="16" class="ml-auto text-slate-400" />
        </button>
        <button class="workbench-task" @click="openEvent()">
          <span class="workbench-icon bg-rose-50 text-rose-600"><AppIcon name="siren" :size="20" /></span>
          <span class="min-w-0 text-left">
            <span class="block text-2xl font-bold text-slate-900">{{ workbench.summary.activeAlertCount }}</span>
            <span class="block text-sm font-medium text-slate-700 mt-0.5">待处理预警</span>
            <span class="block text-xs text-slate-500 mt-1">中高风险且尚未关闭</span>
          </span>
          <AppIcon name="arrow-right" :size="16" class="ml-auto text-slate-400" />
        </button>
        <button class="workbench-task" @click="openEvent()">
          <span class="workbench-icon bg-amber-50 text-amber-600"><AppIcon name="clock" :size="20" /></span>
          <span class="min-w-0 text-left">
            <span class="block text-2xl font-bold text-slate-900">
              {{ Number(workbench.summary.dueSoonCount) + Number(workbench.summary.overdueCount) }}
            </span>
            <span class="block text-sm font-medium text-slate-700 mt-0.5">截止事项</span>
            <span class="block text-xs text-slate-500 mt-1">
              {{ workbench.summary.overdueCount }} 条超时 · {{ workbench.summary.dueSoonCount }} 条临近
            </span>
          </span>
          <AppIcon name="arrow-right" :size="16" class="ml-auto text-slate-400" />
        </button>
        <button class="workbench-task" @click="router.push('/data')">
          <span class="workbench-icon bg-slate-100 text-slate-600"><AppIcon name="database" :size="20" /></span>
          <span class="min-w-0 text-left">
            <span class="block text-2xl font-bold text-slate-900">{{ workbench.summary.dataIssueCount }}</span>
            <span class="block text-sm font-medium text-slate-700 mt-0.5">数据状态事项</span>
            <span class="block text-xs text-slate-500 mt-1">检查数据是否持续更新</span>
          </span>
          <AppIcon name="arrow-right" :size="16" class="ml-auto text-slate-400" />
        </button>
      </div>
    </section>

    <div class="grid grid-cols-1 xl:grid-cols-2 gap-5">
      <section class="card card-pad">
        <div class="flex items-center justify-between gap-3 mb-4">
          <div>
            <h3 class="section-title">优先复核队列</h3>
            <p class="section-sub mt-1">按最终风险标签和传播热度排序</p>
          </div>
          <button class="btn-link text-sm" @click="openPendingReviews">进入全部待复核 →</button>
        </div>
        <div v-if="workbench.pendingReviews.length" class="divide-y divide-slate-100">
          <button
            v-for="item in workbench.pendingReviews"
            :key="item.id"
            class="w-full flex items-center gap-3 py-3.5 text-left hover:bg-slate-50 transition-colors"
            @click="router.push(`/monitoring/${item.id}`)"
          >
            <span :class="['badge flex-shrink-0', riskBadge(item.risk)]">{{ item.risk }}风险</span>
            <span class="min-w-0 flex-1">
              <span class="block text-[15px] font-medium text-slate-800 truncate">{{ item.title }}</span>
              <span class="block text-xs text-slate-500 mt-1">{{ item.category }} · 热度 {{ item.heat }}</span>
            </span>
            <AppIcon name="arrow-right" :size="15" class="text-slate-400" />
          </button>
        </div>
        <div v-else class="py-8 text-center text-sm text-slate-500">当前没有待复核帖子</div>
      </section>

      <div class="space-y-5">
        <section class="card card-pad">
          <div class="flex items-center justify-between gap-3 mb-4">
            <div>
              <h3 class="section-title">今日异常变化</h3>
              <p class="section-sub mt-1">与昨日同口径数据比较</p>
            </div>
            <span class="badge badge-neutral">今日 / 昨日</span>
          </div>
          <div class="grid grid-cols-1 sm:grid-cols-3 gap-3">
            <div v-for="item in workbench.dailyChanges" :key="item.label" class="border border-slate-100 bg-slate-50 p-4">
              <div class="text-sm text-slate-600">{{ item.label }}</div>
              <div class="flex items-end gap-2 mt-2">
                <span class="text-2xl font-bold text-slate-900">{{ item.today }}</span>
                <span class="text-xs text-slate-500 mb-1">{{ item.unit }}</span>
              </div>
              <div :class="['inline-block text-xs px-2 py-1 mt-2', changeClass(item)]">{{ changeText(item) }}</div>
            </div>
          </div>
        </section>

        <section class="card card-pad">
          <div class="flex items-center justify-between gap-3 mb-4">
            <div>
              <h3 class="section-title">截止与数据状态</h3>
              <p class="section-sub mt-1">优先展示已超时和未来48小时到期事件</p>
            </div>
            <button class="btn-link text-sm" @click="router.push('/data')">数据管理 →</button>
          </div>
          <div
            class="border px-4 py-3 mb-3"
            :class="workbench.dataStatus.status === '正常'
              ? 'border-emerald-100 bg-emerald-50 text-emerald-800'
              : 'border-amber-200 bg-amber-50 text-amber-900'"
          >
            <div class="text-sm font-medium">{{ workbench.dataStatus.message }}</div>
            <div class="text-xs mt-1 opacity-80">
              最新数据：{{ formatDateTime(workbench.dataStatus.latestDataAt) }} · 正文缺失 {{ workbench.dataStatus.missingContentCount }} 条
            </div>
          </div>
          <div v-if="workbench.deadlineEvents.length" class="space-y-2">
            <button
              v-for="event in workbench.deadlineEvents"
              :key="event.id"
              class="w-full flex items-center gap-3 border border-slate-100 px-3.5 py-3 text-left hover:bg-slate-50"
              @click="openEvent(event.id)"
            >
              <span :class="['badge flex-shrink-0', event.overdue ? 'badge-high' : 'badge-warn']">
                {{ event.overdue ? '已超时' : '48小时内' }}
              </span>
              <span class="min-w-0 flex-1">
                <span class="block text-sm font-medium text-slate-800 truncate">{{ event.title }}</span>
                <span class="block text-xs text-slate-500 mt-1">{{ event.assignee }} · {{ formatDateTime(event.dueAt) }}</span>
              </span>
            </button>
          </div>
          <div v-else class="text-sm text-slate-500 py-2">暂无已超时或未来48小时到期的事件</div>
        </section>
      </div>
    </div>

    <!-- 图表区域 -->
    <div class="grid grid-cols-1 lg:grid-cols-3 gap-5">
      <div class="card card-pad lg:col-span-2">
        <div class="flex items-start justify-between gap-3 mb-4 flex-wrap">
          <div>
            <h3 class="section-title">舆情趋势</h3>
            <p class="section-sub mt-0.5">帖子总量 / 安全事件 / 高风险事件 · {{ trendRangeLabel }}</p>
            <p v-if="trendCoverage" class="text-xs text-slate-400 mt-1">{{ trendCoverage }}</p>
          </div>
          <div class="flex items-center gap-2 flex-wrap">
            <div class="seg">
              <button type="button" :class="['seg-item', trendRange === '7d' ? 'seg-item-active' : '']" @click="selectTrendRange('7d')">近7天</button>
              <button type="button" :class="['seg-item', trendRange === '30d' ? 'seg-item-active' : '']" @click="selectTrendRange('30d')">近30天</button>
              <button type="button" :class="['seg-item', trendRange === 'custom' ? 'seg-item-active' : '']" @click="trendRange = 'custom'">自定义</button>
            </div>
            <div v-if="trendRange === 'custom'" class="flex items-center gap-1.5">
              <input v-model="trendStartDate" type="date" class="input !py-1.5 text-xs" :max="trendEndDate" aria-label="趋势开始日期" />
              <span class="text-slate-400 text-xs">至</span>
              <input v-model="trendEndDate" type="date" class="input !py-1.5 text-xs" :min="trendStartDate" aria-label="趋势结束日期" />
              <button type="button" class="btn btn-primary !px-2.5 !py-1.5 text-xs" @click="applyCustomTrendRange">应用</button>
            </div>
          </div>
        </div>
        <div class="h-72">
          <BaseChart :option="trendOpt" height="100%" />
        </div>
      </div>

      <div class="card card-pad">
        <div class="mb-4">
          <h3 class="section-title">安全议题分布</h3>
          <p class="section-sub mt-0.5">各类别占比</p>
        </div>
        <div class="h-72">
          <BaseChart :option="donutOpt" height="100%" />
        </div>
      </div>
    </div>

    <!-- 下半区 -->
    <div class="grid grid-cols-1 lg:grid-cols-2 gap-5">
      <!-- 实时预警 -->
      <div class="card card-pad">
        <div class="flex items-center justify-between mb-4">
          <div class="flex items-center gap-2">
            <span class="w-2 h-2 rounded-full bg-rose-500 animate-pulse"></span>
            <h3 class="section-title">实时风险预警</h3>
          </div>
          <button class="btn-link text-sm" @click="router.push('/events')">查看全部 →</button>
        </div>
        <div class="space-y-2.5">
          <div
            v-for="(a, i) in alerts"
            :key="i"
            class="flex items-center gap-3 p-3 bg-slate-50/70 border border-slate-100 hover:bg-slate-100/70 transition-colors cursor-pointer"
            @click="openAlert(a.id)"
          >
            <span :class="a.risk === '高' ? 'dot dot-high flex-shrink-0 pulse-dot bg-rose-500' : 'dot dot-medium flex-shrink-0'"></span>
            <div class="flex-1 min-w-0">
              <div class="text-sm text-slate-800 font-medium">{{ a.title }}</div>
              <div class="text-xs text-slate-500 mt-0.5">{{ a.meta }}</div>
              <div v-if="a.triggerSummary" class="text-xs text-rose-600 mt-1">
                触发：{{ a.triggerSummary }}
                <span v-if="a.matchedRuleCount > 2">等 {{ a.matchedRuleCount }} 项</span>
              </div>
            </div>
            <span class="badge" :class="riskBadge(a.risk)">{{ a.risk }}风险</span>
          </div>
        </div>
      </div>

      <!-- 最近事件 -->
      <div class="card card-pad">
        <div class="flex items-center justify-between mb-4">
          <h3 class="section-title">最近事件速览</h3>
          <span class="btn-link text-sm">全部事件 →</span>
        </div>
        <table class="table-base min-w-[500px]">
          <thead>
            <tr>
              <th class="w-20">时间</th>
              <th class="w-40">事件摘要</th>
              <th class="w-20">类型</th>
              <th class="w-28">风险</th>
              <th class="w-24">状态</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="e in recentEvents"
              :key="e.title"
              class="hover:bg-slate-50/60 cursor-pointer transition-colors"
              @click="openEvent(e.id)"
            >
              <td class="text-slate-500 text-xs whitespace-nowrap">{{ e.time }}</td>
              <td class="text-slate-800 font-medium truncate">{{ e.title }}</td>
              <td><span class="badge badge-neutral">{{ e.category }}</span></td>
              <td>
                <span class="badge" :class="riskBadge(e.risk)">
                  <span class="dot" :class="riskDot(e.risk)"></span>{{ e.risk }}
                </span>
              </td>
              <td class="text-slate-500 text-xs">{{ e.status }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- 与总览去重后的趋势研判 -->
    <TrendAnalysis />
  </div>
</template>

<style scoped>
.workbench-task {
  display: flex;
  align-items: center;
  gap: 12px;
  min-height: 112px;
  padding: 16px;
  border: 1px solid rgb(226 232 240);
  background: rgb(248 250 252 / 0.7);
  transition: background-color 160ms ease, border-color 160ms ease, transform 160ms ease;
}

.workbench-task:hover {
  background: white;
  border-color: rgb(148 163 184);
  transform: translateY(-1px);
}

.workbench-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 42px;
  height: 42px;
  flex: 0 0 auto;
}
</style>
