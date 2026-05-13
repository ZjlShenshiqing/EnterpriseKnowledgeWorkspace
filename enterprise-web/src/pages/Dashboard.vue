<template>
  <div style="display:flex;flex-direction:column;gap:24px">
    <!-- Header -->
    <div>
      <div style="font-size:22px;font-weight:700;color:#1f2937">工作台</div>
      <div style="font-size:13px;color:#9ca3af;margin-top:4px">欢迎回来，{{ userName }}</div>
    </div>

    <!-- Stat Cards -->
    <div class="stat-grid">
      <div v-for="card in statCards" :key="card.label" class="stat-card" @click="$router.push(card.path)">
        <div class="stat-card-top">
          <div class="stat-icon" :style="{background:card.bg}">
            <span v-html="card.icon"></span>
          </div>
          <div class="stat-trend" v-if="card.trend">
            <span style="font-size:11px;color:#10b981;font-weight:500">{{ card.trend }}</span>
          </div>
        </div>
        <div class="stat-value">{{ card.value }}</div>
        <div class="stat-label">{{ card.label }}</div>
      </div>
    </div>

    <!-- Main Content -->
    <div style="display:flex;gap:20px">
      <!-- Left: Recent Docs + Quick Actions -->
      <div style="flex:1;display:flex;flex-direction:column;gap:20px">
        <!-- Quick Actions -->
        <div class="card">
          <div class="card-title">快捷操作</div>
          <div style="display:flex;gap:12px">
            <div v-for="a in quickActions" :key="a.label" class="quick-btn" @click="$router.push(a.path)">
              <div class="quick-btn-icon" :style="{background:a.bg}">
                <el-icon :size="20" :color="a.color"><component :is="a.icon" /></el-icon>
              </div>
              <span class="quick-btn-label">{{ a.label }}</span>
            </div>
          </div>
        </div>

        <!-- Recent Documents -->
        <div class="card" style="flex:1">
          <div class="card-title" style="display:flex;justify-content:space-between">
            最近文档
            <span class="card-link" @click="$router.push('/documents')">查看全部 →</span>
          </div>
          <div v-if="!recentDocs.length" class="card-empty">暂无文档</div>
          <div v-for="doc in recentDocs.slice(0,6)" :key="doc.id" class="doc-item">
            <div class="doc-icon">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#9ca3af" stroke-width="2"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/></svg>
            </div>
            <div style="flex:1;min-width:0">
              <div style="font-size:14px;font-weight:500;overflow:hidden;text-overflow:ellipsis;white-space:nowrap">{{ doc.title }}</div>
              <div style="font-size:12px;color:#9ca3af;margin-top:2px">{{ doc.fileType }} · {{ doc.createdAt || doc.created_at }}</div>
            </div>
          </div>
        </div>
      </div>

      <!-- Right: Todos + Meetings -->
      <div style="width:340px;display:flex;flex-direction:column;gap:20px;flex-shrink:0">
        <!-- Today's Todos -->
        <div class="card">
          <div class="card-title" style="display:flex;justify-content:space-between">
            今日待办 ({{ todos.filter(t=>!t.done).length }})
            <span class="card-link" @click="$router.push('/todos')">查看全部 →</span>
          </div>
          <div v-if="!todos.length" class="card-empty">暂无待办</div>
          <div v-for="t in todos.slice(0,5)" :key="t.id" class="todo-item">
            <div class="todo-check" :class="{done:t.done}" @click="toggleTodo(t)">
              <svg v-if="t.done" width="14" height="14" viewBox="0 0 24 24" fill="#10b981" stroke="#10b981" stroke-width="2"><polyline points="20 6 9 17 4 12"/></svg>
            </div>
            <div style="flex:1;font-size:13px" :class="{'line-through':t.done}">{{ t.title }}</div>
            <span v-if="t.priority==='high'" class="tag tag-red">紧急</span>
          </div>
        </div>

        <!-- Today's Meetings -->
        <div class="card">
          <div class="card-title" style="display:flex;justify-content:space-between">
            今日会议 ({{ todayMeetings.length }})
            <span class="card-link" @click="$router.push('/meetings')">全部 →</span>
          </div>
          <div v-if="!todayMeetings.length" class="card-empty">今日暂无会议</div>
          <div v-for="m in todayMeetings.slice(0,3)" :key="m.id" class="meeting-item">
            <div style="width:40px;text-align:center">
              <div style="font-size:20px;font-weight:700;color:#1f2937">{{ m.start_time?.substring(0,2) || '--' }}</div>
              <div style="font-size:11px;color:#9ca3af">:00</div>
            </div>
            <div style="flex:1">
              <div style="font-size:13px;font-weight:500">{{ m.title }}</div>
              <div style="font-size:11px;color:#9ca3af">{{ m.room }}</div>
            </div>
            <a v-if="m.join_url" :href="m.join_url" target="_blank" class="join-link">加入</a>
          </div>
        </div>

        <!-- Announcements -->
        <div class="card">
          <div class="card-title">最新公告</div>
          <div v-if="!announcements.length" class="card-empty">暂无公告</div>
          <div v-for="a in announcements.slice(0,3)" :key="a.id" class="announce-item">
            <div style="font-size:13px;font-weight:500;overflow:hidden;text-overflow:ellipsis;white-space:nowrap">{{ a.title }}</div>
            <div style="font-size:11px;color:#9ca3af;margin-top:2px">{{ a.publisherName || a.publisher_name }} · {{ a.createdAt || a.created_at }}</div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'

const userName = computed(() => { const u=JSON.parse(localStorage.getItem('user')||'{}'); return u.realName||u.username||'管理员' })

const recentDocs = ref([])
const todos = ref([])
const todayMeetings = ref([])
const announcements = ref([])

