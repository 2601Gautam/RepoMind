import { useState, useEffect, useRef } from 'react'
import { useNavigate } from 'react-router-dom'
import { ingestRepo, getRepoStatus, deleteRepo, listRepos, RateLimitError } from '../api/client'
import RepoUrlInput from '../components/repo/RepoUrlInput'
import IngestionProgress from '../components/repo/IngestionProgress'
import RateLimitBanner from '../components/common/RateLimitBanner'
import NavBar from '../components/layout/NavBar'
import { useAuth } from '../context/AuthContext'

// ── tiny icon components ──────────────────────────────────────────────────────

// Tabler: terminal-2
const IconChat = () => (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" className="w-5 h-5">
        <path d="M8 9l3 3-3 3" />
        <path d="M13 15h3" />
        <rect x="3" y="4" width="18" height="16" rx="2" />
    </svg>
)

// Tabler: bug
const IconDebug = () => (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" className="w-5 h-5">
        <path d="M9 9v-1a3 3 0 0 1 6 0v1" />
        <path d="M8 9h8a6 6 0 0 1 1 3v3a5 5 0 0 1 -10 0v-3a6 6 0 0 1 1 -3" />
        <path d="M3 13l4 0" />
        <path d="M17 13l4 0" />
        <path d="M12 20l0 -6" />
        <path d="M4 19l3.35 -2" />
        <path d="M20 19l-3.35 -2" />
        <path d="M4 7l3.75 2.4" />
        <path d="M20 7l-3.75 2.4" />
    </svg>
)

// Tabler: school
const IconInterview = () => (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" className="w-5 h-5">
        <path d="M22 9l-10 -4l-10 4l10 4l10 -4v6" />
        <path d="M6 10.6v5.4a6 3 0 0 0 12 0v-5.4" />
    </svg>
)

const IconGitHub = () => (
    <svg viewBox="0 0 16 16" fill="currentColor" className="w-3.5 h-3.5">
        <path d="M8 0C3.58 0 0 3.58 0 8c0 3.54 2.29 6.53 5.47 7.59.4.07.55-.17.55-.38 0-.19-.01-.82-.01-1.49-2.01.37-2.53-.49-2.69-.94-.09-.23-.48-.94-.82-1.13-.28-.15-.68-.52-.01-.53.63-.01 1.08.58 1.23.82.72 1.21 1.87.87 2.33.66.07-.52.28-.87.51-1.07-1.78-.2-3.64-.89-3.64-3.95 0-.87.31-1.59.82-2.15-.08-.2-.36-1.02.08-2.12 0 0 .67-.21 2.2.82.64-.18 1.32-.27 2-.27.68 0 1.36.09 2 .27 1.53-1.04 2.2-.82 2.2-.82.44 1.1.16 1.92.08 2.12.51.56.82 1.27.82 2.15 0 3.07-1.87 3.75-3.65 3.95.29.25.54.73.54 1.48 0 1.07-.01 1.93-.01 2.2 0 .21.15.46.55.38A8.013 8.013 0 0016 8c0-4.42-3.58-8-8-8z" />
    </svg>
)

const IconArrow = () => (
    <svg viewBox="0 0 20 20" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" className="w-4 h-4">
        <path d="M4 10h12M10 4l6 6-6 6" />
    </svg>
)

const STATUS = {
    READY:      { label: 'Ready',    dot: 'bg-emerald-500', text: 'text-emerald-400', pulse: false },
    PROCESSING: { label: 'Indexing', dot: 'bg-violet-400',  text: 'text-violet-400',  pulse: true  },
    PENDING:    { label: 'Queued',   dot: 'bg-amber-400',   text: 'text-amber-400',   pulse: true  },
    FAILED:     { label: 'Failed',   dot: 'bg-red-500',     text: 'text-red-400',     pulse: false },
}

// ── tool card ─────────────────────────────────────────────────────────────────

