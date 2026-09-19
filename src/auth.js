import { createHash, timingSafeEqual, randomBytes, createCipheriv, createDecipheriv, hkdfSync } from 'node:crypto';
import { InvalidClientMetadataError, InvalidGrantError, InvalidScopeError, InvalidTargetError, InvalidTokenError } from '@modelcontextprotocol/sdk/server/auth/errors.js';

export const hashToken = token => createHash('sha256').update(token).digest('hex');
export function validateMembers(members) {
  if (!Array.isArray(members) || !members.length) throw new Error('Configure at least one member');
  const ids = new Set(), hashes = new Set();
  for (const m of members) {
    if (!m || typeof m.id !== 'string' || !m.id.trim() || !/^[a-f0-9]{64}$/.test(m.tokenHash) || !/^vs_[a-zA-Z0-9]+$/.test(m.vectorStoreId) || ids.has(m.id) || hashes.has(m.tokenHash)) throw new Error('Invalid or duplicate member configuration');
    ids.add(m.id); hashes.add(m.tokenHash);
  }
  return members;
}
export function findMember(members, token) {
  if (typeof token !== 'string' || !/^[\x21-\x7e]{32,256}$/.test(token)) return undefined;
  const digest = Buffer.from(hashToken(token), 'hex');
  return members.find(m => timingSafeEqual(Buffer.from(m.tokenHash, 'hex'), digest));
}
const escapeHtml = value => String(value).replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
export function allowedRedirect(value) {
  try {
    const u = new URL(value);
    if (u.username || u.password || u.hash || u.search) return false;
    return (u.origin === 'https://chatgpt.com' && (u.pathname === '/connector_platform_oauth_redirect' || /^\/connector\/oauth\/[a-zA-Z0-9_-]+$/.test(u.pathname))) ||
      (u.protocol === 'http:' && ['localhost', '127.0.0.1', '[::1]'].includes(u.hostname) && u.pathname === '/callback');
  } catch { return false; }
}

