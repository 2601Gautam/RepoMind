import test from 'node:test'
import assert from 'node:assert/strict'
import { createKeyedStore, createChatSessions, createDebugRuns } from '../src/state/liveStreams.js'

const rateLimit = seconds => Object.assign(new Error('Rate limit exceeded'), { name: 'RateLimitError', retryAfterSeconds: seconds })

// A stream the test drives by hand: nothing happens until push() or end() is called, like a slow model.
function manualStream() {
    let onEvent, onError, finish, calls = []
    const stream = (repoId, text, conversationId, handler, errorHandler, options) => {
        onEvent = handler; onError = errorHandler; calls.push({ repoId, text, conversationId, options })
        return new Promise(resolve => { finish = resolve })
    }
    return { stream, calls, push: event => onEvent(event), fail: error => onError(error), end: () => finish() }
}
const answer = (...words) => words.map(w => ({ type: 'token', content: w }))

test('cleanup from a finished response cannot stop the next question', async () => {
    const calls = []
    const chats = createChatSessions({ stream: (a, b, c, event, error) => new Promise(end => calls.push({ event, error, end })) })
    const first = chats.send('k', { repoId: 'r', text: 'one', requestId: 'one' })
    calls[0].event({ type: 'done' })
    const second = chats.send('k', { repoId: 'r', text: 'two', requestId: 'two' })
    calls[0].error(new Error('late transport error')); calls[0].end(); await first
    assert.equal(chats.get('k').running, true)
    assert.equal(chats.get('k').messages.at(-1).streaming, true)
    calls[1].event({ type: 'token', content: 'second answer' }); calls[1].event({ type: 'done' }); calls[1].end(); await second
    assert.equal(chats.get('k').messages.at(-1).content, 'second answer')
})

test('received answers do not reload history, but a disconnected answer completed on the server does', async () => {
    const m = manualStream(), chats = createChatSessions({ stream: m.stream })
    chats.hydrate('k', null)
    const first = chats.send('k', { repoId: 'r', text: 'first', requestId: 'one' })
    m.push({ type: 'token', content: 'received' }); m.push({ type: 'done' }); m.end(); await first
    assert.equal(chats.needsHistory('k', ['one']), false)
    const second = chats.send('k', { repoId: 'r', text: 'second', requestId: 'two' })
    m.push({ type: 'token', content: 'partial' }); m.fail(new Error('offline')); m.end(); await second
    assert.equal(chats.needsHistory('k', ['one', 'two']), true)
    await chats.restore('k', async () => ({ conversationId: 'c', messages: [{ role: 'assistant', content: 'saved complete answer' }] }), ['one', 'two'])
    assert.equal(chats.get('k').messages[0].content, 'saved complete answer')
    assert.equal(chats.needsHistory('k', ['one', 'two']), false)
    // Another request completes while the page is away; its first poll on return must restore it.
    assert.equal(chats.needsHistory('k', ['one', 'two', 'other-device']), true)
})

test('a late history read cannot erase a newer answer or bring back a cleared conversation', async () => {
    for (const action of ['send', 'clear']) {
        const chats = createChatSessions({ stream: async (a, b, c, event) => { event({ type: 'token', content: 'new answer' }); event({ type: 'done' }) } })
        chats.hydrate('k', null)
        let resolve
        const reading = chats.restore('k', () => new Promise(done => { resolve = done }), ['old'])
        await Promise.resolve()
        if (action === 'send') await chats.send('k', { repoId: 'r', text: 'new', requestId: 'new' })
        else chats.clear('k')
        resolve({ conversationId: 'old', messages: [{ content: 'stale' }] })
        assert.equal(await reading, false)
        assert.equal(chats.get('k').messages.at(-1)?.content, action === 'send' ? 'new answer' : undefined)
    }
})

test('history reads coalesce and failed recovery remains eligible for retry', async () => {
    const chats = createChatSessions({ stream: async () => {} })
    let calls = 0
    const read = async () => { calls++; throw new Error('offline') }
    const first = chats.restore('k', read, ['completed'])
    const second = chats.restore('k', read, ['completed'])
    assert.equal(first, second)
    await assert.rejects(first, /offline/)
    assert.equal(calls, 1)
    assert.equal(chats.needsHistory('k', ['completed']), true)
    await chats.restore('k', async () => null, ['completed'])
    assert.equal(chats.needsHistory('k', ['completed']), false)
})

