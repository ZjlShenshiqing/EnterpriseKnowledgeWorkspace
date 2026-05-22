# 会议预约页面重设计 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**目标:** 将 Meetings.vue 从基础表格+简陋弹窗重构为完整的个人会议管理面板——统计栏、卡片列表按日期分组、增强弹窗。

**架构:** 前端纯 Vue3+Element Plus 重写 Meetings.vue；后端 MeetingController 新增 `/api/meetings/my` 接口按当前用户过滤会议。

**技术栈:** Vue 3 (Composition API), Element Plus, Axios/Fetch, Spring Boot MVC, MyBatis-Plus

---

### Task 1: SysMeeting 实体加 description 字段

**文件:**
- Modify: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/entity/SysMeeting.java`

- [ ] **Step 1: 给 SysMeeting 添加 description 字段**

在 `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/entity/SysMeeting.java` 的 `attendees` 字段后面插入：

```java
    private String attendees;
    /** 会议备注/议程 */
    private String description;
    private String status;
```

- [ ] **Step 2: 数据库表加列**

执行 SQL（在 MySQL `enterprise_collaboration` 库）：

```sql
ALTER TABLE sys_meeting ADD COLUMN description VARCHAR(500) DEFAULT NULL AFTER attendees;
```

- [ ] **Step 3: 提交**

```bash
git add enterprise-collaboration-service/src/main/java/com/zjl/collaboration/entity/SysMeeting.java
git commit -m "feat: SysMeeting 加 description 字段"
```

---

### Task 2: 后端新增 GET /api/meetings/my 接口

**文件:**
- Modify: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/web/MeetingController.java`
- Modify: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/mapper/SysMeetingMapper.java`

- [ ] **Step 1: MeetingReq 加 description 字段**

在 `MeetingController.java` 的 `MeetingReq` 内部类中添加：

```java
@Data public static class MeetingReq {
    private String title; private String room; private String date;
    private String startTime; private String endTime; private String attendees;
    private String description;
}
```

- [ ] **Step 2: create 方法填充 description**

在 `create` 方法中，`m.setAttendees(req.getAttendees());` 后面加：

```java
m.setDescription(req.getDescription());
```

- [ ] **Step 3: update 方法填充 description**

在 `update` 方法中，`m.setAttendees(req.getAttendees());` 后面加：

```java
m.setDescription(req.getDescription());
```

- [ ] **Step 4: 新增 listMy 接口**

在 `MeetingController.java` 的 `list()` 方法后面添加：

```java
@GetMapping("/my")
public Result<List<SysMeeting>> listMy(
        @RequestHeader("X-User-Id") Long userId,
        @RequestParam(defaultValue = "") String userName) {
    List<SysMeeting> all = meetingMapper.selectList(
            Wrappers.lambdaQuery(SysMeeting.class)
                    .orderByDesc(SysMeeting::getDate)
                    .orderByAsc(SysMeeting::getStartTime)
    );
    String name = userName.isBlank() ? String.valueOf(userId) : userName;
    List<SysMeeting> mine = all.stream()
            .filter(m -> m.getCreatorId().equals(userId)
                    || (m.getAttendees() != null && m.getAttendees().contains(name)))
            .collect(java.util.stream.Collectors.toList());
    return Results.success(mine);
}
```

- [ ] **Step 5: 编译验证**

```bash
mvn compile -pl enterprise-collaboration-service -DskipTests
```

- [ ] **Step 6: 提交**

```bash
git add enterprise-collaboration-service/src/main/java/com/zjl/collaboration/web/MeetingController.java
git commit -m "feat: MeetingController 新增 /api/meetings/my 接口 + description"
```

---

### Task 3: 前端 api/index.js 加会议 API

**文件:**
- Modify: `enterprise-web/src/api/index.js`

- [ ] **Step 1: 在 api/index.js 末尾添加会议 API 函数**

```javascript
// ---- Meetings ----

export function getMyMeetings() {
  const { user } = readStoredAuth()
  const userName = user.realName || user.username || ''
  return fetch(`/api/meetings/my?userName=${encodeURIComponent(userName)}`, { headers: getAuthHeaders() })
}

