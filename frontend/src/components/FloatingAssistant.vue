<script setup lang="ts">
import { nextTick, ref } from 'vue'
import AppIcon from '@/components/AppIcon.vue'
import { assistantApi } from '@/utils/api'
import { toast } from '@/utils/toast'

interface Message {
  role: 'user' | 'assistant'
  content: string
  time: string
}

const open = ref(false)
const inputText = ref('')
const thinking = ref(false)
const messageArea = ref<HTMLElement | null>(null)
const messages = ref<Message[]>([{
  role: 'assistant',
  content: '你好，我是校园安全智能助手。可以帮你查询风险事件、分析议题趋势或生成简报。',
  time: nowTime(),
}])

const suggestions = [
  '最近有哪些高风险事件？',
  '本周哪些议题增长最快？',
  '生成本周舆情简报',
]

function unwrap(res: any) {
  if (res && typeof res === 'object' && (res.code !== undefined || res.success !== undefined) && res.data !== undefined) return res.data
  return res
}

function nowTime() {
  return new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
}

function scrollToBottom() {
  nextTick(() => {
    if (messageArea.value) messageArea.value.scrollTop = messageArea.value.scrollHeight
  })
}

async function sendMessage(text?: string) {
  const question = (text ?? inputText.value).trim()
  if (!question || thinking.value) return
  messages.value.push({ role: 'user', content: question, time: nowTime() })
  inputText.value = ''
  thinking.value = true
  scrollToBottom()
  try {
    const res: any = await assistantApi.query(question)
    const data = unwrap(res)
    const answer = typeof data === 'string'
      ? data
      : data?.answer ?? data?.content ?? data?.reply ?? '暂时无法获取回答，请稍后再试。'
    messages.value.push({ role: 'assistant', content: answer, time: nowTime() })
  } catch (error) {
    console.warn('助手请求失败', error)
    toast.error('助手暂时无法连接')
    messages.value.push({
      role: 'assistant',
      content: '当前无法连接分析服务，请稍后再试。',
      time: nowTime(),
    })
  } finally {
    thinking.value = false
    scrollToBottom()
  }
}
</script>

