import { mkdir, readFile, writeFile, cp, access } from 'node:fs/promises';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

// Build into a fresh output folder; never ship a made-up app ID or embed secrets.
export async function buildChatGPTPlugin(appId, output) {
  if (typeof appId !== 'string' || !/^(asdk_app_|connector_|templated_apps_)[A-Za-z0-9_-]+$/.test(appId)) throw new Error('Provide the real app ID (asdk_app_..., connector_..., or templated_apps_...), not a plugin ID or URL');
  const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
  const dest = resolve(output);
  try { await access(dest); throw new Error('Output already exists; choose a new directory'); } catch (e) { if (e.code !== 'ENOENT') throw e; }
  const pluginDir = resolve(dest,'plugins/nexarag');
  const manifest = JSON.parse(await readFile(resolve(root,'plugins/nexarag/.codex-plugin/plugin.json'),'utf8'));
  delete manifest.mcpServers;
  manifest.apps = './.app.json';
  await mkdir(resolve(pluginDir,'.codex-plugin'),{recursive:true});
  await writeFile(resolve(pluginDir,'.codex-plugin/plugin.json'),JSON.stringify(manifest,null,2)+'\n');
  await writeFile(resolve(pluginDir,'.app.json'),JSON.stringify({apps:{nexarag:{id:appId,required:true}}},null,2)+'\n');
  await cp(resolve(root,'plugins/nexarag/skills'),resolve(pluginDir,'skills'),{recursive:true});
  await mkdir(resolve(dest,'.agents/plugins'),{recursive:true});
  await writeFile(resolve(dest,'.agents/plugins/marketplace.json'),JSON.stringify({name:'nexarag-team',interface:{displayName:'NexaRag'},plugins:[{name:'nexarag',source:{source:'local',path:'./plugins/nexarag'},policy:{installation:'AVAILABLE',authentication:'ON_INSTALL'},category:'Productivity'}]},null,2)+'\n');
  return dest;
}
if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  try {
    if (!process.argv[2]) throw new Error('Usage: node scripts/build-chatgpt-plugin.js APP_ID [NEW_OUTPUT_DIRECTORY]');
    console.log(await buildChatGPTPlugin(process.argv[2],process.argv[3] || 'dist/chatgpt'));
  } catch(e) { console.error(e.message); process.exitCode=1; }
}
