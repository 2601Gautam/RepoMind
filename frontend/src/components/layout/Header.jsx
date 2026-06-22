// Header is its own component so App.jsx stays clean
// Any future nav additions (user avatar, theme toggle) go here only
export default function Header({ repoName, onLogoClick }) {
    return (
        <header className="sticky top-0 z-10 border-b border-gray-800 bg-gray-950/80 backdrop-blur-sm">
            <div className="max-w-4xl mx-auto px-6 py-4 flex items-center gap-2">
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
            </div>
        </header>
    )
}