// Encrypted, purpose-bound credentials survive restarts. Pending browser flows and
// single-use authorization codes deliberately expire on restart.
export class NexaAuth {
  constructor({ members, baseUrl, secret, now = Date.now }) {
    if (typeof secret !== 'string' || secret.length < 32) throw new Error('OAuth signing secret must be at least 32 characters');
    this.members = validateMembers(members); this.baseUrl = baseUrl; this.resource = `${baseUrl}/mcp`; this.now = now;
    this.key = Buffer.from(hkdfSync('sha256', secret, 'nexarag-oauth-v1', baseUrl, 32));
    this.pending = new Map(); this.codes = new Map();
    this.clientsStore = {
      registerClient: async metadata => {
        if (!metadata.redirect_uris?.length || metadata.redirect_uris.length > 5 || metadata.redirect_uris.some(u => !allowedRedirect(u))) throw new InvalidClientMetadataError('Use the ChatGPT callback or a local /callback URL');
        if (!['none', 'client_secret_post', undefined].includes(metadata.token_endpoint_auth_method)) throw new InvalidClientMetadataError('Unsupported client authentication');
        const client = { redirect_uris: metadata.redirect_uris, token_endpoint_auth_method: metadata.token_endpoint_auth_method ?? 'client_secret_post', client_secret: metadata.client_secret, client_secret_expires_at: metadata.client_secret_expires_at, grant_types: ['authorization_code'], response_types: ['code'], scope: 'knowledge:read', client_name: 'NexaRag client' };
        return { ...client, client_id: this.seal('client', client, 365 * 86400) };
      },
      getClient: async id => { try { return { ...this.open('client', id), client_id: id }; } catch { return undefined; } },
    };
  }
  seal(kind, data, seconds) {
    const iv = randomBytes(12), cipher = createCipheriv('aes-256-gcm', this.key, iv);
    cipher.setAAD(Buffer.from(kind));
    const body = Buffer.concat([cipher.update(JSON.stringify({ data, exp: Math.floor(this.now()/1000) + seconds })), cipher.final()]);
    return Buffer.concat([iv, cipher.getAuthTag(), body]).toString('base64url');
  }
  open(kind, value) {
    if (typeof value !== 'string' || value.length > 12000) throw new InvalidTokenError('Invalid credential');
    try {
      const raw = Buffer.from(value, 'base64url'), decipher = createDecipheriv('aes-256-gcm', this.key, raw.subarray(0,12));
      decipher.setAAD(Buffer.from(kind)); decipher.setAuthTag(raw.subarray(12,28));
      const result = JSON.parse(Buffer.concat([decipher.update(raw.subarray(28)), decipher.final()]).toString());
      if (result.exp <= Math.floor(this.now()/1000)) throw new Error();
      return result.data;
    } catch { throw new InvalidTokenError('Invalid or expired credential'); }
  }
  cleanup() {
    for (const map of [this.pending, this.codes]) for (const [key, value] of map) if (value.expires <= this.now()) map.delete(key);
    if (this.pending.size + this.codes.size >= 1000) throw new InvalidGrantError('Too many pending sign-ins');
  }
  async authorize(client, params, res) {
    this.cleanup();
    if (params.resource?.href !== this.resource) throw new InvalidTargetError('Incorrect resource');
    if (params.scopes?.some(s => s !== 'knowledge:read')) throw new InvalidScopeError('Only knowledge:read is supported');
    if (!/^[A-Za-z0-9_-]{43}$/.test(params.codeChallenge)) throw new InvalidGrantError('Invalid PKCE challenge');
    const flow = randomBytes(32).toString('base64url'), csrf = randomBytes(32).toString('base64url');
    this.pending.set(flow, { client, params, csrf, expires: this.now()+300000 });
    res.cookie('nexarag_flow', csrf, { httpOnly: true, secure: this.baseUrl.startsWith('https:'), sameSite: 'lax', path: '/oauth/consent', maxAge: 300000 });
    const nonce = randomBytes(16).toString('base64');
    // no-referrer makes native form POSTs send Origin: null. Preserve the
    // same-origin security check without leaking authorization paths or queries.
    res.set('Referrer-Policy', 'strict-origin');
    // Chromium applies form-action to the redirect after POST as well. The SDK
    // validated this URI against the registered client's callback allowlist.
    res.set('Content-Security-Policy', `default-src 'none'; style-src 'nonce-${nonce}'; form-action 'self' ${params.redirectUri}; frame-ancestors 'none'; base-uri 'none'`);
    res.type('html').send(`<!doctype html><html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width"><title>Connect NexaRag</title><style nonce="${nonce}">body{font:17px system-ui,sans-serif;background:#edf3f8;color:#173047;margin:0;padding:60px 24px}main{max-width:560px;margin:auto;padding:36px;background:white;border:1px solid #d2dee8;border-radius:16px}h1{font-size:32px;margin-top:0}p{line-height:1.55}label{display:block;font-weight:600}input[type=password]{display:block;width:100%;box-sizing:border-box;padding:12px;margin-top:10px;border:1px solid #8296a8;border-radius:6px;font:inherit}button{background:#185b82;color:white;border:0;padding:13px 22px;border-radius:7px;font:inherit;cursor:pointer}</style><body><main><h1>Connect NexaRag</h1><p>Allow <strong>${escapeHtml(new URL(params.redirectUri).origin)}</strong> to search your approved company documents.</p><p>Permission: read document excerpts. No uploads or edits.</p><form method="post" action="/oauth/consent"><input type="hidden" name="flow" value="${flow}"><label>NexaRag access code <input type="password" name="credential" required autocomplete="off" maxlength="256"></label><p>Use the private code from your administrator, never your OpenAI API key.</p><button type="submit">Connect and allow search</button></form><p>Close this window to cancel. Authorization expires in one hour; connect again when prompted.</p></main></body></html>`);
  }
  consent(req, res) {
    this.cleanup();
    const flow = this.pending.get(req.body?.flow);
    const cookie = /(?:^|;\s*)nexarag_flow=([^;]+)/.exec(req.headers.cookie ?? '')?.[1];
    if (!flow) return res.status(400).send('This sign-in page expired, was already used, or the service restarted. Close this tab and start Connect again in ChatGPT or Codex.');
    if (cookie !== flow.csrf) return res.status(400).send('The sign-in cookie is missing or does not match. Allow cookies for NexaRag, close other sign-in tabs, and start Connect again in the same browser.');
    if (req.headers.origin && req.headers.origin !== this.baseUrl) return res.status(400).send('The browser could not verify this sign-in page. Close this tab and start Connect again to load a fresh page.');
    const member = findMember(this.members, req.body.credential);
    if (!member) return res.status(401).send('Invalid access code. Go back and try again.');
    this.pending.delete(req.body.flow);
    const code = randomBytes(32).toString('base64url');
    this.codes.set(code, { ...flow, memberId: member.id, memberHash: member.tokenHash, expires: this.now()+60000 });
    const redirect = new URL(flow.params.redirectUri); redirect.searchParams.set('code',code);
    if (flow.params.state) redirect.searchParams.set('state',flow.params.state);
    res.clearCookie('nexarag_flow', { path: '/oauth/consent' }); res.redirect(303,redirect.href);
  }
  code(client, code) {
    this.cleanup(); const item = this.codes.get(code);
    if (!item || item.client.client_id !== client.client_id) throw new InvalidGrantError('Invalid authorization code');
    return item;
  }
  async challengeForAuthorizationCode(client, code) { return this.code(client,code).params.codeChallenge; }
  async exchangeAuthorizationCode(client, code, verifier, redirectUri, resource) {
    const item = this.code(client,code);
    if (redirectUri !== item.params.redirectUri || resource?.href !== this.resource) throw new InvalidGrantError('Redirect or resource mismatch');
    this.codes.delete(code);
    const expiresAt = Math.floor(this.now()/1000)+3600;
    const access_token = this.seal('access', { memberId:item.memberId, memberHash:item.memberHash, clientId:client.client_id, resource:this.resource, expiresAt },3600);
    return { access_token, token_type:'Bearer', expires_in:3600, scope:'knowledge:read' };
  }
  async exchangeRefreshToken() { throw new InvalidGrantError('Reconnect to renew authorization'); }
  async verifyAccessToken(token) {
    const data = this.open('access',token);
    const member = this.members.find(m => m.id === data.memberId && m.tokenHash === data.memberHash);
    if (!member || data.resource !== this.resource) throw new InvalidTokenError('Access revoked');
    return { token, clientId:data.clientId, scopes:['knowledge:read'], expiresAt:data.expiresAt, resource:new URL(this.resource), extra:{ memberId:member.id, vectorStoreId:member.vectorStoreId } };
  }
}
