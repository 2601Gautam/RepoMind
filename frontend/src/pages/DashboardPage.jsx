import { useState, useRef, useEffect } from 'react'
import { ingestRepo, getRepoStatus, RateLimitError } from '../api/client'
import { useAuth } from '../context/AuthContext'
import { useNavigate } from 'react-router-dom'
import NavBar from '../components/layout/NavBar'
import RepoUrlInput from '../components/repo/RepoUrlInput'
import IngestionProgress from '../components/repo/IngestionProgress'
import RateLimitBanner from '../components/common/RateLimitBanner'

export default function DashboardPage() {
    const { user } = useAuth()

    // Session-only state — nothing persisted to localStorage
    const [busy, setBusy] = useState(false)
    const [current, setCurrent] = useState(null)   // repo being ingested (PROCESSING/PENDING)
    const [readyRepo, setReadyRepo] = useState(null) // repo that just finished → show success msg
    const [submitError, setSubmitError] = useState('')
    const [rateLimitSeconds, setRateLimitSeconds] = useState(null)
    const pollRef = useRef(null)

    function stopPolling() {
        if (pollRef.current) {
            clearInterval(pollRef.current)
            pollRef.current = null
        }
    }

    function startPolling(repoId) {
        stopPolling()
        pollRef.current = setInterval(async () => {
            try {
                const updated = await getRepoStatus(repoId)
                if (updated.status === 'FAILED') {
                    // Failed — silently clear everything, no UI trace
                    stopPolling()
                    setCurrent(null)
                    return
                }
                setCurrent(updated)
                if (updated.status === 'READY') {
                    stopPolling()
                    setCurrent(null)
                    setReadyRepo(updated)
                }
            } catch { /* silently retry */ }
        }, 3000)
    }

    async function handleSubmit(url, token) {
        setSubmitError('')
        setReadyRepo(null)
        setBusy(true)
        setCurrent(null)
        stopPolling()

        try {
            const repo = await ingestRepo(url, token)
            setCurrent(repo)
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

    return (
        <div className="min-h-screen bg-[#070709] text-white relative overflow-hidden">
            {/* Subtle glow background */}
            <div className="pointer-events-none fixed top-0 right-0 w-[500px] h-[500px] z-0 opacity-30"
                style={{ background: 'radial-gradient(circle at 100% 0%, rgba(139,92,246,0.12) 0%, transparent 70%)' }} />
            <div className="pointer-events-none fixed bottom-0 left-0 w-[400px] h-[400px] z-0 opacity-20"
                style={{ background: 'radial-gradient(circle at 0% 100%, rgba(236,72,153,0.08) 0%, transparent 70%)' }} />

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

                {/* Success message — shown only when processing completes in this session */}
                {readyRepo && (
                    <SuccessCard repo={readyRepo} onDismiss={() => setReadyRepo(null)} />
                )}
            </main>
        </div>
    )
}

// ─── SuccessCard — shown when repo becomes READY ─────────────────────────────

function SuccessCard({ repo, onDismiss }) {
    const slugRaw = repo.githubUrl?.replace('https://github.com/', '').replace(/\/$/, '') ?? ''
    
    useEffect(() => {
        const timer = setTimeout(onDismiss, 5000)
        return () => clearTimeout(timer)
    }, [onDismiss])

    return (
        <div className="animate-fade-up relative overflow-hidden bg-[#0d0d0f] border border-white/[0.08] rounded-2xl p-5 shadow-[0_8px_30px_rgba(0,0,0,0.4)] flex items-center justify-between gap-4">
            <div className="flex items-center gap-3">
                <div className="w-9 h-9 rounded-xl bg-white/[0.04] border border-white/[0.08] flex items-center justify-center shrink-0 text-emerald-400">
                    <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth="2.5">
                        <path strokeLinecap="round" strokeLinejoin="round" d="M5 13l4 4L19 7" />
                    </svg>
                </div>
                <div>
                    <p className="text-[13px] font-semibold text-white/90">Repo processing is done</p>
                    <p className="text-[11.5px] text-neutral-500 mt-0.5 font-mono truncate max-w-[260px]">
                        {slugRaw || repo.repoName}
                    </p>
                </div>
            </div>
            <button
                onClick={onDismiss}
                className="cursor-pointer text-neutral-600 hover:text-neutral-350 transition-colors shrink-0 mt-0.5"
                title="Dismiss"
            >
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" className="w-4 h-4">
                    <path strokeLinecap="round" strokeLinejoin="round" d="M18 6L6 18M6 6l12 12" />
                </svg>
            </button>
        </div>
    )
}