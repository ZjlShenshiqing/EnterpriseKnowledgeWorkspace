<template>
  <div style="min-height:min(560px,calc(100vh - 120px));display:flex;gap:0;background:#fff;border-radius:10px;overflow:hidden;box-shadow:0 1px 3px rgba(0,0,0,.06)">
    <!-- Left: Doc List -->
    <div style="width:260px;background:#fafafa;border-right:1px solid #eee;display:flex;flex-direction:column;flex-shrink:0">
      <div style="padding:16px">
        <div style="display:flex;gap:8px;margin-bottom:12px">
          <el-input v-model="searchText" placeholder="搜索文档" size="small" clearable />
          <el-button type="primary" size="small" circle @click="createDoc"><el-icon><Plus /></el-icon></el-button>
        </div>
      </div>
      <div style="flex:1;overflow-y:auto;padding:0 8px">
        <div v-for="doc in filteredDocs" :key="doc.id" @click="openDoc(doc)"
          :style="{ padding:'10px 12px',cursor:'pointer',borderRadius:'8px',marginBottom:'2px',background: activeDoc?.id===doc.id ? '#e8f4ff' : 'transparent',transition:'background .15s' }"
          @mouseenter="e=>e.target.style.background= activeDoc?.id===doc.id ? '#e8f4ff' : '#f5f5f5'"
          @mouseleave="e=>e.target.style.background= activeDoc?.id===doc.id ? '#e8f4ff' : 'transparent'">
          <div style="display:flex;align-items:center;gap:8px">
            <el-icon :size="18" :color="doc.type==='doc'?'#409eff':doc.type==='sheet'?'#67c23a':'#e6a23c'">
              <component :is="doc.type==='doc'?'Document':doc.type==='sheet'?'Grid':'Picture'" />
            </el-icon>
            <div style="font-size:13px;flex:1;overflow:hidden;text-overflow:ellipsis;white-space:nowrap">{{ doc.title }}</div>
          </div>
          <div style="font-size:11px;color:#c0c4cc;margin-top:4px;margin-left:26px">{{ doc.updatedAt || doc.created_at }} · {{ doc.updatedByName || doc.updated_by_name || '未知' }}</div>
        </div>
      </div>
      <div style="border-top:1px solid #eee;padding:12px;display:flex;gap:6px;align-items:center">
        <el-avatar v-for="u in onlineUsers" :key="u.name" :size="26" :style="{background:u.color}">{{ u.name[0] }}</el-avatar>
        <span style="font-size:11px;color:#909399;margin-left:4px">3 人在线</span>
      </div>
    </div>

    <!-- Center: Editor -->
    <div style="flex:1;display:flex;flex-direction:column;min-width:0">
      <div v-if="!activeDoc" style="flex:1;display:flex;align-items:center;justify-content:center;color:#c0c4cc">
        <div style="text-align:center">
          <div style="width:80px;height:80px;border-radius:20px;background:linear-gradient(135deg,#409eff,#67c23a);margin:0 auto;display:flex;align-items:center;justify-content:center" class="pulse-icon">
            <el-icon :size="40" color="#fff"><Document /></el-icon>
          </div>
          <div style="font-size:16px;color:#909399;margin-top:20px">选择或创建一个文档开始协作</div>
          <el-button type="primary" style="margin-top:16px" @click="createDoc">新建文档</el-button>
        </div>
      </div>
      <template v-else>
        <!-- Toolbar -->
        <div style="padding:6px 16px;border-bottom:1px solid #eee;display:flex;align-items:center;gap:4px;flex-wrap:wrap;background:#fafafa">
          <el-input v-model="activeDoc.title" style="width:180px" size="small" placeholder="未命名文档" @input="autoSave" />
          <el-divider direction="vertical" />
          <el-button-group size="small">
            <el-button @click="execCmd('bold')"><b>B</b></el-button>
            <el-button @click="execCmd('italic')"><i>I</i></el-button>
            <el-button @click="execCmd('underline')"><u>U</u></el-button>
            <el-button @click="execCmd('strikeThrough')"><s>S</s></el-button>
          </el-button-group>
          <el-divider direction="vertical" />
          <el-button-group size="small">
            <el-button @click="execCmd('insertUnorderedList')"><el-icon><List /></el-icon></el-button>
            <el-button @click="execCmd('insertOrderedList')"><el-icon><Tickets /></el-icon></el-button>
          </el-button-group>
          <el-divider direction="vertical" />
          <el-select v-model="heading" size="small" style="width:100px" @change="setHeading" placeholder="正文">
            <el-option label="正文" value="div" />
            <el-option label="标题1" value="h2" />
            <el-option label="标题2" value="h3" />
          </el-select>
          <el-divider direction="vertical" />
          <el-button size="small" @click="insertTable">插入表格</el-button>
          <span style="font-size:11px;color:#67c23a;margin-left:auto">已自动保存</span>
        </div>
        <!-- Editor Area -->
        <div ref="editor" contenteditable="true" @input="autoSave" @paste="handlePaste"
          style="flex:1;padding:24px 40px;outline:none;font-size:15px;line-height:1.8;overflow-y:auto;min-height:300px"
          v-html="activeDoc.content">
        </div>
      </template>
    </div>

    <!-- Right: Collaboration Panel -->
    <div v-if="activeDoc" style="width:240px;background:#fafafa;border-left:1px solid #eee;display:flex;flex-direction:column;flex-shrink:0">
      <div style="padding:12px;border-bottom:1px solid #eee">
        <div style="font-weight:600;font-size:13px;margin-bottom:8px">协作成员</div>
        <div v-for="c in collaborators" :key="c.name" style="display:flex;align-items:center;gap:8px;padding:4px 0">
          <el-avatar :size="28" :style="{background:c.color}">{{ c.name[0] }}</el-avatar>
          <div>
            <div style="font-size:12px">{{ c.name }}</div>
            <div style="font-size:11px;color:#909399">{{ c.status }}</div>
          </div>
          <div :style="{width:8,height:8,borderRadius:'50%',background:c.online?'#67c23a':'#c0c4cc',marginLeft:'auto',transition:'background .5s'}"></div>
        </div>
        <el-button size="small" style="margin-top:8px;width:100%" @click="shareVisible=true">
          <el-icon><Share /></el-icon> 分享协作
        </el-button>
      </div>
      <div style="flex:1;overflow-y:auto;padding:12px">
        <div style="font-weight:600;font-size:13px;margin-bottom:8px">评论</div>
        <div v-for="c in comments" :key="c.id" style="margin-bottom:10px;padding:8px;background:#fff;border-radius:6px;font-size:12px">
          <div style="display:flex;align-items:center;gap:6px;margin-bottom:4px">
            <el-avatar :size="20" :style="{background:c.color}">{{ c.user[0] }}</el-avatar>
            <span style="font-weight:500">{{ c.user }}</span>
            <span style="color:#c0c4cc;font-size:11px;margin-left:auto">{{ c.time }}</span>
          </div>
          <div style="line-height:1.6;color:#606266">{{ c.text }}</div>
        </div>
        <div style="display:flex;gap:8px">
          <el-input v-model="newComment" size="small" placeholder="添加评论..." @keyup.enter="addComment" />
          <el-button size="small" circle @click="addComment"><el-icon><Promotion /></el-icon></el-button>
        </div>
      </div>
    </div>

    <!-- Share Dialog -->
    <el-dialog v-model="shareVisible" title="分享文档" width="420px">
      <div style="line-height:2">
        <p><b>分享链接</b></p>
        <el-input v-model="shareLink" readonly><template #append><el-button @click="copyLink">复制</el-button></template></el-input>
        <p style="margin-top:12px"><b>权限设置</b></p>
        <el-radio-group v-model="sharePerm">
          <el-radio value="view">仅查看</el-radio>
          <el-radio value="comment">可评论</el-radio>
          <el-radio value="edit">可编辑</el-radio>
        </el-radio-group>
        <p style="margin-top:12px"><b>邀请成员</b></p>
        <el-select v-model="inviteUsers" multiple filterable placeholder="搜索成员" style="width:100%">
          <el-option v-for="u in allUsers" :key="u" :label="u" :value="u" />
        </el-select>
      </div>
      <template #footer><el-button @click="shareVisible=false">取消</el-button><el-button type="primary" @click="shareVisible=false">确认分享</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'

