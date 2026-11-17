import { Navigate } from 'react-router-dom'
import { useAuth } from '../../context/AuthContext'
import LoadingSpinner from './LoadingSpinner'

// Wraps any page that requires authentication
// Shows spinner while auth status is being checked on page load
// Redirects to /login if user is not authenticated
// replace={true} means the login page replaces this in browser history
// so pressing Back after login doesn't loop back to login page
export default function ProtectedRoute({ children }) {
    const { user, loading, authError, checkAuthStatus } = useAuth()

    if (loading) {
        return (
            <div className="min-h-screen bg-[#0a0a0a] flex items-center justify-center">
                <LoadingSpinner size="xl" />
            </div>
        )
    }

    if (!user) {
        if (authError) return <div role="alert" className="min-h-screen bg-[#0a0a0a] text-white p-10">
            <p>{authError}</p><button className="underline mt-4" onClick={checkAuthStatus}>Check connection</button>
        </div>
        return <Navigate to="/login" replace />
    }

    return children
}
