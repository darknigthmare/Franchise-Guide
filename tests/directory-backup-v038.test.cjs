'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),{webcrypto}=require('node:crypto'),fs=require('node:fs/promises'),os=require('node:os'),path=require('node:path');
const {IDBFactory}=require('fake-indexeddb');
const B=require('../web/directory-backup-core.js');
const now=Date.UTC(2026,10,8,12,34,56,789),snapshot={schemaVersion:11,media:[{id:'demo',notes:'Données privées choisies'}]};
const options={now,crypto:webcrypto,validateSnapshot:x=>assert.equal(x.schemaVersion,11)};
function target(options={}){
 const files=new Map(),calls=[],handle={kind:'directory',async queryPermission(){calls.push('permission');return options.permission??'granted';},async requestPermission(){assert.fail('No automatic permission prompt');},async getFileHandle(name,flags={}){
  calls.push(flags.create?'create':'lookup');if(options.collision&&!flags.create)return {};
  if(!files.has(name)&&!flags.create)throw Object.assign(Error('absent'),{name:'NotFoundError'});
  if(!files.has(name))files.set(name,new Uint8Array());
  return {async createWritable(){calls.push('open');return {
   async write(bytes){calls.push('write');if(options.writeError)throw Error('disk full');files.set(name,new Uint8Array(bytes));options.afterWrite?.();},
   async close(){calls.push('close');if(options.closeError)throw Error('disconnect');options.afterClose?.();},
   async abort(){calls.push('abort');}
  };},async getFile(){calls.push('read');let data=files.get(name).slice();if(options.corrupt)data[0]^=1;
   return {size:data.length+(options.wrongSize?1:0),async arrayBuffer(){return data.buffer;}};}};
 }};return {files,calls,config:{...B.empty(),enabled:true,handle}};
}
test('external snapshot commits success only after close and independent SHA read-back',async()=>{
 const t=target(),r=await B.writeVerified(t.config,snapshot,options);
 assert.deepEqual(t.calls,['permission','lookup','create','open','write','close','read']);assert.equal(t.config.verifiedAt,null);assert.equal(r.verifiedAt,now);
 assert.equal(JSON.parse(Buffer.from(t.files.get(r.lastFile))).media[0].notes,snapshot.media[0].notes);
 assert.match(r.lastFile,/^franchiseguide-20261108T123456789Z-[a-f0-9]{24}\.json$/);assert.equal(r.sha256,await B.sha256(t.files.get(r.lastFile),webcrypto));
});
for(const permission of ['prompt','denied'])test('external snapshot does not prompt or write without permission: '+permission,async()=>{
 const t=target({permission});await assert.rejects(B.writeVerified(t.config,snapshot,options),{code:'PERMISSION'});assert.deepEqual(t.calls,['permission']);assert.equal(t.files.size,0);
});
for(const failure of ['writeError','closeError','corrupt','wrongSize'])test('external snapshot never marks '+failure+' verified',async()=>{
 const t=target({[failure]:true});await assert.rejects(B.writeVerified(t.config,snapshot,options));assert.equal(t.config.verifiedAt,null);assert.equal(t.files.size,1);
 if(['writeError','closeError'].includes(failure))assert.ok(t.calls.includes('abort'));
});
test('external snapshot refuses an existing name without opening it',async()=>{const t=target({collision:true});await assert.rejects(B.writeVerified(t.config,snapshot,options),{code:'COLLISION'});assert.deepEqual(t.calls,['permission','lookup']);});
for(const phase of ['before','afterWrite','afterClose'])test('external snapshot cancellation at '+phase+' never confirms',async()=>{
 const c=new AbortController(),t=target({[phase]:()=>c.abort()});if(phase==='before')c.abort();await assert.rejects(B.writeVerified(t.config,snapshot,{...options,signal:c.signal}),{code:'ABORTED'});assert.equal(t.config.verifiedAt,null);
 if(phase==='afterWrite')assert.ok(t.calls.includes('abort'));
});
test('external snapshot requires consent, validation, size and sane clock before writing',async()=>{
 const t=target();await assert.rejects(B.writeVerified({...t.config,enabled:false},snapshot,options),{code:'DISABLED'});
 await assert.rejects(B.writeVerified(t.config,snapshot,{...options,validateSnapshot:null}),{code:'VALIDATION'});
 await assert.rejects(B.writeVerified(t.config,{schemaVersion:99},options));
 await assert.rejects(B.writeVerified(t.config,{...snapshot,notes:'x'.repeat(B.MAX_BYTES)},options),{code:'SIZE'});
 for(const invalid of [-1,NaN,Infinity,1.5,8640000000000000])await assert.rejects(B.writeVerified(t.config,snapshot,{...options,now:invalid}),{code:'TIME'});
 assert.equal(t.files.size,0);
});
test('external schedule respects consent, periods and clock moving backwards',()=>{
 const c={...target().config,verifiedAt:now,sha256:'a'.repeat(64),lastFile:'franchiseguide-20261108T123456789Z-'+ 'b'.repeat(24)+'.json'};
 assert.equal(B.due(B.empty(),now),false);assert.equal(B.due(target().config,now),true);
 assert.equal(B.due(c,now+7*B.DAY-1),false);assert.equal(B.due(c,now+7*B.DAY),true);assert.equal(B.due(c,now-1),false);
 for(const intervalDays of [1,7,14,30])assert.equal(B.due({...c,intervalDays},now+intervalDays*B.DAY),true);
 for(const bad of [null,{}, {...c,version:99},{...c,enabled:'true'},{...c,intervalDays:0},{...c,sha256:null},{...c,lastFile:'../private.json'}])assert.throws(()=>B.validate(bad));
});
test('external target store is transactional and rejects stale updates after change or forget',async()=>{
 const s=B.createStore(new IDBFactory());assert.equal((await s.load()).revision,0);
 const one=await s.save({...B.empty(),handle:{kind:'directory'},enabled:true},0);assert.equal(one.revision,1);
 await assert.rejects(s.save(one,0),{code:'CHANGED'});assert.equal((await s.load()).enabled,true);
 const two=await s.save({...one,enabled:false},1);assert.equal(two.revision,2);
 await s.forget();assert.equal((await s.load()).handle,null);
 await assert.rejects(s.save(two,2),{code:'CHANGED'});assert.equal((await s.load()).enabled,false);
});
test('external target store refuses absent storage without silently resetting config',async()=>{await assert.rejects(B.createStore(null).load(),{code:'STORAGE'});});
test('external snapshot writes a real directory and is independently readable',async()=>{
 const dir=await fs.mkdtemp(path.join(os.tmpdir(),'fg-backup-'));
 const handle={kind:'directory',queryPermission:async()=> 'granted',async getFileHandle(name,flags={}){
  assert.equal(path.basename(name),name);const p=path.join(dir,name);
  if(!flags.create){try{await fs.access(p);}catch{throw Object.assign(Error('missing'),{name:'NotFoundError'});}}
  return {async createWritable(){let bytes;return {async write(b){bytes=Buffer.from(b);},async close(){await fs.writeFile(p,bytes,{flag:'wx'});},async abort(){}};},async getFile(){const data=await fs.readFile(p);return {size:data.length,arrayBuffer:async()=>data.buffer.slice(data.byteOffset,data.byteOffset+data.byteLength)};}};
 }};
 try{const result=await B.writeVerified({...B.empty(),enabled:true,handle},snapshot,options);assert.deepEqual(JSON.parse(await fs.readFile(path.join(dir,result.lastFile),'utf8')),snapshot);}finally{await fs.rm(dir,{recursive:true,force:true});}
});
