<template>
  <div class="admin-view" style="display:flex;gap:0;height:calc(100vh - 120px);padding:0">
    <!-- 左侧树 -->
    <div style="width:280px;border-right:1px solid #e5e6eb;display:flex;flex-direction:column;background:#fafafa;flex-shrink:0">
      <div style="padding:14px 16px;border-bottom:1px solid #e5e6eb">
        <el-button type="primary" size="small" @click="addRoot">新增场景</el-button>
      </div>
      <div style="flex:1;overflow-y:auto;padding:8px 0">
        <div v-for="node in tree" :key="node.id">
          <div @click="selectNode(node)"
            :style="{padding:'8px 16px',cursor:'pointer',display:'flex',alignItems:'center',gap:'6px',background:selectedNode?.id===node.id?'#e8f3ff':'transparent',color:selectedNode?.id===node.id?'#3370ff':'#1f2329',fontWeight:node.level===1?600:400}">
            <span style="font-size:12px">{{ node.level===1 ? '▸' : '├' }}</span>
            <span style="flex:1;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;font-size:13px">{{ node.name }}</span>
            <span v-if="!node.enabled" style="color:#bbb;font-size:10px">停用</span>
          </div>
          <div v-if="node.children && node.children.length && expandedIds.includes(node.id)" style="padding-left:16px">
            <div v-for="child in node.children" :key="child.id"
              @click="selectNode(child)"
              :style="{padding:'6px 16px',cursor:'pointer',display:'flex',alignItems:'center',gap:'6px',background:selectedNode?.id===child.id?'#e8f3ff':'transparent',color:selectedNode?.id===child.id?'#3370ff':'#555'}">
              <span style="font-size:12px">├</span>
              <span style="flex:1;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;font-size:12px">{{ child.name }}</span>
            </div>
          </div>
        </div>
        <div v-if="tree.length===0" style="text-align:center;padding:30px;color:#bbb;font-size:13px">暂无意图节点</div>
      </div>
    </div>

    <!-- 右侧详情 -->
    <div style="flex:1;overflow-y:auto;padding:20px;background:#fff">
      <div v-if="!selectedNode" style="display:flex;align-items:center;justify-content:center;height:100%;color:#bbb;font-size:14px">
        选择一个节点查看详情
      </div>
      <template v-else>
        <!-- 节点信息 -->
        <div style="background:#fafafa;padding:16px;border-radius:8px;margin-bottom:16px">
          <div style="font-weight:600;font-size:14px;margin-bottom:12px">节点信息</div>
          <div style="display:flex;gap:12px;align-items:flex-end;flex-wrap:wrap">
            <div>
              <div style="font-size:11px;color:#8f959e;margin-bottom:4px">名称</div>
              <input v-model="form.name" style="border:1px solid #e5e6eb;border-radius:6px;padding:6px 10px;font-size:13px;width:180px;outline:none" />
            </div>
            <div>
              <div style="font-size:11px;color:#8f959e;margin-bottom:4px">层级</div>
              <select v-model="form.level" style="border:1px solid #e5e6eb;border-radius:6px;padding:6px 10px;font-size:13px;outline:none">
                <option :value="1">场景</option>
                <option :value="2">意图</option>
              </select>
            </div>
            <div>
              <div style="font-size:11px;color:#8f959e;margin-bottom:4px">排序</div>
              <input v-model.number="form.sortOrder" type="number" style="border:1px solid #e5e6eb;border-radius:6px;padding:6px 10px;font-size:13px;width:70px;outline:none" />
            </div>
            <label style="display:flex;align-items:center;gap:4px;font-size:12px;cursor:pointer">
              <input type="checkbox" v-model="form.enabled" :true-value="1" :false-value="0" /> 启用
            </label>
            <el-button type="primary" size="small" @click="saveNode">保存</el-button>
            <el-button size="small" type="danger" @click="deleteSelected">删除</el-button>
          </div>
          <div style="margin-top:10px">
            <div style="font-size:11px;color:#8f959e;margin-bottom:4px">描述</div>
            <input v-model="form.description" style="border:1px solid #e5e6eb;border-radius:6px;padding:6px 10px;font-size:13px;width:100%;outline:none" placeholder="可选描述" />
          </div>
        </div>

        <!-- 匹配规则 -->
        <div style="background:#fafafa;padding:16px;border-radius:8px;margin-bottom:16px">
          <div style="display:flex;align-items:center;justify-content:space-between;margin-bottom:12px">
            <span style="font-weight:600;font-size:14px">匹配规则</span>
            <el-button type="primary" size="small" @click="openAddRule">新增规则</el-button>
          </div>
          <table v-if="rules.length" style="width:100%;border-collapse:collapse;font-size:12px">
            <tr style="border-bottom:1px solid #e5e6eb;color:#8f959e;text-align:left">
              <th style="padding:6px 8px;font-weight:500">类型</th>
              <th style="padding:6px 8px;font-weight:500">表达式</th>
              <th style="padding:6px 8px;font-weight:500;width:60px">权重</th>
              <th style="padding:6px 8px;font-weight:500;width:40px">启用</th>
              <th style="padding:6px 8px;font-weight:500;width:40px"></th>
            </tr>
            <tr v-for="r in rules" :key="r.id" style="border-bottom:1px solid #f2f3f5">
              <td style="padding:6px 8px">{{ r.ruleType }}</td>
              <td style="padding:6px 8px;font-family:monospace">{{ r.expression }}</td>
              <td style="padding:6px 8px">{{ r.weight }}</td>
              <td style="padding:6px 8px">{{ r.enabled ? '✓' : '✗' }}</td>
              <td style="padding:6px 8px"><span @click="deleteRule(r.id)" style="color:#f54a45;cursor:pointer;font-size:14px">×</span></td>
            </tr>
          </table>
          <div v-else style="color:#bbb;font-size:12px;text-align:center;padding:16px">暂无规则</div>
        </div>

        <!-- 关联知识库 -->
        <div style="background:#fafafa;padding:16px;border-radius:8px;margin-bottom:16px">
          <div style="display:flex;align-items:center;justify-content:space-between;margin-bottom:12px">
            <span style="font-weight:600;font-size:14px">关联知识库</span>
            <div style="display:flex;gap:6px">
              <select v-model="newKbId" style="border:1px solid #e5e6eb;border-radius:6px;padding:5px 8px;font-size:12px;outline:none">
                <option :value="null">选择知识库</option>
                <option v-for="kb in kbList" :key="kb.id" :value="kb.id">{{ kb.name }}</option>
              </select>
              <input v-model.number="newKbWeight" type="number" step="0.1" placeholder="权重" style="border:1px solid #e5e6eb;border-radius:6px;padding:5px 8px;font-size:12px;width:60px;outline:none" />
              <el-button size="small" @click="bindKb">绑定</el-button>
            </div>
          </div>
          <table v-if="kbRels.length" style="width:100%;border-collapse:collapse;font-size:12px">
            <tr style="border-bottom:1px solid #e5e6eb;color:#8f959e;text-align:left">
              <th style="padding:6px 8px;font-weight:500">知识库</th>
              <th style="padding:6px 8px;font-weight:500;width:60px">权重</th>
              <th style="padding:6px 8px;font-weight:500;width:40px"></th>
            </tr>
            <tr v-for="r in kbRels" :key="r.id" style="border-bottom:1px solid #f2f3f5">
              <td style="padding:6px 8px">{{ getKbName(r.kbId) }}</td>
              <td style="padding:6px 8px">{{ r.weight }}</td>
              <td style="padding:6px 8px"><span @click="unbindKb(r.id)" style="color:#f54a45;cursor:pointer;font-size:14px">×</span></td>
            </tr>
          </table>
          <div v-else style="color:#bbb;font-size:12px;text-align:center;padding:16px">暂未关联知识库</div>
        </div>

        <!-- 匹配预览 -->
        <div style="background:#fafafa;padding:16px;border-radius:8px">
          <div style="font-weight:600;font-size:14px;margin-bottom:12px">匹配预览</div>
          <div style="display:flex;gap:8px;margin-bottom:12px">
            <input v-model="testQuery" @keydown.enter="testMatch" placeholder="输入测试文本，如'请假流程怎么走'" style="flex:1;border:1px solid #e5e6eb;border-radius:6px;padding:6px 10px;font-size:13px;outline:none" />
            <el-button type="primary" size="small" @click="testMatch">测试匹配</el-button>
          </div>
          <div v-if="matchResult">
            <div style="font-size:12px;font-weight:500;margin-bottom:4px" v-if="matchResult.bestMatch">
              最佳命中: <span style="color:#3370ff">{{ matchResult.bestMatch.nodeName }}</span>
              ({{ matchResult.bestMatch.ruleType }}: {{ matchResult.bestMatch.expression }})
            </div>
            <div v-if="matchResult.hits && matchResult.hits.length===0" style="color:#bbb;font-size:12px">无命中</div>
            <div v-for="h in matchResult.hits" :key="h.ruleId" style="font-size:11px;color:#555;padding:2px 0">
              {{ h.nodeName }} ← {{ h.ruleType }}:"{{ h.expression }}" (权重 {{ h.weight }})
            </div>
          </div>
        </div>
      </template>
    </div>

    <!-- 规则编辑弹窗 -->
    <el-dialog v-model="ruleDialogVisible" title="编辑匹配规则" width="420px">
      <div style="display:flex;flex-direction:column;gap:12px">
        <div>
          <div style="font-size:12px;color:#8f959e;margin-bottom:4px">规则类型</div>
          <select v-model="ruleForm.ruleType" style="border:1px solid #e5e6eb;border-radius:6px;padding:6px 10px;font-size:13px;width:100%;outline:none">
            <option value="keyword">关键词</option>
            <option value="regex">正则表达式</option>
          </select>
        </div>
        <div>
          <div style="font-size:12px;color:#8f959e;margin-bottom:4px">表达式</div>
          <input v-model="ruleForm.expression" style="border:1px solid #e5e6eb;border-radius:6px;padding:6px 10px;font-size:13px;width:100%;outline:none" placeholder="关键词或正则表达式" />
        </div>
        <div>
          <div style="font-size:12px;color:#8f959e;margin-bottom:4px">权重</div>
          <input v-model.number="ruleForm.weight" type="number" step="0.1" style="border:1px solid #e5e6eb;border-radius:6px;padding:6px 10px;font-size:13px;width:100px;outline:none" />
        </div>
        <label style="font-size:12px;display:flex;align-items:center;gap:4px;cursor:pointer">
          <input type="checkbox" v-model="ruleForm.enabled" :true-value="1" :false-value="0" /> 启用
        </label>
      </div>
      <template #footer>
        <el-button @click="ruleDialogVisible=false">取消</el-button>
        <el-button type="primary" @click="saveRule">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getAuthHeaders, forceLogout } from '../../api'

