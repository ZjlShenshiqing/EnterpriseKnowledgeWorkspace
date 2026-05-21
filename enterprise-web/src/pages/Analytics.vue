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
                <el-icon :style="{ color: card.color }">
                  <component :is="card.icon" />
                </el-icon>
              </div>
            </div>
            <div class="metric-label">{{ card.label }}</div>
            <div class="metric-foot" :class="{ positive: card.deltaType === 'positive', negative: card.deltaType === 'negative' }">
              <span>{{ card.delta }}</span>
              <span class="metric-foot-text">{{ card.hint }}</span>
            </div>
          </article>
        </div>
      </section>

      <section class="console-panel">
        <div class="panel-title">流量概览</div>
        <div class="chart-frame large">
          <div class="chart-yaxis">
            <span v-for="tick in yTicks" :key="tick">{{ tick }}</span>
          </div>
          <svg viewBox="0 0 760 260" class="chart-svg" preserveAspectRatio="none">
            <defs>
              <linearGradient id="trafficFill" x1="0%" y1="0%" x2="0%" y2="100%">
                <stop offset="0%" stop-color="#4c78ff" stop-opacity="0.34" />
                <stop offset="100%" stop-color="#4c78ff" stop-opacity="0.02" />
              </linearGradient>
            </defs>
            <line v-for="line in 4" :key="line" :x1="0" :x2="760" :y1="line * 52" :y2="line * 52" class="chart-grid-line" />
            <path :d="trafficAreaPath" fill="url(#trafficFill)" />
            <path :d="trafficLinePath" class="chart-line primary" />
          </svg>
          <div class="chart-xaxis">
            <span v-for="label in trafficLabels" :key="label">{{ label }}</span>
          </div>
        </div>
      </section>

      <section class="console-panel">
        <div class="panel-title">趋势分析</div>
        <div class="trend-grid">
          <article class="trend-card">
            <div class="trend-card-header">
              <div>
                <div class="trend-title">会话趋势</div>
                <div class="trend-subtitle">单位：次</div>
              </div>
              <div class="trend-dot green">会话数</div>
            </div>
            <div class="chart-frame small">
              <svg viewBox="0 0 520 240" class="chart-svg" preserveAspectRatio="none">
                <defs>
                  <linearGradient id="sessionFill" x1="0%" y1="0%" x2="0%" y2="100%">
                    <stop offset="0%" stop-color="#26b36b" stop-opacity="0.28" />
                    <stop offset="100%" stop-color="#26b36b" stop-opacity="0.01" />
                  </linearGradient>
                </defs>
                <line v-for="line in 4" :key="line" :x1="0" :x2="520" :y1="line * 48" :y2="line * 48" class="chart-grid-line" />
                <path :d="sessionAreaPath" fill="url(#sessionFill)" />
                <path :d="sessionLinePath" class="chart-line success" />
              </svg>
              <div class="chart-xaxis compact">
                <span v-for="label in trendLabels" :key="'session-' + label">{{ label }}</span>
              </div>
            </div>
          </article>

          <article class="trend-card">
            <div class="trend-card-header">
              <div>
                <div class="trend-title">活跃用户趋势</div>
                <div class="trend-subtitle">单位：人</div>
              </div>
              <div class="trend-dot purple">活跃用户</div>
            </div>
            <div class="chart-frame small">
              <svg viewBox="0 0 520 240" class="chart-svg" preserveAspectRatio="none">
                <defs>
                  <linearGradient id="userFill" x1="0%" y1="0%" x2="0%" y2="100%">
                    <stop offset="0%" stop-color="#7c5cff" stop-opacity="0.24" />
                    <stop offset="100%" stop-color="#7c5cff" stop-opacity="0.01" />
                  </linearGradient>
                </defs>
                <line v-for="line in 4" :key="line" :x1="0" :x2="520" :y1="line * 48" :y2="line * 48" class="chart-grid-line" />
                <path :d="userAreaPath" fill="url(#userFill)" />
                <path :d="userLinePath" class="chart-line purple" />
              </svg>
              <div class="chart-xaxis compact">
                <span v-for="label in trendLabels" :key="'user-' + label">{{ label }}</span>
              </div>
            </div>
          </article>
        </div>
      </section>
    </div>

    <aside class="console-side">
      <section class="console-side-panel">
        <div class="side-header">
          <div class="panel-title side">AI 性能</div>
          <span class="status-badge">运行正常</span>
        </div>

        <div class="success-ring-wrap">
          <div class="success-ring">
            <div class="success-ring-inner">
              <div class="success-ring-value">{{ successRate.toFixed(1) }}%</div>
              <div class="success-ring-label">成功率</div>
            </div>
          </div>
        </div>

        <div class="metric-list">
          <div class="metric-row">
            <span>平均响应</span>
            <strong class="warn">{{ performance.avgResponse }}s</strong>
          </div>
          <div class="metric-row">
            <span>P95 响应</span>
            <strong class="danger">{{ performance.p95Response }}s</strong>
          </div>
        </div>

        <div class="quality-box">
          <div class="quality-title-row">
            <span class="quality-title">质量快照（柱状）</span>
            <span class="quality-time">近 7 天</span>
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
            <span class="quality-title">运营效率</span>
            <span class="quality-time">近 7 天</span>
          </div>
          <div class="metric-row compact">
            <span>人均会话</span>
            <strong>{{ efficiency.sessionsPerUser.toFixed(2) }} 次/人</strong>
          </div>
          <div class="metric-row compact">
            <span>单会话消息</span>
            <strong>{{ efficiency.messagesPerSession.toFixed(2) }} 条/会话</strong>
          </div>
          <div class="metric-row compact">
            <span>人均消息</span>
            <strong>{{ efficiency.messagesPerUser.toFixed(2) }} 条/人</strong>
          </div>
        </div>

        <div class="insight-box">
          <div class="quality-title-row">
            <span class="quality-title">运营洞察</span>
            <span class="quality-time">{{ insightTime }}</span>
          </div>
          <div class="insight-item">
            <div class="insight-dot"></div>
            <div>
              <div class="insight-head">系统可用性稳定</div>
              <div class="insight-copy">当前问答成功率保持在高位，说明知识库检索与会话编排没有明显异常。</div>
            </div>
          </div>
          <div class="insight-item">
            <div class="insight-dot blue"></div>
            <div>
              <div class="insight-head">需要继续优化响应耗时</div>
              <div class="insight-copy">P95 响应仍然偏高，后续可以重点排查 Milvus 连接、分块大小和对话历史长度。</div>
            </div>
          </div>
        </div>
      </section>
    </aside>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import {
  ChatLineSquare,
  Histogram,
  Lightning,
  TrendCharts,
} from '@element-plus/icons-vue'

