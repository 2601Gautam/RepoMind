import test from 'node:test'
import assert from 'node:assert/strict'
import { readSseStream } from '../src/api/readSseStream.js'

const frame = event => 'data:' + JSON.stringify(event) + '\r\n\r\n'
const done = frame({type:'done'})
const token = frame({type:'token',content:'एक\t🚀'})
function bytes(text, pieces=false) {
    const data = new TextEncoder().encode(text)
    return new ReadableStream({start(c) {
        if (pieces) for (const byte of data) c.enqueue(Uint8Array.of(byte))
        else c.enqueue(data)
        c.close()
    }})
}
test('a split inside each UTF-8 character and each JSON frame round-trips', async () => {
    let got=[]
    await readSseStream(bytes(': heartbeat\r\n'+token+done,true),e=>got.push(e))
    assert.deepEqual(got,[{type:'token',content:'एक\t🚀'},{type:'done'}])
})
test('the first terminal error stops dispatch and preserves its message', async () => {
    let got=[]
    await readSseStream(bytes(frame({type:'error',content:'timeout'})+token+done),e=>got.push(e))
    assert.deepEqual(got,[{type:'error',content:'timeout'}])
})
test('premature EOF surfaces an interrupted response', async () => {
    await assert.rejects(readSseStream(bytes(token),()=>{}),/ended|incomplete/i)
})
test('malformed JSON is reported even when a later done is present', async () => {
    await assert.rejects(readSseStream(bytes('data:{invalid}\n\n'+done),()=>{}))
})
test('a UI callback failure is surfaced', async () => {
    await assert.rejects(readSseStream(bytes(token+done),()=>{throw Error('render failed')}),/render failed/)
})
test('an empty stream reports the missing outcome', async () => {
    await assert.rejects(readSseStream(bytes(''),()=>{}))
})

