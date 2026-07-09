import { useState, useRef } from 'react'
import { useParams } from 'react-router-dom'
import { streamDebug, RateLimitError } from '../api/client'
import NavBar from '../components/layout/NavBar'
import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import RateLimitBanner from '../components/common/RateLimitBanner'
import LoadingSpinner from '../components/common/LoadingSpinner'

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

// Individual result section card
// Shows different color based on variant

function DebugSection({ variant, title, children, onCopy, streaming }) {
    const styles = {
        danger:  'bg-red-950/30 border-red-800/40',
        neutral: 'bg-gray-900 border-gray-700',
        success: 'bg-green-950/20 border-green-800/30',
        info:    'bg-blue-950/20 border-blue-800/30'
    }
    const titleColors = {
        danger: 'text-red-400', neutral: 'text-gray-400',
        success: 'text-green-400', info: 'text-blue-400'
    }
    return (
        <div className={`border rounded-lg p-4 ${styles[variant]}`}>
            <div className="flex items-center justify-between mb-2">
                <p className={`text-xs font-medium uppercase tracking-wider ${titleColors[variant]}`}>
                    {title}
                </p>
                {onCopy && !streaming && (
                    <button onClick={onCopy}
                        className="text-xs text-gray-500 hover:text-white transition-colors">
                        Copy
                    </button>
                )}
            </div>
            <div className="prose prose-invert prose-sm max-w-none text-gray-300">
                <ReactMarkdown remarkPlugins={[remarkGfm]}>{children}</ReactMarkdown>
                {/* Pulsing cursor shown while this section is actively streaming */}
                {streaming && (
                    <span className="inline-block w-2 h-3.5 bg-current animate-pulse ml-0.5 rounded-sm opacity-70" />
                )}
            </div>
        </div>
    )
}

export default function DebugPage() {
    const { repoId } = useParams() // undefined on /debug standalone route
    const [errorText, setErrorText] = useState('');
    const [context, setContext] = useState('');
    const [streamedText, setStreamedText] = useState('')
    const [sources , setSources] = useState([])
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState('');
    const [done, setDone] = useState(false)
    const [rateLimitSeconds, setRateLimitSeconds] = useState(null)
    const streamedRef = useRef('')  // Ref to accumulate without re-renders


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
    return (
        <div className="min-h-screen bg-gray-950 text-white">
            <NavBar repoName={repoId ? undefined : undefined} />

            <main className="max-w-3xl mx-auto px-6 py-8 space-y-6">
                <div>
                    <h2 className="text-xl font-semibold">Debug Assistant</h2>
                    {repoId && (
                        <p className="text-sm text-gray-500 mt-1">
                            Using codebase context for better analysis
                        </p>
                    )}
                </div>

                {/* Input section */}
                <div className="space-y-3">
                    <div>
                        <label className="block text-sm text-gray-400 mb-1.5">
                            Error message or stack trace
                        </label>
                        <textarea
                            value={errorText}
                            onChange={e => setErrorText(e.target.value)}
                            placeholder={"Paste your error here...\n\nExample:\nNullPointerException at AuthService.java:47\n  at UserController.login(UserController.java:23)"}
                            rows={8}
                            className="w-full bg-gray-900 border border-gray-700 rounded-lg px-4 py-3 text-white text-sm font-mono placeholder-gray-600 focus:outline-none focus:border-blue-500 transition-colors resize-none"
                        />
                    </div>

                    <div>
                        <label className="block text-sm text-gray-400 mb-1.5">
                            Additional context <span className="text-gray-600">(optional)</span>
                        </label>
                        <input
                            value={context}
                            onChange={e => setContext(e.target.value)}
                            placeholder="e.g. This happens when user tries to log in with Google"
                            className="w-full bg-gray-900 border border-gray-700 rounded-lg px-4 py-2.5 text-white text-sm placeholder-gray-500 focus:outline-none focus:border-blue-500 transition-colors"
                        />
                    </div>

                    <button
                        onClick={handleAnalyze}
                        disabled={loading || !errorText.trim() || !!rateLimitSeconds}
                        className="w-full bg-blue-600 hover:bg-blue-500 disabled:opacity-50 text-white font-medium py-2.5 rounded-lg transition-colors"
                    >
                        {loading ? 'Analyzing...' : 'Analyze Error'}
                    </button>

                    {error && <p className="text-red-400 text-sm">{error}</p>}

                    {rateLimitSeconds && (
                        <RateLimitBanner
                            seconds={rateLimitSeconds}
                            onDismiss={() => setRateLimitSeconds(null)}
                        />
                    )}
                </div>

                {/* Loading state — before first token arrives */}
                {loading && !hasContent && (
                    <div className="flex flex-col items-center gap-3 py-8">
                        <LoadingSpinner size="md" />
                        <p className="text-gray-400 text-sm">Analyzing your error...</p>
                    </div>
                )}

                {/* Results — sections appear one by one as streaming progresses */}
                {hasContent && (
                    <div className="space-y-4">
                        {sections.rootCause.trim() && (
                            <DebugSection variant="danger" title="Root Cause"
                                streaming={activeSection === 'rootCause'}>
                                {sections.rootCause.trim()}
                            </DebugSection>
                        )}

                        {sections.explanation.trim() && (
                            <DebugSection variant="neutral" title="Explanation"
                                streaming={activeSection === 'explanation'}>
                                {sections.explanation.trim()}
                            </DebugSection>
                        )}

                        {sections.suggestedFix.trim() && (
                            <DebugSection variant="success" title="Suggested Fix"
                                streaming={activeSection === 'suggestedFix'}
                                onCopy={() => copyToClipboard(sections.suggestedFix.trim())}>
                                {sections.suggestedFix.trim()}
                            </DebugSection>
                        )}

                        {sections.prevention.trim() && (
                            <DebugSection variant="info" title="Prevention"
                                streaming={activeSection === 'prevention'}>
                                {sections.prevention.trim()}
                            </DebugSection>
                        )}

                        {/* Source files — appear after streaming completes */}
                        {sources.length > 0 && (
                            <div className="bg-gray-900 border border-gray-700 rounded-lg p-4">
                                <p className="text-xs text-gray-400 font-medium uppercase tracking-wider mb-2">
                                    Relevant Files
                                </p>
                                <div className="flex flex-wrap gap-1">
                                    {sources.map((f, i) => (
                                        <span key={i}
                                            className="text-xs font-mono text-blue-400 bg-blue-400/10 border border-blue-400/20 px-2 py-0.5 rounded">
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
                                className="w-full text-sm text-gray-400 hover:text-white border border-gray-700 py-2 rounded-lg transition-colors"
                            >
                                Analyze Again
                            </button>
                        )}
                    </div>
                )}
            </main>
        </div>
    )
}