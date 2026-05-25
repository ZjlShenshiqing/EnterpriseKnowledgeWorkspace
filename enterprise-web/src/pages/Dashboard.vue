<template>
  <div style="display:flex;flex-direction:column;gap:24px">
    <!-- Header -->
    <div style="display:flex;justify-content:space-between;align-items:center">
      <div>
        <div style="font-size:22px;font-weight:700;color:#1f2329">工作台</div>
        <div style="font-size:13px;color:#8f959e;margin-top:4px">欢迎回来，{{ userName }}</div>
      </div>
      <div style="display:flex;align-items:center;gap:12px">
        <div class="time-selector">
          <span v-for="t in timeRanges" :key="t"
            :class="['time-item', {active: selectedTime === t}]"
            @click="selectedTime = t">
            {{ t }}
          </span>
        </div>
        <span style="color:#8f959e;font-size:12px">
          <span style="width:8px;height:8px;background:#34c759;border-radius:50%;display:inline-block;margin-right:6px"></span>
          {{ currentTime }}
        </span>
        <el-button circle :icon="Refresh" style="margin-left:8px" @click="refreshData" />
      </div>
    </div>

    <!-- Knowledge Stats -->
    <div class="stat-grid">
      <div class="stat-card" @click="$router.push('/admin/documents')">
        <div class="stat-icon-wrapper">
          <div class="stat-icon" style="background:linear-gradient(135deg,#eff6ff,#dbeafe)">
            <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="#3b82f6" stroke-width="2">
              <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
              <polyline points="14 2 14 8 20 8"/>
            </svg>
          </div>
        </div>
        <div class="stat-value">{{ knowledgeStats.documents || 0 }}</div>
        <div class="stat-label">知识文档</div>
      </div>
      <div class="stat-card" @click="$router.push('/admin/bases')">
        <div class="stat-icon-wrapper">
          <div class="stat-icon" style="background:linear-gradient(135deg,#f0fdf4,#dcfce7)">
            <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="#22c55e" stroke-width="2">
              <path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/>
              <path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"/>
            </svg>
          </div>
        </div>
        <div class="stat-value">{{ knowledgeStats.bases || 0 }}</div>
        <div class="stat-label">知识库</div>
      </div>
      <div class="stat-card" @click="$router.push('/admin/intent-config')">
        <div class="stat-icon-wrapper">
          <div class="stat-icon" style="background:linear-gradient(135deg,#fffbeb,#fef3c7)">
            <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="#f59e0b" stroke-width="2">
              <path d="M22 12h-4l-3 9L9 3l-3 9H2"/>
            </svg>
          </div>
        </div>
        <div class="stat-value">{{ knowledgeStats.intents || 0 }}</div>
        <div class="stat-label">意图配置</div>
      </div>
      <div class="stat-card">
        <div class="stat-icon-wrapper">
          <div class="stat-icon" style="background:linear-gradient(135deg,#fef2f2,#fee2e2)">
            <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="#ef4444" stroke-width="2">
              <circle cx="12" cy="12" r="10"/>
              <polyline points="12 6 12 12 16 14"/>
            </svg>
          </div>
        </div>
        <div class="stat-value">{{ knowledgeStats.sessions || 0 }}</div>
        <div class="stat-label">今日会话</div>
      </div>
    </div>

    <!-- Panels -->
    <div class="content-grid">
      <div class="content-column">
        <!-- Quick Actions -->
        <div class="panel">
          <div class="panel-header">
            <span class="panel-title">快捷操作</span>
          </div>
          <div class="quick-actions-grid">
            <div v-for="a in quickActions" :key="a.label" class="quick-action-item" @click="$router.push(a.path)">
              <div class="quick-action-icon" :style="{background:a.bg}">
                <el-icon :size="20" :color="a.color"><component :is="a.icon" /></el-icon>
              </div>
              <span class="quick-action-label">{{ a.label }}</span>
            </div>
          </div>
        </div>

        <!-- Recent Docs -->
        <div class="panel">
          <div class="panel-header">
            <span class="panel-title">最近文档</span>
            <span class="panel-link" @click="$router.push('/admin/documents')">查看全部 →</span>
          </div>
          <div v-if="!recentDocs.length" class="empty-state">暂无文档</div>
          <div v-else class="doc-list">
            <div v-for="doc in recentDocs.slice(0,5)" :key="doc.id" class="doc-item">
              <div class="doc-icon">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="#8f959e" stroke-width="2">
                  <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
                  <polyline points="14 2 14 8 20 8"/>
                </svg>
              </div>
              <div class="doc-info">
                <div class="doc-title">{{ doc.title }}</div>
                <div class="doc-meta">{{ doc.fileType }} · {{ doc.createdAt || doc.created_at }}</div>
              </div>
            </div>
          </div>
        </div>

        <!-- ToDos -->
        <div class="panel">
          <div class="panel-header">
            <span class="panel-title">今日待办</span>
            <span class="panel-badge">{{ todos.filter(t=>!t.done).length }}</span>
            <span class="panel-link" @click="$router.push('/todos')">查看全部 →</span>
          </div>
          <div v-if="!todos.length" class="empty-state">暂无待办</div>
          <div v-else class="todo-list">
            <div v-for="t in todos.slice(0,5)" :key="t.id" class="todo-item">
              <div class="todo-checkbox" :class="{checked:t.done}" @click="toggleTodo(t)">
                <svg v-if="t.done" width="12" height="12" viewBox="0 0 24 24" fill="#34c759" stroke="#34c759" stroke-width="2">
                  <polyline points="20 6 9 17 4 12"/>
                </svg>
              </div>
              <span class="todo-title" :class="{done:t.done}">{{ t.title }}</span>
              <el-tag v-if="t.priority==='high'" size="small" type="danger">紧急</el-tag>
            </div>
          </div>
        </div>
      </div>
      <div class="content-column">
        <!-- Meetings -->
        <div class="panel">
          <div class="panel-header">
            <span class="panel-title">今日会议</span>
            <span class="panel-badge">{{ todayMeetings.length }}</span>
            <span class="panel-link" @click="$router.push('/meetings')">全部 →</span>
          </div>
          <div v-if="!todayMeetings.length" class="empty-state">今日暂无会议</div>
          <div v-else class="meeting-list">
            <div v-for="m in todayMeetings.slice(0,3)" :key="m.id" class="meeting-item">
              <div class="meeting-time">
                <span class="time-hour">{{ m.start_time?.substring(0,2) || '--' }}</span>
                <span class="time-minute">:00</span>
              </div>
              <div class="meeting-info">
                <div class="meeting-title">{{ m.title }}</div>
                <div class="meeting-room">{{ m.room }}</div>
              </div>
              <a v-if="m.join_url" :href="m.join_url" target="_blank" class="meeting-join">加入</a>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh, DocumentAdd, Calendar, List, Checked } from '@element-plus/icons-vue'

