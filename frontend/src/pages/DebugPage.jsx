import { useState, useRef, useEffect, useMemo } from 'react'
import { useParams } from 'react-router-dom'
import { streamDebug, getRepoStatus, RateLimitError } from '../api/client'
import NavBar from '../components/layout/NavBar'
import RateLimitBanner from '../components/common/RateLimitBanner'
import LoadingSpinner from '../components/common/LoadingSpinner'
import ToolNavLinks from '../components/common/ToolNavLinks'
import RepoSelector from '../components/repo/RepoSelector'
import DebugSection from '../components/debug/DebugSection'
import TerminalTypewriter from '../components/common/TerminalTypewriter'

// Parses the streaming plain text into named sections
// Backend DebugService sends: "## Root Cause\n...\n## Explanation\n..."
// This function accumulates text and splits at ## headers as they arrive
// Returns partial sections — called on every new token so UI updates live

function parseSections(text) {
    const sections = { rootCause: '', explanation: '', suggestedFix: '', prevention: '' }
    const headerMap = {
        '## Root Cause':    'rootCause',
        '## Explanation':   'explanation',
        '## Suggested Fix': 'suggestedFix',
        '## Prevention':    'prevention'
    }

    let currentKey = null
    for (const line of text.split('\n')) {
        const matched = Object.keys(headerMap).find(h => line.trim().startsWith(h))
        if (matched) {
            currentKey = headerMap[matched]
        } else if (currentKey) {
            sections[currentKey] += line + '\n'
        }
    }
    return sections
}

