<script setup lang="ts">
import { nextTick, onMounted, ref, watch } from 'vue'
import AppIcon from '@/components/AppIcon.vue'
import AssistantMessageContent from '@/components/AssistantMessageContent.vue'
import { useAssistantChat } from '@/composables/useAssistantChat'

const open = ref(false)
const inputText = ref('')
const messageArea = ref<HTMLElement | null>(null)
const {
  messages,
  thinking,
  status,
  latestFollowUps,
  loadStatus,
  sendMessage: submit,
  clearConversation,
  copyMessage,
} = useAssistantChat()

onMounted(loadStatus)
watch([() => messages.value.length, thinking, open], scrollToBottom)

function scrollToBottom() {
  nextTick(() => {
    if (messageArea.value) messageArea.value.scrollTop = messageArea.value.scrollHeight
  })
}

async function sendMessage(text?: string) {
  const question = (text ?? inputText.value).trim()
  if (!question || thinking.value) return
  inputText.value = ''
  scrollToBottom()
  await submit(question)
}

function handleEnter(event: KeyboardEvent) {
  if (event.shiftKey) return
  event.preventDefault()
  sendMessage()
}
</script>

<template>
  <div class="assistant-layer">
    <Transition name="assistant-panel">
      <section
        v-if="open"
        class="assistant-panel fixed z-[70] flex flex-col bg-white border border-slate-200/80 rounded-[24px] shadow-2xl shadow-slate-900/20 overflow-hidden"
        aria-label="校园安全智能助手"
      >
        <header class="flex items-center gap-3 px-5 py-4 text-white bg-gradient-to-r from-slate-900 via-indigo-950 to-slate-900">
          <div class="w-10 h-10 rounded-xl flex items-center justify-center bg-cyan-400/15 border border-cyan-300/20 text-cyan-300">
            <AppIcon name="bot" :size="22" />
          </div>
          <div class="flex-1 min-w-0">
            <div class="text-[16px] font-semibold tracking-wide">校园安全智能助手</div>
            <div class="flex items-center gap-1.5 text-xs text-slate-300 mt-0.5">
              <span class="w-1.5 h-1.5 rounded-full bg-emerald-400"></span>
              {{ status?.llmEnabled ? `${status.model} · 数据增强` : '基于平台结构化数据' }}
            </div>
          </div>
          <button
            v-if="messages.length > 1"
            class="w-9 h-9 rounded-xl flex items-center justify-center text-slate-300 hover:text-white hover:bg-white/10 transition-colors cursor-pointer"
            title="新对话"
            @click="clearConversation"
          >
            <AppIcon name="refresh" :size="17" />
          </button>
          <button
            class="w-9 h-9 rounded-xl flex items-center justify-center text-slate-300 hover:text-white hover:bg-white/10 transition-colors cursor-pointer"
            aria-label="关闭智能助手"
            @click="open = false"
          >
            <AppIcon name="x" :size="20" />
          </button>
        </header>

        <div ref="messageArea" class="assistant-messages flex-1 overflow-y-auto px-5 py-5 space-y-6 bg-gradient-to-b from-slate-50 to-indigo-50/30">
          <div
            v-for="(message, index) in messages"
            :key="message.id || index"
            :class="[
              'message-row flex gap-2.5 group',
              message.role === 'user' ? 'message-row--user justify-end' : 'message-row--assistant justify-start',
            ]"
          >
            <div
              v-if="message.role === 'assistant'"
              class="w-8 h-8 rounded-xl flex items-center justify-center flex-shrink-0 bg-indigo-100 text-indigo-700"
            >
              <AppIcon name="bot" :size="17" />
            </div>
            <div
              :class="[
                'max-w-[84%] px-5 py-4 text-[14px] leading-6 shadow-sm',
                message.role === 'user'
                  ? 'rounded-2xl rounded-br-md bg-gradient-to-br from-indigo-600 to-violet-600 text-white shadow-indigo-200/60'
                  : 'rounded-2xl rounded-tl-md bg-white border border-slate-200/80 text-slate-700',
              ]"
            >
              <AssistantMessageContent :content="message.content" />
              <div v-if="message.sources?.length" class="flex flex-wrap gap-2 mt-2 pt-2 border-t border-slate-100">
                <RouterLink
                  v-for="source in message.sources"
                  :key="source.route"
                  :to="source.route"
                  class="text-[11px] text-indigo-600 hover:text-indigo-800"
                  @click="open = false"
                >{{ source.label }} →</RouterLink>
              </div>
              <div :class="['mt-1.5 text-[11px] flex items-center gap-2', message.role === 'user' ? 'text-indigo-200' : 'text-slate-500']">
                <span>{{ message.time }}</span>
                <button
                  v-if="message.role === 'assistant'"
                  class="ml-auto opacity-0 group-hover:opacity-100 hover:text-slate-800 transition-all"
                  @click="copyMessage(message.content)"
                ><AppIcon name="copy" :size="12" /></button>
              </div>
            </div>
          </div>

          <div v-if="thinking" class="flex gap-2.5">
            <div class="w-8 h-8 rounded-xl flex items-center justify-center bg-indigo-100 text-indigo-700">
              <AppIcon name="bot" :size="17" />
            </div>
            <div class="px-5 py-4 rounded-2xl rounded-tl-md bg-white border border-slate-200 text-sm text-slate-600 shadow-sm">
              正在分析<span class="animate-pulse">...</span>
            </div>
          </div>
        </div>

        <div v-if="latestFollowUps.length && !thinking" class="px-5 pt-3 flex gap-2 overflow-x-auto bg-white border-t border-slate-100">
          <button
            v-for="question in latestFollowUps"
            :key="question"
            class="shrink-0 px-3.5 py-2 rounded-full text-xs text-slate-600 bg-slate-50 border border-slate-200 hover:bg-indigo-50 hover:border-indigo-300 hover:text-indigo-700 transition-colors cursor-pointer"
            @click="sendMessage(question)"
          >
            {{ question }}
          </button>
        </div>

        <div class="px-5 pt-3 pb-5 bg-white">
          <div class="flex items-end gap-2 rounded-2xl border border-slate-300 bg-slate-50/70 p-1.5 focus-within:bg-white focus-within:border-indigo-400 focus-within:ring-4 focus-within:ring-indigo-100/70 transition-all">
            <textarea
              v-model="inputText"
              rows="1"
              maxlength="1000"
              class="flex-1 min-w-0 min-h-10 max-h-24 px-3 py-2 text-[14px] leading-6 text-slate-800 bg-transparent outline-none resize-y placeholder:text-slate-400"
              placeholder="输入想了解的问题…"
              @keydown.enter="handleEnter"
            ></textarea>
            <button
              class="w-10 h-10 rounded-xl flex items-center justify-center bg-indigo-600 text-white hover:bg-indigo-700 disabled:opacity-40 disabled:cursor-not-allowed transition-colors cursor-pointer shadow-sm shadow-indigo-200"
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
  width: min(460px, calc(100vw - 48px));
  height: min(680px, calc(100vh - 120px));
}

.assistant-trigger {
  border-radius: 18px;
}

.assistant-messages {
  scrollbar-width: thin;
  scrollbar-color: #cbd5e1 transparent;
}

.message-row--assistant + .message-row--user,
.message-row--user + .message-row--assistant {
  margin-top: 2.5rem !important;
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
