'use strict';
/* DOM/logic integration tests, not a browser-origin, PWA or authenticated-API test.
 * jsdom loads the real index and application scripts except startup.js. Its synthetic
 * .invalid URL only enables localStorage. Network responses are explicit fixtures;
 * delayed fixtures may deliberately finish after abort to exercise stale guards.
 * Missing dialog display methods, scrollTo and timer scheduling are controlled here.
 * No storage/backup/domain/commit/render implementation is replaced. IndexedDB is
 * intentionally unavailable: its independent recovery suite covers that fallback.
 * Reproduce: npm ci && node --test tests/retro-orchestration-v014.test.cjs
 */
const {test}=require('node:test');
const A=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const vm=require('node:vm');
const {JSDOM,VirtualConsole}=require('jsdom');
const R=require('../web/retro-core.js');
const G=require('../web/gaming-core.js');
const WEB=path.join(__dirname,'../web');
const KEY='franchiseguide-web-v8',BACKUP_KEY='franchiseguide-web-backups-v2';
const USER='00003EMFWR7XB8SDPEHB3K56ZQ';
const copy=x=>JSON.parse(JSON.stringify(x));
const withoutRuntime=x=>{const value=copy(x);delete value._gamingControl;delete value._revision;delete value._syncState;return value;};
const tick=()=>new Promise(resolve=>setImmediate(resolve));
function deferred(){let resolve,reject;const promise=new Promise((yes,no)=>{resolve=yes;reject=no;});return {promise,resolve,reject};}
function profile(){return {provider:'retro',user:USER,profile:{User:'Fixture_Player',ULID:USER,UserPic:'/UserPic/Fixture_Player.png'},fetchedAt:Date.now()};}
function row(id=1){return {GameID:id,Title:'Jeu fixture '+id,ConsoleID:1,ConsoleName:'Mega Drive / Genesis',ImageIcon:'/Images/000001.png',MaxPossible:3,NumAwarded:2,NumAwardedHardcore:1,MostRecentAwardedDate:'2025-02-28T15:30:00Z',HighestAwardKind:null,HighestAwardDate:null};}
function page(rows,total=rows.length){return {provider:'retro',user:USER,library:{Count:rows.length,Total:total,Results:rows},fetchedAt:Date.now()};}
function detail(id=1){
 const achievement=(n,x={})=>({ID:n,Title:'Succès fixture '+n,Description:'Objectif fixture '+n,Points:5,BadgeName:'000001',DisplayOrder:n,Type:null,...x});
 return {provider:'retro',user:USER,gameId:String(id),fetchedAt:Date.now(),game:{ID:id,Title:'Jeu fixture '+id,ConsoleID:1,ConsoleName:'Mega Drive / Genesis',ParentGameID:null,NumAchievements:3,NumAwardedToUser:2,NumAwardedToUserHardcore:1,Achievements:{
  '1':achievement(1,{DateEarned:'2025-02-28 15:30:00',DateEarnedHardcore:'2025-03-01 16:00:00'}),
  '2':achievement(2,{DateEarned:'2025-02-28 15:30:00'}),'3':achievement(3)
 }}};
}
function account(x={}){return G.normalizeAccount({id:'ra',provider:'retro',externalId:USER,username:'Fixture_Player',label:'Mon profil rétro',autoImport:false,autoSync:false,...x});}
function media(x={}){return {id:'game-local',kind:'game',title:'Ma fiche locale',source:'manual',added:true,favorite:true,userNote:'Note du jeu à conserver',collected:false,watched:false,started:false,...x};}
function set(x={}){return R.achievements(detail(),{id:'ra-set',mediaId:'game-local',accountId:'ra',accountExternalId:USER,provider:'retro',externalGameId:'1',title:'Succès liés',platform:'Mega Drive / Genesis',edition:'RetroAchievements',...x});}
function response(body,status=200,headers={}){return {ok:status>=200&&status<300,status,headers:new Headers(headers),json:async()=>copy(body)};}

