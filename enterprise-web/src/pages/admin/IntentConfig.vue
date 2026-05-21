<template>
  <div class="admin-view">
    <section class="admin-page-header">
      <div>
        <div class="admin-page-title">意图树配置</div>
        <div class="admin-page-subtitle">维护问答意图层级、路由条件和知识库归属关系。</div>
      </div>
      <div class="admin-actions">
        <el-button plain>导入配置</el-button>
        <el-button type="primary">新增节点</el-button>
      </div>
    </section>

    <section class="admin-grid-3">
      <div class="admin-card">
        <div class="admin-card-head">
          <div>
            <div class="admin-section-title">树节点</div>
            <div class="admin-section-hint">当前线上问答意图树。</div>
          </div>
        </div>
        <div class="admin-list">
          <div v-for="item in nodes" :key="item.name" class="admin-list-item">
            <div class="admin-list-title">{{ item.name }}</div>
            <div class="admin-list-copy">{{ item.desc }}</div>
          </div>
        </div>
      </div>

      <div class="admin-card">
        <div class="admin-card-head">
          <div>
            <div class="admin-section-title">命中规则</div>
            <div class="admin-section-hint">配置关键词、槽位和优先级。</div>
          </div>
        </div>
        <div class="admin-meta-list">
          <div v-for="rule in rules" :key="rule.label" class="admin-meta-row">
            <span class="admin-meta-label">{{ rule.label }}</span>
            <span class="admin-meta-value">{{ rule.value }}</span>
          </div>
        </div>
      </div>

      <div class="admin-card">
        <div class="admin-card-head">
          <div>
            <div class="admin-section-title">发布状态</div>
            <div class="admin-section-hint">当前意图树版本及发布窗口。</div>
          </div>
        </div>
        <div class="admin-list">
          <div class="admin-list-item">
            <div class="admin-list-title">production-v12</div>
            <div class="admin-list-copy">最近发布时间：2026-05-20 21:10 · 发布人：admin</div>
          </div>
          <div class="admin-list-item">
            <div class="admin-list-title">灰度策略</div>
            <div class="admin-list-copy">对客服租户开放 30% 流量，技术租户全量开启。</div>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
const nodes = [
  { name: '知识问答', desc: '通用知识查询、制度说明和文档解读入口。' },
  { name: '工单协助', desc: '售后、客服工单和流转动作的意图分支。' },
  { name: '内部流程', desc: '请假、报销、会议室等流程类咨询入口。' }
]

const rules = [
  { label: '关键词命中', value: '182 条' },
  { label: '正则规则', value: '26 条' },
  { label: '槽位抽取', value: '14 个' },
  { label: 'Fallback 意图', value: '未找到明确依据' }
]
</script>
