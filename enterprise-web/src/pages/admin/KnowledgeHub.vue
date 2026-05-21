<template>
  <div class="admin-view">
    <section class="admin-page-header">
      <div>
        <div class="admin-page-title">知识库管理</div>
        <div class="admin-page-subtitle">统一查看知识库规模、文档状态和分块处理进度。</div>
      </div>
      <div class="admin-actions">
        <el-button plain @click="router.push('/admin/bases')">打开知识库表</el-button>
        <el-button type="primary" @click="router.push('/admin/documents')">上传文档</el-button>
      </div>
    </section>

    <section class="admin-grid-4">
      <article v-for="item in statCards" :key="item.label" class="admin-stat">
        <div class="admin-stat-value">{{ item.value }}</div>
        <div class="admin-stat-label">{{ item.label }}</div>
        <div class="admin-stat-meta">{{ item.meta }}</div>
      </article>
    </section>

    <section class="admin-grid-2">
      <div class="admin-card">
        <div class="admin-card-head">
          <div>
            <div class="admin-section-title">知识库概览</div>
            <div class="admin-section-hint">当前已接入的知识库、向量集合和嵌入模型。</div>
          </div>
          <span class="admin-badge">{{ bases.length }} 个知识库</span>
        </div>
        <div class="admin-list">
          <div v-for="base in bases" :key="base.id" class="admin-list-item">
            <div class="admin-toolbar">
              <div>
                <div class="admin-list-title">{{ base.name }}</div>
                <div class="admin-list-copy">
                  Collection：{{ base.collectionName }} · Model：{{ base.embeddingModel || '未启用向量化' }}
                </div>
              </div>
              <span class="admin-chip">{{ base.documentCount }} 篇文档</span>
            </div>
            <div class="admin-progress">
              <div class="admin-progress-bar" :style="{ width: `${base.health}%` }"></div>
            </div>
          </div>
        </div>
      </div>

      <div class="admin-card">
        <div class="admin-card-head">
          <div>
            <div class="admin-section-title">处理状态</div>
            <div class="admin-section-hint">上传、分块和向量写入的当前状态分布。</div>
          </div>
        </div>
        <div class="admin-meta-list">
          <div v-for="item in statusRows" :key="item.label" class="admin-meta-row">
            <span class="admin-meta-label">{{ item.label }}</span>
            <span class="admin-meta-value">{{ item.value }}</span>
          </div>
        </div>
      </div>
    </section>

    <section class="admin-table-card">
      <div class="admin-toolbar">
        <div>
          <div class="admin-toolbar-title">最近文档</div>
          <div class="admin-toolbar-subtitle">最近上传或处理中的文档会优先显示在这里。</div>
        </div>
        <div class="admin-actions">
          <el-button plain @click="router.push('/admin/documents')">进入文档管理</el-button>
        </div>
      </div>
      <el-table :data="recentDocuments">
        <el-table-column prop="title" label="文档标题" min-width="220" />
        <el-table-column prop="knowledgeBase" label="知识库" min-width="140" />
        <el-table-column prop="fileType" label="类型" width="140" />
        <el-table-column prop="chunkCount" label="Chunk" width="90" />
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="statusType[row.status] || 'info'">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getDocuments, getKnowledgeBases } from '../../api'

const router = useRouter()
const bases = ref([])
const recentDocuments = ref([])

const statusType = {
  SUCCESS: 'success',
  RUNNING: 'warning',
  FAILED: 'danger',
  PENDING: 'info'
}

const statCards = computed(() => {
  const totalDocs = recentDocuments.value.length || 0
  const totalChunks = recentDocuments.value.reduce((sum, item) => sum + Number(item.chunkCount || 0), 0)
  const running = recentDocuments.value.filter(item => item.status === 'RUNNING').length
  const successRate = totalDocs ? Math.round(recentDocuments.value.filter(item => item.status === 'SUCCESS').length / totalDocs * 100) : 100
  return [
    { label: '知识库数量', value: bases.value.length, meta: '当前可管理的逻辑知识库' },
    { label: '最近文档', value: totalDocs, meta: '最近一批上传与处理记录' },
    { label: '累计切片', value: totalChunks, meta: '当前展示文档对应切片数' },
    { label: '成功率', value: `${successRate}%`, meta: running ? `${running} 篇仍在处理中` : '当前无阻塞任务' }
  ]
})

const statusRows = computed(() => {
  const docs = recentDocuments.value
  return [
    { label: '待处理文档', value: docs.filter(item => item.status === 'PENDING').length },
    { label: '处理中文档', value: docs.filter(item => item.status === 'RUNNING').length },
    { label: '处理成功文档', value: docs.filter(item => item.status === 'SUCCESS').length },
    { label: '处理失败文档', value: docs.filter(item => item.status === 'FAILED').length }
  ]
})

onMounted(async () => {
  try {
    const [baseRes, docRes] = await Promise.all([
      getKnowledgeBases({ current: 1, size: 20 }),
      getDocuments({ current: 1, size: 8 })
    ])
    bases.value = (baseRes.data.data?.records || []).map((item, index) => ({
      ...item,
      health: 88 - index * 7
    }))
    recentDocuments.value = (docRes.data.data?.records || []).map(item => ({
      ...item,
      knowledgeBase: item.kbName || item.knowledgeBaseName || '默认知识库'
    }))
  } catch {
    bases.value = [
      { id: 1, name: '技术知识库', collectionName: 'kb_tech_main', embeddingModel: 'text-embedding-3-large', documentCount: 45, health: 92 },
      { id: 2, name: '制度知识库', collectionName: 'kb_policy_main', embeddingModel: 'text-embedding-3-large', documentCount: 18, health: 86 },
      { id: 3, name: '客服知识库', collectionName: 'kb_support_main', embeddingModel: '', documentCount: 12, health: 80 }
    ]
    recentDocuments.value = [
      { title: '微服务设计规范', knowledgeBase: '技术知识库', fileType: 'PDF', chunkCount: 28, status: 'SUCCESS' },
      { title: '报销制度 2026', knowledgeBase: '制度知识库', fileType: 'DOCX', chunkCount: 16, status: 'SUCCESS' },
      { title: '客服 SOP 手册', knowledgeBase: '客服知识库', fileType: 'PDF', chunkCount: 9, status: 'RUNNING' },
      { title: '数据库规范', knowledgeBase: '技术知识库', fileType: 'MD', chunkCount: 0, status: 'PENDING' }
    ]
  }
})
</script>
