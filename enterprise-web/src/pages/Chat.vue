<template>
  <div style="height:calc(100vh - 140px);display:flex;flex-direction:column">
    <div style="flex:1;overflow-y:auto;margin-bottom:12px" ref="box">
      <div v-if="!messages.length" style="text-align:center;margin-top:120px">
        <el-icon :size="64" color="#dcdfe6"><ChatDotRound /></el-icon>
        <div style="font-size:18px;color:#909399;margin-top:16px">智能助手</div>
        <div style="font-size:14px;color:#c0c4cc;margin-top:8px">可以向我提问知识库文档相关问题</div>
        <div style="margin-top:20px;display:flex;gap:8px;justify-content:center;flex-wrap:wrap">
          <el-tag v-for="q in quickQuestions" :key="q" @click="sendQuick(q)" style="cursor:pointer">{{ q }}</el-tag>
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
            <div style="background:#fff;padding:12px 16px;border-radius:4px 12px 12px 12px;white-space:pre-wrap;font-size:14px;line-height:1.7;border:1px solid #e4e7ed" v-html="renderMsg(msg)"></div>
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
  if (msg.loading) return '<span style="color:#909399">思考中...</span>'
  return (msg.content||'').replace(/\n/g,'<br>')
}

async function send() {
  if (!input.value.trim() || sending.value) return
  const text = input.value.trim(); input.value = ''
  messages.value.push({ role: 'user', content: text })
  messages.value.push({ role: 'assistant', content: '', loading: true })
  sending.value = true
  try {
    const resp = await agentChat(null, text)
    const reader = resp.body.getReader()
    const decoder = new TextDecoder()
    const msg = messages.value[messages.value.length-1]
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      for (const line of decoder.decode(value).split('\n')) {
        if (line.startsWith('data:')) {
          try { const d = JSON.parse(line.slice(5).trim()); if (d.delta) msg.content += d.delta } catch(e) {}
        }
      }
    }
    msg.loading = false
  } catch(e) {
    const msg = messages.value[messages.value.length-1]
    msg.content = 'Agent服务暂未启动。以下为示例回复：\n\n为您找到以下文档：\n\n1. **微服务架构设计指南** (application/msword)\n   微服务架构的核心概念和最佳实践\n\n2. **2026年度技术规划** (application/pdf)\n   包含微服务架构相关的技术规划\n\n3. **系统架构评审纪要** (application/pdf)\n   相关架构评审内容\n\n需要我详细介绍哪篇文档？'
    msg.loading = false
  }
  sending.value = false
  await nextTick(); box.value.scrollTop = box.value.scrollHeight
}
</script>
