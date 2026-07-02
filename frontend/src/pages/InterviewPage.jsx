import { useState } from 'react'
import { generateInterview } from '../api/client'

const DIFFICULTIES = ['BEGINNER', 'INTERMEDIATE', 'ADVANCED']

export default function InterviewPage({ repo, onBack }) {
    const [difficulty, setDifficulty] = useState('INTERMEDIATE')
    const [session, setSession] = useState(null)
    const [loading, setLoading] = useState(false)
    const [error, setError] = useState('')
    // Track which questions have their answer revealed
    const [revealed, setRevealed] = useState({})

    async function handleGenerate() {
        setLoading(true)
        setError('')
        setSession(null)
        setRevealed({})
        try {
            const result = await generateInterview(repo.id, difficulty)
            setSession(result)
        } catch (e) {
            setError(e.message)
        } finally {
            setLoading(false)
        }
    }

    function toggleReveal(questionId) {
        setRevealed(prev => ({ ...prev, [questionId]: !prev[questionId] }))
    }

    return (
        <div className="max-w-3xl mx-auto py-8 space-y-6">
            <div className="flex items-center gap-3">
                <button
                    onClick={onBack}
                    className="text-sm text-gray-400 hover:text-white transition-colors"
                >
                    ← Back
                </button>
                <div>
                    <h2 className="text-xl font-semibold text-white">Interview Prep</h2>
                    <p className="text-xs text-gray-500">{repo.repoName}</p>
                </div>
            </div>

            {/* Difficulty selector + generate button */}
            <div className="bg-gray-900 border border-gray-700 rounded-lg p-4 space-y-4">
                <p className="text-sm text-gray-400">Select difficulty level</p>
                <div className="flex gap-2">
                    {DIFFICULTIES.map(d => (
                        <button
                            key={d}
                            onClick={() => setDifficulty(d)}
                            className={`px-4 py-2 rounded-lg text-sm font-medium transition-colors ${
                                difficulty === d
                                    ? 'bg-blue-600 text-white'
                                    : 'bg-gray-800 text-gray-400 hover:text-white border border-gray-700'
                            }`}
                        >
                            {d.charAt(0) + d.slice(1).toLowerCase()}
                        </button>
                    ))}
                </div>
                <button
                    onClick={handleGenerate}
                    disabled={loading}
                    className="w-full bg-blue-600 hover:bg-blue-500 disabled:opacity-50 text-white font-medium py-2.5 rounded-lg transition-colors"
                >
                    {loading ? 'Generating questions...' : 'Generate 10 Questions'}
                </button>
                {error && <p className="text-red-400 text-sm">{error}</p>}
            </div>

            {/* Loading state */}
            {loading && (
                <div className="text-center py-8 space-y-3">
                    <div className="w-8 h-8 border-2 border-blue-500 border-t-transparent rounded-full animate-spin mx-auto" />
                    <p className="text-gray-400 text-sm">
                        Analyzing your codebase and generating questions...
                    </p>
                </div>
            )}

            {/* Questions list */}
            {session && !loading && (
                <div className="space-y-4">
                    <p className="text-sm text-gray-400">
                        {session.questions.length} questions generated for{' '}
                        <span className="text-white">{session.difficulty.toLowerCase()}</span> level
                    </p>

                    {session.questions.map((q, i) => (
                        <div
                            key={q.id}
                            className="bg-gray-900 border border-gray-700 rounded-lg p-4 space-y-3"
                        >
                            <div className="flex items-start gap-3">
                                <span className="text-blue-400 font-mono text-sm shrink-0 mt-0.5">
                                    Q{i + 1}
                                </span>
                                <div className="flex-1 space-y-2">
                                    <p className="text-white text-sm leading-relaxed">
                                        {q.question}
                                    </p>
                                    {q.conceptTested && (
                                        <span className="inline-block text-xs text-gray-400 bg-gray-800 border border-gray-700 px-2 py-0.5 rounded">
                                            {q.conceptTested}
                                        </span>
                                    )}
                                </div>
                            </div>

                            {/* Answer toggle — hidden by default so user can practice */}
                            <div className="ml-7">
                                <button
                                    onClick={() => toggleReveal(q.id)}
                                    className="text-xs text-blue-400 hover:text-blue-300 transition-colors"
                                >
                                    {revealed[q.id] ? '− Hide answer' : '+ Show expected answer'}
                                </button>

                                {revealed[q.id] && (
                                    <div className="mt-2 p-3 bg-gray-800 border border-gray-700 rounded-lg">
                                        <p className="text-sm text-gray-300 leading-relaxed">
                                            {q.expectedAnswer}
                                        </p>
                                    </div>
                                )}
                            </div>
                        </div>
                    ))}
                </div>
            )}
        </div>
    )
}