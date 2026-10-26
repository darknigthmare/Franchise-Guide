"""V0.12 Chromium UI checks: controlled API/storage/image fixtures, not live Steam login.
HTTP OpenID validation is covered separately by real loopback Java and Node servers.
"""
from pathlib import Path
from playwright.sync_api import sync_playwright
import re,json,traceback,base64
from browser_harness import ROOT,WEB,OUT,SETUP,RESET
checks=[]
def check(name,condition=True):
 if not condition:raise AssertionError(name)
 checks.append({'name':name,'passed':True});print('PASS',name,flush=True)
EXTRA=r'''() => {
 const baseFetch=fetch,session={};
 Object.defineProperty(window,'sessionStorage',{configurable:true,value:{getItem:k=>session[k]??null,setItem:(k,v)=>session[k]=String(v),removeItem:k=>delete session[k]}});
 qa.steam={id:'76561198000000001',authenticated:false,mode:'ok',count:3,calls:[],beginStatus:503,hold:false,erased:0,disconnectFail:false};
 window.libraryFixture=()=>({provider:'steam',steamid:qa.steam.id,fetchedAt:Date.now(),profile:{response:{players:[{steamid:qa.steam.id,personaname:'Compte Steam fictif QA',avatarfull:'https://image.tmdb.org/t/p/w500/qa-poster.png'}]}},library:{response:{game_count:qa.steam.count,games:Array.from({length:qa.steam.count},(_,i)=>({appid:100+i,name:'Jeu Steam fictif '+i,playtime_forever:120+i,img_icon_url:'a'.repeat(40),rtime_last_played:1700000000,has_community_visible_stats:qa.steam.count===3?i!==2:true}))}}});
 window.successFixture=(app)=>({provider:'steam',appid:app,steamid:qa.steam.id,schema:{game:{gameName:'Jeu QA',availableGameStats:{achievements:[{name:'A',displayName:'Première étape de test',description:'Description fictive',icon:'https://image.tmdb.org/t/p/w500/qa-poster.png',hidden:0},{name:'B',displayName:'SECRET QA STEAM',hidden:1}]}}},player:{playerstats:{success:true,steamID:qa.steam.id,achievements:[{apiname:'A',achieved:1,unlocktime:1700000000},{apiname:'B',achieved:0,unlocktime:0}]}},rarity:{achievementpercentages:{achievements:[{name:'A',percent:50}]}}});
 window.fetch=async(url,options={})=>{
  const path=String(url);if(!path.startsWith('/api/gaming/'))return baseFetch(url,options);
  qa.steam.calls.push({url:path,method:options.method||'GET',credentials:options.credentials,header:options.headers?.['X-FranchiseGuide-Client']});
  const res=(x,status=200)=>new Response(JSON.stringify(x),{status,headers:{'Content-Type':'application/json'}});
  if(path==='/api/gaming/status')return res({app:'FranchiseGuide Gaming Bridge',steam:true,steamSignIn:true,writes:false});
  if(path.endsWith('/begin'))return res({error:'Clé Steam absente dans le serveur de test.'},qa.steam.beginStatus);
  if(path.endsWith('/session'))return res(qa.steam.authenticated?{authenticated:true,steamid:qa.steam.id,expiresAt:Date.now()+86400000}:{authenticated:false});
  if(path.endsWith('/disconnect')){if(qa.steam.disconnectFail)return res({error:'Serveur de test indisponible'},503);qa.steam.authenticated=false;qa.steam.erased++;return res({authenticated:false});}
  if(qa.steam.hold&&path==='/api/gaming/steam/library')await new Promise(resolve=>{qa.steam.waiting=true;qa.steam.release=resolve;});
  if(path==='/api/gaming/steam/library'){
   const x=libraryFixture();if(qa.steam.mode==='empty')x.library.response={game_count:0};
   if(qa.steam.mode==='private')x.library.response={};
   if(qa.steam.mode==='partial')x.library.response.games.pop();
   if(qa.steam.mode==='wrong')x.steamid='76561198000000009';
   if(qa.steam.mode==='outage')return res({error:'Steam indisponible (fixture)'},502);
   return res(x);
  }
  if(path.startsWith('/api/gaming/steam/achievements?')){
   const app=new URL(path,'https://fixture.test').searchParams.get('appid');
   if(qa.steam.mode==='noStats')return res({provider:'steam',appid:app,steamid:qa.steam.id,noAchievements:true,schema:{game:{gameName:'Pas de succès exposés'}}});
   if(qa.steam.mode==='trophyFail')return res({error:'Succès momentanément privés'},502);
   return res(successFixture(app));
  }
  if(path.startsWith('/api/gaming/steam?'))return res(successFixture(new URL(path,'https://fixture.test').searchParams.get('appid')));
  throw Error('Unhandled Steam UI fixture '+path);
 };
 clearTimeout(traktSaveTimer);steamConnection={authenticated:false,checked:false,checkedAt:0,steamid:null,expiresAt:0};steamNotice='';steamLastReport=null;steamLibraryRetryAt=0;gamingBridge={checked:0,steam:false};
}'''

