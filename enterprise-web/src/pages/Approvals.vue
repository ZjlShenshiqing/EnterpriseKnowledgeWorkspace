<template>
  <div class="approvals-page">
    <div class="page-head">
      <span class="page-title">流程审批</span>
      <el-button type="primary" @click="showCreate = true">发起审批</el-button>
    </div>

    <el-tabs v-model="tab" class="approval-tabs">
      <el-tab-pane label="我的申请" name="applications">
        <div v-if="!myApplications.length" class="empty">暂无申请记录</div>
        <div v-for="item in myApplications" :key="item.id" class="row" @click="openDetail(item.id)">
          <div class="row-main">
            <div class="row-title">{{ item.title }}</div>
            <div class="row-meta">{{ typeLabel(item.type) }} · {{ item.userName || '我' }} · {{ formatTime(item.createdAt) }}</div>
          </div>
          <el-tag :type="statusTag(item.status)" size="small">{{ statusLabel(item.status) }}</el-tag>
        </div>
      </el-tab-pane>

      <el-tab-pane label="我的待审" name="tasks">
        <div v-if="!myTasks.length" class="empty">暂无待处理审批</div>
        <div v-for="task in myTasks" :key="task.id" class="row">
          <div class="row-main" @click="task.approvalId && openDetail(task.approvalId)">
            <div class="row-title">{{ task.approvalTitle || `审批任务 #${task.id}` }}</div>
            <div class="row-meta">{{ typeLabel(task.approvalType) }} · {{ task.applicantName || '申请人' }} · 任务 #{{ task.id }}</div>
          </div>
          <div class="actions">
            <el-button size="small" type="success" @click="approve(task)">通过</el-button>
            <el-button size="small" type="danger" @click="reject(task)">驳回</el-button>
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="showCreate" title="发起审批" width="480px">
      <el-form :model="form" label-width="76px">
        <el-form-item label="类型">
          <el-select v-model="form.type" style="width:100%">
            <el-option label="请假申请" value="leave" />
            <el-option label="报销申请" value="expense" />
          </el-select>
        </el-form-item>
        <el-form-item label="标题">
          <el-input v-model="form.title" />
        </el-form-item>
        <template v-if="form.type === 'leave'">
          <el-form-item label="请假类型">
            <el-select v-model="formData.leaveType" style="width:100%">
              <el-option label="年假" value="annual" />
              <el-option label="事假" value="personal" />
              <el-option label="病假" value="sick" />
            </el-select>
          </el-form-item>
          <el-form-item label="开始日期">
            <el-date-picker v-model="formData.startDate" value-format="YYYY-MM-DD" style="width:100%" />
          </el-form-item>
          <el-form-item label="结束日期">
            <el-date-picker v-model="formData.endDate" value-format="YYYY-MM-DD" style="width:100%" />
          </el-form-item>
        </template>
        <template v-else>
          <el-form-item label="报销金额">
            <el-input-number v-model="formData.amount" :min="0" style="width:100%" />
          </el-form-item>
          <el-form-item label="费用类别">
            <el-select v-model="formData.expenseType" style="width:100%">
              <el-option label="差旅" value="travel" />
              <el-option label="办公用品" value="office" />
              <el-option label="招待" value="entertainment" />
              <el-option label="其他" value="other" />
            </el-select>
          </el-form-item>
        </template>
        <el-form-item label="事由">
          <el-input v-model="formData.reason" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreate = false">取消</el-button>
        <el-button type="primary" @click="submit">提交</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showDetail" title="审批详情" width="620px">
      <div v-if="detail" class="detail">
        <div class="detail-head">
          <div>
            <div class="detail-title">{{ detail.title }}</div>
            <div class="row-meta">{{ typeLabel(detail.type) }} · {{ detail.userName || '申请人' }}</div>
          </div>
          <el-tag :type="statusTag(detail.status)" size="small">{{ statusLabel(detail.status) }}</el-tag>
        </div>
        <div class="detail-grid">
          <span>提交时间</span><b>{{ formatTime(detail.createdAt) }}</b>
          <span>流程状态</span><b>{{ statusLabel(detail.instance?.status || detail.status) }}</b>
          <span>当前节点</span><b>{{ detail.instance?.currentNodeId || '-' }}</b>
        </div>
        <pre class="form-json">{{ prettyForm(detail.formData) }}</pre>
        <el-timeline>
          <el-timeline-item v-for="record in detail.records || []" :key="record.id" :timestamp="formatTime(record.createdAt)">
            <b>{{ actionLabel(record.action) }}</b>
            <span v-if="record.comment"> · {{ record.comment }}</span>
          </el-timeline-item>
        </el-timeline>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createApproval,
  getApprovalDetail,
  getMyApprovals,
  getMyWorkflowTasks,
  handleWorkflowTask
} from '../api/index.js'

