<template>
  <div style="height:100vh;display:flex">
    <!-- Icon Sidebar -->
    <div style="width:64px;background:#1a1a2e;display:flex;flex-direction:column;align-items:center;padding-top:16px">
      <el-tooltip v-for="item in navItems" :key="item.path" :content="item.label" placement="right" :show-after="400">
        <div @click="$router.push(item.path)"
             style="width:42px;height:42px;border-radius:10px;display:flex;align-items:center;justify-content:center;margin-bottom:6px;cursor:pointer;transition:all .25s"
             :style="{ background: isActive(item.path) ? '#409eff' : 'transparent', transform: isActive(item.path) ? 'scale(1.05)' : 'scale(1)' }">
          <el-icon :size="22" :color="isActive(item.path) ? '#fff' : '#909399'"><component :is="item.icon" /></el-icon>
        </div>
      </el-tooltip>
      <div style="flex:1"></div>
      <el-tooltip content="管理后台" placement="right" :show-after="400">
        <div @click="$router.push('/admin')"
             style="width:42px;height:42px;border-radius:10px;display:flex;align-items:center;justify-content:center;margin-bottom:8px;cursor:pointer;transition:all .25s"
             :style="{ background: isAdminActive ? '#409eff' : 'transparent', transform: isAdminActive ? 'scale(1.05)' : 'scale(1)' }">
          <el-icon :size="22" :color="isAdminActive ? '#fff' : '#909399'"><Setting /></el-icon>
        </div>
      </el-tooltip>
      <el-tooltip content="个人设置" placement="right" :show-after="400">
        <el-avatar :size="36" style="margin-bottom:14px;cursor:pointer;transition:transform .25s" @mouseenter="e=>e.target.style.transform='scale(1.1)'" @mouseleave="e=>e.target.style.transform='scale(1)'" />
      </el-tooltip>
    </div>

    <!-- Main Content -->
    <div style="flex:1;display:flex;flex-direction:column;background:#f5f6f7;overflow:hidden">
      <div style="height:52px;background:#fff;border-bottom:1px solid #e5e6eb;display:flex;align-items:center;padding:0 20px;flex-shrink:0">
        <span style="font-size:16px;font-weight:600">{{ title }}</span>
        <div style="margin-left:auto;display:flex;align-items:center;gap:16px">
          <el-badge :value="unread" :max="99" v-if="unread>0">
            <el-icon :size="18" color="#606266" style="cursor:pointer" @click="$router.push('/notifications')"><Bell /></el-icon>
          </el-badge>
          <span style="font-size:14px;color:#606266">管理员</span>
          <el-avatar :size="30" />
        </div>
      </div>
      <div style="flex:1;overflow-y:auto;padding:20px">
        <router-view v-slot="{ Component }">
          <transition name="fade-slide" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useRoute } from 'vue-router'
const route = useRoute()
const isAdmin = ref(true)
const unread = ref(3)
const title = computed(() => route.meta?.title || '工作台')

const navItems = [
  { path: '/', icon: 'HomeFilled', label: '工作台' },
  { path: '/chat', icon: 'ChatDotRound', label: '智能对话' },
  { path: '/documents', icon: 'Document', label: '知识文档' },
  { path: '/meetings', icon: 'Calendar', label: '会议预约' },
  { path: '/todos', icon: 'List', label: '我的待办' },
  { path: '/tasks', icon: 'Aim', label: '任务协同' },
  { path: '/notifications', icon: 'Bell', label: '消息通知' },
]

const isAdminActive = computed(() => route.path.startsWith('/admin'))

function isActive(path) {
  if (path === '/') return route.path === '/'
  return route.path.startsWith(path)
}
</script>

<style>
.fade-slide-enter-active { transition: all .25s ease-out; }
.fade-slide-leave-active { transition: all .15s ease-in; }
.fade-slide-enter-from { opacity:0; transform:translateY(8px); }
.fade-slide-leave-to { opacity:0; transform:translateY(-4px); }
</style>
