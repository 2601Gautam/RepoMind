import { Link } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'

// Feature card - Clean and professional
function FeatureCard({ icon, title, description }) {
    return (
        <div className="bg-white border border-neutral-200 rounded-lg p-8 space-y-4 hover:shadow-md transition-shadow duration-200 hover:border-primary-300">
            <div className="text-4xl">{icon}</div>
            <h3 className="font-semibold text-lg text-neutral-900">{title}</h3>
            <p className="text-sm text-neutral-600 leading-relaxed">{description}</p>
        </div>
    )
}

// Step card - Minimalist with numbered circles
function StepCard({ number, title, description }) {
    return (
        <div className="space-y-5 text-left">
            <div className="flex items-start gap-4">
                <div className="w-12 h-12 rounded-full bg-gradient-to-br from-primary-600 to-primary-700 text-white font-semibold text-lg flex items-center justify-center flex-shrink-0">
                    {number}
                </div>
                <div className="flex-1 pt-1">
                    <h3 className="font-semibold text-neutral-900 mb-2">{title}</h3>
                    <p className="text-neutral-600 text-sm leading-relaxed">{description}</p>
                </div>
            </div>
        </div>
    )
}

export default function LandingPage() {
    const { user } = useAuth()

    return (
        <div className="min-h-screen bg-neutral-50 text-neutral-900">
            {/* ===== NAVIGATION ===== */}
            <header className="sticky top-0 z-50 bg-white/95 backdrop-blur-sm border-b border-neutral-200">
                <div className="max-w-6xl mx-auto px-6 py-4 flex items-center justify-between">
                    <div className="flex items-center gap-2">
                        <div className="w-8 h-8 rounded-lg bg-gradient-to-br from-primary-600 to-primary-700 flex items-center justify-center">
                            <span className="text-white font-bold text-lg">R</span>
                        </div>
                        <span className="text-lg font-bold text-neutral-900">RepoMind</span>
                    </div>
                    <div className="flex items-center gap-4">
                        {user ? (
                            <Link to="/dashboard"
                                className="px-4 py-2 bg-primary-600 text-white rounded-lg text-sm font-medium hover:bg-primary-700 transition-colors">
                                Dashboard
                            </Link>
                        ) : (
                            <>
                                <Link to="/login"
                                    className="px-4 py-2 text-neutral-600 hover:text-neutral-900 transition-colors font-medium text-sm">
                                    Sign in
                                </Link>
                                <Link to="/register"
                                    className="px-4 py-2 bg-primary-600 text-white rounded-lg text-sm font-medium hover:bg-primary-700 transition-colors">
                                    Get Started
                                </Link>
                            </>
                        )}
                    </div>
                </div>
            </header>

            {/* ===== HERO SECTION ===== */}
            <section className="bg-white py-20 md:py-32">
                <div className="max-w-4xl mx-auto px-6 space-y-10">
                    {/* Badge */}
                    <div className="flex justify-center">
                        <div className="inline-flex items-center gap-2 bg-primary-50 border border-primary-200 rounded-full px-4 py-2 text-sm">
                            <span className="w-2 h-2 rounded-full bg-primary-600 animate-pulse" />
                            <span className="text-primary-700 font-medium">AI-powered code understanding</span>
                        </div>
                    </div>

                    {/* Headline */}
                    <div className="space-y-6 text-center">
                        <h1 className="text-5xl md:text-6xl font-bold text-neutral-900 leading-tight">
                            Chat with your{' '}
                            <span className="bg-gradient-to-r from-primary-600 to-primary-700 bg-clip-text text-transparent">
                                GitHub repository
                            </span>
                        </h1>
                        <p className="text-lg md:text-xl text-neutral-600 max-w-2xl mx-auto leading-relaxed">
                            Understand codebases in seconds. Ask questions in English. Get answers grounded in your actual code.
                        </p>
                    </div>

                    {/* CTA Buttons */}
                    <div className="flex flex-col sm:flex-row gap-4 justify-center pt-4">
                        <Link to="/register"
                            className="px-8 py-3 bg-primary-600 text-white rounded-lg font-semibold hover:bg-primary-700 transition-colors shadow-md text-center">
                            Get Started Free
                        </Link>
                        <Link to="/login"
                            className="px-8 py-3 bg-white border-2 border-neutral-300 text-neutral-900 rounded-lg font-semibold hover:border-primary-600 hover:text-primary-600 transition-colors text-center">
                            Sign In
                        </Link>
                    </div>

                    {/* Demo Box - Code-like display */}
                    <div className="pt-8">
                        <div className="bg-neutral-900 border border-neutral-800 rounded-lg p-6 max-w-lg mx-auto">
                            <div className="flex items-center gap-2 mb-4 pb-4 border-b border-neutral-800">
                                <div className="w-3 h-3 rounded-full bg-red-500" />
                                <div className="w-3 h-3 rounded-full bg-yellow-500" />
                                <div className="w-3 h-3 rounded-full bg-green-500" />
                                <span className="text-xs text-neutral-500 ml-3">RepoMind Analysis</span>
                            </div>
                            <div className="space-y-3 font-mono text-sm">
                                <p className="text-neutral-500">$ repomind analyze</p>
                                <p className="text-primary-400">github.com/torvalds/linux</p>
                                <p className="text-green-400">✓ Indexed 68,000+ files</p>
                                <p className="text-green-400">✓ Created semantic embeddings</p>
                                <p className="text-neutral-400">Ready for questions →</p>
                            </div>
                        </div>
                    </div>
                </div>
            </section>

            {/* ===== HOW IT WORKS ===== */}
            <section className="py-20 md:py-32 bg-neutral-50">
                <div className="max-w-4xl mx-auto px-6 space-y-16">
                    <div className="text-center space-y-4">
                        <h2 className="text-4xl font-bold text-neutral-900">How it works</h2>
                        <p className="text-lg text-neutral-600">Three simple steps to understand any codebase</p>
                    </div>

                    <div className="grid grid-cols-1 md:grid-cols-3 gap-10">
                        <StepCard
                            number="1"
                            title="Paste a GitHub URL"
                            description="Share any public repository. We support private repos with a GitHub token. No installation needed."
                        />
                        <StepCard
                            number="2"
                            title="We index everything"
                            description="RepoMind analyzes every file, builds semantic embeddings, and creates a searchable knowledge base of your code."
                        />
                        <StepCard
                            number="3"
                            title="Chat with your code"
                            description="Ask questions in natural language. Get precise answers with file references, line numbers, and context."
                        />
                    </div>
                </div>
            </section>

            {/* ===== FEATURES ===== */}
            <section className="py-20 md:py-32 bg-white">
                <div className="max-w-5xl mx-auto px-6 space-y-16">
                    <div className="text-center space-y-4">
                        <h2 className="text-4xl font-bold text-neutral-900">Powerful features</h2>
                        <p className="text-lg text-neutral-600">Everything you need to understand code faster</p>
                    </div>

                    <div className="grid grid-cols-1 md:grid-cols-3 gap-8">
                        <FeatureCard
                            icon="💬"
                            title="Smart Code Chat"
                            description="Ask questions about architecture, dependencies, performance, and more. Get answers rooted in actual code."
                        />
                        <FeatureCard
                            icon="🎯"
                            title="Interview Prep"
                            description="Generate project-specific interview questions at any difficulty level. Learn by explaining your own code."
                        />
                        <FeatureCard
                            icon="🐛"
                            title="Debugging Made Easy"
                            description="Paste error messages and get root cause analysis with suggested fixes from your codebase context."
                        />
                        <FeatureCard
                            icon="📊"
                            title="Code Analysis"
                            description="Understand codebase structure, dependencies, and patterns instantly without manual documentation."
                        />
                        <FeatureCard
                            icon="⚡"
                            title="Lightning Fast"
                            description="Semantic search powered by AI means answers come back in seconds, not minutes."
                        />
                        <FeatureCard
                            icon="🔒"
                            title="Privacy First"
                            description="Your code stays private. We process and store embeddings securely. No data sharing with third parties."
                        />
                    </div>
                </div>
            </section>

            {/* ===== USE CASES ===== */}
            <section className="py-20 md:py-32 bg-neutral-50">
                <div className="max-w-4xl mx-auto px-6">
                    <div className="text-center space-y-4 mb-16">
                        <h2 className="text-4xl font-bold text-neutral-900">Built for developers</h2>
                        <p className="text-lg text-neutral-600">Whether you&apos;re onboarding or diving deep, RepoMind helps</p>
                    </div>

                    <div className="grid grid-cols-1 md:grid-cols-2 gap-8">
                        <div className="bg-white p-8 rounded-lg border border-neutral-200">
                            <h3 className="text-xl font-semibold text-neutral-900 mb-3">New Team Members</h3>
                            <p className="text-neutral-600">Onboard 3x faster by asking questions about unfamiliar codebases. Learn patterns by example.</p>
                        </div>
                        <div className="bg-white p-8 rounded-lg border border-neutral-200">
                            <h3 className="text-xl font-semibold text-neutral-900 mb-3">Code Interviews</h3>
                            <p className="text-neutral-600">Prepare smarter with auto-generated, project-specific interview questions and discussion points.</p>
                        </div>
                        <div className="bg-white p-8 rounded-lg border border-neutral-200">
                            <h3 className="text-xl font-semibold text-neutral-900 mb-3">Technical Debt</h3>
                            <p className="text-neutral-600">Understand the full scope of legacy systems before refactoring. Identify improvement opportunities.</p>
                        </div>
                        <div className="bg-white p-8 rounded-lg border border-neutral-200">
                            <h3 className="text-xl font-semibold text-neutral-900 mb-3">Debugging</h3>
                            <p className="text-neutral-600">Paste stack traces and error logs. Get context-aware debugging help from your actual code.</p>
                        </div>
                    </div>
                </div>
            </section>

            {/* ===== FINAL CTA ===== */}
            <section className="py-20 md:py-32 bg-gradient-to-r from-primary-600 to-primary-700">
                <div className="max-w-3xl mx-auto px-6 text-center space-y-8">
                    <h2 className="text-4xl font-bold text-white leading-tight">
                        Start understanding your codebase today
                    </h2>
                    <p className="text-lg text-primary-100">
                        Free to use. No credit card required. Analyze any public GitHub repository in seconds.
                    </p>
                    <Link to="/register"
                        className="inline-block px-8 py-4 bg-white text-primary-600 rounded-lg font-semibold hover:bg-neutral-100 transition-colors shadow-lg">
                        Get Started Free
                    </Link>
                </div>
            </section>

            {/* ===== FOOTER ===== */}
            <footer className="bg-neutral-900 text-neutral-400 py-12">
                <div className="max-w-6xl mx-auto px-6 text-center space-y-4">
                    <p className="text-lg font-semibold text-white">RepoMind</p>
                    <p className="text-sm">
                        AI-powered code understanding. Chat with your GitHub repositories.
                    </p>
                    <p className="text-xs text-neutral-500 pt-4">
                        © {new Date().getFullYear()} RepoMind. All rights reserved.
                    </p>
                </div>
            </footer>
        </div>
    )
}
