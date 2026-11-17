export async function readSseStream(body, onEvent) {
    const reader = body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''

    try {
        while (true) {
            const { done, value } = await reader.read()
            if (done) throw new Error('The response stream ended before completion.')

            // Decode incoming bytes and append to buffer
            // { stream: true } handles multi-byte characters split across chunks
            buffer += decoder.decode(value, { stream: true })

            // Split buffer on newlines — SSE events end with \n
            const lines = buffer.split('\n')

            // Last element may be an incomplete line — keep it in buffer
            buffer = lines.pop() || ''

            for (const line of lines) {
                const trimmed = line.trim()

                // SSE data lines start with "data:"
                // Empty lines are event separators — skip them
                if (!trimmed || !trimmed.startsWith('data:')) continue

                const jsonStr = trimmed.slice(5).trim()
                if (!jsonStr) continue

                const event = JSON.parse(jsonStr)
                onEvent(event)
                if (event.type === 'done' || event.type === 'error') {
                    return
                }
            }
        }
    } finally {
        // Always release the lock even if an error occurred
        try {
            await reader.cancel()
        } finally {
            reader.releaseLock()
        }
    }
}

