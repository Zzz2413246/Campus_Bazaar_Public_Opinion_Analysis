import type { EChartsOption } from 'echarts'

// 统一配色 · 与设计系统一致
export const palette = ['#6366f1', '#06b6d4', '#f59e0b', '#f43f5e', '#10b981', '#8b5cf6', '#ec4899']

// 生成近 N 天日期标签（格式 M/D）
function lastNDays(n: number): string[] {
  const days: string[] = []
  const today = new Date()
  for (let i = n - 1; i >= 0; i--) {
    const d = new Date(today)
    d.setDate(d.getDate() - i)
    days.push(`${d.getMonth() + 1}/${d.getDate()}`)
  }
  return days
}

// 通用网格样式
const grid = {
  left: 40,
  right: 20,
  top: 20,
  bottom: 32,
  containLabel: true,
}

const tooltip = {
  trigger: 'axis' as const,
  backgroundColor: 'rgba(255,255,255,0.96)',
  borderColor: '#e2e8f0',
  borderWidth: 1,
  textStyle: { color: '#334155', fontSize: 12 },
  extraCssText: 'box-shadow: 0 6px 24px rgba(15,23,42,0.08); backdrop-filter: blur(6px);',
}

// 近7天舆情趋势 · 多折线（帖子总量 / 安全事件 / 高风险事件）
export function trendLineOption(): EChartsOption {
  const days = lastNDays(7)
  return {
    tooltip,
    legend: {
      data: ['帖子总量', '安全事件', '高风险事件'],
      right: 0, top: 0,
      icon: 'roundRect', itemWidth: 10, itemHeight: 10,
      textStyle: { color: '#64748b', fontSize: 12 },
    },
    grid: { ...grid, top: 40 },
    xAxis: {
      type: 'category', data: days, boundaryGap: false,
      axisLine: { lineStyle: { color: '#e2e8f0' } },
      axisTick: { show: false },
      axisLabel: { color: '#94a3b8', fontSize: 11 },
    },
    yAxis: {
      type: 'value',
      splitLine: { lineStyle: { color: '#f1f5f9' } },
      axisLabel: { color: '#94a3b8', fontSize: 11 },
    },
    series: [
      {
        name: '帖子总量', type: 'line', smooth: true, symbol: 'circle', symbolSize: 6,
        data: [980, 1120, 1050, 1340, 1180, 1420, 1245],
        lineStyle: { width: 3, color: palette[0] },
        itemStyle: { color: palette[0] },
        areaStyle: {
          color: { type: 'linear', x: 0, y: 0, x2: 0, y2: 1,
            colorStops: [{ offset: 0, color: 'rgba(99,102,241,0.25)' }, { offset: 1, color: 'rgba(99,102,241,0)' }] },
        },
      },
      {
        name: '安全事件', type: 'line', smooth: true, symbol: 'circle', symbolSize: 6,
        data: [4, 6, 5, 8, 5, 7, 3],
        lineStyle: { width: 2.5, color: palette[1] },
        itemStyle: { color: palette[1] },
      },
      {
        name: '高风险事件', type: 'line', smooth: true, symbol: 'circle', symbolSize: 6,
        data: [1, 2, 1, 3, 1, 2, 0],
        lineStyle: { width: 2.5, color: palette[3] },
        itemStyle: { color: palette[3] },
      },
    ],
  }
}

// 议题分布 · 环形饼图
export function donutOption(): EChartsOption {
  return {
    tooltip: { trigger: 'item', backgroundColor: 'rgba(255,255,255,0.96)', borderColor: '#e2e8f0', borderWidth: 1, textStyle: { color: '#334155', fontSize: 12 }, extraCssText: 'box-shadow: 0 6px 24px rgba(15,23,42,0.08); backdrop-filter: blur(6px);' },
    legend: {
      orient: 'vertical', right: 0, top: 'center',
      icon: 'circle', itemWidth: 8, itemHeight: 8,
      textStyle: { color: '#64748b', fontSize: 12 },
    },
    series: [{
      type: 'pie', radius: ['52%', '78%'], center: ['38%', '50%'],
      avoidLabelOverlap: false,
      itemStyle: { borderColor: '#fff', borderWidth: 3, borderRadius: 6 },
      label: { show: true, position: 'center',
        formatter: '总议题\n{c}类', fontSize: 14, color: '#475569', lineHeight: 20 },
      emphasis: { label: { show: true, fontSize: 16, fontWeight: 'bold' } },
      labelLine: { show: false },
      data: [
        { value: 35, name: '诈骗', itemStyle: { color: palette[0] } },
        { value: 22, name: '治安', itemStyle: { color: palette[1] } },
        { value: 15, name: '消防', itemStyle: { color: palette[2] } },
        { value: 10, name: '交通', itemStyle: { color: palette[3] } },
        { value: 18, name: '设施', itemStyle: { color: palette[4] } },
      ],
    }],
  }
}

