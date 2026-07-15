<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import BaseChart from '../components/BaseChart.vue'
import AppIcon from '../components/AppIcon.vue'
import { miniTrendOption, radarOption } from '../utils/chartTheme'

const router = useRouter()

const event = ref({
  id: '1', title: '西门快递诈骗集中事件', risk: '高', riskScore: 87,
  summary: '7月10日至12日，多位学生在校园集市和小红书反映西门菜鸟驿站附近有人以借钱为由实施诈骗，金额多在50-200元之间。已有约45条相关讨论，且仍在快速增长。',
  stats: { postCount: 45, affectedRange: '较广（450+人关注）', urgency: '紧急', emotion: '愤怒（负面占比85%）' },
  riskReasons: [
    { reason: '帖子数量：45条（3天）', score: '+25', detail: '高频讨论' },
    { reason: '增长速度：+15条/天', score: '+20', detail: '快速增长' },
    { reason: '事件类型：诈骗', score: '+30', detail: '高风险类别' },
    { reason: '情绪得分：负面85%', score: '+12', detail: '强烈负面情绪' },
  ],
  trend: [{ date: '7月10日', count: 5 }, { date: '7月11日', count: 12 }, { date: '7月12日', count: 28 }],
  relatedPosts: [
    { time: '7月12日 14:30', source: '校园集市', content: '西门菜鸟驿站有人假装借钱，骗了我200块，大家小心！', emotion: '😡', comments: 23 },
    { time: '7月12日 11:20', source: '小红书', content: '提醒南开的同学们，西门附近有骗子假装借钱，已有多人上当', emotion: '😡', comments: 45 },
    { time: '7月11日 16:45', source: '校园集市', content: '今天在西门遇到个借钱的，说自己是外校学生找不到钱包了', emotion: '😐', comments: 8 },
    { time: '7月10日 09:15', source: '微博', content: '南开西门最近出现疑似诈骗人员，各位同学注意安全', emotion: '😟', comments: 12 },
  ],
})

const adjustedRisk = ref(event.value.risk)
const dispositionStatus = ref('未处理')
const remark = ref('')
</script>

