<script setup lang="ts">
import { ref } from 'vue'
import BaseChart from '../components/BaseChart.vue'
import AppIcon from '../components/AppIcon.vue'
import { trendLineOption, donutOption, sparklineOption } from '../utils/chartTheme'
import { useCountUp } from '../utils/useCountUp'

const stats = ref([
  { label: '总帖子数', value: 12845, change: '较昨日 +12%', trend: 'up', icon: 'message-square', accent: 'brand', spark: [980, 1120, 1050, 1340, 1180, 1420, 1245], sparkColor: '#6366f1' },
  { label: '安全事件', value: 23, change: '较昨日 -5%', trend: 'good', icon: 'siren', accent: 'amber', spark: [8, 6, 7, 5, 6, 5, 3], sparkColor: '#f59e0b' },
  { label: '高风险事件', value: 3, change: '需关注', trend: 'warn', icon: 'alert-triangle', accent: 'rose', spark: [1, 2, 1, 3, 1, 2, 3], sparkColor: '#f43f5e' },
  { label: '整体情绪', value: 0, textValue: '平稳', change: '正向 ↑2%', trend: 'up', icon: 'smile', accent: 'emerald', spark: [38, 40, 42, 41, 44, 48, 50], sparkColor: '#10b981' },
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

const recentEvents = ref([
  { time: '10:32', title: '西门快递点投诉集中', category: '设施', risk: '中', status: '处理中' },
  { time: '09:15', title: '某宿舍楼停电问题', category: '宿舍', risk: '低', status: '已确认' },
  { time: '昨天', title: '二食堂卫生投诉增加', category: '食堂', risk: '中', status: '待研判' },
  { time: '昨天', title: '教学楼区域电动车乱停', category: '交通', risk: '低', status: '已确认' },
  { time: '前天', title: '西门快递诈骗集中事件', category: '诈骗', risk: '高', status: '处理中' },
])

const riskBadge = (r: string) => r === '高' ? 'badge-high' : r === '中' ? 'badge-medium' : 'badge-low'
const riskDot = (r: string) => r === '高' ? 'dot-high' : r === '中' ? 'dot-medium' : 'dot-low'
</script>

<template>
  <div class="page">
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

    <!-- 图表区域 -->
    <div class="grid grid-cols-1 lg:grid-cols-3 gap-5">
      <div class="card card-pad lg:col-span-2">
        <div class="flex items-center justify-between mb-4">
          <div>
            <h3 class="section-title">近7天舆情趋势</h3>
            <p class="section-sub mt-0.5">帖子总量 / 安全事件 / 高风险事件</p>
          </div>
          <span class="badge badge-info">7 天</span>
        </div>
        <div class="h-72">
          <BaseChart :option="trendLineOption()" height="100%" />
        </div>
      </div>

      <div class="card card-pad">
        <div class="mb-4">
          <h3 class="section-title">安全议题分布</h3>
          <p class="section-sub mt-0.5">各类别占比</p>
        </div>
        <div class="h-72">
          <BaseChart :option="donutOption()" height="100%" />
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
          <span class="btn-link text-sm">查看全部 →</span>
        </div>
        <div class="space-y-2.5">
          <div class="flex items-center gap-3 p-3 bg-slate-50/70 border border-slate-100 hover:bg-slate-100/70 transition-colors cursor-pointer">
            <span class="dot dot-high flex-shrink-0 pulse-dot bg-rose-500"></span>
            <div class="flex-1 min-w-0">
              <div class="text-sm text-slate-800 font-medium">21栋宿舍电路起火传闻</div>
              <div class="text-xs text-slate-500 mt-0.5">32条讨论 · 7月13日</div>
            </div>
            <span class="badge badge-high">高风险</span>
          </div>
          <div class="flex items-center gap-3 p-3 bg-slate-50/70 border border-slate-100 hover:bg-slate-100/70 transition-colors cursor-pointer">
            <span class="dot dot-medium flex-shrink-0"></span>
            <div class="flex-1 min-w-0">
              <div class="text-sm text-slate-800 font-medium">二食堂卫生投诉集中</div>
              <div class="text-xs text-slate-500 mt-0.5">28条讨论 · 7月11日</div>
            </div>
            <span class="badge badge-medium">中风险</span>
          </div>
          <div class="flex items-center gap-3 p-3 bg-slate-50/70 border border-slate-100 hover:bg-slate-100/70 transition-colors cursor-pointer">
            <span class="dot dot-medium flex-shrink-0"></span>
            <div class="flex-1 min-w-0">
              <div class="text-sm text-slate-800 font-medium">校园交通拥堵讨论</div>
              <div class="text-xs text-slate-500 mt-0.5">18条讨论 · 7月12日</div>
            </div>
            <span class="badge badge-medium">中风险</span>
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
  </div>
</template>
