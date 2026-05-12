<template>
  <div>
    <div style="display:flex;gap:16px;margin-bottom:20px">
      <div v-for="card in cards" :key="card.label" @click="$router.push(card.path)"
           style="flex:1;background:#fff;border-radius:10px;padding:20px;cursor:pointer;transition:box-shadow .3s"
           @mouseenter="e=>e.target.style.boxShadow='0 4px 16px rgba(0,0,0,0.1)'"
           @mouseleave="e=>e.target.style.boxShadow='none'">
        <div style="display:flex;align-items:center;gap:12px">
          <div :style="{width:48,height:48,borderRadius:12,background:card.bg,display:'flex',alignItems:'center',justifyContent:'center'}">
            <el-icon :size="24" :color="card.color"><component :is="card.icon" /></el-icon>
          </div>
          <div>
            <div style="font-size:13px;color:#909399">{{ card.label }}</div>
            <div style="font-size:24px;font-weight:700;margin-top:2px">{{ card.count }}</div>
          </div>
        </div>
      </div>
    </div>
    <el-row :gutter="16">
      <el-col :span="14">
        <el-card header="最近文档">
          <div v-for="doc in recentDocs" :key="doc.id" style="display:flex;align-items:center;padding:10px 0;border-bottom:1px solid #f0f0f0;cursor:pointer" @click="$router.push('/documents')">
            <el-icon style="margin-right:10px" :size="20" color="#409eff"><Document /></el-icon>
            <div style="flex:1"><div style="font-size:14px">{{ doc.title }}</div><div style="font-size:12px;color:#909399">{{ doc.fileType }} · {{ doc.createdAt }}</div></div>
            <el-tag size="small" :type="doc.status==='SUCCESS'?'success':'info'">{{ doc.status }}</el-tag>
          </div>
          <el-empty v-if="!recentDocs.length" description="暂无文档" />
        </el-card>
      </el-col>
      <el-col :span="10">
        <el-card header="今日待办">
          <div v-for="t in todos" :key="t.id" style="padding:8px 0;border-bottom:1px solid #f0f0f0">
            <el-checkbox v-model="t.done" size="large" :label="t.title">
              <span style="font-size:14px">{{ t.title }}</span>
              <span v-if="t.urgent" style="font-size:11px;color:#f56c6c;margin-left:8px">紧急</span>
            </el-checkbox>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getDocuments } from '../api'

const cards = ref([
  { label: '知识文档', count: 0, icon: 'Document', bg: '#ecf5ff', color: '#409eff', path: '/documents' },
  { label: '今日会议', count: 2, icon: 'Calendar', bg: '#f0f9eb', color: '#67c23a', path: '/meetings' },
  { label: '我的待办', count: 4, icon: 'List', bg: '#fdf6ec', color: '#e6a23c', path: '/todos' },
  { label: '进行中任务', count: 2, icon: 'Aim', bg: '#fef0f0', color: '#f56c6c', path: '/tasks' },
])

const recentDocs = ref([])
const todos = ref([
  { id: 1, title: '完成Q2工作总结报告', done: false, urgent: true },
  { id: 2, title: '审核新员工入职资料', done: false, urgent: false },
  { id: 3, title: '更新知识库文档分类', done: true, urgent: false },
  { id: 4, title: '反馈项目进度给负责人', done: false, urgent: true },
])

onMounted(async () => {
  const headers = (() => { const u = JSON.parse(localStorage.getItem('user')||'{}'); return {'X-User-Id':String(u.id||1),'X-Is-Admin':String(u.isAdmin?'true':'false')} })()
  try {
    const resp = await fetch('/api/workbench/overview', { headers })
    const data = (await resp.json()).data || {}
    cards.value[0].count = data.docCount || 0
    cards.value[1].count = data.meetingCount || 0
    cards.value[2].count = data.todoCount || 0
    cards.value[3].count = data.inProgressTaskCount || 0
    recentDocs.value = data.recentDocs || []
    todos.value = (data.todos || []).slice(0, 4).map(t => ({ id: t.id, title: t.title, done: t.done===1||t.done===true, urgent: t.priority==='high' }))
  } catch (e) { console.log('Workbench not available, using mock') }
})
</script>
