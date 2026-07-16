import { reactive } from 'vue'

export interface ToastItem {
  id: number
  type: 'success' | 'error' | 'warning' | 'info'
  message: string
}

const toasts = reactive<ToastItem[]>([])
let toastId = 0

function show(type: ToastItem['type'], message: string, duration = 3000) {
  const id = ++toastId
  toasts.push({ id, type, message })
  // 最多同时显示 3 条，超出时移除最早的
  if (toasts.length > 3) toasts.shift()
  setTimeout(() => remove(id), duration)
}

function remove(id: number) {
  const idx = toasts.findIndex((t) => t.id === id)
  if (idx > -1) toasts.splice(idx, 1)
}

export const toast = {
  success: (msg: string) => show('success', msg),
  error: (msg: string) => show('error', msg),
  warning: (msg: string) => show('warning', msg),
  info: (msg: string) => show('info', msg),
  remove,
  toasts,
}
