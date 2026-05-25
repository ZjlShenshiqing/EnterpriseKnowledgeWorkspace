<template>
  <div class="admin-view">
    <section class="admin-page-header">
      <div>
        <div class="admin-page-title">关键词映射</div>
        <div class="admin-page-subtitle">管理高频关键词到知识库和应答策略的映射关系。</div>
      </div>
      <div class="admin-actions">
        <el-button type="primary" @click="openCreate">新增映射</el-button>
      </div>
    </section>

    <section class="admin-table-card">
      <div class="admin-toolbar">
        <div>
          <div class="admin-toolbar-title">映射规则</div>
          <div class="admin-toolbar-subtitle">优先级越高，越先命中。</div>
        </div>
        <el-input v-model="searchKeyword" placeholder="搜索关键词" clearable style="width:200px" @input="loadData" />
      </div>
      <el-table :data="mappings" v-loading="loading">
        <el-table-column prop="keyword" label="关键词" min-width="120" />
        <el-table-column prop="kbName" label="目标知识库" min-width="140" />
        <el-table-column prop="priority" label="优先级" width="100" />
        <el-table-column prop="strategy" label="应答策略" min-width="180" />
        <el-table-column prop="enabled" label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.enabled === 1 ? 'success' : 'info'" size="small">
              {{ row.enabled === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" size="small" @click="openEdit(row)">编辑</el-button>
            <el-button text type="danger" size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div style="display:flex;justify-content:flex-end;padding:12px 0">
        <el-pagination v-model:current-page="page" :page-size="size" :total="total" layout="total, prev, pager, next" @current-change="loadData" small />
      </div>
    </section>

    <el-dialog :title="isEdit ? '编辑映射' : '新增映射'" v-model="dialogVisible" width="480px" destroy-on-close>
      <el-form :model="form" label-width="90px">
        <el-form-item label="关键词" required>
          <el-input v-model="form.keyword" placeholder="如：报销" />
        </el-form-item>
        <el-form-item label="目标知识库" required>
          <el-input v-model="form.kbName" placeholder="如：制度知识库" />
        </el-form-item>
        <el-form-item label="优先级">
          <el-input-number v-model="form.priority" :min="0" :max="999" />
        </el-form-item>
        <el-form-item label="应答策略">
          <el-input v-model="form.strategy" placeholder="如：优先返回制度类来源" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.enabled" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="禁用" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSave" :loading="saving">{{ isEdit ? '保存' : '创建' }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getAuthHeaders } from '../../api/index.js'

const mappings = ref([])
const loading = ref(false)
const searchKeyword = ref('')
const page = ref(1)
const size = ref(20)
const total = ref(0)

const dialogVisible = ref(false)
const isEdit = ref(false)
const saving = ref(false)
const form = ref({})
const editId = ref(null)

function headers() { return { ...getAuthHeaders(), 'Content-Type': 'application/json' } }

async function loadData() {
  loading.value = true
  try {
    let url = `/api/keyword-mappings?current=${page.value}&size=${size.value}`
    if (searchKeyword.value) url += `&keyword=${encodeURIComponent(searchKeyword.value)}`
    const resp = await fetch(url, { headers: headers() })
    const json = await resp.json()
    const data = json.data || {}
    mappings.value = data.records || []
    total.value = data.total || 0
  } finally {
    loading.value = false
  }
}

function openCreate() {
  isEdit.value = false
  editId.value = null
  form.value = { keyword: '', kbName: '', priority: 100, strategy: '', enabled: 1 }
  dialogVisible.value = true
}

function openEdit(row) {
  isEdit.value = true
  editId.value = row.id
  form.value = { ...row }
  dialogVisible.value = true
}

async function handleSave() {
  if (!form.value.keyword || !form.value.kbName) {
    ElMessage.warning('关键词和目标知识库不能为空')
    return
  }
  saving.value = true
  try {
    const url = isEdit.value ? `/api/keyword-mappings/${editId.value}` : '/api/keyword-mappings'
    const method = isEdit.value ? 'PUT' : 'POST'
    const resp = await fetch(url, { method, headers: headers(), body: JSON.stringify(form.value) })
    if (resp.ok) {
      ElMessage.success(isEdit.value ? '更新成功' : '创建成功')
      dialogVisible.value = false
      loadData()
    }
  } finally {
    saving.value = false
  }
}

async function handleDelete(row) {
  await ElMessageBox.confirm(`确定删除关键词「${row.keyword}」的映射吗？`, '确认删除', { type: 'warning' })
  await fetch(`/api/keyword-mappings/${row.id}`, { method: 'DELETE', headers: headers() })
  ElMessage.success('删除成功')
  loadData()
}

onMounted(() => loadData())
</script>