// ---- the bug: leaving the page cancelled the answer
test('an answer keeps streaming after the page that started it has gone, and is complete when the user returns', async () => {
    const m = manualStream(), chats = createChatSessions({ stream: m.stream })
    chats.hydrate('u:r', null)
    let renders = 0
    const leave = chats.subscribe('u:r', () => renders++)          // the chat page is open
    const done = chats.send('u:r', { repoId: 'r', text: 'How does login work?', requestId: 'q1' })
    m.push({ type: 'start', content: 'conv-1' }); m.push(...answer('The '))
    leave()                                                         // user opens another page: the component unmounts
    const seen = renders
    m.push(...answer('login ')); m.push(...answer('uses JWT.'))
    m.push({ type: 'sources', sources: ['Auth.java'] }); m.push({ type: 'done' })
    await m.end(); await done
    assert.equal(renders, seen)                                      // nobody was listening, nothing broke
    const back = chats.get('u:r')                                    // user returns
    assert.equal(back.running, false)
    assert.deepEqual(back.messages.map(x => x.role), ['user', 'assistant'])
    assert.equal(back.messages[1].content, 'The login uses JWT.')
    assert.deepEqual(back.messages[1].sources, ['Auth.java'])
    assert.equal(back.messages[1].streaming, false)
    assert.equal(back.conversationId, 'conv-1')
})
test('returning mid-answer shows the partial text and the live indicator, then it keeps filling in', async () => {
    const m = manualStream(), chats = createChatSessions({ stream: m.stream })
    const done = chats.send('u:r', { repoId: 'r', text: 'q' })
    m.push({ type: 'token', content: 'Part' })
    const mid = chats.get('u:r')
    assert.equal(mid.running, true); assert.equal(mid.messages[1].content, 'Part'); assert.equal(mid.messages[1].streaming, true)
    let latest = null; chats.subscribe('u:r', () => { latest = chats.get('u:r') })   // page remounts and subscribes
    m.push({ type: 'token', content: 'ial' }); m.push({ type: 'done' }); await m.end(); await done
    assert.equal(latest.messages[1].content, 'Partial'); assert.equal(latest.running, false)
})
test('the stream is not given an abort signal, so route changes cannot cancel it', async () => {
    const m = manualStream(), chats = createChatSessions({ stream: m.stream })
    const done = chats.send('u:r', { repoId: 'r', text: 'q', requestId: 'req-9' })
    assert.equal(m.calls[0].options.signal, undefined); assert.equal(m.calls[0].options.requestId, 'req-9')
    m.push({ type: 'done' }); await m.end(); await done
})

// ---- turn handling
test('a second question is refused while one is streaming and does not start a second request', async () => {
    const m = manualStream(), chats = createChatSessions({ stream: m.stream })
    const first = chats.send('u:r', { repoId: 'r', text: 'one' })
    assert.equal(await chats.send('u:r', { repoId: 'r', text: 'two' }), false)
    assert.equal(m.calls.length, 1); assert.equal(chats.get('u:r').messages.length, 2)
    m.push({ type: 'done' }); await m.end(); await first
})
test('the conversation id from the first answer is reused for the next question', async () => {
    const calls = []
    const chats = createChatSessions({ stream: async (repoId, text, conversationId, onEvent) => {
        calls.push(conversationId); onEvent({ type: 'start', content: 'conv-7' }); onEvent({ type: 'token', content: 'ok' }); onEvent({ type: 'done' }) } })
    await chats.send('u:r', { repoId: 'r', text: 'one' }); await chats.send('u:r', { repoId: 'r', text: 'two' })
    assert.deepEqual(calls, [null, 'conv-7'])
    assert.deepEqual(chats.get('u:r').messages.map(x => x.content), ['one', 'ok', 'two', 'ok'])
})
test('sources are accepted as an array or as the older comma separated text', async () => {
    const run = async event => {
        const chats = createChatSessions({ stream: async (a, b, c, onEvent) => { onEvent(event); onEvent({ type: 'done' }) } })
        await chats.send('k', { repoId: 'r', text: 'q' }); return chats.get('k').messages[1].sources
    }
    assert.deepEqual(await run({ type: 'sources', sources: ['A.java', 'B.java'] }), ['A.java', 'B.java'])
    assert.deepEqual(await run({ type: 'sources', content: 'A.java,B.java' }), ['A.java', 'B.java'])
})

