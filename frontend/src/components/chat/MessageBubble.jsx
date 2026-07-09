import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import SourceBadges from './SourceBadges'

export default function MessageBubble({ message }) {
    const isUser = message.role === 'user'

    return (
        <div className={`flex ${isUser ? 'justify-end' : 'justify-start'}`}>
            <div className={`max-w-2xl rounded-lg px-4 py-3 text-sm ${
                isUser
                    ? 'bg-blue-600 text-white'
                    : 'bg-gray-800 text-gray-100 border border-gray-700'
            }`}>
                <div className="prose prose-invert prose-sm max-w-none
                    prose-p:leading-relaxed prose-p:my-2
                    prose-headings:text-white prose-headings:font-semibold
                    prose-headings:mt-4 prose-headings:mb-2
                    prose-h1:text-base prose-h2:text-sm prose-h3:text-sm
                    prose-ul:my-2 prose-ul:pl-4
                    prose-ol:my-2 prose-ol:pl-4
                    prose-li:my-0.5 prose-li:leading-relaxed
                    prose-pre:bg-gray-900 prose-pre:border prose-pre:border-gray-600
                    prose-pre:rounded-lg prose-pre:p-3 prose-pre:my-3 prose-pre:overflow-x-auto
                    prose-code:text-blue-300 prose-code:bg-gray-900
                    prose-code:px-1.5 prose-code:py-0.5 prose-code:rounded
                    prose-code:text-xs prose-code:font-mono
                    prose-code:before:content-none prose-code:after:content-none
                    prose-strong:text-white prose-strong:font-semibold
                    prose-blockquote:border-l-blue-500 prose-blockquote:text-gray-300
                    prose-blockquote:pl-3 prose-blockquote:my-2
                    prose-hr:border-gray-600">
                    <ReactMarkdown remarkPlugins={[remarkGfm]}>
                        {message.content}
                    </ReactMarkdown>
                    {/* Pulsing cursor shown while this message is streaming */}
                    {message.streaming && (
                        <span className="inline-block w-2 h-4 bg-blue-400 animate-pulse ml-0.5 rounded-sm" />
                    )}
                </div>

                {/* Source files — only on completed assistant messages */}
                {!isUser && !message.streaming && (
                    <SourceBadges sources={message.sources} />
                )}
            </div>
        </div>
    )
}