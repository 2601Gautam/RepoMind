import { useState } from 'react'
import { AuthProvider, useAuth } from './context/AuthContext'
import Header from './components/layout/Header'
import IngestPage from './pages/IngestPage'
import ChatPage from './pages/ChatPage'
import LoginPage from './pages/LoginPage'
import RegisterPage from './pages/RegisterPage'
import OAuthCallbackPage from './pages/OAuthCallbackPage'

// AppContent is separate from App so it can use useAuth()
// useAuth() requires being inside AuthProvider
// If AppContent was inside App directly, AuthProvider would not wrap it yet
function AppContent() {
    const { user, loading, logout } = useAuth()
    const [selectedRepo, setSelectedRepo] = useState(null)
    const [authPage, setAuthPage] = useState('login') // 'login' | 'register'

    // Handle OAuth2 callback route
    if (window.location.pathname === '/auth/callback') {
        return <OAuthCallbackPage />
    }

    // Show loading spinner while checking auth status on page load
    if (loading) {
        return (
            <div className="min-h-screen bg-gray-950 flex items-center justify-center">
                <div className="w-8 h-8 border-2 border-blue-500 border-t-transparent rounded-full animate-spin" />
            </div>
        )
    }

    // Not logged in — show auth pages
    if (!user) {
        return authPage === 'login'
            ? <LoginPage onNavigateToRegister={() => setAuthPage('register')} />
            : <RegisterPage onNavigateToLogin={() => setAuthPage('login')} />
    }

    // Logged in — show main app
    return (
        <div className="min-h-screen bg-gray-950 text-white overflow-hidden">
            <Header
                repoName={selectedRepo?.repoName}
                onLogoClick={() => setSelectedRepo(null)}
                user={user}
                onLogout={logout}
            />
            <main className="max-w-4xl mx-auto px-6">
                {selectedRepo
                    ? <ChatPage repo={selectedRepo} onBack={() => setSelectedRepo(null)} />
                    : <IngestPage onSelect={setSelectedRepo} />
                }
            </main>
        </div>
    )
}

export default function App() {
    return (
        // AuthProvider wraps everything so useAuth() works anywhere in the tree
        <AuthProvider>
            <AppContent />
        </AuthProvider>
    )
}