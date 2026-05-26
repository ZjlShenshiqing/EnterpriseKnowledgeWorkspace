# 仪表盘角色区分 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Dashboard 根据 `isAdmin` 区分普通用户和管理员两套布局——统计卡片、快捷操作、最近文档均按角色展示。

**Architecture:** 纯前端驱动，在 Dashboard.vue 中通过 `isAdminUser()` 条件渲染。后端 `/api/workbench/overview` 扩展字段，对普通用户返回协同类统计，对管理员额外返回知识管理统计。不改变路由守卫、侧边栏、管理后台。

**Tech Stack:** Vue 3 (Composition API), Element Plus, Spring Boot, RestTemplate

---

### Task 1: 后端 — `/overview` 扩展协同统计字段

**Files:**
- Modify: `enterprise-workbench-service/src/main/java/com/zjl/workbench/web/WorkbenchController.java:39-86`

- [ ] **Step 1: 在 overview 方法中新增审批数、未读消息数、知识库数、意图配置数、今日会话数的聚合逻辑**

修改 `overview()` 方法，在现有逻辑后追加以下统计：

```java
// 审批统计
try {
    var approvals = callList(collabUrl + "/api/approvals", headers);
    long pendingApprovals = approvals.stream().filter(a -> {
        Object s = ((Map<?,?>)a).get("status");
        return s == null || (!"approved".equals(s.toString()) && !"rejected".equals(s.toString()));
    }).count();
    data.put("pendingApprovalCount", pendingApprovals);
} catch (Exception e) { data.put("pendingApprovalCount", 0); }

// 未读消息数（有 IM 服务则调用，否则默认 0）
try {
    var unreadResp = callForObject(collabUrl + "/api/im/unread-count?userId=" + userId, Map.of(UA, String.valueOf(userId)), Map.class);
    if (unreadResp != null && unreadResp.get("data") instanceof Number n) {
        data.put("unreadMessageCount", n.intValue());
    } else {
        data.put("unreadMessageCount", 0);
    }
} catch (Exception e) { data.put("unreadMessageCount", 0); }

// 知识库数量
try {
    var basesResp = callForObject(knowledgeUrl + "/api/kb/bases?current=1&size=1", headers, Map.class);
    if (basesResp != null && basesResp.get("data") instanceof Map basesData) {
        data.put("baseCount", basesData.getOrDefault("total", 0));
    }
} catch (Exception e) { data.put("baseCount", 0); }

// 意图配置数量
try {
    var intentsResp = callForObject(collabUrl + "/api/intents?current=1&size=1", headers, Map.class);
    if (intentsResp != null && intentsResp.get("data") instanceof Map intentsData) {
        data.put("intentCount", intentsData.getOrDefault("total", 0));
    }
} catch (Exception e) { data.put("intentCount", 0); }

// 今日会话数量（保留现有 documentCount 作为总文档数）
try {
    var sessionsResp = callForObject(knowledgeUrl + "/api/kb/agent/sessions?current=1&size=1", headers, Map.class);
    if (sessionsResp != null && sessionsResp.get("data") instanceof Map sessionsData) {
        data.put("todaySessionCount", sessionsData.getOrDefault("total", 0));
    }
} catch (Exception e) { data.put("todaySessionCount", 0); }
```

- [ ] **Step 2: 修改 `recentDocs` 数据，每条记录附加 `docType` 字段**

将现有的 `recentDocs` 获取逻辑改为：

```java
try {
    var kbResp = callForObject(knowledgeUrl + "/api/kb/documents?current=1&size=5", headers, Map.class);
    if (kbResp != null) {
        Object dataObj = kbResp.get("data");
        if (dataObj instanceof Map kbData) {
            Object records = kbData.get("records");
            if (records instanceof List list) {
                // 知识文档标记 docType = "knowledge"
                List<Map<String,Object>> docs = new ArrayList<>();
                for (Object item : list) {
                    if (item instanceof Map m) {
                        Map<String,Object> doc = new LinkedHashMap<>(m);
                        doc.put("docType", "knowledge");
                        docs.add(doc);
                    }
                }
                data.put("recentDocs", docs);
                data.put("documentCount", kbData.getOrDefault("total", 0));
            }
        }
    }
} catch (Exception e) {
    data.put("recentDocs", List.of()); data.put("documentCount", 0);
}
```

- [ ] **Step 3: 验证后端编译通过**

Run: `mvn compile -pl enterprise-workbench-service -DskipTests`

- [ ] **Step 4: Commit**

```bash
git add enterprise-workbench-service/src/main/java/com/zjl/workbench/web/WorkbenchController.java
git commit -m "feat: workbench /overview 扩展协同统计与 docType 字段"
```

---

