import { useState, useRef, useEffect } from 'react'
import { sendMessage } from '../api/client'
import ReactMarkdown from 'react-markdown'

export default function ChatPage({ repo, onBack }) {
    const [messages, setMessages] = useState([])
    const [input, setInput] = useState('')
    const [loading, setLoading] = useState(false)
    // conversationId starts null — backend creates one on first message
    // After first message, we store the ID and send it with every subsequent message
    // This groups all messages in this session into one conversation in the DB
    const [conversationId, setConversationId] = useState(null)
    const bottomRef = useRef(null)

    // Auto scroll to latest message whenever messages array changes
    useEffect(() => {
        bottomRef.current?.scrollIntoView({ behavior: 'smooth' })
    }, [messages])

    async function send() {
        const question = input.trim()
        if (!question || loading) return

        setInput('')
        // Add user message to UI immediately without waiting for API response
        // This makes the UI feel fast and responsive
        setMessages(prev => [...prev, {
            role: 'user',
            content: question
        }])
        setLoading(true)

        try {
            const res = await sendMessage(repo.id, question, conversationId)

            // Store conversationId from first response
            // All subsequent messages send this ID back to the server
            if (!conversationId) {
                setConversationId(res.conversationId)
            }

            setMessages(prev => [...prev, {
                role: 'assistant',
                content: res.answer,
                sources: res.sources
            }])

        } catch (e) {
            // Show error as an assistant message so it appears in the flow
            setMessages(prev => [...prev, {
                role: 'assistant',
                content: `Error: ${e.message}. Please try again.`
            }])
        } finally {
            setLoading(false)
        }
    }

    return (
        <div className="flex flex-col h-[85vh] max-w-3xl mx-auto">

            {/* Header */}
            <div className="flex items-center gap-3 pb-4 border-b border-gray-800 mb-4">
                <button
                    onClick={onBack}
                    className="text-gray-400 hover:text-white transition-colors text-sm"
                >
                    ← Back
                </button>
                <div className="flex-1">
                    <p className="font-semibold">{repo.repoName}</p>
                    <p className="text-xs text-gray-500">{repo.totalChunks} chunks indexed</p>
                </div>
            </div>

            {/* Messages area */}
            <div className="flex-1 overflow-y-auto space-y-4 pr-1">
                {messages.length === 0 && (
                    <div className="text-center mt-16 space-y-2">
                        <p className="text-gray-400">Ask anything about this repository</p>
                        <div className="flex flex-wrap gap-2 justify-center mt-4">
                            {/* Example questions the user can click */}
                            {[
                                'How does authentication work?',
                                'What does the main service do?',
                                'Where is the database configured?'
                            ].map(q => (
                                <button
                                    key={q}
                                    onClick={() => { setInput(q); }}
                                    className="text-xs bg-gray-800 hover:bg-gray-700 border border-gray-700 rounded px-3 py-1.5 text-gray-300 transition-colors"
                                >
                                    {q}
                                </button>
                            ))}
                        </div>
                    </div>
                )}

                {messages.map((msg, i) => (
                    <div
                        key={i}
                        className={`flex ${msg.role === 'user' ? 'justify-end' : 'justify-start'}`}
                    >
                        <div className={`max-w-2xl rounded-lg p-3 text-sm ${
                            msg.role === 'user'
                                ? 'bg-blue-700 text-white'
                                : 'bg-gray-800 text-gray-100'
                        }`}>
                            {/* ReactMarkdown renders code blocks, bold, lists from LLM response */}
                            <ReactMarkdown className="prose prose-invert prose-sm max-w-none">
                                {msg.content}
                            </ReactMarkdown>

                            {/* Source files — only shown on assistant messages */}
                            {msg.sources?.length > 0 && (
                                <div className="mt-3 pt-2 border-t border-gray-600">
                                    <p className="text-xs text-gray-400 mb-1">Sources used:</p>
                                    <div className="flex flex-wrap gap-1">
                                        {msg.sources.map((src, j) => (
                                            <span
                                                key={j}
                                                className="text-xs text-blue-400 font-mono bg-blue-400/10 px-2 py-0.5 rounded"
                                            >
                                                {src}
                                            </span>
                                        ))}
                                    </div>
                                </div>
                            )}
                        </div>
                    </div>
                ))}

                {/* Typing indicator while waiting for LLM response */}
                {loading && (
                    <div className="flex justify-start">
                        <div className="bg-gray-800 rounded-lg p-3 text-sm text-gray-400">
                            Thinking...
                        </div>
                    </div>
                )}

                {/* Invisible div at bottom — scrollIntoView targets this */}
                <div ref={bottomRef} />
            </div>

            {/* Input area */}
            <div className="flex gap-2 pt-4 border-t border-gray-800 mt-4">
                <input
                    value={input}
                    onChange={e => setInput(e.target.value)}
                    onKeyDown={e => e.key === 'Enter' && !e.shiftKey && send()}
                    placeholder="Ask about the repository..."
                    disabled={loading}
                    className="flex-1 bg-gray-900 border border-gray-700 rounded px-4 py-2 text-white placeholder-gray-500 focus:outline-none focus:border-blue-500 disabled:opacity-50"
                />
                <button
                    onClick={send}
                    disabled={loading || !input.trim()}
                    className="bg-blue-600 hover:bg-blue-700 disabled:opacity-40 px-5 py-2 rounded font-medium transition-colors"
                >
                    Send
                </button>
            </div>
        </div>
    )
}