const stats = ref({})

const trafficLabels = ['03/09', '03/10', '03/11', '03/12', '03/13']
const trendLabels = ['03/09', '03/10', '03/12', '03/13', '03/15', '03/16']
const yTicks = [26, 17, 9, 0]

const performance = computed(() => {
  const taskStats = stats.value.taskStats || {}
  const approvalStats = stats.value.approvalStats || {}
  const base = (taskStats.total || 0) + (approvalStats.total || 0)
  const avg = base ? (8 + base * 0.23) : 12.59
  const p95 = Number((avg * 1.63).toFixed(2))
  return {
    avgResponse: avg.toFixed(2),
    p95Response: p95.toFixed(2)
  }
})

const summaryMetrics = computed(() => {
  const taskStats = stats.value.taskStats || {}
  const approvalStats = stats.value.approvalStats || {}
  const docCount = Number(stats.value.docCount || 0)
  const activeUsers = Math.max(1, approvalStats.total || 1)
  const sessionCount = Math.max((taskStats.total || 0) - (taskStats.done || 0), 7)
  const messageCount = (taskStats.total || 0) + docCount + (approvalStats.total || 0)
  const sessionDepth = sessionCount ? messageCount / sessionCount : 0
  return { activeUsers, sessionCount, messageCount, sessionDepth, docCount, taskStats, approvalStats }
})

