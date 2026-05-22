<template>
  <div class="chats-fullpage">
    <!-- Left: Conversation list -->
    <div style="width:260px;border-right:1px solid #e5e6eb;display:flex;flex-direction:column;flex-shrink:0;background:#fafafa">
      <div style="padding:14px 16px">
        <div style="display:flex;align-items:center;gap:8px;margin-bottom:10px">
          <span style="font-size:16px;font-weight:600;flex:1">消息</span>
          <span @click="showCreateModal=true" style="color:#3370ff;font-size:22px;cursor:pointer;line-height:1;user-select:none" title="发起群聊">+</span>
        </div>
        <div style="background:#e5e6eb;border-radius:6px;padding:7px 12px;display:flex;align-items:center;gap:6px">
          <span style="color:#8f959e">&#128269;</span>
          <input v-model="convSearch" placeholder="搜索" style="border:none;background:transparent;outline:none;font-size:12px;flex:1;color:#333" />
        </div>
      </div>
      <div style="flex:1;overflow-y:auto">
        <template v-for="(c, i) in filteredConvs" :key="c.id">
          <div v-if="(i===0 && c._pinned) || (c._pinned && !filteredConvs[i-1]._pinned)" style="padding:6px 16px;font-size:11px;color:#8f959e;font-weight:500">置顶</div>
          <div v-if="i>0 && !c._pinned && filteredConvs[i-1]._pinned && filteredConvs.some(x=>x._pinned)" style="padding:6px 16px;font-size:11px;color:#8f959e;font-weight:500;margin-top:4px">最近</div>
          <div @click="openConv(c)"
            :style="{padding:'10px 16px',display:'flex',alignItems:'center',gap:'10px',cursor:'pointer',background:activeConv?.id===c.id?'#e8f3ff':'transparent'}">
            <div :style="{width:'36px',height:'36px',borderRadius:'8px',background:c.type==='group'?'#3370ff':avatarColor(c.id),color:'#fff',display:'flex',alignItems:'center',justifyContent:'center',fontSize:'14px',flexShrink:'0'}">
              {{ c.type==='group'?'群':(c.name||'?').charAt(0) }}
            </div>
            <div style="flex:1;min-width:0">
              <div style="display:flex;justify-content:space-between;align-items:baseline">
                <span style="font-size:13px;font-weight:500;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;max-width:130px;display:inline-block">{{ c.name||'私聊' }}</span>
                <span style="font-size:10px;color:#bbb;flex-shrink:0;margin-left:4px">{{ c._time }}</span>
              </div>
              <div style="display:flex;justify-content:space-between;align-items:center;margin-top:2px">
                <span style="font-size:11px;color:#8f959e;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;max-width:140px;display:inline-block">{{ c.last_msg||'' }}</span>
                <span v-if="c._unread" style="min-width:18px;height:18px;border-radius:9px;background:#f54a45;color:#fff;font-size:10px;display:flex;align-items:center;justify-content:center;flex-shrink:0;padding:0 4px">{{ c._unread>99?'99+':c._unread }}</span>
              </div>
            </div>
          </div>
        </template>
        <div v-if="filteredConvs.length===0" style="text-align:center;padding:40px;color:#bbb;font-size:13px">暂无会话</div>
      </div>
    </div>

    <!-- Right: Chat window -->
    <div style="flex:1;display:flex;flex-direction:column;min-width:0">
      <div v-if="!activeConv" style="flex:1;display:flex;align-items:center;justify-content:center;color:#bbb;font-size:14px">
        选择会话开始聊天
      </div>
      <template v-else>
        <!-- Chat header -->
        <div style="padding:12px 20px;border-bottom:1px solid #e5e6eb;display:flex;align-items:center;gap:10px;flex-shrink:0">
          <div :style="{width:'32px',height:'32px',borderRadius:'6px',background:activeConv.type==='group'?'#3370ff':avatarColor(activeConv.id),color:'#fff',display:'flex',alignItems:'center',justifyContent:'center',fontSize:'12px'}">
            {{ activeConv.type==='group'?'群':(activeConv.name||'?').charAt(0) }}
          </div>
          <span style="font-weight:600;font-size:14px">{{ activeConv.name||'私聊' }}</span>
          <span v-if="activeConv.type==='group'" style="font-size:11px;color:#8f959e;background:#f2f3f5;padding:2px 8px;border-radius:10px">{{ members.length }}人</span>
        </div>
        <!-- Messages -->
        <div style="flex:1;overflow-y:auto;padding:16px 20px" ref="msgBox">
          <div v-for="msg in msgs" :key="msg.id" :style="{display:'flex',justifyContent:msg.senderId===userId?'flex-end':'flex-start',marginBottom:'14px'}">
            <div v-if="msg.senderId!==userId" style="display:flex;gap:10px;align-items:flex-start;max-width:70%">
              <div :style="{width:'32px',height:'32px',borderRadius:'6px',background:avatarColor(msg.senderId),color:'#fff',display:'flex',alignItems:'center',justifyContent:'center',fontSize:'11px',flexShrink:'0'}">
                {{ (msg.senderName||'?').charAt(0) }}
              </div>
              <div>
                <div style="display:flex;align-items:baseline;gap:6px;margin-bottom:4px">
                  <span style="font-weight:500;font-size:12px;color:#1f2329">{{ msg.senderName }}</span>
                  <span style="font-size:10px;color:#bbb">{{ formatTime(msg.createdAt) }}</span>
                </div>
                <div style="background:#f2f3f5;padding:8px 12px;border-radius:4px 12px 12px 12px;font-size:13px;line-height:1.5;word-break:break-word">{{ msg.content }}</div>
              </div>
            </div>
            <div v-else style="max-width:70%;display:flex;flex-direction:column;align-items:flex-end">
              <div style="display:flex;align-items:baseline;gap:6px;margin-bottom:4px">
                <span style="font-size:10px;color:#bbb">{{ formatTime(msg.createdAt) }}</span>
                <span style="font-weight:500;font-size:12px;color:#1f2329">我</span>
              </div>
              <div style="background:#d6e6ff;padding:8px 12px;border-radius:12px 4px 12px 12px;font-size:13px;line-height:1.5;word-break:break-word">{{ msg.content }}</div>
            </div>
          </div>
        </div>
        <!-- Input area -->
        <div style="border-top:1px solid #e5e6eb;padding:10px 20px;flex-shrink:0">
          <div style="display:flex;align-items:center;gap:8px;margin-bottom:8px">
            <span style="color:#8f959e;cursor:pointer;font-size:15px" title="表情">&#128512;</span>
            <span style="color:#8f959e;cursor:pointer;font-size:15px" title="图片">&#128247;</span>
            <span style="color:#8f959e;cursor:pointer;font-size:15px" title="文件">&#128206;</span>
          </div>
          <div style="display:flex;gap:8px">
            <textarea v-model="input" @keydown.enter.exact.prevent="sendMsg" placeholder="输入消息... (Enter 发送)"
              style="flex:1;border:1px solid #e5e6eb;border-radius:8px;padding:8px 12px;font-size:13px;resize:none;height:36px;outline:none;font-family:inherit"
              ref="inputBox"></textarea>
            <button @click="sendMsg" style="background:#3370ff;color:#fff;border:none;padding:8px 18px;border-radius:6px;font-size:12px;cursor:pointer;font-weight:500;flex-shrink:0">发送</button>
          </div>
        </div>
      </template>
    </div>

    <!-- Create Group Modal -->
    <div v-if="showCreateModal" style="position:fixed;inset:0;background:rgba(0,0,0,.4);display:flex;align-items:center;justify-content:center;z-index:2000" @click.self="showCreateModal=false">
      <div style="background:#fff;border-radius:12px;width:520px;max-height:560px;display:flex;flex-direction:column;overflow:hidden;box-shadow:0 8px 40px rgba(0,0,0,.12)">
        <div style="padding:16px 20px;border-bottom:1px solid #e5e6eb;display:flex;align-items:center">
          <span style="font-weight:600;font-size:15px">发起群聊</span>
          <span @click="showCreateModal=false" style="margin-left:auto;color:#8f959e;cursor:pointer;font-size:18px">&times;</span>
        </div>
        <div style="padding:12px 20px">
          <div style="background:#f2f3f5;border-radius:8px;padding:8px 14px;display:flex;align-items:center;gap:6px">
            <span style="color:#8f959e">&#128269;</span>
            <input v-model="memberSearch" placeholder="搜索成员" style="border:none;background:transparent;outline:none;font-size:12px;flex:1;color:#333" />
          </div>
        </div>
        <div style="display:flex;border-bottom:1px solid #e5e6eb;padding:0 20px">
          <div v-for="tab in createTabs" :key="tab.key" @click="createTab=tab.key"
            :style="{padding:'8px 16px',fontSize:'13px',cursor:'pointer',color:createTab===tab.key?'#3370ff':'#8f959e',borderBottom:createTab===tab.key?'2px solid #3370ff':'2px solid transparent',fontWeight:createTab===tab.key?500:400}">
            {{ tab.label }}
          </div>
        </div>
        <div style="flex:1;overflow-y:auto;padding:12px 20px;display:flex;flex-wrap:wrap;align-content:flex-start;gap:12px;max-height:260px">
          <div v-for="u in filteredAvailableUsers" :key="u.id" @click="toggleMember(u)"
            style="display:flex;flex-direction:column;align-items:center;gap:6px;cursor:pointer;width:64px;user-select:none">
            <div :style="{width:'44px',height:'44px',borderRadius:'10px',display:'flex',alignItems:'center',justifyContent:'center',fontSize:'16px',color:u._sel?'#3370ff':'#999',background:u._sel?'#e8f3ff':'#f2f3f5',border:u._sel?'2px solid #3370ff':'2px solid transparent',position:'relative',fontWeight:u._sel?500:400}">
              {{ (u.realName||u.username).charAt(0) }}
              <span v-if="u._sel" style="position:absolute;bottom:-3px;right:-3px;width:18px;height:18px;border-radius:50%;background:#3370ff;color:#fff;fontSize:11px;display:flex;alignItems:center;justifyContent:center">&#10003;</span>
            </div>
            <span style="font-size:11px;text-align:center;line-height:1.3;color:#1f2329">{{ u.realName||u.username }}</span>
          </div>
          <div v-if="filteredAvailableUsers.length===0" style="width:100%;text-align:center;padding:30px;color:#bbb;font-size:13px">暂无可选成员</div>
        </div>
        <div style="padding:12px 20px;border-top:1px solid #e5e6eb;display:flex;align-items:center;gap:8px">
          <span style="font-size:12px;color:#8f959e">已选 <span style="color:#3370ff;font-weight:600">{{ selectedMembers.length }}</span> 人</span>
          <div style="flex:1;display:flex;gap:4px;overflow:hidden">
            <span v-for="u in selectedMembers" :key="u.id" style="background:#e8f3ff;color:#3370ff;padding:2px 10px;border-radius:12px;font-size:11px;display:flex;align-items:center;gap:3px;flex-shrink:0">
              {{ u.realName||u.username }}
              <span @click="toggleMember(u)" style="cursor:pointer">&times;</span>
            </span>
          </div>
          <button @click="doCreateGroup" :disabled="selectedMembers.length<2"
            :style="{background:selectedMembers.length>=2?'#3370ff':'#bbb',color:'#fff',border:'none',padding:'8px 20px',borderRadius:'6px',fontSize:'12px',cursor:selectedMembers.length>=2?'pointer':'not-allowed',fontWeight:500,flexShrink:0}">
            创建群聊
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'

