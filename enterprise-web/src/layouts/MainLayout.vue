<template>
  <div style="height:100vh;display:flex;overflow:hidden">
    <!-- Feishu Style Sidebar -->
    <div class="sidebar-container">
      <div style="padding:12px 16px;">
        <div style="display:flex;align-items:center;gap:10px;margin-bottom:12px;">
          <div style="width:34px;height:34px;border-radius:8px;background:linear-gradient(135deg, #ff6b9d 0%, #c084fc 50%, #22d3ee 100%);display:flex;align-items:center;justify-content:center;font-size:16px;font-weight:700;color:#fff">E</div>
          <span style="color:#1f2937;font-size:15px;font-weight:600">企业工作平台</span>
        </div>

        <!-- Search Box -->
        <div class="search-box">
          <el-icon size="16" color="#9ca3af"><Search /></el-icon>
          <input type="text" placeholder="搜索" class="search-input" />
        </div>
      </div>

      <!-- Navigation -->
      <div style="flex:1;overflow-y:auto;padding:0 12px;">
        <div v-for="item in navItems" :key="item.path" @click="$router.push(item.path)"
          class="nav-item"
          :class="{ 'nav-item-active': isActive(item.path) }">
          <el-icon :size="18"><component :is="item.icon" /></el-icon>
          <span>{{ item.label }}</span>
        </div>
        
        <template v-if="canAccessAdmin">
          <div style="margin:12px 0;border-top:1px solid #f3f4f6;"></div>

          <div @click="$router.push('/admin')"
            class="nav-item"
            :class="{ 'nav-item-active': isAdminActive }">
            <el-icon :size="18"><Setting /></el-icon>
            <span>管理后台</span>
          </div>
        </template>
      </div>

      <!-- User Area -->
      <div style="padding:12px;">
        <el-dropdown trigger="click" @command="handleCommand" style="width:100%">
          <div class="user-item">
            <div class="user-avatar">
              {{ userName.charAt(0) }}
            </div>
            <div style="flex:1;display:flex;flex-direction:column;">
              <span style="font-size:13px;color:#1f2937;font-weight:500;">{{ userName }}</span>
              <span style="font-size:12px;color:#9ca3af;">{{ userRoleLabel }}</span>
            </div>
          </div>
          <template #dropdown><el-dropdown-menu><el-dropdown-item command="logout">退出登录</el-dropdown-item></el-dropdown-menu></template>
        </el-dropdown>
      </div>
    </div>

    <!-- Main Area -->
    <div style="flex:1;display:flex;flex-direction:column;background:#fff;overflow:hidden;min-width:0;box-shadow:-2px 0 8px rgba(0,0,0,0.04);">
      <div class="main-header">
        <span style="font-size:15px;font-weight:600;color:#1f2937;">{{ title }}</span>
        <div style="margin-left:auto;display:flex;align-items:center;gap:12px;">
          <button class="header-btn" title="上传">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
              <polyline points="17 8 12 3 7 8"/>
              <line x1="12" y1="3" x2="12" y2="15"/>
            </svg>
          </button>
          <el-badge :value="unread" :max="99" v-if="unread>0">
            <button class="header-btn" @click="$router.push('/notifications')">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"/>
                <path d="M13.73 21a2 2 0 0 1-3.46 0"/>
              </svg>
            </button>
          </el-badge>
          <button class="header-btn" title="设置">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <circle cx="12" cy="12" r="1"/>
              <circle cx="19" cy="12" r="1"/>
              <circle cx="5" cy="12" r="1"/>
            </svg>
          </button>
        </div>
      </div>
      <div
        :style="mainContentWrapStyle"
        class="main-content-wrap"
        :class="{ 'main-content-chat': route.path === '/chat' }"
      >
        <div class="main-content-inner">
          <router-view v-slot="{ Component }">
            <transition name="page-fade" mode="out-in"><component :is="Component" /></transition>
          </router-view>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Search } from '@element-plus/icons-vue'
import { isAdminUser, readStoredAuth } from '../api/index.js'
const route = useRoute()
const router = useRouter()
const unread = ref(3)
const title = computed(() => route.meta?.title || '工作台')

/**
 * 智能对话页铺满主内容区（无灰边）；其它页保持原有内边距。
 */
