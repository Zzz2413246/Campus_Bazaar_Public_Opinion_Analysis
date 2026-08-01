import { computed, nextTick, ref, watch } from 'vue'
import { assistantApi } from '@/utils/api'
import { toast } from '@/utils/toast'

export interface AssistantSource {
  label: string
  route: string
}

export interface AssistantMessage {
  id: string
  role: 'user' | 'assistant'
  content: string
  time: string
  type?: string
  sources?: AssistantSource[]
  followUps?: string[]
  engine?: 'llm' | 'structured'
  failed?: boolean
}

interface AssistantStatus {
  online: boolean
  llmEnabled: boolean
  model: string
  dataAsOf?: string
}

const STORAGE_KEY = 'yuqing_assistant_messages_v2'
const welcomeMessage: AssistantMessage = {
  id: 'welcome',
  role: 'assistant',
  content: '你好，我是校园安全智能助手。\n\n我可以基于平台数据查询风险事件、对比议题趋势、解释判断依据，并生成舆情简报。',
  time: nowTime(),
  followUps: ['最近有哪些高风险事件？', '本周哪些议题增长最快？', '生成本周舆情简报'],
}

const messages = ref<AssistantMessage[]>(restoreMessages())
const thinking = ref(false)
const status = ref<AssistantStatus | null>(null)
let statusRequested = false

watch(messages, (value) => {
  try {
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(value.slice(-30)))
  } catch {
    // Storage can be unavailable in privacy mode; the in-memory chat still works.
  }
}, { deep: true })

export function useAssistantChat() {
  async function loadStatus() {
    if (statusRequested) return
    statusRequested = true
    try {
      status.value = unwrap(await assistantApi.status())
    } catch {
      status.value = { online: false, llmEnabled: false, model: '本地数据分析引擎' }
    }
  }

  async function sendMessage(text: string) {
    const question = text.trim()
    if (!question || thinking.value) return
    const history = messages.value
      .filter(message => message.id !== 'welcome' && !message.failed)
      .slice(-8)
      .map(({ role, content }) => ({ role, content }))

    messages.value.push({
      id: crypto.randomUUID(),
      role: 'user',
      content: question,
      time: nowTime(),
    })

    const quickReply = getQuickReply(question)
    if (quickReply) {
      messages.value.push({
        id: crypto.randomUUID(),
        role: 'assistant',
        content: quickReply.content,
        time: nowTime(),
        type: 'greeting',
        followUps: quickReply.followUps,
        engine: 'structured',
      })
      return
    }

    thinking.value = true
    try {
      const data: any = unwrap(await assistantApi.query(question, history))
      messages.value.push({
        id: crypto.randomUUID(),
        role: 'assistant',
        content: data?.answer ?? data?.content ?? data?.reply ?? '暂时无法获取回答，请稍后再试。',
        time: nowTime(),
        type: data?.type,
        sources: Array.isArray(data?.sources) ? data.sources : [],
        followUps: Array.isArray(data?.followUps) ? data.followUps : [],
        engine: data?.engine,
      })
    } catch (error) {
      console.warn('助手请求失败', error)
      messages.value.push({
        id: crypto.randomUUID(),
        role: 'assistant',
        content: '当前无法连接分析服务。你可以稍后重试这条问题。',
        time: nowTime(),
        failed: true,
      })
    } finally {
      thinking.value = false
    }
  }

  function retryLast() {
    const lastQuestion = [...messages.value].reverse().find(message => message.role === 'user')
    if (lastQuestion) sendMessage(lastQuestion.content)
  }

  function clearConversation() {
    messages.value = [{ ...welcomeMessage, time: nowTime() }]
  }

  async function copyMessage(content: string) {
    try {
      await navigator.clipboard.writeText(content)
      toast.success('回答已复制')
    } catch {
      toast.error('复制失败，请手动选择文本')
    }
  }

  const latestFollowUps = computed(() => {
    const last = [...messages.value].reverse().find(message => message.role === 'assistant')
    return last?.followUps ?? []
  })

  return {
    messages,
    thinking,
    status,
    latestFollowUps,
    loadStatus,
    sendMessage,
    retryLast,
    clearConversation,
    copyMessage,
    waitForRender: () => nextTick(),
  }
}

function nowTime() {
  return new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
}

function unwrap(res: any) {
  if (res && typeof res === 'object' && (res.code !== undefined || res.success !== undefined) && res.data !== undefined) {
    return res.data
  }
  return res
}

function restoreMessages(): AssistantMessage[] {
  try {
    const parsed = JSON.parse(sessionStorage.getItem(STORAGE_KEY) || '[]')
    if (Array.isArray(parsed) && parsed.length) return parsed
  } catch {
    // Ignore invalid old storage.
  }
  return [{ ...welcomeMessage }]
}

function getQuickReply(question: string): { content: string; followUps: string[] } | null {
  const normalized = question
    .toLowerCase()
    .replace(/[\s，。！？!?、,.~～]+/g, '')

  if (['你好', '您好', '嗨', 'hi', 'hello', '在吗'].includes(normalized)) {
    return {
      content: '你好！我是校园安全智能助手。\n\n我可以帮你查询高风险事件、分析议题趋势与情绪、解释风险依据，或生成校园安全舆情简报。你想先了解什么？',
      followUps: ['最近有哪些高风险事件？', '哪些议题增长最快？', '生成本周舆情简报'],
    }
  }
  if (['谢谢', '感谢', '多谢', 'thankyou', 'thanks'].includes(normalized)) {
    return {
      content: '不客气。如果还需要查询事件、趋势或生成简报，随时告诉我。',
      followUps: ['最近有哪些高风险事件？', '生成本周舆情简报'],
    }
  }
  if (['再见', '拜拜', 'bye', 'goodbye'].includes(normalized)) {
    return {
      content: '再见！需要继续研判校园安全舆情时，我随时在这里。',
      followUps: [],
    }
  }
  if (['你是谁', '你能做什么', '有什么功能', '帮助'].includes(normalized)) {
    return {
      content: '我是校园安全智能助手，能够基于平台数据：\n\n- 查询和概括风险事件\n- 对比校园安全议题趋势\n- 分析讨论情绪与风险依据\n- 提供处置建议\n- 生成日报、周报和事件简报',
      followUps: ['当前有哪些高风险事件？', '最近宿舍问题有哪些？', '生成本周舆情简报'],
    }
  }
  return null
}
