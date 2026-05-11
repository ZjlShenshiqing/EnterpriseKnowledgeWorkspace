<template>
  <div>
    <h2 style="margin-bottom:16px">系统管理</h2>
    <el-tabs v-model="activeTab">
      <el-tab-pane label="用户管理" name="users">
        <el-table :data="users" stripe>
          <el-table-column prop="id" label="ID" width="80" />
          <el-table-column prop="username" label="用户名" />
          <el-table-column prop="realName" label="姓名" width="100" />
          <el-table-column prop="department" label="部门" width="120" />
          <el-table-column prop="roles" label="角色" width="150" />
          <el-table-column prop="status" label="状态" width="80">
            <template #default="{row}"><el-tag :type="row.status==='启用'?'success':'danger'">{{ row.status }}</el-tag></template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
      <el-tab-pane label="角色管理" name="roles">
        <el-table :data="roles" stripe>
          <el-table-column prop="code" label="编码" />
          <el-table-column prop="name" label="名称" />
          <el-table-column prop="permissions" label="权限数" width="100" />
        </el-table>
      </el-tab-pane>
      <el-tab-pane label="操作日志" name="logs">
        <el-table :data="logs" stripe>
          <el-table-column prop="user" label="操作人" width="100" />
          <el-table-column prop="action" label="操作" width="150" />
          <el-table-column prop="path" label="路径" />
          <el-table-column prop="time" label="时间" width="170" />
        </el-table>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { ref } from 'vue'

const activeTab = ref('users')

const users = ref([
  { id: 1, username: 'admin', realName: '系统管理员', department: '技术部', roles: '系统管理员', status: '启用' },
  { id: 2, username: 'zhangsan', realName: '张三', department: '技术部', roles: '部门主管', status: '启用' },
  { id: 3, username: 'lisi', realName: '李四', department: '产品部', roles: '普通员工', status: '启用' },
  { id: 4, username: 'wangwu', realName: '王五', department: '设计部', roles: '普通员工', status: '禁用' },
])

const roles = ref([
  { code: 'admin', name: '系统管理员', permissions: 15 },
  { code: 'manager', name: '部门主管', permissions: 10 },
  { code: 'user', name: '普通员工', permissions: 5 },
])

const logs = ref([
  { user: 'admin', action: 'CREATE_USER', path: '/api/system/users', time: '2026-05-11 14:30:00' },
  { user: 'admin', action: 'CREATE_ROLE', path: '/api/system/roles', time: '2026-05-11 10:15:00' },
  { user: 'zhangsan', action: 'UPLOAD_DOC', path: '/api/kb/documents/upload', time: '2026-05-10 16:00:00' },
])
</script>
