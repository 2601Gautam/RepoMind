// All API calls live in one file
// Reason: if backend URL changes or you add auth headers later,
// you change this one file and every page benefits automatically
// Never write fetch() calls directly inside components

// In development: /api proxied to localhost:8080 by Vite
// In production: /api goes to same domain as frontend
// VITE_API_URL is set on Render as environment variable

//  Central API client — all calls go through here
// credentials: 'include' on every request sends the httpOnly JWT cookie
// 401 → redirect to login, 429 → throw RateLimitError with countdown seconds
export const BASE = import.meta.env.VITE_API_URL
    ? `${import.meta.env.VITE_API_URL}/api`
    : '/api';


// Custom error for rate limiting
// Carries retryAfterSeconds so UI can show a countdown timer
export class RateLimitError extends Error {
    constructor(retryAfterSeconds) {
        super(`Rate limit exceeded. Retry in ${retryAfterSeconds}s`)
        this.name = 'RateLimitError'
        this.retryAfterSeconds = retryAfterSeconds
    }
}
//Helper that adds credentials to every request
// DRY principle - define once, use everywhere
async function apiFetch(url, options={}){
    
    const res = await fetch(url,{
        ...options,
        credentials: 'include',
        headers: {
            'Content-Type': 'application/json',
            ...options.headers,
        }
    })


    if(res.status == 401){
        // Only redirect if this isn't the /auth/me check itself
        // /auth/me returning 401 just means not logged in — not an error
        if (!url.includes('/auth/me')) {
            window.location.href = '/login'
            return
        }
        throw new Error('Unauthorized')
    }

    if(res.status == 429){
        const data = await res.json().catch(() => ({}))
        throw new RateLimitError(data.retryAfterSeconds || 60)
    }

    if (!res.ok) {
        const text = await res.text()
        let message = `Request failed: ${res.status}`
        try {
            const data = JSON.parse(text)
            message = data.message || data.error || message
        } catch { }
        throw new Error(message)
    }

    // Handle empty responses (like 204 No Content)
    // Without this, res.json() throws on empty body
    const text = await res.text()
    return text ? JSON.parse(text) : null

}
// ── Auth ──────────────────────────────────────────────────────────────────

export const getMe = () => apiFetch(`${BASE}/auth/me`)

export const getProfileStats = () => apiFetch(`${BASE}/auth/profile/stats`)

//------Repos---------------------------------------

// Backend now returns PagedResponse: {content:[], page:0, totalPages:N, ...}
export const listRepos = (page = 0, size = 12) =>
    apiFetch(`${BASE}/repos?page=${page}&size=${size}`)

export const getRepoStatus = (repoId) =>
    apiFetch(`${BASE}/repos/${repoId}/status`)

export const deleteRepo = (repoId) =>
    apiFetch(`${BASE}/repos/${repoId}`, { method: 'DELETE' })

export const ingestRepo = (githubUrl, token = null) =>
    apiFetch(`${BASE}/repos/ingest`, {
        method: 'POST',
        body: JSON.stringify({ githubUrl, token })
    })

// Returns {conversationId, messages:[{role,content,sources}]} or null if no history yet
export const getChatHistory = async (repoId) => {
    const res = await fetch(`${BASE}/chat/history/${repoId}`, {
        credentials: 'include'
    })
    if (res.status === 204 || res.status === 404) return null   // no history yet
    if (res.status === 401) { window.location.href = '/login'; return null }
    if (!res.ok) throw new Error(`Failed to load chat history: ${res.status}`)
    return res.json()
}

export const clearChatConversation = (conversationId) =>
    apiFetch(`${BASE}/chat/conversations/${conversationId}`, {
        method: 'DELETE'
    })

// ── Interview ──────────────────────────────────────────────────────────────

export const generateInterview = (repoId, difficulty) =>
    apiFetch(`${BASE}/interview/generate`, {
        method: 'POST',
        body: JSON.stringify({ repoId, difficulty })
    })

export const getInterviewSessions = () =>
    apiFetch(`${BASE}/interview/sessions`)

export const getInterviewSession = (sessionId) =>
    apiFetch(`${BASE}/interview/sessions/${sessionId}`)



