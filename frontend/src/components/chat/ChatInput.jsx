import { useState, useRef, useEffect } from 'react'

export default function ChatInput({ onSend, disabled }) {
    const [value, setValue] = useState('')
    const textareaRef = useRef(null)
    const [focused, setFocused] = useState(false)

    // Auto-resize textarea height
    useEffect(() => {
        const el = textareaRef.current
        if (!el) return
        el.style.height = 'auto'
        el.style.height = Math.min(el.scrollHeight, 200) + 'px'
    }, [value])

    function handleSend() {
        const text = value.trim()
        if (!text || disabled) return
        onSend(text)
        setValue('')
        if (textareaRef.current) textareaRef.current.style.height = 'auto'
    }

    function handleKey(e) {
        if (e.key === 'Enter' && !e.shiftKey) {
            e.preventDefault()
            handleSend()
        }
    }

    const hasText = value.trim().length > 0

    return (
        <div className="relative w-full">
            <div className={`relative flex items-end gap-3 rounded-[26px] pl-5 pr-2.5 py-2.5 transition-all duration-300 ${
                disabled
                    ? 'bg-[#15151a] border border-white/[0.04]'
                    : focused
                        ? 'bg-[#1a1a24] border border-violet-500/40 shadow-[0_8px_32px_rgba(139,92,246,0.08)]'
                        : 'bg-[#15151a] border border-white/[0.1] hover:border-white/[0.15] shadow-xl'
            }`}>
                {/* Textarea */}
                <textarea
                    ref={textareaRef}
                    rows={1}
                    value={value}
                    onChange={e => setValue(e.target.value)}
                    onKeyDown={handleKey}
                    onFocus={() => setFocused(true)}
                    onBlur={() => setFocused(false)}
                    placeholder={disabled ? 'Thinking…' : 'Message RepoMind...'}
                    disabled={disabled}
                    className="flex-1 bg-transparent resize-none text-[15px] text-white/95 placeholder-neutral-500 focus:outline-none disabled:opacity-50 leading-[1.6] py-1.5 max-h-[40vh] overflow-y-auto font-sans"
                    style={{ scrollbarWidth: 'none' }}
                />

                {/* Send button */}
                <button
                    onClick={handleSend}
                    disabled={disabled || !hasText}
                    className={`shrink-0 w-9 h-9 rounded-full flex items-center justify-center transition-all duration-300 ${
                        hasText && !disabled
                            ? 'bg-white text-black hover:scale-105 active:scale-95 shadow-md'
                            : 'bg-white/[0.06] text-neutral-600 cursor-not-allowed'
                    }`}
                >
                    {disabled ? (
                        <svg className="w-4 h-4 animate-spin text-white/50" viewBox="0 0 24 24" fill="none">
                            <circle cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="2.5" className="opacity-20" />
                            <path d="M12 2C17.5228 2 22 6.47715 22 12" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" />
                        </svg>
                    ) : (
                        <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" className={`w-4 h-4 ${hasText && !disabled ? 'translate-x-[1px]' : ''}`}>
                            <path d="M8 13V3M3 8l5-5 5 5" />
                        </svg>
                    )}
                </button>
            </div>
        </div>
    )
}