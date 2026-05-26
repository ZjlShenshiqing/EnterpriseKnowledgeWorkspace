<template>
  <div class="admin-view kb-hub" :class="{ 'kb-hub--ready': pageReady }">
    <section class="admin-page-header kb-fade-in">
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
      <article
        v-for="(item, index) in statCards"
        :key="item.label"
        class="admin-stat kb-stat-card"
        :style="{ '--delay': `${index * 0.07}s` }"
      >
        <div class="kb-stat-icon" :class="`kb-stat-icon--${item.tone}`">
          <span>{{ item.icon }}</span>
        </div>
        <div class="admin-stat-value">{{ displayStat[item.key] }}{{ item.suffix || '' }}</div>
        <div class="admin-stat-label">{{ item.label }}</div>
        <div class="admin-stat-meta">{{ item.meta }}</div>
      </article>
    </section>

    <section class="admin-grid-2">
      <div class="admin-card kb-panel" style="--delay: 0.28s">
        <div class="admin-card-head">
          <div>
            <div class="admin-section-title">知识库概览</div>
            <div class="admin-section-hint">当前已接入的知识库、向量集合和嵌入模型。</div>
          </div>
          <span class="admin-badge kb-badge-pulse">{{ bases.length }} 个知识库</span>
        </div>

        <div v-if="loading" class="kb-skeleton-list">
          <div v-for="n in 3" :key="n" class="kb-skeleton-item">
            <div class="kb-skeleton-line kb-skeleton-line--lg"></div>
            <div class="kb-skeleton-line kb-skeleton-line--sm"></div>
            <div class="kb-skeleton-bar"></div>
          </div>
        </div>

        <TransitionGroup v-else name="kb-list" tag="div" class="admin-list">
          <div v-for="(base, index) in bases" :key="base.id" class="admin-list-item kb-list-item">
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
              <div
                class="admin-progress-bar kb-progress-bar"
                :class="`kb-progress-bar--${base.tone}`"
                :style="{ width: pageReady ? `${base.health}%` : '0%' }"
              ></div>
            </div>
          </div>
        </TransitionGroup>
      </div>

      <div class="admin-card kb-panel" style="--delay: 0.36s">
        <div class="admin-card-head">
          <div>
            <div class="admin-section-title">处理状态</div>
            <div class="admin-section-hint">上传、分块和向量写入的当前状态分布。</div>
          </div>
        </div>

        <div v-if="loading" class="kb-skeleton-list">
          <div v-for="n in 4" :key="n" class="kb-skeleton-row">
            <div class="kb-skeleton-line kb-skeleton-line--md"></div>
            <div class="kb-skeleton-line kb-skeleton-line--xs"></div>
          </div>
        </div>

        <div v-else-if="statusRows.length === 0" class="kb-status-empty">
          <span>暂无文档处理记录</span>
        </div>

        <div v-else class="kb-status-list" :class="{ 'kb-status-list--ready': pageReady }">
          <div
            v-for="(item, index) in statusRows"
            :key="item.key"
            class="kb-status-row"
            :style="{ '--delay': `${0.12 + index * 0.06}s` }"
          >
            <div class="kb-status-head">
              <span class="kb-status-dot" :style="{ background: item.color }"></span>
              <span class="admin-meta-label">{{ item.label }}</span>
              <span class="kb-status-value" :style="{ color: item.color }">{{ item.display }}</span>
            </div>
            <div class="kb-status-track">
              <div
                class="kb-status-fill"
                :style="{ width: pageReady ? `${item.percent}%` : '0%', background: item.color }"
              ></div>
            </div>
          </div>
        </div>
      </div>
    </section>

    <section class="admin-table-card kb-panel kb-table-wrap" style="--delay: 0.44s">
      <div class="admin-toolbar">
        <div>
          <div class="admin-toolbar-title">最近文档</div>
          <div class="admin-toolbar-subtitle">最近上传或处理中的文档会优先显示在这里。</div>
        </div>
        <div class="admin-actions">
          <el-button plain @click="router.push('/admin/documents')">进入文档管理</el-button>
        </div>
      </div>

      <div v-if="loading" class="kb-skeleton-table">
        <div v-for="n in 5" :key="n" class="kb-skeleton-table-row"></div>
      </div>

      <Transition v-else name="kb-table" mode="out-in">
        <el-table :key="tableKey" :data="recentDocuments" class="kb-doc-table">
          <el-table-column prop="title" label="文档标题" min-width="220" />
          <el-table-column prop="knowledgeBase" label="知识库" min-width="140" />
          <el-table-column prop="fileType" label="类型" width="140" />
          <el-table-column prop="chunkCount" label="Chunk" width="90" />
          <el-table-column label="状态" width="120">
            <template #default="{ row }">
              <el-tag :type="statusType[row.status] || 'info'" effect="light" class="kb-status-tag">
                {{ row.status }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
      </Transition>
    </section>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getDocuments, getKnowledgeBases, getAuthHeaders } from '../../api'

