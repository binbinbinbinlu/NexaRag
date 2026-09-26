import test from 'node:test';
import assert from 'node:assert/strict';
import { buildDocuments, parseChecklist, syncDocuments } from '../src/field-log.js';

const snapshot = () => ({schemaVersion:1,baseline:{updated:'September 22, 2026',projects:[{id:'100',address:'Example site',status:'In progress',tags:['FRAMING'],latest:'Work planned',attention:'Check beam',logs:[{date:'2026-09-20',tag:'FRAMING',title:'Beam',items:['Beam proposed'],image:{src:'assets/beam.jpg',caption:'Proposed beam',alt:'Beam photo'}}]}]},publications:[{source:{kind:'drive',url:'https://drive.google.com/file/d/example/view'},updates:[{projectId:'100',date:'2026-09-20',tag:'FRAMING',title:'Beam',items:['Beam proposed']}]}]});
const checklist = [{projectId:'100',description:'Check beam',completed:true,completedAt:'2026-09-21T00:00:00Z'}];
test('exports all logs, deduplicates imports, retains sources, images and completion',()=>{
  const docs=buildDocuments(snapshot(),checklist,'https://example.com','2026-09-26T12:00:00Z');
  assert.equal(docs.length,2); assert.equal(docs[0].logs,1);
  assert.match(docs[0].text,/COMPLETED: Check beam/);
  assert.match(docs[0].text,/https:\/\/example.com\/assets\/beam.jpg/);
  assert.match(docs[0].text,/drive.google.com/);
  assert.match(docs[1].text,/open checklist items: 0/);
  assert.equal(docs[0].hash,buildDocuments(snapshot(),checklist,'https://example.com','2026-09-26T15:00:00Z')[0].hash);
});
test('rejects incomplete or unknown project data',()=>{
  assert.throws(()=>buildDocuments({},[],'https://example.com','2026-09-26'));
  assert.throws(()=>buildDocuments(snapshot(),[{...checklist[0],projectId:'other'}],'https://example.com','2026-09-26'));
});
test('decodes checklist JSON without executing page scripts; fails closed',()=>{
  const stream='1:'+JSON.stringify(['$',{initialItems:checklist,storageAvailable:true}])+'\n';
  const html=`<script>something.push(${JSON.stringify(stream)})</script><script>throw new Error('never execute')</script>`;
  assert.deepEqual(parseChecklist(html),checklist);
  assert.throws(()=>parseChecklist(html.replace('storageAvailable\\\":true','storageAvailable\\\":false')));
  assert.throws(()=>parseChecklist('<html>sign in</html>'));
});
test('unchanged remote files are reused; only managed stale attachments are detached',async()=>{
  const doc={key:'100',hash:'hash',text:'text',filename:'project.md'};
  const files=[{id:'keep',status:'completed',attributes:{nexa_source:'construction-field-log',document_key:'100',content_hash:'hash'}},{id:'old',attributes:{nexa_source:'construction-field-log'}},{id:'unrelated',attributes:{}}];
  const calls=[];
  const api=async(path,opts)=>{calls.push([path,opts]);return opts.method==='GET'?{data:files,has_more:false}:{};};
  const result=await syncDocuments([doc],'vs_test',api);
  assert.equal(result.unchanged,1); assert.equal(result.detached,1);
  assert.deepEqual(calls.filter(c=>c[1].method==='DELETE').map(c=>c[0]),['/vector_stores/vs_test/files/old']);
});
test('failed indexing never removes previously searchable files',async()=>{
  const calls=[];
  const api=async(path,opts)=>{calls.push([path,opts]);if(opts.method==='GET')return {data:[{id:'old',attributes:{nexa_source:'construction-field-log'}}],has_more:false};if(path==='/files')return {id:'file-new'};return {status:'failed'};};
  await assert.rejects(syncDocuments([{key:'100',hash:'new',text:'text',filename:'p.md'}],'vs_test',api),/Indexing failed/);
  assert.equal(calls.some(c=>c[1].method==='DELETE'),false);
});
test('paginates existing files, waits for indexing, then retires older attachment',async()=>{
  const calls=[]; let poll=0;
  const api=async(path,opts)=>{
    calls.push([path,opts]);
    if(path.includes('?limit=100&after='))return {data:[{id:'old',attributes:{nexa_source:'construction-field-log'}}],has_more:false};
    if(path.includes('?limit=100'))return {data:[{id:'unrelated',attributes:{}}],has_more:true,last_id:'unrelated'};
    if(path==='/files')return {id:'file-new'};
    if(opts.method==='DELETE')return {};
    if(opts.method==='GET')return {status:++poll===1?'in_progress':'completed'};
    return {status:'in_progress'};
  };
  const result=await syncDocuments([{key:'100',hash:'new',text:'text',filename:'p.md'}],'vs_test',api,{wait:async()=>{}});
  assert.equal(result.uploaded,1);assert.equal(result.detached,1);
  assert.equal(calls.at(-1)[1].method,'DELETE');
  assert.equal(calls.at(-1)[0],'/vector_stores/vs_test/files/old');
});
