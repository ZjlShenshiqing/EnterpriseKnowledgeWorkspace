<template>
  <div style="height:calc(100vh - 140px);display:flex;flex-direction:column;max-width:800px;margin:0 auto">
    <!-- Messages Area -->
    <div style="flex:1;overflow-y:auto;padding:0 16px" ref="box">
      <!-- Empty State -->
      <div v-if="!messages.length" style="text-align:center;margin-top:15vh">
        <div style="width:60px;height:60px;margin:0 auto;display:flex;align-items:center;justify-content:center">
          <svg width="48" height="48" viewBox="0 0 48 48" fill="none"><rect width="48" height="48" rx="12" fill="#10a37f"/><path d="M24 12c-2.5 0-4.5 1-6 2.5l-2-2c2-2.5 4.5-4 8-4 6 0 10.5 4.5 10.5 10.5S30 29.5 24 29.5c-1.5 0-3-.3-4.5-.8l-3 3c1.5.8 3.5 1.3 5.5 1.3 7 0 12.5-5.5 12.5-12.5S31 12 24 12z" fill="#fff"/><circle cx="17" cy="24" r="2" fill="#fff"/><circle cx="24" cy="24" r="2" fill="#fff"/><circle cx="31" cy="24" r="2" fill="#fff"/></svg>
        </div>
        <div style="font-size:22px;font-weight:600;color:#2d2d2d;margin-top:16px">有什么可以帮助你的？</div>
        <div style="display:flex;gap:8px;justify-content:center;flex-wrap:wrap;margin-top:28px;padding:0 40px">
          <div v-for="s in suggestions" :key="s.label" @click="sendQuick(s.text)"
            style="padding:10px 16px;border:1px solid #e5e5e5;border-radius:12px;cursor:pointer;font-size:13px;color:#555;transition:all .2s;white-space:nowrap"
            @mouseenter="e=>{e.target.style.borderColor='#aaa';e.target.style.background='#f9f9f9'}"
            @mouseleave="e=>{e.target.style.borderColor='#e5e5e5';e.target.style.background='transparent'}">
            <div style="font-weight:500;margin-bottom:2px">{{ s.label }}</div>
            <div style="font-size:11px;color:#999">{{ s.desc }}</div>
          </div>
        </div>
      </div>

      <!-- Message List -->
      <div v-for="(msg,i) in messages" :key="i" style="padding:16px 0">
        <!-- User Message -->
        <div v-if="msg.role==='user'" style="display:flex;justify-content:flex-end;gap:12px;padding:0 8px">
          <div style="max-width:75%">
            <div style="background:#f4f4f4;padding:12px 18px;border-radius:18px;font-size:15px;line-height:1.6;color:#2d2d2d;white-space:pre-wrap">{{ msg.content }}</div>
          </div>
          <div style="width:30px;height:30px;border-radius:50%;background:#5436da;display:flex;align-items:center;justify-content:center;flex-shrink:0;color:#fff;font-size:13px;font-weight:600">U</div>
        </div>
        <!-- AI Message -->
        <div v-else style="display:flex;gap:12px;padding:0 8px">
          <svg width="30" height="30" viewBox="0 0 48 48" fill="none" style="flex-shrink:0"><rect width="48" height="48" rx="12" fill="#10a37f"/><path d="M24 12c-2.5 0-4.5 1-6 2.5l-2-2c2-2.5 4.5-4 8-4 6 0 10.5 4.5 10.5 10.5S30 29.5 24 29.5c-1.5 0-3-.3-4.5-.8l-3 3c1.5.8 3.5 1.3 5.5 1.3 7 0 12.5-5.5 12.5-12.5S31 12 24 12z" fill="#fff"/><circle cx="17" cy="24" r="2" fill="#fff"/><circle cx="24" cy="24" r="2" fill="#fff"/><circle cx="31" cy="24" r="2" fill="#fff"/></svg>
          <div style="max-width:85%;min-width:0">
            <div v-if="msg.typing" class="typing-dots"><span></span><span></span><span></span></div>
            <div v-else style="font-size:15px;line-height:1.8;color:#2d2d2d" v-html="renderMsg(msg)"></div>
          </div>
        </div>
      </div>
      <div ref="bottom" />
    </div>

    <!-- Input Bar -->
    <div style="padding:12px 16px 20px">
      <div style="display:flex;align-items:flex-end;gap:8px;border:1px solid #e5e5e5;border-radius:20px;padding:6px 6px 6px 18px;background:#fff;box-shadow:0 2px 12px rgba(0,0,0,0.04);transition:border-color .2s" :style="{borderColor: input ? '#666' : '#e5e5e5'}">
        <textarea v-model="input" placeholder="给智能助手发送消息" @keydown.enter.exact.prevent="send"
          :disabled="sending" rows="1" ref="textarea"
          style="flex:1;border:none;outline:none;resize:none;font-size:15px;line-height:34px;max-height:180px;background:transparent;font-family:inherit;padding:0;margin:0;align-self:center"
          @input="autoResize" />
        <div @click="send" :style="{width:'34px',height:'34px',borderRadius:'50%',display:'flex',alignItems:'center',justifyContent:'center',cursor:sending||!input.trim()?'default':'pointer',background:input.trim()?'#2d2d2d':'#e5e5e5',transition:'background .2s'}">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="#fff" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><line x1="12" y1="19" x2="12" y2="5"/><polyline points="5 12 12 5 19 12"/></svg>
        </div>
      </div>
      <div style="text-align:center;font-size:11px;color:#bbb;margin-top:8px">AI 助手可能会产生不准确信息，请核对重要内容</div>
    </div>
  </div>