### Task 2: 前端 — Dashboard.vue 角色区分重写

**Files:**
- Modify: `enterprise-web/src/pages/Dashboard.vue`

- [ ] **Step 1: 修改 `<script setup>`，新增 `isAdmin` 和角色相关的计算属性**

替换第 166-227 行的 script 部分：

```js
<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh, DocumentAdd, Calendar, List, Checked } from '@element-plus/icons-vue'
import { isAdminUser } from '../api/index.js'

const isAdmin = computed(() => isAdminUser())

const userName = computed(() => { const u=JSON.parse(localStorage.getItem('user')||'{}'); return u.realName||u.username||'管理员' })

const selectedTime = ref('24h')
const timeRanges = ['24h', '7d', '30d']
const currentTime = computed(() => {
  const now = new Date()
  return `${String(now.getMonth()+1).padStart(2, '0')}/${String(now.getDate()).padStart(2, '0')} ${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}:${String(now.getSeconds()).padStart(2, '0')}`
})

const recentDocs = ref([])
const todos = ref([])
const todayMeetings = ref([])

const collabStats = ref({ todos: 0, meetings: 0, approvals: 0, messages: 0 })
const knowledgeStats = ref({ documents: 0, bases: 0, intents: 0, sessions: 0 })

const quickActions = computed(() => {
  const all = [
    { label:'新建文档', icon:'DocumentAdd', bg:'#eff6ff', color:'#3b82f6', path:'/documents' },
    { label:'新建会议', icon:'Calendar', bg:'#f0fdf4', color:'#22c55e', path:'/meetings' },
    { label:'添加待办', icon:'List', bg:'#fffbeb', color:'#f59e0b', path:'/todos' },
  ]
  if (isAdmin.value) {
    all.push({ label:'意图配置', icon:'Checked', bg:'#fef2f2', color:'#ef4444', path:'/admin/intent-config' })
  }
  return all
})

// 管理�?最近文档分两组
const knowledgeDocs = computed(() => recentDocs.value.filter(d => d.docType === 'knowledge'))
const collabDocs = computed(() => recentDocs.value.filter(d => d.docType === 'collaboration' || !d.docType))

function headers() { const u=JSON.parse(localStorage.getItem('user')||'{}'); return {'X-User-Id':String(u.id||1),'X-Is-Admin':String(u.isAdmin?'true':'false')} }

async function refreshData() {
  await loadData()
  ElMessage.success('刷新成功')
}

async function loadData() {
  try {
    const resp = await fetch('/api/workbench/overview', {headers:headers()})
    const data = (await resp.json()).data||{}
    recentDocs.value = data.recentDocs||[]
    todos.value = (data.todos||[]).map(t=>({...t,done:t.done===1||t.done===true}))
    if (data.meetings) todayMeetings.value = data.meetings.filter(m=>m.date===new Date().toISOString().split('T')[0])

    collabStats.value = {
      todos: data.todoCount || 0,
      meetings: data.meetingCount || 0,
      approvals: data.pendingApprovalCount || 0,
      messages: data.unreadMessageCount || 0
    }

    knowledgeStats.value = {
      documents: data.documentCount || 0,
      bases: data.baseCount || 0,
      intents: data.intentCount || 0,
      sessions: data.todaySessionCount || 0
    }
  } catch(e) {
    console.log('Workbench API not available')
  }
}

function toggleTodo(t) { t.done = !t.done }

onMounted(() => { loadData() })
</script>
```

- [ ] **Step 2: 修改统计卡片模板 — 管理员布局**

替换第 25-74 行（`<!-- Knowledge Stats -->` 区�?：

