import { useState, useEffect, useRef } from 'react'
import { ingestRepo, getRepoStatus, listRepos } from '../api/client'

export default function IngestPage({ onSelect }) {
    const [url, setUrl] = useState('')
    const [busy, setBusy] = useState(false)
    const [current, setCurrent] = useState(null)  // repo currently being processed
    const [repos, setRepos] = useState([])         // all READY repos for the list
    const [error, setError] = useState('')
    // useRef stores the interval ID so we can clear it
    // If we stored it in useState, clearing it would cause a re-render
    const pollRef = useRef(null)

    // Load existing ready repos when component mounts
    // So if user refreshes the page their old repos still appear
    useEffect(() => {
        listRepos()
            .then(all => setRepos(all.filter(r => r.status === 'READY')))
            .catch(() => {}) // silently fail — not critical
        // Cleanup function: if user navigates away, stop polling
        return () => {
            if (pollRef.current) clearInterval(pollRef.current)
        }
    }, [])

    async function handleSubmit() {
        if (!url.trim() || busy) return
        setError('')
        setBusy(true)
        setCurrent(null)

        try {
            // Immediately gets back a repo with status PENDING
            const repo = await ingestRepo(url.trim())
            setCurrent(repo)
            startPolling(repo.id)
        } catch (e) {
            setError(e.message)
            setBusy(false)
        }
    }

    function startPolling(repoId) {
        // Check status every 3 seconds
        // Each call hits GET /api/repos/{id}/status
        // Backend returns the current processedFiles and totalFiles counts
        pollRef.current = setInterval(async () => {
            try {
                const updated = await getRepoStatus(repoId)
                setCurrent(updated)

                if (updated.status === 'READY') {
                    clearInterval(pollRef.current)
                    setBusy(false)
                    // Add to the ready list, avoid duplicates with filter
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

    // Calculate progress percentage for the progress bar
    function getProgress() {
        if (!current || !current.totalFiles || current.totalFiles === 0) return 0
        return Math.round((current.processedFiles / current.totalFiles) * 100)
    }

    return (
        <div className="space-y-8 max-w-xl mx-auto">
            <div>
                <h2 className="text-2xl font-semibold mb-2">Analyze a Repository</h2>
                <p className="text-gray-400 text-sm mb-4">
                    Paste any public GitHub URL. Private repos need a token.
                </p>

                <div className="flex gap-2">
                    <input
                        value={url}
                        onChange={e => setUrl(e.target.value)}
                        onKeyDown={e => e.key === 'Enter' && handleSubmit()}
                        placeholder="https://github.com/user/repo"
                        disabled={busy}
                        className="flex-1 bg-gray-900 border border-gray-700 rounded px-4 py-2 text-white placeholder-gray-500 focus:outline-none focus:border-blue-500 disabled:opacity-50"
                    />
                    <button
                        onClick={handleSubmit}
                        disabled={busy || !url.trim()}
                        className="bg-blue-600 hover:bg-blue-700 disabled:opacity-40 px-5 py-2 rounded font-medium transition-colors"
                    >
                        {busy ? 'Processing...' : 'Analyze'}
                    </button>
                </div>

                {error && (
                    <p className="mt-2 text-red-400 text-sm">{error}</p>
                )}
            </div>

            {/* Progress card — visible while ingestion is running */}
            {current && current.status !== 'READY' && current.status !== 'FAILED' && (
                <div className="bg-gray-900 border border-gray-700 rounded-lg p-4 space-y-3">
                    <div className="flex justify-between items-center">
                        <p className="font-medium">{current.repoName}</p>
                        <span className="text-xs text-yellow-400 bg-yellow-400/10 px-2 py-1 rounded">
                            {current.status}
                        </span>
                    </div>

                    {current.totalFiles > 0 && (
                        <>
                            {/* Progress bar */}
                            <div className="bg-gray-700 rounded-full h-2">
                                <div
                                    className="bg-blue-500 h-2 rounded-full transition-all duration-500"
                                    style={{ width: `${getProgress()}%` }}
                                />
                            </div>
                            <p className="text-xs text-gray-400">
                                {current.processedFiles} of {current.totalFiles} files
                                processed · {current.totalChunks} chunks created
                            </p>
                        </>
                    )}
                </div>
            )}

            {/* List of repos ready to chat with */}
            {repos.length > 0 && (
                <div className="space-y-3">
                    <h3 className="text-lg font-medium">Ready to Chat</h3>
                    {repos.map(repo => (
                        <button
                            key={repo.id}
                            onClick={() => onSelect(repo)}
                            className="w-full text-left bg-gray-900 hover:bg-gray-800 border border-gray-700 rounded-lg p-4 transition-colors group"
                        >
                            <div className="flex justify-between items-start">
                                <p className="font-medium group-hover:text-blue-400 transition-colors">
                                    {repo.repoName}
                                </p>
                                <span className="text-xs text-green-400 bg-green-400/10 px-2 py-1 rounded">
                                    READY
                                </span>
                            </div>
                            <p className="text-xs text-gray-500 mt-1">
                                {repo.totalChunks} chunks indexed
                            </p>
                        </button>
                    ))}
                </div>
            )}
        </div>
    )
}