// ---- failures end the run cleanly instead of leaving the page on "thinking"
test('an error event shows the server message and frees the input', async () => {
    const chats = createChatSessions({ stream: async (a, b, c, onEvent) => onEvent({ type: 'error', content: 'Failed to get response. Please try again.' }) })
    await chats.send('k', { repoId: 'r', text: 'q' })
    const s = chats.get('k'); assert.equal(s.running, false)
    assert.equal(s.messages[1].content, 'Failed to get response. Please try again.'); assert.equal(s.messages[1].streaming, false)
})
test('a dropped connection keeps the partial answer and checks for a saved result', async () => {
    const chats = createChatSessions({ stream: async (a, b, c, onEvent, onError) => { onEvent({ type: 'token', content: 'Half an ans' }); onError(new Error('network error')) } })
    await chats.send('k', { repoId: 'r', text: 'q' })
    const s = chats.get('k'); assert.equal(s.running, false)
    assert.match(s.messages[1].content, /^Half an ans\n\nConnection lost\. network error/)
})
test('a stream function that throws is treated as an interrupted response', async () => {
    const chats = createChatSessions({ stream: async () => { throw new Error('boom') } })
    await chats.send('k', { repoId: 'r', text: 'q' })
    assert.equal(chats.get('k').running, false); assert.match(chats.get('k').messages[1].content, /boom/)
})
test('a rate limit removes the empty answer, keeps the question, and records the wait', async () => {
    const chats = createChatSessions({ stream: async (a, b, c, onEvent, onError) => onError(rateLimit(42)) })
    await chats.send('k', { repoId: 'r', text: 'q' })
    const s = chats.get('k'); assert.equal(s.rateLimitSeconds, 42); assert.equal(s.running, false)
    assert.deepEqual(s.messages.map(x => x.role), ['user'])
    chats.dismissRateLimit('k'); assert.equal(chats.get('k').rateLimitSeconds, null)
})
test('a stream that ends without a final event never leaves the page stuck, and the caller is told it settled', async () => {
    let settled = 0
    const chats = createChatSessions({ stream: async (a, b, c, onEvent) => onEvent({ type: 'token', content: 'x' }) })
    await chats.send('k', { repoId: 'r', text: 'q', onSettled: () => settled++ })
    assert.equal(chats.get('k').running, false); assert.equal(chats.get('k').messages[1].streaming, false); assert.equal(settled, 1)
})
test('onSettled runs exactly once for a normal answer', async () => {
    let settled = 0
    const chats = createChatSessions({ stream: async (a, b, c, onEvent) => onEvent({ type: 'done' }) })
    await chats.send('k', { repoId: 'r', text: 'q', onSettled: () => settled++ }); assert.equal(settled, 1)
})

// ---- saved history and the Clear button
test('saved history fills an idle conversation, marks it loaded, and never overwrites a streaming answer', async () => {
    const m = manualStream(), chats = createChatSessions({ stream: m.stream })
    assert.equal(chats.get('k').loaded, false)
    assert.equal(chats.hydrate('k', { conversationId: 'c', messages: [{ role: 'user', content: 'old' }] }), true)
    assert.equal(chats.get('k').loaded, true); assert.equal(chats.get('k').conversationId, 'c')
    const done = chats.send('k', { repoId: 'r', text: 'new' })
    m.push({ type: 'token', content: 'streaming' })
    assert.equal(chats.hydrate('k', { conversationId: 'c', messages: [{ role: 'user', content: 'stale copy' }] }), false)
    assert.equal(chats.get('k').messages.at(-1).content, 'streaming')
    m.push({ type: 'done' }); await m.end(); await done
})
test('no saved history yet is a loaded, empty conversation', () => {
    const chats = createChatSessions({ stream: async () => {} }); chats.hydrate('k', null)
    assert.deepEqual(chats.get('k').messages, []); assert.equal(chats.get('k').loaded, true)
})
test('Clear empties an idle conversation but is refused while an answer is streaming', async () => {
    const m = manualStream(), chats = createChatSessions({ stream: m.stream })
    chats.hydrate('k', { conversationId: 'c', messages: [{ role: 'user', content: 'a' }] })
    const done = chats.send('k', { repoId: 'r', text: 'b' })
    assert.equal(chats.clear('k'), false); assert.equal(chats.get('k').messages.length, 3)
    m.push({ type: 'done' }); await m.end(); await done
    assert.equal(chats.clear('k'), true); assert.deepEqual(chats.get('k').messages, []); assert.equal(chats.get('k').conversationId, null)
})

