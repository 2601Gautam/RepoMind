import { useEffect, useRef, useState, useLayoutEffect } from 'react'
import MessageBubble from './MessageBubble'
import SuggestedQuestions from './SuggestedQuestions'

export default function MessageList({ messages, loading, onSuggest, onDirect }) {
    const containerRef = useRef(null)
    const [isScrolledUp, setIsScrolledUp] = useState(false)
    const userScrolledRef = useRef(false)

    // Handle scroll events to detect if user manually scrolled up
    const handleScroll = () => {
        const el = containerRef.current
        if (!el) return
        const distanceFromBottom = el.scrollHeight - el.scrollTop - el.clientHeight
        const isUp = distanceFromBottom > 90
        setIsScrolledUp(isUp)
        userScrolledRef.current = isUp
    }

    const scrollToBottom = () => {
        userScrolledRef.current = false
        setIsScrolledUp(false)
        if (containerRef.current) {
            containerRef.current.scrollTo({
                top: containerRef.current.scrollHeight,
                behavior: 'smooth'
            })
        }
    }

    const hasInitializedRef = useRef(false)

    // Reset initialization flag when messages are cleared or reset
    useEffect(() => {
        if (messages.length === 0) {
            hasInitializedRef.current = false
        }
    }, [messages.length])

    // When new user message is sent, reset user scroll lock so view follows new answer
    useEffect(() => {
        const lastMsg = messages[messages.length - 1]
        if (lastMsg && lastMsg.role === 'user') {
            userScrolledRef.current = false
            setIsScrolledUp(false)
        }
    }, [messages.length])

    // Synchronous layout scroll — opens directly at bottom on load without scrolling animation
    useLayoutEffect(() => {
        const el = containerRef.current
        if (!el || messages.length === 0) return

        // Override global smooth scroll for instant positioning
        el.style.scrollBehavior = 'auto'

        if (!hasInitializedRef.current) {
            el.scrollTop = el.scrollHeight
            hasInitializedRef.current = true
            return
        }

        if (!userScrolledRef.current) {
            el.scrollTop = el.scrollHeight
        }
    }, [messages])

    return (
        <div className="relative flex-1 min-h-0 flex flex-col">
            <div 
                ref={containerRef} 
                onScroll={handleScroll}
                className="flex-1 overflow-y-auto py-6 space-y-5 pr-1 scroll-smooth-none"
            >
                {messages.length === 0 && !loading && (
                    <SuggestedQuestions onSelect={onSuggest} />
                )}

                {messages.map((msg, i) => (
                    <MessageBubble key={i} message={msg} onDirect={onDirect} />
                ))}
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