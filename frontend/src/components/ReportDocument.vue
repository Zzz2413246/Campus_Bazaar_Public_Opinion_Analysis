<script setup lang="ts">
import { computed } from 'vue'
import AppIcon from '@/components/AppIcon.vue'

const props = defineProps<{
  title: string
  detail: any
}>()

const meta = computed(() => props.detail?.meta || {})
const overview = computed(() => props.detail?.overview || {})
const emotion = computed(() => props.detail?.emotion || {})
const categories = computed(() => Object.entries(props.detail?.categories || {})
  .map(([name, count]) => ({ name, count: Number(count || 0) }))
  .sort((a, b) => b.count - a.count))
const keyEvents = computed(() => Array.isArray(props.detail?.keyEvents) ? props.detail.keyEvents.slice(0, 5) : [])
const discussions = computed(() => Array.isArray(props.detail?.relatedDiscussions) ? props.detail.relatedDiscussions.slice(0, 4) : [])
const recommendations = computed(() => Array.isArray(props.detail?.recommendations) ? props.detail.recommendations : [])
const totalEmotion = computed(() => Number(emotion.value.positive || 0)
  + Number(emotion.value.neutral || 0) + Number(emotion.value.negative || 0))

function formatGeneratedAt(value: unknown) {
  if (!value) return new Date().toLocaleString('zh-CN')
  const date = new Date(String(value))
  return Number.isNaN(date.getTime()) ? String(value) : date.toLocaleString('zh-CN')
}

function percent(value: unknown) {
  const total = totalEmotion.value
  return total > 0 ? Math.round(Number(value || 0) * 100 / total) : 0
}

function riskClass(risk: string) {
  return risk === '高' ? 'report-risk-high' : risk === '中' ? 'report-risk-medium' : 'report-risk-low'
}
</script>

