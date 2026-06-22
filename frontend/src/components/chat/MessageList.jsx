import { useEffect, useRef } from 'react'
import MessageBubble from './MessageBubble'
import SuggestedQuestions from './SuggestedQuestions'

// Owns scroll behavior and empty state
// ChatPage never deals with refs or scroll logic — that lives here
export default function MessageList({ messages, loading, onSuggest }) {
    const bottomRef = useRef(null)

    useEffect(() => {
        bottomRef.current?.scrollIntoView({ behavior: 'smooth' })
    }, [messages, loading])

    return (
        <div className="flex-1 overflow-y-auto py-4 space-y-4 pr-1">
            {messages.length === 0 && !loading && (
                <SuggestedQuestions onSelect={onSuggest} />
            )}

            {messages.map((msg, i) => (
                <MessageBubble key={i} message={msg} />
            ))}

            {loading && (
                <div className="flex justify-start">
                    <div className="bg-gray-800 border border-gray-700 rounded-lg px-4 py-3 text-sm text-gray-400">
                        <span className="animate-pulse">Thinking...</span>
                    </div>
                </div>
            )}

            <div ref={bottomRef} />
        </div>
    )
}