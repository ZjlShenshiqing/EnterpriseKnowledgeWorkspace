import { createRouter, createWebHistory } from 'vue-router'
import MainLayout from '../layouts/MainLayout.vue'

const routes = [
  {
    path: '/',
    component: MainLayout,
    children: [
      { path: '', name: 'Dashboard', component: () => import('../pages/Dashboard.vue'), meta: { title: '工作台' } },
      { path: 'knowledge/documents', name: 'Documents', component: () => import('../pages/Documents.vue'), meta: { title: '文档管理' } },
      { path: 'knowledge/bases', name: 'KnowledgeBases', component: () => import('../pages/KnowledgeBases.vue'), meta: { title: '知识库' } },
      { path: 'agent/chat', name: 'AgentChat', component: () => import('../pages/AgentChat.vue'), meta: { title: '智能对话' } },
      { path: 'qa/ask', name: 'QaAsk', component: () => import('../pages/QaAsk.vue'), meta: { title: '智能问答' } },
      { path: 'meetings', name: 'Meetings', component: () => import('../pages/Meetings.vue'), meta: { title: '会议预约' } },
      { path: 'todos', name: 'Todos', component: () => import('../pages/Todos.vue'), meta: { title: '我的待办' } },
      { path: 'tasks', name: 'Tasks', component: () => import('../pages/Tasks.vue'), meta: { title: '任务协同' } },
      { path: 'notifications', name: 'Notifications', component: () => import('../pages/Notifications.vue'), meta: { title: '消息通知' } },
      { path: 'admin/users', name: 'AdminUsers', component: () => import('../pages/AdminUsers.vue'), meta: { title: '用户管理' } },
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router