const router = useRouter()
const bases = ref([])
const recentDocuments = ref([])
const allDocuments = ref([])
const docStats = ref({
  totalDocs: 0,
  successDocs: 0,
  pendingDocs: 0,
  runningDocs: 0,
  failedDocs: 0,
  processingDocs: 0,
  docSuccessRate: 100,
  totalChunks: 0
})

const loading = ref(true)
const pageReady = ref(false)
const tableKey = ref(0)

const displayStat = reactive({
  kbCount: 0,
  docCount: 0,
  chunks: 0,
  successRate: 0
})

const statusType = {
  SUCCESS: 'success',
  RUNNING: 'warning',
  FAILED: 'danger',
  PENDING: 'info'
}

const statCards = computed(() => {
  const totalDocs = docStats.value.totalDocs || recentDocuments.value.length
  const totalChunks = docStats.value.totalChunks || sumChunkCount(allDocuments.value.length ? allDocuments.value : recentDocuments.value)
  const running = docStats.value.processingDocs
  const successRate = docStats.value.docSuccessRate
  return [
    { key: 'kbCount', label: '知识库数量', suffix: '', icon: '📚', tone: 'blue', meta: '当前可管理的逻辑知识库', target: bases.value.length },
    { key: 'docCount', label: '文档总数', suffix: '', icon: '📄', tone: 'amber', meta: '已入库文档数量', target: totalDocs },
    { key: 'chunks', label: '累计切片', suffix: '', icon: '🧩', tone: 'purple', meta: '全库已生成分块总数', target: totalChunks },
    { key: 'successRate', label: '成功率', suffix: '%', icon: '✓', tone: 'green', meta: running ? `${running} 篇仍在处理中` : '当前无阻塞任务', target: Math.round(successRate) }
  ]
})

const statusRows = computed(() => {
  const stats = docStats.value
  const total = stats.totalDocs
  const pct = (n) => (total > 0 ? Math.max(n > 0 ? 4 : 0, Math.round(n / total * 100)) : 0)
  const rows = [
    { key: 'pending', label: '待处理文档', value: stats.pendingDocs, color: '#94a3b8' },
    { key: 'running', label: '处理中文档', value: stats.runningDocs, color: '#f59e0b' },
    { key: 'success', label: '处理成功文档', value: stats.successDocs, color: '#22c55e' },
    { key: 'failed', label: '处理失败文档', value: stats.failedDocs, color: '#ef4444' }
  ]
  return rows.map(r => ({
    ...r,
    percent: pct(r.value),
    display: r.value
  }))
})

function calcHealth(docCount, maxCount) {
  if (!maxCount) return 72
  return Math.max(42, Math.min(96, Math.round((docCount / maxCount) * 88 + 8)))
}

function animateNumber(key, target, duration = 720) {
  const start = displayStat[key] || 0
  const delta = target - start
  if (delta === 0) return
  const startTime = performance.now()
  const step = (now) => {
    const p = Math.min(1, (now - startTime) / duration)
    const eased = 1 - Math.pow(1 - p, 3)
    displayStat[key] = Math.round(start + delta * eased)
    if (p < 1) requestAnimationFrame(step)
  }
  requestAnimationFrame(step)
}

