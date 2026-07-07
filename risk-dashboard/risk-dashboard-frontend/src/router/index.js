import Vue from 'vue'
import VueRouter from 'vue-router'
import store from '@/store'
import { ROLE_ROUTES } from '@/utils/constants'

Vue.use(VueRouter)

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue'),
    meta: { title: '登录', noAuth: true }
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('@/views/Register.vue'),
    meta: { title: '注册', noAuth: true }
  },
  { path: '/', redirect: '/dashboard' },
  {
    path: '/dashboard', name: 'Dashboard',
    component: () => import('@/views/Dashboard.vue'),
    meta: { title: '实时监控仪表盘' }
  },
  {
    path: '/transaction', name: 'Transaction',
    component: () => import('@/views/TransactionFlow.vue'),
    meta: { title: '交易流水监控' }
  },
  {
    path: '/alerts', name: 'Alerts',
    component: () => import('@/views/AlertManage.vue'),
    meta: { title: '风险告警管理' }
  },
  {
    path: '/risk-map', name: 'RiskMap',
    component: () => import('@/views/RiskMap.vue'),
    meta: { title: '风险地理分布' }
  },
  {
    path: '/analysis', name: 'Analysis',
    component: () => import('@/views/DataAnalysis.vue'),
    meta: { title: '数据统计分析' }
  },
  {
    path: '/users', name: 'Users',
    component: () => import('@/views/UserManage.vue'),
    meta: { title: '账号管理' }
  },
  {
    path: '/config', name: 'Config',
    component: () => import('@/views/SystemConfig.vue'),
    meta: { title: '评分规则' }
  }
]

const router = new VueRouter({ mode: 'hash', routes })

router.beforeEach((to, from, next) => {
  document.title = to.meta.title ? `${to.meta.title} - 风险监控平台` : '风险监控平台'

  if (to.meta.noAuth) {
    if (store.getters.isLoggedIn) return next('/dashboard')
    return next()
  }

  if (!store.getters.isLoggedIn) return next('/login')

  const role = store.getters.userRole
  const allowed = ROLE_ROUTES[role] || []
  if (allowed.length > 0 && !allowed.includes(to.path)) {
    return next('/dashboard')
  }

  next()
})

export default router