// 帖子总量趋势 · 面积图（近7天，与后端 areaData 对齐）
export function areaOption(): EChartsOption {
  const days = lastNDays(7)
  const data: number[] = []
  return {
    tooltip,
    grid,
    xAxis: {
      type: 'category', data: days, boundaryGap: false,
      axisLine: { lineStyle: { color: '#e2e8f0' } },
      axisTick: { show: false },
      axisLabel: { color: '#94a3b8', fontSize: 11 },
    },
    yAxis: {
      type: 'value',
      splitLine: { lineStyle: { color: '#f1f5f9' } },
      axisLabel: { color: '#94a3b8', fontSize: 11 },
    },
    series: [{
      type: 'line', smooth: true, symbol: 'none',
      data,
      lineStyle: { width: 3, color: palette[0] },
      areaStyle: {
        color: { type: 'linear', x: 0, y: 0, x2: 0, y2: 1,
          colorStops: [{ offset: 0, color: 'rgba(99,102,241,0.30)' }, { offset: 1, color: 'rgba(99,102,241,0.02)' }] },
      },
    }],
  }
}

// 情绪变化 · 多折线（正面/中性/负面）
export function emotionOption(): EChartsOption {
  const days = lastNDays(7)
  return {
    tooltip,
    legend: {
      data: ['正面', '中性', '负面'], right: 0, top: 0,
      icon: 'roundRect', itemWidth: 10, itemHeight: 10,
      textStyle: { color: '#64748b', fontSize: 12 },
    },
    grid: { ...grid, top: 40 },
    xAxis: {
      type: 'category', data: days, boundaryGap: false,
      axisLine: { lineStyle: { color: '#e2e8f0' } },
      axisTick: { show: false },
      axisLabel: { color: '#94a3b8', fontSize: 11 },
    },
    yAxis: {
      type: 'value', max: 100,
      splitLine: { lineStyle: { color: '#f1f5f9' } },
      axisLabel: { color: '#94a3b8', fontSize: 11, formatter: '{value}%' },
    },
    series: [
      { name: '正面', type: 'line', smooth: true, symbol: 'circle', symbolSize: 5,
        data: [] as number[], lineStyle: { width: 2.5, color: palette[4] }, itemStyle: { color: palette[4] } },
      { name: '中性', type: 'line', smooth: true, symbol: 'circle', symbolSize: 5,
        data: [] as number[], lineStyle: { width: 2.5, color: palette[1] }, itemStyle: { color: palette[1] } },
      { name: '负面', type: 'line', smooth: true, symbol: 'circle', symbolSize: 5,
        data: [] as number[], lineStyle: { width: 2.5, color: palette[3] }, itemStyle: { color: palette[3] } },
    ],
  }
}

// 各类议题热度 · 堆叠面积图（默认空，由后端 stackData 填充）
export function stackAreaOption(): EChartsOption {
  const days = lastNDays(7)
  const cats = ['诈骗', '治安', '消防', '交通', '设施']
  const data: Record<string, number[]> = {
    '诈骗': [], '治安': [], '消防': [], '交通': [], '设施': [],
  }
  const colors = [palette[0], palette[1], palette[2], palette[3], palette[4]]
  return {
    tooltip,
    legend: {
      data: cats, right: 0, top: 0,
      icon: 'roundRect', itemWidth: 10, itemHeight: 10,
      textStyle: { color: '#64748b', fontSize: 12 },
    },
    grid: { ...grid, top: 40 },
    xAxis: {
      type: 'category', data: days, boundaryGap: false,
      axisLine: { lineStyle: { color: '#e2e8f0' } },
      axisTick: { show: false },
      axisLabel: { color: '#94a3b8', fontSize: 11 },
    },
    yAxis: {
      type: 'value',
      splitLine: { lineStyle: { color: '#f1f5f9' } },
      axisLabel: { color: '#94a3b8', fontSize: 11 },
    },
    series: cats.map((c, i) => ({
      name: c, type: 'line', stack: 'total', smooth: true, symbol: 'none',
      data: data[c],
      lineStyle: { width: 1.5, color: colors[i] },
      itemStyle: { color: colors[i] },
      areaStyle: { color: colors[i], opacity: 0.18 },
    })),
  }
}

