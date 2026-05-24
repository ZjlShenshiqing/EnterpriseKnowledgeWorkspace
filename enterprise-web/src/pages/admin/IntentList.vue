<template>
  <div class="admin-view">
    <section class="admin-page-header">
      <div>
        <div class="admin-page-title">意图列表</div>
        <div class="admin-page-subtitle">查看每个意图的规则、关联知识库和状态。</div>
      </div>
    </section>
    <section class="admin-table-card">
      <el-table :data="rows" v-loading="loading">
        <el-table-column prop="name" label="意图名称" min-width="150" />
        <el-table-column prop="parentName" label="所属场景" min-width="120" />
        <el-table-column label="规则数" width="80">
          <template #default="{ row }">{{ row.ruleCount || 0 }}</template>
        </el-table-column>
        <el-table-column label="关联知识库" min-width="200">
          <template #default="{ row }">{{ row.kbNames || '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <span :style="{color:row.enabled?'#34c759':'#8f959e'}">{{ row.enabled ? '启用' : '停用' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="updatedAt" label="最近更新" width="180" />
      </el-table>
    </section>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getAuthHeaders, forceLogout } from '../../api'

const rows = ref([])
const loading = ref(false)

function auth() { return { ...getAuthHeaders(), 'Content-Type': 'application/json' } }

onMounted(async () => {
  loading.value = true
  const r = await fetch('/api/intents/nodes', { headers: auth() })
  const body = await r.json()
  if (body.code === 40100) { forceLogout(); return }
  const tree = body.data || []
  const flat = []
  function walk(nodes, parentName) {
    for (const n of nodes) {
      flat.push({
        id: n.id,
        name: n.name,
        parentName: parentName || '-',
        level: n.level,
        enabled: n.enabled,
        ruleCount: 0,
        kbNames: '-',
        updatedAt: n.updatedAt
      })
      if (n.children) walk(n.children, n.name)
    }
  }
  walk(tree, null)
  rows.value = flat
  loading.value = false
})
</script>
