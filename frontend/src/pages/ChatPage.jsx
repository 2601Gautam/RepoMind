import { useState, useRef, useEffect } from 'react'
import { useParams, Link, useNavigate } from 'react-router-dom'
import { streamChat, getRepoStatus, getChatHistory, clearChatConversation, RateLimitError } from '../api/client'
import { useActivity } from '../context/ActivityContext'
import { useAiOperation } from '../hooks/useAiOperation'
import RepositoryNotice from '../components/repo/RepositoryNotice'
import NavBar from '../components/layout/NavBar'
import MessageList from '../components/chat/MessageList'
import ChatInput from '../components/chat/ChatInput'
import RateLimitBanner from '../components/common/RateLimitBanner'
import LoadingSpinner from '../components/common/LoadingSpinner'
import RepoSelector from '../components/repo/RepoSelector'

function FollowupInfo() {
    return (
        <div className="group relative inline-flex items-center">
            <button
                type="button"
                aria-label="Follow-up accuracy note"
                className="flex h-5 w-5 cursor-help items-center justify-center rounded-full border border-violet-400/30 bg-transparent text-[11px] font-bold text-violet-300 transition-colors duration-150 hover:border-violet-300/50 hover:text-violet-100 focus:outline-none focus-visible:border-violet-300/60 focus-visible:text-violet-100"
            >
                i
            </button>
            <div className="pointer-events-none absolute bottom-7 left-1/2 z-50 w-72 -translate-x-1/2 rounded-xl border border-white/[0.08] bg-[#111114] px-3.5 py-3 text-left text-[11.5px] leading-relaxed text-neutral-300 opacity-0 shadow-2xl shadow-black/50 transition-opacity duration-150 group-hover:opacity-100 group-focus-within:opacity-100">
                After 5 days of inactivity, try not to use short follow-up questions. Ask the full question with the key details for better results.
            </div>
        </div>
    )
}

