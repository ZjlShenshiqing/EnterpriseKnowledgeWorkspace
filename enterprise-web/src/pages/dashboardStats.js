export function toNumber(value, fallback = 0) {
  const n = Number(value)
  return Number.isFinite(n) ? n : fallback
}

export function countIntentNodes(nodes = []) {
  let total = 0
  for (const node of nodes || []) {
    if (node?.level === 2) total += 1
    if (node?.children?.length) total += countIntentNodes(node.children)
  }
  return total
}

export function buildKnowledgeStats(overview = {}, adminStats = null, intentTree = null) {
  return {
    documents: toNumber(overview?.documentCount),
    bases: toNumber(overview?.baseCount),
    intents: Array.isArray(intentTree) ? countIntentNodes(intentTree) : 0,
    sessions: adminStats ? toNumber(adminStats.sessionCount) : 0,
    messages: adminStats ? toNumber(adminStats.messageCount) : 0
  }
}
