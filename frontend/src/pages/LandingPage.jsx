import { Link } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'

// Feature card for the landing page features section
function FeatureCard({ icon, title, description }) {
    return (
        <div className="bg-gray-900 border border-gray-700 rounded-xl p-6 space-y-3 hover:border-gray-600 transition-colors">
            <div className="text-3xl">{icon}</div>
            <h3 className="font-semibold text-white">{title}</h3>
            <p className="text-sm text-gray-400 leading-relaxed">{description}</p>
        </div>
    )
}

// Step card for the how-it-works section
function StepCard({ number, title, description }) {
    return (
        <div className="text-center space-y-3">
            <div className="w-10 h-10 rounded-full bg-blue-600 text-white font-bold text-sm flex items-center justify-center mx-auto">
                {number}
            </div>
            <h3 className="font-semibold text-white">{title}</h3>
            <p className="text-sm text-gray-400">{description}</p>
        </div>
    )
}

export default function LandingPage() {
    const { user } = useAuth()

    return (
        <div className="min-h-screen bg-gray-950 text-white">
            {/* Nav */}
            <header className="border-b border-gray-800 bg-gray-950/80 backdrop-blur-sm">
                <div className="max-w-5xl mx-auto px-6 py-4 flex items-center justify-between">
                    <span className="text-lg font-bold text-blue-400">RepoMind</span>
                    <div className="flex items-center gap-3">
                        {user ? (
                            <Link to="/dashboard"
                                className="bg-blue-600 hover:bg-blue-500 text-white text-sm font-medium px-4 py-2 rounded-lg transition-colors">
                                Dashboard
                            </Link>
                        ) : (
                            <>
                                <Link to="/login"
                                    className="text-sm text-gray-400 hover:text-white transition-colors">
                                    Sign in
                                </Link>
                                <Link to="/register"
                                    className="bg-blue-600 hover:bg-blue-500 text-white text-sm font-medium px-4 py-2 rounded-lg transition-colors">
                                    Get Started Free
                                </Link>
                            </>
                        )}
                    </div>
                </div>
            </header>

            {/* Hero */}
            <section className="max-w-4xl mx-auto px-6 py-24 text-center space-y-8">
                <div className="space-y-4">
                    <div className="inline-flex items-center gap-2 bg-blue-600/10 border border-blue-600/20 rounded-full px-4 py-1.5 text-sm text-blue-400">
                        <span className="w-1.5 h-1.5 rounded-full bg-blue-400 animate-pulse" />
                        AI-powered code understanding
                    </div>
                    <h1 className="text-5xl md:text-6xl font-bold text-white leading-tight">
                        Chat with any{' '}
                        <span className="text-blue-400">GitHub repository</span>
                        {' '}using AI
                    </h1>
                    <p className="text-xl text-gray-400 max-w-2xl mx-auto leading-relaxed">
                        Understand codebases instantly. Generate interview questions.
                        Debug errors with full code context.
                    </p>
                </div>

                <div className="flex flex-col sm:flex-row gap-3 justify-center">
                    <Link to="/register"
                        className="bg-blue-600 hover:bg-blue-500 text-white font-medium px-8 py-3 rounded-xl transition-colors text-lg">
                        Get Started Free
                    </Link>
                    <Link to="/login"
                        className="border border-gray-700 hover:border-gray-500 text-gray-300 hover:text-white font-medium px-8 py-3 rounded-xl transition-colors text-lg">
                        Sign In
                    </Link>
                </div>

                {/* Mock terminal showing a GitHub URL being analyzed */}
                <div className="bg-gray-900 border border-gray-700 rounded-xl p-4 max-w-xl mx-auto text-left">
                    <div className="flex items-center gap-2 mb-3">
                        <div className="w-3 h-3 rounded-full bg-red-500 opacity-60" />
                        <div className="w-3 h-3 rounded-full bg-yellow-500 opacity-60" />
                        <div className="w-3 h-3 rounded-full bg-green-500 opacity-60" />
                        <span className="text-xs text-gray-500 ml-2">RepoMind</span>
                    </div>
                    <div className="space-y-2 font-mono text-sm">
                        <p className="text-gray-500">$ Analyze repository...</p>
                        <p className="text-blue-400">github.com/spring-projects/spring-boot</p>
                        <p className="text-gray-400">✓ Indexed 2,847 chunks from 312 files</p>
                        <p className="text-green-400">Ready to chat →</p>
                    </div>
                </div>
            </section>

            {/* How it works */}
            <section className="border-t border-gray-800 py-20">
                <div className="max-w-4xl mx-auto px-6 space-y-12">
                    <h2 className="text-3xl font-bold text-center text-white">How it works</h2>
                    <div className="grid grid-cols-1 md:grid-cols-3 gap-8">
                        <StepCard number="1" title="Paste a GitHub URL"
                            description="Submit any public repository URL. Private repos supported with a GitHub token." />
                        <StepCard number="2" title="AI indexes the codebase"
                            description="RepoMind reads every file, creates semantic embeddings, and builds a searchable knowledge base." />
                        <StepCard number="3" title="Chat, interview, debug"
                            description="Ask questions in plain English and get answers grounded in the actual source code." />
                    </div>
                </div>
            </section>

            {/* Features */}
            <section className="border-t border-gray-800 py-20">
                <div className="max-w-4xl mx-auto px-6 space-y-12">
                    <h2 className="text-3xl font-bold text-center text-white">Features</h2>
                    <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
                        <FeatureCard icon="💬" title="Chat with Code"
                            description="Ask any question about the codebase. Get answers with exact file references and line numbers." />
                        <FeatureCard icon="🎯" title="Interview Prep"
                            description="Generate project-specific interview questions at beginner, intermediate, or advanced level." />
                        <FeatureCard icon="🐛" title="Debug Assistant"
                            description="Paste errors and stack traces. Get root cause analysis with suggested fixes grounded in your actual code." />
                    </div>
                </div>
            </section>

            {/* CTA */}
            <section className="border-t border-gray-800 py-20">
                <div className="max-w-2xl mx-auto px-6 text-center space-y-6">
                    <h2 className="text-3xl font-bold text-white">
                        Ready to understand your codebase?
                    </h2>
                    <p className="text-gray-400">Free to use. No credit card required.</p>
                    <Link to="/register"
                        className="inline-block bg-blue-600 hover:bg-blue-500 text-white font-medium px-8 py-3 rounded-xl transition-colors text-lg">
                        Get Started Free
                    </Link>
                </div>
            </section>

            {/* Footer */}
            <footer className="border-t border-gray-800 py-8">
                <div className="max-w-5xl mx-auto px-6 text-center">
                    <p className="text-sm text-gray-500">
                        RepoMind — AI-powered code understanding
                    </p>
                </div>
            </footer>
        </div>
    )
}