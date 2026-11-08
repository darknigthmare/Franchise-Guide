'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),vm=require('node:vm'),fs=require('node:fs');
const {JSDOM}=require('jsdom');
const source=fs.readFileSync(require.resolve('../web/pwa-update.js'),'utf8');
const persistence=fs.readFileSync(require.resolve('../web/persistence.js'),'utf8');
const writable=persistence.slice(persistence.indexOf('function assertWritable(){'),persistence.indexOf('function persistEnvelope('));
function harness({controlled=true,waiting=false,registrationFailure=false}={}){
 const dom=new JSDOM('<body></body>'),events={},calls=[],messages=[],old={version:'old'};
 const reg={waiting:waiting?{postMessage(message,ports){calls.push('activate');messages.push({message,port:ports[0]});}}:null,addEventListener(){}};
 const sw={controller:controlled?old:null,addEventListener(name,fn){events[name]=fn;},register:()=>registrationFailure?Promise.reject(Error('offline')):Promise.resolve(reg)};
 class Channel {constructor(){const a={onmessage:null,close(){}},b={postMessage(data){a.onmessage?.({data});}};this.port1=a;this.port2=b;}}
 const ctx=vm.createContext({document:dom.window.document,navigator:{serviceWorker:sw},location:{reload(){calls.push('reload');}},confirm:()=>true,MessageChannel:Channel,
  setTimeout:()=>1,clearTimeout(){},clearInterval(){},storageDraft:null,recoveryOnly:null,storageWritable:true,storageRevision:0,
  authGeneration:4,libraryEpoch:9,searchGeneration:3,discoverGeneration:5,traktSaveTimer:null,todayTimer:null,traktSyncing:false,checkInBusy:false,reviewSessions:new Map(),
  FGRequests:{clear(){calls.push('requests');}},cancelTraktRequests(){calls.push('trakt');},invalidateGamingReads(){calls.push('gaming');},directoryBackupStop(){calls.push('backup');},directoryBackupBusy:()=>false,
  save(){calls.push('save');},exportFg(){calls.push('export');},toast(message){calls.push('toast:'+message);},diagnostic(...a){calls.push('diagnostic:'+a.slice(0,2).join('/'));},KEY:'library',localStorage:{getItem:()=>null}});
 vm.runInContext(source+'\n'+writable+'\nthis.api=FGPwaUpdate;',ctx);
 return {dom,ctx,sw,reg,events,calls,messages,async install(){ctx.api.install();await Promise.resolve();await Promise.resolve();},change(next={version:'new'}){sw.controller=next;events.controllerchange();}};
}
test('first PWA controller assignment does not freeze a fresh client',async()=>{
 const h=harness({controlled:false});await h.install();h.change();assert.equal(h.ctx.api.changed(),false);assert.equal(h.ctx.storageWritable,true);assert.equal(h.calls.length,0);h.dom.window.close();
});
test('unexpected controller replacement freezes writes, invalidates operations and preserves export access',async()=>{
 const h=harness();await h.install();h.change();assert.equal(h.ctx.api.changed(),true);assert.equal(h.ctx.storageWritable,false);
 assert.deepEqual([h.ctx.authGeneration,h.ctx.libraryEpoch,h.ctx.searchGeneration,h.ctx.discoverGeneration],[5,10,4,6]);
 assert.deepEqual(h.calls,['requests','trakt','gaming','backup']);assert.equal(h.dom.window.document.querySelector('#appVersionChanged').getAttribute('role'),'alert');
 h.ctx.storageWritable=true;assert.throws(()=>vm.runInContext('assertWritable()',h.ctx),{code:'VERSION_CHANGED'});
 h.dom.window.document.querySelector('[data-version-export]').click();assert.equal(h.calls.at(-1),'export');
 h.change(h.sw.controller);assert.equal(h.dom.window.document.querySelectorAll('#appVersionChanged').length,1);h.dom.window.close();
});
test('PWA update checks draft, recovery, remote work, forms and external snapshots',async()=>{
 const h=harness();assert.equal(h.ctx.api.blockReason(),null);
 for(const key of ['storageDraft','recoveryOnly','traktSyncing','checkInBusy']){h.ctx[key]=true;assert.ok(h.ctx.api.blockReason());h.ctx[key]=false;}
 h.ctx.reviewSessions.set('id',{sending:true});assert.ok(h.ctx.api.blockReason());h.ctx.reviewSessions.clear();
 const d=h.dom.window.document.createElement('dialog');d.setAttribute('open','');h.dom.window.document.body.append(d);assert.ok(h.ctx.api.blockReason());d.remove();
 h.ctx.directoryBackupBusy=()=>true;assert.ok(h.ctx.api.blockReason());h.ctx.directoryBackupBusy=()=>false;assert.equal(h.ctx.api.blockReason(),null);h.dom.window.close();
});
test('PWA activation refusal for other tabs never reloads or leaves a false requested update',async()=>{
 const h=harness({waiting:true});await h.install();const button=h.dom.window.document.querySelector('[data-pwa-update]');
 const done=button.onclick();assert.deepEqual(h.calls,['save','activate']);h.messages[0].port.postMessage({ok:false,reason:'other-tabs'});await done;
 assert.equal(button.disabled,false);assert.ok(h.calls.some(x=>x.startsWith('toast:Ferme les autres')));assert.ok(!h.calls.includes('reload'));
 h.change();assert.ok(!h.calls.includes('reload'));assert.ok(h.dom.window.document.querySelector('#appVersionChanged'));h.dom.window.close();
});
test('explicit PWA activation saves before asking and reloads only after controller replacement',async()=>{
 const h=harness({waiting:true});await h.install();const done=h.dom.window.document.querySelector('[data-pwa-update]').onclick();
 assert.deepEqual(h.calls,['save','activate']);h.messages[0].port.postMessage({ok:true});await done;assert.ok(!h.calls.includes('reload'));h.change();assert.equal(h.calls.at(-1),'reload');h.dom.window.close();
});
test('PWA registration failure reports a fixed non-sensitive diagnostic',async()=>{
 const h=harness({registrationFailure:true});await h.install();await Promise.resolve();assert.deepEqual(h.calls,['diagnostic:warn/pwa']);assert.equal(h.ctx.storageWritable,true);h.dom.window.close();
});