// Inner component — all hooks run unconditionally here
function ChatPageInner({ repoId }) {
    const { tasks } = useActivity()
    const operation = useAiOperation('CHAT',repoId)
    const [restoreError,setRestoreError] = useState('')
    const loadingRef = useRef(false)
    const completed = tasks.filter(t=>t.repoId===repoId && t.kind==='CHAT' && t.status==='COMPLETED').map(t=>t.id).join(',')
    const [reload,setReload] = useState(0)
    const [repo, setRepo] = useState(null)
    const [messages, setMessages] = useState([])
    const [conversationId, setConversationId] = useState(null)
    const [loading, setLoading] = useState(false)
    const [historyLoading, setHistoryLoading] = useState(true)  // true until DB history is fetched
    const [rateLimitSeconds, setRateLimitSeconds] = useState(null)
    const [directValue, setDirectValue] = useState(null)
    const messageIdRef = useRef(0)

    useEffect(() => {
        let active = true
        getRepoStatus(repoId).then(data => { if(active) setRepo(data) })
            .catch(e => { if(active) setRestoreError(e.message) })
        return () => { active = false }
    }, [repoId,reload])

    useEffect(() => {
        let active = true
        async function restore() {
            if (loadingRef.current) return
            setHistoryLoading(true)
            try {
                const data = await getChatHistory(repoId)
                if (!active || loadingRef.current) return
                if (data) {
                    setMessages(data.messages.map(m=>({ role:m.role, content:m.content,
                        sources:m.sourcePaths ?? (m.sources ? m.sources.split(',').filter(Boolean) : []) })))
                    setConversationId(data.conversationId)
                }
                setRestoreError('')
            } catch (e) { if(active) setRestoreError('Could not restore saved chat: ' + e.message) }
            finally { if(active) setHistoryLoading(false) }
        }
        restore()
        window.addEventListener('online',restore)
        return () => { active=false; window.removeEventListener('online',restore) }
    }, [repoId,completed,reload])

    async function handleSend(text) {
        if (loading || operation.runningElsewhere || repo?.status !== 'READY') return
        const attempt = operation.start()
        if (!attempt) return
        loadingRef.current = true

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
                if (!attempt.live()) return
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
                        // Current responses carry file paths as a structured array.
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
                if (!attempt.live()) return
                if (err instanceof RateLimitError) {
                    setRateLimitSeconds(err.retryAfterSeconds)
                    setMessages(prev => prev.filter(msg => msg.id !== streamId))
                } else {
                    setMessages(prev => prev.map(msg =>
                        msg.id === streamId
                            ? { ...msg, content: msg.content + '\n\nResponse interrupted. ' + err.message + ' Check saved history before sending again.', streaming: false }
                            : msg
                    ))
                }
                setLoading(false)
            },
            attempt.options
        )
        if (attempt.live()) { loadingRef.current=false; setLoading(false) }
        attempt.finish()
    }

    // Show spinner while repo info or history is loading
    if ((!repo || historyLoading) && !restoreError) {
        return (
            <div className="min-h-screen bg-[#080809] flex items-center justify-center">
                <LoadingSpinner size="lg" />
            </div>
        )
    }

    const slug = repo?.githubUrl?.replace('https://github.com/', '') ?? ''

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
                            disabled={loading || operation.runningElsewhere}
                            onClick={async () => {
                                if (conversationId) {
                                    try {
                                        await clearChatConversation(conversationId)
                                    } catch (e) {
                                        setRestoreError('Could not clear saved conversation: ' + e.message); return
                                    }
                                }
                                setMessages([])
                                setConversationId(null)
                            }}
                            className="group cursor-pointer flex items-center gap-1.5 text-[11.5px] font-bold text-red-400 hover:text-red-300 bg-red-500/10 hover:bg-red-500/20 active:bg-red-500/30 border border-red-500/20 hover:border-red-500/40 px-3 py-1.5 rounded-xl transition-all duration-200 shadow-[0_0_10px_rgba(239,68,68,0.1)] hover:shadow-[0_0_15px_rgba(239,68,68,0.25)] active:scale-95"
                            title="Clear chat history"
                        >
                            <svg 
                                viewBox="0 0 24 24" 
                                fill="none" 
                                stroke="currentColor" 
                                strokeWidth="2" 
                                strokeLinecap="round" 
                                strokeLinejoin="round" 
                                className="w-3.5 h-3.5 text-red-400 group-hover:text-red-300 transition-colors duration-200 shrink-0 overflow-visible"
                            >
                                {/* Animated Dustbin Lid */}
                                <g 
                                    className="transition-transform duration-300 ease-out group-hover:-rotate-25 group-hover:-translate-y-0.5 group-active:rotate-0 group-active:translate-y-0"
                                    style={{ transformOrigin: '3px 6px', transformBox: 'fill-box' }}
                                >
                                    <path d="M3 6h18" />
                                    <path d="M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2" />
                                </g>
                                {/* Dustbin Body */}
                                <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6" />
                                <line x1="10" y1="11" x2="10" y2="17" />
                                <line x1="14" y1="11" x2="14" y2="17" />
                            </svg>
                            <span>Clear</span>
                        </button>
                    )
                }
            />

            {restoreError && <p role="alert" className="px-8 py-3 text-amber-300">{restoreError} <button className="underline" onClick={()=>setReload(x=>x+1)}>Reload saved history</button></p>}
            {operation.runningElsewhere && !loading && <p role="status" className="px-8 py-3 text-violet-200">A chat request is running. Saved history will refresh when it finishes.</p>}
            {repo && repo.status !== 'READY' && <RepositoryNotice repo={repo} />}
            {/* ── Message area ─────────────────────────────────────── */}
            <div className="flex-1 overflow-hidden relative z-10">
                <div className="h-full px-4 sm:px-8 flex flex-col">
                    <MessageList
                        messages={messages}
                        loading={loading}
                        onSuggest={handleSend}
                        onDirect={(text) => setDirectValue(text)}
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
                        disabled={loading || operation.runningElsewhere || historyLoading || !!restoreError || repo?.status !== 'READY' || !!rateLimitSeconds}
                        directValue={directValue}
                        onDirectConsumed={() => setDirectValue(null)}
                    />
                    <div className="flex items-center justify-center gap-2 text-[10px] text-violet-300/65">
                        <span>Enter to send &middot; Shift+Enter for newline</span>
                        <FollowupInfo />
                    </div>
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
