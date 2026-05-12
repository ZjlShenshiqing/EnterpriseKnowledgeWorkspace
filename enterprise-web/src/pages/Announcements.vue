<template>
  <div style="background:#fff;border-radius:12px;padding:20px">
    <div style="display:flex;justify-content:space-between;margin-bottom:16px">
      <span style="font-size:18px;font-weight:600">公告通知</span>
      <el-button type="primary" @click="showPublish=true" v-if="isAdmin">发布公告</el-button>
    </div>
    <div v-for="a in announcements" :key="a.id" style="padding:16px 0;border-bottom:1px solid var(--border-light)">
      <div style="display:flex;align-items:center;gap:8px;margin-bottom:6px">
        <el-tag v-if="a.is_pinned" type="danger" size="small" effect="dark">置顶</el-tag>
        <span style="font-size:16px;font-weight:600">{{ a.title }}</span>
      </div>
      <div style="font-size:14px;color:var(--text-secondary);line-height:1.8;margin-bottom:8px">{{ a.content }}</div>
      <div style="font-size:12px;color:var(--text-tertiary)">{{ a.publisher_name }} · {{ a.created_at }}</div>
    </div>
    <el-empty v-if="!announcements.length" description="暂无公告" />

    <el-dialog v-model="showPublish" title="发布公告" width="500px">
      <el-form :model="form" label-width="60px">
        <el-form-item label="标题"><el-input v-model="form.title" placeholder="公告标题" /></el-form-item>
        <el-form-item label="内容"><el-input v-model="form.content" type="textarea" :rows="4" placeholder="公告内容" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="showPublish=false">取消</el-button><el-button type="primary" @click="publish">发布</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'

const isAdmin = JSON.parse(localStorage.getItem('user')||'{}').isAdmin
const announcements = ref([])
const showPublish = ref(false)
const form = ref({ title: '', content: '' })

function headers() {
  const u = JSON.parse(localStorage.getItem('user')||'{}')
  return {'X-User-Id':String(u.id||1),'X-Is-Admin':String(u.isAdmin?'true':'false'),'Content-Type':'application/json'}
}

onMounted(async () => {
  try { const r = await fetch('/api/announcements',{headers:headers()}); announcements.value = (await r.json()).data||[] }
  catch(e) { announcements.value = [{id:1,title:'系统升级通知',content:'平台将于本周六进行维护升级，届时服务可能短暂中断。',publisher_name:'管理员',is_pinned:1,created_at:'2026-05-12'},{id:2,title:'Q2技术评审通知',content:'请各部门在5月20日前提交Q2技术评审材料。',publisher_name:'张三',is_pinned:0,created_at:'2026-05-10'}] }
})

async function publish() {
  if (!form.value.title || !form.value.content) return
  try {
    await fetch('/api/announcements',{method:'POST',headers:headers(),body:JSON.stringify(form.value)})
    showPublish.value = false; form.value = { title:'',content:'' }
    ElMessage.success('发布成功')
    const r = await fetch('/api/announcements',{headers:headers()}); announcements.value = (await r.json()).data||[]
  } catch(e) { ElMessage.error('发布失败') }
}
</script>
