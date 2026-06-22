import { useState } from 'react'
import Header from './components/layout/Header'
import IngestPage from './pages/IngestPage'
import ChatPage from './pages/ChatPage'

export default function App() {
    const [selectedRepo, setSelectedRepo] = useState(null)

    return (
        <div className="min-h-screen bg-gray-950 text-white">
            <Header
                repoName={selectedRepo?.repoName}
                onLogoClick={() => setSelectedRepo(null)}
            />
            <main className="max-w-4xl mx-auto px-6">
                {selectedRepo
                    ? <ChatPage repo={selectedRepo} onBack={() => setSelectedRepo(null)} />
                    : <IngestPage onSelect={setSelectedRepo} />
                }
            </main>
        </div>
    )
}