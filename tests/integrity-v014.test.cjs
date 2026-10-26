'use strict';
const {test}=require('node:test');
const assert=require('node:assert/strict');
const I=require('../web/integrity.js');
const C=require('../web/core.js');
const Steam=require('../web/steam-library.js');
const G=require('../web/gaming-core.js');
const fs=require('node:fs');
const vm=require('node:vm');

function library(kind='show') {
  return {schema:8,media:[
    {id:'a',kind,title:'Titre commun',added:true,listIds:[],userNote:'',rating:null},
    {id:'b',kind,title:'Titre commun',added:true,listIds:[],userNote:'',rating:null}
  ],episodes:[],lists:[],suggestions:[],gaming:{version:1,accounts:[],sets:[]}};
}
function merge(state) { return I.mergeMedia(state,'a','b').state; }
function episode(id,showId,extra={}) {
  return {id,showId,season:1,num:1,watched:false,skipped:false,plays:0,watchHistory:[],...extra};
}

test('integrity refuses different RAWG identities stored only as sourceId',()=>{
  const s=library('game');Object.assign(s.media[0],{source:'rawg',sourceId:'10'});Object.assign(s.media[1],{source:'rawg',sourceId:'20'});
  assert.equal(I.audit(s).duplicateCandidates[0].safe,false);assert.throws(()=>merge(s),/identifiant|fournisseur/i);
});
test('integrity refuses homonymous RetroAchievements games with different source IDs',()=>{
  const s=library('game');Object.assign(s.media[0],{source:'retro',sourceId:'10'});Object.assign(s.media[1],{source:'retro',sourceId:'20'});
  assert.equal(I.audit(s).duplicateCandidates[0].safe,false);assert.throws(()=>merge(s),/identifiant|fournisseur/i);
});
test('integrity compares a RetroAchievements source ID against a preserved alias',()=>{
  const s=library('game');Object.assign(s.media[0],{source:'retro',sourceId:'10'});Object.assign(s.media[1],{source:'rawg',sourceId:'70',retroGameId:'20'});
  assert.throws(()=>merge(s),/identifiant|fournisseur/i);
});
test('RAWG-primary merge retains the RetroAchievements alias even without a trophy list',()=>{
  const s=library('game');Object.assign(s.media[0],{source:'rawg',sourceId:'70'});Object.assign(s.media[1],{source:'retro',sourceId:'10'});
  const out=merge(s),m=out.media[0];assert.equal(m.source,'rawg');assert.equal(m.sourceId,'70');assert.equal(String(m.rawgId),'70');assert.equal(m.retroGameId,'10');assert.deepEqual(out.gaming.sets,[]);
});
test('integrity refuses homonymous OpenLibrary works',()=>{
  const s=library('book');Object.assign(s.media[0],{source:'openlibrary',sourceId:'/works/OL1W'});Object.assign(s.media[1],{source:'openlibrary',sourceId:'/works/OL2W'});
  assert.throws(()=>merge(s),/identifiant|fournisseur/i);
});
test('integrity compares a Steam source identity against nested metadata',()=>{
  const s=library('game');Object.assign(s.media[0],{source:'steam',sourceId:'10'});s.media[1].steam={appId:'20',accounts:{}};
  assert.throws(()=>merge(s),/identifiant|fournisseur/i);
});
test('integrity refuses conflicting Trakt identities',()=>{
  const s=library('movie');s.media[0].traktId=11;s.media[1].traktId=12;
  assert.equal(I.mergeAllowed(s.media[0],s.media[1]).ok,false);
});
test('integrity refuses an internally inconsistent provider identity',()=>{
  const s=library('game');Object.assign(s.media[0],{source:'steam',sourceId:'10',steam:{appId:'20',accounts:{}}});
  assert.throws(()=>merge(s),/identifiant|fournisseur/i);
});
test('merging sources retains the identity from the secondary source',()=>{
  const s=library('game');Object.assign(s.media[0],{source:'steam',sourceId:'10'});Object.assign(s.media[1],{source:'rawg',sourceId:'70'});
  const m=merge(s).media[0];assert.equal(m.source,'steam');assert.equal(m.sourceId,'10');assert.equal(String(m.rawgId),'70');assert.equal(String(m.steamAppId),'10');
});
test('merging two Steam cards retains both account playtime records',()=>{
  const s=library('game'),first='76561198000000001',second='76561198000000002';
  s.media[0].steam={appId:'10',accounts:{[first]:{playtimeMinutes:60,lastPlayedAt:1000}},hasStats:true};
  s.media[1].steam={appId:'10',accounts:{[second]:{playtimeMinutes:120,lastPlayedAt:2000}},hasStats:true};
  const before=structuredClone(s),m=merge(s).media[0];assert.deepEqual(Steam.metadata(m.steam).accounts,{...s.media[0].steam.accounts,...s.media[1].steam.accounts});assert.deepEqual(s,before);
});
test('Steam records for the same account retain the greatest known time and latest play',()=>{
  const s=library('game'),account='76561198000000001';
  s.media[0].steam={appId:'10',accounts:{[account]:{playtimeMinutes:60,lastPlayedAt:4000}}};
  s.media[1].steam={appId:'10',accounts:{[account]:{playtimeMinutes:120,lastPlayedAt:2000}}};
  assert.deepEqual(Steam.metadata(merge(s).media[0].steam).accounts[account],{playtimeMinutes:120,lastPlayedAt:4000});
});
test('secondary personal note and rating survive an empty primary card',()=>{
  const s=library('movie');s.media[1].userNote='Mon commentaire personnel';s.media[1].rating=8;
  const m=merge(s).media[0];assert.equal(m.userNote,'Mon commentaire personnel');assert.equal(m.rating,8);
});
test('both different personal notes survive a confirmed media merge',()=>{
  const s=library('movie');s.media[0].userNote='Première observation';s.media[1].userNote='Deuxième observation';
  const m=merge(s).media[0];assert.match(m.userNote,/Première observation/);assert.match(m.userNote,/Deuxième observation/);
});
test('different progress units cannot be merged into a fabricated maximum',()=>{
  const s=library('game');Object.assign(s.media[0],{progressCurrent:10,progressTotal:100,progressUnit:'%'});Object.assign(s.media[1],{progressCurrent:20,progressTotal:50,progressUnit:'h'});
  assert.throws(()=>merge(s),/progression|unités/i);
});
test('known distinct watch dates retain all known plays after normalizing',()=>{
  const s=library('movie');Object.assign(s.media[0],{watched:true,plays:1,watchHistory:[1000],lastWatchedAt:1000});Object.assign(s.media[1],{watched:true,plays:1,watchHistory:[2000],lastWatchedAt:2000});
  const m=merge(s).media[0];assert.equal(m.plays,2);assert.deepEqual(C.knownWatchHistory(m),[1000,2000]);
});
test('a watched episode is never erased by a skipped duplicate',()=>{
  const s=library();s.episodes=[episode('e-a','a',{watched:true,plays:1,watchHistory:[1000],lastWatchedAt:1000}),episode('e-b','b',{skipped:true})];
  const e=merge(s).episodes[0];assert.equal(e.watched,true);assert.equal(e.skipped,false);assert.deepEqual(C.knownWatchHistory(e),[1000]);
});
test('episode merge keeps primary local ID and secondary rating/note',()=>{
  const s=library();s.episodes=[episode('e-a','a',{rating:null,userNote:''}),episode('e-b','b',{rating:9,userNote:'Note épisode',watched:true,plays:1})];
  const e=merge(s).episodes[0];assert.equal(e.id,'e-a');assert.equal(e.rating,9);assert.equal(e.userNote,'Note épisode');
});
test('known numbered episode is enriched by a duplicate with a provider ID',()=>{
  const s=library();s.episodes=[episode('e-a','a',{watched:true,plays:1}),episode('e-b','b',{tmdbId:501})];
  const out=merge(s);assert.equal(out.episodes.length,1);assert.equal(out.episodes[0].tmdbId,501);assert.equal(out.episodes[0].watched,true);
});
test('TVDB identity joins episodes with unknown numbering without inventing numbers',()=>{
  const s=library();s.episodes=[episode('e-a','a',{season:-1,num:0,seasonUnknown:true,numberUnknown:true,tvdbId:123,watched:true,plays:1}),episode('e-b','b',{tvdbId:123,tmdbId:456})];
  const out=merge(s);assert.equal(out.episodes.length,1);assert.equal(out.episodes[0].season,1);assert.equal(out.episodes[0].num,1);assert.equal(out.episodes[0].seasonUnknown,false);assert.equal(out.episodes[0].numberUnknown,false);
});
test('missing episode numbering never collapses distinct unknown episodes',()=>{
  const s=library();s.episodes=[{id:'e-a',showId:'a',userNote:'Inconnu A'},{id:'e-b',showId:'b',userNote:'Inconnu B'}];
  assert.equal(merge(s).episodes.length,2);
});
test('contradictory TVDB IDs prevent number-based episode fusion',()=>{
  const s=library();s.episodes=[episode('e-a','a',{tvdbId:123}),episode('e-b','b',{tvdbId:456})];
  assert.equal(merge(s).episodes.length,2);
});
test('multiple possible episode matches are kept separate',()=>{
  const s=library();s.episodes=[episode('e-a1','a',{tmdbId:501}),episode('e-a2','a',{tmdbId:502}),episode('e-b','b',{watched:true,plays:1,userNote:'Correspondance ambiguë'})];
  assert.equal(merge(s).episodes.length,3);
});
test('duplicate media IDs cannot cause every secondary row to be removed',()=>{
  const s=library('movie');s.media.push({...s.media[1],title:'Autre fiche'});
  const before=structuredClone(s);assert.throws(()=>merge(s),/identifiant|double/i);assert.deepEqual(s,before);
});
test('a trophy-list identity collision refuses the whole merge with original data intact',()=>{
  const s=library('game');s.gaming.sets=['a','b'].map(mediaId=>G.normalizeSet({id:'set-'+mediaId,mediaId,provider:'manual',externalGameId:'my-game',title:'Liste',platform:'PC',edition:'Original',achievements:[]}));
  G.validateState(s.gaming,s.media);const before=structuredClone(s);assert.throws(()=>merge(s),/relevé|trophée|succès/i);assert.deepEqual(s,before);
});
test('different services and trophy editions stay separate while media references are relinked',()=>{
  const s=library('game');s.gaming.sets=[G.normalizeSet({id:'ps4',mediaId:'a',provider:'psn',externalGameId:'np1',title:'Liste',platform:'PS4',edition:'PS4',achievements:[]}),G.normalizeSet({id:'ps5',mediaId:'b',provider:'psn',externalGameId:'np2',title:'Liste',platform:'PS5',edition:'PS5',achievements:[]})];
  const out=merge(s);assert.deepEqual(out.gaming.sets.map(x=>x.mediaId),['a','a']);assert.deepEqual(out.gaming.sets.map(x=>x.platform),['PS4','PS5']);G.validateState(out.gaming,out.media);
});
test('episodes linked to a movie are identified as orphans and tracked data is preserved',()=>{
  const s=library('movie');s.episodes=[episode('untracked','a'),episode('tracked','b',{userNote:'À conserver'})];
  const report=I.audit(s);assert.equal(report.issues.filter(x=>x.code==='ORPHAN_EPISODE').length,2);assert.deepEqual(I.safeRepair(s).state.episodes.map(e=>e.id),['tracked']);
});
test('safe repair preserves every supported kind of orphan tracking',()=>{
  const s=library();s.episodes=[{watched:true},{skipped:true},{collected:true},{plays:2},{rating:0},{userNote:'Texte'},{watchHistory:[1000]},{lastWatchedAt:1000}].map((fields,i)=>episode('orphan-'+i,'gone',fields));
  const before=structuredClone(s.episodes);assert.deepEqual(I.safeRepair(s).state.episodes,before);
});

