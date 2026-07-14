import { useNavigate } from 'react-router-dom'

// ─── tiny SVG icons (consistent stroke weight: 1.5 - 2) ─────────────────────────

const IconGitHub = () => (
    <svg viewBox="0 0 16 16" fill="currentColor" className="w-4 h-4">
        <path d="M8 0C3.58 0 0 3.58 0 8c0 3.54 2.29 6.53 5.47 7.59.4.07.55-.17.55-.38 0-.19-.01-.82-.01-1.49-2.01.37-2.53-.49-2.69-.94-.09-.23-.48-.94-.82-1.13-.28-.15-.68-.52-.01-.53.63-.01 1.08.58 1.23.82.72 1.21 1.87.87 2.33.66.07-.52.28-.87.51-1.07-1.78-.2-3.64-.89-3.64-3.95 0-.87.31-1.59.82-2.15-.08-.2-.36-1.02.08-2.12 0 0 .67-.21 2.2.82.64-.18 1.32-.27 2-.27.68 0 1.36.09 2 .27 1.53-1.04 2.2-.82 2.2-.82.44 1.1.16 1.92.08 2.12.51.56.82 1.27.82 2.15 0 3.07-1.87 3.75-3.65 3.95.29.25.54.73.54 1.48 0 1.07-.01 1.93-.01 2.2 0 .21.15.46.55.38A8.013 8.013 0 0016 8c0-4.42-3.58-8-8-8z" />
    </svg>
)

const IconChat = () => (
    <svg viewBox="0 0 20 20" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="w-3.5 h-3.5">
        <path d="M17.5 11.667a1.667 1.667 0 01-1.667 1.666H5.833L2.5 16.667V4.167A1.667 1.667 0 014.167 2.5H15.833A1.667 1.667 0 0117.5 4.167v7.5z" />
    </svg>
)

const IconInterview = () => (
    <svg viewBox="0 0 20 20" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="w-3.5 h-3.5">
        <path d="M13.333 2.5H6.667A1.667 1.667 0 005 4.167v11.666A1.667 1.667 0 006.667 17.5h6.666A1.667 1.667 0 0015 15.833V4.167A1.667 1.667 0 0013.333 2.5z" />
        <path d="M7.5 6.667h5M7.5 10h5M7.5 13.333h2.5" />
    </svg>
)

const IconDebug = () => (
    <svg viewBox="0 0 20 20" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="w-3.5 h-3.5">
        <path d="M7.5 2.5a2.5 2.5 0 015 0M5.833 5.833S5 6.667 5 8.333H15c0-1.666-.833-2.5-.833-2.5" />
        <path d="M2.5 10h15M5 13.333l-2.5 2.5M15 13.333l2.5 2.5M6.667 17.5h6.666" />
        <rect x="5" y="8.333" width="10" height="5" rx="1" />
    </svg>
)

const IconTrash = () => (
    <svg viewBox="0 0 20 20" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="w-4 h-4">
        <path d="M2.5 5h15M15.833 5l-.833 11.667a.833.833 0 01-.833.833H5.833a.833.833 0 01-.833-.833L4.167 5M7.5 5V3.333a.833.833 0 01.833-.833h3.334a.833.833 0 01.833.833V5" />
    </svg>
)

// ─── status meta ──────────────────────────────────────────────────────────────

const STATUS = {
    READY:      { label: 'Ready',    dot: 'bg-emerald-500' },
    PROCESSING: { label: 'Indexing', dot: 'bg-blue-500 animate-pulse' },
    PENDING:    { label: 'Queued',   dot: 'bg-amber-500' },
    FAILED:     { label: 'Failed',   dot: 'bg-red-500' },
}

// ─── main component ───────────────────────────────────────────────────────────

