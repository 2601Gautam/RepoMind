import { Link, useLocation } from 'react-router-dom'
import { useAuth } from '../../context/AuthContext'

// Wordmark logo
function Logo() {
    return (
        <Link to="/dashboard" className="flex items-center gap-2 group shrink-0 select-none">
            <div className="w-6 h-6 bg-gradient-to-br from-violet-500 to-fuchsia-600 rounded-md flex items-center justify-center shadow-[0_0_8px_rgba(139,92,246,0.25)] group-hover:shadow-[0_0_12px_rgba(139,92,246,0.4)] transition-all">
                <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="white" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                    <polyline points="16 18 22 12 16 6" />
                    <polyline points="8 6 2 12 8 18" />
                </svg>
            </div>
            <span className="text-[13px] font-semibold text-white/70 group-hover:text-white/90 tracking-tight transition-colors">
                RepoMind
            </span>
        </Link>
    )
}

// Breadcrumb chevron
function Chevron() {
    return (
        <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" className="w-3 h-3 text-neutral-700 shrink-0">
            <path d="M6 3.5l4.5 4.5-4.5 4.5" />
        </svg>
    )
}

export default function NavBar({ repoName }) {
    const { user } = useAuth()
    const location = useLocation()

    const initials = user?.name
        ? user.name.split(' ').map(n => n[0]).join('').toUpperCase().slice(0, 2)
        : user?.email?.[0]?.toUpperCase() || '?'

    const firstName = user?.name?.split(' ')[0] || user?.email?.split('@')[0] || 'User'

    const isChat      = location.pathname.startsWith('/chat')
    const isInterview = location.pathname.startsWith('/interview')
    const isDebug     = location.pathname.startsWith('/debug')
    const isProfile   = location.pathname === '/profile'
    const isRepos     = location.pathname === '/repositories'
    const isHome      = location.pathname === '/dashboard' && !repoName

    // Current page label for the breadcrumb tail
    const pageLabel = isRepos ? 'Repositories'
        : isChat      ? 'Chat'
        : isInterview ? 'Interview'
        : isDebug     ? 'Debug'
        : isProfile   ? 'Profile'
        : null

    return (
        <header className="sticky top-0 z-40 h-13 flex items-center justify-between px-5 sm:px-7 bg-[#06060a]/80 backdrop-blur-xl border-b border-white/[0.04]">

            {/* ── Left: Logo + breadcrumb ─────────────────────────────── */}
            <div className="flex items-center gap-2 text-[13px] min-w-0">
                <Logo />

                {!isHome && (
                    <>
                        <Chevron />
                        {/* Repo segment — shown when a repo is active */}
                        {repoName && (
                            <>
                                <span className="text-neutral-400 font-medium truncate max-w-[160px]">
                                    {repoName}
                                </span>
                                {pageLabel && <Chevron />}
                            </>
                        )}
                        {/* Page segment */}
                        {pageLabel && (
                            <span className="text-neutral-600 font-medium">
                                {pageLabel}
                            </span>
                        )}
                    </>
                )}
            </div>

            {/* ── Right: profile pill ─────────────────────────────────── */}
            <Link
                to="/profile"
                title={user?.name || user?.email}
                className="group flex items-center gap-2 px-3 py-1.5 rounded-xl border border-white/[0.05] bg-white/[0.02] hover:bg-white/[0.05] hover:border-white/[0.1] transition-all duration-150 shrink-0"
            >
                {/* Avatar */}
                <div className="w-5 h-5 rounded-md bg-gradient-to-br from-violet-700 to-violet-900 flex items-center justify-center text-[9px] font-bold text-white/80 select-none">
                    {initials}
                </div>
                <span className="text-[12px] font-medium text-neutral-500 group-hover:text-neutral-300 transition-colors pr-0.5 hidden sm:block">
                    {firstName}
                </span>
            </Link>
        </header>
    )
}