<template>
  <div class="report-document">
    <article class="report-pdf-page">
      <header class="report-letterhead">
        <div class="report-brand">
          <span class="report-logo"><AppIcon name="shield" :size="22" /></span>
          <div>
            <div class="report-institution">{{ meta.institution || '校园安全舆情分析平台' }}</div>
            <div class="report-brand-sub">CAMPUS PUBLIC OPINION ANALYSIS</div>
          </div>
        </div>
        <span class="report-confidentiality">{{ meta.confidentiality || '内部资料' }}</span>
      </header>

      <div class="report-title-area">
        <div class="report-type">{{ meta.typeLabel || '校园安全舆情报告' }}</div>
        <h1>{{ title || '校园安全舆情报告' }}</h1>
        <p>{{ detail?.periodLabel || '' }}</p>
      </div>

      <dl class="report-meta-grid">
        <div><dt>报告编号</dt><dd>{{ meta.reportNumber || '待生成' }}</dd></div>
        <div><dt>生成时间</dt><dd>{{ formatGeneratedAt(meta.generatedAt) }}</dd></div>
        <div><dt>数据范围</dt><dd>{{ meta.dataBasis || '校园集市帖子及关联评论' }}</dd></div>
        <div><dt>报告状态</dt><dd>系统生成 · 待人工复核</dd></div>
      </dl>

      <section class="report-section report-block">
        <h2><span>01</span> 数据概览</h2>
        <div class="report-summary-grid">
          <div><strong>{{ overview.postCount || 0 }}</strong><span>监测帖子</span></div>
          <div><strong>{{ overview.eventCount || 0 }}</strong><span>安全事件</span></div>
          <div class="is-risk"><strong>{{ overview.highRiskCount || 0 }}</strong><span>高风险事件</span></div>
          <div><strong>{{ overview.negativeCount || 0 }}</strong><span>负面帖子</span></div>
        </div>
      </section>

      <section class="report-section report-block">
        <h2><span>02</span> 总体情况</h2>
        <p class="report-paragraph">{{ detail?.overviewText || '本期暂无可用的总体情况摘要。' }}</p>
      </section>

      <section class="report-section report-block">
        <h2><span>03</span> 安全议题分布</h2>
        <table class="report-table">
          <thead><tr><th>序号</th><th>安全议题</th><th>帖子数量</th><th>占比</th></tr></thead>
          <tbody>
            <tr v-for="(item, index) in categories.slice(0, 8)" :key="item.name">
              <td>{{ String(index + 1).padStart(2, '0') }}</td>
              <td>{{ item.name }}</td>
              <td>{{ item.count }} 条</td>
              <td>{{ overview.postCount ? Math.round(item.count * 100 / overview.postCount) : 0 }}%</td>
            </tr>
            <tr v-if="!categories.length"><td colspan="4" class="report-empty-cell">暂无安全议题数据</td></tr>
          </tbody>
        </table>
      </section>

      <section class="report-section report-block">
        <h2><span>04</span> 情绪构成</h2>
        <div class="report-emotion-grid">
          <div class="positive"><span>正面</span><strong>{{ emotion.positive || 0 }} 条</strong><small>{{ percent(emotion.positive) }}%</small></div>
          <div class="neutral"><span>中性</span><strong>{{ emotion.neutral || 0 }} 条</strong><small>{{ percent(emotion.neutral) }}%</small></div>
          <div class="negative"><span>负面</span><strong>{{ emotion.negative || 0 }} 条</strong><small>{{ percent(emotion.negative) }}%</small></div>
        </div>
      </section>

      <footer class="report-footer"><span>{{ meta.institution || '校园安全舆情分析平台' }}</span><span>第 1 页 / 共 2 页</span></footer>
    </article>

    <article class="report-pdf-page">
      <header class="report-letterhead report-letterhead-compact">
        <div class="report-brand">
          <span class="report-logo"><AppIcon name="shield" :size="20" /></span>
          <div class="report-institution">{{ meta.institution || '校园安全舆情分析平台' }}</div>
        </div>
        <div class="report-running-title">{{ title }}</div>
      </header>

      <section class="report-section report-block">
        <h2><span>05</span> 重点事件</h2>
        <table class="report-table report-event-table">
          <thead><tr><th>风险</th><th>事件名称</th><th>类别</th><th>讨论量</th><th>处置状态</th></tr></thead>
          <tbody>
            <tr v-for="event in keyEvents" :key="event.id || event.title">
              <td><span :class="['report-risk', riskClass(event.risk)]">{{ event.risk }}</span></td>
              <td><strong>{{ event.title }}</strong><small v-if="event.summary">{{ event.summary }}</small></td>
              <td>{{ event.category }}</td>
              <td>{{ event.postCount || 0 }} 条</td>
              <td>{{ event.status || '待核实' }}</td>
            </tr>
            <tr v-if="!keyEvents.length"><td colspan="5" class="report-empty-cell">本期无重点安全事件</td></tr>
          </tbody>
        </table>
      </section>

      <section v-if="discussions.length" class="report-section report-block">
        <h2><span>06</span> 相关讨论摘要</h2>
        <ol class="report-discussions">
          <li v-for="item in discussions" :key="item.id">
            <span>{{ item.title }}</span>
            <small>{{ item.risk }}风险 · {{ item.emotion }}</small>
          </li>
        </ol>
      </section>

      <section class="report-section report-block">
        <h2><span>{{ discussions.length ? '07' : '06' }}</span> 研判与处置建议</h2>
        <div class="report-recommendation">
          <div class="report-recommendation-title"><AppIcon name="alert-triangle" :size="18" /> 工作建议</div>
          <ol>
            <li v-for="(item, index) in recommendations" :key="index">{{ item }}</li>
            <li v-if="!recommendations.length">建议持续关注舆情变化，并根据实际情况开展人工研判。</li>
          </ol>
        </div>
      </section>

      <section class="report-signoff report-block">
        <p>本报告由系统依据当前数据库自动生成，仅供内部研判参考。具体处置结论应以相关部门人工核实结果为准。</p>
        <div><span>复核人：________________</span><span>复核日期：________________</span></div>
      </section>

      <footer class="report-footer"><span>{{ meta.reportNumber || '' }}</span><span>第 2 页 / 共 2 页</span></footer>
    </article>
  </div>