export default function RepoCard({ repo, onRemove, viewMode = 'grid' }) {
    const navigate = useNavigate()

    const pct = repo.totalFiles > 0
        ? Math.round((repo.processedFiles / repo.totalFiles) * 100)
        : 0

    const st = STATUS[repo.status] ?? { label: repo.status, dot: 'bg-neutral-600' }

    // Never render failed repos in any view
    if (repo.status === 'FAILED') return null
    
    // Parse owner and repo separately for a cleaner UI
    const slugRaw = repo.githubUrl?.replace('https://github.com/', '').replace(/\/$/, '') ?? ''
    const slugParts = slugRaw.split('/')
    const owner = slugParts.length >= 2 ? slugParts[0] : ''
    const repoStr = slugParts.length >= 2 ? slugParts.slice(1).join('/') : slugRaw || 'Unknown'

    if (viewMode === 'list') {
        return (
            <div className="group relative flex items-center justify-between gap-4 rounded-xl bg-[#0a0a0a] border border-white/[0.08] py-3.5 px-4 hover:border-white/[0.15] hover:bg-[#111] transition-colors">
                {/* Left: Info */}
                <div className="flex items-center gap-3 min-w-0 flex-1">
                    <span className="text-neutral-400">
                        <IconGitHub />
                    </span>
                    <div className="min-w-0 flex items-center gap-1.5 text-[14px]">
                        {owner && (
                            <>
                                <span className="text-neutral-500 truncate max-w-[120px] sm:max-w-[180px]">{owner}</span>
                                <span className="text-neutral-600">/</span>
                            </>
                        )}
                        <span className="font-semibold text-neutral-200 truncate tracking-tight">{repoStr}</span>
                    </div>
                </div>

                {/* Right Area */}
                <div className="flex items-center gap-5 shrink-0">
                    {/* Files index count */}
                    {repo.status === 'READY' && repo.totalFiles > 0 && (
                        <span className="hidden md:block text-[12px] text-neutral-500">
                            {repo.totalFiles.toLocaleString()} files
                        </span>
                    )}

                    {/* Processing progress bar in list view */}
                    {repo.status === 'PROCESSING' && (
                        <div className="flex items-center gap-2">
                            <div className="w-16 h-1 bg-neutral-800 rounded-full overflow-hidden hidden sm:block">
                                <div className="h-full bg-white rounded-full" style={{ width: `${Math.max(pct, 2)}%` }} />
                            </div>
                            <span className="text-[12px] text-neutral-400 tabular-nums">{pct}%</span>
                        </div>
                    )}

                    {/* Action buttons (only when READY) */}
                    {repo.status === 'READY' && (
                        <div className="flex items-center gap-2">
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

                    {/* Status badge */}
                    <div className="flex items-center gap-2 text-[12px] font-medium text-neutral-400 min-w-[70px] justify-end">
                        <span className={`w-2 h-2 rounded-full ${st.dot}`} />
                        <span className="hidden sm:inline">{st.label}</span>
                    </div>

                    {/* Remove button */}
                    <button
                        onClick={e => { e.stopPropagation(); onRemove?.(repo.id) }}
                        title="Remove"
                        className="cursor-pointer text-neutral-600 hover:text-red-400 transition-colors"
                    >
                        <IconTrash />
                    </button>
                </div>
            </div>
        )
    }

    // Default Grid view card (Professional)
    return (
        <div className="group relative flex flex-col rounded-xl bg-[#0a0a0a] border border-white/[0.08] p-5 hover:border-white/[0.15] hover:bg-[#111] transition-colors">
            
            {/* Header Section */}
            <div className="flex items-start justify-between gap-4">
                
                {/* Repo Info */}
                <div className="min-w-0 flex-1">
                    <div className="flex items-center gap-2 text-[14px] leading-tight">
                        <span className="text-neutral-400 shrink-0"><IconGitHub /></span>
                        <div className="flex items-center gap-1.5 truncate">
                            {owner && (
                                <>
                                    <span className="text-neutral-400 font-medium truncate max-w-[120px]">{owner}</span>
                                    <span className="text-neutral-600">/</span>
                                </>
                            )}
                            <span className="font-semibold text-neutral-200 truncate tracking-tight">{repoStr}</span>
                        </div>
                    </div>
                    {repo.status === 'READY' && repo.totalFiles > 0 && (
                        <div className="mt-2 text-[12px] text-neutral-500 pl-6">
                            {repo.totalFiles.toLocaleString()} files indexed
                        </div>
                    )}
                </div>

                {/* Top-Right Area: Status & Delete */}
                <div className="shrink-0 flex items-center gap-3">
                    <button
                        onClick={e => { e.stopPropagation(); onRemove?.(repo.id) }}
                        title="Remove"
                        className="cursor-pointer opacity-0 group-hover:opacity-100 text-neutral-500 hover:text-red-400 transition-colors"
                    >
                        <IconTrash />
                    </button>
                    <div className="flex items-center gap-1.5 text-[12px] font-medium text-neutral-400">
                        <span className={`w-2 h-2 rounded-full ${st.dot}`} />
                        <span>{st.label}</span>
                    </div>
                </div>
            </div>

            {/* ── PROCESSING ───────────────────────────────────────────────── */}
            {repo.status === 'PROCESSING' && (
                <div className="mt-4 pl-6">
                    <div className="flex justify-between text-[12px] text-neutral-400 mb-2">
                        <span>{repo.processedFiles} / {repo.totalFiles} files</span>
                        <span className="tabular-nums">{pct}%</span>
                    </div>
                    <div className="h-1 w-full bg-neutral-800 rounded-full overflow-hidden">
                        <div
                            className="h-full bg-white rounded-full transition-all"
                            style={{ width: `${Math.max(pct, 2)}%` }}
                        />
                    </div>
                </div>
            )}

            {/* ── PENDING / FAILED ─────────────────────────────────────────── */}
            {repo.status === 'FAILED' && (
                <div className="mt-4 pl-6 text-[12px] text-red-400">
                    <p className="line-clamp-2">{repo.errorMessage || 'Ingestion failed unexpectedly.'}</p>
                </div>
            )}
            
            {/* ── READY Footer Actions ────────────────────────────────────── */}
            {repo.status === 'READY' && (
                <div className="mt-5 flex gap-2 pl-6">
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
                'cursor-pointer flex flex-1 items-center justify-center gap-1.5 rounded-lg py-2 text-[12.5px] font-medium transition-colors',
                primary
                    ? 'bg-white text-black hover:bg-neutral-200'
                    : 'bg-neutral-900 border border-white/[0.08] text-neutral-300 hover:bg-neutral-800 hover:text-white',
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
            className={`cursor-pointer px-2.5 py-1.5 rounded-md text-[11.5px] font-medium transition-colors flex items-center gap-1.5 ${
                primary
                    ? 'bg-white text-black hover:bg-neutral-200'
                    : 'bg-neutral-900 border border-white/[0.08] text-neutral-300 hover:bg-neutral-800 hover:text-white'
            }`}
        >
            {icon}
            <span>{label}</span>
        </button>
    )
}