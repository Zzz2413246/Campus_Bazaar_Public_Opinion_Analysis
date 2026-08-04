<script setup lang="ts">
import { computed, ref, onMounted, onUnmounted } from 'vue'
import AppIcon from '../components/AppIcon.vue'
import LoadingSpinner from '../components/LoadingSpinner.vue'
import RefreshButton from '../components/RefreshButton.vue'
import { dataApi, analysisExtensionApi, auditApi, authApi } from '@/utils/api'
import { toast } from '@/utils/toast'

const loading = ref(false)
const stats = ref<any>({})
const task2Status = ref<any>({})
const auditLogs = ref<any[]>([])
const auditSummary = ref<any>({})
const auditTotal = ref(0)
const auditPage = ref(1)
const auditAction = ref('')
const accountInfo = ref<any>({ role: localStorage.getItem('yuqing_role') || 'ADMIN', permissions: [] })
const quality = computed(() => stats.value.quality || { score: 0, status: '检查中', dimensions: {}, issues: [] })
const importKind = ref<'posts' | 'comments'>('posts')
const importFileName = ref('')
const importRows = ref<any[]>([])
const importPreview = computed(() => importRows.value.slice(0, 5))
const importDuplicateCount = computed(() => {
  const key = importKind.value === 'posts' ? 'id' : 'comment_id'
  const ids = importRows.value.map(row => String(row?.[key] ?? '').trim()).filter(Boolean)
  return ids.length - new Set(ids).size
})
const importError = ref('')
const importing = ref(false)
const importResult = ref<any>(null)
const reanalysisJob = ref<any>({ status: 'IDLE', progress: 0, message: '' })
let reanalysisTimer: ReturnType<typeof setTimeout> | null = null

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
    const [task2, audit, summary, account] = await Promise.allSettled([
      analysisExtensionApi.status('task2'),
      auditApi.logs({ page: auditPage.value, size: 10, action: auditAction.value || undefined }),
      auditApi.summary(),
      authApi.me(),
    ])
    task2Status.value = task2.status === 'fulfilled' ? unwrap(task2.value) || {} : {}
    if (audit.status === 'fulfilled') applyAudit(unwrap(audit.value))
    auditSummary.value = summary.status === 'fulfilled' ? unwrap(summary.value) || {} : {}
    accountInfo.value = account.status === 'fulfilled' ? unwrap(account.value) || accountInfo.value : accountInfo.value
  } catch (err) {
    console.warn('数据统计加载失败', err)
  } finally {
    loading.value = false
  }
}

function applyAudit(data: any) {
  auditLogs.value = Array.isArray(data?.data) ? data.data : []
  auditTotal.value = Number(data?.total || 0)
}

async function loadAudit(page = 1) {
  auditPage.value = Math.max(1, page)
  try {
    const res: any = await auditApi.logs({ page: auditPage.value, size: 10, action: auditAction.value || undefined })
    applyAudit(unwrap(res))
  } catch (err) {
    console.warn('审计记录加载失败', err)
  }
}

function qualityTone(value: number) {
  return value >= 90 ? 'text-emerald-700 bg-emerald-50 border-emerald-200'
    : value >= 75 ? 'text-amber-700 bg-amber-50 border-amber-200'
      : 'text-rose-700 bg-rose-50 border-rose-200'
}

function severityClass(value: string) {
  return value === '高' ? 'badge-high' : value === '中' ? 'badge-warn' : 'badge-neutral'
}

function formatTime(value: unknown) {
  if (!value) return '-'
  const date = new Date(String(value))
  return Number.isNaN(date.getTime()) ? String(value) : date.toLocaleString('zh-CN')
}

function permissionName(value: string) {
  const names: Record<string, string> = {
    VIEW_DATA: '查看数据', REVIEW_POST: '人工复核', MANAGE_EVENT: '事件处置',
    MANAGE_SETTINGS: '系统设置', MANAGE_DATA: '数据管理', VIEW_AUDIT: '查看审计',
  }
  return names[value] || value
}

async function handleReanalyze() {
  const externalMode = reanalysisJob.value.mode === 'EXTERNAL_CLASSIFIED'
  const prompt = externalMode
    ? '确定刷新事件聚合吗？已导入的最终分类和人工复核结果不会被修改。'
    : '确定要重新分析所有数据吗？这可能需要几分钟时间。'
  if (!confirm(prompt)) return
  try {
    const res: any = await dataApi.reanalyze()
    reanalysisJob.value = unwrap(res) || res
    toast.success(externalMode ? '事件聚合刷新任务已启动' : '重新分析任务已启动')
    pollReanalysis()
  } catch (err) {
    toast.error('重新分析失败，请稍后重试')
  }
}

