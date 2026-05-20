<template>
  <div style="height:100vh;display:flex;flex-direction:column">
    <div style="height:52px;background:var(--bg-sidebar);display:flex;align-items:center;padding:0 20px;color:#fff;font-size:15px;font-weight:600;flex-shrink:0">
      管理后台
      <el-button style="margin-left:auto" text size="small" @click="$router.push('/')" color="#c9cdd4">← 返回工作台</el-button>
    </div>
    <div style="flex:1;display:flex;overflow:hidden">
      <div style="width:200px;background:var(--white);border-right:1px solid var(--border-default);padding-top:8px;flex-shrink:0">
        <el-menu :default-active="route.path" router style="border-right:none">
          <el-menu-item index="/admin/documents"><el-icon><Document /></el-icon> 文档管理</el-menu-item>
          <el-menu-item index="/admin/bases"><el-icon><Folder /></el-icon> 知识库管理</el-menu-item>
          <el-menu-item index="/admin/analytics"><el-icon><DataAnalysis /></el-icon> 数据看板</el-menu-item>
          <el-menu-item index="/admin/users"><el-icon><User /></el-icon> 用户管理</el-menu-item>
          <el-menu-item index="/admin/roles"><el-icon><Lock /></el-icon> 角色管理</el-menu-item>
          <el-menu-item index="/admin/logs"><el-icon><Tickets /></el-icon> 操作日志</el-menu-item>
        </el-menu>
      </div>
      <div style="flex:1;overflow-y:auto;padding:24px;background:var(--bg-body)">
        <router-view />
      </div>
    </div>
  </div>
</template>

<script setup>
import { useRoute } from 'vue-router'
const route = useRoute()
</script>
