// Load test for the WORKHUB API. No dependencies: needs Node 20+ (global fetch).
//
//   node loadtest/load.mjs [--base http://localhost:8080] [--users 20] [--seconds 20] [--issues 200]
//
// Start the backend with RATE_LIMIT_ENABLED=false (sign-up is limited to 20 per minute per address otherwise).
// Setup: one owner creates a workspace, team and project with --issues issues; --users members are registered and
// added to the project. Load: every user loops over a realistic mix of reads and writes for --seconds. The summary
// prints requests/s and latency percentiles per endpoint and exits with code 1 when p95 or the error rate is too high.

const args = Object.fromEntries(
  process.argv.slice(2).reduce((pairs, arg, i, all) => (arg.startsWith('--') ? [...pairs, [arg.slice(2), all[i + 1]]] : pairs), []),
)
const BASE = (args.base ?? 'http://localhost:8080').replace(/\/$/, '')
const USERS = Number(args.users ?? 20)
const SECONDS = Number(args.seconds ?? 20)
const ISSUES = Number(args.issues ?? 200)
const MAX_P95_MS = Number(args['max-p95'] ?? 800)
const MAX_ERROR_RATE = Number(args['max-errors'] ?? 0.01)

async function call(token, method, path, body) {
  const res = await fetch(BASE + path, {
    method,
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: body ? JSON.stringify(body) : undefined,
  })
  const text = await res.text()
  return { status: res.status, json: text ? JSON.parse(text) : null }
}

async function must(token, method, path, body) {
  const r = await call(token, method, path, body)
  if (r.status >= 300) throw new Error(`${method} ${path} -> ${r.status} ${JSON.stringify(r.json)}`)
  return r.json
}

const run = Date.now().toString(36)
async function register(label) {
  const email = `load-${run}-${label}@example.com`
  const r = await must(null, 'POST', '/api/auth/register', { email, password: 'Load-test-pass-1', fullName: `Load ${label}` })
  return { token: r.accessToken, id: r.user.id, email }
}

console.log(`Setting up on ${BASE}: ${USERS} users, ${ISSUES} issues ...`)
const owner = await register('owner')
const ws = await must(owner.token, 'POST', '/api/workspaces', { name: `Load ${run}` })
const team = await must(owner.token, 'POST', `/api/workspaces/${ws.id}/teams`, { name: 'Load team' })
const key = ('L' + run.toUpperCase().replace(/[^A-Z0-9]/g, '')).slice(0, 8)
const project = await must(owner.token, 'POST', `/api/teams/${team.id}/projects`, { key, name: 'Load project', methodology: 'SCRUM' })
for (let i = 0; i < ISSUES; i++) {
  await must(owner.token, 'POST', `/api/projects/${project.id}/issues`, { type: i % 10 === 0 ? 'BUG' : 'TASK', title: `Issue ${i}`, storyPoints: (i % 5) + 1 })
}
const users = []
for (let i = 0; i < USERS; i++) {
  const user = await register(`u${i}`)
  const inv = await must(owner.token, 'POST', `/api/projects/${project.id}/invitations`, { kind: 'LINK', role: 'DEVELOPER' })
  await must(user.token, 'POST', '/api/invitations/accept', { token: new URL(inv.inviteUrl).pathname.split('/').pop() })
  users.push(user)
}
const sample = (await must(owner.token, 'GET', `/api/projects/${project.id}/issues?size=50`)).items

const stats = new Map()
function record(name, ms, ok) {
  const s = stats.get(name) ?? { times: [], errors: 0 }
  s.times.push(ms)
  if (!ok) s.errors++
  stats.set(name, s)
}
async function timed(name, token, method, path, body, okStatus = (s) => s < 400) {
  const t0 = performance.now()
  let ok = false
  try {
    const r = await call(token, method, path, body)
    ok = okStatus(r.status)
    return r
  } catch {
    return { status: 0 }
  } finally {
    record(name, performance.now() - t0, ok)
  }
}

const pick = (list) => list[Math.floor(Math.random() * list.length)]
const scenarios = [
  [5, (u) => timed('GET backlog', u.token, 'GET', `/api/projects/${project.id}/issues?size=100`)],
  [3, (u) => timed('GET issue', u.token, 'GET', `/api/issues/${pick(sample).id}`)],
  [2, (u) => timed('GET project', u.token, 'GET', `/api/projects/${project.id}`)],
  [2, (u) => timed('GET reports/workload', u.token, 'GET', `/api/projects/${project.id}/reports/workload`)],
  [2, (u) => timed('GET notifications', u.token, 'GET', '/api/notifications/unread-count')],
  [1, (u) => timed('GET chat conversations', u.token, 'GET', '/api/chat/conversations')],
  [2, (u) => timed('PUT issue status', u.token, 'PUT', `/api/issues/${pick(sample).id}/status`, { status: pick(['TO_DO', 'IN_PROGRESS', 'CODE_REVIEW']) })],
  [1, (u) => timed('POST issue', u.token, 'POST', `/api/projects/${project.id}/issues`, { type: 'TASK', title: 'Created under load' })],
]
const weighted = scenarios.flatMap(([weight, fn]) => Array(weight).fill(fn))

console.log(`Running ${USERS} concurrent users for ${SECONDS}s ...`)
const end = Date.now() + SECONDS * 1000
const started = performance.now()
await Promise.all(
  users.map(async (user) => {
    while (Date.now() < end) {
      await pick(weighted)(user)
      await new Promise((r) => setTimeout(r, 20 + Math.random() * 80)) // think time
    }
  }),
)
const elapsed = (performance.now() - started) / 1000

const pct = (sorted, p) => sorted[Math.min(sorted.length - 1, Math.ceil((p / 100) * sorted.length) - 1)]
let total = 0
let errors = 0
let worstP95 = 0
const rows = []
for (const [name, s] of [...stats].sort()) {
  const sorted = [...s.times].sort((a, b) => a - b)
  total += sorted.length
  errors += s.errors
  worstP95 = Math.max(worstP95, pct(sorted, 95))
  rows.push({ endpoint: name, requests: sorted.length, errors: s.errors, 'p50 ms': Math.round(pct(sorted, 50)), 'p95 ms': Math.round(pct(sorted, 95)), 'p99 ms': Math.round(pct(sorted, 99)), 'max ms': Math.round(sorted.at(-1)) })
}
console.table(rows)
const errorRate = errors / Math.max(total, 1)
console.log(`${total} requests in ${elapsed.toFixed(1)}s = ${(total / elapsed).toFixed(0)} req/s, error rate ${(errorRate * 100).toFixed(2)}%, worst p95 ${Math.round(worstP95)} ms`)
if (errorRate > MAX_ERROR_RATE || worstP95 > MAX_P95_MS) {
  console.log(`FAILED: thresholds are error rate <= ${MAX_ERROR_RATE * 100}% and p95 <= ${MAX_P95_MS} ms`)
  process.exit(1)
}
console.log('OK')
