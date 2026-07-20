<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { settingsApi } from '@/utils/api'
import LoadingSpinner from '@/components/LoadingSpinner.vue'
import { toast } from '@/utils/toast'

const loading = ref(false)
const saving = ref(false)
const saveMsg = ref('')

const categoryRules = ref<Record<string, string[]>>({})
const builtinCategories = ref<string[]>([])
const alertRules = ref({
  minPostCount: 4,
  negativeRatioPercent: 35,
  minInteractions: 50,
  minViews: 5000,
  burstWindowHours: 2,
  burstPostCount: 4,
  repeatedLocationPostCount: 3,
})
const urgentKeywordsText = ref('聚集，线下行动，报警，起火，爆炸，持刀，跳楼，轻生，食物中毒，救护车')

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
    if (Array.isArray(d.categories) && d.categories.length) {
      categories.value = d.categories.map((c: any) => (typeof c === 'string' ? c : (c.name ?? c.category ?? '')))
    }
    if (d.categoryRules && typeof d.categoryRules === 'object') {
      categoryRules.value = Object.fromEntries(
        Object.entries(d.categoryRules).map(([name, keywords]: [string, any]) => [
          name,
          Array.isArray(keywords) ? keywords.map(String) : [],
        ])
      )
    }
    if (Array.isArray(d.builtinCategories)) {
      builtinCategories.value = d.builtinCategories.map(String)
    }
    if (d.alertRules && typeof d.alertRules === 'object') {
      alertRules.value = {
        ...alertRules.value,
        ...Object.fromEntries(
          Object.entries(d.alertRules)
            .filter(([key]) => key in alertRules.value)
            .map(([key, value]) => [key, Number(value)])
        ),
      }
      if (Array.isArray(d.alertRules.urgentKeywords)) {
        urgentKeywordsText.value = d.alertRules.urgentKeywords.map(String).join('，')
      }
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
    const res: any = await settingsApi.update({
      categories: categories.value,
      categoryRules: categoryRules.value,
      alertRules: {
        ...alertRules.value,
        urgentKeywords: parseKeywords(urgentKeywordsText.value),
      },
    })
    const d = unwrap(res) || res || {}
    if (Array.isArray(d.categories)) categories.value = d.categories.map(String)
    saveMsg.value = '分类已保存，分析结果已刷新'
    // 额外通过 toast 提示保存成功
    toast.success('设置已保存')
    setTimeout(() => { saveMsg.value = '' }, 3000)
  } catch (err) {
    console.warn('保存设置失败', err)
    saveMsg.value = '保存失败，请重试'
    toast.error('保存失败，请重试')
  } finally {
    saving.value = false
  }
}

// 删除分类
function removeCategory(index: number) {
  if (confirm('确定要删除该分类吗？')) {
    const name = categories.value[index]
    categories.value.splice(index, 1)
    delete categoryRules.value[name]
    // 删除分类成功提示
    toast.success('分类已删除')
  }
}

// 新增分类
function addCategory() {
  const name = prompt('请输入新分类名称：')
  const normalized = name?.trim()
  if (normalized) {
    if (categories.value.includes(normalized)) {
      toast.error('该分类已经存在')
      return
    }
    const rawKeywords = prompt('请输入识别关键词，多个词用逗号分隔：\n建议使用明确短语，例如“网络攻击、账号泄露、钓鱼邮件”')
    const keywords = parseKeywords(rawKeywords || '')
    if (!keywords.length) {
      toast.error('新增分类至少需要一个不少于2个字符的关键词')
      return
    }
    categories.value.push(normalized)
    categoryRules.value[normalized] = keywords
    // 新增分类成功提示
    toast.success('分类已新增')
  }
}

// 编辑分类
function editCategory(index: number) {
  const oldName = categories.value[index]
  if (builtinCategories.value.includes(oldName)) {
    toast.error('内置分类名称不可编辑；可以删除以停用，或新增自定义分类')
    return
  }
  const newName = prompt('请输入新的分类名称：', categories.value[index])
  const normalized = newName?.trim()
  if (normalized) {
    if (normalized !== oldName && categories.value.includes(normalized)) {
      toast.error('该分类已经存在')
      return
    }
    const oldKeywords = categoryRules.value[oldName] || []
    const rawKeywords = prompt('请输入识别关键词，多个词用逗号分隔：', oldKeywords.join('，'))
    const keywords = parseKeywords(rawKeywords || '')
    if (!keywords.length) {
      toast.error('自定义分类至少需要一个不少于2个字符的关键词')
      return
    }
    categories.value[index] = normalized
    if (normalized !== oldName) delete categoryRules.value[oldName]
    categoryRules.value[normalized] = keywords
  }
}

function parseKeywords(raw: string) {
  return [...new Set(raw.split(/[,，]/).map(v => v.trim()).filter(v => v.length >= 2))].slice(0, 30)
}

function isBuiltin(name: string) {
  return builtinCategories.value.includes(name)
}
</script>

