<template>
  <div class="doc-page">
    <aside class="doc-sidebar">
      <div class="sidebar-header">
        <input v-model="keyword" placeholder="搜索文档" @input="searchDocs" class="search-input" />
        <el-button type="primary" size="small" @click="createDoc">新建</el-button>
      </div>
      <ul class="doc-list">
        <li v-for="doc in docs" :key="doc.id"
            :class="['doc-item', { active: currentDoc?.id === doc.id }]"
            @click="openDoc(doc)">
          <div class="doc-item-title">{{ doc.title || '无标题文档' }}</div>
          <div class="doc-item-meta">
            <span>{{ doc.updatedByName }}</span>
            <span>{{ formatTime(doc.updatedAt) }}</span>
          </div>
        </li>
      </ul>
    </aside>

    <main class="doc-editor">
      <template v-if="currentDoc">
        <div class="editor-toolbar" id="editor-toolbar"></div>
        <div class="editor-container" id="editor-container"></div>
        <div class="editor-footer">
          <span v-if="onlineCount > 1">{{ onlineCount }} 人在线</span>
          <span v-else>仅自己</span>
          <span class="save-status">{{ saveStatus }}</span>
        </div>
      </template>
      <div v-else class="editor-empty">选择或新建一个文档</div>
    </main>

    <aside class="doc-panel" v-if="currentDoc">
      <el-tabs v-model="activeTab">
        <el-tab-pane label="协作者" name="collaborators">
          <CollaboratorPanel :docId="currentDoc.id" />
        </el-tab-pane>
        <el-tab-pane label="评论" name="comments">
          <CommentPanel :docId="currentDoc.id" :quill="quill" />
        </el-tab-pane>
        <el-tab-pane label="分享" name="share">
          <SharePanel :docId="currentDoc.id" />
        </el-tab-pane>
      </el-tabs>
    </aside>
  </div>
</template>

<script setup>
import { ref, nextTick, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import Quill from 'quill'
import QuillCursors from 'quill-cursors'
import 'quill/dist/quill.snow.css'
import 'quill-cursors/dist/quill-cursors.css'
import { getAuthHeaders } from '../api'
import CollaboratorPanel from '../components/CollaboratorPanel.vue'
import CommentPanel from '../components/CommentPanel.vue'
import SharePanel from '../components/SharePanel.vue'

Quill.register('modules/cursors', QuillCursors)

const docs = ref([])
const currentDoc = ref(null)
const keyword = ref('')
const activeTab = ref('collaborators')
const saveStatus = ref('已保存')
const onlineCount = ref(1)

let quill = null
let ws = null
let cursors = null
let remoteChange = false
let localVersion = 0

function authHeaders() {
  const h = getAuthHeaders()
  return {
    'X-User-Id': h['X-User-Id'] || '',
    'X-Department-Id': h['X-Department-Id'] || '',
    'X-Is-Admin': h['X-Is-Admin'] || 'false'
  }
}

function getToken() {
  try {
    const user = JSON.parse(localStorage.getItem('user') || '{}')
    return user.accessToken || ''
  } catch { return '' }
}

async function loadDocs() {
  try {
    const res = await fetch('/api/docs?keyword=' + encodeURIComponent(keyword.value))
    const body = await res.json()
    if (String(body.code) === '200') {
      docs.value = body.data?.records || []
    }
  } catch (e) {
    console.error('加载文档列表失败', e)
  }
}

let searchTimer = null
function searchDocs() {
  clearTimeout(searchTimer)
  searchTimer = setTimeout(loadDocs, 300)
}

async function createDoc() {
  const title = prompt('请输入文档标题')
  if (!title) return
  try {
    const res = await fetch('/api/docs', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', ...authHeaders() },
      body: JSON.stringify({ title })
    })
    const body = await res.json()
    if (String(body.code) === '200') {
      await loadDocs()
      const newDoc = { id: body.data.id, title }
      openDoc(newDoc)
    }
  } catch (e) {
    ElMessage.error('创建失败')
  }
}

async function openDoc(doc) {
  disconnectWs()

  try {
    const res = await fetch(`/api/docs/${doc.id}`, { headers: authHeaders() })
    const body = await res.json()
    if (String(body.code) !== '200') {
      ElMessage.error('加载文档失败')
      return
    }

    currentDoc.value = body.data
    localVersion = body.data.version || 0

    await nextTick()
    initEditor(body.data.content)
    connectWs(doc.id)

    loadOnlineCount(doc.id)
  } catch (e) {
    console.error('打开文档失败', e)
  }
}

