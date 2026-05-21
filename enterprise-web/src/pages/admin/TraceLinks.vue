<template>
  <div class="admin-view">
    <section class="admin-page-header">
      <div>
        <div class="admin-page-title">链路追踪</div>
        <div class="admin-page-subtitle">从用户问题到检索、工具调用、模型回答的完整链路追踪。</div>
      </div>
      <div class="admin-actions">
        <el-button plain>导出 Trace</el-button>
        <el-button type="primary">查看最新会话</el-button>
      </div>
    </section>

    <section class="admin-grid-2">
      <div class="admin-card">
        <div class="admin-card-head">
          <div>
            <div class="admin-section-title">最近链路</div>
            <div class="admin-section-hint">最近 5 条问答链路采样。</div>
          </div>
        </div>
        <div class="admin-list">
          <div v-for="item in traces" :key="item.id" class="admin-list-item">
            <div class="admin-toolbar">
              <div class="admin-list-title">{{ item.question }}</div>
              <span class="admin-chip">{{ item.latency }}</span>
            </div>
            <div class="admin-list-copy">{{ item.flow }}</div>
          </div>
        </div>
      </div>

      <div class="admin-card">
        <div class="admin-card-head">
          <div>
            <div class="admin-section-title">异常热点</div>
            <div class="admin-section-hint">帮助快速定位慢响应和低命中问题。</div>
          </div>
        </div>
        <div class="admin-meta-list">
          <div class="admin-meta-row">
            <span class="admin-meta-label">P95 响应偏高</span>
            <span class="admin-meta-value">RAG 问答链路</span>
          </div>
          <div class="admin-meta-row">
            <span class="admin-meta-label">重试次数最高</span>
            <span class="admin-meta-value">Milvus 查询</span>
          </div>
          <div class="admin-meta-row">
            <span class="admin-meta-label">无结果比例最高</span>
            <span class="admin-meta-value">制度检索</span>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
const traces = [
  { id: 1, question: '报销流程怎么走？', latency: '4.2s', flow: 'Gateway -> AgentLoop -> RagQaTool -> Milvus -> LLM' },
  { id: 2, question: 'Milvus 连不上怎么办？', latency: '7.9s', flow: 'Gateway -> AgentLoop -> SearchDocumentsTool -> Milvus -> LLM' },
  { id: 3, question: '请假审批在哪看？', latency: '3.6s', flow: 'Gateway -> AgentLoop -> IntentRouter -> FAQ Base -> LLM' }
]
</script>
