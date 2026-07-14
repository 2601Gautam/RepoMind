import LoadingSpinner from './LoadingSpinner'

// Generic stat display card — dark glass card with an icon badge, a big number,
// and a label underneath. Used on Profile (and safe to reuse anywhere else
// a simple metric tile is needed).
//
// icon: small svg element (w-4 h-4) shown inside the accent badge
// label: caption text under the value
// value: the metric to display — shown as "—" if null/undefined
// loading: shows a small spinner instead of the value while true
export default function StatCard({ label, value, loading, icon }) {
    return (
        <div className="bg-[#0d0d12]/50 border border-white/[0.06] rounded-2xl p-5 text-center backdrop-blur-md transition-all duration-300 hover:border-white/[0.12] hover:bg-[#111115]">
            {icon && (
                <div className="w-8 h-8 rounded-lg bg-violet-500/10 border border-violet-500/20 text-violet-400 flex items-center justify-center mx-auto mb-2.5">
                    {icon}
                </div>
            )}
            {loading ? (
                <div className="flex justify-center py-1.5">
                    <LoadingSpinner size="sm" />
                </div>
            ) : (
                <p className="text-2xl font-extrabold text-white tracking-tight">{value ?? '—'}</p>
            )}
            <p className="text-[12px] text-neutral-500 mt-1">{label}</p>
        </div>
    )
}
