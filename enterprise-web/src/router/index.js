import { createRouter, createWebHistory } from 'vue-router'
import MainLayout from '../layouts/MainLayout.vue'
import AdminLayout from '../layouts/AdminLayout.vue'
import { isAdminUser } from '../api/index.js'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('../pages/Login.vue'),
    meta: { title: '登录' }
  },
  {
    path: '/',
    component: MainLayout,
    meta: { requiresAuth: true },
    children: [
      { path: '', name: 'Dashboard', component: () => import('../pages/Dashboard.vue'), meta: { title: '工作台' } },
      { path: 'chat', name: 'Chat', component: () => import('../pages/Chat.vue'), meta: { title: '智能知识问答' } },
      { path: 'documents', name: 'Documents', component: () => import('../pages/Documents.vue'), meta: { title: '文档协作' } },
      { path: 'chats', name: 'Chats', component: () => import('../pages/Chats.vue'), meta: { title: '即时通讯' } },
      { path: 'contacts', name: 'Contacts', component: () => import('../pages/Contacts.vue'), meta: { title: '通讯录' } },
      { path: 'meetings', name: 'Meetings', component: () => import('../pages/Meetings.vue'), meta: { title: '会议预约' } },
      { path: 'todos', name: 'Todos', component: () => import('../pages/Todos.vue'), meta: { title: '我的待办' } },
      { path: 'tasks', name: 'Tasks', component: () => import('../pages/Tasks.vue'), meta: { title: '任务协同' } },
      { path: 'approvals', name: 'Approvals', component: () => import('../pages/Approvals.vue'), meta: { title: '流程审批' } },
      { path: 'notifications', name: 'Notifications', component: () => import('../pages/Announcements.vue'), meta: { title: '公告通知' } },
    ]
  },
  {
    path: '/admin',
    component: AdminLayout,
    meta: { requiresAuth: true, requiresAdmin: true },
    children: [
      { path: '', name: 'AdminDashboard', component: () => import('../pages/Analytics.vue'), meta: { title: 'Dashboard' } },
      { path: 'knowledge', name: 'AdminKnowledge', component: () => import('../pages/admin/KnowledgeHub.vue'), meta: { title: '知识库管理' } },
      { path: 'intents/config', name: 'AdminIntentConfig', component: () => import('../pages/admin/IntentConfig.vue'), meta: { title: '意图树配置' } },
      { path: 'intents/list', name: 'AdminIntentList', component: () => import('../pages/admin/IntentList.vue'), meta: { title: '意图列表' } },
      { path: 'pipelines/manage', name: 'AdminPipelineManage', component: () => import('../pages/admin/PipelineManage.vue'), meta: { title: '流水线管理' } },
      { path: 'pipelines/tasks', name: 'AdminPipelineTasks', component: () => import('../pages/admin/PipelineTasks.vue'), meta: { title: '流水线任务' } },
      { path: 'keyword-mappings', name: 'AdminKeywordMappings', component: () => import('../pages/admin/KeywordMappings.vue'), meta: { title: '关键词映射' } },
      { path: 'traces', name: 'AdminTraces', component: () => import('../pages/admin/TraceLinks.vue'), meta: { title: '链路追踪' } },
      { path: 'documents', name: 'AdminDocuments', component: () => import('../pages/admin/Documents.vue'), meta: { title: '文档管理' } },
      { path: 'bases', name: 'AdminBases', component: () => import('../pages/admin/Bases.vue'), meta: { title: '知识库管理' } },
      { path: 'users', name: 'AdminUsers', component: () => import('../pages/admin/Users.vue'), meta: { title: '用户管理' } },
      { path: 'examples', name: 'AdminExamples', component: () => import('../pages/admin/Examples.vue'), meta: { title: '示例问题' } },
      { path: 'settings', name: 'AdminSettings', component: () => import('../pages/admin/Settings.vue'), meta: { title: '系统设置' } },
      { path: 'roles', name: 'AdminRoles', component: () => import('../pages/admin/Roles.vue'), meta: { title: '角色管理' } },
      { path: 'analytics', name: 'AdminAnalytics', component: () => import('../pages/Analytics.vue'), meta: { title: '数据看板' } },
      { path: 'logs', name: 'AdminLogs', component: () => import('../pages/admin/Logs.vue'), meta: { title: '操作日志' } },
    ]
  }
]

const router = createRouter({ history: createWebHistory(), routes })

router.beforeEach((to, from, next) => {
  const user = localStorage.getItem('user')
  if (to.meta.requiresAuth && !user) {
    next('/login')
  } else if (to.meta.requiresAdmin && !isAdminUser()) {
    next('/')
  } else if (to.path === '/login' && user) {
    next('/')
  } else {
    next()
  }
})

export default router
