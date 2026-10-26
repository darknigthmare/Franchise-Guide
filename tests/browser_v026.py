"""Actual UI/scripts, fixture TVmaze JSON/images/storage; no provider account or real-origin success claimed."""
from browser_harness import SETUP,RESET,ROOT,WEB
from browser_v025 import EXTRA as PUBLIC_BASE
from playwright.sync_api import sync_playwright
import re,json,base64,traceback
OUT=ROOT/'tests/results/v026';OUT.mkdir(parents=True,exist_ok=True)
checks=[]
def check(label,ok=True):
 if not ok:raise AssertionError(label)
 checks.append({'name':label,'passed':True});print('PASS',label,flush=True)
EXTRA=r'''()=>{
 const base=fetch;qa.tvDetailCalls=[];qa.detailMode='ok';qa.personMode='ok';qa.holdRead=false;qa.readWaiting=false;qa.now=Date.now;
 const reply=(x,status=200,headers={})=>new Response(JSON.stringify(x),{status,headers:{'Content-Type':'application/json',...headers}});
 const work=(id=11)=>({id,name:id===11?'Série publique QA':'Nouvelle série QA '+id,genres:['Drama'],premiered:'2020-01-01',rating:{average:0},image:{medium:'https://static.tvmaze.com/qa-work.jpg'}});
 window.fetch=async(url,opt={})=>{
  const u=new URL(String(url),'http://fixture.test');if(u.pathname.startsWith('/api/services/tvmaze')){u.hostname='api.tvmaze.com';u.pathname=u.pathname.replace('/api/services/tvmaze','');}
  if(u.hostname==='api.tvmaze.com'&&(u.pathname.startsWith('/episodes/')||u.pathname.startsWith('/people/'))){
   qa.tvDetailCalls.push({path:u.pathname,query:Object.fromEntries(u.searchParams),method:opt.method||'GET',headers:Object.fromEntries(new Headers(opt.headers||{}))});
   const person=u.pathname.startsWith('/people/'),mode=person?qa.personMode:qa.detailMode;
   if(qa.holdRead)await new Promise(r=>{qa.releaseRead=r;qa.readWaiting=true;});
   if(mode==='503'||mode==='429')return reply({},+mode,{'Retry-After':'120'});
   if(mode==='oversize')return reply({},200,{'Content-Length':'99999999'});
   if(u.pathname.startsWith('/people/')){
    const id=+u.pathname.split('/')[2];
    if(u.pathname.endsWith('/castcredits'))return mode==='partial'?reply({}):reply(mode==='many'?Array.from({length:145},(_,i)=>({_embedded:{show:{...work(100+i),name:i===144?'Étoile finale QA':'Série QA '+i}}})): [{_embedded:{show:work(11)},voice:true},{_embedded:{show:work(11)},self:true},{_embedded:{show:work(12)}}]);
    if(u.pathname.endsWith('/crewcredits'))return reply([{type:'Writer',_embedded:{show:work(11)}}]);
    return reply({id:mode==='wrong'?999:id,name:'Artiste TVmaze QA '+id,birthday:'1980-02-29',country:{name:'Pays renseigné QA'},image:{medium:'https://static.tvmaze.com/qa-person.jpg'}});
   }
   const eid=+u.pathname.split('/')[2];
   if(u.pathname.endsWith('/guestcast'))return mode==='partial'?reply({}):reply([{person:{id:88,name:'Artiste TVmaze QA',image:{medium:'https://static.tvmaze.com/qa-person.jpg'}},character:{name:'SECRET invitée'}},{person:{id:88,name:'Artiste TVmaze QA'},character:{name:'Narratrice'}}]);
   if(u.pathname.endsWith('/guestcrew'))return reply([{type:'Writer',person:{id:89,name:'Auteur de test'}}]);
   return reply({id:mode==='wrong'?999:eid,type:eid===103?'significant_special':'regular',name:'SECRET public '+eid,season:eid===101?1:2,number:eid===103?null:1,airdate:'2020-02-03',runtime:0,summary:'<p>SECRET synopsis public</p>',rating:{average:0},image:{original:'https://static.tvmaze.com/qa-episode.jpg'},_embedded:{show:{id:mode==='wrongShow'?999:11}}});
  }
  if(u.hostname==='api.tvmaze.com'&&u.pathname.startsWith('/shows/12')){
   if(u.pathname.endsWith('/seasons'))return reply([{id:120,number:1}]);
   if(u.pathname.endsWith('/episodes'))return reply([{id:1201,season:1,number:1,name:'Nouveau pilote QA',type:'regular',runtime:0,airdate:'2020-01-01'}]);
   if(u.pathname.endsWith('/cast'))return reply([]);return reply(work(12));
  }
  return base(url,opt);
 };
}'''
def run():
 with sync_playwright() as p:
  b=p.chromium.launch(executable_path='/usr/bin/chromium',headless=True,args=['--no-sandbox']);ctx=b.new_context(viewport={'width':1360,'height':1000},locale='fr-FR',timezone_id='Europe/Paris');page=ctx.new_page();page.set_default_timeout(6500);errors=[];answer={'yes':True};page.on('pageerror',lambda e:errors.append(str(e)));page.on('dialog',lambda d:d.accept() if answer['yes'] else d.dismiss())
  png=base64.b64decode('iVBORw0KGgoAAAANSUhEUgAAAAIAAAACCAYAAABytg0kAAAAFElEQVQImWNkYPj/n4GBgYGJAQoAHhgCAQGXeHsAAAAASUVORK5CYII=')
  ctx.route(re.compile(r'https://(static\.tvmaze\.com|image\.tmdb\.org)/'),lambda r:r.fulfill(status=200,content_type='image/png',body=png))
  html=(WEB/'index.html').read_text();page.set_content(re.sub(r'<script.*?</script>|<link[^>]+>|<meta http-equiv="Content-Security-Policy"[^>]+>','',html));page.add_style_tag(content=(WEB/'app.css').read_text());page.evaluate(SETUP);page.evaluate(PUBLIC_BASE);page.evaluate(EXTRA)
  for f in re.findall(r'<script src="([^"]+)"',html):page.add_script_tag(content=(WEB/f).read_text())
  page.evaluate(RESET);page.evaluate('()=>ensureAutomaticServices(true)');page.evaluate("()=>openScopedSearch('show')");page.locator('#q').fill('publique');page.wait_for_function('remoteResults.some(m=>m.source==="tvmaze")');page.locator('[data-act="add"]').first.click();page.wait_for_function('state.media.length===1')
  check('Public show added with empty key settings',page.evaluate('!state.settings.tmdbKey&&state.media[0].source==="tvmaze"'))
  check('Public credit has internal entry and official fallback',page.locator('[data-tvmaze-person="88"]').get_attribute('href')=='https://www.tvmaze.com/people/88')
  before=page.evaluate('localStorage.getItem(KEY)');page.locator('[data-tvmaze-person="88"]').click();page.wait_for_selector('#publicPersonRefresh')
  check('Person route has explicit TVmaze provider',page.evaluate('overlay.provider')=='tvmaze')
  check('Person accessible without developer keys','TVMAZE · SANS CLÉ' in page.locator('main').inner_text())
  check('Person portrait comes from TVmaze',page.locator('.person-portrait').get_attribute('src')=='https://static.tvmaze.com/qa-person.jpg')
  check('Person roles grouped by TVmaze show identity',page.locator('.filmography-credit').count()==2 and 'Interprétation vocale · Participation en son nom · Writer' in page.locator('main').inner_text())
  check('Biographical and movie limits explicit','pas filmographie' in page.locator('main').inner_text() or 'Ni filmographie cinéma' in page.locator('main').inner_text())
  check('Reading profile adds no titles nor writes',page.evaluate('localStorage.getItem(KEY)')==before and page.evaluate('state.media.length')==1)
  page.locator('#personQuery').fill('writer');check('Filter searches crew roles',page.locator('.filmography-credit').count()==1);page.locator('#personQuery').fill('')
  page.locator('#back').click();check('Back from public person returns correct series',page.evaluate('overlay.type==="item"&&overlay.id===state.media[0].id'))
  calls=page.evaluate('qa.tvDetailCalls.length');page.locator('[data-tvmaze-person="88"]').click();page.wait_for_selector('#publicPersonRefresh');check('Person cache reused without request',page.evaluate('qa.tvDetailCalls.length')==calls)
  page.locator('[data-open="tvmaze-show-12"] [data-act="add"]').click();page.wait_for_function('state.media.length===2')
  check('Adding a credited series uses complete no-key catalogue flow',page.evaluate('state.media[1].source==="tvmaze"&&state.episodes.some(e=>e.id==="tvmaze-episode-1201")'))
  page.locator('#back').click();page.wait_for_selector('#publicPersonRefresh');check('Back after adding credited series returns public person',page.evaluate('overlay.provider')=='tvmaze')
  page.evaluate('()=>{qa.personMode="many";}');page.locator('#publicPersonRefresh').click();page.wait_for_selector('#personMore')
  check('Large credited-series list initially limited to forty',page.locator('.filmography-credit').count()==40)
  page.locator('#personQuery').fill('etoile');check('Search finds not-yet-rendered credited series',page.locator('.filmography-credit').count()==1 and 'Étoile finale' in page.locator('main').inner_text())
  page.locator('#personQuery').fill('');page.locator('#personMore').click();check('Manual display expansion stays bounded',page.locator('.filmography-credit').count()==80)
  # Failed profile refresh retains dated cache with explicit recovery button.
  stamp=page.evaluate('publicPersonProfiles.get("88").value.loadedAt');page.evaluate('()=>{qa.personMode="partial";}');page.locator('#publicPersonRefresh').click();page.wait_for_selector('#publicPersonCached')
  check('Partial public person response not silently accepted',page.locator('#publicPersonRetry').is_visible())
  page.locator('#publicPersonCached').click();page.wait_for_selector('#publicPersonRefresh');check('Last person cache keeps original observation time',page.evaluate('publicPersonProfiles.get("88").value.loadedAt')==stamp)
  page.evaluate('()=>{qa.personMode="ok";overlay={type:"item",id:state.media[0].id,src:"media"};render();}')
  page.locator('[data-episode-sheet="tvmaze-episode-101"]').click();check('Public episode detail opens without network',page.locator('#episodeReadDetails').inner_text()=='Lire les détails sans clé')
  check('Public episode does not offer wrong TMDb availability',page.locator('#episodeAvailability').count()==0)
  page.locator('#episodePersonalNote').fill('Texte local à conserver');page.locator('#episodeReadDetails').click();page.wait_for_function('!episodeSheet.loading')
  check('Episode details exact public ID with embedded show',page.evaluate('qa.tvDetailCalls.some(c=>c.path==="/episodes/101"&&c.query.embed==="show")'))
  check('No keys sent by public detail requests',page.evaluate('qa.tvDetailCalls.every(c=>!c.query.api_key&&!c.headers.authorization)'))
  check('TVmaze guest and guest crew groups distinct',page.locator('#episodeCreditGroup option').count()==3)
  check('Duplicate guest roles grouped',page.locator('[data-episode-person="88"]').count()==1 and 'SECRET invitée · Narratrice' in page.locator('[data-episode-person="88"]').inner_text())
  check('Public zero runtime and score correctly attributed','0 min' in page.locator('.episode-sheet-summary').inner_text() and 'Note TVmaze : 0.0/10' in page.locator('.episode-sheet-summary').inner_text())
  check('Personal draft survives public read',page.locator('#episodePersonalNote').input_value()=='Texte local à conserver')
  check('Public read does not overwrite local episode title',page.evaluate('state.episodes[0].title')=='Pilote QA')
  calls=page.evaluate('qa.tvDetailCalls.length');stamp=page.evaluate('episodeSheetSnapshot(episodeSheetQuery()).observedAt');page.locator('#episodeReadDetails').click();check('Public episode cache does not fake a new timestamp',page.evaluate('qa.tvDetailCalls.length')==calls and page.evaluate('episodeSheetSnapshot(episodeSheetQuery()).observedAt')==stamp)
  for mode in ['wrong','wrongShow','partial','oversize','503']:
   page.evaluate('(mode)=>{qa.detailMode=mode;}',mode);page.locator('#episodeRefreshDetails').click();page.wait_for_function('!episodeSheet.loading');check('Reject public episode '+mode+' while keeping old snapshot',page.locator('#episodeRemoteError').is_visible() and page.evaluate('episodeSheetSnapshot(episodeSheetQuery()).observedAt')==stamp)
  page.evaluate('()=>{qa.detailMode="ok";}');page.locator('#episodePersonalRating').fill('8');page.locator('#episodeSavePersonal').click()
  check('Personal TVmaze fields saved locally with no fabricated Trakt identity',page.evaluate('state.episodes[0].userNote==="Texte local à conserver"&&state.episodes[0].rating===8&&!state.episodes[0].tmdbId&&syncState.queue.length===0'))
  page.evaluate('()=>{state.settings.hideEpisodeSpoilers=true;save(false);render();}')
  check('Spoiler guard hides public guest names and search',page.locator('#episodeCreditQuery').count()==0 and 'SECRET synopsis' not in page.locator('main').inner_text())
  page.locator('#episodeReveal').click();page.locator('#episodeCreditQuery').fill('narratrice');check('Role filtering works after explicit reveal',page.locator('[data-episode-person]').count()==1)
  page.locator('[data-episode-person="88"]').click();page.wait_for_selector('#publicPersonRefresh');check('Guest opens TVmaze rather than same-numbered TMDb person',page.evaluate('overlay.provider==="tvmaze"&&overlay.id===88'))
  page.locator('#back').click();check('Back from guest restores episode route',page.evaluate('overlay.type==="episode-detail"&&episodeSheet.id==="tvmaze-episode-101"'))
  page.locator('#episodeNext').click();check('Next standard episode follows saved ordering',page.evaluate('episodeSheet.id')=='tvmaze-episode-102')
  check('Reveal does not carry to next episode',page.locator('#episodeReveal').is_visible())
  page.evaluate('()=>openEpisodeSheet(state.media[0].id,"tvmaze-episode-103")');page.locator('#episodeReadDetails').click();page.wait_for_function('!episodeSheet.loading')
  check('Unknown-number special read by provider ID',page.evaluate('episodeSheetSnapshot(episodeSheetQuery()).id')==103)
  # Late read must not update after explicit cancellation.
  page.evaluate('()=>{qa.holdRead=true;qa.readWaiting=false;}');page.locator('#episodeRefreshDetails').click();page.wait_for_function('qa.readWaiting');page.locator('#episodeCancelDetails').click();page.evaluate('()=>{qa.holdRead=false;qa.releaseRead();}');page.wait_for_timeout(80)
  check('Cancelled public episode does not replace previous snapshot','annulée' in page.locator('main').inner_text())
  page.evaluate('()=>{qa.detailMode="429";}');page.locator('#episodeRefreshDetails').click();page.wait_for_function('!episodeSheet.loading');count=page.evaluate('qa.tvDetailCalls.length');page.locator('#episodeRefreshDetails').click();page.wait_for_function('!episodeSheet.loading')
  check('Public details share retry cooldown',page.evaluate('qa.tvDetailCalls.length')==count and page.evaluate('tvmazeRetryAt>Date.now()'))
  check('Personal notes remain editable during provider cooldown',page.locator('#episodeSavePersonal').is_enabled())
  # Cooldown also affects a profile read, not just the same endpoint.
  page.evaluate('()=>{publicPersonProfiles.clear();openPublicPerson(89);}');page.wait_for_selector('#publicPersonRetry');check('Same supplier cooldown blocks person network too',page.evaluate('qa.tvDetailCalls.length')==count)
  page.evaluate('()=>{tvmazeRetryAt=0;qa.detailMode="ok";qa.personMode="ok";}');page.locator('#publicPersonRetry').click();page.wait_for_selector('#publicPersonRefresh')
  page.wait_for_function('!document.querySelector("#toast").classList.contains("show")')
  for w in [390,768,1360]:
   page.set_viewport_size({'width':w,'height':1000});page.evaluate('window.scrollTo(0,0)');page.wait_for_timeout(250);page.screenshot(path=str(OUT/f'fixture-public-person-{w}.png'),full_page=True);check('Public person fits viewport with touch-sized input '+str(w),page.evaluate('document.documentElement.scrollWidth<=innerWidth+1&&document.querySelector("#personQuery").getBoundingClientRect().height>=44'))
  page.evaluate('()=>{openEpisodeSheet(state.media[0].id,"tvmaze-episode-101");state.settings.hideEpisodeSpoilers=false;render();}');page.locator('#episodeReadDetails').click();page.wait_for_function('!episodeSheet.loading')
  page.wait_for_function('!document.querySelector("#toast").classList.contains("show")')
  for w in [390,768,1360]:
   page.set_viewport_size({'width':w,'height':1000});page.evaluate('window.scrollTo(0,0)');page.wait_for_timeout(250);page.screenshot(path=str(OUT/f'fixture-public-episode-{w}.png'),full_page=True);check('Public episode fits viewport '+str(w),page.evaluate('document.documentElement.scrollWidth<=innerWidth+1'))
  portable=page.evaluate('toPortableBackup(state)');check('Temporary profiles and episode observations absent from portable data',not any(k in portable for k in ['publicPersonProfiles','episodeDetailCache','tvmazeRetryAt']))
  check('Public profile browsing never converted existing sources',page.evaluate('state.media.every(m=>m.source==="tvmaze")'))
  check('No uncaught errors in keyless profile/episode flow',not errors)
  (OUT/'browser-v026.json').write_text(json.dumps({'version':'0.26.0','passed':len(checks),'scope':'production DOM/scripts/CSS; synthetic providers, images and storage, not actual-origin or live API','checks':checks,'errors':errors},ensure_ascii=False,indent=2));print(json.dumps({'passed':len(checks),'errors':errors}));b.close()
if __name__=='__main__':
 try:run()
 except Exception:
  (OUT/'browser-v026-failure.json').write_text(json.dumps({'passed':len(checks),'checks':checks,'traceback':traceback.format_exc()},ensure_ascii=False,indent=2));raise
