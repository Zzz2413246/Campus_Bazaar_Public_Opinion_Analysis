<script setup lang="ts">
import { computed, ref, onMounted } from 'vue'
import AppIcon from '../components/AppIcon.vue'
import LoadingSpinner from '@/components/LoadingSpinner.vue'
import EmptyState from '@/components/EmptyState.vue'
import ErrorState from '@/components/ErrorState.vue'
import ReportDocument from '@/components/ReportDocument.vue'
import { reportApi } from '@/utils/api'
import { toast } from '@/utils/toast'

const reportType = ref<'daily' | 'weekly' | 'event'>('daily')

const types = [
  { k: 'daily', v: '日报' },
  { k: 'weekly', v: '周报' },
  { k: 'event', v: '事件简报' },
]

const loading = ref(false)
const loadError = ref('')
const previewError = ref('')
const previewLoading = ref(false)
const exportingPdf = ref(false)
const reportPreviewRef = ref<HTMLElement | null>(null)
const reportDetail = ref<any>(null)

type ReportItem = {
  key: string
  date: string
  title: string
  newPosts: number
  events: number
  highRisk: number
  status: string
}

const reports = ref<ReportItem[]>([])
const previewContent = ref('')
const reportSearch = ref('')
const dateStart = ref('')
const dateEnd = ref('')
const generating = ref(false)
const generationHistory = ref<Array<{ time: string; status: string; message: string }>>([])

const selectedReport = ref<ReportItem | null>(null)
const filteredReports = computed(() => {
  const keyword = reportSearch.value.trim().toLowerCase()
  return reports.value.filter((report) => {
    if (keyword && !`${report.title} ${report.date}`.toLowerCase().includes(keyword)) return false
    if (dateStart.value && report.date < dateStart.value) return false
    if (dateEnd.value && report.date > dateEnd.value) return false
    return true
  })
})
const generateLabel = computed(() =>
  reportType.value === 'weekly' ? '生成本周周报' : reportType.value === 'event' ? '刷新事件简报' : '生成今日日报'
)

function unwrap(res: any) {
  if (res && typeof res === 'object' && (res.code !== undefined || res.success !== undefined) && res.data !== undefined) return res.data
  return res
}

async function selectReport(r: ReportItem) {
  selectedReport.value = r
  reportDetail.value = null
  previewError.value = ''
  previewLoading.value = true
  try {
    const res: any = await reportApi.detail(r.key ?? r.date, reportType.value)
    const d = unwrap(res)
    if (typeof d === 'string' && d.trim()) {
      previewContent.value = d
    } else if (d && typeof d === 'object') {
      reportDetail.value = d
      if (typeof d.content === 'string') previewContent.value = d.content
      else if (typeof d.report === 'string') previewContent.value = d.report
      else if (typeof d.text === 'string') previewContent.value = d.text
    }
  } catch (err) {
    console.warn('报告详情加载失败', err)
    previewContent.value = ''
    previewError.value = '报告详情暂时无法获取，请重试。'
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
    generating.value = true
    previewError.value = ''
    previewLoading.value = true
    const res: any = await reportApi.detail(key, reportType.value)
    const d = unwrap(res)
    if (d && typeof d === 'object') {
      reportDetail.value = d
      if (typeof d.content === 'string') previewContent.value = d.content
      if (d.title && selectedReport.value) selectedReport.value.title = d.title
      generationHistory.value.unshift({
        time: new Date().toLocaleString('zh-CN'),
        status: '成功',
        message: d.title || generateLabel.value,
      })
      toast.success('报告已更新')
    }
  } catch (err) {
    console.warn('生成报告失败', err)
    previewError.value = '报告生成失败，请稍后重试。'
    generationHistory.value.unshift({
      time: new Date().toLocaleString('zh-CN'),
      status: '失败',
      message: generateLabel.value,
    })
    toast.error('报告生成失败，请稍后重试')
  } finally {
    generating.value = false
    previewLoading.value = false
  }
}

