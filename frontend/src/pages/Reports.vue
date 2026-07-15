<script setup lang="ts">
import { ref } from 'vue'
import AppIcon from '../components/AppIcon.vue'

const reportType = ref<'daily' | 'weekly' | 'event'>('daily')

const types = [
  { k: 'daily', v: '日报' },
  { k: 'weekly', v: '周报' },
  { k: 'event', v: '事件简报' },
]

const reports = ref([
  { date: '2026-07-14', title: '7月14日 校园安全日报', newPosts: 1245, events: 3, highRisk: 0, status: '已生成' },
  { date: '2026-07-13', title: '7月13日 校园安全日报', newPosts: 1102, events: 5, highRisk: 2, status: '已生成' },
  { date: '2026-07-12', title: '7月12日 校园安全日报', newPosts: 987, events: 4, highRisk: 1, status: '已生成' },
])

const previewContent = ref(`━━━━━━━━━━━━━━━━━━━━━━
  校园安全舆情日报
  2026年7月14日（周日）
━━━━━━━━━━━━━━━━━━━━━━

数据概览
· 新增帖子：1,245 条（↑12%）
· 新增安全事件：3 起
· 高风险事件：0 起
· 整体情绪：平稳

重点事件
1. 西门快递诈骗集中事件（持续）
   - 风险等级：高风险
   - 新增讨论：15 条
   - 状态：保卫处已介入

2. 二食堂卫生投诉（新增）
   - 风险等级：中风险
   - 新增讨论：8 条
   - 建议：通知后勤部门核实

趋势摘要
· 消防与用电安全议题增长230%，需关注夏季用电高峰
· 宿舍设施类投诉略有下降
· 食堂相关讨论有所上升

明日关注
· 持续跟踪西门诈骗事件进展
· 关注宿舍空调维修投诉集中情况
· 留意夏季消防安全相关讨论
`)
</script>

<template>
  <div class="page">
    <div class="flex items-center gap-3 flex-wrap">
      <div class="seg">
        <span
          v-for="r in types"
          :key="r.k"
          @click="reportType = r.k as typeof reportType"
          :class="['seg-item', reportType === r.k ? 'seg-item-active' : '']"
        >{{ r.v }}</span>
      </div>
      <div class="flex-1"></div>
      <button class="btn btn-primary"><AppIcon name="plus" :size="16" /> 生成新报告</button>
    </div>

    <div class="grid grid-cols-1 lg:grid-cols-3 gap-5">
      <!-- 报告列表 -->
      <div class="space-y-3">
        <div
          v-for="(r, i) in reports"
          :key="r.date"
          class="card card-pad card-hover cursor-pointer"
          :class="i === 0 ? 'ring-2 ring-brand-200' : ''"
        >
          <div class="flex items-center justify-between mb-2 gap-2">
            <span class="text-sm font-medium text-slate-800 truncate">{{ r.title }}</span>
            <span class="badge badge-success flex-shrink-0">{{ r.status }}</span>
          </div>
          <div class="flex gap-4 text-xs text-slate-500 flex-wrap">
            <span>帖子 <span class="font-semibold text-slate-700">{{ r.newPosts }}</span></span>
            <span>事件 <span class="font-semibold text-slate-700">{{ r.events }}</span></span>
            <span>高风险 <span :class="r.highRisk > 0 ? 'font-semibold text-rose-500' : 'font-semibold text-slate-700'">{{ r.highRisk }}</span></span>
          </div>
        </div>
      </div>

      <!-- 预览区 -->
      <div class="card card-pad lg:col-span-2">
        <div class="flex items-start justify-between mb-4 gap-3 flex-wrap">
          <div class="min-w-0">
            <h3 class="section-title">报告预览</h3>
            <p class="section-sub mt-0.5 truncate">{{ reports[0].title }}</p>
          </div>
          <div class="flex gap-2 flex-wrap">
            <button class="btn btn-ghost !py-1.5 !px-3 text-xs"><AppIcon name="copy" :size="14" /> 复制文本</button>
            <button class="btn btn-ghost !py-1.5 !px-3 text-xs"><AppIcon name="download" :size="14" /> 导出 PDF</button>
            <button class="btn btn-ghost !py-1.5 !px-3 text-xs"><AppIcon name="download" :size="14" /> 导出 Word</button>
          </div>
        </div>
        <div class="bg-slate-50/70 p-6 border border-slate-100 overflow-x-auto">
          <pre class="text-sm text-slate-700 whitespace-pre-wrap font-sans leading-relaxed">{{ previewContent }}</pre>
        </div>
      </div>
    </div>
  </div>
</template>
