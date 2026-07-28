import { useState } from 'react'
import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import rehypeRaw from 'rehype-raw'
import { Prism as SyntaxHighlighter } from 'react-syntax-highlighter'
import { vscDarkPlus } from 'react-syntax-highlighter/dist/esm/styles/prism'
import SourceBadges from './SourceBadges'

// Clean, premium spark icon for the AI
function BotAvatar({ thinking }) {
    return (
        <div className="shrink-0 w-8 h-8 rounded-[10px] bg-gradient-to-b from-white/[0.08] to-transparent border border-white/[0.05] shadow-lg flex items-center justify-center relative overflow-hidden">
            {/* Subtle inner background tint */}
            <div className="absolute inset-0 bg-violet-500/10" />
            {/* Elegant spark/AI icon */}
            <svg 
                viewBox="0 0 24 24" 
                fill="none" 
                stroke="currentColor" 
                strokeWidth="1.5" 
                className={`w-4 h-4 text-violet-400 relative z-10 transition-opacity duration-700 ${thinking ? 'opacity-50 animate-pulse' : 'opacity-100'}`}
            >
                <path strokeLinecap="round" strokeLinejoin="round" d="M9.813 15.904L9 18.75l-.813-2.846a4.5 4.5 0 00-3.09-3.09L2.25 12l2.846-.813a4.5 4.5 0 003.09-3.09L9 5.25l.813 2.846a4.5 4.5 0 003.09 3.09L15.75 12l-2.846.813a4.5 4.5 0 00-3.09 3.09zM18.259 8.715L18 9.75l-.259-1.035a3.375 3.375 0 00-2.455-2.456L14.25 6l1.036-.259a3.375 3.375 0 002.455-2.456L18 2.25l.259 1.035a3.375 3.375 0 002.456 2.456L21.75 6l-1.035.259a3.375 3.375 0 00-2.456 2.456zM16.894 20.567L16.5 21.75l-.394-1.183a2.25 2.25 0 00-1.423-1.423L13.5 18.75l1.183-.394a2.25 2.25 0 001.423-1.423l.394-1.183.394 1.183a2.25 2.25 0 001.423 1.423l1.183.394-1.183.394a2.25 2.25 0 00-1.423 1.423z" />
            </svg>
        </div>
    )
}

// Copy icon
function IconCopy({ copied }) {
    return copied ? (
        <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="w-3.5 h-3.5">
            <path d="M3 8l3 3 7-7" />
        </svg>
    ) : (
        <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" className="w-3.5 h-3.5">
            <rect x="5" y="5" width="8" height="9" rx="1.5" />
            <path d="M11 5V3.5A1.5 1.5 0 009.5 2h-6A1.5 1.5 0 002 3.5v8A1.5 1.5 0 003.5 13H5" />
        </svg>
    )
}

// Direct / send-to-input icon
function IconDirect() {
    return (
        <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" className="w-3.5 h-3.5">
            <path d="M8 2v8M5 7l3 3 3-3" />
            <path d="M3 13h10" />
        </svg>
    )
}

// Shared icon-only action buttons (Copy + Direct)
function ActionButtons({ content, onDirect, hovered, copied, onCopy, alignRight }) {
    return (
        <div className={`flex items-center gap-1 mt-2 transition-all duration-200 ${
            alignRight ? 'justify-end' : 'justify-start'
        } ${
            hovered ? 'opacity-100 translate-y-0' : 'opacity-0 translate-y-1 pointer-events-none'
        }`}>
            <button
                onClick={onCopy}
                title={copied ? 'Copied!' : 'Copy'}
                className={`w-6 h-6 flex items-center justify-center rounded-md border transition-all duration-200 cursor-pointer ${
                    copied
                        ? 'bg-emerald-500/10 border-emerald-500/30 text-emerald-400'
                        : 'bg-white/[0.04] border-white/[0.08] text-neutral-400 hover:bg-white/[0.08] hover:text-white hover:border-white/[0.15]'
                }`}
            >
                <IconCopy copied={copied} />
            </button>
            <button
                onClick={() => onDirect?.(content)}
                title="Send to input"
                className="w-6 h-6 flex items-center justify-center rounded-md border bg-white/[0.04] border-white/[0.08] text-neutral-400 hover:bg-violet-500/10 hover:text-violet-300 hover:border-violet-500/30 transition-all duration-200 cursor-pointer"
            >
                <IconDirect />
            </button>
        </div>
    )
}

