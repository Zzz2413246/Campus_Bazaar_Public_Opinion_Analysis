<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppIcon from '@/components/AppIcon.vue'
import EmptyState from '@/components/EmptyState.vue'
import LoadingSpinner from '@/components/LoadingSpinner.vue'
import { postApi, settingsApi } from '@/utils/api'
import { toast } from '@/utils/toast'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const post = ref<any>(null)
const comments = ref<any[]>([])
const commentPage = ref(1)
const commentSize = 20
const commentTotal = ref(0)
const reviewCategory = ref('')
const reviewRiskLevel = ref('低')
const reviewEmotion = ref('中性')
const reviewNote = ref('')
const reviewSaving = ref(false)
const categoryOptions = ref<string[]>([
  '诈骗与财产安全', '治安与人身安全', '消防与用电安全', '校园交通安全',
  '宿舍设施问题', '食堂与餐饮问题', '突发事件', '其他',
])

const totalPages = computed(() => Math.max(1, Math.ceil(commentTotal.value / commentSize)))

async function loadDetail() {
  loading.value = true
  try {
    const data: any = await postApi.detail(String(route.params.id), {
      commentPage: commentPage.value,
      commentSize,
    })
    if (data?.error) {
      post.value = null
      comments.value = []
      return
    }
    post.value = data
    comments.value = Array.isArray(data?.comments?.data) ? data.comments.data : []
    commentTotal.value = Number(data?.comments?.total ?? 0)
    reviewCategory.value = data.reviewedCategory || data.safetyCategory || '其他'
    reviewRiskLevel.value = data.reviewedRiskLevel || data.riskLevel || '低'
    reviewEmotion.value = data.reviewedEmotion || data.emotion || '中性'
    reviewNote.value = data.reviewNote || ''
  } finally {
    loading.value = false
  }
}

function goCommentPage(page: number) {
  if (page < 1 || page > totalPages.value || page === commentPage.value) return
  commentPage.value = page
  loadDetail()
}

function emotionClass(emotion: string) {
  if (emotion === '负面') return 'badge-high'
  if (emotion === '正面') return 'badge-success'
  return 'badge-neutral'
}

function reviewClass(status: string) {
  if (status === '待复核') return 'badge-warn'
  if (status === '已确认') return 'badge-success'
  if (status === '已修正') return 'badge-info'
  return 'badge-neutral'
}

async function submitReview(action: 'confirm' | 'correct' | 'irrelevant' | 'reset') {
  if (reviewSaving.value || !post.value) return
  if (action === 'correct' && !reviewNote.value.trim()) {
    toast.error('修正AI判断时请填写复核说明')
    return
  }
  if (action === 'irrelevant' && !reviewNote.value.trim()) {
    toast.error('标记无关内容时请填写原因')
    return
  }
  reviewSaving.value = true
  try {
    const result: any = await postApi.review(String(post.value.id), {
      action,
      category: reviewCategory.value,
      riskLevel: reviewRiskLevel.value,
      emotion: reviewEmotion.value,
      note: reviewNote.value,
      reviewer: localStorage.getItem('yuqing_nickname') || '管理员',
    })
    if (result?.error) {
      toast.error(result.error)
      return
    }
    await loadDetail()
    toast.success(
      action === 'confirm' ? 'AI判断已确认'
        : action === 'correct' ? '人工修正已保存'
          : action === 'irrelevant' ? '已标记为无关内容'
            : '已撤销复核，重新进入待复核',
    )
  } catch (err) {
    console.error('保存人工复核失败', err)
    toast.error('复核保存失败，请稍后重试')
  } finally {
    reviewSaving.value = false
  }
}

async function loadCategories() {
  try {
    const data: any = await settingsApi.get()
    if (Array.isArray(data?.categories) && data.categories.length) {
      categoryOptions.value = [...data.categories.map(String), '其他']
        .filter((item, index, list) => list.indexOf(item) === index)
    }
  } catch {
    // 分类设置不可用时使用默认选项
  }
}

onMounted(() => {
  loadDetail()
  loadCategories()
})
</script>

