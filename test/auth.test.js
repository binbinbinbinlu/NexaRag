import test from 'node:test';
import assert from 'node:assert/strict';
import { fixture, members, secret, credential } from './helpers.js';
import { NexaAuth, allowedRedirect, validateMembers, hashToken } from '../src/auth.js';

test('redirect allowlist blocks external and deceptive destinations',()=>{
 for(const url of ['https://evil.example/callback','https://chatgpt.com.evil.example/connector_platform_oauth_redirect','https://chatgpt.com@evil.example/callback','http://chatgpt.com/connector_platform_oauth_redirect','http://127.0.0.1/callback?next=evil','http://127.0.0.1/other']) assert.equal(allowedRedirect(url),false);
 for(const url of ['https://chatgpt.com/connector/oauth/abc-123','https://chatgpt.com/connector_platform_oauth_redirect','http://127.0.0.1:45321/callback']) assert.equal(allowedRedirect(url),true);
});
test('DCR refuses unsafe redirects before issuing credentials',async t=>{
 const f=await fixture(t); const {response}=await f.register('https://evil.example/callback'); assert.equal(response.status,400);
});
test('authorization checks resource, scope and registered redirect',async t=>{
 const f=await fixture(t); const {client}=await f.register();
 for(const extra of [{resource:'https://evil.example/mcp'},{scope:'knowledge:write'},{code_challenge:'x'}]) {
  const flow=await f.start(client,extra); assert.equal(flow.response.status,302); assert.ok(new URL(flow.response.headers.get('location')).searchParams.get('error'));
 }
 const flow=await f.start(client,{redirect_uri:'https://evil.example/callback'}); assert.equal(flow.response.status,400); assert.equal(flow.response.headers.get('location'),null);
});
test('consent requires browser binding, origin and correct private code',async t=>{
 const f=await fixture(t); const {client}=await f.register(); const flow=await f.start(client);
 assert.match(flow.html,/never your OpenAI API key/); assert.doesNotMatch(flow.html,new RegExp(credential));
 assert.equal((await f.consent(flow,credential,{Cookie:''})).status,400);
 assert.equal((await f.consent(flow,credential,{Origin:'https://evil.example'})).status,400);
 assert.equal((await f.consent(flow,'wrong-private-code')).status,401);
 assert.equal((await f.consent(flow)).status,303); assert.equal((await f.consent(flow)).status,400);
});
test('authorization codes bind PKCE, client, redirect, resource and are single use',async t=>{
 const f=await fixture(t); const {client}=await f.register(); const {client:other}=await f.register(); const flow=await f.start(client);
 const consent=await f.consent(flow); const target=new URL(consent.headers.get('location')); assert.equal(target.searchParams.get('state'),'state-test'); const code=target.searchParams.get('code');
 for(const extra of [{code_verifier:'wrong'},{redirect_uri:'http://localhost/callback'},{resource:'https://evil.example/mcp'}]) assert.equal((await f.exchange(client,flow,code,extra)).status,400);
 assert.equal((await f.exchange(other,flow,code)).status,400);
 assert.equal((await f.exchange(client,flow,code)).status,200);
 assert.equal((await f.exchange(client,flow,code)).status,400);
});
test('short-lived credentials survive restarts but reject tampering, expiry, rotation and wrong audience',async t=>{
 let time=Date.now(); const f=await fixture(t,{now:()=>time}); const {client,tokens}=await f.login();
 const make=(override={})=>new NexaAuth({members,secret,baseUrl:'http://localhost:3000',now:()=>time,...override});
 const auth=make(); assert.ok(await auth.clientsStore.getClient(client.client_id)); assert.equal((await auth.verifyAccessToken(tokens.access_token)).extra.memberId,'alice');
 await assert.rejects(auth.verifyAccessToken(tokens.access_token.slice(0,-5)+'zzzzz'));
 await assert.rejects(auth.verifyAccessToken(client.client_id));
 await assert.rejects(make({baseUrl:'https://elsewhere.example'}).verifyAccessToken(tokens.access_token));
 await assert.rejects(make({members:[{...members[0],tokenHash:hashToken('rotated-code')}]}).verifyAccessToken(tokens.access_token));
 await assert.rejects(make({secret:'another-key-with-more-than-32-characters'}).verifyAccessToken(tokens.access_token));
 time+=3601000; await assert.rejects(auth.verifyAccessToken(tokens.access_token));
});
test('pending consent and authorization codes expire',async t=>{
 let time=Date.now(); const f=await fixture(t,{now:()=>time}); const {client}=await f.register(); const flow=await f.start(client);
 time+=300001; assert.equal((await f.consent(flow)).status,400);
 const next=await f.start(client); const r=await f.consent(next); const code=new URL(r.headers.get('location')).searchParams.get('code');
 time+=60001; assert.equal((await f.exchange(client,next,code)).status,400);
});
test('member validation fails closed',()=>{
 assert.throws(()=>validateMembers([])); assert.throws(()=>validateMembers([...members,...members])); assert.throws(()=>validateMembers([{...members[0],tokenHash:'placeholder'}]));
});
