export default function Header({ repoName, onLogoClick, user, onLogout }) {
    return (
        <header className="sticky top-0 z-10 h-[65px] flex items-center border-b border-gray-800 bg-gray-950/80 backdrop-blur-sm">
            <div className="max-w-4xl w-full mx-auto px-6 flex items-center gap-2">
                <button
                    onClick={onLogoClick}
                    className="text-xl font-bold text-blue-400 hover:text-blue-300 transition-colors"
                >
                    RepoMind
                </button>

                {repoName && (
                    <>
                        <span className="text-gray-600">/</span>
                        <span className="text-sm text-gray-400 truncate max-w-xs">
                            {repoName}
                        </span>
                    </>
                )}

                {/* Push user info to the right */}
                <div className="ml-auto flex items-center gap-3">
                    {user && (
                        <>
                            <span className="text-sm text-gray-400 hidden sm:block">
                                {user.name || user.email}
                            </span>
                            <button
                                onClick={onLogout}
                                className="text-xs text-gray-500 hover:text-white border border-gray-700 hover:border-gray-500 px-3 py-1.5 rounded-lg transition-colors"
                            >
                                Sign out
                            </button>
                        </>
                    )}
                </div>
            </div>
        </header>
    )
}