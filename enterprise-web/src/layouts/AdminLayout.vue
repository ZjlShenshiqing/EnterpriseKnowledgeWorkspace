<template>
  <div class="admin-shell">
    <aside :class="['admin-sidebar', { collapsed }]">
      <div class="admin-brand">
        <div class="admin-brand-mark">R</div>
        <div v-if="!collapsed" class="admin-brand-copy">
          <div class="admin-brand-title">Ragent 管理后台</div>
          <div class="admin-brand-subtitle">Knowledge Console</div>
        </div>
      </div>

      <div class="admin-sidebar-groups">
        <section class="admin-nav-section">
          <div v-if="!collapsed" class="admin-nav-label">导航</div>
          <el-menu
            :default-active="route.path"
            :collapse="collapsed"
            :default-openeds="openedMenus"
            router
            class="admin-menu"
            background-color="transparent"
            text-color="#aeb6c9"
            active-text-color="#ffffff"
          >
            <el-menu-item index="/admin/analytics">
              <el-icon><DataAnalysis /></el-icon>
              <span>Dashboard</span>
            </el-menu-item>

            <el-menu-item index="/admin/knowledge">
              <el-icon><Coin /></el-icon>
              <span>知识库管理</span>
            </el-menu-item>

            <el-sub-menu index="intent-group">
              <template #title>
                <el-icon><Files /></el-icon>
                <span>意图管理</span>
              </template>
              <el-menu-item index="/admin/intents/config">意图树配置</el-menu-item>
              <el-menu-item index="/admin/intents/list">意图列表</el-menu-item>
            </el-sub-menu>

            <el-sub-menu index="pipeline-group">
              <template #title>
                <el-icon><Connection /></el-icon>
                <span>数据通道</span>
              </template>
              <el-menu-item index="/admin/pipelines/manage">流水线管理</el-menu-item>
              <el-menu-item index="/admin/pipelines/tasks">流水线任务</el-menu-item>
            </el-sub-menu>

            <el-menu-item index="/admin/keyword-mappings">
              <el-icon><Key /></el-icon>
              <span>关键词映射</span>
            </el-menu-item>

            <el-menu-item index="/admin/traces">
              <el-icon><Share /></el-icon>
              <span>链路追踪</span>
            </el-menu-item>
          </el-menu>
        </section>

        <section class="admin-nav-section">
          <div v-if="!collapsed" class="admin-nav-label">设置</div>
          <el-menu
            :default-active="route.path"
            :collapse="collapsed"
            router
            class="admin-menu"
            background-color="transparent"
            text-color="#aeb6c9"
            active-text-color="#ffffff"
          >
            <el-menu-item index="/admin/users">
              <el-icon><User /></el-icon>
              <span>用户管理</span>
            </el-menu-item>
            <el-menu-item index="/admin/logs">
              <el-icon><Tickets /></el-icon>
              <span>操作日志</span>
            </el-menu-item>
            <el-menu-item index="/admin/examples">
              <el-icon><Opportunity /></el-icon>
              <span>示例问题</span>
            </el-menu-item>
            <el-menu-item index="/admin/settings">
              <el-icon><Setting /></el-icon>
              <span>系统设置</span>
            </el-menu-item>
          </el-menu>
        </section>
      </div>

      <button class="admin-collapse" @click="collapsed = !collapsed">
        <span>{{ collapsed ? '>>' : '<<' }}</span>
        <span v-if="!collapsed">收起侧边栏</span>
      </button>
    </aside>

    <main class="admin-main">
      <header class="admin-topbar">
        <div class="admin-search">
          <el-icon class="admin-search-icon"><Search /></el-icon>
          <input v-model="searchKeyword" :placeholder="searchPlaceholder" />
          <span class="admin-search-kbd">Ctrl K</span>
        </div>

        <div class="admin-top-actions">
          <button class="admin-ghost-btn" @click="router.push('/chat')">
            <el-icon><ChatDotRound /></el-icon>
            <span>返回聊天</span>
          </button>

          <div class="admin-profile">
            <div class="admin-avatar">{{ initials }}</div>
            <span class="admin-profile-name">{{ displayName }}</span>
            <el-icon><ArrowDown /></el-icon>
          </div>
        </div>
      </header>

      <section class="admin-content">
        <router-view />
      </section>
    </main>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()
const collapsed = ref(false)
const searchKeyword = ref('')

const currentUser = computed(() => {
  try {
    return JSON.parse(localStorage.getItem('user') || '{}')
  } catch {
    return {}
  }
})

const displayName = computed(() => currentUser.value.username || currentUser.value.realName || 'admin')
const initials = computed(() => String(displayName.value).slice(0, 1).toUpperCase())

const searchPlaceholder = computed(() => {
  if (route.path.includes('/intents')) return '筛选意图配置...'
  if (route.path.includes('/pipelines')) return '筛选流水线...'
  if (route.path.includes('/users')) return '筛选用户...'
  return '筛选知识库...'
})

const openedMenus = ['intent-group', 'pipeline-group']
</script>

<style scoped>
.admin-shell {
  height: 100vh;
  display: flex;
  overflow: hidden;
  background: #f5f6f8;
}

