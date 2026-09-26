import { mkdir, writeFile, open, unlink } from 'node:fs/promises';
import { loadConfig } from '../src/config.js';
import { openai } from '../src/openai.js';
import { buildDocuments, parseChecklist, syncDocuments } from '../src/field-log.js';

const dryRun = process.argv.includes('--dry-run');
const site = process.env.FIELD_LOG_URL || 'https://nexa-construction-field-log-vanni.blu42.chatgpt.site';
const stateDirectory = 'data/field-log-sync';
let lock;
try {
  const url = new URL(site);
  if (url.protocol !== 'https:' || url.origin !== site) throw new Error('FIELD_LOG_URL must be an HTTPS origin');
  if (!process.env.PIPELINE_PUBLISH_TOKEN || !process.env.SITES_BYPASS_TOKEN) throw new Error('Field-log credentials are required');
  const config = await loadConfig();
  const stores = [...new Set(config.members.filter(m=>!m.disabled).map(m=>m.vectorStoreId))];
  const store = process.env.FIELD_LOG_VECTOR_STORE_ID || (stores.length===1 ? stores[0] : null);
  if (!store || !/^vs_[A-Za-z0-9]+$/.test(store) || !stores.includes(store)) throw new Error('Select an approved member store with FIELD_LOG_VECTOR_STORE_ID');
  await mkdir(stateDirectory,{recursive:true});
  lock = await open(`${stateDirectory}/sync.lock`,'wx');
  const headers = {Authorization:`Bearer ${process.env.PIPELINE_PUBLISH_TOKEN}`,'OAI-Sites-Authorization':`Bearer ${process.env.SITES_BYPASS_TOKEN}`};
  async function get(path) {
    const response = await fetch(site+path,{headers,redirect:'manual',signal:AbortSignal.timeout(30000)});
    if (!response.ok) throw new Error(`Field-log read failed (${response.status}) at ${path}`);
    return response;
  }
  const snapshot = await (await get('/api/pipeline')).json();
  const checklist = parseChecklist(await (await get('/attention')).text());
  const capturedAt = new Date().toISOString();
  const documents = buildDocuments(snapshot,checklist,site,capturedAt);
  await writeFile(`${stateDirectory}/snapshot.json`,JSON.stringify({capturedAt,snapshot,checklist}));
  for (const doc of documents) await writeFile(`${stateDirectory}/${doc.filename}`,doc.text);
  const counts = {projects:documents.length-1,logs:documents.reduce((n,d)=>n+d.logs,0),checklistItems:checklist.length,completedItems:checklist.filter(t=>t.completed).length};
  const result = dryRun ? {dryRun:true} : await syncDocuments(documents,store,openai);
  const report = {capturedAt,site,...counts,...result};
  await writeFile(`${stateDirectory}/last-run.json`,JSON.stringify(report,null,2));
  console.log(JSON.stringify(report,null,2));
} catch (error) { console.error(error.message); process.exitCode=1; }
finally {if(lock) {await lock.close();await unlink(`${stateDirectory}/sync.lock`);}}
