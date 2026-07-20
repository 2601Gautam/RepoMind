import { useState, useEffect, useRef } from 'react'
import { listRepos, getRepoStatus, deleteRepo } from '../api/client'
import RepoCard from '../components/repo/RepoCard'
import NavBar from '../components/layout/NavBar'
import LoadingSpinner from '../components/common/LoadingSpinner'

export default function AllReposPage() {
    const [repos, setRepos] = useState([])
    const [page, setPage] = useState(0)
    const [loadingRepos, setLoadingRepos] = useState(true)
    const [searchQuery, setSearchQuery] = useState('')
    const pollsRef = useRef({})

    useEffect(() => {
        loadRepos()
        return () => Object.values(pollsRef.current).forEach(clearInterval)
    }, [page])

    async function loadRepos() {
        setLoadingRepos(true)
        try {
            const data = await listRepos(page, 5)
            const repoList = Array.isArray(data) ? data : (data.content || [])
            // Filter out FAILED repos — never show them in the UI
            const visible = repoList.filter(r => r.status !== 'FAILED')
            setRepos(visible)
            visible.forEach(repo => {
                if (repo.status === 'PROCESSING' || repo.status === 'PENDING') startPollingRepo(repo.id)
            })
        } catch (e) {
            console.error('Failed to load repos:', e)
        } finally {
            setLoadingRepos(false)
        }
    }

    function startPollingRepo(repoId) {
        if (pollsRef.current[repoId]) return
        pollsRef.current[repoId] = setInterval(async () => {
            try {
                const updated = await getRepoStatus(repoId)
                if (updated.status === 'FAILED') {
                    // Remove failed repos from the list silently
                    clearInterval(pollsRef.current[repoId])
                    delete pollsRef.current[repoId]
                    setRepos(prev => prev.filter(r => r.id !== repoId))
                    return
                }
                setRepos(prev => prev.map(r => r.id === repoId ? updated : r))
                if (updated.status === 'READY') {
                    clearInterval(pollsRef.current[repoId])
                    delete pollsRef.current[repoId]
                }
            } catch { /* silent retry */ }
        }, 3000)
    }

    async function handleRemove(repoId) {
        setRepos(prev => prev.filter(r => r.id !== repoId))
        if (pollsRef.current[repoId]) {
            clearInterval(pollsRef.current[repoId])
            delete pollsRef.current[repoId]
        }
        try { await deleteRepo(repoId) } catch { /* silent */ }
    }

    return (
        <div className="min-h-screen bg-[#070709] text-white antialiased">
            <NavBar />

            <div className="max-w-5xl mx-auto px-6 sm:px-8 pt-10 pb-20">
                <div className="mb-8">
                    <h1 className="text-[24px] font-extrabold tracking-tight text-white mb-1.5 leading-tight">
                        All Repositories
                    </h1>
                    <p className="text-[13.5px] text-neutral-400">
                        View and manage all the repositories you have previously imported into RepoMind.
                    </p>
                </div>

                {loadingRepos ? (
                    <div className="flex items-center justify-center h-64">
                        <LoadingSpinner size="lg" />
                    </div>
                ) : repos.length === 0 ? (
                    <div className="flex flex-col items-center justify-center h-64 text-center border border-dashed border-white/[0.04] rounded-2xl bg-white/[0.01] px-4">
                        <div className="w-10 h-10 rounded-xl bg-white/[0.02] border border-white/[0.06] flex items-center justify-center mb-3">
                            <svg viewBox="0 0 16 16" fill="currentColor" className="w-4 h-4 text-neutral-500">
                                <path d="M8 0C3.58 0 0 3.58 0 8c0 3.54 2.29 6.53 5.47 7.59.4.07.55-.17.55-.38 0-.19-.01-.82-.01-1.49-2.01.37-2.53-.49-2.69-.94-.09-.23-.48-.94-.82-1.13-.28-.15-.68-.52-.01-.53.63-.01 1.08.58 1.23.82.72 1.21 1.87.87 2.33.66.07-.52.28-.87.51-1.07-1.78-.2-3.64-.89-3.64-3.95 0-.87.31-1.59.82-2.15-.08-.2-.36-1.02.08-2.12 0 0 .67-.21 2.2.82.64-.18 1.32-.27 2-.27.68 0 1.36.09 2 .27 1.53-1.04 2.2-.82 2.2-.82.44 1.1.16 1.92.08 2.12.51.56.82 1.27.82 2.15 0 3.07-1.87 3.75-3.65 3.95.29.25.54.73.54 1.48 0 1.07-.01 1.93-.01 2.2 0 .21.15.46.55.38A8.013 8.013 0 0016 8c0-4.42-3.58-8-8-8z" />
                            </svg>
                        </div>
                        <p className="text-[13px] font-semibold text-neutral-400">No repositories added yet</p>
                        <p className="mt-1 text-[12px] text-neutral-600 max-w-xs">
                            Import repository link in the Dashboard page first.
                        </p>
                    </div>
                ) : (() => {
                    const filtered = repos.filter(repo => {
                        const q = searchQuery.toLowerCase().trim()
                        if (!q) return true
                        const name = (repo.repoName || '').toLowerCase()
                        const slug = (repo.githubUrl || '').toLowerCase()
                        return name.includes(q) || slug.includes(q)
                    })

                    return (
                        <div className="space-y-5">
                            {/* Toolbar controls */}
                            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 border-b border-white/[0.04] pb-3.5">
                                <span className="text-[11.5px] font-bold uppercase tracking-widest text-neutral-500">
                                    Repositories ({repos.length})
                                </span>

                                <div className="flex items-center gap-2.5">
                                    {/* Search Input */}
                                    <div className="relative">
                                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="w-3.5 h-3.5 absolute left-3 top-1/2 -translate-y-1/2 text-neutral-500">
                                            <circle cx="11" cy="11" r="8" />
                                            <line x1="21" y1="21" x2="16.65" y2="16.65" />
                                        </svg>
                                        <input
                                            type="text"
                                            value={searchQuery}
                                            onChange={e => setSearchQuery(e.target.value)}
                                            placeholder="Search list..."
                                            className="w-full sm:w-44 bg-[#0d0d0f] border border-white/[0.06] rounded-lg pl-9 pr-3 py-1.5 text-[12px] text-white placeholder-neutral-600 focus:outline-none focus:border-white/[0.12] transition-all"
                                        />
                                    </div>
                                </div>
                            </div>

                            {/* Repos mapping */}
                            {filtered.length === 0 ? (
                                <div className="py-12 text-center text-[12.5px] text-neutral-650">
                                    No repositories match "{searchQuery}"
                                </div>
                            ) : (
                                <div className="flex flex-col gap-2.5">
                                    {filtered.map(repo => (
                                        <RepoCard key={repo.id} repo={repo} viewMode="list" onRemove={handleRemove} />
                                    ))}
                                </div>
                            )}
                        </div>
                    )
                })()}
            </div>
        </div>
    )
}
