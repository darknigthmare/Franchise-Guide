"""V0.11 DOM integration. Production scripts, explicit synthetic storage/API/image fixtures.
Top-level loopback navigation was blocked by administrator policy; this is not a real
Steam/PSN login, persistent origin or installed PWA test. No platform writes are made.
"""
from pathlib import Path
from playwright.sync_api import sync_playwright
from browser_harness import SETUP,RESET,ROOT,WEB
import re,json,traceback,base64,sys
OUT=ROOT/'tests/results/v011';OUT.mkdir(parents=True,exist_ok=True)
checks=[]
def check(name,condition=True):
 if not condition:raise AssertionError(name)
 checks.append({'name':name,'passed':True});print('PASS',name,flush=True)
EXTRA=r'''() => {
 const oldFetch=fetch,realClock=Date.now;qa.clockOffset=0;Date.now=()=>realClock()+qa.clockOffset;
 qa.passGamingDeadline=()=>{const c=gamingControl(),until=Math.max(Date.now(),...c.lists.map(x=>x.retryAt||0),...c.providers.map(x=>x.retryAt||0));qa.clockOffset+=Math.max(0,until-Date.now())+1;render();};
 qa.gamingCalls=[];qa.gamingMode='ok';qa.downloads=[];
 window.downloadJson=(name,value)=>qa.downloads.push({name,value});
 window.fixtureSteam=()=>({provider:'steam',appid:'123',steamid:'76561198000000001',schema:{game:{gameName:'Jeu fictif de QA',availableGameStats:{achievements:[{name:'A',displayName:'Départ fictif',description:'Objectif de test',icon:'https://image.tmdb.org/t/p/w500/qa-poster.png',icongray:'https://image.tmdb.org/t/p/w500/qa-poster.png',hidden:0},{name:'B',displayName:'SECRET STEAM TEST',hidden:1}]}}},player:{playerstats:{success:true,steamID:'76561198000000001',achievements:[{apiname:'A',achieved:1,unlocktime:1700000000},{apiname:'B',achieved:0,unlocktime:0}]}},rarity:{achievementpercentages:{achievements:[{name:'A',percent:0}]}}});
 window.fetch=async(url,options={})=>{
   if(String(url).startsWith('/api/gaming/')){
    qa.gamingCalls.push({url:String(url),method:options.method||'GET'});
    const response=(body,status=200)=>new Response(JSON.stringify(body),{status,headers:{'Content-Type':'application/json','Retry-After':'60'}});
    if(String(url).includes('/status'))return response({app:'FranchiseGuide Gaming Bridge',steam:qa.gamingMode!=='no-key',writes:false});
    if(qa.gamingHold)await new Promise(resolve=>{qa.gamingWaiting=true;qa.gamingRelease=resolve});
    if(qa.gamingMode==='429')return response({error:'fixture rate limit'},429);
    if(qa.gamingMode==='502')return response({error:'fixture outage'},502);
    const bundle=fixtureSteam();
    if(qa.gamingMode==='private')bundle.player={playerstats:{success:false,error:'private fixture'}};
    if(qa.gamingMode==='partial')bundle.player.playerstats.achievements.pop();
    if(qa.gamingMode==='other')bundle.steamid='76561198000000002';
    return response(bundle);
   }
   return oldFetch(url,options);
 };
 window.fixturePsn=(gameId)=>({format:'franchiseguide-achievements',version:1,accountExternalId:'Fixture_Player',set:{id:'imported',mediaId:gameId,provider:'psn',platform:'PS5',accountId:'ignored',externalGameId:'NPWR_FIXTURE',title:'Jeu fictif de QA',edition:'Édition QA',source:'steam-api',completeCatalog:true,groups:[{id:'base',name:'Jeu de base',kind:'base'},{id:'dlc',name:'Extension fictive',kind:'dlc'}],achievements:[{id:'bronze',title:'Exemple : début',description:'Objectif de test',groupId:'base',grade:'bronze',earned:true,earnedAt:1700000000000,iconUrl:'https://image.tmdb.org/t/p/w500/qa-poster.png'},{id:'secret',title:'SPOILER PSN TEST',description:'Révélation fictive',groupId:'base',hidden:true,earned:false},{id:'platinum',title:'Exemple : platine',groupId:'base',grade:'platinum',earned:false},{id:'dlc',title:'Exemple : extension',groupId:'dlc',grade:'gold',earned:null,missable:true,online:true}]}});
}'''
def run():
 with sync_playwright() as p:
  b=p.chromium.launch(executable_path='/usr/bin/chromium',headless=True,args=['--no-sandbox']);ctx=b.new_context(viewport={'width':1360,'height':1000},locale='fr-FR',timezone_id='Europe/Paris');page=ctx.new_page();page.set_default_timeout(6000)
  errors=[];page.on('pageerror',lambda e:errors.append(str(e)));page.on('dialog',lambda d:d.accept())
  png=base64.b64decode('iVBORw0KGgoAAAANSUhEUgAAAAIAAAACCAYAAABytg0kAAAAFElEQVQImWNkYPj/n4GBgYGJAQoAHhgCAQGXeHsAAAAASUVORK5CYII=')
  ctx.route(re.compile(r'https://(image\.tmdb\.org|covers\.openlibrary\.org)/'),lambda r:r.fulfill(status=200,content_type='image/png',body=png))
  page.set_content(re.sub(r'<script.*?</script>|<link[^>]+>|<meta http-equiv="Content-Security-Policy"[^>]+>','',(WEB/'index.html').read_text()));page.add_style_tag(content=(WEB/'app.css').read_text());page.evaluate(SETUP)
  for f in re.findall(r'<script src="([^"]+)"',(WEB/'index.html').read_text()):page.add_script_tag(content=(WEB/f).read_text())
  page.evaluate(RESET);page.evaluate(EXTRA);page.evaluate("()=>{screen='games';render();}")
  check('Games category exposes trophy hub and manual add',page.locator('#gamesTrophyHub').count()==1 and page.locator('#newManualGame').count()==1)
  page.locator('#newManualGame').click();page.locator('#gamingDialog [name=title]').fill('Jeu fictif de QA');page.locator('#gamingDialog [name=platform]').fill('PS5');page.locator('#gamingDialog [name=cover]').fill('https://image.tmdb.org/t/p/w500/qa-poster.png');page.locator('#gamingSubmit').click();page.wait_for_selector('#openGameTrophies')
  game=page.evaluate('state.media[0].id')
  check('Manual game available without RAWG key',page.evaluate('state.media[0].source==="manual"&&state.media[0].kind==="game"'))
  check('Game detail has dedicated achievement entry',page.locator('#gameTrophySummary').count()==1)
  page.locator('#openGameTrophies').click();check('Empty trophies do not claim 100 percent','Aucune liste' in page.locator('main').inner_text())
  page.locator('#newAchievementSet').click();page.locator('#gamingSubmit').click();page.wait_for_selector('#addManualAchievement')
  check('Creating list leaves platform state and campaign unchanged',page.evaluate('state.media[0].watched===false&&state.gaming.sets[0].achievements.length===0'))
  page.locator('#addManualAchievement').click();page.locator('#gamingDialog [name=title]').fill('Objectif personnel');page.locator('#gamingDialog [name=description]').fill('Ne débloque rien sur un serveur');page.locator('#gamingDialog [name=grade]').select_option('platinum');page.locator('#gamingSubmit').click();page.wait_for_selector('[data-achievement]')
  page.locator('[data-ach-note]').click();page.locator('#gamingDialog [name=done]').check();page.locator('#gamingDialog [name=note]').fill('Plan personnel conservé');page.locator('#gamingSubmit').click();page.wait_for_selector('.local-check')
  check('Personal checklist does not manufacture platinum or campaign completion',page.evaluate('FGGaming.statistics(state.gaming).platinums===0&&state.gaming.sets[0].achievements[0].earned===null&&!state.media[0].watched'))
  check('Local trophy edits do not queue Trakt changes',page.evaluate('syncState.queue.length')==0)
  page.locator('[data-ach-pin]').click();page.locator('#achievementState').select_option('pinned');check('Pinned filter functional',page.locator('[data-achievement]').count()==1)
  page.locator('#addManualAchievement').click();page.locator('#gamingDialog [name=title]').fill('Objectif DLC');page.locator('#gamingDialog [name=group]').select_option('__new_dlc');page.locator('#gamingDialog [name=dlc]').fill('Extension personnelle');page.locator('#gamingSubmit').click();page.wait_for_function('state.gaming.sets[0].achievements.length===2')
  check('New DLC gets its own group',page.evaluate('state.gaming.sets[0].groups[1].kind')=='dlc')
  page.locator('#achievementState').select_option('all');page.locator('#achievementGrade').select_option('platinum');check('Grade filter is connected to actual cards',page.locator('[data-achievement]').count()==1)
  page.locator('#openGamingHub').click();page.locator('#gamingAccountAdd').click();page.locator('#gamingDialog [name=provider]').select_option('psn');page.locator('#gamingDialog [name=externalId]').fill('Fixture_Player');page.locator('#gamingDialog [name=label]').fill('Profil de test');page.locator('#gamingDialog [name=auto]').check();page.locator('#gamingSubmit').click()
  check('PSN cannot be advertised as auto-sync-capable',page.locator('#gamingFormError').inner_text()!='' and page.evaluate('state.gaming.accounts.length')==0)
  page.locator('#gamingDialog [name=auto]').uncheck();page.locator('#gamingSubmit').click();page.wait_for_selector('.gaming-account')
  check('PSNProfiles public profile link constructed correctly',page.locator('a[href="https://psnprofiles.com/Fixture_Player"]').count()==1)
  check('PSN account explicitly described as unauthenticated reference','pas un compte authentifié' in page.locator('.gaming-account').inner_text())
  check('No passwords NPSSO cookies requested',page.locator('main input[type=password]').count()==0)
  page.locator('[data-gaming-game]').click();page.locator('#importAchievementSet').click();aid=page.evaluate('state.gaming.accounts[0].id');page.locator('#gamingDialog [name=account]').select_option(aid)
  raw=page.evaluate('(id)=>fixturePsn(id)',game)
  page.locator('#gamingDialog [name=file]').set_input_files({'name':'fiction-psn.json','mimeType':'application/json','buffer':json.dumps(raw).encode()});page.locator('#gamingSubmit').click();page.wait_for_function('state.gaming.sets.length===2')
  check('PSN file import retains base DLC grades and unknown state',page.evaluate('state.gaming.sets[1].achievements.length===4&&state.gaming.sets[1].groups.length===2&&state.gaming.sets[1].achievements[3].earned===null'))
  check('File import cannot claim live verification',page.evaluate('state.gaming.sets[1].source==="file-import"&&state.gaming.sets[1].lastSyncAt===null'))
  check('Locked secret is masked in actual DOM','SPOILER PSN TEST' not in page.locator('main').inner_text())
  page.locator('#achievementQuery').fill('SPOILER');check('Search does not reveal secret by title',page.locator('[data-achievement]').count()==0)
  page.locator('#achievementQuery').fill('');page.locator('[data-ach-reveal=secret]').click();check('Explicit secret reveal works','SPOILER PSN TEST' in page.locator('main').inner_text());page.locator('[data-ach-reveal=secret]').click()
  page.locator('#achievementGroup').select_option('dlc');check('DLC filter displays only DLC trophy',page.locator('[data-achievement]').count()==1 and page.locator('[data-achievement=dlc]').count()==1)
  page.locator('#achievementGroup').select_option('all');page.locator('#achievementState').select_option('unknown');check('Unknown state filter works',page.locator('[data-achievement=dlc]').count()==1 and page.locator('[data-achievement]').count()==1)
  page.locator('#achievementState').select_option('all');page.locator('[data-ach-note=bronze]').click();page.locator('#gamingDialog [name=note]').fill('Note PSN conservée');page.locator('#gamingSubmit').click();page.wait_for_selector('[data-achievement=bronze]')
  page.locator('#exportAchievementSet').click();check('List export contains neutral format and profile reference',page.evaluate('qa.downloads.at(-1).value.format==="franchiseguide-achievements"&&qa.downloads.at(-1).value.accountExternalId==="Fixture_Player"'))
  page.locator('#importAchievementSet').click();page.locator('#gamingDialog [name=account]').select_option(aid);page.locator('#gamingDialog [name=file]').set_input_files({'name':'same.json','mimeType':'application/json','buffer':json.dumps(raw).encode()});page.locator('#gamingSubmit').click();page.wait_for_selector('[data-achievement=bronze]')
  check('Repeated trophy import deduplicates and preserves personal note',page.evaluate('state.gaming.sets.length===2&&state.gaming.sets[1].achievements[0].note==="Note PSN conservée"'))
  before=page.evaluate('JSON.stringify(state.gaming)');bad=dict(raw);bad['accountExternalId']='Other_Player'
  page.locator('#importAchievementSet').click();page.locator('#gamingDialog [name=account]').select_option(aid);page.locator('#gamingDialog [name=file]').set_input_files({'name':'wrong-account.json','mimeType':'application/json','buffer':json.dumps(bad).encode()});page.locator('#gamingSubmit').click();page.wait_for_function('document.querySelector("#gamingFormError").textContent.length>0')
  check('Wrong profile import refused without state change',page.evaluate('JSON.stringify(state.gaming)')==before);page.locator('#gamingClose').click()
  page.locator('#openGamingHub').click();page.locator('#gamingAccountAdd').click();page.locator('#gamingDialog [name=externalId]').fill('76561198000000001');page.locator('#gamingDialog [name=label]').fill('Steam QA');page.locator('#gamingSubmit').click();page.wait_for_function('state.gaming.accounts.length===2')
  page.locator('[data-gaming-game]').click();page.locator('#linkSteamGame').click();page.locator('#gamingDialog [name=appid]').fill('123');page.locator('#gamingSubmit').click();page.wait_for_selector('#syncAchievementSet')
  sid=page.evaluate('state.gaming.sets.find(s=>s.provider==="steam").id')
  check('Linking Steam AppID does not imply unlocked success',page.evaluate('state.gaming.sets[2].achievements.length===0&&state.gaming.sets[2].lastSyncAt===null'))
  page.locator('#syncAchievementSet').click();page.wait_for_function('state.gaming.sets[2].achievements.length===2')
  check('Steam schema and player state applied together',page.evaluate('state.gaming.sets[2].source==="steam-api"&&state.gaming.sets[2].completeCatalog&&state.gaming.sets[2].achievements[0].earned'))
  check('Steam obtained timestamp is milliseconds without conversion error',page.evaluate('state.gaming.sets[2].achievements[0].earnedAt')==1700000000000)
  check('Steam secret stays masked after sync','SECRET STEAM TEST' not in page.locator('main').inner_text())
  check('Steam base DLC unknown displayed explicitly','Base / DLC non distingués' in page.locator('main').inner_text())
  check('Actual trophy image element loads with fixture image',page.locator('[data-achievement=A] img').count()==1)
  page.locator('[data-ach-note=A]').click();page.locator('#gamingDialog [name=note]').fill('Note entre deux lectures');page.locator('#gamingDialog [name=done]').check();page.locator('#gamingSubmit').click();page.wait_for_selector('[data-achievement=A]')
  for mode in ['private','partial','other','502','429']:
   old=page.evaluate('JSON.stringify(state.gaming.sets[2].achievements)');page.evaluate('(m)=>{qa.passGamingDeadline();qa.gamingMode=m;gamingBridge.checked=0;}',mode);page.locator('#syncAchievementSet').click();page.wait_for_function('gamingRequests.size===0')
   check('Steam '+mode+' response preserves all previous achievements',page.evaluate('JSON.stringify(state.gaming.sets[2].achievements)')==old)
  check('Steam rate limit persists provider retry time',page.evaluate('gamingControl().providers.some(p=>p.id==="steam"&&p.retryAt>Date.now())'))
  page.evaluate("()=>{qa.passGamingDeadline();qa.gamingMode='ok';gamingRetry.clear();render();}");page.locator('#syncAchievementSet').click();page.wait_for_function('gamingRequests.size===0')
  check('Successful resync clears error and preserves notes/checklist',page.evaluate('state.gaming.sets[2].syncError===""&&state.gaming.sets[2].achievements[0].note==="Note entre deux lectures"&&state.gaming.sets[2].achievements[0].localDone'))
  count=page.evaluate('qa.gamingCalls.filter(c=>c.url.includes("/steam?")).length');page.evaluate('(id)=>{qa.gamingHold=true;qa.gamingWaiting=false;void syncGamingSet(id);void syncGamingSet(id);}',sid);page.wait_for_function('qa.gamingWaiting')
  check('Repeated clicks coalesce one in-flight game read',page.evaluate('qa.gamingCalls.filter(c=>c.url.includes("/steam?")).length')==count+1)
  page.evaluate('()=>{libraryEpoch++;qa.gamingMode="partial";qa.gamingHold=false;qa.gamingRelease();}');page.wait_for_function('gamingRequests.size===0')
  check('Late response after replacement does not alter current gaming state',page.evaluate('state.gaming.sets[2].syncError===""&&state.gaming.sets[2].achievements.length===2'))
  page.evaluate('()=>{qa.gamingMode="ok";qa.gamingHold=true;qa.gamingWaiting=false;void syncGamingSet(state.gaming.sets[2].id);}');page.wait_for_function('qa.gamingWaiting');page.evaluate('()=>{cancelGamingSync();qa.gamingHold=false;qa.gamingRelease();}');page.wait_for_function('gamingRequests.size===0')
  check('Cancelled read leaves old earned state intact',page.evaluate('state.gaming.sets[2].achievements[0].earned===true'))
  check('No achievement API request ever writes remotely',page.evaluate('qa.gamingCalls.every(c=>c.method==="GET")'))
  check('Trophy operations do not leak into Trakt queue',page.evaluate('syncState.queue.length')==0)
  # Foreground, explicit consent and six-hour recency govern automatic reads.
  count=page.evaluate('qa.gamingCalls.filter(c=>c.url.includes("/steam?")).length')
  page.evaluate('()=>{gamingCommit(g=>{g.accounts.find(a=>a.provider==="steam").autoSync=false;g.sets[2].lastSyncAt=1;});}')
  page.evaluate('()=>runGamingSync(true)')
  check('Automatic Steam read requires explicit account consent',page.evaluate('qa.gamingCalls.filter(c=>c.url.includes("/steam?")).length')==count)
  page.evaluate('()=>{gamingCommit(g=>g.accounts.find(a=>a.provider==="steam").autoSync=true);}')
  page.evaluate('()=>runGamingSync(true)')
  check('Consented stale Steam list receives one automatic read',page.evaluate('qa.gamingCalls.filter(c=>c.url.includes("/steam?")).length')==count+1)
  page.evaluate('()=>runGamingSync(true)')
  check('Fresh Steam list is not reread within six-hour interval',page.evaluate('qa.gamingCalls.filter(c=>c.url.includes("/steam?")).length')==count+1)
  page.evaluate('()=>{gamingCommit(g=>g.sets[2].lastSyncAt=1);qa.gamingMode="private";}')
  page.evaluate('()=>runGamingSync(true)');failed_count=page.evaluate('qa.gamingCalls.filter(c=>c.url.includes("/steam?")).length')
  page.evaluate('()=>runGamingSync(true)')
  check('Automatic private-profile error sets backoff instead of repeated polling',page.evaluate('qa.gamingCalls.filter(c=>c.url.includes("/steam?")).length')==failed_count)
  check('Automatic failure retains previously earned achievement',page.evaluate('state.gaming.sets[2].achievements[0].earned===true'))
  page.evaluate('()=>{qa.passGamingDeadline();qa.gamingMode="ok";gamingRetry.clear();render();}');page.evaluate('()=>runGamingSync(true)')
  portable=page.evaluate('toPortableBackup(state)');check('Portable backup upgraded to schema nine and contains all sets',portable['schema']==11 and len(portable['gaming']['sets'])==3)
  check('Portable backup round trip disables auto-sync consent',page.evaluate('fromPortableBackup(toPortableBackup(state)).gaming.accounts.every(a=>!a.autoSync)'))
  check('Portable round trip preserves notes and earned dates',page.evaluate('fromPortableBackup(toPortableBackup(state)).gaming.sets[2].achievements[0].earnedAt')==1700000000000)
  page.evaluate('()=>{overlay={type:"stats",title:"Statistiques"};render();}');check('Gaming statistics integrated into normal stats page',page.locator('#gamingStatistics').count()==1 and 'cases personnelles' in page.locator('#gamingStatistics').inner_text())
  page.evaluate('(id)=>openGameAchievements(id)',game);page.locator('#achievementSetSelect').select_option(sid)
  page.set_viewport_size({'width':390,'height':844});page.wait_for_function('parseFloat(getComputedStyle(document.querySelector("#toast")).opacity)<0.01');page.screenshot(path=str(OUT/'fixture-v011-steam-390.png'),full_page=True)
  check('Mobile trophy screen has no horizontal page overflow',page.evaluate('document.documentElement.scrollWidth<=window.innerWidth+1'))
  page.evaluate('()=>{overlay={type:"gaming",title:"Trophées & comptes"};render();}');page.screenshot(path=str(OUT/'fixture-v011-accounts-390.png'),full_page=True)
  check('Mobile account hub has no horizontal page overflow',page.evaluate('document.documentElement.scrollWidth<=window.innerWidth+1'))
  # Persist failure must retain previous library and exportable draft, including trophy data.
  page.evaluate('(id)=>openGameAchievements(id)',game);before=page.evaluate('JSON.stringify(state.gaming)');page.evaluate('()=>{window.originalSet=localStorage.setItem;localStorage.setItem=(k,v)=>{if(k===KEY)throw Error("Quota fixture");return originalSet(k,v)};}')
  page.locator('[data-ach-pin]').first.click();check('Storage failure rolls back trophy state atomically',page.evaluate('JSON.stringify(state.gaming)')==before)
  check('Storage failure leaves an exportable gaming draft',page.evaluate('storageDraft?.gaming?.sets.length')==3)
  page.evaluate('localStorage.setItem=originalSet');page.locator('#exportDraft').click();check('Draft export includes gaming data without platform secret',page.evaluate('qa.downloads.at(-1).value.gaming.sets.length')==3)
  page.evaluate('()=>{storageDraft=null;document.querySelector("#storageBanner")?.remove();}')
  # Rendering and filtering scale by pages, not a huge synchronous DOM.
  page.evaluate('()=>{const s=state.gaming.sets[2];gamingCommit(g=>g.sets[2].achievements=Array.from({length:5000},(_,i)=>FGGaming.normalizeAchievement({id:"stress-"+i,title:"Objectif "+i,groupId:"unknown",earned:false})));gameView(s.mediaId).limit=50;render();}')
  check('Five thousand success definitions render first fifty cards',page.locator('[data-achievement]').count()==50)
  page.locator('#moreAchievements').click();check('Next achievement page adds fifty cards',page.locator('[data-achievement]').count()==100)
  page.locator('#achievementQuery').fill('Objectif 4999');check('Search covers success definitions beyond loaded page',page.locator('[data-achievement="stress-4999"]').count()==1)
  # Schema future stays protected and old schemas receive empty gaming only.
  check('Future schema ten rejected',page.evaluate('()=>{try{fromPortableBackup({schema:12,media:[]});return false}catch{return true}}'))
  check('Legacy schema six accepted without inventing trophies',page.evaluate('fromPortableBackup({schema:6,media:[],episodes:[]}).gaming.sets.length')==0)
  page.evaluate(RESET)
  page.evaluate('()=>importSg([JSON.stringify([{tmdb_id:101,title:"Série importée de QA",language:"ja-JP",seasons:[]}])],["seriesguide-shows.json"])')
  check('SeriesGuide import preserves per-series language',page.evaluate('state.media[0].contentLanguage')=='ja-JP')
  page.evaluate('()=>{state.media[0].contentLanguage="en-US";save(false);importSg([JSON.stringify([{tmdb_id:101,title:"Série importée de QA",language:"fr-FR",seasons:[]}])],["seriesguide-shows.json"]);}')
  check('Repeated SeriesGuide import respects existing local language override',page.evaluate('state.media[0].contentLanguage')=='en-US')
  check('No unhandled browser script error',not errors)
  result={'version':'0.11.0','scope':'in-memory DOM with mocked APIs/storage/images; actual loopback navigation blocked','passed':len(checks),'checks':checks,'pageErrors':errors};(OUT/'browser-v011.json').write_text(json.dumps(result,ensure_ascii=False,indent=2));b.close();print(json.dumps({'passed':len(checks),'errors':errors}))
if __name__=='__main__':
 try:run()
 except Exception:
  (OUT/'browser-v011-failure.json').write_text(json.dumps({'checks':checks,'traceback':traceback.format_exc()},ensure_ascii=False,indent=2));raise
