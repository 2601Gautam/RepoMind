import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { listRepos } from '../../api/client'
import LoadingSpinner from '../common/LoadingSpinner'

const STATUS = {
    READY:      { label: 'Ready',    dot: 'bg-emerald-500', text: 'text-emerald-400' },
    PROCESSING: { label: 'Indexing', dot: 'bg-violet-400',  text: 'text-violet-400'  },
    PENDING:    { label: 'Queued',   dot: 'bg-amber-400',   text: 'text-amber-400'   },
    FAILED:     { label: 'Failed',   dot: 'bg-red-500',     text: 'text-red-400'     },
}

const TOOL_META = {
    chat: {
        label: 'Chat',
        description: 'Ask questions about this codebase in plain English.',
        color: 'violet',
        icon: (
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" className="w-5 h-5">
                <path d="M8 9l3 3-3 3" />
                <path d="M13 15h3" />
                <rect x="3" y="4" width="18" height="16" rx="2" />
            </svg>
        )
    },
    debug: {
        label: 'Debug',
        description: 'Paste an error trace and get AI-powered root cause analysis.',
        color: 'rose',
        icon: (
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
    },
    interview: {
        label: 'Interview',
        description: 'Generate custom mock interview questions based on the codebase.',
        color: 'violet',
        icon: (
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" className="w-5 h-5">
                <path d="M22 9l-10 -4l-10 4l10 4l10 -4v6" />
                <path d="M6 10.6v5.4a6 3 0 0 0 12 0v-5.4" />
            </svg>
        )
    }
}

const COLOR_CLASSES = {
    violet: { bg: 'bg-violet-500/10', border: 'border-violet-500/20', text: 'text-violet-400', btn: 'bg-violet-500/10 border-violet-500/20 text-violet-300 hover:bg-violet-500/20' },
    rose:   { bg: 'bg-rose-500/10',   border: 'border-rose-500/20',   text: 'text-rose-400',   btn: 'bg-rose-500/10 border-rose-500/20 text-rose-300 hover:bg-rose-500/20'         },
    sky:    { bg: 'bg-sky-500/10',    border: 'border-sky-500/20',    text: 'text-sky-400',    btn: 'bg-sky-500/10 border-sky-500/20 text-sky-300 hover:bg-sky-500/20'             },
}

// ── component ─────────────────────────────────────────────────────────────────

export default function RepoSelector({ tool }) {
    const navigate = useNavigate()
    const meta = TOOL_META[tool] || TOOL_META.chat
    const colors = COLOR_CLASSES[meta.color]

    const [repos, setRepos] = useState([])
    const [loading, setLoading] = useState(true)
    const [search, setSearch] = useState('')

    useEffect(() => {
        listRepos(0, 100)
            .then(data => {
                const list = Array.isArray(data) ? data : (data.content || [])
                setRepos(list)
            })
            .catch(console.error)
            .finally(() => setLoading(false))
    }, [])

    const filtered = repos.filter(r => {
        const q = search.toLowerCase()
        return !q || (r.repoName || '').toLowerCase().includes(q) || (r.githubUrl || '').toLowerCase().includes(q)
    })

    const readyRepos = filtered.filter(r => r.status === 'READY')
    const otherRepos = filtered.filter(r => r.status !== 'READY')

    function launch(repoId) {
        navigate(`/${tool}/${repoId}`)
    }

    return (
        <div className="min-h-screen bg-[#080809] text-white antialiased">
            <div className="max-w-2xl mx-auto px-6 pt-14 pb-20">

                {/* Header */}
                <div className="flex items-center gap-4 mb-8">
                    <div className={`w-11 h-11 rounded-xl border flex items-center justify-center ${colors.bg} ${colors.border} ${colors.text}`}>
                        {meta.icon}
                    </div>
                    <div>
                        <h1 className="text-[22px] font-extrabold tracking-tight text-white">{meta.label}</h1>
                        <p className="text-[13px] text-neutral-500 mt-0.5">{meta.description}</p>
                    </div>
                </div>

                {/* Choose repo label */}
                <p className="text-[11px] font-bold uppercase tracking-widest text-neutral-600 mb-3">
                    Choose a repository to start
                </p>

                {/* Search */}
                <div className="relative mb-4">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="w-3.5 h-3.5 absolute left-3.5 top-1/2 -translate-y-1/2 text-neutral-600">
                        <circle cx="11" cy="11" r="8" />
                        <line x1="21" y1="21" x2="16.65" y2="16.65" />
                    </svg>
                    <input
                        type="text"
                        value={search}
                        onChange={e => setSearch(e.target.value)}
                        placeholder="Search repositories…"
                        className="w-full bg-[#0d0d0f] border border-white/[0.06] rounded-xl pl-9 pr-4 py-2.5 text-[13px] text-white placeholder-neutral-600 focus:outline-none focus:border-white/[0.14] transition-colors"
                    />
                </div>

                {/* Repo list */}
                {loading ? (
                    <div className="flex items-center justify-center py-20">
                        <LoadingSpinner size="lg" />
                    </div>
                ) : repos.length === 0 ? (
                    <div className="flex flex-col items-center justify-center py-16 text-center border border-dashed border-white/[0.05] rounded-2xl bg-white/[0.01]">
                        <div className="w-10 h-10 rounded-xl bg-white/[0.02] border border-white/[0.05] flex items-center justify-center mb-3 text-neutral-700">
                            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" className="w-5 h-5">
                                <path d="M12 5v14M5 12h14" />
                            </svg>
                        </div>
                        <p className="text-[13px] font-semibold text-neutral-500">No repositories yet</p>
                        <p className="mt-1 text-[11.5px] text-neutral-700 max-w-xs leading-relaxed">
                            Go to the Dashboard, paste a GitHub URL, and wait for indexing to finish.
                        </p>
                        <button
                            onClick={() => navigate('/dashboard')}
                            className="cursor-pointer mt-5 text-[12px] font-medium px-4 py-2 rounded-lg bg-white/[0.04] border border-white/[0.07] text-neutral-400 hover:text-white hover:border-white/[0.12] transition-all"
                        >
                            Go to Dashboard →
                        </button>
                    </div>
                ) : (
                    <div className="space-y-1.5">
                        {readyRepos.length === 0 && (
                            <p className="text-[12px] text-amber-500/70 bg-amber-500/5 border border-amber-500/10 rounded-lg px-3 py-2 mb-3">
                                No repositories are fully indexed yet. Wait for indexing to complete, then come back.
                            </p>
                        )}

                        {[...readyRepos, ...otherRepos].map(repo => {
                            const st = STATUS[repo.status] ?? { label: repo.status, dot: 'bg-neutral-600', text: 'text-neutral-400' }
                            const slug = repo.githubUrl?.replace('https://github.com/', '') ?? ''
                            const name = repo.repoName ?? slug.split('/').pop() ?? 'Unknown'
                            const canLaunch = repo.status === 'READY'

                            return (
                                <button
                                    key={repo.id}
                                    onClick={() => canLaunch && launch(repo.id)}
                                    disabled={!canLaunch}
                                    className={`w-full text-left flex items-center justify-between gap-4 rounded-xl px-4 py-3.5 border transition-all duration-150 ${
                                        canLaunch
                                            ? `cursor-pointer bg-[#0d0d0f] border-white/[0.06] hover:border-white/[0.12] hover:bg-[#111113]`
                                            : 'cursor-not-allowed bg-[#0a0a0c] border-white/[0.04] opacity-50'
                                    }`}
                                >
                                    <div className="flex items-center gap-3 min-w-0">
                                        <svg viewBox="0 0 16 16" fill="currentColor" className="w-3.5 h-3.5 shrink-0 text-neutral-600">
                                            <path d="M8 0C3.58 0 0 3.58 0 8c0 3.54 2.29 6.53 5.47 7.59.4.07.55-.17.55-.38 0-.19-.01-.82-.01-1.49-2.01.37-2.53-.49-2.69-.94-.09-.23-.48-.94-.82-1.13-.28-.15-.68-.52-.01-.53.63-.01 1.08.58 1.23.82.72 1.21 1.87.87 2.33.66.07-.52.28-.87.51-1.07-1.78-.2-3.64-.89-3.64-3.95 0-.87.31-1.59.82-2.15-.08-.2-.36-1.02.08-2.12 0 0 .67-.21 2.2.82.64-.18 1.32-.27 2-.27.68 0 1.36.09 2 .27 1.53-1.04 2.2-.82 2.2-.82.44 1.1.16 1.92.08 2.12.51.56.82 1.27.82 2.15 0 3.07-1.87 3.75-3.65 3.95.29.25.54.73.54 1.48 0 1.07-.01 1.93-.01 2.2 0 .21.15.46.55.38A8.013 8.013 0 0016 8c0-4.42-3.58-8-8-8z" />
                                        </svg>
                                        <div className="min-w-0">
                                            <p className="truncate text-[13px] font-semibold text-white/85 tracking-tight">{name}</p>
                                            <p className="truncate text-[10.5px] text-neutral-600 font-mono mt-0.5">{slug}</p>
                                        </div>
                                    </div>

                                    <div className="flex items-center gap-3 shrink-0">
                                        {repo.totalFiles > 0 && repo.status === 'READY' && (
                                            <span className="hidden sm:inline text-[11px] text-neutral-700">{repo.totalFiles.toLocaleString()} files</span>
                                        )}
                                        <span className={`flex items-center gap-1.5 text-[10.5px] font-semibold uppercase tracking-wider ${st.text}`}>
                                            <span className={`inline-block h-1.5 w-1.5 rounded-full ${st.dot}`} />
                                            {st.label}
                                        </span>
                                        {canLaunch && (
                                            <span className={`text-[11.5px] font-medium border rounded-lg px-2.5 py-1 transition-colors ${colors.btn}`}>
                                                Open →
                                            </span>
                                        )}
                                    </div>
                                </button>
                            )
                        })}
                    </div>
                )}
            </div>
        </div>
    )
}
