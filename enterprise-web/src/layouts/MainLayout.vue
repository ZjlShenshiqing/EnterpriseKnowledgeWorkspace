<template>
  <div style="height:100vh;display:flex;overflow:hidden">
    <!-- Feishu Sidebar -->
    <div style="width:220px;background:var(--bg-sidebar);display:flex;flex-direction:column;flex-shrink:0">
      <div style="padding:20px 16px;display:flex;align-items:center;gap:10px">
        <div style="width:32px;height:32px;border-radius:8px;background:var(--brand-500);display:flex;align-items:center;justify-content:center;font-size:16px;font-weight:700;color:#fff">E</div>
        <span style="color:#fff;font-size:15px;font-weight:600">企业工作平台</span>
      </div>
      <div style="flex:1;overflow-y:auto;padding:4px 8px">
        <div v-for="item in navItems" :key="item.path" @click="$router.push(item.path)"
          :style="{ color: isActive(item.path) ? '#fff' : '#8f959e', background: isActive(item.path) ? 'rgba(255,255,255,0.08)' : 'transparent', borderRadius:'6px' }"
          style="display:flex;align-items:center;gap:10px;padding:8px 12px;margin-bottom:2px;cursor:pointer;font-size:14px;transition:all .15s"
          @mouseenter="e=>{if(!isActive(item.path))e.target.style.background='rgba(255,255,255,0.04)'}"
          @mouseleave="e=>{if(!isActive(item.path))e.target.style.background='transparent'}">
          <el-icon :size="20"><component :is="item.icon" /></el-icon>
          <span>{{ item.label }}</span>
        </div>
      </div>
      <div style="padding:8px 12px;border-top:1px solid rgba(255,255,255,0.06)">
        <div @click="$router.push('/admin')" :style="{ color: isAdminActive?'#fff':'#8f959e', background: isAdminActive?'rgba(255,255,255,0.08)':'transparent', borderRadius:'6px' }"
          style="display:flex;align-items:center;gap:10px;padding:8px 12px;cursor:pointer;font-size:14px;margin-bottom:8px">
          <el-icon :size="20"><Setting /></el-icon><span>管理后台</span>
        </div>
        <el-dropdown trigger="click" @command="handleCommand" style="width:100%">
          <div style="display:flex;align-items:center;gap:8px;padding:6px 8px;cursor:pointer;border-radius:6px;color:#c9cdd4;font-size:13px"
            @mouseenter="e=>e.target.style.background='rgba(255,255,255,0.04)'" @mouseleave="e=>e.target.style.background='transparent'">
            <el-avatar :size="28" style="background:var(--brand-500);font-size:12px">{{ userName.charAt(0) }}</el-avatar>
            <span style="flex:1;overflow:hidden;text-overflow:ellipsis;white-space:nowrap">{{ userName }}</span>
          </div>
          <template #dropdown><el-dropdown-menu><el-dropdown-item command="logout">退出登录</el-dropdown-item></el-dropdown-menu></template>
        </el-dropdown>
      </div>
    </div>

    <!-- Main Area -->
    <div style="flex:1;display:flex;flex-direction:column;background:var(--bg-body);overflow:hidden;min-width:0">
      <div style="height:56px;background:var(--white);border-bottom:1px solid var(--border-default);display:flex;align-items:center;padding:0 24px;flex-shrink:0">
        <span style="font-size:var(--font-size-lg);font-weight:600;color:var(--text-primary)">{{ title }}</span>
        <div style="margin-left:auto;display:flex;align-items:center;gap:16px">
          <el-badge :value="unread" :max="99" v-if="unread>0">
            <el-icon :size="18" color="var(--text-secondary)" style="cursor:pointer" @click="$router.push('/notifications')"><Bell /></el-icon>
          </el-badge>
        </div>
      </div>
      <div style="flex:1;overflow-y:auto;padding:24px">
        <router-view v-slot="{ Component }">
          <transition name="page-fade" mode="out-in"><component :is="Component" /></transition>
        </router-view>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
const route = useRoute()
const router = useRouter()
const unread = ref(3)
const title = computed(() => route.meta?.title || '工作台')
const userName = computed(() => {
  const user = JSON.parse(localStorage.getItem('user') || '{}')
  return user.realName || user.username || '用户'
})

const navItems = [
  { path: '/', icon: 'HomeFilled', label: '工作台' },
  { path: '/chat', icon: 'ChatDotRound', label: '智能对话' },
  { path: '/documents', icon: 'Document', label: '文档协作' },
  { path: '/chats', icon: 'ChatLineSquare', label: '即时通讯' },
  { path: '/contacts', icon: 'User', label: '通讯录' },
  { path: '/meetings', icon: 'Calendar', label: '会议预约' },
  { path: '/todos', icon: 'List', label: '我的待办' },
  { path: '/tasks', icon: 'Aim', label: '任务协同' },
  { path: '/notifications', icon: 'Bell', label: '公告通知' },
]

const isAdminActive = computed(() => route.path.startsWith('/admin'))

function isActive(path) {
  if (path === '/') return route.path === '/'
  return route.path.startsWith(path)
}

function handleCommand(cmd) {
  if (cmd === 'logout') { localStorage.clear(); router.push('/login') }
}
</script>

<style>
.page-fade-enter-active { transition: opacity .2s ease, transform .2s ease; }
.page-fade-leave-active { transition: opacity .12s ease; }
.page-fade-enter-from { opacity:0; transform:translateY(4px); }
.page-fade-leave-to { opacity:0; }
</style>
