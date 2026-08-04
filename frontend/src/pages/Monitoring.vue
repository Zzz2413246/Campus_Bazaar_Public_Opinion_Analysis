<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch, computed } from 'vue'
import AppIcon from '../components/AppIcon.vue'
import LoadingSpinner from '@/components/LoadingSpinner.vue'
import EmptyState from '@/components/EmptyState.vue'
import { postApi, settingsApi } from '@/utils/api'
import { toast } from '@/utils/toast'
import { useRoute, useRouter } from 'vue-router'
import { postCategoryOptions } from '@/utils/safetyCategories'

const router = useRouter()
const route = useRoute()
const loading = ref(false)
const searchKeyword = ref(String(route.query.keyword || ''))
const filterCategory = ref(String(route.query.category || ''))
const filterEmotion = ref(String(route.query.emotion || ''))
const filterSource = ref(String(route.query.source || ''))
const filterReviewStatus = ref(
  ['待复核', '已确认', '已修正', '无关内容'].includes(String(route.query.reviewStatus || ''))
    ? String(route.query.reviewStatus) : '',
)
const initialSort = String(route.query.sortBy || 'latest')
const sortBy = ref<'latest' | 'risk' | 'heat'>(
  initialSort === 'risk' || initialSort === 'heat' ? initialSort : 'latest',
)
const selectedIds = ref<Set<string>>(new Set())
const expandedIds = ref<Set<string>>(new Set())
const batchMode = ref<'confirm' | 'irrelevant' | null>(null)
const batchNote = ref('')
const batchSaving = ref(false)
const presetName = ref('')
const selectedPreset = ref('')

type FilterPreset = {
  name: string
  keyword: string
  category: string
  emotion: string
  source: string
  reviewStatus: string
  sortBy: 'latest' | 'risk' | 'heat'
}

const FILTER_PRESETS_KEY = 'yuqing_monitoring_filter_presets'
const savedPresets = ref<FilterPreset[]>([])

const posts = ref<any[]>([])
const total = ref(0)
const page = ref(Math.max(1, Number(route.query.page || 1)))
const size = ref(20)
let requestController: AbortController | null = null
let filterTimer: ReturnType<typeof setTimeout> | null = null
const categoryOptions = ref<string[]>(postCategoryOptions())

function unwrap(res: any) {
  if (res && typeof res === 'object' && (res.code !== undefined || res.success !== undefined) && res.data !== undefined) return res.data
  return res
}

function mapPost(p: any) {
  return {
    ...p,
    id: p.id,
    title: p.title ?? '',
    category: p.safetyCategory ?? p.category ?? '疑似主题无法确定',
    emotion: p.emotion ?? '中性',
    riskLevel: p.riskLevel ?? p.aiRiskLevel ?? '低',
    riskLabelSource: p.riskLabelSource ?? '外部最终分类',
    source: p.source ?? p.categoryName ?? '',
    comments: p.commentCount ?? (typeof p.comments === 'number' ? p.comments : 0),
    likes: p.likeCount ?? p.likes ?? 0,
    views: p.viewCount ?? p.views ?? 0,
    time: p.timeDesc ?? p.publishTime ?? p.time ?? '',
    publishTime: p.publishTime ?? '',
    content: p.content ?? '',
    location: p.location ?? '',
    problem: p.problem ?? '',
    demand: p.demand ?? '',
    analyzedComments: p.analyzedCommentCount ?? 0,
    commentRiskAdjustment: p.commentRiskAdjustment ?? 0,
    commentSignal: p.commentSignal ?? '',
    commentSuggestedCategory: p.commentSuggestedCategory ?? '',
    commentSuggestionCount: p.commentSuggestionCount ?? 0,
    analysisBasis: p.analysisBasis ?? '原帖文本',
    screeningLabel: p.screeningLabel ?? '',
    analysisReason: p.analysisReason ?? '',
    discussionSummary: p.discussionSummary ?? '',
    reviewStatus: p.reviewStatus ?? '待复核',
    reviewer: p.reviewer ?? '',
    reviewedAt: p.reviewedAt ?? '',
    reviewNote: p.reviewNote ?? '',
    commentsData: p.comments && typeof p.comments === 'object' ? p.comments : null,
  }
}

