<script setup lang="ts">
import { ref } from 'vue'
import AppIcon from '../components/AppIcon.vue'

const searchKeyword = ref('')

const posts = ref([
  { id: 1, category: '诈骗', emotion: '负面', source: '校园集市', comments: 23, likes: 5, time: '2小时前', content: '昨天在西门遇到骗子假装借钱，大家小心点别被骗了...', location: '西门', problem: '诈骗', demand: '提醒同学注意' },
  { id: 2, category: '宿舍', emotion: '负面', source: '小红书', comments: 56, likes: 12, time: '3小时前', content: '17栋空调坏了快一周了还没修，这么热的天根本没法住...', location: '17栋宿舍', problem: '设施维修', demand: '空调维修' },
  { id: 3, category: '食堂', emotion: '中性', source: '校园集市', comments: 18, likes: 3, time: '4小时前', content: '二食堂今天菜又涨价了，学生党吃不起饭了...', location: '二食堂', problem: '价格问题', demand: '关注价格' },
  { id: 4, category: '消防', emotion: '负面', source: '微博', comments: 89, likes: 34, time: '5小时前', content: '21栋楼下电瓶车充电冒烟了！还好发现及时，不然就着火了...', location: '21栋宿舍', problem: '充电安全', demand: '加强管理' },
  { id: 5, category: '交通', emotion: '负面', source: 'B站', comments: 42, likes: 15, time: '6小时前', content: '西门上下课时间段堵得水泄不通，电瓶车乱窜太危险了...', location: '西门', problem: '交通拥堵', demand: '疏导交通' },
])

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
          />
        </div>
        <select class="select">
          <option>全部类型</option><option>诈骗与财产安全</option><option>治安与人身安全</option>
          <option>消防与用电安全</option><option>校园交通安全</option><option>宿舍设施</option><option>食堂问题</option>
        </select>
        <select class="select">
          <option>全部情绪</option><option>正面</option><option>中性</option><option>负面</option>
        </select>
        <select class="select">
          <option>全部来源</option><option>校园集市</option><option>小红书</option><option>微博</option><option>B站</option>
        </select>
      </div>
    </div>

    <!-- 帖子列表 -->
    <div class="space-y-3">
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
          <span class="text-xs text-slate-500 inline-flex items-center gap-1"><AppIcon name="map-pin" :size="13" /> {{ post.location }}</span>
          <span class="text-xs text-slate-500 inline-flex items-center gap-1"><AppIcon name="tag" :size="13" /> {{ post.problem }}</span>
          <span class="text-xs text-slate-500 inline-flex items-center gap-1"><AppIcon name="megaphone" :size="13" /> {{ post.demand }}</span>
        </div>
      </div>
    </div>

    <!-- 分页 -->
    <div class="flex items-center justify-center gap-1.5">
      <span class="page-btn">上一页</span>
      <span class="page-btn page-btn-active">1</span>
      <span class="page-btn">2</span>
      <span class="page-btn">3</span>
      <span class="page-btn text-slate-400 border-transparent bg-transparent">...</span>
      <span class="page-btn">10</span>
      <span class="page-btn">下一页</span>
    </div>
  </div>
</template>
