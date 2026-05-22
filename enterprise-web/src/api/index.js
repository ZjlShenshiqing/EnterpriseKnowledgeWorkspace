import axios from 'axios'

/**
 * 是否为合法 JWT 紧凑格式（三段 Base64，用两个 '.' 分隔）。
 */
function isJwtFormat(token) {
  const t = (token || '').trim()
  if (!t) return false
  const parts = t.split('.')
  return parts.length === 3 && parts.every((p) => p.length > 0)
}

/**
 * 读取本地登录态。token 优先 user.token，其次 localStorage.token（与 Login.saveAuth 双写一致）。
 * 非 JWT 字符串（如历史的 demo-token）会被丢弃，避免网关 MalformedJwtException。
 */
export function readStoredAuth() {
  let user = {}
  try {
    user = JSON.parse(localStorage.getItem('user') || '{}')
  } catch {
    user = {}
  }
  const rawToken = user.token || localStorage.getItem('token') || ''
  const token = isJwtFormat(rawToken) ? rawToken.trim() : ''
  if (rawToken && !token) {
    localStorage.removeItem('token')
    if (user.token) {
      const cleaned = { ...user }
      delete cleaned.token
      localStorage.setItem('user', JSON.stringify(cleaned))
    }
  }
  return { user, token, hasValidToken: !!token }
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
    headers['Authorization'] = 'Bearer ' + token
  }
  return headers
}

function attachResponseInterceptor(api) {
  api.interceptors.response.use(
    response => {
      const data = response.data
      if (data && (data.code === 40100 || data.code === '40100')) {
        return Promise.reject({
          response: { status: 401, data },
          message: data.message || '未登录或登录已过期'
        })
      }
      return response
    },
    error => Promise.reject(error)
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

// ---- Agent Chat (SSE) ----

export function agentChat(sessionId, message) {
  return fetch('/api/kb/agent/chat', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...getAuthHeaders() },
    body: JSON.stringify({ sessionId, message })
  })
}

export function getAgentSessions() {
  return kbApi.get('/agent/sessions')
}

export function getAgentSessionHistory(id) {
  return kbApi.get(`/agent/sessions/${id}`)
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
