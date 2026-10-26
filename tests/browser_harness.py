"""Offline Chromium integration tests. Real app scripts and DOM, controlled JSON/image fixtures.
Uses set_content because this environment blocks top-level loopback/file navigation.
This is NOT a live OAuth, persistent browser-storage, service-worker, or Android test.
Run: python tests/browser_harness.py [--chromium /path/to/chromium]
"""
from pathlib import Path
from playwright.sync_api import sync_playwright
import re,json,traceback,sys,base64,argparse
ROOT=Path(__file__).resolve().parents[1];WEB=ROOT/'web';OUT=ROOT/'tests/results';OUT.mkdir(exist_ok=True)
checks=[]
def check(name,condition=True):
    if not condition: raise AssertionError(name)
    checks.append({'name':name,'passed':True});print('PASS',name,flush=True)
SETUP=r'''() => {
 const storage={};window.__qaStorage=storage;
 Object.defineProperty(window,'localStorage',{value:{getItem:k=>storage[k]??null,setItem:(k,v)=>storage[k]=String(v),removeItem:k=>delete storage[k],clear:()=>{for(const k in storage)delete storage[k]}}});
 window.Audio=class{play(){return Promise.resolve()}pause(){}};
 window.qa={calls:[],badSeason:false,badShow:false,remote:{},failedPath:null,refreshStatus:200,lostHistory:false,devicePending:false};
 const res=(data,status=200,headers={})=>new Response(data==null?'':JSON.stringify(data),{status,headers:{'Content-Type':'application/json',...headers}});
 const show=()=>({id:111,name:'Série de validation',original_name:'QA Series',overview:'Fiche fictive de validation automatisée. Ne représente aucune série réelle.',first_air_date:'2025-01-01',last_air_date:'2025-02-01',poster_path:'/qa-poster.png',backdrop_path:'/qa-backdrop.png',genres:[{name:'Science-fiction'}],status:'Returning Series',vote_average:8.5,vote_count:123,episode_run_time:[45],number_of_seasons:2,number_of_episodes:qa.badShow?99:4,networks:[{name:'Réseau test'}],created_by:[{name:'Auteur test'}],seasons:[{season_number:0,episode_count:1},{season_number:1,episode_count:2},{season_number:2,episode_count:2}],aggregate_credits:{cast:[{id:88,name:'Personne de validation',profile_path:'/qa-person.png',roles:[{character:'Personnage test'}]}]},external_ids:{imdb_id:'tt111'},videos:{results:[]},content_ratings:{results:[]}});
 window.fetch=async(url,options={})=>{
   url=String(url);const u=new URL(url,'http://test.local'),path=u.pathname,method=options.method||'GET';qa.calls.push({path,search:u.search,method,body:options.body?JSON.parse(options.body):null});
   if(qa.failedPath&&path.includes(qa.failedPath))return res({error:'Controlled unavailable'},503);
   if(u.hostname==='api.themoviedb.org'){
     if(path.includes('/search/tv'))return res({page:Number(u.searchParams.get('page')||1),total_pages:2,results:[{id:Number(u.searchParams.get('page')||1)===1?111:222,name:'Série de validation',poster_path:'/qa-poster.png',first_air_date:'2025-01-01'}]});
     if(path.includes('/search/movie'))return res({page:1,total_pages:1,results:[{id:333,title:'Film de validation',poster_path:'/qa-poster.png',release_date:'2025-01-01'}]});
     if(path.match(/\/tv\/\d+$/))return res(show());
     if(path.match(/\/tv\/\d+\/season\/\d+$/)){
       const number=Number(path.split('/').pop()),count=number===0?1:qa.badSeason&&number===2?1:2;
       return res({id:900+number,name:'Saison '+number,poster_path:'/qa-poster.png',overview:'Résumé de saison de test.',episodes:Array.from({length:count},(_,i)=>({id:11100+number*10+i+1,episode_number:i+1,name:'Épisode '+(i+1),air_date:'2025-01-0'+(i+1),still_path:'/qa-still.png',overview:'Résumé de test à masquer en mode spoilers.',runtime:45,vote_average:0,vote_count:0})),'watch/providers':{results:{FR:{rent:[{provider_name:'Fournisseur test'}]}}}});
     }
     if(path.endsWith('/watch/providers'))return res({results:{FR:{flatrate:[{provider_name:'Fournisseur test'}]}}});
     if(path.match(/\/movie\/\d+$/))return res({id:333,title:'Film de validation',overview:'Film de validation',runtime:100,poster_path:'/qa-poster.png',genres:[{name:'Drame'}],vote_average:7,vote_count:21,credits:{cast:[]},videos:{results:[]},release_date:'2025-01-01'});
     if(path.includes('/discover/'))return res({page:1,total_pages:1,results:[]});
     if(path.includes('/find/'))return res({tv_results:[{id:111}],movie_results:[{id:333}]});
   }
   if(u.hostname==='api.rawg.io')return res(path.match(/\/games\/\d+$/)?{id:44,name:'Jeu de validation',description_raw:'Description de test',rating:4,background_image:'https://image.tmdb.org/t/p/w500/qa-poster.png',platforms:[{platform:{name:'PC'}}]}:{results:[{id:44,name:'Jeu de validation',background_image:'https://image.tmdb.org/t/p/w500/qa-poster.png'}],next:null});
   if(u.hostname==='openlibrary.org')return res(path==='/search.json'?{numFound:1,docs:[{key:'/works/OL1W',title:'Livre de validation',author_name:['Auteur test'],cover_i:12,number_of_pages_median:240}]}:{key:'/works/OL1W',description:'Description du livre de validation',subjects:['Roman'],covers:[12]});
   if(u.hostname==='auth.trakt.tv'){
     if(path==='/oauth/token'){await new Promise(r=>setTimeout(r,30));return qa.refreshStatus===200?res({access_token:'qa-refreshed',refresh_token:'qa-refresh',expires_in:86400,created_at:Math.floor(Date.now()/1000)}):res({error:'temporarily_unavailable'},qa.refreshStatus);}
     if(path==='/oauth/device/code')return res({device_code:'QA-DEVICE',user_code:'QA-CODE',verification_url:'https://trakt.tv/activate',expires_in:100,interval:1});
     if(path==='/oauth/device/token')return qa.devicePending?res({},400):res({access_token:'qa',refresh_token:'qa-refresh',expires_in:86400,created_at:Math.floor(Date.now()/1000)});
   }
   if(u.hostname==='api.trakt.tv'){
     if(path==='/users/settings')return res({user:{username:'qa-user',name:'Compte de test'}});
     if(method==='GET'&&path.startsWith('/search/'))return res([]);
     if(method==='GET'&&path.startsWith('/sync/')){
       const rows=qa.remote[path]||[],page=Number(u.searchParams.get('page')||1),pages=path===qa.pagedPath?2:1;
       return res(pages===2?rows.slice(page-1,page):rows,200,{'X-Pagination-Page-Count':String(pages)});
     }
     if(method==='POST'&&path.startsWith('/sync/')){
       const body=JSON.parse(options.body);
       if(path==='/sync/history'&&body.movies){qa.remote['/sync/watched/movies']=(qa.remote['/sync/watched/movies']||[]).concat(body.movies.map(m=>({movie:{title:'Film de validation',ids:m.ids},plays:1,last_watched_at:m.watched_at})));}
       if(path==='/sync/history'&&qa.lostHistory){qa.lostHistory=false;throw Error('Controlled lost acknowledgement');}
       return res({added:{movies:body.movies?.length||0,episodes:body.episodes?.length||0},not_found:{movies:[],episodes:[]}});
     }
     if(path.includes('/comments'))return res([]);
   }
   throw Error('Unhandled fixture '+method+' '+url);
 };
}'''
RESET=r'''() => {
 clearTimeout(traktSaveTimer);authGeneration++;traktSyncing=false;refreshPromise=null;
 localStorage.clear();storageRevision=0;storageWritable=true;storedSyncSnapshot=null;FGRequests.clear();seriesViews.clear();libraryViews.clear();$('#storageBanner')?.remove();storageDraft=null;state=clone(seed);state.settings.catalogAutoRefresh=false;state.settings.autoBackupEnabled=false;state.settings.traktAutoSync=false;state.settings.detailJingleEnabled=false;
 syncState={queue:[],baseline:{},remote:[],errors:[]};savedState=clone(state);overlay=null;screen='shows';tab=0;remoteResults=[];searchGeneration++;catalogBusy.clear();qa.calls=[];qa.badSeason=false;qa.badShow=false;qa.failedPath=null;qa.remote={};qa.pagedPath=null;qa.refreshStatus=200;
 document.querySelectorAll('dialog[open]').forEach(d=>d.close());render();
}'''
AUTH=r'''() => {state.settings.traktId='qa-client';state.settings.traktSecret='qa-secret';state.settings.traktAutoSync=false;save(false);saveTraktAuth({accessToken:'qa-access',refreshToken:'qa-refresh',expiresAt:Math.floor(Date.now()/1000)+86400,username:'qa-user'});syncState.account='qa-user';savedState=clone(state);}'''

