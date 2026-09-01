// Status badge used on repo cards and anywhere else status needs display
// Automatically picks color based on status string
const STATUS_STYLES = {
    READY:      'text-emerald-400 bg-emerald-500/10 border-emerald-500/20',
    PROCESSING: 'text-violet-400 bg-violet-500/10 border-violet-500/20',
    PENDING:    'text-amber-400 bg-amber-500/10 border-amber-500/20',
    FAILED:     'text-rose-400 bg-rose-500/10 border-rose-500/20'
}

export default function StatusBadge({ status }) {
    const style = STATUS_STYLES[status] || STATUS_STYLES.PENDING
    return (
        <span className={`text-[10px] font-bold uppercase tracking-wider border px-2 py-0.5 rounded-md shrink-0 flex items-center ${style}`}>
            {status === 'PROCESSING' && <span className="w-1.5 h-1.5 bg-violet-400 rounded-full mr-1.5 animate-pulse" />}
            {status === 'PENDING' && <span className="w-1.5 h-1.5 bg-amber-400 rounded-full mr-1.5 animate-pulse" />}
            {status}
        </span>
    )
}