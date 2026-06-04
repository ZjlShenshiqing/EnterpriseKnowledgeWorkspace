import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { join } from 'node:path'

const root = new URL('../../', import.meta.url)

function read(path) {
  return readFileSync(join(root.pathname, path), 'utf8')
}

test('admin intent config links use the registered backend route', () => {
  const router = read('src/router/index.js')
  const dashboard = read('src/pages/Dashboard.vue')
  const intentList = read('src/pages/admin/IntentList.vue')

  assert.match(router, /path:\s*'intents\/config'/)
  assert.doesNotMatch(dashboard, /\/admin\/intent-config/)
  assert.doesNotMatch(intentList, /\/admin\/intent-config/)
  assert.match(dashboard, /\/admin\/intents\/config/)
  assert.match(intentList, /\/admin\/intents\/config/)
})
