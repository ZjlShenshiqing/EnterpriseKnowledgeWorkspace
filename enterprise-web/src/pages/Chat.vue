<template>
  <div class="chat-fullpage">
    <!-- Messages Area -->
    <div
      class="messages-container"
      ref="box"
      :class="{ 'messages-container--landing': !messages.length }"
    >
      <!-- 飞书式落地：白底居中 + 大输入卡 + 推荐问 -->
      <div v-if="!messages.length" class="chat-landing">
        <div class="feishu-logo-ring" aria-hidden="true">
          <svg width="88" height="88" viewBox="0 0 88 88" fill="none" xmlns="http://www.w3.org/2000/svg">
            <defs>
              <linearGradient id="chatRingGrad" x1="0%" y1="0%" x2="100%" y2="100%">
                <stop offset="0%" stop-color="#6366f1"/>
                <stop offset="22%" stop-color="#d946ef"/>
                <stop offset="45%" stop-color="#f43f5e"/>
                <stop offset="68%" stop-color="#f97316"/>
                <stop offset="88%" stop-color="#facc15"/>
                <stop offset="100%" stop-color="#22d3ee"/>
              </linearGradient>
            </defs>
            <circle cx="44" cy="44" r="34" fill="none" stroke="url(#chatRingGrad)" stroke-width="9" stroke-linecap="round"/>
          </svg>
        </div>

        <h1 class="chat-landing-title">智能知识问答</h1>
        <p class="chat-landing-sub">整合企业知识库，AI 搜索生成回答</p>

        <div class="composer-card">
          <textarea
            v-model="input"
            class="composer-textarea"
            placeholder="试试输入“/”，支持深入研究、写作、带图提问"
            @keydown.enter.exact.prevent="send"
            :disabled="sending"
            rows="3"
            ref="textarea"
            @input="autoResize"
          />
          <div class="composer-bar">
            <div class="composer-bar-left">
              <button type="button" class="btn-icon-circle" title="添加（即将支持）" @click.prevent>
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/>
                </svg>
              </button>
              <span class="pill-web" :class="{ 'pill-web--active': webSearchEnabled }" @click="webSearchEnabled = !webSearchEnabled">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <circle cx="12" cy="12" r="10"/><path d="M2 12h20M12 2a15 15 0 0 1 0 20 15 15 0 0 1 0-20"/>
                </svg>
                联网搜索
              </span>
            </div>
            <div class="composer-bar-right">
              <button type="button" class="btn-icon-circle" title="更多" @click.prevent>
                <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor">
                  <circle cx="5" cy="12" r="1.5"/><circle cx="12" cy="12" r="1.5"/><circle cx="19" cy="12" r="1.5"/>
                </svg>
              </button>
              <button
                type="button"
                class="send-btn-circle"
                :disabled="sending || !input.trim()"
                @click="send"
              >
                <svg v-if="!sending" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2">
                  <polyline points="9 18 15 12 9 6"/>
                </svg>
                <span v-else class="send-btn-dots send-btn-dots--on-light" aria-hidden="true">
                  <span></span><span></span><span></span>
                </span>
              </button>
            </div>
          </div>
        </div>

        <div class="suggestion-row">
          <button
            v-for="(card, index) in suggestionCards"
            :key="card.text"
            type="button"
            class="suggestion-card"
            :style="{ animationDelay: `${index * 0.06}s` }"
            @click="sendQuick(card.text)"
          >
            <span class="suggestion-card-icon" :class="'suggestion-card-icon--' + card.tone" v-html="card.iconSvg"></span>
            <span class="suggestion-card-text">{{ card.text }}</span>
          </button>
        </div>

        <div class="landing-footer">
          <svg class="landing-footer-icon" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <polyline points="18 15 12 9 6 15"/><polyline points="18 21 12 15 6 21"/>
          </svg>
          <span>探索知识库，发现优质内容</span>
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

    <!-- 对话中：底部同款大圆角输入卡 -->
    <div v-if="messages.length" class="input-area input-area--thread">
      <div class="composer-card composer-card--thread">
        <textarea
          v-model="input"
          class="composer-textarea composer-textarea--thread"
          placeholder="试试输入“/”，支持深入研究、写作、带图提问"
          @keydown.enter.exact.prevent="send"
          :disabled="sending"
          rows="1"
          ref="textarea"
          @input="autoResize"
        />
        <div class="composer-bar">
          <div class="composer-bar-left">
            <button type="button" class="btn-icon-circle" title="添加（即将支持）" @click.prevent>
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/>
              </svg>
            </button>
            <span class="pill-web" :class="{ 'pill-web--active': webSearchEnabled }" @click="webSearchEnabled = !webSearchEnabled">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <circle cx="12" cy="12" r="10"/><path d="M2 12h20M12 2a15 15 0 0 1 0 20 15 15 0 0 1 0-20"/>
              </svg>
              联网搜索
            </span>
          </div>
          <div class="composer-bar-right">
            <button type="button" class="btn-icon-circle" title="更多" @click.prevent>
              <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor">
                <circle cx="5" cy="12" r="1.5"/><circle cx="12" cy="12" r="1.5"/><circle cx="19" cy="12" r="1.5"/>
              </svg>
            </button>
            <button
              type="button"
              class="send-btn-circle"
              :disabled="sending || !input.trim()"
              @click="send"
            >
              <svg v-if="!sending" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2">
                <polyline points="9 18 15 12 9 6"/>
              </svg>
              <span v-else class="send-btn-dots send-btn-dots--on-light" aria-hidden="true">
                <span></span><span></span><span></span>
              </span>
            </button>
          </div>
        </div>
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
const webSearchEnabled = ref(false)
const box = ref(null)
const bottom = ref(null)
const textarea = ref(null)

