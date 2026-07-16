<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import BaseChart from '../components/BaseChart.vue'
import LoadingSpinner from '@/components/LoadingSpinner.vue'
import RefreshButton from '@/components/RefreshButton.vue'
import EmptyState from '@/components/EmptyState.vue'
import { areaOption, emotionOption, stackAreaOption, barOption, palette } from '../utils/chartTheme'
import { trendsApi } from '@/utils/api'

const timeRange = ref('7d')

const ranges = [
  { k: '7d', v: '近7天' },
  { k: '30d', v: '近30天' },
  { k: 'custom', v: '自定义' },
]

const loading = ref(false)

const areaData = ref<any>(null)
const emotionData = ref<any>(null)
const stackData = ref<any>(null)
const sourceData = ref<any>(null)

// 占位默认值（后端数据到达后会被覆盖）；pct 为占比 0~100
const topItems = ref<any[]>([])

// 帖子总量趋势：API 数据覆盖默认序列
const areaOpt = computed(() => {
  const base = areaOption() as any
  const ad = areaData.value
  if (ad) {
    if (Array.isArray(ad.data)) (base.series as any[])[0].data = ad.data
    if (Array.isArray(ad.labels)) base.xAxis.data = ad.labels
  }
  return base
})

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
  { title: '帖子总量趋势', sub: '面积图 · 日期 / 帖子数量', opt: areaOpt.value },
  { title: '情绪变化趋势', sub: '折线图 · 正面 / 中性 / 负面', opt: emotionOpt.value },
  { title: '各类议题热度变化', sub: '堆叠面积图 · 诈骗 / 治安 / 消防 / 交通 / 设施', opt: stackOpt.value },
  { title: '各类帖子类型分布', sub: '柱状图 · 按安全类别统计', opt: barOpt.value },
])

function unwrap(res: any) {
  if (res && typeof res === 'object' && (res.code !== undefined || res.success !== undefined) && res.data !== undefined) return res.data
  return res
}

// 加载趋势数据，供 onMounted 与手动刷新调用
async function loadTrends() {
  loading.value = true
  try {
    const res: any = await trendsApi.get()
    const d = unwrap(res) || {}
    if (d.areaData) areaData.value = d.areaData
    if (d.emotionData) emotionData.value = d.emotionData
    if (d.stackData) stackData.value = d.stackData
    if (d.sourceData) sourceData.value = d.sourceData
    if (Array.isArray(d.topItems) && d.topItems.length) {
      topItems.value = d.topItems.map((t: any) => {
        // pct = 该类别占所有安全帖子的真实占比（0~100）
        const pct = t.pct ?? 0
        return {
          name: t.name ?? t.topic ?? '',
          pct,
          count: t.count ?? 0,
          w: t.w ?? Math.max(10, Math.min(95, pct)),
          c: t.c ?? (pct > 40 ? 'from-rose-500 to-rose-400' : pct > 20 ? 'from-amber-500 to-amber-400' : 'from-brand-500 to-brand-400'),
        }
      })
    }
  } catch (err) {
    console.warn('趋势数据加载失败，使用默认数据', err)
  } finally {
    loading.value = false
  }
}

onMounted(loadTrends)
</script>

<template>
  <div class="page">
    <!-- 加载状态 -->
    <LoadingSpinner v-if="loading" />
    <!-- 时间切换 -->
    <div class="flex items-center gap-3">
      <span class="text-sm text-slate-600">时间范围</span>
      <div class="seg">
        <span
          v-for="r in ranges"
          :key="r.k"
          @click="timeRange = r.k"
          :class="['seg-item', timeRange === r.k ? 'seg-item-active' : '']"
        >{{ r.v }}</span>
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

    <!-- 议题分布 TOP5 -->
    <div class="card card-pad">
      <div class="flex items-center justify-between mb-5">
        <div>
          <h3 class="section-title">安全议题分布 TOP5</h3>
          <p class="section-sub mt-0.5">按讨论量占比排序 · 基于全部安全相关帖子</p>
        </div>
        <span class="badge badge-info">📊 议题分布</span>
      </div>
      <div v-if="topItems.length" class="space-y-4">
        <div v-for="(item, i) in topItems" :key="i" class="flex items-center gap-4">
          <span class="w-6 h-6 rounded-lg flex items-center justify-center text-xs font-bold flex-shrink-0"
            :class="i === 0 ? 'bg-rose-100 text-rose-600' : i < 3 ? 'bg-amber-100 text-amber-600' : 'bg-slate-100 text-slate-500'">
            {{ i + 1 }}
          </span>
          <span class="w-36 text-sm text-slate-700 font-medium flex-shrink-0 truncate">{{ item.name }}</span>
          <div class="flex-1 h-2.5 bg-slate-100 overflow-hidden">
            <div :class="['h-full bg-gradient-to-r', item.c]" :style="{ width: item.w + '%' }"></div>
          </div>
          <span class="text-xs text-slate-400 w-12 text-right">{{ item.count ?? '-' }} 条</span>
          <span :class="['text-sm font-semibold w-14 text-right', item.pct > 40 ? 'text-rose-500' : item.pct > 20 ? 'text-amber-500' : 'text-slate-500']">{{ item.pct }}%</span>
        </div>
      </div>
      <!-- 无数据时显示空状态 -->
      <EmptyState v-else text="暂无安全议题数据" />
    </div>
  </div>
</template>