const tree = ref([])
const selectedNode = ref(null)
const rules = ref([])
const kbRels = ref([])
const kbList = ref([])
const expandedIds = ref([])
const matchResult = ref(null)

const form = ref({})
const ruleForm = ref({ ruleType: 'keyword', expression: '', weight: 1.0, enabled: 1 })
const ruleDialogVisible = ref(false)
const editingRuleId = ref(null)
const testQuery = ref('')
const newKbId = ref(null)
const newKbWeight = ref(1.0)

function auth() { return { ...getAuthHeaders(), 'Content-Type': 'application/json' } }

async function loadTree() {
  const r = await fetch('/api/intents/nodes', { headers: auth() })
  const body = await r.json()
  if (body.code === 40100) { forceLogout(); return }
  tree.value = body.data || []
}

async function loadKbList() {
  const r = await fetch('/api/kb/bases', { headers: auth() })
  const body = await r.json()
  if (body.code === 200) kbList.value = body.data || []
}

async function selectNode(node) {
  selectedNode.value = node
  form.value = { name: node.name, level: node.level, sortOrder: node.sortOrder, description: node.description || '', enabled: node.enabled }
  if (expandedIds.value.includes(node.id)) {
    expandedIds.value = expandedIds.value.filter(id => id !== node.id)
  } else {
    expandedIds.value.push(node.id)
  }
  const r = await fetch(`/api/intents/nodes/${node.id}`, { headers: auth() })
  const body = await r.json()
  if (body.code === 200) {
    rules.value = body.data.rules || []
    kbRels.value = body.data.kbRels || []
  }
}