function harness(t,{rows=[],initial={}}={}){
 const errors=[],virtualConsole=new VirtualConsole();virtualConsole.on('jsdomError',e=>errors.push(e));
 const dom=new JSDOM(fs.readFileSync(path.join(WEB,'index.html'),'utf8'),{url:'https://fixture.invalid/',runScripts:'outside-only',pretendToBeVisual:true,virtualConsole});
 const w=dom.window,context=dom.getInternalVMContext(),timers=new Map();let timerId=0,now=Date.now();
 w.Date.now=()=>now;
 w.setTimeout=(fn,delay=0,...args)=>{const id=++timerId;timers.set(id,{at:now+Number(delay),fn,args,delay:Number(delay)});return id;};
 w.clearTimeout=id=>timers.delete(id);
 w.setInterval=()=>++timerId;w.clearInterval=()=>{};
 w.AbortController=AbortController;w.AbortSignal=AbortSignal;w.TextDecoder=TextDecoder;w.TextEncoder=TextEncoder;
 w.scrollTo=()=>{};w.confirm=()=>true;
 w.HTMLDialogElement.prototype.showModal=function(){this.setAttribute('open','');};
 w.HTMLDialogElement.prototype.close=function(){if(!this.open)return;this.removeAttribute('open');this.dispatchEvent(new w.Event('close'));};
 const h={w,errors,requests:[],rows:copy(rows),timers,hook:null,run:source=>vm.runInContext(source,context),json:source=>JSON.parse(vm.runInContext('JSON.stringify('+source+')',context)),query:selector=>w.document.querySelector(selector)};
 h.backend=req=>{
  if(req.url.pathname==='/api/gaming/retro/status')return response({provider:'retro',configured:true});
  if(req.url.pathname==='/api/gaming/retro/profile')return response(profile());
  if(req.url.pathname==='/api/gaming/retro/library'){
   const offset=Number(req.url.searchParams.get('offset')),count=Number(req.url.searchParams.get('count'));
   A.ok(Number.isInteger(offset)&&offset>=0);A.ok(Number.isInteger(count)&&count>=1&&count<=500);
   return response(page(h.rows.slice(offset,offset+count),h.rows.length));
  }
  if(req.url.pathname==='/api/gaming/retro/achievements')return response(detail(Number(req.url.searchParams.get('gameId'))));
  throw Error('Unexpected fixture fetch: '+req.url.pathname);
 };
 w.fetch=async(input,options={})=>{
  const req={url:new URL(String(input),w.location.href),options,signal:options.signal};h.requests.push(req);
  A.equal(options.method||'GET','GET','All fixture platform requests must be read-only');
  A.equal(options.credentials,'same-origin');A.equal(options.cache,'no-store');
  return h.hook?await h.hook(req):h.backend(req);
 };
 const scripts=[...w.document.querySelectorAll('script[src]')].map(s=>s.getAttribute('src')).filter(s=>s!=='startup.js');
 for(const script of scripts){A.match(script,/^[a-z0-9-]+\.js$/);vm.runInContext(fs.readFileSync(path.join(WEB,script),'utf8'),context,{filename:script});}
 w.fixtureInitial=copy(initial);
 h.run(`state=migrate({...clone(seed),...fixtureInitial,settings:{...seed.settings,...fixtureInitial.settings,autoBackupEnabled:false,catalogAutoRefresh:false,traktAutoSync:false,detailJingleEnabled:false}});syncState={queue:[],baseline:{},remote:[],errors:[]};save(false);delete window.fixtureInitial;`);
 h.state=()=>h.json('state');h.disk=()=>w.localStorage.getItem(KEY);
 h.waitFor=async predicate=>{for(let i=0;i<100;i++){if(predicate())return;await tick();}A.fail('Expected fixture condition was not reached');};
 h.advance=async ms=>{
  const end=now+ms;let iterations=0;
  for(;;){await tick();const item=[...timers].filter(([,v])=>v.at<=end).sort((a,b)=>a[1].at-b[1].at)[0];if(!item)break;
   A.ok(++iterations<1000,'Controlled timer loop must terminate');const [id,v]=item;timers.delete(id);now=v.at;v.fn(...v.args);
  }
  now=end;await tick();
 };
 h.failStorage=key=>{const proto=Object.getPrototypeOf(w.localStorage),original=proto.setItem;proto.setItem=function(k,value){if(k===key)throw new w.DOMException('Fixture storage full','QuotaExceededError');return original.call(this,k,value);};};
 h.hub=()=>h.run(`overlay={type:'gaming',title:'Trophées & comptes jeux'};render();`);
 h.achievements=()=>h.run(`openGameAchievements('game-local');`);
 h.submit=async()=>{const form=h.query('#gamingForm');A.ok(form,'Dialog form exists');await form.onsubmit({preventDefault(){},target:form});};
 t.after(()=>{dom.window.close();A.deepEqual(errors.map(e=>e.message),[],'No uncaught jsdom/script errors');});
 return h;
}

