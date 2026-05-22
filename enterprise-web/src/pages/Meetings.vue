<template>
  <div class="meetings-page">
    <!-- 统计栏 -->
    <div class="stats-row">
      <div v-for="s in stats" :key="s.label" class="stat-card">
        <div class="stat-value">{{ s.value }}</div>
        <div class="stat-label">{{ s.label }}</div>
      </div>
    </div>

    <!-- 操作栏 -->
    <div class="toolbar">
      <el-button type="primary" size="large" @click="openCreate">新建会议</el-button>
      <div class="toolbar-right">
        <el-input v-model="keyword" placeholder="搜索会议标题" clearable style="width:200px" />
        <el-date-picker v-model="dateFilter" type="date" placeholder="筛选日期" clearable style="width:160px" value-format="YYYY-MM-DD" />
      </div>
    </div>

    <!-- 列表区域 -->
    <div v-loading="loading" class="meeting-list">
      <div v-if="!loading && myMeetings.length === 0" class="empty-state">
        <div class="empty-text">暂无会议</div>
        <div class="empty-sub">点击左上角「新建会议」开始预约吧</div>
      </div>

      <div v-for="group in groupedMeetings" :key="group.date" class="date-group">
        <div class="date-header">{{ group.label }}</div>
        <div v-for="m in group.items" :key="m.id" class="meeting-card">
          <div class="card-bar" :style="{ background: statusColor(m.status) }" />
          <div class="card-body">
            <div class="card-row1">
              <span class="card-time">{{ m.start_time || '--' }} - {{ m.end_time || '--' }}</span>
              <el-tag v-if="m.room === '线上-Zoom'" size="small" effect="plain" type="primary">
                <el-icon style="margin-right:2px"><VideoCamera /></el-icon> 线上
              </el-tag>
            </div>
            <div class="card-title">{{ m.title }}</div>
            <div class="card-meta">
              <el-icon v-if="m.room !== '线上-Zoom'" class="meta-icon"><Location /></el-icon>
              <el-icon v-else class="meta-icon"><VideoCamera /></el-icon>
              {{ m.room }}  ·  {{ attendeeDisplay(m) }}
            </div>
            <div class="card-footer">
              <span class="card-creator">{{ creatorLabel(m) }}</span>
              <span class="card-status" :style="{ color: statusColor(m.status) }">· {{ statusText(m.status) }}</span>
              <span class="card-actions">
                <el-button v-if="m.join_url" size="small" type="primary" @click="joinMeeting(m)">入会</el-button>
                <el-button size="small" @click="openEdit(m)">编辑</el-button>
                <el-button size="small" type="danger" @click="handleDelete(m)">取消</el-button>
              </span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 弹窗 -->
    <el-dialog v-model="dlgVisible" :title="editId ? '编辑会议' : '新建会议'" width="540px" @closed="editId=null; formRef?.resetFields()">
      <el-form ref="formRef" :model="form" label-position="top">
        <el-form-item label="会议标题" required>
          <el-input v-model="form.title" placeholder="请输入会议主题" maxlength="100" show-word-limit />
        </el-form-item>
        <div class="form-row-2col">
          <div class="form-col">
            <el-form-item label="日期" required>
              <el-date-picker v-model="form.date" type="date" placeholder="选择日期" value-format="YYYY-MM-DD" style="width:100%" />
            </el-form-item>
            <div class="form-row-2col">
              <el-form-item label="开始时间" required>
                <el-time-picker v-model="form.startTime" format="HH:mm" value-format="HH:mm" placeholder="开始" style="width:100%" />
              </el-form-item>
              <el-form-item label="会议时长" required>
                <el-select v-model="form.duration" style="width:100%">
                  <el-option label="30 分钟" value="30" />
                  <el-option label="1 小时" value="60" />
                  <el-option label="1.5 小时" value="90" />
                  <el-option label="2 小时" value="120" />
                  <el-option label="3 小时" value="180" />
                </el-select>
              </el-form-item>
            </div>
          </div>
          <div class="form-col">
            <el-form-item label="地点" required>
              <el-select v-model="form.room" style="width:100%">
                <el-option-group label="线下会议室">
                  <el-option label="A301 (20人)" value="A301 (20人)">
                    <el-icon><Location /></el-icon>
                    <span>A301 (20人)</span>
                  </el-option>
                  <el-option label="B102 (10人)" value="B102 (10人)" />
                  <el-option label="C501 (50人)" value="C501 (50人)" />
                </el-option-group>
                <el-option-group label="线上会议">
                  <el-option label="Zoom 视频会议" value="线上-Zoom" />
                </el-option-group>
              </el-select>
            </el-form-item>
          </div>
        </div>
        <el-form-item label="参会人">
          <div class="attendee-tags">
            <el-tag v-for="name in form.attendees" :key="name" closable @close="removeAttendee(name)" style="margin-right:4px;margin-bottom:4px">
              {{ name }}
            </el-tag>
            <el-input v-model="attendeeInput" placeholder="输入姓名后回车添加" style="width:180px" @keyup.enter="addAttendee" @blur="addAttendee" />
          </div>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.description" type="textarea" :autosize="{ minRows: 3, maxRows: 8 }" placeholder="会议议程、准备事项等" maxlength="500" show-word-limit class="desc-textarea" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dlgVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">
          {{ editId ? '保存修改' : '创建会议' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Location, VideoCamera } from '@element-plus/icons-vue'
import { getMyMeetings, createMeeting, updateMeeting, deleteMeeting, readStoredAuth } from '../api/index.js'

const meetings = ref([])
const loading = ref(false)
const keyword = ref('')
const dateFilter = ref('')
const dlgVisible = ref(false)
const submitting = ref(false)
const editId = ref(null)
const formRef = ref(null)
const form = ref({
  title: '', date: '', startTime: '', duration: '60',
  room: 'A301 (20人)', attendees: [], description: ''
})
const attendeeInput = ref('')

const { user } = readStoredAuth()

const todayStr = computed(() => new Date().toISOString().split('T')[0])
const weekStart = computed(() => {
  const d = new Date(); d.setDate(d.getDate() - d.getDay() + 1)
  return d.toISOString().split('T')[0]
})
const tomorrowStr = computed(() => {
  const d = new Date(); d.setDate(d.getDate() + 1)
  return d.toISOString().split('T')[0]
})

const myMeetings = computed(() => {
  return meetings.value.filter(m => {
    const kw = keyword.value.toLowerCase()
    const matchKeyword = !kw || (m.title || '').toLowerCase().includes(kw)
    const matchDate = !dateFilter.value || m.date === dateFilter.value
    return matchKeyword && matchDate
  })
})

const todayMeetings = computed(() => myMeetings.value.filter(m => m.date === todayStr.value))
const weekMeetings = computed(() =>
  myMeetings.value.filter(m => m.date >= weekStart.value)
)
const pendingCount = computed(() => myMeetings.value.filter(m => m.status !== 'confirmed').length)

const stats = computed(() => [
  { label: '今日会议', value: todayMeetings.value.length },
  { label: '本周会议', value: weekMeetings.value.length },
  { label: '待确认', value: pendingCount.value }
])

const groupedMeetings = computed(() => {
  const groups = {}
  for (const m of myMeetings.value) {
    const d = m.date || ''
    if (!groups[d]) groups[d] = []
    groups[d].push(m)
  }
  const sorted = Object.entries(groups).sort(([a], [b]) => a.localeCompare(b))
  return sorted.map(([date, items]) => {
    const d = new Date(date + 'T00:00:00')
    const weekdays = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']
    let label
    if (date === todayStr.value) label = '今天'
    else if (date === tomorrowStr.value) label = '明天'
    else label = `${d.getMonth() + 1}月${d.getDate()}日 ${weekdays[d.getDay()]}`
    return { date, label, items }
  })
})

function creatorLabel(m) {
  if (m.creatorId === user.id) return '我创建的'
  const names = (m.attendees || '').split(',').map(s => s.trim()).filter(Boolean)
  return names.length ? names[0] + '邀请我' : '他人邀请我'
}

function attendeeDisplay(m) {
  const names = (m.attendees || '').split(',').map(s => s.trim()).filter(Boolean)
  if (names.length === 0) return ''
  if (names.length <= 3) return names.join(', ') + ' 共' + names.length + '人'
  return names.slice(0, 3).join(', ') + ' 等' + names.length + '人'
}

/** 根据开始时间+时长计算结束时间 (HH:mm) */
function calcEndTime(startTime, durationMin) {
  if (!startTime || !durationMin) return ''
  const [h, m] = startTime.split(':').map(Number)
  const total = h * 60 + m + Number(durationMin)
  const eh = Math.floor(total / 60) % 24
  const em = total % 60
  return String(eh).padStart(2, '0') + ':' + String(em).padStart(2, '0')
}

async function load() {
  loading.value = true
  try {
    const resp = await getMyMeetings()
    const result = await resp.json()
    if (result.code == 200) meetings.value = result.data || []
    else meetings.value = []
  } catch { meetings.value = [] }
  finally { loading.value = false }
}

function openCreate() {
  editId.value = null
  form.value = { title: '', date: '', startTime: '', duration: '60', room: 'A301 (20人)', attendees: [], description: '' }
  attendeeInput.value = ''
  dlgVisible.value = true
}

function openEdit(row) {
  editId.value = row.id
  form.value = {
    title: row.title || '',
    date: row.date || '',
    startTime: row.start_time || '',
    duration: '60',
    room: row.room || 'A301 (20人)',
    attendees: (row.attendees || '').split(',').map(s => s.trim()).filter(Boolean),
    description: row.description || ''
  }
  attendeeInput.value = ''
  dlgVisible.value = true
}

function addAttendee() {
  const name = attendeeInput.value.trim()
  if (name && !form.value.attendees.includes(name)) form.value.attendees.push(name)
  attendeeInput.value = ''
}

function removeAttendee(name) {
  form.value.attendees = form.value.attendees.filter(a => a !== name)
}

async function handleSubmit() {
  if (!form.value.title || !form.value.date || !form.value.startTime || !form.value.duration) {
    ElMessage.warning('请填写会议标题、日期、时间和时长')
    return
  }
  submitting.value = true
  try {
    const endTime = calcEndTime(form.value.startTime, form.value.duration)
    const body = {
      title: form.value.title, room: form.value.room, date: form.value.date,
      startTime: form.value.startTime, endTime: endTime,
      attendees: form.value.attendees.join(','),
      description: form.value.description
    }
    const resp = editId.value ? await updateMeeting(editId.value, body) : await createMeeting(body)
    const result = await resp.json()
    if (result.code == 200) {
      dlgVisible.value = false
      ElMessage.success(editId.value ? '已更新' : '已创建')
      await load()
    } else {
      ElMessage.error(result.message || '操作失败')
    }
  } catch (e) {
    ElMessage.error('无法连接协作服务，请确认端口 8082 已启动')
  } finally { submitting.value = false }
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(`确定取消会议「${row.title}」吗？`, '提示', { type: 'warning' })
    const resp = await deleteMeeting(row.id)
    const result = await resp.json()
    if (result.code == 200) {
      ElMessage.success('已取消')
      await load()
    } else {
      ElMessage.error(result.message || '取消失败')
    }
  } catch { /* cancelled */ }
}

