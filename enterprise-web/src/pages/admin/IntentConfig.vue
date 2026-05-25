<template>
  <div class="admin-view">
    <section class="admin-page-header">
      <div>
        <el-breadcrumb separator="/" style="margin-bottom: 12px">
          <el-breadcrumb-item>首页</el-breadcrumb-item>
          <el-breadcrumb-item>意图管理</el-breadcrumb-item>
          <el-breadcrumb-item>意图树配置</el-breadcrumb-item>
        </el-breadcrumb>
        <div class="admin-page-title">意图树配置</div>
        <div class="admin-page-subtitle">配置意图层级、类型和节点关系</div>
      </div>
      <div class="admin-actions">
        <el-button @click="refreshTree">
          <template #icon><Refresh /></template>
          刷新
        </el-button>
        <el-button type="primary" @click="addRoot">
          <template #icon><Plus /></template>
          新建根节点
        </el-button>
      </div>
    </section>

    <section class="admin-grid-2">
      <article class="admin-card">
        <div class="admin-card-header">
          <div class="admin-card-title">意图树结构</div>
          <div class="admin-card-subtitle">点击节点查看详情或进行编辑</div>
        </div>
        <div class="admin-card-body" style="min-height: 400px">
          <el-tree
            v-if="tree.length > 0"
            :data="tree"
            :highlight-current="true"
            :default-expanded-keys="expandedIds"
            @node-click="handleNodeClick"
            @node-expand="handleNodeExpand"
            @node-collapse="handleNodeCollapse"
            node-key="id"
            class="intent-tree"
          >
            <template #default="{ node, data }">
              <span class="tree-node-content">
                <span class="node-icon">
                  <Folder v-if="data.children && data.children.length > 0" />
                  <Document v-else />
                </span>
                <span class="node-name">{{ data.name }}</span>
                <el-tag v-if="!data.enabled" size="small" type="info">停用</el-tag>
              </span>
            </template>
          </el-tree>
          <div v-else class="empty-state">
            <div class="empty-icon">🌳</div>
            <div class="empty-text">暂无节点，请先创建</div>
          </div>
        </div>
      </article>

      <article class="admin-card">
        <div class="admin-card-header">
          <div class="admin-card-title">节点详情</div>
          <div class="admin-card-subtitle">查看并管理当前选择的节点</div>
        </div>
        <div class="admin-card-body" style="min-height: 400px">
          <div v-if="!selectedNode" class="empty-state">
            <div class="empty-icon">👈</div>
            <div class="empty-text">请选择左侧节点</div>
          </div>
          <template v-else>
            <el-form :model="form" label-width="90px" style="max-width: 100%">
              <el-form-item label="节点名称">
                <el-input v-model="form.name" placeholder="请输入节点名称" />
              </el-form-item>
              <el-form-item label="层级">
                <el-select v-model="form.level" style="width: 100%">
                  <el-option :value="1" label="场景" />
                  <el-option :value="2" label="意图" />
                </el-select>
              </el-form-item>
              <el-form-item label="排序">
                <el-input-number v-model="form.sortOrder" :min="0" :max="999" style="width: 100%" />
              </el-form-item>
              <el-form-item label="描述">
                <el-input v-model="form.description" type="textarea" :rows="2" placeholder="请输入描述（可选）" />
              </el-form-item>
              <el-form-item label="状态">
                <el-switch v-model="form.enabled" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="停用" />
              </el-form-item>
              <el-form-item>
                <div style="display: flex; gap: 10px">
                  <el-button type="primary" @click="saveNode" :loading="savingNode">保存</el-button>
                  <el-button type="success" @click="addChildNode" v-if="selectedNode.level === 1">新增子意图</el-button>
                  <el-button type="danger" @click="deleteSelected" :disabled="hasChildren(selectedNode.id)">删除</el-button>
                </div>
                <div v-if="hasChildren(selectedNode.id)" style="color: #999; font-size: 12px; margin-top: 8px">请先删除子节点</div>
              </el-form-item>
            </el-form>

            <el-divider style="margin: 20px 0" />

            <div class="section-title">匹配规则</div>
            <div style="margin-bottom: 12px; display: flex; justify-content: flex-end">
              <el-button type="primary" size="small" @click="openAddRule">
                <template #icon><Plus /></template>
                新增规则
              </el-button>
            </div>
            <el-table :data="rules" v-loading="loadingRules" border size="small" style="width: 100%">
              <el-table-column prop="ruleType" label="类型" width="100">
                <template #default="{ row }">
                  <el-tag :type="row.ruleType === 'keyword' ? 'success' : 'warning'">
                    {{ row.ruleType === 'keyword' ? '关键词' : '正则' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="expression" label="表达式" min-width="150">
                <template #default="{ row }">
                  <code style="color: #666">{{ row.expression }}</code>
                </template>
              </el-table-column>
              <el-table-column prop="weight" label="权重" width="80">
                <template #default="{ row }">
                  <el-input-number v-model="row.weight" :min="0.1" :max="10" :step="0.1" style="width: 70px" />
                </template>
              </el-table-column>
              <el-table-column prop="enabled" label="状态" width="80">
                <template #default="{ row }">
                  <el-switch :value="row.enabled === 1" />
                </template>
              </el-table-column>
              <el-table-column label="操作" width="120">
                <template #default="{ row }">
                  <el-button size="small" @click="editRule(row)">编辑</el-button>
                  <el-button size="small" type="danger" @click="deleteRule(row.id)">删除</el-button>
                </template>
              </el-table-column>
            </el-table>
            <div v-if="rules.length === 0" class="table-empty">
              <div class="empty-text">暂无规则</div>
            </div>

            <el-divider style="margin: 20px 0" />

            <div class="section-title">关联知识库</div>
            <div style="margin-bottom: 12px; display: flex; justify-content: flex-end">
              <el-select v-model="newKbId" placeholder="选择知识库" style="width: 200px" size="small">
                <el-option v-for="kb in availableKbs" :key="kb.id" :label="kb.name" :value="kb.id" />
              </el-select>
              <el-button type="primary" size="small" @click="bindKb" style="margin-left: 10px" :disabled="!newKbId">
                绑定
              </el-button>
            </div>
            <el-table v-if="kbRels.length > 0" :data="kbRels" border size="small" style="width: 100%">
              <el-table-column label="知识库" min-width="150">
                <template #default="{ row }">{{ getKbName(row.kbId) }}</template>
              </el-table-column>
              <el-table-column label="检索权重" width="120">
                <template #default="{ row }">
                  <el-input-number v-model="row.weight" :min="0.1" :max="10" :step="0.1" style="width: 100px" />
                </template>
              </el-table-column>
              <el-table-column label="操作" width="80">
                <template #default="{ row }">
                  <el-button size="small" type="danger" @click="unbindKb(row.id)">解绑</el-button>
                </template>
              </el-table-column>
            </el-table>
            <div v-else class="table-empty">
              <div class="empty-text">暂未关联知识库</div>
            </div>
          </template>
        </div>
      </article>
    </section>

    <!-- 规则编辑弹窗 -->
    <el-dialog v-model="ruleDialogVisible" :title="editingRuleId ? '编辑匹配规则' : '新增匹配规则'" width="480px">
      <el-form :model="ruleForm" label-width="90px">
        <el-form-item label="规则类型">
          <el-select v-model="ruleForm.ruleType" style="width: 100%">
            <el-option value="keyword" label="关键词匹配" />
            <el-option value="regex" label="正则表达式" />
          </el-select>
        </el-form-item>
        <el-form-item label="表达式">
          <el-input v-model="ruleForm.expression" placeholder="请输入关键词或正则表达式" />
          <div v-if="ruleForm.ruleType === 'regex'" style="color: #999; font-size: 12px; margin-top: 4px">
            支持Java正则语法，如：^.*请假.*$
          </div>
        </el-form-item>
        <el-form-item label="权重">
          <el-input-number v-model="ruleForm.weight" :min="0.1" :max="10" :step="0.1" style="width: 100%" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="ruleForm.enabled" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="停用" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="ruleDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveRule" :disabled="!ruleForm.expression.trim()">保存</el-button>
      </template>
    </el-dialog>

    <!-- 新增子节点弹窗 -->
    <el-dialog v-model="addChildDialogVisible" title="新增子意图" width="400px">
      <el-form :model="childForm" label-width="80px">
        <el-form-item label="名称">
          <el-input v-model="childForm.name" placeholder="请输入意图名称" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="childForm.description" placeholder="请输入描述（可选）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addChildDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="createChildNode" :disabled="!childForm.name.trim()">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh, Folder, Document } from '@element-plus/icons-vue'
import { getAuthHeaders, forceLogout } from '../../api'

const tree = ref([])
const selectedNode = ref(null)
const rules = ref([])
const kbRels = ref([])
const kbList = ref([])
const expandedIds = ref([])

const form = ref({ name: '', level: 1, sortOrder: 0, description: '', enabled: 1 })
const ruleForm = ref({ ruleType: 'keyword', expression: '', weight: 1.0, enabled: 1 })
const childForm = ref({ name: '', description: '' })

const ruleDialogVisible = ref(false)
const addChildDialogVisible = ref(false)
const editingRuleId = ref(null)

const savingNode = ref(false)
const loadingRules = ref(false)

const newKbId = ref(null)
const newKbWeight = ref(1.0)

const availableKbs = computed(() => {
  const boundKbIds = kbRels.value.map(r => r.kbId)
  return kbList.value.filter(kb => !boundKbIds.includes(kb.id))
})

function auth() { return { ...getAuthHeaders(), 'Content-Type': 'application/json' } }

async function loadTree() {
  try {
    const r = await fetch('/api/intents/nodes', { headers: auth() })
    const body = await r.json()
    if (body.code === 40100) { forceLogout(); return }
    if (body.code === 200) {
      tree.value = body.data || []
    } else {
      ElMessage.error(body.message || '加载失败')
    }
  } catch (e) {
    ElMessage.error('网络请求失败')
  }
}

function refreshTree() {
  loadTree()
  selectedNode.value = null
}

async function loadKbList() {
  try {
    const r = await fetch('/api/kb/bases', { headers: auth() })
    const body = await r.json()
    if (body.code === 200) {
      kbList.value = body.data?.records || []
    }
  } catch (e) {
    console.error('加载知识库列表失败', e)
  }
}

async function handleNodeClick(data) {
  selectedNode.value = data
  form.value = {
    name: data.name,
    level: data.level,
    sortOrder: data.sortOrder || 0,
    description: data.description || '',
    enabled: data.enabled
  }
  await loadNodeDetail(data.id)
}

function handleNodeExpand(data) {
  if (!expandedIds.value.includes(data.id)) {
    expandedIds.value.push(data.id)
  }
}

function handleNodeCollapse(data) {
  expandedIds.value = expandedIds.value.filter(id => id !== data.id)
}

async function loadNodeDetail(nodeId) {
  loadingRules.value = true
  try {
    const r = await fetch(`/api/intents/nodes/${nodeId}`, { headers: auth() })
    const body = await r.json()
    if (body.code === 200) {
      rules.value = body.data.rules || []
      kbRels.value = body.data.kbRels || []
    }
  } catch (e) {
    ElMessage.error('加载详情失败')
  } finally {
    loadingRules.value = false
  }
}

function hasChildren(nodeId) {
  const hasChild = (nodes) => {
    for (const node of nodes) {
      if (node.parentId === nodeId) return true
      if (node.children && node.children.length) {
        if (hasChild(node.children)) return true
      }
    }
    return false
  }
  return hasChild(tree.value)
}

async function saveNode() {
  if (!form.value.name.trim()) {
    ElMessage.warning('请输入名称')
    return
  }
  savingNode.value = true
  try {
    const r = await fetch(`/api/intents/nodes/${selectedNode.value.id}`, {
      method: 'PUT', headers: auth(), body: JSON.stringify(form.value)
    })
    if (r.status === 401) { forceLogout(); return }
    const body = await r.json()
    if (body.code === 200) {
      ElMessage.success('保存成功')
      await loadTree()
      const findNode = (nodes) => {
        for (const node of nodes) {
          if (node.id === selectedNode.value.id) {
            Object.assign(node, form.value)
            return node
          }
          if (node.children) {
            const found = findNode(node.children)
            if (found) return found
          }
        }
        return null
      }
      selectedNode.value = findNode(tree.value)
    } else {
      ElMessage.error(body.message || '保存失败')
    }
  } catch (e) {
    ElMessage.error('保存失败')
  } finally {
    savingNode.value = false
  }
}

async function deleteSelected() {
  await ElMessageBox.confirm(
    '删除该节点将级联删除所有子节点、规则和知识库关联，确定继续？',
    '确认删除',
    { type: 'warning', confirmButtonText: '确定删除', cancelButtonText: '取消' }
  )
  try {
    const r = await fetch(`/api/intents/nodes/${selectedNode.value.id}`, { method: 'DELETE', headers: auth() })
    if (r.status === 401) { forceLogout(); return }
    const body = await r.json()
    if (body.code === 200) {
      ElMessage.success('删除成功')
      selectedNode.value = null
      await loadTree()
    } else {
      ElMessage.error(body.message || '删除失败')
    }
  } catch (e) {
    ElMessage.error('删除失败')
  }
}

async function addRoot() {
  const { value: name } = await ElMessageBox.prompt('请输入场景名称', '新建根节点', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    inputPlaceholder: '如：人事流程、财务报销'
  })
  if (!name || !name.trim()) {
    ElMessage.warning('请输入名称')
    return
  }
  try {
    const r = await fetch('/api/intents/nodes', {
      method: 'POST', headers: auth(), body: JSON.stringify({ name: name.trim(), level: 1, sortOrder: 0, enabled: 1 })
    })
    if (r.status === 401) { forceLogout(); return }
    const body = await r.json()
    if (body.code === 200) {
      ElMessage.success('创建成功')
      await loadTree()
    } else {
      ElMessage.error(body.message || '创建失败')
    }
  } catch (e) {
    ElMessage.error('创建失败')
  }
}

async function addChildNode() {
  childForm.value = { name: '', description: '' }
  addChildDialogVisible.value = true
}

async function createChildNode() {
  if (!childForm.value.name.trim()) {
    ElMessage.warning('请输入名称')
    return
  }
  try {
    const r = await fetch('/api/intents/nodes', {
      method: 'POST',
      headers: auth(),
      body: JSON.stringify({
        name: childForm.value.name.trim(),
        level: 2,
        parentId: selectedNode.value.id,
        sortOrder: 0,
        enabled: 1,
        description: childForm.value.description
      })
    })
    if (r.status === 401) { forceLogout(); return }
    const body = await r.json()
    if (body.code === 200) {
      ElMessage.success('创建成功')
      addChildDialogVisible.value = false
      await loadTree()
      if (!expandedIds.value.includes(selectedNode.value.id)) {
        expandedIds.value.push(selectedNode.value.id)
      }
    } else {
      ElMessage.error(body.message || '创建失败')
    }
  } catch (e) {
    ElMessage.error('创建失败')
  }
}

function openAddRule() {
  editingRuleId.value = null
  ruleForm.value = { ruleType: 'keyword', expression: '', weight: 1.0, enabled: 1 }
  ruleDialogVisible.value = true
}

function editRule(row) {
  editingRuleId.value = row.id
  ruleForm.value = { ...row }
  ruleDialogVisible.value = true
}

async function saveRule() {
  if (!ruleForm.value.expression.trim()) {
    ElMessage.warning('请输入表达式')
    return
  }
  const nodeId = selectedNode.value.id
  try {
    if (editingRuleId.value) {
      await fetch(`/api/intents/rules/${editingRuleId.value}`, {
        method: 'PUT', headers: auth(), body: JSON.stringify(ruleForm.value)
      })
    } else {
      await fetch(`/api/intents/nodes/${nodeId}/rules`, {
        method: 'POST', headers: auth(), body: JSON.stringify(ruleForm.value)
      })
    }
    ElMessage.success('保存成功')
    ruleDialogVisible.value = false
    await loadNodeDetail(nodeId)
  } catch (e) {
    ElMessage.error('保存失败')
  }
}

async function deleteRule(ruleId) {
  await ElMessageBox.confirm('确定删除该规则？', '确认删除', { type: 'warning' })
  try {
    await fetch(`/api/intents/rules/${ruleId}`, { method: 'DELETE', headers: auth() })
    ElMessage.success('删除成功')
    await loadNodeDetail(selectedNode.value.id)
  } catch (e) {
    ElMessage.error('删除失败')
  }
}

async function bindKb() {
  if (!newKbId.value) {
    ElMessage.warning('请选择知识库')
    return
  }
  try {
    const r = await fetch(`/api/intents/nodes/${selectedNode.value.id}/kbs`, {
      method: 'POST', headers: auth(), body: JSON.stringify({ kbId: newKbId.value, weight: newKbWeight.value || 1.0 })
    })
    if (r.status === 401) { forceLogout(); return }
    const body = await r.json()
    if (body.code === 200) {
      ElMessage.success('绑定成功')
      newKbId.value = null
      await loadNodeDetail(selectedNode.value.id)
    } else {
      ElMessage.error(body.message || '绑定失败')
    }
  } catch (e) {
    ElMessage.error('绑定失败')
  }
}

async function unbindKb(relId) {
  await ElMessageBox.confirm('确定解除绑定？', '确认解绑', { type: 'warning' })
  try {
    await fetch(`/api/intents/kb-rel/${relId}`, { method: 'DELETE', headers: auth() })
    ElMessage.success('解绑成功')
    await loadNodeDetail(selectedNode.value.id)
  } catch (e) {
    ElMessage.error('解绑失败')
  }
}

function getKbName(kbId) {
  const kb = kbList.value.find(k => k.id === kbId)
  return kb ? kb.name : `知识库#${kbId}`
}

onMounted(() => { loadTree(); loadKbList() })
</script>

<style scoped>
.intent-tree {
  --el-tree-node-hover-bg-color: #f0f5ff;
  --el-tree-node-current-bg-color: #e8f3ff;
}

.tree-node-content {
  display: flex;
  align-items: center;
  gap: 6px;
}

.node-icon {
  font-size: 14px;
  color: #409eff;
}

.node-name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px 20px;
  color: #999;
}

.empty-icon {
  font-size: 24px;
  margin-bottom: 8px;
}

.empty-text {
  color: #999;
  font-size: 13px;
}

.section-title {
  font-size: 14px;
  font-weight: 600;
  color: #1f2329;
  margin-bottom: 12px;
}

.table-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 30px;
  color: #999;
}
</style>
