"""V0.20 real DOM/scripts/CSS with controlled fetch/images/storage. No live provider or installed PWA claim."""
from pathlib import Path
from playwright.sync_api import sync_playwright
from browser_harness import ROOT,WEB,SETUP,RESET,AUTH
import re,json,base64,traceback
OUT=ROOT/'tests/results/v020';OUT.mkdir(parents=True,exist_ok=True);checks=[]
def check(name,value=True):
 if not value:raise AssertionError(name)
 checks.append({'name':name,'passed':True});print('PASS',name,flush=True)
def run():
 with sync_playwright() as p:
  b=p.chromium.launch(executable_path='/usr/bin/chromium',headless=True,args=['--no-sandbox']);ctx=b.new_context(viewport={'width':1360,'height':1000},locale='fr-FR',timezone_id='Europe/Paris');page=ctx.new_page();page.set_default_timeout(6500);errors=[]
  page.on('pageerror',lambda e:errors.append(str(e)));page.on('dialog',lambda d:d.accept())
  png=base64.b64decode('iVBORw0KGgoAAAANSUhEUgAAAAIAAAACCAYAAABytg0kAAAAFElEQVQImWNkYPj/n4GBgYGJAQoAHhgCAQGXeHsAAAAASUVORK5CYII=')
  ctx.route(re.compile(r'https://(image\.tmdb\.org|covers\.openlibrary\.org)/'),lambda r:r.fulfill(status=200,content_type='image/png',body=png))
  html=(WEB/'index.html').read_text();page.set_content(re.sub(r'<script.*?</script>|<link[^>]+>|<meta http-equiv="Content-Security-Policy"[^>]+>','',html));page.add_style_tag(content=(WEB/'app.css').read_text());page.evaluate(SETUP)
  for f in re.findall(r'<script src="([^"]+)"',html):page.add_script_tag(content=(WEB/f).read_text())
  page.wait_for_timeout(60);page.evaluate('()=>{window.qaEnrich=enrichCatalog;window.qaBlobUrl=URL.createObjectURL;URL.createObjectURL=blob=>{qa.exportedBlob=blob;return qaBlobUrl(blob)};}')
  def reset():
   page.evaluate(RESET);page.evaluate('''()=>{libraryEpoch++;catalogMaintenance={running:false,cancelled:false,controller:null,message:'QA',updated:0,failed:0};catalogRefreshRunning=false;recoveryOnly=null;state.settings.catalogAutoRefresh=false;statisticsView={kind:'all',days:0,from:'',to:'',includeHidden:false,includeSpecials:false,favoritesOnly:false};Object.assign(historyView,{kind:'all',days:0,from:'',to:'',includeHidden:false,includeSpecials:false,favoritesOnly:false,query:'',limit:100});catalogueView={query:'',filter:'all',limit:60};enrichCatalog=qaEnrich;window.downloadJson=(name,data)=>{qa.download={name,data}};window.downloadBlob=(name,text,type)=>{qa.blob={name,text,type}};FGRequests.clear();}''')
  def open_game(kind='game'):
   page.evaluate('''k=>{const now=Date.now();state.media=[{id:'run',kind:k,title:k==='game'?'Jeu de parcours QA':'Livre de parcours QA',added:true,source:'local',watched:true,plays:1,watchHistory:[now-86400000],lastWatchedAt:now-86400000,progressCurrent:100,progressTotal:100,progressUnit:k==='game'?'%':'pages',trackingStatus:'completed',listIds:[],ratings:[],credits:[]}];save(false);overlay={type:'item',id:'run',title:state.media[0].title,src:'media'};render();}''',kind)
  reset();open_game('book');original=page.evaluate('state.media[0].lastWatchedAt')
  check('Current run editor appears in book detail',page.locator('#currentRunStatus').count()==1)
  page.locator('#cur').fill('20');page.locator('#saveProg').click()
  check('Lowering current pages preserves old reading',page.evaluate('state.media[0].watched&&state.media[0].plays===1&&state.media[0].watchHistory.length===1'))
  check('Lowering pages preserves historical last date',page.evaluate('state.media[0].lastWatchedAt')==original)
  check('Lowered run is active, not completed',page.evaluate('state.media[0].trackingStatus')=='active')
  page.locator('#currentRunStatus').select_option('paused');page.locator('#saveProg').click()
  check('Pause changes current state only',page.evaluate('state.media[0].trackingStatus==="paused"&&state.media[0].watched'))
  page.evaluate('()=>{overlay=null;screen="books";tab=4;render();}');check('Paused library tab includes previously finished title',page.locator('[data-open="run"]').count()==1)
  page.locator('[data-open="run"] .open-item').click();page.locator('#restartProgress').click()
  check('Restart starts a fresh run without deleting history',page.evaluate('state.media[0].progressCurrent===0&&state.media[0].trackingStatus==="active"&&state.media[0].plays===1'))
  page.locator('#personalFormat').fill('Édition annotée');page.locator('#cur').fill('200');page.locator('#tot').fill('200');page.locator('#saveProg').click()
  check('New run completion adds exactly one date',page.evaluate('state.media[0].plays===2&&state.media[0].watchHistory.length===2'))
  check('Completion retains submitted total and edition',page.evaluate('state.media[0].progressTotal===200&&state.media[0].personalFormat==="Édition annotée"'))
  page.locator('#saveProg').click();check('Repeated save of completed state is idempotent',page.evaluate('state.media[0].plays')==2)
  page.locator('#cur').fill('201');page.locator('#saveProg').click();check('Invalid progress reported without mutation',page.locator('#progressError').is_visible() and page.evaluate('state.media[0].progressCurrent')==200)
  reset();open_game();page.locator('#restartProgress').click();page.locator('#cur').fill('5');page.locator('#tot').fill('10');page.locator('#progressUnit').fill('missions');page.locator('#personalPlatform').fill('PS5');page.locator('#personalGameMinutes').fill('135');page.locator('#saveProg').click()
  check('Game progress keeps custom units platform and real time',page.evaluate('state.media[0].progressCurrent===5&&state.media[0].progressUnit==="missions"&&state.media[0].personalPlatform==="PS5"&&state.media[0].playtimeMinutes===135'))
  page.locator('#personalGameMinutes').fill('');page.locator('#saveProg').click();check('Blank personal time is unknown, not fabricated zero',page.evaluate('state.media[0].playtimeMinutes') is None)
  check('Portable schema nine carries current run independently',page.evaluate('()=>{const p=toPortableBackup(state);return p.schema===11&&p.media[0].trackingStatus==="active"&&p.media[0].watched&&p.media[0].plays===1}'))
  check('Portable roundtrip retains edition and personal platform',page.evaluate('fromPortableBackup(toPortableBackup(state)).media[0].personalPlatform')=='PS5')
  # Storage refusal cannot erase completion history or claim the failed progress was durable.
  before=page.evaluate('localStorage.getItem(KEY)');page.evaluate('()=>{qa.originalSet=localStorage.setItem;localStorage.setItem=(k,v)=>{if(k===KEY)throw Error("fixture quota");return qa.originalSet(k,v)}}');page.locator('#cur').fill('6');page.locator('#saveProg').click()
  check('Progress write refusal leaves persisted library intact',page.evaluate('localStorage.getItem(KEY)')==before)
  check('Write refusal retains exportable progress draft',page.evaluate('storageDraft.media[0].progressCurrent')==6)
  page.evaluate('()=>{localStorage.setItem=qa.originalSet;}')
  # Statistics are read-only and account-aware. One unknown historical reading is disclosed.
  reset();page.evaluate('''()=>{const d1=Date.now()-2*86400000,d2=Date.now()-86400000;state.media=[
   {id:'m',kind:'movie',title:'Film à dates connues',added:true,watched:true,plays:3,watchHistory:[d1,d2],lastWatchedAt:d2,runtimeMinutes:100,listIds:[],favorite:true},
   {id:'b',kind:'book',title:'Livre en relecture',added:true,watched:true,plays:1,lastWatchedAt:d1,progressCurrent:20,progressTotal:200,progressUnit:'pages',trackingStatus:'active',listIds:[]},
   {id:'pct',kind:'book',title:'Livre suivi en pourcentage',added:true,watched:false,progressCurrent:50,progressTotal:100,progressUnit:'%',listIds:[]},
   {id:'g',kind:'game',title:'Jeu Steam et temps personnel',added:true,watched:false,playtimeMinutes:30,listIds:[],steam:{appId:'123',accounts:{'76561198000000001':{playtimeMinutes:120,lastPlayedAt:null},'76561198000000002':{playtimeMinutes:null,lastPlayedAt:null}}}},
   {id:'s',kind:'show',title:'Série incomplète',added:true,catalogComplete:false,episodeCount:1,lastWatchedAt:d2,watched:true,listIds:[]},
   {id:'h',kind:'movie',title:'Film masqué QA',added:true,hidden:true,watched:true,plays:1,lastWatchedAt:d1,runtimeMinutes:0,listIds:[]}];
   state.episodes=[{id:'e',showId:'s',season:1,num:1,title:'Pilote',watched:true,plays:1,lastWatchedAt:d1,runtimeMinutes:45},{id:'special',showId:'s',season:0,num:1,title:'Spécial QA',watched:true,plays:1,lastWatchedAt:d1,runtimeMinutes:60},{id:'orphan',showId:'gone',season:1,num:1,title:'Orphelin',watched:true,plays:1,lastWatchedAt:d1,runtimeMinutes:45}];
   state.gaming={version:1,accounts:[{id:'a',provider:'steam',externalId:'76561198000000001',label:'Profil privé A',autoSync:false},{id:'b',provider:'steam',externalId:'76561198000000002',label:'Profil privé B',autoSync:false}],sets:[{id:'set',mediaId:'g',provider:'steam',platform:'PC',accountId:'a',externalGameId:'123',title:'Relevé QA',groups:[{id:'u',name:'Inconnu',kind:'unknown'}],source:'file-import',completeCatalog:true,achievements:[{id:'1',title:'Normal + HC',groupId:'u',grade:'achievement',earned:true,hardcoreEarned:true,localDone:true},{id:'2',title:'Inconnu',groupId:'u',grade:'achievement',earned:null,localDone:true}]}]};save(false);overlay={type:'stats',title:'Statistiques'};render();}''')
  unchanged=page.evaluate('localStorage.getItem(KEY)');calls=page.evaluate('qa.calls.length')
  check('Stats uses one panel for gaming instead of duplicate totals',page.locator('#gamingStatistics').count()==1)
  check('Pages counter excludes percentage-based books',page.locator('[data-insight="pages"] b').inner_text()=='20')
  check('Current game time is separate from Steam time',page.locator('[data-insight="manualMinutes"] b').inner_text()==page.evaluate('formatMinutes(30)') and '2h' in page.locator('#gamingStatistics').inner_text())
  check('Steam profiles and unknown minutes remain distinct',page.locator('.provider-insight h4').count()>=2 and 'temps inconnu' in page.locator('#gamingStatistics').inner_text())
  check('Series incomplete is not labeled caught up',page.locator('[data-insight="caught"] b').inner_text()=='0 / 1')
  check('Unknown old dates disclosed without fake events',page.locator('[data-insight="undated"] b').inner_text()=='1')
  check('Library source selection excludes hidden title',page.locator('[data-insight="titles"] b').inner_text()=='5')
  page.locator('#statsHidden').check();check('Hidden inclusion is explicit and updates counters',page.locator('[data-insight="titles"] b').inner_text()=='6')
  page.locator('#statsKind').select_option('book');check('Category filter isolates books, not game data',page.locator('#gamingStatistics').count()==0 and page.locator('[data-insight="titles"] b').inner_text()=='2')
  page.locator('#statsFavorites').check();check('Favorites filter does not fabricate a matching entry',page.locator('[data-insight="titles"] b').inner_text()=='0');page.locator('#statsFavorites').uncheck()
  page.locator('#statsKind').select_option('all');page.locator('#statsHidden').uncheck();page.locator('#statsDays').select_option('-1');page.locator('#statsFrom').fill('2000-01-01');page.locator('#statsTo').fill('2000-01-02');page.locator('#statsApply').click()
  check('Empty period does not pretend library itself is empty',page.locator('[data-insight="titles"] b').inner_text()=='5' and page.locator('[data-insight="window"] b').inner_text()=='0')
  page.locator('#statsFrom').fill('2000-01-03');page.locator('#statsApply').click();check('Inverted period is rejected inline',page.locator('#statsFilterError').is_visible())
  check('Read-only stats filters create no storage write or API call',page.evaluate('localStorage.getItem(KEY)')==unchanged and page.evaluate('qa.calls.length')==calls)
  page.locator('#statsDays').select_option('0');page.locator('#exportStats').click();report=page.evaluate('qa.download')
  check('Stats JSON omits titles private account labels and identifiers',not any(x in json.dumps(report,ensure_ascii=False) for x in ['Film à dates connues','Profil privé','76561198000000001']))
  page.locator('#openFilteredHistory').click();check('Stats opens same filtered history',page.locator('.history-entry').count()==4)
  page.locator('#historySearch').fill('film');check('History searches all matching events',page.locator('.history-entry').count()==2)
  page.locator('#historySearch').fill('sans résultat');check('Search does not remove undated counters','sans date exploitable' in page.locator('main').inner_text());page.locator('#historySearch').fill('')
  page.locator('#exportActivity').click();check('History JSON contains only four known date rows',len(page.evaluate('qa.download.data.entries'))==4)
  page.locator('#exportActivityCsv').click();check('CSV includes BOM and quoted video/event fields',page.evaluate('async()=>Array.from(new Uint8Array(await qa.exportedBlob.arrayBuffer()).slice(0,3)).join(",")')=='239,187,191' and 'Film à dates connues' in page.evaluate('async()=>await qa.exportedBlob.text()'))
  # Long-history pagination and whole-selection search remain bounded.
  page.evaluate('''()=>{const at=Date.now()-10000;state.media=Array.from({length:205},(_,i)=>({id:'long'+i,kind:'movie',title:i===204?'Dernier film recherché':'Lecture QA '+i,added:true,watched:true,plays:1,lastWatchedAt:at-i*1000,listIds:[]}));state.episodes=[];state.gaming={version:1,accounts:[],sets:[]};historyView.query='';historyView.days=0;historyView.limit=100;render();}''')
  check('History initially builds one hundred entries',page.locator('.history-entry').count()==100);page.locator('#historyMore').click();check('History builds next hundred on demand',page.locator('.history-entry').count()==200)
  page.locator('#historySearch').fill('dernier');check('Search finds event beyond rendered batch',page.locator('.history-entry').count()==1)
  # Catalogue maintenance avoids starvation and respects provider-level limits.
  reset();page.evaluate('''()=>{state.settings.tmdbKey='qa-key';state.settings.rawgKey='qa-rawg';state.media=Array.from({length:10},(_,i)=>({id:'m'+i,kind:'movie',title:'Catalogue QA '+i,source:'tmdb',sourceId:String(333+i),tmdbId:333+i,added:true,catalogComplete:false,listIds:[],userNote:'note '+i}));save(false);overlay={type:'catalog-health',title:'Entretien'};render();}''')
  check('Maintenance reachable with incomplete status per title',page.locator('.catalog-health-row').count()==10)
  page.evaluate('async()=>await runCatalogueBatch()');check('Manual catalogue batch reads maximum eight records',page.evaluate('state.media.filter(m=>m.catalogComplete).length')==8)
  check('Batch catalogue refresh retains private notes',page.evaluate('state.media.every((m,i)=>m.userNote==="note "+i)'))
  page.evaluate('async()=>await runCatalogueBatch()');check('Next batch completes remaining records without rereading first eight',page.evaluate('state.media.filter(m=>m.catalogComplete).length')==10)
  check('Maintenance runtime excluded from portable backup',page.evaluate('!Object.hasOwn(toPortableBackup(state),"_catalogMaintenance")'))
  reset();page.evaluate('''()=>{state.settings.tmdbKey='qa-key';state.media=Array.from({length:5},(_,i)=>({id:'m'+i,kind:'movie',title:'Auto QA '+i,source:'tmdb',tmdbId:333+i,added:true,catalogComplete:false,listIds:[]}));save(false);state.settings.catalogAutoRefresh=true;}''');page.evaluate('async()=>await repairIncompleteCatalog()')
  check('Automatic repair bounded to three records',page.evaluate('state.media.filter(m=>m.catalogComplete).length')==3)
  reset();page.evaluate('''()=>{state.settings.tmdbKey='qa-key';state.media=[{id:'m',kind:'movie',title:'429 QA',source:'tmdb',tmdbId:333,added:true,listIds:[]},{id:'n',kind:'movie',title:'Deferred QA',source:'tmdb',tmdbId:334,added:true,listIds:[]},{id:'b',kind:'book',title:'OL QA',source:'openlibrary',sourceId:'/works/OL1W',added:true,listIds:[]}];save(false);qa.maintenanceCalls=[];enrichCatalog=async(m,o)=>{qa.maintenanceCalls.push(m.id);if(m.id==='m')throw Object.assign(Error('Rate limited'),{status:429,retryAfter:'120'});return qaEnrich(m,o)};overlay={type:'catalog-health'};render();}''');page.evaluate('async()=>await runCatalogueBatch()')
  check('429 blocks same provider but not Open Library',sorted(page.evaluate('qa.maintenanceCalls'))==['b','m'])
  check('Provider cooldown stored durably',page.evaluate('JSON.parse(localStorage.getItem(KEY))._catalogMaintenance.providers[0].retryAt>Date.now()'))
  check('Per-title refresh disabled during cooldown',page.locator('[data-catalog-read="n"]').is_disabled())
  calls=page.evaluate('qa.maintenanceCalls.length');page.evaluate('async()=>await refreshCatalogItem(state.media[1])');check('Manual refresh cannot bypass provider cooldown',page.evaluate('qa.maintenanceCalls.length')==calls)
  page.evaluate('()=>{const m=state.media[1];m.catalogComplete=true;m.catalogUpdatedAt=Date.now();m.posterUrl=null;state._catalogMaintenance={version:1,items:[],providers:[]};state.media=[m];save(false);render();}')
  check('Missing poster alone is not incomplete failure loop',page.evaluate('catalogRows()[0].status')=='current')
  reset();page.evaluate('''()=>{state.settings.tmdbKey='qa-key';state.media=[{id:'m',kind:'movie',source:'tmdb',tmdbId:333,title:'Reprise QA',added:true,listIds:[]},{id:'n',kind:'movie',source:'tmdb',tmdbId:334,title:'Suivant QA',added:true,listIds:[]}];save(false);qa.maintenanceCalls=[];enrichCatalog=async(m,o)=>{qa.maintenanceCalls.push(m.id);if(m.id==='m')throw Error('Server failure');return qaEnrich(m,o)};}''');page.evaluate('async()=>await runCatalogueBatch()')
  check('First failing title does not starve next title',page.evaluate('qa.maintenanceCalls')==['m','n'] and page.evaluate('state.media[1].catalogComplete'))
  calls=page.evaluate('qa.maintenanceCalls.length');page.evaluate('async()=>await runCatalogueBatch()');check('Failed record waits before retry',page.evaluate('qa.maintenanceCalls.length')==calls)
  # Cancel must stop the batch, not just the first fetch; no provider error recorded for user cancellation.
  reset();page.evaluate('''()=>{state.settings.tmdbKey='qa';state.media=[{id:'m',kind:'movie',tmdbId:333,source:'tmdb',title:'Slow QA',added:true,listIds:[]},{id:'n',kind:'movie',tmdbId:334,source:'tmdb',title:'Do not run QA',added:true,listIds:[]}];save(false);overlay={type:'catalog-health'};render();qa.maintenanceCalls=[];enrichCatalog=async(m,o)=>{qa.maintenanceCalls.push(m.id);const result=await qaEnrich(m,o);await new Promise(resolve=>{qa.releaseMaintenance=resolve});return result;};void runCatalogueBatch();}''');page.wait_for_function('typeof qa.releaseMaintenance==="function"');page.locator('#cancelCatalogWork').click();page.evaluate('()=>{qa.releaseMaintenance();}');page.wait_for_function('!catalogMaintenance.running')
  check('Cancel stops subsequent title reads',page.evaluate('qa.maintenanceCalls')==['m'])
  check('User cancel leaves no incomplete catalogue committed',page.evaluate('state.media.every(m=>!m.catalogComplete)'))
  check('User cancel not recorded as provider outage',page.evaluate('FGCatalog.runtime(state._catalogMaintenance).items.length')==0)
  reset();page.evaluate('''()=>{state.settings.tmdbKey='qa';state.media=[{id:'m',kind:'movie',tmdbId:333,source:'tmdb',title:'Late QA',added:true,listIds:[]}];save(false);enrichCatalog=async(m,o)=>{const result=await qaEnrich(m,o);await new Promise(resolve=>qa.releaseMaintenance=resolve);return result};void refreshCatalogItem(state.media[0]);}''');page.wait_for_function('typeof qa.releaseMaintenance==="function"');page.evaluate('()=>{libraryEpoch++;state.media=[];save(false);qa.releaseMaintenance();}');page.wait_for_function('!catalogMaintenance.running')
  check('Late catalogue response cannot resurrect removed title',page.evaluate('state.media.length')==0)
  reset();page.evaluate('''()=>{state.settings.rawgKey='qa';state.media=[{id:'g',kind:'game',rawgId:44,source:'rawg',sourceId:'44',title:'Jeu privé',added:true,watched:true,plays:2,watchHistory:[Date.now()-10000,Date.now()-5000],progressCurrent:3,progressTotal:20,progressUnit:'missions',trackingStatus:'paused',playtimeMinutes:55,personalPlatform:'PS5',userNote:'Privé',listIds:[]}];save(false);}''');page.evaluate('async()=>await refreshCatalogItem(state.media[0])')
  check('RAWG refresh preserves current rerun state private fields and history',page.evaluate('state.media[0].progressCurrent===3&&state.media[0].trackingStatus==="paused"&&state.media[0].playtimeMinutes===55&&state.media[0].personalPlatform==="PS5"&&state.media[0].watchHistory.length===2'))
  check('Automatic catalogue pass skips unsupported Steam or manual identity',page.evaluate('()=>{state.media=[{id:"manual",kind:"game",source:"local",title:"Manual",added:true},{id:"steam",kind:"game",source:"steam",sourceId:"123",title:"Steam",added:true}];return catalogRows().every(r=>r.status==="unsupported")}'))
  # Actual responsive DOM/CSS, fixtures explicitly labeled.
  reset();open_game();
  page.wait_for_function('parseFloat(getComputedStyle(document.querySelector("#toast")).opacity)<0.01')
  for width in [390,768,1360]:
   page.set_viewport_size({'width':width,'height':900});page.screenshot(path=str(OUT/f'fixture-personal-progress-{width}.png'),full_page=True)
   check('Personal progress fits viewport '+str(width),page.evaluate('document.documentElement.scrollWidth<=innerWidth+1'))
   page.evaluate('()=>{overlay={type:"stats",title:"Stats"};render();}');page.screenshot(path=str(OUT/f'fixture-insights-{width}.png'),full_page=True)
   check('Statistics fits viewport '+str(width),page.evaluate('document.documentElement.scrollWidth<=innerWidth+1'))
   page.evaluate('()=>{overlay={type:"catalog-health",title:"Entretien"};render();}');page.screenshot(path=str(OUT/f'fixture-catalog-health-{width}.png'),full_page=True)
   check('Catalogue health fits viewport '+str(width),page.evaluate('document.documentElement.scrollWidth<=innerWidth+1'))
   page.evaluate('()=>{overlay={type:"item",id:"run",title:state.media[0].title,src:"media"};render();}')
  check('No uncaught JavaScript errors in V0.20 tests',not errors)
  (OUT/'browser-v020.json').write_text(json.dumps({'version':'0.21.0','scope':'real DOM/CSS/scripts; controlled provider/image/storage fixtures; no live identity or real-origin persistence','passed':len(checks),'checks':checks,'errors':errors},ensure_ascii=False,indent=2));print('V0.20 Chromium assertions:',len(checks));b.close()
if __name__=='__main__':
 try:run()
 except Exception:
  (OUT/'browser-v020-failure.json').write_text(json.dumps({'checks':checks,'traceback':traceback.format_exc()},ensure_ascii=False,indent=2));raise
