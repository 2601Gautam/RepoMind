import { useState, useEffect, useRef } from 'react'
import { listRepos, ingestRepo, getRepoStatus, deleteRepo, RateLimitError } from '../api/client'
import { useAuth } from '../context/AuthContext'
import NavBar from '../components/layout/NavBar'
import RepoCard from '../components/repo/RepoCard'
import RepoUrlInput from '../components/repo/RepoUrlInput'
import IngestionProgress from '../components/repo/IngestionProgress'
import LoadingSpinner from '../components/common/LoadingSpinner'
import RateLimitBanner from '../components/common/RateLimitBanner'
import EmptyState from '../components/common/EmptyState'

const LS_KEY = 'repomind_active_repo'

export default function DashboardPage() {
    const { user } = useAuth()
    const [activeRepo, setActiveRepo] = useState(null)   // single active repo shown
    const [loadingRepos, setLoadingRepos] = useState(true)
    const [busy, setBusy] = useState(false)
    const [current, setCurrent] = useState(null)         // for IngestionProgress
    const [submitError, setSubmitError] = useState('')
    const [rateLimitSeconds, setRateLimitSeconds] = useState(null)
    const pollRef = useRef(null)                         // single polling interval

    // On mount: restore last active repo from localStorage, then sync its status
    useEffect(() => {
        async function restoreActive() {
            try {
                const saved = localStorage.getItem(LS_KEY)
                if (saved) {
                    const parsed = JSON.parse(saved)
                    setActiveRepo(parsed)
                    // Refresh status from server
                    const fresh = await getRepoStatus(parsed.id)
                    const updated = { ...parsed, ...fresh }
                    setActiveRepo(updated)
                    localStorage.setItem(LS_KEY, JSON.stringify(updated))
                    // If still in progress, start polling
                    if (fresh.status === 'PROCESSING' || fresh.status === 'PENDING') {
                        setCurrent(fresh)
                        startPolling(parsed.id)
                    }
                } else {
                    // No saved repo — fetch the most recently submitted one
                    const data = await listRepos(0, 1)
                    const list = Array.isArray(data) ? data : (data.content || [])
                    if (list.length > 0) {
                        setActiveRepo(list[0])
                        localStorage.setItem(LS_KEY, JSON.stringify(list[0]))
                        if (list[0].status === 'PROCESSING' || list[0].status === 'PENDING') {
                            setCurrent(list[0])
                            startPolling(list[0].id)
                        }
                    }
                }
            } catch (e) {
                console.error('Failed to restore active repo:', e)
            } finally {
                setLoadingRepos(false)
            }
        }
        restoreActive()
        return () => { if (pollRef.current) clearInterval(pollRef.current) }
    }, [])

    function startPolling(repoId) {
        if (pollRef.current) clearInterval(pollRef.current)
        pollRef.current = setInterval(async () => {
            try {
                const updated = await getRepoStatus(repoId)
                setActiveRepo(prev => {
                    const merged = { ...prev, ...updated }
                    localStorage.setItem(LS_KEY, JSON.stringify(merged))
                    return merged
                })
                setCurrent(updated)
                if (updated.status === 'READY' || updated.status === 'FAILED') {
                    clearInterval(pollRef.current)
                    pollRef.current = null
                }
            } catch { /* silently retry */ }
        }, 3000)
    }

    async function handleSubmit(url, token) {
        setSubmitError('')
        setBusy(true)
        setCurrent(null)

        try {
            const repo = await ingestRepo(url, token)
            setActiveRepo(repo)
            setCurrent(repo)
            localStorage.setItem(LS_KEY, JSON.stringify(repo))
            startPolling(repo.id)
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

    async function handleRemove(repoId) {
        setActiveRepo(null)
        setCurrent(null)
        localStorage.removeItem(LS_KEY)
        if (pollRef.current) { clearInterval(pollRef.current); pollRef.current = null }
        try {
            await deleteRepo(repoId)
        } catch (e) {
            console.error('Failed to delete repository:', e)
        }
    }

    return (
        <div className="min-h-screen bg-[#070709] text-white relative overflow-hidden">
            {/* Subtle glow background */}
            <div className="pointer-events-none fixed top-0 right-0 w-[500px] h-[500px] z-0 opacity-30"
                style={{
                    background: 'radial-gradient(circle at 100% 0%, rgba(139,92,246,0.12) 0%, transparent 70%)'
                }} />
            <div className="pointer-events-none fixed bottom-0 left-0 w-[400px] h-[400px] z-0 opacity-20"
                style={{
                    background: 'radial-gradient(circle at 0% 100%, rgba(236,72,153,0.08) 0%, transparent 70%)'
                }} />

            <NavBar />

            <main className="max-w-5xl mx-auto px-6 py-8 space-y-8 relative z-10">
                {/* Welcome section */}
                <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 pb-2 border-b border-white/[0.04] relative z-10">
                    <div>
                        <h1 className="text-[22px] font-extrabold tracking-tight text-white leading-tight">
                            Welcome back, <span className="gradient-text">{user?.name?.split(' ')[0] || 'Developer'}</span>
                        </h1>
                        <p className="text-[13px] text-neutral-400 mt-1">
                            Analyze, chat with, and debug your GitHub codebases using AI.
                        </p>
                    </div>
                </div>

                {/* URL submission card */}
                <div className="relative overflow-hidden bg-[#0d0d12]/50 border border-white/[0.06] rounded-2xl p-6 sm:p-8 space-y-6 backdrop-blur-md shadow-2xl">
                    {/* Glowing effect in the background */}
                    <div className="absolute top-0 right-0 w-[300px] h-[300px] bg-violet-600/5 blur-[80px] rounded-full pointer-events-none" />

                    <div className="relative z-10 flex flex-col md:flex-row md:items-center justify-between gap-4">
                        <div>
                            <h2 className="text-[16px] font-bold text-white tracking-tight flex items-center gap-2">
                                <svg className="w-5 h-5 text-violet-400" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth="2.5">
                                    <path strokeLinecap="round" strokeLinejoin="round" d="M12 4v16m8-8H4" />
                                </svg>
                                Ingest New Repository
                            </h2>
                            <p className="text-[13px] text-neutral-400 mt-1">
                                Paste any public or private GitHub repository link to build its semantic vector map.
                            </p>
                        </div>
                    </div>

                    <div className="relative z-10">
                        <RepoUrlInput onSubmit={handleSubmit} disabled={busy || !!rateLimitSeconds} />
                    </div>

                    {submitError && (
                        <div className="p-3 bg-red-500/5 border border-red-500/15 text-red-400 text-xs rounded-xl flex items-center gap-2 animate-fade-up">
                            <svg className="w-4 h-4 shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth="2">
                                <path strokeLinecap="round" strokeLinejoin="round" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
                            </svg>
                            <span>{submitError}</span>
                        </div>
                    )}

                    {rateLimitSeconds && (
                        <RateLimitBanner
                            seconds={rateLimitSeconds}
                            onDismiss={() => setRateLimitSeconds(null)}
                        />
                    )}

                    <div className="relative z-10">
                        <IngestionProgress repo={current} />
                    </div>
                </div>

                {/* Active repository */}
                <div className="space-y-4">
                    <div className="flex items-center justify-between border-b border-white/[0.04] pb-3">
                        <h3 className="text-[11px] font-bold text-neutral-500 uppercase tracking-wider">
                            Active Repository
                        </h3>
                        {activeRepo && (
                            <button
                                onClick={() => {
                                    setActiveRepo(null)
                                    setCurrent(null)
                                    localStorage.removeItem(LS_KEY)
                                    if (pollRef.current) { clearInterval(pollRef.current); pollRef.current = null }
                                }}
                                className="cursor-pointer text-[11px] text-neutral-600 hover:text-neutral-400 transition-colors"
                            >
                                Clear
                            </button>
                        )}
                    </div>

                    {loadingRepos ? (
                        <div className="flex justify-center py-12">
                            <LoadingSpinner size="lg" />
                        </div>
                    ) : !activeRepo ? (
                        <EmptyState
                            title="No active repository"
                            description="Paste a GitHub URL above to analyze a codebase"
                        />
                    ) : (
                        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                            <RepoCard key={activeRepo.id} repo={activeRepo} onRemove={handleRemove} />
                        </div>
                    )}
                </div>
            </main>
        </div>
    )
}