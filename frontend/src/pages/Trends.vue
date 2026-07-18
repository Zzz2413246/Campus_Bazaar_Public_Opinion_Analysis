<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import BaseChart from '../components/BaseChart.vue'
import LoadingSpinner from '@/components/LoadingSpinner.vue'
import RefreshButton from '@/components/RefreshButton.vue'
import { emotionOption, stackAreaOption, barOption, palette } from '../utils/chartTheme'
import { trendsApi } from '@/utils/api'
import { toast } from '@/utils/toast'

const timeRange = ref('7d')
const today = new Date()
const thirtyDaysAgo = new Date(today)
thirtyDaysAgo.setDate(today.getDate() - 29)
const formatDateInput = (date: Date) => {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}
const customStart = ref(formatDateInput(thirtyDaysAgo))
const customEnd = ref(formatDateInput(today))

const ranges = [
  { k: '7d', v: '近7天' },
  { k: '30d', v: '近30天' },
  { k: 'custom', v: '自定义' },
]

const loading = ref(false)

const emotionData = ref<any>(null)
const stackData = ref<any>(null)
const sourceData = ref<any>(null)

// 情绪变化趋势：API 数据覆盖正/中/负
const emotionOpt = computed(() => {
  const base = emotionOption() as any
  const ed = emotionData.value
  if (ed) {
    const s = base.series as any[]
    if (Array.isArray(ed.positive)) s[0].data = ed.positive
    if (Array.isArray(ed.neutral)) s[1].data = ed.neutral
    if (Array.isArray(ed.negative)) s[2].data = ed.negative
    if (Array.isArray(ed.labels)) base.xAxis.data = ed.labels
  }
  return base
})

// 各类议题热度变化：API 数据覆盖堆叠序列
const stackOpt = computed(() => {
  const base = stackAreaOption() as any
  const sd = stackData.value
  if (sd) {
    if (Array.isArray(sd.labels)) base.xAxis.data = sd.labels
    // 后端可能提供 { series: [{ name, data }] } 或 { 诈骗: [...] }
    const incoming: { name: string; data: number[] }[] = Array.isArray(sd.series)
      ? sd.series.map((x: any) => ({ name: x.name ?? x.category ?? '', data: Array.isArray(x.data) ? x.data : [] }))
      : Object.keys(sd).filter((k) => k !== 'labels' && Array.isArray(sd[k])).map((k) => ({ name: k, data: sd[k] }))
    if (incoming.length) {
      base.series = incoming.map((item, i) => ({
        name: item.name, type: 'line', stack: 'total', smooth: true, symbol: 'none',
        data: item.data,
        lineStyle: { width: 1.5, color: palette[i % palette.length] },
        itemStyle: { color: palette[i % palette.length] },
        areaStyle: { color: palette[i % palette.length], opacity: 0.18 },
      }))
      base.legend.data = incoming.map((x) => x.name)
    }
  }
  return base
})

// 各来源渠道对比：API 数据覆盖柱状数值
const barOpt = computed(() => {
  const base = barOption() as any
  const sd = sourceData.value
  if (sd) {
    const series = (base.series as any[])[0]
    if (Array.isArray(sd.values) && sd.values.length) {
      series.data = sd.values.map((v: any, i: number) => {
        const num = typeof v === 'number' ? v : (v?.value ?? v?.count ?? 0)
        return { value: num, itemStyle: series.data[i % series.data.length]?.itemStyle }
      })
    } else if (Array.isArray(sd) && sd.length) {
      series.data = sd.map((v: any, i: number) => ({
        value: typeof v === 'number' ? v : (v?.value ?? v?.count ?? 0),
        itemStyle: series.data[i % series.data.length]?.itemStyle,
      }))
    }
    if (Array.isArray(sd.labels)) base.xAxis.data = sd.labels
    else if (Array.isArray(sd) && sd.length && sd[0]?.name) base.xAxis.data = sd.map((x: any) => x.name)
  }
  return base
})

