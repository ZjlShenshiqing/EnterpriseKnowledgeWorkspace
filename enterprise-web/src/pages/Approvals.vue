<template>
  <div style="height:calc(100vh - 140px);display:flex;flex-direction:column">
    <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:16px">
      <span style="font-size:18px;font-weight:600">流程审批</span>
      <el-button type="primary" @click="showCreate=true">发起审批</el-button>
    </div>

    <el-tabs v-model="tab" style="flex:1;display:flex;flex-direction:column">
      <el-tab-pane label="我的申请" name="my">
        <div v-if="!myList.length" style="text-align:center;padding:60px;color:var(--text-tertiary)">暂无申请记录</div>
        <div v-for="r in myList" :key="r.id" @click="openDetail(r)"
          style="display:flex;align-items:center;padding:14px 16px;border-bottom:1px solid var(--border-light);cursor:pointer;transition:background .15s"
          @mouseenter="e=>e.target.style.background='var(--bg-hover)'" @mouseleave="e=>e.target.style.background='transparent'">
          <div style="flex:1">
            <div style="font-size:15px;font-weight:500">{{ r.title }}</div>
            <div style="font-size:12px;color:var(--text-tertiary);margin-top:4px">{{ typeLabel(r.type) }} · {{ r.user_name }} · {{ r.created_at }}</div>
          </div>
          <el-tag :type="statusTag(r.status)" size="small">{{ statusLabel(r.status) }}</el-tag>
        </div>
      </el-tab-pane>
      <el-tab-pane v-if="isAdmin" label="待审批" name="pending">
        <div v-if="!pendingList.length" style="text-align:center;padding:60px;color:var(--text-tertiary)">暂无待审批</div>
        <div v-for="r in pendingList" :key="r.id" style="display:flex;align-items:center;padding:14px 16px;border-bottom:1px solid var(--border-light)">
          <div style="flex:1">
            <div style="font-size:15px;font-weight:500">{{ r.title }}</div>
            <div style="font-size:12px;color:var(--text-tertiary);margin-top:4px">{{ typeLabel(r.type) }} · {{ r.user_name }} · {{ r.created_at }}</div>
          </div>
          <div style="display:flex;gap:8px">
            <el-button size="small" type="success" @click="doApprove(r,'approve')">通过</el-button>
            <el-button size="small" type="danger" @click="doReject(r)">驳回</el-button>
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- Create Dialog -->
    <el-dialog v-model="showCreate" title="发起审批" width="480px">
      <el-form :model="form" label-width="60px">
        <el-form-item label="类型"><el-select v-model="form.type" style="width:100%"><el-option label="请假申请" value="leave" /><el-option label="报销申请" value="expense" /></el-select></el-form-item>
        <el-form-item label="标题"><el-input v-model="form.title" /></el-form-item>
        <template v-if="form.type==='leave'">
          <el-form-item label="请假类型"><el-select v-model="fd.leaveType" style="width:100%"><el-option label="年假" value="annual" /><el-option label="事假" value="personal" /><el-option label="病假" value="sick" /></el-select></el-form-item>
          <el-form-item label="开始日期"><el-date-picker v-model="fd.startDate" style="width:100%" /></el-form-item>
          <el-form-item label="结束日期"><el-date-picker v-model="fd.endDate" style="width:100%" /></el-form-item>
        </template>
        <template v-if="form.type==='expense'">
          <el-form-item label="报销金额"><el-input-number v-model="fd.amount" :min="0" style="width:100%" /></el-form-item>
          <el-form-item label="费用类别"><el-select v-model="fd.expenseType" style="width:100%"><el-option label="差旅" value="travel" /><el-option label="办公用品" value="office" /><el-option label="招待" value="entertainment" /><el-option label="其他" value="other" /></el-select></el-form-item>
        </template>
        <el-form-item label="事由"><el-input v-model="fd.reason" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="showCreate=false">取消</el-button><el-button type="primary" @click="doCreate">提交</el-button></template>
    </el-dialog>

    <!-- Detail Dialog -->
    <el-dialog v-model="showDetail" title="审批详情" width="520px">
      <div v-if="detail" style="line-height:2">
        <p><b>标题：</b>{{ detail.title }}</p><p><b>类型：</b>{{ typeLabel(detail.type) }}</p><p><b>申请人：</b>{{ detail.user_name }}</p>
        <p><b>状态：</b><el-tag :type="statusTag(detail.status)" size="small">{{ statusLabel(detail.status) }}</el-tag></p>
        <p><b>提交时间：</b>{{ detail.created_at }}</p>
        <div style="margin-top:12px"><b>审批记录</b></div>
        <div v-for="r in detail.records" :key="r.id" style="padding:8px;background:var(--bg-body);border-radius:6px;margin:4px 0;font-size:13px;display:flex;justify-content:space-between">
          <span>{{ r.approver_name }} · {{ r.action==='approve'?'通过':'驳回' }}{{ r.comment?' - '+r.comment:'' }}</span>
          <span style="color:var(--text-tertiary);font-size:11px">{{ r.created_at }}</span>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

