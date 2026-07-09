import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../../context/AuthContext'

// Shared navigation bar used across all protected pages
// repoName: optional breadcrumb shown after the logo
export default function NavBar({ repoName }) {
    const { user, logout } = useAuth()
    const navigate = useNavigate()

    async function handleLogout() {
        await logout()
        navigate('/')
    }

    // Get user initials for avatar circle
    const initials = user?.name
        ? user.name.split(' ').map(n => n[0]).join('').toUpperCase().slice(0, 2)
        : user?.email?.[0]?.toUpperCase() || '?'

    return (
        <header className="sticky top-0 z-50 h-[60px] flex items-center border-b border-gray-800 bg-gray-950/90 backdrop-blur-sm">
            <div className="max-w-5xl w-full mx-auto px-6 flex items-center gap-3">
                <Link to="/dashboard"
                    className="text-lg font-bold text-blue-400 hover:text-blue-300 transition-colors shrink-0">
                    RepoMind
                </Link>

                {repoName && (
                    <>
                        <span className="text-gray-700">/</span>
                        <span className="text-sm text-gray-400 truncate max-w-[200px]">
                            {repoName}
                        </span>
                    </>
                )}

                <div className="ml-auto flex items-center gap-3">
                    <Link to="/profile"
                        className="w-8 h-8 rounded-full bg-blue-600 flex items-center justify-center text-xs font-bold text-white hover:bg-blue-500 transition-colors shrink-0"
                        title={user?.name || user?.email}>
                        {initials}
                    </Link>
                    <button onClick={handleLogout}
                        className="text-xs text-gray-500 hover:text-white border border-gray-700 hover:border-gray-500 px-3 py-1.5 rounded-lg transition-colors hidden sm:block">
                        Sign out
                    </button>
                </div>
            </div>
        </header>
    )
}