async function pollReanalysis() {
  if (reanalysisTimer) clearTimeout(reanalysisTimer)
  try {
    const res: any = await dataApi.reanalyzeStatus()
    reanalysisJob.value = unwrap(res) || res || {}
    if (reanalysisJob.value.status === 'RUNNING') {
      reanalysisTimer = setTimeout(pollReanalysis, 1000)
    } else if (reanalysisJob.value.status === 'COMPLETED') {
      if (reanalysisJob.value.result) stats.value = reanalysisJob.value.result
      toast.success(reanalysisJob.value.message || '处理完成')
      await loadStats()
    } else if (reanalysisJob.value.status === 'FAILED') {
      toast.error(reanalysisJob.value.message || '重新分析失败')
    }
  } catch {
    reanalysisTimer = setTimeout(pollReanalysis, 2000)
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

async function handleImportFile(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  importError.value = ''
  importResult.value = null
  importRows.value = []
  importFileName.value = ''
  if (!file) return
  if (!file.name.toLowerCase().endsWith('.json')) {
    importError.value = '请选择扩展名为 .json 的文件'
    return
  }
  try {
    const parsed = JSON.parse(await file.text())
    if (!Array.isArray(parsed)) throw new Error('JSON 顶层必须是数组')
    if (!parsed.length) throw new Error('文件中没有可导入的数据')
    const idField = importKind.value === 'posts' ? 'id' : 'comment_id'
    const invalidCount = parsed.filter((row) => !row || typeof row !== 'object' || !String(row[idField] ?? '').trim()).length
    if (invalidCount) throw new Error(`${invalidCount} 条数据缺少必填字段 ${idField}`)
    if (importKind.value === 'comments') {
      const missingThreadCount = parsed.filter((row) => !String(row?.thread_id ?? '').trim()).length
      if (missingThreadCount) throw new Error(`${missingThreadCount} 条评论缺少必填字段 thread_id`)
    }
    importFileName.value = file.name
    importRows.value = parsed
  } catch (err: any) {
    importError.value = err?.message || '文件解析失败，请检查 JSON 格式'
  }
}

async function submitImport() {
  if (!importRows.value.length || importing.value) return
  importing.value = true
  importError.value = ''
  importResult.value = null
  try {
    const res: any = importKind.value === 'posts'
      ? await dataApi.import(importRows.value)
      : await dataApi.importComments(importRows.value)
    importResult.value = unwrap(res) || res || {}
    if (importResult.value.reanalysisJob) {
      reanalysisJob.value = importResult.value.reanalysisJob
      pollReanalysis()
    }
    toast.success(importResult.value.message || `${importKind.value === 'posts' ? '帖子' : '评论'}导入完成`)
    await loadStats()
  } catch (err: any) {
    importError.value = err?.response?.data?.message || '导入失败，请检查文件内容后重试'
  } finally {
    importing.value = false
  }
}

function resetImport() {
  importFileName.value = ''
  importRows.value = []
  importError.value = ''
  importResult.value = null
}

onMounted(async () => {
  await loadStats()
  const job: any = await dataApi.reanalyzeStatus().catch(() => null)
  if (job) {
    reanalysisJob.value = unwrap(job) || job
    if (reanalysisJob.value.status === 'RUNNING') pollReanalysis()
  }
})
onUnmounted(() => {
  if (reanalysisTimer) clearTimeout(reanalysisTimer)
})
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

    <!-- 数据质量总览 -->
    <section class="card card-pad border-t-4 border-t-brand-500">
      <div class="quality-header">
        <div>
          <h3 class="section-title">数据质量监控</h3>
          <p class="text-sm text-slate-500 mt-1">从完整性、分析覆盖、评论关联和数据时效四个维度自动检查</p>
        </div>
        <div :class="['quality-score', qualityTone(Number(quality.score || 0))]">
          <div class="text-2xl font-bold">{{ quality.score || 0 }}<span class="text-sm font-normal"> / 100</span></div>
          <div class="text-xs mt-0.5">{{ quality.status }} · {{ quality.issueCount || 0 }} 项异常</div>
        </div>
      </div>
      <div class="grid grid-cols-2 lg:grid-cols-4 gap-3">
        <div class="quality-dimension"><span>内容完整性</span><strong>{{ quality.dimensions?.completeness || 0 }}%</strong></div>
        <div class="quality-dimension"><span>分析覆盖率</span><strong>{{ quality.dimensions?.analysisCoverage || 0 }}%</strong></div>
        <div class="quality-dimension"><span>评论关联率</span><strong>{{ quality.dimensions?.commentLinkage || 0 }}%</strong></div>
        <div class="quality-dimension"><span>数据时效性</span><strong>{{ quality.dimensions?.timeliness || 0 }}%</strong></div>
      </div>
      <div class="quality-issues">
        <table class="quality-issues-table">
          <colgroup>
            <col class="quality-col-name" />
            <col class="quality-col-level" />
            <col class="quality-col-description" />
            <col />
          </colgroup>
          <thead><tr><th>异常项目</th><th>级别</th><th>数量/情况</th><th>处理建议</th></tr></thead>
          <tbody>
            <tr v-for="issue in quality.issues || []" :key="issue.code">
              <td class="font-medium text-slate-800">{{ issue.name }}</td>
              <td><span :class="['badge', severityClass(issue.severity)]">{{ issue.severity }}</span></td>
              <td class="text-slate-600">{{ issue.description }}</td>
              <td class="text-slate-600">{{ issue.suggestion }}</td>
            </tr>
            <tr v-if="!(quality.issues || []).length"><td colspan="4" class="text-center text-emerald-600 py-6">当前未发现数据质量异常</td></tr>
          </tbody>
        </table>
      </div>
      <p class="quality-check-time text-xs text-slate-400">
        最近数据：{{ formatTime(quality.latestDataAt) }} · 本次检查：{{ formatTime(quality.generatedAt) }}
      </p>
    </section>

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

    <!-- 权限与审计 -->
    <div class="grid grid-cols-1 xl:grid-cols-3 gap-5 mt-5">
      <section class="card card-pad">
        <div class="flex items-center justify-between gap-3 mb-4">
          <div>
            <h3 class="section-title">当前账号权限</h3>
            <p class="text-xs text-slate-500 mt-1">关键写操作由后端权限校验</p>
          </div>
          <span class="badge badge-success">{{ accountInfo.role === 'ADMIN' ? '管理员' : accountInfo.role }}</span>
        </div>
        <div class="space-y-2">
          <div v-for="permission in accountInfo.permissions || []" :key="permission" class="flex items-center gap-2 text-sm text-slate-700 border-b border-slate-100 pb-2">
            <AppIcon name="check" :size="15" class="text-emerald-600" /> {{ permissionName(permission) }}
          </div>
        </div>
        <p class="text-xs text-slate-400 mt-4">系统不会在审计记录中保存密码、令牌或请求正文。</p>
      </section>

      <section class="card card-pad xl:col-span-2">
        <div class="flex flex-wrap items-start justify-between gap-3 mb-4">
          <div>
            <h3 class="section-title">关键操作审计</h3>
            <p class="text-xs text-slate-500 mt-1">记录人工复核、事件处置、设置、导入、重分析和清空操作</p>
          </div>
          <div class="flex items-center gap-2">
            <span class="text-xs text-slate-500">24小时 {{ auditSummary.last24Hours || 0 }} 次</span>
            <select v-model="auditAction" class="input !h-9 !py-1 text-xs" @change="loadAudit(1)">
              <option value="">全部操作</option>
              <option value="POST_REVIEW">人工复核</option>
              <option value="POST_BATCH_REVIEW">批量复核</option>
              <option value="EVENT_UPDATE">事件处置</option>
              <option value="SETTINGS_UPDATE">系统设置</option>
              <option value="POST_IMPORT">帖子导入</option>
              <option value="COMMENT_IMPORT">评论导入</option>
              <option value="DATA_REANALYZE">重新分析</option>
              <option value="DATA_CLEAR">清空数据</option>
            </select>
          </div>
        </div>
        <div class="overflow-x-auto border border-slate-200">
          <table class="table w-full">
            <thead><tr><th>时间</th><th>操作人</th><th>操作</th><th>对象</th><th>结果</th></tr></thead>
            <tbody>
              <tr v-for="log in auditLogs" :key="log.id">
                <td class="whitespace-nowrap text-slate-500">{{ formatTime(log.createdAt) }}</td>
                <td>{{ log.operator }}<div class="text-[11px] text-slate-400">{{ log.role }}</div></td>
                <td class="font-medium text-slate-800">{{ log.actionName }}</td>
                <td class="text-slate-600">{{ log.targetType }}<span v-if="log.targetId"> · {{ log.targetId }}</span></td>
                <td><span :class="['badge', log.status === '成功' ? 'badge-success' : 'badge-high']">{{ log.status }}</span></td>
              </tr>
              <tr v-if="!auditLogs.length"><td colspan="5" class="text-center text-slate-400 py-6">暂无关键操作记录</td></tr>
            </tbody>
          </table>
        </div>
        <div class="flex justify-between items-center mt-3 text-xs text-slate-500">
          <span>共 {{ auditTotal }} 条</span>
          <div class="flex gap-2">
            <button class="btn btn-ghost !px-3 !py-1.5 text-xs" :disabled="auditPage <= 1" @click="loadAudit(auditPage - 1)">上一页</button>
            <button class="btn btn-ghost !px-3 !py-1.5 text-xs" :disabled="auditPage * 10 >= auditTotal" @click="loadAudit(auditPage + 1)">下一页</button>
          </div>
        </div>
      </section>
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
            <div class="text-2xl font-semibold text-slate-800">{{ stats.mediumHighRiskPosts || 0 }}</div>
            <div class="text-xs text-slate-500">中高风险标签</div>
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

    <!-- 可视化导入 -->
    <div class="card card-pad mt-5">
      <div class="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h3 class="section-title">JSON 数据导入</h3>
          <p class="text-sm text-slate-500 mt-1">先校验并预览数据，确认无误后执行增量导入。</p>
        </div>
        <div class="seg" role="group" aria-label="导入数据类型">
          <button type="button" :class="{ active: importKind === 'posts' }" @click="importKind = 'posts'; resetImport()">帖子</button>
          <button type="button" :class="{ active: importKind === 'comments' }" @click="importKind = 'comments'; resetImport()">评论</button>
        </div>
      </div>

      <div class="mt-5 border-2 border-dashed border-slate-200 bg-slate-50/70 p-6 text-center">
        <input id="json-import-file" type="file" accept=".json,application/json" class="sr-only" @change="handleImportFile" />
        <label for="json-import-file" class="btn btn-ghost inline-flex items-center gap-2 cursor-pointer">
          <AppIcon name="upload" :size="16" /> 选择 JSON 文件
        </label>
        <p class="text-xs text-slate-500 mt-3">
          {{ importKind === 'posts' ? '帖子数据需包含 id 字段' : '评论数据需包含 comment_id 字段' }}
        </p>
      </div>

      <div v-if="importError" class="mt-4 border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700" role="alert">
        {{ importError }}
      </div>

      <div v-if="importRows.length" class="mt-5">
        <div class="flex flex-wrap items-center justify-between gap-3">
          <div>
            <div class="font-medium text-slate-800">{{ importFileName }}</div>
          <div class="text-sm text-emerald-700 mt-1">格式校验通过，共 {{ importRows.length }} 条数据</div>
          <div v-if="importDuplicateCount" class="text-xs text-amber-700 mt-1">
            检测到 {{ importDuplicateCount }} 条重复 ID，导入时会安全合并，不会重复新增。
          </div>
          </div>
          <button class="btn btn-ghost" type="button" @click="resetImport">重新选择</button>
        </div>
        <div class="overflow-x-auto mt-4 border border-slate-200">
          <table class="table-base w-full text-xs">
            <thead>
              <tr>
                <th>序号</th>
                <th v-for="key in Object.keys(importPreview[0] || {}).slice(0, 5)" :key="key">{{ key }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(row, index) in importPreview" :key="index">
                <td>{{ index + 1 }}</td>
                <td v-for="key in Object.keys(importPreview[0] || {}).slice(0, 5)" :key="key" class="max-w-56 truncate">
                  {{ typeof row[key] === 'object' ? JSON.stringify(row[key]) : row[key] }}
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <p v-if="importRows.length > 5" class="text-xs text-slate-400 mt-2">仅预览前 5 条，导入时将处理全部数据。</p>
        <div class="flex justify-end mt-4">
          <button class="btn btn-primary min-w-32" type="button" :disabled="importing" @click="submitImport">
            {{ importing ? `正在导入 ${importRows.length} 条...` : `确认导入 ${importRows.length} 条` }}
          </button>
        </div>
      </div>

      <div v-if="importResult" class="mt-5 border border-emerald-200 bg-emerald-50 p-4">
        <div class="font-medium text-emerald-800">导入完成</div>
        <div class="grid grid-cols-2 sm:grid-cols-4 lg:grid-cols-6 gap-3 mt-3 text-sm">
          <div>总数：<strong>{{ importResult.total ?? importRows.length }}</strong></div>
          <div>新增：<strong>{{ importResult.imported ?? 0 }}</strong></div>
          <div>更新：<strong>{{ importResult.updated ?? 0 }}</strong></div>
          <div>跳过：<strong>{{ importResult.skipped ?? 0 }}</strong></div>
          <div>错误：<strong>{{ importResult.errors ?? 0 }}</strong></div>
          <div>合并重复：<strong>{{ importResult.duplicatesMerged ?? 0 }}</strong></div>
        </div>
        <p v-if="importResult.unmatched" class="text-xs text-amber-700 mt-3">
          {{ importResult.unmatched }} 条评论暂未找到对应帖子，数据已保留；以后导入对应帖子并重新分析即可自动关联。
        </p>
        <p v-if="importResult.reanalysisScheduled" class="text-xs text-brand-700 mt-3">
          评论已保存，分析任务正在后台运行；即使离开本页，已导入数据也不会丢失。
        </p>
      </div>
    </div>

    <!-- 操作按钮 -->
    <div class="card card-pad mt-5">
      <h3 class="section-title mb-4">数据操作</h3>
      <div class="flex flex-wrap gap-3">
        <button
          @click="handleReanalyze"
          :disabled="reanalysisJob.status === 'RUNNING'"
          class="btn btn-primary inline-flex items-center gap-2"
        >
          <AppIcon name="refresh-cw" :size="16" />
          {{ reanalysisJob.mode === 'EXTERNAL_CLASSIFIED' ? '刷新事件聚合' : '重新分析所有数据' }}
        </button>
        <button @click="handleClear" class="btn inline-flex items-center gap-2 text-rose-600 border-rose-200 hover:bg-rose-50">
          <AppIcon name="trash-2" :size="16" /> 清空所有数据
        </button>
      </div>
      <div v-if="reanalysisJob.status === 'RUNNING'" class="mt-4 border border-brand-100 bg-brand-50 p-4">
        <div class="flex items-center justify-between text-sm">
          <span class="font-medium text-brand-800">{{ reanalysisJob.message }}</span>
          <span class="text-brand-700">{{ reanalysisJob.progress || 0 }}%</span>
        </div>
        <div class="h-2 bg-white mt-3 overflow-hidden" role="progressbar" :aria-valuenow="reanalysisJob.progress || 0" aria-valuemin="0" aria-valuemax="100">
          <div class="h-full bg-brand-500 transition-all duration-500" :style="{ width: `${reanalysisJob.progress || 0}%` }"></div>
        </div>
      </div>
      <div class="mt-4 text-xs text-slate-400 bg-slate-50/70 p-3 border border-slate-100">
        <strong>提示：</strong>
        <ul class="list-disc list-inside mt-1 space-y-1">
          <li v-if="reanalysisJob.mode === 'EXTERNAL_CLASSIFIED'">当前采用外部最终分类；刷新操作只更新事件聚合，不会覆盖现有分类和人工复核结果</li>
          <li v-else>重新分析会对所有帖子重新进行分类、情绪识别和风险评分，适用于更新关键词规则后</li>
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

<style scoped>
.quality-dimension {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 62px;
  gap: 16px;
  padding: 16px 18px;
  border: 1px solid #e2e8f0;
  background: #f8fafc;
}
.quality-dimension span { color: #64748b; font-size: 14px; line-height: 20px; }
.quality-dimension strong { flex: 0 0 auto; color: #1e3a8a; font-size: 18px; line-height: 24px; }

.quality-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 20px;
}

.quality-score {
  flex: 0 0 auto;
  min-width: 132px;
  padding: 12px 16px;
  border-width: 1px;
  text-align: right;
}

.quality-issues {
  margin-top: 16px;
  overflow-x: auto;
  border: 1px solid #e2e8f0;
}

.quality-issues-table {
  width: 100%;
  min-width: 860px;
  border-collapse: collapse;
  table-layout: fixed;
}

.quality-issues-table th,
.quality-issues-table td {
  padding: 12px 16px;
  border-bottom: 1px solid #e2e8f0;
  text-align: left;
  vertical-align: top;
  line-height: 1.55;
}

.quality-issues-table th {
  background: #f8fafc;
  color: #475569;
  font-size: 14px;
  font-weight: 600;
  white-space: nowrap;
}

.quality-issues-table td {
  font-size: 14px;
}

.quality-issues-table tbody tr:last-child td {
  border-bottom: 0;
}

.quality-col-name { width: 15%; }
.quality-col-level { width: 9%; }
.quality-col-description { width: 24%; }

.quality-check-time {
  margin-top: 12px;
  line-height: 20px;
}

@media (max-width: 640px) {
  .quality-header {
    flex-direction: column;
  }

  .quality-score {
    width: 100%;
    text-align: left;
  }
}
</style>