```html
    <!-- Admin Stats -->
    <template v-if="isAdmin">
      <div style="display:flex;gap:16px;">
        <div class="stat-card admin-knowledge-card" style="background:linear-gradient(135deg,#1e40af,#3b82f6);color:#fff;cursor:pointer;" @click="$router.push('/admin/documents')">
          <div style="font-size:12px;opacity:0.8;margin-bottom:4px;">知识库 · 文档</div>
          <div class="stat-value" style="color:#fff;font-size:36px;font-weight:700;">{{ knowledgeStats.documents }}</div>
          <div style="font-size:12px;opacity:0.7;">分布在 <b>{{ knowledgeStats.bases }}</b> 个知识库</div>
        </div>
        <div class="stat-card admin-knowledge-card" style="background:linear-gradient(135deg,#047857,#10b981);color:#fff;cursor:pointer;" @click="$router.push('/admin/intent-config')">
          <div style="font-size:12px;opacity:0.8;margin-bottom:4px;">意图配置</div>
          <div class="stat-value" style="color:#fff;font-size:36px;font-weight:700;">{{ knowledgeStats.intents }}</div>
          <div style="font-size:12px;opacity:0.7;">覆盖 <b>{{ knowledgeStats.intents }}</b> 个意图</div>
        </div>
        <div class="stat-card admin-knowledge-card" style="background:linear-gradient(135deg,#7c3aed,#a78bfa);color:#fff;">
          <div style="font-size:12px;opacity:0.8;margin-bottom:4px;">今日智能会话</div>
          <div class="stat-value" style="color:#fff;font-size:36px;font-weight:700;">{{ knowledgeStats.sessions }}</div>
          <div style="font-size:12px;opacity:0.7;">AI 对话次数</div>
        </div>
      </div>
      <div style="display:flex;gap:12px;">
        <div class="stat-card admin-collab-card" @click="$router.push('/todos')">
          <div class="stat-icon-wrapper">
            <div class="stat-icon" style="background:#ecfdf5;">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#0d9488" stroke-width="2"><polyline points="20 6 9 17 4 12"/></svg>
            </div>
          </div>
          <div class="stat-value" style="color:#0d9488;">{{ collabStats.todos }}</div>
          <div class="stat-label">我的待办</div>
        </div>
        <div class="stat-card admin-collab-card" @click="$router.push('/meetings')">
          <div class="stat-icon-wrapper">
            <div class="stat-icon" style="background:#ecfdf5;">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#0d9488" stroke-width="2"><rect x="3" y="4" width="18" height="18" rx="2" ry="2"/><line x1="16" y1="2" x2="16" y2="6"/><line x1="8" y1="2" x2="8" y2="6"/><line x1="3" y1="10" x2="21" y2="10"/></svg>
            </div>
          </div>
          <div class="stat-value" style="color:#0d9488;">{{ collabStats.meetings }}</div>
          <div class="stat-label">今日会议</div>
        </div>
        <div class="stat-card admin-collab-card" @click="$router.push('/approvals')">
          <div class="stat-icon-wrapper">
            <div class="stat-icon" style="background:#fff7ed;">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#f97316" stroke-width="2"><polyline points="9 11 12 14 22 4"/><path d="M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11"/></svg>
            </div>
          </div>
          <div class="stat-value" style="color:#f97316;">{{ collabStats.approvals }}</div>
          <div class="stat-label">待审批</div>
        </div>
        <div class="stat-card admin-collab-card" @click="$router.push('/chats')">
          <div class="stat-icon-wrapper">
            <div class="stat-icon" style="background:#f5f3ff;">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#8b5cf6" stroke-width="2"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/></svg>
            </div>
          </div>
          <div class="stat-value" style="color:#8b5cf6;">{{ collabStats.messages }}</div>
          <div class="stat-label">未读消息</div>
        </div>
      </div>
    </template>

    <!-- Regular User Stats -->
    <template v-else>
      <div class="stat-grid">
        <div class="stat-card collab-gradient-card" style="background:linear-gradient(135deg,#0d9488,#0f766e);color:#fff;cursor:pointer;" @click="$router.push('/todos')">
          <div style="font-size:13px;opacity:0.85;margin-bottom:8px;">我的待办</div>
          <div style="font-size:32px;font-weight:700;">{{ collabStats.todos }}</div>
          <div style="font-size:12px;opacity:0.7;margin-top:4px;">待处理任务</div>
        </div>
        <div class="stat-card collab-gradient-card" style="background:linear-gradient(135deg,#14b8a6,#06b6d4);color:#fff;cursor:pointer;" @click="$router.push('/meetings')">
          <div style="font-size:13px;opacity:0.85;margin-bottom:8px;">今日会议</div>
          <div style="font-size:32px;font-weight:700;">{{ collabStats.meetings }}</div>
          <div style="font-size:12px;opacity:0.7;margin-top:4px;">今日安排</div>
        </div>
        <div class="stat-card collab-gradient-card" style="background:linear-gradient(135deg,#f97316,#ef4444);color:#fff;cursor:pointer;" @click="$router.push('/approvals')">
          <div style="font-size:13px;opacity:0.85;margin-bottom:8px;">待审批</div>
          <div style="font-size:32px;font-weight:700;">{{ collabStats.approvals }}</div>
          <div style="font-size:12px;opacity:0.7;margin-top:4px;">需要处理</div>
        </div>
        <div class="stat-card collab-gradient-card" style="background:linear-gradient(135deg,#8b5cf6,#7c3aed);color:#fff;cursor:pointer;" @click="$router.push('/chats')">
          <div style="font-size:13px;opacity:0.85;margin-bottom:8px;">未读消息</div>
          <div style="font-size:32px;font-weight:700;">{{ collabStats.messages }}</div>
          <div style="font-size:12px;opacity:0.7;margin-top:4px;">新消息提醒</div>
        </div>
      </div>
    </template>
```

