<template>
  <div class="admin-view">
    <section class="admin-page-header">
      <div>
        <div class="admin-page-title">关键词映射</div>
        <div class="admin-page-subtitle">管理高频关键词到意图、知识库和应答策略的映射关系。</div>
      </div>
      <div class="admin-actions">
        <el-button plain>批量导出</el-button>
        <el-button type="primary">新增映射</el-button>
      </div>
    </section>

    <section class="admin-table-card">
      <div class="admin-toolbar">
        <div>
          <div class="admin-toolbar-title">映射规则</div>
          <div class="admin-toolbar-subtitle">优先级越高，越先命中。</div>
        </div>
      </div>
      <el-table :data="mappings">
        <el-table-column prop="keyword" label="关键词" min-width="160" />
        <el-table-column prop="intent" label="目标意图" min-width="160" />
        <el-table-column prop="knowledgeBase" label="知识库" min-width="160" />
        <el-table-column prop="priority" label="优先级" width="100" />
        <el-table-column prop="strategy" label="应答策略" min-width="180" />
      </el-table>
    </section>
  </div>
</template>

<script setup>
const mappings = [
  { keyword: '报销', intent: '制度查询', knowledgeBase: '制度知识库', priority: 100, strategy: '优先返回制度类来源' },
  { keyword: 'milvus', intent: '技术方案检索', knowledgeBase: '技术知识库', priority: 90, strategy: '优先返回架构文档' },
  { keyword: '会议室', intent: '会议预约咨询', knowledgeBase: '流程知识库', priority: 80, strategy: '返回预约流程与注意事项' }
]
</script>
