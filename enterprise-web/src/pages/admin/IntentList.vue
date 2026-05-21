<template>
  <div class="admin-view">
    <section class="admin-page-header">
      <div>
        <div class="admin-page-title">意图列表</div>
        <div class="admin-page-subtitle">查看每个意图的命中量、关联知识库和处理状态。</div>
      </div>
      <div class="admin-actions">
        <el-button plain>批量启停</el-button>
        <el-button type="primary">新增意图</el-button>
      </div>
    </section>

    <section class="admin-table-card">
      <div class="admin-toolbar">
        <div>
          <div class="admin-toolbar-title">意图清单</div>
          <div class="admin-toolbar-subtitle">支持按场景、命中率和路由类型管理。</div>
        </div>
        <div class="admin-filters">
          <span class="admin-chip">全部场景</span>
          <span class="admin-chip">命中率 > 80%</span>
          <span class="admin-chip">已上线</span>
        </div>
      </div>
      <el-table :data="rows">
        <el-table-column prop="name" label="意图名称" min-width="180" />
        <el-table-column prop="scene" label="所属场景" min-width="120" />
        <el-table-column prop="knowledgeBase" label="关联知识库" min-width="160" />
        <el-table-column prop="hitRate" label="命中率" width="120" />
        <el-table-column prop="status" label="状态" width="100" />
        <el-table-column prop="updatedAt" label="最近更新" width="180" />
      </el-table>
    </section>
  </div>
</template>

<script setup>
const rows = [
  { name: '制度查询', scene: '知识问答', knowledgeBase: '制度知识库', hitRate: '96.1%', status: '已上线', updatedAt: '2026-05-21 09:22' },
  { name: '技术方案检索', scene: '知识问答', knowledgeBase: '技术知识库', hitRate: '92.5%', status: '已上线', updatedAt: '2026-05-20 18:40' },
  { name: '工单分派', scene: '工单协助', knowledgeBase: '客服知识库', hitRate: '83.7%', status: '灰度中', updatedAt: '2026-05-20 15:17' },
  { name: '会议预约咨询', scene: '内部流程', knowledgeBase: '流程知识库', hitRate: '88.0%', status: '已上线', updatedAt: '2026-05-19 11:05' }
]
</script>
