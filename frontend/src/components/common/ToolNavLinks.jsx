import { Link } from 'react-router-dom'

const TOOLS = [
    { key: 'chat',      label: 'Chat' },
    { key: 'interview', label: 'Interview' },
    { key: 'debug',     label: 'Debug' },
]

// Small pill-style quick-nav links between the three repo tools (Chat/Interview/Debug).
// Renders a link for every tool except `current` (the tool the page itself represents),
// so the page doesn't link to itself.
//
// repoId: active repo id, used to build /{tool}/{repoId} links
// current: 'chat' | 'interview' | 'debug' — the tool this page belongs to
export default function ToolNavLinks({ repoId, current }) {
    const others = TOOLS.filter(t => t.key !== current)

    return (
        <div className="flex items-center gap-1.5 shrink-0">
            {others.map(t => (
                <Link
                    key={t.key}
                    to={`/${t.key}/${repoId}`}
                    className="text-xs bg-[#0f0f11] hover:bg-[#151518] border border-white/[0.06] text-neutral-400 hover:text-white px-3.5 py-1.5 rounded-lg transition-all font-medium"
                >
                    {t.label}
                </Link>
            ))}
        </div>
    )
}
