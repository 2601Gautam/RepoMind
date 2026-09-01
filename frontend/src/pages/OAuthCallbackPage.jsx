import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import LoadingSpinner from '../components/common/LoadingSpinner'

export default function OAuthCallbackPage() {
    const { checkAuthStatus } = useAuth()
    const navigate = useNavigate()
    const [failed, setFailed] = useState(false)

     useEffect(() => {
        const params = new URLSearchParams(window.location.search)

        // Spring redirects here with ?error= on OAuth failure
        if (params.get('error')) {
            setFailed(true)
            setTimeout(() => navigate('/login?error=oauth_failed'), 2000)
            return
        }

        // Cookie already set by Spring's OAuth2SuccessHandler
        // Just need to fetch /auth/me to load user into React state
        checkAuthStatus()
            .then(() => navigate('/dashboard'))
            .catch(() => {
                setFailed(true)
                setTimeout(() => navigate('/login'), 2000)
            })
    }, [])

    if (failed) {
        return (
            <div className="min-h-screen bg-gray-950 flex items-center justify-center">
                <div className="text-center space-y-2">
                    <p className="text-red-400">Authentication failed</p>
                    <p className="text-gray-500 text-sm">Redirecting to login...</p>
                </div>
            </div>
        )
    }

    return (
        <div className="min-h-screen bg-gray-950 flex items-center justify-center">
            <div className="text-center space-y-4">
                <LoadingSpinner size="lg" />
                <p className="text-gray-400 text-sm">Completing sign in...</p>
            </div>
        </div>
    )
}