import { analyzeError } from '../api/client'
import ReactMarkdown from 'react-markdown'
import { useState } from 'react'

export default function DebugPage({ repo, onBack }) {
    const [errorText, setErrorText] = useState('')
    const [context, setContext] = useState('')
    const [result, setResult] = useState(null)
    const [loading, setLoading] = useState(false)
    const [error, setError] = useState('')

    async function handleAnalyze() {
        if(!errorText.trim()) {
            setError('Please enter an error message to analyze.');
            return;
        }
        setLoading(true);
        setError('');
        setResult(null);
        try {
            const res = await analyzeError(errorText, repo?.id || null, context || null);
            setResult(res);
        } catch (err) {
            setError(err.message);
        } finally {
            setLoading(false);
        }
    }

    return (
        <div className = "max-w-3xl mx-auto py-8 space-y-6">
            <div className="flex items-center gap-3">
                <button
                    onClick={onBack}
                    className="text-sm text-gray-400 hover:text-white transition-colors"
                >
                    &larr; Back
                </button>
            <div>
            <h2 className = "text-xl font-semibold text-white">Debug Assistant</h2>
            {repo && (
                <p className="text-xs text-gray-500">
                    Using context from {repo.repoName}
                </p>
                )}
            </div>
        </div>

{/*          Error input*/}
        <div className="space-y-3">
            <label className="block text-sm text-gray-400">
                Paste your error message or stack trace
            </label>
            <textarea
                value={errorText}
                onChange={(e) => setErrorText(e.target.value)}
                placeholder= {`Example:\nNullPointerException at com.example.AuthService.login(AuthService.java:47)\n\nor paste your full stack trace here`}
                className="w-full bg-gray-900 border border-gray-700 rounded-lg px-4 py-3 text-white text-sm font-mono placeholder-gray-600 focus:outline-none focus:border-blue-500 transition-colors resize-none"
                rows={8}
            />

        <div>
            <label className="block text-sm text-gray-400 mb-1.5">
                Additional context (optional)
            </label>
            <textarea
                value={context}
                onChange={(e) => setContext(e.target.value)}
                placeholder="e.g. This happens when user tries to login with Google"
                className="w-full bg-gray-900 border border-gray-700 rounded-lg px-4 py-2.5 text-white text-sm placeholder-gray-500 focus:outline-none focus:border-blue-500 transition-colors"
                rows={4}
            />
        </div>

        <button
            onClick = {handleAnalyze}
            disabled={loading || !errorText.trim()}
            className= "w-full bg-blue-600 hover:bg-blue-500 disabled:opacity-50 text-white font-medium py-2.5 rounded-lg transition-colors"
        >
         {loading ? 'Analyzing...' : 'Analyze Error'}
        </button>

        {error && <p className = "text-red-400 text-sm">{error}</p>}
        </div>

        {/*Loading */}
        {loading && (
            <div className="text-center py-6 space-y-2">
                <div className="w-7 h-7 border-2 border-blue-500 border-t-transparent rounded-full animate-spin mx-auto " />
                <p className="text-gray-400 text-sm">Analyzing your error...</p>
            </div>
        )}

        {/* Result */}
        {result && !loading && (
            <div className="space-y-4">
            {/* Root cause - most prominent*/}
            <div className="bg-red-950/30 border border-red-800/40 rounded-lg p-4">
            <p className = "text-xs text-red-400 font-medium mb-1 uppercase tracking-wider">
                Root Cause
            </p>
            <p className="text-white text-sm">{result.rootCause}</p>
           </div>

           {/* Explanation */}
           <div className="bg-gray-900 border border-gray-700 rounded-lg p-4">
               <p className="text-xs text-gray-400 font-medium mb-2 uppercase tracking-wider">
                      Explanation
                  </p>
                  <div className="prose prose-invert prose-sm max-w-none">
                       <ReactMarkdown>{result.explanation}</ReactMarkdown>
                  </div>
           </div>

              {/* Suggested Fix */}
              <div className="bg-green-950/20 border border-green-800/30 rounded-lg p-4">
                    <p className="text-xs text-green-400 font-medium mb-2 uppercase tracking-wider">
                        Suggested Fix
                    </p>
                    <div className="prose prose-invert prose-sm max-w-none">
                        <ReactMarkdown>{result.suggestedFix}</ReactMarkdown>
                    </div>
              </div>

            {/*  Prevention tip  */}
            {result.preventionTip && (
                <div className="bg-blue-950/20 border border-blue-800/30 rounded-lg p-4">
                    <p className="text-xs text-blue-400 font-medium mb-2 uppercase tracking-wider">
                        Prevention
                    </p>
                    <div className="text-gray-300 text-sm">
                        {result.preventionTip}
                    </div>
                </div>
            )}

            {/*  Relevant files*/}
            {result.relevantFiles?.length > 0 && (
                <div className="bg-gray-900 border border-gray-700 rounded-lg p-4">
                    <p className="text-xs text-gray-400 font-medium mb-2 uppercase tracking-wider">
                        Relevant Files
                    </p>
                    <div className="flex flex-wrap gap-1">
                        {result.relevantFiles.map((file, index) => (
                            <span
                                key={index}
                                className="text-xs font-mono text-blue-400 bg-blue-400/10 border border-blue-400/20 px-2 py-0.5 rounded"
                            >
                                {file}
                            </span>
                        ))}
                    </div>
                </div>
        )}
</div>
)}
</div>
);
}