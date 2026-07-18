import { createRouter, createWebHashHistory } from 'vue-router'

const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    {
      path: '/login',
      name: 'Login',
      meta: { title: '登录', public: true },
      component: () => import('@/pages/Login.vue'),
    },
    {
      path: '/',
      component: () => import('@/layouts/MainLayout.vue'),
      redirect: '/dashboard',
      children: [
        {
          path: 'dashboard',
          name: 'Dashboard',
          meta: { title: '舆情总览', icon: 'layout-dashboard' },
          component: () => import('@/pages/Dashboard.vue'),
        },
        {
          path: 'monitoring',
          name: 'Monitoring',
          meta: { title: '舆情监测', icon: 'radar' },
          component: () => import('@/pages/Monitoring.vue'),
        },
        {
          path: 'monitoring/:id',
          name: 'PostDetail',
          meta: { title: '帖子详情', hidden: true },
          component: () => import('@/pages/PostDetail.vue'),
        },
        {
          path: 'events',
          name: 'EventList',
          meta: { title: '事件管理', icon: 'siren' },
          component: () => import('@/pages/EventList.vue'),
        },
        {
          path: 'events/:id',
          name: 'EventDetail',
          meta: { title: '事件详情', hidden: true },
          component: () => import('@/pages/EventDetail.vue'),
        },
        {
          path: 'trends',
          name: 'Trends',
          meta: { title: '趋势分析', hidden: true },
          redirect: '/dashboard',
        },
        {
          path: 'assistant',
          name: 'Assistant',
          meta: { title: '智能助手', hidden: true },
          redirect: '/dashboard',
        },
        {
          path: 'reports',
          name: 'Reports',
          meta: { title: '报告中心', icon: 'file-text' },
          component: () => import('@/pages/Reports.vue'),
        },
        {
          path: 'settings',
          name: 'Settings',
          meta: { title: '系统设置', icon: 'settings' },
          component: () => import('@/pages/Settings.vue'),
        },
        {
          path: 'data',
          name: 'DataManage',
          meta: { title: '数据管理', icon: 'database' },
          component: () => import('@/pages/DataManage.vue'),
        },
      ],
    },
  ],
})

router.beforeEach((to) => {
  const loggedIn = Boolean(localStorage.getItem('yuqing_token'))
  if (!to.meta.public && !loggedIn) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if (to.path === '/login' && loggedIn) return '/dashboard'
  return true
})

export default router