.admin-sidebar {
  width: 230px;
  flex-shrink: 0;
  padding: 12px 12px 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  background: linear-gradient(180deg, rgba(17, 20, 31, 0.98) 0%, rgba(27, 32, 47, 0.98) 100%);
  color: #fff;
  border-right: 1px solid rgba(255, 255, 255, 0.06);
  box-shadow: inset -1px 0 0 rgba(255, 255, 255, 0.04);
  overflow: hidden;
}

.admin-sidebar.collapsed {
  width: 64px;
  padding-inline: 8px;
}

.admin-brand {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 4px 4px 8px;
}

.admin-brand-mark {
  width: 34px;
  height: 34px;
  border-radius: 8px;
  display: grid;
  place-items: center;
  font-weight: 700;
  font-size: 16px;
  color: #fff;
  background: linear-gradient(135deg, #6b4cff 0%, #4731d8 100%);
  box-shadow: 0 8px 18px rgba(84, 67, 229, 0.2);
  flex-shrink: 0;
}

.admin-brand-title {
  font-size: 15px;
  font-weight: 600;
  letter-spacing: 0.01em;
}

.admin-brand-subtitle {
  margin-top: 2px;
  color: #8f99b1;
  font-size: 12px;
}

.admin-sidebar-groups {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.admin-nav-section {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.admin-nav-label {
  padding: 0 12px;
  font-size: 11px;
  color: #666f88;
  letter-spacing: 0.12em;
}

:deep(.admin-menu) {
  border-right: none;
}

:deep(.admin-menu .el-menu-item),
:deep(.admin-menu .el-sub-menu__title) {
  margin-bottom: 4px;
  min-height: 36px;
  height: 36px;
  line-height: 36px;
  border-radius: 10px;
  font-size: 14px;
  padding-inline: 12px !important;
}

:deep(.admin-menu .el-menu-item.is-active) {
  background: linear-gradient(135deg, rgba(92, 90, 246, 0.34), rgba(70, 58, 180, 0.42)) !important;
  box-shadow: inset 0 0 0 1px rgba(128, 143, 255, 0.18);
}

:deep(.admin-menu .el-menu-item:hover),
:deep(.admin-menu .el-sub-menu__title:hover) {
  background: rgba(255, 255, 255, 0.05) !important;
}

:deep(.admin-menu .el-sub-menu .el-menu-item) {
  min-width: auto;
  margin-left: 8px;
  font-size: 13px;
  min-height: 34px;
  height: 34px;
  line-height: 34px;
}

:deep(.admin-menu .el-sub-menu .el-menu) {
  background: transparent !important;
}

.admin-collapse {
  width: 100%;
  height: 32px;
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.03);
  color: #9ba6bd;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  cursor: pointer;
  font-size: 12px;
  transition: all 0.2s ease;
  flex-shrink: 0;
}

.admin-collapse:hover {
  color: #ffffff;
  background: rgba(255, 255, 255, 0.08);
}

.admin-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: #fff;
  box-shadow: -2px 0 8px rgba(0, 0, 0, 0.04);
}

.admin-topbar {
  height: 52px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 0 20px;
  background: #fff;
  border-bottom: 1px solid #f3f4f6;
}

.admin-search {
  flex: 1;
  max-width: 320px;
  height: 36px;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 12px;
  border-radius: 10px;
  background: #fff;
  border: 1px solid #e5e7eb;
  box-shadow: none;
}

.admin-search input {
  flex: 1;
  border: none;
  outline: none;
  background: transparent;
  font-size: 13px;
  color: #374151;
}

.admin-search-kbd {
  padding: 2px 8px;
  border-radius: 8px;
  background: #f3f4f6;
  border: 1px solid #e5e7eb;
  color: #9ca3af;
  font-size: 11px;
}

.admin-search-icon {
  color: #9ca3af;
}

.admin-top-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.admin-ghost-btn {
  height: 32px;
  border-radius: 8px;
  border: 1px solid #e5e7eb;
  background: #fff;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 0 12px;
  color: #374151;
  font-size: 13px;
  cursor: pointer;
  box-shadow: none;
}

.admin-profile {
  min-width: auto;
  height: 32px;
  border-radius: 8px;
  padding: 0 10px 0 4px;
  background: #fff;
  border: 1px solid #e5e7eb;
  display: flex;
  align-items: center;
  gap: 8px;
  box-shadow: none;
}

.admin-avatar {
  width: 24px;
  height: 24px;
  border-radius: 6px;
  display: grid;
  place-items: center;
  background: linear-gradient(135deg, #ff6b9d 0%, #c084fc 100%);
  color: #fff;
  font-size: 12px;
  font-weight: 600;
}

.admin-profile-name {
  flex: 1;
  color: #1f2937;
  font-size: 13px;
  font-weight: 500;
}

.admin-content {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 20px;
  background: #f5f6f8;
}

@media (max-width: 1180px) {
  .admin-topbar {
    height: auto;
    min-height: 52px;
    padding: 10px 20px;
    flex-wrap: wrap;
  }

  .admin-search {
    max-width: none;
    width: 100%;
  }

  .admin-top-actions {
    width: 100%;
    justify-content: flex-end;
    flex-wrap: wrap;
  }
}
</style>
