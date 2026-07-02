import { useState } from 'react'
import { sendMessage } from '../api/client'
import MessageList from '../components/chat/MessageList'
import ChatInput from '../components/chat/ChatInput'
import InterviewPage from './InterviewPage'

import DebugPage from './DebugPage'
// ChatPage owns message state and API logic only
// No JSX for individual messages, no scroll refs, no input state
export default function ChatPage({ repo, onBack }) {
    const [messages, setMessages] = useState([])
    const [loading, setLoading] = useState(false)
    const [conversationId, setConversationId] = useState(null)
    const [showInterview, setShowInterview] = useState(false)

    // Conditionally render:
    if (showInterview) {
        return <InterviewPage repo={repo} onBack={() => setShowInterview(false)} />
    }
    const [showDebug, setShowDebug] = useState(false)

    if(showDebug) {
        return <DebugPage repo={repo} onBack={() => setShowDebug(false)} />
    }
    async function handleSend(text) {
        setMessages(prev => [...prev, { role: 'user', content: text }])
        setLoading(true)
        try {
            const res = await sendMessage(repo.id, text, conversationId)
            if (!conversationId) setConversationId(res.conversationId)
            setMessages(prev => [...prev, {
                role: 'assistant',
                content: res.answer,
                sources: res.sources
            }])
        } catch (e) {
            setMessages(prev => [...prev, {
                role: 'assistant',
                content: `Something went wrong: ${e.message}`
            }])
        } finally {
            setLoading(false)
        }
    }

    return (
        <div className="flex flex-col h-[calc(100vh-65px)]">
            <div className="flex items-center gap-3 py-4 border-b border-gray-800 shrink-0">
                <button
                    onClick={() => setShowDebug(true)}
                    className="text-xs bg-gray-800 hover:bg-gray-700 border border-gray-700 text-gray-300 hover:text-white px-3 py-1.5 rounded-lg transition-colors"
                >
                    Debug
                </button>
                <button
                    onClick={onBack}
                    className="text-sm text-gray-400 hover:text-white transition-colors"
                >
                    ← Back
                </button>
                <button
                    onClick={() => setShowInterview(true)}
                    className="text-xs bg-gray-800 hover:bg-gray-700 border border-gray-700 text-gray-300 hover:text-white px-3 py-1.5 rounded-lg transition-colors ml-auto"
                >
                    Interview Prep
                </button>
                <div className="flex-1 min-w-0">
                    <p className="text-sm font-semibold text-white truncate">
                        {repo.repoName}
                    </p>
                    <p className="text-xs text-gray-500">
                        {repo.totalChunks?.toLocaleString()} chunks indexed
                    </p>
                </div>
                <span className="text-xs text-green-400 bg-green-400/10 border border-green-400/20 px-2 py-0.5 rounded-full shrink-0">
                    READY
                </span>
            </div>

            <MessageList
                messages={messages}
                loading={loading}
                onSuggest={handleSend}
            />

            <ChatInput onSend={handleSend} disabled={loading} />
        </div>
    )
}