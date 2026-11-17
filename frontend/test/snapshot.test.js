import test from 'node:test'
import assert from 'node:assert/strict'
import { loadSnapshot, pollDelay, userIdOf } from '../src/state/recovery.js'
import { rememberRecentRepo, readRecentRepos, RECENT_CHAT_KEY } from '../src/state/recentRepos.js'

const memory = () => { const m = new Map(); return { getItem: k => m.has(k) ? m.get(k) : null, setItem: (k, v) => m.set(k, v) } }
const httpError = status => Object.assign(new Error('Request failed: ' + status), { status })
const repo = (id, status, extra = {}) => ({ id, status, totalFiles: 0, processedFiles: 0, ...extra })

// ---- older or partly migrated backend must not blank the Repositories page, dashboard and progress bars
test('a working activity endpoint is used as is', async () => {
    const snapshot = { repositories: [repo('a', 'READY')], tasks: [{ id: 't', status: 'RUNNING' }] }
    let listed = false
    const got = await loadSnapshot({ getActivity: async () => snapshot, listRepos: async () => { listed = true; return [] }, getRepoStatus: async () => ({}) })
    assert.equal(got, snapshot); assert.equal(listed, false)
})
test('a backend without the activity endpoint (404) falls back to the repository list', async () => {
    const got = await loadSnapshot({ getActivity: async () => { throw httpError(404) },
        listRepos: async () => [repo('a', 'READY'), repo('b', 'FAILED', { errorMessage: 'No processable code files found.' })], getRepoStatus: async () => ({}) })
    assert.equal(got.repositories.length, 2); assert.deepEqual(got.tasks, []); assert.equal(got.degraded, true)
    assert.equal(got.repositories[1].errorMessage, 'No processable code files found.')
})
test('a failing activity endpoint (500, for example a missing table) also falls back', async () => {
    const got = await loadSnapshot({ getActivity: async () => { throw httpError(500) }, listRepos: async () => [repo('a', 'READY')], getRepoStatus: async () => ({}) })
    assert.equal(got.repositories.length, 1); assert.equal(got.degraded, true)
})
test('running jobs are refreshed from the uncached status endpoint so the percentage is current', async () => {
    const got = await loadSnapshot({ getActivity: async () => { throw httpError(404) },
        listRepos: async () => [repo('run', 'PROCESSING', { totalFiles: 20, processedFiles: 2 }), repo('ok', 'READY')],
        getRepoStatus: async id => repo(id, 'PROCESSING', { totalFiles: 20, processedFiles: 10 }) })
    assert.equal(got.repositories[0].processedFiles, 10)
    assert.equal(got.repositories[1].status, 'READY')
})
test('if the status refresh fails the listed job is kept rather than dropped', async () => {
    const got = await loadSnapshot({ getActivity: async () => { throw httpError(404) },
        listRepos: async () => [repo('run', 'PROCESSING', { totalFiles: 20, processedFiles: 2 })], getRepoStatus: async () => { throw new Error('boom') } })
    assert.equal(got.repositories[0].processedFiles, 2)
})
test('offline, expired session and rate limit errors are reported, not hidden by the fallback', async () => {
    for (const error of [new TypeError('Failed to fetch'), httpError(401), Object.assign(new Error('slow down'), { name: 'RateLimitError' }), httpError(400)]) {
        let listed = false
        await assert.rejects(loadSnapshot({ getActivity: async () => { throw error }, listRepos: async () => { listed = true; return [] }, getRepoStatus: async () => ({}) }), e => e === error)
        assert.equal(listed, false)
    }
})
test('if the fallback also fails the error surfaces', async () => {
    await assert.rejects(loadSnapshot({ getActivity: async () => { throw httpError(404) }, listRepos: async () => { throw new TypeError('Failed to fetch') }, getRepoStatus: async () => ({}) }), /Failed to fetch/)
})

// ---- progress bars move at a useful speed
test('polling is fastest while an ingestion runs and slow when idle or hidden', () => {
    const idle = { repositories: [repo('a', 'READY')], tasks: [] }
    assert.equal(pollDelay({ hidden: false, snapshot: { repositories: [repo('a', 'PROCESSING')], tasks: [] }, journalLength: 0 }), 3000)
    assert.equal(pollDelay({ hidden: false, snapshot: { repositories: [repo('a', 'PENDING')], tasks: [] }, journalLength: 0 }), 3000)
    assert.equal(pollDelay({ hidden: false, snapshot: { repositories: [], tasks: [{ status: 'RUNNING' }] }, journalLength: 0 }), 5000)
    assert.equal(pollDelay({ hidden: false, snapshot: idle, journalLength: 1 }), 5000)
    assert.equal(pollDelay({ hidden: false, snapshot: idle, journalLength: 0 }), 30000)
    assert.equal(pollDelay({ hidden: true, snapshot: { repositories: [repo('a', 'PROCESSING')], tasks: [] }, journalLength: 0 }), 30000)
})

// ---- the Recent Chats shelf finally has something to read
test('recent chat repositories are most recent first, without duplicates, and capped', () => {
    const s = memory()
    for (const id of ['a', 'b', 'c', 'a', 'd', 'e', 'f']) rememberRecentRepo(s, id)
    assert.deepEqual(readRecentRepos(s), ['f', 'e', 'd', 'a', 'c'])
})
test('recent chat storage tolerates corrupt data, bad ids and disabled storage', () => {
    const s = memory(); s.setItem(RECENT_CHAT_KEY, '{not json')
    assert.deepEqual(rememberRecentRepo(s, 'a'), ['a'])
    s.setItem(RECENT_CHAT_KEY, JSON.stringify(['x', 7, null, 'y']))
    assert.deepEqual(readRecentRepos(s), ['x', 'y'])
    assert.deepEqual(rememberRecentRepo(s, undefined), ['x', 'y'])
    assert.doesNotThrow(() => rememberRecentRepo({ getItem() { throw new Error('blocked') }, setItem() { throw new Error('quota') } }, 'a'))
})

// ---- the bug that left every repository page empty: the server says userId, not id
test('the id is read from the real /api/auth/me shape (userId) and from an id field', () => {
    assert.equal(userIdOf({ userId: 'u-1', email: 'a@b.c', name: 'A', provider: 'LOCAL' }), 'u-1')
    assert.equal(userIdOf({ id: 'u-2' }), 'u-2')
    assert.equal(userIdOf({ userId: 'u-3', id: 'u-4' }), 'u-3')
})
test('a missing or malformed user yields no id instead of a bad storage key', () => {
    for (const bad of [null, undefined, {}, { userId: 7 }, { userId: '' }, { id: null }, 'u-1']) assert.equal(userIdOf(bad), null)
})