const mainContentWrapStyle = computed(() => {
  if (route.path === '/chat') {
    return {
      flex: '1',
      minHeight: '0',
      overflow: 'hidden',
      padding: '0',
      background: '#fff',
      display: 'flex',
      flexDirection: 'column',
    }
  }
  return {
    flex: '1',
    overflowY: 'auto',
    padding: '20px',
    background: '#f5f6f8',
  }
})
const userName = computed(() => {
  try {
    const user = readStoredAuth().user
    return user.realName || user.username || '用户'
  } catch {
    return '用户'
  }
})

const canAccessAdmin = computed(() => isAdminUser())
const userRoleLabel = computed(() => (canAccessAdmin.value ? '管理员' : '普通用户'))

const navItems = [
  { path: '/', icon: 'HomeFilled', label: '工作台' },
  { path: '/chat', icon: 'ChatDotRound', label: '智能对话' },
  { path: '/documents', icon: 'Document', label: '文档协作' },
  { path: '/chats', icon: 'ChatLineSquare', label: '即时通讯' },
  { path: '/contacts', icon: 'User', label: '通讯录' },
  { path: '/meetings', icon: 'Calendar', label: '会议预约' },
  { path: '/todos', icon: 'List', label: '我的待办' },
  { path: '/tasks', icon: 'Aim', label: '任务协同' },
  { path: '/approvals', icon: 'Checked', label: '流程审批' },
  { path: '/notifications', icon: 'Bell', label: '公告通知' },
]

const isAdminActive = computed(() => route.path.startsWith('/admin'))

/**
 * 高亮当前菜单：不能用 startsWith 单独判断，否则 /chats 会匹配 /chat，两个菜单同时高亮。
 */
function isActive(path) {
  const p = route.path
  if (path === '/') {
    return p === '/' || p === ''
  }
  return p === path || p.startsWith(path + '/')
}

function handleCommand(cmd) {
  if (cmd === 'logout') { localStorage.clear(); router.push('/login') }
}
</script>

<style>
.sidebar-container {
  width: 230px;
  background: linear-gradient(180deg, #fff1f2 0%, #ecfdf5 100%);
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  border-right: 1px solid #f3f4f6;
}

.search-box {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  transition: all 0.2s ease;
}

.search-box:focus-within {
  border-color: #c084fc;
  box-shadow: 0 0 0 3px rgba(192, 132, 252, 0.1);
}

.search-input {
  flex: 1;
  border: none;
  outline: none;
  font-size: 13px;
  background: transparent;
  color: #374151;
}

.search-input::placeholder {
  color: #9ca3af;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 9px 12px;
  margin-bottom: 4px;
  border-radius: 10px;
  cursor: pointer;
  font-size: 14px;
  transition: all 0.15s ease;
  color: #6b7280;
}

.nav-item:hover {
  background: rgba(0, 0, 0, 0.03);
  color: #374151;
}

.nav-item-active {
  background: #fff;
  border: 1px solid #e5e7eb;
  color: #1f2937;
  font-weight: 500;
}

.nav-item-active:hover {
  background: #fff;
}

.user-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 10px;
  cursor: pointer;
  border-radius: 10px;
  transition: all 0.15s ease;
}

.user-item:hover {
  background: rgba(0, 0, 0, 0.03);
}

.user-avatar {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  background: linear-gradient(135deg, #ff6b9d 0%, #c084fc 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  font-weight: 600;
  color: #fff;
}

.main-header {
  height: 52px;
  background: #fff;
  border-bottom: 1px solid #f3f4f6;
  display: flex;
  align-items: center;
  padding: 0 20px;
  flex-shrink: 0;
}

.header-btn {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  border: none;
  background: transparent;
  color: #6b7280;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s ease;
}

.header-btn:hover {
  background: #f3f4f6;
  color: #374151;
}

/**
 * 智能对话等页需要子根节点占满高度时，由该内层承担 flex 子项，避免 router-view / transition 打断高度链。
 */
.main-content-inner {
  flex: 1;
  min-height: 0;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.page-fade-enter-active { transition: opacity .2s ease, transform .2s ease; }
.page-fade-leave-active { transition: opacity .12s ease; }
.page-fade-enter-from { opacity:0; transform:translateY(4px); }
.page-fade-leave-to { opacity:0; }
</style>