</template>

<style scoped>
.report-document { display: flex; flex-direction: column; align-items: center; gap: 24px; }
.report-pdf-page { position: relative; box-sizing: border-box; width: 794px; height: 1123px; padding: 48px 58px 54px; overflow: hidden; background: #fff; color: #1e293b; font-family: "Microsoft YaHei", "PingFang SC", Arial, sans-serif; box-shadow: 0 8px 30px rgba(15, 23, 42, .12); }
.report-pdf-page::before { content: ""; position: absolute; inset: 0 0 auto; height: 7px; background: linear-gradient(90deg, #1d4ed8, #0891b2); }
.report-letterhead { display: flex; align-items: center; justify-content: space-between; padding-bottom: 16px; border-bottom: 1px solid #cbd5e1; }
.report-letterhead-compact { padding-bottom: 12px; }
.report-brand { display: flex; align-items: center; gap: 11px; }
.report-logo { display: inline-flex; align-items: center; justify-content: center; width: 38px; height: 38px; color: #1d4ed8; background: #eff6ff; border: 1px solid #bfdbfe; }
.report-institution { font-size: 16px; line-height: 1.25; font-weight: 700; letter-spacing: .08em; color: #0f172a; }
.report-brand-sub { margin-top: 3px; font-size: 8px; letter-spacing: .16em; color: #64748b; }
.report-confidentiality { padding: 5px 10px; border: 1px solid #fca5a5; color: #b91c1c; font-size: 12px; font-weight: 700; letter-spacing: .15em; }
.report-running-title { max-width: 390px; overflow: hidden; color: #64748b; font-size: 12px; text-overflow: ellipsis; white-space: nowrap; }
.report-title-area { padding: 34px 0 25px; text-align: center; }
.report-title-area .report-type { color: #2563eb; font-size: 13px; font-weight: 700; letter-spacing: .22em; }
.report-title-area h1 { margin: 11px 0 8px; color: #0f172a; font-size: 28px; line-height: 1.45; font-weight: 700; letter-spacing: .04em; }
.report-title-area p { margin: 0; color: #64748b; font-size: 13px; }
.report-meta-grid { display: grid; grid-template-columns: 1fr 1fr; margin: 0 0 24px; border-top: 1px solid #cbd5e1; border-left: 1px solid #cbd5e1; }
.report-meta-grid div { display: grid; grid-template-columns: 82px 1fr; min-height: 38px; border-right: 1px solid #cbd5e1; border-bottom: 1px solid #cbd5e1; }
.report-meta-grid dt, .report-meta-grid dd { display: flex; align-items: center; margin: 0; padding: 7px 10px; font-size: 11px; }
.report-meta-grid dt { background: #f8fafc; color: #64748b; }
.report-meta-grid dd { color: #334155; font-weight: 500; }
.report-section { margin-top: 22px; }
.report-section h2 { display: flex; align-items: center; gap: 10px; margin: 0 0 12px; color: #0f172a; font-size: 16px; line-height: 1.4; font-weight: 700; }
.report-section h2 span { display: inline-flex; align-items: center; justify-content: center; width: 28px; height: 24px; background: #1d4ed8; color: white; font-size: 10px; letter-spacing: .05em; }
.report-summary-grid { display: grid; grid-template-columns: repeat(4, 1fr); border: 1px solid #dbeafe; background: #f8fbff; }
.report-summary-grid div { padding: 13px 10px; text-align: center; border-right: 1px solid #dbeafe; }
.report-summary-grid div:last-child { border-right: 0; }
.report-summary-grid strong { display: block; color: #1e3a8a; font-size: 22px; line-height: 1.2; }
.report-summary-grid .is-risk strong { color: #be123c; }
.report-summary-grid span { display: block; margin-top: 5px; color: #64748b; font-size: 10px; }
.report-paragraph { margin: 0; padding: 12px 14px; border-left: 3px solid #3b82f6; background: #f8fafc; color: #334155; font-size: 12px; line-height: 1.8; text-align: justify; }
.report-table { width: 100%; border-collapse: collapse; table-layout: fixed; }
.report-table th, .report-table td { padding: 8px 10px; border: 1px solid #cbd5e1; color: #334155; font-size: 10.5px; line-height: 1.45; text-align: left; vertical-align: middle; }
.report-table th { background: #eff6ff; color: #1e3a8a; font-weight: 700; }
.report-table th:first-child, .report-table td:first-child { width: 45px; text-align: center; }
.report-empty-cell { padding: 18px !important; color: #94a3b8 !important; text-align: center !important; }
.report-emotion-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 10px; }
.report-emotion-grid div { display: grid; grid-template-columns: 1fr auto; gap: 4px 12px; padding: 10px 12px; border: 1px solid #e2e8f0; }
.report-emotion-grid span { grid-column: 1 / -1; color: #64748b; font-size: 10px; }
.report-emotion-grid strong { color: #334155; font-size: 13px; }
.report-emotion-grid small { font-size: 11px; }
.report-emotion-grid .positive small { color: #059669; }.report-emotion-grid .neutral small { color: #64748b; }.report-emotion-grid .negative small { color: #e11d48; }
.report-event-table th:nth-child(1) { width: 48px; }.report-event-table th:nth-child(3) { width: 105px; }.report-event-table th:nth-child(4) { width: 62px; }.report-event-table th:nth-child(5) { width: 72px; }
.report-event-table td strong { display: block; color: #1e293b; font-size: 10.5px; }
.report-event-table td small { display: -webkit-box; margin-top: 3px; overflow: hidden; color: #64748b; font-size: 9px; -webkit-box-orient: vertical; -webkit-line-clamp: 2; }
.report-risk { display: inline-flex; justify-content: center; min-width: 25px; padding: 3px 5px; font-size: 9px; font-weight: 700; }
.report-risk-high { background: #ffe4e6; color: #be123c; }.report-risk-medium { background: #fef3c7; color: #b45309; }.report-risk-low { background: #dcfce7; color: #15803d; }
.report-discussions { margin: 0; padding: 0; list-style: none; border: 1px solid #e2e8f0; }
.report-discussions li { display: flex; align-items: center; justify-content: space-between; gap: 16px; padding: 9px 12px; border-bottom: 1px solid #e2e8f0; color: #334155; font-size: 10.5px; }
.report-discussions li:last-child { border-bottom: 0; }.report-discussions span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }.report-discussions small { flex: 0 0 auto; color: #64748b; font-size: 9.5px; }
.report-recommendation { padding: 14px 16px; border: 1px solid #fde68a; background: #fffbeb; }
.report-recommendation-title { display: flex; align-items: center; gap: 7px; color: #92400e; font-size: 12px; font-weight: 700; }
.report-recommendation ol { margin: 8px 0 0; padding-left: 20px; color: #78350f; font-size: 11px; line-height: 1.8; }
.report-signoff { margin-top: 22px; padding: 13px 15px; background: #f8fafc; color: #64748b; font-size: 10px; line-height: 1.7; }
.report-signoff p { margin: 0; }.report-signoff div { display: flex; justify-content: flex-end; gap: 34px; margin-top: 14px; color: #475569; }
.report-footer { position: absolute; right: 58px; bottom: 25px; left: 58px; display: flex; justify-content: space-between; padding-top: 9px; border-top: 1px solid #cbd5e1; color: #94a3b8; font-size: 9px; }
@media (max-width: 900px) { .report-document { align-items: flex-start; } }
</style>
