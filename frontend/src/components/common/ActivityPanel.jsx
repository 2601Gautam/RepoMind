import { Link } from 'react-router-dom'
import { useActivity } from '../../context/ActivityContext'
import { isRepoActive, taskLink } from '../../state/recovery.js'

export default function ActivityPanel() {
    const { repositories, tasks, degraded, loading, error, refresh, forget } = useActivity()
    const working = repositories.filter(isRepoActive)
    const active = tasks.filter(t => t.status === 'RUNNING')
    const labels = { RUNNING:'Running', COMPLETED:'Completed', FAILED:'Failed', INTERRUPTED:'Interrupted', UNKNOWN:'Status unconfirmed' }
    return <div className="border-b border-white/10 bg-[#111118] px-6 py-3 text-sm text-neutral-200" aria-live="polite">
        {degraded && !error && <p role="status" className="text-neutral-400">The server has no activity service yet, so repository status uses the basic list. Interrupted AI requests will not be shown until the backend is updated.</p>}
                {error && <p role="alert" className="text-amber-300">{error} <button onClick={refresh} className="underline ml-2">Check again</button></p>}
        <details>
            <summary className="cursor-pointer">{loading ? 'Checking your activity…' : working.length + active.length
                ? `${working.length + active.length} operation(s) in progress` : 'Recent activity and recovery'}</summary>
            <div className="mt-3 space-y-2">
                {working.map(r => <p key={r.id}><Link className="underline" to="/repositories">{r.repoName || 'Repository'}</Link>
                    {' — '}{r.syncing ? 'Syncing' : r.status === 'PENDING' && r.errorMessage ? 'Awaiting retry' : r.status === 'PENDING' ? 'Queued' : 'Indexing'}</p>)}
                {tasks.slice(0,20).map(t => <div key={t.id}>
                    <Link className="underline" to={taskLink(t)}>{t.kind.toLowerCase()} · {repositories.find(r=>r.id===t.repoId)?.repoName || 'Repository'}</Link>
                    {' — '}{labels[t.status] || 'Status unconfirmed'}
                    {['INTERRUPTED','UNKNOWN'].includes(t.status) && <span className="text-amber-200">. Check saved results before sending another request.</span>}
                    {t.kind === 'DEBUG' && t.status === 'COMPLETED' && <span>. Debug output is available only in its original open page.</span>}
                    {t.localOnly && <button className="ml-2 underline" onClick={()=>forget(t.id)}>Dismiss</button>}
                </div>)}
                {!working.length && !tasks.length && !loading && <p>No recent AI requests or active repository jobs found.</p>}
                <p className="text-xs text-neutral-400">Saved chats and interview sessions can be reopened. Interrupted streams are not automatically resumed or resubmitted.</p>
            </div>
        </details>
    </div>
}

