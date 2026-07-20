import { useState, useEffect, useRef } from 'react'
import { useParams, useLocation, useNavigate } from 'react-router-dom'
import {
    generateInterview,
    getRepoStatus,
    getInterviewSessions,
    getInterviewSession,
    RateLimitError
} from '../api/client'
import LoadingSpinner from '../components/common/LoadingSpinner'
import RateLimitBanner from '../components/common/RateLimitBanner'
import ToolNavLinks from '../components/common/ToolNavLinks'
import RepoSelector from '../components/repo/RepoSelector'
import NavBar from '../components/layout/NavBar'

const DIFFICULTIES = ['BEGINNER', 'INTERMEDIATE', 'ADVANCED']

export default function InterviewPage() {
    const { repoId } = useParams()
    const location = useLocation()
    const navigate = useNavigate()

    // Read active session ID from URL query parameters
    const queryParams = new URLSearchParams(location.search)
    const querySessionId = queryParams.get('sessionId')

    // ─── STATE HOOKS DEFINED FIRST (React Rules of Hooks compliance) ───────
    const [repo, setRepo] = useState(null)
    const [difficulty, setDifficulty] = useState('INTERMEDIATE')
    const [sessions, setSessions] = useState([])
    const [session, setSession] = useState(null)
    const [loading, setLoading] = useState(false)
    const [loadingSessions, setLoadingSessions] = useState(true)
    const [error, setError] = useState('')
    const [rateLimitSeconds, setRateLimitSeconds] = useState(null)

    // Toggle for Saved Sessions Drawer
    const [showHistory, setShowHistory] = useState(false)

    // Interactive deck state
    const [currentQuestionIdx, setCurrentQuestionIdx] = useState(0)
    const [isAnswerRevealed, setIsAnswerRevealed] = useState(false)
    const [copiedAll, setCopiedAll] = useState(false)
    const [copiedQuestionId, setCopiedQuestionId] = useState(null)

    // Effect: Load repository status and previous sessions list on mount/repoId change
    useEffect(() => {
        if (!repoId) return
        getRepoStatus(repoId).then(setRepo).catch(console.error)
        loadPreviousSessions()
    }, [repoId])

    // Effect: Sync active session content with URL sessionId parameter change (back/forward support)
    useEffect(() => {
        if (!repoId) return
        if (querySessionId) {
            setLoading(true)
            getInterviewSession(querySessionId)
                .then(data => {
                    setSession(data)
                    setCurrentQuestionIdx(0)
                    setIsAnswerRevealed(false)
                })
                .catch(e => {
                    setError(e.message)
                    setSession(null)
                })
                .finally(() => setLoading(false))
        } else {
            setSession(null)
        }
    }, [repoId, querySessionId])

    async function loadPreviousSessions() {
        setLoadingSessions(true)
        try {
            const data = await getInterviewSessions()
            const filtered = (data || []).filter(s => s && s.repoId === repoId)
            setSessions(filtered)
        } catch (e) {
            console.error('Failed to load sessions:', e)
        } finally {
            setLoadingSessions(false)
        }
    }


    async function handleGenerate() {
        setLoading(true)
        setError('')
        try {
            const result = await generateInterview(repoId, difficulty)
            // Navigate to active session parameter URL (replace in history stack)
            navigate(`/interview/${repoId}?sessionId=${result.id}`, { replace: true })
            await loadPreviousSessions()
        } catch (e) {
            if (e instanceof RateLimitError) {
                setRateLimitSeconds(e.retryAfterSeconds)
            } else {
                setError(e.message)
            }
        } finally {
            setLoading(false)
        }
    }

    function handleSelectSession(sessionId) {
        // Navigate to active session parameter URL (replace in history stack)
        navigate(`/interview/${repoId}?sessionId=${sessionId}`, { replace: true })
        setShowHistory(false) // Close side drawer after selection
    }

    function handleStartNew() {
        // Exit session and return to configuration (replace in history stack)
        navigate(`/interview/${repoId}`, { replace: true })
    }

    function copyAllQuestions() {
        if (!session || !session.questions) return
        const text = session.questions.map((q, i) =>
            `Q${i + 1}: ${q.question}\n\nExpected Answer: ${q.expectedAnswer}\n`
        ).join('\n---\n\n')
        navigator.clipboard.writeText(text)
            .then(() => {
                setCopiedAll(true)
                setTimeout(() => setCopiedAll(false), 2000)
            })
            .catch(() => {})
    }

    // Custom single question text copy
    function copySingleQuestion(q, i) {
        const text = `Q${i + 1}: ${q.question}\n\nExpected Answer: ${q.expectedAnswer}`
        navigator.clipboard.writeText(text)
            .then(() => {
                setCopiedQuestionId(q.id || i)
                setTimeout(() => setCopiedQuestionId(null), 2000)
            })
            .catch(() => {})
    }

    // Defensive date formatter: parses ISO strings or LocalDateTime array formats
    function formatDate(dateVal) {
        try {
            if (!dateVal) return 'Recently'

            if (Array.isArray(dateVal)) {
                const [y, m, d] = dateVal
                if (y === undefined || m === undefined || d === undefined) return 'Recently'
                const date = new Date(y, m - 1, d)
                if (isNaN(date.getTime())) return 'Recently'
                return date.toLocaleDateString(undefined, {
                    month: 'short',
                    day: 'numeric',
                    year: 'numeric'
                })
            }

            const date = new Date(dateVal)
            if (isNaN(date.getTime())) return 'Recently'
            return date.toLocaleDateString(undefined, {
                month: 'short',
                day: 'numeric',
                year: 'numeric'
            })
        } catch (e) {
            console.error('Date parsing failed:', e)
            return 'Recently'
        }
    }

    const activeQuestion = session?.questions?.[currentQuestionIdx]


    if (!repoId) return <RepoSelector tool="interview" />

    return (
        <div className="min-h-screen bg-[#080809] text-white font-sans flex flex-col relative overflow-x-hidden">
            <NavBar repoName={repo?.repoName} />
            {/* Subtle glow background — matches Dashboard/Chat/Debug */}
            <div className="pointer-events-none fixed top-0 right-0 w-[500px] h-[500px] z-0 opacity-30"
                style={{ background: 'radial-gradient(circle at 100% 0%, rgba(139,92,246,0.12) 0%, transparent 70%)' }} />
            <div className="pointer-events-none fixed bottom-0 left-0 w-[400px] h-[400px] z-0 opacity-20"
                style={{ background: 'radial-gradient(circle at 0% 100%, rgba(236,72,153,0.08) 0%, transparent 70%)' }} />

            {/* ─── SIDE-OVER SAVED SESSIONS DRAWER ─── */}
            {showHistory && (
                <div
                    onClick={() => setShowHistory(false)}
                    className="fixed inset-0 bg-black/60 backdrop-blur-sm z-50 transition-opacity duration-300"
                />
            )}

            <div className={`fixed inset-y-0 right-0 w-80 max-w-full bg-[#0d0d0f] border-l border-white/[0.07] p-6 z-50 flex flex-col shadow-2xl transition-transform duration-300 ease-in-out ${
                showHistory ? 'translate-x-0' : 'translate-x-full'
            }`}>
                <div className="flex items-center justify-between border-b border-white/[0.05] pb-4 mb-4">
                    <h3 className="text-xs font-bold uppercase tracking-wider text-neutral-400">
                        Saved Sessions
                    </h3>
                    <button
                        onClick={() => setShowHistory(false)}
                        className="cursor-pointer text-neutral-500 hover:text-white transition-colors"
                    >
                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" className="w-5 h-5">
                            <path d="M18 6L6 18M6 6l12 12" />
                        </svg>
                    </button>
                </div>

                <div className="flex-1 overflow-y-auto space-y-2 pr-1 scrollbar-thin">
                    {loadingSessions ? (
                        <div className="flex justify-center py-8">
                            <LoadingSpinner size="sm" />
                        </div>
                    ) : sessions.length === 0 ? (
                        <p className="text-xs text-neutral-600 text-center py-10">
                            No saved sessions found.
                        </p>
                    ) : (
                        sessions.map(s => {
                            if (!s) return null
                            const active = s.id === querySessionId
                            const formattedDate = formatDate(s.createdAt)
                            return (
                                <button
                                    key={s.id}
                                    onClick={() => handleSelectSession(s.id)}
                                    className={`w-full text-left py-3 px-4 rounded-xl transition-all duration-150 flex items-center justify-between hover:bg-white/[0.03] group border cursor-pointer ${
                                        active
                                            ? 'bg-violet-500/5 border-violet-500/20 text-white border-l-2 border-l-violet-500 rounded-l-none'
                                            : 'bg-transparent border-transparent text-neutral-400 hover:text-white'
                                    }`}
                                >
                                    <div className="space-y-1">
                                        <p className="text-[12.5px] font-semibold tracking-tight">5 Questions Prep</p>
                                        <p className="text-[10px] text-neutral-500 font-mono">{formattedDate}</p>
                                    </div>
                                    <span className={`text-[9px] font-bold uppercase tracking-wider px-2 py-0.5 rounded-md border shrink-0 ${
                                        active
                                            ? 'text-violet-400 bg-violet-950/20 border-violet-800/30'
                                            : 'text-neutral-500 bg-white/[0.02] border-white/[0.05]'
                                    }`}>
                                        {s.difficulty}
                                    </span>
                                </button>
                            )
                        })
                    )}
                </div>
            </div>

            {/* ─── MAIN CONTAINER ─── */}
            <main className="max-w-2xl mx-auto px-6 py-4 space-y-6 w-full z-10 flex-1 flex flex-col">

                {/* ─── TOOL HEADER ─── */}
                <div className="flex items-center justify-between">
                    <div className="flex items-center gap-3.5">
                        {/* Grad Cap Circle Icon — Violet theme */}
                        <div className="w-10 h-10 rounded-xl border border-violet-500/20 bg-violet-500/10 flex items-center justify-center text-violet-400 shrink-0">
                            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" className="w-5 h-5">
                                <path d="M22 9l-10 -4l-10 4l10 4l10 -4v6" />
                                <path d="M6 10.6v5.4a6 3 0 0 0 12 0v-5.4" />
                            </svg>
                        </div>
                        <div>
                            <h2 className="text-lg font-bold text-white tracking-tight leading-none">Interview Prep</h2>
                            <p className="text-[12.5px] text-neutral-500 mt-1.5">
                                Generate custom mock interview questions based on the codebase.
                            </p>
                        </div>
                    </div>

                    {/* History Toggle */}
                    <button onClick={() => setShowHistory(true)}
                        className="text-xs bg-[#0f0f11] hover:bg-[#151518] border border-white/[0.06] text-neutral-400 hover:text-white px-3.5 py-1.5 rounded-lg transition-all font-medium flex items-center gap-1 cursor-pointer">
                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" className="w-3.5 h-3.5">
                            <path d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" strokeLinecap="round" strokeLinejoin="round" />
                        </svg>
                        History
                    </button>
                </div>

                {/* ─── CASE 1: GENERATED STATIC CARD DECK ─── */}
                {session && activeQuestion && (
                    <div className="space-y-6 py-2">

                        {/* Integrated Flat Header Row (Replacing the bulky banner card) */}
                        <div className="flex items-center justify-between border-b border-white/[0.05] pb-4">
                            <div>
                                <h3 className="text-sm font-semibold text-white/90">Mock Questionnaire</h3>
                                <p className="text-[11.5px] text-neutral-500 mt-0.5">
                                    Difficulty: <span className="text-neutral-300 font-semibold uppercase">{session.difficulty}</span>
                                </p>
                            </div>
                            <div className="flex items-center gap-4">
                                <button onClick={copyAllQuestions}
                                    className={`text-xs border px-3 py-1.5 rounded-lg font-medium transition-all ${
                                        copiedAll
                                            ? 'bg-emerald-950/20 border-emerald-800/30 text-emerald-400'
                                            : 'border-white/[0.06] bg-[#0f0f11] text-neutral-400 hover:text-white hover:bg-white/[0.02] cursor-pointer'
                                    }`}>
                                    {copiedAll ? 'Copied All!' : 'Copy All Questions'}
                                </button>

                                <button onClick={handleStartNew}
                                    className="text-xs bg-transparent text-neutral-500 hover:text-white transition-all cursor-pointer font-semibold flex items-center gap-1.5">
                                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" className="w-3.5 h-3.5">
                                        <path d="M18 6L6 18M6 6l12 12" strokeLinecap="round" strokeLinejoin="round" />
                                    </svg>
                                    Close Session
                                </button>
                            </div>
                        </div>

                        {/* Interactive deck layout with absolute side chevrons */}
                        <div className="relative w-full flex flex-col sm:block">

                            {/* Soft violet ambient glow orb behind the deck */}
                            <div className="absolute -inset-10 bg-violet-500/5 blur-[50px] rounded-full pointer-events-none z-0" />

                            {/* Previous Button (Left Side - absolute positioned outside card bounds) */}
                            <button
                                onClick={() => {
                                    setIsAnswerRevealed(false)
                                    setCurrentQuestionIdx(idx => Math.max(0, idx - 1))
                                }}
                                disabled={currentQuestionIdx === 0}
                                className="hidden sm:flex absolute -left-12 lg:-left-14 top-1/2 -translate-y-1/2 w-10 h-10 shrink-0 rounded-full border border-white/[0.06] bg-[#0f0f11] hover:bg-[#151518] hover:border-white/[0.12] text-neutral-400 hover:text-white disabled:opacity-20 disabled:cursor-not-allowed transition-all items-center justify-center cursor-pointer z-20 shadow-md"
                                title="Previous Question"
                            >
                                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" className="w-5 h-5">
                                    <path d="M15 19l-7-7 7-7" strokeLinecap="round" strokeLinejoin="round"/>
                                </svg>
                            </button>

                            {/* Clickable central Question Card (full width matching banner) */}
                            <div
                                onClick={() => setIsAnswerRevealed(!isAnswerRevealed)}
                                className="relative w-full bg-[#0f0f11] border border-white/[0.07] hover:border-white/[0.12] rounded-2xl p-5 flex flex-col shadow-[0_4px_30px_rgba(0,0,0,0.4)] cursor-pointer select-none transition-all duration-150 overflow-hidden z-10"
                            >
                                {/* Fuchsia-to-violet top-edge gradient accent line */}
                                <div className="absolute top-0 left-0 w-full h-[1.5px] bg-gradient-to-r from-transparent via-violet-500 to-transparent" />

                                <div className="space-y-2">
                                    <div className="flex items-center justify-between">
                                        {/* Fuchsia-violet text gradient question tracker */}
                                        <span className="text-[10px] font-mono tracking-widest font-bold bg-gradient-to-r from-violet-400 to-fuchsia-400 bg-clip-text text-transparent">
                                            QUESTION {currentQuestionIdx + 1} OF 5
                                        </span>
                                        {activeQuestion.conceptTested && (
                                            <span className="text-[9.5px] text-neutral-500 bg-white/[0.02] border border-white/[0.05] px-2 py-0.5 rounded">
                                                {activeQuestion.conceptTested}
                                            </span>
                                        )}
                                    </div>
                                    <p className="text-[14.5px] sm:text-[15.5px] font-bold text-white leading-relaxed tracking-tight">
                                        {activeQuestion.question}
                                    </p>
                                </div>

                                {/* Landing Page style gradient line divider */}
                                <div className="h-px bg-gradient-to-r from-transparent via-white/[0.08] to-transparent my-3.5" />

                                <div className="space-y-3">
                                    <div className="flex items-center justify-between">
                                        {/* Subtle reveal text prompt */}
                                        <span className="text-[11px] text-neutral-600 font-medium tracking-tight">
                                            {isAnswerRevealed ? 'Click card to hide answer' : 'Click card to reveal answer'}
                                        </span>
                                        <button
                                            onClick={(e) => {
                                                e.stopPropagation() // Prevent toggling the answer reveal
                                                copySingleQuestion(activeQuestion, currentQuestionIdx)
                                            }}
                                            className={`text-[9.5px] font-medium border px-2 py-1 rounded transition-all shrink-0 cursor-pointer ${
                                                copiedQuestionId === (activeQuestion.id || currentQuestionIdx)
                                                    ? 'bg-emerald-950/20 border-emerald-800/30 text-emerald-400'
                                                    : 'border-white/[0.05] text-neutral-500 hover:text-neutral-300 hover:bg-white/[0.02]'
                                            }`}
                                        >
                                            {copiedQuestionId === (activeQuestion.id || currentQuestionIdx) ? 'Copied' : 'Copy'}
                                        </button>
                                    </div>

                                    {/* Inline suggested answer section */}
                                    {isAnswerRevealed && (
                                        <div className="bg-[#050507]/60 border border-white/[0.04] rounded-xl p-3.5 animate-fade-up mt-1">
                                            {/* Emerald-to-teal text gradient answer title */}
                                            <span className="block text-[9.5px] font-mono font-bold tracking-widest bg-gradient-to-r from-emerald-400 to-teal-400 bg-clip-text text-transparent mb-1.5 uppercase">
                                                Suggested Answer
                                            </span>
                                            <p className="text-[12.5px] text-neutral-300 leading-relaxed font-normal">
                                                {activeQuestion.expectedAnswer}
                                            </p>
                                        </div>
                                    )}
                                </div>
                            </div>

                            {/* Next Button (Right Side - absolute positioned outside card bounds) */}
                            <button
                                onClick={() => {
                                    setIsAnswerRevealed(false)
                                    setCurrentQuestionIdx(idx => Math.min(session.questions.length - 1, idx + 1))
                                }}
                                disabled={currentQuestionIdx === session.questions.length - 1}
                                className="hidden sm:flex absolute -right-12 lg:-right-14 top-1/2 -translate-y-1/2 w-10 h-10 shrink-0 rounded-full border border-white/[0.06] bg-[#0f0f11] hover:bg-[#151518] hover:border-white/[0.12] text-neutral-400 hover:text-white disabled:opacity-20 disabled:cursor-not-allowed transition-all items-center justify-center cursor-pointer z-20 shadow-md"
                                title="Next Question"
                            >
                                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" className="w-5 h-5">
                                    <path d="M9 5l7 7-7 7" strokeLinecap="round" strokeLinejoin="round"/>
                                </svg>
                            </button>
                        </div>

                        {/* Inline navigation buttons below the card for mobile screen bounds */}
                        <div className="flex sm:hidden items-center justify-between gap-4 w-full mt-2">
                            <button
                                onClick={() => {
                                    setIsAnswerRevealed(false)
                                    setCurrentQuestionIdx(idx => Math.max(0, idx - 1))
                                }}
                                disabled={currentQuestionIdx === 0}
                                className="px-4 py-2 border border-white/[0.06] bg-[#0f0f11] text-neutral-400 disabled:opacity-20 rounded-xl text-xs font-semibold flex-1 py-2 cursor-pointer flex items-center justify-center gap-1"
                            >
                                Previous
                            </button>
                            <button
                                onClick={() => {
                                    setIsAnswerRevealed(false)
                                    setCurrentQuestionIdx(idx => Math.min(session.questions.length - 1, idx + 1))
                                }}
                                disabled={currentQuestionIdx === session.questions.length - 1}
                                className="px-4 py-2 border border-white/[0.06] bg-[#0f0f11] text-neutral-400 disabled:opacity-20 rounded-xl text-xs font-semibold flex-1 py-2 cursor-pointer flex items-center justify-center gap-1"
                            >
                                Next
                            </button>
                        </div>

                        {/* Dot progress indicators styled like landing page */}
                        <div className="flex justify-center gap-1.5 mt-2">
                            {Array.from({ length: 5 }).map((_, i) => (
                                <button
                                    key={i}
                                    onClick={() => {
                                        setIsAnswerRevealed(false)
                                        setCurrentQuestionIdx(i)
                                    }}
                                    className={`h-1 rounded-full transition-all duration-350 cursor-pointer ${
                                        currentQuestionIdx === i ? 'w-6 bg-white' : 'w-1.5 bg-white/20 hover:bg-white/40'
                                    }`}
                                    aria-label={`Go to question ${i + 1}`}
                                />
                            ))}
                        </div>
                    </div>
                )}

                {/* ─── CASE 2: CONFIGURATION SCREEN ─── */}
                {!session && (
                    <div className="space-y-6 animate-fade-up">

                        {/* Configuration Card */}
                        <div className="bg-[#0f0f11] border border-white/[0.07] rounded-xl p-5 sm:p-6 space-y-5">
                            <div>
                                <h3 className="text-sm font-semibold text-white/95">Configure Mock Interview</h3>
                                <p className="text-[12.5px] text-neutral-500 mt-1">
                                    Generate 5 codebase-specific questions to review design patterns and logic.
                                </p>
                            </div>

                            <div className="space-y-2.5">
                                <span className="block text-[10px] font-bold uppercase tracking-wider text-neutral-500">
                                    Select Difficulty
                                </span>
                                <div className="flex flex-wrap gap-2">
                                    {DIFFICULTIES.map(d => (
                                        <button key={d} onClick={() => setDifficulty(d)}
                                            className={`px-4 py-2 rounded-lg text-xs font-semibold border transition-all cursor-pointer ${
                                                difficulty === d
                                                    ? 'bg-white/[0.06] border-white/[0.12] text-white/95'
                                                    : 'bg-transparent border-transparent text-neutral-500 hover:bg-white/[0.02] hover:border-white/[0.05]'
                                            }`}>
                                            {d.charAt(0) + d.slice(1).toLowerCase()}
                                        </button>
                                    ))}
                                </div>
                            </div>

                            <button onClick={handleGenerate}
                                disabled={loading || !!rateLimitSeconds}
                                className="w-full bg-white/[0.06] border border-white/[0.1] text-white/80 hover:bg-white/[0.1] hover:text-white font-semibold py-2.5 rounded-xl transition-all flex items-center justify-center gap-2 cursor-pointer text-xs">
                                {loading ? 'Preparing Prep Deck...' : 'Generate 5 Questions'}
                            </button>

                            {error && (
                                <p className="text-xs text-red-400 text-center bg-red-950/20 border border-red-800/20 py-2.5 rounded-lg">
                                    {error}
                                </p>
                            )}

                            {rateLimitSeconds && (
                                <RateLimitBanner
                                    seconds={rateLimitSeconds}
                                    onDismiss={() => setRateLimitSeconds(null)}
                                />
                            )}
                        </div>

                        {/* Loading State — questions generating */}
                        {loading && (
                            <InterviewLoadingCard difficulty={difficulty} repoName={repo?.repoName} />
                        )}

                    </div>
                )}
            </main>
        </div>
    )
}

