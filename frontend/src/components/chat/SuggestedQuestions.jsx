// Only shown when message list is empty
// Questions are defined here — ChatPage never needs to know what they are
const QUESTIONS = [
    'How does authentication work?',
    'What does the main service do?',
    'Where is the database configured?',
    'What are the main API endpoints?',
]

export default function SuggestedQuestions({ onSelect }) {
    return (
        <div className="flex flex-col items-center justify-center h-48 gap-4 text-center">
            <p className="text-gray-400 text-sm">
                Ask anything about this repository
            </p>
            <div className="flex flex-wrap gap-2 justify-center max-w-lg">
                {QUESTIONS.map(q => (
                    <button
                        key={q}
                        onClick={() => onSelect(q)}
                        className="text-xs bg-gray-800 hover:bg-gray-700 border border-gray-700 rounded-lg px-3 py-1.5 text-gray-300 hover:text-white transition-colors"
                    >
                        {q}
                    </button>
                ))}
            </div>
        </div>
    )
}