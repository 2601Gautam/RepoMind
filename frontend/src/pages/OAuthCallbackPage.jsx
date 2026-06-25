import { useEffect } from 'react'
import { useAuth } from '../context/AuthContext'

// This page handles the redirect after Google OAuth2 login
// Google redirects to /auth/callback after successful authentication
// Spring has already set the httpOnly cookie by this point
// This page just calls checkAuthStatus() to load the user into React state
export default function OAuthCallbackPage() {
    const { checkAuthStatus } = useAuth()

    useEffect(() => {
        // Cookie is already set by Spring's OAuth2SuccessHandler
        // We just need to fetch /api/auth/me to get the user info
        checkAuthStatus()
    }, [])

    return (
        <div className="min-h-screen bg-gray-950 flex items-center justify-center">
            <div className="text-center space-y-3">
                <div className="w-8 h-8 border-2 border-blue-500 border-t-transparent rounded-full animate-spin mx-auto" />
                <p className="text-gray-400 text-sm">Completing sign in...</p>
            </div>
        </div>
    )
}