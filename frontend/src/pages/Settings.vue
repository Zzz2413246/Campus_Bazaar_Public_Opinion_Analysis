<script setup lang="ts">
import { ref } from 'vue'

const riskThresholds = ref({ high: 70, medium: 40 })

const categories = ref([
  '诈骗与财产安全', '治安与人身安全', '消防与用电安全', '校园交通安全',
  '宿舍设施问题', '食堂及校园设施', '突发事件与异常', '学生投诉与诉求',
])

const sources = ref([
  { name: '校园集市数据', desc: '最后同步：2026-07-14 10:30', status: '已连接', ok: true },
  { name: '外部社交媒体数据', desc: '微博 · 小红书 · B站', status: '已连接', ok: true },
  { name: '项目组已有数据', desc: '约10万条社交媒体评论', status: '待导入', ok: false },
])
</script>

<template>
  <div class="page">
    <div class="grid grid-cols-1 xl:grid-cols-2 gap-5">
      <!-- 风险评分阈值 -->
      <div class="card card-pad">
        <h3 class="section-title mb-4">风险评分阈值</h3>
        <div class="space-y-6">
          <div>
            <div class="flex items-center justify-between mb-2">
              <span class="text-sm text-slate-700">高风险阈值</span>
              <span class="badge badge-high">≥ {{ riskThresholds.high }} 分</span>
            </div>
            <input type="range" v-model.number="riskThresholds.high" min="50" max="100" class="w-full accent-rose-500" />
          </div>
          <div>
            <div class="flex items-center justify-between mb-2">
              <span class="text-sm text-slate-700">中风险阈值</span>
              <span class="badge badge-medium">≥ {{ riskThresholds.medium }} 分</span>
            </div>
            <input type="range" v-model.number="riskThresholds.medium" min="20" max="80" class="w-full accent-amber-500" />
          </div>
          <div class="text-sm text-slate-500 bg-slate-50/70 p-4 leading-relaxed border border-slate-100">
            评分规则：低于 <span class="font-semibold text-emerald-600">{{ riskThresholds.medium }} 分</span> 为低风险，<span class="font-semibold text-amber-600">{{ riskThresholds.medium }}–{{ riskThresholds.high }} 分</span> 为中风险，<span class="font-semibold text-rose-600">{{ riskThresholds.high }} 分</span> 及以上为高风险
          </div>
        </div>
      </div>

      <!-- 数据源配置 -->
      <div class="card card-pad">
        <h3 class="section-title mb-4">数据源配置</h3>
        <div class="space-y-2">
          <div
            v-for="s in sources"
            :key="s.name"
            class="flex items-center justify-between p-3 bg-slate-50/70 border border-slate-100"
          >
            <div class="min-w-0">
              <div class="text-sm text-slate-700 font-medium">{{ s.name }}</div>
              <div class="text-xs text-slate-500 mt-0.5">{{ s.desc }}</div>
            </div>
            <span :class="['badge', s.ok ? 'badge-success' : 'badge-warn']">
              <span :class="['dot', s.ok ? 'dot-low' : 'dot-medium']"></span>{{ s.status }}
            </span>
          </div>
        </div>
      </div>
    </div>

    <!-- 安全议题分类 -->
    <div class="card card-pad">
      <h3 class="section-title mb-4">安全议题分类</h3>
      <div class="grid grid-cols-1 sm:grid-cols-2 gap-2">
        <div v-for="(c, i) in categories" :key="i" class="flex items-center justify-between p-3 bg-slate-50/70 border border-slate-100 hover:bg-slate-100/70 transition-colors">
          <div class="flex items-center gap-3 min-w-0">
            <span class="w-7 h-7 rounded-lg bg-brand-50 text-brand-600 flex items-center justify-center text-xs font-semibold flex-shrink-0">{{ i + 1 }}</span>
            <span class="text-sm text-slate-700 truncate">{{ c }}</span>
          </div>
          <div class="flex gap-1 flex-shrink-0">
            <button class="text-xs text-slate-400 hover:text-brand-500 cursor-pointer px-2 py-1 hover:bg-white transition-colors">编辑</button>
            <button class="text-xs text-slate-400 hover:text-rose-500 cursor-pointer px-2 py-1 hover:bg-white transition-colors">删除</button>
          </div>
        </div>
      </div>
      <button class="btn btn-ghost mt-4 w-full">+ 新增分类</button>
    </div>
  </div>
</template>
