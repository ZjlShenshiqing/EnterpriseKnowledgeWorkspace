<template>
  <div class="chatgpt-page" :class="{ 'chatgpt-page--sidebar-open': historyOpen }">
    <aside class="chat-sidebar">
      <div class="sidebar-head">
        <button type="button" class="icon-btn" title="收起侧栏" @click="historyOpen = false">
          <el-icon><Fold /></el-icon>
        </button>
        <button type="button" class="icon-btn" title="新对话" @click="startNewChat">
          <el-icon><EditPen /></el-icon>
        </button>
      </div>

      <button type="button" class="new-chat-btn" @click="startNewChat">
        <el-icon><Plus /></el-icon>
        <span>新对话</span>
      </button>

      <div class="sidebar-search">
        <el-icon><Search /></el-icon>
        <input v-model="historySearch" type="text" placeholder="搜索历史对话" />
      </div>

      <div class="session-section">
        <div class="session-section-title">最近</div>
        <div class="session-list">
          <div v-if="loadingSessions" class="session-empty">加载中...</div>
          <div v-else-if="filteredSessions.length === 0" class="session-empty">暂无历史对话</div>
          <button
            v-for="s in filteredSessions"
            :key="s.id"
            type="button"
            class="session-item"
            :class="{ 'session-item--active': sessionId === s.id }"
            :title="s.title || '新对话'"
            @click="openSession(s)"
          >
            {{ sessionTitle(s) }}
          </button>
        </div>
      </div>
    </aside>

    <div v-if="historyOpen" class="mobile-backdrop" @click="historyOpen = false" />

    <main class="chat-main">
      <section
        ref="box"
        class="conversation"
        :class="{ 'conversation--empty': !messages.length }"
      >
        <div v-if="!messages.length" class="empty-state">
          <h1>有什么可以帮你？</h1>
          <div class="chat-composer">
            <div v-if="attachments.length" class="attachment-list">
              <div
                v-for="(att, index) in attachments"
                :key="`${att.name}-${index}`"
                class="attachment-chip"
              >
                <el-icon class="attachment-icon"><Document /></el-icon>
                <span class="attachment-name">{{ att.name }}</span>
                <button type="button" class="attachment-remove" title="移除" @click="removeAttachment(index)">
                  ×
                </button>
              </div>
            </div>
            <textarea
              ref="textarea"
              v-model="input"
              class="composer-input"
              placeholder="问任何和企业知识有关的问题"
              rows="1"
              :disabled="sending"
              @input="autoResize"
              @keydown.enter.exact.prevent="send"
            />
            <div class="composer-actions">
              <div class="composer-actions-left">
                <button type="button" class="composer-tool" title="上传文件" :disabled="uploading" @click="triggerUpload">
                  <el-icon :class="{ 'icon-spin': uploading }">
                    <Loading v-if="uploading" />
                    <Upload v-else />
                  </el-icon>
                </button>
                <button
                  type="button"
                  class="composer-tool"
                  :class="{ 'composer-tool--active': webSearchEnabled }"
                  title="联网搜索"
                  @click="webSearchEnabled = !webSearchEnabled"
                >
                  <el-icon><Link /></el-icon>
                  <span>联网搜索</span>
                </button>
                <button type="button" class="composer-tool composer-tool--icon-only" title="语音输入" disabled>
                  <el-icon><Microphone /></el-icon>
                </button>
              </div>
              <button
                type="button"
                class="composer-send"
                :class="{ 'composer-send--stop': sending }"
                :title="sending ? '停止生成' : '发送'"
                :disabled="!sending && !input.trim()"
                @click="sending ? stopGenerating() : send()"
              >
                <el-icon>
                  <SwitchButton v-if="sending" />
                  <Position v-else />
                </el-icon>
              </button>
            </div>
          </div>
        </div>

        <div v-else class="thread">
          <article
            v-for="(msg, i) in messages"
            :key="i"
            class="message-row"
            :class="`message-row--${msg.role}`"
          >
            <template v-if="msg.role === 'user'">
              <div class="user-card">
                <div class="message-text">{{ msg.content }}</div>
              </div>
              <div v-if="msg.createdAt" class="message-time">{{ formatTime(msg.createdAt) }}</div>
            </template>

            <template v-else-if="msg.role === 'assistant'">
              <div class="assistant-card">
                <div v-if="msg.typing" class="typing-line">
                  <span></span>
                  <span></span>
                  <span></span>
                </div>
                <div v-else class="markdown-body" v-html="renderMsg(msg)" />
              </div>
              <div class="assistant-actions">
                <button type="button" class="action-btn" title="复制" @click="copyMessage(msg)">
                  <el-icon><CopyDocument /></el-icon>
                </button>
                <button
                  v-if="isLastAssistant(i)"
                  type="button"
                  class="action-btn"
                  title="重新生成"
                  :disabled="sending"
                  @click="regenerateLast"
                >
                  <el-icon><Refresh /></el-icon>
                </button>
              </div>
              <div v-if="msg.createdAt" class="message-time message-time--assistant">
                {{ formatTime(msg.createdAt) }}
              </div>
            </template>

            <details v-else-if="msg.role === 'tool'" class="tool-call">
              <summary>
                <el-icon><Tools /></el-icon>
                <span>{{ msg.toolName || '工具调用' }}</span>
              </summary>
              <pre>{{ msg.content || '（无详情）' }}</pre>
            </details>
          </article>
          <div ref="bottom" />
        </div>
      </section>

      <div v-if="messages.length" class="composer-shell">
        <div class="chat-composer">
          <div v-if="attachments.length" class="attachment-list">
            <div
              v-for="(att, index) in attachments"
              :key="`${att.name}-${index}`"
              class="attachment-chip"
            >
              <el-icon class="attachment-icon"><Document /></el-icon>
              <span class="attachment-name">{{ att.name }}</span>
              <button type="button" class="attachment-remove" title="移除" @click="removeAttachment(index)">
                ×
              </button>
            </div>
          </div>
          <textarea
            ref="textarea"
            v-model="input"
            class="composer-input"
            placeholder="给智能对话发送消息"
            rows="1"
            :disabled="sending"
            @input="autoResize"
            @keydown.enter.exact.prevent="send"
          />
          <div class="composer-actions">
            <div class="composer-actions-left">
              <button type="button" class="composer-tool" title="上传文件" :disabled="uploading" @click="triggerUpload">
                <el-icon :class="{ 'icon-spin': uploading }">
                  <Loading v-if="uploading" />
                  <Upload v-else />
                </el-icon>
              </button>
              <button
                type="button"
                class="composer-tool"
                :class="{ 'composer-tool--active': webSearchEnabled }"
                title="联网搜索"
                @click="webSearchEnabled = !webSearchEnabled"
              >
                <el-icon><Link /></el-icon>
                <span>联网搜索</span>
              </button>
              <button type="button" class="composer-tool composer-tool--icon-only" title="语音输入" disabled>
                <el-icon><Microphone /></el-icon>
              </button>
            </div>
            <button
              type="button"
              class="composer-send"
              :class="{ 'composer-send--stop': sending }"
              :title="sending ? '停止生成' : '发送'"
              :disabled="!sending && !input.trim()"
              @click="sending ? stopGenerating() : send()"
            >
              <el-icon>
                <SwitchButton v-if="sending" />
                <Position v-else />
              </el-icon>
            </button>
          </div>
        </div>
        <p class="composer-note">AI 可能会出错，请核对重要信息。</p>
      </div>
    </main>

    <input
      ref="fileInput"
      type="file"
      class="hidden-file"
      accept=".pdf,.doc,.docx,.xls,.xlsx,.ppt,.pptx,.txt,.md,.html,.png,.jpg,.jpeg,.gif,.webp"
      @change="onFileSelected"
    />
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  CopyDocument,
  Document,
  EditPen,
  Fold,
  Link,
  Loading,
  Microphone,
  Plus,
  Position,
  Refresh,
  Search,
  SwitchButton,
  Tools,
  Upload,
} from '@element-plus/icons-vue'
import { agentChat, getAgentSessions, getAgentSessionHistory, uploadAgentAttachment } from '../api'

