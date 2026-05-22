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
          <div class="success-ring" :style="{ background: successRingGradient }">
            <div class="success-ring-inner">
              <div class="success-ring-value">{{ aiStats.successRate }}%</div>
              <div class="success-ring-label">成功率</div>
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
            <span class="quality-time">累计</span>
          </div>
          <div class="quality-bars">
            <div v-for="item in qualityCards" :key="item.label" class="quality-item">
              <div class="quality-chart">
                <div class="quality-bar" :style="{ height: item.height + '%', background: item.color }"></div>
              </div>
              <div class="quality-value" :style="{ color: item.color }">{{ item.value }}</div>
              <div class="quality-label">{{ item.label }}</div>
              <div class="quality-hint">{{ item.hint }}</div>
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

const userStats = ref({ total: 0, enabled: 0, admin: 0, disabled: 0 })
const aiStats = ref({ successRate: 0, avgDurationMs: 0, p95DurationMs: 0, sessionCount: 0, messageCount: 0, avgMessagesPerSession: 0, totalTokens: 0 })
const collabStats = ref({ taskTodo: 0, taskInProgress: 0, taskReview: 0, taskDone: 0, approvalPending: 0, approvalApproved: 0, meetingToday: 0, meetingTotal: 0 })
const docStats = ref({ totalDocs: 0, successDocs: 0, failedDocs: 0, kbCount: 0 })

const aiHealthClass = computed(() => aiStats.value.successRate >= 95 ? '' : 'warn')
const aiHealthText = computed(() => aiStats.value.successRate >= 95 ? '运行正常' : '需关注')

const successRingGradient = computed(() => {
  const rate = aiStats.value.successRate
  return `conic-gradient(#27b269 0deg ${rate * 3.6}deg, #dfe8f1 ${rate * 3.6}deg 360deg)`
})

const metricCards = computed(() => [
  { label: '用户总数', value: userStats.value.total, icon: User, color: '#356dff', tint: 'rgba(83,127,255,0.18)', hint: `启用 ${userStats.value.enabled} · 管理员 ${userStats.value.admin}` },
  { label: 'AI 会话数', value: aiStats.value.sessionCount, icon: ChatLineSquare, color: '#6658ff', tint: 'rgba(129,115,255,0.18)', hint: `${aiStats.value.messageCount} 条消息` },
  { label: '文档数', value: docStats.value.totalDocs, icon: Document, color: '#f0a31b', tint: 'rgba(255,213,94,0.22)', hint: `${docStats.value.kbCount} 个知识库` },
  { label: '会议数', value: collabStats.value.meetingTotal, icon: DataBoard, color: '#2f9bff', tint: 'rgba(93,177,255,0.2)', hint: `今日 ${collabStats.value.meetingToday} 场` }
])

const qualityCards = computed(() => {
  const total = docStats.value.totalDocs || 1
  return [
    { label: '成功率', value: aiStats.value.successRate + '%', color: '#22c55e', hint: '文档处理', height: aiStats.value.successRate },
    { label: '处理中', value: docStats.value.totalDocs - docStats.value.successDocs - docStats.value.failedDocs, color: '#f59e0b', hint: '含待处理', height: Math.max(8, (docStats.value.totalDocs - docStats.value.successDocs - docStats.value.failedDocs) / total * 100) },
    { label: '失败', value: docStats.value.failedDocs, color: '#ef4444', hint: '需排查', height: Math.max(8, docStats.value.failedDocs / total * 100) }
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

async function fetchJson(url, headers) {
  const resp = await fetch(url, { headers })
  if (!resp.ok) throw new Error('HTTP ' + resp.status)
  const json = await resp.json()
  return json.data || {}
}

onMounted(async () => {
  const user = JSON.parse(localStorage.getItem('user') || '{}')
  const headers = {
    'X-User-Id': String(user.id || 1),
    'X-Is-Admin': String(user.isAdmin ? 'true' : 'false'),
    'X-Department-Id': String(user.departmentId || 1)
  }
  if (user.token) headers['Authorization'] = 'Bearer ' + user.token

  try {
    const data = await fetchJson('/api/system/users/stats', headers)
    userStats.value = data
  } catch { /* ignore */ }

  try {
    const data = await fetchJson('/api/kb/admin/stats', headers)
    aiStats.value = data
  } catch { /* ignore */ }

  try {
    const data = await fetchJson('/api/workbench/stats', headers)
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
    docStats.value.totalDocs = data.docCount || 0
  } catch { /* ignore */ }

  try {
    const resp = await fetch('/api/kb/documents?current=1&size=1', { headers })
    const json = await resp.json()
    docStats.value.totalDocs = docStats.value.totalDocs || (json.data?.total || 0)
    docStats.value.successDocs = json.data?.records?.filter(d => d.status === 'SUCCESS').length || 0
    docStats.value.failedDocs = json.data?.records?.filter(d => d.status === 'FAILED').length || 0
  } catch { /* ignore */ }

  try {
    const resp = await fetch('/api/kb/bases?current=1&size=1', { headers })
    const json = await resp.json()
    docStats.value.kbCount = json.data?.total || 0
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

.success-ring-wrap { display: flex; justify-content: center; margin: 10px 0 18px; }
.success-ring { width: 132px; height: 132px; border-radius: 50%; display: grid; place-items: center; }
.success-ring-inner { width: 106px; height: 106px; border-radius: 50%; background: #fff; display: grid; place-items: center; text-align: center; }
.success-ring-value { font-size: 22px; font-weight: 800; color: #1dae5e; }
.success-ring-label { font-size: 13px; color: #7f8aa0; }

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
.quality-bars { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 12px; }
.quality-item { text-align: center; }
.quality-chart { height: 100px; border-radius: 12px; border: 1px solid #dbe3ef; display: flex; align-items: flex-end; justify-content: center; padding: 8px; background: #fff; }
.quality-bar { width: 84%; border-radius: 999px; min-height: 6px; }
.quality-value { margin-top: 10px; font-size: 18px; font-weight: 800; }
.quality-label { margin-top: 6px; font-size: 13px; color: #394559; }
.quality-hint { margin-top: 4px; font-size: 12px; color: #8e98ab; }

@media (max-width: 1280px) { .console-page { grid-template-columns: 1fr; } .console-side-panel { position: static; } }
@media (max-width: 920px) { .metric-grid, .trend-grid, .quality-bars { grid-template-columns: 1fr; } }
</style>
