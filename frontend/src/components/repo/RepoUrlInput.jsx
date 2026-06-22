import { useState } from 'react'

// Owns the URL input and optional token field
// Calls onSubmit(url, token) — parent never touches input state directly
// Token field is hidden by default to keep UI minimal
export default function RepoUrlInput({ onSubmit, disabled }) {
    const [url, setUrl] = useState('')
    const [token, setToken] = useState('')
    const [showToken, setShowToken] = useState(false)

    function handleSubmit() {
        if (!url.trim() || disabled) return
        onSubmit(url.trim(), token.trim() || null)
    }

    return (
        <div className="space-y-3">
            <div className="flex gap-2">
                <input
                    value={url}
                    onChange={e => setUrl(e.target.value)}
                    onKeyDown={e => e.key === 'Enter' && handleSubmit()}
                    placeholder="https://github.com/user/repo"
                    disabled={disabled}
                    className="flex-1 bg-gray-900 border border-gray-700 rounded-lg px-4 py-2.5 text-white text-sm placeholder-gray-500 focus:outline-none focus:border-blue-500 disabled:opacity-50 transition-colors"
                />
                <button
                    onClick={handleSubmit}
                    disabled={disabled || !url.trim()}
                    className="bg-blue-600 hover:bg-blue-500 disabled:opacity-40 disabled:cursor-not-allowed px-5 py-2.5 rounded-lg text-sm font-medium transition-colors whitespace-nowrap"
                >
                    {disabled ? 'Processing...' : 'Analyze'}
                </button>
            </div>

            <button
                type="button"
                onClick={() => setShowToken(v => !v)}
                className="text-xs text-gray-500 hover:text-gray-300 transition-colors"
            >
                {showToken ? '− Hide token' : '+ Private repo? Add GitHub token'}
            </button>

            {showToken && (
                <input
                    value={token}
                    onChange={e => setToken(e.target.value)}
                    placeholder="GitHub Personal Access Token"
                    type="password"
                    className="w-full bg-gray-900 border border-gray-700 rounded-lg px-4 py-2.5 text-white text-sm placeholder-gray-500 focus:outline-none focus:border-blue-500 transition-colors"
                />
            )}
        </div>
    )
}