// Strip markdown syntax → plain readable text for clipboard
function stripMarkdown(md) {
    return md
        .replace(/```[\w]*\n?/g, '')           // fenced code block delimiters
        .replace(/`([^`]+)`/g, '$1')            // inline code
        .replace(/^#{1,6}\s+/gm, '')            // headings
        .replace(/\*\*([^*]+)\*\*/g, '$1')      // bold **
        .replace(/__([^_]+)__/g, '$1')          // bold __
        .replace(/\*([^*]+)\*/g, '$1')          // italic *
        .replace(/_([^_]+)_/g, '$1')            // italic _
        .replace(/~~([^~]+)~~/g, '$1')          // strikethrough
        .replace(/!\[([^\]]*)\]\([^)]*\)/g, '$1') // images
        .replace(/\[([^\]]+)\]\([^)]*\)/g, '$1')  // links
        .replace(/^>+\s?/gm, '')               // blockquotes
        .replace(/^[-*_]{3,}\s*$/gm, '')        // horizontal rules
        .replace(/^[\s]*[-*+]\s+/gm, '')        // unordered list markers
        .replace(/^[\s]*\d+\.\s+/gm, '')        // ordered list markers
        .replace(/\n{3,}/g, '\n\n')             // collapse excess blank lines
        .trim()
}