def run():
 ap=argparse.ArgumentParser();ap.add_argument('--chromium',default='/usr/bin/chromium');args=ap.parse_args()
 with sync_playwright() as p:
  b=p.chromium.launch(executable_path=args.chromium,headless=True,args=['--no-sandbox'])
  context=b.new_context(viewport={'width':1360,'height':1000},locale='fr-FR',timezone_id='Europe/Paris');page=context.new_page();page.set_default_timeout(5000);errors=[]
  page.on('pageerror',lambda e:errors.append(str(e)));page.on('dialog',lambda d:d.accept())
  # Static image fixture: verifies <img> load and layout, not real remote posters.
  png=base64.b64decode('iVBORw0KGgoAAAANSUhEUgAAAAIAAAACCAYAAABytg0kAAAAFElEQVQImWNkYPj/n4GBgYGJAQoAHhgCAQGXeHsAAAAASUVORK5CYII=')
  context.route(re.compile(r'https://(image\.tmdb\.org|covers\.openlibrary\.org)/'),lambda r:r.fulfill(status=200,content_type='image/png',body=png))
  # Fixture-only: inline script injection requires removing CSP. Production CSP is tested separately, not bypassed on a real origin.
  page.set_content(re.sub(r'<script.*?</script>|<link[^>]+>|<meta http-equiv="Content-Security-Policy"[^>]+>','',(WEB/'index.html').read_text()));page.add_style_tag(content=(WEB/'app.css').read_text());page.evaluate(SETUP)
  for f in re.findall(r'<script src="([^"]+)"', (WEB/'index.html').read_text()):page.add_script_tag(content=(WEB/f).read_text())
  page.evaluate(RESET);check('Application boots with all modules',not errors)
  legacy=page.evaluate('()=>{const x=clone(seed);x.media=[{id:"legacy",kind:"movie",title:"Legacy storage",added:true,listIds:[]}];localStorage.setItem("franchiseguide-web-v7",JSON.stringify(x));localStorage.removeItem("franchiseguide-web-v8");return load().media[0]?.id;}')
  check('V7 browser storage remains readable after V8 key migration',legacy=='legacy');page.evaluate(RESET)
  page.evaluate('()=>{state.media=[{id:"m",kind:"movie",title:"Integrity",added:true,listIds:["missing"]}];state.episodes=[{id:"orphan",showId:"gone",season:1,num:1,watched:false,plays:0}];savedState=clone(state);overlay={type:"integrity",title:"Intégrité"};render();}')
  check('Integrity view exposes safe repairs',page.locator('#integrityRepair').count()==1)
  page.locator('#integrityRepair').click();check('Integrity repair removes dangling list refs',page.evaluate('state.media[0].listIds.length')==0);check('Integrity repair removes untracked orphan episodes',page.evaluate('state.episodes.length')==0);page.evaluate(RESET)
  for screen,kind in [('shows','show'),('movies','movie'),('games','game'),('books','book')]:
   page.evaluate('(s)=>{screen=s;tab=0;overlay=null;render();}',screen)
   page.locator('[data-add-kind]').first.click()
   check('Contextual + opens '+kind,page.evaluate('kindFilter')==kind and page.locator('#searchDialog').is_visible());page.locator('#searchDialog .close').click()
  page.evaluate(RESET);page.evaluate("() => {state.settings.tmdbKey='qa-tmdb';state.settings.rawgKey='qa-rawg';}")
  page.locator('[data-add-kind]').first.click();page.locator('#q').fill('validation');page.wait_for_function('remoteResults.length>0');check('Typed TV search calls /search/tv',page.evaluate("qa.calls.some(c=>c.path.endsWith('/search/tv'))"))
  page.locator('[data-act="add"]').first.click();page.wait_for_function('state.media.length===1');check('Search closes after complete add',not page.locator('#searchDialog').is_visible())
  check('All seasons including hidden specials downloaded',page.evaluate('state.episodes.length')==5)
  check('All season metadata stored',page.evaluate('state.media[0].seasons.length')==3)
  check('Poster URL used by real image element',page.locator('.detail-poster img').get_attribute('src').endswith('qa-poster.png'))
  check('Cast portrait is rendered',page.locator('img[src*="qa-person"]').count()>0)
  check('Continuing status and genres displayed','Science-fiction' in page.locator('main').inner_text() and 'Continuing' in page.locator('main').inner_text())
  check('Regular season counters exclude specials by default',page.evaluate('seasonStatsAll(state.media[0]).total')==4)
  check('Per-season provider location present','Fournisseur test' in page.locator('main').inner_text())
  page.locator('[data-ep="watch"]').first.click();check('Episode watched counter increments',page.evaluate('state.episodes.filter(e=>e.watched).length')==1)
  page.locator('#rating').fill('8');page.locator('#saveNote').click();check('Personal rating persists',page.evaluate('state.media[0].rating')==8)
  page.locator('#rating').fill('');page.locator('#saveNote').click();check('Empty personal rating clears to null',page.evaluate('state.media[0].rating') is None)
  check('Catalogue rating remains separate',page.evaluate('state.media[0].ratings[0].score')==8.5)
  page.evaluate("() => {state.settings.hideEpisodeSpoilers=true;render();}");check('Unwatched episode spoilers hidden',page.evaluate("[...document.querySelectorAll('[data-ep=watch]')].some(b=>b.closest('.episode')?.innerText.includes('Épisode non vu'))"))
  check('Episode zero catalogue rating retained',page.evaluate('state.episodes[0].catalogRating')==0)
  # refresh retains watched flags
  page.evaluate('async()=>{await refreshCatalogItem(state.media[0]);}');check('Refresh preserves watched state',page.evaluate('state.episodes.filter(e=>e.watched).length')==1)
  # Complete transaction failures
  page.evaluate("()=>{FGRequests.clear();qa.badSeason=true;remoteResults=[{id:'tmdb-show-222',tmdbId:222,kind:'show',title:'Incomplet',source:'tmdb'}];}");page.evaluate("async()=>await add('tmdb-show-222')")
  check('Incomplete season leaves library unchanged',page.evaluate('state.media.length===1 && state.episodes.length===5'))
  page.evaluate("()=>{qa.badSeason=false;FGRequests.clear();qa.badShow=true;}");page.evaluate("async()=>await add('tmdb-show-222')");check('Missing series-wide episodes rejected',page.evaluate('state.media.length')==1);page.evaluate('qa.badShow=false')
  # all main/More routes exercised; empty page/state smoke alone cannot pass these
  for route in ['stats','history','diagnostics','about','trakt-library','discover','integrity']:
   page.evaluate('(r)=>{overlay={type:r,title:r};render();}',route);page.wait_for_timeout(50);check('Route renders '+route,len(page.locator('main').inner_text())>20)
  page.evaluate("()=>{overlay=null;screen='more';render();}");check('Plus page functions wired',page.locator('#export').count()==1)
  # Secrets excluded and cross-platform round trip
  portable=page.evaluate('toPortableBackup(state)');check('Backup excludes all service credentials',not any(k in json.dumps(portable) for k in ['qa-tmdb','qa-rawg','qa-access','qa-secret']))
  check('Portable round trip retains season metadata',page.evaluate('fromPortableBackup(toPortableBackup(state)).media[0].seasons.length')==3)
  page.evaluate('createBackup("QA")');check('Manual backup recorded',page.evaluate('backups().length')>=1)
  count=page.evaluate('state.media.length');page.evaluate('()=>{const old=clone(state);state.media=[];state.episodes=[];savedState=clone(state);restoreBackup(0);}');check('Backup restores media',page.evaluate('state.media.length')==count)
  # Actual importer, not assertions against fixture input only
  page.evaluate(RESET);fixtures=ROOT/'tests/seriesguide-fixtures'
  texts=[(fixtures/n).read_text() for n in ['seriesguide-lists-backup.json','seriesguide-shows-backup.json','seriesguide-movies-backup.json']]
  page.evaluate('(texts)=>importSg(texts)',texts);check('SeriesGuide importer adds show and movies',page.evaluate('state.media.some(m=>m.kind==="show") && state.media.some(m=>m.kind==="movie")'))
  check('SeriesGuide importer links mixed lists after other files',page.evaluate('state.media.some(m=>m.listIds.length>0)'))
  original=page.evaluate('JSON.stringify(state)');page.evaluate('(texts)=>importSg(texts)',texts);check('SeriesGuide repeated import does not duplicate',page.evaluate('state.media.length')==len(json.loads(original)['media']))
  check('Imported watched flags retained',page.evaluate('state.episodes[0].watched'))
  failed=page.evaluate("()=>{const n=state.media.length;try{importSg(['[{\"tmdb_id\":666,\"in_watchlist\":true}]','INVALID']);return false;}catch{return state.media.length===n;}}")
  check('Multi-file invalid import rolls back completely',failed)
  check('Future backup schema rejected',page.evaluate("()=>{try{fromPortableBackup({schema:12,media:[]});return false;}catch{return true;}}"))
  # Trakt calls are mock responses only: no real user account involved.
  page.evaluate(RESET);page.evaluate("()=>{state.media=[{id:'m',kind:'movie',title:'Film de validation',tmdbId:333,added:true,watched:false,plays:0}];savedState=clone(state);}");page.evaluate(AUTH)
  page.evaluate("()=>{qa.pagedPath='/sync/watched/movies';qa.remote[qa.pagedPath]=[{movie:{ids:{tmdb:900},title:'Page une'},plays:1},{movie:{ids:{tmdb:901},title:'Page deux'},plays:1}];}")
  page.evaluate('async()=>await traktSync()');check('Trakt follows pagination headers',page.evaluate("qa.calls.some(c=>c.path==='/sync/watched/movies'&&c.search.includes('page=2'))"))
  check('First read never blindly reuploads library history',page.evaluate("qa.calls.filter(c=>c.method==='POST'&&c.path==='/sync/history').length") ==0)
  page.evaluate("()=>{qa.pagedPath=null;overlay={type:'item',id:'m',src:'media',title:'Film'};render();}");page.locator('[data-detail="done"]').click();check('Local watched action enters persistent journal',page.evaluate('syncState.queue.some(o=>o.field==="history")'))
  page.evaluate('async()=>await traktSync()');check('History change sent once',page.evaluate("qa.calls.filter(c=>c.path==='/sync/history'&&c.method==='POST').length")==1)
  check('Successful change acknowledged individually',page.evaluate('syncState.queue.length')==0)
  page.evaluate('async()=>await traktSync()');check('Repeat sync does not duplicate history',page.evaluate("qa.calls.filter(c=>c.path==='/sync/history'&&c.method==='POST').length")==1)
  # Remote absence is not a deletion of old local imports, even on a second sync.
  page.evaluate("()=>{state.media[0].collected=true;savedState=clone(state);}")
  page.evaluate('async()=>await traktSync()');page.evaluate('async()=>await traktSync()')
  check('Repeated remote absence preserves pre-existing local collection',page.evaluate('state.media[0].collected'))
  # A response lost after non-idempotent POST must not be blindly repeated.
  page.evaluate("()=>{qa.lostHistory=true;state.media[0].watched=true;state.media[0].plays++;state.media[0].lastWatchedAt=Date.now();save();}")
  page.evaluate('async()=>await traktSync()')
  check('Lost history acknowledgement stays uncertain',page.evaluate('syncState.queue.some(o=>o.uncertain)'))
  calls=page.evaluate("qa.calls.filter(c=>c.method==='POST'&&c.path==='/sync/history').length")
  page.evaluate('async()=>await traktSync()')
  check('Uncertain history is not retransmitted',page.evaluate("qa.calls.filter(c=>c.method==='POST'&&c.path==='/sync/history').length")==calls)
  page.evaluate("()=>{qa.lostHistory=false;syncState.queue=[];syncStore();}")
  last=page.evaluate('traktAuth().lastSync');page.evaluate("qa.failedPath='/sync/watchlist/shows'");page.evaluate('async()=>await traktSync()');check('Partial API failure is not marked success',page.evaluate('traktAuth().lastSync')==last and page.evaluate('syncState.errors.length')>0)
  page.evaluate('qa.failedPath=null');page.evaluate("()=>{saveTraktAuth({...traktAuth(),expiresAt:0});qa.refreshStatus=503;qa.calls=[];}")
  result=page.evaluate('async()=>{return (await Promise.allSettled([validTraktToken(),validTraktToken(),validTraktToken()])).map(x=>x.status)}')
  check('Concurrent token renewals share a single request',page.evaluate("qa.calls.filter(c=>c.path==='/oauth/token').length")==1)
  check('Temporary refresh failure preserves credentials',page.evaluate('traktConnected()') and result==['rejected']*3)
  page.evaluate(RESET);page.evaluate(AUTH);page.evaluate("()=>{state.media=[{id:'g',kind:'game',tmdbId:null,title:'Game',added:true,watched:false}];savedState=clone(state);state.media[0].watched=true;save();}");check('Games and books never sent to Trakt',page.evaluate('syncState.queue.length')==0)
  page.evaluate(RESET);page.evaluate("()=>{state.settings.traktId='qa-client';state.settings.traktSecret='qa-secret';qa.devicePending=true;$('#settingsDialog').showModal();startTraktLogin().catch(()=>{});}")
  page.wait_for_selector('#traktDevice:not(.hidden)');check('Device login displays official authorization code','QA-CODE' in page.locator('#traktDevice').inner_text());page.locator('#settingsDialog .close').click();page.wait_for_timeout(1200);check('Closing settings cancels pending login',not page.evaluate('traktConnected()'))
  # Responsive checks on populated fixture library, no external production account.
  page.evaluate(RESET);page.evaluate("()=>{state.media=[{id:'m',kind:'movie',title:'Bibliothèque de validation',tmdbId:333,added:true,posterUrl:'https://image.tmdb.org/t/p/w500/qa-poster.png',year:2025,genres:['Test'],credits:[]}];screen='movies';render();}")
  for width,height in [(390,844),(768,1024),(1360,1000)]:
   page.set_viewport_size({'width':width,'height':height});page.wait_for_timeout(50);check('No horizontal overflow '+str(width),page.evaluate('document.documentElement.scrollWidth <= window.innerWidth+1'))
   page.screenshot(path=str(OUT/f'fixture-library-{width}.png'),full_page=True)
  check('No uncaught script errors across all tested paths',not errors)
  b.close()
 try:return 0
 finally:(OUT/'browser-results.json').write_text(json.dumps({'mode':'isolated DOM with in-memory storage and mocked API/images; no live OAuth, persistent storage or service-worker validation','passed':len(checks),'checks':checks},ensure_ascii=False,indent=2))
if __name__=='__main__':
 try:sys.exit(run())
 except Exception as e:
  traceback.print_exc();(OUT/'browser-results.json').write_text(json.dumps({'passed':len(checks),'failed':str(e),'checks':checks},ensure_ascii=False,indent=2));sys.exit(1)
