<template>
  <div>
    <div style="display:flex;justify-content:space-between;margin-bottom:16px">
      <h2>消息通知</h2>
      <el-button @click="readAll">全部已读</el-button>
    </div>
    <el-card>
      <div v-for="item in notifications" :key="item.id" style="padding:14px 0;border-bottom:1px solid #ebeef5;display:flex;align-items:center;cursor:pointer" @click="item.read=true">
        <el-badge :is-dot="!item.read" style="margin-right:12px" />
        <div style="flex:1">
          <div style="font-weight:bold">{{ item.title }}</div>
          <div style="font-size:13px;color:#909399;margin-top:4px">{{ item.content }}</div>
        </div>
        <span style="font-size:12px;color:#c0c4cc">{{ item.time }}</span>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref } from 'vue'

const notifications = ref([
  { id: 1, title: '会议通知', content: 'Q2项目评审会将于5月12日14:00在A301举行', time: '10分钟前', read: false },
  { id: 2, title: '任务分配', content: '张三将「系统架构升级方案」分配给你', time: '1小时前', read: false },
  { id: 3, title: '文档更新', content: '知识库《微服务架构设计指南》分块完成', time: '3小时前', read: true },
  { id: 4, title: '待办提醒', content: '「审核新员工入职资料」截止时间为5月18日', time: '昨天', read: true },
  { id: 5, title: '系统通知', content: '系统将于5月15日进行维护升级', time: '2天前', read: true },
])

function readAll() { notifications.value.forEach(n => n.read = true) }
</script>