function ToolCard({ icon, name, description, color, onClick, disabled }) {
    const colorMap = {
        violet: {
            glow:   'group-hover:shadow-[0_0_28px_rgba(139,92,246,0.15)]',
            iconBg: 'bg-violet-500/10 border-violet-500/20',
            iconClr: 'text-violet-400',
            btn:    'bg-violet-500/10 border-violet-500/20 text-violet-400 hover:bg-violet-500/20',
        },
        rose: {
            glow:   'group-hover:shadow-[0_0_28px_rgba(244,63,94,0.12)]',
            iconBg: 'bg-rose-500/10 border-rose-500/20',
            iconClr: 'text-rose-400',
            btn:    'bg-rose-500/10 border-rose-500/20 text-rose-400 hover:bg-rose-500/20',
        },
        sky: {
            glow:   'group-hover:shadow-[0_0_28px_rgba(14,165,233,0.12)]',
            iconBg: 'bg-sky-500/10 border-sky-500/20',
            iconClr: 'text-sky-400',
            btn:    'bg-sky-500/10 border-sky-500/20 text-sky-400 hover:bg-sky-500/20',
        },
    }
    const c = colorMap[color]

    return (
        <button
            onClick={onClick}
            disabled={disabled}
            className={`group relative w-full text-left flex flex-col gap-4 rounded-2xl bg-[#0d0d0f] border border-white/[0.06] p-5 transition-all duration-200 hover:border-white/[0.1] hover:bg-[#101012] ${c.glow} ${disabled ? 'opacity-40 cursor-not-allowed' : 'cursor-pointer'}`}
        >
            {/* icon */}
            <div className={`w-10 h-10 rounded-xl border flex items-center justify-center ${c.iconBg} ${c.iconClr}`}>
                {icon}
            </div>

            {/* text */}
            <div className="flex-1 space-y-1">
                <p className="text-[14px] font-semibold text-white/90">{name}</p>
                <p className="text-[12.5px] text-neutral-500 leading-relaxed">{description}</p>
            </div>

            {/* launch */}
            <div className={`flex items-center gap-1.5 text-[12px] font-medium border rounded-lg px-3 py-1.5 w-fit transition-colors ${c.btn}`}>
                <span>Open</span>
                <IconArrow />
            </div>

            {disabled && (
                <div className="absolute inset-0 rounded-2xl flex items-center justify-center">
                    <span className="text-[11px] text-neutral-600 bg-[#0d0d0f] px-2 py-1 rounded">Ingest a repo first</span>
                </div>
            )}
        </button>
    )
}

// ── active repo mini-card ─────────────────────────────────────────────────────

