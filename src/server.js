import http from 'node:http';
import { createHash, timingSafeEqual } from 'node:crypto';
import { readFile } from 'node:fs/promises';
import { pathToFileURL } from 'node:url';
import { schema } from './schema.js';
import { searchStore } from './openai.js';

export const hashToken = token => createHash('sha256').update(token).digest('hex');

export function validateMembers(members) {
  if (!Array.isArray(members) || members.length === 0) throw new Error('Configure at least one member');
  const ids = new Set(), hashes = new Set();
  for (const m of members) {
    if (!m.id || !/^[a-f0-9]{64}$/.test(m.tokenHash) || !/^vs_[a-zA-Z0-9]+$/.test(m.vectorStoreId) || ids.has(m.id) || hashes.has(m.tokenHash)) {
      throw new Error('Invalid or duplicate member configuration');
    }
    ids.add(m.id); hashes.add(m.tokenHash);
  }
  return members;
}

export function createServer({ members, baseUrl = 'http://localhost:3000', search = searchStore, requestsPerMinute = 30 }) {
  validateMembers(members);
  const buckets = new Map();
  const server = http.createServer(async (req, res) => {
    const send = (status, body) => {
      res.writeHead(status, { 'Content-Type': 'application/json', 'Cache-Control': 'no-store', 'X-Content-Type-Options': 'nosniff' });
      res.end(JSON.stringify(body));
    };
    if (req.method === 'GET' && req.url === '/health') return send(200, { status: 'ok' });
    if (req.method === 'GET' && req.url === '/openapi.json') return send(200, schema(baseUrl));
    if (req.method !== 'POST' || req.url !== '/search') return send(404, { error: 'Not found' });
    const match = /^Bearer ([\x21-\x7e]{32,256})$/i.exec(req.headers.authorization ?? '');
    const digest = Buffer.from(hashToken(match?.[1] ?? ''), 'hex');
    const member = match && members.find(m => timingSafeEqual(Buffer.from(m.tokenHash, 'hex'), digest));
    if (!member) return send(401, { error: 'Invalid credential' });
    const now = Date.now();
    let bucket = buckets.get(member.id);
    if (!bucket || now >= bucket.reset) { bucket = { count: 0, reset: now + 60000 }; buckets.set(member.id, bucket); }
    if (++bucket.count > requestsPerMinute) {
      res.setHeader('Retry-After', Math.ceil((bucket.reset - now) / 1000));
      return send(429, { error: 'Too many requests' });
    }
    if (!/^application\/json(?:;|$)/i.test(req.headers['content-type'] ?? '')) return send(400, { error: 'Use application/json' });
    let input;
    try {
      const chunks = []; let size = 0;
      for await (const chunk of req) {
        size += chunk.length;
        if (size > 16384) { send(413, { error: 'Request too large' }); return; }
        chunks.push(chunk);
      }
      input = JSON.parse(Buffer.concat(chunks).toString('utf8'));
    } catch { return send(400, { error: 'Invalid JSON' }); }
    if (!input || Array.isArray(input) || typeof input.query !== 'string' || !input.query.trim() || input.query.length > 2000 ||
        Object.keys(input).some(k => !['query', 'limit'].includes(k)) ||
        (input.limit !== undefined && (!Number.isInteger(input.limit) || input.limit < 1 || input.limit > 5))) {
      return send(400, { error: 'Provide query (1–2000 characters) and optional limit (1–5)' });
    }
    try { return send(200, { sources: await search(member.vectorStoreId, input.query.trim(), input.limit ?? 5) }); }
    catch { return send(502, { error: 'Knowledge search temporarily unavailable' }); }
  });
  server.requestTimeout = 30000;
  server.headersTimeout = 10000;
  return server;
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  if (!process.env.OPENAI_API_KEY) throw new Error('Set OPENAI_API_KEY in .env');
  const baseUrl = process.env.PUBLIC_BASE_URL ?? 'http://localhost:3000';
  const url = new URL(baseUrl);
  if (url.protocol !== 'https:' && !(url.protocol === 'http:' && ['localhost', '127.0.0.1'].includes(url.hostname))) throw new Error('Public deployments require HTTPS');
  const members = JSON.parse(await readFile(process.env.MEMBERS_FILE ?? 'config/members.json', 'utf8'));
  createServer({ members, baseUrl }).listen(Number(process.env.PORT ?? 3000), process.env.HOST ?? '127.0.0.1', () => console.log(`Company RAG listening; schema: ${baseUrl}/openapi.json`));
}
