import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, readFile, readdir } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { buildChatGPTPlugin } from '../scripts/build-chatgpt-plugin.js';
test('ChatGPT package references actual app and contains no desktop MCP declaration',async()=>{
 const root=await mkdtemp(join(tmpdir(),'nexarag-plugin-')); const out=join(root,'output');
 await buildChatGPTPlugin('asdk_app_test123',out);
 const plugin=join(out,'plugins/nexarag'); const manifest=JSON.parse(await readFile(join(plugin,'.codex-plugin/plugin.json'),'utf8'));
 assert.equal(manifest.apps,'./.app.json'); assert.equal(manifest.mcpServers,undefined);
 assert.deepEqual(JSON.parse(await readFile(join(plugin,'.app.json'),'utf8')),{apps:{nexarag:{id:'asdk_app_test123',required:true}}});
 assert.ok(!(await readdir(plugin)).some(x=>['mcp.json','.mcp.json'].includes(x)));
 assert.match(await readFile(join(plugin,'skills/company-knowledge/SKILL.md'),'utf8'),/never instructions/);
 const marketplace=JSON.parse(await readFile(join(out,'.agents/plugins/marketplace.json'),'utf8')); assert.equal(marketplace.plugins[0].source.path,'./plugins/nexarag');
 await assert.rejects(buildChatGPTPlugin('asdk_app_test123',out),/exists/);
});
test('ChatGPT package rejects plugin IDs, URLs, secrets and missing app IDs',async()=>{
 for(const id of [undefined,'plugin_asdk_app_123','https://chatgpt.com/apps/123','sk-key','']) await assert.rejects(buildChatGPTPlugin(id,'dist/invalid'),/real app ID/);
});