test('RA DOM association alone stores a stable public profile without a library fetch or SSO claim',async t=>{
 const h=harness(t);h.hub();h.query('#retroAssociate').click();
 A.match(h.query('#gamingDialog').textContent,/ne constitue pas une connexion au compte/);
 h.query('[name=user]').value='Fixture_Player';h.query('[name=import]').checked=false;
 await h.submit();
 const a=h.state().gaming.accounts[0];A.equal(a.externalId,USER);A.equal(a.username,'Fixture_Player');A.equal(a.autoImport,false);A.equal(a.autoSync,false);A.equal(a.authenticated,undefined);
 A.deepEqual(h.requests.map(r=>r.url.pathname),['/api/gaming/retro/status','/api/gaming/retro/profile']);
 A.equal(h.query('#gamingDialog'),null);A.match(h.query('#retroConnectCard').textContent,/elle n’authentifie pas son propriétaire/);
 A.equal(h.state().media.length,0);A.equal(JSON.parse(h.disk()).gaming.accounts[0].externalId,USER);
});

test('RA association form with import checked reads games and then their detailed successes with automation off',async t=>{
 const h=harness(t,{rows:[row(1),row(2)]});h.hub();h.query('#retroAssociate').click();h.query('[name=user]').value='Fixture_Player';
 A.equal(h.query('[name=import]').checked,true);A.equal(h.query('[name=auto]').checked,false);await h.submit();
 await h.waitFor(()=>!h.run('gamingAutoRunning')&&h.state().gaming.sets.length===2&&h.state().gaming.sets.every(s=>s.lastSyncAt));
 A.equal(h.query('#gamingDialog'),null);A.equal(h.state().gaming.accounts[0].autoImport,false);A.equal(h.state().gaming.accounts[0].autoSync,false);
 A.deepEqual(h.requests.filter(r=>r.url.pathname.endsWith('/achievements')).map(r=>r.url.searchParams.get('gameId')),['1','2']);
 A.ok(h.state().gaming.sets.every(s=>s.source==='retro-api'&&s.achievements.length===3&&s.achievements.filter(a=>a.earned).length===2&&s.achievements.filter(a=>a.hardcoreEarned).length===1));
 A.equal(h.run('gamingRequests.size'),0);A.ok(JSON.parse(h.disk()).gaming.sets.every(s=>s.lastSyncAt));
});