<template>
  <div class="page">
    <div class="flex items-center justify-between gap-4 flex-wrap">
      <button class="btn btn-ghost" @click="router.push('/monitoring')">
        <AppIcon name="arrow-left" :size="16" />
        返回舆情监测
      </button>
      <span v-if="post" class="text-sm text-slate-500">帖子编号：{{ post.id }}</span>
    </div>

    <LoadingSpinner v-if="loading && !post" />
    <EmptyState v-else-if="!post" text="帖子不存在或已被删除" />

    <template v-else>
      <article class="card card-pad">
        <div class="flex flex-wrap items-center gap-2.5 mb-4">
          <span v-if="post.riskLevel" class="badge"
            :class="post.riskLevel === '高' ? 'badge-high' : post.riskLevel === '中' ? 'badge-medium' : 'badge-low'">
            {{ post.riskLevel }}风险
          </span>
          <span class="badge badge-info">{{ post.safetyCategory || '其他' }}</span>
          <span class="badge" :class="emotionClass(post.emotion)">{{ post.emotion || '中性' }}</span>
          <span :class="['badge', reviewClass(post.reviewStatus || '待复核')]">
            {{ post.reviewStatus || '待复核' }}
          </span>
          <span class="ml-auto text-sm text-slate-600 inline-flex items-center gap-1.5">
            <AppIcon name="clock" :size="15" />{{ post.publishTime }}
          </span>
        </div>

        <h2 class="section-title !text-[18px] !leading-7">
          {{ post.title || '无标题帖子' }}
        </h2>
        <p class="text-[15px] leading-7 text-slate-800 whitespace-pre-wrap mt-3">
          {{ post.content || '（正文为空）' }}
        </p>

        <div class="flex flex-wrap gap-x-6 gap-y-3 mt-5 pt-4 border-t border-slate-200 text-sm text-slate-600">
          <span class="inline-flex items-center gap-1.5"><AppIcon name="eye" :size="15" />{{ post.viewCount || 0 }} 次浏览</span>
          <span class="inline-flex items-center gap-1.5"><AppIcon name="message-circle" :size="15" />{{ commentTotal }} 条评论</span>
          <span class="inline-flex items-center gap-1.5"><AppIcon name="thumbs-up" :size="15" />{{ post.likeCount || 0 }} 个赞</span>
          <span v-if="post.location" class="inline-flex items-center gap-1.5"><AppIcon name="map-pin" :size="15" />{{ post.location }}</span>
          <span v-if="post.problem" class="inline-flex items-center gap-1.5"><AppIcon name="tag" :size="15" />{{ post.problem }}</span>
          <span v-if="post.demand" class="inline-flex items-center gap-1.5"><AppIcon name="megaphone" :size="15" />{{ post.demand }}</span>
        </div>
      </article>

      <section class="card card-pad relative overflow-hidden">
        <div class="absolute inset-x-0 top-0 h-1 bg-gradient-to-r from-brand-600 to-accent-500"></div>
        <div class="flex items-start justify-between gap-4 mb-5 flex-wrap">
          <div>
            <h2 class="section-title">人工复核</h2>
            <p class="section-sub mt-1">外部AI提供的低／中／高标签会原样保留，列表和报告优先使用人工复核后的最终结论。</p>
          </div>
          <div v-if="post.reviewer" class="text-sm text-slate-500 text-right">
            <div>复核人：{{ post.reviewer }}</div>
            <div v-if="post.reviewedAt" class="mt-1">{{ new Date(post.reviewedAt).toLocaleString('zh-CN') }}</div>
          </div>
        </div>

        <div class="grid grid-cols-1 lg:grid-cols-2 gap-5 mb-5">
          <div class="border border-slate-200 bg-slate-50/70 p-5">
            <div class="text-[15px] font-semibold text-slate-800 mb-3">
              AI原始判断
              <span class="text-xs font-normal text-slate-400 ml-1">{{ post.riskLabelSource || '' }}</span>
            </div>
            <div class="flex flex-wrap gap-2">
              <span class="badge badge-info">{{ post.aiSafetyCategory || post.safetyCategory }}</span>
              <span class="badge" :class="emotionClass(post.aiEmotion || post.emotion)">
                {{ post.aiEmotion || post.emotion }}
              </span>
              <span
                class="badge"
                :class="(post.aiRiskLevel || post.riskLevel) === '高' ? 'badge-high' : (post.aiRiskLevel || post.riskLevel) === '中' ? 'badge-medium' : 'badge-low'"
              >
                {{ post.aiRiskLevel || post.riskLevel }}风险
              </span>
            </div>
          </div>

          <div class="border border-brand-100 bg-brand-50/50 p-5">
            <div class="text-[15px] font-semibold text-slate-800 mb-3">人工最终结论</div>
            <div class="grid grid-cols-1 sm:grid-cols-3 gap-3">
              <select v-model="reviewCategory" class="select w-full">
                <option v-for="category in categoryOptions" :key="category" :value="category">{{ category }}</option>
              </select>
              <select v-model="reviewEmotion" class="select w-full">
                <option>正面</option><option>中性</option><option>负面</option>
              </select>
              <select v-model="reviewRiskLevel" class="select w-full">
                <option>高</option><option>中</option><option>低</option>
              </select>
            </div>
          </div>
        </div>

        <div>
          <label class="text-[15px] text-slate-700 font-medium block mb-2">
            复核说明
            <span class="text-sm font-normal text-slate-400">（修正或标记无关时必填）</span>
          </label>
          <textarea
            v-model="reviewNote"
            rows="3"
            class="input w-full resize-none"
            placeholder="说明判断依据、修正原因或标记无关的原因..."
          ></textarea>
        </div>

        <div class="flex items-center justify-end gap-2 mt-4 flex-wrap">
          <button
            v-if="post.reviewStatus && post.reviewStatus !== '待复核'"
            class="btn btn-ghost mr-auto"
            :disabled="reviewSaving"
            @click="submitReview('reset')"
          >
            撤销复核
          </button>
          <button class="btn btn-ghost" :disabled="reviewSaving" @click="submitReview('irrelevant')">
            标记无关内容
          </button>
          <button class="btn btn-ghost" :disabled="reviewSaving" @click="submitReview('correct')">
            保存人工修正
          </button>
          <button class="btn btn-primary" :disabled="reviewSaving" @click="submitReview('confirm')">
            {{ reviewSaving ? '保存中...' : '确认AI判断' }}
          </button>
        </div>
      </section>

      <section class="card card-pad">
        <div class="flex items-center justify-between mb-5">
          <div>
            <h2 class="section-title">帖子评论</h2>
            <p class="section-sub mt-1">共 {{ commentTotal }} 条，评论者信息已去标识化</p>
          </div>
          <span class="badge badge-info">第 {{ commentPage }} / {{ totalPages }} 页</span>
        </div>

        <LoadingSpinner v-if="loading" />
        <EmptyState v-else-if="!comments.length" text="该帖子暂无已关联评论" />
        <div v-else class="divide-y divide-slate-200">
          <div
            v-for="comment in comments"
            :key="comment.id"
            class="py-5"
            :style="{ paddingLeft: `${Math.min(Number(comment.replyDepth || 0), 3) * 24}px` }"
          >
            <div class="flex flex-wrap items-center gap-2 mb-2.5">
              <span v-if="comment.isAuthor" class="badge badge-info">楼主</span>
              <span v-if="comment.isReply" class="badge badge-neutral">回复</span>
              <span v-if="comment.emotion" class="badge" :class="emotionClass(comment.emotion)">{{ comment.emotion }}</span>
              <span v-if="comment.safetyCategory" class="badge badge-warn">{{ comment.safetyCategory }}</span>
              <span class="ml-auto text-sm font-medium text-slate-700">{{ comment.publishTime }}</span>
            </div>
            <p class="text-[15px] leading-7 text-slate-800 whitespace-pre-wrap">{{ comment.content }}</p>
            <div class="flex items-center gap-4 mt-2.5 text-sm text-slate-600">
              <span class="inline-flex items-center gap-1"><AppIcon name="thumbs-up" :size="14" />{{ comment.likeCount || 0 }}</span>
              <span v-if="comment.evidenceScore > 0">证据评分 {{ comment.evidenceScore }}</span>
            </div>
          </div>
        </div>

        <div v-if="totalPages > 1" class="flex justify-center items-center gap-2 mt-6">
          <button class="page-btn" :disabled="commentPage <= 1" @click="goCommentPage(commentPage - 1)">上一页</button>
          <span class="text-sm text-slate-700">第 {{ commentPage }} 页，共 {{ totalPages }} 页</span>
          <button class="page-btn" :disabled="commentPage >= totalPages" @click="goCommentPage(commentPage + 1)">下一页</button>
        </div>
      </section>
    </template>
  </div>
</template>
