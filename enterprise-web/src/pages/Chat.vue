<template>
  <div class="chat-fullpage">
    <!-- Messages Area -->
    <div class="messages-container" ref="box">
      <!-- Empty State -->
      <div v-if="!messages.length" class="empty-state">
        <div class="empty-logo">
          <svg width="96" height="96" viewBox="0 0 100 100" fill="none">
            <defs>
              <linearGradient id="emptyLogoGradient" x1="0%" y1="0%" x2="100%" y2="100%">
                <stop offset="0%" style="stop-color:#ff6b9d"/>
                <stop offset="50%" style="stop-color:#c084fc"/>
                <stop offset="100%" style="stop-color:#22d3ee"/>
              </linearGradient>
              <filter id="logoGlow">
                <feGaussianBlur stdDeviation="2" result="coloredBlur"/>
                <feMerge>
                  <feMergeNode in="coloredBlur"/>
                  <feMergeNode in="SourceGraphic"/>
                </feMerge>
              </filter>
            </defs>
            <circle cx="50" cy="50" r="40" fill="none" stroke="url(#emptyLogoGradient)" stroke-width="6" stroke-linecap="round" stroke-dasharray="200 200" transform="rotate(-90 50 50)" filter="url(#logoGlow)">
              <animateTransform attributeName="transform" type="rotate" from="-90 50 50" to="270 50 50" dur="3s" repeatCount="indefinite"/>
              <animate attributeName="stroke-dashoffset" from="0" to="-400" dur="3s" repeatCount="indefinite"/>
            </circle>
          </svg>
        </div>
        <div class="empty-title">智能助手</div>
        <div class="empty-subtitle">整合企业知识库，AI搜索生成回答</div>
        
        <div class="quick-actions">
          <div 
            v-for="(action, index) in quickActions" 
            :key="action.label"
            class="quick-action-card"
            :style="{ animationDelay: `${index * 0.1}s` }"
            @click="sendQuick(action.text)"
          >
            <div class="quick-action-icon" :style="{ background: action.color }">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path :d="action.icon"/>
              </svg>
            </div>
            <span class="quick-action-label">{{ action.label }}</span>
          </div>
        </div>
      </div>

      <!-- Message List -->
      <div v-for="(msg, i) in messages" :key="i" class="message-wrapper">
        <!-- User Message -->
        <div v-if="msg.role === 'user'" class="message user-message">
          <div class="message-bubble user-bubble">
            {{ msg.content }}
          </div>
          <div class="message-avatar user-avatar">
            <span>U</span>
          </div>
        </div>

        <!-- AI Message -->
        <div v-else class="message ai-message">
          <div class="message-avatar ai-avatar">
            <svg width="32" height="32" viewBox="0 0 48 48" fill="none">
              <defs>
                <linearGradient id="aiAvatarGradient" x1="0%" y1="0%" x2="100%" y2="100%">
                  <stop offset="0%" style="stop-color:#ff6b9d"/>
                  <stop offset="50%" style="stop-color:#c084fc"/>
                  <stop offset="100%" style="stop-color:#22d3ee"/>
                </linearGradient>
              </defs>
              <rect width="48" height="48" rx="12" fill="url(#aiAvatarGradient)"/>
              <path d="M24 12c-2.5 0-4.5 1-6 2.5l-2-2c2-2.5 4.5-4 8-4 6 0 10.5 4.5 10.5 10.5S30 29.5 24 29.5c-1.5 0-3-.3-4.5-.8l-3 3c1.5.8 3.5 1.3 5.5 1.3 7 0 12.5-5.5 12.5-12.5S31 12 24 12z" fill="#fff"/>
              <circle cx="17" cy="24" r="2" fill="#fff"/>
              <circle cx="24" cy="24" r="2" fill="#fff"/>
              <circle cx="31" cy="24" r="2" fill="#fff"/>
            </svg>
          </div>
          <div class="message-bubble ai-bubble">
            <div v-if="msg.typing" class="typing-container">
              <div class="typing-dots">
                <span></span>
                <span></span>
                <span></span>
              </div>
            </div>
            <div v-else v-html="renderMsg(msg)"></div>
          </div>
        </div>
      </div>
      <div ref="bottom" />
    </div>

    <!-- Input Area -->
    <div class="input-area">
      <div class="input-wrapper">
        <button class="input-btn" title="上传文件">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
            <polyline points="17 8 12 3 7 8"/>
            <line x1="12" y1="3" x2="12" y2="15"/>
          </svg>
        </button>
        <textarea 
          v-model="input" 
          placeholder="试试输入“/”，支持深入研究、写作、带图提问" 
          @keydown.enter.exact.prevent="send"
          :disabled="sending" 
          rows="1" 
          ref="textarea"
          class="message-input"
          @input="autoResize" 
        />
        <button 
          @click="send" 
          :disabled="sending || !input.trim()"
          class="send-btn"
        >
          <svg v-if="!sending" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <polyline points="9 18 15 12 9 6"/>
          </svg>
          <svg v-else width="18" height="18" viewBox="0 0 24 24" fill="none" class="send-loading">
            <circle cx="12" cy="12" r="10" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-dasharray="16 8"/>
          </svg>
        </button>
      </div>
      <div class="input-footer">
        <div class="footer-tags">
          <span class="footer-tag">联网搜索</span>
        </div>
        <span class="footer-text">探索知识库，发现优质内容</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, nextTick } from 'vue'
