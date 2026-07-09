// Status badge used on repo cards and anywhere else status needs display
// Automatically picks color based on status string
const STATUS_STYLES = {
    READY:      'text-green-400 bg-green-400/10 border-green-400/20',
    PROCESSING: 'text-yellow-400 bg-yellow-400/10 border-yellow-400/20',
    PENDING:    'text-gray-400 bg-gray-400/10 border-gray-400/20',
    FAILED:     'text-red-400 bg-red-400/10 border-red-400/20'
}

export default function StatusBadge({ status }) {
    const style = STATUS_STYLES[status] || STATUS_STYLES.PENDING
    return (
        <span className={`text-xs border px-2 py-0.5 rounded-full shrink-0 ${style}`}>
            {status}
        </span>
    )
}