const docs = ref([])
const searchText = ref('')
const activeDoc = ref(null)
const shareVisible = ref(false)
const shareLink = ref('https://enterprise.local/docs/abc123')
const sharePerm = ref('edit')
const inviteUsers = ref([])
const newComment = ref('')
const heading = ref('div')
const editor = ref(null)

const allUsers = ['张三','李四','王五','赵六','陈七']
const onlineUsers = [{name:'张三',color:'#409eff'},{name:'李四',color:'#67c23a'},{name:'王五',color:'#e6a23c'}]

const collaborators = ref([
  {name:'张三',color:'#409eff',status:'正在编辑',online:true},
  {name:'李四',color:'#67c23a',status:'正在查看',online:true},
  {name:'王五',color:'#e6a23c',status:'2分钟前离开',online:false},
])

const comments = ref([
  {id:1,user:'李四',color:'#67c23a',text:'这里的数据来源需要补充一下',time:'10:30'},
  {id:2,user:'王五',color:'#e6a23c',text:'已补充数据来源，请再次确认',time:'10:45'},
])

const mockDocs = [
  {id:1,title:'Q2项目评审报告',type:'doc',content:'<h2>Q2项目评审报告</h2><p>本报告总结了2026年第二季度的项目进展情况。</p><h3>一、项目概况</h3><p>Q2共完成5个重点项目，其中<strong>知识库微服务</strong>为核心交付。</p>',updatedByName:'张三',updatedAt:'10:45'},
  {id:2,title:'技术架构设计文档',type:'doc',content:'<h2>技术架构设计文档</h2><p>本文档描述企业智能工作平台的技术架构设计。</p><h3>微服务架构</h3><ul><li>Gateway 网关服务</li><li>Knowledge-AI 知识库服务</li><li>Collaboration 协同服务</li><li>Workbench 工作台服务</li></ul>',updatedByName:'张三',updatedAt:'09:30'},
]