import { agentChat } from '../api'

const input = ref('')
const messages = ref([])
const sending = ref(false)
const box = ref(null)
const bottom = ref(null)
const textarea = ref(null)

const quickActions = [
  { label: '查找文档', text: '帮我找关于微服务架构的文档', icon: 'M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0zM10 7v3m0 0v3m0-3h3m-3 0H7', color: 'linear-gradient(135deg, #fbbf24 0%, #f59e0b 100%)' },
  { label: '浏览文档', text: '最近有哪些文档', icon: 'M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0zM10 7v3m0 0v3m0-3h3m-3 0H7', color: 'linear-gradient(135deg, #3b82f6 0%, #2563eb 100%)' },
  { label: '知识库', text: '有哪些知识库', icon: 'M12 6V4m0 2a2 2 0 100 4m0-4a2 2 0 110 4m-6 8a2 2 0 100-4m0 4a2 2 0 110-4m0 4v2m0-6V4m6 6v10m6-2a2 2 0 100-4m0 4a2 2 0 110-4m0 4v2m0-6V4', color: 'linear-gradient(135deg, #10b981 0%, #059669 100%)' },
  { label: '帮我总结', text: '帮我总结最近上传的文档', icon: 'M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z', color: 'linear-gradient(135deg, #8b5cf6 0%, #7c3aed 100%)' },
]

function sendQuick(q) { 
  input.value = q 
  send() 
}

function renderMsg(msg) {
  if (!msg.content) return ''
  return msg.content
    .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
    .replace(/\*(.*?)\*/g, '<em>$1</em>')
    .replace(/`(.*?)`/g, '<code style="background:#f1f5f9;padding:2px 6px;border-radius:4px;font-size:14px;color:#1e293b">$1</code>')
    .replace(/\n/g, '<br>')
    .replace(/^- (.*)$/gm, '<li style="margin-left:20px;padding:4px 0">$1</li>')
}

async function scrollBottom() { 
  await nextTick()
  bottom.value?.scrollIntoView({ behavior: 'smooth' }) 
}

function autoResize() {
  const el = textarea.value
  if (el) { 
    el.style.height = 'auto'
    el.style.height = Math.min(el.scrollHeight, 200) + 'px' 
  }
}

async function send() {
  if (!input.value.trim() || sending.value) return
  const text = input.value.trim()
  input.value = ''
  if (textarea.value) textarea.value.style.height = 'auto'
  
  messages.value.push({ role: 'user', content: text })
  messages.value.push({ role: 'assistant', content: '', typing: true })
  sending.value = true
  await scrollBottom()
  
  try {
    const resp = await agentChat(null, text)
    if (!resp.ok) throw new Error('HTTP ' + resp.status)
    const reader = resp.body.getReader()
    const decoder = new TextDecoder()
    const msg = messages.value[messages.value.length - 1]
    msg.typing = false
    
    let buffer = ''
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      const lines = buffer.split('\n')
      buffer = lines.pop() || ''
      for (const line of lines) {
        const trimmed = line.trim()
        if (!trimmed || !trimmed.startsWith('data:')) continue
        const json = trimmed.substring(5).trim()
        if (!json) continue
        try { 
          const d = JSON.parse(json)
          if (d.delta) msg.content += d.delta 
        } catch(e) {}
      }
      await scrollBottom()
    }
    if (!msg.content) msg.content = '收到响应但未获取到内容，请重试。'
  } catch(e) {
    const msg = messages.value[messages.value.length - 1]
    msg.typing = false
    msg.content = 'Agent服务暂未启动。以下为示例回复：\n\n为您找到以下文档：\n\n1. **微服务架构设计指南** (application/msword)\n   微服务架构的核心概念和最佳实践\n\n2. **2026年度技术规划** (application/pdf)\n   包含微服务架构相关的技术规划\n\n3. **系统架构评审纪要** (application/pdf)\n   相关架构评审内容\n\n需要我详细介绍哪篇文档？'
  }
  sending.value = false
  await scrollBottom()
}
</script>

<style>
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

/**
 * 在 MainLayout 主内容区内占满剩余高度与宽度（侧栏保留），由外层 flex 分配高度。
 */
