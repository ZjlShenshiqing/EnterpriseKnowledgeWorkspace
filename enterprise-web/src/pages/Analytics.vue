<template>
  <div class="console-page">
    <div class="console-main">
      <section class="console-panel">
        <div class="panel-title">核心指标</div>
        <div class="metric-grid">
          <article v-for="card in metricCards" :key="card.label" class="metric-card">
            <div class="metric-card-top">
              <div class="metric-value">{{ card.value }}</div>
              <div class="metric-icon" :style="{ background: card.tint }">
                <el-icon :style="{ color: card.color }"><component :is="card.icon" /></el-icon>
              </div>
            </div>
            <div class="metric-label">{{ card.label }}</div>
            <div class="metric-foot">
              <span>{{ card.hint }}</span>
            </div>
          </article>
        </div>
      </section>

      <section class="console-panel">
        <div class="panel-title">文档与知识库</div>
        <div class="trend-grid">
          <article class="trend-card">
            <div class="trend-card-header">
              <div class="trend-title">文档处理</div>
              <div class="trend-dot green">文档</div>
            </div>
            <div class="kv-grid">
              <div class="kv-item"><span class="kv-label">文档总数</span><span class="kv-value">{{ docStats.totalDocs }}</span></div>
              <div class="kv-item"><span class="kv-label">处理成功</span><span class="kv-value green">{{ docStats.successDocs }}</span></div>
              <div class="kv-item"><span class="kv-label">处理中</span><span class="kv-value warn">{{ docStats.processingDocs }}</span></div>
              <div class="kv-item"><span class="kv-label">处理失败</span><span class="kv-value red">{{ docStats.failedDocs }}</span></div>
              <div class="kv-item"><span class="kv-label">知识库数</span><span class="kv-value">{{ docStats.kbCount }}</span></div>
            </div>
          </article>

          <article class="trend-card">
            <div class="trend-card-header">
              <div class="trend-title">AI 对话</div>
              <div class="trend-dot purple">会话</div>
            </div>
            <div class="kv-grid">
              <div class="kv-item"><span class="kv-label">总会话</span><span class="kv-value">{{ aiStats.sessionCount }}</span></div>
              <div class="kv-item"><span class="kv-label">总消息</span><span class="kv-value">{{ aiStats.messageCount }}</span></div>
              <div class="kv-item"><span class="kv-label">条/会话</span><span class="kv-value">{{ aiStats.avgMessagesPerSession }}</span></div>
              <div class="kv-item"><span class="kv-label">Token</span><span class="kv-value">{{ formatTokens(aiStats.totalTokens) }}</span></div>
            </div>
          </article>
        </div>
      </section>

      <section class="console-panel">
        <div class="panel-title">协作概览</div>
        <div class="trend-grid">
          <article class="trend-card">
            <div class="trend-card-header">
              <div class="trend-title">任务分布</div>
              <div class="trend-dot green">任务</div>
            </div>
            <div class="kv-grid">
              <div class="kv-item"><span class="kv-label">待办</span><span class="kv-value">{{ collabStats.taskTodo }}</span></div>
              <div class="kv-item"><span class="kv-label">进行中</span><span class="kv-value warn">{{ collabStats.taskInProgress }}</span></div>
              <div class="kv-item"><span class="kv-label">审核中</span><span class="kv-value">{{ collabStats.taskReview }}</span></div>
              <div class="kv-item"><span class="kv-label">已完成</span><span class="kv-value green">{{ collabStats.taskDone }}</span></div>
            </div>
          </article>

          <article class="trend-card">
            <div class="trend-card-header">
              <div class="trend-title">审批与会议</div>
              <div class="trend-dot purple">审批</div>
            </div>
            <div class="kv-grid">
              <div class="kv-item"><span class="kv-label">待审批</span><span class="kv-value warn">{{ collabStats.approvalPending }}</span></div>
              <div class="kv-item"><span class="kv-label">已通过</span><span class="kv-value green">{{ collabStats.approvalApproved }}</span></div>
              <div class="kv-item"><span class="kv-label">今日会议</span><span class="kv-value">{{ collabStats.meetingToday }}</span></div>
              <div class="kv-item"><span class="kv-label">会议总数</span><span class="kv-value">{{ collabStats.meetingTotal }}</span></div>
            </div>
          </article>
        </div>
      </section>
    </div>

    <aside class="console-side">
      <section class="console-side-panel">
        <div class="side-header">
          <div class="panel-title side">AI 性能</div>
          <span class="status-badge" :class="aiHealthClass">{{ aiHealthText }}</span>
        </div>

        <div class="success-ring-wrap">
          <div class="success-ring-card">
            <svg class="success-ring-svg" viewBox="0 0 120 120" aria-hidden="true">
              <defs>
                <linearGradient :id="ringGradientId" x1="0%" y1="0%" x2="100%" y2="100%">
                  <stop offset="0%" :stop-color="ringGradient.start" />
                  <stop offset="100%" :stop-color="ringGradient.end" />
                </linearGradient>
              </defs>
              <circle class="ring-track" cx="60" cy="60" r="52" />
              <circle
                class="ring-progress"
                cx="60"
                cy="60"
                r="52"
                :stroke="`url(#${ringGradientId})`"
                :stroke-dasharray="ringDasharray"
                transform="rotate(-90 60 60)"
              />
            </svg>
            <div class="success-ring-center">
              <div class="success-ring-value" :style="{ color: ringTheme.main }">{{ formattedDocRate }}<span class="success-ring-unit">%</span></div>
              <div class="success-ring-label">文档成功率</div>
              <div class="success-ring-sub">{{ docStats.successDocs }} / {{ docStats.totalDocs }} 已成功</div>
            </div>
          </div>
        </div>

        <div class="metric-list">
          <div class="metric-row">
            <span>平均响应</span>
            <strong :class="aiStats.avgDurationMs > 5000 ? 'danger' : 'warn'">{{ formatMs(aiStats.avgDurationMs) }}</strong>
          </div>
          <div class="metric-row">
            <span>P95 响应</span>
            <strong :class="aiStats.p95DurationMs > 10000 ? 'danger' : 'warn'">{{ formatMs(aiStats.p95DurationMs) }}</strong>
          </div>
        </div>

        <div class="quality-box">
          <div class="quality-title-row">
            <span class="quality-title">文档处理</span>
            <span class="quality-time">共 {{ docStats.totalDocs }} 篇</span>
          </div>

          <div v-if="docStats.totalDocs > 0" class="doc-stack-bar">
            <div
              v-if="docDistribution.success > 0"
              class="doc-stack-seg doc-stack-seg--success"
              :style="{ width: docDistribution.success + '%' }"
            ></div>
            <div
              v-if="docDistribution.processing > 0"
              class="doc-stack-seg doc-stack-seg--processing"
              :style="{ width: docDistribution.processing + '%' }"
            ></div>
            <div
              v-if="docDistribution.failed > 0"
              class="doc-stack-seg doc-stack-seg--failed"
              :style="{ width: docDistribution.failed + '%' }"
            ></div>
          </div>
          <div v-else class="doc-stack-empty">暂无文档</div>

          <div class="doc-status-list">
            <div v-for="row in docStatusRows" :key="row.key" class="doc-status-row">
              <div class="doc-status-head">
                <span class="doc-status-dot" :style="{ background: row.color }"></span>
                <span class="doc-status-label">{{ row.label }}</span>
                <span class="doc-status-count" :style="{ color: row.color }">{{ row.value }}</span>
                <span class="doc-status-pct">{{ row.percent }}%</span>
              </div>
              <div class="doc-status-track">
                <div
                  class="doc-status-fill"
                  :style="{ width: row.percent + '%', background: row.color }"
                ></div>
              </div>
            </div>
          </div>
        </div>

        <div class="efficiency-box">
          <div class="quality-title-row">
            <span class="quality-title">运营数据</span>
            <span class="quality-time">当前</span>
          </div>
          <div class="metric-row compact">
            <span>待办任务</span>
            <strong>{{ collabStats.taskTodo }}</strong>
          </div>
          <div class="metric-row compact">
            <span>进行中任务</span>
            <strong>{{ collabStats.taskInProgress }}</strong>
          </div>
          <div class="metric-row compact">
            <span>用户总数</span>
            <strong>{{ userStats.total }}</strong>
          </div>
          <div class="metric-row compact">
            <span>管理员数</span>
            <strong>{{ userStats.admin }}</strong>
          </div>
        </div>
      </section>
    </aside>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ChatLineSquare, Document, User, DataBoard } from '@element-plus/icons-vue'
