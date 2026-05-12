import axios from 'axios'

function getAuthHeaders() {
  const user = JSON.parse(localStorage.getItem('user') || '{}')
  const headers = {
    'X-User-Id': String(user.id || '1'),
    'X-Department-Id': String(user.departmentId || '1'),
    'X-Is-Admin': String(user.isAdmin ? 'true' : 'false')
  }
  if (user.token) headers['Authorization'] = 'Bearer ' + user.token
  return headers
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

// ---- Helpers ----

export function isApiAvailable() {
  return kbApi.get('/documents', { params: { current: 1, size: 1 } })
    .then(() => true)
    .catch(() => false)
}
