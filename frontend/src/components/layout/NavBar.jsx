import { Link, useLocation, useParams } from 'react-router-dom'
import { useAuth } from '../../context/AuthContext'
import logo from "../../../dist/websiteLogo.png"
// Wordmark logo
function Logo() {
    return (
            <Link
            to="/dashboard"
            className="flex items-center gap-2 group shrink-0 select-none"
        >
            <div className="w-12 h-12 flex items-center justify-center">
                <img
                    src={logo}
                    alt="RepoMind Logo"
                    className="w-full h-full object-contain transition-transform"
                    draggable={false}
                />
            </div>

            <span className="text-[15.5px] font-bold text-white  tracking-tight ">
                RepoMind
            </span>
        </Link>
    )
}

// Breadcrumb chevron
function Chevron() {
    return (
        <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" className="w-3.5 h-3.5 text-neutral-600 shrink-0">
            <path d="M6 3.5l4.5 4.5-4.5 4.5" />
        </svg>
    )
}

export default function NavBar({ repoName, action }) {
    const { user } = useAuth()
    const location = useLocation()
    const { repoId } = useParams()

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
        <header className="sticky top-0 z-40 h-14 flex items-center justify-between px-5 sm:px-8 bg-[#07070b]/90 backdrop-blur-xl border-b border-white/[0.04]">

            {/* ── Left: Logo + breadcrumb ─────────────────────────────── */}
            <div className="flex items-center gap-3 text-[13px] min-w-0 flex-1">
                <Logo />

                {!isHome && (
                    <>
                        {pageLabel && (
                            <>
                                <Chevron />
                                {repoId ? (
                                    <Link to={`/${pageLabel.toLowerCase()}`} className="text-neutral-500 hover:text-white font-semibold transition-colors duration-200">
                                        {pageLabel}
                                    </Link>
                                ) : (
                                    <span className="text-neutral-500 font-semibold">
                                        {pageLabel}
                                    </span>
                                )}
                            </>
                        )}
                        {repoName && (
                            <>
                                <Chevron />
                                {repoId && pageLabel ? (
                                    <Link to={`/${pageLabel.toLowerCase()}/${repoId}`} className="text-neutral-400 hover:text-neutral-350 font-medium truncate max-w-[150px] transition-colors duration-200">
                                        {repoName}
                                    </Link>
                                ) : (
                                    <span className="text-neutral-400 font-medium truncate max-w-[150px]">
                                        {repoName}
                                    </span>
                                )}
                            </>
                        )}
                    </>
                )}
            </div>

            {/* ── Center: Unified Quick Tool Navigation (when repo context is present) ── */}
            {repoId && (
                <div className="hidden sm:flex items-center bg-white/[0.02] border border-white/[0.05] p-1 rounded-xl gap-0.5 mx-4">
                    <Link
                        to={`/chat/${repoId}`}
                        className={`text-[11.5px] px-3.5 py-1.5 rounded-lg transition-all duration-200 font-semibold ${
                            isChat
                                ? 'bg-white/[0.08] border-white/[0.08] text-white shadow-sm'
                                : 'text-neutral-500 hover:text-neutral-300'
                        }`}
                    >
                        Chat
                    </Link>
                    <Link
                        to={`/interview/${repoId}`}
                        className={`text-[11.5px] px-3.5 py-1.5 rounded-lg transition-all duration-200 font-semibold ${
                            isInterview
                                ? 'bg-white/[0.08] border-white/[0.08] text-white shadow-sm'
                                : 'text-neutral-500 hover:text-neutral-350'
                        }`}
                    >
                        Interview
                    </Link>
                    <Link
                        to={`/debug/${repoId}`}
                        className={`text-[11.5px] px-3.5 py-1.5 rounded-lg transition-all duration-200 font-semibold ${
                            isDebug
                                ? 'bg-white/[0.08] border-white/[0.08] text-white shadow-sm'
                                : 'text-neutral-500 hover:text-neutral-300'
                        }`}
                    >
                        Debug
                    </Link>
                </div>
            )}

            {/* ── Right: Custom action + Profile pill ─────────────────── */}
            <div className="flex items-center gap-3 shrink-0">
                {action && <div className="flex items-center">{action}</div>}
                
                <Link
                    to="/profile"
                    title={user?.name || user?.email}
                    className="group flex items-center gap-2 px-3 py-1.5 rounded-xl border border-white/[0.05] bg-white/[0.02] hover:bg-white/[0.05] hover:border-white/[0.1] transition-all duration-150"
                >
                    <div className="w-5.5 h-5.5 rounded-md bg-gradient-to-br from-violet-600/40 to-fuchsia-600/40 border border-white/[0.1] flex items-center justify-center text-[10px] font-bold text-white select-none shadow-lg shadow-violet-500/20">
                        {initials}
                    </div>
                    <span className="text-[12px] font-semibold text-neutral-500 group-hover:text-neutral-300 transition-colors pr-0.5 hidden md:block">
                        {firstName}
                    </span>
                </Link>
            </div>
        </header>
    )
}