async function loadPosts() {
  requestController?.abort()
  requestController = new AbortController()
  const controller = requestController
  loading.value = true
  try {
    const res: any = await postApi.list({
      keyword: searchKeyword.value || undefined,
      category: filterCategory.value || undefined,
      emotion: filterEmotion.value || undefined,
      source: filterSource.value || undefined,
      reviewStatus: filterReviewStatus.value || undefined,
      sortBy: sortBy.value,
      page: page.value,
      size: size.value,
    }, controller.signal)
    const d = unwrap(res)
    // 后端返回 { total, page, size, data: [...] }
    if (d && typeof d === 'object' && Array.isArray(d.data)) {
      posts.value = d.data.map(mapPost)
      total.value = d.total ?? posts.value.length
    } else if (Array.isArray(d)) {
      posts.value = d.map(mapPost)
      total.value = d.length
    }
  } catch (err) {
    if ((err as any)?.code === 'ERR_CANCELED' || (err as any)?.name === 'CanceledError') return
    console.warn('帖子数据加载失败', err)
  } finally {
    if (requestController === controller) loading.value = false
  }
}

function syncFiltersToUrl() {
  const query: Record<string, string> = {}
  if (searchKeyword.value) query.keyword = searchKeyword.value
  if (filterCategory.value) query.category = filterCategory.value
  if (filterEmotion.value) query.emotion = filterEmotion.value
  if (filterSource.value) query.source = filterSource.value
  if (filterReviewStatus.value) query.reviewStatus = filterReviewStatus.value
  if (sortBy.value !== 'latest') query.sortBy = sortBy.value
  if (page.value > 1) query.page = String(page.value)
  router.replace({ query })
}

// 筛选条件变化时重置到第1页并重新加载
watch([searchKeyword, filterCategory, filterEmotion, filterSource, filterReviewStatus, sortBy], () => {
  page.value = 1
  selectedIds.value = new Set()
  syncFiltersToUrl()
  if (filterTimer) clearTimeout(filterTimer)
  filterTimer = setTimeout(loadPosts, 300)
})

function goPage(p: number) {
  if (p < 1 || p > totalPages.value) return
  page.value = p
  syncFiltersToUrl()
  loadPosts()
}

function openPost(id: string) {
  router.push(`/monitoring/${id}`)
}

function toggleExpanded(id: string) {
  const next = new Set(expandedIds.value)
  next.has(id) ? next.delete(id) : next.add(id)
  expandedIds.value = next
}

function toggleSelected(id: string) {
  const next = new Set(selectedIds.value)
  next.has(id) ? next.delete(id) : next.add(id)
  selectedIds.value = next
}

const allPageSelected = computed(() =>
  posts.value.length > 0 && posts.value.every((post) => selectedIds.value.has(post.id)),
)

function toggleSelectPage() {
  const next = new Set(selectedIds.value)
  if (allPageSelected.value) {
    posts.value.forEach((post) => next.delete(post.id))
  } else {
    posts.value.forEach((post) => next.add(post.id))
  }
  selectedIds.value = next
}

function startBatch(mode: 'confirm' | 'irrelevant') {
  if (!selectedIds.value.size) return
  batchMode.value = mode
  batchNote.value = ''
}

async function submitBatch() {
  if (!batchMode.value || batchSaving.value) return
  if (batchMode.value === 'irrelevant' && !batchNote.value.trim()) {
    toast.error('请填写标记无关内容的原因')
    return
  }
  batchSaving.value = true
  try {
    const res: any = await postApi.batchReview({
      ids: Array.from(selectedIds.value),
      action: batchMode.value,
      note: batchNote.value.trim(),
      reviewer: localStorage.getItem('yuqing_nickname') || '管理员',
    })
    const d = unwrap(res) || res
    toast.success(`已完成 ${d.updated ?? selectedIds.value.size} 条帖子复核`)
    selectedIds.value = new Set()
    batchMode.value = null
    await loadPosts()
  } catch (err) {
    console.warn('批量复核失败', err)
    toast.error('批量复核失败，请稍后重试')
  } finally {
    batchSaving.value = false
  }
}

function loadSavedPresets() {
  try {
    const parsed = JSON.parse(localStorage.getItem(FILTER_PRESETS_KEY) || '[]')
    savedPresets.value = Array.isArray(parsed) ? parsed : []
  } catch {
    savedPresets.value = []
  }
}

