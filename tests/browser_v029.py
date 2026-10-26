"""V0.29 production DOM/scripts with controlled catalogues, storage and authentication races.
Not a real account, Android SDK build, PWA installation or persistence-origin validation.
"""
from playwright.sync_api import sync_playwright
from browser_harness import SETUP,RESET,AUTH,ROOT,WEB
from pathlib import Path
import re,json,traceback,base64
OUT=ROOT/'tests/results/v029';OUT.mkdir(parents=True,exist_ok=True);checks=[]
def check(name,v=True):
 if not v:raise AssertionError(name)
 checks.append({'name':name,'passed':True});print('PASS',name,flush=True)
EXTRA=r'''()=>{
 const base=fetch;qa.searchCalls=[];qa.searchFail='';qa.searchInvalid='';qa.holdSearch=false;qa.searchWait=false;qa.rawgPages=1;
 window.fetch=async(url,options={})=>{
  const u=new URL(String(url),'http://fixture.test');let provider=u.hostname==='api.themoviedb.org'&&u.pathname.includes('/search/')?'tmdb':u.hostname==='api.rawg.io'&&u.pathname==='/api/games'?'rawg':u.hostname==='openlibrary.org'&&u.pathname==='/search.json'?'openlibrary':null;
  if(provider){const p=Number(u.searchParams.get('page')||1);qa.searchCalls.push({provider,page:p,signal:!!options.signal});
   if(qa.holdSearch){qa.searchWait=true;await new Promise((resolve,reject)=>{qa.releaseSearch=resolve;options.signal?.addEventListener('abort',()=>{qa.searchAborted=true;reject(Error('Controlled cancellation'));},{once:true});});}
   const failure=qa.searchFail===provider+':'+p||qa.searchFail===provider;
   if(failure)return new Response('{}',{status:qa.searchStatus||503,headers:{'Retry-After':'60'}});
   if(qa.searchInvalid===provider)return new Response('{}');
   let data=provider==='tmdb'?{page:p,total_pages:3,results:[{id:100+p,media_type:'movie',title:'Film de recherche '+p,release_date:'2020-01-01'}]}:provider==='rawg'?{results:[{id:400+p,name:'Jeu de recherche '+p}],next:p<qa.rawgPages?'https://api.rawg.io/api/games?page='+(p+1):null}:{numFound:45,start:(p-1)*20,docs:[{key:'/works/OL'+p+'W',title:'Livre de recherche '+p}]};
   if(provider==='rawg'&&p>qa.rawgPages)return new Response('{}',{status:404});
   if(qa.searchCount===0)data=provider==='tmdb'?{results:[],page:p,total_pages:0}:provider==='rawg'?{results:[],next:null}:{docs:[],numFound:0,start:(p-1)*20};
   return new Response(JSON.stringify(data),{headers:{'Content-Type':'application/json'}});
  }
  return base(url,options);
 };
}'''
def run():
 with sync_playwright() as p:
  b=p.chromium.launch(executable_path='/usr/bin/chromium',headless=True,args=['--no-sandbox']);context=b.new_context(viewport={'width':1360,'height':1000},locale='fr-FR',timezone_id='Europe/Paris');page=context.new_page();page.set_default_timeout(7000);errors=[]
  page.on('pageerror',lambda e:errors.append(str(e)));page.on('dialog',lambda d:d.accept())
  png=base64.b64decode('iVBORw0KGgoAAAANSUhEUgAAAAIAAAACCAYAAABytg0kAAAAFElEQVQImWNkYPj/n4GBgYGJAQoAHhgCAQGXeHsAAAAASUVORK5CYII=')
  context.route(re.compile(r'https://(image\.tmdb\.org|covers\.openlibrary\.org)/'),lambda r:r.fulfill(status=200,content_type='image/png',body=png))
  html=(WEB/'index.html').read_text();page.set_content(re.sub(r'<script.*?</script>|<link[^>]+>|<meta http-equiv="Content-Security-Policy"[^>]+>','',html));page.add_style_tag(content=(WEB/'app.css').read_text());page.evaluate(SETUP)
  for f in re.findall(r'<script src="([^"]+)"',html):page.add_script_tag(content=(WEB/f).read_text())
  page.wait_for_timeout(20);page.evaluate(EXTRA)
  def reset():
   page.evaluate(RESET);page.evaluate('''()=>{invalidateSearch();searchSession=null;automaticServices={loaded:true,gateway:false,tmdb:false,rawg:false,trakt:false};state.settings.tmdbKey='qa';state.settings.rawgKey='qa';qa.searchCalls=[];qa.searchFail='';qa.searchInvalid='';qa.searchCount=null;qa.holdSearch=false;qa.searchWait=false;qa.searchStatus=503;qa.rawgPages=1;libraryEpoch++;}''')
  def search(kind='all'):
   page.evaluate("async k=>{openScopedSearch(k);$('#q').value='Recherche';await searchResults();}",kind)
  reset();search()
  check('All three configured sources display successful page one',page.evaluate('remoteResults.length')==3)
  page.evaluate('qa.searchFail="tmdb:2"');page.locator('#searchMore').click();page.wait_for_selector('#searchRetry')
  check('Source with exhausted first page is never fetched at page two',not page.evaluate('qa.searchCalls.some(c=>c.provider==="rawg"&&c.page>1)'))
  check('Successful first-page RAWG source not rerequested',page.evaluate('qa.searchCalls.filter(c=>c.provider==="rawg").length')==1)
  check('Successful OpenLibrary page survives TMDb error',page.evaluate('remoteResults.some(m=>m.sourceId==="/works/OL2W")'))
  check('Other sources may continue while one source has an error',page.locator('#searchMore').is_visible())
  before=page.evaluate('qa.searchCalls.length');page.evaluate('qa.searchFail=""');page.locator('#searchRetry').click();page.wait_for_function('!searchSession.loading')
  check('Retry issues only the failed source and failed page',page.evaluate('(n)=>qa.searchCalls.slice(n)',before)==[{'provider':'tmdb','page':2,'signal':True}])
  check('Retried result merged once',page.evaluate('remoteResults.filter(m=>m.source==="tmdb"&&m.sourceId==="102").length')==1)
  page.locator('#searchMore').click();page.wait_for_function('!searchSession.loading')
  check('Mixed-source last page finishes without out-of-range RAWG',page.locator('#searchMore').count()==0 and page.evaluate('searchSession.pages.tmdb.page===3&&searchSession.pages.openlibrary.page===3'))
  check('Search makes no library or Trakt writes',page.evaluate('state.media.length===0&&state.episodes.length===0&&syncState.queue.length===0'))
  reset();page.evaluate('qa.searchInvalid="tmdb"');search('movie')
  check('Malformed provider response is not success-empty','Recherche partiellement indisponible' in page.locator('#results').inner_text())
  check('Error identifies supplier and permits same-page retry','tmdb' in page.locator('#results').inner_text() and page.locator('#searchRetry').count()==1)
  page.evaluate('qa.searchInvalid=""');page.locator('#searchRetry').click();page.wait_for_function('!searchSession.loading');check('Valid retry recovers the malformed page',page.evaluate('remoteResults.length')==1)
  reset();page.evaluate('qa.searchFail="tmdb";qa.searchStatus=429');search('movie');calls=page.evaluate('qa.searchCalls.length');page.locator('#searchRetry').click();page.wait_for_function('!searchSession.loading')
  check('Manual retry honours per-source Retry-After',page.evaluate('qa.searchCalls.length')==calls)
  reset();page.evaluate('qa.searchCount=0');search('movie');check('Successful explicit empty result stays distinct from an error','Aucun résultat' in page.locator('#results').inner_text() and page.locator('#searchRetry').count()==0)
  reset();page.evaluate("()=>{state.media=Array.from({length:185},(_,i)=>({id:'local-'+i,title:'Éléphant '+i,kind:'movie',added:true}));openScopedSearch('movie');}")
  check('Local suggestions initially bounded to sixty rows',page.locator('#results .row').count()==60)
  page.locator('#searchLocalMore').click();check('Local next block adds sixty rows',page.locator('#results .row').count()==120)
  page.evaluate("async()=>{$('#q').value='elephant 184';await searchResults();}");check('Accent-insensitive query reaches initially unrendered local title','Éléphant 184' in page.locator('#results').inner_text())
  reset();page.evaluate("()=>{state.media=[{id:'retired',kind:'movie',title:'Film retiré',tmdbId:101,source:'tmdb',sourceId:'101',added:false}];}");search('movie')
  check('Retired local title does not hide catalogue candidate',page.locator('#results [data-src="remote"]').count()>0)
  reset();page.evaluate("()=>{qa.holdSearch=true;openScopedSearch('movie');$('#q').value='retard';void searchResults();}");page.wait_for_function('qa.searchWait');page.locator('#searchCancel').click();page.wait_for_function('qa.searchAborted')
  check('Cancel actually propagates an AbortSignal',page.evaluate('qa.searchAborted') and 'Recherche annulée' in page.locator('#results').inner_text())
  check('Cancel offers explicit resume',page.locator('#searchResume').count()==1)
  page.evaluate('qa.holdSearch=false');page.locator('#searchResume').click();page.wait_for_function('!searchSession.loading');check('Resume can complete canceled first page',page.evaluate('remoteResults.length')==1)
  page.evaluate("()=>{qa.holdSearch=true;qa.searchWait=false;qa.searchAborted=false;$('#q').value='autre';void searchResults();}");page.wait_for_function('qa.searchWait');page.locator('#searchDialog .close').click();page.wait_for_function('qa.searchAborted');check('Closing search cancels its network read',not page.locator('#searchDialog').is_visible())
  reset();page.evaluate("()=>{qa.holdSearch=true;openScopedSearch('movie');$('#q').value='avant';void searchResults();}");page.wait_for_function('qa.searchWait');page.locator('#q').fill('après');page.wait_for_function('qa.searchAborted');check('Typing aborts old request before debounce delay',page.evaluate('qa.searchAborted'))
  page.evaluate('qa.holdSearch=false');page.wait_for_function('!searchSession.loading&&searchSession.query==="après"')
  check('New query replaces old session safely',page.evaluate('searchSession.query')=='après')
  page.locator('#searchDialog .close').click();page.evaluate("()=>{openScopedSearch('movie');$('#searchDialog').close();openScopedSearch('book');}");page.wait_for_timeout(30);check('Rapid close-reopen does not cancel the new open dialog',page.locator('#searchDialog').is_visible() and page.evaluate('kindFilter')=='book')
  # Two dates with counts=1 must not be truncated before raising the merged count.
  reset();page.evaluate('''()=>{state.media=[{id:'m',kind:'movie',title:'Test',tmdbId:1,added:true,watched:true,plays:1,lastWatchedAt:1700000000000,watchHistory:[1700000000000],rating:9,userNote:'Privé'}];save(false);window.review=stageSeriesGuide([JSON.stringify([{tmdb_id:1,title:'Imported',watched:true,plays:1,last_watched_ms:1710000000000}])],['movies.json'],state);}''')
  check('Import preparation preserves old state',page.evaluate('state.media[0].plays')==1)
  check('Two distinct imported movie dates preserved before count merge',page.evaluate('review.staged.media[0].watchHistory')==[1700000000000,1710000000000] and page.evaluate('review.staged.media[0].plays')==2)
  check('Local movie score and note retained',page.evaluate('review.staged.media[0].rating===9&&review.staged.media[0].userNote==="Privé"'))
  check('Reimporting same movie event is idempotent',page.evaluate("()=>stageSeriesGuide([JSON.stringify([{tmdb_id:1,title:'Imported',watched:true,plays:1,last_watched_ms:1710000000000}])],['movies.json'],review.staged).staged.media[0].plays") ==2)
  reset();page.evaluate('''()=>{state.media=[{id:'s',kind:'show',title:'S',tmdbId:1,added:true}];state.episodes=[{id:'e',showId:'s',tmdbId:10,tvdbId:20,season:1,num:1,title:'E',watched:true,plays:1,watchHistory:[1700000000000],lastWatchedAt:1700000000000,rating:9,userNote:'Note privée',runtimeMinutes:0}];window.sg=[{tmdb_id:1,title:'S',seasons:[{season:1,episodes:[{tmdb_id:10,tvdb_id:20,episode:1,title:'Imported',watched:true,plays:1,last_watched_ms:1710000000000,rating_user:3}]}]}];window.prepared=stageSeriesGuide([JSON.stringify(sg)],['shows.json'],state);}''')
  check('Episode reimport keeps both dated viewings',page.evaluate('prepared.staged.episodes[0].watchHistory')==[1700000000000,1710000000000])
  check('Episode private note score and zero runtime preserved',page.evaluate('prepared.staged.episodes[0].userNote==="Note privée"&&prepared.staged.episodes[0].rating===9&&prepared.staged.episodes[0].runtimeMinutes===0'))
  check('Conflicting same-TMDb different-TVDB import rejected',page.evaluate('''()=>{const old=JSON.stringify(state);sg[0].seasons[0].episodes[0].tvdb_id=21;try{stageSeriesGuide([JSON.stringify(sg)],['shows.json'],state);return false}catch{return JSON.stringify(state)===old;}}'''))
  # Auth races use real production response boundary, controlled response timing.
  reset();page.evaluate(AUTH);page.evaluate('''()=>{window.originalResult=fetchResult;window.authSeen=[];window.waiter=null;fetchResult=async(url,opts)=>{authSeen.push({url,body:opts.body,signal:opts.signal});if(url.includes('/sync/'))return new Promise(r=>waiter=r);return {ok:true,status:200,json:{access_token:'new',refresh_token:'r',expires_in:3600}};};window.pendingAuth=traktResponse('/sync/watched/movies').then(()=>false,()=>true);}''');page.wait_for_function('waiter!==null');page.evaluate("()=>{clearTraktAuth();waiter({ok:false,status:401,json:{}});}")
  check('Late 401 after logout is rejected',page.evaluate('()=>pendingAuth'))
  check('Late 401 never initiates a refresh or retry on a new account',page.evaluate('authSeen.length')==1)
  check('Logout aborts outstanding authenticated transport',page.evaluate('authSeen[0].signal.aborted'))
  page.evaluate('()=>{fetchResult=originalResult;}');page.evaluate(AUTH)
  page.evaluate('''()=>{window.resolvers=[];window.originalResult=fetchResult;fetchResult=async(url,o)=>new Promise(r=>resolvers.push(r));window.oldRefresh=validTraktToken(true).catch(()=>null);}''');page.wait_for_function('resolvers.length===1');page.evaluate("()=>{clearTraktAuth();saveTraktAuth({accessToken:'new-account',refreshToken:'new-r',expiresAt:1,username:'new-account'});window.newRefresh=validTraktToken(true);}");page.wait_for_function('resolvers.length===2')
  page.evaluate("()=>{resolvers[0]({ok:true,status:200,json:{access_token:'stale',refresh_token:'old-r',expires_in:3600}});}");page.evaluate('()=>oldRefresh');check('Old refresh finally cannot erase current refresh lock',page.evaluate('refreshPromise!==null'))
  page.evaluate("()=>{resolvers[1]({ok:true,status:200,json:{access_token:'current',refresh_token:'new-r',expires_in:3600}});}");page.evaluate('()=>newRefresh');check('New account token survives stale response',page.evaluate('traktAuth().accessToken')=='current');page.evaluate('()=>{fetchResult=originalResult;}')
  reset();page.evaluate(AUTH);page.evaluate('''()=>{window.originalResult=fetchResult;window.pageCalls=0;fetchResult=async()=>{pageCalls++;if(pageCalls===1){clearTraktAuth();saveTraktAuth({accessToken:'another',refreshToken:'r',expiresAt:9999999999,username:'another'});}return {ok:true,status:200,json:[{}],headers:new Headers({'X-Pagination-Page-Count':'2'})};};window.paging=traktAll('/sync/watched/movies').then(()=>false,()=>true);}''')
  check('Paginated authenticated read cannot cross an account change',page.evaluate('()=>paging') and page.evaluate('pageCalls')==1);page.evaluate('()=>{fetchResult=originalResult;}')
  # Cancelled all-page reads cannot write errors into a new account's queue.
  reset();page.evaluate(AUTH);page.evaluate('''()=>{window.originalResult=fetchResult;window.pendingReads=[];fetchResult=async()=>new Promise(r=>pendingReads.push(r));window.syncAttempt=traktSync();}''');page.wait_for_function('pendingReads.length===3')
  page.evaluate('''()=>{clearTraktAuth();syncState={queue:[],baseline:{},remote:[],errors:[]};window.savedNew=JSON.stringify(syncState);pendingReads.forEach(r=>r({ok:false,status:503,json:{},headers:new Headers()}));}''');page.evaluate('()=>syncAttempt')
  check('Disconnected sync cannot write errors or queue into replacement account',page.evaluate('JSON.stringify(syncState)===savedNew'))
  page.evaluate('()=>{fetchResult=originalResult;}')
  reset();page.evaluate('''()=>{state.media=Array.from({length:2000},(_,i)=>({id:'m'+i,kind:'movie',title:'Même titre de validation',added:true}));overlay={type:'integrity'};render();}''')
  check('Large integrity audit declares truncated candidates','Analyse de doublons partielle' in page.locator('main').inner_text())
  check('Integrity diagnostic never merges automatically',page.evaluate('state.media.length')==2000)
  check('Public identity conflicts are explicitly blocked',page.evaluate('FGIntegrity.mergeAllowed({id:"a",kind:"show",source:"tvmaze",sourceId:"1"},{id:"b",kind:"show",source:"tvmaze",sourceId:"2"}).ok') is False)
  for width in [390,768,1360]:
   reset();page.set_viewport_size({'width':width,'height':950});page.evaluate('qa.searchFail="tmdb"');search()
   page.screenshot(path=str(OUT/f'fixture-search-reliability-{width}.png'),full_page=True)
   check(f'Partial search dialogue no horizontal overflow at {width}',page.locator('#searchDialog').evaluate('(e)=>e.scrollWidth<=e.clientWidth+1'))
   page.keyboard.press('Escape');page.wait_for_function('!document.querySelector("#searchDialog").open');check(f'Escape closes search at {width}',not page.locator('#searchDialog').is_visible())
  if errors: print('UNCAUGHT_ERRORS',json.dumps(errors,ensure_ascii=False),flush=True)
  check('No uncaught V0.29 JavaScript errors',not errors)
  result={'version':'0.31.0','mode':'real DOM/scripts and CSS; controlled providers/storage; not real accounts or persistence-origin','passed':len(checks),'checks':checks,'errors':errors}
  (OUT/'browser-v029.json').write_text(json.dumps(result,ensure_ascii=False,indent=2));print(json.dumps({'passed':len(checks),'errors':errors}));b.close()
if __name__=='__main__':
 try:run()
 except Exception:
  (OUT/'browser-v029-failure.json').write_text(json.dumps({'passed':len(checks),'checks':checks,'traceback':traceback.format_exc()},ensure_ascii=False,indent=2));raise
