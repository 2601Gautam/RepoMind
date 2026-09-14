import { useState, useEffect, useRef } from 'react'
import { ingestRepo, getRepoStatus, listRepos } from '../api/client'
import RepoUrlInput from '../components/repo/RepoUrlInput'
import IngestionProgress from '../components/repo/IngestionProgress'
import RepoCard from '../components/repo/RepoCard'

// IngestPage owns state and logic only
// All visual rendering delegated to components above
export default function IngestPage({ onSelect }) {
    const [busy, setBusy] = useState(false)
    const [current, setCurrent] = useState(null)
    const [repos, setRepos] = useState([])
    const [error, setError] = useState('')
    const pollRef = useRef(null)

    useEffect(() => {
        listRepos()
            .then(data => {
                const all = Array.isArray(data) ? data : (data.content || [])
                setRepos(all.filter(r => r.status === 'READY'))
                
                const processing = all.find(r => r.status === 'PROCESSING' || r.status === 'PENDING')
                if (processing) {
                    setCurrent(processing)
                    setBusy(true)
                    startPolling(processing.id)
                }
            })
            .catch(() => {})
        return () => { if (pollRef.current) clearInterval(pollRef.current) }
    }, [])

    async function handleSubmit(url, token) {
        setError('')
        setBusy(true)
        setCurrent(null)
        try {
            const repo = await ingestRepo(url, token)
            setCurrent(repo)
            startPolling(repo.id)
        } catch (e) {
            setError(e.message)
            setBusy(false)
        }
    }

    function startPolling(repoId) {
        pollRef.current = setInterval(async () => {
            try {
                const updated = await getRepoStatus(repoId)
                setCurrent(updated)
                if (updated.status === 'READY') {
                    clearInterval(pollRef.current)
                    setBusy(false)
                    setRepos(prev => [updated, ...prev.filter(r => r.id !== updated.id)])
                }
                if (updated.status === 'FAILED') {
                    clearInterval(pollRef.current)
                    setBusy(false)
                    setError(updated.errorMessage || 'Ingestion failed')
                }
            } catch {
                clearInterval(pollRef.current)
                setBusy(false)
                setError('Lost connection to server')
            }
        }, 3000)
    }

    return (
        <div className="max-w-xl mx-auto py-10 space-y-8">
            <div className="space-y-1">
                <h2 className="text-2xl font-semibold text-white">
                    Analyze a Repository
                </h2>
                <p className="text-sm text-gray-400">
                    Paste any public GitHub URL to start chatting with the codebase.
                </p>
            </div>

            <RepoUrlInput onSubmit={handleSubmit} disabled={busy} />

            {error && (
                <div className="animate-fade-up rounded-lg border border-white/[0.08] bg-white/[0.035] px-3.5 py-2.5 text-[12.5px] text-neutral-400">
                    <div className="flex items-start gap-2.5">
                        <svg className="mt-0.5 h-4 w-4 shrink-0 text-amber-300/80" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth="2.25">
                            <path strokeLinecap="round" strokeLinejoin="round" d="M12 9v4m0 4h.01M10.29 3.86 1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z" />
                        </svg>
                        <div className="min-w-0 leading-relaxed">
                            {error}
                        </div>
                    </div>
                </div>
            )}

            <IngestionProgress repo={current} />

            {repos.length > 0 && (
                <div className="space-y-3">
                    <p className="text-xs font-medium text-gray-500 uppercase tracking-wider">
                        Ready to Chat
                    </p>
                    {repos.map(repo => (
                        <RepoCard key={repo.id} repo={repo} onClick={onSelect} />
                    ))}
                </div>
            )}
        </div>
    )
}
