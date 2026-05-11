<template>
  <el-container style="height:100vh">
    <el-header style="height:56px;background:#409eff;display:flex;align-items:center;padding:0 20px">
      <span style="color:#fff;font-size:18px;font-weight:bold">企业智能工作平台</span>
      <div style="margin-left:auto;color:#fff;display:flex;align-items:center;gap:12px">
        <span>管理员</span>
        <el-avatar :size="32" />
      </div>
    </el-header>
    <el-container>
      <el-aside width="220px" style="background:#f5f7fa;border-right:1px solid #e4e7ed">
        <el-menu :default-active="activeMenu" router style="border-right:none" @select="handleSelect">
          <el-menu-item index="/">
            <el-icon><Monitor /></el-icon><span>工作台</span>
          </el-menu-item>
          <el-sub-menu index="knowledge">
            <template #title><el-icon><Document /></el-icon><span>知识库</span></template>
            <el-menu-item index="/knowledge/documents">文档管理</el-menu-item>
            <el-menu-item index="/knowledge/bases">知识库设置</el-menu-item>
          </el-sub-menu>
          <el-sub-menu index="ai">
            <template #title><el-icon><ChatDotRound /></el-icon><span>智能助手</span></template>
            <el-menu-item index="/agent/chat">智能对话</el-menu-item>
            <el-menu-item index="/qa/ask">智能问答</el-menu-item>
          </el-sub-menu>
          <el-menu-item index="/meetings">
            <el-icon><Calendar /></el-icon><span>会议预约</span>
          </el-menu-item>
          <el-menu-item index="/todos">
            <el-icon><List /></el-icon><span>我的待办</span>
          </el-menu-item>
          <el-menu-item index="/tasks">
            <el-icon><Aim /></el-icon><span>任务协同</span>
          </el-menu-item>
          <el-menu-item index="/notifications">
            <el-icon><Bell /></el-icon><span>消息通知</span>
          </el-menu-item>
          <el-menu-item index="/admin/users">
            <el-icon><Setting /></el-icon><span>系统管理</span>
          </el-menu-item>
        </el-menu>
      </el-aside>
      <el-main style="background:#f0f2f5;padding:20px">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
const route = useRoute()
const activeMenu = computed(() => route.path)
</script>