// 各来源渠道 · 柱状图
export function barOption(): EChartsOption {
  const sources = ['校园集市', '小红书', '微博', 'B站']
  return {
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, backgroundColor: 'rgba(255,255,255,0.96)', borderColor: '#e2e8f0', borderWidth: 1, textStyle: { color: '#334155', fontSize: 12 }, extraCssText: 'box-shadow: 0 6px 24px rgba(15,23,42,0.08); backdrop-filter: blur(6px);' },
    grid,
    xAxis: {
      type: 'category', data: sources,
      axisLine: { lineStyle: { color: '#e2e8f0' } },
      axisTick: { show: false },
      axisLabel: { color: '#94a3b8', fontSize: 11 },
    },
    yAxis: {
      type: 'value',
      splitLine: { lineStyle: { color: '#f1f5f9' } },
      axisLabel: { color: '#94a3b8', fontSize: 11 },
    },
    series: [{
      type: 'bar', barWidth: '46%',
      data: [
        { value: 0, itemStyle: { color: { type: 'linear', x: 0, y: 0, x2: 0, y2: 1,
          colorStops: [{ offset: 0, color: palette[0] }, { offset: 1, color: 'rgba(99,102,241,0.5)' }] } } },
        { value: 0, itemStyle: { color: { type: 'linear', x: 0, y: 0, x2: 0, y2: 1,
          colorStops: [{ offset: 0, color: palette[1] }, { offset: 1, color: 'rgba(6,182,212,0.5)' }] } } },
        { value: 0, itemStyle: { color: { type: 'linear', x: 0, y: 0, x2: 0, y2: 1,
          colorStops: [{ offset: 0, color: palette[2] }, { offset: 1, color: 'rgba(245,158,11,0.5)' }] } } },
        { value: 0, itemStyle: { color: { type: 'linear', x: 0, y: 0, x2: 0, y2: 1,
          colorStops: [{ offset: 0, color: palette[4] }, { offset: 1, color: 'rgba(16,185,129,0.5)' }] } } },
      ],
      itemStyle: { borderRadius: [6, 6, 0, 0] },
    }],
  }
}

// 事件相关帖子时间趋势 · 小折线
export function miniTrendOption(data: number[]): EChartsOption {
  return {
    tooltip: { trigger: 'axis', backgroundColor: 'rgba(255,255,255,0.96)', borderColor: '#e2e8f0', borderWidth: 1, textStyle: { color: '#334155', fontSize: 12 }, extraCssText: 'box-shadow: 0 6px 24px rgba(15,23,42,0.08); backdrop-filter: blur(6px);' },
    grid: { left: 30, right: 10, top: 10, bottom: 24, containLabel: true },
    xAxis: {
      type: 'category', data: data.map((_, i) => `${i + 1}天前`).reverse(), boundaryGap: false,
      axisLine: { lineStyle: { color: '#e2e8f0' } },
      axisTick: { show: false },
      axisLabel: { color: '#94a3b8', fontSize: 10 },
    },
    yAxis: {
      type: 'value',
      splitLine: { lineStyle: { color: '#f1f5f9' } },
      axisLabel: { color: '#94a3b8', fontSize: 10 },
    },
    series: [{
      type: 'line', smooth: true, symbol: 'circle', symbolSize: 5,
      data,
      lineStyle: { width: 2.5, color: palette[3] },
      itemStyle: { color: palette[3] },
      areaStyle: {
        color: { type: 'linear', x: 0, y: 0, x2: 0, y2: 1,
          colorStops: [{ offset: 0, color: 'rgba(244,63,94,0.25)' }, { offset: 1, color: 'rgba(244,63,94,0)' }] },
      },
    }],
  }
}

// 风险雷达图
export function radarOption(): EChartsOption {
  return {
    tooltip: { trigger: 'item', backgroundColor: 'rgba(255,255,255,0.96)', borderColor: '#e2e8f0', borderWidth: 1, textStyle: { color: '#334155', fontSize: 12 }, extraCssText: 'box-shadow: 0 6px 24px rgba(15,23,42,0.08); backdrop-filter: blur(6px);' },
    radar: {
      indicator: [
        { name: '讨论量', max: 100 },
        { name: '增长速度', max: 100 },
        { name: '负面情绪', max: 100 },
        { name: '事件类型', max: 100 },
        { name: '影响范围', max: 100 },
        { name: '紧急程度', max: 100 },
      ],
      radius: '65%',
      axisName: { color: '#64748b', fontSize: 12 },
      splitLine: { lineStyle: { color: '#e2e8f0' } },
      splitArea: { areaStyle: { color: ['#fff', '#f8fafc'] } },
      axisLine: { lineStyle: { color: '#e2e8f0' } },
    },
    series: [{
      type: 'radar',
      data: [{
        value: [85, 78, 72, 90, 65, 80],
        name: '风险维度',
        areaStyle: { color: 'rgba(244,63,94,0.18)' },
        lineStyle: { color: palette[3], width: 2 },
        itemStyle: { color: palette[3] },
      }],
    }],
  }
}

// 统计卡迷你 sparkline · 无坐标轴极简折线
export function sparklineOption(data: number[], color: string): EChartsOption {
  return {
    grid: { left: 0, right: 0, top: 4, bottom: 0 },
    xAxis: { type: 'category', show: false, boundaryGap: false, data: data.map((_, i) => i) },
    yAxis: { type: 'value', show: false, min: 'dataMin', max: 'dataMax' },
    series: [{
      type: 'line', smooth: true, symbol: 'none', data,
      lineStyle: { width: 2, color },
      areaStyle: {
        color: { type: 'linear', x: 0, y: 0, x2: 0, y2: 1,
          colorStops: [
            { offset: 0, color: color + '40' },
            { offset: 1, color: color + '00' },
          ] },
      },
    }],
  }
}
