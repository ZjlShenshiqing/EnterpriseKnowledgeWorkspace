<template>
  <div class="admin-view">
    <section class="admin-page-header">
      <div>
        <div class="admin-page-title">示例问题</div>
        <div class="admin-page-subtitle">维护推荐问法，帮助用户快速体验知识问答与流程助手。</div>
      </div>
      <div class="admin-actions">
        <el-button plain>按场景筛选</el-button>
        <el-button type="primary">新增示例</el-button>
      </div>
    </section>

    <section class="admin-table-card">
      <div class="admin-toolbar">
        <div>
          <div class="admin-toolbar-title">推荐问法列表</div>
          <div class="admin-toolbar-subtitle">示例问题会显示在聊天页或新手引导区域。</div>
        </div>
      </div>
      <el-table :data="examples">
        <el-table-column prop="question" label="示例问题" min-width="240" />
        <el-table-column prop="scene" label="场景" min-width="120" />
        <el-table-column prop="intent" label="关联意图" min-width="140" />
        <el-table-column prop="status" label="状态" width="100" />
        <el-table-column prop="updatedAt" label="最近更新" width="180" />
      </el-table>
    </section>
  </div>
</template>

<script setup>
const examples = [
  { question: '报销需要准备哪些材料？', scene: '制度问答', intent: '制度查询', status: '启用', updatedAt: '2026-05-21 08:30' },
  { question: '帮我找一下微服务架构设计规范', scene: '知识检索', intent: '技术方案检索', status: '启用', updatedAt: '2026-05-20 16:10' },
  { question: '会议室冲突怎么处理？', scene: '流程问答', intent: '会议预约咨询', status: '启用', updatedAt: '2026-05-19 14:55' }
]
</script>
