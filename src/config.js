import { readFile } from 'node:fs/promises';

export async function loadConfig(env = process.env) {
  if (!env.OPENAI_API_KEY?.trim()) throw new Error('Set OPENAI_API_KEY');
  const baseUrl = env.PUBLIC_BASE_URL || env.RENDER_EXTERNAL_URL || 'http://localhost:3000';
  const url = new URL(baseUrl);
  if (url.username || url.password || url.search || url.hash || url.pathname !== '/') throw new Error('PUBLIC_BASE_URL must be an origin without credentials, path, query, or fragment');
  if (url.protocol !== 'https:' && !(url.protocol === 'http:' && ['localhost', '127.0.0.1'].includes(url.hostname))) throw new Error('Public deployments require HTTPS');
  const port = Number(env.PORT || 3000);
  if (!Number.isInteger(port) || port < 1 || port > 65535) throw new Error('PORT must be an integer from 1 to 65535');
  let members;
  try {
    members = JSON.parse(env.MEMBERS_JSON !== undefined ? env.MEMBERS_JSON : await readFile(env.MEMBERS_FILE || 'config/members.json', 'utf8'));
  } catch {
    throw new Error('Provide valid member JSON in MEMBERS_JSON or MEMBERS_FILE');
  }
  return { baseUrl: url.origin, members, port, host: env.HOST || '127.0.0.1' };
}
