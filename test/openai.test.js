import test from 'node:test';
import assert from 'node:assert/strict';
import { openai } from '../src/openai.js';
import { execFileSync } from 'node:child_process';
import { hashToken } from '../src/server.js';

test('missing API key fails before network access', async () => {
  await assert.rejects(openai('/files', { apiKey: '', fetchImpl: () => assert.fail('Network must not be called') }), /OPENAI_API_KEY/);
});
test('upstream HTTP errors exclude response body', async () => {
  await assert.rejects(openai('/files', { apiKey: 'test', fetchImpl: async () => ({ ok: false, status: 403, json: () => assert.fail('Do not read sensitive errors') }) }), /^Error: OpenAI request failed \(403\)$/);
});
test('file uploads preserve multipart body and allow generated boundary', async () => {
  const body = new FormData();
  body.set('purpose', 'assistants');
  body.set('file', new Blob(['sample']), 'sample.txt');
  await openai('/files', { apiKey: 'test', body, fetchImpl: async (url, options) => {
    assert.equal(options.body, body);
    assert.equal(options.headers['Content-Type'], undefined);
    assert.ok(options.signal instanceof AbortSignal);
    return { ok: true, json: async () => ({ id: 'file-sample' }) };
  } });
});
test('GET requests omit the body', async () => {
  await openai('/files/file-sample', { method: 'GET', apiKey: 'test', fetchImpl: async (url, options) => {
    assert.equal(options.method, 'GET');
    assert.equal(options.body, undefined);
    return { ok: true, json: async () => ({ id: 'file-sample' }) };
  } });
});
test('admin token command generates unique matching hashes without credentials', () => {
  const run = () => JSON.parse(execFileSync(process.execPath, ['src/admin.js', 'token'], { encoding: 'utf8', env: { ...process.env, OPENAI_API_KEY: '' } }));
  const first = run(), second = run();
  assert.notEqual(first.token, second.token);
  assert.equal(first.token.length, 43);
  assert.equal(first.tokenHash, hashToken(first.token));
});
