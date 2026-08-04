<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { settingsApi } from '@/utils/api'
import LoadingSpinner from '@/components/LoadingSpinner.vue'
import ErrorState from '@/components/ErrorState.vue'
import { toast } from '@/utils/toast'

const loading = ref(false)
const loadError = ref('')
const saving = ref(false)
const saveMsg = ref('')
const categoryDialog = ref<'add' | 'edit' | 'delete' | null>(null)
const categoryDialogIndex = ref(-1)
const categoryDraftName = ref('')
const categoryDraftKeywords = ref('')
const saveConfirmOpen = ref(false)

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

const categories = ref<string[]>([])
const sources = ref<any[]>([])

function unwrap(res: any) {
  if (res && typeof res === 'object' && (res.code !== undefined || res.success !== undefined) && res.data !== undefined) return res.data
  return res
}

async function loadSettings() {
  loading.value = true
  loadError.value = ''
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
    console.warn('设置数据加载失败', err)
    loadError.value = '系统设置暂时无法获取，请检查服务连接后重试。'
  } finally {
    loading.value = false
  }
}

onMounted(loadSettings)

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
    const jobStarted = Boolean(d.reanalysisJob)
    const externalMode = d.reanalysisJob?.mode === 'EXTERNAL_CLASSIFIED'
    saveMsg.value = jobStarted
      ? (externalMode
          ? '设置已保存，后台仅刷新事件聚合，现有最终分类不会被覆盖'
          : '设置已保存，后台重新分析已启动，可在数据管理中查看进度')
      : (d.message || '设置已保存')
    toast.success(jobStarted
      ? (externalMode ? '设置已保存，事件聚合刷新已启动' : '设置已保存，重新分析任务已启动')
      : '设置已保存')
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
  categoryDialogIndex.value = index
  categoryDraftName.value = categories.value[index]
  categoryDialog.value = 'delete'
}

// 新增分类
function addCategory() {
  categoryDialogIndex.value = -1
  categoryDraftName.value = ''
  categoryDraftKeywords.value = ''
  categoryDialog.value = 'add'
}

// 编辑分类
function editCategory(index: number) {
  const oldName = categories.value[index]
  if (builtinCategories.value.includes(oldName)) {
    toast.error('内置分类名称不可编辑；可以删除以停用，或新增自定义分类')
    return
  }
  categoryDialogIndex.value = index
  categoryDraftName.value = oldName
  categoryDraftKeywords.value = (categoryRules.value[oldName] || []).join('，')
  categoryDialog.value = 'edit'
}

function confirmCategoryDialog() {
  const index = categoryDialogIndex.value
  if (categoryDialog.value === 'delete') {
    const name = categories.value[index]
    categories.value.splice(index, 1)
    delete categoryRules.value[name]
    categoryDialog.value = null
    toast.success('分类已删除，保存设置后生效')
    return
  }
  const name = categoryDraftName.value.trim()
  const keywords = parseKeywords(categoryDraftKeywords.value)
  if (!name) return toast.error('请输入分类名称')
  const oldName = index >= 0 ? categories.value[index] : ''
  if (name !== oldName && categories.value.includes(name)) return toast.error('该分类已经存在')
  if (!keywords.length) return toast.error('至少需要一个不少于 2 个字符的关键词')
  if (categoryDialog.value === 'add') categories.value.push(name)
  else categories.value[index] = name
  if (oldName && oldName !== name) delete categoryRules.value[oldName]
  categoryRules.value[name] = keywords
  categoryDialog.value = null
  toast.success(index < 0 ? '分类已新增，保存后生效' : '分类已更新，保存后生效')
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
    <ErrorState
      v-else-if="loadError"
      title="系统设置加载失败"
      :message="loadError"
      @retry="loadSettings"
    />
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
        <button @click="saveConfirmOpen = true" :disabled="saving" class="btn btn-primary">
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
        <button @click="saveConfirmOpen = true" :disabled="saving" class="btn btn-primary">
          {{ saving ? '保存并重算中...' : '保存辅助规则' }}
        </button>
        <span v-if="saveMsg" class="text-sm text-emerald-600">{{ saveMsg }}</span>
      </div>
    </div>

    <Teleport to="body">
      <div v-if="categoryDialog" class="fixed inset-0 z-[95] flex items-center justify-center p-5" @keydown.esc="categoryDialog = null">
        <button class="absolute inset-0 bg-slate-950/40" aria-label="关闭分类编辑" @click="categoryDialog = null"></button>
        <section class="relative w-full max-w-lg bg-white shadow-2xl p-6" role="dialog" aria-modal="true" aria-labelledby="category-dialog-title">
          <h2 id="category-dialog-title" class="text-lg font-semibold text-slate-900">
            {{ categoryDialog === 'add' ? '新增安全分类' : categoryDialog === 'edit' ? '编辑安全分类' : '删除安全分类' }}
          </h2>
          <template v-if="categoryDialog !== 'delete'">
            <label class="block mt-5">
              <span class="text-sm text-slate-600 block mb-1.5">分类名称</span>
              <input v-model="categoryDraftName" class="input w-full" maxlength="40" placeholder="例如：网络与账号安全" />
            </label>
            <label class="block mt-4">
              <span class="text-sm text-slate-600 block mb-1.5">识别关键词</span>
              <textarea v-model="categoryDraftKeywords" rows="4" class="input w-full resize-none" placeholder="多个关键词用逗号分隔，例如：账号泄露，钓鱼邮件"></textarea>
            </label>
          </template>
          <p v-else class="mt-5 text-sm text-slate-700">确定删除“{{ categoryDraftName }}”吗？保存设置后该分类将停止识别。</p>
          <div class="flex justify-end gap-3 mt-6">
            <button class="btn btn-ghost" type="button" @click="categoryDialog = null">取消</button>
            <button :class="['btn', categoryDialog === 'delete' ? 'text-white bg-rose-600' : 'btn-primary']" type="button" @click="confirmCategoryDialog">
              {{ categoryDialog === 'delete' ? '确认删除' : '确认' }}
            </button>
          </div>
        </section>
      </div>

      <div v-if="saveConfirmOpen" class="fixed inset-0 z-[96] flex items-center justify-center p-5" @keydown.esc="saveConfirmOpen = false">
        <button class="absolute inset-0 bg-slate-950/40" aria-label="关闭保存确认" @click="saveConfirmOpen = false"></button>
        <section class="relative w-full max-w-lg bg-white shadow-2xl p-6" role="dialog" aria-modal="true" aria-labelledby="save-settings-title">
          <h2 id="save-settings-title" class="text-lg font-semibold text-slate-900">保存设置并刷新数据状态？</h2>
          <p class="text-sm text-slate-600 mt-3 leading-6">
            本次保存包含 {{ categories.length }} 个启用分类和 {{ parseKeywords(urgentKeywordsText).length }} 个高危信号词。
            外部最终分类模式下只刷新事件聚合，不覆盖现有分类；本地规则模式下会重新计算帖子、评论和事件。
          </p>
          <div class="flex justify-end gap-3 mt-6">
            <button class="btn btn-ghost" type="button" @click="saveConfirmOpen = false">取消</button>
            <button class="btn btn-primary" type="button" @click="saveConfirmOpen = false; saveSettings()">确认保存</button>
          </div>
        </section>
      </div>
    </Teleport>
  </div>
</template>
