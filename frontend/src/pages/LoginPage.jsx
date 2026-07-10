import { useState, useEffect } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'

const GoogleIcon = () => (
    <svg className="w-5 h-5" viewBox="0 0 24 24">
        <path fill="#4285F4" d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z"/>
        <path fill="#34A853" d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"/>
        <path fill="#FBBC05" d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z"/>
        <path fill="#EA4335" d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z"/>
    </svg>
)

const GitHubIcon = () => (
    <svg className="w-5 h-5" fill="currentColor" viewBox="0 0 24 24">
        <path fillRule="evenodd" clipRule="evenodd"
            d="M12 2C6.477 2 2 6.484 2 12.017c0 4.425 2.865 8.18 6.839 9.504.5.092.682-.217.682-.483 0-.237-.008-.868-.013-1.703-2.782.605-3.369-1.343-3.369-1.343-.454-1.158-1.11-1.466-1.11-1.466-.908-.62.069-.608.069-.608 1.003.07 1.531 1.032 1.531 1.032.892 1.53 2.341 1.088 2.91.832.092-.647.35-1.088.636-1.338-2.22-.253-4.555-1.113-4.555-4.951 0-1.093.39-1.988 1.029-2.688-.103-.253-.446-1.272.098-2.65 0 0 .84-.27 2.75 1.026A9.564 9.564 0 0112 6.844c.85.004 1.705.115 2.504.337 1.909-1.296 2.747-1.027 2.747-1.027.546 1.379.202 2.398.1 2.651.64.7 1.028 1.595 1.028 2.688 0 3.848-2.339 4.695-4.566 4.942.359.31.678.921.678 1.856 0 1.338-.012 2.419-.012 2.747 0 .268.18.58.688.482A10.019 10.019 0 0022 12.017C22 6.484 17.522 2 12 2z"/>
    </svg>
)

