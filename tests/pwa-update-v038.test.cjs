'use strict';
const { test } = require('node:test');
const A = require('node:assert/strict');
const vm = require('node:vm');
const fs = require('node:fs');
function worker(clients, sourceUrl='https://fg.example/app/') {
  const events={}, replies=[];let skips=0, done=Promise.resolve();
  const source={id:'writer',url:sourceUrl};
  const self={registration:{scope:'https://fg.example/app/'},location:{origin:'https://fg.example'},
    clients:{matchAll:async()=>clients,claim:async()=>{}},skipWaiting:async()=>{skips++;},
    addEventListener:(name,fn)=>{events[name]=fn;}};
  const ctx=vm.createContext({self,URL,Response,caches:{},Set,Promise,console});
  vm.runInContext(fs.readFileSync(require.resolve('../web/sw.js'),'utf8'),ctx);
  return {get skips(){return skips;},replies,async activate(type='ACTIVATE_UPDATE'){
    events.message({data:{type},source,ports:[{postMessage:x=>replies.push(x)}],waitUntil:p=>{done=p;}});await done;
  }};
}
test('PWA update refuses to replace a worker used by another open application tab',async()=>{
  const h=worker([{id:'writer',url:'https://fg.example/app/'},{id:'reader',url:'https://fg.example/app/#books'}]);
  await h.activate();A.equal(h.skips,0);A.equal(h.replies[0]?.reason,'other-tabs');
});
test('PWA update accepts only the sole initiating in-scope client',async()=>{
  const h=worker([{id:'writer',url:'https://fg.example/app/'}]);await h.activate();A.equal(h.skips,1);A.equal(h.replies[0]?.ok,true);
});
test('PWA update ignores same-origin clients outside the application scope',async()=>{
  const h=worker([{id:'writer',url:'https://fg.example/app/'},{id:'other',url:'https://fg.example/another/'}]);await h.activate();A.equal(h.skips,1);
});
test('PWA update refuses a foreign or out-of-scope initiator',async()=>{
  for(const origin of ['https://evil.example/app/','https://fg.example/another/']){
    const h=worker([{id:'writer',url:origin}],origin);await h.activate();A.equal(h.skips,0);
  }
});
test('PWA update refuses a stale source not found in its client inventory',async()=>{
  const h=worker([{id:'other',url:'https://fg.example/app/'}]);await h.activate();A.equal(h.skips,0);
});
test('Unknown worker messages cannot activate a new version',async()=>{
  const h=worker([{id:'writer',url:'https://fg.example/app/'}]);await h.activate('OTHER');A.equal(h.skips,0);
});
