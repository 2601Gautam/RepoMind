const QUESTIONS = [
    {
        label: 'Architecture',
        text: 'How is this codebase structured and what are the main modules?',
        icon: (
            <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" className="w-3.5 h-3.5">
                <rect x="1" y="1" width="5" height="5" rx="1"/><rect x="10" y="1" width="5" height="5" rx="1"/>
                <rect x="1" y="10" width="5" height="5" rx="1"/><rect x="10" y="10" width="5" height="5" rx="1"/>
                <path d="M6 3.5h4M3.5 6v4M12.5 6v4M6 12.5h4"/>
            </svg>
        )
    },
    {
        label: 'Authentication',
        text: 'How does authentication and authorization work?',
        icon: (
            <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" className="w-3.5 h-3.5">
                <rect x="3" y="7" width="10" height="8" rx="1.5"/><path d="M5 7V5a3 3 0 016 0v2"/>
                <circle cx="8" cy="11" r="1" fill="currentColor" stroke="none"/>
            </svg>
        )
    },
    {
        label: 'API Endpoints',
        text: 'What are the main API endpoints and their responsibilities?',
        icon: (
            <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" className="w-3.5 h-3.5">
                <path d="M2 8h12M10 4l4 4-4 4"/><path d="M6 4L2 8l4 4" opacity=".4"/>
            </svg>
        )
    },
    {
        label: 'Database',
        text: 'How is the database modeled and what are the main entities?',
        icon: (
            <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" className="w-3.5 h-3.5">
                <ellipse cx="8" cy="4" rx="6" ry="2"/><path d="M2 4v4c0 1.1 2.686 2 6 2s6-.9 6-2V4"/>
                <path d="M2 8v4c0 1.1 2.686 2 6 2s6-.9 6-2V8"/>
            </svg>
        )
    },
    {
        label: 'Core Service',
        text: 'What does the main service layer do and how is business logic organised?',
        icon: (
            <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" className="w-3.5 h-3.5">
                <circle cx="8" cy="8" r="2.5"/><path d="M8 1v2M8 13v2M1 8h2M13 8h2M3.05 3.05l1.42 1.42M11.53 11.53l1.42 1.42M3.05 12.95l1.42-1.42M11.53 4.47l1.42-1.42"/>
            </svg>
        )
    },
    {
        label: 'Dependencies',
        text: 'What are the key dependencies and why are they used?',
        icon: (
            <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" className="w-3.5 h-3.5">
                <path d="M4 4h8v8H4z" rx="1"/><path d="M4 8h8M8 4v8" opacity=".5"/>
            </svg>
        )
    },
]

export default function SuggestedQuestions({ onSelect }) {
    return (
        <div className="flex flex-col items-center justify-center min-h-[55vh] gap-10 text-center px-4 animate-fade-up">
            {/* Wordmark / hero */}
            <div className="space-y-3">
                <div className="flex items-center justify-center gap-2.5 mb-4">
                    <div className="w-10 h-10 rounded-xl bg-gradient-to-br from-violet-600 to-violet-800 flex items-center justify-center shadow-lg shadow-violet-900/40">
                        <svg viewBox="0 0 20 20" fill="none" className="w-5 h-5 text-white">
                            <path d="M3 5a2 2 0 012-2h10a2 2 0 012 2v8a2 2 0 01-2 2H5a2 2 0 01-2-2V5z" fill="currentColor" opacity=".2"/>
                            <path d="M6 8h8M6 11h5" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round"/>
                        </svg>
                    </div>
                </div>
                <h2 className="text-[22px] font-bold text-white tracking-tight leading-tight">
                    What would you like to know?
                </h2>
                <p className="text-[13px] text-neutral-500 max-w-[340px] leading-relaxed mx-auto">
                    Every file in this repository has been indexed. Ask about architecture, logic, APIs, or any implementation detail.
                </p>
            </div>

            {/* Suggestion grid */}
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-2 max-w-[520px] w-full">
                {QUESTIONS.map((q, i) => (
                    <button
                        key={q.label}
                        onClick={() => onSelect(q.text)}
                        className="cursor-pointer group flex items-start gap-3 text-left bg-white/[0.02] border border-white/[0.06] hover:border-violet-500/30 hover:bg-violet-500/[0.04] rounded-xl px-4 py-3.5 transition-all duration-200"
                        style={{ animationDelay: `${i * 0.05}s` }}
                    >
                        <span className="mt-0.5 shrink-0 text-neutral-500 group-hover:text-violet-400 transition-colors duration-200">
                            {q.icon}
                        </span>
                        <span className="flex-1 min-w-0">
                            <span className="block text-[11px] font-semibold uppercase tracking-wider text-neutral-600 group-hover:text-violet-400 mb-0.5 transition-colors duration-200">
                                {q.label}
                            </span>
                            <span className="block text-[12.5px] text-neutral-400 group-hover:text-neutral-200 leading-snug transition-colors duration-200">
                                {q.text}
                            </span>
                        </span>
                    </button>
                ))}
            </div>
        </div>
    )
}