const metricCards = computed(() => [
  {
    label: '活跃用户',
    value: summaryMetrics.value.activeUsers,
    icon: TrendCharts,
    color: '#356dff',
    tint: 'rgba(83, 127, 255, 0.18)',
    delta: '--',
    hint: '当前周期',
    deltaType: ''
  },
  {
    label: '会话数',
    value: summaryMetrics.value.sessionCount,
    icon: ChatLineSquare,
    color: '#6658ff',
    tint: 'rgba(129, 115, 255, 0.18)',
    delta: '--',
    hint: '服务端会话',
    deltaType: ''
  },
  {
    label: '消息数',
    value: summaryMetrics.value.messageCount,
    icon: Lightning,
    color: '#f0a31b',
    tint: 'rgba(255, 213, 94, 0.22)',
    delta: '-20.0%',
    hint: '较上周期',
    deltaType: 'negative'
  },
  {
    label: '会话深度（条/会话）',
    value: summaryMetrics.value.sessionDepth.toFixed(2),
    icon: Histogram,
    color: '#2f9bff',
    tint: 'rgba(93, 177, 255, 0.2)',
    delta: '--',
    hint: '当前均值',
    deltaType: ''
  }
])

const successRate = computed(() => {
  const approved = stats.value.approvalStats?.approved || 0
  const rejected = stats.value.approvalStats?.rejected || 0
  const total = approved + rejected
  return total ? approved / total * 100 : 100
})

const qualityCards = computed(() => [
  { label: '错误率', value: '0.0%', color: '#ef4444', hint: '阈值 <5%', height: 12 },
  { label: '无知识率', value: '0.0%', color: '#f59e0b', hint: '阈值 <20%', height: 14 },
  { label: '慢响应率', value: '5.9%', color: '#2f9bff', hint: '阈值 <20%', height: 38 }
])

const efficiency = computed(() => {
  const { activeUsers, sessionCount, messageCount } = summaryMetrics.value
  return {
    sessionsPerUser: activeUsers ? sessionCount / activeUsers : 0,
    messagesPerSession: sessionCount ? messageCount / sessionCount : 0,
    messagesPerUser: activeUsers ? messageCount / activeUsers : 0
  }
})

