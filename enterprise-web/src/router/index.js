import { createRouter, createWebHistory } from 'vue-router'
import MainLayout from '../layouts/MainLayout.vue'
import AdminLayout from '../layouts/AdminLayout.vue'

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
      { path: 'chat', name: 'Chat', component: () => import('../pages/Chat.vue'), meta: { title: '智能对话' } },
      { path: 'documents', name: 'Documents', component: () => import('../pages/Documents.vue'), meta: { title: '文档协作' } },
      { path: 'chats', name: 'Chats', component: () => import('../pages/Chats.vue'), meta: { title: '即时通讯' } },
      { path: 'contacts', name: 'Contacts', component: () => import('../pages/Contacts.vue'), meta: { title: '通讯录' } },
      { path: 'meetings', name: 'Meetings', component: () => import('../pages/Meetings.vue'), meta: { title: '会议预约' } },
      { path: 'todos', name: 'Todos', component: () => import('../pages/Todos.vue'), meta: { title: '我的待办' } },
      { path: 'tasks', name: 'Tasks', component: () => import('../pages/Tasks.vue'), meta: { title: '任务协同' } },
      { path: 'analytics', name: 'Analytics', component: () => import('../pages/Analytics.vue'), meta: { title: '数据看板' } },
      { path: 'approvals', name: 'Approvals', component: () => import('../pages/Approvals.vue'), meta: { title: '流程审批' } },
      { path: 'notifications', name: 'Notifications', component: () => import('../pages/Announcements.vue'), meta: { title: '公告通知' } },
    ]
  },
  {
    path: '/admin',
    component: AdminLayout,
    meta: { requiresAuth: true },
    children: [
      { path: '', name: 'AdminDashboard', component: () => import('../pages/admin/Documents.vue'), meta: { title: '文档管理' } },
      { path: 'documents', name: 'AdminDocuments', component: () => import('../pages/admin/Documents.vue'), meta: { title: '文档管理' } },
      { path: 'bases', name: 'AdminBases', component: () => import('../pages/admin/Bases.vue'), meta: { title: '知识库管理' } },
      { path: 'users', name: 'AdminUsers', component: () => import('../pages/admin/Users.vue'), meta: { title: '用户管理' } },
      { path: 'roles', name: 'AdminRoles', component: () => import('../pages/admin/Roles.vue'), meta: { title: '角色管理' } },
      { path: 'logs', name: 'AdminLogs', component: () => import('../pages/admin/Logs.vue'), meta: { title: '操作日志' } },
    ]
  }
]

const router = createRouter({ history: createWebHistory(), routes })

router.beforeEach((to, from, next) => {
  const user = localStorage.getItem('user')
  if (to.meta.requiresAuth && !user) {
    next('/login')
  } else if (to.path === '/login' && user) {
    next('/')
  } else {
    next()
  }
})

export default router