const statCards = computed(() => [
  { label:'知识文档', value:recentDocs.value.length||0, bg:'linear-gradient(135deg,#eff6ff,#dbeafe)', icon:'<svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="#3b82f6" stroke-width="2"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/></svg>', trend:'+12%', path:'/documents' },
  { label:'今日会议', value:todayMeetings.value.length, bg:'linear-gradient(135deg,#f0fdf4,#dcfce7)', icon:'<svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="#22c55e" stroke-width="2"><rect x="3" y="4" width="18" height="18" rx="2" ry="2"/><line x1="16" y1="2" x2="16" y2="6"/><line x1="8" y1="2" x2="8" y2="6"/><line x1="3" y1="10" x2="21" y2="10"/></svg>', trend:null, path:'/meetings' },
  { label:'我的待办', value:todos.value.filter(t=>!t.done).length, bg:'linear-gradient(135deg,#fffbeb,#fef3c7)', icon:'<svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="#f59e0b" stroke-width="2"><polyline points="9 11 12 14 22 4"/><path d="M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11"/></svg>', trend:null, path:'/todos' },
  { label:'进行中任务', value:0, bg:'linear-gradient(135deg,#fef2f2,#fee2e2)', icon:'<svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="#ef4444" stroke-width="2"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg>', trend:null, path:'/tasks' },
])

const quickActions = [
  { label:'新建文档', icon:'DocumentAdd', bg:'#eff6ff', color:'#3b82f6', path:'/documents' },
  { label:'新建会议', icon:'Calendar', bg:'#f0fdf4', color:'#22c55e', path:'/meetings' },
  { label:'添加待办', icon:'List', bg:'#fffbeb', color:'#f59e0b', path:'/todos' },
  { label:'发起审批', icon:'Checked', bg:'#fef2f2', color:'#ef4444', path:'/approvals' },
]

function headers() { const u=JSON.parse(localStorage.getItem('user')||'{}'); return {'X-User-Id':String(u.id||1),'X-Is-Admin':String(u.isAdmin?'true':'false')} }

onMounted(async () => {
  try {
    const resp = await fetch('/api/workbench/overview', {headers:headers()})
    const data = (await resp.json()).data||{}
    recentDocs.value = data.recentDocs||[]
    todos.value = (data.todos||[]).map(t=>({...t,done:t.done===1||t.done===true}))
    if (data.meetings) todayMeetings.value = data.meetings.filter(m=>m.date===new Date().toISOString().split('T')[0])
    statCards.value[3].value = data.inProgressTaskCount||0
  } catch(e) { console.log('Workbench API not available') }

  try {
    const resp = await fetch('/api/announcements', {headers:headers()})
    announcements.value = (await resp.json()).data||[]
  } catch(e) {}
})

function toggleTodo(t) { t.done = !t.done }
</script>

<style scoped>
.stat-grid { display:flex; gap:16px; }
.stat-card { flex:1; background:#fff; border-radius:16px; padding:20px; cursor:pointer; border:1px solid #f3f4f6; transition:all .2s; }
.stat-card:hover { box-shadow:0 4px 20px rgba(0,0,0,0.06); transform:translateY(-2px); }
.stat-card-top { display:flex; justify-content:space-between; align-items:flex-start; margin-bottom:12px; }
.stat-icon { width:44px;height:44px;border-radius:12px;display:flex;align-items:center;justify-content:center; }
.stat-value { font-size:28px;font-weight:700;color:#1f2937; }
.stat-label { font-size:13px;color:#9ca3af;margin-top:4px; }

.card { background:#fff;border-radius:16px;padding:20px;border:1px solid #f3f4f6; }
.card-title { font-size:15px;font-weight:600;color:#1f2937;margin-bottom:16px; }
.card-link { font-size:12px;color:#3b82f6;cursor:pointer;font-weight:400; }
.card-empty { color:#d1d5db;font-size:13px;text-align:center;padding:30px 0; }

.quick-btn { display:flex;flex-direction:column;align-items:center;gap:8px;padding:12px 16px;border-radius:12px;cursor:pointer;transition:all .15s;flex:1; }
.quick-btn:hover { background:#f9fafb; }
.quick-btn-icon { width:40px;height:40px;border-radius:10px;display:flex;align-items:center;justify-content:center; }
.quick-btn-label { font-size:12px;color:#6b7280; }

.doc-item { display:flex;align-items:center;gap:12px;padding:10px 0;border-bottom:1px solid #f9fafb; }
.doc-icon { width:32px;height:32px;background:#f9fafb;border-radius:8px;display:flex;align-items:center;justify-content:center;flex-shrink:0; }

.todo-item { display:flex;align-items:center;gap:10px;padding:8px 0;border-bottom:1px solid #f9fafb; }
.todo-check { width:20px;height:20px;border-radius:6px;border:2px solid #d1d5db;cursor:pointer;display:flex;align-items:center;justify-content:center;flex-shrink:0; }
.todo-check.done { border-color:#10b981;background:#ecfdf5; }
.line-through { text-decoration:line-through;color:#9ca3af; }
.tag { font-size:11px;padding:2px 8px;border-radius:10px;font-weight:500;flex-shrink:0; }
.tag-red { background:#fef2f2;color:#ef4444; }

.meeting-item { display:flex;align-items:center;gap:12px;padding:10px 0;border-bottom:1px solid #f9fafb; }
.join-link { font-size:12px;color:#3b82f6;text-decoration:none;border:1px solid #3b82f6;padding:4px 10px;border-radius:6px;transition:all .15s; }
.join-link:hover { background:#eff6ff; }

.announce-item { display:flex;justify-content:space-between;padding:8px 0;border-bottom:1px solid #f9fafb; }
</style>
