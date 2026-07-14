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
                <p className="text-sm text-red-400">{error}</p>
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