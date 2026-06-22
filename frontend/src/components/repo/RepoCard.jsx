// One card per repo in the ready list
// onClick passes the full repo object up to IngestPage → App → ChatPage
export default function RepoCard({ repo, onClick }) {
    return (
        <button
            onClick={() => onClick(repo)}
            className="w-full text-left bg-gray-900 hover:bg-gray-800 border border-gray-700 hover:border-gray-600 rounded-lg p-4 transition-all group"
        >
            <div className="flex justify-between items-start gap-3">
                <p className="text-sm font-medium text-white group-hover:text-blue-400 transition-colors truncate">
                    {repo.repoName}
                </p>
                <span className="text-xs text-green-400 bg-green-400/10 border border-green-400/20 px-2 py-0.5 rounded-full shrink-0">
                    READY
                </span>
            </div>
            <p className="text-xs text-gray-500 mt-1">
                {repo.totalChunks?.toLocaleString()} chunks indexed
            </p>
        </button>
    )
}