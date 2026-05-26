<template>
  <div style="height:calc(100vh - 132px);display:flex;flex-direction:column">
    <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:16px">
      <span style="font-size:18px;font-weight:600">任务看板</span>
      <el-button type="primary" @click="showCreate=true">新建任务</el-button>
    </div>
    <div style="flex:1;display:flex;gap:16px;overflow-x:auto">
      <div v-for="col in columns" :key="col.key" style="flex:1;min-width:260px;background:var(--bg-body);border-radius:10px;padding:12px;display:flex;flex-direction:column">
        <div style="font-weight:600;font-size:14px;margin-bottom:10px;display:flex;align-items:center;gap:8px">
          <div :style="{width:8,height:8,borderRadius:'50%',background:col.color}"></div>
          {{ col.label }} <span style="color:var(--text-tertiary);font-weight:400">({{ col.tasks.length }})</span>
        </div>
        <div style="flex:1;overflow-y:auto">
          <div v-for="task in col.tasks" :key="task.id" @click="openDetail(task)"
            style="background:#fff;padding:12px;margin-bottom:8px;border-radius:8px;cursor:pointer;border:1px solid var(--border-light);transition:box-shadow .15s"
            @mouseenter="e=>e.target.style.boxShadow='0 2px 8px rgba(0,0,0,0.1)'" @mouseleave="e=>e.target.style.boxShadow='none'">
            <div style="font-size:14px;font-weight:500;margin-bottom:6px;line-height:1.5">{{ task.title }}</div>
            <div style="display:flex;align-items:center;justify-content:space-between;font-size:12px;color:var(--text-tertiary)">
              <span>{{ task.assignee_name || '未分配' }}</span>
              <el-tag :type="task.priority==='high'?'danger':task.priority==='medium'?'warning':'info'" size="small">{{ ['high','medium','low'][['high','medium','low'].indexOf(task.priority)]?.replace('high','高').replace('medium','中').replace('low','低') || task.priority }}</el-tag>
            </div>
            <div v-if="task.due_date" style="font-size:11px;color:var(--text-tertiary);margin-top:4px">{{ task.due_date }}</div>
          </div>
        </div>
      </div>
    </div>

    <!-- Create Dialog -->
    <el-dialog v-model="showCreate" title="新建任务" width="480px">
      <el-form :model="form" label-width="60px">
        <el-form-item label="标题"><el-input v-model="form.title" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="form.description" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="负责人">
          <el-select v-model="form.assigneeId" placeholder="选择负责人" style="width:100%">
            <el-option v-for="u in userList" :key="u.id" :label="u.realName||u.username" :value="u.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="优先级"><el-select v-model="form.priority" style="width:100%"><el-option label="高" value="high" /><el-option label="中" value="medium" /><el-option label="低" value="low" /></el-select></el-form-item>
        <el-form-item label="截止日"><el-date-picker v-model="form.dueDate" style="width:100%" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="showCreate=false">取消</el-button><el-button type="primary" @click="doCreate">创建</el-button></template>
    </el-dialog>

    <!-- Detail Dialog -->
    <el-dialog v-model="showDetail" title="任务详情" width="520px">
      <div v-if="detail" style="line-height:2">
        <p><b>标题：</b>{{ detail.title }}</p>
        <p><b>描述：</b>{{ detail.description || '无' }}</p>
        <p><b>负责人：</b>{{ detail.assignee_name || '未分配' }}</p>
        <p><b>优先级：</b>{{ detail.priority }}</p>
        <p><b>截止：</b>{{ detail.due_date || '无' }}</p>
        <p><b>状态：</b>
          <el-select v-model="detail.status" @change="changeStatus" size="small">
            <el-option v-for="s in statusOpts" :key="s.key" :label="s.label" :value="s.key" />
          </el-select>
        </p>
        <div style="margin-top:12px"><b>评论</b></div>
        <div v-for="c in comments" :key="c.id" style="padding:8px;background:var(--bg-body);border-radius:6px;margin:4px 0;font-size:13px">
          <b>{{ c.user_name }}</b> <span style="color:var(--text-tertiary);font-size:11px">{{ c.created_at }}</span>
          <div>{{ c.content }}</div>
        </div>
        <div style="display:flex;gap:8px;margin-top:8px">
          <el-input v-model="newComment" size="small" placeholder="添加评论" @keyup.enter="addComment" /><el-button size="small" @click="addComment">发送</el-button>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getAuthHeaders } from '../api/index.js'

const tasks = ref([]); const comments = ref([]); const userList = ref([])
const showCreate = ref(false); const showDetail = ref(false)
const detail = ref(null); const newComment = ref('')
const form = ref({ title:'',description:'',assigneeId:null,priority:'medium',dueDate:'' })

const statusOpts = [{key:'todo',label:'待开始'},{key:'in_progress',label:'进行中'},{key:'review',label:'待确认'},{key:'done',label:'已完成'}]
const columnDefs = [{key:'todo',label:'待开始',color:'#909399'},{key:'in_progress',label:'进行中',color:'var(--brand-500)'},{key:'review',label:'待确认',color:'#e6a23c'},{key:'done',label:'已完成',color:'#67c23a'}]

const columns = computed(() => columnDefs.map(c => ({...c, tasks: tasks.value.filter(t=>t.status===c.key)})))

function headers() {
  return { ...getAuthHeaders(), 'Content-Type': 'application/json' }
}

async function load() {
  try {
    const r = await fetch('/api/tasks', { headers: headers() })
    const body = await r.json()
    if (String(body.code) === '200') {
      tasks.value = body.data || []
    } else {
      tasks.value = []
    }
  } catch (e) {
    tasks.value = []
  }
}
async function loadUsers() {
  try {
    const r = await fetch('/api/contacts/users', { headers: headers() })
    const body = await r.json()
    userList.value = String(body.code) === '200' ? (body.data || []) : []
  } catch (e) {
    userList.value = []
  }
}

async function doCreate() {
  if (!form.value.title) return
  await fetch('/api/tasks',{method:'POST',headers:headers(),body:JSON.stringify(form.value)})
  showCreate.value = false; form.value = {title:'',description:'',assigneeId:null,priority:'medium',dueDate:''}
  ElMessage.success('创建成功'); await load()
}

async function changeStatus() {
  await fetch(`/api/tasks/${detail.value.id}/status`,{method:'PUT',headers:headers(),body:JSON.stringify({status:detail.value.status})})
  await load()
}

async function openDetail(task) {
  detail.value = task; showDetail.value = true
  try { const r = await fetch(`/api/tasks/${task.id}/comments`,{headers:headers()}); comments.value = (await r.json()).data||[] }
  catch(e) { comments.value = [] }
}

async function addComment() {
  if (!newComment.value.trim()||!detail.value) return
  await fetch(`/api/tasks/${detail.value.id}/comments`,{method:'POST',headers:headers(),body:JSON.stringify({content:newComment.value})})
  comments.value.push({id:Date.now(),user_name:'我',content:newComment.value,created_at:'刚刚'})
  newComment.value = ''
}

onMounted(()=>{load();loadUsers()})
</script>
