<template>
  <div>
    <div style="display:flex;justify-content:space-between;margin-bottom:16px">
      <span style="font-size:16px;font-weight:600">角色管理</span>
      <el-button type="primary">新增角色</el-button>
    </div>
    <el-table :data="roles" stripe>
      <el-table-column prop="code" label="角色编码" width="140" />
      <el-table-column prop="name" label="角色名称" />
      <el-table-column prop="permissions" label="权限数" width="80" />
      <el-table-column prop="users" label="用户数" width="80" />
      <el-table-column label="操作" width="120"><template #default><el-button size="small">编辑</el-button><el-button size="small" type="danger">删除</el-button></template></el-table-column>
    </el-table>
  </div>
</template>

<script setup>
import { ref } from 'vue'
const roles=ref([
  {code:'admin',name:'系统管理员',permissions:15,users:1},
  {code:'manager',name:'部门主管',permissions:10,users:3},
  {code:'user',name:'普通员工',permissions:5,users:25},
])
</script>
