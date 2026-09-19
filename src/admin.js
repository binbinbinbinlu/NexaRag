import { randomBytes } from 'node:crypto';
import { readFile } from 'node:fs/promises';
import { basename } from 'node:path';
import { setTimeout } from 'node:timers/promises';
import { openai } from './openai.js';
import { hashToken } from './auth.js';

const [command, arg, extra] = process.argv.slice(2);
try {
  if (command === 'token') {
    const token = randomBytes(32).toString('base64url');
    console.log(JSON.stringify({ token, tokenHash: hashToken(token) }, null, 2));
  } else if (command === 'create-store' && arg) {
    console.log(JSON.stringify(await openai('/vector_stores', { body: { name: arg } }), null, 2));
  } else if (command === 'upload' && /^vs_[a-zA-Z0-9]+$/.test(arg) && extra) {
    const form = new FormData();
    form.set('purpose', 'assistants');
    form.set('file', new Blob([await readFile(extra)]), basename(extra));
    const file = await openai('/files', { body: form });
    console.log(`Uploaded file: ${file.id}. Save this ID for removal.`);
    let item = await openai(`/vector_stores/${arg}/files`, { body: { file_id: file.id } });
    const deadline = Date.now() + 300000;
    while (item.status === 'in_progress' && Date.now() < deadline) {
      await setTimeout(1500);
      item = await openai(`/vector_stores/${arg}/files/${file.id}`, { method: 'GET' });
    }
    if (item.status !== 'completed') throw new Error(`Indexing status: ${item.status}. Check file ${file.id} before retrying.`);
    console.log(`Indexed ${basename(extra)} in ${arg}`);
  } else if (command === 'delete-file' && /^file-[a-zA-Z0-9]+$/.test(arg)) {
    console.log(JSON.stringify(await openai(`/files/${arg}`, { method: 'DELETE' }), null, 2));
  } else {
    console.error('Usage: node --env-file=.env src/admin.js token | create-store "Name" | upload vs_ID path | delete-file file-ID');
    process.exitCode = 1;
  }
} catch (error) { console.error(error.message); process.exitCode = 1; }