import { getAuthHeaders } from '../api/index.js'

const userStats = ref({ total: 0, enabled: 0, admin: 0, disabled: 0 })
const aiStats = ref({ successRate: 0, avgDurationMs: 0, p95DurationMs: 0, sessionCount: 0, messageCount: 0, avgMessagesPerSession: 0, totalTokens: 0 })
const collabStats = ref({ taskTodo: 0, taskInProgress: 0, taskReview: 0, taskDone: 0, approvalPending: 0, approvalApproved: 0, meetingToday: 0, meetingTotal: 0 })
const docStats = ref({
  totalDocs: 0,
  successDocs: 0,
  pendingDocs: 0,
  runningDocs: 0,
  failedDocs: 0,
  processingDocs: 0,
  docSuccessRate: 0,
  kbCount: 0
})

const aiHealthClass = computed(() => docStats.value.docSuccessRate >= 95 ? '' : 'warn')
const aiHealthText = computed(() => docStats.value.docSuccessRate >= 95 ? '运行正常' : '需关注')

const RING_RADIUS = 52
const RING_CIRCUMFERENCE = 2 * Math.PI * RING_RADIUS
const ringGradientId = 'doc-success-ring-grad'

const formattedDocRate = computed(() => {
  const rate = docStats.value.docSuccessRate || 0
  return Number.isInteger(rate) ? rate : Number(rate).toFixed(1)
})

