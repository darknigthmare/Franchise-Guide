"""V0.25 UI + real app scripts; catalogue, capabilities and image responses are fixtures.
No real TMDb/Steam/Trakt credentials or authentication. No live-origin or installed-PWA claim.
"""
from browser_harness import SETUP,RESET,ROOT,WEB
from playwright.sync_api import sync_playwright
from pathlib import Path
import re,json,base64,traceback
OUT=ROOT/'tests/results/v025';OUT.mkdir(parents=True,exist_ok=True)
checks=[]
def check(name,ok=True):
 if not ok:raise AssertionError(name)
 checks.append({'name':name,'passed':True});print('PASS',name,flush=True)
EXTRA=r'''()=>{
 const inherited=fetch;qa.autoMode='public';qa.autoCalls=[];qa.tvBroken=false;
 const reply=(v,status=200)=>new Response(JSON.stringify(v),{status,headers:{'Content-Type':'application/json'}});
 const show=()=>({id:11,name:'Série publique QA',summary:'<p>Synopsis de validation</p>',status:'Running',premiered:'2020-02-03',language:'English',runtime:null,image:{original:'https://static.tvmaze.com/qa-show.jpg'},genres:['Drama'],rating:{average:8},externals:{imdb:'tt001111',thetvdb:1111},network:{name:'Chaîne de validation'}});
 const seasons=[{id:21,number:1,name:'Saison 1',image:{original:'https://static.tvmaze.com/s.jpg'}},{id:22,number:2,name:'Saison 2'}];
 const episodes=[{id:101,season:1,number:1,name:'Pilote QA',type:'regular',airdate:'2020-02-03',runtime:42,summary:'Synopsis pilote',rating:{average:9},image:{original:'https://static.tvmaze.com/qa-still.jpg'}},{id:102,season:2,number:1,name:'Suite QA',type:'regular',airdate:'2021-03-04',runtime:0},{id:103,season:2,number:null,name:'Bonus sans numéro',type:'significant_special',airdate:null,runtime:null}];
 const movie=()=>({kind:'feature-movie',trackId:44,trackName:'Film boutique QA',releaseDate:'2021-01-01T08:00:00Z',trackViewUrl:'https://itunes.apple.com/fr/movie/id44',trackTimeMillis:6000000,longDescription:'Film boutique de validation',artworkUrl100:'https://evil.test/promotional.jpg',previewUrl:'https://evil.test/preview'});
 const game=()=>({id:99,title:'Jeu gratuit QA',release_date:'2023-05-06',genre:'Shooter',platform:'PC (Windows), Web Browser',thumbnail:'https://www.freetogame.com/qa-game.jpg',short_description:'Jeu gratuit pour validation',description:'Fiche complète test',freetogame_profile_url:'https://www.freetogame.com/qa-game'});
 window.fetch=async(url,opt={})=>{
  const u=new URL(String(url),'http://test.local'),headers=new Headers(opt.headers||{});qa.autoCalls.push({url:String(url),path:u.pathname,method:opt.method||'GET',header:headers.get('X-FranchiseGuide-Services'),body:opt.body||null});
  if(u.pathname==='/api/services/config'){
   if(qa.autoMode==='static')return reply({},404);
   return reply({app:'FranchiseGuide Auto Services',contract:1,keyless:['tvmaze','itunes','freetogame','openlibrary'],tmdb:qa.autoMode==='publisher',rawg:qa.autoMode==='publisher',trakt:qa.autoMode==='publisher',steam:false,retro:false});
  }
  if(u.pathname.startsWith('/api/services/')){
   const rest=u.pathname.slice('/api/services/'.length),at=rest.indexOf('/'),provider=rest.slice(0,at),path=rest.slice(at);if(headers.get('X-FranchiseGuide-Services')!=='1')return reply({},403);
   if(provider==='tmdb'||provider==='rawg'||provider==='trakt'||provider==='trakt-auth')return inherited('https://'+({tmdb:'api.themoviedb.org',rawg:'api.rawg.io',trakt:'api.trakt.tv','trakt-auth':'auth.trakt.tv'}[provider])+path+u.search,opt);
   u.hostname={tvmaze:'api.tvmaze.com',itunes:'itunes.apple.com',freetogame:'www.freetogame.com'}[provider]||u.hostname;u.pathname=path;
  }
  if(u.hostname==='api.tvmaze.com'){
   if(u.pathname==='/search/shows')return reply([{show:show(),score:1}]);
   if(u.pathname==='/shows')return reply([show()]);
   if(u.pathname==='/shows/11')return reply(show());
   if(u.pathname==='/shows/11/seasons')return reply(seasons);
   if(u.pathname==='/shows/11/episodes')return qa.tvBroken?reply({error:'incomplete'},503):reply(episodes);
   if(u.pathname==='/shows/11/cast')return reply([{person:{id:88,name:'Artiste TVmaze QA',image:{medium:'https://static.tvmaze.com/qa-person.jpg'}},character:{name:'Rôle de test'}}]);
  }
  if(u.hostname==='itunes.apple.com')return reply({resultCount:1,results:[movie()]});
  if(u.hostname==='www.freetogame.com')return reply(u.pathname==='/api/game'?game():[game()]);
  return inherited(String(url),opt);
 };
}'''
def run():
 with sync_playwright() as p:
  b=p.chromium.launch(executable_path='/usr/bin/chromium',headless=True,args=['--no-sandbox']);ctx=b.new_context(viewport={'width':1360,'height':1000},locale='fr-FR',timezone_id='Europe/Paris');page=ctx.new_page();errors=[];page.on('pageerror',lambda e:errors.append(str(e)));page.on('dialog',lambda d:d.accept());page.set_default_timeout(6500)
  png=base64.b64decode('iVBORw0KGgoAAAANSUhEUgAAAAIAAAACCAYAAABytg0kAAAAFElEQVQImWNkYPj/n4GBgYGJAQoAHhgCAQGXeHsAAAAASUVORK5CYII=')
  ctx.route(re.compile(r'https://(static\.tvmaze\.com|www\.freetogame\.com|image\.tmdb\.org|covers\.openlibrary\.org)/'),lambda r:r.fulfill(status=200,content_type='image/png',body=png))
  html=(WEB/'index.html').read_text();page.set_content(re.sub(r'<script.*?</script>|<link[^>]+>|<meta http-equiv="Content-Security-Policy"[^>]+>','',html));page.add_style_tag(content=(WEB/'app.css').read_text());page.evaluate(SETUP);page.evaluate(EXTRA)
  for f in re.findall(r'<script src="([^"]+)"',html):page.add_script_tag(content=(WEB/f).read_text())
  page.evaluate(RESET);page.evaluate('()=>ensureAutomaticServices(true)')
  check('Startup has no prefilled or required API keys',page.evaluate("['tmdbKey','rawgKey','traktId','traktSecret'].every(k=>!state.settings[k])"))
  page.evaluate('()=>settingsButtonHandler()');page.wait_for_selector('#automaticServiceStatus h3');check('Normal settings display keyless choices',all(x in page.locator('#automaticServiceStatus').inner_text() for x in ['TVmaze','FreeToGame','Open Library','Apple']))
  check('Developer key fields are hidden by default',not page.locator('#tmdb').is_visible() and not page.locator('#rawg').is_visible() and not page.locator('#traktSecret').is_visible())
  check('Trakt sign-in remains user facing, not client ID form',page.locator('#traktConnect').is_visible())
  page.locator('#traktConnect').click();page.wait_for_timeout(40);check('Unconfigured editor Trakt does not send fake credentials',not page.evaluate('qa.autoCalls.some(c=>c.path.includes("/oauth/device"))'))
  check('Unconfigured service has activation explanation','éditeur' in page.locator('#toast').inner_text())
  page.locator('#settingsDialog .close').click()
  page.evaluate("()=>{overlay={type:'setup'};render();}");check('Getting started no longer requires developer keys','Clé manquante' not in page.locator('main').inner_text() and 'sans saisir de clé' in page.locator('main').inner_text())
  # Exact normal action: + -> search -> add full show, zero input in advanced settings.
  page.locator('[data-add-kind="show"]').first.click();page.locator('#q').fill('publique');page.wait_for_function("remoteResults.some(m=>m.source==='tvmaze')")
  check('Keyless search selects TVmaze automatically',page.evaluate('remoteResults[0].source')=='tvmaze')
  check('TVmaze search routed through local launcher gateway',page.evaluate('qa.autoCalls.some(c=>c.path==="/api/services/tvmaze/search/shows")'))
  page.locator('[data-act="add"]').first.click();page.wait_for_function('state.media.length===1')
  check('TVmaze complete addition closes search',not page.locator('#searchDialog').is_visible())
  check('TVmaze all episodes and specials staged before commit',page.evaluate('state.episodes.length===3&&state.media[0].seasons.length===3'))
  check('Public show keeps own identities',page.evaluate('state.media[0].source==="tvmaze"&&!state.media[0].tmdbId&&state.episodes.every(e=>!e.tmdbId)'))
  check('Null episode numbers stay unknown',page.evaluate('state.episodes.find(e=>e.id==="tvmaze-episode-103").numberUnknown'))
  check('Public source note clearly attributed', 'TVmaze' in page.locator('.source-info').inner_text() and 'CC BY-SA' in page.locator('.source-info').inner_text())
  check('Public show poster is rendered',page.locator('.detail-poster img').get_attribute('src')=='https://static.tvmaze.com/qa-show.jpg')
  check('TVmaze cast portrait and correct source link rendered',page.locator('.credits a[href="https://www.tvmaze.com/people/88"] img').count()==1)
  page.evaluate('()=>{const e=state.episodes[0];Object.assign(e,{watched:true,plays:2,watchHistory:[1700000000000,1701000000000],lastWatchedAt:1701000000000,rating:8,userNote:"Ne pas perdre"});state.media[0].rating=7;save(false);}')
  before=page.evaluate('JSON.stringify(state.episodes)');page.evaluate('()=>{qa.tvBroken=true;FGRequests.clear();}');page.evaluate('()=>refreshCatalogItem(state.media[0])')
  check('Failed public episode fetch retains complete saved tracking',page.evaluate('JSON.stringify(state.episodes)')==before)
  page.evaluate('()=>{qa.tvBroken=false;FGRequests.clear();}');page.evaluate('()=>refreshCatalogItem(state.media[0])')
  check('Public refresh preserves private episode note and timestamps',page.evaluate('state.episodes[0].userNote==="Ne pas perdre"&&state.episodes[0].watchHistory.length===2&&state.episodes[0].rating===8'))
  check('Movie TV show genre and status information present',page.evaluate('state.media[0].genres.includes("Drama")&&state.media[0].status==="CONTINUING"'))
  # Movie fallback and limitations, no unauthorized artwork or preview.
  page.evaluate("()=>openScopedSearch('movie')");page.locator('#q').fill('film');page.wait_for_function('remoteResults.some(m=>m.source==="itunes")');page.locator('[data-act="add"]').first.click();page.wait_for_function('state.media.length===2')
  check('Movie search works without developer key via Apple metadata',page.evaluate('state.media[1].source==="itunes"&&state.media[1].runtimeMinutes===100'))
  check('Apple fallback does not copy promotional artwork or preview',page.evaluate('!state.media[1].posterUrl&&!state.media[1].previewUrl'))
  check('Movie storefront limit and outbound offer disclosed', 'fiche limitée' in page.locator('.source-info').inner_text() and page.locator('.source-info a').get_attribute('href').startswith('https://itunes.apple.com/'))
  page.evaluate("()=>openScopedSearch('game')");page.locator('#q').fill('gratuit');page.wait_for_function('remoteResults.some(m=>m.source==="freetogame")');page.locator('[data-act="add"]').first.click();page.wait_for_function('state.media.length===3')
  check('No-key game search selects FreeToGame with artwork',page.evaluate('state.media[2].source==="freetogame"&&state.media[2].posterUrl.includes("freetogame")'))
  check('Limited free-to-play scope disclosed', 'free-to-play' in page.locator('.source-info').inner_text() and 'payants' in page.locator('.source-info').inner_text())
  page.evaluate("()=>openScopedSearch('book')");page.locator('#q').fill('livre');page.wait_for_function('remoteResults.some(m=>m.source==="openlibrary")');page.locator('[data-act="add"]').first.click();page.wait_for_function('state.media.length===4')
  check('Books remain searchable without keys',page.evaluate('state.media[3].source==="openlibrary"'))
  check('Portable public records preserve all source IDs without secrets',page.evaluate('()=>{const b=toPortableBackup(state);return b.media.length===4&&!JSON.stringify(b).includes(PUBLISHER_MARKER)&&fromPortableBackup(b).media.every(m=>m.sourceId)}'))
  # Paid app credentials never pushed into forms; backend detection instead.
  page.evaluate('()=>{qa.autoMode="publisher";FGRequests.clear();}');page.evaluate('()=>ensureAutomaticServices(true)')
  check('Publisher config chosen automatically with blank personal fields',page.evaluate('serviceCredential("tmdb")===PUBLISHER_MARKER&&!state.settings.tmdbKey'))
  page.evaluate("()=>openScopedSearch('show')");page.locator('#q').fill('validation');page.wait_for_function('remoteResults.some(m=>m.source==="tmdb")')
  check('Managed search routes to gateway instead of sending app marker upstream',page.evaluate('qa.autoCalls.some(c=>c.path==="/api/services/tmdb/3/search/tv")&&!qa.autoCalls.some(c=>c.url.startsWith("https://api.themoviedb.org")&&c.url.includes(PUBLISHER_MARKER))'))
  page.locator('[data-act="add"]').first.click();page.wait_for_function('state.media.length===5')
  check('TMDb source still provides original rich series flow when editor configured',page.evaluate('state.media[4].source==="tmdb"&&state.episodes.some(e=>e.showId===state.media[4].id&&e.tmdbId)'))
  check('Existing public show is not re-sourced when publisher becomes available',page.evaluate('state.media[0].source')=='tvmaze')
  page.evaluate('()=>settingsButtonHandler()');page.locator('#traktConnect').click();page.wait_for_function('traktConnected()',timeout=12000)
  check('Trakt device sign-in uses publisher configuration without user IDs',page.evaluate('qa.autoCalls.some(c=>c.path==="/api/services/trakt-auth/oauth/device/code")&&!state.settings.traktId&&!state.settings.traktSecret'))
  check('Publisher sign-in retrieves the user profile',page.evaluate('traktAccountLabel()')=='Compte de test')
  check('OAuth transport never sends marker to official auth host',page.evaluate('!qa.autoCalls.some(c=>c.url.startsWith("https://auth.trakt.tv")&&String(c.body).includes(PUBLISHER_MARKER))'))
  check('Publisher app fields stay blank and hidden after login',page.locator('#trakt').input_value()=='' and not page.locator('#trakt').is_visible())
  check('User tokens excluded from portable export',page.evaluate('!JSON.stringify(toPortableBackup(state)).includes("qa-refresh")'))
  page.locator('#settingsDialog .close').click()
  # Public data never silently swapped during maintenance or language preference changes.
  page.evaluate('()=>{qa.autoMode="public";FGRequests.clear();}');page.evaluate('()=>ensureAutomaticServices(true)')
  page.evaluate('()=>{state.settings.discoverKind="show";overlay={type:"discover"};render();}');page.wait_for_selector('#publicDiscoveryInfo')
  page.locator('#discoverYearMin').fill('2030');page.locator('#discoverFilters button[type=submit]').click();page.wait_for_function('state.settings.discoverYearMin===2030')
  page.wait_for_timeout(100);check('Public discovery filter saves ordinary preferences',page.evaluate('state.settings.discoverYearMin')==2030)
  check('Discovery does not persist publisher marker in settings',page.evaluate('!JSON.stringify(state.settings).includes(PUBLISHER_MARKER)'))
  check('TVmaze index not mislabeled as trends', 'index TVmaze' in page.locator('#publicDiscoveryInfo').inner_text())
  # Static mode public CORS endpoint avoids a nonexistent proxy.
  page.evaluate('()=>{qa.autoMode="static";FGRequests.clear();}');page.evaluate('()=>ensureAutomaticServices(true)');page.evaluate("()=>remoteSearch('publique','show',1,true)")
  check('Static fallback calls public TVmaze directly',page.evaluate('qa.autoCalls.some(c=>c.url.startsWith("https://api.tvmaze.com/search/shows"))'))
  page.evaluate('()=>settingsButtonHandler()');page.wait_for_timeout(100)
  check('Static mode announces remaining CORS constraint','statique' in page.locator('#automaticServiceStatus').inner_text())
  for w in [390,768,1360]:
   page.set_viewport_size({'width':w,'height':1000});page.screenshot(path=str(OUT/f'fixture-settings-keyless-{w}.png'),full_page=True)
   check('Normal settings no horizontal overflow '+str(w),page.locator('#settingsDialog').evaluate('(e)=>e.scrollWidth<=e.clientWidth+1'))
  check('No uncaught JavaScript errors in no-key and publisher scenarios',not errors)
  (OUT/'browser-v025.json').write_text(json.dumps({'version':'0.25.0','scope':'real scripts/DOM; fixture capabilities, catalogues, auth and images; no real account','passed':len(checks),'checks':checks,'errors':errors},ensure_ascii=False,indent=2));print(json.dumps({'passed':len(checks),'errors':errors}));b.close()
if __name__=='__main__':
 try:run()
 except Exception:
  (OUT/'browser-v025-failure.json').write_text(json.dumps({'passed':len(checks),'checks':checks,'traceback':traceback.format_exc()},ensure_ascii=False,indent=2));raise
