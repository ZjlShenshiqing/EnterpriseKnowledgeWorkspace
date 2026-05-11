import axios from 'axios'

const headers = {
  'X-User-Id': '1',
  'X-Department-Id': '1',
  'X-Is-Admin': 'true'
}

const kbApi = axios.create({ baseURL: '/api/kb', headers })
const systemApi = axios.create({ baseURL: '/api/system', headers })

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
    headers: { 'Content-Type': 'application/json', ...headers },
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
