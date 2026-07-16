<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, watch, nextTick } from 'vue'
import * as echarts from 'echarts'

const props = defineProps<{
  option: echarts.EChartsOption
  height?: string
}>()

const chartEl = ref<HTMLDivElement>()
// 加载态与错误态，用于骨架屏及错误兜底展示
const loading = ref(true)
const error = ref(false)
let chart: echarts.ECharts | null = null
let ro: ResizeObserver | null = null

onMounted(async () => {
  await nextTick()
  try {
    loading.value = true
    error.value = false
    chart = echarts.init(chartEl.value!)
    chart.setOption(props.option)
    chart.resize()
    loading.value = false
    // 监听容器尺寸变化，自动调整图表
    if (chartEl.value) {
      ro = new ResizeObserver(() => chart?.resize())
      ro.observe(chartEl.value)
    }
  } catch (e) {
    console.warn('图表渲染失败', e)
    error.value = true
    loading.value = false
  }
})

// 监听 option 变化，更新图表并处理异常
watch(() => props.option, () => {
  if (!chart || error.value) return
  try {
    chart.setOption(props.option, true)
  } catch (e) {
    console.warn('图表更新失败', e)
    error.value = true
  }
}, { deep: true })

onBeforeUnmount(() => {
  ro?.disconnect()
  chart?.dispose()
  chart = null
})
</script>

<template>
  <div class="relative w-full" :style="{ height: height || '100%' }">
    <!-- 骨架屏加载态 -->
    <div v-if="loading" class="absolute inset-0 flex items-center justify-center bg-slate-50/80">
      <div class="w-full h-full p-4">
        <div class="h-4 w-24 bg-slate-200 animate-pulse mb-3"></div>
        <div class="h-full flex items-end gap-2">
          <div class="flex-1 h-1/2 bg-slate-200 animate-pulse"></div>
          <div class="flex-1 h-3/4 bg-slate-200 animate-pulse"></div>
          <div class="flex-1 h-2/3 bg-slate-200 animate-pulse"></div>
          <div class="flex-1 h-5/6 bg-slate-200 animate-pulse"></div>
          <div class="flex-1 h-1/3 bg-slate-200 animate-pulse"></div>
        </div>
      </div>
    </div>
    <!-- 错误兜底 -->
    <div v-else-if="error" class="absolute inset-0 flex flex-col items-center justify-center text-sm text-slate-400">
      <span class="text-2xl mb-2">📊</span>
      <span>图表加载失败</span>
    </div>
    <!-- 正常图表 -->
    <div ref="chartEl" v-show="!loading && !error" class="w-full h-full"></div>
  </div>
</template>
