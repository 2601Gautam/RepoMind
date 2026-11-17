import test from 'node:test'
import assert from 'node:assert/strict'
import { isRepoActive, isTaskActive, repositoryNotice, taskLink, readJournal, writeJournal,
    reconcileTasks, createRecoveryPoller } from '../src/state/recovery.js'

const memory = () => { const m = new Map(); return { getItem: k => m.has(k) ? m.get(k) : null, setItem: (k, v) => m.set(k, v), raw: m } }
const tick = () => new Promise(r => setTimeout(r, 0))

// ---- failure reasons are shown on every page that renders a repository ------------------------------
test('a FAILED repository shows the backend reason, with a fallback when none was stored', () => {
    const n = repositoryNotice({ status: 'FAILED', errorMessage: 'No processable code files found. Check if the repo has supported file types.' })
    assert.equal(n.tone, 'error'); assert.match(n.message, /No processable code files/)
    assert.match(repositoryNotice({ status: 'FAILED' }).message, /could not be indexed/)
})
test('a PENDING repository with a stored error is a retry, not a failure', () => {
    const n = repositoryNotice({ status: 'PENDING', errorMessage: 'embedding provider unavailable' })
    assert.equal(n.title, 'Awaiting retry'); assert.equal(n.tone, 'warning'); assert.match(n.message, /embedding provider unavailable/)
})
test('healthy repositories show no notice; a running sync shows progress', () => {
    assert.equal(repositoryNotice({ status: 'READY' }), null)
    assert.equal(repositoryNotice({ status: 'PROCESSING' }), null)
    assert.equal(repositoryNotice({ status: 'READY', syncing: true, syncMessage: 'Fetching' }).message, 'Fetching')
})
test('only queued, processing or syncing repositories and RUNNING tasks count as in progress', () => {
    for (const status of ['PENDING', 'PROCESSING']) assert.ok(isRepoActive({ status }))
    assert.ok(isRepoActive({ status: 'READY', syncing: true }))
    assert.ok(!isRepoActive({ status: 'READY' })); assert.ok(!isRepoActive({ status: 'FAILED' }))
    assert.ok(isTaskActive({ status: 'RUNNING' })); assert.ok(!isTaskActive({ status: 'UNKNOWN' }))
})
test('task links go back to the page that owns the saved result', () => {
    assert.equal(taskLink({ kind: 'CHAT', repoId: 'r1' }), '/chat/r1')
    assert.equal(taskLink({ kind: 'INTERVIEW', repoId: 'r1', resultId: 's9' }), '/interview/r1?sessionId=s9')
    assert.equal(taskLink({ kind: 'DEBUG' }), '/dashboard')
})

// ---- the browser-side journal that survives a crash or restart ----------------------------------------
test('journal round-trips per user and never leaks between accounts', () => {
    const s = memory(), now = Date.now()
    writeJournal(s, 'alice', [{ id: 'a', kind: 'CHAT', repoId: 'r', startedAt: now }])
    assert.equal(readJournal(s, 'alice').length, 1)
    assert.deepEqual(readJournal(s, 'bob'), [])
})
test('journal drops expired, malformed and unknown-kind entries and caps its size', () => {
    const s = memory(), now = Date.now()
    const good = i => ({ id: 'g' + i, kind: 'DEBUG', repoId: 'r', startedAt: now })
    s.setItem('repomind:requests:u', JSON.stringify([
        { id: 'old', kind: 'CHAT', repoId: 'r', startedAt: now - 90000000 },
        { id: 'bad-kind', kind: 'ADMIN', repoId: 'r', startedAt: now },
        { id: 7, kind: 'CHAT', repoId: 'r', startedAt: now }, null, 'junk',
        ...Array.from({ length: 30 }, (_, i) => good(i))]))
    const read = readJournal(s, 'u')
    assert.equal(read.length, 20); assert.ok(read.every(x => x.id.startsWith('g')))
})
test('corrupt or unavailable storage degrades to an empty journal instead of crashing', () => {
    const s = memory(); s.setItem('repomind:requests:u', '{not json')
    assert.deepEqual(readJournal(s, 'u'), [])
    assert.deepEqual(readJournal({ getItem() { throw new Error('blocked') } }, 'u'), [])
    assert.doesNotThrow(() => writeJournal({ setItem() { throw new Error('quota') } }, 'u', []))
})
test('journal stores identifiers only, never prompts or credentials', () => {
    const s = memory()
    writeJournal(s, 'u', [{ id: 'x', kind: 'CHAT', repoId: 'r', startedAt: Date.now() }])
    assert.deepEqual(Object.keys(JSON.parse(s.raw.get('repomind:requests:u'))[0]).sort(), ['id', 'kind', 'repoId', 'startedAt'])
})
test('a request the server never heard of is shown as unconfirmed; one it knows keeps the server status', () => {
    const server = [{ id: 'a', status: 'COMPLETED' }]
    const journal = [{ id: 'a', kind: 'CHAT' }, { id: 'b', kind: 'DEBUG' }]
    const merged = reconcileTasks(server, journal)
    assert.equal(merged.length, 2)
    assert.equal(merged.find(x => x.id === 'a').status, 'COMPLETED')
    const lost = merged.find(x => x.id === 'b'); assert.equal(lost.status, 'UNKNOWN'); assert.ok(lost.localOnly)
})

// ---- polling after a network drop ---------------------------------------------------------------------------
function harness(load, extra = {}) {
    const calls = { data: [], errors: [], scheduled: [] }
    let n = 0
    const poller = createRecoveryPoller({
        load, onData: d => calls.data.push(d), onError: e => calls.errors.push(e.message),
        schedule: (fn, ms) => { calls.scheduled.push(ms); return ++n }, cancel: () => {}, delay: () => 5000, ...extra })
    return { poller, calls }
}
test('after a failed poll it reports the error and keeps polling so it recovers by itself', async () => {
    let up = false
    const { poller, calls } = harness(async () => { if (!up) throw new Error('offline'); return { ok: true } })
    await poller.refresh(); await tick()
    assert.deepEqual(calls.errors, ['offline']); assert.equal(calls.scheduled.length, 1)
    up = true; await poller.refresh(); await tick()
    assert.deepEqual(calls.data, [{ ok: true }]); assert.equal(calls.scheduled.length, 2)
})
test('overlapping refresh requests are coalesced into one follow-up call', async () => {
    let inflight = 0, peak = 0, total = 0
    const { poller } = harness(async () => { total++; inflight++; peak = Math.max(peak, inflight); await tick(); inflight--; return total })
    await Promise.all([poller.refresh(), poller.refresh(), poller.refresh()])
    await tick(); await tick()
    assert.equal(peak, 1); assert.equal(total, 2)
})
test('a response that arrives after stop (logout or account switch) is discarded', async () => {
    let release
    const { poller, calls } = harness(() => new Promise(r => { release = r }))
    const pending = poller.refresh()
    poller.stop(); release({ secret: 'previous user' }); await pending
    assert.deepEqual(calls.data, []); assert.deepEqual(calls.errors, []); assert.equal(calls.scheduled.length, 0)
})
test('a stopped poller never schedules or loads again', async () => {
    let loads = 0
    const { poller, calls } = harness(async () => { loads++; return 1 })
    poller.stop(); await poller.refresh()
    assert.equal(loads, 0); assert.equal(calls.scheduled.length, 0)
})
