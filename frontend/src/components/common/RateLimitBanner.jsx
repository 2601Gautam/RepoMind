import { useState, useEffect } from 'react'

// Shows a countdown banner when user hits a rate limit
// Auto-dismisses when countdown reaches zero
// onDismiss called after countdown so parent can re-enable inputs
export default function RateLimitBanner({ seconds, onDismiss }) {
    const [remaining, setRemaining] = useState(seconds)

    useEffect(() => {
        if (remaining <= 0) {
            onDismiss()
            return
        }
        const timer = setTimeout(() => setRemaining(r => r - 1), 1000)
        return () => clearTimeout(timer)
    }, [remaining, onDismiss])

    return (
        <div className="bg-yellow-900/30 border border-yellow-700/50 rounded-lg px-4 py-2.5 text-sm text-yellow-400 text-center">
            Rate limit reached. You can try again in{' '}
            <span className="font-bold">{remaining}s</span>
        </div>
    )
}