<template>
  <div class="assistant-layer">
    <Transition name="assistant-panel">
      <section
        v-if="open"
        class="assistant-panel fixed z-[70] flex flex-col bg-white border border-slate-200 shadow-2xl shadow-slate-900/20 overflow-hidden"
        aria-label="校园安全智能助手"
      >
        <header class="flex items-center gap-3 px-5 py-4 text-white bg-gradient-to-r from-slate-900 via-indigo-950 to-slate-900">
          <div class="w-10 h-10 flex items-center justify-center bg-cyan-400/15 border border-cyan-300/20 text-cyan-300">
            <AppIcon name="bot" :size="22" />
          </div>
          <div class="flex-1 min-w-0">
            <div class="text-[16px] font-semibold tracking-wide">校园安全智能助手</div>
            <div class="flex items-center gap-1.5 text-xs text-slate-300 mt-0.5">
              <span class="w-1.5 h-1.5 rounded-full bg-emerald-400"></span>
              基于平台实时数据
            </div>
          </div>
          <button
            class="w-9 h-9 flex items-center justify-center text-slate-300 hover:text-white hover:bg-white/10 transition-colors cursor-pointer"
            aria-label="关闭智能助手"
            @click="open = false"
          >
            <AppIcon name="x" :size="20" />
          </button>
        </header>

        <div ref="messageArea" class="flex-1 overflow-y-auto px-4 py-5 space-y-4 bg-slate-50">
          <div
            v-for="(message, index) in messages"
            :key="index"
            :class="['flex gap-2.5', message.role === 'user' ? 'justify-end' : 'justify-start']"
          >
            <div
              v-if="message.role === 'assistant'"
              class="w-8 h-8 flex items-center justify-center flex-shrink-0 bg-indigo-100 text-indigo-700"
            >
              <AppIcon name="bot" :size="17" />
            </div>
            <div
              :class="[
                'max-w-[82%] px-3.5 py-3 text-sm shadow-sm',
                message.role === 'user'
                  ? 'bg-indigo-600 text-white'
                  : 'bg-white border border-slate-200 text-slate-800',
              ]"
            >
              <div class="whitespace-pre-wrap leading-6">{{ message.content }}</div>
              <div :class="['mt-1.5 text-[11px]', message.role === 'user' ? 'text-indigo-200' : 'text-slate-500']">
                {{ message.time }}
              </div>
            </div>
          </div>

          <div v-if="thinking" class="flex gap-2.5">
            <div class="w-8 h-8 flex items-center justify-center bg-indigo-100 text-indigo-700">
              <AppIcon name="bot" :size="17" />
            </div>
            <div class="px-4 py-3 bg-white border border-slate-200 text-sm text-slate-600">
              正在分析<span class="animate-pulse">...</span>
            </div>
          </div>
        </div>

        <div v-if="messages.length === 1" class="px-4 pt-3 flex flex-wrap gap-2 bg-white border-t border-slate-100">
          <button
            v-for="question in suggestions"
            :key="question"
            class="px-3 py-1.5 text-xs text-slate-700 bg-slate-50 border border-slate-200 hover:border-indigo-300 hover:text-indigo-700 transition-colors cursor-pointer"
            @click="sendMessage(question)"
          >
            {{ question }}
          </button>
        </div>

        <div class="p-4 bg-white">
          <div class="flex gap-2 border border-slate-300 bg-white p-1.5 focus-within:border-indigo-400 focus-within:ring-2 focus-within:ring-indigo-100">
            <input
              v-model="inputText"
              class="flex-1 min-w-0 px-2.5 py-2 text-[14px] text-slate-800 outline-none"
              placeholder="输入问题..."
              @keyup.enter="sendMessage()"
            />
            <button
              class="w-10 h-10 flex items-center justify-center bg-indigo-600 text-white hover:bg-indigo-700 disabled:opacity-50 transition-colors cursor-pointer"
              :disabled="thinking || !inputText.trim()"
              aria-label="发送消息"
              @click="sendMessage()"
            >
              <AppIcon name="send" :size="17" />
            </button>
          </div>
        </div>
      </section>
    </Transition>

    <button
      class="assistant-trigger fixed right-6 bottom-6 z-[71] w-14 h-14 flex items-center justify-center text-white bg-gradient-to-br from-indigo-600 to-cyan-500 shadow-xl shadow-indigo-900/25 hover:-translate-y-1 hover:shadow-2xl transition-all cursor-pointer"
      :class="{ 'assistant-trigger-open': open }"
      :aria-label="open ? '关闭智能助手' : '打开智能助手'"
      :title="open ? '关闭智能助手' : '智能助手'"
      @click="open = !open"
    >
      <AppIcon :name="open ? 'x' : 'bot'" :size="25" />
      <span v-if="!open" class="absolute -top-1 -right-1 w-3 h-3 rounded-full bg-emerald-400 ring-2 ring-white"></span>
    </button>
  </div>
</template>

<style scoped>
.assistant-panel {
  right: 24px;
  bottom: 92px;
  width: 420px;
  height: min(640px, calc(100vh - 120px));
}

.assistant-trigger {
  border-radius: 18px;
}

.assistant-trigger-open {
  background: #0f172a;
}

.assistant-panel-enter-active,
.assistant-panel-leave-active {
  transition: opacity 180ms ease, transform 180ms ease;
}

.assistant-panel-enter-from,
.assistant-panel-leave-to {
  opacity: 0;
  transform: translateY(16px) scale(0.97);
}

@media (max-width: 640px) {
  .assistant-panel {
    left: 12px;
    right: 12px;
    bottom: 82px;
    width: auto;
    height: min(620px, calc(100vh - 104px));
  }

  .assistant-trigger {
    right: 16px;
    bottom: 16px;
  }
}
</style>