function joinMeeting(row) {
  if (row.join_url) window.open(row.join_url, '_blank')
}

function statusColor(status) {
  if (status === 'confirmed') return '#22c55e'
  if (status === 'cancelled') return '#9ca3af'
  return '#f59e0b'
}

function statusText(status) {
  if (status === 'confirmed') return '已确认'
  if (status === 'cancelled') return '已取消'
  return '待确认'
}

onMounted(load)
</script>

<style scoped>
.meetings-page { display:flex; flex-direction:column; gap:20px; }
.meetings-page :deep(.el-button) { border-radius: 20px; padding-left: 20px; padding-right: 20px; }
.stats-row { display:flex; gap:16px; }
.stat-card { flex:1; background:#fff; border-radius:12px; padding:20px 24px; border:1px solid #f3f4f6; }
.stat-value { font-size:28px; font-weight:700; color:#1f2937; }
.stat-label { font-size:13px; color:#9ca3af; margin-top:4px; }
.toolbar { display:flex; justify-content:space-between; align-items:center; }
.toolbar-right { display:flex; gap:12px; align-items:center; }
.meeting-list { display:flex; flex-direction:column; gap:16px; min-height:200px; }
.empty-state { display:flex; flex-direction:column; align-items:center; justify-content:center; padding:60px 0; gap:12px; }
.empty-text { font-size:16px; color:#9ca3af; }
.empty-sub { font-size:13px; color:#d1d5db; }
.date-group { display:flex; flex-direction:column; gap:8px; }
.date-header { font-size:14px; font-weight:600; color:#6b7280; padding:4px 0; }
.meeting-card { display:flex; background:#fff; border-radius:12px; overflow:hidden; border:1px solid #f3f4f6; transition: box-shadow .2s; }
.meeting-card:hover { box-shadow: 0 4px 16px rgba(0,0,0,0.06); }
.card-bar { width:4px; flex-shrink:0; }
.card-body { flex:1; padding:16px 20px; display:flex; flex-direction:column; gap:6px; }
.card-row1 { display:flex; align-items:center; gap:8px; }
.card-time { font-size:14px; font-weight:600; color:#374151; }
.card-title { font-size:16px; font-weight:600; color:#1f2937; }
.card-meta { font-size:13px; color:#6b7280; display:flex; align-items:center; gap:4px; }
.meta-icon { font-size:14px; color:#9ca3af; }
.card-footer { display:flex; align-items:center; gap:8px; margin-top:4px; }
.card-creator { font-size:12px; color:#9ca3af; }
.card-status { font-size:12px; }
.card-actions { margin-left:auto; display:flex; gap:6px; }
.form-row-2col { display:flex; gap:16px; }
.form-col { flex:1; display:flex; flex-direction:column; }
.attendee-tags { display:flex; flex-wrap:wrap; align-items:center; gap:4px; }
.desc-textarea :deep(.el-textarea__inner) {
  border-radius: 8px;
  background: #fafafa;
  transition: background .2s;
}
.desc-textarea :deep(.el-textarea__inner):focus {
  background: #fff;
}
</style>
