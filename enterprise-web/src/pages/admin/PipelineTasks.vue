<template>
  <div class="admin-view">
    <section class="admin-page-header">
      <div>
        <div class="admin-page-title">流水线任务</div>
        <div class="admin-page-subtitle">查看当前执行中的异步任务、重试情况和失败原因。</div>
      </div>
      <div class="admin-actions">
        <el-button plain @click="fetchTasks(pagination.current)">刷新队列</el-button>
      </div>
    </section>

    <section class="admin-table-card">
      <div class="admin-toolbar">
        <div>
          <div class="admin-toolbar-title">任务队列</div>
          <div class="admin-toolbar-subtitle">文档分块、向量同步等异步处理任务。</div>
        </div>
        <div class="admin-filters">
          <span class="admin-chip" :class="{ active: activeStatus === '' }" @click="clearFilter">全部</span>
          <span class="admin-chip" :class="{ active: activeStatus === 'RUNNING' }" @click="filterByStatus('RUNNING')">进行中</span>
          <span class="admin-chip" :class="{ active: activeStatus === 'FAILED' }" @click="filterByStatus('FAILED')">失败</span>
          <span class="admin-chip" :class="{ active: activeStatus === 'SUCCESS' }" @click="filterByStatus('SUCCESS')">成功</span>
        </div>
      </div>
      <el-table :data="tasks" v-loading="loading" style="width: 100%">
        <el-table-column prop="taskId" label="任务 ID" width="200" />
        <el-table-column prop="type" label="任务类型" min-width="120" />
        <el-table-column prop="documentName" label="关联文档" min-width="160" />
        <el-table-column prop="pipelineName" label="所属流水线" min-width="180" />
        <el-table-column prop="progress" label="进度" width="100" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="updatedAt" label="最近更新" width="180" />
      </el-table>
      <div style="margin-top: 16px; display: flex; justify-content: flex-end;">
        <el-pagination
          v-model:current-page="pagination.current"
          :page-size="pagination.size"
          :total="pagination.total"
          layout="prev, pager, next"
          @current-change="onPageChange"
        />
      </div>
    </section>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getPipelineTasks } from '../../api'

const tasks = ref([])
const loading = ref(false)
const pagination = reactive({ current: 1, size: 20, total: 0 })
const activeStatus = ref('')

const fetchTasks = async (page = 1) => {
  loading.value = true
  try {
    const params = { current: page, size: pagination.size }
    if (activeStatus.value) {
      params.status = activeStatus.value
    }
    const res = await getPipelineTasks(params)
    const body = res.data || {}
    const data = body.data || body
    tasks.value = data.records || []
    pagination.current = data.current || page
    pagination.total = data.total || 0
  } catch (e) {
    ElMessage.error('获取任务列表失败')
  } finally {
    loading.value = false
  }
}

const filterByStatus = (status) => {
  activeStatus.value = status
  fetchTasks(1)
}

const clearFilter = () => {
  activeStatus.value = ''
  fetchTasks(1)
}

const statusTagType = (status) => {
  if (status === 'SUCCESS') return 'success'
  if (status === 'RUNNING') return 'warning'
  if (status === 'FAILED') return 'danger'
  return 'info'
}

const onPageChange = (page) => {
  fetchTasks(page)
}

onMounted(() => fetchTasks())
</script>
