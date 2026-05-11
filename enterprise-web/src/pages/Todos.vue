<template>
  <div>
    <div style="display:flex;justify-content:space-between;margin-bottom:16px">
      <h2>我的待办</h2>
      <el-button type="primary" @click="dialogVisible=true">新建待办</el-button>
    </div>
    <el-table :data="todos" stripe>
      <el-table-column width="50"><template #default="{row}"><el-checkbox v-model="row.done" /></template></el-table-column>
      <el-table-column prop="title" label="标题" />
      <el-table-column prop="priority" label="优先级" width="80">
        <template #default="{row}"><el-tag :type="row.priority==='紧急'?'danger':row.priority==='重要'?'warning':'info'">{{ row.priority }}</el-tag></template>
      </el-table-column>
      <el-table-column prop="dueDate" label="截止时间" width="120" />
      <el-table-column prop="status" label="状态" width="80">
        <template #default="{row}"><el-tag :type="row.done?'success':'info'">{{ row.done?'已完成':'待处理' }}</el-tag></template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" title="新建待办" width="400px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="标题"><el-input v-model="form.title" /></el-form-item>
        <el-form-item label="优先级"><el-select v-model="form.priority"><el-option v-for="p in ['普通','重要','紧急']" :key="p" :label="p" :value="p" /></el-select></el-form-item>
        <el-form-item label="截止时间"><el-date-picker v-model="form.dueDate" type="date" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="dialogVisible=false">取消</el-button><el-button type="primary" @click="dialogVisible=false">创建</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref } from 'vue'
const dialogVisible = ref(false)
const form = ref({ title: '', priority: '普通', dueDate: '' })
const todos = ref([
  { title: '完成Q2工作总结报告', priority: '紧急', dueDate: '2026-05-15', done: false },
  { title: '审核新员工入职资料', priority: '普通', dueDate: '2026-05-18', done: false },
  { title: '更新知识库文档分类', priority: '重要', dueDate: '2026-05-12', done: true },
  { title: '反馈项目进度给负责人', priority: '紧急', dueDate: '2026-05-14', done: false },
  { title: '预约下周项目评审会议', priority: '普通', dueDate: '2026-05-20', done: false },
])
</script>
