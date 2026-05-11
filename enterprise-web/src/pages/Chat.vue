<template>
  <div style="height:calc(100vh - 140px);display:flex;flex-direction:column">
    <div style="flex:1;overflow-y:auto;margin-bottom:12px" ref="box">
      <div v-if="!messages.length" style="text-align:center;margin-top:120px">
        <div style="width:80px;height:80px;border-radius:20px;background:linear-gradient(135deg,#409eff,#67c23a);margin:0 auto;display:flex;align-items:center;justify-content:center" class="pulse-icon">
          <el-icon :size="40" color="#fff"><ChatDotRound /></el-icon>
        </div>
        <div style="font-size:20px;color:#303133;margin-top:20px;font-weight:600">智能助手</div>
        <div style="font-size:14px;color:#909399;margin-top:8px">可以向我提问知识库文档相关问题</div>
        <div style="margin-top:24px;display:flex;gap:8px;justify-content:center;flex-wrap:wrap">
          <el-tag v-for="q in quickQuestions" :key="q" @click="sendQuick(q)" style="cursor:pointer;padding:6px 14px;transition:all .2s"
            @mouseenter="e=>{e.target.style.transform='translateY(-2px)';e.target.style.boxShadow='0 4px 12px rgba(64,158,255,0.3)'}"
            @mouseleave="e=>{e.target.style.transform='';e.target.style.boxShadow=''}">{{ q }}</el-tag>
        </div>
      </div>
      <div v-for="(msg,i) in messages" :key="i" style="margin-bottom:16px">
        <div v-if="msg.role==='user'" style="display:flex;justify-content:flex-end">
          <div style="background:#409eff;color:#fff;max-width:70%;padding:10px 14px;border-radius:12px 12px 4px 12px;white-space:pre-wrap;font-size:14px;line-height:1.6">{{ msg.content }}</div>
        </div>
        <div v-else style="display:flex;gap:10px">
          <el-avatar :size="32" style="flex-shrink:0;background:#67c23a"><el-icon><ChatDotRound /></el-icon></el-avatar>
          <div style="max-width:80%">
            <div style="font-size:12px;color:#909399;margin-bottom:4px">AI助手</div>
            <div v-if="msg.typing" class="typing-dots"><span></span><span></span><span></span></div>
            <div v-else style="background:#fff;padding:12px 16px;border-radius:4px 12px 12px 12px;white-space:pre-wrap;font-size:14px;line-height:1.7;border:1px solid #e4e7ed" v-html="renderMsg(msg)"></div>
          </div>
        </div>
      </div>
    </div>
    <div style="display:flex;gap:8px;background:#fff;padding:12px;border-radius:12px;box-shadow:0 2px 12px rgba(0,0,0,0.08)">
      <el-input v-model="input" placeholder="输入问题..." @keyup.enter="send" :disabled="sending" size="large" />
      <el-button type="primary" size="large" @click="send" :loading="sending">发送</el-button>
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

const quickQuestions = ['帮我找关于微服务架构的文档', '最近有哪些文档', '有哪些知识库']

function sendQuick(q) { input.value = q; send() }

function renderMsg(msg) {
  return (msg.content||'').replace(/\n/g,'<br>').replace(/\*\*(.*?)\*\*/g,'<b>$1</b>')
}

async function scrollBottom() { await nextTick(); box.value?.scrollTo({ top: box.value.scrollHeight, behavior:'smooth' }) }

async function send() {
  if (!input.value.trim() || sending.value) return
  const text = input.value.trim(); input.value = ''
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
        if (!trimmed) continue
        if (trimmed.startsWith('data:')) {
          const json = trimmed.substring(5).trim()
          if (!json) continue
          try {
            const d = JSON.parse(json)
            if (d.delta) { msg.content += d.delta; await scrollBottom() }
          } catch(e) { console.log('SSE parse skip:', json.substring(0,50)) }
        }
      }
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
.typing-dots { display:flex; gap:4px; padding:8px 16px; }
.typing-dots span { width:8px;height:8px;border-radius:50%;background:#c0c4cc;animation:typingBounce 1.4s ease-in-out infinite; }
.typing-dots span:nth-child(2) { animation-delay:.2s; }
.typing-dots span:nth-child(3) { animation-delay:.4s; }
@keyframes typingBounce { 0%,60%,100% { transform:translateY(0) } 30% { transform:translateY(-6px) } }
.pulse-icon { animation: pulse 2s ease-in-out infinite; }
@keyframes pulse { 0%,100% { transform:scale(1) } 50% { transform:scale(1.05) } }
</style>
