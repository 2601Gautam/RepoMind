// Reusable spinner for loading states
// size: 'sm', 'md' (default), 'lg', 'xl' (logo variant)
export default function LoadingSpinner({ size = 'md' }) {
    // For normal sizes (sm, md, lg), render a sleek minimal circular spinner
    if (size === 'sm' || size === 'md' || size === 'lg') {
        const dimensions = 
            size === 'sm' ? 'w-4 h-4 border-2' : 
            size === 'lg' ? 'w-8 h-8 border-2' : 
            'w-5 h-5 border-2';
            
        return (
            <div className="flex items-center justify-center">
                <div className={`${dimensions} border-white/20 border-t-violet-400 rounded-full animate-spin`} />
            </div>
        )
    }

    // For extra-large size (xl), render the animated RepoMind Logo (used for full-page loads)
    const logoSizes = {
        xl: { wrapper: 'w-14 h-14 rounded-2xl', icon: 'w-7 h-7', stroke: 2 }
    }
    const s = logoSizes[size] || logoSizes.xl

    return (
        <div className="relative flex items-center justify-center select-none">
            {/* Subtle background pulsing glow */}
            <div className={`absolute bg-violet-500/40 blur-lg rounded-full animate-pulse ${s.wrapper}`} />
            
            {/* The Logo with a bounce-like scale animation */}
            <div className={`relative ${s.wrapper} bg-gradient-to-br from-violet-500 to-fuchsia-600 flex items-center justify-center shadow-[0_0_15px_rgba(139,92,246,0.3)] animate-[pulse_2s_ease-in-out_infinite]`}>
                <svg viewBox="0 0 24 24" fill="none" stroke="white" strokeWidth={s.stroke} strokeLinecap="round" strokeLinejoin="round" className={s.icon}>
                    <polyline points="16 18 22 12 16 6" />
                    <polyline points="8 6 2 12 8 18" />
                </svg>
            </div>
        </div>
    )
}