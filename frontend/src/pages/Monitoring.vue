<script setup lang="ts">
import { ref, onMounted, watch, computed } from 'vue'
import AppIcon from '../components/AppIcon.vue'
import LoadingSpinner from '@/components/LoadingSpinner.vue'
import EmptyState from '@/components/EmptyState.vue'
import { postApi, settingsApi } from '@/utils/api'

const loading = ref(false)
const searchKeyword = ref('')
const filterCategory = ref('')
const filterEmotion = ref('')
const filterSource = ref('')

const posts = ref<any[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const categoryOptions = ref<string[]>([
  '诈骗与财产安全', '治安与人身安全', '消防与用电安全', '校园交通安全',
  '宿舍设施问题', '食堂与餐饮问题', '突发事件', '其他',
])

function unwrap(res: any) {
  if (res && typeof res === 'object' && (res.code !== undefined || res.success !== undefined) && res.data !== undefined) return res.data
  return res
}

async function loadPosts() {
  loading.value = true
  try {
    const res: any = await postApi.list({
      keyword: searchKeyword.value || undefined,
      category: filterCategory.value || undefined,
      emotion: filterEmotion.value || undefined,
      source: filterSource.value || undefined,
      page: page.value,
      size: size.value,
    })
    const d = unwrap(res)
    // 后端返回 { total, page, size, data: [...] }
    if (d && typeof d === 'object' && Array.isArray(d.data)) {
      posts.value = d.data.map((p: any) => ({
        id: p.id,
        category: p.safetyCategory ?? p.category ?? '其他',
        emotion: p.emotion ?? '中性',
        source: p.source ?? p.categoryName ?? '',
        comments: p.commentCount ?? p.comments ?? 0,
        likes: p.likeCount ?? p.likes ?? 0,
        time: p.timeDesc ?? p.time ?? '',
        content: p.content ?? '',
        location: p.location ?? '',
        problem: p.problem ?? '',
        demand: p.demand ?? '',
      }))
      total.value = d.total ?? posts.value.length
    } else if (Array.isArray(d)) {
      posts.value = d.map((p: any) => ({
        id: p.id,
        category: p.safetyCategory ?? p.category ?? '其他',
        emotion: p.emotion ?? '中性',
        source: p.source ?? p.categoryName ?? '',
        comments: p.commentCount ?? p.comments ?? 0,
        likes: p.likeCount ?? p.likes ?? 0,
        time: p.timeDesc ?? p.time ?? '',
        content: p.content ?? '',
        location: p.location ?? '',
        problem: p.problem ?? '',
        demand: p.demand ?? '',
      }))
      total.value = d.length
    }
  } catch (err) {
    console.warn('帖子数据加载失败', err)
  } finally {
    loading.value = false
  }
}

// 筛选条件变化时重置到第1页并重新加载
watch([searchKeyword, filterCategory, filterEmotion, filterSource], () => {
  page.value = 1
  loadPosts()
})

function goPage(p: number) {
  if (p < 1 || p > totalPages.value) return
  page.value = p
  loadPosts()
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
    if (Array.isArray(d.categories) && d.categories.length) categoryOptions.value = d.categories.map(String)
  } catch (err) {
    console.warn('分类设置加载失败，使用默认分类', err)
  }
}

onMounted(() => {
  loadPosts()
  loadCategories()
})

const emotionIcon = (e: string) => e.includes('负面') ? 'anger' : e.includes('中性') ? 'meh' : 'smile'
const emotionBadge = (e: string) => e.includes('负面') ? 'badge-high' : e.includes('中性') ? 'badge-neutral' : 'badge-success'
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
          <option value="">全部分类</option><option>打听求助</option><option>二手闲置</option><option>恋爱交友</option><option>其他</option>
        </select>
        <span class="text-xs text-slate-400 ml-auto">共 {{ total }} 条</span>
      </div>
    </div>

    <!-- 加载状态 -->
    <LoadingSpinner v-if="loading" />

    <!-- 帖子列表 -->
    <div v-else-if="posts.length" class="space-y-3">
      <div
        v-for="post in posts"
        :key="post.id"
        class="card card-pad card-hover cursor-pointer"
      >
        <div class="flex items-center gap-2 mb-2.5 flex-wrap">
          <span class="badge badge-info">{{ post.category }}</span>
          <span :class="['badge inline-flex items-center gap-1.5', emotionBadge(post.emotion)]">
            <AppIcon :name="emotionIcon(post.emotion)" :size="13" /> {{ post.emotion }}
          </span>
          <span class="text-slate-300">·</span>
          <span class="text-xs text-slate-500">{{ post.source }}</span>
          <span class="text-slate-300">·</span>
          <span class="text-xs text-slate-500 inline-flex items-center gap-1"><AppIcon name="message-circle" :size="13" /> {{ post.comments }}</span>
          <span class="text-xs text-slate-500 inline-flex items-center gap-1"><AppIcon name="thumbs-up" :size="13" /> {{ post.likes }}</span>
          <span class="text-xs text-slate-400 ml-auto">{{ post.time }}</span>
        </div>
        <p class="text-sm text-slate-700 leading-relaxed">{{ post.content }}</p>
        <div class="flex items-center gap-4 mt-3 pt-3 border-t border-slate-100 flex-wrap">
          <span v-if="post.location" class="text-xs text-slate-500 inline-flex items-center gap-1"><AppIcon name="map-pin" :size="13" /> {{ post.location }}</span>
          <span v-if="post.problem" class="text-xs text-slate-500 inline-flex items-center gap-1"><AppIcon name="tag" :size="13" /> {{ post.problem }}</span>
          <span v-if="post.demand" class="text-xs text-slate-500 inline-flex items-center gap-1"><AppIcon name="megaphone" :size="13" /> {{ post.demand }}</span>
        </div>
      </div>
    </div>

    <!-- 空状态 -->
    <EmptyState v-else text="暂无符合条件的帖子" hint="尝试调整筛选条件" />

    <!-- 分页 -->
    <div v-if="totalPages > 1" class="flex items-center justify-center gap-1.5">
      <span class="page-btn" :class="{ 'opacity-40 pointer-events-none': page <= 1 }" @click="goPage(page - 1)">上一页</span>
      <template v-for="(p, i) in pageNumbers" :key="i">
        <span v-if="p === '...'" class="page-btn text-slate-400 border-transparent bg-transparent">...</span>
        <span v-else class="page-btn" :class="{ 'page-btn-active': p === page }" @click="goPage(p as number)">{{ p }}</span>
      </template>
      <span class="page-btn" :class="{ 'opacity-40 pointer-events-none': page >= totalPages }" @click="goPage(page + 1)">下一页</span>
    </div>
  </div>
</template>