const ringTheme = computed(() => {
  const rate = docStats.value.docSuccessRate || 0
  if (rate >= 95) {
    return { main: '#16a34a', start: '#4ade80', end: '#16a34a', glow: 'rgba(34, 197, 94, 0.35)' }
  }
  if (rate >= 80) {
    return { main: '#d97706', start: '#fbbf24', end: '#f59e0b', glow: 'rgba(245, 158, 11, 0.35)' }
  }
  return { main: '#dc2626', start: '#f87171', end: '#ef4444', glow: 'rgba(239, 68, 68, 0.35)' }
})

const ringGradient = computed(() => ({
  start: ringTheme.value.start,
  end: ringTheme.value.end
}))

const ringDasharray = computed(() => {
  const rate = Math.min(100, Math.max(0, docStats.value.docSuccessRate || 0))
  const filled = (rate / 100) * RING_CIRCUMFERENCE
  return `${filled} ${RING_CIRCUMFERENCE - filled}`
})

const metricCards = computed(() => [
  { label: '用户总数', value: userStats.value.total, icon: User, color: '#356dff', tint: 'rgba(83,127,255,0.18)', hint: `启用 ${userStats.value.enabled} · 管理员 ${userStats.value.admin}` },
  { label: 'AI 会话数', value: aiStats.value.sessionCount, icon: ChatLineSquare, color: '#6658ff', tint: 'rgba(129,115,255,0.18)', hint: `${aiStats.value.messageCount} 条消息` },
  { label: '文档数', value: docStats.value.totalDocs, icon: Document, color: '#f0a31b', tint: 'rgba(255,213,94,0.22)', hint: `${docStats.value.kbCount} 个知识库` },
  { label: '会议数', value: collabStats.value.meetingTotal, icon: DataBoard, color: '#2f9bff', tint: 'rgba(93,177,255,0.2)', hint: `今日 ${collabStats.value.meetingToday} 场` }
])

const docDistribution = computed(() => {
  const total = docStats.value.totalDocs || 0
  if (total <= 0) {
    return { success: 0, processing: 0, failed: 0 }
  }
  const pct = (n) => Math.round(n / total * 1000) / 10
  return {
    success: pct(docStats.value.successDocs),
    processing: pct(docStats.value.processingDocs),
    failed: pct(docStats.value.failedDocs)
  }
})

const docStatusRows = computed(() => {
  const total = docStats.value.totalDocs || 0
  const pct = (n) => (total > 0 ? Math.round(n / total * 100) : 0)
  return [
    { key: 'success', label: '处理成功', value: docStats.value.successDocs, color: '#22c55e', percent: pct(docStats.value.successDocs) },
    { key: 'processing', label: '处理中', value: docStats.value.processingDocs, color: '#f59e0b', percent: pct(docStats.value.processingDocs) },
    { key: 'failed', label: '处理失败', value: docStats.value.failedDocs, color: '#ef4444', percent: pct(docStats.value.failedDocs) }
  ]
})

function formatMs(ms) {
  if (!ms || ms === 0) return '--'
  if (ms < 1000) return ms + 'ms'
  return (ms / 1000).toFixed(1) + 's'
}

function formatTokens(n) {
  if (!n) return '0'
  if (n >= 1000000) return (n / 1000000).toFixed(2) + 'M'
  if (n >= 1000) return (n / 1000).toFixed(1) + 'K'
  return String(n)
}

async function fetchJson(url) {
  const resp = await fetch(url, { headers: getAuthHeaders() })
  if (!resp.ok) throw new Error('HTTP ' + resp.status)
  const json = await resp.json()
  if (String(json.code) !== '200') throw new Error(json.message || '请求失败')
  return json.data || {}
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
}

