import { citedSourcePaths } from '../../state/chatSources.js'

// Show only citations, including when reopening conversations saved by an older backend.
export default function SourceBadges({ sources, content }) {
    const cited = citedSourcePaths(content, sources)
    if (!cited.length) return null

    return (
        <div className="mt-0 flex flex-wrap gap-1.5">
            <span className="text-[10px] font-semibold uppercase tracking-wider text-neutral-600 self-center mr-1">
                Sources
            </span>
            {cited.map((src, i) => {
                const filename = src.split('/').pop()
                return (
                    <span
                        key={i}
                        title={src}
                        className="text-[10.5px] font-mono text-violet-300 bg-violet-500/8 border border-violet-500/15 px-2 py-0.5 rounded-lg truncate max-w-[180px]"
                    >
                        {filename}
                    </span>
                )
            })}
        </div>
    )
}