function runStatAnimations() {
  for (const card of statCards.value) {
    animateNumber(card.key, card.target)
  }
}

async function fetchJson(url) {
  const resp = await fetch(url, { headers: getAuthHeaders() })
  if (!resp.ok) throw new Error('HTTP ' + resp.status)
  const json = await resp.json()
  if (String(json.code) !== '200') throw new Error(json.message || '请求失败')
  return json.data || {}
}

function sumChunkCount(records) {
  return (records || []).reduce((sum, item) => sum + Number(item.chunkCount || 0), 0)
}

function applyDocStats(data) {
  if (!data) return
  const num = (v) => (v === undefined || v === null ? undefined : Number(v))
  const total = num(data.totalDocs)
  const success = num(data.successDocs)
  const pending = num(data.pendingDocs)
  const running = num(data.runningDocs)
  const failed = num(data.failedDocs)
  const processing = num(data.processingDocs)
  const rate = num(data.docSuccessRate)
  const chunks = num(data.totalChunks)

  if (total !== undefined) docStats.value.totalDocs = total
  if (success !== undefined) docStats.value.successDocs = success
  if (pending !== undefined) docStats.value.pendingDocs = pending
  if (running !== undefined) docStats.value.runningDocs = running
  if (failed !== undefined) docStats.value.failedDocs = failed
  if (processing !== undefined) {
    docStats.value.processingDocs = processing
  } else if (pending !== undefined || running !== undefined) {
    docStats.value.processingDocs = (pending || 0) + (running || 0)
  }
  if (rate !== undefined) docStats.value.docSuccessRate = rate
  if (chunks !== undefined) docStats.value.totalChunks = chunks
}

function deriveStatsFromDocuments(records, total) {
  const count = (status) => records.filter(item => item.status === status).length
  const successDocs = count('SUCCESS')
  const pendingDocs = count('PENDING')
  const runningDocs = count('RUNNING')
  const failedDocs = count('FAILED')
  const docTotal = Number(total) || records.length
  applyDocStats({
    totalDocs: docTotal,
    successDocs,
    pendingDocs,
    runningDocs,
    failedDocs,
    processingDocs: pendingDocs + runningDocs,
    docSuccessRate: docTotal > 0 ? Math.round(successDocs / docTotal * 1000) / 10 : 100,
    totalChunks: sumChunkCount(records)
  })
}

async function loadDocStats() {
  try {
    applyDocStats(await fetchJson('/api/kb/document-stats'))
    if (docStats.value.totalDocs > 0 && docStats.value.totalChunks > 0) return
  } catch { /* fallback */ }

  try {
    const data = await fetchJson('/api/kb/admin/stats')
    if (data.totalDocs !== undefined && data.totalDocs !== null) {
      applyDocStats(data)
      if (docStats.value.totalDocs > 0 && docStats.value.totalChunks > 0) return
    }
  } catch { /* fallback */ }

  try {
    const json = await fetchJson('/api/kb/documents?current=1&size=200')
    const records = json.records || []
    allDocuments.value = records
    if (records.length || json.total) {
      deriveStatsFromDocuments(records, json.total)
    }
  } catch { /* ignore */ }
}