const suggestionCards = [
  {
    text: '企业知识问答能做什么？',
    tone: 'amber',
    iconSvg:
      '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M15 14c.2-1 .7-1.7 1.5-2.5 1-.9 1.5-2.2 1.5-3.5A6 6 0 0 0 6 8c0 1 .2 2.2 1.5 3.5.7.7 1.3 1.5 1.5 2.5"/><path d="M9 18h6"/><path d="M10 22h4"/></svg>',
  },
  {
    text: '如何用 AI 提高我的工作效率？',
    tone: 'blue',
    iconSvg:
      '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><path d="M14 2v6h6M16 13H8M16 17H8M10 9H8"/></svg>',
  },
  {
    text: '最近上传的文档有哪些？',
    tone: 'violet',
    iconSvg:
      '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M3 3v18h18"/><path d="M7 16l4-4 4 4 5-7"/></svg>',
  },
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
    const resp = await agentChat(null, text, webSearchEnabled.value)
    if (!resp.ok) throw new Error('HTTP ' + resp.status)
    const reader = resp.body.getReader()
    const decoder = new TextDecoder()
    const msg = messages.value[messages.value.length - 1]
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
        if (!trimmed) { currentEvent = ''; continue }
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
          } else if (d.delta) {
            msg.content += d.delta
          }
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

.messages-container--landing {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  justify-content: center;
  padding: 20px 24px 40px;
  background: #fff;
}

.chat-landing {
  width: 100%;
  max-width: 680px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.feishu-logo-ring {
  margin: 12px 0 4px;
  flex-shrink: 0;
}

.chat-landing-title {
  font-size: 26px;
  font-weight: 600;
  color: #111827;
  letter-spacing: -0.02em;
  margin: 18px 0 10px;
  text-align: center;
}

.chat-landing-sub {
  font-size: 14px;
  line-height: 1.5;
  color: #6b7280;
  text-align: center;
  margin-bottom: 28px;
  max-width: 420px;
}

.composer-card {
  width: 100%;
  background: #fff;
  border: 1px solid #e8e8e8;
  border-radius: 16px;
  box-shadow: 0 4px 28px rgba(15, 23, 42, 0.06);
  padding: 16px 16px 12px;
  transition: border-color 0.2s ease, box-shadow 0.2s ease;
}

.composer-card:focus-within {
  border-color: #ddd6fe;
  box-shadow: 0 6px 32px rgba(124, 58, 237, 0.08);
}

.composer-card--thread {
  box-shadow: 0 2px 16px rgba(15, 23, 42, 0.05);
}

.composer-textarea {
  width: 100%;
  border: none;
  outline: none;
  resize: none;
  font-size: 15px;
  line-height: 1.55;
  color: #1f2937;
  font-family: inherit;
  min-height: 72px;
  max-height: 200px;
  background: transparent;
}

.composer-textarea::placeholder {
  color: #9ca3af;
}

.composer-textarea:disabled {
  opacity: 0.65;
  cursor: not-allowed;
}

.composer-textarea--thread {
  min-height: 44px;
  max-height: 160px;
}

.composer-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 4px;
  padding-top: 12px;
  border-top: 1px solid #f3f4f6;
}

