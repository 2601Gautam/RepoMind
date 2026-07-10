import { useState, useEffect } from 'react'

export default function RepoUrlInput({ onSubmit, disabled }) {
    const [url,       setUrl]       = useState('')
    const [token,     setToken]     = useState('')
    const [showToken, setShowToken] = useState(false)
    const [showPwd,   setShowPwd]   = useState(false)
    const [parsedRepo, setParsedRepo] = useState(null)

    useEffect(() => {
        const githubRegex = /github\.com\/([^/]+)\/([^/]+)/
        const match = url.match(githubRegex)
        if (match) {
            setParsedRepo({
                owner: match[1],
                name: match[2].replace(/\.git$/, '')
            })
        } else {
            setParsedRepo(null)
        }
    }, [url])

    function handleSubmit() {
        if (!url.trim() || disabled) return
        onSubmit(url.trim(), token.trim() || null)
        setUrl('')
        setToken('')
        setShowToken(false)
    }

    return (
        <div className="space-y-4">

            {/* ── unified search bar ─────────────────────────────────────── */}
            <div className="flex items-center bg-[#0d0d12]/80 border border-white/[0.08] rounded-xl px-4 focus-within:border-violet-500/40 focus-within:ring-4 focus-within:ring-violet-500/10 transition-all duration-300 shadow-inner">
                {/* github icon */}
                <svg viewBox="0 0 16 16" fill="currentColor" className={`w-4 h-4 shrink-0 text-neutral-500 mr-3 transition-colors ${parsedRepo ? 'text-violet-400' : ''}`}>
                    <path d="M8 0C3.58 0 0 3.58 0 8c0 3.54 2.29 6.53 5.47 7.59.4.07.55-.17.55-.38 0-.19-.01-.82-.01-1.49-2.01.37-2.53-.49-2.69-.94-.09-.23-.48-.94-.82-1.13-.28-.15-.68-.52-.01-.53.63-.01 1.08.58 1.23.82.72 1.21 1.87.87 2.33.66.07-.52.28-.87.51-1.07-1.78-.2-3.64-.89-3.64-3.95 0-.87.31-1.59.82-2.15-.08-.2-.36-1.02.08-2.12 0 0 .67-.21 2.2.82.64-.18 1.32-.27 2-.27.68 0 1.36.09 2 .27 1.53-1.04 2.2-.82 2.2-.82.44 1.1.16 1.92.08 2.12.51.56.82 1.27.82 2.15 0 3.07-1.87 3.75-3.65 3.95.29.25.54.73.54 1.48 0 1.07-.01 1.93-.01 2.2 0 .21.15.46.55.38A8.013 8.013 0 0016 8c0-4.42-3.58-8-8-8z" />
                </svg>

                <input
                    value={url}
                    onChange={e => setUrl(e.target.value)}
                    onKeyDown={e => e.key === 'Enter' && handleSubmit()}
                    placeholder="Paste repository link (e.g. github.com/user/repo)"
                    disabled={disabled}
                    className="flex-1 bg-transparent py-4 text-[14px] text-white placeholder-neutral-500 focus:outline-none disabled:opacity-40 font-sans"
                />

                <button
                    onClick={handleSubmit}
                    disabled={disabled || !url.trim()}
                    className={`cursor-pointer ml-3 shrink-0 text-[13px] font-bold px-5 py-2 rounded-xl transition-all duration-200 ${
                        disabled
                            ? 'bg-white/10 text-white/40 cursor-wait'
                            : !url.trim()
                                ? 'bg-white/[0.04] text-neutral-500 cursor-not-allowed'
                                : 'bg-white text-black hover:bg-neutral-200 active:scale-[0.97] hover:shadow-[0_0_15px_rgba(255,255,255,0.1)]'
                    }`}
                >
                    {disabled ? (
                        <span className="flex items-center gap-2">
                            <svg className="animate-spin h-3.5 w-3.5" viewBox="0 0 24 24" fill="none">
                                <circle cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="3" className="opacity-20" />
                                <path d="M12 2C17.5228 2 22 6.47715 22 12" stroke="currentColor" strokeWidth="3" strokeLinecap="round" />
                            </svg>
                            Cloning
                        </span>
                    ) : 'Analyze'}
                </button>
            </div>

            {/* ── parsed repository pill ─────────────────────────────────── */}
            {parsedRepo && (
                <div className="flex items-center gap-2 text-[12px] text-violet-400 bg-violet-500/10 border border-violet-500/15 px-3.5 py-2 rounded-xl w-fit animate-fade-up">
                    <svg className="w-3.5 h-3.5" viewBox="0 0 16 16" fill="currentColor">
                        <path fillRule="evenodd" d="M2 2.5A2.5 2.5 0 014.5 0h8.75a.75.75 0 01.75.75v12.5a.75.75 0 01-.75.75h-2.5a.75.75 0 110-1.5h1.75v-2h-8a1 1 0 00-.714 1.7.75.75 0 01-1.072 1.05A2.495 2.495 0 012 11.5v-9zm10.5-1V9h-8c-.356 0-.694.074-1 .208V2.5a1 1 0 011-1h8z" />
                    </svg>
                    <span>Detected GitHub repository: <strong className="text-violet-200">{parsedRepo.owner}</strong> / <strong className="text-white">{parsedRepo.name}</strong></span>
                </div>
            )}

            {/* ── private repo toggle ────────────────────────────────────── */}
            <button
                type="button"
                onClick={() => setShowToken(v => !v)}
                className="cursor-pointer group flex items-center gap-1.5 text-[11.5px] text-neutral-500 hover:text-neutral-300 transition-colors"
            >
                <svg viewBox="0 0 12 12" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" className="w-3 h-3 text-neutral-500 group-hover:text-neutral-400 transition-colors">
                    <rect x="2" y="5" width="8" height="5.5" rx="1" />
                    <path d="M4 5V3.5a2 2 0 014 0V5" />
                </svg>
                {showToken ? 'Hide token field' : 'Add private repository access token'}
                <svg
                    viewBox="0 0 10 10" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"
                    className={`w-2.5 h-2.5 text-neutral-500 group-hover:text-neutral-400 transition-transform duration-300 ${showToken ? 'rotate-180' : ''}`}
                >
                    <path d="M2 3.5l3 3 3-3" />
                </svg>
            </button>

            {/* ── token field ────────────────────────────────────────────── */}
            {showToken && (
                <div className="flex items-center bg-[#0d0d12]/80 border border-white/[0.08] rounded-xl px-4 focus-within:border-violet-500/40 focus-within:ring-4 focus-within:ring-violet-500/10 transition-all duration-300 shadow-inner">
                    <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" className="w-3.5 h-3.5 shrink-0 text-neutral-500 mr-3">
                        <path d="M10.5 1a4.5 4.5 0 014 6.6L7 15l-1 .5-1-.5-.5-1 .5-1-.75-.75-.75.75-1-.5-.5-1 .5-1L1 9.5l.5-1 1-.5h.5A4.5 4.5 0 0110.5 1z" />
                        <circle cx="11.5" cy="4.5" r=".75" fill="currentColor" stroke="none" />
                    </svg>
                    <input
                        value={token}
                        onChange={e => setToken(e.target.value)}
                        placeholder="ghp_xxxxxxxxxxxxxxxxxxxx (GitHub Personal Access Token)"
                        type={showPwd ? 'text' : 'password'}
                        className="flex-1 bg-transparent py-3.5 text-[13px] font-mono text-white placeholder-neutral-600 focus:outline-none"
                    />
                    <button
                        type="button"
                        onClick={() => setShowPwd(v => !v)}
                        className="cursor-pointer ml-2 text-neutral-500 hover:text-neutral-300 transition-colors"
                    >
                        {showPwd ? (
                            <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" className="w-4 h-4">
                                <path d="M2 2l12 12M6.7 6.8a2 2 0 002.6 2.5M3.4 4.3C2 5.4 1 7 1 8s2.7 4.5 7 4.5c1.2 0 2.3-.3 3.2-.7M5.8 2.8C6.5 2.3 7.2 2 8 2c4.3 0 7 3.5 7 6 0 .8-.3 1.6-.8 2.3" />
                            </svg>
                        ) : (
                            <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" className="w-4 h-4">
                                <path d="M1 8s2.7-4.5 7-4.5S15 8 15 8s-2.7 4.5-7 4.5S1 8 1 8z" />
                                <circle cx="8" cy="8" r="2" />
                            </svg>
                        )}
                    </button>
                </div>
            )}
        </div>
    )
}