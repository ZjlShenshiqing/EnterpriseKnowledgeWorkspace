<template>
  <div style="height:calc(100vh - 140px);display:flex;background:#fff;border-radius:12px;overflow:hidden">
    <!-- Conv List -->
    <div style="width:280px;border-right:1px solid var(--border-default);display:flex;flex-direction:column;flex-shrink:0">
      <div style="padding:16px;border-bottom:1px solid var(--border-light)">
        <div style="display:flex;align-items:center;gap:8px;margin-bottom:12px">
          <span style="font-size:16px;font-weight:600;flex:1">消息</span>
          <el-button size="small" circle @click="showCreate=true"><el-icon><Plus /></el-icon></el-button>
        </div>
      </div>
      <div style="flex:1;overflow-y:auto">
        <div v-for="c in conversations" :key="c.id" @click="openConv(c)"
          :style="{padding:'14px 16px',cursor:'pointer',borderBottom:'1px solid var(--border-light)',background:activeConv?.id===c.id?'var(--brand-100)':'transparent'}">
          <div style="display:flex;align-items:center;gap:10px">
            <el-avatar :size="40" :style="{background:c.type==='group'?'var(--brand-500)':'#67c23a'}">
              {{ c.type==='group'?'群':(c.name||'?').charAt(0) }}
            </el-avatar>
            <div style="flex:1;min-width:0">
              <div style="font-size:14px;font-weight:500;overflow:hidden;text-overflow:ellipsis;white-space:nowrap">{{ c.name || '私聊' }}</div>
              <div style="font-size:12px;color:var(--text-tertiary);margin-top:3px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap">{{ c.last_msg || '暂无消息' }}</div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Chat Window -->
    <div style="flex:1;display:flex;flex-direction:column;min-width:0">
      <div v-if="!activeConv" style="flex:1;display:flex;align-items:center;justify-content:center;color:var(--text-tertiary);font-size:15px">
        选择会话开始聊天
      </div>
      <template v-else>
        <div style="padding:14px 20px;border-bottom:1px solid var(--border-light);font-weight:600;font-size:15px;display:flex;align-items:center;gap:8px;flex-shrink:0">
          <el-avatar :size="32" :style="{background:activeConv.type==='group'?'var(--brand-500)':'#67c23a'}">{{ activeConv.type==='group'?'群':(activeConv.name||'?').charAt(0) }}</el-avatar>
          {{ activeConv.name || '私聊' }}
          <span v-if="activeConv.type==='group'" style="font-size:12px;color:var(--text-tertiary);font-weight:400;margin-left:4px">{{ members.length }}人</span>
        </div>
        <div style="flex:1;overflow-y:auto;padding:16px 20px" ref="msgBox">
          <div v-for="msg in msgs" :key="msg.id" :style="{display:'flex',justifyContent:msg.senderId===userId?'flex-end':'flex-start',marginBottom:'12px'}">
            <div v-if="msg.senderId!==userId" style="display:flex;gap:8px;align-items:flex-start">
              <el-avatar :size="32" style="background:#67c23a;flex-shrink:0;font-size:12px">{{ (msg.senderName||'?').charAt(0) }}</el-avatar>
              <div>
                <div style="font-size:11px;color:var(--text-tertiary);margin-bottom:3px">{{ msg.senderName }}</div>
                <div style="background:#f2f3f5;padding:8px 12px;border-radius:12px;font-size:14px;max-width:400px;word-break:break-word">{{ msg.content }}</div>
              </div>
            </div>
            <div v-else style="background:var(--brand-100);padding:8px 12px;border-radius:12px;font-size:14px;max-width:400px;word-break:break-word">
              {{ msg.content }}
            </div>
          </div>
        </div>
        <div style="padding:12px 20px;border-top:1px solid var(--border-light);display:flex;gap:8px;flex-shrink:0">
          <el-input v-model="input" placeholder="输入消息..." @keyup.enter="sendMsg" size="large" />
          <el-button type="primary" size="large" @click="sendMsg">发送</el-button>
        </div>
      </template>
    </div>

    <!-- Create Group Dialog -->
    <el-dialog v-model="showCreate" title="创建群聊" width="420px">
      <el-form :model="newConv" label-width="60px">
        <el-form-item label="群名称"><el-input v-model="newConv.name" placeholder="输入群名称" /></el-form-item>
        <el-form-item label="成员">
          <el-select v-model="newConv.memberIds" multiple placeholder="选择成员" style="width:100%" filterable>
            <el-option v-for="u in allUsers" :key="u.id" :label="u.realName || u.username" :value="u.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer><el-button @click="showCreate=false">取消</el-button><el-button type="primary" @click="doCreate">创建</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick } from 'vue'
