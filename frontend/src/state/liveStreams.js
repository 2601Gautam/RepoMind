// Conversations that must keep running while the user is on another page.
//
// React pages are created and destroyed with the route. These stores are not, so a chat answer keeps streaming
// from the server while the user looks at the dashboard, and the finished answer is already there when they come back.
// Nothing here touches the network directly: the stream function is passed in, which keeps the logic testable.

export function createKeyedStore(initial) {
    const states = new Map()
    const listeners = new Map()
    const get = key => states.get(key) ?? initial
    function set(key, change) {
        const current = get(key)
        const next = Object.freeze({ ...current, ...(typeof change === 'function' ? change(current) : change) })
        states.set(key, next)
        for (const listener of [...(listeners.get(key) ?? [])]) listener()
        return next
    }
    function subscribe(key, listener) {
        if (!listeners.has(key)) listeners.set(key, new Set())
        listeners.get(key).add(listener)
        return () => { listeners.get(key)?.delete(listener); if (!listeners.get(key)?.size) listeners.delete(key) }
    }
    return { get, set, subscribe }
}

const IDLE_CHAT = Object.freeze({
    messages: [], conversationId: null,
    loaded: false,            // saved history has been read at least once in this browser session
    running: false,           // an answer is streaming right now
    rateLimitSeconds: null,
    requestIds: [],
    syncedRequestIds: [],     // answers actually received or restored, not merely started here
    revision: 0
})

const isRateLimit = error => error?.name === 'RateLimitError'

export function createChatSessions({ stream }) {
    const store = createKeyedStore(IDLE_CHAT)
    const restores = new Map()
    let counter = 0

    // Coalesce page/activity reads, and discard a saved snapshot if a new question or clear happened during the read.
    function restore(key, readHistory, completedIds = []) {
        if (restores.has(key)) return restores.get(key)
        const before = store.get(key)
        if (before.running) return Promise.resolve(false)
        const pending = Promise.resolve().then(readHistory).then(history => {
            if (store.get(key).revision !== before.revision || !hydrate(key, history)) return false
            store.set(key, state => ({ syncedRequestIds: [...new Set([...state.syncedRequestIds, ...completedIds])].slice(-100) }))
            return true
        }).finally(() => restores.delete(key))
        restores.set(key, pending)
        return pending
    }

    const needsHistory = (key, completedIds) => {
        const state = store.get(key)
        return !state.running && (!state.loaded || completedIds.some(id => !state.syncedRequestIds.includes(id)))
    }

    // Saved history never replaces an answer that is still arriving.
    function hydrate(key, history) {
        if (store.get(key).running) return false
        store.set(key, { messages: history?.messages ?? [], conversationId: history?.conversationId ?? null, loaded: true })
        return true
    }
    function clear(key) {
        if (store.get(key).running) return false
        store.set(key, state => ({ messages: [], conversationId: null, revision: state.revision + 1 }))
        return true
    }
    const dismissRateLimit = key => { store.set(key, { rateLimitSeconds: null }) }

    // Starts one question. The returned promise settles when the stream ends, whether or not any page is still watching.
    async function send(key, { repoId, text, requestId, onSettled }) {
        const before = store.get(key)
        if (before.running) return false
        const id = ++counter
        store.set(key, state => ({
            running: true, rateLimitSeconds: null, revision: state.revision + 1,
            requestIds: requestId ? [...state.requestIds, requestId].slice(-50) : state.requestIds,
            messages: [...state.messages, { role: 'user', content: text }, { id, role: 'assistant', content: '', sources: [], streaming: true }]
        }))
        const patch = (change, extra = {}) => store.set(key, state => ({
            ...extra, messages: state.messages.map(message => message.id === id ? { ...message, ...(typeof change === 'function' ? change(message) : change) } : message)
        }))
        let conversationId = before.conversationId
        let terminal = false
        const finish = (change, extra = {}) => {
            terminal = true
            patch(change, { running: false, ...extra })
        }

        const fail = error => {
            if (terminal) return
            if (isRateLimit(error)) {
                terminal = true
                store.set(key, state => ({ running: false, rateLimitSeconds: error.retryAfterSeconds, messages: state.messages.filter(message => message.id !== id) }))
            } else {
                finish(message => ({ content: message.content + '\n\nConnection lost. ' + error.message + ' The answer may still be running; checking saved results automatically.', streaming: false }))
            }
        }

        try {
            await stream(repoId, text, conversationId, event => {
                if (terminal) return
                switch (event.type) {
                    case 'start':
                        if (event.content && !conversationId) { conversationId = event.content; store.set(key, { conversationId }) }
                        break
                    case 'token':
                        patch(message => ({ content: message.content + event.content }))
                        break
                    case 'sources': {
                        const raw = event.sources || event.content || ''
                        patch({ sources: Array.isArray(raw) ? raw : raw.split(',').filter(Boolean) })
                        break
                    }
                    case 'done':
                        finish({ streaming: false }, { syncedRequestIds: requestId
                            ? [...new Set([...store.get(key).syncedRequestIds, requestId])].slice(-100)
                            : store.get(key).syncedRequestIds })
                        break
                    case 'error':
                        finish({ content: event.content || 'Something went wrong', streaming: false })
                        break
                }
            }, fail, { requestId })
        } catch (error) {
            fail(error)   // a stream function that throws must not leave the page stuck in "thinking"
        } finally {
            // A stream that ended without a final event must never leave the page stuck in "thinking".
            if (!terminal) fail(new Error('The response stream ended before completion.'))
            onSettled?.()
        }
        return true
    }

    return { ...store, hydrate, restore, needsHistory, clear, dismissRateLimit, send }
}