const userName = computed(() => { const u=JSON.parse(localStorage.getItem('user')||'{}'); return u.realName||u.username||'管理员' })

const selectedTime = ref('24h')
const timeRanges = ['24h', '7d', '30d']
const currentTime = computed(() => {
  const now = new Date()
  return `${String(now.getMonth()+1).padStart(2, '0')}/${String(now.getDate()).padStart(2, '0')} ${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}:${String(now.getSeconds()).padStart(2, '0')}`
})

const recentDocs = ref([])
const todos = ref([])
const todayMeetings = ref([])

const knowledgeStats = ref({
  documents: 0,
  bases: 0,
  intents: 0,
  sessions: 0
})

const quickActions = [
  { label:'新建文档', icon:'DocumentAdd', bg:'#eff6ff', color:'#3b82f6', path:'/admin/documents' },
  { label:'新建会议', icon:'Calendar', bg:'#f0fdf4', color:'#22c55e', path:'/meetings' },
  { label:'添加待办', icon:'List', bg:'#fffbeb', color:'#f59e0b', path:'/todos' },
  { label:'意图配置', icon:'Checked', bg:'#fef2f2', color:'#ef4444', path:'/admin/intent-config' },
]

function headers() { const u=JSON.parse(localStorage.getItem('user')||'{}'); return {'X-User-Id':String(u.id||1),'X-Is-Admin':String(u.isAdmin?'true':'false')} }

async function refreshData() {
  await loadData()
  ElMessage.success('刷新成功')
}

async function loadData() {
  try {
    const resp = await fetch('/api/workbench/overview', {headers:headers()})
    const data = (await resp.json()).data||{}
    recentDocs.value = data.recentDocs||[]
    todos.value = (data.todos||[]).map(t=>({...t,done:t.done===1||t.done===true}))
    if (data.meetings) todayMeetings.value = data.meetings.filter(m=>m.date===new Date().toISOString().split('T')[0])

    knowledgeStats.value = {
      documents: data.documentCount || 0,
      bases: data.baseCount || 0,
      intents: data.intentCount || 0,
      sessions: data.todaySessionCount || 0
    }
  } catch(e) {
    console.log('Workbench API not available')
  }
}

function toggleTodo(t) { t.done = !t.done }

onMounted(() => { loadData() })
</script>

<style scoped>
.time-selector {
  display:flex;
  background:#f7f8fa;
  padding:4px;
  border-radius:12px;
}
.time-item {
  padding:6px 16px;
  border-radius:8px;
  font-size:13px;
  cursor:pointer;
  color:#8f959e;
  transition:all .15s;
}
.time-item.active {
  background:#1f2329;
  color:#fff;
  font-weight:600;
}

