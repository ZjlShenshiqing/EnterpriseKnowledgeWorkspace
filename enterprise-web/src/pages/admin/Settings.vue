<template>
  <div class="admin-view">
    <section class="admin-page-header">
      <div>
        <div class="admin-page-title">系统设置</div>
        <div class="admin-page-subtitle">维护向量写入、模型路由、告警和后台基础配置。</div>
      </div>
      <div class="admin-actions">
        <el-button plain>恢复默认</el-button>
        <el-button type="primary">保存设置</el-button>
      </div>
    </section>

    <section class="admin-grid-2">
      <div class="admin-card">
        <div class="admin-card-head">
          <div>
            <div class="admin-section-title">知识服务</div>
            <div class="admin-section-hint">控制分块、向量化和 Milvus 写入。</div>
          </div>
        </div>
        <div class="admin-meta-list">
          <div class="admin-meta-row">
            <span class="admin-meta-label">启用向量写入</span>
            <el-switch v-model="knowledge.vectorWriteEnabled" />
          </div>
          <div class="admin-meta-row">
            <span class="admin-meta-label">默认 Embedding 模型</span>
            <span class="admin-meta-value">{{ knowledge.embeddingModel }}</span>
          </div>
          <div class="admin-meta-row">
            <span class="admin-meta-label">分块默认大小</span>
            <span class="admin-meta-value">{{ knowledge.maxChars }} chars</span>
          </div>
        </div>
      </div>

      <div class="admin-card">
        <div class="admin-card-head">
          <div>
            <div class="admin-section-title">运营与告警</div>
            <div class="admin-section-hint">控制通知方式和监控阈值。</div>
          </div>
        </div>
        <div class="admin-meta-list">
          <div class="admin-meta-row">
            <span class="admin-meta-label">慢响应告警</span>
            <el-switch v-model="alerts.slowResponse" />
          </div>
          <div class="admin-meta-row">
            <span class="admin-meta-label">失败任务通知</span>
            <el-switch v-model="alerts.failedTask" />
          </div>
          <div class="admin-meta-row">
            <span class="admin-meta-label">告警通道</span>
            <span class="admin-meta-value">{{ alerts.channel }}</span>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { reactive } from 'vue'

const knowledge = reactive({
  vectorWriteEnabled: true,
  embeddingModel: 'text-embedding-3-large',
  maxChars: 2000
})

const alerts = reactive({
  slowResponse: true,
  failedTask: true,
  channel: '企业微信 + 邮件'
})
</script>
