import axios from 'axios'

const http = axios.create({
  baseURL: 'http://localhost:8080/api',
  timeout: 15000,
})

http.interceptors.response.use(
  (res) => res.data,
  (err) => {
    console.error('API请求失败:', err.message)
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
  update: (data: any) => http.put('/settings', data),
}

// 智能助手
export const assistantApi = {
  query: (question: string) => http.post('/assistant/query', { question }),
}

export default http
