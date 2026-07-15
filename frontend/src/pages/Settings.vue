<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { settingsApi } from '@/utils/api'

const loading = ref(false)
const saving = ref(false)
const saveMsg = ref('')

const riskThresholds = ref({ high: 70, medium: 40 })

const categories = ref([
  '诈骗与财产安全', '治安与人身安全', '消防与用电安全', '校园交通安全',
  '宿舍设施问题', '食堂及校园设施', '突发事件与异常', '学生投诉与诉求',
])

const sources = ref([
  { name: '校园集市数据', desc: '最后同步：2026-07-14 10:30', status: '已连接', ok: true },
  { name: '外部社交媒体数据', desc: '微博 · 小红书 · B站', status: '已连接', ok: true },
  { name: '项目组已有数据', desc: '约10万条社交媒体评论', status: '待导入', ok: false },
])

function unwrap(res: any) {
  if (res && typeof res === 'object' && (res.code !== undefined || res.success !== undefined) && res.data !== undefined) return res.data
  return res
}

onMounted(async () => {
  loading.value = true
  try {
    const res: any = await settingsApi.get()
    const d = unwrap(res) || {}
    if (d.riskThresholds) {
      if (d.riskThresholds.high !== undefined) riskThresholds.value.high = d.riskThresholds.high
      if (d.riskThresholds.medium !== undefined) riskThresholds.value.medium = d.riskThresholds.medium
    }
    if (Array.isArray(d.categories) && d.categories.length) {
      categories.value = d.categories.map((c: any) => (typeof c === 'string' ? c : (c.name ?? c.category ?? '')))
    }
    if (Array.isArray(d.sources) && d.sources.length) {
      sources.value = d.sources.map((s: any) => ({
        name: s.name ?? s.source ?? '',
        desc: s.desc ?? s.description ?? s.lastSync ?? '',
        status: s.status ?? (s.ok ? '已连接' : '待导入'),
        ok: s.ok ?? (s.status === '已连接' || s.status === 'connected'),
      }))
    }
  } catch (err) {
    console.warn('设置数据加载失败，使用默认数据', err)
  } finally {
    loading.value = false
  }
})

// 保存设置
async function saveSettings() {
  saving.value = true
  saveMsg.value = ''
  try {
    await settingsApi.update({
      riskThresholds: { high: riskThresholds.value.high, medium: riskThresholds.value.medium },
      categories: categories.value,
    })
    saveMsg.value = '设置已保存'
    setTimeout(() => { saveMsg.value = '' }, 3000)
  } catch (err) {
    console.warn('保存设置失败', err)
    saveMsg.value = '保存失败，请重试'
  } finally {
    saving.value = false
  }
}

// 删除分类
function removeCategory(index: number) {
  if (confirm('确定要删除该分类吗？')) {
    categories.value.splice(index, 1)
  }
}

// 新增分类
function addCategory() {
  const name = prompt('请输入新分类名称：')
  if (name && name.trim()) {
    categories.value.push(name.trim())
  }
}

// 编辑分类
function editCategory(index: number) {
  const newName = prompt('请输入新的分类名称：', categories.value[index])
  if (newName && newName.trim()) {
    categories.value[index] = newName.trim()
  }
}
</script>

