import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import SourceBadges from './SourceBadges'

export default function MessageBubble({ message }) {
    const isUser = message.role === 'user'

    return (
        <div className={`flex ${isUser ? 'justify-end' : 'justify-start'}`}>
            <div className={`max-w-2xl rounded-lg px-4 py-3 text-sm ${
                isUser
                    ? 'bg-white-600 text-black border border-gray-700'
                    : 'bg-white-100 text-gray-400 border border-gray-700'
            }`}>
                <div className={`
                    prose prose-sm max-w-none
                    prose-invert
                    prose-p:leading-relaxed
                    prose-p:my-2
                    prose-headings:text-white
                    prose-headings:font-semibold
                    prose-headings:mt-4
                    prose-headings:mb-2
                    prose-h1:text-base
                    prose-h2:text-sm
                    prose-h3:text-sm
                    prose-ul:my-2
                    prose-ul:pl-4
                    prose-ol:my-2
                    prose-ol:pl-4
                    prose-li:my-0.5
                    prose-li:leading-relaxed
                    prose-pre:bg-gray-900
                    prose-pre:border
                    prose-pre:border-gray-600
                    prose-pre:rounded-lg
                    prose-pre:p-3
                    prose-pre:my-3
                    prose-pre:overflow-x-auto
                    prose-code:text-yellow-300
                    prose-code:bg-white-500
                    prose-code:px-1.5
                    prose-code:py-0.5
                    prose-code:rounded
                    prose-code:text-xs
                    prose-code:font-mono
                    prose-code:before:content-none
                    prose-code:after:content-none
                    prose-strong:text-white
                    prose-strong:font-semibold
                    prose-blockquote:border-l-blue-500
                    prose-blockquote:text-gray-300
                    prose-blockquote:pl-3
                    prose-blockquote:my-2
                    prose-hr:border-gray-600
                    ${isUser ? 'prose-p:text-white prose-strong:text-white prose-code:text-blue-200' : ''}
                `}>
                    <ReactMarkdown remarkPlugins={[remarkGfm]}>
                        {message.content}
                    </ReactMarkdown>
                </div>

                {!isUser && <SourceBadges sources={message.sources} />}
            </div>
        </div>
    )
}