import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { getProfileStats } from '../api/client'
import NavBar from '../components/layout/NavBar'
import StatCard from '../components/common/StatCard'

const PROVIDER_STYLES = {
    LOCAL:  'text-neutral-400 bg-white/[0.03] border-white/[0.08]',
    GOOGLE: 'text-sky-400 bg-sky-500/10 border-sky-500/20',
    GITHUB: 'text-violet-400 bg-violet-500/10 border-violet-500/20'
}

export default function ProfilePage() {
    const { user, logout } = useAuth()
    const navigate = useNavigate()
    const [stats, setStats] = useState(null)
    const [loadingStats, setLoadingStats] = useState(true)

    useEffect(() => {
        getProfileStats()
            .then(setStats)
            .catch(() => {})
            .finally(() => setLoadingStats(false))
    }, [])

    async function handleLogout() {
        await logout()
        navigate('/')
    }

    const initials = user?.name
        ? user.name.split(' ').map(n => n[0]).join('').toUpperCase().slice(0, 2)
        : user?.email?.[0]?.toUpperCase() || '?'

    const providerStyle = PROVIDER_STYLES[user?.provider] || PROVIDER_STYLES.LOCAL

    const memberSince = stats?.memberSince
        ? new Date(stats.memberSince).toLocaleDateString('en-US', {
              year: 'numeric', month: 'long', day: 'numeric'
          })
        : null

    return (
        <div className="min-h-screen bg-[#080809] text-white flex flex-col relative overflow-x-hidden">
            {/* Subtle glow background */}
            <div className="pointer-events-none fixed top-0 right-0 w-[600px] h-[600px] z-0 opacity-20"
                style={{ background: 'radial-gradient(circle at 100% 0%, rgba(139,92,246,0.15) 0%, transparent 60%)' }} />
            <div className="pointer-events-none fixed bottom-0 left-0 w-[500px] h-[500px] z-0 opacity-15"
                style={{ background: 'radial-gradient(circle at 0% 100%, rgba(236,72,153,0.1) 0%, transparent 60%)' }} />

            <NavBar />

            <main className="flex-1 max-w-3xl mx-auto w-full px-6 py-12 space-y-8 relative z-10 animate-fade-up">
                
                <div>
                    <h1 className="text-2xl font-bold tracking-tight text-white mb-2">Account Profile</h1>
                    <p className="text-sm text-neutral-400">Manage your identity, view statistics, and configure settings.</p>
                </div>

                <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
                    {/* Identity card - spans 2 cols */}
                    <div className="md:col-span-2 relative overflow-hidden bg-[#0d0d12]/50 border border-white/[0.06] rounded-2xl p-8 flex items-center gap-6 shadow-2xl backdrop-blur-md">
                        <div className="absolute top-0 right-0 w-[200px] h-[200px] bg-violet-600/10 blur-[60px] rounded-full pointer-events-none" />
                        
                        <div className="relative z-10 w-20 h-20 rounded-full bg-gradient-to-br from-violet-600/40 to-fuchsia-600/40 border border-white/[0.1] flex items-center justify-center text-3xl font-bold text-white shrink-0 shadow-lg shadow-violet-500/20">
                            {initials}
                        </div>
                        <div className="relative z-10 min-w-0 space-y-2 flex-1">
                            <div>
                                <p className="font-bold text-white text-2xl leading-tight tracking-tight">
                                    {user?.name || 'No name set'}
                                </p>
                                <p className="text-neutral-400 text-[15px] truncate mt-1">{user?.email}</p>
                            </div>
                            <div className="pt-2 flex items-center gap-3">
                                <span className={`text-[10px] font-bold uppercase tracking-widest border px-3 py-1 rounded-full inline-block ${providerStyle}`}>
                                    {user?.provider || 'Email'}
                                </span>
                                {memberSince && (
                                    <span className="text-xs text-neutral-500 font-medium">Joined {memberSince}</span>
                                )}
                            </div>
                        </div>
                    </div>

                    {/* Quick Stats - spans 1 col */}
                    <div className="md:col-span-1 space-y-4 flex flex-col">
                        <div className="bg-[#0d0d12]/50 border border-white/[0.06] rounded-2xl p-5 backdrop-blur-md shadow-xl flex flex-col justify-center flex-1 relative overflow-hidden">
                            <div className="absolute bottom-0 right-0 w-[100px] h-[100px] bg-sky-500/10 blur-[40px] rounded-full pointer-events-none" />
                            <div className="relative z-10">
                                <p className="text-sm font-medium text-neutral-400 mb-1">Repos Analyzed</p>
                                <p className="text-3xl font-bold text-white">{loadingStats ? '—' : stats?.reposAnalyzed}</p>
                            </div>
                            <div className="relative z-10 mt-6">
                                <p className="text-sm font-medium text-neutral-400 mb-1">Total Chats</p>
                                <p className="text-3xl font-bold text-white">{loadingStats ? '—' : stats?.conversationsStarted}</p>
                            </div>
                        </div>
                        
                        <button
                            onClick={handleLogout}
                            className="cursor-pointer w-full px-5 py-3.5 rounded-2xl border border-red-500/10 bg-red-500/5 text-[14px] font-semibold text-red-400 hover:text-white hover:bg-red-500/90 hover:border-red-500 transition-all duration-300 shadow-sm"
                        >
                            Sign out
                        </button>
                    </div>
                </div>

            </main>
        </div>
    )
}
