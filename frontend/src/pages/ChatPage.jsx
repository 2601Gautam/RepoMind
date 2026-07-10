import { useState, useRef, useEffect } from 'react'
import { useParams, Link } from 'react-router-dom'
import { streamChat, getRepoStatus, RateLimitError } from '../api/client'
import NavBar from '../components/layout/NavBar'
import MessageList from '../components/chat/MessageList'
import ChatInput from '../components/chat/ChatInput'
import RateLimitBanner from '../components/common/RateLimitBanner'
import LoadingSpinner from '../components/common/LoadingSpinner'
import RepoSelector from '../components/repo/RepoSelector'

export default function ChatPage() {
    const { repoId } = useParams()

    // No repo selected — show picker
    if (!repoId) return <RepoSelector tool="chat" />

    const [repo, setRepo] = useState(null)
    const [messages, setMessages] = useState([])
    const [loading, setLoading] = useState(false)
    const [conversationId, setConversationId] = useState(null)
    const [rateLimitSeconds, setRateLimitSeconds] = useState(null)
    // Counter for unique message IDs — needed to update streaming messages in place
    const messageIdRef = useRef(0)

    useEffect(() => {
        getRepoStatus(repoId).then(setRepo).catch(console.error)
    }, [repoId])

    async function handleSend(text) {
        if (loading) return

        // Add user message immediately — don't wait for response
        setMessages(prev => [...prev, { role: 'user', content: text }])
        setLoading(true)

        // Create streaming placeholder with unique ID
        // We need to find and update this specific message as tokens arrive
        const streamId = ++messageIdRef.current
        setMessages(prev => [...prev, {
            id: streamId,
            role: 'assistant',
            content: '',
            sources: [],
            streaming: true
        }])

        let resolvedConversationId = conversationId

        await streamChat(
            repoId,
            text,
            conversationId,

            // onEvent — called for each SSE event from ChatService
            (event) => {
                switch (event.type) {
                    case 'start':
                        // Backend sends conversationId in the start event content field
                        // Store it so subsequent messages maintain conversation context
                        if (event.content && !resolvedConversationId) {
                            resolvedConversationId = event.content
                            setConversationId(event.content)
                        }
                        break

                    case 'token':
                        // Append each token to the streaming message
                        // Using functional update to avoid stale closure over messages
                        setMessages(prev => prev.map(msg =>
                            msg.id === streamId
                                ? { ...msg, content: msg.content + event.content }
                                : msg
                        ))
                        break

                    case 'sources':
                        // Sources arrive as comma-separated string: "file1.java,file2.java"
                        // Split into array for SourceBadges component
                        const sourceList = event.sources
                            ? event.sources.split(',').filter(Boolean)
                            : []
                        setMessages(prev => prev.map(msg =>
                            msg.id === streamId
                                ? { ...msg, sources: sourceList }
                                : msg
                        ))
                        break

                    case 'done':
                        // Remove streaming flag — hides the pulsing cursor
                        setMessages(prev => prev.map(msg =>
                            msg.id === streamId
                                ? { ...msg, streaming: false }
                                : msg
                        ))
                        setLoading(false)
                        break

                    case 'error':
                        setMessages(prev => prev.map(msg =>
                            msg.id === streamId
                                ? { ...msg, content: event.content || 'Something went wrong', streaming: false }
                                : msg
                        ))
                        setLoading(false)
                        break
                }
            },

            // onError — called if the network request itself fails
            (err) => {
                if (err instanceof RateLimitError) {
                    setRateLimitSeconds(err.retryAfterSeconds)
                    // Remove the empty placeholder
                    setMessages(prev => prev.filter(msg => msg.id !== streamId))
                } else {
                    setMessages(prev => prev.map(msg =>
                        msg.id === streamId
                            ? { ...msg, content: `Error: ${err.message}`, streaming: false }
                            : msg
                    ))
                }
                setLoading(false)
            }
        )
    }

    if (!repo) {
        return (
            <div className="min-h-screen bg-gray-950 flex items-center justify-center">
                <LoadingSpinner size="lg" />
            </div>
        )
    }

    return (
        <div className="flex flex-col h-screen bg-gray-950 text-white">
            <NavBar repoName={repo.repoName} />

            {/* Chat header — repo info + navigation to other features */}
            <div className="border-b border-gray-800 shrink-0">
                <div className="max-w-4xl mx-auto px-6 py-3 flex items-center gap-3">
                    <div className="flex-1 min-w-0">
                        <p className="text-sm font-semibold text-white truncate">{repo.repoName}</p>
                        <p className="text-xs text-gray-500">
                            {repo.totalChunks?.toLocaleString()} chunks indexed
                        </p>
                    </div>

                    {/* Quick navigation to Interview and Debug for this repo */}
                    <Link to={`/interview/${repoId}`}
                        className="text-xs bg-gray-800 hover:bg-gray-700 border border-gray-700 text-gray-300 hover:text-white px-3 py-1.5 rounded-lg transition-colors whitespace-nowrap">
                        Interview
                    </Link>
                    <Link to={`/debug/${repoId}`}
                        className="text-xs bg-gray-800 hover:bg-gray-700 border border-gray-700 text-gray-300 hover:text-white px-3 py-1.5 rounded-lg transition-colors">
                        Debug
                    </Link>
                </div>
            </div>

            {/* Message list — flex-1 takes all space between header and input */}
            <div className="flex-1 overflow-hidden max-w-4xl w-full mx-auto px-6">
                <MessageList
                    messages={messages}
                    loading={loading}
                    onSuggest={handleSend}
                />
            </div>

            {/* Rate limit banner + input — always at bottom */}
            <div className="max-w-4xl w-full mx-auto px-6 pb-4 space-y-2 shrink-0">
                {rateLimitSeconds && (
                    <RateLimitBanner
                        seconds={rateLimitSeconds}
                        onDismiss={() => setRateLimitSeconds(null)}
                    />
                )}
                <ChatInput
                    onSend={handleSend}
                    disabled={loading || !!rateLimitSeconds}
                />
            </div>
        </div>
    )
}