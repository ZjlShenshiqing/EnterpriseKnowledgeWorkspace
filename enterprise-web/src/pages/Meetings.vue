<template>
  <div style="background:#fff;border-radius:12px;padding:20px">
    <div style="display:flex;justify-content:space-between;margin-bottom:16px">
      <span style="font-size:18px;font-weight:600">会议预约</span>
      <el-button type="primary" @click="openCreate">新建会议</el-button>
    </div>
    <el-table :data="meetings" stripe>
      <el-table-column prop="title" label="会议标题" min-width="160" />
      <el-table-column prop="room" label="会议室" width="140" />
      <el-table-column prop="date" label="日期" width="110" />
      <el-table-column label="时间" width="140"><template #default="{row}">{{ row.start_time }} - {{ row.end_time }}</template></el-table-column>
      <el-table-column prop="attendees" label="参会人" width="200" />
      <el-table-column label="状态" width="90"><template #default="{row}"><el-tag :type="row.status==='confirmed'?'success':'warning'" size="small">{{ row.status==='confirmed'?'已确认':'待确认' }}</el-tag></template></el-table-column>
      <el-table-column label="入会" width="120"><template #default="{row}">
        <el-button v-if="row.join_url" size="small" type="primary" @click="openMeeting(row)" style="padding:4px 12px">立即入会</el-button>
        <span v-else style="color:var(--text-tertiary);font-size:12px">—</span>
      </template></el-table-column>
      <el-table-column label="操作" width="80"><template #default="{row}"><el-button size="small" type="danger" @click="doDelete(row)">取消</el-button></template></el-table-column>
    </el-table>

    <el-dialog v-model="dlg" :title="editId?'编辑会议':'新建会议'" width="480px">
      <el-form :model="f" label-width="70px">
        <el-form-item label="标题"><el-input v-model="f.title" /></el-form-item>
        <el-form-item label="会议室"><el-select v-model="f.room" style="width:100%"><el-option label="A301 (20人)" value="A301 (20人)" /><el-option label="B102 (10人)" value="B102 (10人)" /><el-option label="C501 (50人)" value="C501 (50人)" /><el-option label="线上-Zoom" value="线上-Zoom" /></el-select></el-form-item>
        <el-form-item label="日期"><el-date-picker v-model="f.date" style="width:100%" /></el-form-item>
        <el-form-item label="时间"><el-time-picker v-model="f.timeRange" is-range style="width:100%" format="HH:mm" /></el-form-item>
        <el-form-item label="参会人"><el-input v-model="f.attendees" placeholder="姓名用逗号分隔" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="dlg=false">取消</el-button><el-button type="primary" @click="save">{{ editId?'更新':'创建' }}</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'

const meetings = ref([]); const dlg = ref(false); const editId = ref(null)
const f = ref({ title:'',room:'',date:'',timeRange:[],attendees:'' })

function headers() {
  const u = JSON.parse(localStorage.getItem('user')||'{}')
  return {'X-User-Id':String(u.id||1),'X-Is-Admin':String(u.isAdmin?'true':'false'),'Content-Type':'application/json'}
}

async function load() {
  try { const r = await fetch('/api/meetings',{headers:headers()}); meetings.value = (await r.json()).data||[] }
  catch(e) { meetings.value = mockMeetings }
}

function openCreate() { editId.value = null; f.value = { title:'',room:'A301 (20人)',date:'',timeRange:[],attendees:'' }; dlg.value = true }

async function save() {
  if (!f.value.title) return
  const formatDate = (d) => {
    if (!d) return ''
    const date = new Date(d)
    return `${date.getFullYear()}-${String(date.getMonth()+1).padStart(2,'0')}-${String(date.getDate()).padStart(2,'0')}`
  }
  const body = { title:f.value.title, room:f.value.room, date:formatDate(f.value.date), startTime:f.value.timeRange[0]?.toLocaleTimeString?.('zh',{hour:'2-digit',minute:'2-digit'})||'', endTime:f.value.timeRange[1]?.toLocaleTimeString?.('zh',{hour:'2-digit',minute:'2-digit'})||'', attendees:f.value.attendees }
  const url = editId.value ? `/api/meetings/${editId.value}` : '/api/meetings'
  const method = editId.value ? 'PUT' : 'POST'
  const r = await fetch(url,{method,headers:headers(),body:JSON.stringify(body)})
  const result = await r.json()
  dlg.value = false; ElMessage.success(editId.value?'已更新':'已创建'); await load()
  
  if (!editId.value && f.value.room === '线上-Zoom') {
    setTimeout(() => {
      const newMeeting = meetings.value.find(m => m.title === f.value.title && m.date === formatDate(f.value.date))
      if (newMeeting && newMeeting.join_url) {
        if (confirm('Zoom会议已创建，是否立即加入会议？')) {
          window.open(newMeeting.join_url, '_blank')
        }
      }
    }, 500)
  }
}

function openMeeting(row) {
  if (row.join_url) {
    window.open(row.join_url, '_blank')
  }
}

async function doDelete(row) {
  await fetch(`/api/meetings/${row.id}`,{method:'DELETE',headers:headers()})
  ElMessage.success('已取消'); await load()
}

onMounted(load)

const mockMeetings = [
  {id:1,title:'Q2项目评审会',room:'A301',date:'2026-05-12',start_time:'14:00',end_time:'16:00',attendees:'张三,李四,王五',status:'confirmed'},
  {id:2,title:'技术方案讨论',room:'B102',date:'2026-05-12',start_time:'10:00',end_time:'11:00',attendees:'张三,赵六',status:'confirmed'},
]
</script>
