import { useNavigate } from 'react-router-dom'

// ─── tiny SVG icons (consistent stroke weight: 1.75) ─────────────────────────

const IconGitHub = () => (
    <svg viewBox="0 0 16 16" fill="currentColor" className="w-3.5 h-3.5">
        <path d="M8 0C3.58 0 0 3.58 0 8c0 3.54 2.29 6.53 5.47 7.59.4.07.55-.17.55-.38 0-.19-.01-.82-.01-1.49-2.01.37-2.53-.49-2.69-.94-.09-.23-.48-.94-.82-1.13-.28-.15-.68-.52-.01-.53.63-.01 1.08.58 1.23.82.72 1.21 1.87.87 2.33.66.07-.52.28-.87.51-1.07-1.78-.2-3.64-.89-3.64-3.95 0-.87.31-1.59.82-2.15-.08-.2-.36-1.02.08-2.12 0 0 .67-.21 2.2.82.64-.18 1.32-.27 2-.27.68 0 1.36.09 2 .27 1.53-1.04 2.2-.82 2.2-.82.44 1.1.16 1.92.08 2.12.51.56.82 1.27.82 2.15 0 3.07-1.87 3.75-3.65 3.95.29.25.54.73.54 1.48 0 1.07-.01 1.93-.01 2.2 0 .21.15.46.55.38A8.013 8.013 0 0016 8c0-4.42-3.58-8-8-8z" />
    </svg>
)

const IconChat = () => (
    <svg viewBox="0 0 20 20" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" className="w-3 h-3">
        <path d="M17.5 11.667a1.667 1.667 0 01-1.667 1.666H5.833L2.5 16.667V4.167A1.667 1.667 0 014.167 2.5H15.833A1.667 1.667 0 0117.5 4.167v7.5z" />
    </svg>
)

const IconInterview = () => (
    <svg viewBox="0 0 20 20" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" className="w-3.5 h-3.5">
        <path d="M13.333 2.5H6.667A1.667 1.667 0 005 4.167v11.666A1.667 1.667 0 006.667 17.5h6.666A1.667 1.667 0 0015 15.833V4.167A1.667 1.667 0 0013.333 2.5z" />
        <path d="M7.5 6.667h5M7.5 10h5M7.5 13.333h2.5" />
    </svg>
)

const IconDebug = () => (
    <svg viewBox="0 0 20 20" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" className="w-3.5 h-3.5">
        <path d="M7.5 2.5a2.5 2.5 0 015 0M5.833 5.833S5 6.667 5 8.333H15c0-1.666-.833-2.5-.833-2.5" />
        <path d="M2.5 10h15M5 13.333l-2.5 2.5M15 13.333l2.5 2.5M6.667 17.5h6.666" />
        <rect x="5" y="8.333" width="10" height="5" rx="1" />
    </svg>
)

const IconTrash = () => (
    <svg viewBox="0 0 20 20" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" className="w-3.5 h-3.5">
        <path d="M2.5 5h15M15.833 5l-.833 11.667a.833.833 0 01-.833.833H5.833a.833.833 0 01-.833-.833L4.167 5M7.5 5V3.333a.833.833 0 01.833-.833h3.334a.833.833 0 01.833.833V5" />
    </svg>
)

// ─── status meta ──────────────────────────────────────────────────────────────

const STATUS = {
    READY:      { label: 'Ready',    dot: 'bg-emerald-500',  text: 'text-emerald-400', pulse: false },
    PROCESSING: { label: 'Indexing', dot: 'bg-violet-400',   text: 'text-violet-400',  pulse: true  },
    PENDING:    { label: 'Queued',   dot: 'bg-amber-400',    text: 'text-amber-400',   pulse: true  },
    FAILED:     { label: 'Failed',   dot: 'bg-red-500',      text: 'text-red-400',     pulse: false },
}

// ─── main component ───────────────────────────────────────────────────────────