async function loadDocStats() {
  try {
    const data = await fetchJson('/api/kb/document-stats')
    applyDocStats(data)
    return
  } catch { /* fallback below */ }

  try {
    const data = await fetchJson('/api/kb/admin/stats')
    if (data.totalDocs !== undefined && data.totalDocs !== null) {
      applyDocStats(data)
      return
    }
  } catch { /* fallback below */ }

  try {
    const resp = await fetch('/api/kb/documents?current=1&size=200', { headers: getAuthHeaders() })
    const json = await resp.json()
    if (String(json.code) !== '200' || !json.data) return
    const records = json.data.records || []
    const total = Number(json.data.total) || records.length
    const count = (s) => records.filter(d => d.status === s).length
    const successDocs = count('SUCCESS')
    const pendingDocs = count('PENDING')
    const runningDocs = count('RUNNING')
    const failedDocs = count('FAILED')
    applyDocStats({
      totalDocs: total,
      successDocs,
      pendingDocs,
      runningDocs,
      failedDocs,
      processingDocs: pendingDocs + runningDocs,
      docSuccessRate: total > 0 ? Math.round(successDocs / total * 1000) / 10 : 100
    })
  } catch { /* ignore */ }
}

onMounted(async () => {
  try {
    userStats.value = await fetchJson('/api/system/users/stats')
  } catch { /* ignore */ }

  try {
    const data = await fetchJson('/api/kb/admin/stats')
    aiStats.value = data
  } catch { /* ignore */ }

  await loadDocStats()

  try {
    const data = await fetchJson('/api/workbench/stats')
    if (data.taskStats) {
      collabStats.value.taskTodo = data.taskStats.todo || 0
      collabStats.value.taskInProgress = data.taskStats.inProgress || 0
      collabStats.value.taskReview = data.taskStats.review || 0
      collabStats.value.taskDone = data.taskStats.done || 0
    }
    if (data.approvalStats) {
      collabStats.value.approvalPending = data.approvalStats.pending || 0
      collabStats.value.approvalApproved = data.approvalStats.approved || 0
    }
    if (data.meetingStats) {
      collabStats.value.meetingToday = data.meetingStats.today || 0
      collabStats.value.meetingTotal = data.meetingStats.total || 0
    }
    if (!docStats.value.totalDocs) {
      docStats.value.totalDocs = data.docCount || 0
    }
  } catch { /* ignore */ }

  try {
    const json = await fetch('/api/kb/bases?current=1&size=1', { headers: getAuthHeaders() }).then(r => r.json())
    if (String(json.code) === '200') {
      docStats.value.kbCount = json.data?.total || 0
    }
  } catch { /* ignore */ }
})
</script>

