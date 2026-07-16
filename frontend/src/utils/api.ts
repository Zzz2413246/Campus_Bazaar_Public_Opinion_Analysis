import axios from 'axios'
import { toast } from '@/utils/toast'

const http = axios.create({
  baseURL: 'http://localhost:8080/api',
  timeout: 15000,
})

http.interceptors.response.use(
  (res) => res.data,
  (err) => {
    console.error('API请求失败:', err.message)
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

// Dashboard 总览
export const dashboardApi = {
  get: () => http.get('/dashboard'),
}

// 帖子监测
export const postApi = {
  list: (params: { keyword?: string; category?: string; emotion?: string; source?: string; page?: number; size?: number }) =>
    http.get('/posts', { params }),
  detail: (id: string) => http.get(`/posts/${id}`),
}

// 事件管理
export const eventApi = {
  list: () => http.get('/events'),
  detail: (id: string) => http.get(`/events/${id}`),
  updateStatus: (id: string, data: { status?: string; risk?: string }) =>
    http.put(`/events/${id}/status`, data),
}

// 趋势分析
export const trendsApi = {
  get: () => http.get('/trends'),
}

// 报告中心
export const reportApi = {
  list: () => http.get('/reports'),
  detail: (date: string) => http.get(`/reports/${date}`),
}

// 系统设置
export const settingsApi = {
  get: () => http.get('/settings'),
  update: (data: any) => http.put('/settings', data, { timeout: 120000 }),
}

// 智能助手
export const assistantApi = {
  query: (question: string) => http.post('/assistant/query', { question }),
}

// 数据管理
export const dataApi = {
  stats: () => http.get('/data/stats'),
  import: (data: any[]) => http.post('/data/import', data, { timeout: 120000 }),
  reanalyze: () => http.post('/data/reanalyze', undefined, { timeout: 120000 }),
  clear: () => http.delete('/data/all'),
}

// 可插拔分析任务（任务二标准到位后沿用此接口）
export const analysisExtensionApi = {
  list: () => http.get('/analysis/extensions'),
  status: (code: string) => http.get(`/analysis/extensions/${code}`),
  run: (code: string, data: any = {}) => http.post(`/analysis/extensions/${code}/run`, data, { timeout: 120000 }),
}

export default http
