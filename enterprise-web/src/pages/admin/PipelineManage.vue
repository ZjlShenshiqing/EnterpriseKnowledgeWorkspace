<template>
  <div class="admin-view" v-loading="loading">
    <section class="admin-page-header">
      <div>
        <div class="admin-page-title">流水线管理</div>
        <div class="admin-page-subtitle">管理数据接入、解析、分块、向量写入等处理通道。</div>
      </div>
      <div class="admin-actions">
        <el-button plain @click="fetchPipelines">刷新</el-button>
        <el-button type="primary" @click="router.push('/admin/kb/bases')">知识库管理</el-button>
      </div>
    </section>

    <el-empty v-if="!loading && pipelines.length === 0" description="暂无流水线，请先创建知识库" />

    <section v-else class="admin-grid-3">
      <article v-for="pipeline in pipelines" :key="pipeline.id" class="admin-card">
        <div class="admin-card-head">
          <div>
            <div class="admin-section-title">{{ pipeline.name }}</div>
            <div class="admin-section-hint">{{ pipeline.description }}</div>
          </div>
          <el-tag :type="statusType(pipeline.status)" size="small">{{ statusLabel(pipeline.status) }}</el-tag>
        </div>
        <div class="admin-meta-list" style="margin-top: 18px;">
          <div class="admin-meta-row">
            <span class="admin-meta-label">分块策略</span>
            <span class="admin-meta-value">{{ chunkStrategyLabel(pipeline.chunkStrategy) }}</span>
          </div>
          <div class="admin-meta-row">
            <span class="admin-meta-label">向量写入</span>
            <span class="admin-meta-value">{{ pipeline.vectorEnabled ? '已启用' : '未启用' }}</span>
          </div>
          <div class="admin-meta-row">
            <span class="admin-meta-label">嵌入模型</span>
            <span class="admin-meta-value">{{ pipeline.embeddingModel || '全局默认' }}</span>
          </div>
          <div class="admin-meta-row">
            <span class="admin-meta-label">处理阶段</span>
            <span class="admin-meta-value">{{ stageLabel(pipeline.stages) }}</span>
          </div>
        </div>
      </article>
    </section>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getPipelines, getAuthHeaders } from '../../api'

const router = useRouter()
const pipelines = ref([])
const loading = ref(false)

const fetchPipelines = async () => {
  loading.value = true
  try {
    const res = await getPipelines()
    const body = res.data
    pipelines.value = (body && body.data) ? body.data : (Array.isArray(body) ? body : [])
  } catch (e) {
    ElMessage.error('获取流水线列表失败')
  } finally {
    loading.value = false
  }
}

const chunkStrategyLabel = (strategy) => {
  if (!strategy) return '—'
  if (strategy === 'PARAGRAPH') return '按段落'
  if (strategy === 'FIXED_SIZE') return '固定长度'
  return strategy
}

const stageLabel = (stages) => {
  if (!stages || stages.length === 0) return '0 个'
  return stages.length + ' 个'
}

const statusType = (status) => {
  if (status === 'ACTIVE') return 'success'
  return 'info'
}

const statusLabel = (status) => {
  if (status === 'ACTIVE') return '运行中'
  return status
}

onMounted(fetchPipelines)
</script>
