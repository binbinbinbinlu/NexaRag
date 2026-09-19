import test from 'node:test';
import assert from 'node:assert/strict';
import { fixture, credential } from './helpers.js';
import { Client } from '@modelcontextprotocol/sdk/client/index.js';
import { StreamableHTTPClientTransport } from '@modelcontextprotocol/sdk/client/streamableHttp.js';

test('official SDK initializes, lists and calls the authenticated MCP tool',async t=>{
 const f=await fixture(t); const {tokens}=await f.login();
 const client=new Client({name:'integration-test',version:'1.0.0'});
 t.after(()=>client.close());
 await client.connect(new StreamableHTTPClientTransport(new URL(f.url+'/mcp'),{requestInit:{headers:{Authorization:`Bearer ${tokens.access_token}`}}}));
 const list=await client.listTools(); assert.equal(list.tools.length,1); const tool=list.tools[0];
 assert.equal(tool.name,'search_company_knowledge'); assert.equal(tool.annotations.readOnlyHint,true);
 assert.equal(tool.inputSchema.additionalProperties,false);
 assert.deepEqual(tool._meta.securitySchemes,[{type:'oauth2',scopes:['knowledge:read']}]);
 const result=await client.callTool({name:tool.name,arguments:{query:' Leave policy? ',limit:2}});
 assert.equal(result.structuredContent.sources[0].citation,'S1'); assert.deepEqual(f.calls,[['vs_shared','Leave policy?',2]]);
});
test('unauthenticated and legacy credentials cannot read MCP; discovery is public',async t=>{
 const f=await fixture(t);
 for(const token of [undefined,'wrong',credential]) { const r=await f.rpc('tools/list',{},token); assert.equal(r.status,401); assert.match(r.headers.get('www-authenticate'),/oauth-protected-resource\/mcp/); }
 const metadata=await (await fetch(f.url+'/.well-known/oauth-protected-resource/mcp')).json(); assert.equal(metadata.resource,f.resource);
 const oauth=await (await fetch(f.url+'/.well-known/oauth-authorization-server')).json(); assert.deepEqual(oauth.code_challenge_methods_supported,['S256']); assert.deepEqual(oauth.grant_types_supported,['authorization_code']);
 assert.deepEqual(f.calls,[]);
});
test('MCP validates query, limits and forbids caller-selected stores',async t=>{
 const f=await fixture(t); const {tokens}=await f.login();
 for(const args of [{query:''},{query:'x'.repeat(2001)},{query:'x',limit:6},{query:'x',vectorStoreId:'vs_secret'},null]) {
  const r=await (await f.rpc('tools/call',{name:'search_company_knowledge',arguments:args},tokens.access_token)).json(); assert.ok(r.error||r.result?.isError);
 }
 assert.equal(f.calls.length,0);
});
test('unknown tools do not reach retrieval; upstream errors are sanitized',async t=>{
 const f=await fixture(t,{search:async()=>{throw new Error('secret API key');}}); const {tokens}=await f.login();
 const missing=await (await f.rpc('tools/call',{name:'delete_file',arguments:{}},tokens.access_token)).json(); assert.ok(missing.error||missing.result?.isError);
 const r=await (await f.rpc('tools/call',{name:'search_company_knowledge',arguments:{query:'x'}},tokens.access_token)).json(); assert.equal(r.result.isError,true); assert.doesNotMatch(JSON.stringify(r),/secret API/);
});
test('empty evidence stays empty',async t=>{
 const f=await fixture(t,{search:async()=>[]}); const {tokens}=await f.login(); const r=await (await f.rpc('tools/call',{name:'search_company_knowledge',arguments:{query:'unknown'}},tokens.access_token)).json(); assert.deepEqual(r.result.structuredContent,{sources:[]});
});
test('rate limits authenticated MCP requests',async t=>{
 const f=await fixture(t,{requestsPerMinute:1}); const {tokens}=await f.login(); assert.equal((await f.rpc('tools/list',{},tokens.access_token)).status,200); const r=await f.rpc('tools/list',{},tokens.access_token); assert.equal(r.status,429); assert.ok(r.headers.get('retry-after'));
});
test('rejects untrusted origins, malformed and oversized payloads',async t=>{
 const f=await fixture(t); const {tokens}=await f.login();
 assert.equal((await f.rpc('tools/list',{},tokens.access_token,{Origin:'https://evil.example'})).status,403);
 for(const [body,status] of [['{',400],['x'.repeat(17000),413]]) { const r=await fetch(f.url+'/mcp',{method:'POST',headers:{'Content-Type':'application/json'},body}); assert.equal(r.status,status); }
});
test('old GPT Action endpoints are removed and health identifies MCP',async t=>{
 const f=await fixture(t); assert.equal((await (await fetch(f.url+'/health')).json()).integration,'mcp');
 for(const path of ['/openapi.json','/search']) assert.equal((await fetch(f.url+path)).status,404);
});
