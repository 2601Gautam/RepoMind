import { useEffect, useRef } from 'react'
import { useActivity } from '../context/ActivityContext'

// Cancels the browser subscription on route changes. Durable saved results are
// recovered separately; cancellation never silently starts another AI request.
export function useAiOperation(kind, repoId) {
    const { begin, refresh, tasks } = useActivity()
    const controller = useRef(null)
    useEffect(() => () => { controller.current?.abort(); controller.current = null }, [repoId])
    const runningElsewhere = tasks.some(t => t.kind === kind && t.repoId === repoId && t.status === 'RUNNING')
    function start() {
        if (controller.current || runningElsewhere) return null
        const current = new AbortController()
        controller.current = current
        const requestId = begin(kind,repoId)
        return {
            options: { requestId, signal: current.signal },
            live: () => controller.current === current && !current.signal.aborted,
            finish: () => {
                if (controller.current === current) controller.current = null
                refresh()
            }
        }
    }
    return { start, runningElsewhere }
}

