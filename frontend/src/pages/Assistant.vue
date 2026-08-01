<script setup lang="ts">
import { nextTick, onMounted, ref, watch } from 'vue'
import AppIcon from '@/components/AppIcon.vue'
import AssistantMessageContent from '@/components/AssistantMessageContent.vue'
import { useAssistantChat } from '@/composables/useAssistantChat'

const inputText = ref('')
const chatArea = ref<HTMLElement | null>(null)
const {
  messages,
  thinking,
  status,
  latestFollowUps,
  loadStatus,
  sendMessage: submit,
  retryLast,
  clearConversation,
  copyMessage,
} = useAssistantChat()

onMounted(loadStatus)
watch([() => messages.value.length, thinking], scrollToBottom)

async function sendMessage(text?: string) {
  const question = (text ?? inputText.value).trim()
  if (!question) return
  inputText.value = ''
  await submit(question)
}

function handleEnter(event: KeyboardEvent) {
  if (event.shiftKey) return
  event.preventDefault()
  sendMessage()
}

function scrollToBottom() {
  nextTick(() => {
    if (chatArea.value) chatArea.value.scrollTop = chatArea.value.scrollHeight
  })
}
</script>

<template>
  <div class="max-w-5xl mx-auto h-[calc(100vh-8.5rem)] min-h-[560px] flex flex-col">
    <div class="flex items-center gap-3 mb-4 px-1">
      <div class="w-11 h-11 rounded-xl bg-gradient-to-br from-brand-500 to-accent-500 flex items-center justify-center text-white shadow-lg shadow-brand-600/25">
        <AppIcon name="bot" :size="23" />
      </div>
      <div class="min-w-0">
        <div class="text-base font-semibold text-slate-800">校园安全智能助手</div>
        <div class="text-xs text-slate-500 flex items-center gap-1.5 mt-0.5">
          <span :class="['w-1.5 h-1.5 rounded-full', status?.online === false ? 'bg-rose-500' : 'bg-emerald-500']"></span>
          <span>{{ status?.llmEnabled ? `${status.model} · 平台数据增强` : '本地数据分析引擎' }}</span>
          <span v-if="status?.dataAsOf" class="hidden sm:inline">· 数据截至 {{ status.dataAsOf }}</span>
        </div>
      </div>
      <button
        v-if="messages.length > 1"
        class="ml-auto inline-flex items-center gap-1.5 px-3.5 py-2 rounded-xl text-xs text-slate-500 hover:text-slate-800 hover:bg-slate-100 transition-colors"
        @click="clearConversation"
      >
        <AppIcon name="refresh" :size="14" /> 新对话
      </button>
    </div>

    <div ref="chatArea" class="flex-1 overflow-y-auto space-y-7 pr-1 pb-3">
      <div
        v-for="message in messages"
        :key="message.id"
        :class="[
          'assistant-message-row flex gap-3 group',
          message.role === 'user'
            ? 'assistant-message-row--user justify-end'
            : 'assistant-message-row--assistant justify-start',
        ]"
      >
        <div v-if="message.role === 'assistant'" class="w-9 h-9 rounded-xl bg-gradient-to-br from-brand-500 to-accent-500 flex items-center justify-center flex-shrink-0 text-white shadow-sm">
          <AppIcon name="bot" :size="18" />
        </div>
        <div :class="[
          'max-w-[82%] sm:max-w-[75%] px-5 py-4 text-[15px] leading-6',
          message.role === 'user'
            ? 'rounded-2xl rounded-br-md bg-gradient-to-br from-brand-600 to-brand-500 text-white shadow-sm shadow-brand-600/25'
            : 'rounded-2xl rounded-tl-md bg-white border border-slate-200/70 shadow-sm text-slate-700',
        ]">
          <AssistantMessageContent :content="message.content" />
          <div v-if="message.sources?.length" class="flex flex-wrap gap-2 mt-3 pt-3 border-t border-slate-100">
            <RouterLink
              v-for="source in message.sources"
              :key="source.route"
              :to="source.route"
              class="inline-flex items-center gap-1 text-xs text-indigo-600 hover:text-indigo-800"
            >
              <AppIcon name="database" :size="12" /> {{ source.label }}
            </RouterLink>
          </div>
          <div :class="['flex items-center gap-2 text-[11px] mt-2', message.role === 'user' ? 'text-brand-100' : 'text-slate-400']">
            <span>{{ message.time }}</span>
            <span v-if="message.engine === 'llm'">· AI 数据增强</span>
            <button
              v-if="message.role === 'assistant'"
              class="ml-auto opacity-0 group-hover:opacity-100 inline-flex items-center gap-1 hover:text-slate-700 transition-all"
              @click="copyMessage(message.content)"
            >
              <AppIcon name="copy" :size="12" /> 复制
            </button>
          </div>
        </div>
        <div v-if="message.role === 'user'" class="w-9 h-9 rounded-xl bg-slate-200 flex items-center justify-center flex-shrink-0 text-slate-500">
          <AppIcon name="user" :size="17" />
        </div>
      </div>

      <div v-if="thinking" class="flex gap-3 justify-start">
        <div class="w-9 h-9 rounded-xl bg-gradient-to-br from-brand-500 to-accent-500 flex items-center justify-center flex-shrink-0 text-white">
          <AppIcon name="bot" :size="18" />
        </div>
        <div class="px-5 py-4 rounded-2xl rounded-tl-md bg-white border border-slate-200/70 shadow-sm text-sm text-slate-500">
          <span class="inline-flex items-center gap-1.5">
            <span class="w-1.5 h-1.5 rounded-full bg-indigo-400 animate-bounce"></span>
            <span class="w-1.5 h-1.5 rounded-full bg-indigo-400 animate-bounce [animation-delay:150ms]"></span>
            <span class="w-1.5 h-1.5 rounded-full bg-indigo-400 animate-bounce [animation-delay:300ms]"></span>
            正在读取平台数据…
          </span>
        </div>
      </div>
    </div>

    <div v-if="latestFollowUps.length && !thinking" class="flex gap-2 py-3 overflow-x-auto">
      <button
        v-for="question in latestFollowUps"
        :key="question"
        class="shrink-0 text-xs sm:text-sm px-4 py-2 rounded-full bg-white border border-slate-200/70 shadow-sm text-slate-600 hover:bg-brand-50 hover:text-brand-600 hover:border-brand-200 transition-all"
        @click="sendMessage(question)"
      >
        {{ question }}
      </button>
    </div>

    <div class="rounded-2xl bg-white border border-slate-200/70 shadow-sm shadow-slate-200/50 p-2 flex items-end gap-2 focus-within:border-brand-300 focus-within:ring-4 focus-within:ring-brand-100/60 transition-all">
      <textarea
        v-model="inputText"
        rows="1"
        maxlength="1000"
        placeholder="输入问题；Enter 发送，Shift + Enter 换行"
        class="flex-1 min-h-10 max-h-28 px-3 py-2 text-[15px] leading-6 outline-none bg-transparent resize-y placeholder:text-slate-400"
        @keydown.enter="handleEnter"
      ></textarea>
      <button
        v-if="messages.at(-1)?.failed"
        class="btn"
        :disabled="thinking"
        @click="retryLast"
      >
        <AppIcon name="refresh" :size="15" /> 重试
      </button>
      <button @click="sendMessage()" :disabled="thinking || !inputText.trim()" class="btn btn-primary">
        <AppIcon name="send" :size="15" /> 发送
      </button>
    </div>
    <div class="text-[11px] text-slate-400 text-center mt-2">回答基于平台数据生成，重要处置决策请结合人工研判。</div>
  </div>
</template>

<style scoped>
.assistant-message-row--assistant + .assistant-message-row--user,
.assistant-message-row--user + .assistant-message-row--assistant {
  margin-top: 2.75rem !important;
}
</style>
