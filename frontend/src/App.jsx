import { useState } from 'react'
import IngestPage from './pages/IngestPage'
import ChatPage from './pages/ChatPage'

export default function App() {
    const [selectedRepo, setSelectedRepo] = useState(null)

    return (
        <div className="min-h-screen bg-gray-950 text-white">
            <header className="border-b border-gray-800 px-6 py-4">
                <div className="max-w-3xl mx-auto flex items-center gap-3">
                    <h1
                        onClick={() => setSelectedRepo(null)}
                        className="text-xl font-bold text-blue-400 cursor-pointer hover:text-blue-300 transition-colors"
                    >
                        RepoMind
                    </h1>
                    {selectedRepo && (
                        <span className="text-gray-600 text-sm">
                            / {selectedRepo.repoName}
                        </span>
                    )}
                </div>
            </header>

            <main className="px-6 py-8">
                <div className="max-w-3xl mx-auto">
                    {selectedRepo
                        ? <ChatPage repo={selectedRepo} onBack={() => setSelectedRepo(null)} />
                        : <IngestPage onSelect={setSelectedRepo} />
                    }
                </div>
            </main>
        </div>
    )
}