test('RA async paging stages 501 games until the final validated page and persists a complete portable library',async t=>{
 const h=harness(t,{rows:Array.from({length:501},(_,i)=>row(i+1)),initial:{media:[media({source:'retro',sourceId:'1'})]}}),last=deferred(),before=h.state(),disk=h.disk();
 h.hook=req=>req.url.pathname.endsWith('/library')&&Number(req.url.searchParams.get('offset'))>0?last.promise:h.backend(req);
 const pending=h.run(`importRetroLibrary({user:'Fixture_Player',autoImport:false,autoSync:false})`);
 await h.waitFor(()=>h.requests.some(r=>r.url.pathname.endsWith('/library')&&Number(r.url.searchParams.get('offset'))>0));
 A.deepEqual(h.state(),before);A.equal(h.disk(),disk);
 const req=h.requests.at(-1),offset=Number(req.url.searchParams.get('offset')),count=Number(req.url.searchParams.get('count'));
 A.equal(offset,500);last.resolve(response(page(h.rows.slice(offset,offset+count),h.rows.length)));
 A.equal(await pending,true);A.equal(h.run('retroLibraryBusy'),false);
 const state=h.state();A.equal(state.media.length,501);A.equal(state.gaming.accounts.length,1);A.equal(state.gaming.sets.length,501);
 A.equal(state.media.find(m=>m.id==='game-local').userNote,'Note du jeu à conserver');A.equal(state.media.find(m=>m.id==='game-local').favorite,true);
 A.ok(state.media.every(m=>m.watched===false&&m.collected===false&&m.started===false));
 A.ok(state.gaming.sets.every(s=>s.provider==='retro'&&s.completeCatalog===false&&s.achievements.length===0));
 const libraryRequests=h.requests.filter(r=>r.url.pathname.endsWith('/library'));
 A.equal(libraryRequests.length,2);A.ok(libraryRequests.every(r=>r.url.searchParams.get('user')===USER));
 A.ok(h.requests.every(r=>!r.url.pathname.endsWith('/achievements')));
 A.equal(h.json('retroLastReport').added,500);A.equal(h.json('retroLastReport').updated,1);
 A.equal(JSON.parse(h.disk()).media.length,501);h.run('FGCore.validateBackup(toPortableBackup(state))');
 const old=JSON.parse(h.w.localStorage.getItem(BACKUP_KEY))[0].state;A.equal(old.media.length,1);A.equal(old.gaming.accounts.length,0);
});

test('RA duplicate ID across paged responses refuses the whole import without an early commit',async t=>{
 const rows=Array.from({length:501},(_,i)=>row(i===500?1:i+1)),h=harness(t,{rows}),before=h.state(),disk=h.disk();
 await A.rejects(h.run(`importRetroLibrary({user:'Fixture_Player',initial:true})`));
 A.deepEqual(h.state(),before);A.equal(h.disk(),disk);A.equal(h.run('retroLibraryBusy'),false);A.equal(h.w.localStorage.getItem(BACKUP_KEY),null);
});

test('RA visible cancellation discards a late library response without saving a partial import',async t=>{
 const h=harness(t,{rows:[row()]}),gate=deferred(),before=h.state(),disk=h.disk();h.hub();
 h.hook=req=>req.url.pathname.endsWith('/library')?gate.promise:h.backend(req);
 const pending=h.run(`importRetroLibrary({user:'Fixture_Player'})`);await h.waitFor(()=>h.requests.some(r=>r.url.pathname.endsWith('/library')));
 const req=h.requests.at(-1);h.query('#retroCancelLibrary').click();A.equal(req.signal.aborted,true);
 gate.resolve(response(page([row()])));A.equal(await pending,false);
 A.deepEqual(h.state(),before);A.equal(h.disk(),disk);A.equal(h.run('retroLibraryBusy'),false);A.equal(h.run('retroLibraryController'),null);
 A.match(h.query('#retroSyncNotice').textContent,/interrompue|annulée/);
});

