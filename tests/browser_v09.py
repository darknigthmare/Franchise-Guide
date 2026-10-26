"""V0.9 regression scenarios, actual DOM/scripts with controlled network/storage.
No top-level navigation, persistent-origin storage, real OAuth or offline installation claim.
"""
from pathlib import Path
from playwright.sync_api import sync_playwright
from browser_harness import SETUP, RESET, AUTH, ROOT, WEB
import re,json,traceback,sys,base64
OUT=ROOT/'tests/results'; checks=[]
def check(name,condition=True):
 if not condition: raise AssertionError(name)
 checks.append({'name':name,'passed':True});print('PASS',name,flush=True)
EXTRA=r'''() => {
 const base=window.fetch;
 window.fetch=async(url,options={})=>{
  const u=new URL(String(url),'http://test.local');
  if(qa.failPage2&&u.pathname.endsWith('/search/tv')&&u.searchParams.get('page')==='2'){
   qa.calls.push({path:u.pathname,search:u.search,method:'GET'});return new Response('{}',{status:400});
  }
  if(qa.pauseCatalog&&u.hostname==='api.themoviedb.org'&&u.pathname==='/3/tv/111'){
   qa.catalogWaiting=true;await new Promise(r=>qa.releaseCatalog=r);
  }
  if(qa.historyStatus&&u.hostname==='api.trakt.tv'&&u.pathname==='/sync/history'&&options.method==='POST'){
   qa.calls.push({path:u.pathname,method:'POST',body:JSON.parse(options.body)});return new Response('{}',{status:qa.historyStatus});
  }
  return base(url,options);
 };
 const map=new Map();window.qaCaches=map;
 Object.defineProperty(window,'caches',{configurable:true,value:{keys:async()=>[...map.keys()],delete:async k=>map.delete(k),open:async k=>({keys:async()=>Array.from({length:map.get(k)||0},(_,i)=>new Request('https://image.tmdb.org/t/p/w500/'+i+'.jpg'))})}});
 Object.defineProperty(navigator,'storage',{configurable:true,value:{estimate:async()=>({usage:1024,quota:1048576}),persisted:async()=>false,persist:async()=>false}});
}'''
def run():
 with sync_playwright() as p:
  browser=p.chromium.launch(executable_path='/usr/bin/chromium',headless=True,args=['--no-sandbox'])
  ctx=browser.new_context(viewport={'width':1360,'height':1000},locale='fr-FR',timezone_id='Europe/Paris');page=ctx.new_page();page.set_default_timeout(5000);errors=[]
  page.on('pageerror',lambda e:errors.append(str(e)));page.on('dialog',lambda d:d.accept())
  png=base64.b64decode('iVBORw0KGgoAAAANSUhEUgAAAAIAAAACCAYAAABytg0kAAAAFElEQVQImWNkYPj/n4GBgYGJAQoAHhgCAQGXeHsAAAAASUVORK5CYII=')
  ctx.route(re.compile(r'https://(image\.tmdb\.org|covers\.openlibrary\.org)/'),lambda r:r.fulfill(status=200,content_type='image/png',body=png))
  page.set_content(re.sub(r'<script.*?</script>|<link[^>]+>|<meta http-equiv="Content-Security-Policy"[^>]+>','',(WEB/'index.html').read_text()));page.add_style_tag(content=(WEB/'app.css').read_text());page.evaluate(SETUP)
  for f in re.findall(r'<script src="([^"]+)"',(WEB/'index.html').read_text()):page.add_script_tag(content=(WEB/f).read_text())
  page.wait_for_timeout(20);page.evaluate(EXTRA)
  def reset():
   page.evaluate(RESET);page.evaluate("()=>{recoveryOnly=null;loadIssue=null;qa.failPage2=false;qa.pauseCatalog=false;qa.catalogWaiting=false;qa.releaseCatalog=null;qa.historyStatus=0;qaCaches.clear();libraryEpoch++;}")
  def prepare_queue():
   reset();page.evaluate(AUTH);page.evaluate("()=>{state.media=[{id:'m',kind:'movie',title:'Film test',tmdbId:333,added:true,watched:false,plays:0}];save(false);Object.assign(state.media[0],FGCore.episodeState(state.media[0],'watched',1700000000000));save();}")
  reset();page.evaluate("async()=>{state.settings.tmdbKey='qa';openScopedSearch('show');$('#q').value='pagination';await searchResults(false);}")
  check('Search page one shows next-page action',page.locator('#searchMore').is_visible() and page.evaluate('searchPage')==1)
  page.evaluate('qa.failPage2=true');page.locator('#searchMore').click();page.wait_for_selector('#searchRetry')
  check('Failed page two does not advance committed page',page.evaluate('searchPage')==1)
  check('Failed page preserves first-page results',page.evaluate('remoteResults.length')==1)
  page.evaluate('qa.failPage2=false');page.locator('#searchRetry').click();page.wait_for_function('searchPage===2')
  check('Retry requests same page and merges results once',page.evaluate('remoteResults.length')==2 and not page.evaluate('qa.calls.some(x=>x.search?.includes("page=3"))'))
  check('Last search page hides load-more action',page.locator('#searchMore').count()==0)
  check('Search no longer shares error pagination flags',page.evaluate("async()=>{const r=await remoteSearch('x','movie',1,true);return r.complete&&r.more===false&&r.errors.length===0&&searchPage===2;}"))
  page.locator('#searchDialog .close').click()
  # Provider failure in multi-category mode leaves usable results while retrying same page.
  reset();page.evaluate("async()=>{openScopedSearch('all');$('#q').value='livre';await searchResults(false);}")
  check('Missing keys do not hide successful book results',page.evaluate('remoteResults.some(x=>x.kind==="book")'))
  check('Partial search exposes retry without advancing',page.locator('#searchRetry').count()==1 and page.evaluate('searchPage')==0)
  # Restore must quarantine queued operations rather than silently replay old-state changes.
  prepare_queue();page.evaluate("()=>{createBackup('Ancien état');state.media[0].title='Titre modifié';save(false);restoreBackup(0);}")
  check('Restore suspends all existing Trakt operations',page.evaluate('syncState.queue.length===1&&syncState.queue.every(x=>x.held)'))
  check('Suspension persisted atomically in library envelope',page.evaluate('JSON.parse(localStorage.getItem(KEY))._syncState.queue[0].held!=null'))
  before=page.evaluate('qa.calls.filter(x=>x.method==="POST"&&x.path.startsWith("/sync/")).length')
  page.evaluate('async()=>{await traktSync();}')
  check('Suspended history operation is never uploaded',page.evaluate('qa.calls.filter(x=>x.method==="POST"&&x.path.startsWith("/sync/")).length')==before)
  page.evaluate("()=>{overlay={type:'trakt-library'};render();}")
  check('Suspended operations are visible and actionable',page.locator('[data-resume-op]').count()==1 and page.locator('[data-discard-op]').count()==1)
  old=page.evaluate('JSON.stringify(state.media)');page.locator('[data-discard-op]').click()
  check('Discard changes journal only, not library',page.evaluate('syncState.queue.length')==0 and page.evaluate('JSON.stringify(state.media)')==old)
  check('Discard never sends remote deletion',page.evaluate('qa.calls.filter(x=>x.method==="POST"&&x.path.startsWith("/sync/")).length')==before)
  prepare_queue();page.evaluate("()=>{prepareLibraryReplacement('Test');save(false);overlay={type:'trakt-library'};render();}");page.locator('[data-resume-op]').click()
  check('Explicit resume clears hold but does not auto-send',page.evaluate('!syncState.queue[0].held') and page.evaluate('qa.calls.filter(x=>x.method==="POST"&&x.path.startsWith("/sync/")).length')==0)
  check('Replacement is refused during active synchronization',page.evaluate("()=>{traktSyncing=true;const before=JSON.stringify(state);try{prepareLibraryReplacement('x');return false}catch{return before===JSON.stringify(state)}finally{traktSyncing=false}}"))
  # Asynchronous catalogue calls cannot resurrect data after replacement.
  reset();page.evaluate("()=>{state.settings.tmdbKey='qa';qa.pauseCatalog=true;remoteResults=[{id:'tmdb-show-111',tmdbId:111,kind:'show',title:'Retard',source:'tmdb'}];window.delayed=add('tmdb-show-111');}")
  page.wait_for_function('qa.catalogWaiting');page.evaluate("()=>{prepareLibraryReplacement('Nouvelle bibliothèque');state.media=[];state.episodes=[];save(false);qa.releaseCatalog();}");page.evaluate('async()=>{await window.delayed;}')
  check('Late catalogue addition cannot enter replaced library',page.evaluate('state.media.length===0&&state.episodes.length===0'))
  reset();page.evaluate("async()=>{state.settings.tmdbKey='qa';remoteResults=[{id:'tmdb-show-111',tmdbId:111,kind:'show',title:'Test',source:'tmdb'}];await add('tmdb-show-111');}")
  page.evaluate("()=>{FGRequests.clear();qa.pauseCatalog=true;window.delayed=refreshCatalogItem(state.media[0]);}");page.wait_for_function('qa.catalogWaiting')
  page.evaluate("()=>{state.media[0].added=false;qa.releaseCatalog();}");page.evaluate('async()=>{await window.delayed;}')
  check('Late refresh cannot re-add a removed media',page.evaluate('state.media[0].added===false'))
  # Trakt empty snapshots and ambiguous history server errors.
  reset();page.evaluate(AUTH);page.evaluate("()=>{syncState.remote=[{id:'old',kind:'movie',title:'Old',tmdbId:999}];}");page.evaluate('async()=>{await traktSync();}')
  check('Complete empty remote library clears cached titles',page.evaluate('syncState.remote.length')==0)
  reset();page.evaluate(AUTH);page.evaluate("()=>{syncState.remote=[{id:'old',kind:'movie',title:'Old',tmdbId:999}];qa.failedPath='/sync/watchlist/movies';}");page.evaluate('async()=>{await traktSync();}')
  check('Partial remote snapshot does not erase unknown titles',page.evaluate('syncState.remote.some(x=>x.tmdbId===999)'))
  prepare_queue();page.evaluate('qa.historyStatus=500');page.evaluate('async()=>{await traktSync();}')
  check('HTTP 500 after a history POST remains uncertain',page.evaluate('syncState.queue.length===1&&syncState.queue[0].uncertain'))
  attempts=page.evaluate('qa.calls.filter(x=>x.method==="POST"&&x.path==="/sync/history").length');page.evaluate('qa.historyStatus=0');page.evaluate('async()=>{await traktSync();}')
  check('Uncertain server error is not blindly retried',page.evaluate('qa.calls.filter(x=>x.method==="POST"&&x.path==="/sync/history").length')==attempts)
  prepare_queue();page.evaluate('qa.historyStatus=400');page.evaluate('async()=>{await traktSync();}')
  check('Explicit rejection retains queue without uncertain flag',page.evaluate('syncState.queue.length===1&&!syncState.queue[0].uncertain'))
  # Maintenance capabilities may be unavailable; safe scopes are still preserved.
  reset();page.evaluate("()=>{state.settings.tmdbKey='PRIVATE_TEST_VALUE';qaCaches.set('franchiseguide-images-v1',3);qaCaches.set('franchiseguide-web-0.9.0',12);qaCaches.set('unrelated-images',4);overlay={type:'maintenance'};render();}");page.wait_for_selector('#storageFacts h3')
  check('Maintenance reports only image-cache item count',page.evaluate('async()=> (await storageHealth()).images')==3)
  check('Maintenance never displays configured secret', 'PRIVATE_TEST_VALUE' not in page.locator('main').inner_text())
  page.locator('#persistStorage').click();check('Persistent-storage denial is not shown as success','non accordée' in page.locator('#maintStatus').inner_text())
  old=page.evaluate('JSON.stringify(state.media)');page.locator('#clearPosters').click();page.wait_for_selector('#storageFacts h3')
  check('Clear posters keeps application shell and unrelated caches',page.evaluate("!qaCaches.has('franchiseguide-images-v1')&&qaCaches.has('franchiseguide-web-0.9.0')&&qaCaches.has('unrelated-images')"))
  check('Clear posters does not change library or secrets',old==page.evaluate('JSON.stringify(state.media)') and page.evaluate('state.settings.tmdbKey')=='PRIVATE_TEST_VALUE')
  check('Cache clear with nothing to delete is idempotent',page.evaluate('async()=>await clearPosterCache()')==0)
  page.evaluate("()=>{Object.defineProperty(window,'caches',{configurable:true,value:{keys:async()=>{throw Error('denied')}}});overlay={type:'maintenance'};render();}");page.wait_for_selector('#storageFacts .notice')
  check('Unavailable cache capability produces visible warning','refuse' in page.locator('#storageFacts .notice').inner_text())
  page.evaluate("()=>{$('#toast').classList.remove('show');$('#toast').textContent='';}")
  for width,height in [(390,844),(768,1024),(1360,1000)]:
   page.set_viewport_size({'width':width,'height':height});page.wait_for_timeout(30)
   check('Maintenance layout has no horizontal overflow '+str(width),page.evaluate('document.documentElement.scrollWidth<=innerWidth+1'))
   page.screenshot(path=str(OUT/f'fixture-maintenance-v09-{width}.png'),full_page=True)
  # Future/corrupt library recovery never migrates or overwrites original bytes.
  reset()
  result=page.evaluate("""async()=>{const raw=JSON.stringify({schema:99,media:[{id:'f',kind:'movie',title:'Future',unknownNewField:123}]});localStorage.setItem(KEY,raw);const next=decodeStored(raw,KEY);await protectWriter();let blocked=false;try{persistEnvelope()}catch{blocked=true}return {blocked,same:localStorage.getItem(KEY)===raw,empty:next.media.length===0,readonly:!storageWritable}}""")
  check('Future schema remains unchanged and writer is blocked',result['blocked'] and result['same'] and result['readonly'])
  check('Recovery exposes exact-original export action',page.locator('#exportOriginal').is_visible())
  check('Recovery suppresses automatic snapshots',page.evaluate("()=>{const before=localStorage.getItem(BACKUP_KEY);maybeBackup();return !createBackup('x')&&before===localStorage.getItem(BACKUP_KEY)}"))
  reset();check('Malformed JSON is retained without silent empty overwrite',page.evaluate("()=>{const raw='{broken';localStorage.setItem(KEY,raw);decodeStored(raw,KEY);return recoveryOnly.raw===raw&&localStorage.getItem(KEY)===raw}"))
  reset();page.evaluate('storageWritable=false');check('Read-only window cannot rewrite backup history',page.evaluate("()=>{const before=localStorage.getItem(BACKUP_KEY);return !createBackup('x')&&localStorage.getItem(BACKUP_KEY)===before}"))
  check('V0.9 scenarios have no uncaught runtime errors',not errors)
  browser.close()
 return 0
if __name__=='__main__':
 failed=None
 try:code=run()
 except Exception as e:traceback.print_exc();failed=str(e);code=1
 OUT.mkdir(exist_ok=True);(OUT/'browser-v09-results.json').write_text(json.dumps({'mode':'actual app scripts/DOM; controlled HTTP, image, storage and cache capability fixtures; NOT live OAuth or real offline installation','passed':len(checks),'failed':failed,'checks':checks},ensure_ascii=False,indent=2));sys.exit(code)
