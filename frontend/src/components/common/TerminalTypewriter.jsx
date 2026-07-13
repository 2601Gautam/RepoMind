import { useState, useEffect } from 'react'

export default function TerminalTypewriter({ title = "terminal", steps = [], onComplete }) {
    const [visibleLines, setVisibleLines] = useState([])
    const [currentLineText, setCurrentLineText] = useState('')
    const [currentStepIdx, setCurrentStepIdx] = useState(0)
    const [charIdx, setCharIdx] = useState(0)

    const stepsJSON = JSON.stringify(steps)

    useEffect(() => {
        if (currentStepIdx >= steps.length) {
            if (onComplete) onComplete()
            return
        }

        const step = steps[currentStepIdx]
        const isCommand = step.type === 'command'

        if (isCommand) {
            // Type out character by character
            if (charIdx < step.text.length) {
                const timeout = setTimeout(() => {
                    setCurrentLineText(prev => prev + step.text[charIdx])
                    setCharIdx(prev => prev + 1)
                }, 40) // Character typing speed
                return () => clearTimeout(timeout)
            } else {
                // Done typing this command line, add to visibleLines and proceed
                setVisibleLines(prev => [...prev, { text: step.text, type: 'command' }])
                setCurrentLineText('')
                setCharIdx(0)
                const timeout = setTimeout(() => {
                    setCurrentStepIdx(prev => prev + 1)
                }, 300)
                return () => clearTimeout(timeout)
            }
        } else {
            // Immediate line display or small delay for system messages
            const delay = step.delay || 500
            const timeout = setTimeout(() => {
                setVisibleLines(prev => [...prev, { text: step.text, type: step.type }])
                setCurrentStepIdx(prev => prev + 1)
            }, delay)
            return () => clearTimeout(timeout)
        }
    }, [currentStepIdx, charIdx, stepsJSON])

    // Get color classes based on step type
    const getLineStyle = (type) => {
        switch (type) {
            case 'command':
                return 'text-neutral-300'
            case 'info':
                return 'text-violet-300'
            case 'success':
                return 'text-emerald-400'
            case 'warning':
                return 'text-yellow-400'
            case 'error':
                return 'text-rose-400'
            default:
                return 'text-neutral-400'
        }
    }

    return (
        <div className="w-full rounded-xl overflow-hidden border border-white/[0.07] bg-[#0d0d0d] shadow-[0_24px_80px_rgba(0,0,0,0.5)] font-mono text-[13px] sm:text-[14px] leading-relaxed text-left animate-fade-up">
            {/* Terminal Header */}
            <div className="flex items-center gap-2 px-4 py-3 bg-white/[0.03] border-b border-white/[0.06] select-none">
                <span className="w-2.5 h-2.5 rounded-full bg-[#FF5F57]" />
                <span className="w-2.5 h-2.5 rounded-full bg-[#FEBC2E]" />
                <span className="w-2.5 h-2.5 rounded-full bg-[#28C840]" />
                <span className="ml-2 text-[11px] text-[#4b5563]">{title}</span>
                <span className="ml-auto text-[11px] text-gray-500 flex items-center gap-1.5">
                    <span className="w-1.5 h-1.5 rounded-full bg-violet-400 animate-pulse"></span>
                    Active process
                </span>
            </div>

            {/* Terminal Body */}
            <div className="p-6 space-y-2 min-h-[160px] max-h-[300px] overflow-y-auto">
                {visibleLines.map((line, i) => (
                    <div key={i} className={getLineStyle(line.type)}>
                        {line.type === 'command' && <span className="text-[#4b5563] mr-2">$</span>}
                        {line.text}
                    </div>
                ))}
                
                {/* Currently typing line */}
                {currentStepIdx < steps.length && (
                    <div className={getLineStyle(steps[currentStepIdx].type)}>
                        {steps[currentStepIdx].type === 'command' && <span className="text-[#4b5563] mr-2">$</span>}
                        {currentLineText}
                        <span className="inline-block w-1.5 h-4 bg-violet-400/80 ml-0.5 animate-pulse align-middle" />
                    </div>
                )}

                {/* Final pulsing cursor if done and no more steps */}
                {currentStepIdx >= steps.length && (
                    <div className="flex items-center gap-1.5 text-neutral-500 text-xs mt-4 border-t border-white/[0.03] pt-3 animate-fade-up">
                        <span className="w-1.5 h-1.5 rounded-full bg-emerald-400 animate-pulse" />
                        <span>Analysis stream initialized successfully.</span>
                    </div>
                )}
            </div>
        </div>
    )
}