export function createMeeting(body) {
  return fetch('/api/meetings', {
    method: 'POST',
    headers: { ...getAuthHeaders(), 'Content-Type': 'application/json' },
    body: JSON.stringify(body)
  })
}

export function updateMeeting(id, body) {
  return fetch(`/api/meetings/${id}`, {
    method: 'PUT',
    headers: { ...getAuthHeaders(), 'Content-Type': 'application/json' },
    body: JSON.stringify(body)
  })
}

export function deleteMeeting(id) {
  return fetch(`/api/meetings/${id}`, {
    method: 'DELETE',
    headers: getAuthHeaders()
  })
}
```

- [ ] **Step 2: 提交**

```bash
git add enterprise-web/src/api/index.js
git commit -m "feat: api/index.js 加会议 CRUD 函数"
```

---

### Task 4: 重写 Meetings.vue

**文件:**
- Modify: `enterprise-web/src/pages/Meetings.vue`

这是核心任务，分步骤完成整个页面。

- [ ] **Step 1: 重写 `<script setup>` — 数据与逻辑**

完整替换 `<script setup>` 部分：

```javascript
<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
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
  title: '', date: '', startTime: '', endTime: '',
  room: 'A301 (20人)', attendees: [], description: ''
})
const attendeeInput = ref('')

const { user } = readStoredAuth()
const currentUserName = user.realName || user.username || ''

// 统计
const todayStr = computed(() => new Date().toISOString().split('T')[0])
const myMeetings = computed(() => {
  return meetings.value.filter(m => {
    const kw = keyword.value.toLowerCase()
    const matchKeyword = !kw || (m.title || '').toLowerCase().includes(kw)
    const matchDate = !dateFilter.value || m.date === dateFilter.value
    return matchKeyword && matchDate
  })
})
const todayMeetings = computed(() => myMeetings.value.filter(m => m.date === todayStr.value))
const weekStart = computed(() => {
  const d = new Date(); d.setDate(d.getDate() - d.getDay() + 1)
  return d.toISOString().split('T')[0]
})
const weekMeetings = computed(() =>
  myMeetings.value.filter(m => m.date >= weekStart.value && m.date <= todayStr.value + 'T23:59:59')
)
const pendingCount = computed(() => myMeetings.value.filter(m => m.status !== 'confirmed').length)

const stats = computed(() => [
  { label: '今日会议', value: todayMeetings.value.length },
  { label: '本周会议', value: weekMeetings.value.length },
  { label: '待确认', value: pendingCount.value }
])

// 按日期分组
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
    const label = date === todayStr.value ? '今天'
      : date === tomorrowStr.value ? '明天'
      : `${d.getMonth() + 1}月${d.getDate()}日 ${weekdays[d.getDay()]}`
    return { date, label, items }
  })
})
const tomorrowStr = computed(() => {
  const d = new Date(); d.setDate(d.getDate() + 1)
  return d.toISOString().split('T')[0]
})

// 身份标签
function creatorLabel(m) {
  if (m.creatorId === user.id) return '我创建的'
  const names = (m.attendees || '').split(',').map(s => s.trim())
  return names[0] ? names[0] + '邀请我' : '他人邀请我'
}

// 参会人展示（超过3人缩略）
function attendeeDisplay(m) {
  const names = (m.attendees || '').split(',').map(s => s.trim()).filter(Boolean)
  if (names.length <= 3) return names.join(', ') + (names.length ? ` 共${names.length}人` : '')
  return names.slice(0, 3).join(', ') + ` 等${names.length}人`
}

// 加载
async function load() {
  loading.value = true
  try {
    const resp = await getMyMeetings()
    const result = await resp.json()
    if (result.code == 200) {
      meetings.value = result.data || []
    } else {
      meetings.value = []
    }
  } catch { meetings.value = [] }
  finally { loading.value = false }
}

