import axios from 'axios'

const api = axios.create({
  baseURL: '/api/kb',
  headers: {
    'X-User-Id': '1',
    'X-Department-Id': '1',
    'X-Is-Admin': 'true'
  }
})

export function getDocuments(params) {
  return api.get('/documents', { params })
}

export function getDocument(id) {
  return api.get(`/documents/${id}`)
}

export function searchDocuments(keyword, limit = 10) {
  return api.get('/documents/search', { params: { keyword, limit } })
}

export function getCategories() {
  return api.get('/categories')
}

export function getKnowledgeBases(params) {
  return api.get('/bases', { params })
}

export function agentChat(sessionId, message) {
  return fetch('/api/kb/agent/chat', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-User-Id': '1',
      'X-Department-Id': '1',
      'X-Is-Admin': 'true'
    },
    body: JSON.stringify({ sessionId, message })
  })
}

export function getAgentSessions() {
  return api.get('/agent/sessions')
}

export function getAgentSessionHistory(id) {
  return api.get(`/agent/sessions/${id}`)
}