function saveCurrentPreset() {
  const name = presetName.value.trim()
  if (!name) {
    toast.error('请先填写筛选方案名称')
    return
  }
  const preset: FilterPreset = {
    name,
    keyword: searchKeyword.value,
    category: filterCategory.value,
    emotion: filterEmotion.value,
    source: filterSource.value,
    reviewStatus: filterReviewStatus.value,
    sortBy: sortBy.value,
  }
  const next = savedPresets.value.filter((item) => item.name !== name)
  next.push(preset)
  savedPresets.value = next
  localStorage.setItem(FILTER_PRESETS_KEY, JSON.stringify(next))
  selectedPreset.value = name
  presetName.value = ''
  toast.success('筛选方案已保存')
}

function applyPreset() {
  const preset = savedPresets.value.find((item) => item.name === selectedPreset.value)
  if (!preset) return
  searchKeyword.value = preset.keyword
  filterCategory.value = preset.category
  filterEmotion.value = preset.emotion
  filterSource.value = preset.source
  filterReviewStatus.value = preset.reviewStatus
  sortBy.value = preset.sortBy
}

function deletePreset() {
  if (!selectedPreset.value) return
  savedPresets.value = savedPresets.value.filter((item) => item.name !== selectedPreset.value)
  localStorage.setItem(FILTER_PRESETS_KEY, JSON.stringify(savedPresets.value))
  selectedPreset.value = ''
  toast.success('筛选方案已删除')
}

function resetFilters() {
  searchKeyword.value = ''
  filterCategory.value = ''
  filterEmotion.value = ''
  filterSource.value = ''
  filterReviewStatus.value = ''
  sortBy.value = 'latest'
  selectedPreset.value = ''
}

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / size.value)))

// 生成页码数组（最多显示7个）
const pageNumbers = computed(() => {
  const tp = totalPages.value
  const cur = page.value
  if (tp <= 7) return Array.from({ length: tp }, (_, i) => i + 1)
  if (cur <= 4) return [1, 2, 3, 4, 5, '...', tp]
  if (cur >= tp - 3) return [1, '...', tp - 4, tp - 3, tp - 2, tp - 1, tp]
  return [1, '...', cur - 1, cur, cur + 1, '...', tp]
})

async function loadCategories() {
  try {
    const res: any = await settingsApi.get()
    const d = unwrap(res) || {}
    if (Array.isArray(d.categories) && d.categories.length) {
      categoryOptions.value = [...d.categories.map(String), '非安全内容']
    }
  } catch (err) {
    console.warn('分类设置加载失败，使用默认分类', err)
  }
}

onMounted(() => {
  loadSavedPresets()
  loadPosts()
  loadCategories()
})

onUnmounted(() => {
  requestController?.abort()
  if (filterTimer) clearTimeout(filterTimer)
})

const emotionIcon = (e: string) => e.includes('负面') ? 'anger' : e.includes('中性') ? 'meh' : 'smile'
const emotionBadge = (e: string) => e.includes('负面') ? 'badge-high' : e.includes('中性') ? 'badge-neutral' : 'badge-success'
const reviewBadge = (status: string) =>
  status === '待复核' ? 'badge-warn'
    : status === '已修正' ? 'badge-info'
      : status === '已确认' ? 'badge-success'
        : 'badge-neutral'
const riskBadge = (risk: string) =>
  risk === '高' ? 'badge-high' : risk === '中' ? 'badge-warn' : 'badge-success'
</script>