async function loadData() {
  loading.value = true
  pageReady.value = false
  try {
    const [baseRes, docRes] = await Promise.all([
      getKnowledgeBases({ current: 1, size: 20 }),
      getDocuments({ current: 1, size: 8 }),
      loadDocStats()
    ])
    const records = baseRes.data.data?.records || []
    const maxDocs = Math.max(...records.map(b => Number(b.documentCount) || 0), 1)
    bases.value = records.map((item) => {
      const docCount = Number(item.documentCount) || 0
      const health = calcHealth(docCount, maxDocs)
      return {
        ...item,
        health,
        tone: health >= 85 ? 'high' : health >= 60 ? 'mid' : 'low'
      }
    })
    recentDocuments.value = (docRes.data.data?.records || []).map(item => ({
      ...item,
      knowledgeBase: item.kbName || item.knowledgeBaseName || '默认知识库'
    }))
    if (!docStats.value.totalDocs && recentDocuments.value.length) {
      deriveStatsFromDocuments(recentDocuments.value, docRes.data.data?.total)
    } else if (!docStats.value.totalChunks && allDocuments.value.length) {
      docStats.value.totalChunks = sumChunkCount(allDocuments.value)
    } else if (!docStats.value.totalChunks && recentDocuments.value.length) {
      docStats.value.totalChunks = sumChunkCount(recentDocuments.value)
    }
  } catch {
    bases.value = [
      { id: 1, name: '技术知识库', collectionName: 'kb_tech_main', embeddingModel: 'text-embedding-3-large', documentCount: 45, health: 92, tone: 'high' },
      { id: 2, name: '制度知识库', collectionName: 'kb_policy_main', embeddingModel: 'text-embedding-3-large', documentCount: 18, health: 68, tone: 'mid' },
      { id: 3, name: '客服知识库', collectionName: 'kb_support_main', embeddingModel: '', documentCount: 12, health: 52, tone: 'low' }
    ]
    recentDocuments.value = [
      { title: '微服务设计规范', knowledgeBase: '技术知识库', fileType: 'PDF', chunkCount: 28, status: 'SUCCESS' },
      { title: '报销制度 2026', knowledgeBase: '制度知识库', fileType: 'DOCX', chunkCount: 16, status: 'SUCCESS' },
      { title: '客服 SOP 手册', knowledgeBase: '客服知识库', fileType: 'PDF', chunkCount: 9, status: 'RUNNING' },
      { title: '数据库规范', knowledgeBase: '技术知识库', fileType: 'MD', chunkCount: 0, status: 'PENDING' }
    ]
    docStats.value = { totalDocs: 4, successDocs: 2, pendingDocs: 1, runningDocs: 1, failedDocs: 0, processingDocs: 2, docSuccessRate: 50, totalChunks: 53 }
  } finally {
    loading.value = false
    tableKey.value += 1
    await nextTick()
    setTimeout(() => {
      pageReady.value = true
      runStatAnimations()
    }, 80)
  }
}

onMounted(loadData)
</script>

<style scoped>
.kb-hub--ready .kb-panel {
  animation: kb-panel-in 0.55s cubic-bezier(0.22, 1, 0.36, 1) both;
  animation-delay: var(--delay, 0s);
}

.kb-fade-in {
  animation: kb-fade-in 0.45s ease both;
}

.kb-stat-card {
  position: relative;
  overflow: hidden;
  opacity: 0;
  transform: translateY(14px);
  animation: kb-rise 0.55s cubic-bezier(0.22, 1, 0.36, 1) forwards;
  animation-delay: var(--delay, 0s);
  transition: transform 0.25s ease, box-shadow 0.25s ease;
}

.kb-stat-card:hover {
  transform: translateY(-3px);
  box-shadow: 0 10px 28px rgba(20, 35, 70, 0.08);
}

.kb-stat-icon {
  width: 36px;
  height: 36px;
  border-radius: 12px;
  display: grid;
  place-items: center;
  font-size: 16px;
  margin-bottom: 12px;
}

.kb-stat-icon--blue { background: rgba(59, 130, 246, 0.12); }
.kb-stat-icon--amber { background: rgba(245, 158, 11, 0.14); }
.kb-stat-icon--purple { background: rgba(139, 92, 246, 0.12); }
.kb-stat-icon--green { background: rgba(34, 197, 94, 0.12); }

.kb-badge-pulse {
  animation: kb-badge-pulse 2.4s ease-in-out infinite;
}

.kb-progress-bar {
  transition: width 0.9s cubic-bezier(0.22, 1, 0.36, 1);
}