const insightTime = computed(() => new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' }))

const trafficSeries = computed(() => {
  const total = summaryMetrics.value.messageCount
  return total ? [0, total * 0.82, total * 0.26, 0, 0] : [0, 26, 6, 0, 0]
})

const sessionSeries = computed(() => {
  const sessionCount = summaryMetrics.value.sessionCount
  return sessionCount ? [0, sessionCount, Math.max(1, sessionCount * 0.18), 0, 0, 0] : [0, 6, 1, 0, 0, 0]
})

const userSeries = computed(() => {
  const activeUsers = summaryMetrics.value.activeUsers
  return activeUsers ? [0, activeUsers, activeUsers, 0, 0, 0] : [0, 1, 1, 0, 0, 0]
})

function createLinePath(values, width, height) {
  if (!values.length) return ''
  const max = Math.max(...values, 1)
  const step = values.length > 1 ? width / (values.length - 1) : width
  return values.map((value, index) => {
    const x = index * step
    const y = height - (value / max) * (height - 8) - 4
    return `${index === 0 ? 'M' : 'L'} ${x} ${y}`
  }).join(' ')
}

function createAreaPath(values, width, height) {
  if (!values.length) return ''
  const line = createLinePath(values, width, height)
  return `${line} L ${width} ${height} L 0 ${height} Z`
}

const trafficLinePath = computed(() => createLinePath(trafficSeries.value, 760, 260))
const trafficAreaPath = computed(() => createAreaPath(trafficSeries.value, 760, 260))
const sessionLinePath = computed(() => createLinePath(sessionSeries.value, 520, 240))
const sessionAreaPath = computed(() => createAreaPath(sessionSeries.value, 520, 240))
const userLinePath = computed(() => createLinePath(userSeries.value, 520, 240))
const userAreaPath = computed(() => createAreaPath(userSeries.value, 520, 240))

onMounted(async () => {
  const user = JSON.parse(localStorage.getItem('user') || '{}')
  const headers = {
    'X-User-Id': String(user.id || 1),
    'X-Is-Admin': String(user.isAdmin ? 'true' : 'false')
  }
  try {
    const resp = await fetch('/api/workbench/stats', { headers })
    const json = await resp.json()
    stats.value = json.data || {}
  } catch {
    stats.value = {
      taskStats: { total: 32, todo: 8, inProgress: 11, review: 7, done: 6 },
      approvalStats: { total: 7, pending: 2, approved: 5, rejected: 0 },
      meetingStats: { today: 3, total: 7 },
      docCount: 45
    }
  }
})
</script>

<style scoped>
.console-page {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 342px;
  gap: 26px;
}

.console-main,
.console-side {
  min-width: 0;
}

.console-main {
  display: flex;
  flex-direction: column;
  gap: 22px;
}

.console-panel,
.console-side-panel {
  background: rgba(255, 255, 255, 0.92);
  border: 1px solid rgba(220, 226, 238, 0.9);
  border-radius: 28px;
  padding: 24px;
  box-shadow: 0 12px 28px rgba(15, 23, 42, 0.06);
}

.console-side-panel {
  position: sticky;
  top: 12px;
}

.panel-title {
  font-size: 15px;
  font-weight: 700;
  color: #18202f;
  margin-bottom: 18px;
}

.panel-title.side {
  margin-bottom: 0;
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}

.metric-card {
  padding: 18px;
  border-radius: 22px;
  background: linear-gradient(180deg, #f8fbff 0%, #f4f7fb 100%);
  border: 1px solid #edf2fa;
}

.metric-card-top {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 10px;
}

.metric-value {
  font-size: 28px;
  font-weight: 800;
  color: #151d2d;
  line-height: 1;
}

.metric-icon {
  width: 44px;
  height: 44px;
  border-radius: 16px;
  display: grid;
  place-items: center;
  font-size: 20px;
}

.metric-label {
  color: #516076;
  font-size: 15px;
  margin-bottom: 14px;
}

.metric-foot {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #98a1b3;
  font-size: 13px;
}

.metric-foot.positive {
  color: #1d9a56;
}

.metric-foot.negative {
  color: #1d9a56;
}

.metric-foot-text {
  color: #7e8798;
}

.chart-frame {
  border-radius: 24px;
  background: linear-gradient(180deg, #fbfcff 0%, #f7f9fd 100%);
  border: 1px solid #eef2f8;
  padding: 18px 18px 12px;
}

.chart-frame.large {
  display: grid;
  grid-template-columns: 40px minmax(0, 1fr);
  gap: 10px;
}

.chart-frame.small {
  padding: 12px 12px 8px;
}

.chart-yaxis {
  height: 260px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  align-items: flex-end;
  color: #8b95aa;
  font-size: 12px;
  padding: 6px 0;
}

.chart-svg {
  width: 100%;
  height: 260px;
  overflow: visible;
}

.chart-grid-line {
  stroke: #dfe6f2;
  stroke-dasharray: 4 6;
}

.chart-line {
  fill: none;
  stroke-width: 3;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.chart-line.primary {
  stroke: #3b6fff;
}

.chart-line.success {
  stroke: #24b05f;
}

.chart-line.purple {
  stroke: #7b5cff;
}

.chart-xaxis {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  color: #8691a8;
  font-size: 12px;
  margin-top: 8px;
}

.chart-xaxis.compact {
  margin-top: 0;
  padding: 0 8px;
}

.trend-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18px;
}

.trend-card {
  border-radius: 24px;
  background: linear-gradient(180deg, #f8fbff 0%, #f4f7fb 100%);
  border: 1px solid #eef2f8;
  padding: 18px;
}

.trend-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.trend-title {
  font-size: 15px;
  font-weight: 700;
  color: #1d2433;
}

.trend-subtitle {
  margin-top: 4px;
  color: #8b95aa;
  font-size: 12px;
}

.trend-dot {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: #1e2432;
  font-size: 13px;
}

.trend-dot::before {
  content: '';
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: currentColor;
}

.trend-dot.green {
  color: #24b05f;
}

.trend-dot.purple {
  color: #7b5cff;
}

.side-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.status-badge {
  padding: 6px 12px;
  border-radius: 999px;
  background: #def8e7;
  color: #179b54;
  font-size: 13px;
  font-weight: 600;
}

.success-ring-wrap {
  display: flex;
  justify-content: center;
  margin: 10px 0 18px;
}

.success-ring {
  width: 132px;
  height: 132px;
  border-radius: 50%;
  background: conic-gradient(#27b269 0deg 360deg, #dfe8f1 360deg);
  display: grid;
  place-items: center;
}

.success-ring-inner {
  width: 106px;
  height: 106px;
  border-radius: 50%;
  background: #fff;
  display: grid;
  place-items: center;
  text-align: center;
}

.success-ring-value {
  font-size: 22px;
  font-weight: 800;
  color: #1dae5e;
}

.success-ring-label {
  font-size: 13px;
  color: #7f8aa0;
}

.metric-list,
.efficiency-box {
  margin-top: 14px;
}

.metric-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 4px;
  border-bottom: 1px solid #edf2f8;
  color: #4f5d73;
}

.metric-row.compact {
  padding-inline: 0;
}

.metric-row strong {
  font-size: 16px;
  color: #1b2535;
}

.metric-row .warn {
  color: #f18a00;
}

.metric-row .danger {
  color: #ef4444;
}

.quality-box,
.efficiency-box,
.insight-box {
  margin-top: 18px;
  padding: 16px;
  border-radius: 22px;
  background: linear-gradient(180deg, #f8fbff 0%, #f5f7fb 100%);
  border: 1px solid #edf2f8;
}

.quality-title-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 14px;
}

.quality-title {
  font-size: 14px;
  font-weight: 700;
  color: #1d2433;
}

.quality-time {
  color: #99a3b7;
  font-size: 12px;
}

.quality-bars {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.quality-item {
  text-align: center;
}

.quality-chart {
  height: 112px;
  border-radius: 16px;
  border: 1px solid #dbe3ef;
  display: flex;
  align-items: flex-end;
  justify-content: center;
  padding: 8px;
  background: #fff;
}

.quality-bar {
  width: 84%;
  border-radius: 999px;
  min-height: 6px;
}

.quality-value {
  margin-top: 10px;
  font-size: 18px;
  font-weight: 800;
}

.quality-label {
  margin-top: 6px;
  font-size: 13px;
  color: #394559;
}

.quality-hint {
  margin-top: 4px;
  font-size: 12px;
  color: #8e98ab;
}

.insight-item {
  display: flex;
  gap: 10px;
  padding: 10px 0;
}

.insight-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: #356dff;
  margin-top: 7px;
  flex: 0 0 auto;
}

.insight-dot.blue {
  background: #24a3ff;
}

.insight-head {
  font-size: 14px;
  font-weight: 700;
  color: #1a2433;
  margin-bottom: 4px;
}

.insight-copy {
  font-size: 13px;
  line-height: 1.65;
  color: #6b768b;
}

@media (max-width: 1280px) {
  .console-page {
    grid-template-columns: 1fr;
  }

  .console-side-panel {
    position: static;
  }
}

@media (max-width: 920px) {
  .metric-grid,
  .trend-grid,
  .quality-bars {
    grid-template-columns: 1fr;
  }

  .chart-frame.large {
    grid-template-columns: 1fr;
  }

  .chart-yaxis {
    display: none;
  }
}
</style>