- [ ] **Step 3: 修改快捷操作 — 动态遍历 quickActions**

替换第 84-90 行的快捷操作循环（保持 `v-for="a in quickActions"` 不变，因为 `quickActions` 已是计算属性）：

第 84-90 行保持不变，因为 `quickActions` computed 属性已自动按角色过滤。

- [ ] **Step 4: 修改最近文档 — 管理员分为两组**

替换第 95-115 行（最近文档面板）：

```html
        <!-- Recent Docs -->
        <div class="panel">
          <div class="panel-header">
            <span class="panel-title">最近文档</span>
            <span class="panel-link" @click="$router.push(isAdmin ? '/admin/documents' : '/documents')">查看全部 →</span>
          </div>
          <template v-if="isAdmin">
            <div v-if="knowledgeDocs.length" style="padding:0 20px;">
              <div style="font-size:12px;color:#9ca3af;font-weight:600;padding:8px 0;">知识文档</div>
              <div v-for="doc in knowledgeDocs.slice(0,3)" :key="doc.id" class="doc-item">
                <div class="doc-icon">
                  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="#8f959e" stroke-width="2"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/></svg>
                </div>
                <div class="doc-info">
                  <div class="doc-title">{{ doc.title }}</div>
                  <div class="doc-meta">{{ doc.fileType }} · {{ doc.createdAt || doc.created_at }}</div>
                </div>
              </div>
            </div>
            <div v-if="collabDocs.length" style="padding:0 20px;">
              <div style="font-size:12px;color:#9ca3af;font-weight:600;padding:8px 0;">协作文档</div>
              <div v-for="doc in collabDocs.slice(0,3)" :key="doc.id" class="doc-item">
                <div class="doc-icon">
                  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="#8f959e" stroke-width="2"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/></svg>
                </div>
                <div class="doc-info">
                  <div class="doc-title">{{ doc.title }}</div>
                  <div class="doc-meta">{{ doc.fileType }} · {{ doc.createdAt || doc.created_at }}</div>
                </div>
              </div>
            </div>
            <div v-if="!knowledgeDocs.length && !collabDocs.length" class="empty-state">暂无文档</div>
          </template>
          <template v-else>
            <div v-if="!collabDocs.length" class="empty-state">暂无文档</div>
            <div v-else class="doc-list">
              <div v-for="doc in collabDocs.slice(0,5)" :key="doc.id" class="doc-item">
                <div class="doc-icon">
                  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="#8f959e" stroke-width="2"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/></svg>
                </div>
                <div class="doc-info">
                  <div class="doc-title">{{ doc.title }}</div>
                  <div class="doc-meta">{{ doc.fileType }} · {{ doc.createdAt || doc.created_at }}</div>
                </div>
              </div>
            </div>
          </template>
        </div>
```

- [ ] **Step 5: 新增管理员卡片样式**

在 `<style scoped>` 末尾追加：

```css
/* Admin Knowledge Cards */
.admin-knowledge-card {
  flex:1;
  border-radius:16px;
  padding:24px;
  cursor:pointer;
  transition:all .2s;
}
.admin-knowledge-card:hover {
  box-shadow:0 4px 20px rgba(0,0,0,0.12);
  transform:translateY(-2px);
}

/* Admin Collab Cards */
.admin-collab-card {
  flex:1;
  background:#fff;
  border:1px solid #e5e7eb;
  border-radius:12px;
  padding:16px;
  text-align:center;
  cursor:pointer;
  transition:all .15s;
}
.admin-collab-card:hover {
  box-shadow:0 2px 12px rgba(0,0,0,0.04);
  transform:translateY(-1px);
}

/* Regular User Gradient Cards */
.collab-gradient-card {
  flex:1;
  border-radius:16px;
  padding:20px;
  cursor:pointer;
  transition:all .2s;
}
.collab-gradient-card:hover {
  box-shadow:0 4px 20px rgba(0,0,0,0.1);
  transform:translateY(-2px);
}
```

- [ ] **Step 6: 验证前端能正常构建**

Run: `cd enterprise-web && npm run build 2>&1 | tail -5`

---

### Task 3: 验证与收尾

- [ ] **Step 1: 启动后端验证编译通过**

Run: `mvn compile -pl enterprise-workbench-service -DskipTests`

- [ ] **Step 2: 检查未引入 lint 错误**

Run: `cd enterprise-web && npx vue-tsc --noEmit 2>&1 | head -20`（如果项目配置了 type-check）

- [ ] **Step 3: Commit**

```bash
git add enterprise-web/src/pages/Dashboard.vue
git commit -m "feat: 仪表盘角色区分 — 普通用户协同视图 + 管理员双区布局"
```
