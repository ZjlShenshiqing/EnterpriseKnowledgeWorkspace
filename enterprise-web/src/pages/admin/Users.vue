<template>
  <div class="admin-view">
    <section class="admin-page-header">
      <div>
        <div class="admin-page-title">用户管理</div>
        <div class="admin-page-subtitle">统一管理后台管理员、知识库运营人员和普通业务用户。</div>
      </div>
      <div class="admin-actions">
        <el-button type="primary" @click="openCreate">新增用户</el-button>
      </div>
    </section>

    <section class="admin-grid-4">
      <article v-for="item in stats" :key="item.label" class="admin-stat">
        <div class="admin-stat-value">{{ item.value }}</div>
        <div class="admin-stat-label">{{ item.label }}</div>
        <div class="admin-stat-meta">{{ item.meta }}</div>
      </article>
    </section>

    <section class="admin-table-card">
      <div class="admin-toolbar">
        <div>
          <div class="admin-toolbar-title">用户清单</div>
          <div class="admin-toolbar-subtitle">查看账号、部门、角色和启停状态。</div>
        </div>
        <div class="admin-filters">
          <el-input v-model="keyword" placeholder="搜索用户名或姓名" style="width:220px" clearable @input="onSearch" />
        </div>
      </div>
      <el-table :data="users" v-loading="loading">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="username" label="用户名" min-width="120" />
        <el-table-column prop="realName" label="姓名" min-width="100" />
        <el-table-column label="部门" min-width="100">
          <template #default="{ row }">{{ row.dept?.name || '-' }}</template>
        </el-table-column>
        <el-table-column label="角色" min-width="160">
          <template #default="{ row }">{{ row.roles?.map(r => r.name).join('、') || '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'danger'" size="small">
              {{ row.enabled ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="openEdit(row)">编辑</el-button>
            <el-button size="small" type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div style="display:flex;justify-content:flex-end;margin-top:16px">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[10,20,50]"
          layout="total,sizes,prev,pager,next"
          @size-change="loadUsers"
          @current-change="loadUsers"
        />
      </div>
    </section>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑用户' : '新增用户'" width="480px" @closed="resetForm">
      <el-form ref="formRef" :model="form" label-width="80px">
        <el-form-item label="用户名" required>
          <el-input v-model="form.username" :disabled="!!editingId" />
        </el-form-item>
        <el-form-item v-if="!editingId" label="密码" required>
          <el-input v-model="form.password" type="password" show-password />
        </el-form-item>
        <el-form-item label="姓名">
          <el-input v-model="form.realName" />
        </el-form-item>
        <el-form-item label="部门">
          <el-select v-model="form.deptId" clearable placeholder="请选择部门">
            <el-option v-for="d in depts" :key="d.id" :label="d.name" :value="d.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="form.roleCodes" multiple placeholder="请选择角色">
            <el-option v-for="r in allRoles" :key="r.code" :label="r.name" :value="r.code" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="editingId" label="启用">
          <el-switch v-model="form.enabled" />
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
import { computed, onMounted, ref } from 'vue'
import { getUsers, getUserStats, createUser, updateUser, deleteUser, getRoles, getDepts, readStoredAuth } from '../../api/index.js'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'

const router = useRouter()

const users = ref([])
const loading = ref(false)
const keyword = ref('')
const currentPage = ref(1)
const pageSize = ref(20)
const total = ref(0)

const _stats = ref({ total: 0, enabled: 0, admin: 0, disabled: 0 })
const stats = computed(() => [
  { label: '总用户数', value: _stats.value.total, meta: '后台当前管理的账号总数' },
  { label: '启用账号', value: _stats.value.enabled, meta: '可正常登录与使用' },
  { label: '管理员', value: _stats.value.admin, meta: '具备后台操作权限' },
  { label: '停用账号', value: _stats.value.disabled, meta: '已冻结或待恢复' }
])

let timer = null
function onSearch() {
  clearTimeout(timer)
  timer = setTimeout(() => { currentPage.value = 1; loadUsers() }, 300)
}

function handleAuthError(e, fallback) {
  const status = e.response?.status
  const msg = e.response?.data?.message || e.message
  if (status === 401 || status === 403 || msg?.includes('未登录')) {
    ElMessage.error(msg || '未登录或登录已过期，请重新登录')
    setTimeout(() => router.push('/login'), 800)
    return true
  }
  ElMessage.error(msg || fallback)
  return false
}

async function loadUsers() {
  if (!readStoredAuth().hasValidToken) {
    ElMessage.warning('请先登录后再访问用户管理')
    router.push('/login')
    return
  }
  loading.value = true
  try {
    const { data: res } = await getUsers({ keyword: keyword.value, page: currentPage.value, size: pageSize.value })
    if (res.code == 200 || res.code === '200') {
      users.value = res.data?.records || []
      total.value = res.data?.total || 0
    } else {
      users.value = []
      total.value = 0
      if (res.code === 40100 || res.code === '40100') {
        handleAuthError({ response: { status: 401, data: res } }, '加载用户列表失败')
      } else {
        ElMessage.error(res.message || '加载用户列表失败')
      }
    }
  } catch (e) {
    users.value = []
    total.value = 0
    handleAuthError(e, '加载用户列表失败')
  } finally { loading.value = false }
}

async function loadStats() {
  try {
    const { data: res } = await getUserStats()
    if (res.code == 200) {
      _stats.value = res.data
    } else {
      ElMessage.error(res.message || '加载用户统计失败')
    }
  } catch { /* ignore */ }
}

const depts = ref([])
const allRoles = ref([])

async function loadOptions() {
  try {
    const [deptRes, roleRes] = await Promise.all([getDepts(), getRoles()])
    if (deptRes.data.code == 200) {
      depts.value = deptRes.data.data || []
    } else {
      ElMessage.error(deptRes.data.message || '加载部门列表失败')
    }
    if (roleRes.data.code == 200) {
      allRoles.value = roleRes.data.data || []
    } else {
      ElMessage.error(roleRes.data.message || '加载角色列表失败')
    }
  } catch { /* ignore */ }
}

// 新增 / 编辑
const dialogVisible = ref(false)
const editingId = ref(null)
const submitting = ref(false)
const formRef = ref(null)
const form = ref({ username: '', password: '', realName: '', deptId: null, roleCodes: [], enabled: true })

function openCreate() {
  editingId.value = null
  form.value = { username: '', password: '', realName: '', deptId: null, roleCodes: [], enabled: true }
  dialogVisible.value = true
}

function openEdit(row) {
  editingId.value = row.id
  form.value = {
    username: row.username,
    password: '',
    realName: row.realName || '',
    deptId: row.dept?.id || null,
    roleCodes: row.roles?.map(r => r.code) || [],
    enabled: row.enabled
  }
  dialogVisible.value = true
}

function resetForm() {
  editingId.value = null
  form.value = { username: '', password: '', realName: '', deptId: null, roleCodes: [], enabled: true }
}

async function handleSubmit() {
  submitting.value = true
  try {
    const body = {
      username: form.value.username,
      password: form.value.password || undefined,
      realName: form.value.realName || undefined,
      deptId: form.value.deptId || undefined,
      roleCodes: form.value.roleCodes.length ? form.value.roleCodes : undefined
    }
    if (editingId.value) {
      body.enabled = form.value.enabled
      delete body.username
      delete body.password
      const { data: res } = await updateUser(editingId.value, body)
      if (res.code != 200) {
        ElMessage.error(res.message || '更新失败')
        return
      }
      ElMessage.success('更新成功')
    } else {
      if (!form.value.username || !form.value.password) {
        ElMessage.warning('用户名和密码为必填项')
        submitting.value = false
        return
      }
      const { data: res } = await createUser(body)
      if (res.code != 200) {
        ElMessage.error(res.message || '创建失败')
        return
      }
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    await loadUsers()
    await loadStats()
  } catch (e) {
    const msg = e.response?.data?.message || e.message
    if (e.response?.status === 401) {
      ElMessage.error('未登录或登录已过期，请重新登录')
    } else if (e.response?.status === 403) {
      ElMessage.error('无权限：请使用管理员账号登录')
    } else {
      ElMessage.error(msg || '操作失败')
    }
  } finally { submitting.value = false }
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(`确定删除用户「${row.username}」吗？`, '提示', { type: 'warning' })
    const { data: res } = await deleteUser(row.id)
    if (res.code != 200) {
      ElMessage.error(res.message || '删除失败')
      return
    }
    ElMessage.success('删除成功')
    await loadUsers()
    await loadStats()
  } catch { /* cancelled */ }
}

onMounted(() => { loadUsers(); loadStats(); loadOptions() })
</script>