test('RA a real portable replacement invalidates an in-flight library import',async t=>{
 const h=harness(t,{rows:[row()]}),gate=deferred();h.hook=req=>req.url.pathname.endsWith('/library')?gate.promise:h.backend(req);
 const pending=h.run(`importRetroLibrary({user:'Fixture_Player'})`);await h.waitFor(()=>h.requests.some(r=>r.url.pathname.endsWith('/library')));
 h.run(`const fixturePortable=toPortableBackup(state);prepareLibraryReplacement('Fixture restore');state=fromPortableBackup(fixturePortable);state.lists.push({id:'replacement',name:'Bibliothèque restaurée',description:'',sortMode:'title',ascending:true});save(false);`);
 const replacement=h.state(),disk=h.disk();A.equal(h.requests.at(-1).signal.aborted,true);
 gate.resolve(response(page([row()])));A.equal(await pending,false);A.deepEqual(h.state(),replacement);A.equal(h.disk(),disk);
});

test('RA removing a profile through the hub prevents a late library result from recreating it',async t=>{
 const h=harness(t,{rows:[row()],initial:{media:[media()],gaming:{version:1,accounts:[account()],sets:[set()]}}}),gate=deferred();h.hub();
 h.hook=req=>req.url.pathname.endsWith('/library')?gate.promise:h.backend(req);
 const pending=h.run(`importRetroLibrary({accountId:'ra'})`);await h.waitFor(()=>h.requests.some(r=>r.url.pathname.endsWith('/library')));
 h.query('[data-account-remove="ra"]').click();const removed=h.state(),disk=h.disk();
 A.equal(removed.gaming.accounts.length,0);A.equal(removed.gaming.sets.length,0);A.equal(removed.media.length,1);
 gate.resolve(response(page([row()])));A.equal(await pending,false);A.deepEqual(h.state(),removed);A.equal(h.disk(),disk);
});

test('RA refusing the safety snapshot stops the import before touching the live library',async t=>{
 const h=harness(t,{rows:[row()]}),before=h.state(),disk=h.disk();h.failStorage(BACKUP_KEY);
 A.equal(await h.run(`importRetroLibrary({user:'Fixture_Player'})`),false);
 A.deepEqual(h.state(),before);A.equal(h.disk(),disk);A.equal(h.run('storageDraft'),null);A.match(h.run('retroNotice'),/Copie de sécurité impossible/);
});

test('RA refusing the main save preserves disk and saved state while retaining a recoverable import draft',async t=>{
 const h=harness(t,{rows:[row()]}),before=h.state(),disk=h.disk();h.failStorage(KEY);
 A.equal(await h.run(`importRetroLibrary({user:'Fixture_Player'})`),false);
 A.deepEqual(h.state(),before);A.equal(h.disk(),disk);
 const draft=h.json('storageDraft');A.equal(draft.media.length,1);A.equal(draft.gaming.accounts.length,1);A.equal(draft.gaming.sets.length,1);
 A.ok(h.query('#storageBanner'));A.ok(h.query('#exportDraft'));A.equal(h.run('retroLibraryBusy'),false);
 A.equal(JSON.parse(h.w.localStorage.getItem(BACKUP_KEY))[0].state.media.length,0);
 A.ok(h.requests.every(r=>!r.url.pathname.endsWith('/achievements')));
});

test('RA direct game link submits an exact game ID even when the public progression library is empty',async t=>{
 const h=harness(t,{initial:{media:[media()],gaming:{version:1,accounts:[account()],sets:[]}}});h.achievements();
 h.query('#linkRetroGame').click();h.query('[name=gameId]').value='77';await h.submit();
 A.equal(h.query('#gamingDialog'),null);A.equal(h.state().media.length,1);A.equal(h.state().media[0].source,'manual');
 const linked=h.state().gaming.sets[0];A.equal(linked.mediaId,'game-local');A.equal(linked.externalGameId,'77');A.equal(linked.accountId,'ra');A.equal(linked.source,'retro-api');A.equal(linked.achievements.length,3);
 A.equal(linked.achievements.filter(a=>a.earned).length,2);A.equal(linked.achievements.filter(a=>a.hardcoreEarned).length,1);A.ok(linked.achievements.every(a=>a.grade==='achievement'));
 A.deepEqual(h.requests.map(r=>r.url.pathname),['/api/gaming/retro/achievements']);
 A.equal(h.requests[0].url.searchParams.get('user'),USER);A.equal(h.requests[0].url.searchParams.get('gameId'),'77');
 h.run('FGCore.validateBackup(toPortableBackup(state))');A.match(h.query('#achievementSetBody').textContent,/Mode normal : 2 \/ 3 · Hardcore : 1 \/ 3/);
});

