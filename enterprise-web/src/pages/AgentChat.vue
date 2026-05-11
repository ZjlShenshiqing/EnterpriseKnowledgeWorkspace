<template>
  <div style="height:calc(100vh - 120px);display:flex;flex-direction:column">
    <h2 style="margin-bottom:12px">智能对话</h2>
    <div style="flex:1;overflow-y:auto;background:#fff;border-radius:8px;padding:16px;margin-bottom:12px" ref="chatBox">
      <div v-for="(msg,i) in messages" :key="i" style="margin-bottom:12px">
        <div v-if="msg.role==='user'" style="text-align:right">
          <el-tag type="primary" style="max-width:70%;text-align:left;white-space:pre-wrap">{{ msg.content }}</el-tag>
        </div>
        <div v-else style="text-align:left">
          <div style="background:#f5f7fa;display:inline-block;max-width:80%;padding:10px 14px;border-radius:8px;white-space:pre-wrap;line-height:1.6">
            <span v-if="msg.loading"><el-icon class="is-loading"><Loading /></el-icon> 思考中...</span>
            {{ msg.content }}
            <div v-if="msg.toolCalls" style="margin-top:6px;font-size:12px;color:#909399">
              调用工具: {{ msg.toolCalls }}
            </div>
          </div>
        </div>
      </div>
    </div>
    <div style="display:flex;gap:8px">
      <el-input v-model="input" placeholder="输入问题，如：帮我找关于微服务架构的文档" @keyup.enter="send" />
      <el-button type="primary" @click="send" :loading="sending">发送</el-button>
      <el-button @click="messages=[]">清空</el-button>
    </div>
  </div>
</template>

<script setup>
import { ref, nextTick } from 'vue'
import { agentChat } from '../api'

const input = ref('')
const messages = ref([])
const sending = ref(false)
const chatBox = ref(null)

async function send() {
  if (!input.value.trim() || sending.value) return
  const text = input.value.trim()
  input.value = ''
  messages.value.push({ role: 'user', content: text })
  messages.value.push({ role: 'assistant', content: '', loading: true })
  sending.value = true
  try {
    const resp = await agentChat(null, text)
    const reader = resp.body.getReader()
    const decoder = new TextDecoder()
    const assistantMsg = messages.value[messages.value.length - 1]
    let toolCalls = []

    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      const chunk = decoder.decode(value, { stream: true })
      for (const line of chunk.split('\n')) {
        if (line.startsWith('event:')) continue
        if (line.startsWith('data:')) {
          try {
            const data = JSON.parse(line.slice(5).trim())
            if (data.tool) toolCalls.push(data.tool)
            if (data.delta) assistantMsg.content += data.delta
            if (toolCalls.length) assistantMsg.toolCalls = toolCalls.join(', ')
          } catch(e) {}
        }
      }
    }
    assistantMsg.loading = false
    await nextTick(); chatBox.value.scrollTop = chatBox.value.scrollHeight
  } catch (e) {
    const assistantMsg = messages.value[messages.value.length - 1]
    assistantMsg.content = 'Agent 服务暂未启动，可以启动 knowledge-ai-service 后再试。\n\n示例对话（mock）：\n我找到 3 篇相关文档：\n1. 《微服务架构设计指南》- application/msword\n2. 《2026年度技术规划》- application/pdf\n3. 《系统架构评审纪要》- application/pdf\n\n需要我详细介绍哪篇文档？'
    assistantMsg.loading = false
  }
  sending.value = false
  await nextTick(); chatBox.value.scrollTop = chatBox.value.scrollHeight
}
</script>