function ActiveRepoPanel({ repo, onRemove }) {
    const navigate = useNavigate()

    if (!repo) {
        return (
            <div className="flex flex-col gap-3 rounded-2xl border border-dashed border-white/[0.06] bg-white/[0.01] p-6 text-center">
                <div className="mx-auto w-10 h-10 rounded-xl bg-white/[0.02] border border-white/[0.05] flex items-center justify-center text-neutral-600">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" className="w-5 h-5">
                        <path d="M12 5v14M5 12h14" />
                    </svg>
                </div>
                <div>
                    <p className="text-[13px] font-semibold text-neutral-500">No repo loaded</p>
                    <p className="mt-0.5 text-[11.5px] text-neutral-700 leading-relaxed">Paste a GitHub URL on the left to get started.</p>
                </div>
            </div>
        )
    }

    const st = STATUS[repo.status] ?? { label: repo.status, dot: 'bg-neutral-600', text: 'text-neutral-400', pulse: false }
    const slug = repo.githubUrl?.replace('https://github.com/', '') ?? ''
    const name = repo.repoName ?? slug.split('/').pop() ?? 'Unknown'
    const pct = repo.totalFiles > 0 ? Math.round((repo.processedFiles / repo.totalFiles) * 100) : 0
    const isReady = repo.status === 'READY'

    return (
        <div className="rounded-2xl bg-[#0d0d0f] border border-white/[0.07] overflow-hidden">
            {/* header */}
            <div className="flex items-start justify-between gap-3 p-4 border-b border-white/[0.04]">
                <div className="flex items-center gap-2.5 min-w-0">
                    <span className="text-neutral-600 shrink-0"><IconGitHub /></span>
                    <div className="min-w-0">
                        <p className="truncate text-[13.5px] font-semibold text-white/90 tracking-tight">{name}</p>
                        <p className="truncate text-[10.5px] text-neutral-600 font-mono mt-0.5">{slug}</p>
                    </div>
                </div>
                <div className="flex items-center gap-2 shrink-0">
                    <span className={`flex items-center gap-1.5 text-[10.5px] font-semibold uppercase tracking-widest ${st.text}`}>
                        <span className={`inline-block h-1.5 w-1.5 rounded-full ${st.dot} ${st.pulse ? 'animate-pulse' : ''}`} />
                        {st.label}
                    </span>
                    <button
                        onClick={() => onRemove?.(repo.id)}
                        className="cursor-pointer flex h-6 w-6 items-center justify-center rounded-md text-neutral-700 hover:bg-red-500/10 hover:text-red-400 transition-colors"
                    >
                        <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.8" className="w-3 h-3">
                            <path d="M2 4h12M5.333 4V2.667a.667.667 0 01.667-.667h4a.667.667 0 01.667.667V4M12.667 4L12 13.333a.667.667 0 01-.667.667H4.667A.667.667 0 014 13.333L3.333 4" />
                        </svg>
                    </button>
                </div>
            </div>

            {/* stats / progress */}
            <div className="p-4 space-y-3">
                {isReady && repo.totalFiles > 0 && (
                    <div className="flex items-center justify-between text-[11.5px]">
                        <span className="text-neutral-600">{repo.totalFiles.toLocaleString()} files indexed</span>
                        <span className="text-emerald-500/70 font-medium">100%</span>
                    </div>
                )}

                {repo.status === 'PROCESSING' && (
                    <div className="space-y-2">
                        <div className="flex justify-between text-[11px]">
                            <span className="text-neutral-600">
                                {repo.processedFiles > 0 ? `${repo.processedFiles.toLocaleString()} / ${repo.totalFiles?.toLocaleString()}` : 'Indexing…'}
                            </span>
                            <span className="text-neutral-400 tabular-nums">{pct}%</span>
                        </div>
                        <div className="h-[3px] w-full overflow-hidden rounded-full bg-white/[0.05]">
                            <div className="h-full rounded-full bg-violet-500 transition-all duration-700" style={{ width: `${Math.max(pct, 4)}%` }} />
                        </div>
                    </div>
                )}

                {repo.status === 'PENDING' && (
                    <p className="text-[11.5px] text-neutral-600">Queued — starting soon…</p>
                )}

                {repo.status === 'FAILED' && (
                    <p className="text-[11.5px] text-red-400/80 line-clamp-2">{repo.errorMessage || 'Ingestion failed.'}</p>
                )}

                {/* tool buttons — only when READY */}
                {isReady && (
                    <div className="pt-1 grid grid-cols-3 gap-2">
                        {[
                            { label: 'Chat', path: `/chat/${repo.id}`, clr: 'hover:bg-violet-500/10 hover:text-violet-300 hover:border-violet-500/20' },
                            { label: 'Debug', path: `/debug/${repo.id}`, clr: 'hover:bg-rose-500/10 hover:text-rose-300 hover:border-rose-500/20' },
                            { label: 'Interview', path: `/interview/${repo.id}`, clr: 'hover:bg-sky-500/10 hover:text-sky-300 hover:border-sky-500/20' },
                        ].map(({ label, path, clr }) => (
                            <button
                                key={label}
                                onClick={() => navigate(path)}
                                className={`cursor-pointer text-[11.5px] font-medium py-1.5 rounded-lg border border-white/[0.06] text-neutral-500 transition-all duration-150 ${clr}`}
                            >
                                {label}
                            </button>
                        ))}
                    </div>
                )}
            </div>
        </div>
    )
}

// ── main page ─────────────────────────────────────────────────────────────────

