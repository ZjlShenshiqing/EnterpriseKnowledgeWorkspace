<template>
  <div class="share-panel">
    <div class="create-share">
      <el-select v-model="perm" size="small" style="width:100px">
        <el-option label="查看" value="VIEW" />
        <el-option label="评论" value="COMMENT" />
        <el-option label="编辑" value="EDIT" />
      </el-select>
      <el-button type="primary" size="small" @click="createShare">生成链接</el-button>
    </div>
    <div class="share-list" v-if="shares.length > 0">
      <div v-for="s in shares" :key="s.id" class="share-item">
        <div class="share-link">
          <code>{{ shareUrl(s.token) }}</code>
        </div>
        <div class="share-meta">
          <span>{{ s.permission }}</span>
          <el-button text size="small" @click="copyLink(s.token)">复制</el-button>
          <el-button text size="small" type="danger" @click="deleteShare(s.id)">删除</el-button>
        </div>
      </div>
    </div>
    <div v-else class="empty-hint">暂无分享链接</div>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { getAuthHeaders } from '../api'

const props = defineProps({ docId: Number })

const shares = ref([])
const perm = ref('VIEW')

function authHeaders() {
  const h = getAuthHeaders()
  return { 'Content-Type': 'application/json', 'X-User-Id': h['X-User-Id'] || '' }
}

async function loadShares() {
  if (!props.docId) return
  try {
    const res = await fetch(`/api/docs/${props.docId}/shares`, { headers: authHeaders() })
    const body = await res.json()
    if (String(body.code) === '200') shares.value = body.data || []
  } catch { /* ignore */ }
}

async function createShare() {
  try {
    const res = await fetch(`/api/docs/${props.docId}/shares`, {
      method: 'POST',
      headers: authHeaders(),
      body: JSON.stringify({ permission: perm.value })
    })
    const body = await res.json()
    if (String(body.code) === '200') {
      await loadShares()
    }
  } catch { ElMessage.error('创建失败') }
}

async function deleteShare(id) {
  try {
    await fetch(`/api/shares/${id}`, { method: 'DELETE', headers: authHeaders() })
    await loadShares()
  } catch { /* ignore */ }
}

function shareUrl(token) {
  return `${location.origin}/documents/shared/${token}`
}

function copyLink(token) {
  navigator.clipboard.writeText(shareUrl(token)).then(() => ElMessage.success('链接已复制'))
}

watch(() => props.docId, loadShares, { immediate: true })
</script>

<style scoped>
.share-panel { padding: 8px 0; }
.create-share { display: flex; gap: 4px; margin-bottom: 12px; }
.share-list { display: flex; flex-direction: column; gap: 8px; }
.share-item { padding: 8px; background: #f8f8f8; border-radius: 4px; }
.share-link code { font-size: 11px; word-break: break-all; color: #666; }
.share-meta { display: flex; align-items: center; gap: 8px; margin-top: 4px; font-size: 12px; color: #999; }
.empty-hint { text-align: center; color: #999; font-size: 13px; padding: 24px 0; }
</style>
