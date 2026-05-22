<template>
  <div class="admin-view">
    <section class="admin-page-header">
      <div>
        <div class="admin-page-title">操作日志</div>
        <div class="admin-page-subtitle">查看管理员和用户的关键操作记录。</div>
      </div>
    </section>

    <section class="admin-table-card">
      <div class="admin-toolbar">
        <div>
          <div class="admin-toolbar-title">日志清单</div>
          <div class="admin-toolbar-subtitle">按时间倒序展示，支持关键词和操作类型筛选。</div>
        </div>
        <div class="admin-filters">
          <el-select v-model="action" placeholder="操作类型" clearable style="width:180px;margin-right:8px" @change="onFilterChange">
            <el-option label="全部" value="" />
            <el-option label="CREATE_USER" value="CREATE_USER" />
            <el-option label="UPDATE_USER" value="UPDATE_USER" />
            <el-option label="DELETE_USER" value="DELETE_USER" />
            <el-option label="CREATE_ROLE" value="CREATE_ROLE" />
            <el-option label="UPDATE_ROLE" value="UPDATE_ROLE" />
            <el-option label="DELETE_ROLE" value="DELETE_ROLE" />
            <el-option label="CREATE_DEPT" value="CREATE_DEPT" />
            <el-option label="CREATE_PERMISSION" value="CREATE_PERMISSION" />
          </el-select>
          <el-input v-model="keyword" placeholder="搜索用户名或详情" style="width:220px" clearable @input="onSearch" />
        </div>
      </div>
      <el-table :data="logs" v-loading="loading">
        <el-table-column prop="username" label="操作人" width="100" />
        <el-table-column prop="action" label="操作类型" width="180" />
        <el-table-column prop="path" label="路径" min-width="200" />
        <el-table-column prop="detail" label="详情" width="180" />
        <el-table-column label="时间" width="180">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
      </el-table>
      <div style="display:flex;justify-content:flex-end;margin-top:16px">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[10,20,50]"
          layout="total,sizes,prev,pager,next"
          @size-change="loadLogs"
          @current-change="loadLogs"
        />
      </div>
    </section>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { getLogs, readStoredAuth } from '../../api/index.js'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'

const router = useRouter()

const logs = ref([])
const loading = ref(false)
const keyword = ref('')
const action = ref('')
const currentPage = ref(1)
const pageSize = ref(20)
const total = ref(0)

let timer = null
function onSearch() {
  clearTimeout(timer)
  timer = setTimeout(() => { currentPage.value = 1; loadLogs() }, 300)
}

function onFilterChange() {
  currentPage.value = 1
  loadLogs()
}

function formatTime(instant) {
  if (!instant) return '-'
  const d = new Date(instant)
  const pad = n => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

async function loadLogs() {
  if (!readStoredAuth().hasValidToken) {
    ElMessage.warning('请先登录后再访问操作日志')
    router.push('/login')
    return
  }
  loading.value = true
  try {
    const { data: res } = await getLogs({
      keyword: keyword.value,
      action: action.value,
      page: currentPage.value,
      size: pageSize.value
    })
    if (res.code == 200 || res.code === '200') {
      logs.value = res.data?.records || []
      total.value = res.data?.total || 0
    } else {
      logs.value = []
      total.value = 0
      ElMessage.error(res.message || '加载日志失败')
    }
  } catch (e) {
    logs.value = []
    total.value = 0
    const status = e.response?.status
    if (status === 401 || status === 403) {
      ElMessage.error('未登录或登录已过期，请重新登录')
      setTimeout(() => router.push('/login'), 800)
    } else {
      ElMessage.error(e.response?.data?.message || e.message || '加载日志失败')
    }
  } finally { loading.value = false }
}

onMounted(() => { loadLogs() })
</script>