async function saveNode() {
  const r = await fetch(`/api/intents/nodes/${selectedNode.value.id}`, {
    method: 'PUT', headers: auth(), body: JSON.stringify(form.value)
  })
  if (r.status === 401) { forceLogout(); return }
  ElMessage.success('已保存')
  loadTree()
}

async function deleteSelected() {
  await ElMessageBox.confirm('删除该节点将级联删除所有子节点、规则和关联，确定？', '确认删除', { type: 'warning' })
  await fetch(`/api/intents/nodes/${selectedNode.value.id}`, { method: 'DELETE', headers: auth() })
  selectedNode.value = null
  ElMessage.success('已删除')
  loadTree()
}

async function addRoot() {
  const name = prompt('场景名称:')
  if (!name) return
  await fetch('/api/intents/nodes', {
    method: 'POST', headers: auth(), body: JSON.stringify({ name, level: 1, sortOrder: 0, enabled: 1 })
  })
  loadTree()
}

async function openAddRule() {
  editingRuleId.value = null
  ruleForm.value = { ruleType: 'keyword', expression: '', weight: 1.0, enabled: 1 }
  ruleDialogVisible.value = true
}

async function saveRule() {
  const nodeId = selectedNode.value.id
  if (editingRuleId.value) {
    await fetch(`/api/intents/rules/${editingRuleId.value}`, {
      method: 'PUT', headers: auth(), body: JSON.stringify(ruleForm.value)
    })
  } else {
    await fetch(`/api/intents/nodes/${nodeId}/rules`, {
      method: 'POST', headers: auth(), body: JSON.stringify(ruleForm.value)
    })
  }
  ruleDialogVisible.value = false
  selectNode(selectedNode.value)
}

