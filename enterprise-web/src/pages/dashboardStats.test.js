import test from 'node:test'
import assert from 'node:assert/strict'
import { buildKnowledgeStats } from './dashboardStats.js'

test('admin AI stats come from kb admin stats, not workbench overview', () => {
  const stats = buildKnowledgeStats(
    {
      documentCount: 12,
      baseCount: 3,
      intentCount: 4,
      todaySessionCount: 99
    },
    {
      sessionCount: 7,
      messageCount: 21
    }
  )

  assert.deepEqual(stats, {
    documents: 12,
    bases: 3,
    intents: 0,
    sessions: 7,
    messages: 21
  })
})

test('intent count comes from intent tree level 2 nodes, not workbench overview', () => {
  const stats = buildKnowledgeStats(
    {
      documentCount: 12,
      baseCount: 3,
      intentCount: 0
    },
    {
      sessionCount: 7,
      messageCount: 21
    },
    [
      {
        id: 1,
        level: 1,
        children: [
          { id: 2, level: 2, children: [] },
          { id: 3, level: 2, children: [{ id: 4, level: 3, children: [] }] }
        ]
      },
      { id: 5, level: 1, children: [{ id: 6, level: 2, children: [] }] }
    ]
  )

  assert.equal(stats.intents, 3)
})

test('AI stats stay zero when kb admin stats are unavailable', () => {
  const stats = buildKnowledgeStats(
    {
      documentCount: 12,
      baseCount: 3,
      intentCount: 4,
      todaySessionCount: 5
    },
    null
  )

  assert.equal(stats.sessions, 0)
  assert.equal(stats.messages, 0)
})
