export const isRepoActive = repo => ['PENDING', 'PROCESSING'].includes(repo.status) || repo.syncing
export const isTaskActive = task => task.status === 'RUNNING'

export function repositoryNotice(repo) {
    if (repo.status === 'FAILED') return { title: 'Ingestion failed', message: repo.errorMessage || 'This repository could not be indexed.', tone: 'error' }
    if (repo.status === 'PENDING' && repo.errorMessage) return { title: 'Awaiting retry', message: repo.errorMessage + ' The job is retained for retry. If it stays queued, contact support.', tone: 'warning' }
    if (repo.syncing) return { title: 'Sync in progress', message: repo.syncMessage || 'Checking repository changes.', tone: 'info' }
    return null
}

export function taskLink(task) {
    if (!task.repoId) return '/dashboard'
    if (task.kind === 'INTERVIEW') return '/interview/' + task.repoId + (task.resultId ? '?sessionId=' + task.resultId : '')
    return '/' + task.kind.toLowerCase() + '/' + task.repoId
}

// No credentials, prompts or generated content are kept in the recovery journal.
export function readJournal(storage, userId) {
    try {
        const data = JSON.parse(storage.getItem('repomind:requests:' + userId) || '[]')
        return Array.isArray(data) ? data.filter(x => x && typeof x.id === 'string' && ['CHAT','DEBUG','INTERVIEW'].includes(x.kind)
            && typeof x.repoId === 'string' && Number.isFinite(x.startedAt) && Date.now()-x.startedAt < 86400000).slice(-20) : []
    } catch { return [] }
}
export function writeJournal(storage, userId, items) {
    try { storage.setItem('repomind:requests:' + userId, JSON.stringify(items.slice(-20))) } catch { /* storage can be disabled */ }
}
export function reconcileTasks(server, journal) {
    const ids = new Set(server.map(x => x.id))
    return [...server, ...journal.filter(x => !ids.has(x.id)).map(x => ({ ...x, status: 'UNKNOWN', localOnly: true }))]
}

// One request at a time; late responses cannot update an unmounted/account-switched view.
export function createRecoveryPoller({ load, onData, onError, delay = () => 5000,
    schedule = setTimeout, cancel = clearTimeout }) {
    let stopped = false, running = false, again = false, timer
    async function refresh() {
        if (stopped) return
        cancel(timer)
        if (running) { again = true; return }
        running = true
        try { const data = await load(); if (!stopped) onData(data) }
        catch (error) { if (!stopped) onError(error) }
        finally {
            running = false
            if (!stopped) {
                if (again) { again = false; void refresh() }
                else timer = schedule(refresh, delay())
            }
        }
    }
    return { refresh, stop() { stopped = true; cancel(timer) } }
}

