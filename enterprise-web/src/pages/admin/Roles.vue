<template>
  <div>
    <div style="display:flex;justify-content:space-between;margin-bottom:16px">
      <span style="font-size:16px;font-weight:600">角色管理</span>
      <el-button type="primary" @click="openCreate">新增角色</el-button>
    </div>
    <el-table :data="roles" stripe v-loading="loading">
      <el-table-column prop="code" label="角色编码" width="140" />
      <el-table-column prop="name" label="角色名称" />
      <el-table-column label="权限数" width="80">
        <template #default="{ row }">{{ row.permissions?.length || 0 }}</template>
      </el-table-column>
      <el-table-column prop="userCount" label="用户数" width="80" />
      <el-table-column label="操作" width="160">
        <template #default="{ row }">
          <el-button size="small" @click="openEdit(row)">编辑</el-button>
          <el-button size="small" type="danger" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑角色' : '新增角色'" width="460px" @closed="resetForm">
      <el-form ref="formRef" :model="form" label-width="80px">
        <el-form-item label="角色编码" required>
          <el-input v-model="form.code" :disabled="!!editingId" />
        </el-form-item>
        <el-form-item label="角色名称" required>
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="权限">
          <el-select v-model="form.permissionCodes" multiple placeholder="请选择权限">
            <el-option v-for="p in permissions" :key="p.code" :label="p.name" :value="p.code" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { getRoles, createRole, updateRole, deleteRole, getPermissions } from '../../api/index.js'
import { ElMessage, ElMessageBox } from 'element-plus'

const roles = ref([])
const loading = ref(false)

async function loadRoles() {
  loading.value = true
  try {
    const { data: res } = await getRoles()
    if (res.code == 200) roles.value = res.data
  } catch { ElMessage.error('加载角色列表失败') }
  finally { loading.value = false }
}

const permissions = ref([])

async function loadPermissions() {
  try {
    const { data: res } = await getPermissions()
    if (res.code == 200) permissions.value = res.data
  } catch { /* ignore */ }
}

// 新增 / 编辑
const dialogVisible = ref(false)
const editingId = ref(null)
const submitting = ref(false)
const formRef = ref(null)
const form = ref({ code: '', name: '', permissionCodes: [] })

function openCreate() {
  editingId.value = null
  form.value = { code: '', name: '', permissionCodes: [] }
  dialogVisible.value = true
}

function openEdit(row) {
  editingId.value = row.id
  form.value = {
    code: row.code,
    name: row.name,
    permissionCodes: row.permissions?.map(p => p.code) || []
  }
  dialogVisible.value = true
}

function resetForm() {
  editingId.value = null
  form.value = { code: '', name: '', permissionCodes: [] }
}

async function handleSubmit() {
  submitting.value = true
  try {
    const body = {
      code: form.value.code,
      name: form.value.name,
      permissionCodes: form.value.permissionCodes.length ? form.value.permissionCodes : undefined
    }
    if (editingId.value) {
      await updateRole(editingId.value, { name: body.name, permissionCodes: body.permissionCodes })
      ElMessage.success('更新成功')
    } else {
      if (!form.value.code || !form.value.name) {
        ElMessage.warning('角色编码和名称为必填项')
        submitting.value = false
        return
      }
      await createRole(body)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    loadRoles()
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '操作失败')
  } finally { submitting.value = false }
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(`确定删除角色「${row.name}」吗？`, '提示', { type: 'warning' })
    await deleteRole(row.id)
    ElMessage.success('删除成功')
    loadRoles()
  } catch { /* cancelled */ }
}

onMounted(() => { loadRoles(); loadPermissions() })
</script>
