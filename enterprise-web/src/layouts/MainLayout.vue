<template>
  <div style="height:100vh;display:flex">
    <!-- Icon Sidebar -->
    <div style="width:64px;background:#1a1a2e;display:flex;flex-direction:column;align-items:center;padding-top:12px">
      <div v-for="item in navItems" :key="item.path"
           @click="$router.push(item.path)"
           style="width:44px;height:44px;border-radius:10px;display:flex;align-items:center;justify-content:center;margin-bottom:8px;cursor:pointer"
           :style="{ background: isActive(item.path) ? '#409eff' : 'transparent' }">
        <el-icon :size="22" :color="isActive(item.path) ? '#fff' : '#909399'"><component :is="item.icon" /></el-icon>
      </div>
      <div style="flex:1"></div>
      <div v-if="isAdmin" @click="$router.push('/admin')"
           style="width:44px;height:44px;border-radius:10px;display:flex;align-items:center;justify-content:center;margin-bottom:12px;cursor:pointer"
           :style="{ background: isAdminActive ? '#409eff' : 'transparent' }">
        <el-icon :size="22" :color="isAdminActive ? '#fff' : '#909399'"><Setting /></el-icon>
      </div>
      <el-avatar :size="36" style="margin-bottom:12px" />
    </div>

    <!-- Main Content -->
    <div style="flex:1;display:flex;flex-direction:column;background:#f5f6f7;overflow:hidden">
      <div style="height:52px;background:#fff;border-bottom:1px solid #e5e6eb;display:flex;align-items:center;padding:0 20px;flex-shrink:0">
        <span style="font-size:16px;font-weight:600">{{ title }}</span>
        <div style="margin-left:auto;display:flex;align-items:center;gap:12px">
          <span style="font-size:14px;color:#606266">管理员</span>
        </div>
      </div>
      <div style="flex:1;overflow-y:auto;padding:20px">
        <router-view />
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useRoute } from 'vue-router'
const route = useRoute()
const isAdmin = ref(true)
const title = computed(() => route.meta?.title || '工作台')

const navItems = [
  { path: '/', icon: 'HomeFilled' },
  { path: '/chat', icon: 'ChatDotRound' },
  { path: '/documents', icon: 'Document' },
  { path: '/meetings', icon: 'Calendar' },
  { path: '/todos', icon: 'List' },
  { path: '/tasks', icon: 'Aim' },
  { path: '/notifications', icon: 'Bell' },
]

const isAdminActive = computed(() => route.path.startsWith('/admin'))

function isActive(path) {
  if (path === '/') return route.path === '/'
  return route.path.startsWith(path)
}
</script>