const route = useRoute()
const router = useRouter()

function readUser() {
  try { return JSON.parse(localStorage.getItem('user')||'{}')||{} } catch { return {} }
}
const userId = ref(readUser().id||1)
const conversations = ref([])
const activeConv = ref(null)
const msgs = ref([])
const input = ref('')
const members = ref([])
const allUsers = ref([])
const msgBox = ref(null)
const inputBox = ref(null)
const convSearch = ref('')

const avatarColors = ['#3370ff','#34c759','#ff9500','#f54a45','#af52de','#5856d6','#ff3b30','#007aff']

let ws = null
let wsTimer = null

// Create group modal state
const showCreateModal = ref(false)
const createTab = ref('recent')
const createTabs = [{key:'recent',label:'最近联系人'},{key:'org',label:'组织架构'}]
const memberSearch = ref('')
const selectedMembers = ref([])

function authHeaders() {
  const u = readUser()
  return { 'X-User-Id': String(u.id||1), 'X-Is-Admin': String(u.isAdmin?'true':'false'), 'Content-Type': 'application/json' }
}

function avatarColor(id) { return avatarColors[Number(id) % avatarColors.length] }

const filteredConvs = computed(() => {
  let list = [...conversations.value]
  // Sort: pinned first, then by time
  list.sort((a,b) => {
    if (a._pinned && !b._pinned) return -1
    if (!a._pinned && b._pinned) return 1
    return 0
  })
  const kw = convSearch.value.trim().toLowerCase()
  if (kw) {
    list = list.filter(c => (c.name||'').toLowerCase().includes(kw) || (c.last_msg||'').toLowerCase().includes(kw))
  }
  return list
})

