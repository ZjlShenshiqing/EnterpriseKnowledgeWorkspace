<template>
  <div>
    <h2 style="margin-bottom:20px">工作台</h2>
    <el-row :gutter="20">
      <el-col :span="6" v-for="card in cards" :key="card.title">
        <el-card shadow="hover" @click="$router.push(card.path)" style="cursor:pointer;margin-bottom:16px">
          <div style="text-align:center;padding:20px 0">
            <el-icon :size="40" :color="card.color"><component :is="card.icon" /></el-icon>
            <div style="font-size:18px;font-weight:bold;margin:12px 0">{{ card.title }}</div>
            <div style="font-size:28px;color:#409eff;font-weight:bold">{{ card.count }}</div>
            <div style="color:#909399;font-size:13px">{{ card.desc }}</div>
          </div>
        </el-card>
      </el-col>
    </el-row>
    <el-row :gutter="20" style="margin-top:16px">
      <el-col :span="12">
        <el-card header="最近文档">
          <el-table :data="recentDocs" size="small" @row-click="(row) => $router.push('/knowledge/documents')">
            <el-table-column prop="title" label="标题" />
            <el-table-column prop="fileType" label="类型" width="100" />
            <el-table-column prop="createdAt" label="时间" width="160" />
          </el-table>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card header="今日待办">
          <div v-for="todo in todos" :key="todo.id" style="padding:8px 0;border-bottom:1px solid #ebeef5">
            <el-checkbox v-model="todo.done">{{ todo.title }}</el-checkbox>
            <span style="float:right;color:#f56c6c;font-size:12px" v-if="todo.urgent">紧急</span>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getDocuments } from '../api'

const cards = ref([
  { title: '知识文档', count: 0, desc: '企业管理知识库', icon: 'Document', color: '#409eff', path: '/knowledge/documents' },
  { title: '会议预约', count: 3, desc: '今日会议', icon: 'Calendar', color: '#67c23a', path: '/meetings' },
  { title: '我的待办', count: 5, desc: '待处理事项', icon: 'List', color: '#e6a23c', path: '/todos' },
  { title: '任务协同', count: 1, desc: '进行中任务', icon: 'Aim', color: '#f56c6c', path: '/tasks' },
])

const recentDocs = ref([])
const todos = ref([
  { id: 1, title: '完成Q2工作总结报告', done: false, urgent: true },
  { id: 2, title: '审核新员工入职资料', done: false, urgent: false },
  { id: 3, title: '更新知识库文档分类', done: true, urgent: false },
  { id: 4, title: '反馈项目进度给负责人', done: false, urgent: true },
  { id: 5, title: '预约下周项目评审会议', done: false, urgent: false },
])

onMounted(async () => {
  try {
    const res = await getDocuments({ current: 1, size: 5 })
    recentDocs.value = res.data.data?.records || []
    cards.value[0].count = res.data.data?.total || 0
  } catch (e) { console.log('使用 mock 数据') }
})
</script>
