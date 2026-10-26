"""Real Chromium DOM and shipped JS/CSS. All providers, images, credentials and storage are synthetic.
Exercises explicit foreground coordination, never a real Steam/Retro/Trakt account.
"""
from pathlib import Path
from playwright.sync_api import sync_playwright
from browser_harness import SETUP,RESET,ROOT,WEB
import json,re,traceback
OUT=ROOT/'tests/results/v015';OUT.mkdir(parents=True,exist_ok=True)
checks=[]
def check(name,value=True):
 if not value:raise AssertionError(name)
 checks.append({'name':name,'passed':True});print('PASS',name,flush=True)
EXTRA=r'''() => {
 const baseline=window.fetch;const realNow=Date.now;window.realTime=realNow;qa.clock=realNow();Date.now=()=>qa.clock;
 qa.syncCalls=[];qa.mode='ok';qa.retroMode='ok';qa.retroWaitTimers=[];qa.libFail=false;qa.libRows=0;qa.steamId='76561198000000001';qa.sessionAuthenticated=true;qa.holdSession=false;qa.sessionWaiters=[];qa.holdRead=false;qa.readWaiters=[];qa.newTimers=[];
 const timeout=window.setTimeout;window.setTimeout=(f,n,...args)=>{if(n===50000){qa.newTimers.push(()=>f(...args));return -qa.newTimers.length;}if(n===60000){qa.retroWaitTimers.push(()=>f(...args));return -10000-qa.retroWaitTimers.length;}return timeout(f,n,...args);};
 const reply=(x,status=200,header={})=>new Response(JSON.stringify(x),{status,headers:{'Content-Type':'application/json',...header}});
 qa.steam=(id)=>({provider:'steam',appid:String(id),steamid:qa.steamId,schema:{game:{gameName:'Fixture',availableGameStats:{achievements:[{name:'A',displayName:'Succès fictif public',hidden:0,description:'Aucune donnée de compte réel.'}]}}},player:{playerstats:{success:true,steamID:qa.steamId,achievements:[{apiname:'A',achieved:1,unlocktime:1700000000}]}},rarity:{achievementpercentages:{achievements:[{name:'A',percent:10}]}}});
 qa.retro=(id)=>({provider:'retro',fetchedAt:qa.clock,user:'00003EMFWR7XB8SDPEHB3K56ZQ',gameId:String(id),game:{ID:Number(id),Title:'Jeu rétro fictif',ConsoleID:1,ConsoleName:'Console test',NumAchievements:1,NumAwardedToUser:1,NumAwardedToUserHardcore:0,Achievements:{'1':{ID:1,Title:'Succès rétro fictif',Description:'Objectif de test.',Points:5,BadgeName:'000001',DisplayOrder:1,DateEarned:'2025-02-28 15:30:00'}}}});
 window.fetch=async(u,opt={})=>{
  const path=String(u);if(!path.startsWith('/api/gaming/'))return baseline(u,opt);
  qa.syncCalls.push({path,method:opt.method||'GET'});
  if(path==='/api/gaming/status')return reply({app:'FranchiseGuide Gaming Bridge',steam:true,steamSignIn:true});
  if(path.includes('/retro/status'))return reply({provider:'retro',configured:true});
  if(path.includes('/retro/profile'))return reply({provider:'retro',user:'00003EMFWR7XB8SDPEHB3K56ZQ',fetchedAt:qa.clock,profile:{User:'Fixture_Player',ULID:'00003EMFWR7XB8SDPEHB3K56ZQ',UserPic:'/UserPic/Fixture_Player.png'}});
  if(path.includes('/retro/library')){const u=new URL(path,'http://fixture'),off=Number(u.searchParams.get('offset')||0),count=Number(u.searchParams.get('count')||500);if(qa.libFail&&off>=qa.libFailAt){if(qa.libFail==='once')qa.libFail=false;return reply({provider:'retro',error:'Limite de pagination de test'},429,{'Retry-After':'60'});}const rows=Array.from({length:Math.max(0,Math.min(count,qa.libRows-off))},(_,i)=>({GameID:off+i+1,Title:'Jeu rétro fixture '+(off+i+1),ConsoleID:1,ConsoleName:'Console test',ImageIcon:null,MaxPossible:1,NumAwarded:0,NumAwardedHardcore:0,MostRecentAwardedDate:null,HighestAwardKind:null,HighestAwardDate:null}));return reply({provider:'retro',user:'00003EMFWR7XB8SDPEHB3K56ZQ',fetchedAt:qa.clock,library:{Count:rows.length,Total:qa.libRows,Results:rows}});}

  if(path.endsWith('/session')){const result={authenticated:qa.sessionAuthenticated,steamid:qa.steamId,expiresAt:qa.clock+3600000};if(qa.holdSession)await new Promise(resolve=>qa.sessionWaiters.push(resolve));return reply(result);}
  if(path.endsWith('/disconnect')){qa.sessionAuthenticated=false;return reply({authenticated:false});}
  if(path.includes('/retro/achievements')){if(qa.retroMode==='429')return reply({error:'Limite rétro de test'},429,{'Retry-After':'180'});return reply(qa.retro(new URL(path,'http://fixture').searchParams.get('gameId')));}
  if(path.startsWith('/api/gaming/steam?')||path.startsWith('/api/gaming/steam/achievements?')){
   const value=qa.steam(new URL(path,'http://fixture').searchParams.get('appid'));
   if(qa.holdRead)await new Promise((resolve,reject)=>{qa.readWaiters.push(resolve);if(qa.rejectOnAbort)opt.signal.addEventListener('abort',()=>reject(Error('Timed out fixture')),{once:true});});
   if(qa.mode==='429')return reply({error:'Limite de test'},429,{'Retry-After':'120'});
   if(qa.mode==='date429')return reply({error:'Limite de test'},429,{'Retry-After':new Date(qa.clock+12*60000).toUTCString()});
   if(qa.mode==='503')return reply({error:'Indisponible fixture'},503);
   return reply(value);
  }
  return reply({error:'Unlisted local fixture'},503);
 };
 window.qaSeed=(count=3)=>{
  stopGamingQueue();invalidateGamingReads();gamingAutoRunning=false;gamingCurrentSet=null;gamingContinuous=false;gamingNextBatchAt=0;gamingRequests.clear();gamingRetry.clear();
  qa.clock=realNow();qa.mode='ok';qa.retroMode='ok';qa.retroWaitTimers=[];qa.libFail=false;qa.libRows=0;qa.holdRead=false;qa.readWaiters=[];qa.newTimers=[];qa.rejectOnAbort=false;qa.holdSession=false;qa.sessionWaiters=[];qa.syncCalls=[];qa.sessionAuthenticated=true;
  localStorage.clear();storageRevision=0;storageWritable=true;recoveryOnly=null;storageDraft=null;storedSyncSnapshot=null;state=clone(seed);state.settings.autoBackupEnabled=false;state.settings.catalogAutoRefresh=false;state.settings.traktAutoSync=false;state.settings.detailJingleEnabled=false;
  syncState={queue:[],baseline:{},remote:[],errors:[]};state.media=[{id:'g',kind:'game',title:'Jeu de validation du centre',source:'manual',added:true,watched:false,listIds:[],genres:[],ratings:[],credits:[]}];
  state.gaming={version:1,accounts:[{id:'a',provider:'steam',externalId:qa.steamId,label:'Profil Steam fictif',autoSync:true,autoImport:false}],sets:Array.from({length:count},(_,i)=>({id:'s'+String(i).padStart(3,'0'),mediaId:'g',provider:'steam',accountId:'a',externalGameId:String(i+1),title:'Liste fictive '+String(i+1).padStart(3,'0'),platform:'PC (Steam)',edition:'Edition '+(i+1),groups:[{id:'unknown',name:'Périmètre inconnu',kind:'unknown'}],achievements:[],source:'file-import',lastSyncAt:null,completeCatalog:false}))};
  state=migrate(state);gamingBridge={checked:Date.now(),steam:true,steamSignIn:true,message:'Fixture configurée'};retroBridge={checked:Date.now(),configured:true,message:'Fixture configurée'};
  steamConnection={authenticated:true,checked:true,checkedAt:Date.now(),steamid:qa.steamId,expiresAt:Date.now()+3600000};steamDisconnecting=false;steamSessionRevision++;steamLibraryBusy=false;retroLibraryBusy=false;
  savedState=clone(state);save(false);screen='games';overlay={type:'sync-center',title:'Centre de synchronisation'};render();
 };
}'''
def run():
 with sync_playwright() as p:
  browser=p.chromium.launch(executable_path='/usr/bin/chromium',headless=True,args=['--no-sandbox']);ctx=browser.new_context(viewport={'width':1360,'height':980},locale='fr-FR',timezone_id='Europe/Paris');page=ctx.new_page();page.set_default_timeout(7000)
  errors=[];page.on('pageerror',lambda e:errors.append(str(e)));page.on('dialog',lambda d:d.accept())
  html=(WEB/'index.html').read_text();page.set_content(re.sub(r'<script.*?</script>|<link[^>]+>|<meta http-equiv="Content-Security-Policy"[^>]+>','',html));page.add_style_tag(content=(WEB/'app.css').read_text());page.evaluate(SETUP)
  for f in re.findall(r'<script src="([^"]+)"',html):page.add_script_tag(content=(WEB/f).read_text())
  page.wait_for_timeout(40);page.evaluate(RESET);page.evaluate(EXTRA);page.evaluate('()=>qaSeed(18)')
  page.evaluate("()=>{screen='more';overlay=null;render();}");page.locator('[data-quick="sync-center"]').click()
  check('New center is reachable from Plus',page.locator('#startGamingAll').is_visible())
  check('All known lists counted before starting',page.locator('.sync-list-row').count()==18)
  check('No requests just for opening local center',page.evaluate('qa.syncCalls.length')==0)
  page.locator('#startGamingAll').click();page.wait_for_function('gamingControl().checkpoint?.ok===8&&!gamingAutoRunning')
  check('First foreground pass processes at most eight',page.evaluate('gamingControl().checkpoint.pending.length')==10)
  check('Eight requests use read-only provider paths',page.evaluate('qa.syncCalls.length===8&&qa.syncCalls.every(c=>c.method==="GET")'))
  check('Checkpoint persisted with remaining reads',page.evaluate('JSON.parse(localStorage.getItem(KEY))._gamingControl.checkpoint.pending.length')==10)
  page.evaluate('async()=>{gamingNextBatchAt=0;await pumpGamingQueue();}')
  check('Next pass continues rather than rereading first eight',page.evaluate('gamingControl().checkpoint.ok===16&&gamingControl().checkpoint.pending.length===2'))
  page.evaluate('async()=>{gamingNextBatchAt=0;await pumpGamingQueue();}')
  check('Manual full run settles all 18 once',page.evaluate('gamingControl().checkpoint.ok===18&&gamingControl().checkpoint.failed===0&&gamingControl().checkpoint.pending.length===0&&!gamingContinuous'))
  check('No duplicated achievement requests',page.evaluate('new Set(qa.syncCalls.map(c=>c.path)).size')==18)
  check('Provider reads do not alter Trakt journal or game completion',page.evaluate('syncState.queue.length===0&&!state.media[0].watched'))
  check('Completed list stored with declared provider data',page.evaluate('state.gaming.sets.every(s=>s.source==="steam-api"&&s.achievements[0].earned===true)'))
  # Persisted checkpoint does not restart a user run merely because an automatic tick occurs.
  page.evaluate('()=>qaSeed(10)');page.locator('#startGamingAll').click();page.wait_for_function('gamingControl().checkpoint?.ok===8&&!gamingAutoRunning');page.locator('#pauseGamingBatch').click()
  check('Pause leaves two pending reads and eight results',page.evaluate('!gamingContinuous&&gamingControl().checkpoint.pending.length===2&&gamingControl().checkpoint.ok===8'))
  snapshot=page.evaluate('localStorage.getItem(KEY)');calls=page.evaluate('qa.syncCalls.length')
  page.evaluate('async()=>{state=migrate(JSON.parse(localStorage.getItem(KEY)));savedState=clone(state);gamingContinuous=false;await runGamingSync(true);render();}')
  check('Restored checkpoint never resumes on automatic tick',page.evaluate('qa.syncCalls.length')==calls)
  check('Restored checkpoint has explicit resume button',page.locator('#resumeGamingBatch').is_enabled())
  page.locator('#resumeGamingBatch').click();page.wait_for_function('gamingControl().checkpoint?.ok===10')
  check('Explicit resume reads only remaining lists',page.evaluate('qa.syncCalls.length')==10)
  # Pauses survive durable store and direct manual controls cannot bypass them.
  page.evaluate('()=>qaSeed(2)');page.locator('[data-sync-pause="s000"]').click();page.evaluate('async()=>await syncGamingSet("s000",false)')
  check('Individual pause prevents direct manual fetch',page.evaluate('qa.syncCalls.length')==0)
  check('Paused preference durable through JSON',page.evaluate('FGSyncPolicy.rows(state.gaming,JSON.parse(localStorage.getItem(KEY))._gamingControl,{now:Date.now()})[0].code')=='paused')
  page.locator('#pauseAllGaming').click();page.evaluate('async()=>await runGamingSync(true)')
  check('Global pause prevents automatic reads',page.evaluate('qa.syncCalls.length')==0)
  check('Global pause is persisted',page.evaluate('JSON.parse(localStorage.getItem(KEY))._gamingControl.pauseAll'))
  page.locator('#pauseAllGaming').click();page.locator('[data-sync-pause="s000"]').click();page.evaluate('async()=>await syncGamingSet("s000",false)')
  check('Explicit unpause makes list readable',page.evaluate('state.gaming.sets[0].lastSyncAt') is not None)
  # Single provider cooldown applies to another account/list and survives reload.
  page.evaluate('()=>qaSeed(3)');page.evaluate('()=>{qa.mode="429";}');page.evaluate('async()=>await runGamingSync(false)')
  check('429 stops subsequent Steam requests within the same pass',page.evaluate('qa.syncCalls.length')==1)
  check('429 provider deadline durably stored',page.evaluate('JSON.parse(localStorage.getItem(KEY))._gamingControl.providers[0].retryAt>Date.now()'))
  page.evaluate('async()=>await syncGamingSet("s001",false)')
  check('Manual single-list button cannot bypass provider cooldown',page.evaluate('qa.syncCalls.length')==1)
  page.evaluate('async()=>{state=migrate(JSON.parse(localStorage.getItem(KEY)));savedState=clone(state);gamingRetry.clear();await runGamingSync(false);}')
  check('Reloading does not clear the rate limit',page.evaluate('qa.syncCalls.length')==1)
  page.evaluate('async()=>{qa.clock+=121000;qa.mode="ok";await runGamingSync(false);}')
  check('Reads resume after deadline without losing prior list bindings',page.evaluate('state.gaming.sets.every(s=>s.achievements.length===1)&&qa.syncCalls.filter(c=>c.path.startsWith("/api/gaming/steam?")).length===4'))
  # HTTP date headers and timeout path.
  page.evaluate('()=>qaSeed(1)');page.evaluate('()=>{qa.mode="date429";}');page.evaluate('async()=>await syncGamingSet("s000",false)')
  check('Absolute Retry-After date honored in runtime',page.evaluate('gamingControl().providers[0].retryAt-Date.now()')>=11*60000)
  page.evaluate('()=>qaSeed(1)');page.evaluate('()=>{qa.holdRead=true;qa.rejectOnAbort=true;void syncGamingSet("s000",false);}')
  page.wait_for_function('qa.readWaiters.length===1');page.evaluate('()=>qa.newTimers.at(-1)()');page.wait_for_function('gamingRequests.size===0')
  check('Timeout persists failure and backoff',page.evaluate('gamingControl().lists[0].failures===1&&gamingControl().lists[0].retryAt>Date.now()'))
  check('Timeout message remains visible rather than falsely successful','Délai Steam dépassé' in page.evaluate('state.gaming.sets[0].syncError'))
  # Controlled failure is counted as failure, not a success or a looped request.
  page.evaluate('()=>qaSeed(2)');page.evaluate('()=>{qa.mode="503";}');page.locator('#startGamingAll').click();page.wait_for_function('gamingControl().checkpoint?.failed===2&&!gamingAutoRunning')
  check('Failed run reports zero successes and keeps old lists',page.evaluate('gamingControl().checkpoint.ok===0&&state.gaming.sets.length===2&&qa.syncCalls.length===2'))
  check('Failed lists not retried in same manual session',page.evaluate('gamingControl().checkpoint.pending.length')==0)
  # Cancel while a fixture ignores AbortSignal: valid guard still prevents applying stale data.
  page.evaluate('()=>qaSeed(2)');page.evaluate('()=>{qa.holdRead=true;void startGamingQueue("due");}')
  page.wait_for_function('qa.readWaiters.length===1');page.locator('#pauseGamingBatch').click();page.evaluate('()=>{qa.holdRead=false;qa.readWaiters[0]();}');page.wait_for_function('!gamingAutoRunning')
  check('Late read after cancel cannot write success',page.evaluate('state.gaming.sets[0].lastSyncAt===null&&gamingControl().checkpoint.pending.length===2'))
  check('Cancel is not counted as a failed API call',page.evaluate('gamingControl().checkpoint.failed===0&&gamingControl().lists.length===0'))
  # Mixed providers use the same coordinator, but a Retro limit must not block Steam.
  page.evaluate('()=>{qaSeed(2);state.gaming.accounts.push({id:"r",provider:"retro",externalId:"00003EMFWR7XB8SDPEHB3K56ZQ",label:"Profil rétro de test",autoSync:true,autoImport:false});state.gaming.sets.push(...Array.from({length:2},(_,i)=>({...clone(state.gaming.sets[i]),id:"r00"+i,provider:"retro",accountId:"r",platform:"Console test",externalGameId:String(i+1),title:"Liste rétro "+i})));state=migrate(state);save(false);render();qa.retroMode="429";}')
  page.evaluate('async()=>await runGamingSync(false)')
  check('Retro rate limit allows other provider in same batch',page.evaluate('state.gaming.sets.filter(s=>s.provider==="steam").every(s=>s.lastSyncAt)&&qa.syncCalls.filter(c=>c.path.includes("/retro/achievements")).length===1'))
  check('Retro rate limit persisted without blocking Steam',page.evaluate('gamingProviderBlocked("retro")&&!gamingProviderBlocked("steam")'))
  page.evaluate('async()=>await syncRetroSet("r001",false)')
  check('Direct Retro action respects the shared deadline',page.evaluate('qa.syncCalls.filter(c=>c.path.includes("/retro/achievements")).length')==1)
  page.evaluate('async()=>{qa.clock+=181000;qa.retroMode="ok";await syncRetroSet("r001",false);}')
  check('Retro normal and Hardcore states stay distinct after retry',page.evaluate('()=>{const s=state.gaming.sets.find(s=>s.id==="r001");return s.source==="retro-api"&&s.achievements[0].earned===true&&s.achievements[0].hardcoreEarned===false;}'))
  page.locator('#syncAccountFilter').select_option('r');check('Profile filter restricts center to correct account',page.locator('.sync-list-row').count()==2)
  # Paged Retro library imports stage all catalogue pages while persisting only rate-control metadata.
  page.evaluate('()=>{qaSeed(0);qa.libRows=501;qa.libFail="once";qa.libFailAt=500;window.qaPendingLibrary=importRetroLibrary({user:"Fixture_Player",autoSync:false,autoImport:false});}')
  page.wait_for_function('qa.retroWaitTimers.length===1')
  check('Paged Retro import does not commit early pages during 429',page.evaluate('state.media.length===1&&state.gaming.accounts.length===1&&state.gaming.sets.length===0'))
  check('Paged Retro rate limit is persisted independently of pending catalogue',page.evaluate('gamingControl().providers.some(p=>p.id==="retro"&&p.retryAt>Date.now())'))
  page.evaluate('()=>{qa.clock+=60000;qa.retroWaitTimers[0]();}');page.evaluate('async()=>await qaPendingLibrary')
  check('Retro retry resumes same page without refetching validated page',page.evaluate('qa.syncCalls.filter(c=>c.path.includes("/retro/library")).map(c=>Number(new URL(c.path,"http://fixture").searchParams.get("offset")))')==[0,500,500])
  check('Complete paged library commits 501 references once',page.evaluate('state.gaming.sets.filter(s=>s.provider==="retro").length===501&&state.media.length===502'))
  page.evaluate('()=>{qaSeed(0);qa.libRows=1;qa.libFail=true;qa.libFailAt=0;window.qaPendingLibrary=importRetroLibrary({user:"Fixture_Player",autoSync:false,autoImport:false});}')
  page.wait_for_function('qa.retroWaitTimers.length===1');page.evaluate('()=>{invalidateGamingReads();}');page.evaluate('async()=>await qaPendingLibrary');page.evaluate('()=>{qa.clock+=60000;qa.retroWaitTimers[0]();}')
  check('Cancelling a paged 429 wait makes no retry and no partial import',page.evaluate('qa.syncCalls.filter(c=>c.path.includes("/retro/library")).length===1&&state.media.length===1&&state.gaming.sets.length===0'))
  check('Cancelling catalogue work does not erase provider cooldown',page.evaluate('gamingControl().providers.some(p=>p.id==="retro")'))
  # Steam identity, not a stored account label, authorizes private signed-in lists.
  page.evaluate('()=>{qaSeed(1);state.gaming.sets[0].requiresSteamSignIn=true;steamConnection.authenticated=false;save(false);render();}')
  page.evaluate('async()=>await syncGamingSet("s000",false)')
  check('Session-bound Steam list cannot bypass login in manual mode',page.evaluate('qa.syncCalls.length')==0)
  check('Missing Steam session visibly blocks row control',page.locator('[data-sync-once="s000"]').is_disabled() and 'Connexion requise' in page.locator('.sync-list-row').inner_text())
  # Session probe after logout and competing session responses.
  page.evaluate('()=>qaSeed(1)');page.evaluate('()=>{qa.holdSession=true;void refreshSteamSession();}');page.wait_for_function('qa.sessionWaiters.length===1')
  page.evaluate('async()=>await disconnectSteam()');page.evaluate('()=>qa.sessionWaiters[0]()');page.wait_for_timeout(40)
  check('Late connected response cannot revive logged-out Steam identity',page.evaluate('steamConnection.authenticated') is False)
  check('Disconnect turns off automatic account reads',page.evaluate('!state.gaming.accounts[0].autoSync&&!state.gaming.accounts[0].autoImport'))
  page.evaluate('()=>qaSeed(1)');page.evaluate('()=>{qa.holdSession=true;void refreshSteamSession();}');page.wait_for_function('qa.sessionWaiters.length===1');page.evaluate('()=>{qa.sessionAuthenticated=false;void refreshSteamSession();}');page.wait_for_function('qa.sessionWaiters.length===2')
  page.evaluate('()=>qa.sessionWaiters[1]()');page.wait_for_timeout(15);page.evaluate('()=>qa.sessionWaiters[0]()');page.wait_for_timeout(30)
  check('Older session probe cannot overwrite newer disconnected result',page.evaluate('steamConnection.authenticated') is False)
  # Additional controls and export boundaries.
  page.evaluate('()=>qaSeed(65)');check('Large center initially mounts 50 rows',page.locator('.sync-list-row').count()==50);page.locator('#moreGamingSync').click();check('Next page reveals remaining rows',page.locator('.sync-list-row').count()==65)
  page.locator('#syncListQuery').fill('fictive 065');check('Filtering includes initially unmounted rows',page.locator('.sync-list-row').count()==1 and '065' in page.locator('.sync-list-row').inner_text())
  page.locator('#syncListQuery').fill('');page.locator('[data-sync-pause="s000"]').click();page.locator('#syncListFilter').select_option('paused');check('State filter matches paused lists',page.locator('.sync-list-row').count()==1)
  page.locator('#syncListFilter').select_option('all')
  check('Portable export excludes local runtime control and checkpoints',page.evaluate('()=>{const p=toPortableBackup(state);return p.schema===11&&!Object.hasOwn(p,"_gamingControl")}'))
  check('Imported foreign runtime fields removed',page.evaluate('()=>{const p=toPortableBackup(state);p._gamingControl={version:999};p._revision=999;p._syncState={queue:["fake"]};const s=fromPortableBackup(p);return !Object.hasOwn(s,"_gamingControl")&&!Object.hasOwn(s,"_revision")&&!Object.hasOwn(s,"_syncState")}'))
  # A store rejection must leave the library/control identical to its last durable state.
  before=page.evaluate('localStorage.getItem(KEY)');page.evaluate('()=>{window.realStore=localStorage.setItem;localStorage.setItem=(k,v)=>{if(k===KEY)throw Error("Quota de test");return realStore(k,v)};}');page.locator('#pauseAllGaming').click()
  check('Failed pause save preserves durable library and control',page.evaluate('localStorage.getItem(KEY)')==before)
  check('Refused control update leaves exportable draft and no false pause',page.evaluate('!!storageDraft&&!gamingControl().pauseAll'))
  check('Storage error banner stays inside app shell without disrupting desktop columns',page.evaluate('document.querySelector("#storageBanner").parentElement.id==="appShell"&&document.querySelector("#appShell").getBoundingClientRect().width>innerWidth/2'))
  page.evaluate('()=>{localStorage.setItem=realStore;storageDraft=null;document.querySelector("#storageBanner")?.remove();qaSeed(5);document.activeElement?.blur();window.scrollTo(0,0);}')
  for width in [390,768,1360]:
   page.set_viewport_size({'width':width,'height':980});page.evaluate('()=>{document.activeElement?.blur();window.scrollTo(0,0);}');page.wait_for_timeout(1900);page.screenshot(path=str(OUT/f'sync-center-fixture-{width}.png'),full_page=True)
   check('Sync center fits viewport '+str(width),page.evaluate('document.documentElement.scrollWidth<=innerWidth+1'))
  page.evaluate('()=>{state.settings.dark=true;theme();render();window.scrollTo(0,0);}')
  page.screenshot(path=str(OUT/'sync-center-fixture-dark-1360.png'),full_page=True)
  check('Center supports inherited dark theme',page.locator('body').evaluate('(e)=>e.classList.contains("dark")'))
  check('Sync filters are usable touch targets',page.locator('#syncListQuery').evaluate('(e)=>e.getBoundingClientRect().height>=44'))
  check('No uncaught JavaScript errors in new scenarios',not errors)
  (OUT/'browser-v015.json').write_text(json.dumps({'version':'0.15.0','scope':'real Chromium scripts, DOM and CSS; synthetic provider/storage/time fixtures; no real user accounts','passed':len(checks),'checks':checks,'errors':errors},ensure_ascii=False,indent=2))
  browser.close();print(json.dumps({'passed':len(checks),'errors':errors}))
if __name__=='__main__':
 try:run()
 except Exception:
  (OUT/'browser-v015-failure.json').write_text(json.dumps({'passed':len(checks),'checks':checks,'traceback':traceback.format_exc()},ensure_ascii=False,indent=2));raise
