<script setup lang="ts">
import { ref } from 'vue'
import BaseChart from '../components/BaseChart.vue'
import { areaOption, emotionOption, stackAreaOption, barOption } from '../utils/chartTheme'

const timeRange = ref('7d')

const ranges = [
  { k: '7d', v: '近7天' },
  { k: '30d', v: '近30天' },
  { k: 'custom', v: '自定义' },
]

const charts = [
  { title: '帖子总量趋势', sub: '面积图 · 日期 / 帖子数量', opt: areaOption() },
  { title: '情绪变化趋势', sub: '折线图 · 正面 / 中性 / 负面', opt: emotionOption() },
  { title: '各类议题热度变化', sub: '堆叠面积图 · 诈骗 / 治安 / 消防 / 交通 / 设施', opt: stackAreaOption() },
  { title: '各来源渠道对比', sub: '柱状图 · 校园集市 / 小红书 / 微博 / B站', opt: barOption() },
]

const topItems = [
  { name: '消防与用电安全', pct: 230, w: 95, c: 'from-rose-500 to-rose-400' },
  { name: '宿舍设施问题', pct: 85, w: 60, c: 'from-amber-500 to-amber-400' },
  { name: '诈骗与财产安全', pct: 42, w: 42, c: 'from-amber-500 to-amber-400' },
  { name: '校园交通安全', pct: 28, w: 30, c: 'from-brand-500 to-brand-400' },
  { name: '食堂问题', pct: 15, w: 20, c: 'from-brand-500 to-brand-400' },
]
</script>

<template>
  <div class="page">
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

    <!-- TOP5 -->
    <div class="card card-pad">
      <div class="flex items-center justify-between mb-5">
        <div>
          <h3 class="section-title">本周增长最快议题 TOP5</h3>
          <p class="section-sub mt-0.5">按讨论量环比增速排序</p>
        </div>
        <span class="badge badge-high">🔥 高增长</span>
      </div>
      <div class="space-y-4">
        <div v-for="(item, i) in topItems" :key="i" class="flex items-center gap-4">
          <span class="w-6 h-6 rounded-lg flex items-center justify-center text-xs font-bold flex-shrink-0"
            :class="i === 0 ? 'bg-rose-100 text-rose-600' : i < 3 ? 'bg-amber-100 text-amber-600' : 'bg-slate-100 text-slate-500'">
            {{ i + 1 }}
          </span>
          <span class="w-32 text-sm text-slate-700 font-medium flex-shrink-0">{{ item.name }}</span>
          <div class="flex-1 h-2.5 bg-slate-100 overflow-hidden">
            <div :class="['h-full rounded-full bg-gradient-to-r', item.c]" :style="{ width: item.w + '%' }"></div>
          </div>
          <span :class="['text-sm font-semibold w-16 text-right', item.pct > 50 ? 'text-rose-500' : 'text-amber-500']">↑ {{ item.pct }}%</span>
        </div>
      </div>
    </div>
  </div>
</template>
