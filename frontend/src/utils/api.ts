import axios from 'axios'
import { toast } from '@/utils/toast'

const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 15000,
})

http.interceptors.request.use((config) => {
  const token = localStorage.getItem('yuqing_token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

http.interceptors.response.use(
  (res) => res.data,
  (err) => {
    console.error('API请求失败:', err.message)
    if (err?.response?.status === 401 && !String(err?.config?.url || '').includes('/auth/login')) {
      localStorage.removeItem('yuqing_token')
      localStorage.removeItem('yuqing_nickname')
      localStorage.removeItem('yuqing_role')
      localStorage.removeItem('yuqing_permissions')
      window.location.hash = '#/login'
      return Promise.reject(err)
    }
    // 根据错误类型向用户展示对应的提示
    if (err?.response?.status >= 500) {
      toast.error('服务器异常，请稍后重试')
    } else if (err?.code === 'ECONNABORTED') {
      toast.error('请求超时，请检查网络连接')
    } else if (!err?.response) {
      toast.error('网络连接失败，请检查网络')
    }
    return Promise.reject(err)
  }
)

export const authApi = {
  login: (nickname: string, password: string) =>
    http.post('/auth/login', { nickname, password }),
  me: () => http.get('/auth/me'),
  profile: () => http.get('/auth/profile'),
  changePassword: (currentPassword: string, newPassword: string) =>
    http.put('/auth/password', { currentPassword, newPassword }),
  logout: () => http.post('/auth/logout'),
}

// Dashboard 总览
export const dashboardApi = {
  get: (params?: { days?: number; startDate?: string; endDate?: string }) => http.get('/dashboard', { params }),
}

// 帖子监测
export const postApi = {
  list: (params: {
    keyword?: string
    category?: string
    emotion?: string
    source?: string
    reviewStatus?: string
    sortBy?: 'latest' | 'risk' | 'heat'
    page?: number
    size?: number
  }, signal?: AbortSignal) =>
    http.get('/posts', { params, signal }),
  detail: (id: string, params?: { commentPage?: number; commentSize?: number }) =>
    http.get(`/posts/${id}`, { params }),
  review: (id: string, data: {
    action: 'confirm' | 'correct' | 'irrelevant' | 'reset'
    category?: string
    riskLevel?: string
    emotion?: string
    note?: string
    reviewer?: string
  }) => http.put(`/posts/${id}/review`, data),
  batchReview: (data: {
    ids: string[]
    action: 'confirm' | 'irrelevant'
    note?: string
    reviewer?: string
  }) => http.put('/posts/review/batch', data, { timeout: 120000 }),
}

// 评论评判依据（接口不返回评论者个人标识）
export const commentApi = {
  listForPost: (postId: string, params?: { page?: number; size?: number }) =>
    http.get(`/comments/post/${postId}`, { params }),
}

// 事件管理
export const eventApi = {
  list: () => http.get('/events'),
  detail: (id: string) => http.get(`/events/${id}`),
  updateStatus: (id: string, data: {
    status?: string
    risk?: string
    assignee?: string
    dueAt?: string
    remark?: string
    operator?: string
    expectedUpdatedAt?: string
  }) =>
    http.put(`/events/${id}/status`, data),
}

// 趋势分析
export const trendsApi = {
  get: (params?: { days?: number; startDate?: string; endDate?: string }) =>
    http.get('/trends', { params }),
}

// 报告中心
export const reportApi = {
  list: (type: 'daily' | 'weekly' | 'event' = 'daily') =>
    http.get('/reports', { params: { type } }),
  detail: (key: string, type: 'daily' | 'weekly' | 'event' = 'daily') =>
    http.get(`/reports/${key}`, { params: { type } }),
}

// 系统设置
export const settingsApi = {
  get: () => http.get('/settings'),
  update: (data: any) => http.put('/settings', data, { timeout: 120000 }),
}

// 智能助手
export const assistantApi = {
  query: (question: string, history: Array<{ role: 'user' | 'assistant'; content: string }> = []) =>
    http.post('/assistant/query', { question, history }, { timeout: 120000 }),
  status: () => http.get('/assistant/status'),
}

// 数据管理
export const dataApi = {
  stats: () => http.get('/data/stats'),
  import: (data: any[]) => http.post('/data/import', data, { timeout: 120000 }),
  importComments: (data: any[]) => http.post('/data/comments/import', data, { timeout: 120000 }),
  reanalyze: () => http.post('/data/reanalyze', undefined, { timeout: 120000 }),
  reanalyzeStatus: () => http.get('/data/reanalyze/status'),
  clear: () => http.delete('/data/all'),
}

export const auditApi = {
  logs: (params?: { action?: string; page?: number; size?: number }) =>
    http.get('/audit/logs', { params }),
  summary: () => http.get('/audit/summary'),
  mine: (params?: { page?: number; size?: number }) => http.get('/audit/my', { params }),
}

// 可插拔分析任务（任务二标准到位后沿用此接口）
export const analysisExtensionApi = {
  list: () => http.get('/analysis/extensions'),
  status: (code: string) => http.get(`/analysis/extensions/${code}`),
  run: (code: string, data: any = {}) => http.post(`/analysis/extensions/${code}/run`, data, { timeout: 120000 }),
}

export default http