const IDLE_DEBUG = Object.freeze({
    errorText: '', context: '',     // the form, kept so it is still filled in when the user comes back
    streamedText: '', sources: [], done: false, error: '', rateLimitSeconds: null,
    running: false
})

// The debug assistant has no saved history on the server, so the analysis only exists in the browser. Keeping the stream
// here (instead of in the page) is what lets it finish while the user looks at another page.
export function createDebugRuns({ stream }) {
    const store = createKeyedStore(IDLE_DEBUG)
    const setInputs = (key, inputs) => { store.set(key, inputs) }
    const dismissRateLimit = key => { store.set(key, { rateLimitSeconds: null }) }
    // "Analyze Again" clears the finished result and keeps the form.
    function reset(key) {
        if (store.get(key).running) return false
        store.set(key, { streamedText: '', sources: [], done: false, error: '' })
        return true
    }

    async function run(key, { repoId, requestId, onSettled }) {
        const form = store.get(key)
        if (form.running || !form.errorText.trim()) return false
        store.set(key, { running: true, error: '', streamedText: '', sources: [], done: false, rateLimitSeconds: null })
        const fail = error => {
            if (isRateLimit(error)) store.set(key, { running: false, rateLimitSeconds: error.retryAfterSeconds })
            else store.set(key, { running: false, error: error.message })
        }
        try {
            await stream(form.errorText, repoId, form.context || null, event => {
                switch (event.type) {
                    case 'token':
                        store.set(key, state => ({ streamedText: state.streamedText + event.content }))
                        break
                    case 'sources':
                        // The debug stream names this key "files", unlike chat.
                        store.set(key, { sources: event.files || [] })
                        break
                    case 'done':
                        store.set(key, { running: false, done: true })
                        break
                    case 'error':
                        store.set(key, { running: false, error: event.content || 'Analysis failed. Please try again.' })
                        break
                }
            }, fail, { requestId })
        } catch (error) {
            fail(error)
        } finally {
            if (store.get(key).running) store.set(key, { running: false })
            onSettled?.()
        }
        return true
    }

    return { ...store, setInputs, dismissRateLimit, reset, run }
}