def run():
 with sync_playwright() as p:
  b=p.chromium.launch(executable_path='/usr/bin/chromium',headless=True,args=['--no-sandbox']);ctx=b.new_context(viewport={'width':1360,'height':1000},locale='fr-FR');page=ctx.new_page();page.set_default_timeout(6000)
  errors=[];page.on('pageerror',lambda e:errors.append(str(e)));page.on('dialog',lambda d:d.accept())
  png=base64.b64decode('iVBORw0KGgoAAAANSUhEUgAAAAIAAAACCAYAAABytg0kAAAAFElEQVQImWNkYPj/n4GBgYGJAQoAHhgCAQGXeHsAAAAASUVORK5CYII=')
  ctx.route(re.compile(r'https://(image\.tmdb\.org|steamcdn-a\.akamaihd\.net|avatars\.steamstatic\.com)/'),lambda r:r.fulfill(status=200,content_type='image/png',body=png))
  page.set_content(re.sub(r'<script.*?</script>|<link[^>]+>|<meta http-equiv="Content-Security-Policy"[^>]+>','',(WEB/'index.html').read_text()));page.add_style_tag(content=(WEB/'app.css').read_text());page.evaluate(SETUP)
  for f in re.findall(r'<script src="([^"]+)"',(WEB/'index.html').read_text()):page.add_script_tag(content=(WEB/f).read_text())
  page.evaluate(RESET);page.evaluate(EXTRA);page.evaluate("()=>{screen='games';render();}")
  check('Games expose official Steam entry',page.locator('#steamQuickConnect').count()==1)
  page.locator('#steamQuickConnect').click();page.locator('#steamSignIn').click()
  check('Login opens explicit consent dialog',page.locator('#gamingDialog').is_visible())
  check('Auto import is visible and can be disabled',page.locator('[name=import]').is_checked() and page.locator('[name=auto]').is_checked())
  check('Steam login does not request password, SteamID or API key',page.locator('#gamingDialog input').count()==2)
  page.locator('#gamingSubmit').click();page.wait_for_function('document.querySelector("#gamingFormError").textContent.length>0')
  check('Missing server key produces actionable form error','Clé Steam absente' in page.locator('#gamingFormError').inner_text())
  check('Failed login clears pending intent',page.evaluate('sessionStorage.getItem(STEAM_INTENT)') is None)
  check('Auth begin is POST with same-origin credentials',page.evaluate('qa.steam.calls.some(x=>x.url.endsWith("/begin")&&x.method==="POST"&&x.credentials==="same-origin"&&x.header==="0.12")'))
  check('No false connected badge after refused login',page.locator('#steamSignIn').count()==1)
  page.locator('#gamingClose').click()
  # Authenticated identity fixture represents a server session, not a password/OAuth browser test.
  page.evaluate('async()=>{qa.steam.authenticated=true;await refreshSteamSession();await checkGamingBridge(true);}')
  check('Server-verified account shown connected',page.locator('#steamSignOut').count()==1 and 'Steam connecté' in page.locator('#steamSignInCard').inner_text())
  page.locator('#steamImportLibrary').click();page.locator('#gamingDialog [name=auto]').check();page.locator('#gamingSubmit').click()
  page.wait_for_function('state.media.length===3&&!steamLibraryBusy&&!gamingAutoRunning');page.wait_for_selector('#gamingDialog',state='detached')
  check('All owned game entries imported without manual IDs',page.evaluate('state.media.every(m=>m.source==="steam")&&state.media.length===3'))
  check('One registered account with identity and avatar',page.evaluate('state.gaming.accounts.length===1&&state.gaming.accounts[0].externalId===qa.steam.id&&!!state.gaming.accounts[0].avatarUrl'))
  check('Only games with exposed stats have success bindings',page.evaluate('state.gaming.sets.length')==2)
  check('Success reads happen automatically after import',page.evaluate('state.gaming.sets.every(s=>s.achievements.length===2&&s.lastSyncAt)'))
  check('Connected requests contain no client supplied SteamID',page.evaluate('qa.steam.calls.filter(x=>x.url.includes("/achievements?")).every(x=>!x.url.includes("steamid")&&x.credentials==="same-origin")'))
  check('Session protected list binding retained',page.evaluate('state.gaming.sets.every(s=>s.requiresSteamSignIn)'))
  check('Playtime never marks campaigns complete',page.evaluate('state.media.every(m=>!m.watched&&!m.started)'))
  check('Trophy import does not queue Trakt',page.evaluate('syncState.queue.length')==0)
  check('Provider icons are used rather than empty placeholders',page.evaluate('state.media.every(m=>m.posterUrl?.includes("steamcommunity/public/images/apps"))'))
  # Repeated retrieval, personal notes and account separation.
  page.evaluate('()=>{state.media[0].userNote="Ma note conservée";state.media[0].favorite=true;state.media[0].rating=9;state.gaming.sets[0].achievements[0].note="Stratégie personnelle";save(false);}')
  page.evaluate('async()=>{await importSteamLibrary({autoImport:true,autoSync:true});}');page.wait_for_function('!gamingAutoRunning')
  check('Repeated import idempotent',page.evaluate('state.media.length===3&&state.gaming.sets.length===2&&state.gaming.accounts.length===1'))
  check('Personal game notes rating favorites preserved',page.evaluate('state.media[0].userNote==="Ma note conservée"&&state.media[0].favorite&&state.media[0].rating===9'))
  page.evaluate('async()=>{await syncGamingSet(state.gaming.sets[0].id,true);}')
  check('Personal achievement notes survive read',page.evaluate('state.gaming.sets[0].achievements[0].note')=='Stratégie personnelle')
  for mode in ['private','partial','wrong','outage']:
   before=page.evaluate('JSON.stringify(state)');page.evaluate('(mode)=>qa.steam.mode=mode',mode);page.evaluate('async()=>{await importSteamLibrary();}')
   check(mode+' response does not change library',page.evaluate('JSON.stringify(state)')==before)
  page.evaluate('async()=>{qa.steam.mode="empty";await importSteamLibrary();}')
  check('Empty library does not erase previously imported titles',page.evaluate('state.media.length')==3)
  check('Empty privacy ambiguity visible','zéro jeu' in page.locator('#steamSyncNotice').inner_text())
  page.evaluate('async()=>{qa.steam.mode="ok";qa.steam.id="76561198000000002";await refreshSteamSession();await importSteamLibrary({autoImport:true,autoSync:true,initial:true});}');page.wait_for_function('!gamingAutoRunning')
  check('Second account reuses game identifiers without duplicate media',page.evaluate('state.media.length===3&&state.gaming.accounts.length===2'))
  check('Two accounts have separate achievement sets',page.evaluate('state.gaming.sets.length===4&&new Set(state.gaming.sets.map(s=>s.accountId)).size===2'))
  check('Per-account playtimes retained',page.evaluate('Object.keys(state.media[0].steam.accounts).length')==2)
  check('Portable schema nine retains Steam metadata but not authentication',page.evaluate('()=>{let b=toPortableBackup(state);return b.schema===11&&b.media[0].steam.appId==="100"&&!JSON.stringify(b).includes("expiresAt")&&!JSON.stringify(b).includes("sessionToken")}'))
  check('Portable import does not activate auto-reads',page.evaluate('fromPortableBackup(toPortableBackup(state)).gaming.accounts.every(a=>!a.autoSync&&!a.autoImport)'))
  # Delayed answers must not survive account/library changes.
  before=page.evaluate('JSON.stringify(state)');page.evaluate('()=>{qa.steam.hold=true;qa.steam.waiting=false;window.pendingImport=importSteamLibrary();}');page.wait_for_function('qa.steam.waiting')
  page.evaluate('()=>{libraryEpoch++;qa.steam.hold=false;qa.steam.release();}');page.wait_for_function('!steamLibraryBusy')
  check('Late library response discarded after epoch change',page.evaluate('JSON.stringify(state)')==before)
  page.locator('#steamAutoToggle').click()
  check('Automatic updates can be suspended per active account',page.evaluate('!activeSteamAccount().autoImport&&!activeSteamAccount().autoSync'))
  calls=page.evaluate('qa.steam.calls.length');page.evaluate('async()=>{steamLibraryRetryAt=0;await steamForeground();}')
  check('Paused foreground loop performs no game API reads',page.evaluate('qa.steam.calls.length')==calls)
  page.locator('#steamAutoToggle').click();page.wait_for_function('!gamingAutoRunning')
  # More than one batch: unsynchronized sets are processed first on a manual continuation.
  page.evaluate('async()=>{qa.steam.count=20;await importSteamLibrary({autoImport:true,autoSync:true,initial:true});}');page.wait_for_function('!gamingAutoRunning')
  count=page.evaluate('state.gaming.sets.filter(s=>s.accountId===activeSteamAccount().id&&s.lastSyncAt).length')
  check('Initial batch is bounded rather than flooding provider',count>=8 and count<20)
  page.evaluate('async()=>{await runGamingSync(false,activeSteamAccount().id);}')
  check('Next manual batch advances unread games, not first eight repeatedly',page.evaluate('state.gaming.sets.filter(s=>s.accountId===activeSteamAccount().id&&s.lastSyncAt).length')>count)
  page.evaluate('async()=>{await runGamingSync(true,activeSteamAccount().id);}')
  check('Automatic continuation eventually reads all account lists',page.evaluate('state.gaming.sets.filter(s=>s.accountId===activeSteamAccount().id).every(s=>s.lastSyncAt)'))
  check('Old account lists are not fetched during other account import',page.evaluate('qa.steam.calls.filter(x=>x.url.includes("/achievements?")).every(x=>!x.url.includes("76561198000000001"))'))
  page.evaluate('()=>{overlay={type:"gaming",title:"Trophées & comptes"};render();}')
  page.screenshot(path=str(OUT/'fixture-v012-steam-desktop.png'),full_page=True)
  page.set_viewport_size({'width':390,'height':844});page.screenshot(path=str(OUT/'fixture-v012-steam-mobile.png'),full_page=True)
  check('Steam hub mobile has no horizontal overflow',page.evaluate('document.documentElement.scrollWidth<=innerWidth+1'))
  page.evaluate('()=>{overlay={type:"item",id:state.media[0].id,title:state.media[0].title,src:"media"};render();}')
  check('Steam game detail shows playtime with account labels','Bibliothèque Steam' in page.locator('main').inner_text() and '2 h' in page.locator('main').inner_text())
  page.locator('#openGameTrophies').click();check('Automatic success list visible from game detail',page.locator('[data-achievement]').count()==2)
  check('Hidden secret remains masked','SECRET QA STEAM' not in page.locator('main').inner_text())
  page.screenshot(path=str(OUT/'fixture-v012-success-mobile.png'),full_page=True)
  # No-success catalog outcome and storage failure.
  page.evaluate('()=>{const a=activeSteamAccount();const s=state.gaming.sets.find(s=>s.accountId===a.id);s.achievements=[];s.lastSyncAt=null;qa.steam.mode="noStats";window.emptySetId=s.id;save(false);}')
  page.evaluate('async()=>{await syncGamingSet(emptySetId);openGameAchievements(state.gaming.sets.find(s=>s.id===emptySetId).mediaId);gameView(overlay.id).setId=emptySetId;render();}')
  check('No exposed catalog gets clear status without 100 percent','Steam ne fournit aucun catalogue' in page.locator('main').inner_text() and page.evaluate('FGGaming.progress(state.gaming.sets.find(s=>s.id===emptySetId)).completed')==False)
  before=page.evaluate('JSON.stringify(state)');page.evaluate('()=>{qa.steam.mode="ok";qa.steam.count=21;window.oldSetItem=localStorage.setItem;localStorage.setItem=(k,v)=>{if(k===KEY)throw Error("Quota fixture");oldSetItem(k,v);};}')
  page.evaluate('async()=>{await importSteamLibrary();}')
  check('Storage rejection preserves saved library',page.evaluate('JSON.stringify(state)')==before)
  check('Quota failure retains recoverable import draft',page.evaluate('storageDraft.media.length')==21)
  page.evaluate('()=>{localStorage.setItem=oldSetItem;storageDraft=null;document.querySelector("#storageBanner")?.remove();overlay={type:"gaming",title:"Trophées & comptes"};render();}')
  page.locator('#steamSignOut').click();page.wait_for_function('!steamConnection.authenticated')
  check('Disconnect calls local session revocation',page.evaluate('qa.steam.erased')==1)
  check('Logout preserves titles and achievement history',page.evaluate('state.media.length')==20)
  check('Logout removes connected badge',page.locator('#steamSignIn').count()==1)
  check('Imported identity alone never authenticates account',page.evaluate('activeSteamAccount()===null'))
  # Privacy deletion must revoke before losing library, and fail closed on outage.
  page.evaluate('async()=>{qa.steam.authenticated=true;await refreshSteamSession();qa.steam.disconnectFail=true;window.eraseError="";try{await eraseLocalData()}catch(e){eraseError=e.message;}}')
  check('Privacy wipe fails closed when session cannot be revoked',page.evaluate('eraseError.length>0&&state.media.length===20&&storageWritable'))
  page.evaluate('async()=>{qa.steam.disconnectFail=false;try{await eraseLocalData()}catch(e){eraseError=e.message;}}')
  check('Opaque-origin IndexedDB failure is reported instead of false wipe success',page.evaluate('eraseError.includes("IndexedDB")&&!storageWritable'))
  check('Partial wipe still revokes session and clears local titles (not proof of IDB deletion)',page.evaluate('qa.steam.erased===2&&state.media.length===0&&!steamConnection.authenticated'))
  check('No unhandled page error',not errors)
  result={'version':'0.12.0','scope':'real Chromium DOM/scripts; fixture APIs/storage/images; no live Steam login or persistent service worker','passed':len(checks),'checks':checks,'pageErrors':errors};(OUT/'browser-v012.json').write_text(json.dumps(result,ensure_ascii=False,indent=2));b.close();print(json.dumps({'passed':len(checks),'errors':errors}))
if __name__=='__main__':
 try:run()
 except Exception:
  (OUT/'browser-v012-failure.json').write_text(json.dumps({'checks':checks,'traceback':traceback.format_exc()},ensure_ascii=False,indent=2));raise
