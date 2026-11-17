import { createContext, useContext, useEffect, useRef, useState, useCallback } from 'react'
import { useAuth } from './AuthContext'
import { getActivity } from '../api/client'
import { createRecoveryPoller, isRepoActive, isTaskActive, readJournal, writeJournal, reconcileTasks } from '../state/recovery.js'

const empty = { repositories: [], tasks: [], loading: false, error: '', refresh() {}, begin() {}, forget() {} }
const ActivityContext = createContext(empty)

function ActivityScope({ userId, children }) {
    const [snapshot, setSnapshot] = useState({ repositories: [], tasks: [] })
    const [loading, setLoading] = useState(true)
    const [error, setError] = useState('')
    const [journal, setJournal] = useState(() => readJournal(localStorage,userId))
    const journalRef = useRef(journal)
    const stateRef = useRef(snapshot)
    const poller = useRef(null)
    useEffect(() => {
        const control = createRecoveryPoller({
            load: () => {
                if (!navigator.onLine) throw new Error('You are offline. Showing the last known status.')
                return getActivity()
            },
            onData: data => { stateRef.current = data; setSnapshot(data); setError(''); setLoading(false) },
            onError: () => { setError('Connection lost. Status may be out of date; checking again automatically.'); setLoading(false) },
            delay: () => document.hidden ? 30000 :
                stateRef.current.repositories.some(isRepoActive) || stateRef.current.tasks.some(isTaskActive) || journalRef.current.length ? 5000 : 30000
        })
        poller.current = control
        const wake = () => control.refresh()
        const visible = () => { if (!document.hidden) wake() }
        window.addEventListener('online',wake)
        window.addEventListener('offline',wake)
        window.addEventListener('focus',wake)
        document.addEventListener('visibilitychange',visible)
        wake()
        return () => {
            control.stop()
            window.removeEventListener('online',wake)
            window.removeEventListener('offline',wake)
            window.removeEventListener('focus',wake)
            document.removeEventListener('visibilitychange',visible)
        }
    }, [userId])
    const refresh = useCallback(() => poller.current?.refresh(), [])
    const forget = useCallback(id => {
        const next = journalRef.current.filter(x => x.id !== id)
        journalRef.current = next; setJournal(next); writeJournal(localStorage,userId,next)
    }, [userId])
    const begin = useCallback((kind,repoId) => {
        const requestId = crypto.randomUUID()
        const next = [...journalRef.current,{ id: requestId, kind, repoId, startedAt: Date.now() }].slice(-20)
        journalRef.current = next; setJournal(next); writeJournal(localStorage,userId,next)
        return requestId
    }, [userId])
    // Completed server records no longer need a browser-only marker.
    useEffect(() => {
        const terminal = new Set(snapshot.tasks.filter(t => t.status !== 'RUNNING' && t.status !== 'UNKNOWN').map(t=>t.id))
        const next = journalRef.current.filter(t=>!terminal.has(t.id))
        if (next.length !== journalRef.current.length) {
            journalRef.current=next; setJournal(next); writeJournal(localStorage,userId,next)
        }
    }, [snapshot.tasks,userId])
    return <ActivityContext.Provider value={{ ...snapshot, tasks: reconcileTasks(snapshot.tasks,journal), loading, error, refresh, begin, forget }}>{children}</ActivityContext.Provider>
}
export function ActivityProvider({ children }) {
    const { user } = useAuth()
    return user?.id ? <ActivityScope key={user.id} userId={user.id}>{children}</ActivityScope> :
        <ActivityContext.Provider value={empty}>{children}</ActivityContext.Provider>
}
// eslint-disable-next-line react-refresh/only-export-components
export const useActivity = () => useContext(ActivityContext)

