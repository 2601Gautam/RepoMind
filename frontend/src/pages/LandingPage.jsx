import { useState, useEffect, useRef } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'

const RevealOnScroll = ({ children, delay = 0 }) => {
    const [isVisible, setIsVisible] = useState(false)
    const ref = useRef(null)

    useEffect(() => {
        const observer = new IntersectionObserver(
            ([entry]) => {
                if (entry.isIntersecting) {
                    setIsVisible(true)
                    observer.unobserve(entry.target)
                }
            },
            { threshold: 0.1, rootMargin: '50px' }
        )

        if (ref.current) {
            observer.observe(ref.current)
        }

        return () => {
            if (ref.current) observer.unobserve(ref.current)
        }
    }, [])

    return (
        <div 
            ref={ref}
            className={`transition-all duration-1000 ease-out ${
                isVisible ? 'opacity-100 translate-y-0' : 'opacity-0 translate-y-12'
            }`}
            style={{ transitionDelay: `${delay}ms` }}
        >
            {children}
        </div>
    )
}

export default function LandingPage() {
    const { user, loading } = useAuth()
    const navigate = useNavigate()
    const [logoText, setLogoText] = useState('')
    const [isTyping, setIsTyping] = useState(true)
    const [activeDemo, setActiveDemo] = useState('ingest') // 'ingest', 'chat', 'interview', 'debug'
    const [activeStep, setActiveStep] = useState('1') // '1', '2', '3'
    const [showScrollTop, setShowScrollTop] = useState(false)

    useEffect(() => {
        if (!loading && user) {
            navigate('/dashboard', { replace: true })
        }
    }, [user, loading, navigate])

    // Auto-play timer to cycle features deck automatically
    useEffect(() => {
        const interval = setInterval(() => {
            setActiveStep(prev => {
                if (prev === '1') return '2'
                if (prev === '2') return '3'
                return '1'
            })
        }, 3000)
        return () => clearInterval(interval)
    }, [])

    useEffect(() => {
        const fullLogo = 'RepoMind'
        let idx = 0
        let deleting = false
        let timeout

        function tick() {
            if (!deleting) {
                idx++
                setLogoText(fullLogo.slice(0, idx))
                setIsTyping(true)
                if (idx === fullLogo.length) {
                    deleting = true
                    timeout = setTimeout(tick, 2000)
                } else {
                    timeout = setTimeout(tick, 110)
                }
            } else {
                idx--
                setLogoText(fullLogo.slice(0, idx))
                setIsTyping(false)
                if (idx === 0) {
                    deleting = false
                    timeout = setTimeout(tick, 700)
                } else {
                    timeout = setTimeout(tick, 55)
                }
            }
        }

        timeout = setTimeout(tick, 400)
        return () => clearTimeout(timeout)
    }, [])

    useEffect(() => {
        const handleScroll = () => {
            if (window.scrollY > 300) {
                setShowScrollTop(true)
            } else {
                setShowScrollTop(false)
            }
        }
        window.addEventListener('scroll', handleScroll)
        return () => window.removeEventListener('scroll', handleScroll)
    }, [])

    const scrollToTop = () => {
        window.scrollTo({ top: 0, behavior: 'smooth' })
    }

    const renderTerminalContent = () => {
        switch (activeDemo) {
            case 'chat':
                return (
                    <>
                        <div className="text-gray-500"><span className="text-gray-600">$</span> repomind chat --query "Explain JWT verification"</div>
                        <div className="text-violet-300">Searching codebase embeddings...</div>
                        <div className="text-gray-400 mt-2">// Matches found in: src/auth/jwt.strategy.ts</div>
                        <div className="text-emerald-400">Response:</div>
                        <div className="text-gray-300 leading-relaxed pl-2 border-l border-white/10">
                            The application uses passport-jwt strategy. The token is parsed from the Authorization header and validated against the secret key configured in config.ts.
                        </div>
                    </>
                )
            case 'interview':
                return (
                    <>
                        <div className="text-gray-500"><span className="text-gray-600">$</span> repomind interview --level advanced</div>
                        <div className="text-violet-300">Generating interview questions based on src/service/...</div>
                        <div className="text-emerald-400 mt-2">Questions Generated:</div>
                        <div className="text-gray-300 space-y-1.5 font-sans pl-2 border-l border-white/10 text-xs">
                            <p><strong className="text-violet-300">Q1.</strong> How does the ChunkingService handle overlaps to avoid loss of context during tokenization?</p>
                            <p><strong className="text-violet-300">Q2.</strong> What strategy is used to synchronize ingestion steps concurrently?</p>
                        </div>
                    </>
                )
            case 'debug':
                return (
                    <>
                        <div className="text-gray-500"><span className="text-gray-600">$</span> repomind debug --error "Database connection timeout"</div>
                        <div className="text-violet-300">Analyzing application.properties and schema init.sql...</div>
                        <div className="text-emerald-400 mt-2">Potential Fixes:</div>
                        <div className="text-gray-300 space-y-1 pl-2 border-l border-white/10">
                            <p>1. Check if PostgreSQL container is running on port 5432</p>
                            <p>2. Increase pool size in configuration properties:</p>
                            <p className="text-yellow-400 pl-4 font-mono text-xs">spring.datasource.hikari.maximum-pool-size=15</p>
                        </div>
                    </>
                )
            case 'ingest':
            default:
                return (
                    <>
                        <div className="text-gray-500"><span className="text-gray-600">$</span> repomind analyze</div>
                        <div className="text-violet-300">  github.com/vercel/next.js</div>
                        <div className="text-gray-600">  Cloning &amp; chunking repository…</div>
                        <div className="text-gray-500">  Indexed <span className="text-gray-300">3,241 chunks</span> from <span className="text-gray-300">487 files</span></div>
                        <div className="text-gray-500">  Embeddings stored → pgvector</div>
                        <div className="mt-1"><span className="text-emerald-400">✓</span> <span className="text-gray-300">Ready. Ask anything about this repo.</span></div>
                    </>
                )
        }
    }

    return (
        <div className="min-h-screen bg-[#0a0a0a] text-white" style={{ fontFamily: "'Inter', system-ui, sans-serif" }}>

            {/* Subtle dot grid background */}
            <div className="pointer-events-none fixed inset-0 z-0"
                style={{
                    backgroundImage: 'radial-gradient(circle, #ffffff08 1px, transparent 1px)',
                    backgroundSize: '32px 32px'
                }} />

            {/* Single top glow */}
            <div className="pointer-events-none fixed top-0 left-1/2 -translate-x-1/2 w-[600px] h-[300px] z-0"
                style={{ background: 'radial-gradient(ellipse at top, rgba(139,92,246,0.12) 0%, transparent 70%)' }} />

            {/* ── Navbar ── */}
            <header className="relative z-50 sticky top-0 bg-[#0a0a0a]/80 backdrop-blur-2xl">
                <div className="max-w-6xl mx-auto px-8 h-14 flex items-center justify-between">
                    <Link to="/" className="flex items-center gap-2 select-none group">
                        <div className="w-7 h-7 bg-gradient-to-br from-violet-500 to-fuchsia-600 rounded-lg flex items-center justify-center shadow-[0_0_12px_rgba(139,92,246,0.35)] group-hover:shadow-[0_0_18px_rgba(139,92,246,0.5)] transition-all">
                            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="white" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                                <polyline points="16 18 22 12 16 6" />
                                <polyline points="8 6 2 12 8 18" />
                            </svg>
                        </div>
                        <span className="font-mono text-2xl font-bold text-white tracking-tight leading-none">
                            {logoText}
                        </span>
                        <span className={`inline-block w-[2px] h-[22px] bg-violet-400 transition-opacity duration-75 ${isTyping ? 'opacity-100' : 'opacity-30'} animate-pulse`} />
                    </Link>

                    <nav className="flex items-center gap-1">
                        {user ? (
                            <Link to="/dashboard"
                                className="text-sm font-medium text-gray-400 hover:text-white hover:bg-white/[0.06] px-4 py-2 rounded-lg transition-all duration-150 cursor-pointer">
                                Dashboard
                            </Link>
                        ) : (
                            <>
                                <Link to="/login"
                                    className="text-sm font-medium text-gray-400 hover:text-white px-4 py-2 rounded-lg hover:bg-white/[0.05] transition-all duration-150 cursor-pointer">
                                    Sign in
                                </Link>
                                <Link to="/register"
                                    className="ml-1 text-sm font-semibold bg-white text-[#0a0a0a] hover:bg-[#f0f0f0] px-4 py-2 rounded-lg transition-all duration-150 hover:scale-[1.02] active:scale-[0.98] cursor-pointer">
                                    Get Started
                                </Link>
                            </>
                        )}
                    </nav>
                </div>
            </header>

            {/* ── Hero ── */}
            <section className="relative z-10 max-w-5xl mx-auto px-8 pt-28 pb-8 text-center">

                <h1 className="animate-fade-up text-[clamp(3rem,7vw,5.25rem)] font-bold leading-[1.08] tracking-[-0.04em] text-white mb-6">
                    Chat with any GitHub
                    <br />
                    <span className="gradient-text">repository</span> using AI
                </h1>

                <p className="animate-fade-up-1 text-[17px] text-[#6b7280] leading-[1.75] max-w-[480px] mx-auto mb-14">
                    Understand codebases instantly. Generate interview questions.
                    Debug errors with full source context.
                </p>

                <div className="animate-fade-up-2 flex flex-col sm:flex-row gap-3 justify-center items-center">
                    <Link to="/register" viewTransition
                        className="w-full sm:w-auto inline-flex items-center justify-center gap-2 text-[15px] font-semibold bg-white text-[#0a0a0a] hover:bg-[#f0f0f0] px-7 py-3 rounded-xl transition-all duration-150 hover:scale-[1.02] active:scale-[0.98] shadow-[0_0_0_1px_rgba(255,255,255,0.15)] cursor-pointer">
                        Get started free
                        <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
                            <path d="M1 7h12M8 2l5 5-5 5" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
                        </svg>
                    </Link>
                    <Link to="/login" viewTransition
                        className="w-full sm:w-auto inline-flex items-center justify-center text-[15px] font-medium text-[#6b7280] hover:text-white px-7 py-3 rounded-xl border border-white/[0.08] hover:border-white/[0.16] hover:bg-white/[0.04] transition-all duration-150 cursor-pointer">
                        Sign in
                    </Link>
                </div>

                {/* Terminal */}
                <div className="animate-fade-up-3 mt-24 mx-auto max-w-[660px] rounded-xl overflow-hidden border border-white/[0.07] shadow-[0_24px_80px_rgba(0,0,0,0.5)]">
                    <div className="flex items-center gap-2 px-4 py-3 bg-white/[0.03] border-b border-white/[0.06]">
                        <span className="w-3 h-3 rounded-full" style={{ background: '#FF5F57' }} />
                        <span className="w-3 h-3 rounded-full" style={{ background: '#FEBC2E' }} />
                        <span className="w-3 h-3 rounded-full" style={{ background: '#28C840' }} />
                        <span className="ml-3 text-[11px] text-[#4b5563] font-mono">demo — terminal</span>
                        <span className="ml-auto text-[11px] text-gray-500 font-mono flex items-center gap-1.5">
                            <span className="w-1.5 h-1.5 rounded-full bg-violet-400 animate-pulse"></span>
                            Interactive Click Demo
                        </span>
                    </div>
                    <div className="bg-[#0d0d0d] p-7 font-mono text-[14px] leading-[1.8] text-left transition-all duration-300 min-h-[170px]">
                        {renderTerminalContent()}
                    </div>
                </div>
            </section>

            {/* ── Divider ── */}
            <div className="relative z-10 max-w-6xl mx-auto px-8">
                <div className="h-px bg-gradient-to-r from-transparent via-white/[0.08] to-transparent" />
            </div>

            {/* ── How it works ── */}
            <section className="relative z-10 max-w-6xl mx-auto px-8 pt-10 pb-32">
                <RevealOnScroll>
                    <div className="max-w-3xl mx-auto mb-20 text-center flex flex-col items-center">
                        <h2 className="text-4xl md:text-5xl font-extrabold tracking-tight mb-6 leading-tight">
                            <span className="text-white">From repository to </span>
                            <span className="gradient-text">intelligence.</span>
                        </h2>
                        <p className="text-[#888] text-lg max-w-xl mx-auto leading-relaxed">
                            Connect your codebase and let RepoMind's engine instantly build a semantic map of your entire project.
                        </p>
                    </div>
                </RevealOnScroll>

                <RevealOnScroll delay={200}>
                    <div className="max-w-4xl mx-auto relative h-[420px] md:h-[220px]">
                    {/* Background glow */}
                    <div className="absolute inset-0 bg-violet-500/5 blur-[80px] rounded-full pointer-events-none" />
                    
                    {[
                        {
                            num: '1',
                            title: 'Paste repository link',
                            body: 'Submit any public or private GitHub repository URL to start indexing.',
                            demo: (
                                <div className="w-full h-[180px] md:h-[180px] p-5 rounded-2xl bg-[#0a0a0a] border border-white/[0.05] font-mono text-[12px] md:text-[13px] leading-relaxed text-left space-y-3 flex flex-col justify-center">
                                    <div className="text-gray-500 flex items-center gap-2"><span className="text-violet-400 font-semibold">Input:</span> github.com/your-org/your-repo <div className="w-1.5 h-3.5 bg-violet-400/80 animate-pulse" /></div>
                                    <div className="text-gray-300 text-xs font-sans mt-3 space-y-2">
                                        <div className="flex items-center gap-2 text-gray-400 animate-[fadeIn_0.4s_ease-out_0.4s_both]"><span className="w-2 h-2 rounded-full bg-emerald-400/80"></span> Repository access validated</div>
                                        <div className="flex items-center gap-2 text-violet-300 animate-[fadeIn_0.4s_ease-out_0.8s_both]">
                                            <svg className="w-3.5 h-3.5 animate-spin text-violet-400" viewBox="0 0 24 24" fill="none"><circle cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="3" strokeDasharray="32" strokeLinecap="round" className="opacity-70"></circle></svg>
                                            AI is scanning repository files...
                                        </div>
                                    </div>
                                </div>
                            )
                        },
                        {
                            num: '2',
                            title: 'Vector embedding build',
                            body: 'AI processes raw source files, generating semantic chunks stored directly inside pgvector.',
                            demo: (
                                <div className="w-full h-[180px] md:h-[180px] p-5 rounded-2xl bg-[#0a0a0a] border border-white/[0.05] font-mono text-[12px] md:text-[13px] leading-relaxed text-left space-y-3 flex flex-col justify-center relative overflow-hidden">
                                    <div className="absolute top-0 left-0 w-full h-[2px] bg-gradient-to-r from-transparent via-violet-500/50 to-transparent animate-[shimmer_2s_infinite]" />
                                    <div className="text-gray-500 flex items-center gap-2"><span className="text-violet-400 font-semibold">System:</span> AI Embedding Engine Active</div>
                                    <div className="text-gray-300 text-xs font-sans space-y-2 mt-3 relative z-10">
                                        <p className="text-gray-400 flex items-center gap-2"><span className="text-violet-500/70">✦</span> Abstracting 487 syntax trees...</p>
                                        <p className="text-gray-400 flex items-center gap-2"><span className="text-violet-500/70">✦</span> Generating dense embeddings...</p>
                                        <p className="text-emerald-400/90 mt-2 flex items-center gap-2 font-medium bg-emerald-500/10 w-fit px-2 py-0.5 rounded">✓ 3,241 semantic chunks mapped</p>
                                    </div>
                                </div>
                            )
                        },
                        {
                            num: '3',
                            title: 'Chat & ask questions',
                            body: 'Interact directly with the codebase to debug stack traces, prepare for interviews, or explain files.',
                            demo: (
                                <div className="w-full h-[180px] md:h-[180px] p-5 rounded-2xl bg-[#0a0a0a] border border-white/[0.05] font-mono text-[12px] md:text-[13px] leading-relaxed text-left space-y-3 flex flex-col justify-center">
                                    <div className="text-gray-500"><span className="text-violet-400 font-semibold">You:</span> Where is the login handler?</div>
                                    <div className="text-violet-300/80 font-sans text-[12px] flex items-center gap-1.5 mt-2">
                                        <span className="w-1.5 h-1.5 rounded-full bg-violet-400 animate-pulse"></span> RepoMind AI
                                    </div>
                                    <div className="text-gray-300 text-[13px] font-sans leading-relaxed border-l-2 border-violet-500/40 pl-3 mt-1 relative">
                                        <span className="absolute -left-[9px] top-1.5 w-4 h-4 bg-[#0a0a0a] flex items-center justify-center rounded-full"><span className="w-1 h-1 bg-violet-400 rounded-full"></span></span>
                                        The login handler is located in <code className="text-violet-200 bg-violet-500/15 px-1.5 py-0.5 rounded border border-violet-500/20 mx-1">AuthController.login()</code> starting at line 42.
                                        <div className="w-1.5 h-3.5 bg-violet-400/60 inline-block align-middle ml-1 animate-pulse" />
                                    </div>
                                </div>
                            )
                        }
                    ].map(({ num, title, body, demo }) => (
                        <div 
                            key={num} 
                            className={`absolute inset-0 w-full flex flex-col md:flex-row items-center gap-10 md:gap-14 transition-opacity duration-500 ease-in-out ${
                                activeStep === num 
                                    ? 'opacity-100 z-10' 
                                    : 'opacity-0 z-0 pointer-events-none'
                            }`}
                        >
                            <div className="flex-1 space-y-4 md:pr-4 text-center md:text-left">
                                <h3 className="text-2xl md:text-3xl font-semibold text-white tracking-tight leading-snug">{title}</h3>
                                <p className="text-sm md:text-base text-[#888] leading-relaxed max-w-sm mx-auto md:mx-0">{body}</p>
                            </div>
                            <div className="flex-1 w-full relative">
                                <div className="absolute -inset-4 bg-violet-500/10 blur-[30px] rounded-full opacity-40 pointer-events-none" />
                                <div className="relative border border-white/[0.05] bg-[#050505]/80 rounded-2xl p-2 shadow-[0_0_30px_rgba(139,92,246,0.03)] backdrop-blur-xl">
                                    {demo}
                                </div>
                            </div>
                        </div>
                    ))}
                    
                    {/* Navigation Progress Dots */}
                    <div className="absolute -bottom-12 left-0 right-0 flex justify-center gap-3">
                        {['1', '2', '3'].map((num) => (
                            <button
                                key={num}
                                onClick={() => setActiveStep(num)}
                                className={`h-1 rounded-full transition-all duration-500 cursor-pointer ${
                                    activeStep === num ? 'w-10 bg-white' : 'w-2 bg-white/20 hover:bg-white/40'
                                }`}
                                aria-label={`Go to step ${num}`}
                            />
                        ))}
                    </div>
                    </div>
                </RevealOnScroll>
            </section>



            {/* ── CTA ── */}
            <section className="relative z-10 py-24 border-t border-white/[0.05]">
                <RevealOnScroll>
                    <div className="max-w-xl mx-auto px-8 text-center relative">
                    <h2 className="text-4xl md:text-5xl font-extrabold text-white tracking-tight leading-tight mb-4">
                        Start exploring <br className="hidden md:block" />
                        <span className="gradient-text">your codebase</span> today.
                    </h2>
                    <p className="text-[15px] md:text-[17px] text-[#888] mb-10 max-w-md mx-auto">
                        Join thousands of developers navigating complex codebases in seconds. Free to use. No credit card required.
                    </p>
                    <Link to="/register" viewTransition
                        className="inline-flex items-center gap-2 text-[15px] font-semibold bg-white text-[#0a0a0a] hover:bg-[#f0f0f0] px-8 py-3.5 rounded-xl transition-all duration-150 hover:scale-[1.02] active:scale-[0.98] shadow-lg shadow-white/5 cursor-pointer">
                        Get started for free
                        <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
                            <path d="M1 7h12M8 2l5 5-5 5" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
                        </svg>
                    </Link>
                    </div>
                </RevealOnScroll>
            </section>

            {/* ── Footer ── */}
            <footer className="relative z-10 border-t border-white/[0.05] bg-[#050505] pt-16 pb-8">
                <div className="max-w-6xl mx-auto px-8">
                    <RevealOnScroll>
                        <div className="grid grid-cols-2 md:grid-cols-4 gap-12 mb-16">
                        <div className="col-span-2 md:col-span-1">
                            <div className="flex items-center gap-2 mb-4 select-none">
                                <div className="w-6 h-6 bg-gradient-to-br from-violet-500 to-fuchsia-600 rounded-md flex items-center justify-center shadow-[0_0_8px_rgba(139,92,246,0.25)]">
                                    <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="white" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                                        <polyline points="16 18 22 12 16 6" />
                                        <polyline points="8 6 2 12 8 18" />
                                    </svg>
                                </div>
                                <span className="font-mono text-xl font-bold text-white tracking-tight">RepoMind</span>
                            </div>
                            <p className="text-[13px] text-[#6b7280] leading-relaxed max-w-[200px]">
                                AI-powered code understanding and intelligent search for modern engineering teams.
                            </p>
                        </div>
                        <div>
                            <h4 className="text-white font-semibold text-[13px] mb-4 tracking-wide uppercase">Product</h4>
                            <ul className="space-y-3 text-[14px] text-[#6b7280]">
                                <li><Link to="/" className="hover:text-white transition-colors">Features</Link></li>
                                <li><Link to="/" className="hover:text-white transition-colors">Integrations</Link></li>
                                <li><Link to="/" className="hover:text-white transition-colors">Pricing</Link></li>
                            </ul>
                        </div>
                        <div>
                            <h4 className="text-white font-semibold text-[13px] mb-4 tracking-wide uppercase">Company</h4>
                            <ul className="space-y-3 text-[14px] text-[#6b7280]">
                                <li><Link to="/" className="hover:text-white transition-colors">About</Link></li>
                                <li><Link to="/" className="hover:text-white transition-colors">Blog</Link></li>
                                <li><Link to="/" className="hover:text-white transition-colors">Contact</Link></li>
                            </ul>
                        </div>
                        <div>
                            <h4 className="text-white font-semibold text-[13px] mb-4 tracking-wide uppercase">Legal</h4>
                            <ul className="space-y-3 text-[14px] text-[#6b7280]">
                                <li><Link to="/" className="hover:text-white transition-colors">Privacy Policy</Link></li>
                                <li><Link to="/" className="hover:text-white transition-colors">Terms of Service</Link></li>
                            </ul>
                        </div>
                        </div>
                    </RevealOnScroll>
                    <div className="border-t border-white/[0.05] pt-8 flex flex-col md:flex-row items-center justify-between gap-4">
                        <span className="text-[13px] text-[#6b7280]">© {new Date().getFullYear()} RepoMind. All rights reserved.</span>
                        <div className="flex gap-4 text-[#6b7280]">
                            <a href="#" className="hover:text-white transition-colors" aria-label="GitHub">
                                <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor"><path d="M12 2C6.477 2 2 6.484 2 12.017c0 4.425 2.865 8.18 6.839 9.504.5.092.682-.217.682-.483 0-.237-.008-.868-.013-1.703-2.782.605-3.369-1.343-3.369-1.343-.454-1.158-1.11-1.466-1.11-1.466-.908-.62.069-.608.069-.608 1.003.07 1.531 1.032 1.531 1.032.892 1.53 2.341 1.088 2.91.832.092-.647.35-1.088.636-1.338-2.22-.253-4.555-1.113-4.555-4.951 0-1.093.39-1.988 1.029-2.688-.103-.253-.446-1.272.098-2.65 0 0 .84-.27 2.75 1.026A9.564 9.564 0 0112 6.844c.85.004 1.705.115 2.504.337 1.909-1.296 2.747-1.027 2.747-1.027.546 1.379.202 2.398.1 2.651.64.7 1.028 1.595 1.028 2.688 0 3.848-2.339 4.695-4.566 4.943.359.309.678.92.678 1.855 0 1.338-.012 2.419-.012 2.747 0 .268.18.58.688.482A10.019 10.019 0 0022 12.017C22 6.484 17.522 2 12 2z" /></svg>
                            </a>
                            <a href="#" className="hover:text-white transition-colors" aria-label="Twitter">
                                <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor"><path d="M23.953 4.57a10 10 0 01-2.825.775 4.958 4.958 0 002.163-2.723c-.951.555-2.005.959-3.127 1.184a4.92 4.92 0 00-8.384 4.482C7.69 8.095 4.067 6.13 1.64 3.162a4.822 4.822 0 00-.666 2.475c0 1.71.87 3.213 2.188 4.096a4.904 4.904 0 01-2.228-.616v.06a4.923 4.923 0 003.946 4.827 4.996 4.996 0 01-2.212.085 4.936 4.936 0 004.604 3.417 9.867 9.867 0 01-6.102 2.105c-.39 0-.779-.023-1.17-.067a13.995 13.995 0 007.557 2.209c9.053 0 13.998-7.496 13.998-13.985 0-.21 0-.42-.015-.63A9.935 9.935 0 0024 4.59z"/></svg>
                            </a>
                        </div>
                    </div>
                </div>
            </footer>

            {/* Scroll to Top Button */}
            <button
                onClick={scrollToTop}
                className={`fixed bottom-8 left-1/2 z-50 p-3 rounded-full bg-[#0a0a0a]/80 hover:bg-white/[0.1] text-white border border-white/[0.15] backdrop-blur-xl shadow-lg transition-all duration-300 cursor-pointer flex items-center justify-center group ${showScrollTop ? 'opacity-100 translate-y-0 -translate-x-1/2' : 'opacity-0 translate-y-10 -translate-x-1/2 pointer-events-none'}`}
                aria-label="Scroll to top"
            >
                <svg className="w-5 h-5 text-gray-400 group-hover:text-white group-hover:-translate-y-0.5 transition-all duration-300" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                    <path d="M12 19V5M5 12l7-7 7 7" />
                </svg>
            </button>
        </div>
    )
}