import { ElMessage } from 'element-plus'

const userId = ref(JSON.parse(localStorage.getItem('user')||'{}').id||1)
const conversations = ref([])
const activeConv = ref(null)
const msgs = ref([])
const input = ref('')
const showCreate = ref(false)
const members = ref([])
const allUsers = ref([])
const msgBox = ref(null)
const newConv = ref({ name: '', memberIds: [] })
let ws = null

onMounted(async () => {
  await loadConvs()
  loadUsers()
  connectWs()
})

async function loadConvs() {
  try { const r = await fetch('/api/chat/conversations',{headers:authHeaders()}); conversations.value = (await r.json()).data||[] }
  catch(e) { conversations.value = [{id:1,name:'全员群',type:'group',last_msg:'欢迎加入'},{id:2,name:'张三',type:'private',last_msg:'好的'}] }
}

async function loadUsers() {
  try { const r = await fetch('/api/contacts/users',{headers:authHeaders()}); allUsers.value = (await r.json()).data||[] }
  catch(e) { allUsers.value = [{id:2,realName:'张三'},{id:3,realName:'李四'},{id:4,realName:'王五'}] }
}

function authHeaders() {
  const u = JSON.parse(localStorage.getItem('user')||'{}')
  return {'X-User-Id':String(u.id||1),'X-Is-Admin':String(u.isAdmin?'true':'false'),'Content-Type':'application/json'}
}

function connectWs() {
  const token = localStorage.getItem('token') || ''
  ws = new WebSocket(`ws://localhost:8090/ws/chat?token=${token}`)
  ws.onmessage = (e) => {
    const d = JSON.parse(e.data)
    if (d.type === 'message' && activeConv.value && d.conversationId === activeConv.value.id) {
      msgs.value.push(d); scrollBottom()
    }
  }
}

async function openConv(c) {
  activeConv.value = c
  try {
    const [mr, mm] = await Promise.all([
      fetch(`/api/chat/messages/${c.id}`,{headers:authHeaders()}),
      fetch(`/api/chat/members/${c.id}`,{headers:authHeaders()})
    ])
    msgs.value = (await mr.json()).data||[]
    members.value = (await mm.json()).data||[]
    await nextTick(); scrollBottom()
  } catch(e) {}
}

async function sendMsg() {
  if (!input.value.trim() || !ws) return
  ws.send(JSON.stringify({ conversationId: activeConv.value.id, content: input.value }))
  msgs.value.push({ senderId: userId.value, senderName: '我', content: input.value, id: Date.now() })
  input.value = ''; await nextTick(); scrollBottom()
}

async function doCreate() {
  if (!newConv.value.name || !newConv.value.memberIds.length) return
  try {
    await fetch('/api/chat/conversations',{method:'POST',headers:authHeaders(),body:JSON.stringify({name:newConv.value.name,type:'group',memberIds:newConv.value.memberIds})})
    showCreate.value = false; newConv.value = { name: '', memberIds: [] }
    await loadConvs(); ElMessage.success('群聊已创建')
  } catch(e) {}
}

function scrollBottom() { nextTick(() => { msgBox.value?.scrollTo({top:msgBox.value.scrollHeight,behavior:'smooth'}) }) }
</script>
