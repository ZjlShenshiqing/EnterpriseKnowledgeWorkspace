<template>
  <div style="background:#fff;border-radius:12px;padding:20px">
    <div style="display:flex;justify-content:space-between;margin-bottom:16px">
      <span style="font-size:18px;font-weight:600">我的待办</span>
      <el-button type="primary" @click="openCreate">新建待办</el-button>
    </div>
    <div v-if="!todos.length" style="text-align:center;padding:60px;color:var(--text-tertiary)">暂无待办事项</div>
    <div v-for="t in todos" :key="t.id" style="display:flex;align-items:center;padding:12px 0;border-bottom:1px solid var(--border-light);gap:12px">
      <el-checkbox :model-value="t.done===1||t.done===true" @change="toggle(t)" size="large" />
      <div style="flex:1" :style="{textDecoration:(t.done===1||t.done===true)?'line-through':'none',color:(t.done===1||t.done===true)?'var(--text-tertiary)':''}">
        <div style="font-size:14px">{{ t.title }}</div>
        <div style="font-size:12px;color:var(--text-tertiary);margin-top:2px">{{ t.due_date || '无截止' }} · {{ ['high','normal','low'][['high','normal','low'].indexOf(t.priority)]?.replace('high','紧急').replace('normal','普通').replace('low','低') }}</div>
      </div>
      <el-tag v-if="t.priority==='high'" type="danger" size="small">紧急</el-tag>
      <el-button size="small" type="danger" circle @click="doDelete(t)"><el-icon><Delete /></el-icon></el-button>
    </div>

    <el-dialog v-model="dlg" title="新建待办" width="400px">
      <el-form :model="f" label-width="60px">
        <el-form-item label="标题"><el-input v-model="f.title" /></el-form-item>
        <el-form-item label="优先级"><el-select v-model="f.priority" style="width:100%"><el-option label="普通" value="normal" /><el-option label="紧急" value="high" /><el-option label="低" value="low" /></el-select></el-form-item>
        <el-form-item label="截止日"><el-date-picker v-model="f.dueDate" style="width:100%" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="dlg=false">取消</el-button><el-button type="primary" @click="save">创建</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'

const todos = ref([]); const dlg = ref(false)
const f = ref({ title:'',priority:'normal',dueDate:'' })

function headers() {
  const u = JSON.parse(localStorage.getItem('user')||'{}')
  return {'X-User-Id':String(u.id||1),'Content-Type':'application/json'}
}

async function load() {
  try { const r = await fetch('/api/todos',{headers:headers()}); todos.value = (await r.json()).data||[] }
  catch(e) { todos.value = [{id:1,title:'完成Q2工作总结报告',priority:'high',due_date:'2026-05-15',done:0},{id:2,title:'审核新员工入职资料',priority:'normal',due_date:'2026-05-18',done:0},{id:3,title:'更新知识库文档分类',priority:'high',due_date:'2026-05-12',done:1}] }
}

function openCreate() { f.value = { title:'',priority:'normal',dueDate:'' }; dlg.value = true }

async function save() {
  if (!f.value.title) return
  await fetch('/api/todos',{method:'POST',headers:headers(),body:JSON.stringify(f.value)})
  dlg.value = false; ElMessage.success('已创建'); await load()
}

async function toggle(t) { await fetch(`/api/todos/${t.id}/toggle`,{method:'PUT',headers:headers()}); await load() }
async function doDelete(t) { await fetch(`/api/todos/${t.id}`,{method:'DELETE',headers:headers()}); await load() }

onMounted(load)
</script>
