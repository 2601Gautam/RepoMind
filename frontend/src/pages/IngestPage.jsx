import { useState } from 'react'
import { ingestRepo } from '../api/client'
import { useActivity } from '../context/ActivityContext'
import RepoUrlInput from '../components/repo/RepoUrlInput'
import IngestionProgress from '../components/repo/IngestionProgress'
import RepoCard from '../components/repo/RepoCard'

export default function IngestPage() {
    const { repositories, refresh, error: connectionError } = useActivity()
    const [busy,setBusy] = useState(false)
    const [error,setError] = useState('')
    async function submit(url,token) {
        setBusy(true); setError('')
        try { await ingestRepo(url,token) }
        catch (e) { setError(e.message + ' Check current jobs before submitting again.') }
        finally { setBusy(false); refresh() }
    }
    return <div className="max-w-xl mx-auto py-10 space-y-6 text-white">
        <h2 className="text-2xl">Analyze a Repository</h2>
        <RepoUrlInput onSubmit={submit} disabled={busy} />
        {(error || connectionError) && <p role="alert">{error || connectionError}</p>}
        {repositories.map(repo=>repo.status==='READY' ? <RepoCard key={repo.id} repo={repo} /> : <IngestionProgress key={repo.id} repo={repo} />)}
    </div>
}