.composer-bar-left,
.composer-bar-right {
  display: flex;
  align-items: center;
  gap: 10px;
}

.btn-icon-circle {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  border: 1px solid #e5e7eb;
  background: #fff;
  color: #6b7280;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: background 0.15s ease, border-color 0.15s ease, color 0.15s ease;
}

.btn-icon-circle:hover {
  background: #f9fafb;
  border-color: #d1d5db;
  color: #374151;
}

.pill-web {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  border-radius: 20px;
  border: 1px solid #e5e7eb;
  background: #fafafa;
  font-size: 13px;
  color: #4b5563;
  user-select: none;
  cursor: pointer;
  transition: all 0.2s ease;
}

.pill-web:hover {
  background: #f0f0f0;
  border-color: #d0d0d0;
}

.pill-web--active {
  background: #eff6ff;
  border-color: #6366f1;
  color: #4f46e5;
}

.pill-web--active:hover {
  background: #e0eeff;
  border-color: #4f46e5;
}

.send-btn-circle {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  border: none;
  background: linear-gradient(135deg, #6366f1 0%, #a855f7 45%, #ec4899 100%);
  color: #fff;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  flex-shrink: 0;
  transition: transform 0.15s ease, opacity 0.15s ease;
}

.send-btn-circle:hover:not(:disabled) {
  transform: scale(1.04);
}

.send-btn-circle:disabled {
  background: #e5e7eb;
  color: #9ca3af;
  cursor: not-allowed;
  transform: none;
}

.send-btn-dots--on-light > span {
  background: #6b7280;
}

.suggestion-row {
  width: 100%;
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  margin-top: 20px;
}

@media (max-width: 720px) {
  .suggestion-row {
    grid-template-columns: 1fr;
  }
}

.suggestion-card {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 14px 14px;
  text-align: left;
  border: 1px solid #ebebeb;
  border-radius: 12px;
  background: #fff;
  cursor: pointer;
  transition: background 0.15s ease, border-color 0.15s ease, box-shadow 0.15s ease;
  animation: suggestionFade 0.45s ease forwards;
  opacity: 0;
}

@keyframes suggestionFade {
  from {
    opacity: 0;
    transform: translateY(8px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.suggestion-card:hover {
  background: #fafafa;
  border-color: #e0e0e0;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.04);
}

.suggestion-card-icon {
  flex-shrink: 0;
  width: 36px;
  height: 36px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #4b5563;
}

.suggestion-card-icon svg {
  display: block;
}

.suggestion-card-icon--amber {
  background: #fffbeb;
  color: #b45309;
}

.suggestion-card-icon--blue {
  background: #eff6ff;
  color: #1d4ed8;
}

.suggestion-card-icon--violet {
  background: #f5f3ff;
  color: #6d28d9;
}

.suggestion-card-text {
  font-size: 13px;
  line-height: 1.45;
  color: #374151;
}

.landing-footer {
  margin-top: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  font-size: 12px;
  color: #9ca3af;
}

.landing-footer-icon {
  color: #c4c4c4;
  flex-shrink: 0;
}

/* 对话线程底部输入区 */
.input-area {
  flex-shrink: 0;
}

.input-area--thread {
  padding: 12px 20px 20px;
  background: #fafafa;
  border-top: 1px solid #f0f0f0;
}

.input-area--thread .composer-card--thread {
  max-width: min(720px, 100%);
  margin: 0 auto;
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

.send-btn-dots {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 3px;
  width: 18px;
  height: 18px;
}

.send-btn-dots > span {
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background: #fff;
  animation: sendDotBounce 0.85s ease-in-out infinite;
}

.send-btn-dots > span:nth-child(2) {
  animation-delay: 0.14s;
}

.send-btn-dots > span:nth-child(3) {
  animation-delay: 0.28s;
}

@keyframes sendDotBounce {
  0%, 70%, 100% {
    transform: translateY(0);
    opacity: 0.55;
  }
  35% {
    transform: translateY(-5px);
    opacity: 1;
  }
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