function initEditor(content) {
  const container = document.getElementById('editor-container')
  const toolbar = document.getElementById('editor-toolbar')
  if (!container) return

  if (quill) {
    quill.off('text-change')
    quill.off('selection-change')
    container.innerHTML = ''
  }

  quill = new Quill(container, {
    modules: {
      toolbar: toolbar || '#editor-toolbar',
      cursors: true
    },
    theme: 'snow',
    placeholder: '开始输入...'
  })

  cursors = quill.getModule('cursors')

  try {
    const delta = JSON.parse(content)
    quill.setContents(delta, 'silent')
  } catch (e) {
    quill.setContents([{ insert: '\n' }], 'silent')
  }

  quill.on('text-change', (delta, oldDelta, source) => {
    if (source !== 'user') return
    if (remoteChange) return

    const ops = delta.ops
    if (!ops || ops.length === 0) return

    sendWsMessage({
      action: 'op',
      docId: currentDoc.value.id,
      ops: ops,
      version: localVersion
    })

    saveStatus.value = '保存中...'
  })

  quill.on('selection-change', (range) => {
    if (range && ws && ws.readyState === WebSocket.OPEN) {
      sendWsMessage({
        action: 'cursor',
        docId: currentDoc.value.id,
        range: range
      })
    }
  })
}

function connectWs(docId) {
  const token = getToken()
  const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:'
  const host = location.hostname
  ws = new WebSocket(`${protocol}//${host}:8090/ws/docs?token=${encodeURIComponent(token)}`)

  ws.onopen = () => {
    sendWsMessage({ action: 'sub', docId })
  }

  ws.onmessage = (event) => {
    try {
      const msg = JSON.parse(event.data)
      handleWsMessage(msg)
    } catch (e) {
      console.error('解析 WebSocket 消息失败', e)
    }
  }

  ws.onclose = () => {
    console.log('WebSocket 断开')
  }

  ws.onerror = (e) => {
    console.error('WebSocket 错误', e)
  }
}

function disconnectWs() {
  if (ws) {
    ws.onclose = null
    ws.close()
    ws = null
  }
}

function sendWsMessage(msg) {
  if (ws && ws.readyState === WebSocket.OPEN) {
    ws.send(JSON.stringify(msg))
  }
}

function handleWsMessage(msg) {
  if (!currentDoc.value) return

  switch (msg.action) {
    case 'init': {
      localVersion = msg.version
      try {
        const delta = JSON.parse(msg.content)
        remoteChange = true
        quill.setContents(delta, 'silent')
        remoteChange = false
      } catch (e) { /* ignore */ }
      break
    }
    case 'ack': {
      localVersion = msg.version
      saveStatus.value = '已保存'
      break
    }
    case 'op': {
      localVersion = msg.version
      remoteChange = true
      quill.updateContents(msg.ops, 'silent')
      remoteChange = false
      break
    }
    case 'cursor': {
      if (cursors) {
        cursors.createCursor(msg.userId, msg.userName || ('用户' + msg.userId), 'red')
        cursors.moveCursor(msg.userId, msg.range)
      }
      break
    }
    case 'presence': {
      loadOnlineCount(currentDoc.value.id)
      break
    }
    case 'error': {
      ElMessage.error(msg.message || '协同编辑出错')
      break
    }
  }
}

async function loadOnlineCount(docId) {
  try {
    const res = await fetch(`/api/docs/${docId}/collaborators`, { headers: authHeaders() })
    const body = await res.json()
    if (String(body.code) === '200') {
      const list = body.data || []
      onlineCount.value = Math.max(1, list.filter(c => c.online).length)
    }
  } catch { /* ignore */ }
}

function formatTime(t) {
  if (!t) return ''
  return new Date(t).toLocaleString('zh-CN', { month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit' })
}

onBeforeUnmount(() => {
  disconnectWs()
})

loadDocs()
</script>

<style scoped>
.doc-page { display: flex; height: 100vh; overflow: hidden; }
.doc-sidebar { width: 260px; border-right: 1px solid #e5e5e5; display: flex; flex-direction: column; background: #fff; }
.sidebar-header { padding: 12px; display: flex; gap: 8px; }
.search-input { flex: 1; border: 1px solid #ddd; border-radius: 4px; padding: 4px 8px; font-size: 13px; outline: none; }
.doc-list { flex: 1; overflow-y: auto; list-style: none; margin: 0; padding: 0; }
.doc-item { padding: 12px; cursor: pointer; border-bottom: 1px solid #f0f0f0; }
.doc-item:hover, .doc-item.active { background: #e8f0fe; }
.doc-item-title { font-size: 14px; font-weight: 500; color: #333; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.doc-item-meta { font-size: 12px; color: #999; margin-top: 4px; display: flex; justify-content: space-between; }
.doc-editor { flex: 1; display: flex; flex-direction: column; background: #f9f9f9; min-width: 0; }
.editor-toolbar { border-bottom: 1px solid #ddd; background: #fff; }
.editor-container { flex: 1; padding: 24px 40px; overflow-y: auto; background: #fff; margin: 0 auto; width: 100%; max-width: 800px; }
.editor-container :deep(.ql-editor) { min-height: 400px; font-size: 15px; line-height: 1.8; }
.editor-footer { padding: 8px 16px; font-size: 12px; color: #999; border-top: 1px solid #eee; display: flex; justify-content: space-between; background: #fff; }
.editor-empty { flex: 1; display: flex; align-items: center; justify-content: center; color: #999; font-size: 16px; }
.doc-panel { width: 300px; border-left: 1px solid #e5e5e5; background: #fff; overflow-y: auto; }
.doc-panel :deep(.el-tabs__header) { margin: 0; padding: 0 12px; }
.doc-panel :deep(.el-tabs__content) { padding: 0 12px; }
</style>