async function loadReports() {
  loading.value = true
  loadError.value = ''
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
      reportDetail.value = null
      previewContent.value = '暂无该类型报告'
    }
  } catch (err) {
    console.warn('报告列表加载失败', err)
    reports.value = []
    selectedReport.value = null
    loadError.value = '报告列表暂时无法获取，请检查服务连接后重试。'
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
  try {
    const pages = Array.from(reportPreviewRef.value?.querySelectorAll<HTMLElement>('.report-pdf-page') || [])
    if (!pages.length) throw new Error('暂无可导出的报告内容')
    const [{ default: html2canvas }, { jsPDF }] = await Promise.all([
      import('html2canvas'),
      import('jspdf'),
    ])
    await document.fonts.ready
    const pdf = new jsPDF({ orientation: 'portrait', unit: 'mm', format: 'a4' })
    for (let index = 0; index < pages.length; index += 1) {
      const canvas = await html2canvas(pages[index], {
        scale: 2,
        backgroundColor: '#ffffff',
        useCORS: true,
        logging: false,
      })
      if (index > 0) pdf.addPage('a4', 'portrait')
      pdf.addImage(canvas.toDataURL('image/jpeg', 0.96), 'JPEG', 0, 0, 210, 297)
    }

    pdf.save(`${safeFilename(selectedReport.value.title)}.pdf`)
    toast.success('PDF 已导出')
  } catch (err) {
    console.error('PDF 导出失败', err)
    toast.error('PDF 导出失败，请稍后重试')
  } finally {
    exportingPdf.value = false
  }
}

