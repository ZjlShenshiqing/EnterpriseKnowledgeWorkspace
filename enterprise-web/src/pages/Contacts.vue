<template>
  <div style="background:#fff;border-radius:12px;padding:20px">
    <div style="font-size:18px;font-weight:600;margin-bottom:16px">通讯录</div>
    <div style="display:flex;gap:20px">
      <div style="width:200px;flex-shrink:0">
        <div @click="filterDept=null" :style="{padding:'8px 12px',borderRadius:'6px',cursor:'pointer',fontSize:'14px',background:!filterDept?'var(--brand-100)':'transparent',color:!filterDept?'var(--brand-500)':''}">全部成员 ({{ users.length }})</div>
        <div v-for="d in depts" :key="d.id" @click="filterDept=d.id"
          :style="{padding:'8px 12px',borderRadius:'6px',cursor:'pointer',fontSize:'14px',background:filterDept===d.id?'var(--brand-100)':'transparent',color:filterDept===d.id?'var(--brand-500)':''}">
          {{ d.name || getDeptName(d.id) }} ({{ users.filter(u=>u.deptId===d.id).length }})
        </div>
      </div>
      <div style="flex:1">
        <div v-for="u in filteredUsers" :key="u.id"
          style="display:flex;align-items:center;gap:12px;padding:10px;border-bottom:1px solid var(--border-light);cursor:pointer"
          @click="startChat(u)" @mouseenter="e=>e.target.style.background='var(--bg-hover)'" @mouseleave="e=>e.target.style.background='transparent'">
          <el-avatar :size="40" :style="{background:u.isAdmin?'var(--brand-500)':'#67c23a'}">{{ (u.realName||u.username).charAt(0) }}</el-avatar>
          <div style="flex:1">
            <div style="font-size:14px;font-weight:500">{{ u.realName || u.username }}</div>
            <div style="font-size:12px;color:var(--text-tertiary)">{{ getDeptName(u.deptId) }} {{ u.isAdmin?'· 管理员':'' }}</div>
          </div>
          <el-button size="small" circle><el-icon><ChatDotRound /></el-icon></el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'

const users = ref([])
const depts = ref([{id:1,name:'技术部'},{id:2,name:'产品部'},{id:3,name:'设计部'}])
const filterDept = ref(null)
const filteredUsers = computed(() => filterDept.value ? users.value.filter(u=>u.deptId===filterDept.value) : users.value)

function getDeptName(id) { return depts.value.find(d=>d.id===id)?.name||'' }

onMounted(async () => {
  const headers = {'X-User-Id':JSON.parse(localStorage.getItem('user')||'{}').id||'1','X-Is-Admin':'true'}
  try {
    const r = await fetch('/api/contacts/users',{headers})
    users.value = (await r.json()).data||[]
  } catch(e) { users.value = [{id:1,username:'admin',realName:'系统管理员',deptId:1,isAdmin:1},{id:2,realName:'张三',deptId:1},{id:3,realName:'李四',deptId:2},{id:4,realName:'王五',deptId:1},{id:5,realName:'赵六',deptId:3}] }
})

function startChat(user) {}
</script>