<template>
  <div class="page">
    <!-- 加载状态 -->
    <div v-if="loading" class="flex items-center justify-center gap-2 py-3 text-sm text-slate-400">
      <span class="w-4 h-4 border-2 border-slate-300 border-t-brand-500 rounded-full animate-spin"></span>
      数据加载中...
    </div>
    <div class="grid grid-cols-1 xl:grid-cols-2 gap-5">
      <!-- 风险评分阈值 -->
      <div class="card card-pad">
        <h3 class="section-title mb-4">风险评分阈值</h3>
        <div class="space-y-6">
          <div>
            <div class="flex items-center justify-between mb-2">
              <span class="text-sm text-slate-700">高风险阈值</span>
              <span class="badge badge-high">≥ {{ riskThresholds.high }} 分</span>
            </div>
            <input type="range" v-model.number="riskThresholds.high" min="50" max="100" class="w-full accent-rose-500" />
          </div>
          <div>
            <div class="flex items-center justify-between mb-2">
              <span class="text-sm text-slate-700">中风险阈值</span>
              <span class="badge badge-medium">≥ {{ riskThresholds.medium }} 分</span>
            </div>
            <input type="range" v-model.number="riskThresholds.medium" min="20" max="80" class="w-full accent-amber-500" />
          </div>
          <div class="text-sm text-slate-500 bg-slate-50/70 p-4 leading-relaxed border border-slate-100">
            评分规则：低于 <span class="font-semibold text-emerald-600">{{ riskThresholds.medium }} 分</span> 为低风险，<span class="font-semibold text-amber-600">{{ riskThresholds.medium }}–{{ riskThresholds.high }} 分</span> 为中风险，<span class="font-semibold text-rose-600">{{ riskThresholds.high }} 分</span> 及以上为高风险
          </div>
          <div class="flex items-center gap-3 mt-4">
            <button @click="saveSettings" :disabled="saving" class="btn btn-primary">
              {{ saving ? '保存中...' : '保存设置' }}
            </button>
            <span v-if="saveMsg" class="text-sm text-emerald-600">{{ saveMsg }}</span>
          </div>
        </div>
      </div>

      <!-- 数据源配置 -->
      <div class="card card-pad">
        <h3 class="section-title mb-4">数据源配置</h3>
        <div class="space-y-2">
          <div
            v-for="s in sources"
            :key="s.name"
            class="flex items-center justify-between p-3 bg-slate-50/70 border border-slate-100"
          >
            <div class="min-w-0">
              <div class="text-sm text-slate-700 font-medium">{{ s.name }}</div>
              <div class="text-xs text-slate-500 mt-0.5">{{ s.desc }}</div>
            </div>
            <span :class="['badge', s.ok ? 'badge-success' : 'badge-warn']">
              <span :class="['dot', s.ok ? 'dot-low' : 'dot-medium']"></span>{{ s.status }}
            </span>
          </div>
        </div>
      </div>
    </div>

    <!-- 安全议题分类 -->
    <div class="card card-pad">
      <h3 class="section-title mb-4">安全议题分类</h3>
      <div class="grid grid-cols-1 sm:grid-cols-2 gap-2">
        <div v-for="(c, i) in categories" :key="i" class="flex items-center justify-between p-3 bg-slate-50/70 border border-slate-100 hover:bg-slate-100/70 transition-colors">
          <div class="flex items-center gap-3 min-w-0">
            <span class="w-7 h-7 rounded-lg bg-brand-50 text-brand-600 flex items-center justify-center text-xs font-semibold flex-shrink-0">{{ i + 1 }}</span>
            <span class="text-sm text-slate-700 truncate">{{ c }}</span>
          </div>
          <div class="flex gap-1 flex-shrink-0">
            <button @click="editCategory(i)" class="text-xs text-slate-400 hover:text-brand-500 cursor-pointer px-2 py-1 hover:bg-white transition-colors">编辑</button>
            <button @click="removeCategory(i)" class="text-xs text-slate-400 hover:text-rose-500 cursor-pointer px-2 py-1 hover:bg-white transition-colors">删除</button>
          </div>
        </div>
      </div>
      <button @click="addCategory" class="btn btn-ghost mt-4 w-full">+ 新增分类</button>
      <div class="flex items-center gap-3 mt-3">
        <button @click="saveSettings" :disabled="saving" class="btn btn-primary">
          {{ saving ? '保存中...' : '保存分类' }}
        </button>
        <span v-if="saveMsg" class="text-sm text-emerald-600">{{ saveMsg }}</span>
      </div>
    </div>
  </div>
</template>
