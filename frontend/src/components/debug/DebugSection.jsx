import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import { Prism as SyntaxHighlighter } from 'react-syntax-highlighter'
import { vscDarkPlus } from 'react-syntax-highlighter/dist/esm/styles/prism'

export const SECTION_META = {
    rootCause: {
        title: 'Root Cause',
        border: 'border-white/[0.06]',
        text: 'text-neutral-400'
    },
    explanation: {
        title: 'Explanation',
        border: 'border-white/[0.06]',
        text: 'text-neutral-400'
    },
    suggestedFix: {
        title: 'Suggested Fix',
        border: 'border-white/[0.06]',
        text: 'text-neutral-400'
    },
    prevention: {
        title: 'Prevention',
        border: 'border-white/[0.06]',
        text: 'text-neutral-400'
    }
}

// Individual result section card — Chat style professional card
// variant: one of 'rootCause' | 'explanation' | 'suggestedFix' | 'prevention'
// streaming: shows a pulsing cursor while this section is actively receiving tokens
// onCopy: optional — renders a "Copy" button in the header when provided (hidden while streaming)
export default function DebugSection({ variant, children, onCopy, streaming }) {
    const meta = SECTION_META[variant]
    return (
        <div className={`relative bg-[#0d0d12] border ${meta.border} rounded-2xl px-5 py-4`}>
            <div className="flex items-center justify-between mb-3 border-b border-white/[0.04] pb-3">
                <div className="flex items-center gap-2">
                    <p className={`text-[11px] font-bold uppercase tracking-wider ${meta.text}`}>
                        {meta.title}
                    </p>
                </div>
                {onCopy && !streaming && (
                    <button
                        onClick={onCopy}
                        className="cursor-pointer text-[11px] font-medium text-neutral-500 hover:text-white border border-white/[0.06] hover:border-white/[0.12] hover:bg-white/[0.03] px-2.5 py-1 rounded-lg transition-all"
                    >
                        Copy
                    </button>
                )}
            </div>
            <div className="prose prose-invert prose-sm max-w-none prose-p:leading-relaxed prose-p:my-2 prose-p:text-[13.5px] prose-p:text-neutral-300 prose-headings:text-white prose-headings:font-semibold prose-ul:my-2 prose-ul:pl-4 prose-ul:text-neutral-300 prose-ol:my-2 prose-ol:pl-4 prose-ol:text-neutral-300 prose-li:my-0.5 prose-li:leading-relaxed prose-li:text-[13.5px] prose-code:before:content-none prose-code:after:content-none prose-strong:text-white prose-strong:font-semibold">
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
                    {children}
                </ReactMarkdown>
                {/* Pulsing cursor shown while this section is actively streaming */}
                {streaming && (
                    <span className="stream-cursor inline-block w-[2px] h-[1em] bg-violet-400/70 ml-0.5 rounded-full align-middle animate-pulse" />
                )}
            </div>
        </div>
    )
}
