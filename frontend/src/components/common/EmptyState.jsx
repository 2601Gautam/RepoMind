// Generic empty state shown when a list has no items
// title: main message, description: secondary explanation
export default function EmptyState({ title, description, children }) {
    return (
        <div className="text-center py-16 space-y-3">
            <div className="w-12 h-12 rounded-full bg-gray-800 border border-gray-700 flex items-center justify-center mx-auto">
                <svg className="w-6 h-6 text-gray-500" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5}
                        d="M3 7v10a2 2 0 002 2h14a2 2 0 002-2V9a2 2 0 00-2-2h-6l-2-2H5a2 2 0 00-2 2z" />
                </svg>
            </div>
            <p className="text-gray-300 font-medium">{title}</p>
            {description && <p className="text-gray-500 text-sm">{description}</p>}
            {children}
        </div>
    )
}