<template>
  <div class="page">
    <!-- 搜索筛选栏 -->
    <div class="card card-pad">
      <div class="flex flex-wrap items-center gap-3">
        <div class="flex-1 relative min-w-[240px]">
          <span class="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400"><AppIcon name="search" :size="16" /></span>
          <input
            v-model="searchKeyword"
            type="text"
            placeholder="搜索关键词、地点、事件..."
            class="input w-full !pl-9"
            @keyup.enter="loadPosts"
          />
        </div>
        <select v-model="filterCategory" class="select">
          <option value="">全部类型</option>
          <option v-for="category in categoryOptions" :key="category" :value="category">{{ category }}</option>
        </select>
        <select v-model="filterEmotion" class="select">
          <option value="">全部情绪</option><option>正面</option><option>中性</option><option>负面</option>
        </select>
        <select v-model="filterSource" class="select">
          <option value="">全部分类</option><option>打听求助</option><option>二手闲置</option><option>恋爱交友</option><option>兼职招聘</option><option>其他</option>
        </select>
        <select v-model="filterReviewStatus" class="select">
          <option value="">全部复核状态</option>
          <option>待复核</option>
          <option>已确认</option>
          <option>已修正</option>
          <option>无关内容</option>
        </select>
        <select v-model="sortBy" class="select">
          <option value="latest">最新发布</option>
          <option value="risk">风险优先</option>
          <option value="heat">热度优先</option>
        </select>
        <span class="text-sm text-slate-500 ml-auto whitespace-nowrap">共 {{ total }} 条</span>
      </div>
      <div class="flex flex-wrap items-center gap-3 mt-4 pt-4 border-t border-slate-100">
        <span class="text-sm font-medium text-slate-600">常用筛选</span>
        <select v-model="selectedPreset" class="select min-w-40" @change="applyPreset">
          <option value="">选择已保存方案</option>
          <option v-for="preset in savedPresets" :key="preset.name" :value="preset.name">{{ preset.name }}</option>
        </select>
        <button class="btn btn-ghost" :disabled="!selectedPreset" @click="deletePreset">删除方案</button>
        <div class="flex-1"></div>
        <input v-model="presetName" class="input min-w-44" placeholder="输入方案名称" @keyup.enter="saveCurrentPreset" />
        <button class="btn btn-ghost" @click="saveCurrentPreset">
          <AppIcon name="bookmark" :size="15" /> 保存当前筛选
        </button>
        <button class="btn btn-ghost" @click="resetFilters">重置</button>
      </div>
    </div>

    <!-- 加载状态 -->
    <LoadingSpinner v-if="loading" />

    <!-- 帖子列表 -->
    <div v-else-if="posts.length" class="space-y-3">
      <div class="card card-pad !py-3 flex items-center gap-3 flex-wrap">
        <label class="inline-flex items-center gap-2 text-sm text-slate-700 cursor-pointer">
          <input type="checkbox" :checked="allPageSelected" @change="toggleSelectPage" />
          本页全选
        </label>
        <span class="text-sm text-slate-500">已选择 {{ selectedIds.size }} 条</span>
        <div class="flex-1"></div>
        <button class="btn btn-ghost" :disabled="!selectedIds.size" @click="startBatch('confirm')">
          批量确认 AI
        </button>
        <button class="btn btn-ghost text-rose-600" :disabled="!selectedIds.size" @click="startBatch('irrelevant')">
          批量标记无关
        </button>
      </div>
      <div
        v-for="post in posts"
        :key="post.id"
        class="card card-pad card-hover cursor-pointer flex gap-4"
        role="button"
        tabindex="0"
        @click="openPost(post.id)"
        @keyup.enter="openPost(post.id)"
      >
        <div class="pt-1" @click.stop>
          <input
            type="checkbox"
            :checked="selectedIds.has(post.id)"
            :aria-label="`选择帖子 ${post.id}`"
            @change="toggleSelected(post.id)"
          />
        </div>
        <div class="min-w-0 flex-1">
          <div class="flex items-center gap-2 mb-2.5 flex-wrap">
            <span :class="['badge', riskBadge(post.riskLevel)]">{{ post.riskLevel }}风险</span>
            <span class="badge badge-info">{{ post.category }}</span>
            <span :class="['badge inline-flex items-center gap-1.5', emotionBadge(post.emotion)]">
              <AppIcon :name="emotionIcon(post.emotion)" :size="13" /> {{ post.emotion }}
            </span>
            <span class="text-slate-300">·</span>
            <span class="text-sm text-slate-600">{{ post.source }}</span>
            <span class="text-sm text-slate-600 inline-flex items-center gap-1"><AppIcon name="eye" :size="14" /> {{ post.views }}</span>
            <span class="text-sm text-slate-600 inline-flex items-center gap-1"><AppIcon name="message-circle" :size="14" /> {{ post.comments }}</span>
            <span class="text-sm text-slate-600 inline-flex items-center gap-1"><AppIcon name="thumbs-up" :size="14" /> {{ post.likes }}</span>
            <span class="text-sm font-medium text-slate-700 ml-auto">{{ post.time }}</span>
            <span :class="['badge', reviewBadge(post.reviewStatus)]">{{ post.reviewStatus }}</span>
          </div>
          <h3 v-if="post.title" class="text-[15px] font-semibold text-slate-800 mb-1.5">{{ post.title }}</h3>
          <p :class="['text-[15px] text-slate-800 leading-7', { 'content-clamped': !expandedIds.has(post.id) }]">
            {{ post.content || '（正文为空）' }}
          </p>
          <button
            v-if="post.content && post.content.length > 90"
            class="btn-link text-xs mt-1"
            @click.stop="toggleExpanded(post.id)"
          >
            {{ expandedIds.has(post.id) ? '收起正文' : '展开正文' }}
          </button>
          <div
            v-if="post.commentSuggestedCategory"
            class="mt-2 text-sm text-brand-700 bg-brand-50 border border-brand-100 px-3 py-2"
          >
            评论复核提示：{{ post.commentSignal }}。该提示仅用于辅助人工判断。
          </div>
          <div class="flex items-center gap-4 mt-3 pt-3 border-t border-slate-100 flex-wrap">
            <span v-if="post.location" class="text-sm text-slate-600 inline-flex items-center gap-1"><AppIcon name="map-pin" :size="14" /> {{ post.location }}</span>
            <span v-if="post.problem" class="text-sm text-slate-600 inline-flex items-center gap-1"><AppIcon name="tag" :size="14" /> {{ post.problem }}</span>
            <span v-if="post.demand" class="text-sm text-slate-600 inline-flex items-center gap-1"><AppIcon name="megaphone" :size="14" /> {{ post.demand }}</span>
            <span class="ml-auto text-sm font-medium text-brand-600 inline-flex items-center gap-1">
              查看完整详情 <AppIcon name="arrow-right" :size="14" />
            </span>
          </div>
        </div>
      </div>
    </div>

    <!-- 空状态 -->
    <EmptyState v-else text="暂无符合条件的帖子" hint="尝试调整筛选条件" />

    <!-- 分页 -->
    <div v-if="totalPages > 1" class="flex items-center justify-center gap-1.5">
      <button type="button" class="page-btn" :disabled="page <= 1" :class="{ 'opacity-40': page <= 1 }" @click="goPage(page - 1)">上一页</button>
      <template v-for="(p, i) in pageNumbers" :key="i">
        <span v-if="p === '...'" class="page-btn text-slate-400 border-transparent bg-transparent">...</span>
        <button v-else type="button" class="page-btn" :aria-current="p === page ? 'page' : undefined" :class="{ 'page-btn-active': p === page }" @click="goPage(p as number)">{{ p }}</button>
      </template>
      <button type="button" class="page-btn" :disabled="page >= totalPages" :class="{ 'opacity-40': page >= totalPages }" @click="goPage(page + 1)">下一页</button>
    </div>

    <Teleport to="body">
      <div v-if="batchMode" class="fixed inset-0 z-[90] flex items-center justify-center p-5" @keydown.esc="batchMode = null">
        <button class="absolute inset-0 bg-slate-950/35" aria-label="关闭批量操作" @click="batchMode = null"></button>
        <div class="relative bg-white shadow-2xl w-full max-w-lg p-7">
          <h2 class="text-lg font-semibold text-slate-900">
            {{ batchMode === 'confirm' ? '批量确认 AI 判定' : '批量标记无关内容' }}
          </h2>
          <p class="text-sm text-slate-600 mt-2">
            将处理当前选择的 {{ selectedIds.size }} 条帖子，保存后会同步更新事件和首页预警。
          </p>
          <label class="text-sm text-slate-700 block mt-5 mb-2">
            复核说明{{ batchMode === 'irrelevant' ? '（必填）' : '（选填）' }}
          </label>
          <textarea
            v-model="batchNote"
            rows="4"
            class="input w-full resize-none"
            :placeholder="batchMode === 'irrelevant' ? '请说明为何判定为无关内容' : '可填写批量确认依据'"
          ></textarea>
          <div class="flex justify-end gap-3 mt-5">
            <button class="btn btn-ghost" :disabled="batchSaving" @click="batchMode = null">取消</button>
            <button class="btn btn-primary" :disabled="batchSaving" @click="submitBatch">
              {{ batchSaving ? '处理中...' : '确认执行' }}
            </button>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<style scoped>
.content-clamped {
  display: -webkit-box;
  overflow: hidden;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}
</style>
