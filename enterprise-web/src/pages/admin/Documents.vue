<template>
  <div>
    <div style="display:flex;justify-content:space-between;margin-bottom:16px">
      <span style="font-size:16px;font-weight:600">文档管理</span>
      <el-button type="primary" @click="openUpload">上传文档</el-button>
    </div>
    <el-table :data="docs" stripe v-loading="loading">
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="title" label="标题" min-width="160" />
      <el-table-column prop="fileType" label="类型" width="120" />
      <el-table-column label="状态" width="100"><template #default="{row}"><el-tag :type="row.status==='SUCCESS'?'success':'info'" size="small">{{row.status}}</el-tag></template></el-table-column>
      <el-table-column prop="chunkCount" label="切片" width="60" />
      <el-table-column label="操作" width="260">
        <template #default="{row}">
          <el-button size="small" type="warning" v-if="row.status==='PENDING'||row.status==='FAILED'" @click="startChunk(row)">分块</el-button>
          <el-button size="small" type="primary" @click="toggleEnabled(row)">{{row.enabled===0?'启用':'禁用'}}</el-button>
          <el-button size="small" type="danger" @click="deleteDoc(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination v-if="total>20" v-model:current-page="page" :total="total" layout="prev,pager,next" @current-change="load" style="margin-top:16px;justify-content:center" />

    <!-- Upload Dialog -->
    <el-dialog v-model="uploadVisible" title="上传文档" width="520px" @closed="resetForm">
      <el-form :model="form" label-width="80px">
        <el-form-item label="文档标题" required><el-input v-model="form.title" placeholder="输入文档标题" /></el-form-item>
        <el-form-item label="分类" required>
          <el-select v-model="form.categoryId" placeholder="选择分类" style="width:100%">
            <el-option v-for="c in categories" :key="c.id" :label="c.categoryName" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="知识库"><el-select v-model="form.kbId" placeholder="可选" clearable style="width:100%"><el-option v-for="b in bases" :key="b.id" :label="b.name" :value="b.id" /></el-select></el-form-item>
        <el-form-item label="权限类型" required>
          <el-select v-model="form.permissionType" style="width:100%">
            <el-option label="全员可见" value="ALL" />
            <el-option label="部门可见" value="DEPARTMENT" />
            <el-option label="指定用户" value="USER" />
            <el-option label="指定项目" value="PROJECT" />
            <el-option label="仅管理员" value="ADMIN" />
          </el-select>
        </el-form-item>
        <el-form-item label="标签"><el-input v-model="form.tags" placeholder="逗号分隔，如：技术,架构" /></el-form-item>
        <el-form-item label="选择文件" required>
          <el-upload ref="uploadRef" :auto-upload="false" :limit="1" :on-change="handleFileChange" accept=".pdf,.doc,.docx,.xls,.xlsx,.ppt,.pptx,.txt,.md,.html">
            <el-button type="primary">选择文件</el-button>
            <template #tip><div style="font-size:12px;color:#909399;margin-top:4px">支持 PDF/Word/Excel/PPT/TXT/MD/HTML</div></template>
          </el-upload>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="uploadVisible=false">取消</el-button>
        <el-button type="primary" @click="doUpload" :loading="uploading">上传</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getDocuments, getCategories, getKnowledgeBases, readStoredAuth } from '../../api'
import { ElMessage, ElMessageBox } from 'element-plus'

const docs = ref([]); const loading = ref(false); const page = ref(1); const total = ref(0)
const uploadVisible = ref(false); const uploading = ref(false)
const categories = ref([]); const bases = ref([])
const selectedFile = ref(null); const uploadRef = ref(null)

const form = ref({ title:'', categoryId:null, kbId:null, permissionType:'ALL', tags:'' })

function authHeaders() {
  const { user } = readStoredAuth()
  return {
    'X-User-Id': String(user.id || '1'),
    'X-Department-Id': String(user.departmentId || '1'),
    'X-Is-Admin': String(user.isAdmin ? 'true' : 'false')
  }
}

function resetForm() { form.value = { title:'', categoryId:null, kbId:null, permissionType:'ALL', tags:'' }; selectedFile.value = null; uploadRef.value?.clearFiles() }

async function load() {
  loading.value = true
  try {
    const res = await getDocuments({ current: page.value, size: 20 })
    docs.value = res.data.data?.records || []; total.value = res.data.data?.total || 0
  } catch(e) {
    docs.value = [
      {id:1,title:'公司差旅报销制度',fileType:'application/pdf',status:'SUCCESS',chunkCount:12,enabled:1},
      {id:2,title:'微服务架构设计指南',fileType:'application/msword',status:'SUCCESS',chunkCount:25,enabled:1},
      {id:3,title:'2026年度技术规划',fileType:'application/pdf',status:'PENDING',chunkCount:0,enabled:1},
    ]; total.value = 3
  }
  loading.value = false
}

async function openUpload() {
  try {
    const [catRes, baseRes] = await Promise.all([getCategories(), getKnowledgeBases({current:1,size:50})])
    categories.value = catRes.data.data || []
    bases.value = baseRes.data.data?.records || []
  } catch(e) {
    categories.value = [{id:1001,categoryName:'默认分类'}]; bases.value = []
  }
  uploadVisible.value = true
}

function handleFileChange(file) { selectedFile.value = file.raw }

async function doUpload() {
  if (!form.value.title || !form.value.categoryId || !selectedFile.value) {
    ElMessage.warning('请填写标题、分类并选择文件'); return
  }
  uploading.value = true
  try {
    const fd = new FormData()
    fd.append('meta', new Blob([JSON.stringify({
      title: form.value.title,
      categoryId: form.value.categoryId,
      kbId: form.value.kbId,
      permissionType: form.value.permissionType,
      tags: form.value.tags
    })], { type: 'application/json' }))
    fd.append('file', selectedFile.value)

    const resp = await fetch('/api/kb/documents/upload', {
      method: 'POST',
      headers: authHeaders(),
      body: fd
    })
    const body = await resp.json()
    if (resp.ok && String(body.code) === '200') {
      ElMessage.success('上传成功')
      uploadVisible.value = false
      resetForm()
      await load()
    } else {
      ElMessage.error(body.message || '上传失败')
    }
  } catch(e) {
    ElMessage.error('上传失败: ' + e.message)
  }
  uploading.value = false
}

async function startChunk(row) {
  try {
    await fetch('/api/kb/documents/' + row.id + '/start-chunk', {
      method: 'POST',
      headers: authHeaders()
    })
    ElMessage.success('分块任务已提交'); await load()
  } catch(e) { ElMessage.error('提交失败') }
}

async function deleteDoc(row) {
  try {
    await ElMessageBox.confirm('确定删除该文档？', '确认', { type: 'warning' })
    await fetch('/api/kb/documents/' + row.id, {
      method: 'DELETE',
      headers: authHeaders()
    })
    ElMessage.success('已删除'); await load()
  } catch(e) {}
}

async function toggleEnabled(row) {
  try {
    const on = row.enabled === 1 ? 'false' : 'true'
    await fetch('/api/kb/documents/' + row.id + '/enabled?on=' + on, {
      method: 'PATCH',
      headers: authHeaders()
    })
    ElMessage.success('操作成功'); await load()
  } catch(e) { ElMessage.error('操作失败') }
}

onMounted(load)
</script>
