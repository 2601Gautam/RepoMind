import { repositoryNotice } from '../../state/recovery.js'
export default function RepositoryNotice({ repo, showName = false }) {
    const notice = repositoryNotice(repo)
    if (!notice) return null
    return <div role={notice.tone === 'error' ? 'alert' : 'status'} className="my-3 rounded-lg border border-amber-400/20 bg-amber-500/5 p-3 text-sm text-amber-100 break-words">
        <strong>{notice.title}{showName && repo.repoName ? ' — ' + repo.repoName : ''}</strong><p className="mt-1">{notice.message}</p>
    </div>
}

