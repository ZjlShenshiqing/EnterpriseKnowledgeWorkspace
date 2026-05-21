<template>
  <div class="admin-view">
    <section class="admin-page-header">
      <div>
        <div class="admin-page-title">用户管理</div>
        <div class="admin-page-subtitle">统一管理后台管理员、知识库运营人员和普通业务用户。</div>
      </div>
      <div class="admin-actions">
        <el-button plain>导入用户</el-button>
        <el-button type="primary">新增用户</el-button>
      </div>
    </section>

    <section class="admin-grid-4">
      <article v-for="item in stats" :key="item.label" class="admin-stat">
        <div class="admin-stat-value">{{ item.value }}</div>
        <div class="admin-stat-label">{{ item.label }}</div>
        <div class="admin-stat-meta">{{ item.meta }}</div>
      </article>
    </section>

    <section class="admin-table-card">
      <div class="admin-toolbar">
        <div>
          <div class="admin-toolbar-title">用户清单</div>
          <div class="admin-toolbar-subtitle">查看账号、部门、角色和启停状态。</div>
        </div>
        <div class="admin-filters">
          <span class="admin-chip">全部部门</span>
          <span class="admin-chip">仅启用账号</span>
        </div>
      </div>
      <el-table :data="users">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="username" label="用户名" min-width="120" />
        <el-table-column prop="realName" label="姓名" min-width="120" />
        <el-table-column prop="department" label="部门" min-width="120" />
        <el-table-column prop="roles" label="角色" min-width="160" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'danger'" size="small">{{ row.enabled ? '启用' : '禁用' }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </section>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'

const users = ref([
  { id: 1, username: 'admin', realName: '系统管理员', department: '技术部', roles: '系统管理员', enabled: true },
  { id: 2, username: 'zhangsan', realName: '张三', department: '技术部', roles: '知识库运营', enabled: true },
  { id: 3, username: 'lisi', realName: '李四', department: '产品部', roles: '流程管理员', enabled: true },
  { id: 4, username: 'wangwu', realName: '王五', department: '设计部', roles: '普通员工', enabled: false }
])

const stats = computed(() => [
  { label: '总用户数', value: users.value.length, meta: '后台当前管理的账号总数' },
  { label: '启用账号', value: users.value.filter(item => item.enabled).length, meta: '可正常登录与使用' },
  { label: '管理员', value: users.value.filter(item => item.roles.includes('管理员')).length, meta: '具备后台操作权限' },
  { label: '停用账号', value: users.value.filter(item => !item.enabled).length, meta: '已冻结或待恢复' }
])
</script>
