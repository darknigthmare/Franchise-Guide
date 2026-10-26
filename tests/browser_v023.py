"""V0.23: real application DOM/scripts; synthetic TMDb responses/images/storage, no real catalogue credentials."""
from pathlib import Path
from playwright.sync_api import sync_playwright
from browser_harness import ROOT,WEB,SETUP,RESET,AUTH
import re,json,base64,traceback
OUT=ROOT/'tests/results/v023';OUT.mkdir(parents=True,exist_ok=True);checks=[]
def check(name,v=True):
 if not v:raise AssertionError(name)
 checks.append({'name':name,'passed':True});print('PASS',name,flush=True)
FIXTURE=r'''()=>{
 const old=fetch;qa.avCalls=[];qa.avMode='normal';qa.regionMode='normal';qa.hold=false;qa.waiting=false;
 qa.provider=(id,name,priority=id,logo='/qa.png')=>({provider_id:id,provider_name:name,display_priority:priority,logo_path:logo});
 qa.offers=(url)=>{
  const id=Number(url.pathname.split('/')[3]),season=url.pathname.includes('/season/');
  if(qa.avMode==='invalid')return {results:{FR:{flatrate:[qa.provider(1,'Partiel'),null]}}};
  if(qa.avMode==='mismatch')return {id:999,results:{FR:{flatrate:[qa.provider(1,'Autre titre')]}}};
  if(qa.avMode==='none')return {id:season?901:id,results:{FR:{}}};
  if(qa.avMode==='missing')return {id:season?901:id,results:{}};
  if(qa.avMode==='malicious')return {results:{FR:{link:'https://evil.test/player',flatrate:[qa.provider(1,'<img src=x onerror=alert(1)>',1,'https://evil.test/p.png')]}}};
  if(qa.avMode==='many')return {id:season?901:id,results:{FR:{flatrate:Array.from({length:175},(_,i)=>qa.provider(i+1,i===174?'Écran final QA':'Service de validation '+i)),link:'https://www.themoviedb.org/'+(url.pathname.includes('/movie/')?'movie':'tv')+'/'+id+'/watch?locale=FR'}}};
  const type=url.pathname.includes('/movie/')?'movie':'tv';
  return {id:season?901:id,results:{FR:{link:'https://www.themoviedb.org/'+type+'/'+id+'/watch?locale=US&api_key=do-not-copy&redirect=https%3A%2F%2Fevil.test',flatrate:[qa.provider(1,'Service Alpha')],free:[qa.provider(2,'Service Libre')],ads:[qa.provider(3,'Service Pub')],rent:[qa.provider(4,'Service Location')],buy:[qa.provider(1,'Service Alpha')]},US:{link:'https://www.themoviedb.org/'+type+'/'+id+'/watch?locale=US',rent:[qa.provider(5,'Service USA')]},JP:{}}};
 };
 window.fetch=async(url,options={})=>{
  const u=new URL(String(url),'http://test.local');
  if(u.hostname!=='api.themoviedb.org'||!(u.pathname.endsWith('/watch/providers')||u.pathname.endsWith('/watch/providers/regions')))return old(url,options);
  qa.avCalls.push({path:u.pathname,method:options.method||'GET',params:Object.fromEntries(u.searchParams)});
  const countries=u.pathname.endsWith('/regions'),mode=countries?qa.regionMode:qa.avMode;
  const body=countries?(mode==='invalid'?{results:[{iso_3166_1:'FR',native_name:'France'},{iso_3166_1:'FR',native_name:'Duplicate'}]}:{results:[{iso_3166_1:'FR',native_name:'France'},{iso_3166_1:'US',native_name:'États-Unis'},{iso_3166_1:'JP',native_name:'Japon'}]}):qa.offers(u);
  const response=mode==='503'?new Response('{}',{status:503,headers:{'Content-Type':'application/json'}}):mode==='429'?new Response('{}',{status:429,headers:{'Content-Type':'application/json','Retry-After':'120'}}):mode==='nonjson'?new Response('<html>unavailable</html>',{headers:{'Content-Type':'text/html'}}):mode==='oversize'?new Response(JSON.stringify(body),{headers:{'Content-Type':'application/json','Content-Length':'99999999'}}):new Response(JSON.stringify(body),{headers:{'Content-Type':'application/json'}});
  if(qa.hold){await new Promise(r=>{qa.release=r;qa.waiting=true;});}return response;
 };
 qa.realNow=Date.now;
}'''
def run():
 with sync_playwright() as p:
  b=p.chromium.launch(executable_path='/usr/bin/chromium',headless=True,args=['--no-sandbox']);ctx=b.new_context(viewport={'width':1360,'height':1000},locale='fr-FR',timezone_id='Europe/Paris');page=ctx.new_page();page.set_default_timeout(6500);errors=[]
  page.on('pageerror',lambda e:errors.append(str(e)));page.on('dialog',lambda d:d.accept())
  png=base64.b64decode('iVBORw0KGgoAAAANSUhEUgAAAAIAAAACCAYAAABytg0kAAAAFElEQVQImWNkYPj/n4GBgYGJAQoAHhgCAQGXeHsAAAAASUVORK5CYII=')
  ctx.route(re.compile(r'https://image\.tmdb\.org/'),lambda r:r.fulfill(status=200,content_type='image/png',body=png))
  html=(WEB/'index.html').read_text();page.set_content(re.sub(r'<script.*?</script>|<link[^>]+>|<meta http-equiv="Content-Security-Policy"[^>]+>','',html));page.add_style_tag(content=(WEB/'app.css').read_text());page.evaluate(SETUP)
  for f in re.findall(r'<script src="([^"]+)"',html):page.add_script_tag(content=(WEB/f).read_text())
  page.wait_for_timeout(100);page.evaluate(FIXTURE)
  def seed(kind='show',identifier=True):
   page.evaluate(RESET);page.evaluate('''({kind,identifier})=>{
    availabilityForget();availabilityCooldown=0;libraryEpoch++;qa.avCalls=[];qa.avMode='normal';qa.regionMode='normal';qa.hold=false;qa.waiting=false;Date.now=qa.realNow;recoveryOnly=null;
    state.settings.region='FR';state.settings.tmdbKey='qa-key';state.settings.includeSpecials=false;
    state.media=[{id:'s',kind,source:identifier?'tmdb':'local',title:'Titre de validation des disponibilités',tmdbId:identifier?111:null,added:true,catalogComplete:identifier,episodeCount:2,posterUrl:'https://image.tmdb.org/t/p/w500/qa.png',providers:['Ancien service'],seasons:kind==='show'?[{number:0,title:'Spéciaux',episodeCount:1,providers:['Ancien service spécial']},{number:1,title:'Saison 1',episodeCount:2,providers:['Ancien service saison']}]:[],listIds:[],watched:false,plays:0,watchHistory:[],userNote:'Note privée conservée',rating:8}];
    state.episodes=kind==='show'?[{id:'e0',showId:'s',season:0,num:1,title:'Spécial',date:'2025-01-01',watched:false,plays:0,skipped:false},{id:'e1',showId:'s',season:1,num:1,title:'Épisode',date:'2025-01-01',watched:true,plays:2,watchHistory:[1700000000000,1700000001000],userNote:'Note privée épisode',rating:9},{id:'e2',showId:'s',season:1,num:2,title:'Épisode à voir',date:'2025-01-01',watched:false,plays:0,skipped:false}]:[];
    save(false);overlay={type:'item',id:'s',src:'media',title:state.media[0].title};render();}''',{'kind':kind,'identifier':identifier})
  def open_read(force=False):
   page.locator('#availabilityRefresh' if force else '#availabilityRead').click();page.wait_for_function('!availabilityScreen.loading')
  def region_load():
   page.locator('#availabilityCountries').click();page.wait_for_function('!availabilityScreen.regionsLoading')
  def start_hold():
   page.evaluate('()=>{qa.hold=true;qa.waiting=false;}');page.locator('#availabilityRefresh').click();page.wait_for_function('qa.waiting')
  def release():
   page.evaluate('()=>{qa.hold=false;qa.release();}');page.wait_for_timeout(60)
  seed();check('Series detail exposes on-demand availability',page.locator('#whereToWatch').is_visible())
  check('Legacy season names never presented with current country/date','pays et date non conservés' in page.locator('.season-providers').inner_text())
  page.evaluate("()=>{state.settings.region='US';render();}");check('Changing preferences does not relabel old season snapshot as US',page.locator('.season-providers').inner_text().count('US')==0 and 'Ancien relevé' in page.locator('.season-providers').inner_text());page.evaluate("()=>{state.settings.region='FR';render();}")
  raw=page.evaluate('localStorage.getItem(KEY)');before=page.evaluate('JSON.stringify(state)')
  page.locator('#whereToWatch').click();check('Opening screen does not fetch catalogue automatically',page.evaluate('qa.avCalls.length')==0)
  check('Attribution present before any request','JustWatch via TMDb' in page.locator('main').inner_text())
  check('Initial scope uses preference country without writing it',page.locator('#availabilityCountry').input_value()=='FR' and page.locator('#availabilitySeason').input_value()=='all')
  region_load();check('Country picker uses official-shape returned regions',page.locator('#availabilityCountry option').count()==3 and 'États-Unis' in page.locator('#availabilityCountry').inner_text())
  open_read();check('Five monetization types kept separate',page.locator('.availability-group').count()==5)
  check('Repeated provider across types counted once per type, once globally',page.evaluate('availabilityCached(availabilityQuery()).totalProviders===4&&availabilityCached(availabilityQuery()).totalOffers===5'))
  check('All provider logos are pinned to TMDb image endpoint',page.locator('.provider-card img').count()==5 and page.locator('.provider-card img').first.get_attribute('src')=='https://image.tmdb.org/t/p/w185/qa.png')
  check('Source link carries selected country but no arbitrary query',page.locator('#availabilitySource').get_attribute('href')=='https://www.themoviedb.org/tv/111/watch?locale=FR')
  check('Source external opening is explicitly labelled and isolated',page.locator('#availabilitySource').get_attribute('target')=='_blank' and 'noopener' in page.locator('#availabilitySource').get_attribute('rel') and 'externe' in page.locator('#availabilitySource').inner_text())
  check('Opening and reading cannot change personal data or Trakt queue',page.evaluate('localStorage.getItem(KEY)')==raw and page.evaluate('JSON.stringify(state)')==before and page.evaluate('syncState.queue.length')==0)
  stamp=page.evaluate('availabilityCached(availabilityQuery()).observedAt');calls=page.evaluate('qa.avCalls.length');open_read()
  check('Repeated read reuses session snapshot without a request or false freshness',page.evaluate('qa.avCalls.length')==calls and page.evaluate('availabilityCached(availabilityQuery()).observedAt')==stamp)
  page.locator('#availabilityMode').select_option('buy');check('Category filter does not mix renting and buying',page.locator('.provider-card').count()==1 and 'Achat' in page.locator('#availabilityGroups').inner_text())
  page.locator('#availabilitySearch').fill('does not exist');check('No filtered match is not global unavailability','correspond au filtre' in page.locator('#availabilityGroups').inner_text())
  page.locator('#availabilityCountry').select_option('US');check('Changing country immediately hides French results',page.locator('.provider-card').count()==0 and 'Aucun relevé' in page.locator('main').inner_text());open_read()
  check('US uses its own offers and link',page.locator('.provider-card').count()==1 and 'Service USA' in page.locator('.provider-card').inner_text() and page.locator('#availabilitySource').get_attribute('href').endswith('locale=US'))
  check('Country change remains a view-only preference',page.evaluate('state.settings.region')=='FR')
  page.locator('#availabilityCountry').select_option('FR');check('Country-scoped snapshot can be revisited without mixing US result',page.locator('.provider-card').count()==5)
  page.locator('#availabilitySeason').select_option('1');open_read();check('Season query uses season endpoint rather than whole show cache',page.evaluate('qa.avCalls.at(-1).path')=='/3/tv/111/season/1/watch/providers' and 'Saison 1' in page.locator('.availability-result').inner_text())
  check('Season response ID is not incorrectly checked as series ID',page.locator('#availabilityError').count()==0)
  page.locator('#availabilitySeason').select_option('0');open_read();check('Known specials remain selectable even when hidden in detail',page.evaluate('qa.avCalls.at(-1).path')=='/3/tv/111/season/0/watch/providers')
  page.locator('#availabilityBack').click();check('Return keeps original series detail',page.evaluate('overlay.type==="item"&&overlay.id==="s"') and page.locator('#whereToWatch').is_visible())
  page.locator('[data-availability-season="1"]').click();check('Season detail shortcut opens exact known scope',page.locator('#availabilitySeason').input_value()=='1');page.locator('#back').click();check('Top back restores correct detail',page.locator('#whereToWatch').is_visible())
  # Error responses must not replace the valid snapshot or write settings/history.
  seed();page.locator('#whereToWatch').click();open_read();stamp=page.evaluate('availabilityCached(availabilityQuery()).observedAt');raw=page.evaluate('localStorage.getItem(KEY)')
  for mode in ['invalid','mismatch','nonjson','oversize','503']:
   page.evaluate('(m)=>{qa.avMode=m;}',mode);open_read(True)
   check('Failed '+mode+' response keeps complete snapshot with original date',page.locator('#availabilityError').is_visible() and page.locator('.provider-card').count()==5 and page.evaluate('availabilityCached(availabilityQuery()).observedAt')==stamp)
  check('All failures leave saved library intact',page.evaluate('localStorage.getItem(KEY)')==raw)
  page.evaluate('()=>{qa.avMode="normal";Date.now=()=>qa.realNow()+360000;renderAvailability();}')
  check('Old snapshot explicitly says older than five minutes','plus de cinq minutes' in page.locator('.availability-result').inner_text());calls=page.evaluate('qa.avCalls.length');open_read()
  check('Expired snapshot is fetched again',page.evaluate('qa.avCalls.length')==calls+1 and page.evaluate('availabilityCached(availabilityQuery()).observedAt')>stamp);page.evaluate('Date.now=qa.realNow')
  page.evaluate('qa.avMode="429"');open_read(True);calls=page.evaluate('qa.avCalls.length');open_read(True);region_load()
  check('429 cooldown blocks manual refresh and countries, not a blind retry loop',page.evaluate('qa.avCalls.length')==calls and page.evaluate('availabilityCooldown>Date.now()') and page.locator('.provider-card').count()==5)
  check('No credential or response body reflected in errors','qa-key' not in page.locator('main').inner_text())
  # Regions failures and country-not-reported are distinct from no offers.
  seed();page.locator('#whereToWatch').click();page.evaluate('qa.regionMode="invalid"');region_load();check('Duplicate country response is rejected before populating selector',page.locator('#availabilityCountry option').count()==1 and page.locator('main [role=alert]').count()==1)
  page.evaluate('qa.regionMode="normal"');region_load();page.evaluate('qa.avMode="missing"');open_read()
  check('Missing country reports unknown not unavailable','ne rapporte pas' in page.locator('.availability-result').inner_text() and 'ne prouve pas' in page.locator('.availability-result').inner_text())
  page.evaluate('qa.avMode="none"');open_read(True);check('Empty known country is distinct from missing country','Aucune offre n’est renseignée' in page.locator('.availability-result').inner_text())
  check('No fabricated source link without provider-supplied URL',page.locator('#availabilitySource').count()==0)
  page.evaluate('qa.avMode="malicious"');open_read(True);check('Provider text is escaped, not injected markup',page.locator('.provider-card strong').inner_text()=='<img src=x onerror=alert(1)>' and page.locator('.provider-card img').count()==0)
  check('Unsafe source and logo ignored while names remain readable',page.locator('#availabilitySource').count()==0 and 'liens / logos non fiables' in page.locator('.availability-result').inner_text())
  # Cancellation and replacement exercise deliberately delayed fetch ignoring the signal.
  seed();page.locator('#whereToWatch').click();start_hold();page.locator('#availabilityCancel').click();release()
  check('Cancelled successful fetch cannot publish a snapshot',page.evaluate('availabilityCache.size')==0 and not page.evaluate('availabilityScreen.loading'))
  region_load();start_hold();page.locator('#availabilityCountry').select_option('US');release();check('Late French result cannot appear after country change',page.locator('.provider-card').count()==0 and page.evaluate('availabilityScreen.country')=='US')
  start_hold();page.locator('#availabilitySeason').select_option('1');release();check('Late whole-title result cannot appear as season result',page.evaluate('availabilityCache.size')==0 and page.locator('#availabilitySeason').input_value()=='1')
  start_hold();page.evaluate('state.settings.tmdbKey="other-key"');release();check('Changed API key rejects previous request result',page.evaluate('availabilityCache.size')==0)
  page.evaluate('state.settings.tmdbKey="qa-key"');start_hold();page.locator('#availabilityBack').click();release();check('Leaving screen rejects old result and preserves navigation',page.evaluate('availabilityCache.size')==0 and page.locator('#whereToWatch').is_visible())
  page.locator('#whereToWatch').click();start_hold();page.evaluate('libraryEpoch++');release();check('Replaced library never receives old availability snapshot',page.evaluate('availabilityCache.size')==0 and 'a changé' in page.locator('main').inner_text())
  # Country fetch can also be abandoned.
  seed();page.locator('#whereToWatch').click();page.evaluate('()=>{qa.hold=true;qa.waiting=false;}');page.locator('#availabilityCountries').click();page.wait_for_function('qa.waiting');page.locator('#availabilityCancel').click();release()
  check('Cancelled country discovery cannot change selector',page.locator('#availabilityCountry option').count()==1 and not page.evaluate('availabilityScreen.regionsLoading'))
  # No ID and no API key: no guesses or reads.
  seed(identifier=False);check('Manual unlinked show has no fabricated provider lookup',page.locator('#whereToWatch').count()==0 and 'identifiant TMDb confirmé' in page.locator('main').inner_text())
  check('Unknown season cannot trigger a lookup',page.evaluate('()=>{state.media[0].tmdbId=111;openAvailability("s",99);return overlay.type==="item";}'))
  seed('movie');page.locator('#whereToWatch').click();check('Film scope has no season selector',page.locator('#availabilitySeason').count()==0);page.evaluate('state.settings.tmdbKey=""');open_read();check('Missing key yields visible guidance without API request',page.locator('#availabilityError').is_visible() and page.evaluate('qa.avCalls.length')==0)
  page.evaluate('state.settings.tmdbKey="qa-key"');open_read();check('Film lookup uses film endpoint',page.evaluate('qa.avCalls.at(-1).path')=='/3/movie/111/watch/providers')
  # Cache stays in memory, capped; actual selectors operate beyond initial DOM limit.
  seed();page.locator('#whereToWatch').click();page.evaluate('qa.avMode="many"');open_read();check('Large provider response renders first sixty cards',page.locator('.provider-card').count()==60)
  page.locator('#availabilityMore').click();check('More adds only next sixty',page.locator('.provider-card').count()==120)
  page.locator('#availabilitySearch').fill('ecran final');check('Accent-insensitive search covers not-yet-rendered offers',page.locator('.provider-card').count()==1 and 'Écran final' in page.locator('.provider-card').inner_text())
  check('Session snapshots never enter portable backup',page.evaluate('()=>{const b=toPortableBackup(state);return b.schema===11&&!b.availabilityCache&&!b.availabilityScreen&&!b.media[0].watchAvailability;}'))
  page.evaluate('''()=>{for(let n=1;n<=25;n++){const q=FGAvailability.request({kind:'movie',tmdbId:n},'FR');rememberAvailability(FGAvailability.parse({results:{}},q),state.settings.tmdbKey,libraryEpoch);}}''');check('Availability cache is bounded to twenty snapshots',page.evaluate('availabilityCache.size')==20)
  # New provider details are safe in read-only mode too: they are runtime reads, not library writes.
  seed();page.locator('#whereToWatch').click();page.evaluate('storageWritable=false');raw=page.evaluate('localStorage.getItem(KEY)');open_read();check('Read-only library can consult offers without persistence',page.locator('.provider-card').count()==5 and page.evaluate('localStorage.getItem(KEY)')==raw);page.evaluate('storageWritable=true')
  # Next-up return retains its own filters, while lookup never marks viewing.
  seed();page.evaluate("()=>{overlay={type:'next-up',title:'À reprendre'};render();}")
  check('Next-up view offers source lookup without viewing action',page.locator('[data-next-availability="s"]').is_visible());page.locator('[data-next-availability="s"]').click();open_read();page.locator('#availabilityBack').click();check('Availability returns to next-up, not to unrelated page',page.evaluate('overlay.type')=='next-up')
  # Responsive layouts, contrast surfaces and keyboard navigation documented with fixture images.
  seed();page.locator('#whereToWatch').click();region_load();open_read()
  for width in [390,768,1360]:
   page.set_viewport_size({'width':width,'height':960});page.wait_for_function('parseFloat(getComputedStyle(document.querySelector("#toast")).opacity)<0.01')
   check('Availability fits viewport '+str(width),page.evaluate('document.documentElement.scrollWidth<=innerWidth+1'))
   check('Primary country and read controls keep touch height '+str(width),page.locator('#availabilityCountry').evaluate('(e)=>e.getBoundingClientRect().height>=42') and page.locator('#availabilityRead').evaluate('(e)=>e.getBoundingClientRect().height>=42'))
   page.screenshot(path=str(OUT/f'fixture-availability-{width}.png'),full_page=False)
  page.keyboard.press('Escape');check('Escape returns to the originating detail',page.locator('#whereToWatch').is_visible())
  check('New endpoints are GET only and no provider mutation called',page.evaluate('qa.avCalls.every(x=>x.method==="GET")'))
  check('New scenarios produce no uncaught JavaScript errors',not errors)
  (OUT/'browser-v023.json').write_text(json.dumps({'version':'0.23.0','passed':len(checks),'checks':checks,'errors':errors,'scope':'Production DOM and scripts in fixture context; controlled TMDb payloads, logos, clock and localStorage. No API account, real availability, native UI, external playback or PWA installation.'},ensure_ascii=False,indent=2));print(json.dumps({'passed':len(checks),'errors':errors}));b.close()
if __name__=='__main__':
 try:run()
 except Exception:
  (OUT/'browser-v023-failure.json').write_text(json.dumps({'checks':checks,'error':traceback.format_exc()},ensure_ascii=False,indent=2));raise
