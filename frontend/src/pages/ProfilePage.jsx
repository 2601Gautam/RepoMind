import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { getProfileStats } from '../api/client'
import NavBar from '../components/layout/NavBar'
import LoadingSpinner from '../components/common/LoadingSpinner'

function StatCard({ label, value, loading }) {
    return (
        <div className="bg-[#050505] border border-white/[0.06] rounded-xl p-5 text-center">
            {loading ? (
                <div className="flex justify-center py-2">
                    <LoadingSpinner size="sm" />
                </div>
            ) : (
                <p className="text-2xl font-bold text-white">{value ?? '—'}</p>
            )}
            <p className="text-[13px] text-[#666] mt-1">{label}</p>
        </div>
    )
}

const PROVIDER_STYLES = {
    LOCAL:  'text-[#888] bg-white/[0.03] border-white/[0.08]',
    GOOGLE: 'text-blue-400 bg-blue-500/10 border-blue-500/20',
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
        <div className="min-h-screen bg-[#0a0a0a] text-white flex flex-col">
            <NavBar />

            <main className="flex-1 max-w-xl mx-auto w-full px-6 py-12 space-y-6">

                {/* Identity card */}
                <div className="bg-[#050505] border border-white/[0.06] rounded-2xl p-6 flex items-center gap-5">
                    <div className="w-14 h-14 rounded-full bg-[#1a1a1a] border border-white/[0.1] flex items-center justify-center text-lg font-bold text-gray-200 shrink-0">
                        {initials}
                    </div>
                    <div className="min-w-0 space-y-1">
                        <p className="font-semibold text-white text-[17px] leading-tight">
                            {user?.name || 'No name set'}
                        </p>
                        <p className="text-[#666] text-sm truncate">{user?.email}</p>
                        <span className={`text-[11px] font-semibold uppercase tracking-wider border px-2 py-0.5 rounded-full inline-block ${providerStyle}`}>
                            {user?.provider || 'Email'}
                        </span>
                    </div>
                </div>

                {/* Stats */}
                <div className="grid grid-cols-2 gap-3">
                    <StatCard label="Repositories Analyzed" value={stats?.reposAnalyzed} loading={loadingStats} />
                    <StatCard label="Conversations Started" value={stats?.conversationsStarted} loading={loadingStats} />
                </div>

                {/* Member since */}
                {memberSince && (
                    <div className="bg-[#050505] border border-white/[0.06] rounded-xl px-5 py-4 flex items-center justify-between">
                        <span className="text-[13px] text-[#666]">Member since</span>
                        <span className="text-[13px] text-gray-300 font-medium">{memberSince}</span>
                    </div>
                )}

                {/* Sign out */}
                <div className="pt-2">
                    <button
                        onClick={handleLogout}
                        className="w-full py-3 rounded-xl border border-white/[0.06] text-[14px] font-medium text-[#888] hover:text-white hover:border-white/[0.15] hover:bg-white/[0.03] transition-all duration-150"
                    >
                        Sign out
                    </button>
                </div>
            </main>
        </div>
    )
}