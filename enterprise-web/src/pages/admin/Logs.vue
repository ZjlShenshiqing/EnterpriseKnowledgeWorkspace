<template>
  <div>
    <span style="font-size:16px;font-weight:600;display:block;margin-bottom:16px">操作日志</span>
    <el-table :data="logs" stripe>
      <el-table-column prop="user" label="操作人" width="100" />
      <el-table-column prop="action" label="操作类型" width="150" />
      <el-table-column prop="path" label="路径" min-width="200" />
      <el-table-column prop="detail" label="详情" width="120" />
      <el-table-column prop="time" label="时间" width="170" />
    </el-table>
  </div>
</template>

<script setup>
import { ref } from 'vue'
const logs=ref([
  {user:'admin',action:'CREATE_USER',path:'/api/system/users',detail:'新增用户zhangsan',time:'2026-05-11 14:30'},
  {user:'admin',action:'CREATE_ROLE',path:'/api/system/roles',detail:'新增角色部门主管',time:'2026-05-11 10:15'},
  {user:'zhangsan',action:'UPLOAD_DOC',path:'/api/kb/documents/upload',detail:'上传技术规划.pdf',time:'2026-05-10 16:00'},
  {user:'admin',action:'DELETE_DOC',path:'/api/kb/documents/99',detail:'删除过期文档',time:'2026-05-09 09:30'},
])
</script>