// ─── Interview Loading Card ────────────────────────────────────────────────────

function InterviewLoadingCard({ difficulty, repoName }) {
    const steps = [
        { label: 'Building repository context', done: false },
        { label: 'Generating semantic query embeddings', done: false },
        { label: `Retrieving code chunks from ${repoName || 'repository'}`, done: false },
        { label: `Formulating ${difficulty?.toLowerCase() || 'intermediate'} questions`, done: false },
        { label: 'Drafting answer guidelines', done: false },
    ]

    const [activeIdx, setActiveIdx] = useState(0)

    useEffect(() => {
        const interval = setInterval(() => {
            setActiveIdx(prev => (prev < steps.length - 1 ? prev + 1 : prev))
        }, 900)
        return () => clearInterval(interval)
    }, [])

    return (
        <div className="animate-fade-up bg-[#0f0f11] border border-white/[0.07] rounded-xl p-5 space-y-4">
            {/* Header */}
            <div className="flex items-center gap-3">
                <div className="w-8 h-8 rounded-lg bg-violet-500/10 border border-violet-500/20 flex items-center justify-center shrink-0">
                    <svg className="w-4 h-4 text-violet-400 animate-spin" viewBox="0 0 24 24" fill="none">
                        <circle cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="3" strokeDasharray="32" strokeLinecap="round" className="opacity-50" />
                        <path d="M12 2a10 10 0 0 1 10 10" stroke="currentColor" strokeWidth="3" strokeLinecap="round" />
                    </svg>
                </div>
                <div>
                    <p className="text-[13px] font-semibold text-white/90">Generating Questions</p>
                    <p className="text-[11px] text-neutral-500 mt-0.5">This usually takes a few seconds…</p>
                </div>
            </div>

            {/* Step list */}
            <div className="space-y-2.5 pl-1">
                {steps.map((step, i) => {
                    const isDone = i < activeIdx
                    const isActive = i === activeIdx
                    return (
                        <div key={i} className="flex items-center gap-2.5">
                            {isDone ? (
                                <span className="w-4 h-4 flex items-center justify-center shrink-0">
                                    <svg className="w-3.5 h-3.5 text-emerald-400" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth="2.5">
                                        <path strokeLinecap="round" strokeLinejoin="round" d="M5 13l4 4L19 7" />
                                    </svg>
                                </span>
                            ) : isActive ? (
                                <span className="w-4 h-4 flex items-center justify-center shrink-0">
                                    <span className="w-2 h-2 rounded-full bg-violet-400 animate-pulse" />
                                </span>
                            ) : (
                                <span className="w-4 h-4 flex items-center justify-center shrink-0">
                                    <span className="w-1.5 h-1.5 rounded-full bg-white/10" />
                                </span>
                            )}
                            <span className={`text-[12px] transition-colors ${
                                isDone ? 'text-neutral-500' :
                                isActive ? 'text-neutral-200 font-medium' :
                                'text-neutral-600'
                            }`}>
                                {step.label}
                            </span>
                        </div>
                    )
                })}
            </div>
        </div>
    )
}