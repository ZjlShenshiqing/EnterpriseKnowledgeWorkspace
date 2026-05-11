<template>
  <div>
    <div style="display:flex;justify-content:space-between;margin-bottom:16px">
      <span style="font-size:16px;font-weight:600">用户管理</span>
      <el-button type="primary">新增用户</el-button>
    </div>
    <el-table :data="users" stripe>
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="username" label="用户名" width="110" />
      <el-table-column prop="realName" label="姓名" width="100" />
      <el-table-column prop="department" label="部门" width="100" />
      <el-table-column prop="roles" label="角色" />
      <el-table-column label="状态" width="80"><template #default="{row}"><el-tag :type="row.enabled?'success':'danger'" size="small">{{row.enabled?'启用':'禁用'}}</el-tag></template></el-table-column>
      <el-table-column label="操作" width="120"><template #default><el-button size="small">编辑</el-button><el-button size="small" type="danger">禁用</el-button></template></el-table-column>
    </el-table>
  </div>
</template>

<script setup>
import { ref } from 'vue'
const users=ref([
  {id:1,username:'admin',realName:'系统管理员',department:'技术部',roles:'系统管理员',enabled:true},
  {id:2,username:'zhangsan',realName:'张三',department:'技术部',roles:'部门主管',enabled:true},
  {id:3,username:'lisi',realName:'李四',department:'产品部',roles:'普通员工',enabled:true},
  {id:4,username:'wangwu',realName:'王五',department:'设计部',roles:'普通员工',enabled:false},
])
</script>
