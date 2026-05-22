<template>
  <div class="contacts-fullpage">
    <!-- Left: org tree -->
    <aside class="contacts-sidebar">
      <div class="sidebar-head">
        <div class="sidebar-title">组织架构</div>
        <div class="search-box">
          <span class="search-icon">&#128269;</span>
          <input v-model="search" placeholder="搜索联系人" class="search-input" />
        </div>
      </div>
      <div class="org-list">
        <div
          @click="selectedDeptId = null"
          class="org-item"
          :class="{ 'org-item-active': selectedDeptId === null }"
        >
          <span class="org-icon">&#11208;</span>
          <span class="org-name">全部成员</span>
          <span class="org-count">{{ users.length }}</span>
        </div>
        <div
          v-for="d in depts"
          :key="d.id"
          @click="selectedDeptId = d.id"
          class="org-item"
          :class="{ 'org-item-active': selectedDeptId === d.id }"
        >
          <span class="org-icon">&#11208;</span>
          <span class="org-name">{{ d.name }}</span>
          <span class="org-count">{{ deptUserCount(d.id) }}</span>
        </div>
      </div>
    </aside>

    <!-- Right: user list -->
    <section class="contacts-main">
      <div class="main-head">
        <span class="main-title">{{ currentSectionTitle }}</span>
        <span class="main-count">{{ filteredUsers.length }} 人</span>
      </div>
      <div class="member-list">
        <div v-if="filteredUsers.length === 0" class="empty-tip">暂无联系人</div>
        <div v-for="u in filteredUsers" :key="u.id" class="member-row">
          <div class="member-avatar" :style="{ background: avatarColor(u.id) }">
            {{ (u.realName || u.username).charAt(0) }}
          </div>
          <div class="member-info">
            <div class="member-name">{{ u.realName || u.username }}</div>
            <div class="member-dept">{{ getDeptName(u.deptId) }}</div>
          </div>
          <button class="chat-btn" @click="goChat(u)">发消息</button>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getAuthHeaders } from '../api'

const router = useRouter()
const users = ref([])
const depts = ref([])
const selectedDeptId = ref(null)
const search = ref('')
const avatarColors = ['#3370ff', '#34c759', '#ff9500', '#f54a45', '#af52de', '#5856d6', '#ff3b30', '#007aff']

function isApiSuccess(body) {
  return body && String(body.code) === '200'
}

function authHeaders() {
  return { ...getAuthHeaders(), 'Content-Type': 'application/json' }
}
function avatarColor(id) { return avatarColors[Number(id) % avatarColors.length] }
function getDeptName(id) { return depts.value.find(d => d.id === id)?.name || '' }
function deptUserCount(deptId) { return users.value.filter(u => u.deptId === deptId).length }

const currentSectionTitle = computed(() => {
  if (selectedDeptId.value === null) return '全部成员'
  return getDeptName(selectedDeptId.value) || '部门成员'
})

const filteredUsers = computed(() => {
  let list = selectedDeptId.value ? users.value.filter(u => u.deptId === selectedDeptId.value) : users.value
  if (search.value.trim()) {
    const kw = search.value.trim().toLowerCase()
    list = list.filter(u =>
      (u.realName || u.username).toLowerCase().includes(kw) ||
      getDeptName(u.deptId).toLowerCase().includes(kw)
    )
  }
  return list
})

onMounted(async () => {
  const h = authHeaders()
  try {
    const [ur, dr] = await Promise.all([
      fetch('/api/contacts/users', { headers: h }),
      fetch('/api/contacts/departments', { headers: h })
    ])
    const userBody = await ur.json()
    const deptBody = await dr.json()
    if (!ur.ok || !isApiSuccess(userBody)) {
      ElMessage.error(userBody?.message || '加载联系人失败')
      return
    }
    if (!dr.ok || !isApiSuccess(deptBody)) {
      ElMessage.error(deptBody?.message || '加载部门失败')
      return
    }
    users.value = (userBody.data || []).map(u => ({
      ...u,
      id: Number(u.id),
      deptId: u.deptId != null ? Number(u.deptId) : null
    }))
    depts.value = (deptBody.data || []).map(d => ({
      ...d,
      id: Number(d.id)
    }))
  } catch (e) {
    ElMessage.error('无法加载通讯录，请确认网关已启动（端口 8086）')
  }
})

function goChat(user) {
  router.push({
    path: '/chats',
    query: {
      userId: String(user.id),
      userName: user.realName || user.username || ''
    }
  })
}
</script>

<style scoped>
.contacts-fullpage {
  display: flex;
  flex: 1;
  min-height: 0;
  width: 100%;
  height: 100%;
  background: #fff;
  overflow: hidden;
}

.contacts-sidebar {
  width: 260px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  background: #fafafa;
  border-right: 1px solid #e5e6eb;
}

.sidebar-head {
  padding: 14px 16px;
  border-bottom: 1px solid #eef0f3;
}

.sidebar-title {
  font-size: 16px;
  font-weight: 600;
  color: #1f2329;
  margin-bottom: 10px;
}

.search-box {
  display: flex;
  align-items: center;
  gap: 6px;
  background: #e5e6eb;
  border-radius: 6px;
  padding: 7px 12px;
}

.search-icon {
  color: #8f959e;
  font-size: 13px;
  flex-shrink: 0;
}

.search-input {
  flex: 1;
  border: none;
  background: transparent;
  outline: none;
  font-size: 12px;
  color: #333;
}

.search-input::placeholder {
  color: #8f959e;
}

.org-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px 0;
}

.org-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 9px 16px;
  cursor: pointer;
  font-size: 13px;
  color: #1f2329;
  transition: background 0.15s ease;
}

.org-item:hover {
  background: rgba(51, 112, 255, 0.06);
}

.org-item-active {
  background: #e8f3ff;
  color: #3370ff;
  font-weight: 500;
}

.org-icon {
  font-size: 10px;
  color: #8f959e;
}

.org-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.org-count {
  font-size: 11px;
  color: #bbb;
  flex-shrink: 0;
}

.contacts-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.main-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 20px;
  border-bottom: 1px solid #e5e6eb;
  flex-shrink: 0;
}

.main-title {
  font-size: 15px;
  font-weight: 600;
  color: #1f2329;
}

.main-count {
  font-size: 12px;
  color: #8f959e;
}

.member-list {
  flex: 1;
  overflow-y: auto;
}

.empty-tip {
  text-align: center;
  padding: 80px 20px;
  color: #bbb;
  font-size: 14px;
}

.member-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 20px;
  border-bottom: 1px solid #f5f6f7;
  transition: background 0.15s ease;
}

.member-row:hover {
  background: #fafafa;
}

.member-avatar {
  width: 40px;
  height: 40px;
  border-radius: 8px;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 15px;
  font-weight: 500;
  flex-shrink: 0;
}

.member-info {
  flex: 1;
  min-width: 0;
}

.member-name {
  font-size: 14px;
  font-weight: 500;
  color: #1f2329;
}

.member-dept {
  font-size: 12px;
  color: #8f959e;
  margin-top: 2px;
}

.chat-btn {
  padding: 5px 14px;
  border: 1px solid #3370ff;
  border-radius: 4px;
  background: #fff;
  color: #3370ff;
  font-size: 12px;
  cursor: pointer;
  font-weight: 500;
  flex-shrink: 0;
  transition: all 0.15s ease;
}

.chat-btn:hover {
  background: #3370ff;
  color: #fff;
}
</style>
