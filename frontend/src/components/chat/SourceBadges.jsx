// Shown under every assistant message that has source files
// Separated so MessageBubble stays clean — just renders <SourceBadges sources={...} />
export default function SourceBadges({ sources }) {
    if (!sources?.length) return null

    return (
        <div className="mt-3 pt-2 border-t border-gray-700">
            <p className="text-xs text-gray-400 mb-1.5">Sources:</p>
            <div className="flex flex-wrap gap-1">
                {sources.map((src, i) => (
                    <span
                        key={i}
                        className="text-xs font-mono text-yellow-400 bg-white-400/10 border border-blue-400/20 px-2 py-0.5 rounded"
                    >
                        {src}
                    </span>
                ))}
            </div>
        </div>
    )
}