.kb-progress-bar--high {
  background: linear-gradient(90deg, #22c55e, #4ade80);
}

.kb-progress-bar--mid {
  background: linear-gradient(90deg, #3b82f6, #6366f1);
}

.kb-progress-bar--low {
  background: linear-gradient(90deg, #f59e0b, #fbbf24);
}

.kb-list-item {
  transition: transform 0.2s ease, border-color 0.2s ease;
}

.kb-list-item:hover {
  transform: translateX(4px);
  border-color: #cfd8ea;
}

.kb-status-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.kb-status-row {
  opacity: 1;
  transform: translateX(0);
}

.kb-status-list--ready .kb-status-row {
  animation: kb-slide-in 0.45s ease both;
  animation-delay: var(--delay, 0s);
}

.kb-status-empty {
  padding: 28px 0;
  text-align: center;
  color: #8a96ae;
  font-size: 14px;
}

.kb-status-head {
  display: grid;
  grid-template-columns: 8px 1fr auto;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}

.kb-status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

.kb-status-value {
  font-size: 15px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}

.kb-status-track {
  height: 6px;
  border-radius: 999px;
  background: #ebf0f7;
  overflow: hidden;
}

.kb-status-fill {
  height: 100%;
  border-radius: inherit;
  transition: width 0.85s cubic-bezier(0.22, 1, 0.36, 1);
}

.kb-skeleton-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.kb-skeleton-item,
.kb-skeleton-row {
  padding: 14px 16px;
  border-radius: 16px;
  background: #f3f6fb;
  border: 1px solid #e8edf5;
}

.kb-skeleton-line {
  height: 10px;
  border-radius: 999px;
  background: linear-gradient(90deg, #e8edf5 25%, #f5f8fc 50%, #e8edf5 75%);
  background-size: 200% 100%;
  animation: kb-shimmer 1.2s ease-in-out infinite;
}

.kb-skeleton-line--lg { width: 55%; margin-bottom: 10px; height: 12px; }
.kb-skeleton-line--sm { width: 78%; margin-bottom: 12px; }
.kb-skeleton-line--md { width: 42%; }
.kb-skeleton-line--xs { width: 18%; margin-left: auto; margin-top: -10px; }

.kb-skeleton-bar {
  height: 8px;
  border-radius: 999px;
  background: linear-gradient(90deg, #e8edf5 25%, #f5f8fc 50%, #e8edf5 75%);
  background-size: 200% 100%;
  animation: kb-shimmer 1.2s ease-in-out infinite;
}

.kb-skeleton-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.kb-skeleton-table {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.kb-skeleton-table-row {
  height: 44px;
  border-radius: 10px;
  background: linear-gradient(90deg, #eef2f8 25%, #f8fafc 50%, #eef2f8 75%);
  background-size: 200% 100%;
  animation: kb-shimmer 1.2s ease-in-out infinite;
}

.kb-table-wrap :deep(.el-table__row) {
  transition: background-color 0.2s ease;
}

.kb-status-tag {
  transition: transform 0.2s ease;
}

.kb-doc-table :deep(.el-table__body tr:hover .kb-status-tag) {
  transform: scale(1.04);
}

.kb-list-enter-active,
.kb-list-leave-active {
  transition: all 0.35s ease;
}

.kb-list-enter-from,
.kb-list-leave-to {
  opacity: 0;
  transform: translateY(10px);
}

.kb-table-enter-active,
.kb-table-leave-active {
  transition: opacity 0.35s ease, transform 0.35s ease;
}

.kb-table-enter-from,
.kb-table-leave-to {
  opacity: 0;
  transform: translateY(8px);
}

@keyframes kb-rise {
  to { opacity: 1; transform: translateY(0); }
}

@keyframes kb-panel-in {
  from { opacity: 0; transform: translateY(16px); }
  to { opacity: 1; transform: translateY(0); }
}

@keyframes kb-fade-in {
  from { opacity: 0; transform: translateY(-6px); }
  to { opacity: 1; transform: translateY(0); }
}

@keyframes kb-slide-in {
  from { opacity: 0; transform: translateX(8px); }
  to { opacity: 1; transform: translateX(0); }
}

@keyframes kb-shimmer {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}

@keyframes kb-badge-pulse {
  0%, 100% { box-shadow: 0 0 0 0 rgba(41, 95, 218, 0); }
  50% { box-shadow: 0 0 0 6px rgba(41, 95, 218, 0.08); }
}
</style>