const input = ref('')
const messages = ref([])
const sending = ref(false)
const webSearchEnabled = ref(false)
const sessionId = ref(null)
const historyOpen = ref(false)
const sessions = ref([])
const historySearch = ref('')
const loadingSessions = ref(false)
const loadingHistory = ref(false)
const box = ref(null)
const bottom = ref(null)
const textarea = ref(null)
const fileInput = ref(null)
const uploading = ref(false)
const attachments = ref([])
const abortController = ref(null)

const filteredSessions = computed(() => {
  const kw = historySearch.value.trim().toLowerCase()
  if (!kw) return sessions.value
  return sessions.value.filter(s => (s.title || '').toLowerCase().includes(kw))
})

function sessionTitle(s) {
  const t = (s.title || '新对话').trim()
  return t.length > 24 ? `${t.slice(0, 24)}...` : t
}

function formatTime(iso) {
  if (!iso) return ''
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return ''
  const now = new Date()
  const pad = n => String(n).padStart(2, '0')
  const time = `${pad(d.getHours())}:${pad(d.getMinutes())}`
  const isToday = d.getFullYear() === now.getFullYear()
    && d.getMonth() === now.getMonth()
    && d.getDate() === now.getDate()
  if (isToday) return time
  const date = `${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
  if (d.getFullYear() === now.getFullYear()) return `${date} ${time}`
  return `${d.getFullYear()}-${date} ${time}`
}

function triggerUpload() {
  fileInput.value?.click()
}

async function onFileSelected(e) {
  const file = e.target.files?.[0]
  if (!file) return
  uploading.value = true
  try {
    const resp = await uploadAgentAttachment(file)
    const body = await resp.json()
    if (body && String(body.code) === '200' && body.data) {
      if (body.data.error) {
        ElMessage.error(body.data.error)
      } else {
        attachments.value.push({
          name: body.data.name || file.name,
          size: body.data.size || file.size,
          path: body.data.path || '',
        })
      }
    } else {
      ElMessage.error(body?.message || '上传失败')
    }
  } catch (e2) {
    ElMessage.error(`上传失败: ${e2.message || '网络错误'}`)
  } finally {
    uploading.value = false
    if (fileInput.value) fileInput.value.value = ''
  }
}

function removeAttachment(i) {
  attachments.value.splice(i, 1)
}

function normalizeSession(session) {
  return { ...session, id: String(session.id) }
}

function isApiSuccess(body) {
  return body && String(body.code) === '200'
}

async function loadSessions() {
  loadingSessions.value = true
  try {
    const res = await getAgentSessions()
    const body = res.data
    sessions.value = isApiSuccess(body) ? (body.data || []).map(normalizeSession) : []
  } catch {
    sessions.value = []
  } finally {
    loadingSessions.value = false
  }
}

async function openHistoryPanel() {
  historyOpen.value = true
  await loadSessions()
}

function startNewChat() {
  if (sending.value) stopGenerating()
  sessionId.value = null
  messages.value = []
  input.value = ''
  attachments.value = []
  historyOpen.value = false
}

async function openSession(session) {
  if (sending.value || loadingHistory.value) return
  loadingHistory.value = true
  const sid = String(session.id)
  try {
    const res = await getAgentSessionHistory(sid)
    const body = res.data
    if (!isApiSuccess(body)) {
      ElMessage.error(body?.message || '加载历史失败')
      return
    }
    const history = body.data || []
    sessionId.value = sid
    messages.value = history.map(m => ({
      role: m.role,
      content: m.content || '',
      toolName: m.toolName || '',
      createdAt: m.createdAt || null,
    }))
    historyOpen.value = false
    if (messages.value.length === 0) ElMessage.info('该会话暂无消息')
    await scrollBottom()
  } catch (e) {
    ElMessage.error(e.response?.data?.message || e.message || '加载历史失败')
  } finally {
    loadingHistory.value = false
  }
}

onMounted(() => {
  loadSessions()
  window.addEventListener('chat:trigger-upload', triggerUpload)
  window.addEventListener('chat:open-history', openHistoryPanel)
})

onUnmounted(() => {
  abortController.value?.abort()
  window.removeEventListener('chat:trigger-upload', triggerUpload)
  window.removeEventListener('chat:open-history', openHistoryPanel)
})

function sendQuick(q) {
  input.value = q
  send()
}

function escapeHtml(text) {
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

function renderInlineMarkdown(text) {
  return text
    .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
    .replace(/\*(.*?)\*/g, '<em>$1</em>')
    .replace(/`(.*?)`/g, '<code>$1</code>')
}

function splitTableRow(line) {
  return line
    .trim()
    .replace(/^\|/, '')
    .replace(/\|$/, '')
    .split('|')
    .map(cell => cell.trim())
}

function splitTabTableRow(line) {
  return line.split('\t').map(cell => cell.trim())
}

function isTableSeparator(line) {
  const cells = splitTableRow(line)
  return cells.length > 1 && cells.every(cell => /^:?-{3,}:?$/.test(cell))
}

function renderTableFromRows(headers, rows) {
  const head = headers
    .map(cell => `<th>${renderInlineMarkdown(cell)}</th>`)
    .join('')
  const body = rows
    .map(row => `<tr>${headers.map((_, index) => `<td>${renderInlineMarkdown(row[index] || '')}</td>`).join('')}</tr>`)
    .join('')
  return `<div class="markdown-table-wrap"><table><thead><tr>${head}</tr></thead><tbody>${body}</tbody></table></div>`
}

function renderMarkdownTable(headerLine, rowLines) {
  return renderTableFromRows(splitTableRow(headerLine), rowLines.map(splitTableRow))
}

function isTabTableLine(line) {
  return line.includes('\t') && splitTabTableRow(line).length > 1
}

function renderMsg(msg) {
  if (!msg.content) return ''
  const lines = escapeHtml(msg.content).split('\n')
  const html = []
  for (let i = 0; i < lines.length; i += 1) {
    const line = lines[i]
    if (line.includes('|') && lines[i + 1] && isTableSeparator(lines[i + 1])) {
      const rowLines = []
      i += 2
      while (i < lines.length && lines[i].includes('|') && lines[i].trim()) {
        rowLines.push(lines[i])
        i += 1
      }
      i -= 1
      html.push(renderMarkdownTable(line, rowLines))
      continue
    }
    if (isTabTableLine(line) && lines[i + 1] && isTabTableLine(lines[i + 1])) {
      const headers = splitTabTableRow(line)
      const rowLines = []
      i += 1
      while (i < lines.length && isTabTableLine(lines[i]) && splitTabTableRow(lines[i]).length === headers.length) {
        rowLines.push(lines[i])
        i += 1
      }
      i -= 1
      html.push(renderTableFromRows(headers, rowLines.map(splitTabTableRow)))
      continue
    }
    if (/^- /.test(line)) {
      html.push(`<li>${renderInlineMarkdown(line.replace(/^- /, ''))}</li>`)
      continue
    }
    html.push(renderInlineMarkdown(line))
  }
  return html.join('<br>')
}

async function scrollBottom() {
  await nextTick()
  bottom.value?.scrollIntoView({ behavior: 'smooth', block: 'end' })
}

function autoResize() {
  const el = textarea.value
  if (!el) return
  el.style.height = 'auto'
  el.style.height = `${Math.min(el.scrollHeight, 180)}px`
}

function isLastAssistant(index) {
  for (let i = messages.value.length - 1; i >= 0; i -= 1) {
    if (messages.value[i].role === 'assistant') return i === index
  }
  return false
}

async function copyMessage(msg) {
  try {
    await navigator.clipboard.writeText(msg.content || '')
    ElMessage.success('已复制')
  } catch {
    ElMessage.error('复制失败')
  }
}

function stopGenerating() {
  abortController.value?.abort()
}

async function regenerateLast() {
  if (sending.value) return
  const userIndex = [...messages.value].map(m => m.role).lastIndexOf('user')
  if (userIndex < 0) return
  const userMsg = messages.value[userIndex]
  messages.value = messages.value.slice(0, userIndex + 1)
  await runChat(userMsg.rawContent || userMsg.content || '', [], userMsg.content || '', false)
}

async function send() {
  if (!input.value.trim() || sending.value) return
  const text = input.value.trim()
  const files = [...attachments.value]
  input.value = ''
  attachments.value = []
  nextTick(autoResize)
  const displayContent = files.length
    ? `${text}\n\n附件：${files.map(f => f.name).join(', ')}`
    : text
  await runChat(text, files, displayContent, true)
}

async function runChat(text, files, displayContent, appendUser) {
  const now = new Date().toISOString()
  if (appendUser) {
    messages.value.push({ role: 'user', content: displayContent, rawContent: text, createdAt: now })
  }
  messages.value.push({ role: 'assistant', content: '', createdAt: null, typing: true })
  sending.value = true
  abortController.value = new AbortController()
  await scrollBottom()

  const msg = messages.value[messages.value.length - 1]
  try {
    const resp = await agentChat(sessionId.value, text, webSearchEnabled.value, files, {
      signal: abortController.value.signal,
    })
    if (!resp.ok) throw new Error(`HTTP ${resp.status}`)
    const reader = resp.body.getReader()
    const decoder = new TextDecoder()
    msg.typing = false
    let buffer = ''
    let currentEvent = ''
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      const lines = buffer.split('\n')
      buffer = lines.pop() || ''
      for (const line of lines) {
        const trimmed = line.trim()
        if (!trimmed) {
          currentEvent = ''
          continue
        }
        if (trimmed.startsWith('event:')) {
          currentEvent = trimmed.substring(6).trim()
          continue
        }
        if (!trimmed.startsWith('data:')) continue
        const json = trimmed.substring(5).trim()
        if (!json) continue
        try {
          const d = JSON.parse(json)
          if (currentEvent === 'error') {
            msg.content = d.message || '对话处理异常，请重试。'
          } else if (currentEvent === 'done' && d.sessionId != null) {
            sessionId.value = String(d.sessionId)
            loadSessions()
          } else if (d.delta) {
            msg.content += d.delta
          }
        } catch {}
      }
      await scrollBottom()
    }
    if (!msg.content) msg.content = '收到响应但未获取到内容，请重试。'
  } catch (e) {
    msg.typing = false
    if (e.name === 'AbortError') {
      msg.content = msg.content || '已停止生成。'
    } else {
      msg.content = 'Agent 服务暂未启动。请确认后端服务和知识库问答接口可用后重试。'
    }
  } finally {
    msg.typing = false
    msg.createdAt = new Date().toISOString()
    sending.value = false
    abortController.value = null
    await scrollBottom()
  }
}
</script>

<style scoped>
.chatgpt-page {
  display: flex;
  width: 100%;
  height: 100%;
  min-height: 0;
  background: #ffffff;
  color: #0d0d0d;
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
  position: relative;
  overflow: hidden;
}

.chat-sidebar {
  position: absolute;
  z-index: 30;
  top: 0;
  bottom: 0;
  left: 0;
  width: 272px;
  display: flex;
  flex-direction: column;
  min-height: 0;
  padding: 10px;
  background: #f9f9f9;
  border-right: 1px solid #ececec;
  box-shadow: 4px 0 24px rgba(0, 0, 0, 0.12);
  transform: translateX(-100%);
  transition: transform 0.2s ease;
}

.chatgpt-page--sidebar-open .chat-sidebar {
  transform: translateX(0);
}

.sidebar-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.icon-btn {
  width: 36px;
  height: 36px;
  border: 0;
  border-radius: 10px;
  background: transparent;
  color: #5d5d5d;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}

.icon-btn:hover {
  background: #ececec;
  color: #111111;
}

.new-chat-btn {
  width: 100%;
  height: 42px;
  border: 0;
  border-radius: 10px;
  background: transparent;
  color: #171717;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 10px;
  font-size: 14px;
  cursor: pointer;
}

.new-chat-btn:hover {
  background: #ececec;
}

.sidebar-search {
  height: 38px;
  margin: 8px 0 12px;
  border-radius: 10px;
  background: #eeeeee;
  color: #777777;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 10px;
}

.sidebar-search input {
  min-width: 0;
  width: 100%;
  border: 0;
  outline: 0;
  background: transparent;
  color: #1f1f1f;
  font-size: 13px;
}

.session-section {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.session-section-title {
  padding: 8px 10px;
  font-size: 12px;
  color: #777777;
}

.session-list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
}

.session-item {
  width: 100%;
  height: 36px;
  border: 0;
  border-radius: 10px;
  background: transparent;
  color: #2f2f2f;
  display: block;
  padding: 0 10px;
  text-align: left;
  font-size: 14px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  cursor: pointer;
}

.session-item:hover,
.session-item--active {
  background: #ececec;
}

.session-empty {
  padding: 18px 10px;
  color: #8a8a8a;
  font-size: 13px;
  text-align: center;
}

.chat-main {
  flex: 1;
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
  position: relative;
  background: #ffffff;
}

.conversation {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 24px 20px 0;
}

.conversation--empty {
  display: flex;
  align-items: center;
  justify-content: center;
  padding-bottom: 96px;
}

.empty-state {
  width: min(820px, 100%);
  display: flex;
  flex-direction: column;
  align-items: stretch;
}

.empty-state h1 {
  margin: 0 0 30px;
  font-size: 32px;
  line-height: 1.2;
  font-weight: 600;
  color: #2f2f2f;
  letter-spacing: 0;
  text-align: center;
}

.thread {
  width: min(760px, 100%);
  margin: 0 auto;
  padding-bottom: 28px;
}

.message-row {
  display: flex;
  flex-direction: column;
  margin: 0 0 24px;
}

.message-row--user {
  align-items: flex-end;
}

.message-row--assistant {
  align-items: stretch;
}

.user-card {
  max-width: min(620px, 86%);
  border-radius: 22px;
  background: #f4f4f4;
  color: #111111;
  padding: 10px 16px;
}

.message-text {
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 15px;
  line-height: 1.6;
}

.assistant-card {
  width: 100%;
  color: #111111;
  font-size: 15px;
  line-height: 1.75;
}

.markdown-body {
  word-break: break-word;
}

.markdown-body :deep(strong) {
  font-weight: 700;
}

.markdown-body :deep(em) {
  font-style: italic;
}

.markdown-body :deep(code) {
  padding: 2px 5px;
  border-radius: 5px;
  background: #f1f1f1;
  color: #111111;
  font-size: 13px;
}

.markdown-body :deep(li) {
  margin-left: 20px;
  padding: 2px 0;
}

.markdown-body :deep(.markdown-table-wrap) {
  width: 100%;
  overflow-x: auto;
  margin: 10px 0;
  border: 1px solid #e5e5e5;
  border-radius: 10px;
}

.markdown-body :deep(table) {
  width: 100%;
  border-collapse: collapse;
  min-width: 420px;
  background: #ffffff;
}

.markdown-body :deep(th),
.markdown-body :deep(td) {
  padding: 10px 12px;
  border-bottom: 1px solid #ededed;
  border-right: 1px solid #ededed;
  text-align: left;
  vertical-align: top;
  font-size: 14px;
  line-height: 1.5;
}

.markdown-body :deep(th:last-child),
.markdown-body :deep(td:last-child) {
  border-right: 0;
}

.markdown-body :deep(thead th) {
  background: #f7f7f7;
  color: #333333;
  font-weight: 600;
}

.markdown-body :deep(tbody tr:last-child td) {
  border-bottom: 0;
}

.assistant-actions {
  display: flex;
  gap: 4px;
  margin-top: 8px;
  min-height: 30px;
}

.action-btn {
  width: 30px;
  height: 30px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: #777777;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}

.action-btn:hover:not(:disabled) {
  background: #f2f2f2;
  color: #111111;
}

.action-btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.message-time {
  margin-top: 6px;
  color: #9b9b9b;
  font-size: 12px;
}

.message-time--assistant {
  margin-top: 0;
}

.typing-line {
  display: flex;
  align-items: center;
  gap: 5px;
  height: 28px;
}

.typing-line span {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #737373;
  animation: typingPulse 1s infinite ease-in-out;
}

.typing-line span:nth-child(2) {
  animation-delay: 0.14s;
}

.typing-line span:nth-child(3) {
  animation-delay: 0.28s;
}

@keyframes typingPulse {
  0%,
  80%,
  100% {
    opacity: 0.25;
    transform: translateY(0);
  }
  40% {
    opacity: 1;
    transform: translateY(-3px);
  }
}

.tool-call {
  width: 100%;
  border-radius: 10px;
  background: #f7f7f7;
  color: #4b4b4b;
  font-size: 13px;
  padding: 10px 12px;
}

.tool-call summary {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
}

.tool-call pre {
  margin: 10px 0 0;
  white-space: pre-wrap;
  word-break: break-word;
  color: #555555;
}

.composer-shell {
  flex: 0 0 auto;
  padding: 10px 20px 14px;
  background: #ffffff;
}

.composer-note {
  width: min(760px, 100%);
  margin: 8px auto 0;
  color: #8a8a8a;
  text-align: center;
  font-size: 12px;
}

.chat-composer {
  width: min(820px, 100%);
  margin: 0 auto;
  border: 1px solid #dcdcdc;
  border-radius: 26px;
  background: #ffffff;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
  padding: 14px 16px 12px;
}

.chat-composer:focus-within {
  border-color: #c7c7c7;
  box-shadow: 0 4px 18px rgba(0, 0, 0, 0.08);
}

.composer-input {
  width: 100%;
  min-height: 44px;
  max-height: 180px;
  resize: none;
  border: 0;
  outline: 0;
  background: transparent;
  color: #111111;
  font-family: inherit;
  font-size: 15px;
  line-height: 1.55;
  padding: 4px 2px 8px;
}

.composer-input::placeholder {
  color: #9b9b9b;
}

.composer-input:disabled {
  opacity: 0.72;
}

.composer-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.composer-actions-left {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.composer-tool {
  height: 34px;
  border: 1px solid #e5e5e5;
  border-radius: 18px;
  background: #ffffff;
  color: #5f5f5f;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 0 11px;
  font-size: 13px;
  cursor: pointer;
}

.composer-tool:hover:not(:disabled) {
  background: #f7f7f7;
}

.composer-tool--active {
  border-color: #9bc7a8;
  background: #edf7f0;
  color: #176b36;
}

.composer-tool--icon-only {
  width: 34px;
  padding: 0;
}

.composer-tool:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.composer-send {
  width: 34px;
  height: 34px;
  border: 0;
  border-radius: 50%;
  background: #111111;
  color: #ffffff;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}

.composer-send:hover:not(:disabled) {
  background: #2f2f2f;
}

.composer-send:disabled {
  background: #d8d8d8;
  color: #ffffff;
  cursor: not-allowed;
}

.composer-send--stop {
  background: #111111;
}

.attachment-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 8px;
}

.attachment-chip {
  max-width: 100%;
  min-width: 0;
  height: 32px;
  border-radius: 16px;
  background: #f2f2f2;
  color: #333333;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 0 8px;
  font-size: 12px;
}

.attachment-icon {
  flex: 0 0 auto;
}

.attachment-name {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.attachment-remove {
  width: 18px;
  height: 18px;
  border: 0;
  border-radius: 50%;
  background: transparent;
  color: #777777;
  cursor: pointer;
}

.attachment-remove:hover {
  background: #e2e2e2;
  color: #111111;
}

.suggestions {
  width: min(760px, 100%);
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  margin-top: 18px;
}

.suggestion {
  min-height: 56px;
  border: 1px solid #ececec;
  border-radius: 14px;
  background: #ffffff;
  color: #3f3f3f;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  text-align: left;
  font-size: 13px;
  line-height: 1.35;
  cursor: pointer;
}

.suggestion:hover {
  background: #f7f7f7;
}

.icon-spin {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.hidden-file {
  display: none;
}

.mobile-backdrop {
  display: block;
  position: absolute;
  inset: 0;
  z-index: 20;
  background: rgba(0, 0, 0, 0.18);
}

@media (max-width: 900px) {
  .conversation {
    padding: 18px 14px 0;
  }

  .empty-state h1 {
    font-size: 26px;
    text-align: center;
  }

  .suggestions {
    grid-template-columns: 1fr;
  }

  .composer-shell {
    padding: 8px 12px 12px;
  }
}

@media (max-width: 560px) {
  .composer-tool span {
    display: none;
  }

  .composer-tool {
    width: 34px;
    padding: 0;
  }

  .user-card {
    max-width: 92%;
  }
}
</style>