test('RA closing the direct-link dialog aborts its read and refuses a response that still arrives',async t=>{
 const h=harness(t,{initial:{media:[media()],gaming:{version:1,accounts:[account()],sets:[]}}}),gate=deferred(),before=h.state(),disk=h.disk();h.achievements();
 h.hook=req=>req.url.pathname.endsWith('/achievements')?gate.promise:h.backend(req);h.query('#linkRetroGame').click();h.query('[name=gameId]').value='77';
 const pending=h.submit();await h.waitFor(()=>h.requests.some(r=>r.url.pathname.endsWith('/achievements')));h.query('#gamingClose').click();
 A.equal(h.requests.at(-1).signal.aborted,true);gate.resolve(response(detail(77)));await pending;
 A.deepEqual(h.state(),before);A.equal(h.disk(),disk);A.equal(h.w.localStorage.getItem(BACKUP_KEY),null);A.equal(h.query('#gamingDialog'),null);
});

test('RA refresh merges personal UI edits made while the detailed response is still in flight',async t=>{
 const h=harness(t,{initial:{media:[media()],gaming:{version:1,accounts:[account()],sets:[set()]}}}),gate=deferred();h.achievements();
 h.hook=req=>req.url.pathname.endsWith('/achievements')?gate.promise:h.backend(req);
 const pending=h.run(`syncRetroSet('ra-set')`);await h.waitFor(()=>h.requests.some(r=>r.url.pathname.endsWith('/achievements')));
 h.query('[data-ach-pin="1"]').click();h.query('[data-ach-note="1"]').click();
 h.query('[name=done]').checked=true;h.query('[name=note]').value='Note saisie pendant la lecture';h.query('[name=guide]').value='https://example.org/guide';await h.submit();
 const fresh=detail();fresh.game.Achievements['1'].Title='Intitulé fournisseur actualisé';gate.resolve(response(fresh));A.equal(await pending,true);
 const a=h.state().gaming.sets[0].achievements[0];A.equal(a.title,'Intitulé fournisseur actualisé');A.equal(a.note,'Note saisie pendant la lecture');A.equal(a.localDone,true);A.equal(a.pinned,true);A.equal(a.guideUrl,'https://example.org/guide');
 A.equal(h.state().media[0].userNote,'Note du jeu à conserver');A.equal(h.state().media[0].favorite,true);A.equal(h.state().media[0].watched,false);
 A.equal(h.run('gamingRequests.size'),0);A.equal(JSON.parse(h.disk()).gaming.sets[0].achievements[0].note,a.note);
});

test('RA deleting a profile prevents its late achievement response from resurrecting lists',async t=>{
 const h=harness(t,{initial:{media:[media()],gaming:{version:1,accounts:[account()],sets:[set()]}}}),gate=deferred();h.hub();
 h.hook=req=>req.url.pathname.endsWith('/achievements')?gate.promise:h.backend(req);
 const pending=h.run(`syncRetroSet('ra-set')`);await h.waitFor(()=>h.requests.some(r=>r.url.pathname.endsWith('/achievements')));
 h.query('[data-account-remove="ra"]').click();const removed=h.state(),disk=h.disk();gate.resolve(response(detail()));
 A.equal(await pending,false);A.deepEqual(h.state(),removed);A.equal(h.disk(),disk);A.equal(h.run('gamingRequests.size'),0);
});

