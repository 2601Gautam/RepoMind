import { useNavigate } from 'react-router-dom'
import StatusBadge from '../common/StatusBadge'

// Repo card used in Dashboard
// Shows different content based on status:
//   READY: action buttons (Chat, Interview, Debug)
//   PROCESSING: progress bar with file count
//   FAILED: error message
//   PENDING: waiting indicator
export default function RepoCard({ repo }) {
    const navigate = useNavigate()

    const pct = repo.totalFiles > 0
        ? Math.round((repo.processedFiles / repo.totalFiles) * 100)
        : 0

    return (
        <div className="bg-gray-900 border border-gray-700 rounded-lg p-4 space-y-3 hover:border-gray-600 transition-colors">
            <div className="flex justify-between items-start gap-2">
                <p className="text-sm font-medium text-white font-mono truncate">
                    {repo.repoName}
                </p>
                <StatusBadge status={repo.status} />
            </div>

            {/* PROCESSING: show progress bar */}
            {repo.status === 'PROCESSING' && (
                <div className="space-y-1.5">
                    <div className="bg-gray-800 rounded-full h-1.5 overflow-hidden">
                        <div
                            className="bg-blue-500 h-1.5 rounded-full transition-all duration-500"
                            style={{ width: `${pct}%` }}
                        />
                    </div>
                    <p className="text-xs text-gray-500">
                        {repo.processedFiles} of {repo.totalFiles} files
                        {repo.totalChunks > 0 && ` · ${repo.totalChunks?.toLocaleString()} chunks`}
                    </p>
                </div>
            )}

            {/* READY: chunk count + action buttons */}
            {repo.status === 'READY' && (
                <>
                    <p className="text-xs text-gray-500">
                        {repo.totalChunks?.toLocaleString()} chunks indexed
                    </p>
                    <div className="flex gap-2 pt-1">
                        <button
                            onClick={() => navigate(`/chat/${repo.id}`)}
                            className="flex-1 text-xs bg-blue-600 hover:bg-blue-500 text-white py-1.5 rounded-lg transition-colors font-medium"
                        >
                            Chat
                        </button>
                        <button
                            onClick={() => navigate(`/interview/${repo.id}`)}
                            className="flex-1 text-xs bg-gray-800 hover:bg-gray-700 border border-gray-700 text-gray-300 hover:text-white py-1.5 rounded-lg transition-colors"
                        >
                            Interview
                        </button>
                        <button
                            onClick={() => navigate(`/debug/${repo.id}`)}
                            className="flex-1 text-xs bg-gray-800 hover:bg-gray-700 border border-gray-700 text-gray-300 hover:text-white py-1.5 rounded-lg transition-colors"
                        >
                            Debug
                        </button>
                    </div>
                </>
            )}

            {/* FAILED: error message */}
            {repo.status === 'FAILED' && repo.errorMessage && (
                <p className="text-xs text-red-400 line-clamp-2">{repo.errorMessage}</p>
            )}

            {/* PENDING: waiting */}
            {repo.status === 'PENDING' && (
                <p className="text-xs text-gray-500">Waiting to start...</p>
            )}
        </div>
    )
}