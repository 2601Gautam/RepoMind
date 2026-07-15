import { useState, useRef, useEffect } from 'react'
import { useParams, Link, useNavigate } from 'react-router-dom'
import { streamChat, getRepoStatus, getChatHistory, clearChatConversation, RateLimitError } from '../api/client'
import NavBar from '../components/layout/NavBar'
import MessageList from '../components/chat/MessageList'
import ChatInput from '../components/chat/ChatInput'
import RateLimitBanner from '../components/common/RateLimitBanner'
import LoadingSpinner from '../components/common/LoadingSpinner'
import RepoSelector from '../components/repo/RepoSelector'

// Inner component — all hooks run unconditionally here
function ChatPageInner({ repoId }) {
    const navigate = useNavigate()
    const [repo, setRepo] = useState(null)
    const [messages, setMessages] = useState([])
    const [conversationId, setConversationId] = useState(null)
    const [loading, setLoading] = useState(false)
    const [historyLoading, setHistoryLoading] = useState(true)  // true until DB history is fetched
    const [rateLimitSeconds, setRateLimitSeconds] = useState(null)
    const messageIdRef = useRef(0)

    // Load repo info
    useEffect(() => {
        getRepoStatus(repoId).then(setRepo).catch(console.error)
        // Track this repo as recently chatted
        if (repoId) {
            try {
                const recent = JSON.parse(localStorage.getItem('recent_chat_repos') || '[]')
                const updated = [repoId, ...recent.filter(id => id !== repoId)].slice(0, 5)
                localStorage.setItem('recent_chat_repos', JSON.stringify(updated))
            } catch (e) {
                console.error('Failed to update recent chat repos:', e)
            }
        }
    }, [repoId])

    // On mount: fetch chat history from the database (per user + per repo)
    // This replaces localStorage — history survives across devices and sessions
    useEffect(() => {
        setHistoryLoading(true)
        setMessages([])
        setConversationId(null)

        getChatHistory(repoId)
            .then(data => {
                if (data && data.messages?.length > 0) {
                    // Map DB messages to the shape the MessageList component expects
                    const restored = data.messages.map(m => ({
                        role: m.role,
                        content: m.content,
                        // sources is a comma-separated string from DB — convert to array
                        sources: m.sources ? m.sources.split(',').filter(Boolean) : []
                    }))
                    setMessages(restored)
                    setConversationId(data.conversationId)
                }
            })
            .catch(err => console.error('Failed to load chat history:', err))
            .finally(() => setHistoryLoading(false))
    }, [repoId])

    async function handleSend(text) {
        if (loading) return

        setMessages(prev => [...prev, { role: 'user', content: text }])
        setLoading(true)

        const streamId = ++messageIdRef.current
        // Add placeholder with thinking dots (empty content + streaming flag)
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
            (event) => {
                switch (event.type) {
                    case 'start':
                        if (event.content && !resolvedConversationId) {
                            resolvedConversationId = event.content
                            setConversationId(event.content)
                        }
                        break
                    case 'token':
                        setMessages(prev => prev.map(msg =>
                            msg.id === streamId
                                ? { ...msg, content: msg.content + event.content }
                                : msg
                        ))
                        break
                    case 'sources':
                        // The backend sends sources in event.content as a comma-separated string
                        const sourceStr = event.sources || event.content || ''
                        const sourceList = Array.isArray(sourceStr) 
                            ? sourceStr 
                            : sourceStr.split(',').filter(Boolean)
                            
                        setMessages(prev => prev.map(msg =>
                            msg.id === streamId
                                ? { ...msg, sources: sourceList }
                                : msg
                        ))
                        break
                    case 'done':
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
            (err) => {
                if (err instanceof RateLimitError) {
                    setRateLimitSeconds(err.retryAfterSeconds)
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

    // Show spinner while repo info or history is loading
    if (!repo || historyLoading) {
        return (
            <div className="min-h-screen bg-[#080809] flex items-center justify-center">
                <LoadingSpinner size="lg" />
            </div>
        )
    }

    const slug = repo.githubUrl?.replace('https://github.com/', '') ?? ''

    return (
        <div className="flex flex-col h-screen bg-[#080809] text-white overflow-hidden">
            {/* Background glows */}
            <div className="pointer-events-none fixed top-0 right-0 w-[600px] h-[600px] z-0 opacity-20"
                style={{ background: 'radial-gradient(circle at 100% 0%, rgba(139,92,246,0.15) 0%, transparent 70%)' }} />
            <div className="pointer-events-none fixed bottom-0 left-0 w-[400px] h-[400px] z-0 opacity-10"
                style={{ background: 'radial-gradient(circle at 0% 100%, rgba(236,72,153,0.1) 0%, transparent 70%)' }} />

            {/* ── Top Bar ─────────────────────────────────────────── */}
            <NavBar 
                repoName={repo?.repoName} 
                action={
                    messages.length > 0 && (
                        <button
                            onClick={async () => {
                                if (conversationId) {
                                    try {
                                        await clearChatConversation(conversationId)
                                    } catch (e) {
                                        console.error('Failed to clear database conversation:', e)
                                    }
                                }
                                setMessages([])
                                setConversationId(null)
                            }}
                            className="cursor-pointer text-[11px] font-bold text-neutral-500 hover:text-red-400 bg-white/[0.02] hover:bg-red-500/5 border border-white/[0.05] hover:border-red-500/15 px-3 py-1.5 rounded-xl transition-all"
                            title="Clear chat history"
                        >
                            Clear
                        </button>
                    )
                }
            />

            {/* ── Message area ─────────────────────────────────────── */}
            <div className="flex-1 overflow-hidden relative z-10">
                <div className="h-full px-4 sm:px-8 flex flex-col">
                    <MessageList
                        messages={messages}
                        loading={loading}
                        onSuggest={handleSend}
                    />
                </div>
            </div>

            {/* ── Input area ───────────────────────────────────────── */}
            <div className="relative z-10 shrink-0">
                <div className="px-4 sm:px-8 pb-5 pt-2 space-y-2">
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
                    <p className="text-center text-[10px] text-neutral-800">
                        Enter to send &middot; Shift+Enter for newline
                    </p>
                </div>
            </div>
        </div>
    )
}

// Outer wrapper: handles missing repoId before any hooks are called
export default function ChatPage() {
    const { repoId } = useParams()

    // No repo selected — show picker
    if (!repoId) return <RepoSelector tool="chat" />

    // key={repoId} ensures ChatPageInner fully remounts when switching repos
    return <ChatPageInner key={repoId} repoId={repoId} />
}