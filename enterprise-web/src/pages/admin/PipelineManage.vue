<template>
  <div class="admin-view">
    <section class="admin-page-header">
      <div>
        <div class="admin-page-title">流水线管理</div>
        <div class="admin-page-subtitle">管理数据接入、解析、分块、向量写入等处理通道。</div>
      </div>
      <div class="admin-actions">
        <el-button plain>查看模板</el-button>
        <el-button type="primary">新增流水线</el-button>
      </div>
    </section>

    <section class="admin-grid-3">
      <article v-for="pipeline in pipelines" :key="pipeline.name" class="admin-card">
        <div class="admin-card-head">
          <div>
            <div class="admin-section-title">{{ pipeline.name }}</div>
            <div class="admin-section-hint">{{ pipeline.type }}</div>
          </div>
          <span class="admin-badge">{{ pipeline.status }}</span>
        </div>
        <div class="admin-note">{{ pipeline.desc }}</div>
        <div class="admin-meta-list" style="margin-top: 18px;">
          <div class="admin-meta-row">
            <span class="admin-meta-label">源端</span>
            <span class="admin-meta-value">{{ pipeline.source }}</span>
          </div>
          <div class="admin-meta-row">
            <span class="admin-meta-label">目标</span>
            <span class="admin-meta-value">{{ pipeline.target }}</span>
          </div>
          <div class="admin-meta-row">
            <span class="admin-meta-label">处理阶段</span>
            <span class="admin-meta-value">{{ pipeline.stages }}</span>
          </div>
        </div>
      </article>
    </section>
  </div>
</template>

<script setup>
const pipelines = [
  { name: '文档入库主链路', type: '知识摄取', status: '稳定运行', desc: '覆盖上传、解析、分块、向量写入和主表回写。', source: 'Upload API', target: 'MySQL + Milvus', stages: '5 个' },
  { name: '意图词典同步', type: '规则发布', status: '待发布', desc: '把意图树和关键词映射同步到在线问答服务。', source: 'Admin Console', target: 'Agent Router', stages: '3 个' },
  { name: '问答日志归档', type: '分析数据', status: '运行中', desc: '归集问答链路日志，为运营看板和追踪页提供数据。', source: 'Chat Events', target: 'Analytics Store', stages: '4 个' }
]
</script>
