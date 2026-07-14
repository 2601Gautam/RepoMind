import { Link, useLocation } from 'react-router-dom'

const navItems = [
    {
        group: 'Main',
        items: [
            {
                name: 'Dashboard',
                path: '/dashboard',
                icon: (
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.9" strokeLinecap="round" strokeLinejoin="round">
                        <path d="M4 4h6v8h-6z" />
                        <path d="M4 16h6v4h-6z" />
                        <path d="M14 12h6v8h-6z" />
                        <path d="M14 4h6v4h-6z" />
                    </svg>
                )
            },
        ]
    },
    {
        group: 'Tools',
        items: [
            {
                name: 'Chat',
                path: '/chat',
                icon: (
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.9" strokeLinecap="round" strokeLinejoin="round">
                        <path d="M8 9l3 3-3 3" />
                        <path d="M13 15h3" />
                        <rect x="3" y="4" width="18" height="16" rx="2" />
                    </svg>
                )
            },
            {
                name: 'Debug',
                path: '/debug',
                icon: (
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.9" strokeLinecap="round" strokeLinejoin="round">
                        <path d="M9 9v-1a3 3 0 0 1 6 0v1" />
                        <path d="M8 9h8a6 6 0 0 1 1 3v3a5 5 0 0 1 -10 0v-3a6 6 0 0 1 1 -3" />
                        <path d="M3 13l4 0" />
                        <path d="M17 13l4 0" />
                        <path d="M12 20l0 -6" />
                        <path d="M4 19l3.35 -2" />
                        <path d="M20 19l-3.35 -2" />
                        <path d="M4 7l3.75 2.4" />
                        <path d="M20 7l-3.75 2.4" />
                    </svg>
                )
            },
            {
                name: 'Interview',
                path: '/interview',
                icon: (
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.9" strokeLinecap="round" strokeLinejoin="round">
                        <path d="M22 9l-10 -4l-10 4l10 4l10 -4v6" />
                        <path d="M6 10.6v5.4a6 3 0 0 0 12 0v-5.4" />
                    </svg>
                )
            },
        ]
    },
    {
        group: 'Library',
        items: [
            {
                name: 'Repositories',
                path: '/repositories',
                icon: (
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.9" strokeLinecap="round" strokeLinejoin="round">
                        <circle cx="6" cy="6" r="2" />
                        <circle cx="6" cy="18" r="2" />
                        <circle cx="18" cy="6" r="2" />
                        <line x1="6" y1="8" x2="6" y2="16" />
                        <path d="M8 6h4a2 2 0 0 1 2 2v1" />
                    </svg>
                )
            },
            {
                name: 'Profile',
                path: '/profile',
                icon: (
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.9" strokeLinecap="round" strokeLinejoin="round">
                        <circle cx="12" cy="7" r="4" />
                        <path d="M6 21v-2a4 4 0 0 1 4-4h4a4 4 0 0 1 4 4v2" />
                    </svg>
                )
            }
        ]
    }
]

export default function Sidebar({ isOpen = true, onToggle }) {
    const location = useLocation()

    function isActive(path) {
        if (path === '/dashboard') return location.pathname === '/dashboard'
        return location.pathname.startsWith(path)
    }

    return (
        <aside className={`fixed inset-y-0 left-0 bg-[#060608] border-r border-white/[0.04] flex flex-col z-40 transition-all duration-300 ${isOpen ? 'w-60' : 'w-[72px]'}`}>
            {/* Logo */}
            <div className="h-[60px] flex items-center justify-between px-5 shrink-0 border-b border-white/[0.04]">
                <Link to="/dashboard" className={`flex items-center gap-2.5 group ${isOpen ? '' : 'hidden'}`}>
                    <div className="w-7 h-7 bg-gradient-to-br from-violet-500 to-fuchsia-600 rounded-lg flex items-center justify-center shadow-[0_0_12px_rgba(139,92,246,0.35)] group-hover:shadow-[0_0_18px_rgba(139,92,246,0.5)] transition-all">
                        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="white" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                            <polyline points="16 18 22 12 16 6" />
                            <polyline points="8 6 2 12 8 18" />
                        </svg>
                    </div>
                    <span className="text-[15px] font-bold text-white tracking-tight">RepoMind</span>
                </Link>
                {/* Toggle Button */}
                <button
                    onClick={onToggle}
                    className={`cursor-pointer p-1.5 rounded-lg text-neutral-500 hover:text-white hover:bg-white/[0.06] transition-colors ${isOpen ? '' : 'mx-auto'}`}
                    title={isOpen ? "Collapse Sidebar" : "Expand Sidebar"}
                >
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" className="w-4 h-4">
                        {isOpen ? (
                            <path strokeLinecap="round" strokeLinejoin="round" d="M15 19l-7-7 7-7" />
                        ) : (
                            <path strokeLinecap="round" strokeLinejoin="round" d="M9 5l7 7-7 7" />
                        )}
                    </svg>
                </button>
            </div>

            {/* Navigation groups */}
            <nav className={`flex-1 ${isOpen ? 'px-3 py-4' : 'px-2 py-4'} space-y-5 overflow-y-auto`}>
                {navItems.map((group) => (
                    <div key={group.group}>
                        {isOpen && (
                            <p className="text-[9.5px] font-bold uppercase tracking-[0.1em] text-neutral-600 px-2.5 mb-1.5">
                                {group.group}
                            </p>
                        )}
                        <div className="space-y-1">
                            {group.items.map((item) => {
                                const active = isActive(item.path)
                                return (
                                    <Link
                                        key={item.name}
                                        to={item.path}
                                        title={!isOpen ? item.name : undefined}
                                        className={`flex items-center rounded-lg text-[13px] font-medium transition-all duration-150 ${
                                            isOpen ? 'gap-3 px-2.5 py-2' : 'justify-center p-2.5'
                                        } ${
                                            active
                                                ? 'bg-white/[0.07] text-white'
                                                : 'text-neutral-500 hover:bg-white/[0.03] hover:text-neutral-300'
                                        }`}
                                    >
                                        <span className={`${active ? 'text-violet-400' : 'text-neutral-600'} shrink-0`}>
                                            {item.icon}
                                        </span>
                                        {isOpen && (
                                            <>
                                                <span className="truncate">{item.name}</span>
                                                {active && (
                                                    <span className="ml-auto w-1 h-1 rounded-full bg-violet-400 shrink-0" />
                                                )}
                                            </>
                                        )}
                                    </Link>
                                )
                            })}
                        </div>
                    </div>
                ))}
            </nav>

            {/* Bottom hint */}
            {isOpen && (
                <div className="px-5 py-4 border-t border-white/[0.04]">
                    <p className="text-[10.5px] text-neutral-700 leading-relaxed">
                        Ingest a repo on the dashboard first to unlock tools.
                    </p>
                </div>
            )}
        </aside>
    )
}
