﻿﻿﻿﻿<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { eventApi, settingsApi } from '@/utils/api'
import LoadingSpinner from '@/components/LoadingSpinner.vue'
import EmptyState from '@/components/EmptyState.vue'
import ErrorState from '@/components/ErrorState.vue'
import RefreshButton from '@/components/RefreshButton.vue'

const router = useRouter()

const loading = ref(false)
const loadError = ref('')

const events = ref<any[]>([])

// 筛选条件变量
const filterRisk = ref('')
const filterCategory = ref('')
const filterStatus = ref('')
const categoryOptions = ref<string[]>([
  '诈骗与财产安全', '治安与人身安全', '消防与用电安全', '校园交通安全',
  '宿舍设施问题', '食堂与餐饮问题', '突发事件',
])

// 根据筛选条件过滤事件列表
const filteredEvents = computed(() => {
  return events.value.filter(e => {
    if (filterRisk.value && e.risk !== filterRisk.value) return false
    if (filterCategory.value && e.category !== filterCategory.value) return false
    if (filterStatus.value && e.status !== filterStatus.value) return false
    return true
  })
})

const statusBadge = (s: string) =>
  s === '待核实' ? 'badge-warn'
    : s === '处理中' ? 'badge-info'
      : s === '已解决' ? 'badge-success'
        : 'badge-neutral'

function formatDueAt(value: string) {
  if (!value) return '未设置'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}

function goDetail(id: string) { router.push(`/events/${id}`) }

function unwrap(res: any) {
  if (res && typeof res === 'object' && (res.code !== undefined || res.success !== undefined) && res.data !== undefined) return res.data
  return res
}

// 加载事件列表数据
async function loadEvents() {
  loading.value = true
  loadError.value = ''
  try {
    const res: any = await eventApi.list()
    const d = unwrap(res)
    if (Array.isArray(d)) {
      events.value = d.map((e: any) => ({
        id: String(e.id ?? e.eventId ?? ''),
        risk: e.risk ?? e.riskLevel ?? '低',
        title: e.title ?? e.name ?? e.summary ?? '',
        category: e.category ?? e.type ?? '',
        posts: e.posts ?? e.postCount ?? e.count ?? 0,
        growth: e.growth ?? e.growthRate ?? '稳定',
        time: e.time ?? e.date ?? e.createdAt ?? '',
        status: e.status ?? '待核实',
        assignee: e.assignee ?? '',
        dueAt: e.dueAt ?? '',
        overdue: Boolean(e.overdue),
      }))
    }
  } catch (err) {
    console.warn('事件列表加载失败', err)
    events.value = []
    loadError.value = '事件数据暂时无法获取，请稍后重试。'
  } finally {
    loading.value = false
  }
}

async function loadCategories() {
  try {
    const res: any = await settingsApi.get()
    const d = unwrap(res) || {}
    if (Array.isArray(d.categories)) {
      categoryOptions.value = d.categories.map(String).filter((name: string) => name !== '其他')
    }
  } catch (err) {
    console.warn('分类设置加载失败，使用默认分类', err)
  }
}

onMounted(() => {
  loadEvents()
  loadCategories()
})
</script>

<template>
  <div class="page large-detail-page">
    <!-- 加载状态 -->
    <LoadingSpinner v-if="loading" />
    <ErrorState v-else-if="loadError" title="事件列表加载失败" :message="loadError" @retry="loadEvents" />
    <!-- 筛选栏 -->
    <div class="card card-pad flex flex-wrap items-center gap-3">
      <span class="text-sm text-slate-500">筛选</span>
      <select class="select" v-model="filterRisk">
        <option value="">全部风险等级</option><option value="高">高风险</option><option value="中">中风险</option><option value="低">低风险</option>
      </select>
      <select class="select" v-model="filterCategory">
        <option value="">全部事件类型</option>
        <option v-for="category in categoryOptions" :key="category" :value="category">{{ category }}</option>
      </select>
      <select class="select" v-model="filterStatus">
        <option value="">全部状态</option>
        <option value="待核实">待核实</option>
        <option value="处理中">处理中</option>
        <option value="持续观察">持续观察</option>
        <option value="已解决">已解决</option>
        <option value="误报">误报</option>
      </select>
      <div class="flex-1"></div>
      <RefreshButton :on-refresh="loadEvents" />
      <span class="text-sm text-slate-500">共 <span class="font-semibold text-slate-700">{{ filteredEvents.length }}</span> 个事件</span>
    </div>

    <!-- 事件表格 -->
    <div class="card overflow-x-auto">
      <!-- 表格空状态 -->
      <EmptyState v-if="!filteredEvents.length" text="暂无事件" />
      <table v-else class="table-base table-row-hover min-w-[1180px]">
        <thead>
          <tr>
            <th class="w-36">风险等级</th>
            <th class="w-64">事件标题</th>
            <th class="w-24">类型</th>
            <th class="w-24">帖子数</th>
            <th class="w-32">增长速度</th>
            <th class="w-28">发现时间</th>
            <th class="w-28">状态</th>
            <th class="w-28">负责人</th>
            <th class="w-36">计划完成</th>
            <th class="w-28">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="e in filteredEvents"
            :key="e.id"
            @click="goDetail(e.id)"
          >
            <td>
              <span :class="['badge', e.risk === '高' ? 'badge-high' : e.risk === '中' ? 'badge-medium' : 'badge-low']">
                <span :class="['dot', e.risk === '高' ? 'dot-high' : e.risk === '中' ? 'dot-medium' : 'dot-low']"></span>
                {{ e.risk }}风险
              </span>
            </td>
            <td class="font-medium text-slate-800">{{ e.title }}</td>
            <td><span class="badge badge-neutral">{{ e.category }}</span></td>
            <td class="text-slate-600">{{ e.posts }}条</td>
            <td>
              <span :class="e.growth !== '稳定' ? 'text-rose-500 font-medium' : 'text-slate-500'">{{ e.growth }}</span>
            </td>
            <td class="text-slate-500 text-xs">{{ e.time }}</td>
            <td>
              <span :class="['badge', e.overdue ? 'badge-high' : statusBadge(e.status)]">
                {{ e.overdue ? '已超时' : e.status }}
              </span>
            </td>
            <td class="text-slate-600">{{ e.assignee || '待指派' }}</td>
            <td :class="e.overdue ? 'text-rose-600 font-medium' : 'text-slate-500'" class="text-xs">
              {{ formatDueAt(e.dueAt) }}
            </td>
            <td>
              <button @click.stop="goDetail(e.id)" class="btn-link">查看详情 →</button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- 分页：事件数量不多，仅显示统计文字 -->
    <div class="flex items-center justify-center text-sm text-slate-400">
      共 {{ filteredEvents.length }} 个事件
    </div>
  </div>
</template>
