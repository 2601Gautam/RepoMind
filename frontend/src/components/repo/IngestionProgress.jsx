import StatusBadge from '../common/StatusBadge'

// Renders nothing if repo is null, READY, or FAILED
// IngestPage just renders <IngestionProgress repo={current} /> — no conditions needed there
export default function IngestionProgress({ repo }) {
    if (!repo || repo.status === 'READY' || repo.status === 'FAILED') return null

    const pct = repo.totalFiles > 0
        ? Math.round((repo.processedFiles / repo.totalFiles) * 100)
        : 0

    return (
        <div className="bg-white/[0.02] border border-white/[0.05] rounded-xl p-5 space-y-4 shadow-sm relative overflow-hidden">
            <div className="absolute top-0 inset-x-0 h-[1px] bg-gradient-to-r from-transparent via-amber-500/20 to-transparent" />
            
            <div className="flex justify-between items-center gap-4">
                <div className="flex items-center gap-3 min-w-0">
                    <svg className="animate-spin shrink-0 h-5 w-5 text-amber-500/80" viewBox="0 0 24 24" fill="none">
                        <circle cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="2.5" className="opacity-20" />
                        <path d="M12 2C17.5228 2 22 6.47715 22 12" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" />
                    </svg>
                    <p className="text-[15px] font-medium text-white truncate tracking-tight">
                        {repo.repoName}
                    </p>
                </div>
                <StatusBadge status={repo.status} />
            </div>

            {repo.totalFiles > 0 && (
                <div className="space-y-2 pt-1">
                    <div className="flex justify-between text-[11px] font-semibold text-[#888] uppercase tracking-wider">
                        <span>Ingesting files...</span>
                        <span>{pct}%</span>
                    </div>
                    <div className="bg-white/[0.05] rounded-full h-1.5 overflow-hidden shadow-inner">
                        <div
                            className="bg-gradient-to-r from-amber-500 to-orange-500 h-full rounded-full transition-all duration-500 shadow-[0_0_10px_rgba(245,158,11,0.5)]"
                            style={{ width: `${pct}%` }}
                        />
                    </div>
                    <div className="text-[13px] text-[#666] font-medium pt-1 flex items-center justify-between">
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