// The "Recent Chats" shelf on the repository picker reads this list but nothing ever wrote it, so it stayed empty.
// Most recent first, no duplicates, capped. Only repository ids are stored, never chat content.
export const RECENT_CHAT_KEY = 'recent_chat_repos'

export function readRecentRepos(storage) {
    try {
        const data = JSON.parse(storage.getItem(RECENT_CHAT_KEY) || '[]')
        return Array.isArray(data) ? data.filter(id => typeof id === 'string') : []
    } catch { return [] }
}

export function rememberRecentRepo(storage, repoId, limit = 5) {
    if (typeof repoId !== 'string' || !repoId) return readRecentRepos(storage)
    const next = [repoId, ...readRecentRepos(storage).filter(id => id !== repoId)].slice(0, limit)
    try { storage.setItem(RECENT_CHAT_KEY, JSON.stringify(next)) } catch { /* storage can be disabled */ }
    return next
}