test('RA achievement save refusal keeps the previous successful snapshot and the detailed recovery draft',async t=>{
 const h=harness(t,{initial:{media:[media()],gaming:{version:1,accounts:[account()],sets:[set()]}}}),before=h.state(),disk=h.disk();
 const fresh=detail();fresh.game.Achievements['1'].Title='Titre non encore enregistré';
 h.hook=req=>req.url.pathname.endsWith('/achievements')?response(fresh):h.backend(req);h.failStorage(KEY);
 A.equal(await h.run(`syncRetroSet('ra-set')`),false);A.deepEqual(h.state(),before);A.equal(h.disk(),disk);
 A.equal(h.json('storageDraft').gaming.sets[0].achievements[0].title,'Titre non encore enregistré');A.equal(h.run('gamingRequests.size'),0);A.ok(h.query('#exportDraft'));
});

test('RA DOM filters distinguish Hardcore true, false and unknown while normal filters keep their meaning',t=>{
 const imported=set();imported.source='file-import';imported.lastSyncAt=null;imported.achievements[2].hardcoreEarned=null;
 const h=harness(t,{initial:{media:[media()],gaming:{version:1,accounts:[account()],sets:[G.normalizeSet(imported)]}}});h.achievements();
 const ids=()=>[...h.w.document.querySelectorAll('[data-achievement]')].map(el=>el.dataset.achievement);
 const filter=value=>{const select=h.query('#achievementState');select.value=value;select.dispatchEvent(new h.w.Event('change'));return ids();};
 A.deepEqual(ids(),['1','2','3']);A.match(h.query('#achievementSetBody').textContent,/Mode normal : 2 \/ 3 · Hardcore : 1 \/ 3 · 1 état\(s\) Hardcore inconnu/);
 A.deepEqual(filter('hardcore-earned'),['1']);A.match(h.query('.hardcore-state').textContent,/Hardcore : obtenu/);
 A.deepEqual(filter('hardcore-remaining'),['2','3']);A.match(h.query('[data-achievement="2"] .hardcore-state').textContent,/non obtenu/);
 A.deepEqual(filter('hardcore-unknown'),['3']);A.equal(h.query('.hardcore-state').textContent,'Hardcore : inconnu');
 A.deepEqual(filter('unknown'),[]);A.deepEqual(filter('earned'),['1','2']);A.deepEqual(filter('locked'),['3']);
});

test('RA an explicitly empty catalogue names RetroAchievements and remains visible in the unfinished view',t=>{
 const raw=detail();Object.assign(raw.game,{NumAchievements:0,NumAwardedToUser:0,NumAwardedToUserHardcore:0,Achievements:{}});
 const empty=R.achievements(raw,{id:'ra-set',mediaId:'game-local',accountId:'ra',accountExternalId:USER,provider:'retro',externalGameId:'1',title:'Aucun catalogue',platform:'Mega Drive / Genesis'});
 const h=harness(t,{initial:{media:[media()],gaming:{version:1,accounts:[account()],sets:[empty]}}});h.achievements();
 A.match(h.query('#achievementSetBody .notice').textContent,/^RetroAchievements ne fournit aucun catalogue/);
 const hidden=h.query('#hideCompletedTrophies');hidden.checked=true;hidden.dispatchEvent(new h.w.Event('change'));
 A.equal(h.query('#achievementSetSelect').value,'ra-set');A.equal(h.query('#achievementRows').querySelectorAll('[data-achievement]').length,0);A.equal(h.run("FGGaming.progress(gamingState().sets[0]).completed"),false);
});

