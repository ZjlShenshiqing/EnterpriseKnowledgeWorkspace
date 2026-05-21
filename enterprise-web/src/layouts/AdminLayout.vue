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

          <button class="admin-pill-btn">
            <el-icon><Star /></el-icon>
            <span>Star</span>
            <span class="admin-pill-count">748</span>
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
  min-height: 100vh;
  display: grid;
  grid-template-columns: auto 1fr;
  background:
    radial-gradient(circle at top left, rgba(63, 81, 181, 0.14), transparent 24%),
    linear-gradient(180deg, #eff2f8 0%, #f6f8fc 100%);
}

.admin-sidebar {
  width: 286px;
  padding: 22px 18px 24px;
  display: flex;
  flex-direction: column;
  gap: 22px;
  background: linear-gradient(180deg, rgba(17, 20, 31, 0.98) 0%, rgba(27, 32, 47, 0.98) 100%);
  color: #fff;
  border-right: 1px solid rgba(255, 255, 255, 0.06);
  box-shadow: inset -1px 0 0 rgba(255, 255, 255, 0.04);
}

.admin-sidebar.collapsed {
  width: 94px;
  padding-inline: 12px;
}

.admin-brand {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 10px 12px;
}

.admin-brand-mark {
  width: 44px;
  height: 44px;
  border-radius: 14px;
  display: grid;
  place-items: center;
  font-weight: 700;
  font-size: 20px;
  color: #fff;
  background: linear-gradient(135deg, #6b4cff 0%, #4731d8 100%);
  box-shadow: 0 16px 30px rgba(84, 67, 229, 0.25);
}

.admin-brand-title {
  font-size: 15px;
  font-weight: 700;
  letter-spacing: 0.01em;
}

.admin-brand-subtitle {
  margin-top: 4px;
  color: #8f99b1;
  font-size: 13px;
}

.admin-sidebar-groups {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.admin-nav-section {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.admin-nav-label {
  padding: 0 14px;
  font-size: 12px;
  color: #666f88;
  letter-spacing: 0.14em;
}

:deep(.admin-menu) {
  border-right: none;
}

:deep(.admin-menu .el-menu-item),
:deep(.admin-menu .el-sub-menu__title) {
  margin-bottom: 8px;
  min-height: 44px;
  border-radius: 12px;
  font-size: 15px;
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
  margin-left: 12px;
  font-size: 14px;
}

:deep(.admin-menu .el-sub-menu .el-menu) {
  background: transparent !important;
}

.admin-collapse {
  width: 100%;
  height: 40px;
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.03);
  color: #9ba6bd;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.admin-collapse:hover {
  color: #ffffff;
  background: rgba(255, 255, 255, 0.08);
}

.admin-main {
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.admin-topbar {
  min-height: 94px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  padding: 22px 34px;
  background: rgba(255, 255, 255, 0.76);
  backdrop-filter: blur(18px);
  border-bottom: 1px solid rgba(207, 214, 226, 0.7);
}

.admin-search {
  flex: 1;
  max-width: 400px;
  height: 52px;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 0 16px;
  border-radius: 14px;
  background: #ffffff;
  border: 1px solid #dfe6f2;
  box-shadow: 0 8px 22px rgba(16, 24, 40, 0.06);
}

.admin-search input {
  flex: 1;
  border: none;
  outline: none;
  background: transparent;
  font-size: 15px;
  color: #4a556d;
}

.admin-search-kbd {
  padding: 4px 10px;
  border-radius: 10px;
  background: #f4f7fb;
  border: 1px solid #d9e1ef;
  color: #8a94aa;
  font-size: 12px;
}

.admin-search-icon {
  color: #75829a;
}

.admin-top-actions {
  display: flex;
  align-items: center;
  gap: 14px;
}

.admin-ghost-btn,
.admin-pill-btn {
  height: 48px;
  border-radius: 16px;
  border: 1px solid #d7dfed;
  background: rgba(255, 255, 255, 0.95);
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 18px;
  color: #344054;
  font-size: 15px;
  cursor: pointer;
  box-shadow: 0 8px 18px rgba(15, 23, 42, 0.06);
}

.admin-pill-count {
  padding: 2px 8px;
  border-radius: 999px;
  background: #eef2f8;
  color: #667085;
  font-size: 13px;
}

.admin-profile {
  min-width: 132px;
  height: 48px;
  border-radius: 999px;
  padding: 0 16px 0 10px;
  background: rgba(255, 255, 255, 0.95);
  border: 1px solid #d7dfed;
  display: flex;
  align-items: center;
  gap: 10px;
  box-shadow: 0 8px 18px rgba(15, 23, 42, 0.06);
}

.admin-avatar {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  background: linear-gradient(135deg, #ffcf7d 0%, #ff9f46 100%);
  color: #36210a;
  font-weight: 700;
}

.admin-profile-name {
  flex: 1;
  color: #344054;
  font-weight: 600;
}

.admin-content {
  padding: 26px 28px 32px;
}

@media (max-width: 1180px) {
  .admin-topbar {
    padding: 18px 22px;
    flex-direction: column;
    align-items: stretch;
  }

  .admin-search {
    max-width: none;
  }

  .admin-top-actions {
    justify-content: flex-end;
    flex-wrap: wrap;
  }
}
</style>
