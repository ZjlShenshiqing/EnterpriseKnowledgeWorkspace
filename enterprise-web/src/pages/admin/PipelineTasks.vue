<template>
  <div class="admin-view">
    <section class="admin-page-header">
      <div>
        <div class="admin-page-title">流水线任务</div>
        <div class="admin-page-subtitle">查看当前执行中的异步任务、重试情况和失败原因。</div>
      </div>
      <div class="admin-actions">
        <el-button plain>刷新队列</el-button>
        <el-button type="primary">重跑失败任务</el-button>
      </div>
    </section>

    <section class="admin-table-card">
      <div class="admin-toolbar">
        <div>
          <div class="admin-toolbar-title">任务队列</div>
          <div class="admin-toolbar-subtitle">包括文档分块、向量同步、意图发布等任务。</div>
        </div>
        <div class="admin-filters">
          <span class="admin-chip">进行中 3</span>
          <span class="admin-chip">失败 1</span>
        </div>
      </div>
      <el-table :data="tasks">
        <el-table-column prop="taskId" label="任务 ID" width="180" />
        <el-table-column prop="type" label="任务类型" min-width="140" />
        <el-table-column prop="owner" label="归属模块" min-width="120" />
        <el-table-column prop="progress" label="进度" width="120" />
        <el-table-column prop="status" label="状态" width="100" />
        <el-table-column prop="updatedAt" label="最近更新" width="180" />
      </el-table>
    </section>
  </div>
</template>

<script setup>
const tasks = [
  { taskId: 'chunk-20260521-001', type: '文档分块', owner: 'knowledge-ai-service', progress: '82%', status: 'RUNNING', updatedAt: '2026-05-21 10:21' },
  { taskId: 'vector-20260521-003', type: '向量同步', owner: 'knowledge-ai-service', progress: '45%', status: 'RUNNING', updatedAt: '2026-05-21 10:19' },
  { taskId: 'intent-20260520-014', type: '意图发布', owner: 'agent-router', progress: '100%', status: 'SUCCESS', updatedAt: '2026-05-20 21:10' },
  { taskId: 'trace-20260520-022', type: '日志归档', owner: 'analytics-pipeline', progress: '100%', status: 'FAILED', updatedAt: '2026-05-20 19:48' }
]
</script>
