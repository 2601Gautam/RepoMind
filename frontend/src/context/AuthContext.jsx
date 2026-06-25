import { createContext, useContext, useState, useEffect } from 'react'

// AuthContext answers the question throughout the entire app:
// "Is there a logged-in user right now, and who are they?"
//
// Using Context instead of prop-drilling means:
// Any component anywhere in the tree can call useAuth()
// and get the current user — no need to pass user as a prop
// through every component level
const AuthContext = createContext(null)

export function AuthProvider({ children }) {
    const [user, setUser] = useState(null)       // null = not logged in
    const [loading, setLoading] = useState(true) // true = still checking auth status

    // On app load, check if user is already logged in
    // The /api/auth/me endpoint reads the httpOnly cookie and returns user info
    // This is how we restore the session after a page refresh
    useEffect(() => {
        checkAuthStatus()
    }, [])

    const checkAuthStatus = async () => {
        try {
            const user = await getMe() // should use BASE from client.js
            setUser(user)
        } catch {
            setUser(null)
        } finally {
            setLoading(false)
        }
    }

    async function login(email, password) {
        const res = await fetch('/api/auth/login', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            credentials: 'include',   // tells browser to accept and store the cookie
            body: JSON.stringify({ email, password })
        })
        if (!res.ok) {
            const data = await res.json()
            throw new Error(data.error || 'Login failed')
        }
        const data = await res.json()
        setUser(data)
        return data
    }

    async function register(name, email, password) {
        const res = await fetch('/api/auth/register', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            credentials: 'include',
            body: JSON.stringify({ name, email, password })
        })
        if (!res.ok) {
            const data = await res.json()
            throw new Error(data.error || 'Registration failed')
        }
        const data = await res.json()
        setUser(data)
        return data
    }

    async function logout() {
        await fetch('/api/auth/logout', {
            method: 'POST',
            credentials: 'include'
        })
        setUser(null)
    }

    // loginWithGoogle redirects the browser to Spring's OAuth2 endpoint
    // Spring handles the Google redirect, callback, and cookie setting
    // After success, Google redirects to /auth/callback which calls checkAuthStatus
    // In development: backend is on 8080
    // In production: backend is on Render
    const BACKEND_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080'

    // Replace both functions:
    function loginWithGoogle() {
        // Must navigate directly to backend — cannot go through Vite proxy
        // OAuth2 flow requires real browser redirect, not a proxied fetch call
        window.location.href = `${BACKEND_URL}/oauth2/authorization/google`
    }

    function loginWithGitHub() {
        window.location.href = `${BACKEND_URL}/oauth2/authorization/github`
    }


    return (
        <AuthContext.Provider value={{
            user,
            loading,
            login,
            register,
            logout,
            loginWithGoogle,
            loginWithGitHub,
            checkAuthStatus
        }}>
            {children}
        </AuthContext.Provider>
    )
}

// Custom hook — any component calls useAuth() to access auth state
// Throws if used outside AuthProvider — catches setup mistakes early
export function useAuth() {
    const context = useContext(AuthContext)
    if (!context) throw new Error('useAuth must be used inside AuthProvider')
    return context
}