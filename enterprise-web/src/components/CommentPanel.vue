<template>
  <div class="comment-panel">
    <div class="comment-input">
      <el-input v-model="newComment" type="textarea" :rows="2" placeholder="添加评论..." />
      <el-button type="primary" size="small" @click="addComment(null)" :disabled="!newComment.trim()" style="margin-top:8px">
        发表
      </el-button>
    </div>
    <div class="comment-list" v-if="comments.length > 0">
      <div v-for="c in comments" :key="c.id" class="comment-item" :class="{ resolved: c.resolved }">
        <div class="comment-user">{{ c.userName }}</div>
        <div class="comment-content">{{ c.content }}</div>
        <div class="comment-time">{{ formatTime(c.createdAt) }}</div>
        <div class="comment-actions">
          <el-button text size="small" @click="replyTo = c.id" v-if="replyTo !== c.id">回复</el-button>
          <el-button text size="small" @click="resolveComment(c)">{{ c.resolved ? '重新打开' : '解决' }}</el-button>
          <el-button text size="small" type="danger" @click="deleteComment(c.id)">删除</el-button>
        </div>
        <div v-if="replyTo === c.id" class="reply-input">
          <el-input v-model="replyContent" type="textarea" :rows="2" placeholder="回复..." />
          <el-button size="small" @click="addComment(c.id)" :disabled="!replyContent.trim()">回复</el-button>
          <el-button size="small" @click="replyTo = null">取消</el-button>
        </div>
        <div v-if="c.replies?.length" class="reply-list">
          <div v-for="r in c.replies" :key="r.id" class="reply-item">
            <span class="reply-user">{{ r.userName }}</span>: {{ r.content }}
          </div>
        </div>
      </div>
    </div>
    <div v-else class="empty-hint">暂无评论</div>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { getAuthHeaders } from '../api'

const props = defineProps({ docId: Number, quill: Object })

const comments = ref([])
const newComment = ref('')
const replyTo = ref(null)
const replyContent = ref('')

function authHeaders() {
  const h = getAuthHeaders()
  return { 'Content-Type': 'application/json', 'X-User-Id': h['X-User-Id'] || '' }
}

async function loadComments() {
  if (!props.docId) return
  try {
    const res = await fetch(`/api/docs/${props.docId}/comments`, { headers: authHeaders() })
    const body = await res.json()
    if (String(body.code) === '200') comments.value = body.data || []
  } catch { /* ignore */ }
}

async function addComment(parentId) {
  const content = parentId ? replyContent.value : newComment.value
  if (!content.trim()) return
  try {
    const res = await fetch(`/api/docs/${props.docId}/comments`, {
      method: 'POST',
      headers: authHeaders(),
      body: JSON.stringify({ content: content.trim(), parentId })
    })
    const body = await res.json()
    if (String(body.code) === '200') {
      if (parentId) { replyTo.value = null; replyContent.value = '' }
      else newComment.value = ''
      await loadComments()
    }
  } catch { ElMessage.error('评论失败') }
}

async function resolveComment(c) {
  try {
    await fetch(`/api/docs/comments/${c.id}`, {
      method: 'PUT',
      headers: authHeaders(),
      body: JSON.stringify({ resolved: c.resolved ? 0 : 1 })
    })
    await loadComments()
  } catch { /* ignore */ }
}

async function deleteComment(id) {
  try {
    await fetch(`/api/docs/comments/${id}`, { method: 'DELETE', headers: authHeaders() })
    await loadComments()
  } catch { /* ignore */ }
}

function formatTime(t) {
  if (!t) return ''
  return new Date(t).toLocaleString('zh-CN')
}

watch(() => props.docId, loadComments, { immediate: true })
</script>

<style scoped>
.comment-panel { padding: 8px 0; }
.comment-input { margin-bottom: 16px; }
.comment-list { display: flex; flex-direction: column; gap: 12px; }
.comment-item { padding: 8px; background: #f8f8f8; border-radius: 6px; }
.comment-item.resolved { opacity: 0.5; }
.comment-user { font-size: 13px; font-weight: 600; color: #333; }
.comment-content { font-size: 13px; color: #555; margin: 4px 0; }
.comment-time { font-size: 11px; color: #aaa; }
.comment-actions { display: flex; gap: 4px; margin-top: 4px; }
.reply-input { margin-top: 8px; display: flex; gap: 4px; align-items: center; }
.reply-list { margin-top: 8px; padding-left: 12px; border-left: 2px solid #e0e0e0; }
.reply-item { font-size: 13px; color: #555; padding: 2px 0; }
.reply-user { font-weight: 600; color: #333; }
.empty-hint { text-align: center; color: #999; font-size: 13px; padding: 24px 0; }
</style>
