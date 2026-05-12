<template>
  <div>
    <span style="font-size:18px;font-weight:600;display:block;margin-bottom:20px">数据看板</span>

    <!-- Top Cards -->
    <div style="display:flex;gap:16px;margin-bottom:24px">
      <div v-for="c in topCards" :key="c.label" class="feishu-card" style="flex:1;text-align:center;padding:24px">
        <div style="font-size:13px;color:var(--text-tertiary);margin-bottom:8px">{{ c.label }}</div>
        <div style="font-size:32px;font-weight:700" :style="{color:c.color}">{{ c.value }}</div>
      </div>
    </div>

    <div style="display:flex;gap:20px">
      <!-- Task Stats -->
      <div class="feishu-card" style="flex:1">
        <div style="font-weight:600;font-size:15px;margin-bottom:16px">任务统计</div>
        <div v-for="bar in taskBars" :key="bar.label" style="margin-bottom:12px">
          <div style="display:flex;justify-content:space-between;font-size:13px;margin-bottom:4px">
            <span>{{ bar.label }}</span><span :style="{color:bar.color}">{{ bar.value }}</span>
          </div>
          <div style="height:8px;background:var(--bg-body);border-radius:4px;overflow:hidden">
            <div :style="{width:bar.percent+'%',height:'100%',background:bar.color,borderRadius:'4px',transition:'width .5s ease'}" />
          </div>
        </div>
      </div>

      <!-- Approval Stats -->
      <div class="feishu-card" style="flex:1">
        <div style="font-weight:600;font-size:15px;margin-bottom:16px">审批统计</div>
        <div style="height:180px;display:flex;align-items:flex-end;gap:24px;padding:0 20px">
          <div v-for="col in approvalBars" :key="col.label" style="flex:1;text-align:center">
            <div style="font-size:20px;font-weight:700" :style="{color:col.color}">{{ col.value }}</div>
            <div :style="{height:col.height+'px',background:col.color,margin:'8px auto 0',width:'60px',borderRadius:'8px 8px 0 0',opacity:.2}">
              <div :style="{height:col.height+'px',background:col.color,borderRadius:'8px 8px 0 0',transition:'height .5s ease'}" />
            </div>
            <div style="font-size:12px;color:var(--text-tertiary);margin-top:6px">{{ col.label }}</div>
          </div>
        </div>
        <div style="text-align:center;font-size:13px;color:var(--text-tertiary);margin-top:12px">
          通过率 {{ approvalRate }}%
        </div>
      </div>
    </div>

    <!-- Bottom -->
    <div style="display:flex;gap:20px;margin-top:20px">
      <div class="feishu-card" style="flex:1">
        <div style="font-weight:600;font-size:15px;margin-bottom:12px">知识文档</div>
        <div style="font-size:48px;font-weight:700;color:var(--brand-500);text-align:center;padding:20px">{{ stats.docCount || 0 }}</div>
        <div style="text-align:center;color:var(--text-tertiary);font-size:13px">文档总数</div>
      </div>
      <div class="feishu-card" style="flex:1">
        <div style="font-weight:600;font-size:15px;margin-bottom:12px">今日会议</div>
        <div style="font-size:48px;font-weight:700;color:#67c23a;text-align:center;padding:20px">{{ meetingStats?.today || 0 }}</div>
        <div style="text-align:center;color:var(--text-tertiary);font-size:13px">共 {{ meetingStats?.total || 0 }} 场</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'

const stats = ref({})

const topCards = computed(() => [
  { label:'任务总数', value: stats.value.taskStats?.total||0, color:'var(--brand-500)' },
  { label:'待审批', value: stats.value.approvalStats?.pending||0, color:'#e6a23c' },
  { label:'进行中任务', value: stats.value.taskStats?.inProgress||0, color:'#67c23a' },
  { label:'文档总数', value: stats.value.docCount||0, color:'#f56c6c' },
])

const taskBars = computed(() => {
  const ts = stats.value.taskStats || {}
  const total = ts.total || 1
  return [
    { label:'待开始', value:ts.todo||0, color:'#909399', percent: Math.round((ts.todo||0)/total*100) },
    { label:'进行中', value:ts.inProgress||0, color:'var(--brand-500)', percent: Math.round((ts.inProgress||0)/total*100) },
    { label:'待确认', value:ts.review||0, color:'#e6a23c', percent: Math.round((ts.review||0)/total*100) },
    { label:'已完成', value:ts.done||0, color:'#67c23a', percent: Math.round((ts.done||0)/total*100) },
  ]
})

const approvalBars = computed(() => {
  const as = stats.value.approvalStats || {}
  const max = Math.max(as.pending||0, as.approved||0, as.rejected||0, 1)
  return [
    { label:'待审批', value:as.pending||0, color:'#e6a23c', height: Math.round((as.pending||0)/max*120) },
    { label:'已通过', value:as.approved||0, color:'#67c23a', height: Math.round((as.approved||0)/max*120) },
    { label:'已驳回', value:as.rejected||0, color:'#f56c6c', height: Math.round((as.rejected||0)/max*120) },
  ]
})

const approvalRate = computed(() => {
  const as = stats.value.approvalStats || {}
  const total = (as.approved||0) + (as.rejected||0)
  return total ? Math.round((as.approved||0)/total*100) : 0
})

const meetingStats = computed(() => stats.value.meetingStats)

onMounted(async () => {
  const u = JSON.parse(localStorage.getItem('user')||'{}')
  const headers = {'X-User-Id':String(u.id||1),'X-Is-Admin':String(u.isAdmin?'true':'false')}
  try {
    const resp = await fetch('/api/workbench/stats', { headers })
    stats.value = (await resp.json()).data || {}
  } catch(e) { console.log('Stats API not available') }
})
</script>