<style scoped>
.console-page { display: grid; grid-template-columns: minmax(0, 1fr) 342px; gap: 26px; }
.console-main, .console-side { min-width: 0; }
.console-main { display: flex; flex-direction: column; gap: 22px; }
.console-panel, .console-side-panel { background: rgba(255,255,255,0.92); border: 1px solid rgba(220,226,238,0.9); border-radius: 16px; padding: 24px; box-shadow: 0 4px 16px rgba(15,23,42,0.04); }
.console-side-panel { position: sticky; top: 12px; }
.panel-title { font-size: 15px; font-weight: 700; color: #18202f; margin-bottom: 18px; }
.panel-title.side { margin-bottom: 0; }
.metric-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 16px; }
.metric-card { padding: 18px; border-radius: 12px; background: linear-gradient(180deg, #f8fbff 0%, #f4f7fb 100%); border: 1px solid #edf2fa; }
.metric-card-top { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 10px; }
.metric-value { font-size: 28px; font-weight: 800; color: #151d2d; line-height: 1; }
.metric-icon { width: 44px; height: 44px; border-radius: 12px; display: grid; place-items: center; font-size: 20px; }
.metric-label { color: #516076; font-size: 15px; margin-bottom: 10px; }
.metric-foot { display: flex; align-items: center; gap: 8px; color: #98a1b3; font-size: 13px; }

.trend-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 18px; }
.trend-card { border-radius: 12px; background: linear-gradient(180deg, #f8fbff 0%, #f4f7fb 100%); border: 1px solid #eef2f8; padding: 18px; }
.trend-card-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.trend-title { font-size: 15px; font-weight: 700; color: #1d2433; }
.trend-dot { display: inline-flex; align-items: center; gap: 6px; color: #1e2432; font-size: 13px; }
.trend-dot::before { content: ''; width: 10px; height: 10px; border-radius: 50%; }
.trend-dot.green::before { background: #22c55e; }
.trend-dot.purple::before { background: #7b5cff; }

.kv-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 14px; }
.kv-item { display: flex; flex-direction: column; gap: 4px; }
.kv-label { font-size: 12px; color: #8b95aa; }
.kv-value { font-size: 22px; font-weight: 700; color: #1d2433; }
.kv-value.green { color: #22c55e; }
.kv-value.red { color: #ef4444; }
.kv-value.warn { color: #f59e0b; }

.side-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.status-badge { padding: 6px 12px; border-radius: 999px; background: #def8e7; color: #179b54; font-size: 13px; font-weight: 600; }
.status-badge.warn { background: #fff3cd; color: #b45309; }

.success-ring-wrap { display: flex; justify-content: center; margin: 4px 0 20px; }
.success-ring-card {
  position: relative;
  width: 148px;
  height: 148px;
  display: grid;
  place-items: center;
}
.success-ring-svg {
  width: 148px;
  height: 148px;
  filter: drop-shadow(0 4px 12px rgba(34, 197, 94, 0.12));
}
.ring-track {
  fill: none;
  stroke: #eef2f7;
  stroke-width: 10;
}
.ring-progress {
  fill: none;
  stroke-width: 10;
  stroke-linecap: round;
  transition: stroke-dasharray 0.6s cubic-bezier(0.4, 0, 0.2, 1);
}
.success-ring-center {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  pointer-events: none;
}
.success-ring-value {
  font-size: 32px;
  font-weight: 800;
  line-height: 1;
  letter-spacing: -0.02em;
  font-variant-numeric: tabular-nums;
}
.success-ring-unit {
  font-size: 16px;
  font-weight: 700;
  margin-left: 1px;
}
.success-ring-label {
  margin-top: 6px;
  font-size: 12px;
  font-weight: 600;
  color: #64748b;
  letter-spacing: 0.02em;
}
.success-ring-sub {
  margin-top: 4px;
  font-size: 11px;
  color: #94a3b8;
  font-variant-numeric: tabular-nums;
}

.metric-list, .efficiency-box { margin-top: 14px; }
.metric-row { display: flex; justify-content: space-between; align-items: center; padding: 14px 4px; border-bottom: 1px solid #edf2f8; color: #4f5d73; }
.metric-row.compact { padding-inline: 0; }
.metric-row strong { font-size: 16px; color: #1b2535; }
.metric-row .warn { color: #f18a00; }
.metric-row .danger { color: #ef4444; }

.quality-box, .efficiency-box { margin-top: 18px; padding: 16px; border-radius: 12px; background: linear-gradient(180deg, #f8fbff 0%, #f5f7fb 100%); border: 1px solid #edf2f8; }
.quality-title-row { display: flex; justify-content: space-between; align-items: center; margin-bottom: 14px; }
.quality-title { font-size: 14px; font-weight: 700; color: #1d2433; }
.quality-time { color: #99a3b7; font-size: 12px; }

.doc-stack-bar {
  display: flex;
  height: 8px;
  border-radius: 999px;
  overflow: hidden;
  background: #e8edf4;
  margin-bottom: 16px;
}
.doc-stack-seg { height: 100%; min-width: 0; transition: width .3s ease; }
.doc-stack-seg--success { background: linear-gradient(90deg, #22c55e, #4ade80); }
.doc-stack-seg--processing { background: linear-gradient(90deg, #f59e0b, #fbbf24); }
.doc-stack-seg--failed { background: linear-gradient(90deg, #ef4444, #f87171); }
.doc-stack-empty {
  padding: 12px 0 16px;
  text-align: center;
  color: #99a3b7;
  font-size: 13px;
}

.doc-status-list { display: flex; flex-direction: column; gap: 14px; }
.doc-status-row { display: flex; flex-direction: column; gap: 6px; }
.doc-status-head {
  display: grid;
  grid-template-columns: 8px 1fr auto auto;
  align-items: center;
  gap: 8px;
  font-size: 13px;
}
.doc-status-dot { width: 8px; height: 8px; border-radius: 50%; flex-shrink: 0; }
.doc-status-label { color: #4f5d73; }
.doc-status-count { font-weight: 700; font-size: 15px; min-width: 24px; text-align: right; }
.doc-status-pct { color: #99a3b7; font-size: 12px; min-width: 36px; text-align: right; }
.doc-status-track {
  height: 6px;
  border-radius: 999px;
  background: #e8edf4;
  overflow: hidden;
}
.doc-status-fill {
  height: 100%;
  border-radius: 999px;
  transition: width .3s ease;
  min-width: 0;
}

@media (max-width: 1280px) { .console-page { grid-template-columns: 1fr; } .console-side-panel { position: static; } }
@media (max-width: 920px) { .metric-grid, .trend-grid { grid-template-columns: 1fr; } }
</style>