// Controlled IndexedDB events, not a browser or a persistence emulator. This fixture
// provides request/commit separation and transaction rollback so aborts and quota-like
// failures can be exercised deterministically without claiming real-device coverage.
function durableFixture() {
  let rows=new Map(),active=false;const pending=[];
  const stats={opened:0,closed:0,transactions:0},faults={};
  const copied=value=>structuredClone(value);
  function startNext(){if(active||!pending.length)return;active=true;pending.shift()();}
  const indexedDB={open(){const request={};stats.opened++;
    const db={objectStoreNames:{contains:()=>true},close(){if(!this.closed){this.closed=true;stats.closed++;}},transaction(name,mode){
      stats.transactions++;
      if(faults.transactionThrow){faults.transactionThrow=false;throw Error('Transaction indisponible');}
      const requests=[];let staged,finished=false;const tx={error:null};
      const end=(aborted,error)=>{
        if(finished)return;finished=true;
        if(aborted){tx.error=error||Error('Arrêt contrôlé');tx.onabort?.();}
        else{if(mode==='readwrite')rows=staged;tx.oncomplete?.();}
        active=false;setImmediate(startNext);
      };
      tx.abort=()=>setImmediate(()=>end(true));
      function requestOperation(kind,value){if(finished)throw Error('Transaction inactive');const request={};requests.push({kind,value,request});return request;}
      const store={getAll:()=>requestOperation('getAll'),put:value=>requestOperation('put',copied(value)),delete:id=>requestOperation('delete',id),clear:()=>requestOperation('clear')};
      tx.objectStore=()=>store;
      function pump(){
        if(finished)return;const op=requests.shift();
        if(!op){const abort=faults.abortCommit;faults.abortCommit=false;end(!!abort,abort?Error('Transaction annulée après la requête'):undefined);return;}
        if(op.kind==='delete'&&faults.deleteFailure){faults.deleteFailure=false;op.request.error=Error('Suppression refusée');op.request.onerror?.();end(true,op.request.error);return;}
        if(op.kind==='getAll')op.request.result=copied([...staged.values()]);
        if(op.kind==='put')staged.set(op.value.id,op.value);
        if(op.kind==='delete')staged.delete(op.value);
        if(op.kind==='clear')staged.clear();
        op.request.onsuccess?.();setImmediate(pump);
      }
      pending.push(()=>{staged=copied(rows);setImmediate(pump);});setImmediate(startNext);return tx;
    }};
    setImmediate(()=>{request.result=db;if(faults.blockedOpen){faults.blockedOpen=false;request.onblocked?.();setImmediate(()=>request.onsuccess?.());}else request.onsuccess?.();});return request;
  }};
  const module={exports:{}};vm.runInNewContext(fs.readFileSync(require.resolve('../web/durable.js'),'utf8'),{module,indexedDB});
  return {api:module.exports,stats,faults,get rows(){return [...rows.values()].map(copied);}};
}
function deadline(promise){let timer;return Promise.race([promise,new Promise((_,reject)=>{timer=setTimeout(()=>reject(Error('Promesse IndexedDB bloquée')),1000);})]).finally(()=>clearTimeout(timer));}

