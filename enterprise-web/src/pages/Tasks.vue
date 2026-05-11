<template>
  <div>
    <div style="display:flex;justify-content:space-between;margin-bottom:16px">
      <h2>任务协同</h2>
      <el-button type="primary" @click="dialogVisible=true">新建任务</el-button>
    </div>
    <el-row :gutter="16">
      <el-col :span="6" v-for="col in columns" :key="col.title">
        <el-card :header="col.title" shadow="hover">
          <div v-for="task in col.tasks" :key="task.id" style="padding:10px;margin:6px 0;background:#f5f7fa;border-radius:6px;cursor:pointer" @click="detail=task;detailVisible=true">
            <div style="font-weight:bold;font-size:14px">{{ task.title }}</div>
            <div style="font-size:12px;color:#909399;margin-top:4px">{{ task.assignee }} · {{ task.dueDate }}</div>
            <el-tag size="small" :type="task.priority==='高'?'danger':'info'">{{ task.priority }}</el-tag>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-dialog v-model="detailVisible" title="任务详情" width="500px">
      <div v-if="detail">
        <p><b>标题：</b>{{ detail.title }}</p>
        <p><b>负责人：</b>{{ detail.assignee }}</p>
        <p><b>截止时间：</b>{{ detail.dueDate }}</p>
        <p><b>状态：</b>{{ detail.status }}</p>
        <p><b>评论：</b></p>
        <div v-for="(c,i) in detail.comments" :key="i" style="padding:4px 0;font-size:13px">💬 {{ c }}</div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref } from 'vue'
const dialogVisible = ref(false)
const detailVisible = ref(false)
const detail = ref(null)
const columns = ref([
  { title: '待开始 (2)', tasks: [
    { id: 1, title: '系统架构升级方案', assignee: '张三', dueDate: '2026-05-20', priority: '高', status: '待开始', comments: ['架构师已开始评估'] },
    { id: 2, title: '数据库迁移计划', assignee: '李四', dueDate: '2026-05-25', priority: '中', status: '待开始', comments: [] },
  ]},
  { title: '进行中 (1)', tasks: [
    { id: 3, title: '知识库检索优化', assignee: '王五', dueDate: '2026-05-15', priority: '高', status: '进行中', comments: ['已完成向量索引优化', '正在调试混合检索'] },
  ]},
  { title: '待确认 (1)', tasks: [
    { id: 4, title: '前端性能优化', assignee: '赵六', dueDate: '2026-05-10', priority: '中', status: '待确认', comments: ['首屏加载已优化到1.2s', '请产品确认'] },
  ]},
  { title: '已完成 (1)', tasks: [
    { id: 5, title: '文档上传流程优化', assignee: '张三', dueDate: '2026-05-05', priority: '中', status: '已完成', comments: ['已上线'] },
  ]},
])
</script>