export default function DashboardPage() {
    const navigate = useNavigate()
    const { user } = useAuth()
    const firstName = user?.name?.split(' ')[0] || null

    const [busy, setBusy] = useState(false)
    const [current, setCurrent] = useState(() => {
        const s = localStorage.getItem('repomind_current_repo')
        return s ? JSON.parse(s) : null
    })
    const [submitError, setSubmitError] = useState('')
    const [rateLimitSeconds, setRateLimitSeconds] = useState(null)
    const pollsRef = useRef({})

    useEffect(() => {
        if (!submitError) return
        const t = setTimeout(() => setSubmitError(''), 5000)
        return () => clearTimeout(t)
    }, [submitError])

    useEffect(() => {
        if (current && (current.status === 'PROCESSING' || current.status === 'PENDING')) {
            startPollingRepo(current.id)
        }
        return () => Object.values(pollsRef.current).forEach(clearInterval)
    }, [])

    function startPollingRepo(repoId) {
        if (pollsRef.current[repoId]) return
        pollsRef.current[repoId] = setInterval(async () => {
            try {
                const updated = await getRepoStatus(repoId)
                if (updated.status === 'FAILED') {
                    const msg = (updated.errorMessage || '').toLowerCase()
                    const isPrivate = ['auth', 'not authorized', 'authentication', '403', 'could not read', 'access denied'].some(k => msg.includes(k))
                    clearInterval(pollsRef.current[repoId])
                    delete pollsRef.current[repoId]
                    if (isPrivate) {
                        setCurrent(null)
                        localStorage.removeItem('repomind_current_repo')
                        setSubmitError('Private repo detected. Add a GitHub token to continue.')
                        return
                    }
                }
                setCurrent(updated)
                localStorage.setItem('repomind_current_repo', JSON.stringify(updated))
                if (updated.status === 'READY' || updated.status === 'FAILED') {
                    clearInterval(pollsRef.current[repoId])
                    delete pollsRef.current[repoId]
                }
            } catch { /* silent retry */ }
        }, 3000)
    }

    async function handleSubmit(url, token) {
        setSubmitError('')
        setBusy(true)
        setCurrent(null)
        localStorage.removeItem('repomind_current_repo')
        try {
            const repo = await ingestRepo(url, token)
            setCurrent(repo)
            localStorage.setItem('repomind_current_repo', JSON.stringify(repo))
            startPollingRepo(repo.id)
        } catch (e) {
            if (e instanceof RateLimitError) setRateLimitSeconds(e.retryAfterSeconds)
            else setSubmitError(e.message)
        } finally {
            setBusy(false)
        }
    }

    async function handleRemove(repoId) {
        setCurrent(null)
        localStorage.removeItem('repomind_current_repo')
        if (pollsRef.current[repoId]) {
            clearInterval(pollsRef.current[repoId])
            delete pollsRef.current[repoId]
        }
        try { await deleteRepo(repoId) } catch { /* silent */ }
    }

    const isReady = current?.status === 'READY'

    // Navigate to tool — if a READY repo is active, go straight to it; otherwise tool selector page
    function launchTool(toolPath) {
        if (isReady) navigate(`${toolPath}/${current.id}`)
        else navigate(toolPath)
    }

    return (
        <div className="min-h-screen bg-[#080809] text-white antialiased">
            <NavBar />

            <div className="max-w-5xl mx-auto px-6 sm:px-8 pt-10 pb-20">

                {/* ── Hero greeting ─────────────────────────────────────── */}
                <div className="mb-8">
                    <h1 className="text-[26px] sm:text-[30px] font-extrabold tracking-tight text-white leading-tight">
                        {firstName ? `Hey, ${firstName}` : 'Your workspace'}
                    </h1>
                    <p className="mt-1.5 text-[13.5px] text-neutral-500 max-w-lg leading-relaxed">
                        Ingest a GitHub repository to unlock AI-powered chat, debugging, and interview prep tools.
                    </p>
                </div>

                {/* ── Main two-column grid ───────────────────────────────── */}
                <div className="grid grid-cols-1 lg:grid-cols-[1fr_320px] gap-6">

                    {/* ── LEFT COLUMN ────────────────────────────────────── */}
                    <div className="space-y-6">

                        {/* Ingestion card */}
                        <div className="rounded-2xl bg-[#0d0d0f] border border-white/[0.07] p-5 space-y-4">
                            <div>
                                <div className="flex items-center gap-2 mb-1">
                                    <span className="w-5 h-5 rounded-md bg-violet-500/15 border border-violet-500/25 flex items-center justify-center">
                                        <svg viewBox="0 0 16 16" fill="currentColor" className="w-2.5 h-2.5 text-violet-400">
                                            <path d="M8 0C3.58 0 0 3.58 0 8c0 3.54 2.29 6.53 5.47 7.59.4.07.55-.17.55-.38 0-.19-.01-.82-.01-1.49-2.01.37-2.53-.49-2.69-.94-.09-.23-.48-.94-.82-1.13-.28-.15-.68-.52-.01-.53.63-.01 1.08.58 1.23.82.72 1.21 1.87.87 2.33.66.07-.52.28-.87.51-1.07-1.78-.2-3.64-.89-3.64-3.95 0-.87.31-1.59.82-2.15-.08-.2-.36-1.02.08-2.12 0 0 .67-.21 2.2.82.64-.18 1.32-.27 2-.27.68 0 1.36.09 2 .27 1.53-1.04 2.2-.82 2.2-.82.44 1.1.16 1.92.08 2.12.51.56.82 1.27.82 2.15 0 3.07-1.87 3.75-3.65 3.95.29.25.54.73.54 1.48 0 1.07-.01 1.93-.01 2.2 0 .21.15.46.55.38A8.013 8.013 0 0016 8c0-4.42-3.58-8-8-8z" />
                                        </svg>
                                    </span>
                                    <p className="text-[13px] font-semibold text-white/90">Ingest Repository</p>
                                </div>
                                <p className="text-[12px] text-neutral-600 ml-7">Paste any public or private GitHub URL below.</p>
                            </div>

                            <RepoUrlInput onSubmit={handleSubmit} disabled={busy || !!rateLimitSeconds} />

                            {submitError && (
                                <p className="text-[12px] text-red-400/90 font-medium">{submitError}</p>
                            )}
                            {rateLimitSeconds && (
                                <RateLimitBanner seconds={rateLimitSeconds} onDismiss={() => setRateLimitSeconds(null)} />
                            )}
                            {current && <IngestionProgress repo={current} />}
                        </div>

                        {/* Tool cards */}
                        <div>
                            <div className="mb-3">
                                <p className="text-[11px] font-bold uppercase tracking-widest text-neutral-600">Tools</p>
                            </div>
                            <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
                                <ToolCard
                                    icon={<IconChat />}
                                    name="Chat"
                                    description="Ask questions about your codebase in plain English. Explore logic, dependencies, and architecture."
                                    color="violet"
                                    disabled={!isReady}
                                    onClick={() => launchTool('/chat')}
                                />
                                <ToolCard
                                    icon={<IconDebug />}
                                    name="Debug"
                                    description="Paste an error or stack trace. Get root cause analysis and suggested fixes."
                                    color="rose"
                                    disabled={!isReady}
                                    onClick={() => launchTool('/debug')}
                                />
                                <ToolCard
                                    icon={<IconInterview />}
                                    name="Interview"
                                    description="Generate custom mock interview questions based on the repository's code and patterns."
                                    color="sky"
                                    disabled={!isReady}
                                    onClick={() => launchTool('/interview')}
                                />
                            </div>
                        </div>
                    </div>

                    {/* ── RIGHT COLUMN ───────────────────────────────────── */}
                    <div className="space-y-4">
                        <p className="text-[11px] font-bold uppercase tracking-widest text-neutral-600">Active Repository</p>
                        <ActiveRepoPanel repo={current} onRemove={handleRemove} />

                        {/* Tip */}
                        <div className="rounded-xl bg-white/[0.01] border border-white/[0.04] p-4">
                            <p className="text-[11px] font-semibold text-neutral-600 uppercase tracking-widest mb-2">How it works</p>
                            <ol className="space-y-2">
                                {[
                                    'Paste a GitHub repo URL and click Analyze',
                                    'Wait for indexing to complete (READY status)',
                                    'Launch Chat, Debug, or Interview for that repo',
                                    'Switch repos anytime from the Repositories page',
                                ].map((step, i) => (
                                    <li key={i} className="flex items-start gap-2.5 text-[11.5px] text-neutral-600 leading-relaxed">
                                        <span className="shrink-0 w-4 h-4 rounded-full bg-white/[0.04] border border-white/[0.06] flex items-center justify-center text-[9px] text-neutral-700 font-bold mt-0.5">
                                            {i + 1}
                                        </span>
                                        {step}
                                    </li>
                                ))}
                            </ol>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    )
}