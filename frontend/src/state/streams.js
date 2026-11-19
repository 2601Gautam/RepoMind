import { streamChat, streamDebug } from '../api/client'
import { createChatSessions, createDebugRuns } from './liveStreams.js'

// One instance each for the whole browser session. Keys are "<userId>:<repoId>", so a second account on the same tab
// never sees the first account's conversation or analysis.
export const chatSessions = createChatSessions({ stream: streamChat })
export const debugRuns = createDebugRuns({ stream: streamDebug })
