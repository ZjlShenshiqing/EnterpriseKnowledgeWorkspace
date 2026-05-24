<template>
  <div class="collaborator-panel">
    <div class="add-collaborator">
      <el-input v-model="userId" placeholder="用户 ID" size="small" />
      <el-select v-model="permission" size="small" style="width:90px">
        <el-option label="查看" value="VIEW" />
        <el-option label="评论" value="COMMENT" />
        <el-option label="编辑" value="EDIT" />
      </el-select>
      <el-button type="primary" size="small" @click="addCollab" :disabled="!userId.trim()">添加</el-button>
    </div>
    <div class="collab-list" v-if="collaborators.length > 0">
      <div v-for="c in collaborators" :key="c.id" class="collab-item">
        <span class="collab-name">{{ c.targetName || ('用户' + c.targetId) }}</span>
        <span class="collab-perm">{{ c.permission }}</span>
        <el-button text size="small" type="danger" @click="removeCollab(c.id)">移除</el-button>
      </div>
    </div>
    <div v-else class="empty-hint">暂无协作者</div>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import { getAuthHeaders } from '../api'

const props = defineProps({ docId: Number })

const collaborators = ref([])
const userId = ref('')
const permission = ref('EDIT')

function authHeaders() {
  const h = getAuthHeaders()
  return { 'Content-Type': 'application/json', 'X-User-Id': h['X-User-Id'] || '' }
}

async function loadCollabs() {
  if (!props.docId) return
  try {
    const res = await fetch(`/api/docs/${props.docId}/collaborators`, { headers: authHeaders() })
    const body = await res.json()
    if (String(body.code) === '200') collaborators.value = body.data || []
  } catch { /* ignore */ }
}

async function addCollab() {
  if (!userId.value.trim()) return
  try {
    await fetch(`/api/docs/${props.docId}/collaborators`, {
      method: 'POST',
      headers: authHeaders(),
      body: JSON.stringify({ targetType: 'USER', targetId: Number(userId.value.trim()), permission: permission.value })
    })
    userId.value = ''
    await loadCollabs()
  } catch { /* ignore */ }
}

async function removeCollab(id) {
  try {
    await fetch(`/api/collaborators/${id}`, { method: 'DELETE', headers: authHeaders() })
    await loadCollabs()
  } catch { /* ignore */ }
}

watch(() => props.docId, loadCollabs, { immediate: true })
</script>

<style scoped>
.collaborator-panel { padding: 8px 0; }
.add-collaborator { display: flex; gap: 4px; margin-bottom: 12px; }
.collab-list { display: flex; flex-direction: column; gap: 8px; }
.collab-item { display: flex; align-items: center; gap: 8px; padding: 6px 8px; background: #f8f8f8; border-radius: 4px; }
.collab-name { flex: 1; font-size: 13px; }
.collab-perm { font-size: 11px; color: #999; background: #eee; padding: 2px 6px; border-radius: 3px; }
.empty-hint { text-align: center; color: #999; font-size: 13px; padding: 24px 0; }
</style>