const charts = computed(() => [
  { title: '情绪变化趋势', sub: '折线图 · 正面 / 中性 / 负面', opt: emotionOpt.value },
  { title: '各类议题热度变化', sub: '堆叠面积图 · 诈骗 / 治安 / 消防 / 交通 / 设施', opt: stackOpt.value },
  { title: '各类帖子类型分布', sub: '柱状图 · 按安全类别统计', opt: barOpt.value },
])

function unwrap(res: any) {
  if (res && typeof res === 'object' && (res.code !== undefined || res.success !== undefined) && res.data !== undefined) return res.data
  return res
}

// 加载趋势数据，供 onMounted 与手动刷新调用
function currentRangeParams() {
  if (timeRange.value === '30d') return { days: 30 }
  if (timeRange.value === 'custom') {
    return { startDate: customStart.value, endDate: customEnd.value }
  }
  return { days: 7 }
}

async function loadTrends() {
  loading.value = true
  try {
    const res: any = await trendsApi.get(currentRangeParams())
    const d = unwrap(res) || {}
    if (d.emotionData) emotionData.value = d.emotionData
    if (d.stackData) stackData.value = d.stackData
    if (d.sourceData) sourceData.value = d.sourceData
  } catch (err) {
    console.warn('趋势数据加载失败，使用默认数据', err)
  } finally {
    loading.value = false
  }
}

function selectRange(range: string) {
  timeRange.value = range
  if (range !== 'custom') {
    loadTrends()
  }
}

function applyCustomRange() {
  if (!customStart.value || !customEnd.value) {
    toast.error('请选择完整的起止日期')
    return
  }
  if (customStart.value > customEnd.value) {
    toast.error('开始日期不能晚于结束日期')
    return
  }
  loadTrends()
}

onMounted(loadTrends)
</script>

<template>
  <section class="space-y-5" aria-labelledby="trend-analysis-title">
    <!-- 加载状态 -->
    <LoadingSpinner v-if="loading" />
    <!-- 时间切换 -->
    <div class="trend-toolbar flex flex-wrap items-center gap-3 border-t border-slate-200 pt-6">
      <div class="mr-auto">
        <h2 id="trend-analysis-title" class="text-xl font-semibold text-slate-900">趋势研判</h2>
        <p class="text-sm text-slate-600 mt-1">总览趋势的补充分析，聚焦情绪与议题变化</p>
      </div>
      <span class="text-sm font-medium text-slate-700">时间范围</span>
      <div class="seg">
        <span
          v-for="r in ranges"
          :key="r.k"
          @click="selectRange(r.k)"
          :class="['seg-item', timeRange === r.k ? 'seg-item-active' : '']"
        >{{ r.v }}</span>
      </div>
      <div v-if="timeRange === 'custom'" class="flex flex-wrap items-center gap-2">
        <input v-model="customStart" type="date" class="input" :max="customEnd" aria-label="开始日期" />
        <span class="text-slate-500">至</span>
        <input v-model="customEnd" type="date" class="input" :min="customStart" aria-label="结束日期" />
        <button class="btn btn-primary" @click="applyCustomRange">应用</button>
      </div>
      <!-- 刷新按钮 -->
      <RefreshButton :on-refresh="loadTrends" class="ml-auto" />
    </div>

    <!-- 图表网格 -->
    <div class="grid grid-cols-1 lg:grid-cols-2 gap-5">
      <div v-for="c in charts" :key="c.title" class="card card-pad">
        <div class="flex items-center justify-between mb-4">
          <div>
            <h3 class="section-title">{{ c.title }}</h3>
            <p class="section-sub mt-0.5">{{ c.sub }}</p>
          </div>
        </div>
        <div class="h-60">
          <BaseChart :option="c.opt" height="100%" />
        </div>
      </div>
    </div>

  </section>
</template>

<style scoped>
.trend-toolbar {
  padding-left: 52px;
  padding-right: 52px;
}

@media (max-width: 767px) {
  .trend-toolbar {
    padding-left: 20px;
    padding-right: 20px;
  }
}
</style>
