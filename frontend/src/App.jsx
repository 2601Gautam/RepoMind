import { useEffect } from 'react'
import { Routes, Route, Navigate } from 'react-router-dom'
import ProtectedRoute from './components/common/ProtectedRoute'
import { AuthProvider, useAuth } from './context/AuthContext'
// Pages
import LandingPage from './pages/LandingPage'
import LoginPage from './pages/LoginPage'
import RegisterPage from './pages/RegisterPage'
import OAuthCallbackPage from './pages/OAuthCallbackPage'
import DashboardPage from './pages/DashboardPage'
import ChatPage from './pages/ChatPage'
import InterviewPage from './pages/InterviewPage'
import DebugPage from './pages/DebugPage'
import ProfilePage from './pages/ProfilePage'
import AllReposPage from './pages/AllReposPage'
import DashboardLayout from './components/layout/DashboardLayout'

// AppContent is separate from App so it can use useAuth()
// useAuth() requires being inside AuthProvider
// If AppContent was inside App directly, AuthProvider would not wrap it yet


// Inner component so it can access useAuth()
// App wraps with AuthProvider — AppRoutes lives inside it
function AppRoutes() {
    const { user } = useAuth()

    // Keep-alive: pings backend every 14 minutes to prevent Render free tier sleep
    // Only runs when a user is logged in — no point pinging when nobody is using it
    // Without this, first request after 15 minutes of idle takes 30-50 seconds
    useEffect(() => {
        if (!user) return

        const BASE = import.meta.env.VITE_API_URL || ''
        const ping = () => {
            fetch(`${BASE}/api/repos?page=0&size=1`, {
                credentials: 'include'
            }).catch(() => {})
        }

        const interval = setInterval(ping, 14 * 60 * 1000)
        return () => clearInterval(interval)
    }, [user])

    return (
        <Routes>
            {/* Public routes */}
            <Route path="/" element={<LandingPage />} />
            <Route path="/login" element={<LoginPage />} />
            <Route path="/register" element={<RegisterPage />} />
            <Route path="/auth/callback" element={<OAuthCallbackPage />} />

            {/* Protected routes */}
            <Route path="/dashboard" element={
                <ProtectedRoute><DashboardLayout><DashboardPage /></DashboardLayout></ProtectedRoute>
            } />
            <Route path="/repositories" element={
                <ProtectedRoute><DashboardLayout><AllReposPage /></DashboardLayout></ProtectedRoute>
            } />
            <Route path="/chat" element={
                <ProtectedRoute><DashboardLayout><ChatPage /></DashboardLayout></ProtectedRoute>
            } />
            <Route path="/chat/:repoId" element={
                <ProtectedRoute><DashboardLayout><ChatPage /></DashboardLayout></ProtectedRoute>
            } />
            <Route path="/interview" element={
                <ProtectedRoute><DashboardLayout><InterviewPage /></DashboardLayout></ProtectedRoute>
            } />
            <Route path="/interview/:repoId" element={
                <ProtectedRoute><DashboardLayout><InterviewPage /></DashboardLayout></ProtectedRoute>
            } />
            <Route path="/debug" element={
                <ProtectedRoute><DashboardLayout><DebugPage /></DashboardLayout></ProtectedRoute>
            } />
            <Route path="/debug/:repoId" element={
                <ProtectedRoute><DashboardLayout><DebugPage /></DashboardLayout></ProtectedRoute>
            } />
            <Route path="/profile" element={
                <ProtectedRoute><DashboardLayout><ProfilePage /></DashboardLayout></ProtectedRoute>
            } />
            {/* Catch-all: logged in → dashboard, not logged in → landing */}
            <Route path="*" element={
                <Navigate to="/dashboard" replace />
            } />
        </Routes>
    )
}

export default function App() {
    return (
        // AuthProvider wraps everything so useAuth() works anywhere in the tree
        <AuthProvider>
            <AppRoutes />
        </AuthProvider>
    )
}