const tab = ref('applications')
const showCreate = ref(false)
const showDetail = ref(false)
const myApplications = ref([])
const myTasks = ref([])
const detail = ref(null)
const form = ref({ type: 'leave', title: '' })
const formData = ref(defaultFormData())

function defaultFormData() {
  return { leaveType: 'annual', startDate: '', endDate: '', amount: 0, expenseType: 'travel', reason: '' }
}

async function load() {
  const [applicationsResp, tasksResp] = await Promise.all([getMyApprovals(), getMyWorkflowTasks()])
  myApplications.value = applicationsResp.data || []
  myTasks.value = tasksResp.data || []
}

async function submit() {
  if (!form.value.title.trim()) {
    ElMessage.warning('请填写标题')
    return
  }
  const resp = await createApproval({ ...form.value, formData: formData.value })
  if (!(resp.code === 200 || resp.code === '200')) {
    ElMessage.error(resp.message || '提交失败')
    return
  }
  showCreate.value = false
  form.value = { type: 'leave', title: '' }
  formData.value = defaultFormData()
  ElMessage.success('提交成功')
  await load()
}

async function openDetail(id) {
  const resp = await getApprovalDetail(id)
  if (resp.code === 200 || resp.code === '200') {
    detail.value = resp.data
    showDetail.value = true
  } else {
    ElMessage.error(resp.message || '加载详情失败')
  }
}

async function approve(task) {
  await handleTask(task, 'APPROVE', '')
}

async function reject(task) {
  try {
    const { value } = await ElMessageBox.prompt('驳回理由', '驳回审批', {
      confirmButtonText: '确认',
      cancelButtonText: '取消'
    })
    await handleTask(task, 'REJECT', value || '')
  } catch {
  }
}

async function handleTask(task, action, comment) {
  const resp = await handleWorkflowTask(task.id, action, comment)
  if (!(resp.code === 200 || resp.code === '200')) {
    ElMessage.error(resp.message || '处理失败')
    return
  }
  ElMessage.success(action === 'APPROVE' ? '已通过' : '已驳回')
  await load()
}

function typeLabel(type) {
  return ({ leave: '请假', expense: '报销' })[type] || type || '-'
}

function statusLabel(status) {
  return ({
    PENDING: '待审批',
    RUNNING: '审批中',
    APPROVED: '已通过',
    REJECTED: '已驳回',
    CLOSED: '已关闭'
  })[status] || status || '-'
}

function statusTag(status) {
  if (status === 'APPROVED') return 'success'
  if (status === 'REJECTED') return 'danger'
  return 'warning'
}

function actionLabel(action) {
  return ({ START: '发起', APPROVE: '通过', REJECT: '驳回', AUTO_CLOSE: '自动关闭', COMPLETE: '完成' })[action] || action
}

function formatTime(value) {
  return value ? String(value).replace('T', ' ').slice(0, 19) : '-'
}

function prettyForm(value) {
  try {
    return JSON.stringify(JSON.parse(value || '{}'), null, 2)
  } catch {
    return value || '{}'
  }
}

onMounted(load)
</script>

<style scoped>
.approvals-page { height: calc(100vh - 132px); display: flex; flex-direction: column; }
.page-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.page-title { font-size: 18px; font-weight: 600; }
.approval-tabs { flex: 1; min-height: 0; }
.empty { text-align: center; padding: 60px; color: var(--text-tertiary); }
.row { display: flex; align-items: center; gap: 12px; padding: 14px 16px; border-bottom: 1px solid var(--border-light); }
.row:hover { background: var(--bg-hover); }
.row-main { flex: 1; min-width: 0; cursor: pointer; }
.row-title { font-size: 15px; font-weight: 500; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.row-meta { font-size: 12px; color: var(--text-tertiary); margin-top: 4px; }
.actions { display: flex; gap: 8px; flex-shrink: 0; }
.detail { line-height: 1.8; }
.detail-head { display: flex; justify-content: space-between; gap: 16px; margin-bottom: 16px; }
.detail-title { font-size: 16px; font-weight: 600; }
.detail-grid { display: grid; grid-template-columns: 80px 1fr; gap: 6px 12px; margin-bottom: 12px; }
.detail-grid span { color: var(--text-tertiary); }
.form-json { padding: 10px; background: var(--bg-body); border-radius: 6px; overflow: auto; font-size: 12px; }
</style>
