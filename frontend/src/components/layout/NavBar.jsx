import { Link, useLocation } from 'react-router-dom'
import { useAuth } from '../../context/AuthContext'

export default function NavBar({ repoName }) {
    const { user } = useAuth()
    const location = useLocation()

    const initials = user?.name
        ? user.name.split(' ').map(n => n[0]).join('').toUpperCase().slice(0, 2)
        : user?.email?.[0]?.toUpperCase() || '?'

    const firstName = user?.name?.split(' ')[0] || user?.email?.split('@')[0] || 'User'
    const isOnDashboard = location.pathname === '/dashboard' && !repoName

    return (
        <header className="sticky top-0 z-40 h-14 flex items-center justify-between px-6 sm:px-8 bg-[#070709]/75 backdrop-blur-md border-b border-white/[0.04]">
            
            {/* ── Breadcrumbs ───────────────────────────────────────────── */}
            <div className="flex items-center gap-1.5 text-[14px] font-sans">
                <Link
                    to="/dashboard"
                    className={`px-2.5 py-1 rounded-md transition-colors duration-150 font-medium ${
                        isOnDashboard
                            ? 'text-white/95'
                            : 'text-neutral-500 hover:text-neutral-300'
                    }`}
                >
                    Dashboard
                </Link>

                {location.pathname === '/repositories' && (
                    <>
                        <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" className="w-3 h-3 text-neutral-700">
                            <path d="M6 3.5l4.5 4.5-4.5 4.5" />
                        </svg>
                        <span className="text-white/90 font-semibold px-2.5 py-1 bg-white/[0.02] border border-white/[0.05] rounded-md">
                            All Repositories
                        </span>
                    </>
                )}

                {repoName && (
                    <>
                        {/* Elegant Chevron */}
                        <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" className="w-3 h-3 text-neutral-700">
                            <path d="M6 3.5l4.5 4.5-4.5 4.5" />
                        </svg>
                        <span className="text-white/90 font-semibold px-2.5 py-1 truncate max-w-[200px] bg-white/[0.02] border border-white/[0.05] rounded-md">
                            {repoName}
                        </span>
                    </>
                )}

                {location.pathname === '/profile' && (
                    <>
                        <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" className="w-3 h-3 text-neutral-700">
                            <path d="M6 3.5l4.5 4.5-4.5 4.5" />
                        </svg>
                        <span className="text-white/90 font-semibold px-2.5 py-1 bg-white/[0.02] border border-white/[0.05] rounded-md">
                            Profile
                        </span>
                    </>
                )}

                {(location.pathname.startsWith('/chat/') || location.pathname === '/chat') && (
                    <>
                        <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" className="w-3 h-3 text-neutral-700">
                            <path d="M6 3.5l4.5 4.5-4.5 4.5" />
                        </svg>
                        <span className="text-neutral-500 font-medium px-2.5 py-1">
                            Chat
                        </span>
                    </>
                )}

                {(location.pathname.startsWith('/interview/') || location.pathname === '/interview') && (
                    <>
                        <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" className="w-3 h-3 text-neutral-700">
                            <path d="M6 3.5l4.5 4.5-4.5 4.5" />
                        </svg>
                        <span className="text-neutral-500 font-medium px-2.5 py-1">
                            Interview
                        </span>
                    </>
                )}

                {(location.pathname.startsWith('/debug/') || location.pathname === '/debug') && (
                    <>
                        <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" className="w-3 h-3 text-neutral-700">
                            <path d="M6 3.5l4.5 4.5-4.5 4.5" />
                        </svg>
                        <span className="text-neutral-500 font-medium px-2.5 py-1">
                            Debug
                        </span>
                    </>
                )}
            </div>

            {/* ── Profile Dropdown / Pill Link ────────────────────────────── */}
            <Link
                to="/profile"
                title={user?.name || user?.email}
                className="cursor-pointer group flex items-center shrink-0"
            >
                <div className="flex items-center gap-2 px-2.5 py-1.5 rounded-full bg-white/[0.02] border border-white/[0.05] hover:bg-white/[0.06] hover:border-white/[0.12] transition-all duration-150">
                    {/* Avatar Circle with gradient background */}
                    <div className="w-5.5 h-5.5 rounded-full bg-gradient-to-br from-neutral-800 to-neutral-900 border border-white/[0.06] flex items-center justify-center text-[10px] font-bold text-neutral-300">
                        {initials}
                    </div>
                    {/* Username label */}
                    <span className="text-[11.5px] font-medium text-neutral-400 group-hover:text-neutral-200 transition-colors pr-0.5">
                        {firstName}
                    </span>
                </div>
            </Link>
        </header>
    )
}