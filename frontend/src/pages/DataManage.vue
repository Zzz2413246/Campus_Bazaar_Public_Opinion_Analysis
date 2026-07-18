<script setup lang="ts">
import { ref, onMounted } from 'vue'
import AppIcon from '../components/AppIcon.vue'
import LoadingSpinner from '../components/LoadingSpinner.vue'
import RefreshButton from '../components/RefreshButton.vue'
import { dataApi, analysisExtensionApi } from '@/utils/api'
import { toast } from '@/utils/toast'

const loading = ref(false)
const stats = ref<any>({})
const task2Status = ref<any>({})

function unwrap(res: any) {
  if (res && typeof res === 'object' && (res.code !== undefined || res.success !== undefined) && res.data !== undefined) return res.data
  return res
}

async function loadStats() {
  loading.value = true
  try {
    const res: any = await dataApi.stats()
    const d = unwrap(res)
    if (d && typeof d === 'object') {
      stats.value = d
    }
    try {
      const task2: any = await analysisExtensionApi.status('task2')
      task2Status.value = unwrap(task2) || {}
    } catch {
      task2Status.value = {}
    }
  } catch (err) {
    console.warn('数据统计加载失败', err)
  } finally {
    loading.value = false
  }
}

async function handleReanalyze() {
  if (!confirm('确定要重新分析所有数据吗？这可能需要几分钟时间。')) return
  loading.value = true
  try {
    const res: any = await dataApi.reanalyze()
    const d = unwrap(res)
    if (d && typeof d === 'object') {
      stats.value = d
      toast.success('重新分析完成')
    }
  } catch (err) {
    toast.error('重新分析失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

async function handleClear() {
  if (!confirm('⚠️ 警告：此操作将清空所有数据！确定继续吗？')) return
  if (!confirm('⚠️ 再次确认：清空后数据不可恢复！确定继续吗？')) return
  loading.value = true
  try {
    await dataApi.clear()
    stats.value = {}
    toast.success('数据已清空')
  } catch (err) {
    toast.error('清空失败')
  } finally {
    loading.value = false
  }
}

onMounted(loadStats)
</script>

<template>
  <div class="page large-detail-page">
    <!-- 页面头部 -->
    <div class="flex items-center justify-between mb-5">
      <div>
        <h2 class="text-xl font-semibold text-slate-800">数据管理</h2>
        <p class="text-sm text-slate-500 mt-1">定期维护数据、增量导入、重新分析</p>
      </div>
      <RefreshButton :on-refresh="loadStats" />
    </div>

    <!-- 评论评判数据 -->
    <div class="card card-pad mt-5">
      <div class="flex flex-wrap items-center justify-between gap-3 mb-4">
        <div>
          <h3 class="section-title">评论评判数据</h3>
          <p class="text-xs text-slate-400 mt-1">评论只作为原帖的增量佐证，单条评论不会改变原分类</p>
        </div>
        <span class="text-xs text-brand-700 bg-brand-50 px-2.5 py-1">
          帖子覆盖率 {{ stats.commentStats?.coverage || 0 }}%
        </span>
      </div>
      <div class="grid grid-cols-2 lg:grid-cols-5 gap-3">
        <div class="p-3 bg-brand-50 border border-brand-100">
          <div class="text-xs text-slate-500">评论总数</div>
          <div class="text-lg font-semibold text-brand-700 mt-1">{{ stats.commentStats?.totalComments || 0 }}</div>
        </div>
        <div class="p-3 bg-emerald-50 border border-emerald-100">
          <div class="text-xs text-slate-500">已关联评论</div>
          <div class="text-lg font-semibold text-emerald-700 mt-1">{{ stats.commentStats?.matchedComments || 0 }}</div>
        </div>
        <div class="p-3 bg-slate-50 border border-slate-100">
          <div class="text-xs text-slate-500">已覆盖帖子</div>
          <div class="text-lg font-semibold text-slate-700 mt-1">{{ stats.commentStats?.assistedPosts || 0 }}</div>
        </div>
        <div class="p-3 bg-rose-50 border border-rose-100">
          <div class="text-xs text-slate-500">负面评论</div>
          <div class="text-lg font-semibold text-rose-700 mt-1">{{ stats.commentStats?.negativeComments || 0 }}</div>
        </div>
        <div class="p-3 bg-amber-50 border border-amber-100">
          <div class="text-xs text-slate-500">风险佐证评论</div>
          <div class="text-lg font-semibold text-amber-700 mt-1">{{ stats.commentStats?.safetyComments || 0 }}</div>
        </div>
      </div>
      <p v-if="stats.commentStats?.unmatchedComments" class="text-xs text-slate-400 mt-3">
        另有 {{ stats.commentStats.unmatchedComments }} 条评论暂未匹配当前帖子，已保留但不参与评分。
      </p>
      <p v-if="stats.commentStats?.adjustedPosts || stats.commentStats?.suggestedPosts" class="text-xs text-slate-500 mt-2">
        当前有 {{ stats.commentStats.adjustedPosts }} 篇帖子获得评论风险加权；另有
        {{ stats.commentStats.suggestedPosts || 0 }} 篇由评论共识生成了人工复核提示，但未改写原帖分类。
      </p>
    </div>

    <!-- 分析质量 -->
    <div class="card card-pad mt-5">
      <div class="flex flex-wrap items-center justify-between gap-3 mb-4">
        <h3 class="section-title">分析质量</h3>
        <span class="text-xs text-slate-500 bg-slate-100 px-2.5 py-1">规则版本 {{ stats.analysisVersion || '-' }}</span>
      </div>
      <div class="grid grid-cols-2 lg:grid-cols-5 gap-3">
        <div class="p-3 bg-brand-50 border border-brand-100">
          <div class="text-xs text-slate-500">识别为安全相关</div>
          <div class="text-lg font-semibold text-brand-700 mt-1">{{ stats.safetyPosts || 0 }}</div>
        </div>
        <div class="p-3 bg-slate-50 border border-slate-100">
          <div class="text-xs text-slate-500">普通讨论</div>
          <div class="text-lg font-semibold text-slate-700 mt-1">{{ stats.normalPosts || 0 }}</div>
        </div>
        <div class="p-3 bg-amber-50 border border-amber-100">
          <div class="text-xs text-slate-500">安全内容占比</div>
          <div class="text-lg font-semibold text-amber-700 mt-1">{{ stats.safetyCoverage || 0 }}%</div>
        </div>
        <div class="p-3 bg-emerald-50 border border-emerald-100">
          <div class="text-xs text-slate-500">高置信分类占比</div>
          <div class="text-lg font-semibold text-emerald-700 mt-1">{{ stats.highConfidenceRate || 0 }}%</div>
        </div>
        <div class="p-3 bg-rose-50 border border-rose-100">
          <div class="text-xs text-slate-500">正文缺失</div>
          <div class="text-lg font-semibold text-rose-700 mt-1">{{ stats.missingContent || 0 }}</div>
        </div>
      </div>
    </div>

    <!-- 加载状态 -->
    <div v-if="loading" class="card card-pad">
      <LoadingSpinner />
    </div>

    <!-- 统计卡片 -->
    <div v-else class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-5">
      <div class="card card-pad">
        <div class="flex items-center gap-3">
          <span class="w-10 h-10 flex items-center justify-center text-brand-600 bg-brand-50">
            <AppIcon name="file-text" :size="20" />
          </span>
          <div>
            <div class="text-2xl font-semibold text-slate-800">{{ stats.totalPosts || 0 }}</div>
            <div class="text-xs text-slate-500">帖子总数</div>
          </div>
        </div>
      </div>

      <div class="card card-pad">
        <div class="flex items-center gap-3">
          <span class="w-10 h-10 flex items-center justify-center text-rose-600 bg-rose-50">
            <AppIcon name="siren" :size="20" />
          </span>
          <div>
            <div class="text-2xl font-semibold text-slate-800">{{ stats.eventCount || 0 }}</div>
            <div class="text-xs text-slate-500">安全事件</div>
          </div>
        </div>
      </div>

      <div class="card card-pad">
        <div class="flex items-center gap-3">
          <span class="w-10 h-10 flex items-center justify-center text-amber-600 bg-amber-50">
            <AppIcon name="trending-up" :size="20" />
          </span>
          <div>
            <div class="text-2xl font-semibold text-slate-800">{{ stats.avgRiskScore || 0 }}</div>
            <div class="text-xs text-slate-500">平均风险评分</div>
          </div>
        </div>
      </div>

      <div class="card card-pad">
        <div class="flex items-center gap-3">
          <span class="w-10 h-10 flex items-center justify-center text-emerald-600 bg-emerald-50">
            <AppIcon name="calendar" :size="20" />
          </span>
          <div>
            <div class="text-2xl font-semibold text-slate-800">{{ stats.todayPosts || 0 }}</div>
            <div class="text-xs text-slate-500">今日新增</div>
          </div>
        </div>
      </div>
    </div>

    <!-- 分类统计 -->
    <div class="card card-pad mt-5">
      <h3 class="section-title mb-4">安全分类统计</h3>
      <div v-if="stats.categoryStats" class="grid grid-cols-2 sm:grid-cols-4 lg:grid-cols-7 gap-3">
        <div v-for="(count, cat) in stats.categoryStats" :key="cat" class="p-3 bg-slate-50/70 border border-slate-100">
          <div class="text-xs text-slate-500 truncate">{{ cat }}</div>
          <div class="text-lg font-semibold text-slate-800 mt-1">{{ count }}</div>
        </div>
      </div>
      <div v-else class="text-sm text-slate-400">暂无分类数据</div>
    </div>

    <!-- 情绪和风险统计 -->
    <div class="grid grid-cols-1 lg:grid-cols-2 gap-5 mt-5">
      <div class="card card-pad">
        <h3 class="section-title mb-4">情绪分布</h3>
        <div v-if="stats.emotionStats" class="flex gap-4">
          <div class="flex-1 text-center p-3 bg-emerald-50 border border-emerald-100">
            <div class="text-xl font-semibold text-emerald-600">{{ stats.emotionStats.正面 || 0 }}</div>
            <div class="text-xs text-slate-500">正面</div>
          </div>
          <div class="flex-1 text-center p-3 bg-slate-50 border border-slate-100">
            <div class="text-xl font-semibold text-slate-600">{{ stats.emotionStats.中性 || 0 }}</div>
            <div class="text-xs text-slate-500">中性</div>
          </div>
          <div class="flex-1 text-center p-3 bg-rose-50 border border-rose-100">
            <div class="text-xl font-semibold text-rose-600">{{ stats.emotionStats.负面 || 0 }}</div>
            <div class="text-xs text-slate-500">负面</div>
          </div>
        </div>
        <div v-else class="text-sm text-slate-400">暂无情绪数据</div>
      </div>

      <div class="card card-pad">
        <h3 class="section-title mb-4">风险等级分布</h3>
        <div v-if="stats.riskLevelStats" class="flex gap-4">
          <div class="flex-1 text-center p-3 bg-rose-50 border border-rose-100">
            <div class="text-xl font-semibold text-rose-600">{{ stats.riskLevelStats.高 || 0 }}</div>
            <div class="text-xs text-slate-500">高风险</div>
          </div>
          <div class="flex-1 text-center p-3 bg-amber-50 border border-amber-100">
            <div class="text-xl font-semibold text-amber-600">{{ stats.riskLevelStats.中 || 0 }}</div>
            <div class="text-xs text-slate-500">中风险</div>
          </div>
          <div class="flex-1 text-center p-3 bg-emerald-50 border border-emerald-100">
            <div class="text-xl font-semibold text-emerald-600">{{ stats.riskLevelStats.低 || 0 }}</div>
            <div class="text-xs text-slate-500">低风险</div>
          </div>
        </div>
        <div v-else class="text-sm text-slate-400">暂无风险数据</div>
      </div>
    </div>

    <!-- 操作按钮 -->
    <div class="card card-pad mt-5">
      <h3 class="section-title mb-4">数据操作</h3>
      <div class="flex flex-wrap gap-3">
        <button @click="handleReanalyze" class="btn btn-primary inline-flex items-center gap-2">
          <AppIcon name="refresh-cw" :size="16" /> 重新分析所有数据
        </button>
        <button @click="handleClear" class="btn inline-flex items-center gap-2 text-rose-600 border-rose-200 hover:bg-rose-50">
          <AppIcon name="trash-2" :size="16" /> 清空所有数据
        </button>
      </div>
      <div class="mt-4 text-xs text-slate-400 bg-slate-50/70 p-3 border border-slate-100">
        <strong>提示：</strong>
        <ul class="list-disc list-inside mt-1 space-y-1">
          <li>重新分析会对所有帖子重新进行分类、情绪识别和风险评分，适用于更新关键词规则后</li>
          <li>评论通过 thread_id 与帖子 id 自动关联，只在形成同类风险佐证时参与加权</li>
          <li>清空数据后需重新导入，谨慎操作</li>
          <li>增量导入可通过 API POST /api/data/import 进行</li>
          <li>评论增量导入可通过 API POST /api/data/comments/import 进行</li>
        </ul>
      </div>
    </div>

    <!-- 后续任务扩展 -->
    <div class="card card-pad mt-5">
      <div class="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h3 class="section-title">分析标准任务二</h3>
          <p class="text-sm text-slate-500 mt-1">独立扩展位，不影响当前核心分析结果</p>
        </div>
        <span :class="task2Status.ready ? 'text-emerald-700 bg-emerald-50' : 'text-amber-700 bg-amber-50'" class="text-xs px-3 py-1.5 border border-current/10">
          {{ task2Status.ready ? '已就绪' : '等待分析标准' }}
        </span>
      </div>
      <p class="text-xs text-slate-400 mt-3">{{ task2Status.message || '接口已预留，收到标准后可直接接入。' }}</p>
    </div>
  </div>
</template>