</template>

<script setup>
import { ref, nextTick, watch } from 'vue'
import { agentChat } from '../api'

const input = ref('')
const messages = ref([])
const sending = ref(false)
const box = ref(null)
const bottom = ref(null)
const textarea = ref(null)

const suggestions = [
  { label: '查找文档', desc: '搜索知识库内容', text: '帮我找关于微服务架构的文档' },
  { label: '浏览文档', desc: '查看最近上传的文件', text: '最近有哪些文档' },
  { label: '知识库', desc: '了解可用的知识资源', text: '有哪些知识库' },
  { label: '帮我总结', desc: '了解文档核心内容', text: '帮我总结最近上传的文档' },
]

function sendQuick(q) { input.value = q; send() }

function renderMsg(msg) {
  if (!msg.content) return ''
  return msg.content
    .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
    .replace(/\*(.*?)\*/g, '<em>$1</em>')
    .replace(/`(.*?)`/g, '<code style="background:#f0f0f0;padding:2px 6px;border-radius:4px;font-size:13px">$1</code>')
    .replace(/\n/g, '<br>')
    .replace(/^- (.*)$/gm, '<li style="margin-left:20px">$1</li>')
}

async function scrollBottom() { await nextTick(); bottom.value?.scrollIntoView({ behavior:'smooth' }) }

function autoResize() {
  const el = textarea.value
  if (el) { el.style.height = 'auto'; el.style.height = Math.min(el.scrollHeight, 200) + 'px' }
}

async function send() {
  if (!input.value.trim() || sending.value) return
  const text = input.value.trim(); input.value = ''
  if (textarea.value) textarea.value.style.height = 'auto'
  messages.value.push({ role: 'user', content: text })
  messages.value.push({ role: 'assistant', content: '', typing: true })
  sending.value = true; await scrollBottom()
  try {
    const resp = await agentChat(null, text)
    if (!resp.ok) throw new Error('HTTP ' + resp.status)
    const reader = resp.body.getReader()
    const decoder = new TextDecoder()
    const msg = messages.value[messages.value.length-1]
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
        try { const d = JSON.parse(json); if (d.delta) msg.content += d.delta } catch(e) {}
      }
      await scrollBottom()
    }
    if (!msg.content) msg.content = '收到响应但未获取到内容，请重试。'
  } catch(e) {
    const msg = messages.value[messages.value.length-1]
    msg.typing = false
    msg.content = 'Agent服务暂未启动。以下为示例回复：\n\n为您找到以下文档：\n\n1. **微服务架构设计指南** (application/msword)\n   微服务架构的核心概念和最佳实践\n\n2. **2026年度技术规划** (application/pdf)\n   包含微服务架构相关的技术规划\n\n3. **系统架构评审纪要** (application/pdf)\n   相关架构评审内容\n\n需要我详细介绍哪篇文档？'
  }
  sending.value = false; await scrollBottom()
}
</script>

<style>
.typing-dots { display:flex; gap:4px; padding:12px 0; }
.typing-dots span { width:6px;height:6px;border-radius:50%;background:#bbb;animation:typingBounce 1.4s ease-in-out infinite; }
.typing-dots span:nth-child(2) { animation-delay:.2s; }
.typing-dots span:nth-child(3) { animation-delay:.4s; }
@keyframes typingBounce { 0%,60%,100% { transform:translateY(0) } 30% { transform:translateY(-5px) } }
</style>
