// Legacy header — kept for backward compatibility
// New pages use NavBar component instead
import { Link } from 'react-router-dom'
import { useAuth } from '../../context/AuthContext'

export default function Header({ repoName, onLogoClick }) {
    const { user, logout } = useAuth()

    return (
        <header className="sticky top-0 z-50 h-[60px] flex items-center border-b border-gray-800 bg-gray-950/90 backdrop-blur-sm">
            <div className="max-w-5xl w-full mx-auto px-6 flex items-center gap-3">
                <button onClick={onLogoClick}
                    className="text-lg font-bold text-blue-400 hover:text-blue-300 transition-colors">
                    RepoMind
                </button>
                {repoName && (
                    <>
                        <span className="text-gray-700">/</span>
                        <span className="text-sm text-gray-400 truncate max-w-xs">{repoName}</span>
                    </>
                )}
                <div className="ml-auto flex items-center gap-3">
                    {user && (
                        <>
                            <span className="text-sm text-gray-400 hidden sm:block">
                                {user.name || user.email}
                            </span>
                            <button onClick={logout}
                                className="text-xs text-gray-500 hover:text-white border border-gray-700 px-3 py-1.5 rounded-lg transition-colors">
                                Sign out
                            </button>
                        </>
                    )}
                </div>
            </div>
        </header>
    )
}