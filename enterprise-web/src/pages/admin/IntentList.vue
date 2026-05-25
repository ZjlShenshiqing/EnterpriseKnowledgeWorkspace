<template>
  <div class="admin-view">
    <section class="admin-page-header">
      <div>
        <el-breadcrumb separator="/" style="margin-bottom: 12px">
          <el-breadcrumb-item>首页</el-breadcrumb-item>
          <el-breadcrumb-item>意图管理</el-breadcrumb-item>
          <el-breadcrumb-item>意图列表</el-breadcrumb-item>
        </el-breadcrumb>
        <div class="admin-page-title">意图列表</div>
        <div class="admin-page-subtitle">支持多维筛选、分页查看和快速定位到意图树节点</div>
      </div>
    </section>

    <section class="admin-table-card">
      <div class="admin-toolbar">
        <div class="admin-filters" style="width: 100%">
          <el-input v-model="keyword" placeholder="搜索意图名称/ID" style="width: 240px" clearable>
            <template #prefix><Search /></template>
          </el-input>
          <el-select v-model="filterLevel" placeholder="全部层级" clearable style="width: 140px; margin-left: 12px">
            <el-option :value="1" label="场景" />
            <el-option :value="2" label="意图" />
          </el-select>
          <el-select v-model="filterType" placeholder="全部类型" clearable style="width: 140px; margin-left: 12px">
            <el-option value="keyword" label="关键词" />
            <el-option value="regex" label="正则表达式" />
          </el-select>
          <el-select v-model="filterStatus" placeholder="全部状态" clearable style="width: 140px; margin-left: 12px">
            <el-option :value="1" label="启用" />
            <el-option :value="0" label="停用" />
          </el-select>
          <el-select v-model="filterParent" placeholder="全部父节点" clearable style="width: 180px; margin-left: 12px">
            <el-option v-for="node in rootNodes" :key="node.id" :label="node.name" :value="node.id" />
          </el-select>
          <el-button style="margin-left: 12px" @click="refreshData">
            <template #icon><Refresh /></template>
            刷新
          </el-button>
          <el-button style="margin-left: 8px" @click="clearFilters">
            <template #icon><Close /></template>
            清空筛选
          </el-button>
        </div>
      </div>

      <el-table :data="filteredList" v-loading="loading" style="width: 100%">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="name" label="意图名称" min-width="160" />
        <el-table-column label="层级" width="100">
          <template #default="{ row }">
            <el-tag :type="row.level === 1 ? 'primary' : 'success'" size="small">
              {{ row.level === 1 ? '场景' : '意图' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="父节点" width="160">
          <template #default="{ row }">{{ getParentName(row.parentId) }}</template>
        </el-table-column>
        <el-table-column label="规则数" width="100">
          <template #default="{ row }">{{ row.ruleCount || 0 }}</template>
        </el-table-column>
        <el-table-column label="关联知识库" min-width="180">
          <template #default="{ row }">{{ row.kbNames || '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'info'" size="small">
              {{ row.enabled ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="updatedAt" label="更新时间" width="180" />
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="goToTree(row)">查看</el-button>
            <el-button size="small" type="danger" @click="deleteNode(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div v-if="filteredList.length === 0" class="table-empty">
        <div class="empty-icon">🌳</div>
        <div class="empty-text">暂无意图节点，请先在意图树配置中创建</div>
      </div>

      <div style="display: flex; justify-content: flex-end; margin-top: 16px">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="total"
          layout="total, sizes, prev, pager, next"
          @size-change="loadData"
          @current-change="loadData"
        />
      </div>
    </section>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Close } from '@element-plus/icons-vue'
import { getAuthHeaders, forceLogout } from '../../api'

const router = useRouter()
const tree = ref([])
const list = ref([])
const loading = ref(false)

const keyword = ref('')
const filterLevel = ref(null)
const filterType = ref(null)
const filterStatus = ref(null)
const filterParent = ref(null)

const currentPage = ref(1)
const pageSize = ref(20)
const total = ref(0)

const rootNodes = computed(() => {
  return tree.value.filter(node => node.level === 1)
})

const filteredList = computed(() => {
  let result = [...list.value]

  if (keyword.value.trim()) {
    const kw = keyword.value.trim().toLowerCase()
    result = result.filter(node => 
      node.name.toLowerCase().includes(kw) || 
      String(node.id).includes(kw)
    )
  }

  if (filterLevel.value !== null) {
    result = result.filter(node => node.level === filterLevel.value)
  }

  if (filterStatus.value !== null) {
    result = result.filter(node => node.enabled === filterStatus.value)
  }

  if (filterParent.value) {
    result = result.filter(node => node.parentId === filterParent.value)
  }

  total.value = result.length
  const start = (currentPage.value - 1) * pageSize.value
  const end = start + pageSize.value
  return result.slice(start, end)
})

function auth() { return { ...getAuthHeaders(), 'Content-Type': 'application/json' } }

async function loadData() {
  loading.value = true
  try {
    const r = await fetch('/api/intents/nodes', { headers: auth() })
    const body = await r.json()
    if (body.code === 40100) { forceLogout(); return }
    if (body.code === 200) {
      tree.value = body.data || []
      flattenTree(body.data || [])
    } else {
      ElMessage.error(body.message || '加载失败')
    }
  } catch (e) {
    ElMessage.error('网络请求失败')
  } finally {
    loading.value = false
  }
}

function flattenTree(nodes, parentName = null) {
  const result = []
  const walk = (nodes, parentName = null) => {
    for (const node of nodes) {
      result.push({
        id: node.id,
        name: node.name,
        level: node.level,
        parentId: node.parentId,
        parentName: parentName || '-',
        enabled: node.enabled,
        ruleCount: node.rules?.length || 0,
        kbNames: node.kbRels?.map(rel => getKbName(rel.kbId)).join(', ') || '-',
        updatedAt: node.updatedAt,
        rules: node.rules || [],
        kbRels: node.kbRels || []
      })
      if (node.children && node.children.length) {
        walk(node.children, node.name)
      }
    }
  }
  walk(nodes, parentName)
  list.value = result
}

function getKbName(kbId) {
  return `知识库#${kbId}`
}

function getParentName(parentId) {
  if (!parentId) return '-'
  const find = (nodes) => {
    for (const node of nodes) {
      if (node.id === parentId) return node.name
      if (node.children && node.children.length) {
        const found = find(node.children)
        if (found) return found
      }
    }
    return '-'
  }
  return find(tree.value)
}

function refreshData() {
  currentPage.value = 1
  loadData()
}

function clearFilters() {
  keyword.value = ''
  filterLevel.value = null
  filterType.value = null
  filterStatus.value = null
  filterParent.value = null
  currentPage.value = 1
}

function goToTree(row) {
  router.push('/admin/intent-config')
}

async function deleteNode(row) {
  await ElMessageBox.confirm(
    `确定删除意图"${row.name}"吗？将级联删除所有子节点、规则和知识库关联。`,
    '确认删除',
    { type: 'warning', confirmButtonText: '确定删除', cancelButtonText: '取消' }
  )
  try {
    const r = await fetch(`/api/intents/nodes/${row.id}`, { method: 'DELETE', headers: auth() })
    const body = await r.json()
    if (body.code === 200) {
      ElMessage.success('删除成功')
      loadData()
    } else {
      ElMessage.error(body.message || '删除失败')
    }
  } catch (e) {
    ElMessage.error('删除失败')
  }
}

onMounted(() => { loadData() })
</script>

<style scoped>
.empty-icon {
  font-size: 24px;
  margin-bottom: 8px;
}

.empty-text {
  color: #999;
  font-size: 13px;
}

.table-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 60px 20px;
  color: #999;
}
</style>
