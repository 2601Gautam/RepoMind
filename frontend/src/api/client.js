// All API calls live in one file
// Reason: if backend URL changes or you add auth headers later,
// you change this one file and every page benefits automatically
// Never write fetch() calls directly inside components

// In development: /api proxied to localhost:8080 by Vite
// In production: /api goes to same domain as frontend
// VITE_API_URL is set on Render as environment variable
export const BASE = import.meta.env.VITE_API_URL
    ? `${import.meta.env.VITE_API_URL}/api`
    : '/api';

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
    if(!res.ok){
        if(res.status === 401){
            if (url.endsWith('/auth/me')) {
                throw new Error('Unauthorized');
            }
            window.location.href = '/';
            return;
        }
        const text = await res.text()
        throw new Error(text || `Request failed: ${res.status}`)
    }
    // Handle empty responses (like 204 No Content)
    // Without this, res.json() throws on empty body
    const text = await res.text()
    return text ? JSON.parse(text) : null

}

// Submits a GitHub URL for ingestion
// Returns the repo object with id and status PENDING
export const ingestRepo = (githubUrl, token = null) =>
    apiFetch(`${BASE}/repos/ingest`, {
        method: 'POST',
        body: JSON.stringify({ githubUrl, token })
    })

// Polls this every 3 seconds to track ingestion progress
export const getRepoStatus = (repoId) =>
    apiFetch(`${BASE}/repos/${repoId}/status`)

// Returns all repos ever submitted — used to show the list on home page
export const listRepos = () =>
    apiFetch(`${BASE}/repos`)

// Sends a chat message
// conversationId is null on first message, then you get one back and send it each time
// This groups messages into one conversation in the DB
export const sendMessage = (repoId, message, conversationId = null) =>
    apiFetch(`${BASE}/chat`,{
        method: 'POST',
        body: JSON.stringify({repoId,message,conversationId})
    })

export const getMe = () => apiFetch(`${BASE}/auth/me`)

export const generateInterview = (repoId, difficulty) =>
    apiFetch(`${BASE}/interview/generate`, {
        method: 'POST',
        body: JSON.stringify({ repoId, difficulty })
    })

export const getInterviewSessions = () =>
    apiFetch(`${BASE}/interview/sessions`)
export const analyzeError = (errorText, repoId = null, additionalContext = null) =>
    apiFetch(`${BASE}/debug/analyze`, {
        method: "POST",
        body: JSON.stringify({
            errorText,
            repoId,
            additionalContext
        })
    });
