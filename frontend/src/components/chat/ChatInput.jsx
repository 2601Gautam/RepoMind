import { useState } from 'react'

// Owns input value state locally — ChatPage never manages this
// Emits onSend(text) and clears itself after sending
export default function ChatInput({ onSend, disabled }) {
    const [value, setValue] = useState('')

    function handleSend() {
        const text = value.trim()
        if (!text || disabled) return
        onSend(text)
        setValue('')
    }

    return (
        <div className="flex gap-2 pt-4 border-t border-gray-800 shrink-0">
            <input
                value={value}
                onChange={e => setValue(e.target.value)}
                onKeyDown={e => e.key === 'Enter' && !e.shiftKey && handleSend()}
                placeholder="Ask about the repository..."
                disabled={disabled}
                className="flex-1 bg-gray-900 border border-gray-700 rounded-lg px-4 py-2.5 text-white text-sm placeholder-gray-500 focus:outline-none focus:border-blue-500 disabled:opacity-50 transition-colors"
            />
            <button
                onClick={handleSend}
                disabled={disabled || !value.trim()}
                className="bg-blue-600 hover:bg-blue-500 disabled:opacity-40 disabled:cursor-not-allowed px-5 py-2.5 rounded-lg text-sm font-medium transition-colors"
            >
                Send
            </button>
        </div>
    )
}