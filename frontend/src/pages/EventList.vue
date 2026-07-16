﻿﻿﻿﻿<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { eventApi, settingsApi } from '@/utils/api'
import LoadingSpinner from '@/components/LoadingSpinner.vue'
import EmptyState from '@/components/EmptyState.vue'
import RefreshButton from '@/components/RefreshButton.vue'

const router = useRouter()

const loading = ref(false)

const events = ref([
  { id: '1', risk: '高', title: '西门快递诈骗集中事件', category: '诈骗', posts: 45, growth: '+15/天', time: '7月12日', status: '处理中' },
  { id: '2', risk: '高', title: '21栋宿舍电瓶车充电起火', category: '消防', posts: 32, growth: '+8/天', time: '7月13日', status: '待研判' },
  { id: '3', risk: '中', title: '二食堂卫生投诉集中', category: '食堂', posts: 28, growth: '+5/天', time: '7月11日', status: '处理中' },
  { id: '4', risk: '中', title: '教学楼区域电动车乱停', category: '交通', posts: 18, growth: '+3/天', time: '7月12日', status: '已确认' },
  { id: '5', risk: '低', title: '图书馆空调温度过低', category: '设施', posts: 5, growth: '稳定', time: '7月13日', status: '已确认' },
  { id: '6', risk: '低', title: '操场夜间照明不足', category: '设施', posts: 8, growth: '稳定', time: '7月10日', status: '已忽略' },
])

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
  s === '待研判' ? 'badge-warn' : s === '处理中' ? 'badge-info' : s === '已确认' ? 'badge-success' : 'badge-neutral'

function goDetail(id: string) { router.push(`/events/${id}`) }

function unwrap(res: any) {
  if (res && typeof res === 'object' && (res.code !== undefined || res.success !== undefined) && res.data !== undefined) return res.data
  return res
}

// 加载事件列表数据
async function loadEvents() {
  loading.value = true
  try {
    const res: any = await eventApi.list()
    const d = unwrap(res)
    if (Array.isArray(d) && d.length) {
      events.value = d.map((e: any) => ({
        id: String(e.id ?? e.eventId ?? ''),
        risk: e.risk ?? e.riskLevel ?? '低',
        title: e.title ?? e.name ?? e.summary ?? '',
        category: e.category ?? e.type ?? '',
        posts: e.posts ?? e.postCount ?? e.count ?? 0,
        growth: e.growth ?? e.growthRate ?? '稳定',
        time: e.time ?? e.date ?? e.createdAt ?? '',
        status: e.status ?? '待研判',
      }))
    }
  } catch (err) {
    console.warn('事件列表加载失败，使用默认数据', err)
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
  <div class="page">
    <!-- 加载状态 -->
    <LoadingSpinner v-if="loading" />
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
        <option value="">全部状态</option><option value="待研判">待研判</option><option value="处理中">处理中</option><option value="已确认">已确认</option><option value="已忽略">已忽略</option>
      </select>
      <div class="flex-1"></div>
      <RefreshButton :on-refresh="loadEvents" />
      <span class="text-sm text-slate-500">共 <span class="font-semibold text-slate-700">{{ filteredEvents.length }}</span> 个事件</span>
    </div>

    <!-- 事件表格 -->
    <div class="card overflow-x-auto">
      <!-- 表格空状态 -->
      <EmptyState v-if="!filteredEvents.length" text="暂无事件" />
      <table v-else class="table-base table-row-hover min-w-[1000px]">
        <thead>
          <tr>
            <th class="w-36">风险等级</th>
            <th class="w-64">事件标题</th>
            <th class="w-24">类型</th>
            <th class="w-24">帖子数</th>
            <th class="w-32">增长速度</th>
            <th class="w-28">发现时间</th>
            <th class="w-28">状态</th>
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
            <td><span :class="['badge', statusBadge(e.status)]">{{ e.status }}</span></td>
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
