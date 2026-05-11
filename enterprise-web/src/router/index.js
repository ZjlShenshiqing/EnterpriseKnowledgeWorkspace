import { createRouter, createWebHistory } from 'vue-router'
import MainLayout from '../layouts/MainLayout.vue'
import AdminLayout from '../layouts/AdminLayout.vue'

const routes = [
  {
    path: '/',
    component: MainLayout,
    children: [
      { path: '', name: 'Dashboard', component: () => import('../pages/Dashboard.vue'), meta: { title: '工作台' } },
      { path: 'chat', name: 'Chat', component: () => import('../pages/Chat.vue'), meta: { title: '智能对话' } },
      { path: 'documents', name: 'Documents', component: () => import('../pages/Documents.vue'), meta: { title: '知识文档' } },
      { path: 'meetings', name: 'Meetings', component: () => import('../pages/Meetings.vue'), meta: { title: '会议预约' } },
      { path: 'todos', name: 'Todos', component: () => import('../pages/Todos.vue'), meta: { title: '我的待办' } },
      { path: 'tasks', name: 'Tasks', component: () => import('../pages/Tasks.vue'), meta: { title: '任务协同' } },
      { path: 'notifications', name: 'Notifications', component: () => import('../pages/Notifications.vue'), meta: { title: '消息通知' } },
    ]
  },
  {
    path: '/admin',
    component: AdminLayout,
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

export default createRouter({ history: createWebHistory(), routes })
