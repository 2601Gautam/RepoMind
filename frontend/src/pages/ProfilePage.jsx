import { useState, useEffect } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { getProfileStats } from '../api/client'
import NavBar from '../components/layout/NavBar'
import LoadingSpinner from '../components/common/LoadingSpinner'

// Stat card used in the stats grid
function StatCard({ label, value, loading }) {
    return (
        <div className="bg-gray-900 border border-gray-700 rounded-xl p-5 text-center">
            {loading ? (
                <div className="flex justify-center py-2">
                    <LoadingSpinner size="sm" />
                </div>
            ) : (
                <p className="text-3xl font-bold text-blue-400">{value ?? '—'}</p>
            )}
            <p className="text-sm text-gray-400 mt-1">{label}</p>
        </div>
    )
}

const PROVIDER_STYLES = {
    LOCAL:  'text-gray-400 bg-gray-400/10 border-gray-400/20',
    GOOGLE: 'text-blue-400 bg-blue-400/10 border-blue-400/20',
    GITHUB: 'text-purple-400 bg-purple-400/10 border-purple-400/20'
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

    // Generate initials from name or first char of email
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
        <div className="min-h-screen bg-gray-950 text-white">
            <NavBar />

            <main className="max-w-2xl mx-auto px-6 py-8 space-y-6">
                <h2 className="text-xl font-semibold">Profile</h2>

                {/* Identity card */}
                <div className="bg-gray-900 border border-gray-700 rounded-xl p-6">
                    <div className="flex items-center gap-4">
                        <div className="w-16 h-16 rounded-full bg-blue-600 flex items-center justify-center text-xl font-bold shrink-0">
                            {initials}
                        </div>
                        <div className="space-y-1 min-w-0">
                            <p className="font-semibold text-lg text-white">
                                {user?.name || 'No name set'}
                            </p>
                            <p className="text-gray-400 text-sm truncate">{user?.email}</p>
                            <span className={`text-xs border px-2 py-0.5 rounded-full inline-block ${providerStyle}`}>
                                {user?.provider || 'LOCAL'}
                            </span>
                        </div>
                    </div>
                </div>

                {/* Stats grid */}
                <div className="grid grid-cols-2 gap-4">
                    <StatCard
                        label="Repositories Analyzed"
                        value={stats?.reposAnalyzed}
                        loading={loadingStats}
                    />
                    <StatCard
                        label="Conversations Started"
                        value={stats?.conversationsStarted}
                        loading={loadingStats}
                    />
                </div>

                {/* Member since */}
                {memberSince && (
                    <div className="bg-gray-900 border border-gray-700 rounded-xl p-5">
                        <p className="text-xs text-gray-500 uppercase tracking-wider mb-1">Member Since</p>
                        <p className="text-white">{memberSince}</p>
                    </div>
                )}

                {/* Account actions */}
                <div className="bg-gray-900 border border-gray-700 rounded-xl p-5 space-y-3">
                    <p className="text-sm font-medium text-gray-300">Account</p>
                    <div className="flex flex-col gap-2">
                        <Link to="/dashboard"
                            className="text-sm text-gray-400 hover:text-white transition-colors">
                            ← Back to Dashboard
                        </Link>
                        <button onClick={handleLogout}
                            className="text-sm text-red-400 hover:text-red-300 transition-colors text-left">
                            Sign out
                        </button>
                    </div>
                </div>
            </main>
        </div>
    )
}