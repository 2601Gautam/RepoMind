// All API calls live in one file
// Reason: if backend URL changes or you add auth headers later,
// you change this one file and every page benefits automatically
// Never write fetch() calls directly inside components

// In development: /api proxied to localhost:8080 by Vite
// In production: /api goes to same domain as frontend
// VITE_API_URL is set on Render as environment variable
const BASE = import.meta.env.VITE_API_URL
    ? `${import.meta.env.VITE_API_URL}/api`
    : '/api';

// Submits a GitHub URL for ingestion
// Returns the repo object with id and status PENDING
export async function ingestRepo(githubUrl, token = null) {
    const res = await fetch(`${BASE}/repos/ingest`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ githubUrl, token })
    });
    if (!res.ok) {
        const text = await res.text();
        throw new Error(text || `Request failed: ${res.status}`);
    }
    return res.json();
}

// Polls this every 3 seconds to track ingestion progress
export async function getRepoStatus(repoId) {
    const res = await fetch(`${BASE}/repos/${repoId}/status`);
    if (!res.ok) throw new Error('Failed to fetch status');
    return res.json();
}

// Returns all repos ever submitted — used to show the list on home page
export async function listRepos() {
    const res = await fetch(`${BASE}/repos`);
    if (!res.ok) throw new Error('Failed to list repos');
    return res.json();
}

// Sends a chat message
// conversationId is null on first message, then you get one back and send it each time
// This groups messages into one conversation in the DB
export async function sendMessage(repoId, message, conversationId = null) {
    const res = await fetch(`${BASE}/chat`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ repoId, message, conversationId })
    });
    if (!res.ok) throw new Error('Chat request failed');
    return res.json();
}