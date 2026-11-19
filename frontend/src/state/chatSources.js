// Retrieved files are candidates, not citations. Also filter old saved messages that contain every retrieved file.
export function citedSourcePaths(answer, sources = []) {
    if (!answer || !Array.isArray(sources)) return []
    const paths = [...new Set(sources.filter(path => typeof path === 'string' && path))]
    const normalized = paths.map(path => path.replaceAll('\\', '/'))
    const filenames = normalized.map(path => path.split('/').pop())
    const text = answer.replaceAll('\\', '/')
    const mentions = name => {
        const escaped = name.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
        return new RegExp(`(?<![\\p{L}\\p{N}_.-])${escaped}(?![\\p{L}\\p{N}_-])`, 'u').test(text)
    }
    return paths.filter((path, index) => mentions(normalized[index]) ||
        (filenames.filter(name => name === filenames[index]).length === 1 && mentions(filenames[index])))
}
