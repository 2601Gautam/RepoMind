import { useEffect, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import { Link } from 'react-router-dom'
import { Activity, Check, LoaderCircle, RefreshCw, TriangleAlert, X } from 'lucide-react'
import { useActivity } from '../../context/ActivityContext'
import { isRepoActive, taskLink } from '../../state/recovery.js'

const labels = { RUNNING: 'Running', COMPLETED: 'Completed', FAILED: 'Failed', INTERRUPTED: 'Interrupted', UNKNOWN: 'Status unconfirmed' }

export default function ActivityPanel({ compact = false }) {
    const { repositories, tasks, degraded, loading, error, refresh, forget } = useActivity()
    const [open, setOpen] = useState(false)
    const trigger = useRef(null)
    const panel = useRef(null)
    const close = useRef(null)
    const working = repositories.filter(isRepoActive)
    const count = working.length + tasks.filter(t => t.status === 'RUNNING').length
    const needsAttention = !!error || degraded || tasks.some(t => ['FAILED', 'INTERRUPTED', 'UNKNOWN'].includes(t.status))
    const summary = loading ? 'Checking activity…' : count ? `${count} in progress` : needsAttention ? 'Check recent activity' : 'All caught up'

    useEffect(() => {
        if (!open) return
        close.current?.focus()
        const dismiss = event => {
            if (!panel.current?.contains(event.target) && !trigger.current?.contains(event.target)) setOpen(false)
        }
        const escape = event => {
            if (event.key === 'Escape') { setOpen(false); trigger.current?.focus() }
        }
        document.addEventListener('pointerdown', dismiss)
        document.addEventListener('keydown', escape)
        return () => {
            document.removeEventListener('pointerdown', dismiss)
            document.removeEventListener('keydown', escape)
        }
    }, [open])

    return <>
        <div className={`flex items-center gap-3 ${compact ? 'justify-center' : ''}`}>
            <button ref={trigger} type="button" onClick={() => setOpen(value => !value)}
                aria-label={`Activity: ${summary}`} aria-expanded={open} aria-controls="activity-panel" aria-haspopup="dialog"
                title={`Activity: ${summary}`}
                className={`relative flex h-10 w-10 shrink-0 cursor-pointer items-center justify-center rounded-full border transition-colors focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-violet-400 ${count ? 'border-violet-400/50 bg-violet-500/10 text-violet-300' : 'border-white/10 bg-white/[0.03] text-neutral-400 hover:bg-white/[0.08] hover:text-white'}`}>
                {count || loading ? <LoaderCircle size={18} className="motion-safe:animate-spin" /> : <Activity size={18} />}
                {!!count && <span className="absolute -right-1 -top-1 min-w-4 rounded-full bg-violet-500 px-1 text-[10px] font-bold leading-4 text-white">{count}</span>}
                {!count && needsAttention && <span className="absolute right-0 top-0 h-2.5 w-2.5 rounded-full border-2 border-[#060608] bg-amber-400" />}
            </button>
            {!compact && <div className="min-w-0">
                <p className="text-xs font-semibold text-neutral-300">Activity</p>
                <p className="mt-0.5 truncate text-[10.5px] text-neutral-500">{summary}</p>
            </div>}
            <span role="status" className="sr-only">{summary}</span>
        </div>
        {open && createPortal(<section ref={panel} id="activity-panel" role="dialog" aria-labelledby="activity-heading"
            className="fixed bottom-20 left-4 z-[60] flex max-h-[calc(100dvh-7rem)] w-[360px] max-w-[calc(100vw-2rem)] flex-col overflow-hidden rounded-2xl border border-white/10 bg-[#111118] text-sm text-neutral-200 shadow-2xl shadow-black/60">
            <div className="flex items-center justify-between border-b border-white/[0.06] px-4 py-3">
                <div><h2 id="activity-heading" className="font-semibold text-white">Activity</h2><p className="mt-0.5 text-xs text-neutral-500">{summary}</p></div>
                <div className="flex gap-1">
                    <button type="button" onClick={refresh} aria-label="Refresh activity" title="Refresh activity" className="cursor-pointer rounded-lg p-2 hover:bg-white/10"><RefreshCw size={15} /></button>
                    <button ref={close} type="button" onClick={() => { setOpen(false); trigger.current?.focus() }} aria-label="Close activity" className="cursor-pointer rounded-lg p-2 hover:bg-white/10"><X size={16} /></button>
                </div>
            </div>
            <div className="space-y-2 overflow-y-auto p-3">
                {error && <p role="alert" className="rounded-xl bg-amber-400/10 p-3 text-xs leading-relaxed text-amber-200">{error}</p>}
                {degraded && !error && <p role="status" className="p-2 text-xs text-neutral-400">Repository status is available. AI activity is temporarily unavailable.</p>}
                {working.map(repo => <Link key={repo.id} to="/repositories" onClick={() => setOpen(false)} className="flex items-center gap-3 rounded-xl bg-white/[0.03] p-3 hover:bg-white/[0.06]">
                    <LoaderCircle size={16} className="shrink-0 text-violet-300 motion-safe:animate-spin" />
                    <div className="min-w-0"><p className="truncate text-xs font-medium">{repo.repoName || 'Repository'}</p><p className="mt-1 text-[11px] text-violet-300">{repo.syncing ? 'Syncing' : repo.status === 'PENDING' && repo.errorMessage ? 'Awaiting retry' : repo.status === 'PENDING' ? 'Queued' : 'Indexing'}</p></div>
                </Link>)}
                {tasks.slice(0, 20).map(task => <div key={task.id} className="rounded-xl bg-white/[0.03] p-3">
                    <Link to={taskLink(task)} onClick={() => setOpen(false)} className="flex items-start gap-3 hover:text-white">
                        {task.status === 'RUNNING' ? <LoaderCircle size={16} className="mt-0.5 shrink-0 text-violet-300 motion-safe:animate-spin" /> : task.status === 'COMPLETED' ? <Check size={16} className="mt-0.5 shrink-0 text-emerald-400" /> : <TriangleAlert size={16} className="mt-0.5 shrink-0 text-amber-300" />}
                        <div className="min-w-0"><p className="truncate text-xs font-medium">{repositories.find(repo => repo.id === task.repoId)?.repoName || 'Repository'}</p><p className={`mt-1 text-[11px] ${task.status === 'COMPLETED' ? 'text-emerald-400' : task.status === 'RUNNING' ? 'text-violet-300' : 'text-amber-300'}`}><span className="capitalize">{task.kind.toLowerCase()}</span> · {labels[task.status] || labels.UNKNOWN}</p></div>
                    </Link>
                    {['INTERRUPTED', 'UNKNOWN'].includes(task.status) && <p className="mt-2 text-[11px] text-neutral-400">Check saved results before sending another request.</p>}
                    {task.kind === 'DEBUG' && task.status === 'COMPLETED' && <p className="mt-2 text-[11px] text-neutral-400">Debug output is kept in this browser until you reload or close the tab.</p>}
                    {task.localOnly && <button type="button" className="mt-2 cursor-pointer text-xs text-neutral-400 underline" onClick={() => forget(task.id)}>Dismiss</button>}
                </div>)}
                {!working.length && !tasks.length && !loading && <p className="px-2 py-6 text-center text-xs text-neutral-500">No recent activity yet.</p>}
            </div>
            <p className="border-t border-white/[0.06] px-4 py-3 text-[11px] leading-relaxed text-neutral-500">Chats keep running when you leave the page. Open a completed chat to view its saved answer.</p>
        </section>, document.body)}
    </>
}