<template>
  <div class="page">
    <!-- 返回 + 标题 -->
    <div class="flex items-center gap-3 flex-wrap">
      <button @click="router.push('/events')" class="btn btn-ghost !py-1.5 !px-3 text-xs"><AppIcon name="arrow-left" :size="14" /> 返回列表</button>
      <span :class="['badge', event.risk === '高' ? 'badge-high' : 'badge-medium']">
        <span :class="['dot', event.risk === '高' ? 'dot-high' : 'dot-medium']"></span>{{ event.risk }}风险
      </span>
      <h2 class="text-lg font-semibold text-slate-800">{{ event.title }}</h2>
    </div>

    <!-- 事件摘要 -->
    <div class="card card-pad">
      <h3 class="section-title mb-2">事件摘要</h3>
      <p class="text-sm text-slate-600 leading-relaxed">{{ event.summary }}</p>
    </div>

    <!-- 核心指标 -->
    <div class="grid grid-cols-2 xl:grid-cols-4 gap-5">
      <div class="card card-pad text-center relative overflow-hidden">
        <div class="absolute inset-x-0 top-0 h-1 bg-gradient-to-r from-rose-500 to-rose-400"></div>
        <div class="text-3xl font-bold text-rose-500 tracking-tight">{{ event.riskScore }}</div>
        <div class="text-xs text-slate-500 mt-1">风险评分 / 100</div>
      </div>
      <div class="card card-pad text-center relative overflow-hidden">
        <div class="absolute inset-x-0 top-0 h-1 bg-gradient-to-r from-brand-500 to-brand-400"></div>
        <div class="text-lg font-semibold text-slate-800">{{ event.stats.postCount }}条</div>
        <div class="text-xs text-slate-500 mt-1">相关帖子</div>
      </div>
      <div class="card card-pad text-center relative overflow-hidden">
        <div class="absolute inset-x-0 top-0 h-1 bg-gradient-to-r from-accent-500 to-accent-400"></div>
        <div class="text-sm font-medium text-slate-700 leading-relaxed">{{ event.stats.affectedRange }}</div>
        <div class="text-xs text-slate-500 mt-1">影响范围</div>
      </div>
      <div class="card card-pad text-center relative overflow-hidden">
        <div class="absolute inset-x-0 top-0 h-1 bg-gradient-to-r from-amber-500 to-amber-400"></div>
        <div class="text-sm font-medium text-amber-600 leading-relaxed">{{ event.stats.emotion }}</div>
        <div class="text-xs text-slate-500 mt-1">情绪倾向</div>
      </div>
    </div>

    <!-- 判断依据 + 风险雷达 -->
    <div class="grid grid-cols-1 lg:grid-cols-2 gap-5">
      <div class="card card-pad">
        <h3 class="section-title mb-4">风险判断依据</h3>
        <div class="space-y-2">
          <div v-for="(r, i) in event.riskReasons" :key="i" class="flex items-center justify-between p-3 bg-slate-50/70 border border-slate-100">
            <div>
              <div class="text-sm text-slate-700">{{ r.reason }}</div>
              <div class="text-xs text-slate-500 mt-0.5">{{ r.detail }}</div>
            </div>
            <span class="text-sm font-semibold text-brand-600">{{ r.score }}</span>
          </div>
          <div class="flex items-center justify-between p-3 bg-brand-50 border border-brand-100">
            <div class="text-sm font-semibold text-brand-700">综合评分</div>
            <span class="text-lg font-bold text-brand-700">{{ event.riskScore }}</span>
          </div>
        </div>
      </div>

      <div class="card card-pad">
        <h3 class="section-title mb-4">风险维度雷达</h3>
        <div class="h-64">
          <BaseChart :option="radarOption()" height="100%" />
        </div>
      </div>
    </div>

    <!-- 相关帖子时间趋势 -->
    <div class="card card-pad">
      <h3 class="section-title mb-4">相关帖子时间趋势</h3>
      <div class="h-48">
        <BaseChart :option="miniTrendOption(event.trend.map(t => t.count))" height="100%" />
      </div>
    </div>

    <!-- 相关帖子 · 时间轴 -->
    <div class="card card-pad">
      <div class="flex items-center justify-between mb-5">
        <h3 class="section-title">相关帖子</h3>
        <span class="badge badge-neutral">{{ event.relatedPosts.length }} 条</span>
      </div>
      <div class="relative pl-6">
        <!-- 时间轴竖线 -->
        <div class="absolute left-[7px] top-2 bottom-2 w-px bg-slate-200"></div>
        <div v-for="(p, i) in event.relatedPosts" :key="i" class="relative mb-5 last:mb-0">
          <!-- 节点 -->
          <span class="absolute -left-6 top-1.5 w-3.5 h-3.5 rounded-full bg-white border-2 border-brand-400 ring-4 ring-white"></span>
          <div class="flex items-center gap-2 mb-1.5 flex-wrap">
            <span class="text-xs text-slate-500 font-medium">{{ p.time }}</span>
            <span class="badge badge-info">{{ p.source }}</span>
            <span class="text-base">{{ p.emotion }}</span>
            <span class="text-xs text-slate-500 inline-flex items-center gap-1 ml-auto"><AppIcon name="message-circle" :size="13" /> {{ p.comments }}</span>
          </div>
          <p class="text-sm text-slate-700 leading-relaxed bg-slate-50/70 border border-slate-100 p-3">{{ p.content }}</p>
        </div>
      </div>
    </div>

    <!-- 人工研判 -->
    <div class="card card-pad relative overflow-hidden">
      <div class="absolute inset-x-0 top-0 h-1 bg-gradient-to-r from-brand-600 to-accent-500"></div>
      <h3 class="section-title mb-4">人工研判</h3>
      <div class="grid grid-cols-1 sm:grid-cols-3 gap-4 mb-4">
        <div>
          <label class="text-sm text-slate-600 block mb-1.5">风险等级调整</label>
          <select v-model="adjustedRisk" class="select w-full">
            <option>高</option><option>中</option><option>低</option>
          </select>
        </div>
        <div>
          <label class="text-sm text-slate-600 block mb-1.5">处置状态</label>
          <select v-model="dispositionStatus" class="select w-full">
            <option>未处理</option><option>处理中</option><option>已确认</option><option>已忽略</option>
          </select>
        </div>
        <div class="flex items-end gap-2">
          <button class="btn btn-primary flex-1">保存研判</button>
          <button class="btn btn-ghost">生成简报</button>
        </div>
      </div>
      <div>
        <label class="text-sm text-slate-600 block mb-1.5">研判备注</label>
        <textarea v-model="remark" rows="2" placeholder="输入研判意见..." class="input w-full resize-none"></textarea>
      </div>
    </div>
  </div>
</template>
