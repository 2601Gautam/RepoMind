import { useState, useEffect } from 'react'
import { useParams, Link } from 'react-router-dom'
import { generateInterview, getRepoStatus, RateLimitError } from '../api/client'
import NavBar from '../components/layout/NavBar'
import RateLimitBanner from '../components/common/RateLimitBanner'
import LoadingSpinner from '../components/common/LoadingSpinner'
import RepoSelector from '../components/repo/RepoSelector'

const DIFFICULTIES = ['BEGINNER', 'INTERMEDIATE', 'ADVANCED']
// Individual question card with answer toggle
// Separated so InterviewPage stays clean — just renders a list of these
function QuestionCard({ question, index }) {
    const [revealed, setRevealed] = useState(false)
    return (
        <div className="bg-gray-900 border border-gray-700 rounded-lg p-4 space-y-3">
            <div className="flex items-start gap-3">
                <span className="text-blue-400 font-mono text-sm font-bold shrink-0 mt-0.5">
                    Q{index + 1}
                </span>
                <div className="flex-1 space-y-2">
                    <p className="text-white text-sm leading-relaxed">{question.question}</p>
                    {question.conceptTested && (
                        <span className="inline-block text-xs text-gray-400 bg-gray-800 border border-gray-700 px-2 py-0.5 rounded">
                            {question.conceptTested}
                        </span>
                    )}
                </div>
            </div>

            <div className="ml-7">
                <button onClick={() => setRevealed(v => !v)}
                    className="text-xs text-blue-400 hover:text-blue-300 transition-colors">
                    {revealed ? '− Hide answer' : '+ Show expected answer'}
                </button>
                {revealed && (
                    <div className="mt-2 p-3 bg-gray-800 border border-gray-700 rounded-lg">
                        <p className="text-sm text-gray-300 leading-relaxed">
                            {question.expectedAnswer}
                        </p>
                    </div>
                )}
            </div>
        </div>
    )
}
export default function InterviewPage() {
    const { repoId } = useParams()

    // No repo selected — show picker
    if (!repoId) return <RepoSelector tool="interview" />

    const [repo, setRepo] = useState(null)
    const [difficulty, setDifficulty] = useState('INTERMEDIATE')
    const [session, setSession] = useState(null)
    const [loading, setLoading] = useState(false)
    const [error, setError] = useState('')
    const [rateLimitSeconds, setRateLimitSeconds] = useState(null)

    useEffect(() => {
        getRepoStatus(repoId).then(setRepo).catch(console.error)
    }, [repoId])

    async function handleGenerate() {
        setLoading(true)
        setError('')
        setSession(null)

        try {
            const result = await generateInterview(repoId, difficulty)
            setSession(result)
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

    function copyAllQuestions() {
        if (!session) return
        const text = session.questions.map((q, i) =>
            `Q${i + 1}: ${q.question}\n\nExpected Answer: ${q.expectedAnswer}\n`
        ).join('\n---\n\n')
        navigator.clipboard.writeText(text).catch(() => {})
    }

    return (
        <div className="min-h-screen bg-gray-950 text-white">
            <NavBar repoName={repo?.repoName} />

            <main className="max-w-3xl mx-auto px-6 py-8 space-y-6">
                {/* Header with navigation to other features */}
                <div className="flex items-center justify-between">
                    <div>
                        <h2 className="text-xl font-semibold">Interview Prep</h2>
                        {repo && <p className="text-sm text-gray-500 mt-1">{repo.repoName}</p>}
                    </div>
                    <div className="flex gap-2">
                        <Link to={`/chat/${repoId}`}
                            className="text-xs bg-gray-800 hover:bg-gray-700 border border-gray-700 text-gray-300 hover:text-white px-3 py-1.5 rounded-lg transition-colors">
                            Chat
                        </Link>
                        <Link to={`/debug/${repoId}`}
                            className="text-xs bg-gray-800 hover:bg-gray-700 border border-gray-700 text-gray-300 hover:text-white px-3 py-1.5 rounded-lg transition-colors">
                            Debug
                        </Link>
                    </div>
                </div>

                {/* Controls */}
                <div className="bg-gray-900 border border-gray-700 rounded-xl p-5 space-y-4">
                    <p className="text-sm text-gray-400">Select difficulty level</p>
                    <div className="flex gap-2">
                        {DIFFICULTIES.map(d => (
                            <button key={d} onClick={() => setDifficulty(d)}
                                className={`px-4 py-2 rounded-lg text-sm font-medium transition-colors ${
                                    difficulty === d
                                        ? 'bg-blue-600 text-white'
                                        : 'bg-gray-800 text-gray-400 hover:text-white border border-gray-700'
                                }`}>
                                {d.charAt(0) + d.slice(1).toLowerCase()}
                            </button>
                        ))}
                    </div>

                    <button onClick={handleGenerate}
                        disabled={loading || !!rateLimitSeconds}
                        className="w-full bg-blue-600 hover:bg-blue-500 disabled:opacity-50 text-white font-medium py-2.5 rounded-lg transition-colors">
                        {loading ? 'Generating...' : 'Generate 10 Questions'}
                    </button>

                    {error && <p className="text-red-400 text-sm">{error}</p>}

                    {rateLimitSeconds && (
                        <RateLimitBanner
                            seconds={rateLimitSeconds}
                            onDismiss={() => setRateLimitSeconds(null)}
                        />
                    )}
                </div>

                {/* Loading */}
                {loading && (
                    <div className="flex flex-col items-center gap-3 py-10">
                        <LoadingSpinner size="lg" />
                        <p className="text-gray-400 text-sm">
                            Analyzing codebase and generating questions...
                        </p>
                    </div>
                )}

                {/* Questions */}
                {session && !loading && (
                    <div className="space-y-4">
                        <div className="flex items-center justify-between">
                            <p className="text-sm text-gray-400">
                                {session.questions.length} questions ·{' '}
                                <span className="text-white">{session.difficulty.toLowerCase()}</span> level
                            </p>
                            <button onClick={copyAllQuestions}
                                className="text-xs text-gray-400 hover:text-white border border-gray-700 px-3 py-1.5 rounded-lg transition-colors">
                                Copy All
                            </button>
                        </div>

                        {session.questions.map((q, i) => (
                            <QuestionCard key={q.id} question={q} index={i} />
                        ))}
                    </div>
                )}
            </main>
        </div>
    )
}