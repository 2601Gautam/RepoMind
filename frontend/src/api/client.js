import { readSseStream } from './readSseStream.js'

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
            throw Object.assign(new Error('Your session expired. Please sign in again.'), { status: 401 })
        }
        throw Object.assign(new Error('Unauthorized'), { status: 401 })
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
            message = data.message || data.error || data.syncMessage || message
        } catch {
            // Plain-text and empty error responses have no structured message.
        }

        // The ingestion endpoint intentionally returns an empty 403 when
        // GitHub cannot verify access to a private repository. Give that flow
        // a useful explanation without changing other forbidden responses.
        if (res.status === 403 && url.endsWith('/repos/ingest') && message === `Request failed: ${res.status}`) {
            message = 'GitHub could not access this repository. Check the URL and use a personal access token that has permission to access it.'
        }
        throw new Error(message)
    }

    // Handle empty responses (like 204 No Content)
    // Without this, res.json() throws on empty body
    const text = await res.text()
    return text ? JSON.parse(text) : null

}
// ── Auth ──────────────────────────────────────────────────────────────────

export const getMe = () => apiFetch(`${BASE}/auth/me`)
export const getActivity = () => apiFetch(`${BASE}/activity`)

export const getProfileStats = () => apiFetch(`${BASE}/auth/profile/stats`)

//------Repos---------------------------------------

// Backend now returns PagedResponse: {content:[], page:0, totalPages:N, ...}
export const listRepos = () => apiFetch(`${BASE}/repos`)


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

export const generateInterview = (repoId, difficulty, options = {}) =>
    apiFetch(`${BASE}/interview/generate`, {
        method: 'POST',
        signal: options.signal,
        headers: options.requestId ? { 'X-Request-ID': options.requestId } : {},
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
//   {"type":"sources","sources":["file1.java","file2.java"],"conversationId":"<uuid>"}
//   {"type":"done","content":"","conversationId":"<uuid>"}
//   {"type":"error","content":"message","conversationId":"<uuid>"}
//
// Current clients use the structured sources array. Older clients can still read content.
//
// onEvent(eventObj) called for each parsed event
// onError(Error) called if stream fails
export async function streamChat(repoId, message, conversationId, onEvent, onError, options = {}) {
    try {
        const res = await fetch(`${BASE}/chat`, {
            signal: options.signal,
            method: 'POST',
            credentials: 'include',
            headers: { 'Content-Type': 'application/json', ...(options.requestId ? { 'X-Request-ID': options.requestId } : {}) },
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
            onError(new Error('The AI service is temporarily unavailable. Please try again in a few moments.'))
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
export async function streamDebug(errorText, repoId, additionalContext, onEvent, onError, options = {}) {
    try {
        const res = await fetch(`${BASE}/debug/analyze`, {
            signal: options.signal,
            method: 'POST',
            credentials: 'include',
            headers: { 'Content-Type': 'application/json', ...(options.requestId ? { 'X-Request-ID': options.requestId } : {}) },
            body: JSON.stringify({
                errorText,
                repoId: repoId,
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

export const syncRepo = (repoId, token = null) =>
    apiFetch(`${BASE}/repos/${repoId}/sync`, {
        method: 'POST',
        body: JSON.stringify({ token })
    })

// Returns {downloadUrl, expiresInMinutes}. The URL is presigned and expires
// in 15 minutes, so callers should navigate to it immediately after this
// resolves rather than storing/reusing it.
export const getRepoArchive = (repoId) =>
    apiFetch(`${BASE}/repos/${repoId}/archive`)
