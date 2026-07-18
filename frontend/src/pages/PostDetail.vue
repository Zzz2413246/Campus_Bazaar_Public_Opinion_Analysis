<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppIcon from '@/components/AppIcon.vue'
import EmptyState from '@/components/EmptyState.vue'
import LoadingSpinner from '@/components/LoadingSpinner.vue'
import { postApi } from '@/utils/api'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const post = ref<any>(null)
const comments = ref<any[]>([])
const commentPage = ref(1)
const commentSize = 20
const commentTotal = ref(0)

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

onMounted(loadDetail)
</script>

<template>
  <div class="page">
    <div>
      <button class="btn btn-ghost" @click="router.push('/monitoring')">
        <AppIcon name="arrow-left" :size="16" />
        返回舆情监测
      </button>
    </div>

    <LoadingSpinner v-if="loading && !post" />
    <EmptyState v-else-if="!post" text="帖子不存在或已被删除" />

    <template v-else>
      <article class="card card-pad">
        <div class="flex flex-wrap items-center gap-2.5 mb-5">
          <span class="badge badge-info">{{ post.safetyCategory || '其他' }}</span>
          <span class="badge" :class="emotionClass(post.emotion)">{{ post.emotion || '中性' }}</span>
          <span v-if="post.riskLevel" class="badge"
            :class="post.riskLevel === '高' ? 'badge-high' : post.riskLevel === '中' ? 'badge-medium' : 'badge-low'">
            {{ post.riskLevel }}风险
          </span>
          <span class="ml-auto text-sm font-medium text-slate-700">{{ post.publishTime }}</span>
        </div>

        <h2 v-if="post.title" class="text-xl font-semibold text-slate-900 mb-3">{{ post.title }}</h2>
        <p class="text-[16px] leading-8 text-slate-800 whitespace-pre-wrap">{{ post.content }}</p>

        <div class="flex flex-wrap gap-5 mt-6 pt-4 border-t border-slate-200 text-sm text-slate-600">
          <span class="inline-flex items-center gap-1.5"><AppIcon name="message-circle" :size="15" />{{ commentTotal }} 条评论</span>
          <span class="inline-flex items-center gap-1.5"><AppIcon name="thumbs-up" :size="15" />{{ post.likeCount || 0 }} 个赞</span>
          <span v-if="post.location" class="inline-flex items-center gap-1.5"><AppIcon name="map-pin" :size="15" />{{ post.location }}</span>
          <span v-if="post.problem" class="inline-flex items-center gap-1.5"><AppIcon name="tag" :size="15" />{{ post.problem }}</span>
        </div>
      </article>

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
            <div class="flex flex-wrap items-center gap-2 mb-2">
              <span v-if="comment.isAuthor" class="badge badge-info">楼主</span>
              <span v-if="comment.isReply" class="badge badge-neutral">回复</span>
              <span v-if="comment.emotion" class="badge" :class="emotionClass(comment.emotion)">{{ comment.emotion }}</span>
              <span v-if="comment.safetyCategory" class="badge badge-warn">{{ comment.safetyCategory }}</span>
              <span class="ml-auto text-sm font-medium text-slate-700">{{ comment.publishTime }}</span>
            </div>
            <p class="text-[15px] leading-7 text-slate-800 whitespace-pre-wrap">{{ comment.content }}</p>
            <div class="flex items-center gap-4 mt-2 text-sm text-slate-600">
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
