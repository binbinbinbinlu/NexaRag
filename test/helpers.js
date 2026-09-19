import { createServer } from '../src/server.js';
import { hashToken } from '../src/auth.js';
import { createHash, randomBytes } from 'node:crypto';
export const credential = 'test-private-access-code-with-32-characters';
export const members = [{ id:'alice', tokenHash:hashToken(credential), vectorStoreId:'vs_shared' }];
export const secret = 'test-oauth-signing-key-at-least-32-characters';
export async function fixture(t, options={}) {
  const calls=[];
  const server=createServer({ members, oauthSecret:secret, search:async (...args)=> { calls.push(args); return [{ citation:'S1',file_id:'file-test',filename:'Policy.txt',score:0.9,excerpts:'Ask your manager.' }]; }, ...options });
  await new Promise(r=>server.listen(0,'127.0.0.1',r));
  t.after(()=>new Promise(r=> { server.closeAllConnections(); server.close(r); }));
  const url=`http://127.0.0.1:${server.address().port}`;
  const resource='http://localhost:3000/mcp';
  const form=(path,body,headers={})=>fetch(url+path,{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded',...headers},body:new URLSearchParams(body),redirect:'manual'});
  const register=async (redirect='http://127.0.0.1:34567/callback')=> {
    const r=await fetch(url+'/register',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({redirect_uris:[redirect],token_endpoint_auth_method:'none',grant_types:['authorization_code'],response_types:['code']})});
    return {response:r,client:await r.json()};
  };
  const start=async (client,extra={})=>{
    const verifier=randomBytes(32).toString('base64url');
    const challenge=createHash('sha256').update(verifier).digest('base64url');
    const params={client_id:client.client_id,redirect_uri:client.redirect_uris[0],response_type:'code',code_challenge:challenge,code_challenge_method:'S256',resource,scope:'knowledge:read',state:'state-test',...extra};
    const r=await fetch(url+'/authorize?'+new URLSearchParams(params),{redirect:'manual'});
    const html=await r.text();
    return {response:r,html,verifier,flow:/name="flow" value="([^"]+)"/.exec(html)?.[1],cookie:r.headers.get('set-cookie')?.split(';')[0],params};
  };
  const consent=(flow,code=credential,headers={})=>form('/oauth/consent',{flow:flow.flow,credential:code},{Cookie:flow.cookie,Origin:'http://localhost:3000',...headers});
  const exchange=(client,flow,code,extra={})=>form('/token',{grant_type:'authorization_code',client_id:client.client_id,code,code_verifier:flow.verifier,redirect_uri:client.redirect_uris[0],resource,...extra});
  const login=async (code=credential)=>{
    const {client}=await register(); const flow=await start(client); const r=await consent(flow,code);
    if(r.status!==303) throw new Error('Consent failed: '+await r.text());
    const authCode=new URL(r.headers.get('location')).searchParams.get('code');
    const tokenResponse=await exchange(client,flow,authCode); const tokens=await tokenResponse.json();
    if(!tokens.access_token) throw new Error(JSON.stringify(tokens));
    return {client,flow,authCode,tokens};
  };
  const rpc=(method,params,token,extra={})=>fetch(url+'/mcp',{method:'POST',headers:{'Content-Type':'application/json',Accept:'application/json, text/event-stream',...(token?{Authorization:`Bearer ${token}`} : {}),...extra},body:JSON.stringify({jsonrpc:'2.0',id:1,method,params})});
  return {url,server,calls,form,register,start,consent,exchange,login,rpc,resource};
}
