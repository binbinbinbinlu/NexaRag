import { readFile } from 'node:fs/promises';
import { randomBytes, createHash } from 'node:crypto';
import { Client } from '@modelcontextprotocol/sdk/client/index.js';
import { StreamableHTTPClientTransport } from '@modelcontextprotocol/sdk/client/streamableHttp.js';

const base = process.argv[2] || 'https://nexarag-fypg.onrender.com';
const codeFile = process.argv[3];
const query = process.argv[4] || 'How far ahead should I request annual leave?';
const expectedFilename = process.argv[5];
const json = async r => { if (!r.ok) throw new Error(`Request failed (${r.status})`); return r.json(); };
try {
  const origin = new URL(base);
  if (origin.origin !== base || (origin.protocol !== 'https:' && !['localhost','127.0.0.1'].includes(origin.hostname))) throw new Error('Provide an HTTPS origin or localhost');
  if (!codeFile) throw new Error('Usage: node scripts/smoke-mcp.js HTTPS_ORIGIN PRIVATE_ACCESS_CODE_FILE');
  const health = await json(await fetch(base+'/health'));
  if (health.integration !== 'mcp') throw new Error('MCP version is not deployed');
  const denied = await fetch(base+'/mcp',{ method:'POST',headers:{'Content-Type':'application/json'},body:'{}' });
  if (denied.status !== 401) throw new Error('Unauthenticated request was not rejected');
  const client = await json(await fetch(base+'/register',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({redirect_uris:['http://127.0.0.1:34567/callback'],token_endpoint_auth_method:'none',grant_types:['authorization_code'],response_types:['code']})}));
  const verifier = randomBytes(32).toString('base64url');
  const params = new URLSearchParams({client_id:client.client_id,redirect_uri:client.redirect_uris[0],response_type:'code',code_challenge:createHash('sha256').update(verifier).digest('base64url'),code_challenge_method:'S256',scope:'knowledge:read',resource:base+'/mcp',state:randomBytes(16).toString('hex')});
  const page = await fetch(base+'/authorize?'+params,{redirect:'manual'});
  const html = await page.text(), flow = /name="flow" value="([^"]+)"/.exec(html)?.[1];
  if (!flow) throw new Error('Authorization page missing');
  const credential = (await readFile(codeFile,'utf8')).trim();
  const consent = await fetch(base+'/oauth/consent',{method:'POST',redirect:'manual',headers:{'Content-Type':'application/x-www-form-urlencoded',Origin:base,Cookie:page.headers.get('set-cookie').split(';')[0]},body:new URLSearchParams({flow,credential})});
  if (consent.status !== 303) throw new Error(`Consent failed (${consent.status})`);
  const redirect = new URL(consent.headers.get('location'));
  if (redirect.searchParams.get('state') !== params.get('state')) throw new Error('State mismatch');
  const tokens = await json(await fetch(base+'/token',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:new URLSearchParams({grant_type:'authorization_code',client_id:client.client_id,code:redirect.searchParams.get('code'),code_verifier:verifier,redirect_uri:client.redirect_uris[0],resource:base+'/mcp'})}));
  const sdk = new Client({name:'nexarag-smoke',version:'2.0.0'});
  try {
    await sdk.connect(new StreamableHTTPClientTransport(new URL(base+'/mcp'),{requestInit:{headers:{Authorization:`Bearer ${tokens.access_token}`}}}));
    const tools = await sdk.listTools();
    if (!tools.tools.some(t=>t.name==='search_company_knowledge')) throw new Error('Search tool missing');
    const result = await sdk.callTool({name:'search_company_knowledge',arguments:{query,limit:3}});
    if (result.isError || !result.structuredContent?.sources?.length) throw new Error('Retrieval did not return evidence');
    if (expectedFilename && !result.structuredContent.sources.some(s=>s.filename===expectedFilename)) throw new Error('Expected source file was not retrieved');
    console.log(JSON.stringify({health:health.status,unauthenticated:denied.status,oauth:'passed',mcp:'passed',sourceFiles:result.structuredContent.sources.map(s=>s.filename)},null,2));
  } finally { await sdk.close(); }
} catch(error) { console.error(error.message); process.exitCode=1; }