<template>
  <div class="page large-detail-page">
    <!-- 加载状态 -->
    <LoadingSpinner v-if="loading" />
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

    <!-- 安全议题分类 -->
    <div class="card card-pad">
      <h3 class="section-title mb-4">安全议题分类</h3>
      <div class="grid grid-cols-1 sm:grid-cols-2 gap-2">
        <div v-for="(c, i) in categories" :key="i" class="flex items-center justify-between p-3 bg-slate-50/70 border border-slate-100 hover:bg-slate-100/70 transition-colors">
          <div class="flex items-center gap-3 min-w-0">
            <span class="w-7 h-7 rounded-lg bg-brand-50 text-brand-600 flex items-center justify-center text-xs font-semibold flex-shrink-0">{{ i + 1 }}</span>
            <div class="min-w-0">
              <div class="text-sm text-slate-700 truncate">{{ c }}</div>
              <div class="text-xs text-slate-400 truncate mt-0.5">
                {{ isBuiltin(c) ? '内置高精度规则' : categoryRules[c]?.length ? `关键词：${categoryRules[c].join('、')}` : '仅作为展示分类，未配置关键词' }}
              </div>
            </div>
          </div>
          <div class="flex gap-1 flex-shrink-0">
            <button v-if="!isBuiltin(c)" @click="editCategory(i)" class="text-xs text-slate-400 hover:text-brand-500 cursor-pointer px-2 py-1 hover:bg-white transition-colors">编辑</button>
            <button @click="removeCategory(i)" class="text-xs text-slate-400 hover:text-rose-500 cursor-pointer px-2 py-1 hover:bg-white transition-colors">删除</button>
          </div>
        </div>
      </div>
      <button @click="addCategory" class="btn btn-ghost mt-4 w-full">+ 新增分类及识别关键词</button>
      <div class="flex items-center gap-3 mt-3">
        <button @click="saveSettings" :disabled="saving" class="btn btn-primary">
          {{ saving ? '保存中...' : '保存分类' }}
        </button>
        <span v-if="saveMsg" class="text-sm text-emerald-600">{{ saveMsg }}</span>
      </div>
    </div>

    <!-- 风险预警规则 -->
    <div class="card card-pad">
      <div class="flex items-start justify-between gap-4 mb-5 flex-wrap">
        <div>
          <h3 class="section-title">预警辅助规则</h3>
          <p class="section-sub mt-1">是否属于中高风险直接采用AI标签；以下规则只补充讨论量、情绪和传播等解释依据。</p>
        </div>
        <span class="badge badge-info">保存后立即生效</span>
      </div>

      <div class="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-4">
        <label class="block">
          <span class="text-sm text-slate-600 block mb-1.5">同类讨论数量</span>
          <div class="relative">
            <input v-model.number="alertRules.minPostCount" type="number" min="1" max="1000" class="input w-full pr-12" />
            <span class="absolute right-3 top-2.5 text-xs text-slate-400">条</span>
          </div>
        </label>
        <label class="block">
          <span class="text-sm text-slate-600 block mb-1.5">负面情绪占比</span>
          <div class="relative">
            <input v-model.number="alertRules.negativeRatioPercent" type="number" min="0" max="100" class="input w-full pr-12" />
            <span class="absolute right-3 top-2.5 text-xs text-slate-400">%</span>
          </div>
        </label>
        <label class="block">
          <span class="text-sm text-slate-600 block mb-1.5">评论与点赞合计</span>
          <div class="relative">
            <input v-model.number="alertRules.minInteractions" type="number" min="0" max="1000000" class="input w-full pr-12" />
            <span class="absolute right-3 top-2.5 text-xs text-slate-400">次</span>
          </div>
        </label>
        <label class="block">
          <span class="text-sm text-slate-600 block mb-1.5">累计浏览量</span>
          <div class="relative">
            <input v-model.number="alertRules.minViews" type="number" min="0" max="10000000" class="input w-full pr-16" />
            <span class="absolute right-3 top-2.5 text-xs text-slate-400">人次</span>
          </div>
        </label>
        <label class="block">
          <span class="text-sm text-slate-600 block mb-1.5">突增检测窗口</span>
          <div class="relative">
            <input v-model.number="alertRules.burstWindowHours" type="number" min="1" max="168" class="input w-full pr-12" />
            <span class="absolute right-3 top-2.5 text-xs text-slate-400">小时</span>
          </div>
        </label>
        <label class="block">
          <span class="text-sm text-slate-600 block mb-1.5">窗口内帖子阈值</span>
          <div class="relative">
            <input v-model.number="alertRules.burstPostCount" type="number" min="2" max="1000" class="input w-full pr-12" />
            <span class="absolute right-3 top-2.5 text-xs text-slate-400">条</span>
          </div>
        </label>
        <label class="block">
          <span class="text-sm text-slate-600 block mb-1.5">同地点重复次数</span>
          <div class="relative">
            <input v-model.number="alertRules.repeatedLocationPostCount" type="number" min="2" max="1000" class="input w-full pr-12" />
            <span class="absolute right-3 top-2.5 text-xs text-slate-400">次</span>
          </div>
        </label>
      </div>

      <label class="block mt-4">
        <span class="text-sm text-slate-600 block mb-1.5">高危信号词</span>
        <textarea
          v-model="urgentKeywordsText"
          rows="3"
          class="input w-full resize-none"
          placeholder="多个信号词用逗号分隔，例如：聚集、线下行动、报警、起火"
        ></textarea>
        <span class="text-xs text-slate-400 mt-1.5 block">命中任意信号词即记录一条触发依据；设置为 0 的数值型阈值视为关闭该项。</span>
      </label>

      <div class="flex items-center gap-3 mt-4">
        <button @click="saveSettings" :disabled="saving" class="btn btn-primary">
          {{ saving ? '保存并重算中...' : '保存辅助规则' }}
        </button>
        <span v-if="saveMsg" class="text-sm text-emerald-600">{{ saveMsg }}</span>
      </div>
    </div>
  </div>
</template>
