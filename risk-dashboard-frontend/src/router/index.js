/**
 * 路由配置
 */
import Vue from 'vue'
import VueRouter from 'vue-router'

Vue.use(VueRouter)

const routes = [
  {
    path: '/',
    redirect: '/dashboard'
  },
  {
    path: '/dashboard',
    name: 'Dashboard',
    component: () => import('@/views/Dashboard.vue'),
    meta: { title: '实时监控仪表盘' }
  },
  {
    path: '/transaction',
    name: 'Transaction',
    component: () => import('@/views/TransactionFlow.vue'),
    meta: { title: '交易流水监控' }
  },
  {
    path: '/alerts',
    name: 'Alerts',
    component: () => import('@/views/AlertManage.vue'),
    meta: { title: '风险告警管理' }
  },
  {
    path: '/risk-map',
    name: 'RiskMap',
    component: () => import('@/views/RiskMap.vue'),
    meta: { title: '风险地理分布' }
  },
  {
    path: '/analysis',
    name: 'Analysis',
    component: () => import('@/views/DataAnalysis.vue'),
    meta: { title: '数据统计分析' }
  },
  {
    path: '/config',
    name: 'Config',
    component: () => import('@/views/SystemConfig.vue'),
    meta: { title: '系统配置' }
  }
]

const router = new VueRouter({
  mode: 'hash',
  routes
})

router.beforeEach((to, from, next) => {
  document.title = to.meta.title ? `${to.meta.title} - 风险监控平台` : '风险监控平台'
  next()
})

export default router
