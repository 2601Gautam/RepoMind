// Renders nothing if repo is null, READY, or FAILED
// IngestPage just renders <IngestionProgress repo={current} /> — no conditions needed there
export default function IngestionProgress({ repo }) {
    if (!repo || repo.status === 'READY' || repo.status === 'FAILED') return null

    const pct = repo.totalFiles > 0
        ? Math.round((repo.processedFiles / repo.totalFiles) * 100)
        : 0

    return (
        <div className="bg-gray-900 border border-gray-700 rounded-lg p-4 space-y-3">
            <div className="flex justify-between items-center">
                <p className="text-sm font-medium text-white truncate">
                    {repo.repoName}
                </p>
                <span className="text-xs text-yellow-400 bg-yellow-400/10 border border-yellow-400/20 px-2 py-0.5 rounded-full">
                    {repo.status}
                </span>
            </div>

            {repo.totalFiles > 0 && (
                <>
                    <div className="bg-gray-800 rounded-full h-1.5 overflow-hidden">
                        <div
                            className="bg-blue-500 h-1.5 rounded-full transition-all duration-500"
                            style={{ width: `${pct}%` }}
                        />
                    </div>
                    <p className="text-xs text-gray-400">
                        {repo.processedFiles} of {repo.totalFiles} files
                        · {repo.totalChunks?.toLocaleString()} chunks created
                    </p>
                </>
            )}
        </div>
    )
}