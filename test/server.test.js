import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { createServer, hashToken, validateMembers } from '../src/server.js';
import { openai } from '../src/openai.js';

const token = 'test-token-with-at-least-32-characters';
const members = [{ id: 'alice', tokenHash: hashToken(token), vectorStoreId: 'vs_shared' }];
async function fixture(t, options = {}) {
  const calls = [];
  const server = createServer({ members, search: async (...args) => { calls.push(args); return [{ filename: 'Policy.txt', citation: 'S1', excerpts: 'Ask your manager.' }]; }, ...options });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  t.after(() => new Promise(resolve => { server.closeAllConnections(); server.close(resolve); }));
  const url = `http://127.0.0.1:${server.address().port}`;
  const post = (body, credential = token) => fetch(`${url}/search`, { method: 'POST', headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${credential}` }, body: JSON.stringify(body) });
  return { url, post, calls };
}

test('authenticated retrieval selects configured store and returns citation', async t => {
  const { post, calls } = await fixture(t);
  const result = await post({ query: ' Leave policy? ', limit: 2 });
  assert.equal(result.status, 200);
  assert.equal((await result.json()).sources[0].citation, 'S1');
  assert.deepEqual(calls, [['vs_shared', 'Leave policy?', 2]]);
});
test('invalid tokens never reach retrieval', async t => {
  const { post, calls } = await fixture(t);
  assert.equal((await post({ query: 'policy' }, 'wrong-token')).status, 401);
  assert.equal((await post({ query: 'policy' }, '')).status, 401);
  assert.equal(calls.length, 0);
});
test('rejects caller-selected stores and invalid queries', async t => {
  const { post, calls } = await fixture(t);
  for (const body of [null, [], { query: '' }, { query: 'x', vectorStoreId: 'vs_secret' }, { query: 'x', limit: 6 }, { query: 'x'.repeat(2001) }]) {
    assert.equal((await post(body)).status, 400);
  }
  assert.equal(calls.length, 0);
});
test('rate limits authenticated callers', async t => {
  const { post } = await fixture(t, { requestsPerMinute: 1 });
  assert.equal((await post({ query: 'x' })).status, 200);
  const response = await post({ query: 'x' });
  assert.equal(response.status, 429);
  assert.ok(response.headers.get('retry-after'));
});
test('upstream errors do not expose secrets', async t => {
  const { post } = await fixture(t, { search: async () => { throw new Error('secret upstream data'); } });
  const response = await post({ query: 'x' });
  assert.equal(response.status, 502);
  assert.doesNotMatch(await response.text(), /secret/);
});
test('empty retrieval remains empty and schema declares authentication', async t => {
  const { post, url } = await fixture(t, { search: async () => [] });
  assert.deepEqual(await (await post({ query: 'unknown' })).json(), { sources: [] });
  const schema = await (await fetch(`${url}/openapi.json`)).json();
  assert.deepEqual(schema.paths['/search'].post.security, [{ bearerAuth: [] }]);
});
test('configuration fails closed', () => {
  assert.throws(() => validateMembers([]));
  assert.throws(() => validateMembers([...members, ...members]));
  assert.throws(() => validateMembers([{ ...members[0], tokenHash: 'placeholder' }]));
});
test('live and documented schemas include the schemas object required by GPT Actions', async t => {
  const { url } = await fixture(t);
  const live = await (await fetch(`${url}/openapi.json`)).json();
  const documented = JSON.parse(await readFile(new URL('../docs/openapi.json', import.meta.url), 'utf8'));
  for (const spec of [live, documented]) {
    assert.ok(spec.components.schemas !== null && typeof spec.components.schemas === 'object');
    assert.equal(Array.isArray(spec.components.schemas), false);
    assert.equal(spec.components.securitySchemes.bearerAuth.scheme, 'bearer');
  }
  assert.deepEqual({ ...live, servers: documented.servers }, documented);
});
test('rejects malformed JSON and unsupported content type', async t => {
  const { url, calls } = await fixture(t);
  for (const [type, body] of [['application/json', '{'], ['text/plain', '{"query":"policy"}']]) {
    const response = await fetch(`${url}/search`, { method: 'POST', headers: { Authorization: `Bearer ${token}`, 'Content-Type': type }, body });
    assert.equal(response.status, 400);
  }
  assert.equal(calls.length, 0);
});
test('rejects oversized requests before retrieval', async t => {
  const { post, calls } = await fixture(t);
  assert.equal((await post({ query: 'x'.repeat(17000) })).status, 413);
  assert.equal(calls.length, 0);
});
test('public routes expose liveness and schema but no credentials', async t => {
  const { url } = await fixture(t);
  const health = await fetch(`${url}/health`);
  assert.deepEqual(await health.json(), { status: 'ok' });
  assert.equal(health.headers.get('cache-control'), 'no-store');
  const schema = await (await fetch(`${url}/openapi.json`)).text();
  assert.ok(!schema.includes(token) && !schema.includes(members[0].tokenHash));
  assert.equal((await fetch(`${url}/search`)).status, 404);
  assert.equal((await fetch(`${url}/unknown`)).status, 404);
});
test('separate credentials have isolated rate limits and configured stores', async t => {
  const bobToken = 'another-test-token-at-least-32-characters';
  const { post, calls } = await fixture(t, { requestsPerMinute: 1, members: [...members, { id: 'bob', tokenHash: hashToken(bobToken), vectorStoreId: 'vs_bob' }] });
  assert.equal((await post({ query: 'alice' })).status, 200);
  assert.equal((await post({ query: 'alice again' })).status, 429);
  assert.equal((await post({ query: 'bob' }, bobToken)).status, 200);
  assert.deepEqual(calls, [['vs_shared', 'alice', 5], ['vs_bob', 'bob', 5]]);
});
test('OpenAI transport sets auth and serializes search request', async () => {
  let call;
  const result = await openai('/vector_stores/vs_shared/search', { apiKey: 'test-key', body: { query: 'policy' }, fetchImpl: async (...args) => { call = args; return { ok: true, json: async () => ({ data: [] }) }; } });
  assert.deepEqual(result, { data: [] });
  assert.equal(call[0], 'https://api.openai.com/v1/vector_stores/vs_shared/search');
  assert.equal(call[1].headers.Authorization, 'Bearer test-key');
  assert.deepEqual(JSON.parse(call[1].body), { query: 'policy' });
});