test('RA Hardcore filter choices are absent from a manual checklist',t=>{
 const manual=G.normalizeSet({id:'local-set',mediaId:'game-local',provider:'manual',externalGameId:'local-fixture',title:'Checklist personnelle',platform:'Personnel',source:'manual',groups:[{id:'base',name:'Objectifs',kind:'base'}],achievements:[]});
 const h=harness(t,{initial:{media:[media()],gaming:{version:1,accounts:[],sets:[manual]}}});h.achievements();
 A.ok(![...h.query('#achievementState').options].some(option=>option.value.startsWith('hardcore-')));A.equal(h.query('.hardcore-state'),null);
});

test('RA pagination keeps validated earlier pages during 429 backoff and resumes the same offset',async t=>{
 const h=harness(t,{rows:Array.from({length:501},(_,i)=>row(i+1))});let refused=false;
 h.hook=req=>{if(req.url.pathname.endsWith('/library')&&Number(req.url.searchParams.get('offset'))>0&&!refused){refused=true;return response({provider:'retro',error:'Fixture rate limit'},429,{'Retry-After':'60'});}return h.backend(req);};
 const before=h.state(),disk=h.disk(),pending=h.run(`importRetroLibrary({user:'Fixture_Player'})`);
 await h.waitFor(()=>h.run('retroNotice').includes('Limite de lecture'));A.deepEqual(withoutRuntime(h.state()),withoutRuntime(before));A.ok(h.json('gamingControl()').providers.some(p=>p.id==='retro'));A.deepEqual(withoutRuntime(JSON.parse(h.disk())),withoutRuntime(JSON.parse(disk)));
 await h.advance(59999);A.equal(h.requests.filter(r=>r.url.pathname.endsWith('/library')).length,2);
 await h.advance(1);A.equal(await pending,true);
 A.deepEqual(h.requests.filter(r=>r.url.pathname.endsWith('/library')).map(r=>Number(r.url.searchParams.get('offset'))),[0,500,500]);
 A.equal(h.state().media.length,501);A.equal(h.state().gaming.sets.length,501);A.equal(h.run('retroLibraryBusy'),false);
});

test('RA cancelling a 429 backoff removes its scheduled retry and leaves the saved library intact',async t=>{
 const h=harness(t),before=h.state(),disk=h.disk();h.hub();h.hook=req=>req.url.pathname.endsWith('/library')?response({provider:'retro',error:'Fixture rate limit'},429,{'Retry-After':'60'}):h.backend(req);
 const pending=h.run(`importRetroLibrary({user:'Fixture_Player'})`);await h.waitFor(()=>h.run('retroNotice').includes('Limite de lecture'));
 h.query('#retroCancelLibrary').click();A.equal(await pending,false);await h.advance(180000);
 A.equal(h.requests.filter(r=>r.url.pathname.endsWith('/library')).length,1);A.deepEqual(withoutRuntime(h.state()),withoutRuntime(before));A.deepEqual(withoutRuntime(JSON.parse(h.disk())),withoutRuntime(JSON.parse(disk)));A.ok(h.json('gamingControl()').providers.some(p=>p.id==='retro'));A.equal(h.run('retroLibraryBusy'),false);
});

test('RA 429 retries are bounded and never create a partial account or library',async t=>{
 const h=harness(t),before=h.state(),disk=h.disk();h.hook=req=>req.url.pathname.endsWith('/library')?response({provider:'retro',error:'Fixture rate limit'},429,{'Retry-After':'60'}):h.backend(req);
 const pending=h.run(`importRetroLibrary({user:'Fixture_Player'})`);await h.waitFor(()=>h.run('retroNotice').includes('Limite de lecture'));
 await h.advance(120000);A.equal(await pending,false);
 A.equal(h.requests.filter(r=>r.url.pathname.endsWith('/library')).length,3);A.deepEqual(withoutRuntime(h.state()),withoutRuntime(before));A.deepEqual(withoutRuntime(JSON.parse(h.disk())),withoutRuntime(JSON.parse(disk)));A.ok(h.json('gamingControl()').providers.some(p=>p.id==='retro'));A.equal(h.run('retroLibraryBusy'),false);
});