export default function RepoCard({ repo, onRemove, viewMode = 'grid' }) {
    const navigate = useNavigate()

    function handleNavigate(path) {
        localStorage.setItem('repomind_active_repo', JSON.stringify(repo))
        navigate(path)
    }

    const pct = repo.totalFiles > 0
        ? Math.round((repo.processedFiles / repo.totalFiles) * 100)
        : 0

    const st   = STATUS[repo.status] ?? { label: repo.status, dot: 'bg-neutral-600', text: 'text-neutral-400', pulse: false }
    const slug = repo.githubUrl?.replace('https://github.com/', '') ?? ''
    const name = repo.repoName ?? slug.split('/').pop() ?? 'Unknown'

    if (viewMode === 'list') {
        return (
            <div className="group relative flex items-center justify-between gap-4 rounded-xl bg-[#0d0d12]/50 border border-white/[0.06] py-3.5 px-5 transition-all duration-300 hover:border-white/[0.14] hover:bg-[#111114] hover:shadow-lg">
                {/* Left: Info */}
                <div className="flex items-center gap-3 min-w-0 flex-1">
                    <span className="text-neutral-500 shrink-0 p-1.5 bg-white/[0.02] border border-white/[0.04] rounded-lg">
                        <IconGitHub />
                    </span>
                    <div className="min-w-0 flex flex-col sm:flex-row sm:items-center sm:gap-3">
                        <p className="truncate text-[13px] font-semibold text-white/90 tracking-tight hover:text-violet-400 transition-colors cursor-pointer">
                            {name}
                        </p>
                        <p className="truncate text-[10.5px] text-neutral-600 font-mono sm:mt-0.5">
                            {slug}
                        </p>
                    </div>
                </div>

                {/* Right Area */}
                <div className="flex items-center gap-4 shrink-0">
                    {/* Status badge */}
                    <span className={`flex items-center gap-1.5 text-[10px] font-bold uppercase tracking-wider px-2 py-0.5 rounded-md border ${
                        repo.status === 'READY' ? 'bg-emerald-500/5 border-emerald-500/15 text-emerald-400' :
                        repo.status === 'PROCESSING' ? 'bg-violet-500/5 border-violet-500/15 text-violet-400' :
                        repo.status === 'PENDING' ? 'bg-amber-500/5 border-amber-500/15 text-amber-400' :
                        'bg-red-500/5 border-red-500/15 text-red-400'
                    }`}>
                        {repo.status === 'READY' ? (
                            <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" className="w-3 h-3">
                                <path d="M3.5 8.5l3 3 6-6" />
                            </svg>
                        ) : (
                            <span className={`inline-block h-1.5 w-1.5 rounded-full ${
                                repo.status === 'PROCESSING' ? 'bg-violet-400 animate-pulse' :
                                repo.status === 'PENDING' ? 'bg-amber-400 animate-pulse' :
                                'bg-red-400'
                            }`} />
                        )}
                        <span className="hidden sm:inline">{st.label}</span>
                    </span>

                    {/* Files index count */}
                    {repo.status === 'READY' && repo.totalFiles > 0 && (
                        <span className="hidden md:inline text-[11px] text-neutral-600">
                            {repo.totalFiles} files
                        </span>
                    )}

                    {/* Action buttons (only when READY) */}
                    {repo.status === 'READY' && (
                        <div className="flex items-center gap-1">
                            <MiniActionBtn
                                icon={<IconChat />}
                                label="Chat"
                                primary
                                onClick={() => navigate(`/chat/${repo.id}`)}
                            />
                            <MiniActionBtn
                                icon={<IconInterview />}
                                label="Interview"
                                onClick={() => navigate(`/interview/${repo.id}`)}
                            />
                            <MiniActionBtn
                                icon={<IconDebug />}
                                label="Debug"
                                onClick={() => navigate(`/debug/${repo.id}`)}
                            />
                        </div>
                    )}

                    {/* Processing progress bar in list view */}
                    {repo.status === 'PROCESSING' && (
                        <div className="flex items-center gap-2">
                            <div className="w-16 h-1.5 bg-white/[0.05] rounded-full overflow-hidden hidden sm:block">
                                <div className="h-full bg-gradient-to-r from-violet-500 to-fuchsia-500 rounded-full" style={{ width: `${pct}%` }} />
                            </div>
                            <span className="text-[11px] text-neutral-500 tabular-nums">{pct}%</span>
                        </div>
                    )}

                    {repo.status === 'PENDING' && (
                        <span className="text-[11px] text-neutral-600 hidden sm:inline">Queued</span>
                    )}

                    {repo.status === 'FAILED' && (
                        <span className="text-[11px] text-red-400/80 max-w-[120px] truncate hidden sm:inline" title={repo.errorMessage}>
                            Failed
                        </span>
                    )}

                    {/* Remove button */}
                    <button
                        onClick={e => { e.stopPropagation(); onRemove?.(repo.id) }}
                        title="Remove"
                        className="cursor-pointer flex h-7 w-7 items-center justify-center rounded-lg text-neutral-600 hover:bg-red-500/10 hover:text-red-400 transition-colors"
                    >
                        <IconTrash />
                    </button>
                </div>
            </div>
        )
    }

    // Default Grid view card
    return (
        <div className="group relative flex flex-col gap-4 rounded-2xl bg-[#0d0d12]/50 border border-white/[0.06] p-5 transition-all duration-300 hover:border-violet-500/30 hover:bg-[#111115] hover:shadow-[0_12px_30px_rgba(0,0,0,0.3)] hover:-translate-y-0.5">

            {/* ── delete ───────────────────────────────────────────────────── */}
            <button
                onClick={e => { e.stopPropagation(); onRemove?.(repo.id) }}
                title="Remove"
                className="absolute top-4 right-4 flex h-7 w-7 cursor-pointer items-center justify-center rounded-lg text-neutral-600 opacity-0 transition-all duration-200 hover:bg-red-500/10 hover:text-red-400 group-hover:opacity-100"
            >
                <IconTrash />
            </button>

            {/* ── header ───────────────────────────────────────────────────── */}
            <div className="flex items-start gap-2.5 pr-8">

                {/* repo icon */}
                <span className="mt-0.5 shrink-0 text-neutral-500 p-1.5 bg-white/[0.02] border border-white/[0.04] rounded-lg">
                    <IconGitHub />
                </span>

                {/* name + path */}
                <div className="min-w-0 flex-1">
                    <p className="truncate text-[13.5px] font-bold leading-snug text-white/90 tracking-[-0.01em] hover:text-violet-400 transition-colors cursor-pointer">
                        {name}
                    </p>
                    <p className="mt-0.5 truncate text-[11px] text-neutral-600 font-mono">
                        {slug}
                    </p>
                </div>

                {/* status pill — right-aligned, tiny */}
                <span className={`mt-0.5 flex shrink-0 items-center gap-1.5 text-[10px] font-bold uppercase tracking-wider px-2 py-0.5 rounded-md border ${
                    repo.status === 'READY' ? 'bg-emerald-500/5 border-emerald-500/15 text-emerald-400' :
                    repo.status === 'PROCESSING' ? 'bg-violet-500/5 border-violet-500/15 text-violet-400' :
                    repo.status === 'PENDING' ? 'bg-amber-500/5 border-amber-500/15 text-amber-400' :
                    'bg-red-500/5 border-red-500/15 text-red-400'
                }`}>
                    {repo.status === 'READY' ? (
                        <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" className="w-3 h-3">
                            <path d="M3.5 8.5l3 3 6-6" />
                        </svg>
                    ) : (
                        <span className={`inline-block h-1.5 w-1.5 rounded-full ${
                            repo.status === 'PROCESSING' ? 'bg-violet-400 animate-pulse' :
                            repo.status === 'PENDING' ? 'bg-amber-400 animate-pulse' :
                            'bg-red-400'
                        }`} />
                    )}
                    {st.label}
                </span>
            </div>

            {/* ── READY ────────────────────────────────────────────────────── */}
            {repo.status === 'READY' && (
                <>
                    {/* file count */}
                    {repo.totalFiles > 0 && (
                        <div className="flex items-center gap-1.5 text-[11.5px] text-neutral-500 font-medium">
                            <svg className="w-3.5 h-3.5 opacity-60" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth="2">
                                <path strokeLinecap="round" strokeLinejoin="round" d="M3 7v10a2 2 0 002 2h14a2 2 0 002-2V9a2 2 0 00-2-2h-6l-2-2H5a2 2 0 00-2 2z" />
                            </svg>
                            <span>{repo.totalFiles.toLocaleString()} files indexed</span>
                        </div>
                    )}

                    {/* action row */}
                    <div className="flex gap-2 border-t border-white/[0.05] pt-3.5 mt-1">
                        <ActionBtn
                            icon={<IconChat />}
                            label="Chat"
                            primary
                            onClick={() => navigate(`/chat/${repo.id}`)}
                        />
                        <ActionBtn
                            icon={<IconInterview />}
                            label="Interview"
                            onClick={() => navigate(`/interview/${repo.id}`)}
                        />
                        <ActionBtn
                            icon={<IconDebug />}
                            label="Debug"
                            onClick={() => navigate(`/debug/${repo.id}`)}
                        />
                    </div>
                </>
            )}

            {/* ── PROCESSING ───────────────────────────────────────────────── */}
            {repo.status === 'PROCESSING' && (
                <div className="space-y-2.5 bg-violet-500/[0.02] border border-violet-500/5 rounded-xl p-3">
                    <div className="flex justify-between text-[11px]">
                        <span className="text-neutral-400 flex items-center gap-1.5">
                            <svg className="animate-spin h-3.5 w-3.5 text-violet-400" viewBox="0 0 24 24" fill="none">
                                <circle cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="2.5" className="opacity-20" />
                                <path d="M12 2C17.5228 2 22 6.47715 22 12" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" />
                            </svg>
                            {repo.processedFiles > 0
                                ? `${repo.processedFiles.toLocaleString()} / ${repo.totalFiles?.toLocaleString()} files`
                                : 'Indexing codebase…'}
                        </span>
                        <span className="tabular-nums font-semibold text-violet-400">{pct}%</span>
                    </div>
                    <div className="h-1.5 w-full overflow-hidden rounded-full bg-white/[0.04] shadow-inner">
                        <div
                            className="h-full rounded-full bg-gradient-to-r from-violet-500 to-fuchsia-500 transition-all duration-700 shadow-[0_0_8px_rgba(139,92,246,0.3)]"
                            style={{ width: `${Math.max(pct, 3)}%` }}
                        />
                    </div>
                </div>
            )}

            {/* ── PENDING ──────────────────────────────────────────────────── */}
            {repo.status === 'PENDING' && (
                <div className="bg-amber-500/[0.02] border border-amber-500/5 rounded-xl p-3 flex items-center gap-2 text-[11.5px] text-amber-400/80">
                    <svg className="animate-pulse h-3.5 w-3.5 text-amber-400" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5">
                        <circle cx="12" cy="12" r="10" className="opacity-20" />
                        <path d="M12 6v6l4 2" strokeLinecap="round" />
                    </svg>
                    <span>Queued — starting soon…</span>
                </div>
            )}

            {/* ── FAILED ───────────────────────────────────────────────────── */}
            {repo.status === 'FAILED' && (
                <div className="bg-red-500/[0.02] border border-red-500/5 rounded-xl p-3 flex items-start gap-2 text-[11.5px] text-red-400/80">
                    <svg className="h-3.5 w-3.5 text-red-400 mt-0.5 shrink-0" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5">
                        <circle cx="12" cy="12" r="10" className="opacity-20" />
                        <path d="M12 8v4M12 16h.01" strokeLinecap="round" />
                    </svg>
                    <p className="line-clamp-2 leading-relaxed">
                        {repo.errorMessage || 'Ingestion failed.'}
                    </p>
                </div>
            )}
        </div>
    )
}

