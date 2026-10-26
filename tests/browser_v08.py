"""V0.8 regression scenarios. Real DOM/JS; mocked APIs and localStorage failure injection.
Not a live provider, persistent-origin, SW-installation or Android device test.
"""
from pathlib import Path
from playwright.sync_api import sync_playwright
from browser_harness import SETUP, RESET, AUTH, ROOT, WEB
import re,json,traceback,sys,base64
OUT=ROOT/'tests/results'; checks=[]
def check(name,condition=True):
 if not condition: raise AssertionError(name)
 checks.append({'name':name,'passed':True}); print('PASS',name,flush=True)
EXTRA=r'''() => {
 const base=window.fetch;
 window.fetch=async(url,opts={})=>{
  const u=new URL(String(url),'http://test.local');
  if(u.hostname==='api.themoviedb.org'&&u.pathname.startsWith('/3/person/')) {
   qa.calls.push({path:u.pathname,search:u.search});
   if(qa.badPerson)return new Response('{}',{status:404});
   const movies=Array.from({length:89},(_,i)=>({id:333+i,media_type:'movie',title:'Film test '+i,release_date:(2025-i%20)+'-01-01',poster_path:'/qa-poster.png',character:'Interprète'}));
   return new Response(JSON.stringify({id:88,name:'Personne de validation',biography:'Biographie fictive pour les essais.',birthday:'1980-01-01',place_of_birth:'Lieu de test',profile_path:'/qa-person.png',known_for_department:'Acting',combined_credits:{cast:[...movies,{id:111,media_type:'tv',name:'Série de validation',first_air_date:'2025-01-01'}],crew:[{...movies[0],job:'Director'}]}}),{status:200});
  }
  return base(url,opts);
 };
}'''
def run():
 with sync_playwright() as p:
  browser=p.chromium.launch(executable_path='/usr/bin/chromium',headless=True,args=['--no-sandbox'])
  ctx=browser.new_context(viewport={'width':1360,'height':1000},locale='fr-FR',timezone_id='Europe/Paris'); page=ctx.new_page();page.set_default_timeout(4000);errors=[]
  page.on('pageerror',lambda e:errors.append(str(e)));page.on('dialog',lambda d:d.accept())
  png=base64.b64decode('iVBORw0KGgoAAAANSUhEUgAAAAIAAAACCAYAAABytg0kAAAAFElEQVQImWNkYPj/n4GBgYGJAQoAHhgCAQGXeHsAAAAASUVORK5CYII=')
  ctx.route(re.compile(r'https://(image\.tmdb\.org|covers\.openlibrary\.org)/'),lambda r:r.fulfill(status=200,content_type='image/png',body=png))
  page.set_content(re.sub(r'<script.*?</script>|<link[^>]+>|<meta http-equiv="Content-Security-Policy"[^>]+>','',(WEB/'index.html').read_text()));page.add_style_tag(content=(WEB/'app.css').read_text());page.evaluate(SETUP)
  for f in re.findall(r'<script src="([^"]+)"',(WEB/'index.html').read_text()):page.add_script_tag(content=(WEB/f).read_text())
  page.wait_for_timeout(20);page.evaluate(EXTRA)
  def reset():
   page.evaluate(RESET);page.evaluate("()=>{$('#storageBanner')?.remove();storageDraft=null;personProfiles.clear();libraryViews.clear();historyView={kind:'all',days:0,limit:100};qa.badPerson=false;}")
  def add_series():
   page.evaluate("async()=>{state.settings.tmdbKey='qa';remoteResults=[{id:'tmdb-show-111',tmdbId:111,kind:'show',title:'Test',source:'tmdb'}];await add('tmdb-show-111');}")
  reset(); add_series()
  check('Only expanded season has episode DOM',page.locator('.episode').count()==2)
  page.locator('[data-person="88"]').first.click();page.wait_for_selector('#personKind')
  check('Person biography is integrated in app','Biographie fictive' in page.locator('main').inner_text())
  check('Person uses real profile image field',page.locator('.person-portrait').get_attribute('src').endswith('qa-person.png'))
  check('Filmography coalesces cast and crew by provider identity',page.evaluate('personProfiles.get("88:fr-FR").credits.length')==90)
  check('Filmography preserves multiple roles','Director' in page.locator('#filmographyRows').inner_text())
  check('Filmography initial DOM capped at 40 works',page.locator('.filmography-credit').count()==40)
  page.locator('#personMore').click();check('Filmography loads more on demand',page.locator('.filmography-credit').count()==80)
  page.locator('#personKind').select_option('show');check('Filmography kind filter works',page.locator('.filmography-credit').count()==1)
  check('Opening person does not add filmography to library',page.evaluate('state.media.length')==1)
  page.locator('#back').click();check('Back restores originating series',page.evaluate('overlay.type')=='item' and page.evaluate('overlay.id')=='tmdb-show-111')
  # Existing profile cache is reused; typing filmography filters does not refetch.
  before=page.evaluate('qa.calls.filter(x=>x.path.includes("/person/")).length')
  page.locator('[data-person="88"]').first.click();page.wait_for_selector('#personKind');page.locator('#personQuery').fill('Film test 0')
  check('Filmography title filter works',page.locator('.filmography-credit').count()==1)
  check('Cached person is reused',page.evaluate('qa.calls.filter(x=>x.path.includes("/person/")).length')==before)
  page.locator('#filmographyRows [data-act="add"]').click();page.wait_for_function('state.media.length===2')
  check('Filmography add loads full movie details',page.evaluate('state.media.find(x=>x.kind==="movie").runtime')==100 or page.evaluate('state.media.find(x=>x.kind==="movie").runtimeMinutes')==100)
  # Per-person error remains retryable.
  page.evaluate("()=>{personProfiles.clear();FGRequests.clear();qa.badPerson=true;overlay={type:'person',id:88,kind:'all'};render();}");page.wait_for_selector('#retryPerson')
  check('Person request failure exposes retry',page.locator('#retryPerson').is_visible())
  page.evaluate('qa.badPerson=false');page.locator('#retryPerson').click();page.wait_for_selector('#personKind');check('Retry restores person content')
  # Large series: rendering is bounded even when there are hundreds of records.
  reset();add_series()
  page.evaluate("()=>{const sid=state.media[0].id;state.episodes=Array.from({length:220},(_,i)=>({id:'bulk'+i,showId:sid,season:i<200?1:2,num:i<200?i+1:i-199,title:'Épisode '+i,watched:false,date:'2025-01-01',plays:0}));seriesViews.clear();render();}")
  check('Large open season initially renders 50 records',page.locator('.episode').count()==50)
  page.locator('.more-episodes').click();check('More episodes appends next 50',page.locator('.episode').count()==100)
  page.locator('.season[data-number="2"] summary').click();page.wait_for_timeout(20);check('Second expanded season is rendered',page.locator('.season[data-number="2"] .episode').count()==20)
  page.locator('.season[data-number="2"] [data-ep="watch"]').first.click();check('Watching retains expanded seasons',page.locator('.season[data-number="1"]').evaluate('(e)=>e.open') and page.locator('.season[data-number="2"]').evaluate('(e)=>e.open'))
  page.locator('#episodeFilter').select_option('watched');check('Watched episode filter leaves one row',page.locator('.episode').count()==1)
  page.locator('.season[data-number="1"] summary').click();page.wait_for_timeout(20);check('Collapsed season releases episode DOM',page.locator('.season[data-number="1"] .episode').count()==0)
  # Dated history goes through real save path and full export/import.
  page.evaluate("()=>{let e=state.episodes[0];Object.assign(e,FGCore.episodeState(e,'watched',1700000000000));Object.assign(e,FGCore.episodeState(e,'replay',1700100000000));save();overlay={type:'history'};render();}")
  check('Dated history includes every known rewatch',page.evaluate('FGCore.timeline(state.media,state.episodes).knownPlays')==3)
  check('New watch dates survive portable backup',page.evaluate('fromPortableBackup(toPortableBackup(state)).episodes[0].watchHistory.length')==2)
  page.locator('#historyKind').select_option('movie');check('History media filter excludes episodes',page.locator('.history-entry').count()==0)
  # Minimal IDs: no invented numbering, distinct items cannot collapse on (0,0).
  reset();minimal=json.dumps([{'tmdb_id':111,'title':'Minimal','seasons':[{'tmdb_id':901,'episodes':[{'tmdb_id':11111,'watched':True},{'tmdb_id':11112,'watched':True}]}]}])
  page.evaluate('(t)=>importSg([t])',minimal)
  check('Minimal import preserves distinct unnumbered episodes',page.evaluate('state.episodes.length')==2)
  check('Minimal import explicitly flags unknown numbering',page.evaluate('state.episodes.every(e=>e.seasonUnknown&&e.numberUnknown&&e.season===-1)'))
  check('Minimal imported state exports as valid schema 6',page.evaluate('FGCore.validateBackup(toPortableBackup(state))'))
  page.evaluate('(t)=>importSg([t])',minimal);check('Repeated minimal import is idempotent',page.evaluate('state.episodes.length')==2)
  page.evaluate("async()=>{state.settings.tmdbKey='qa';await refreshCatalogItem(state.media[0]);}")
  check('Hydration matches minimal watched episodes by ID',page.evaluate('state.episodes.filter(e=>e.watched).length')==2)
  check('Hydration resolves season and episode numbering',page.evaluate('state.episodes.length===5 && state.episodes.filter(e=>e.watched).every(e=>e.season===1 && !e.numberUnknown && !e.seasonUnknown)'))
  # A failing journal/library save must roll back both, preserving a recovery draft.
  reset();page.evaluate(AUTH)
  result=page.evaluate("""()=>{state.media=[{id:'m',kind:'movie',title:'Storage test',tmdbId:333,added:true,watched:false,plays:0}];save(false);const original=localStorage.setItem;const before=localStorage.getItem(KEY);localStorage.setItem=(k,v)=>{if(k===KEY)throw new DOMException('Quota pleine','QuotaExceededError');return original(k,v)};let failed=false;try{Object.assign(state.media[0],FGCore.episodeState(state.media[0],'watched',1700000000000));save()}catch{failed=true}finally{localStorage.setItem=original}return {failed,same:before===localStorage.getItem(KEY),watched:state.media[0].watched,queue:syncState.queue.length,draft:storageDraft.media[0].watched}}""")
  check('Storage error never overwrites persisted library',result['failed'] and result['same'])
  check('Storage error restores in-memory state and journal',not result['watched'] and result['queue']==0)
  check('Unsaved work remains exportable as recovery draft',result['draft'] and page.locator('#exportDraft').is_visible())
  # Successful save stores both in one atomic localStorage record and export excludes journal.
  page.evaluate("()=>{Object.assign(state.media[0],FGCore.episodeState(state.media[0],'watched',1700000000000));save();}")
  check('Library and outbox share a single committed envelope',page.evaluate("()=>{const x=JSON.parse(localStorage.getItem(KEY));return x.media[0].watched&&x._syncState.queue.length===1&&x._revision===storageRevision}"))
  check('Portable export strips account journals',page.evaluate('!JSON.stringify(toPortableBackup(state)).includes("_syncState")'))
  # Another editor's successful revision cannot be silently lost.
  result=page.evaluate("""()=>{const disk=JSON.parse(localStorage.getItem(KEY));disk._revision++;disk.media[0].title='Other editor';localStorage.setItem(KEY,JSON.stringify(disk));let failed=false;try{state.media[0].title='Stale editor';save()}catch{failed=true}return {failed,title:JSON.parse(localStorage.getItem(KEY)).media[0].title,readOnly:!storageWritable}}""")
  check('Stale editor cannot overwrite another revision',result['failed'] and result['title']=='Other editor' and result['readOnly'])
  reset();page.evaluate("()=>{state.media=[{id:'m',kind:'movie',title:'Keep',added:true}];save(false);}")
  result=page.evaluate("""()=>{const original=localStorage.setItem;localStorage.setItem=(k,v)=>{if(k===BACKUP_KEY)throw Error('Quota backup');return original(k,v)};const before=JSON.stringify(state.media);let failed=false;try{importSg(['[{"tmdb_id":333,"title":"Import","in_watchlist":true}]'])}catch{failed=true}finally{localStorage.setItem=original}return failed&&JSON.stringify(state.media)===before}""")
  check('Failed safety snapshot blocks destructive import',result)
  # Pagination keeps large library DOM bounded and local filter remains useful.
  reset();page.evaluate("()=>{state.media=Array.from({length:205},(_,i)=>({id:'m'+i,kind:'movie',title:'Titre '+String(i).padStart(3,'0'),added:true,watched:false}));screen='movies';render();}")
  check('Large library initially renders 80 title rows',page.locator('[data-open]').count()==80)
  page.locator('[data-library-more]').click();check('Large library loads next 80',page.locator('[data-open]').count()==160)
  page.locator('[data-local-filter]').fill('Titre 204');check('Local library filter finds beyond initial page',page.locator('[data-open]').count()==1 and '204' in page.locator('[data-open]').inner_text())
  # Schema future/malformed input does not bypass validation.
  check('Nested invalid backup rolls back before state mutation',page.evaluate("()=>{const before=JSON.stringify(state);try{fromPortableBackup({schema:6,media:[{id:'x',kind:'movie',title:'X',credits:{}}]})}catch{return JSON.stringify(state)===before}return false}"))
  reset();add_series();page.locator('[data-person="88"]').first.click();page.wait_for_selector('#personKind')
  for width,height in [(390,844),(768,1024),(1360,1000)]:
   page.set_viewport_size({'width':width,'height':height});page.wait_for_timeout(30)
   check('Person layout without horizontal overflow '+str(width),page.evaluate('document.documentElement.scrollWidth<=innerWidth+1'))
   page.screenshot(path=str(OUT/f'fixture-person-v08-{width}.png'),full_page=True)
  check('V0.8 scenarios have no uncaught runtime errors',not errors)
  browser.close()
 return 0
if __name__=='__main__':
 failed=None
 try:code=run()
 except Exception as e:traceback.print_exc();failed=str(e);code=1
 OUT.mkdir(exist_ok=True);(OUT/'browser-v08-results.json').write_text(json.dumps({'mode':'real app scripts and DOM; controlled API, image and storage fixtures; not live OAuth or native Android','passed':len(checks),'failed':failed,'checks':checks},ensure_ascii=False,indent=2));sys.exit(code)
