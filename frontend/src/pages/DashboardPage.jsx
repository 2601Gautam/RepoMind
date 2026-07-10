import { useState, useEffect, useRef } from 'react'
import { listRepos, ingestRepo, getRepoStatus, RateLimitError } from '../api/client'
import NavBar from '../components/layout/Navbar'
import RepoCard from '../components/repo/RepoCard'
import RepoUrlInput from '../components/repo/RepoUrlInput'
import IngestionProgress from '../components/repo/IngestionProgress'
import LoadingSpinner from '../components/common/LoadingSpinner'
import RateLimitBanner from '../components/common/RateLimitBanner'
import EmptyState from '../components/common/EmptyState'

export default function DashboardPage() {
    const [repos, setRepos] = useState([])
    const [page, setPage] = useState(0)
    const [totalPages, setTotalPages] = useState(0)
    const [loadingRepos, setLoadingRepos] = useState(true)
    const [busy, setBusy] = useState(false)
    const [current, setCurrent] = useState(null)
    const [submitError, setSubmitError] = useState('')
    const [rateLimitSeconds, setRateLimitSeconds] = useState(null)
    // Map of repoId → interval — poll each PROCESSING repo independently
    const pollsRef = useRef({})

    useEffect(() => {
        loadRepos()
        // Cleanup: stop all polling intervals when component unmounts
        return () => Object.values(pollsRef.current).forEach(clearInterval)
    }, [page])

    async function loadRepos() {
        setLoadingRepos(true)
        try {
            // Backend returns PagedResponse: {content:[], page:0, totalPages:N, ...}
            const data = await listRepos(page, 10)
            const repoList = data.content || []
            setRepos(repoList)
            setTotalPages(data.totalPages || 0)

            // Auto-start polling for any repo still processing
            repoList.forEach(repo => {
                if (repo.status === 'PROCESSING' || repo.status === 'PENDING') {
                    startPollingRepo(repo.id)
                }
            })
        } catch (e) {
            console.error('Failed to load repos:', e)
        } finally {
            setLoadingRepos(false)
        }
    }

    // Poll a specific repo's status every 3 seconds
    // Updates that repo's card in the list independently of others
    function startPollingRepo(repoId) {
        if (pollsRef.current[repoId]) return // already polling

        pollsRef.current[repoId] = setInterval(async () => {
            try {
                const updated = await getRepoStatus(repoId)
                setRepos(prev => prev.map(r => r.id === repoId ? updated : r))

                // Stop polling once terminal state reached
                if (updated.status === 'READY' || updated.status === 'FAILED') {
                    clearInterval(pollsRef.current[repoId])
                    delete pollsRef.current[repoId]
                }
            } catch {
                // Silently ignore — will retry on next interval
            }
        }, 3000)
    }

    async function handleSubmit(url, token) {
        setSubmitError('')
        setBusy(true)
        setCurrent(null)

        try {
            const repo = await ingestRepo(url, token)
            setCurrent(repo)

            // Add to list immediately with PENDING status
            // Don't wait for a full reload — give instant feedback
            setRepos(prev => [repo, ...prev])
            startPollingRepo(repo.id)

        } catch (e) {
            if (e instanceof RateLimitError) {
                setRateLimitSeconds(e.retryAfterSeconds)
            } else {
                setSubmitError(e.message)
            }
        } finally {
            setBusy(false)
        }
    }

    return (
        <div className="min-h-screen bg-gray-950 text-white">
            <NavBar />

            <main className="max-w-5xl mx-auto px-6 py-8 space-y-8">
                {/* URL submission card */}
                <div className="bg-gray-900 border border-gray-700 rounded-xl p-6 space-y-4">
                    <div>
                        <h2 className="text-lg font-semibold text-white">Analyze a Repository</h2>
                        <p className="text-sm text-gray-400 mt-1">
                            Paste any public GitHub URL to start chatting with the codebase.
                        </p>
                    </div>

                    <RepoUrlInput onSubmit={handleSubmit} disabled={busy || !!rateLimitSeconds} />

                    {submitError && (
                        <p className="text-sm text-red-400">{submitError}</p>
                    )}

                    {rateLimitSeconds && (
                        <RateLimitBanner
                            seconds={rateLimitSeconds}
                            onDismiss={() => setRateLimitSeconds(null)}
                        />
                    )}

                    <IngestionProgress repo={current} />
                </div>

                {/* Repo grid */}
                <div className="space-y-4">
                    <h3 className="text-xs font-medium text-gray-500 uppercase tracking-wider">
                        Your Repositories
                    </h3>

                    {loadingRepos ? (
                        <div className="flex justify-center py-12">
                            <LoadingSpinner size="lg" />
                        </div>
                    ) : repos.length === 0 ? (
                        <EmptyState
                            title="No repositories yet"
                            description="Paste a GitHub URL above to analyze your first codebase"
                        />
                    ) : (
                        <>
                            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                                {repos.map(repo => (
                                    <RepoCard key={repo.id} repo={repo} />
                                ))}
                            </div>

                            {/* Pagination — only shown when there are multiple pages */}
                            {totalPages > 1 && (
                                <div className="flex items-center justify-center gap-3 pt-4">
                                    <button
                                        onClick={() => setPage(p => Math.max(0, p - 1))}
                                        disabled={page === 0}
                                        className="text-sm text-gray-400 hover:text-white disabled:opacity-40 disabled:cursor-not-allowed px-3 py-1.5 border border-gray-700 rounded-lg transition-colors"
                                    >
                                        ← Previous
                                    </button>
                                    <span className="text-sm text-gray-500">
                                        Page {page + 1} of {totalPages}
                                    </span>
                                    <button
                                        onClick={() => setPage(p => Math.min(totalPages - 1, p + 1))}
                                        disabled={page >= totalPages - 1}
                                        className="text-sm text-gray-400 hover:text-white disabled:opacity-40 disabled:cursor-not-allowed px-3 py-1.5 border border-gray-700 rounded-lg transition-colors"
                                    >
                                        Next →
                                    </button>
                                </div>
                            )}
                        </>
                    )}
                </div>
            </main>
        </div>
    )
}