export default function LoginPage() {
    const { user, loading: authLoading, login, loginWithGoogle, loginWithGitHub } = useAuth()
    const navigate = useNavigate()
    const [email, setEmail] = useState('')
    const [password, setPassword] = useState('')
    const [showPassword, setShowPassword] = useState(false)
    const [error, setError] = useState('')
    const [loading, setLoading] = useState(false)

    useEffect(() => {
        if (!authLoading && user) {
            navigate('/dashboard', { replace: true })
        }
    }, [user, authLoading, navigate])

     // Check for OAuth error in URL params — Spring redirects here on OAuth failure
    const oauthError = new URLSearchParams(window.location.search).get('error')

    async function handleLogin(e) {
        e.preventDefault()

        if (!email.trim() || !password) {
            setError('Please fill in all fields')
            return
        }

        const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
        if (!emailRegex.test(email.trim())) {
            setError('Please enter a valid email address')
            return
        }

        setError('')
        setLoading(true)
        try {
            await login(email, password)
            navigate('/dashboard')
            // AuthContext updates user state — App re-renders and shows main content
        } catch (err) {
            setError(err.message)
        } finally {
            setLoading(false)
        }
    }

    return (
        <div className="min-h-screen bg-[#0a0a0a] text-white flex flex-col items-center justify-center px-4 py-16 relative overflow-hidden font-sans">
            {/* Subtle dot grid background */}
            <div className="pointer-events-none absolute inset-0 z-0"
                style={{
                    backgroundImage: 'radial-gradient(circle, #ffffff08 1px, transparent 1px)',
                    backgroundSize: '32px 32px'
                }} />

            {/* Ambient Animated Glows */}
            <div className="pointer-events-none absolute top-[-10%] left-[-10%] w-[50%] h-[50%] bg-violet-600/10 blur-[120px] rounded-full mix-blend-screen animate-[pulse_8s_ease-in-out_infinite]" />
            <div className="pointer-events-none absolute bottom-[-10%] right-[-10%] w-[50%] h-[50%] bg-fuchsia-600/10 blur-[120px] rounded-full mix-blend-screen animate-[pulse_10s_ease-in-out_infinite]" />
            
            <div className="pointer-events-none absolute top-0 left-1/2 -translate-x-1/2 w-[600px] h-[300px] z-0"
                style={{ background: 'radial-gradient(ellipse at top, rgba(139,92,246,0.15) 0%, transparent 70%)' }} />

            <div className="w-full max-w-md space-y-10 relative z-10 animate-fade-up">
                <div className="text-center flex flex-col items-center">
                    <Link to="/" className="inline-flex flex-col items-center gap-3 group select-none">
                        <div className="w-10 h-10 bg-gradient-to-br from-violet-500 to-fuchsia-600 rounded-xl flex items-center justify-center shadow-[0_0_16px_rgba(139,92,246,0.3)] group-hover:shadow-[0_0_24px_rgba(139,92,246,0.45)] transition-all duration-300">
                            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="white" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                                <polyline points="16 18 22 12 16 6" />
                                <polyline points="8 6 2 12 8 18" />
                            </svg>
                        </div>
                        <span className="text-3xl md:text-4xl font-extrabold text-white tracking-tighter leading-none font-sans">
                            RepoMind
                        </span>
                    </Link>
                    <p className="mt-5 text-[#888] text-[16px]">Sign in to your account</p>
                </div>

                <div className="bg-[#050505]/80 backdrop-blur-2xl border border-white/[0.05] rounded-3xl p-8 md:p-10 shadow-2xl animate-fade-up-1">
                    {oauthError && (
                        <div className="mb-6 bg-red-950/30 border border-red-800/40 rounded-xl px-4 py-3 text-sm text-red-400 text-center">
                            OAuth sign in failed. Please try again.
                        </div>
                    )}

                    <form onSubmit={handleLogin} className="space-y-5">
                        <div>
                            <label className="block text-sm text-[#888] mb-2 font-medium">Email</label>
                            <input type="email" value={email} onChange={e => setEmail(e.target.value)}
                                required placeholder="you@example.com"
                                className="w-full bg-[#0a0a0a] border border-white/[0.1] rounded-xl px-4 py-3 text-white text-[15px] placeholder-[#555] focus:outline-none focus:border-violet-500/50 focus:ring-1 focus:ring-violet-500/50 transition-all" />
                        </div>
                        <div>
                            <div className="flex justify-between items-center mb-2">
                                <label className="block text-sm text-[#888] font-medium">Password</label>
                            </div>
                            <div className="relative">
                                <input type={showPassword ? "text" : "password"} value={password} onChange={e => setPassword(e.target.value)}
                                    required placeholder="••••••••"
                                    className="w-full bg-[#0a0a0a] border border-white/[0.1] rounded-xl px-4 py-3 text-white text-[15px] placeholder-[#555] focus:outline-none focus:border-violet-500/50 focus:ring-1 focus:ring-violet-500/50 transition-all pr-12" />
                                <button type="button" onClick={() => setShowPassword(!showPassword)}
                                    className="absolute right-4 top-1/2 -translate-y-1/2 text-[#666] hover:text-[#aaa] transition-colors focus:outline-none cursor-pointer" aria-label={showPassword ? "Hide password" : "Show password"}>
                                    {showPassword ? (
                                        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24"/><line x1="1" y1="1" x2="23" y2="23"/></svg>
                                    ) : (
                                        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/></svg>
                                    )}
                                </button>
                            </div>
                        </div>
                        {error && <p className="text-red-400 text-sm mt-1">{error}</p>}
                        <button type="submit" disabled={loading}
                            className={`w-full mt-2 font-semibold py-3 rounded-xl transition-all duration-300 flex justify-center items-center shadow-[0_0_20px_rgba(255,255,255,0.1)] relative overflow-hidden ${
                                loading 
                                    ? 'bg-[#e0e0e0] text-[#0a0a0a] cursor-wait shadow-[0_0_30px_rgba(255,255,255,0.2)]'
                                    : 'bg-white text-[#0a0a0a] hover:bg-[#f0f0f0] hover:scale-[1.01] active:scale-[0.99] cursor-pointer'
                            }`}>
                            <div className="relative flex justify-center items-center h-6 w-full">
                                <span className={`absolute transition-all duration-300 ${loading ? 'opacity-0 scale-95 translate-y-4' : 'opacity-100 scale-100 translate-y-0'}`}>
                                    Sign in
                                </span>
                                <div className={`absolute flex items-center gap-2.5 transition-all duration-300 ${loading ? 'opacity-100 scale-100 translate-y-0' : 'opacity-0 scale-95 -translate-y-4'}`}>
                                    <svg className="animate-spin h-4 w-4" viewBox="0 0 24 24" fill="none">
                                        <circle cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="3" className="opacity-20" />
                                        <path d="M12 2C17.5228 2 22 6.47715 22 12" stroke="currentColor" strokeWidth="3" strokeLinecap="round" />
                                    </svg>
                                    <span className="font-medium tracking-wide">Authenticating...</span>
                                </div>
                            </div>
                        </button>
                    </form>

                    <div className="flex items-center gap-3 my-8">
                        <div className="flex-1 border-t border-white/[0.05]" />
                        <span className="text-xs text-[#555] uppercase tracking-wider font-medium">or continue with</span>
                        <div className="flex-1 border-t border-white/[0.05]" />
                    </div>

                    <div className="grid grid-cols-2 gap-4">
                        <button onClick={loginWithGoogle}
                            className="flex items-center justify-center gap-2.5 bg-white/[0.02] hover:bg-white/[0.06] border border-white/[0.05] hover:border-white/[0.1] rounded-xl py-3 px-4 transition-all duration-200 cursor-pointer"
                            aria-label="Continue with Google">
                            <GoogleIcon />
                            <span className="text-[14px] font-medium text-gray-300">Google</span>
                        </button>

                        <button onClick={loginWithGitHub}
                            className="flex items-center justify-center gap-2.5 bg-white/[0.02] hover:bg-white/[0.06] border border-white/[0.05] hover:border-white/[0.1] rounded-xl py-3 px-4 transition-all duration-200 cursor-pointer"
                            aria-label="Continue with GitHub">
                            <GitHubIcon />
                            <span className="text-[14px] font-medium text-gray-300">GitHub</span>
                        </button>
                    </div>

                    <p className="text-center text-sm text-[#888] mt-8">
                        Don't have an account?{' '}
                        <Link to="/register" viewTransition className="text-white hover:text-violet-300 transition-colors font-medium">
                            Register
                        </Link>
                    </p>
                </div>
            </div>
        </div>
    )
}