// 新建/编辑
function openCreate() {
  editId.value = null
  form.value = { title: '', date: '', startTime: '', endTime: '', room: 'A301 (20人)', attendees: [], description: '' }
  attendeeInput.value = ''
  dlgVisible.value = true
}
function openEdit(row) {
  editId.value = row.id
  form.value = {
    title: row.title || '',
    date: row.date || '',
    startTime: row.start_time || '',
    endTime: row.end_time || '',
    room: row.room || 'A301 (20人)',
    attendees: (row.attendees || '').split(',').map(s => s.trim()).filter(Boolean),
    description: row.description || ''
  }
  attendeeInput.value = ''
  dlgVisible.value = true
}

function addAttendee() {
  const name = attendeeInput.value.trim()
  if (name && !form.value.attendees.includes(name)) {
    form.value.attendees.push(name)
  }
  attendeeInput.value = ''
}
function removeAttendee(name) {
  form.value.attendees = form.value.attendees.filter(a => a !== name)
}

async function handleSubmit() {
  if (!form.value.title || !form.value.date || !form.value.startTime || !form.value.endTime) {
    ElMessage.warning('请填写会议标题、日期和时间')
    return
  }
  submitting.value = true
  try {
    const body = {
      title: form.value.title,
      room: form.value.room,
      date: form.value.date,
      startTime: form.value.startTime,
      endTime: form.value.endTime,
      attendees: form.value.attendees.join(','),
      description: form.value.description
    }
    let resp
    if (editId.value) {
      resp = await updateMeeting(editId.value, body)
    } else {
      resp = await createMeeting(body)
    }
    const result = await resp.json()
    if (result.code == 200) {
      dlgVisible.value = false
      ElMessage.success(editId.value ? '已更新' : '已创建')
      await load()
    } else {
      ElMessage.error(result.message || '操作失败')
    }
  } catch (e) {
    ElMessage.error('操作失败，请确认协作服务已启动')
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
```

- [ ] **Step 2: 重写 `<template>` — 统计栏 + 操作栏**

```html
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
      <el-button type="primary" @click="openCreate">新建会议</el-button>
      <div class="toolbar-right">
        <el-input v-model="keyword" placeholder="搜索会议标题" clearable style="width:200px" />
        <el-date-picker v-model="dateFilter" type="date" placeholder="筛选日期" clearable style="width:160px" value-format="YYYY-MM-DD" />
      </div>
    </div>

    <!-- 列表区域 -->
    <div v-loading="loading" class="meeting-list">
      <!-- 空状态 -->
      <div v-if="!loading && myMeetings.length === 0" class="empty-state">
        <div class="empty-text">暂无会议</div>
        <div class="empty-sub">点击「新建会议」开始预约吧</div>
        <el-button type="primary" @click="openCreate">新建会议</el-button>
      </div>

      <!-- 按日期分组 -->
      <div v-for="group in groupedMeetings" :key="group.date" class="date-group">
        <div class="date-header">{{ group.label }}</div>
        <div v-for="m in group.items" :key="m.id" class="meeting-card">
          <div class="card-bar" :style="{ background: statusColor(m.status) }" />
          <div class="card-body">
            <div class="card-row1">
              <span class="card-time">{{ m.start_time || '--' }} - {{ m.end_time || '--' }}</span>
              <el-tag v-if="m.room === '线上-Zoom'" size="small" type="info">线上</el-tag>
            </div>
            <div class="card-title">{{ m.title }}</div>
            <div class="card-meta">{{ m.room }}  ·  {{ attendeeDisplay(m) }}</div>
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
    <el-dialog v-model="dlgVisible" :title="editId ? '编辑会议' : '新建会议'" width="520px" @closed="editId=null">
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
              <el-form-item label="结束时间" required>
                <el-time-picker v-model="form.endTime" format="HH:mm" value-format="HH:mm" placeholder="结束" style="width:100%" />
              </el-form-item>
            </div>
          </div>
          <div class="form-col">
            <el-form-item label="地点" required>
              <el-select v-model="form.room" style="width:100%">
                <el-option label="A301 (20人)" value="A301 (20人)" />
                <el-option label="B102 (10人)" value="B102 (10人)" />
                <el-option label="C501 (50人)" value="C501 (50人)" />
                <el-option label="线上-Zoom" value="线上-Zoom" />
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
          <el-input v-model="form.description" type="textarea" :rows="3" placeholder="会议议程、准备事项等" maxlength="500" show-word-limit />
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
```

- [ ] **Step 3: 重写 `<style>`**

```css
<style scoped>
.meetings-page { display:flex; flex-direction:column; gap:20px; }

/* 统计栏 */
.stats-row { display:flex; gap:16px; }
.stat-card { flex:1; background:#fff; border-radius:12px; padding:20px 24px; border:1px solid #f3f4f6; }
.stat-value { font-size:28px; font-weight:700; color:#1f2937; }
.stat-label { font-size:13px; color:#9ca3af; margin-top:4px; }

/* 操作栏 */
.toolbar { display:flex; justify-content:space-between; align-items:center; }
.toolbar-right { display:flex; gap:12px; align-items:center; }

/* 列表 */
.meeting-list { display:flex; flex-direction:column; gap:16px; min-height:200px; }

.empty-state { display:flex; flex-direction:column; align-items:center; justify-content:center; padding:60px 0; gap:12px; }
.empty-text { font-size:16px; color:#9ca3af; }
.empty-sub { font-size:13px; color:#d1d5db; }

.date-group { display:flex; flex-direction:column; gap:8px; }
.date-header { font-size:14px; font-weight:600; color:#6b7280; padding:4px 0; }

/* 卡片 */
.meeting-card { display:flex; background:#fff; border-radius:12px; overflow:hidden; border:1px solid #f3f4f6; transition: box-shadow .2s; }
.meeting-card:hover { box-shadow: 0 4px 16px rgba(0,0,0,0.06); }
.card-bar { width:4px; flex-shrink:0; }
.card-body { flex:1; padding:16px 20px; display:flex; flex-direction:column; gap:6px; }
.card-row1 { display:flex; align-items:center; gap:8px; }
.card-time { font-size:14px; font-weight:600; color:#374151; }
.card-title { font-size:16px; font-weight:600; color:#1f2937; }
.card-meta { font-size:13px; color:#6b7280; }
.card-footer { display:flex; align-items:center; gap:8px; margin-top:4px; }
.card-creator { font-size:12px; color:#9ca3af; }
.card-status { font-size:12px; }
.card-actions { margin-left:auto; display:flex; gap:6px; }

/* 弹窗 */
.form-row-2col { display:flex; gap:16px; }
.form-col { flex:1; display:flex; flex-direction:column; }
.attendee-tags { display:flex; flex-wrap:wrap; align-items:center; gap:4px; }
</style>
```

- [ ] **Step 4: 删除原文件末尾的 mockMeetings 残留代码（如存在）**

确认 Meetings.vue 底部没有遗留的 `mockMeetings` 数组定义。

- [ ] **Step 5: 编译验证前端**

```bash
cd enterprise-web && npm run build --if-present 2>&1 | tail -5
```

或检查 Vite dev server 控制台无编译错误。

- [ ] **Step 6: 提交**

```bash
git add enterprise-web/src/pages/Meetings.vue
git commit -m "feat: 会议预约页面重设计 — 统计栏、卡片列表、增强弹窗"
```

---

### Task 5: 重启服务联调验证

- [ ] **Step 1: 重启协作服务**

在 IntelliJ 中重新运行 `enterprise-collaboration-service`（端口 8082）。

- [ ] **Step 2: 验证 /api/meetings/my 接口**

```bash
curl -H "X-User-Id: 6" http://localhost:8082/api/meetings/my
```

预期返回当前用户的会议列表 JSON。

- [ ] **Step 3: 浏览器验证**

打开 http://localhost:5173，登录后进入会议预约页面：
- 统计栏显示正确的数字
- 列表按日期分组展示卡片
- 新建会议弹窗：填表、添加参会人 tag、提交有 loading
- 编辑、取消功能正常
- 搜索和日期筛选生效

- [ ] **Step 4: 如有问题修复后提交**

```bash
git add -A && git commit -m "fix: 会议页面联调修复"
```
