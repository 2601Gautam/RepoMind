export default function OAuthCallbackPage() {
    const { checkAuthStatus } = useAuth()

    useEffect(() => {
        checkAuthStatus().then(() => {
            // Clean URL and go to home
            window.location.replace('/')
        })
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