// ─── action button ────────────────────────────────────────────────────────────

function ActionBtn({ icon, label, onClick, primary }) {
    return (
        <button
            onClick={onClick}
            className={[
                'flex flex-1 items-center justify-center gap-1.5 rounded-xl py-2 text-[12px] font-semibold transition-all duration-200',
                primary
                    ? 'cursor-pointer border border-violet-500/20 bg-violet-500/10 text-violet-300 hover:bg-violet-500/18 hover:text-violet-200 hover:border-violet-500/30'
                    : 'cursor-pointer border border-transparent text-neutral-500 hover:border-white/[0.07] hover:bg-white/[0.04] hover:text-neutral-350',
            ].join(' ')}
        >
            {icon}
            {label}
        </button>
    )
}

// ─── mini action button (for list view) ────────────────────────────────────────

function MiniActionBtn({ icon, label, onClick, primary }) {
    return (
        <button
            onClick={onClick}
            className={`cursor-pointer px-2.5 py-1 rounded-md text-[11.5px] font-semibold transition-all duration-200 flex items-center gap-1 border ${
                primary
                    ? 'bg-violet-500/10 border-violet-500/15 text-violet-300 hover:bg-violet-500/18 hover:text-violet-200'
                    : 'bg-transparent border-transparent text-neutral-500 hover:border-white/[0.06] hover:bg-white/[0.03] hover:text-neutral-300'
            }`}
        >
            {icon}
            <span>{label}</span>
        </button>
    )
}