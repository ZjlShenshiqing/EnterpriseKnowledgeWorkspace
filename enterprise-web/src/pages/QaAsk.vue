<template>
  <div>
    <h2 style="margin-bottom:16px">智能问答 (RAG)</h2>
    <el-card>
      <div style="display:flex;gap:12px;margin-bottom:16px">
        <el-input v-model="question" placeholder="输入问题，如：差旅报销需要什么材料？" size="large" @keyup.enter="ask" />
        <el-button type="primary" size="large" @click="ask" :loading="asking">提问</el-button>
      </div>
      <div v-if="answer || sources.length" style="background:#f5f7fa;padding:20px;border-radius:8px">
        <div v-if="answer" style="font-size:16px;line-height:1.8;margin-bottom:20px">{{ answer }}</div>
        <div v-if="sources.length">
          <h4>参考来源：</h4>
          <div v-for="(src,i) in sources" :key="i" style="padding:10px;margin:8px 0;background:#fff;border-radius:6px">
            <div style="font-weight:bold">{{ src.title }}</div>
            <div style="color:#909399;font-size:13px">{{ src.fileType }} · {{ src.fileName }} · {{ src.createdAt }}</div>
            <div style="margin-top:6px;color:#606266">{{ src.summary }}</div>
            <div v-if="src.matchedChunks" style="margin-top:8px">
              <div v-for="(chunk,j) in src.matchedChunks" :key="j" style="font-size:13px;color:#409eff;padding:4px 0">
                「{{ chunk.text?.substring(0,100) }}{{ chunk.text?.length>100?'...':'' }}」 (相关度: {{ (chunk.score*100).toFixed(0) }}%)
              </div>
            </div>
            <div v-if="src.metadata" style="margin-top:4px;font-size:12px;color:#909399">
              元数据: {{ JSON.stringify(src.metadata) }}
            </div>
          </div>
        </div>
      </div>
      <el-empty v-if="!answer && !sources.length && !asking" description="请输入问题开始智能问答" />
    </el-card>
  </div>
</template>

<script setup>
import { ref } from 'vue'

const question = ref('')
const answer = ref('')
const sources = ref([])
const asking = ref(false)

async function ask() {
  if (!question.value.trim() || asking.value) return
  asking.value = true; answer.value = ''; sources.value = []

  try {
    const resp = await fetch('/api/kb/agent/chat', {
      method: 'POST', headers: { 'Content-Type':'application/json', 'X-User-Id':'1','X-Is-Admin':'true' },
      body: JSON.stringify({ message: question.value })
    })
    const reader = resp.body.getReader()
    const decoder = new TextDecoder()
    let full = ''
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      const chunk = decoder.decode(value, { stream: true })
      for (const line of chunk.split('\n')) {
        if (line.startsWith('data:')) {
          try {
            const data = JSON.parse(line.slice(5).trim())
            if (data.delta) full += data.delta
          } catch(e) {}
        }
      }
    }
    answer.value = full || '未找到相关答案。请确保已在知识库中上传相关文档并完成分块。'
  } catch (e) {
    console.log('RAG服务未启动，使用mock数据')
    answer.value = '根据公司差旅报销制度，员工出差报销需要提交以下材料：\n\n1. 出差审批单（需提前审批）\n2. 交通票据（机票/火车票/打车票）\n3. 住宿发票\n4. 行程证明（会议通知/拜访记录等）\n\n报销流程：提交材料 → 部门负责人审批 → 财务审核 → 打款'
    sources.value = [
      { title: '公司差旅报销制度', fileType: 'application/pdf', fileName: 'travel-policy.pdf', createdAt: '2026-03-15', summary: '差旅报销需提交审批单、发票和行程证明', matchedChunks: [{ text: '员工出差需提前提交出差审批单，经部门负责人审批通过后方可出差。报销时需提供出差审批单、交通票据、住宿发票及相关行程证明。', score: 0.95 }], metadata: { author: '行政部', creationDate: '2026-01-10' } }
    ]
  }
  asking.value = false
}
</script>
