import test from 'node:test';
import assert from 'node:assert/strict';
import { loadConfig } from '../src/config.js';
import { validateMembers } from '../src/server.js';

const members = [{ id: 'demo', tokenHash: 'a'.repeat(64), vectorStoreId: 'vs_demo' }];
const env = { OPENAI_API_KEY: 'test-only', MEMBERS_JSON: JSON.stringify(members) };

test('Render configuration uses platform HTTPS URL and injected members', async () => {
  const config = await loadConfig({ ...env, RENDER_EXTERNAL_URL: 'https://nexarag-test.onrender.com', PORT: '10000', HOST: '0.0.0.0' });
  assert.deepEqual(config, { baseUrl: 'https://nexarag-test.onrender.com', members, port: 10000, host: '0.0.0.0' });
});
test('custom public domain overrides Render URL', async () => {
  const config = await loadConfig({ ...env, PUBLIC_BASE_URL: 'https://knowledge.example.com/', RENDER_EXTERNAL_URL: 'https://nexarag-test.onrender.com' });
  assert.equal(config.baseUrl, 'https://knowledge.example.com');
});
test('configuration errors never echo secret JSON', async () => {
  await assert.rejects(loadConfig({ ...env, MEMBERS_JSON: 'sensitive-value' }), { message: 'Provide valid member JSON in MEMBERS_JSON or MEMBERS_FILE' });
  await assert.rejects(loadConfig({ ...env, MEMBERS_JSON: '' }), /valid member JSON/);
});
test('rejects insecure origins, embedded credentials, paths, and invalid ports', async () => {
  for (const origin of ['http://public.example.com', 'https://user:secret@example.com', 'https://example.com/path', 'https://example.com/?token=x', 'https://example.com/#x']) {
    await assert.rejects(loadConfig({ ...env, PUBLIC_BASE_URL: origin }));
  }
  for (const port of ['0', '70000', 'NaN', '3.5']) await assert.rejects(loadConfig({ ...env, PORT: port }), /PORT/);
  await assert.rejects(loadConfig({ ...env, OPENAI_API_KEY: '' }), /OPENAI_API_KEY/);
});
test('rejects null and non-string member identities', () => {
  assert.throws(() => validateMembers([null]), /Invalid/);
  assert.throws(() => validateMembers([{ ...members[0], id: 123 }]), /Invalid/);
});