const tab = ref('my'); const showCreate = ref(false); const showDetail = ref(false)
const allList = ref([]); const detail = ref(null)
const userId = JSON.parse(localStorage.getItem('user')||'{}').id||1
const isAdmin = JSON.parse(localStorage.getItem('user')||'{}').isAdmin
const form = ref({ type:'leave',title:'' })
const fd = ref({ leaveType:'annual',startDate:'',endDate:'',amount:0,expenseType:'travel',reason:'' })

const myList = computed(() => allList.value.filter(r=>r.user_id==userId))
const pendingList = computed(() => allList.value.filter(r=>r.status==='pending'||r.status==='manager_approved'||r.status==='finance_approved'))

function headers() {
  const u = JSON.parse(localStorage.getItem('user')||'{}')
  return {'X-User-Id':String(u.id||1),'X-Is-Admin':String(u.isAdmin?'true':'false'),'Content-Type':'application/json'}
}

function typeLabel(t) { return t==='leave'?'请假':'报销' }
function statusLabel(s) { return ({pending:'待审批',manager_approved:'主管已批',finance_approved:'财务已批',approved:'已通过',rejected:'已驳回'})[s]||s }
function statusTag(s) { return s==='approved'?'success':s==='rejected'?'danger':'warning' }

async function load() { try { const r = await fetch('/api/approvals',{headers:headers()}); allList.value = (await r.json()).data||[] } catch(e) {} }

async function doCreate() {
  if (!form.value.title) return
  await fetch('/api/approvals',{method:'POST',headers:headers(),body:JSON.stringify({...form.value,formData:fd.value})})
  showCreate.value = false; form.value = { type:'leave',title:'' }; fd.value = { leaveType:'annual',startDate:'',endDate:'',amount:0,expenseType:'travel',reason:'' }
  ElMessage.success('提交成功'); await load()
}

async function openDetail(r) {
  try { const resp = await fetch(`/api/approvals/${r.id}`,{headers:headers()}); detail.value = await resp.json(); detail.value = detail.value.data; showDetail.value = true }
  catch(e) { detail.value = r; showDetail.value = true }
}

async function doApprove(r, action) {
  await fetch(`/api/approvals/${r.id}/approve`,{method:'POST',headers:headers(),body:JSON.stringify({action,comment:''})})
  ElMessage.success(action==='approve'?'已通过':'已驳回'); await load()
}

async function doReject(r) {
  try {
    const { value } = await ElMessageBox.prompt('驳回理由', '驳回审批', { confirmButtonText:'确认', cancelButtonText:'取消' })
    await fetch(`/api/approvals/${r.id}/approve`,{method:'POST',headers:headers(),body:JSON.stringify({action:'reject',comment:value||''})})
    ElMessage.success('已驳回'); await load()
  } catch(e) {}
}

onMounted(load)
</script>