export default function MessageBubble({ message, onDirect }) {
    const isUser = message.role === 'user'
    const [copied, setCopied] = useState(false)
    const [hovered, setHovered] = useState(false)

    function handleCopy() {
        if (!message.content) return
        navigator.clipboard.writeText(stripMarkdown(message.content)).then(() => {
            setCopied(true)
            setTimeout(() => setCopied(false), 2000)
        })
    }



    if (isUser) {
        return (
            <div
                className="flex justify-end animate-msg-in"
                onMouseEnter={() => setHovered(true)}
                onMouseLeave={() => setHovered(false)}
            >
                <div className="max-w-[78%]">
                    <div className="bg-[#1a1530] border border-violet-500/20 text-white/95 rounded-2xl rounded-tr-sm px-5 py-3.5 text-[14px] leading-relaxed shadow-sm">
                        {message.content}
                    </div>
                    <ActionButtons
                        content={message.content}
                        onDirect={onDirect}
                        hovered={hovered}
                        copied={copied}
                        onCopy={handleCopy}
                        alignRight
                    />
                </div>
            </div>
        )
    }

    return (
        <div
            className="flex items-start gap-3.5 animate-msg-in group"
            onMouseEnter={() => setHovered(true)}
            onMouseLeave={() => setHovered(false)}
        >
            <BotAvatar thinking={message.streaming && !message.content} />
            <div className="flex-1 min-w-0 max-w-[82%]">
                <div className="bg-white/[0.03] border border-white/[0.06] rounded-2xl rounded-tl-sm px-5 py-4 sm:px-6 sm:py-5 shadow-sm">
                    {message.content ? (
                        <div className="prose prose-invert prose-sm max-w-none
                            prose-p:leading-[1.7] prose-p:my-2.5 prose-p:text-[14px] prose-p:text-neutral-200
                            prose-headings:text-white prose-headings:font-bold prose-headings:tracking-tight
                            prose-h1:mt-7 prose-h1:mb-3.5
                            prose-h2:mt-6 prose-h2:mb-3
                            prose-h3:mt-5 prose-h3:mb-2.5
                            prose-ul:my-3 prose-ul:pl-5 prose-ul:space-y-1.5
                            prose-ol:my-3 prose-ol:pl-5 prose-ol:space-y-1.5
                            prose-li:my-1 prose-li:leading-[1.6] prose-li:text-[14px] prose-li:text-neutral-200 [&_li::marker]:text-white/70
                            prose-td:px-4 prose-td:py-2.5 prose-td:border-b prose-td:border-white/[0.04] prose-td:text-[14.5px] prose-td:text-neutral-200
                            prose-th:px-4 prose-th:py-2.5 prose-th:bg-white/[0.03] prose-th:border-b prose-th:border-white/[0.08] prose-th:text-xs prose-th:font-semibold prose-th:uppercase prose-th:tracking-wider prose-th:text-neutral-300
                            prose-table:my-5 prose-table:w-full prose-table:border-collapse
                            prose-code:before:content-none prose-code:after:content-none prose-code:bg-transparent prose-code:p-0 prose-code:border-none
                            prose-strong:text-white prose-strong:font-bold
                            prose-blockquote:border-l-2 prose-blockquote:border-l-violet-500/70 prose-blockquote:bg-white/[0.02] prose-blockquote:text-neutral-300 prose-blockquote:pl-4 prose-blockquote:py-2 prose-blockquote:my-4 prose-blockquote:rounded-r-lg
                            prose-hr:my-7 prose-hr:border-white/[0.08]
                            prose-a:text-violet-400 hover:prose-a:text-violet-300 prose-a:underline decoration-violet-500/40 font-medium">
                            <ReactMarkdown 
                                remarkPlugins={[remarkGfm]}
                                rehypePlugins={[rehypeRaw]}
                                components={{
                                    h1({ node, children, ...props }) {
                                        return (
                                            <div className="mt-6 mb-3 not-prose">
                                                <span className="text-[15px] font-bold text-white tracking-tight block mb-2">{children}</span>
                                                <div className="h-[1.5px] bg-white/20 rounded-full" />
                                            </div>
                                        )
                                    },
                                    h2({ node, children, ...props }) {
                                        return (
                                            <div className="mt-5 mb-2.5 not-prose">
                                                <span className="text-[13.5px] font-semibold text-white/90 tracking-tight block mb-1.5">{children}</span>
                                                <div className="h-px bg-white/10 rounded-full" />
                                            </div>
                                        )
                                    },
                                    h3({ node, children, ...props }) {
                                        return (
                                            <div className="mt-4 mb-2 not-prose">
                                                <span className="text-[13px] font-semibold text-neutral-300 tracking-tight block mb-1">{children}</span>
                                                <div className="h-px w-16 bg-white/10 rounded-full" />
                                            </div>
                                        )
                                    },
                                    table({ node, children, ...props }) {
                                        return (
                                            <div className="overflow-x-auto w-full my-5 rounded-xl border border-white/[0.08] bg-[#0d0d12] pb-1 scrollbar-thin scrollbar-thumb-white/10">
                                                <table className="w-full text-left border-collapse" {...props}>
                                                    {children}
                                                </table>
                                            </div>
                                        )
                                    },
                                    code({ node, inline, className, children, ...props }) {
                                        const match = /language-(\w+)/.exec(className || '')
                                        return !inline && match ? (
                                            <div className="my-5 rounded-xl overflow-hidden border border-white/[0.08] bg-[#0d0d12]">
                                                <div className="bg-[#14141c] px-4 py-2 text-xs text-neutral-400 font-mono border-b border-white/[0.05] flex items-center justify-between">
                                                    <span className="font-semibold text-violet-400/80 uppercase text-[11px] tracking-wider">{match[1]}</span>
                                                </div>
                                                <SyntaxHighlighter
                                                    {...props}
                                                    style={vscDarkPlus}
                                                    language={match[1]}
                                                    PreTag="div"
                                                    customStyle={{
                                                        margin: 0,
                                                        padding: '1.1rem 1.2rem',
                                                        background: 'transparent',
                                                        fontSize: '14.5px',
                                                        lineHeight: '1.65'
                                                    }}
                                                >
                                                    {String(children).replace(/\n$/, '')}
                                                </SyntaxHighlighter>
                                            </div>
                                        ) : (
                                            <code {...props} className="text-violet-300 font-mono font-medium px-0.5 text-[14px]">
                                                {children}
                                            </code>
                                        )
                                    }
                                }}
                            >
                                {message.content}
                            </ReactMarkdown>
                            {/* Blinking cursor while streaming */}
                            {message.streaming && (
                                <span className="stream-cursor inline-block w-[2px] h-[1em] bg-violet-400/80 ml-0.5 rounded-full align-middle" />
                            )}
                        </div>
                    ) : message.streaming ? (
                        /* Thinking dots — shown before first token arrives */
                        <div className="flex items-center gap-1.5 py-1">
                            <span className="dot-1 w-1.5 h-1.5 rounded-full bg-violet-500/60 inline-block" />
                            <span className="dot-2 w-1.5 h-1.5 rounded-full bg-violet-500/60 inline-block" />
                            <span className="dot-3 w-1.5 h-1.5 rounded-full bg-violet-500/60 inline-block" />
                        </div>
                    ) : null}
                </div>

                {/* Action buttons — Copy & Direct — appear on hover for completed messages */}
                {!message.streaming && message.content && (
                    <ActionButtons
                        content={message.content}
                        onDirect={onDirect}
                        hovered={hovered}
                        copied={copied}
                        onCopy={handleCopy}
                        alignRight={false}
                    />
                )}

                {/* Source files — only on completed assistant messages */}
                {!message.streaming && message.sources?.length > 0 && (
                    <SourceBadges sources={message.sources} />
                )}
            </div>
        </div>
    )
}