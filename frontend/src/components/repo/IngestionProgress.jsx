import RepositoryNotice from './RepositoryNotice'
// Displays terminal failures and queued retries as well as live progress.
// IngestPage just renders <IngestionProgress repo={current} /> — no conditions needed there
export default function IngestionProgress({ repo }) {
    if (!repo || repo.status === 'READY') return null
    if (repo.status === 'FAILED') return <RepositoryNotice repo={repo} showName />

    const pct = repo.totalFiles > 0
        ? Math.round((repo.processedFiles / repo.totalFiles) * 100)
        : 0

    const isPending = repo.status === 'PENDING'

    return (
        <div className="mt-6 mx-auto w-full rounded-xl overflow-hidden border border-violet-500/20 bg-[#0d0d0f] shadow-[0_8px_30px_rgba(0,0,0,0.4)] animate-fade-up">
            {/* Shimmer top edge */}
            <div className="absolute top-0 left-0 w-full h-[1.5px] bg-gradient-to-r from-transparent via-violet-500/60 to-transparent" />

            <div className="p-5 space-y-4">
                {/* Header row */}
                <div className="flex items-center justify-between">
                    <div className="flex items-center gap-2.5">
                        <div className="w-7 h-7 rounded-lg bg-violet-500/10 border border-violet-500/20 flex items-center justify-center shrink-0">
                            <svg className="w-3.5 h-3.5 text-violet-400 animate-spin" viewBox="0 0 24 24" fill="none">
                                <circle cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="3" strokeDasharray="32" strokeLinecap="round" className="opacity-60" />
                                <path d="M12 2a10 10 0 0 1 10 10" stroke="currentColor" strokeWidth="3" strokeLinecap="round" />
                            </svg>
                        </div>
                        <div>
                            <p className="text-[12.5px] font-semibold text-white/90 leading-none">
                                {isPending ? (repo.errorMessage ? 'Awaiting retry' : 'Queued for processing') : 'Indexing in progress'}
                            </p>
                            <p className="text-[11px] text-neutral-500 mt-0.5 truncate max-w-[240px]">
                                {repo.repoName || 'Repository'}
                            </p>
                        </div>
                    </div>
                    <span className="text-[11px] text-violet-400 font-mono font-semibold tabular-nums">
                        {repo.totalFiles > 0 ? `${pct}%` : '—'}
                    </span>
                </div>

                {/* Progress bar */}
                <div className="w-full bg-white/[0.05] rounded-full h-1 overflow-hidden">
                    {repo.totalFiles > 0 ? (
                        <div
                            className="bg-gradient-to-r from-violet-500 to-fuchsia-500 h-full rounded-full transition-all duration-500"
                            style={{ width: `${Math.max(pct, 2)}%` }}
                        />
                    ) : (
                        <div className="bg-gradient-to-r from-violet-500/60 to-fuchsia-500/60 h-full rounded-full w-full animate-pulse" />
                    )}
                </div>

                {/* File counts */}
                {repo.totalFiles > 0 && (
                    <p className="text-[11px] text-neutral-500">
                        {(repo.processedFiles || 0).toLocaleString()} of {repo.totalFiles.toLocaleString()} files processed
                    </p>
                )}
                <RepositoryNotice repo={repo} />
            </div>
        </div>
    )
}
