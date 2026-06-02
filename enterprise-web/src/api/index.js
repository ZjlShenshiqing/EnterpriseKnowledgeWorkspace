import axios from 'axios'

/**
 * 规范化 Sa-Token 字符串（去掉 Bearer 前缀，兼容历史 JWT 本地缓存）。
 */
function normalizeToken(raw) {
  const t = (raw || '').trim()
  if (!t) return ''
  return t.startsWith('Bearer ') ? t.substring(7).trim() : t
}

/**
 * 读取本地登录态。token 优先 user.token，其次 localStorage.token。
 */
export function readStoredAuth() {
  let user = {}
  try {
    user = JSON.parse(localStorage.getItem('user') || '{}')
  } catch {
    user = {}
  }
  const token = normalizeToken(user.token || localStorage.getItem('token') || '')
  return { user, token, hasValidToken: !!token }
}

/**
 * 持久化登录态到 localStorage。
 */
export function saveStoredAuth(user) {
  const token = user.token || ''
  const payload = { ...user, token }
  if (token) {
    localStorage.setItem('token', token)
  } else {
    localStorage.removeItem('token')
  }
  localStorage.setItem('user', JSON.stringify(payload))
}

/**
 * 是否具备管理后台访问权限（登录时由网关根据 admin 角色写入 isAdmin）。
 */
export function isAdminUser() {
  return !!readStoredAuth().user.isAdmin
}

export function getAuthHeaders() {
  const { user, token } = readStoredAuth()
  const headers = {
    'X-User-Id': String(user.id || '1'),
    'X-Department-Id': String(user.departmentId || '1'),
    'X-Is-Admin': String(user.isAdmin ? 'true' : 'false')
  }
  if (token) {
    headers['Authorization'] = token
  }
  return headers
}

/**
 * 清除登录态并跳转登录页。
 * 可被各模块调用或作为 401 回调统一使用。
 */
export function forceLogout(message) {
  localStorage.removeItem('token')
  localStorage.removeItem('user')
  if (message) {
    sessionStorage.setItem('login_redirect_message', message)
  }
  window.location.href = '/login'
}

/**
 * 调用后端登出并销毁 Sa-Token 会话，随后清除本地登录态。
 */
export async function logout() {
  const { token } = readStoredAuth()
  try {
    if (token) {
      await fetch('/api/auth/logout', {
        method: 'POST',
        headers: { Authorization: token }
      })
    }
  } catch {
    /* 网络异常时仍清除本地登录态 */
  }
  forceLogout()
}

/**
 * 获取当前登录用户资料。
 */
export async function getProfile() {
  const { token } = readStoredAuth()
  if (!token) {
    throw new Error('未登录')
  }
  const resp = await fetch('/api/auth/profile', {
    headers: { Authorization: token }
  })
  const result = await resp.json()
  if (!resp.ok || !(result.code === 200 || result.code === '200')) {
    throw new Error(result.message || '未登录或登录已过期')
  }
  return result.data || {}
}

/**
 * 应用启动时校验 Token 并刷新本地用户信息。
 */
export async function checkAuth() {
  const { token, user } = readStoredAuth()
  if (!token) {
    return false
  }
  try {
    const data = await getProfile()
    saveStoredAuth({
      ...user,
      id: data.userId,
      username: data.username,
      realName: data.realName || data.username,
      isAdmin: !!data.isAdmin,
      departmentId: data.deptId,
      token
    })
    return true
  } catch {
    forceLogout('登录已过期，请重新登录')
    return false
  }
}

function attachResponseInterceptor(api) {
  api.interceptors.response.use(
    response => {
      const data = response.data
      if (data && (data.code === 40100 || data.code === '40100')) {
        forceLogout('登录已过期，请重新登录')
        return Promise.reject({
          response: { status: 401, data },
          message: data.message || '未登录或登录已过期'
        })
      }
      return response
    },
    error => {
      const status = error?.response?.status
      if (status === 401) {
        forceLogout('登录已过期，请重新登录')
      }
      return Promise.reject(error)
    }
  )
}

const kbApi = axios.create({ baseURL: '/api/kb' })
const systemApi = axios.create({ baseURL: '/api/system' })

// Intercept requests to inject auth headers dynamically
kbApi.interceptors.request.use(config => {
  config.headers = { ...config.headers, ...getAuthHeaders() }
  return config
})
systemApi.interceptors.request.use(config => {
  config.headers = { ...config.headers, ...getAuthHeaders() }
  return config
})

attachResponseInterceptor(systemApi)
attachResponseInterceptor(kbApi)

// ---- Knowledge Base ----

export function getDocuments(params) {
  return kbApi.get('/documents', { params })
}

export function getDocument(id) {
  return kbApi.get(`/documents/${id}`)
}

export function searchDocuments(keyword, limit = 10) {
  return kbApi.get('/documents/search', { params: { keyword, limit } })
}

export function getCategories() {
  return kbApi.get('/categories')
}

export function getKnowledgeBases(params) {
  return kbApi.get('/bases', { params })
}

export function getKnowledgeBase(id) {
  return kbApi.get(`/bases/${id}`)
}

export function createKnowledgeBase(body) {
  return kbApi.post('/bases', body)
}

