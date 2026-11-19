import test from 'node:test'
import assert from 'node:assert/strict'
import { citedSourcePaths } from '../src/state/chatSources.js'

test('scope redirects and missing-context replies do not show retrieved files', () => {
    const files = ['src/Product.java', 'README.md', 'web/Product.ts']
    for (const answer of ["I'm here to help with questions about this codebase—its architecture, setup, testing, debugging, or improvements.",
        'This is not covered in the provided code context.', 'Thanks for the kind words!']) {
        assert.deepEqual(citedSourcePaths(answer, files), [])
    }
})

test('technical answers show only explicitly referenced candidate files', () => {
    assert.deepEqual(citedSourcePaths('`Product.java` defines the entity. See **README.md**.', ['src/Product.java', 'README.md', 'Product.ts']), ['src/Product.java', 'README.md'])
    assert.deepEqual(citedSourcePaths('OtherProduct.java is unrelated.', ['Product.java']), [])
    assert.deepEqual(citedSourcePaths('`a/index.ts` handles this.', ['a/index.ts', 'b/index.ts']), ['a/index.ts'])
    assert.deepEqual(citedSourcePaths('See index.ts', ['a/index.ts', 'b/index.ts']), [])
    assert.deepEqual(citedSourcePaths('`src/Product.java`', ['src\\Product.java']), ['src\\Product.java'])
})
