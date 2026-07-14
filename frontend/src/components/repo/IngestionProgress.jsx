import StatusBadge from '../common/StatusBadge'

// Renders nothing if repo is null, READY, or FAILED
// IngestPage just renders <IngestionProgress repo={current} /> — no conditions needed there
export default function IngestionProgress({ repo }) {
    if (!repo || repo.status === 'READY' || repo.status === 'FAILED') return null

    const pct = repo.totalFiles > 0
        ? Math.round((repo.processedFiles / repo.totalFiles) * 100)
        : 0

    return (
        <div className="mt-6 mx-auto w-full rounded-xl overflow-hidden border border-white/[0.07] shadow-[0_8px_30px_rgba(0,0,0,0.5)]">
            <div className="flex items-center gap-2 px-4 py-3 bg-white/[0.03] border-b border-white/[0.06]">
                <span className="w-3 h-3 rounded-full" style={{ background: '#FF5F57' }} />
                <span className="w-3 h-3 rounded-full" style={{ background: '#FEBC2E' }} />
                <span className="w-3 h-3 rounded-full" style={{ background: '#28C840' }} />
                <span className="ml-3 text-[11px] text-[#4b5563] font-mono">repomind — ai processing</span>
                <span className="ml-auto text-[11px] text-gray-500 font-mono flex items-center gap-1.5">
                    <span className="w-1.5 h-1.5 rounded-full bg-violet-400 animate-pulse"></span>
                    {repo.status}
                </span>
            </div>
            <div className="bg-[#0d0d0d] p-5 md:p-7 font-mono text-[13px] md:text-[14px] leading-[1.8] text-left transition-all duration-300 relative overflow-hidden">
                <div className="absolute top-0 left-0 w-full h-[2px] bg-gradient-to-r from-transparent via-violet-500/50 to-transparent animate-[shimmer_2s_infinite]" />
                <div className="text-gray-500 flex items-center gap-2"><span className="text-violet-400 font-semibold">System:</span> AI Embedding Engine Active</div>
                <div className="text-gray-300 text-xs font-sans space-y-3 mt-4 relative z-10">
                    <p className="text-gray-400 flex items-center gap-2">
                        <span className="text-violet-500/70">✦</span> 
                        Analyzing repository: <span className="text-white font-mono text-[13px]">{repo.repoName}</span>
                    </p>
                    
                    {repo.totalFiles > 0 ? (
                        <>
                            <p className="text-gray-400 flex items-center gap-2">
                                <span className="text-violet-500/70">✦</span> 
                                Processing files... {repo.processedFiles} / {repo.totalFiles} ({pct}%)
                            </p>
                            
                            <div className="w-full max-w-md bg-white/[0.05] rounded-full h-1.5 mt-2 mb-3 overflow-hidden shadow-inner flex">
                                <div
                                    className="bg-gradient-to-r from-violet-500 to-fuchsia-500 h-full rounded-full transition-all duration-500 shadow-[0_0_10px_rgba(139,92,246,0.4)]"
                                    style={{ width: `${pct}%` }}
                                />
                            </div>
                        </>
                    ) : (
                        <p className="flex items-center gap-2 text-violet-300">
                            <svg className="w-3.5 h-3.5 animate-spin text-violet-400" viewBox="0 0 24 24" fill="none">
                                <circle cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="3" strokeDasharray="32" strokeLinecap="round" className="opacity-70" />
                            </svg>
                            AI is scanning repository files...
                        </p>
                    )}
                </div>
            </div>
        </div>
    )
}