.chat-fullpage {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
  width: 100%;
  height: 100%;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
}

/* Messages Container */
.messages-container {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
  background: #fafafa;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 80px 20px;
}

.empty-logo {
  margin-bottom: 24px;
}

.empty-title {
  font-size: 24px;
  font-weight: 600;
  color: #1f2937;
  margin-bottom: 8px;
}

.empty-subtitle {
  font-size: 14px;
  color: #9ca3af;
  margin-bottom: 40px;
}

.quick-actions {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  justify-content: center;
}

.quick-action-card {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 18px;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 20px;
  cursor: pointer;
  transition: all 0.25s ease;
  animation: slideUp 0.4s ease forwards;
  opacity: 0;
}

@keyframes slideUp {
  from {
    opacity: 0;
    transform: translateY(20px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.quick-action-card:hover {
  border-color: #d1d5db;
  background: #f9fafb;
  transform: translateY(-2px);
}

.quick-action-icon {
  width: 28px;
  height: 28px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  flex-shrink: 0;
}

.quick-action-label {
  font-size: 14px;
  color: #374151;
  font-weight: 500;
}

/* Message Wrapper */
.message-wrapper {
  padding: 14px 0;
}

.message {
  display: flex;
  gap: 10px;
  max-width: min(1080px, 100%);
}

.user-message {
  margin-left: auto;
  flex-direction: row-reverse;
}

.message-avatar {
  flex-shrink: 0;
}

.user-avatar {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  background: linear-gradient(135deg, #ff6b9d 0%, #c084fc 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  font-weight: 600;
  color: #fff;
}

.ai-avatar {
  display: flex;
  align-items: center;
  justify-content: center;
}

.message-bubble {
  padding: 14px 18px;
  border-radius: 18px;
  font-size: 14px;
  line-height: 1.6;
  word-break: break-word;
  white-space: pre-wrap;
}

.user-bubble {
  background: linear-gradient(135deg, #ff6b9d 0%, #c084fc 100%);
  color: #fff;
  border-bottom-right-radius: 6px;
}

.ai-bubble {
  background: #fff;
  color: #374151;
  border-radius: 18px;
  border-bottom-left-radius: 6px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
}

.typing-container {
  display: flex;
  align-items: center;
}

.typing-dots {
  display: flex;
  gap: 5px;
}

.typing-dots span {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #9ca3af;
  animation: typingBounce 1.4s ease-in-out infinite;
}

.typing-dots span:nth-child(2) { animation-delay: 0.2s; }
.typing-dots span:nth-child(3) { animation-delay: 0.4s; }

@keyframes typingBounce {
  0%, 60%, 100% { transform: translateY(0); opacity: 0.5; }
  30% { transform: translateY(-4px); opacity: 1; }
}

/* Input Area */
.input-area {
  padding: 12px 20px 20px;
  background: #fff;
  border-top: 1px solid #f3f4f6;
  flex-shrink: 0;
}

.input-wrapper {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 8px 8px 14px;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 24px;
  transition: all 0.2s ease;
}

.input-wrapper:focus-within {
  border-color: #c084fc;
  box-shadow: 0 0 0 3px rgba(192, 132, 252, 0.1);
}

.input-btn {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  border: none;
  background: transparent;
  color: #9ca3af;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s ease;
  flex-shrink: 0;
}

.input-btn:hover {
  background: #f3f4f6;
  color: #6b7280;
}

.message-input {
  flex: 1;
  border: none;
  outline: none;
  resize: none;
  font-size: 15px;
  line-height: 24px;
  max-height: 160px;
  background: transparent;
  font-family: inherit;
  color: #374151;
  padding: 6px 0;
}

.message-input::placeholder {
  color: #9ca3af;
}

.message-input:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.send-btn {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  border: none;
  background: linear-gradient(135deg, #ff6b9d 0%, #c084fc 100%);
  color: #fff;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s ease;
  flex-shrink: 0;
}

.send-btn:hover:not(:disabled) {
  transform: scale(1.05);
}

.send-btn:disabled {
  background: #d1d5db;
  cursor: not-allowed;
}

.send-loading {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

.input-footer {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  margin-top: 10px;
}

.footer-tags {
  display: flex;
  gap: 8px;
}

.footer-tag {
  padding: 4px 10px;
  background: #f3f4f6;
  border-radius: 12px;
  font-size: 12px;
  color: #6b7280;
}

.footer-text {
  font-size: 12px;
  color: #9ca3af;
}

/* Scrollbar */
::-webkit-scrollbar {
  width: 6px;
}

::-webkit-scrollbar-track {
  background: transparent;
}

::-webkit-scrollbar-thumb {
  background: #d1d5db;
  border-radius: 3px;
}

::-webkit-scrollbar-thumb:hover {
  background: #9ca3af;
}
</style>
