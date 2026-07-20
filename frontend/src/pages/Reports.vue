<script setup lang="ts">
import { computed, ref, onMounted } from 'vue'
import AppIcon from '../components/AppIcon.vue'
import LoadingSpinner from '@/components/LoadingSpinner.vue'
import EmptyState from '@/components/EmptyState.vue'
import { reportApi } from '@/utils/api'
import { toast } from '@/utils/toast'

const reportType = ref<'daily' | 'weekly' | 'event'>('daily')

const types = [
  { k: 'daily', v: '日报' },
  { k: 'weekly', v: '周报' },
  { k: 'event', v: '事件简报' },
]

const loading = ref(false)
const previewLoading = ref(false)
const exportingPdf = ref(false)

type ReportItem = {
  key: string
  date: string
  title: string
  newPosts: number
  events: number
  highRisk: number
  status: string
}

const reports = ref<ReportItem[]>([
  { key: '2026-07-14', date: '2026-07-14', title: '7月14日 校园安全日报', newPosts: 1245, events: 3, highRisk: 0, status: '已生成' },
  { key: '2026-07-13', date: '2026-07-13', title: '7月13日 校园安全日报', newPosts: 1102, events: 5, highRisk: 2, status: '已生成' },
  { key: '2026-07-12', date: '2026-07-12', title: '7月12日 校园安全日报', newPosts: 987, events: 4, highRisk: 1, status: '已生成' },
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

const selectedReport = ref<ReportItem | null>(reports.value[0])
const generateLabel = computed(() =>
  reportType.value === 'weekly' ? '生成本周周报' : reportType.value === 'event' ? '刷新事件简报' : '生成今日日报'
)

function unwrap(res: any) {
  if (res && typeof res === 'object' && (res.code !== undefined || res.success !== undefined) && res.data !== undefined) return res.data
  return res
}

async function selectReport(r: ReportItem) {
  selectedReport.value = r
  previewLoading.value = true
  try {
    const res: any = await reportApi.detail(r.key ?? r.date, reportType.value)
    const d = unwrap(res)
    if (typeof d === 'string' && d.trim()) {
      previewContent.value = d
    } else if (d && typeof d === 'object') {
      if (typeof d.content === 'string') previewContent.value = d.content
      else if (typeof d.report === 'string') previewContent.value = d.report
      else if (typeof d.text === 'string') previewContent.value = d.text
    }
  } catch (err) {
    console.warn('报告详情加载失败，使用默认内容', err)
  } finally {
    previewLoading.value = false
  }
}

// 生成新报告：调用后端获取今天的报告详情并更新预览
async function generateReport() {
  const today = new Date().toISOString().slice(0, 10)
  let key = today
  if (reportType.value === 'weekly') {
    const date = new Date()
    const day = date.getDay() || 7
    date.setDate(date.getDate() - day + 1)
    key = date.toISOString().slice(0, 10)
  } else if (reportType.value === 'event') {
    const eventKey = selectedReport.value?.key ?? selectedReport.value?.date
    if (!eventKey) {
      toast.error('暂无可生成的事件简报')
      return
    }
    key = eventKey
  }
  try {
    const res: any = await reportApi.detail(key, reportType.value)
    const d = unwrap(res)
    if (d && typeof d === 'object' && typeof d.content === 'string') {
      previewContent.value = d.content
      if (d.title && selectedReport.value) selectedReport.value.title = d.title
      toast.success('报告已更新')
    }
  } catch (err) {
    console.warn('生成报告失败', err)
  }
}

async function loadReports() {
  loading.value = true
  try {
    const res: any = await reportApi.list(reportType.value)
    const d = unwrap(res)
    reports.value = Array.isArray(d) ? d.map((r: any) => ({
      key: r.key ?? r.id ?? r.date ?? '',
      date: r.date ?? r.periodStart ?? '',
      title: r.title ?? r.name ?? '',
      newPosts: r.newPosts ?? r.posts ?? 0,
      events: r.events ?? r.eventCount ?? 0,
      highRisk: r.highRisk ?? r.highRiskCount ?? 0,
      status: r.status ?? '已生成',
    })) : []
    if (reports.value.length) {
      selectedReport.value = reports.value[0]
      await selectReport(reports.value[0])
    } else {
      selectedReport.value = null
      previewContent.value = '暂无该类型报告'
    }
  } catch (err) {
    console.warn('报告列表加载失败', err)
    reports.value = []
  } finally {
    loading.value = false
  }
}

function selectType(type: 'daily' | 'weekly' | 'event') {
  if (reportType.value === type) return
  reportType.value = type
  loadReports()
}

// 复制文本到剪贴板
async function copyText() {
  try {
    await navigator.clipboard.writeText(previewContent.value)
    toast.success('报告文本已复制到剪贴板')
  } catch (err) {
    console.warn('复制失败', err)
    alert('复制失败，请手动选择文本复制')
  }
}

function safeFilename(name: string) {
  return name.replace(/[\\/:*?"<>|]/g, '_').trim() || '校园安全舆情报告'
}

async function exportPdf() {
  if (!selectedReport.value || exportingPdf.value) return

  exportingPdf.value = true
  const report = document.createElement('article')
  report.setAttribute('aria-hidden', 'true')
  Object.assign(report.style, {
    position: 'fixed',
    left: '-10000px',
    top: '0',
    width: '794px',
    padding: '64px 68px',
    background: '#ffffff',
    color: '#1e293b',
    fontFamily: '"Microsoft YaHei", "PingFang SC", Arial, sans-serif',
    fontSize: '16px',
    lineHeight: '1.85',
    whiteSpace: 'pre-wrap',
    overflowWrap: 'break-word',
  })

  const title = document.createElement('h1')
  title.textContent = selectedReport.value.title
  Object.assign(title.style, {
    margin: '0 0 28px',
    paddingBottom: '18px',
    borderBottom: '2px solid #2563eb',
    color: '#0f172a',
    fontSize: '26px',
    lineHeight: '1.4',
    fontWeight: '700',
  })

  const content = document.createElement('div')
  content.textContent = previewContent.value
  report.append(title, content)
  document.body.appendChild(report)

  try {
    const [{ default: html2canvas }, { jsPDF }] = await Promise.all([
      import('html2canvas'),
      import('jspdf'),
    ])
    await document.fonts.ready
    const canvas = await html2canvas(report, {
      scale: 2,
      backgroundColor: '#ffffff',
      useCORS: true,
      logging: false,
    })

    const pdf = new jsPDF({ orientation: 'portrait', unit: 'mm', format: 'a4' })
    const margin = 12
    const contentWidth = 210 - margin * 2
    const contentHeight = 297 - margin * 2
    const pageSliceHeight = Math.floor(canvas.width * contentHeight / contentWidth)
    let sourceY = 0
    let pageIndex = 0

    while (sourceY < canvas.height) {
      const sliceHeight = Math.min(pageSliceHeight, canvas.height - sourceY)
      const pageCanvas = document.createElement('canvas')
      pageCanvas.width = canvas.width
      pageCanvas.height = sliceHeight
      const context = pageCanvas.getContext('2d')
      if (!context) throw new Error('无法创建 PDF 画布')
      context.fillStyle = '#ffffff'
      context.fillRect(0, 0, pageCanvas.width, pageCanvas.height)
      context.drawImage(
        canvas,
        0, sourceY, canvas.width, sliceHeight,
        0, 0, canvas.width, sliceHeight,
      )

      if (pageIndex > 0) pdf.addPage()
      const imageHeight = sliceHeight * contentWidth / canvas.width
      pdf.addImage(pageCanvas.toDataURL('image/jpeg', 0.95), 'JPEG', margin, margin, contentWidth, imageHeight)
      sourceY += sliceHeight
      pageIndex += 1
    }

    pdf.save(`${safeFilename(selectedReport.value.title)}.pdf`)
    toast.success('PDF 已导出')
  } catch (err) {
    console.error('PDF 导出失败', err)
    toast.error('PDF 导出失败，请稍后重试')
  } finally {
    report.remove()
    exportingPdf.value = false
  }
}

function exportWord() {
  const escapedContent = previewContent.value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
  const html = `<!doctype html><html><head><meta charset="utf-8"><title>${safeFilename(selectedReport.value?.title || '报告')}</title></head><body><pre style="font-family:Microsoft YaHei,Arial,sans-serif;white-space:pre-wrap;line-height:1.8">${escapedContent}</pre></body></html>`
  const blob = new Blob(['\ufeff', html], { type: 'application/msword;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `${safeFilename(selectedReport.value?.title || '报告')}.doc`
  a.click()
  URL.revokeObjectURL(url)
  toast.success('Word 已导出')
}

function exportFile(format: 'pdf' | 'word') {
  if (format === 'pdf') {
    void exportPdf()
  } else {
    exportWord()
  }
}

onMounted(loadReports)
</script>

<template>
  <div class="page large-detail-page">
    <!-- 加载状态 -->
    <LoadingSpinner v-if="loading" />
    <div class="flex items-center gap-3 flex-wrap">
      <div class="seg">
        <span
          v-for="r in types"
          :key="r.k"
          @click="selectType(r.k as 'daily' | 'weekly' | 'event')"
          :class="['seg-item', reportType === r.k ? 'seg-item-active' : '']"
        >{{ r.v }}</span>
      </div>
      <div class="flex-1"></div>
      <button class="btn btn-primary" @click="generateReport"><AppIcon name="plus" :size="16" /> {{ generateLabel }}</button>
    </div>

    <div class="grid grid-cols-1 lg:grid-cols-3 gap-5">
      <!-- 报告列表 -->
      <div class="space-y-3">
        <EmptyState v-if="!reports.length" text="暂无该类型报告" />
        <div
          v-for="r in reports"
          :key="r.key ?? r.date"
          class="card card-pad card-hover cursor-pointer"
          :class="selectedReport?.key === r.key ? 'ring-2 ring-brand-200' : ''"
          @click="selectReport(r)"
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
            <p class="section-sub mt-0.5 truncate">{{ selectedReport?.title || '暂无报告' }}</p>
          </div>
          <div class="flex gap-2 flex-wrap">
            <button class="btn btn-ghost !py-1.5 !px-3 text-xs" @click="copyText"><AppIcon name="copy" :size="14" /> 复制文本</button>
            <button
              class="btn btn-ghost !py-1.5 !px-3 text-xs"
              :disabled="exportingPdf"
              :class="{ 'opacity-60 cursor-wait': exportingPdf }"
              @click="exportFile('pdf')"
            >
              <AppIcon name="download" :size="14" />
              {{ exportingPdf ? '正在生成...' : '导出 PDF' }}
            </button>
            <button class="btn btn-ghost !py-1.5 !px-3 text-xs" @click="exportFile('word')"><AppIcon name="download" :size="14" /> 导出 Word</button>
          </div>
        </div>
        <div class="bg-slate-50/70 p-6 border border-slate-100 overflow-x-auto relative">
          <div v-if="previewLoading" class="absolute inset-0 flex items-center justify-center bg-white/60 text-sm text-slate-400">
            <span class="w-4 h-4 border-2 border-slate-300 border-t-brand-500 rounded-full animate-spin mr-2"></span> 报告加载中...
          </div>
          <pre class="text-sm text-slate-700 whitespace-pre-wrap font-sans leading-relaxed">{{ previewContent }}</pre>
        </div>
      </div>
    </div>
  </div>
</template>
