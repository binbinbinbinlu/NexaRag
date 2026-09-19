import { test, expect } from '@playwright/test';
import http from 'node:http';
import { createHash, randomBytes } from 'node:crypto';
import { createServer } from '../src/server.js';
import { hashToken } from '../src/auth.js';

test('native browser form returns to a different OAuth callback origin', async ({page,request}) => {
  const credential='browser-test-private-code-not-a-real-secret';
  const base='http://127.0.0.1:34873';
  const server=createServer({baseUrl:base,oauthSecret:'browser-test-secret-with-at-least-32-characters',members:[{id:'test',tokenHash:hashToken(credential),vectorStoreId:'vs_test'}]});
  const callback=http.createServer((req,res)=>{res.end('OAuth callback reached');});
  await new Promise(r=>server.listen(34873,'127.0.0.1',r));
  await new Promise(r=>callback.listen(0,'127.0.0.1',r));
  try {
    const redirect=`http://127.0.0.1:${callback.address().port}/callback`;
    const registration=await request.post(base+'/register',{data:{redirect_uris:[redirect],token_endpoint_auth_method:'none'}});
    expect(registration.status()).toBe(201);
    const client=await registration.json(), verifier=randomBytes(32).toString('base64url');
    const params=new URLSearchParams({client_id:client.client_id,redirect_uri:redirect,response_type:'code',resource:base+'/mcp',scope:'knowledge:read',state:'browser-regression',code_challenge_method:'S256',code_challenge:createHash('sha256').update(verifier).digest('base64url')});
    const violations=[];
    page.on('console',msg=>{if(msg.type()==='error' && /Content Security Policy|form-action/i.test(msg.text())) violations.push(msg.text());});
    await page.goto(base+'/authorize?'+params);
    await page.getByLabel('NexaRag access code').fill(credential);
    await page.getByRole('button',{name:'Connect and allow search'}).click();
    await expect(page).toHaveURL(new RegExp(`^${redirect.replaceAll('.','\\.')}\\?`));
    await expect(page.getByText('OAuth callback reached')).toBeVisible();
    expect(violations).toEqual([]);
    const result=new URL(page.url());
    expect(result.searchParams.get('state')).toBe('browser-regression');
    const exchanged=await request.post(base+'/token',{form:{grant_type:'authorization_code',client_id:client.client_id,code:result.searchParams.get('code'),code_verifier:verifier,redirect_uri:redirect,resource:base+'/mcp'}});
    expect(exchanged.status()).toBe(200);
    expect((await exchanged.json()).access_token).toBeTruthy();
  } finally {
    await page.close();
    await Promise.all([server,callback].map(s=>new Promise(r=>{s.closeAllConnections();s.close(r);} )));
  }
});
