<template>
  <div style="background:#fff;border-radius:12px;overflow:hidden;display:flex;flex-direction:column;min-height:min(560px,calc(100vh - 120px));box-shadow:0 1px 3px rgba(0,0,0,.06)">
    <!-- Header -->
    <div style="padding:16px 20px;border-bottom:1px solid #e5e6eb;display:flex;align-items:center;gap:12px">
      <span style="font-size:16px;font-weight:600">通讯录</span>
      <div style="flex:1"></div>
      <div style="background:#f2f3f5;border-radius:8px;padding:7px 14px;display:flex;align-items:center;gap:6px;width:260px">
        <span style="color:#8f959e;font-size:14px">&#128269;</span>
        <input v-model="search" placeholder="搜索联系人" style="border:none;background:transparent;outline:none;font-size:12px;flex:1;color:#333" />
      </div>
    </div>
    <div style="display:flex;flex:1;overflow:hidden">
      <!-- Left: org tree -->
      <div style="width:230px;border-right:1px solid #e5e6eb;overflow-y:auto;padding:8px 0;flex-shrink:0">
        <div @click="selectedDeptId = null"
          :style="{padding:'9px 16px',cursor:'pointer',fontSize:'13px',display:'flex',alignItems:'center',gap:'8px',background:selectedDeptId===null?'#e8f3ff':'transparent',color:selectedDeptId===null?'#3370ff':'#1f2329',fontWeight:selectedDeptId===null?500:400}">
          <span style="font-size:10px;color:#8f959e">&#11208;</span>
          全部成员
          <span style="font-size:11px;color:#bbb;margin-left:auto">{{ users.length }}</span>
        </div>
        <div v-for="d in depts" :key="d.id"
          @click="selectedDeptId = d.id"
          :style="{padding:'9px 16px',cursor:'pointer',fontSize:'13px',display:'flex',alignItems:'center',gap:'8px',background:selectedDeptId===d.id?'#e8f3ff':'transparent',color:selectedDeptId===d.id?'#3370ff':'#1f2329',fontWeight:selectedDeptId===d.id?500:400}">
          <span style="font-size:10px;color:#8f959e">&#11208;</span>
          {{ d.name }}
          <span style="font-size:11px;color:#bbb;margin-left:auto">{{ deptUserCount(d.id) }}</span>
        </div>
      </div>
      <!-- Right: user list -->
      <div style="flex:1;overflow-y:auto">
        <div v-if="filteredUsers.length===0" style="text-align:center;padding:60px;color:#bbb;font-size:14px">暂无联系人</div>
        <div v-for="u in filteredUsers" :key="u.id"
          style="display:flex;align-items:center;gap:12px;padding:12px 20px;border-bottom:1px solid #f5f6f7;transition:background .15s"
          @mouseenter="(e) => e.currentTarget.style.background='#fafafa'"
          @mouseleave="(e) => e.currentTarget.style.background='transparent'">
          <div :style="{width:'40px',height:'40px',borderRadius:'8px',background:avatarColor(u.id),color:'#fff',display:'flex',alignItems:'center',justifyContent:'center',fontSize:'15px',fontWeight:'500',flexShrink:'0'}">
            {{ (u.realName||u.username).charAt(0) }}
          </div>
          <div style="flex:1;min-width:0">
            <div style="font-size:14px;font-weight:500;color:#1f2329">{{ u.realName || u.username }}</div>
            <div style="font-size:12px;color:#8f959e;margin-top:2px">{{ getDeptName(u.deptId) }}</div>
          </div>
          <button
            @click="goChat(u)"
            style="padding:5px 14px;border:1px solid #3370ff;border-radius:4px;background:#fff;color:#3370ff;font-size:12px;cursor:pointer;font-weight:500;transition:all .15s"
            @mouseenter="(e) => { e.currentTarget.style.background='#3370ff'; e.currentTarget.style.color='#fff' }"
            @mouseleave="(e) => { e.currentTarget.style.background='#fff'; e.currentTarget.style.color='#3370ff' }">
            发消息
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()
const users = ref([])
const depts = ref([])
const selectedDeptId = ref(null)
const search = ref('')
const avatarColors = ['#3370ff','#34c759','#ff9500','#f54a45','#af52de','#5856d6','#ff3b30','#007aff']

function readUser() {
  try { return JSON.parse(localStorage.getItem('user')||'{}')||{} } catch { return {} }
}
function authHeaders() {
  const u = readUser()
  return { 'X-User-Id': String(u.id||1), 'X-Is-Admin': String(u.isAdmin?'true':'false'), 'Content-Type': 'application/json' }
}
function avatarColor(id) { return avatarColors[Number(id) % avatarColors.length] }
function getDeptName(id) { return depts.value.find(d=>d.id===id)?.name||'' }
function deptUserCount(deptId) { return users.value.filter(u=>u.deptId===deptId).length }

const filteredUsers = computed(() => {
  let list = selectedDeptId.value ? users.value.filter(u=>u.deptId===selectedDeptId.value) : users.value
  if (search.value.trim()) {
    const kw = search.value.trim().toLowerCase()
    list = list.filter(u => (u.realName||u.username).toLowerCase().includes(kw) || getDeptName(u.deptId).toLowerCase().includes(kw))
  }
  return list
})

onMounted(async () => {
  const h = authHeaders()
  try {
    const [ur, dr] = await Promise.all([
      fetch('/api/contacts/users',{headers:h}),
      fetch('/api/contacts/departments',{headers:h})
    ])
    users.value = (await ur.json()).data||[]
    depts.value = (await dr.json()).data||[]
  } catch(e) {
    depts.value = [{id:1,name:'技术部'},{id:2,name:'产品部'},{id:3,name:'设计部'}]
    users.value = [{id:1,username:'admin',realName:'系统管理员',deptId:1},{id:2,realName:'张三',deptId:1},{id:3,realName:'李四',deptId:2},{id:4,realName:'王五',deptId:1},{id:5,realName:'赵六',deptId:3}]
  }
})

function goChat(user) {
  router.push({ path: '/chats', query: { userId: String(user.id) } })
}
</script>