// ---- Agent Chat (SSE) ----

export function agentChat(sessionId, message, webSearch = false, attachments = []) {
  return fetch('/api/kb/agent/chat', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...getAuthHeaders() },
    body: JSON.stringify({
      sessionId: sessionId != null && sessionId !== '' ? String(sessionId) : null,
      message,
      webSearch,
      attachments: attachments.map(a => ({
        name: a.name,
        size: a.size,
        path: a.path
      }))
    })
  })
}

export function getAgentSessions() {
  return kbApi.get('/agent/sessions')
}

export function getAgentSessionHistory(id) {
  return kbApi.get(`/agent/sessions/${String(id)}`)
}

export function uploadAgentAttachment(file) {
  const form = new FormData()
  form.append('file', file)
  return fetch('/api/kb/agent/upload', {
    method: 'POST',
    headers: getAuthHeaders(),
    body: form
  })
}

// ---- Approval Workflow ----

export function createApproval(body) {
  return fetch('/api/approvals', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...getAuthHeaders() },
    body: JSON.stringify(body)
  }).then(r => r.json())
}

export function getMyApprovals() {
  return fetch('/api/approvals/my', { headers: getAuthHeaders() }).then(r => r.json())
}

export function getApprovalDetail(id) {
  return fetch(`/api/approvals/${id}`, { headers: getAuthHeaders() }).then(r => r.json())
}

export function getMyWorkflowTasks() {
  return fetch('/api/workflow/tasks/my', { headers: getAuthHeaders() }).then(r => r.json())
}

export function handleWorkflowTask(taskId, action, comment) {
  return fetch(`/api/workflow/tasks/${taskId}/actions`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...getAuthHeaders() },
    body: JSON.stringify({ action, comment })
  }).then(r => r.json())
}

// ---- System Admin ----

export function getUsers(params) {
  return systemApi.get('/users', { params })
}

export function getRoles() {
  return systemApi.get('/roles')
}

export function getUser(id) {
  return systemApi.get(`/users/${id}`)
}

export function createUser(body) {
  return systemApi.post('/users', body)
}

export function updateUser(id, body) {
  return systemApi.put(`/users/${id}`, body)
}

export function deleteUser(id) {
  return systemApi.delete(`/users/${id}`)
}

export function getUserStats() {
  return systemApi.get('/users/stats')
}

// ---- Roles ----

export function getRole(id) {
  return systemApi.get(`/roles/${id}`)
}

export function createRole(body) {
  return systemApi.post('/roles', body)
}

export function updateRole(id, body) {
  return systemApi.put(`/roles/${id}`, body)
}

export function deleteRole(id) {
  return systemApi.delete(`/roles/${id}`)
}

export function getPermissions() {
  return systemApi.get('/permissions')
}

export function getDepts() {
  return systemApi.get('/depts')
}

// ---- Helpers ----

// ---- Operation Logs ----

export function getLogs(params) {
  return systemApi.get('/logs', { params })
}

export function isApiAvailable() {
  return kbApi.get('/documents', { params: { current: 1, size: 1 } })
    .then(() => true)
    .catch(() => false)
}

// ---- Meetings ----

export function getMyMeetings() {
  const { user } = readStoredAuth()
  const userName = user.realName || user.username || ''
  return fetch(`/api/meetings/my?userName=${encodeURIComponent(userName)}`, { headers: getAuthHeaders() })
}

export function createMeeting(body) {
  return fetch('/api/meetings', {
    method: 'POST',
    headers: { ...getAuthHeaders(), 'Content-Type': 'application/json' },
    body: JSON.stringify(body)
  })
}

export function updateMeeting(id, body) {
  return fetch(`/api/meetings/${id}`, {
    method: 'PUT',
    headers: { ...getAuthHeaders(), 'Content-Type': 'application/json' },
    body: JSON.stringify(body)
  })
}

export function deleteMeeting(id) {
  return fetch(`/api/meetings/${id}`, {
    method: 'DELETE',
    headers: getAuthHeaders()
  })
}

// ---- Favorites ----

export function getFavorites() {
  return fetch('/api/workbench/favorites', { headers: getAuthHeaders() }).then(r => r.json())
}

export function addFavorite(body) {
  return fetch('/api/workbench/favorites', {
    method: 'POST',
    headers: { ...getAuthHeaders(), 'Content-Type': 'application/json' },
    body: JSON.stringify(body)
  }).then(r => r.json())
}

export function removeFavorite(id) {
  return fetch(`/api/workbench/favorites/${id}`, {
    method: 'DELETE',
    headers: getAuthHeaders()
  }).then(r => r.json())
}

// ---- Pipelines ----

export function getPipelines(params) {
  return kbApi.get('/pipelines', { params })
}

export function getPipelineDetail(id) {
  return kbApi.get(`/pipelines/${id}`)
}

export function getPipelineTasks(params) {
  return kbApi.get('/pipelines/tasks', { params })
}

// ---- Chat / Notifications ----

export function getChatUnreadCount() {
  return fetch('/api/chat/unread-count', { headers: getAuthHeaders() })
    .then(r => r.json())
    .then(body => (String(body?.code) === '200' ? (body.data ?? 0) : 0))
    .catch(() => 0)
}