/* Stat Grid */
.stat-grid {
  display:flex;
  gap:16px;
}
.stat-card {
  flex:1;
  background:#fff;
  border-radius:16px;
  padding:20px;
  cursor:pointer;
  border:1px solid #f2f3f5;
  transition:all .2s;
}
.stat-card:hover {
  box-shadow:0 4px 20px rgba(0,0,0,0.06);
  transform:translateY(-2px);
}
.stat-icon-wrapper {
  margin-bottom:12px;
}
.stat-icon {
  width:44px;
  height:44px;
  border-radius:12px;
  display:flex;
  align-items:center;
  justify-content:center;
}
.stat-value {
  font-size:28px;
  font-weight:700;
  color:#1f2329;
}
.stat-label {
  font-size:13px;
  color:#8f959e;
  margin-top:4px;
}

/* Content Grid */
.content-grid {
  display:flex;
  gap:20px;
}
.content-column {
  flex:1;
  display:flex;
  flex-direction:column;
  gap:16px;
}

/* Panel */
.panel {
  background:#fff;
  border-radius:16px;
  border:1px solid #f2f3f5;
  overflow:hidden;
}
.panel-header {
  display:flex;
  align-items:center;
  gap:8px;
  padding:16px 20px;
  border-bottom:1px solid #f2f3f5;
}
.panel-title {
  font-size:15px;
  font-weight:600;
  color:#1f2329;
}
.panel-link {
  margin-left:auto;
  font-size:12px;
  color:#3b82f6;
  cursor:pointer;
}
.panel-badge {
  background:#f7f8fa;
  color:#1f2329;
  font-size:12px;
  font-weight:600;
  padding:2px 8px;
  border-radius:10px;
}

/* Quick Actions */
.quick-actions-grid {
  display:flex;
  flex-wrap:wrap;
  padding:16px;
  gap:12px;
}
.quick-action-item {
  width:calc(50% - 6px);
  display:flex;
  flex-direction:column;
  align-items:center;
  gap:8px;
  padding:12px;
  border-radius:12px;
  cursor:pointer;
  transition:all .15s;
}
.quick-action-item:hover {
  background:#f7f8fa;
}
.quick-action-icon {
  width:40px;
  height:40px;
  border-radius:10px;
  display:flex;
  align-items:center;
  justify-content:center;
}
.quick-action-label {
  font-size:12px;
  color:#8f959e;
}

/* Empty State */
.empty-state {
  padding:30px 20px;
  text-align:center;
  color:#8f959e;
  font-size:13px;
}

/* Doc List */
.doc-list {
  padding:0 20px;
}
.doc-item {
  display:flex;
  align-items:center;
  gap:10px;
  padding:10px 0;
  border-bottom:1px solid #f7f8fa;
}
.doc-item:last-child {
  border-bottom:none;
}
.doc-icon {
  width:28px;
  height:28px;
  background:#f7f8fa;
  border-radius:6px;
  display:flex;
  align-items:center;
  justify-content:center;
  flex-shrink:0;
}
.doc-info {
  flex:1;
  min-width:0;
}
.doc-title {
  font-size:13px;
  color:#1f2329;
  overflow:hidden;
  text-overflow:ellipsis;
  white-space:nowrap;
}
.doc-meta {
  font-size:11px;
  color:#8f959e;
  margin-top:2px;
}

/* Todo List */
.todo-list {
  padding:0 20px;
}
.todo-item {
  display:flex;
  align-items:center;
  gap:10px;
  padding:8px 0;
  border-bottom:1px solid #f7f8fa;
}
.todo-item:last-child {
  border-bottom:none;
}
.todo-checkbox {
  width:18px;
  height:18px;
  border-radius:6px;
  border:2px solid #d1d5db;
  cursor:pointer;
  display:flex;
  align-items:center;
  justify-content:center;
  flex-shrink:0;
  transition:all .15s;
}
.todo-checkbox.checked {
  border-color:#34c759;
  background:#ecfdf5;
}
.todo-title {
  flex:1;
  font-size:13px;
  color:#1f2329;
}
.todo-title.done {
  text-decoration:line-through;
  color:#8f959e;
}

/* Meeting List */
.meeting-list {
  padding:0 20px;
}
.meeting-item {
  display:flex;
  align-items:center;
  gap:12px;
  padding:10px 0;
  border-bottom:1px solid #f7f8fa;
}
.meeting-item:last-child {
  border-bottom:none;
}
.meeting-time {
  text-align:center;
}
.time-hour {
  font-size:18px;
  font-weight:700;
  color:#1f2329;
}
.time-minute {
  font-size:11px;
  color:#8f959e;
}
.meeting-info {
  flex:1;
}
.meeting-title {
  font-size:13px;
  font-weight:500;
  color:#1f2329;
}
.meeting-room {
  font-size:11px;
  color:#8f959e;
  margin-top:2px;
}
.meeting-join {
  font-size:12px;
  color:#3b82f6;
  text-decoration:none;
  padding:4px 10px;
  border:1px solid #3b82f6;
  border-radius:6px;
  transition:all .15s;
}
.meeting-join:hover {
  background:#eff6ff;
}
</style>