function exportWord() {
  const content = reportPreviewRef.value?.innerHTML
  if (!content) {
    toast.error('暂无可导出的报告内容')
    return
  }
  const wordStyles = `
    @page { size: A4 portrait; margin: 16mm 15mm 16mm; }
    body { margin: 0; color: #1e293b; font-family: "Microsoft YaHei", Arial, sans-serif; }
    .report-document { width: 100%; }
    .report-pdf-page { position: relative; box-sizing: border-box; min-height: 260mm; page-break-after: always; }
    .report-pdf-page:last-child { page-break-after: auto; }
    .report-pdf-page:before { content: ""; display: block; height: 2mm; margin-bottom: 8mm; background: #1d4ed8; }
    .report-letterhead, .report-brand, .report-signoff div { display: flex; align-items: center; justify-content: space-between; }
    .report-brand { justify-content: flex-start; gap: 8pt; }
    .report-logo { display: inline-block; color: #1d4ed8; }
    .report-institution { color: #0f172a; font-size: 12pt; font-weight: bold; letter-spacing: 1pt; }
    .report-brand-sub, .report-running-title { color: #64748b; font-size: 7pt; }
    .report-confidentiality { padding: 3pt 7pt; border: 1pt solid #fca5a5; color: #b91c1c; font-size: 9pt; font-weight: bold; }
    .report-title-area { padding: 20pt 0 16pt; text-align: center; }
    .report-title-area .report-type { color: #2563eb; font-size: 10pt; font-weight: bold; letter-spacing: 2pt; }
    .report-title-area h1 { margin: 8pt 0 5pt; color: #0f172a; font-size: 21pt; }
    .report-title-area p { margin: 0; color: #64748b; font-size: 10pt; }
    .report-meta-grid { width: 100%; margin-bottom: 15pt; border-collapse: collapse; }
    .report-meta-grid div { display: inline-block; box-sizing: border-box; width: 49%; padding: 6pt; border: 1pt solid #cbd5e1; font-size: 8pt; }
    .report-meta-grid dt, .report-meta-grid dd { display: inline; margin: 0; }
    .report-meta-grid dt { margin-right: 10pt; color: #64748b; }.report-meta-grid dd { font-weight: bold; }
    .report-section { margin-top: 15pt; }.report-section h2 { margin: 0 0 7pt; color: #0f172a; font-size: 12pt; }
    .report-section h2 span { margin-right: 7pt; padding: 3pt 5pt; background: #1d4ed8; color: white; font-size: 8pt; }
    .report-summary-grid, .report-emotion-grid { display: table; width: 100%; table-layout: fixed; border-collapse: collapse; }
    .report-summary-grid div, .report-emotion-grid div { display: table-cell; padding: 8pt; border: 1pt solid #dbeafe; text-align: center; }
    .report-summary-grid strong, .report-summary-grid span, .report-emotion-grid span, .report-emotion-grid strong, .report-emotion-grid small { display: block; }
    .report-summary-grid strong { color: #1e3a8a; font-size: 16pt; }.report-summary-grid span { color: #64748b; font-size: 8pt; }
    .report-paragraph { padding: 8pt; border-left: 3pt solid #3b82f6; background: #f8fafc; font-size: 9pt; line-height: 1.7; }
    .report-table { width: 100%; border-collapse: collapse; table-layout: fixed; }
    .report-table th, .report-table td { padding: 5pt; border: 1pt solid #cbd5e1; font-size: 8pt; text-align: left; }
    .report-table th { background: #eff6ff; color: #1e3a8a; }.report-event-table td small { display: block; color: #64748b; }
    .report-risk { padding: 2pt 4pt; font-weight: bold; }.report-risk-high { color: #be123c; }.report-risk-medium { color: #b45309; }.report-risk-low { color: #15803d; }
    .report-discussions { padding-left: 18pt; font-size: 8pt; }.report-discussions li { margin-bottom: 5pt; }.report-discussions small { float: right; color: #64748b; }
    .report-recommendation { padding: 9pt; border: 1pt solid #fde68a; background: #fffbeb; color: #78350f; font-size: 9pt; }
    .report-recommendation-title { font-weight: bold; }.report-signoff { margin-top: 14pt; padding: 9pt; background: #f8fafc; color: #64748b; font-size: 8pt; }
    .report-footer { margin-top: 16pt; padding-top: 6pt; border-top: 1pt solid #cbd5e1; color: #94a3b8; font-size: 7pt; text-align: right; }
  `
  const html = `<!doctype html><html><head><meta charset="utf-8"><title>${safeFilename(selectedReport.value?.title || '报告')}</title><style>${wordStyles}</style></head><body>${content}</body></html>`
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
    <ErrorState
      v-else-if="loadError"
      title="报告中心加载失败"
      :message="loadError"
      @retry="loadReports"
    />
    <div class="flex items-center gap-3 flex-wrap">
      <div class="seg">
        <button
          v-for="r in types"
          :key="r.k"
          type="button"
          @click="selectType(r.k as 'daily' | 'weekly' | 'event')"
          :class="['seg-item', reportType === r.k ? 'seg-item-active' : '']"
        >{{ r.v }}</button>
      </div>
      <div class="flex-1"></div>
      <button class="btn btn-primary" :disabled="generating" @click="generateReport">
        <AppIcon name="plus" :size="16" /> {{ generating ? '生成中...' : generateLabel }}
      </button>
    </div>

    <div class="card card-pad flex flex-wrap items-end gap-3">
      <label class="flex-1 min-w-56">
        <span class="text-xs text-slate-500 block mb-1.5">搜索报告</span>
        <input v-model="reportSearch" class="input w-full" placeholder="输入报告标题或日期" />
      </label>
      <label>
        <span class="text-xs text-slate-500 block mb-1.5">开始日期</span>
        <input v-model="dateStart" type="date" class="input" :max="dateEnd || undefined" />
      </label>
      <label>
        <span class="text-xs text-slate-500 block mb-1.5">结束日期</span>
        <input v-model="dateEnd" type="date" class="input" :min="dateStart || undefined" />
      </label>
      <button class="btn btn-ghost" type="button" @click="reportSearch = ''; dateStart = ''; dateEnd = ''">清除筛选</button>
      <span class="text-sm text-slate-500 ml-auto">显示 {{ filteredReports.length }} / {{ reports.length }} 份</span>
    </div>

    <div class="grid grid-cols-1 lg:grid-cols-3 gap-5">
      <!-- 报告列表 -->
      <div class="space-y-3">
        <EmptyState v-if="!filteredReports.length" text="暂无符合条件的报告" hint="尝试调整搜索词或日期范围" />
        <div
          v-for="r in filteredReports"
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
        <div class="report-preview-stage relative">
          <div v-if="previewLoading" class="absolute inset-0 flex items-center justify-center bg-white/60 text-sm text-slate-400">
            <span class="w-4 h-4 border-2 border-slate-300 border-t-brand-500 rounded-full animate-spin mr-2"></span> 报告加载中...
          </div>
          <ErrorState
            v-if="previewError"
            title="报告预览加载失败"
            :message="previewError"
            @retry="selectedReport && selectReport(selectedReport)"
          />
          <div v-else ref="reportPreviewRef">
            <ReportDocument
              v-if="selectedReport && reportDetail"
              :title="selectedReport.title"
              :detail="reportDetail"
            />
            <EmptyState v-else text="暂无可预览的报告内容" />
          </div>
        </div>
      </div>
    </div>

    <section v-if="generationHistory.length" class="card card-pad">
      <h3 class="section-title">本次操作记录</h3>
      <div class="space-y-2 mt-3">
        <div v-for="(item, index) in generationHistory" :key="`${item.time}-${index}`" class="flex items-center gap-3 text-sm border-b border-slate-100 pb-2">
          <span :class="['badge', item.status === '成功' ? 'badge-success' : 'badge-high']">{{ item.status }}</span>
          <span class="text-slate-700">{{ item.message }}</span>
          <span class="text-slate-400 ml-auto">{{ item.time }}</span>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.report-preview-stage {
  min-height: 520px;
  padding: 24px;
  overflow: auto;
  border: 1px solid #e2e8f0;
  background: #e8edf4;
}
</style>
