import { useEffect, useRef, useState } from 'react'
import MessageBubble from './MessageBubble'
import SuggestedQuestions from './SuggestedQuestions'

export default function MessageList({ messages, loading, onSuggest }) {
    const bottomRef = useRef(null)
    const containerRef = useRef(null)
    const [isScrolledUp, setIsScrolledUp] = useState(false)

    const handleScroll = () => {
        const el = containerRef.current
        if (!el) return
        const scrolledUp = el.scrollHeight - el.scrollTop - el.clientHeight > 150
        setIsScrolledUp(scrolledUp)
    }

    const scrollToBottom = () => {
        bottomRef.current?.scrollIntoView({ behavior: 'smooth' })
    }

    useEffect(() => {
        const el = containerRef.current
        if (!el) return

        if (!isScrolledUp) {
            // Use 'auto' (instant) during streaming so the browser doesn't stutter 
            // trying to calculate overlapping smooth-scroll animations 50 times a second.
            bottomRef.current?.scrollIntoView({ behavior: loading ? 'auto' : 'smooth' })
        }
    }, [messages, loading, isScrolledUp])

    return (
        <div className="relative flex-1 min-h-0 flex flex-col">
            <div 
                ref={containerRef} 
                onScroll={handleScroll}
                className="flex-1 overflow-y-auto py-6 space-y-5 pr-1"
            >
                {messages.length === 0 && !loading && (
                    <SuggestedQuestions onSelect={onSuggest} />
                )}

                {messages.map((msg, i) => (
                    <MessageBubble key={i} message={msg} />
                ))}

                <div ref={bottomRef} className="h-2 shrink-0" />
            </div>

            {/* Go Down Button */}
            <div className={`absolute bottom-6 left-1/2 -translate-x-1/2 transition-all duration-300 pointer-events-none ${isScrolledUp ? 'opacity-100 translate-y-0' : 'opacity-0 translate-y-4'}`}>
                <button 
                    onClick={scrollToBottom}
                    className="cursor-pointer pointer-events-auto w-9 h-9 flex items-center justify-center bg-[#1a1530]/90 hover:bg-[#1a1530] border border-violet-500/30 shadow-[0_8px_32px_rgba(139,92,246,0.15)] backdrop-blur-md text-white/90 rounded-full transition-all duration-200 hover:scale-110 active:scale-95"
                    title="Go to bottom"
                >
                    <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" className="w-4 h-4 translate-y-[1px]">
                        <path d="M4 6l4 4 4-4" />
                    </svg>
                </button>
            </div>
        </div>
    )
}