async function deleteRule(ruleId) {
  await fetch(`/api/intents/rules/${ruleId}`, { method: 'DELETE', headers: auth() })
  selectNode(selectedNode.value)
}

async function bindKb() {
  if (!newKbId.value) return
  await fetch(`/api/intents/nodes/${selectedNode.value.id}/kbs`, {
    method: 'POST', headers: auth(), body: JSON.stringify({ kbId: Number(newKbId.value), weight: newKbWeight.value || 1.0 })
  })
  newKbId.value = null
  newKbWeight.value = 1.0
  ElMessage.success('已绑定')
  selectNode(selectedNode.value)
}

async function unbindKb(relId) {
  await fetch(`/api/intents/kb-rel/${relId}`, { method: 'DELETE', headers: auth() })
  selectNode(selectedNode.value)
}

async function testMatch() {
  if (!testQuery.value.trim()) return
  const r = await fetch('/api/intents/match', {
    method: 'POST', headers: auth(), body: JSON.stringify({ query: testQuery.value })
  })
  matchResult.value = (await r.json()).data
}

function getKbName(kbId) {
  const kb = kbList.value.find(k => k.id === kbId)
  return kb ? kb.name : `知识库#${kbId}`
}

onMounted(() => { loadTree(); loadKbList() })
</script>
