import StatusBadge from '../common/StatusBadge'

// Renders nothing if repo is null, READY, or FAILED
// IngestPage just renders <IngestionProgress repo={current} /> — no conditions needed there
export default function IngestionProgress({ repo }) {
    if (!repo || repo.status === 'READY' || repo.status === 'FAILED') return null

    const pct = repo.totalFiles > 0
        ? Math.round((repo.processedFiles / repo.totalFiles) * 100)
        : 0

    return (
        <div className="bg-[#0f0f13]/60 border border-white/[0.06] rounded-xl p-5 space-y-4 shadow-inner relative overflow-hidden backdrop-blur-md">
            <div className="absolute top-0 inset-x-0 h-[1px] bg-gradient-to-r from-transparent via-violet-500/20 to-transparent" />
            
            <div className="flex justify-between items-center gap-4">
                <div className="flex items-center gap-3 min-w-0">
                    <svg className="animate-spin shrink-0 h-5 w-5 text-violet-400" viewBox="0 0 24 24" fill="none">
                        <circle cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="2.5" className="opacity-20" />
                        <path d="M12 2C17.5228 2 22 6.47715 22 12" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" />
                    </svg>
                    <p className="text-[14px] font-bold text-white truncate tracking-tight">
                        {repo.repoName}
                    </p>
                </div>
                <StatusBadge status={repo.status} />
            </div>

            {repo.totalFiles > 0 && (
                <div className="space-y-2 pt-1">
                    <div className="flex justify-between text-[11px] font-bold text-neutral-400 uppercase tracking-wider">
                        <span>Ingesting files...</span>
                        <span>{pct}%</span>
                    </div>
                    <div className="bg-white/[0.05] rounded-full h-1.5 overflow-hidden shadow-inner">
                        <div
                            className="bg-gradient-to-r from-violet-500 to-fuchsia-500 h-full rounded-full transition-all duration-500 shadow-[0_0_10px_rgba(139,92,246,0.4)]"
                            style={{ width: `${pct}%` }}
                        />
                    </div>
                    <div className="text-[12.5px] text-neutral-500 font-semibold pt-1 flex items-center justify-between">
                        <span>{repo.processedFiles} of {repo.totalFiles} files</span>
                        {repo.totalChunks > 0 && (
                            <span>{repo.totalChunks.toLocaleString()} chunks indexed</span>
                        )}
                    </div>
                </div>
            )}
        </div>
    )
}