<script setup lang="ts">
import { ref, nextTick } from 'vue'
import AppIcon from '../components/AppIcon.vue'
import { assistantApi } from '@/utils/api'
import { toast } from '@/utils/toast'

const inputText = ref('')
const thinking = ref(false)

interface Message { role: 'user' | 'assistant'; content: string; time: string }

const messages = ref<Message[]>([{
  role: 'assistant',
  content: '你好！我是校园安全智能助手\n\n我可以帮你查询和分析校园安全相关数据，比如：\n· 最近有哪些值得关注的安全问题\n· 哪些议题增长最快\n· 特定事件的风险判断依据\n· 生成简报\n\n请问你想了解什么？',
  time: '14:30',
}])

const suggestQuestions = [
  '最近一周有哪些值得关注的校园安全问题？',
  '哪些校园安全议题增长最快？',
  '最近宿舍相关问题主要集中在哪些方面？',
  '为什么西门诈骗事件被判断为高风险？',
  '生成一份本周校园安全舆情简报',
]

function unwrap(res: any) {
  if (res && typeof res === 'object' && (res.code !== undefined || res.success !== undefined) && res.data !== undefined) return res.data
  return res
}

function nowTime() {
  return new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
}

async function sendMessage(text?: string) {
  const msg = text || inputText.value
  if (!msg.trim() || thinking.value) return
  messages.value.push({ role: 'user', content: msg, time: nowTime() })
  inputText.value = ''
  thinking.value = true
  nextTick(() => { document.getElementById('chat-end')?.scrollIntoView({ behavior: 'smooth' }) })
  try {
    const res: any = await assistantApi.query(msg)
    const d = unwrap(res)
    const answer = typeof d === 'string' ? d
      : (d?.answer ?? d?.content ?? d?.reply ?? '抱歉，暂时无法获取回答，请稍后再试。')
    messages.value.push({ role: 'assistant', content: answer, time: nowTime() })
  } catch (err) {
    console.warn('助手请求失败', err)
    // 发送消息失败时通过 toast 提示
    toast.error('发送失败，请稍后重试')
    messages.value.push({
      role: 'assistant',
      content: `收到你的问题：「${msg}」\n\n抱歉，当前无法连接分析服务，请稍后再试。`,
      time: nowTime(),
    })
  } finally {
    thinking.value = false
    nextTick(() => { document.getElementById('chat-end')?.scrollIntoView({ behavior: 'smooth' }) })
  }
}
</script>

<template>
  <div class="max-w-4xl mx-auto h-[calc(100vh-9rem)] flex flex-col">
    <!-- 助手标识 -->
    <div class="flex items-center gap-3 mb-4 px-1">
      <div class="w-10 h-10 rounded-xl bg-gradient-to-br from-brand-500 to-accent-500 flex items-center justify-center text-white shadow-lg shadow-brand-600/30">
        <AppIcon name="bot" :size="22" />
      </div>
      <div>
        <div class="text-base font-semibold text-slate-800">校园安全智能助手</div>
        <div class="text-xs text-slate-500 flex items-center gap-1.5">
          <span class="w-1.5 h-1.5 rounded-full bg-emerald-500"></span>
          基于平台结构化数据与 LLM · 在线
        </div>
      </div>
    </div>

    <!-- 对话区域 -->
    <div class="flex-1 overflow-y-auto space-y-5 pr-1">
      <div v-for="(msg, i) in messages" :key="i" :class="['flex gap-3', msg.role === 'user' ? 'justify-end' : 'justify-start']">
        <div v-if="msg.role === 'assistant'" class="w-9 h-9 rounded-xl bg-gradient-to-br from-brand-500 to-accent-500 flex items-center justify-center flex-shrink-0 text-white shadow-sm"><AppIcon name="bot" :size="18" /></div>
        <div :class="['max-w-[75%] px-4 py-3', msg.role === 'user' ? 'bg-gradient-to-br from-brand-600 to-brand-500 text-white text-sm shadow-sm shadow-brand-600/25' : 'bg-white border border-slate-200/70 shadow-sm text-sm text-slate-700']">
          <div class="whitespace-pre-wrap leading-relaxed">{{ msg.content }}</div>
          <div :class="['text-xs mt-2', msg.role === 'user' ? 'text-brand-100' : 'text-slate-400']">{{ msg.time }}</div>
        </div>
        <div v-if="msg.role === 'user'" class="w-9 h-9 rounded-xl bg-slate-200 flex items-center justify-center flex-shrink-0 text-slate-500"><AppIcon name="smile" :size="18" /></div>
      </div>
      <div v-if="thinking" class="flex gap-3 justify-start">
        <div class="w-9 h-9 rounded-xl bg-gradient-to-br from-brand-500 to-accent-500 flex items-center justify-center flex-shrink-0 text-white shadow-sm"><AppIcon name="bot" :size="18" /></div>
        <div class="max-w-[75%] px-4 py-3 bg-white border border-slate-200/70 shadow-sm text-sm text-slate-400">
          <span class="inline-flex items-center gap-1">
            <span class="w-1.5 h-1.5 rounded-full bg-slate-400 animate-bounce" style="animation-delay:0ms"></span>
            <span class="w-1.5 h-1.5 rounded-full bg-slate-400 animate-bounce" style="animation-delay:150ms"></span>
            <span class="w-1.5 h-1.5 rounded-full bg-slate-400 animate-bounce" style="animation-delay:300ms"></span>
            正在思考...
          </span>
        </div>
      </div>
      <div id="chat-end"></div>
    </div>

    <!-- 推荐问题 -->
    <div v-if="messages.length <= 1" class="flex flex-wrap gap-2 py-4">
      <span
        v-for="q in suggestQuestions"
        :key="q"
        @click="sendMessage(q)"
        class="text-sm px-3.5 py-2 bg-white border border-slate-200/70 shadow-sm text-slate-600 cursor-pointer hover:bg-brand-50 hover:text-brand-600 hover:border-brand-200 transition-all"
      >{{ q }}</span>
    </div>

    <!-- 输入框 -->
    <div class="bg-white border border-slate-200/70 shadow-sm shadow-slate-200/50 p-2 flex gap-2 mt-3">
      <input
        v-model="inputText"
        @keyup.enter="sendMessage()"
        type="text"
        placeholder="输入问题，查询平台中的校园安全数据..."
        class="flex-1 px-3 py-2 text-sm outline-none bg-transparent"
      >
      <button @click="sendMessage()" :disabled="thinking" class="btn btn-primary"><AppIcon name="send" :size="15" /> 发送</button>
    </div>
  </div>
</template>