// ---- isolation and bookkeeping
test('conversations are kept apart per user and repository', async () => {
    const chats = createChatSessions({ stream: async (a, b, c, onEvent) => { onEvent({ type: 'token', content: 'mine' }); onEvent({ type: 'done' }) } })
    await chats.send('alice:r1', { repoId: 'r1', text: 'q' })
    assert.equal(chats.get('alice:r1').messages.length, 2)
    assert.equal(chats.get('bob:r1').messages.length, 0); assert.equal(chats.get('alice:r2').messages.length, 0)
})
test('requests started here are remembered (so their completion is not mistaken for another device), capped at 50', async () => {
    const chats = createChatSessions({ stream: async (a, b, c, onEvent) => onEvent({ type: 'done' }) })
    for (let i = 0; i < 55; i++) await chats.send('k', { repoId: 'r', text: 'q', requestId: 'id' + i })
    const ids = chats.get('k').requestIds
    assert.equal(ids.length, 50); assert.equal(ids.at(-1), 'id54'); assert.ok(ids.includes('id5')); assert.ok(!ids.includes('id4'))
})

// ---- the debug assistant has the same problem and the same fix
function manualDebug() {
    let onEvent, onError, finish; const calls = []
    const stream = (errorText, repoId, additionalContext, handler, errorHandler, options) => {
        onEvent = handler; onError = errorHandler; calls.push({ errorText, repoId, additionalContext, options })
        return new Promise(resolve => { finish = resolve })
    }
    return { stream, calls, push: event => onEvent(event), fail: error => onError(error), end: () => finish() }
}
test('a debug analysis keeps streaming after the page is left, and the form is still filled in on return', async () => {
    const m = manualDebug(), debug = createDebugRuns({ stream: m.stream })
    debug.setInputs('u:r', { errorText: 'NullPointerException at AuthService.java:47', context: 'login with Google' })
    const leave = debug.subscribe('u:r', () => {})
    const done = debug.run('u:r', { repoId: 'r', requestId: 'req-1' })
    m.push({ type: 'token', content: '## Root Cause\nA null ' }); leave()           // page unmounts mid-analysis
    m.push({ type: 'token', content: 'user.' }); m.push({ type: 'sources', files: ['AuthService.java'] }); m.push({ type: 'done' })
    await m.end(); await done
    const back = debug.get('u:r')
    assert.equal(back.running, false); assert.equal(back.done, true)
    assert.equal(back.streamedText, '## Root Cause\nA null user.'); assert.deepEqual(back.sources, ['AuthService.java'])
    assert.equal(back.errorText, 'NullPointerException at AuthService.java:47'); assert.equal(back.context, 'login with Google')
    assert.deepEqual(m.calls[0], { errorText: 'NullPointerException at AuthService.java:47', repoId: 'r', additionalContext: 'login with Google', options: { requestId: 'req-1' } })
})
test('debug refuses an empty form and a second run while one is streaming', async () => {
    const m = manualDebug(), debug = createDebugRuns({ stream: m.stream })
    assert.equal(await debug.run('k', { repoId: 'r' }), false); assert.equal(m.calls.length, 0)
    debug.setInputs('k', { errorText: '   ' }); assert.equal(await debug.run('k', { repoId: 'r' }), false)
    debug.setInputs('k', { errorText: 'boom' })
    const first = debug.run('k', { repoId: 'r' })
    assert.equal(await debug.run('k', { repoId: 'r' }), false); assert.equal(m.calls.length, 1)
    m.push({ type: 'done' }); await m.end(); await first
})
test('an optional context that was left empty is sent as null', async () => {
    const m = manualDebug(), debug = createDebugRuns({ stream: m.stream })
    debug.setInputs('k', { errorText: 'boom' }); const run = debug.run('k', { repoId: 'r' })
    assert.equal(m.calls[0].additionalContext, null); m.push({ type: 'done' }); await m.end(); await run
})
test('debug failures free the form: error event, dropped connection, rate limit and a thrown stream', async () => {
    const run = async behaviour => {
        const debug = createDebugRuns({ stream: behaviour }); debug.setInputs('k', { errorText: 'boom' })
        await debug.run('k', { repoId: 'r' }); return debug.get('k')
    }
    const event = await run(async (a, b, c, onEvent) => onEvent({ type: 'error', content: 'model unavailable' }))
    assert.equal(event.running, false); assert.equal(event.error, 'model unavailable'); assert.equal(event.done, false)
    assert.equal((await run(async (a, b, c, onEvent) => onEvent({ type: 'error' }))).error, 'Analysis failed. Please try again.')
    const dropped = await run(async (a, b, c, onEvent, onError) => { onEvent({ type: 'token', content: 'half' }); onError(new Error('network error')) })
    assert.equal(dropped.running, false); assert.equal(dropped.error, 'network error'); assert.equal(dropped.streamedText, 'half')
    const limited = await run(async (a, b, c, onEvent, onError) => onError(rateLimit(30)))
    assert.equal(limited.running, false); assert.equal(limited.rateLimitSeconds, 30); assert.equal(limited.error, '')
    assert.match((await run(async () => { throw new Error('kaboom') })).error, /kaboom/)
    assert.equal((await run(async (a, b, c, onEvent) => onEvent({ type: 'token', content: 'x' }))).running, false)   // ended with no final event
})
test('Analyze Again clears the result but keeps the form, and is refused while streaming', async () => {
    const m = manualDebug(), debug = createDebugRuns({ stream: m.stream })
    debug.setInputs('k', { errorText: 'boom', context: 'ctx' })
    const run = debug.run('k', { repoId: 'r' }); m.push({ type: 'token', content: 'partial' })
    assert.equal(debug.reset('k'), false); assert.equal(debug.get('k').streamedText, 'partial')
    m.push({ type: 'done' }); await m.end(); await run
    assert.equal(debug.reset('k'), true)
    const after = debug.get('k'); assert.equal(after.streamedText, ''); assert.equal(after.done, false)
    assert.equal(after.errorText, 'boom'); assert.equal(after.context, 'ctx')
    debug.dismissRateLimit('k'); assert.equal(debug.get('k').rateLimitSeconds, null)
})
test('debug results are kept apart per user and repository', async () => {
    const debug = createDebugRuns({ stream: async (a, b, c, onEvent) => { onEvent({ type: 'token', content: 'mine' }); onEvent({ type: 'done' }) } })
    debug.setInputs('alice:r1', { errorText: 'boom' }); await debug.run('alice:r1', { repoId: 'r1' })
    assert.equal(debug.get('alice:r1').streamedText, 'mine')
    assert.equal(debug.get('bob:r1').streamedText, ''); assert.equal(debug.get('alice:r2').errorText, '')
})
test('onSettled runs once per debug run so the activity list is refreshed', async () => {
    let settled = 0
    const debug = createDebugRuns({ stream: async (a, b, c, onEvent) => onEvent({ type: 'done' }) })
    debug.setInputs('k', { errorText: 'boom' }); await debug.run('k', { repoId: 'r', onSettled: () => settled++ }); assert.equal(settled, 1)
})

// ---- what React's useSyncExternalStore needs from the store
test('the same state object is returned until something changes, then a new one; unsubscribed listeners stop being called', async () => {
    const store = createKeyedStore(Object.freeze({ n: 0 }))
    assert.equal(store.get('k'), store.get('k'))
    let calls = 0; const off = store.subscribe('k', () => calls++)
    const first = store.get('k'); store.set('k', { n: 1 })
    assert.notEqual(store.get('k'), first); assert.equal(calls, 1)
    off(); store.set('k', { n: 2 }); assert.equal(calls, 1)
    assert.ok(Object.isFrozen(store.get('k')))
})
test('a listener that unsubscribes while being notified does not break the others', () => {
    const store = createKeyedStore(Object.freeze({ n: 0 })); const seen = []
    const offA = store.subscribe('k', () => { seen.push('a'); offA() }); store.subscribe('k', () => seen.push('b'))
    store.set('k', { n: 1 }); store.set('k', { n: 2 })
    assert.deepEqual(seen, ['a', 'b', 'b'])
})