export default function DebugPage() {
    const { repoId } = useParams() // undefined on /debug standalone route

    // Hooks defined first for React Rules of Hooks compliance
    const [repo, setRepo] = useState(null)
    const [errorText, setErrorText] = useState('')
    const [context, setContext] = useState('')
    const [streamedText, setStreamedText] = useState('')
    const [sources , setSources] = useState([])
    const [loading, setLoading] = useState(false)
    const [error, setError] = useState('')
    const [done, setDone] = useState(false)
    const [rateLimitSeconds, setRateLimitSeconds] = useState(null)
    const streamedRef = useRef('')  // Ref to accumulate without re-renders

    // Fetch repository metadata on mount / repoId change
    useEffect(() => {
        if (!repoId) return
        getRepoStatus(repoId).then(setRepo).catch(console.error)
    }, [repoId])


    const typewriterSteps = useMemo(() => [
        { text: `repomind debug --error "${errorText.substring(0, 35)}${errorText.length > 35 ? '...' : ''}"`, type: 'command' },
        { text: 'Embedding error signature & context...', type: 'info', delay: 400 },
        { text: 'Querying vector database for similar code chunks...', type: 'info', delay: 600 },
        { text: `Found matching chunks in repository ${repo?.repoName || ''}.`, type: 'success', delay: 500 },
        { text: 'Executing reasoning LLM analysis model...', type: 'info', delay: 400 }
    ], [errorText, repo?.repoName])

    async function handleAnalyze() {
        if (!errorText.trim()) return
                setLoading(true)
                setError('')
                setStreamedText('')
                setSources([])
                setDone(false)
                setRateLimitSeconds(null)
                streamedRef.current = ''
        await streamDebug(
            errorText,
            repoId || null,
            context || null,

            // onEvent — called for each SSE event from DebugService
            (event) => {
                switch (event.type) {
                    case 'token':
                        // Accumulate in ref to avoid closure issues
                        // Then set state to trigger re-render
                        streamedRef.current += event.content
                        setStreamedText(streamedRef.current)
                        break

                    case 'sources':
                        // Debug SSE sends "files" key (not "sources") as a JSON array
                        setSources(event.files || [])
                        break

                    case 'done':
                        setLoading(false)
                        setDone(true)
                        break

                    case 'error':
                        setError(event.content || 'Analysis failed. Please try again.')
                        setLoading(false)
                        break
                }
            },
             // onError — network-level failure
            (err) => {
                if (err instanceof RateLimitError) {
                    setRateLimitSeconds(err.retryAfterSeconds)
                } else {
                    setError(err.message)
                }
                setLoading(false)
            }
        )
    }
    const sections = parseSections(streamedText)
    const hasContent = streamedText.length > 0

    // Determine which section is currently being streamed
    // Used to show/hide the pulsing cursor per section
    const activeSection = !done && hasContent
        ? sections.prevention ? 'prevention'
        : sections.suggestedFix ? 'suggestedFix'
        : sections.explanation ? 'explanation'
        : sections.rootCause ? 'rootCause'
        : null
        : null
    function copyToClipboard(text) {
        navigator.clipboard.writeText(text).catch(() => {})
    }


    // No repo selected — show picker (called after hooks)
    if (!repoId) return <RepoSelector tool="debug" />

    return (
        <div className="min-h-screen bg-[#080809] text-white relative overflow-x-hidden">
            {/* Subtle glow background — matches Dashboard/Chat */}
            <div className="pointer-events-none fixed top-0 right-0 w-[500px] h-[500px] z-0 opacity-30"
                style={{ background: 'radial-gradient(circle at 100% 0%, rgba(244,63,94,0.10) 0%, transparent 70%)' }} />
            <div className="pointer-events-none fixed bottom-0 left-0 w-[400px] h-[400px] z-0 opacity-20"
                style={{ background: 'radial-gradient(circle at 0% 100%, rgba(139,92,246,0.08) 0%, transparent 70%)' }} />

            <NavBar repoName={repo?.repoName} />

            <main className="max-w-3xl mx-auto px-6 py-8 space-y-6 relative z-10">

                {/* ─── TOOL HEADER ─── */}
                <div className="flex items-center gap-3.5">
                    <div className="w-10 h-10 rounded-xl border border-rose-500/20 bg-rose-500/10 flex items-center justify-center text-rose-400 shrink-0">
                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" className="w-5 h-5">
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
                    </div>
                    <div>
                        <h2 className="text-lg font-bold text-white tracking-tight leading-none">Debug Assistant</h2>
                        <p className="text-[12.5px] text-neutral-500 mt-1.5">
                            Paste an error trace and get AI-powered root cause analysis.
                        </p>
                    </div>
                </div>

                {/* ─── Input card ─── */}
                <div className="relative overflow-hidden bg-[#0d0d12]/50 border border-white/[0.06] rounded-2xl p-6 space-y-4 backdrop-blur-md shadow-2xl">
                    <div className="absolute top-0 right-0 w-[260px] h-[260px] bg-rose-600/5 blur-[80px] rounded-full pointer-events-none" />

                    <div className="relative z-10 space-y-1.5">
                        <label className="block text-[11px] font-bold uppercase tracking-wider text-neutral-500">
                            Error message or stack trace
                        </label>
                        <textarea
                            value={errorText}
                            onChange={e => setErrorText(e.target.value)}
                            placeholder={"Paste your error here...\n\nExample:\nNullPointerException at AuthService.java:47\n  at UserController.login(UserController.java:23)"}
                            rows={8}
                            className="w-full bg-[#050507] border border-white/[0.07] rounded-xl px-4 py-3 text-white text-[13px] font-mono placeholder-neutral-600 focus:outline-none focus:border-violet-500/40 focus:ring-1 focus:ring-violet-500/20 transition-all resize-none"
                        />
                    </div>

                    <div className="relative z-10 space-y-1.5">
                        <label className="block text-[11px] font-bold uppercase tracking-wider text-neutral-500">
                            Additional context <span className="text-neutral-600 normal-case font-medium">(optional)</span>
                        </label>
                        <input
                            value={context}
                            onChange={e => setContext(e.target.value)}
                            placeholder="e.g. This happens when user tries to log in with Google"
                            className="w-full bg-[#050507] border border-white/[0.07] rounded-xl px-4 py-2.5 text-white text-[13px] placeholder-neutral-600 focus:outline-none focus:border-violet-500/40 focus:ring-1 focus:ring-violet-500/20 transition-all"
                        />
                    </div>

                    <button
                        onClick={handleAnalyze}
                        disabled={loading || !errorText.trim() || !!rateLimitSeconds}
                        className="relative z-10 w-full bg-white/[0.06] border border-white/[0.1] text-white/80 hover:bg-white/[0.1] hover:text-white disabled:opacity-40 disabled:cursor-not-allowed font-semibold py-2.5 rounded-xl transition-all cursor-pointer text-[13px] flex items-center justify-center gap-2"
                    >
                        {loading && <LoadingSpinner size="sm" />}
                        {loading ? 'Analyzing...' : 'Analyze Error'}
                    </button>

                    {error && (
                        <div className="relative z-10 p-3 bg-rose-500/5 border border-rose-500/15 text-rose-400 text-[12.5px] rounded-xl flex items-center gap-2 animate-fade-up">
                            <svg className="w-4 h-4 shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth="2">
                                <path strokeLinecap="round" strokeLinejoin="round" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
                            </svg>
                            <span>{error}</span>
                        </div>
                    )}

                    {rateLimitSeconds && (
                        <div className="relative z-10">
                            <RateLimitBanner
                                seconds={rateLimitSeconds}
                                onDismiss={() => setRateLimitSeconds(null)}
                            />
                        </div>
                    )}
                </div>

                {/* Loading state — before first token arrives */}
                {loading && !hasContent && (
                    <div className="max-w-3xl mx-auto py-4 animate-fade-up">
                        <TerminalTypewriter
                            title="debug — terminal"
                            steps={typewriterSteps}
                        />
                    </div>
                )}

                {/* Results — sections appear one by one as streaming progresses */}
                {hasContent && (
                    <div className="mt-8 bg-[#0d0d12]/50 border border-white/[0.06] rounded-2xl overflow-hidden flex flex-col shadow-2xl backdrop-blur-md">
                        <div className="bg-black/20 border-b border-white/[0.04] flex items-center px-5 py-3 shrink-0">
                            <span className="text-[11px] uppercase tracking-wider text-neutral-400 font-bold flex items-center gap-2">
                                <svg className="w-4 h-4 text-violet-400" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth="2">
                                    <path strokeLinecap="round" strokeLinejoin="round" d="M13 10V3L4 14h7v7l9-11h-7z" />
                                </svg>
                                Analysis Output
                            </span>
                        </div>
                        <div className="p-5 space-y-4 max-h-[500px] overflow-y-auto" style={{ scrollbarWidth: 'none', msOverflowStyle: 'none' }}>
                            {sections.rootCause.trim() && (
                                <DebugSection variant="rootCause"
                                    streaming={activeSection === 'rootCause'}>
                                    {sections.rootCause.trim()}
                                </DebugSection>
                            )}

                            {sections.explanation.trim() && (
                                <DebugSection variant="explanation"
                                    streaming={activeSection === 'explanation'}>
                                    {sections.explanation.trim()}
                                </DebugSection>
                            )}

                            {sections.suggestedFix.trim() && (
                                <DebugSection variant="suggestedFix"
                                    streaming={activeSection === 'suggestedFix'}
                                    onCopy={() => copyToClipboard(sections.suggestedFix.trim())}>
                                    {sections.suggestedFix.trim()}
                                </DebugSection>
                            )}

                            {sections.prevention.trim() && (
                                <DebugSection variant="prevention"
                                    streaming={activeSection === 'prevention'}>
                                    {sections.prevention.trim()}
                                </DebugSection>
                            )}

                            {/* Bot typing indicator while analyzing */}
                            {!done && activeSection && (
                                <div className="flex items-center gap-3 py-2 px-1">
                                    <div className="w-6 h-6 rounded-md bg-gradient-to-br from-violet-600/30 to-fuchsia-600/30 border border-violet-500/20 flex items-center justify-center relative overflow-hidden shrink-0">
                                        <div className="absolute inset-0 bg-violet-400/10 animate-pulse" />
                                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" className="w-3.5 h-3.5 text-violet-300 relative z-10">
                                            <path strokeLinecap="round" strokeLinejoin="round" d="M9.813 15.904L9 18.75l-.813-2.846a4.5 4.5 0 00-3.09-3.09L2.25 12l2.846-.813a4.5 4.5 0 003.09-3.09L9 5.25l.813 2.846a4.5 4.5 0 003.09 3.09L15.75 12l-2.846.813a4.5 4.5 0 00-3.09 3.09zM18.259 8.715L18 9.75l-.259-1.035a3.375 3.375 0 00-2.455-2.456L14.25 6l1.036-.259a3.375 3.375 0 002.455-2.456L18 2.25l.259 1.035a3.375 3.375 0 002.456 2.456L21.75 6l-1.035.259a3.375 3.375 0 00-2.456 2.456z" />
                                        </svg>
                                    </div>
                                    <p className="text-[12.5px] font-medium text-neutral-400 animate-pulse">
                                        {activeSection === 'rootCause' && 'Analyzing root cause...'}
                                        {activeSection === 'explanation' && 'Writing explanation...'}
                                        {activeSection === 'suggestedFix' && 'Generating suggested fix...'}
                                        {activeSection === 'prevention' && 'Formulating prevention tips...'}
                                    </p>
                                </div>
                            )}

                            {/* Source files — appear after streaming completes */}
                            {sources.length > 0 && (
                                <div className="bg-[#050507] border border-white/[0.04] rounded-xl p-4">
                                    <p className="text-[10.5px] text-neutral-500 font-bold uppercase tracking-wider mb-3">
                                        Relevant Files
                                    </p>
                                    <div className="flex flex-wrap gap-1.5">
                                        {sources.map((f, i) => (
                                            <span key={i}
                                                className="text-[11px] font-mono text-violet-300 bg-violet-500/10 border border-violet-500/20 px-2.5 py-1 rounded-lg">
                                                {f}
                                            </span>
                                        ))}
                                    </div>
                                </div>
                            )}

                            {/* Retry button after analysis completes */}
                            {done && (
                                <button
                                    onClick={() => {
                                        setStreamedText('')
                                        setSources([])
                                        setDone(false)
                                    }}
                                    className="cursor-pointer w-full text-[12.5px] font-medium text-neutral-400 hover:text-white border border-white/[0.06] hover:border-white/[0.12] hover:bg-white/[0.03] py-2.5 rounded-xl transition-all mt-4"
                                >
                                    Analyze Again
                                </button>
                            )}
                        </div>
                    </div>
                )}
            </main>
        </div>
    )
}
