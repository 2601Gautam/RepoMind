import { createContext, useContext, useState, useEffect } from 'react'
import { BASE, getMe } from '../api/client'

const AuthContext = createContext(null)

export function AuthProvider({ children }) {
    const [user, setUser] = useState(null)
    const [loading, setLoading] = useState(true)

    useEffect(() => {
        checkAuthStatus()
    }, [])

    async function checkAuthStatus() {
        try {
            const data = await getMe()
            setUser(data)
        } catch {
            setUser(null)
        } finally {
            setLoading(false)
        }
    }

    async function login(email, password) {
        const res = await fetch(`${BASE}/auth/login`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            credentials: 'include',
            body: JSON.stringify({ email, password })
        })
        if (!res.ok) {
            const data = await res.json().catch(() => ({}))
            throw new Error(data.message || data.error || 'Login failed')
        }
        const data = await res.json()
        setUser(data)
        return data
    }

    async function register(name, email, password) {
        const res = await fetch(`${BASE}/auth/register`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            credentials: 'include',
            body: JSON.stringify({ name, email, password })
        })
        if (!res.ok) {
            const data = await res.json().catch(() => ({}))
            throw new Error(data.message || data.error || 'Registration failed')
        }
        const data = await res.json()
        setUser(data)
        return data
    }

    async function logout() {
        await fetch(`${BASE}/auth/logout`, {
            method: 'POST',
            credentials: 'include'
        })
        setUser(null)
    }

    const BACKEND = import.meta.env.VITE_API_URL || ''

    function loginWithGoogle() {
        window.location.href = `${BACKEND}/oauth2/authorization/google`
    }

    function loginWithGitHub() {
        window.location.href = `${BACKEND}/oauth2/authorization/github`
    }

    return (
        <AuthContext.Provider value={{
            user, loading,
            login, register, logout,
            loginWithGoogle, loginWithGitHub,
            checkAuthStatus
        }}>
            {children}
        </AuthContext.Provider>
    )
}

export function useAuth() {
    const context = useContext(AuthContext)
    if (!context) throw new Error('useAuth must be used inside AuthProvider')
    return context
}