// ── SSE Streaming — Chat ──────────────────────────────────────────────────
//
// Chat SSE event format from backend (ChatService.java):
//   {"type":"start","content":"<conversationId>","conversationId":"<uuid>"}
//   {"type":"token","content":"word","conversationId":"<uuid>"}
//   {"type":"sources","sources":"file1.java,file2.java","conversationId":"<uuid>"}
//   {"type":"done","content":"","conversationId":"<uuid>"}
//   {"type":"error","content":"message","conversationId":"<uuid>"}
//
// NOTE: sources event sends "sources" as comma-separated STRING not array
// Parse with: event.sources.split(',') — see ChatPage.jsx
//
// onEvent(eventObj) called for each parsed event
// onError(Error) called if stream fails
export async function streamChat(repoId, message, conversationId, onEvent, onError) {
    try {
        const res = await fetch(`${BASE}/chat`, {
            method: 'POST',
            credentials: 'include',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                repoId,
                message,
                conversationId: conversationId || null
            })
        })

        if (res.status === 429) {
            const data = await res.json().catch(() => ({}))
            onError(new RateLimitError(data.retryAfterSeconds || 60))
            return
        }

        if (!res.ok) {
            onError(new Error(`Chat failed: ${res.status}`))
            return
        }

        await readSseStream(res.body, onEvent)

    } catch (err) {
        onError(err)
    }
}
// ── SSE Streaming — Debug ─────────────────────────────────────────────────
//
// Debug SSE event format from backend (DebugService.java):
//   {"type":"start","content":""}
//   {"type":"token","content":"word"}
//   {"type":"sources","files":["file1.java","file2.java"]}   ← array not string
//   {"type":"done","content":""}
//   {"type":"error","content":"message"}
//
// NOTE: sources event uses "files" key (not "sources") and is a JSON array
// Different from chat SSE format — check DebugPage.jsx for handling
export async function streamDebug(errorText, repoId, additionalContext, onEvent, onError) {
    try {
        const res = await fetch(`${BASE}/debug/analyze`, {
            method: 'POST',
            credentials: 'include',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                errorText,
                repoId: repoId || null,
                additionalContext: additionalContext || null
            })
        })

        if (res.status === 429) {
            const data = await res.json().catch(() => ({}))
            onError(new RateLimitError(data.retryAfterSeconds || 60))
            return
        }

        if (!res.ok) {
            onError(new Error(`Debug analysis failed: ${res.status}`))
            return
        }

        await readSseStream(res.body, onEvent)

    } catch (err) {
        onError(err)
    }
}

// ── SSE Core Reader ───────────────────────────────────────────────────────
//
// Reads a ReadableStream byte by byte, decodes to text, splits on newlines
// Each line starting with "data:" is parsed as JSON and passed to onEvent
// The stream closes when a "done" event is received or the stream ends
//
// Why fetch + ReadableStream instead of EventSource:
//   EventSource only supports GET requests — our endpoints are POST
//   EventSource doesn't send cookies properly cross-origin in all browsers
//   fetch + ReadableStream gives us full control and works everywhere
async function readSseStream(body, onEvent) {
    const reader = body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''

    try {
        while (true) {
            const { done, value } = await reader.read()
            if (done) break

            // Decode incoming bytes and append to buffer
            // { stream: true } handles multi-byte characters split across chunks
            buffer += decoder.decode(value, { stream: true })

            // Split buffer on newlines — SSE events end with \n
            const lines = buffer.split('\n')

            // Last element may be an incomplete line — keep it in buffer
            buffer = lines.pop() || ''

            for (const line of lines) {
                const trimmed = line.trim()

                // SSE data lines start with "data:"
                // Empty lines are event separators — skip them
                if (!trimmed || !trimmed.startsWith('data:')) continue

                const jsonStr = trimmed.slice(5).trim()
                if (!jsonStr) continue

                try {
                    const event = JSON.parse(jsonStr)
                    onEvent(event)

                    // Stop reading when stream signals completion
                    if (event.type === 'done' || event.type === 'error') {
                        reader.cancel()
                        return
                    }
                } catch {
                    // Malformed JSON in one event — skip it, keep reading
                    // This can happen with partial chunks, just ignore
                }
            }
        }
    } finally {
        // Always release the lock even if an error occurred
        reader.releaseLock()
    }
}