test('durable write rejects an abort event without a separate error event and closes its database',async()=>{
  const f=durableFixture();f.faults.abortCommit=true;
  await assert.rejects(deadline(f.api.put({media:[{id:'a'}]})),/annulée/);assert.equal(f.rows.length,0);assert.equal(f.stats.closed,f.stats.opened);
});
test('durable list waits for commit and rejects a transaction aborted after getAll succeeded',async()=>{
  const f=durableFixture();await f.api.put({media:[{id:'a'}]});f.faults.abortCommit=true;
  await assert.rejects(deadline(f.api.list()),/annulée/);assert.equal(f.rows.length,1);assert.equal(f.stats.closed,f.stats.opened);
});
test('snapshot insertion and five-copy retention roll back together on a deletion failure',async()=>{
  const f=durableFixture();for(let i=0;i<5;i++)await f.api.put({value:i});const original=f.rows;
  f.faults.deleteFailure=true;await assert.rejects(deadline(f.api.put({value:5})),/refusée/);assert.deepEqual(f.rows,original);assert.equal(f.stats.closed,f.stats.opened);
});
test('parallel snapshot requests preserve only the five latest writes in request order',async()=>{
  const f=durableFixture();await Promise.all(Array.from({length:12},(_,i)=>f.api.put({value:i})));
  const out=await f.api.list();assert.deepEqual(Array.from(out,r=>r.state.value),[11,10,9,8,7]);assert.equal((await f.api.latest()).state.value,11);assert.equal(f.stats.closed,f.stats.opened);
});
test('snapshot copies the caller state before its asynchronous transaction',async()=>{
  const f=durableFixture(),state={media:[{id:'a',userNote:'Avant'}]};const saved=f.api.put(state);state.media[0].userNote='Après';await saved;
  assert.equal((await f.api.latest()).state.media[0].userNote,'Avant');
});
test('clear requested after pending writes cannot be undone by their late completion',async()=>{
  const f=durableFixture(),first=f.api.put({value:1}),second=f.api.put({value:2}),clear=f.api.clear();await Promise.all([first,second,clear]);assert.equal(f.rows.length,0);assert.equal((await f.api.list()).length,0);
});
test('failed write does not poison later recovery operations',async()=>{
  const f=durableFixture();f.faults.abortCommit=true;await assert.rejects(deadline(f.api.put({value:1})),/annulée/);await f.api.put({value:2});assert.equal((await f.api.latest()).state.value,2);
});
test('clear and remove aborts report failure without losing stored snapshots',async()=>{
  const f=durableFixture(),saved=await f.api.put({value:1});
  f.faults.abortCommit=true;await assert.rejects(deadline(f.api.remove(saved.id)),/annulée/);assert.equal(f.rows.length,1);
  f.faults.abortCommit=true;await assert.rejects(deadline(f.api.clear()),/annulée/);assert.equal(f.rows.length,1);assert.equal(f.stats.closed,f.stats.opened);
});
test('failure to create a transaction still closes the opened database',async()=>{
  const f=durableFixture();f.faults.transactionThrow=true;await assert.rejects(deadline(f.api.put({value:1})),/indisponible/);assert.equal(f.stats.closed,f.stats.opened);
});
test('blocked open rejects and closes a database which opens after the rejection',async()=>{
  const f=durableFixture();f.faults.blockedOpen=true;await assert.rejects(deadline(f.api.list()),/bloque/);await new Promise(resolve=>setImmediate(resolve));assert.equal(f.stats.closed,f.stats.opened);
});
