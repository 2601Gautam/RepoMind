import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
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
                className={`w-4 h-4 text-violet-300 relative z-10 transition-opacity duration-700 ${thinking ? 'opacity-50 animate-pulse' : 'opacity-100'}`}
            >
                <path strokeLinecap="round" strokeLinejoin="round" d="M9.813 15.904L9 18.75l-.813-2.846a4.5 4.5 0 00-3.09-3.09L2.25 12l2.846-.813a4.5 4.5 0 003.09-3.09L9 5.25l.813 2.846a4.5 4.5 0 003.09 3.09L15.75 12l-2.846.813a4.5 4.5 0 00-3.09 3.09zM18.259 8.715L18 9.75l-.259-1.035a3.375 3.375 0 00-2.455-2.456L14.25 6l1.036-.259a3.375 3.375 0 002.455-2.456L18 2.25l.259 1.035a3.375 3.375 0 002.456 2.456L21.75 6l-1.035.259a3.375 3.375 0 00-2.456 2.456zM16.894 20.567L16.5 21.75l-.394-1.183a2.25 2.25 0 00-1.423-1.423L13.5 18.75l1.183-.394a2.25 2.25 0 001.423-1.423l.394-1.183.394 1.183a2.25 2.25 0 001.423 1.423l1.183.394-1.183.394a2.25 2.25 0 00-1.423 1.423z" />
            </svg>
        </div>
    )
}

export default function MessageBubble({ message }) {
    const isUser = message.role === 'user'

    if (isUser) {
        return (
            <div className="flex justify-end animate-msg-in">
                <div className="max-w-[72%]">
                    <div className="bg-[#1a1530] border border-violet-500/20 text-white/90 rounded-2xl rounded-tr-sm px-4 py-3 text-[13.5px] leading-relaxed">
                        {message.content}
                    </div>
                </div>
            </div>
        )
    }

    return (
        <div className="flex items-start gap-3 animate-msg-in">
            <BotAvatar thinking={message.streaming && !message.content} />
            <div className="flex-1 min-w-0 max-w-[88%]">
                <div className="bg-[#0d0d12] border border-white/[0.06] rounded-2xl rounded-tl-sm px-4 py-3.5">
                    {message.content ? (
                        <div className="prose prose-invert prose-sm max-w-none
                            prose-p:leading-relaxed prose-p:my-2 prose-p:text-[13.5px] prose-p:text-neutral-300
                            prose-headings:text-white prose-headings:font-semibold
                            prose-headings:mt-4 prose-headings:mb-2
                            prose-h1:text-base prose-h2:text-sm prose-h3:text-sm
                            prose-ul:my-2 prose-ul:pl-4 prose-ul:text-neutral-300
                            prose-ol:my-2 prose-ol:pl-4 prose-ol:text-neutral-300
                            prose-li:my-0.5 prose-li:leading-relaxed prose-li:text-[13.5px]
                            prose-code:before:content-none prose-code:after:content-none
                            prose-strong:text-white prose-strong:font-semibold
                            prose-blockquote:border-l-violet-500/50 prose-blockquote:text-neutral-400
                            prose-blockquote:pl-3 prose-blockquote:my-2
                            prose-hr:border-white/[0.05]">
                            <ReactMarkdown 
                                remarkPlugins={[remarkGfm]}
                                components={{
                                    code({ node, inline, className, children, ...props }) {
                                        const match = /language-(\w+)/.exec(className || '')
                                        return !inline && match ? (
                                            <div className="my-4 rounded-xl overflow-hidden border border-white/[0.06] bg-[#0a0a0f]">
                                                <div className="bg-[#111118] px-4 py-1.5 text-xs text-neutral-400 font-mono border-b border-white/[0.04] flex items-center">
                                                    {match[1]}
                                                </div>
                                                <SyntaxHighlighter
                                                    {...props}
                                                    style={vscDarkPlus}
                                                    language={match[1]}
                                                    PreTag="div"
                                                    customStyle={{
                                                        margin: 0,
                                                        padding: '1rem',
                                                        background: 'transparent',
                                                        fontSize: '12.5px',
                                                        lineHeight: '1.6'
                                                    }}
                                                >
                                                    {String(children).replace(/\n$/, '')}
                                                </SyntaxHighlighter>
                                            </div>
                                        ) : (
                                            <code {...props} className="text-violet-300 font-mono font-semibold">
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
                                <span className="stream-cursor inline-block w-[2px] h-[1em] bg-violet-400/70 ml-0.5 rounded-full align-middle" />
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

                {/* Source files — only on completed assistant messages */}
                {!message.streaming && message.sources?.length > 0 && (
                    <SourceBadges sources={message.sources} />
                )}
            </div>
        </div>
    )
}