onMounted(async () => {
  await loadConvs()
  await loadUsers()
  connectWs()
  const targetUserId = route.query.userId
  if (targetUserId) {
    await openOrCreatePrivateChat(Number(targetUserId))
    router.replace({ path: '/chats', query: {} })
  }
})

async function loadConvs() {
  try {
    const r = await fetch('/api/chat/conversations',{headers:authHeaders()})
    const data = (await r.json()).data||[]
    data.forEach(c => {
      c._pinned = false
      const t = c.last_msg_time || c.updatedAt
      c._time = t ? formatTime(t) : ''
      c._unread = 0
    })
    conversations.value = data
  } catch(e) {
    const mock = [{id:1,name:'全员群',type:'group',last_msg:'欢迎加入'},{id:2,name:'张三',type:'private',last_msg:'好的'}]
    mock.forEach(c => { c._pinned = false; c._time = ''; c._unread = 0 })
    conversations.value = mock
  }
}

async function loadUsers() {
  try {
    const r = await fetch('/api/contacts/users',{headers:authHeaders()})
    allUsers.value = (await r.json()).data||[]
  } catch(e) {
    allUsers.value = [{id:2,realName:'张三',deptId:1},{id:3,realName:'李四',deptId:2},{id:4,realName:'王五',deptId:1},{id:5,realName:'赵六',deptId:3}]
  }
}