const filteredDocs = computed(() => docs.value.filter(d => !searchText.value || d.title.includes(searchText.value)))

function readUser() {
  try {
    return JSON.parse(localStorage.getItem('user') || '{}') || {}
  } catch {
    return {}
  }
}
function headers() {
  const u = readUser()
  return { 'X-User-Id': String(u.id || 1), 'X-Is-Admin': String(u.isAdmin ? 'true' : 'false'), 'Content-Type': 'application/json' }
}

async function loadDocs() {
  try { const r=await fetch('/api/docs',{headers:headers()}); docs.value=(await r.json()).data||[] }
  catch(e) { docs.value=mockDocs }
}

const route = useRoute()
watch(
  () => route.path,
  (path) => {
    if (path === '/documents') {
      loadDocs()
    }
  },
  { immediate: true }
)

async function createDoc() {
  try {
    const r=await fetch('/api/docs',{method:'POST',headers:headers(),body:JSON.stringify({title:'未命名文档',content:''})})
    const id=(await r.json()).data; const d={id,title:'未命名文档',type:'doc',content:'',updatedBy:'我',updatedAt:'刚刚'}
    docs.value.unshift(d); activeDoc.value=d
  } catch(e) { const d={id:Date.now(),title:'未命名文档',type:'doc',content:'',updatedBy:'我',updatedAt:'刚刚'}; docs.value.unshift(d); activeDoc.value=d }
}

function openDoc(doc) { activeDoc.value = doc }

let saveTimer=null
function autoSave() {
  if (!activeDoc.value) return
  clearTimeout(saveTimer)
  saveTimer=setTimeout(async () => {
    const el=document.querySelector('[contenteditable="true"]')
    if (el) activeDoc.value.content=el.innerHTML
    try { await fetch(`/api/docs/${activeDoc.value.id}`,{method:'PUT',headers:headers(),body:JSON.stringify({title:activeDoc.value.title,content:activeDoc.value.content})}) } catch(e) {}
  }, 1500)
}
function execCmd(cmd) { document.execCommand(cmd, false, null); editor.value?.focus() }
function setHeading(v) { document.execCommand('formatBlock', false, `<${v}>`); editor.value?.focus() }
function insertTable() {
  document.execCommand('insertHTML', false, '<table border="1" style="border-collapse:collapse;width:100%"><tr><td>列1</td><td>列2</td></tr><tr><td></td><td></td></tr></table><p><br></p>')
  autoSave()
}
function addComment() {
  if (!newComment.value.trim()) return
  comments.value.push({ id: Date.now(), user:'我', color:'#409eff', text:newComment.value, time: new Date().toLocaleTimeString('zh',{hour:'2-digit',minute:'2-digit'}) })
  newComment.value = ''
}
function copyLink() { navigator.clipboard.writeText(shareLink.value); ElMessage.success('链接已复制') }
function handlePaste(e) { e.preventDefault(); document.execCommand('insertText', false, e.clipboardData.getData('text/plain')) }
</script>
