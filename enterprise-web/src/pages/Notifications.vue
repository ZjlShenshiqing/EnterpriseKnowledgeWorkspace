<template>
  <div style="background:#fff;border-radius:10px;padding:16px">
    <div style="display:flex;justify-content:space-between;margin-bottom:16px">
      <span style="font-size:16px;font-weight:600">消息通知</span>
      <el-button @click="items.forEach(n=>n.read=true)">全部已读</el-button>
    </div>
    <div v-for="n in items" :key="n.id" style="padding:14px 0;border-bottom:1px solid #f0f0f0;display:flex;align-items:center;cursor:pointer" @click="n.read=true">
      <el-badge :is-dot="!n.read" style="margin-right:14px" />
      <div style="flex:1"><div style="font-size:14px;font-weight:500">{{n.title}}</div><div style="font-size:13px;color:#909399;margin-top:4px">{{n.content}}</div></div>
      <span style="font-size:12px;color:#c0c4cc">{{n.time}}</span>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
const items=ref([
  {id:1,title:'会议通知',content:'Q2项目评审会将于5月12日14:00在A301举行',time:'10分钟前',read:false},
  {id:2,title:'任务分配',content:'张三将系统架构升级方案分配给你',time:'1小时前',read:false},
  {id:3,title:'文档更新',content:'知识库《微服务架构设计指南》分块完成',time:'3小时前',read:true},
  {id:4,title:'待办提醒',content:'审核新员工入职资料截止时间为5月18日',time:'昨天',read:true},
  {id:5,title:'系统通知',content:'系统将于5月15日进行维护升级',time:'2天前',read:true},
])
</script>