function connectWs() {
  if (ws) { ws.onclose = null; try { ws.close() } catch(e) {}; ws = null }
  if (wsTimer) { clearTimeout(wsTimer); wsTimer = null }
  try {
    const token = localStorage.getItem('token')||''
    const host = window.location.hostname || 'localhost'
    ws = new WebSocket(`ws://${host}:8090/ws/chat?token=${encodeURIComponent(token)}`)
    ws.onmessage = (e) => {
      try {
        const d = JSON.parse(e.data)
        if (d.type==='message' && activeConv.value && d.conversationId===activeConv.value.id) {
          msgs.value.push(d)
          scrollBottom()
        }
      } catch {}
    }
    ws.onclose = () => { wsTimer = setTimeout(connectWs, 3000) }
    ws.onerror = () => {}
  } catch { ws = null }
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

async function openOrCreatePrivateChat(targetUserId) {
  // Check ALL private conversations for this user pair
  const privateConvs = conversations.value.filter(c => c.type === 'private')
  for (const conv of privateConvs) {
    try {
      const r = await fetch(`/api/chat/members/${conv.id}`, {headers: authHeaders()})
      const mems = (await r.json()).data || []
      if (mems.some(m => m.id === targetUserId)) {
        openConv(conv)
        return
      }
    } catch(e) {}
  }
  // No existing private chat found — create one (backend dedup handles races)
  try {
    const targetUser = allUsers.value.find(u => u.id === targetUserId)
    const targetName = targetUser ? (targetUser.realName || targetUser.username) : ('用户' + targetUserId)
    const r = await fetch('/api/chat/conversations', {
      method: 'POST',
      headers: authHeaders(),
      body: JSON.stringify({ name: targetName, type: 'private', memberIds: [targetUserId] })
    })
    const newId = (await r.json()).data
    await loadConvs()
    const created = conversations.value.find(c => c.id === newId)
    if (created) openConv(created)
  } catch(e) {}
}

async function sendMsg() {
  const content = input.value.trim()
  if (!content || !activeConv.value) return
  if (!ws || ws.readyState !== WebSocket.OPEN) {
    ElMessage.warning('连接未建立，请刷新页面后重试')
    return
  }
  ws.send(JSON.stringify({conversationId:activeConv.value.id,content}))
  const temp = { id: 'msg-'+Date.now()+'-'+Math.random().toString(36).slice(2,8), senderId: userId.value, senderName: '我', content, createdAt: new Date().toISOString() }
  msgs.value.push(temp)
  input.value = ''
  await nextTick()
  scrollBottom()
  inputBox.value?.focus()
}

async function doCreateGroup() {
  if (selectedMembers.value.length<2) return
  const memberIds = selectedMembers.value.map(u=>u.id)
  const names = selectedMembers.value.map(u=>u.realName||u.username).join('、')
  try {
    const r = await fetch('/api/chat/conversations',{method:'POST',headers:authHeaders(),body:JSON.stringify({name:names,type:'group',memberIds})})
    const newId = (await r.json()).data
    showCreateModal.value = false
    selectedMembers.value = []
    await loadConvs()
    const created = conversations.value.find(c=>c.id===newId)
    if (created) openConv(created)
  } catch(e) { ElMessage.error('创建失败') }
}

function toggleMember(u) {
  u._sel = !u._sel
  if (u._sel) selectedMembers.value.push(u)
  else selectedMembers.value = selectedMembers.value.filter(m=>m.id!==u.id)
}

const filteredAvailableUsers = computed(() => {
  let list = allUsers.value.filter(u=>u.id!==userId.value).map(u => {
    const sel = selectedMembers.value.find(m=>m.id===u.id)
    return {...u, _sel: !!sel}
  })
  const kw = memberSearch.value.trim().toLowerCase()
  if (kw) list = list.filter(u => (u.realName||u.username).toLowerCase().includes(kw))
  return list
})

function formatTime(t) {
  if (!t) return ''
  const d = new Date(t)
  const now = new Date()
  const pad = n => String(n).padStart(2,'0')
  if (d.toDateString()===now.toDateString()) return pad(d.getHours())+':'+pad(d.getMinutes())
  const yesterday = new Date(now); yesterday.setDate(now.getDate()-1)
  if (d.toDateString()===yesterday.toDateString()) return '昨天'
  return (d.getMonth()+1)+'/'+d.getDate()
}

function scrollBottom() {
  nextTick(() => {
    if (msgBox.value) msgBox.value.scrollTop = msgBox.value.scrollHeight
  })
}

onBeforeUnmount(() => {
  if (ws) { ws.onclose = null; ws.close(); ws = null }
  if (wsTimer) { clearTimeout(wsTimer); wsTimer = null }
})
</script>

<style scoped>
/**
 * 在 MainLayout 主内容区内占满剩余高度与宽度。
 */
.chats-fullpage {
  display: flex;
  flex: 1;
  min-height: 0;
  width: 100%;
  height: 100%;
  background: #fff;
  overflow: hidden;
}
</style>
