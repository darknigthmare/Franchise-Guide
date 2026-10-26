"""Production episode-sheet DOM/script fixtures, not live TMDb or browser-origin persistence."""
from pathlib import Path
from playwright.sync_api import sync_playwright
from browser_harness import SETUP,RESET,AUTH,ROOT,WEB
import re,json,base64,traceback,sys
OUT=ROOT/'tests/results/v024';OUT.mkdir(parents=True,exist_ok=True)
checks=[]
def check(name,value=True):
 if not value:raise AssertionError(name)
 checks.append({'name':name,'passed':True});print('PASS',name,flush=True)
FIXTURE=r'''()=>{
 const previous=fetch;qa.detailCalls=[];qa.detailMode='normal';qa.holdDetail=false;qa.detailWaiting=false;qa.realNow=Date.now;
 qa.episodeRaw=u=>{const p=u.pathname.split('/'),season=Number(p[5]),number=Number(p[7]),id=11100+season*10+number;
 const x={id,show_id:Number(p[3]),season_number:season,episode_number:number,name:'SECRET Titre de validation '+number,overview:'SECRET Synopsis épisode',air_date:'2025-01-01',runtime:0,vote_average:0,vote_count:0,production_code:'QA-'+number,still_path:'/qa-still.png',credits:{id,cast:[{id:900,name:'Acteur QA',character:'Capitaine',profile_path:'/qa-person.png'},{id:900,name:'Acteur QA',character:'Narrateur',profile_path:'/qa-person.png'}],guest_stars:[{id:901,name:'Invitée Élodie QA',character:'SECRET Émissaire',profile_path:'/qa-guest.png'}],crew:[{id:902,name:'Réalisateur QA',job:'Director',department:'Directing'}]}};
 if(qa.detailMode==='wrong')x.id=123456;
 if(qa.detailMode==='partial')delete x.credits;
 if(qa.detailMode==='unsafe'){x.name='<img src=x onerror=alert(1)>';x.still_path='https://evil.test/still';x.credits.cast[0].profile_path='https://evil.test/p.png';}
 if(qa.detailMode==='many')x.credits.cast=Array.from({length:175},(_,i)=>({id:1000+i,name:i===174?'Étoile finale QA':'Acteur QA '+i,character:'Rôle '+i,profile_path:'/qa-person.png'}));
 return x;
 };
 window.fetch=async(url,options={})=>{
 const u=new URL(String(url),'http://fixture.local');
 if(u.hostname==='api.themoviedb.org'&&/^\/3\/person\/\d+$/.test(u.pathname))return new Response(JSON.stringify({id:Number(u.pathname.split('/').pop()),name:'Personne de la fiche épisode QA',biography:'Fiche personne fictive',combined_credits:{cast:[],crew:[]}}),{headers:{'Content-Type':'application/json'}});
 if(u.hostname!=='api.themoviedb.org'||!/^\/3\/tv\/\d+\/season\/\d+\/episode\/\d+$/.test(u.pathname))return previous(url,options);
 qa.detailCalls.push({path:u.pathname,method:options.method||'GET',params:Object.fromEntries(u.searchParams)});const mode=qa.detailMode,body=qa.episodeRaw(u);
 if(qa.holdDetail)await new Promise(r=>{qa.releaseDetail=r;qa.detailWaiting=true;});
 if(mode==='503'||mode==='429')return new Response('{}',{status:Number(mode),headers:{'Content-Type':'application/json','Retry-After':'120'}});
 if(mode==='nonjson')return new Response('<html>error</html>',{headers:{'Content-Type':'text/html'}});
 if(mode==='oversize')return new Response(JSON.stringify(body),{headers:{'Content-Length':'99999999','Content-Type':'application/json'}});
 return new Response(JSON.stringify(body),{headers:{'Content-Type':'application/json'}});
 };
}'''
def run():
 with sync_playwright() as p:
  browser=p.chromium.launch(executable_path='/usr/bin/chromium',headless=True,args=['--no-sandbox']);ctx=browser.new_context(viewport={'width':1360,'height':1000},locale='fr-FR',timezone_id='Europe/Paris');page=ctx.new_page();page.set_default_timeout(6500)
  errors=[];dialogs=[];decision={'accept':True}
  page.on('pageerror',lambda e:errors.append(str(e)))
  def ondialog(d):
   dialogs.append(d.message)
   if decision['accept']:d.accept()
   else:d.dismiss()
  page.on('dialog',ondialog)
  png=base64.b64decode('iVBORw0KGgoAAAANSUhEUgAAAAIAAAACCAYAAABytg0kAAAAFElEQVQImWNkYPj/n4GBgYGJAQoAHhgCAQGXeHsAAAAASUVORK5CYII=')
  ctx.route(re.compile(r'https://image\.tmdb\.org/'),lambda r:r.fulfill(status=200,content_type='image/png',body=png))
  html=(WEB/'index.html').read_text();page.set_content(re.sub(r'<script.*?</script>|<link[^>]+>|<meta http-equiv="Content-Security-Policy"[^>]+>','',html));page.add_style_tag(content=(WEB/'app.css').read_text());page.evaluate(SETUP)
  for f in re.findall(r'<script src="([^"]+)"',html):page.add_script_tag(content=(WEB/f).read_text())
  page.wait_for_timeout(100);page.evaluate(FIXTURE)
  def seed(identified=True,spoilers=False):
   page.evaluate('()=>{episodeSheetAbort();episodeSheet=null;episodeDetailCache.clear();episodeSheetCooldown=0;Date.now=qa.realNow;}');page.evaluate(RESET)
   page.evaluate('''({identified,spoilers})=>{
    libraryEpoch++;recoveryOnly=null;qa.detailCalls=[];qa.detailMode='normal';qa.holdDetail=false;qa.detailWaiting=false;
    state.settings.tmdbKey=identified?'qa-detail-key':'';state.settings.hideEpisodeSpoilers=spoilers;state.settings.showCastImages=true;state.settings.includeSpecials=false;
    state.media=[{id:'s',kind:'show',source:identified?'tmdb':'local',tmdbId:identified?111:null,title:'Série de validation individuelle',added:true,contentLanguage:'fr-FR',posterUrl:'https://image.tmdb.org/t/p/w500/qa.png',catalogComplete:true,episodeCount:4,seasons:[{number:0,title:'Spéciaux',episodeCount:1},{number:1,title:'Saison 1',episodeCount:2},{number:2,title:'Saison 2',episodeCount:2}],listIds:[],rating:9,userNote:'Note parent préservée',credits:[{id:'tmdb-person-99',name:'Cast GLOBAL à ne pas remplacer',role:'Autre'}]}];
    state.episodes=[{id:'sp',showId:'s',season:0,num:1,title:'Spécial local',tmdbId:11101,date:'2025-01-01',runtimeMinutes:0,watched:false,plays:0},...Array.from({length:4},(_,i)=>{const season=i<2?1:2,num=i%2+1;return{id:'e'+(i+1),showId:'s',season,num,tmdbId:identified?11100+season*10+num:null,title:'SECRET local '+(i+1),overview:'SECRET synopsis local',date:'2025-01-01',runtimeMinutes:0,watched:i===1,plays:i===1?3:0,watchHistory:i===1?[1700000000000,1700000001000]:[],lastWatchedAt:i===1?1700000001000:null,skipped:false,collected:false,rating:i===0?6:null,userNote:i===0?'Note privée initiale':null}})];
    save(false);overlay={type:'item',id:'s',src:'media',title:state.media[0].title};render();
   }''',{'identified':identified,'spoilers':spoilers})
  def open_sheet():page.locator('.episode [data-episode-sheet="e1"]').click()
  def read(force=False):
   page.locator('#episodeRefreshDetails' if force else '#episodeReadDetails').click();page.wait_for_function('!episodeSheet?.loading')
  def hold():
   page.evaluate('()=>{qa.holdDetail=true;qa.detailWaiting=false;}');page.locator('#episodeRefreshDetails').click();page.wait_for_function('qa.detailWaiting')
  def release():page.evaluate('()=>{qa.holdDetail=false;qa.releaseDetail();}');page.wait_for_timeout(80)
  seed();check('Series list exposes episode sheet button',page.locator('.episode [data-episode-sheet]').count()==2)
  before=page.evaluate('localStorage.getItem(KEY)');open_sheet()
  check('Opening individual sheet performs no network call',page.evaluate('qa.detailCalls.length')==0)
  check('Local episode immediately readable',page.locator('main h2').inner_text()=='SECRET local 1')
  check('Local zero-minute duration is not replaced by default','0 min' in page.locator('.episode-sheet-summary').inner_text())
  check('Opening does not persist or enqueue anything',page.evaluate('localStorage.getItem(KEY)')==before and page.evaluate('syncState.queue.length')==0)
  check('Private note and score populate separate form',page.locator('#episodePersonalRating').input_value()=='6' and page.locator('#episodePersonalNote').input_value()=='Note privée initiale')
  page.locator('#episodePersonalNote').fill('Brouillon privé conservé pendant lecture');read()
  check('Episode request uses exact local identifiers',page.evaluate('qa.detailCalls[0].path')=='/3/tv/111/season/1/episode/1')
  check('Episode request carries series language and appended episode credits',page.evaluate('qa.detailCalls[0].params.language')=='fr-FR' and page.evaluate('qa.detailCalls[0].params.append_to_response')=='credits')
  check('Reading metadata preserves personal draft',page.locator('#episodePersonalNote').input_value()=='Brouillon privé conservé pendant lecture')
  check('Metadata read does not alter saved private data',page.evaluate('localStorage.getItem(KEY)')==before)
  check('Episode zero rating displayed rather than hidden','0.0/10' in page.locator('.episode-sheet-summary').inner_text())
  check('Episode cast guest crew are separate groups',page.locator('#episodeCreditGroup option').count()==4)
  check('Repeated actor roles grouped in one cast card',page.locator('[data-episode-person="900"]').count()==1 and 'Capitaine · Narrateur' in page.locator('[data-episode-person="900"]').inner_text())
  check('Global series cast not substituted in episode credits','Cast GLOBAL' not in page.locator('main').inner_text())
  check('Still loads from allowed TMDb path',page.locator('.episode-sheet-still').get_attribute('src')=='https://image.tmdb.org/t/p/w780/qa-still.png')
  calls=page.evaluate('qa.detailCalls.length');stamp=page.evaluate('episodeSheetSnapshot(episodeSheetQuery()).observedAt');read()
  check('Repeated consultation keeps original timestamp without another request',page.evaluate('qa.detailCalls.length')==calls and page.evaluate('episodeSheetSnapshot(episodeSheetQuery()).observedAt')==stamp)
  page.locator('#episodePersonalRating').fill('8');page.locator('#episodeSavePersonal').click()
  check('Saving personal form persists note and numeric rating',page.evaluate('state.episodes.find(e=>e.id==="e1").rating===8&&state.episodes.find(e=>e.id==="e1").userNote==="Brouillon privé conservé pendant lecture"'))
  check('Personal save never copies temporary TMDb title into local metadata',page.evaluate('state.episodes.find(e=>e.id==="e1").title')=='SECRET local 1')
  check('Parent series score and note remain unchanged',page.evaluate('state.media[0].rating===9&&state.media[0].userNote==="Note parent préservée"'))
  page.locator('#episodePersonalRating').fill('');page.locator('#episodePersonalNote').fill('');page.locator('#episodeSavePersonal').click()
  check('Empty personal form truly clears note and rating',page.evaluate('state.episodes.find(e=>e.id==="e1").rating===null&&state.episodes.find(e=>e.id==="e1").userNote===null'))
  # Navigation and anti-spoiler controls.
  page.locator('#episodeNext').click();check('Next reaches real next episode',page.evaluate('episodeSheet.id')=='e2')
  check('Existing known and undated views shown without manufactured dates','3 lecture(s) comptée(s) · 2 date(s) connue(s) · 1 sans date précise' in page.locator('main').inner_text())
  page.locator('#episodeNext').click();check('Navigation crosses seasons in order',page.evaluate('episodeSheet.id')=='e3');page.locator('#episodePrevious').click();page.locator('#episodePrevious').click()
  check('Hidden special does not become previous standard episode',page.locator('#episodePrevious').is_disabled())
  page.locator('#episodePersonalNote').fill('Brouillon non sauvegardé');decision['accept']=False;page.locator('#episodeNext').click()
  check('Declining navigation retains draft and episode',page.evaluate('episodeSheet.id')=='e1' and page.locator('#episodePersonalNote').input_value()=='Brouillon non sauvegardé')
  page.locator('#back').click();check('App bar back also protects unsaved form',page.evaluate('overlay.type')=='episode-detail')
  decision['accept']=True;page.locator('#episodeReloadPersonal').click()
  page.locator('#episodeCreditGroup').select_option('guest_stars');check('Guest-only filter isolates episode guest',page.locator('.episode-credit').count()==1)
  page.locator('#episodeCreditQuery').fill('elodie');check('Credit search folds accents',page.locator('.episode-credit').count()==1)
  page.locator('[data-episode-person="901"]').click();page.wait_for_selector('.person-hero')
  check('Episode actor opens existing person page','Personne de la fiche épisode QA' in page.locator('main').inner_text())
  page.locator('#back').click();check('Person back returns to original episode sheet',page.evaluate('overlay.type==="episode-detail"&&episodeSheet.id==="e1"'))
  page.locator('#episodeAvailability').click();check('Availability opened for this actual season',page.locator('#availabilitySeason').input_value()=='1');page.locator('#back').click()
  check('Availability back returns to same episode',page.evaluate('episodeSheet.id')=='e1')
  seed(spoilers=True);open_sheet();read()
  check('Spoilers absent from header body and person cards','SECRET' not in page.locator('main').inner_text() and page.locator('.episode-credit').count()==0)
  check('Hidden episode still is absent from DOM',page.locator('.episode-sheet-still').count()==0)
  check('Hidden credit search cannot leak guest names',page.locator('#episodeCreditQuery').count()==0)
  page.locator('#episodeReveal').click();check('Explicit reveal displays episode details and guests','SECRET Titre' in page.locator('main').inner_text() and page.locator('.episode-credit').count()==3)
  page.locator('#episodeHide').click();check('Can conceal episode details again','SECRET' not in page.locator('main').inner_text())
  page.locator('#episodeReveal').click();page.locator('#episodeNext').click();page.locator('#episodeNext').click();check('Reveal is not inherited by another unseen episode','SECRET' not in page.locator('main').inner_text())
  # Targeted form conflicts and journal semantics.
  seed();open_sheet();page.evaluate(AUTH);page.locator('#episodeReloadPersonal').click();page.locator('#episodePersonalNote').fill('Texte strictement privé');page.locator('#episodeSavePersonal').click()
  check('Text note alone is not a Trakt comment or rating operation',page.evaluate('syncState.queue.length')==0)
  page.locator('#episodePersonalRating').fill('7');page.locator('#episodeSavePersonal').click()
  check('Only numeric episode rating can enter Trakt outbox',page.evaluate('syncState.queue.length===1&&syncState.queue[0].field==="ratings"'))
  check('Private note never appears in queued request body','Texte strictement privé' not in page.evaluate('JSON.stringify(syncState.queue)'))
  page.evaluate('()=>{state.episodes.find(e=>e.id==="e1").watchHistory=[1700000002000];state.episodes.find(e=>e.id==="e1").plays=1;state.episodes.find(e=>e.id==="e1").watched=true;state.episodes.find(e=>e.id==="e1").lastWatchedAt=1700000002000;save(false);}')
  page.locator('#episodePersonalNote').fill('Après visionnage');page.locator('#episodeSavePersonal').click()
  check('Later viewing remains when saving older form personal fields',page.evaluate('state.episodes.find(e=>e.id==="e1").lastWatchedAt')==1700000002000)
  page.evaluate('()=>{state.episodes.find(e=>e.id==="e1").rating=10;save(false);}')
  page.locator('#episodePersonalNote').fill('Ne pas écraser');page.locator('#episodeSavePersonal').click()
  check('Conflicting personal fields block whole save',page.locator('#episodePersonalError').count()==1 and page.evaluate('state.episodes.find(e=>e.id==="e1").rating===10&&state.episodes.find(e=>e.id==="e1").userNote==="Après visionnage"'))
  page.locator('#episodeReloadPersonal').click();page.evaluate('()=>{authGeneration++;}')
  page.locator('#episodePersonalNote').fill('Compte changé');page.locator('#episodeSavePersonal').click();check('Account change requires explicit reload before numeric-save side effect','compte Trakt a changé' in page.locator('#episodePersonalError').inner_text())
  page.locator('#episodeReloadPersonal').click();page.evaluate('traktSyncing=true');page.locator('#episodePersonalNote').fill('Pendant synchro');page.locator('#episodeSavePersonal').click();check('Concurrent sync blocks personal transaction','Synchronisation Trakt en cours' in page.locator('#episodePersonalError').inner_text());page.evaluate('traktSyncing=false')
  page.locator('#episodeReloadPersonal').click();page.evaluate('()=>{Date.now=()=>qa.realNow()+601000;}');page.locator('#episodePersonalNote').fill('Expiré');page.locator('#episodeSavePersonal').click();check('Expired editor cannot overwrite current data','expiré' in page.locator('#episodePersonalError').inner_text());page.evaluate('Date.now=qa.realNow')
  seed(identified=False);open_sheet();check('Local episode without provider identity remains editable',page.locator('#episodeReadDetails').is_disabled() and page.locator('#episodeSavePersonal').is_enabled());page.locator('#episodePersonalNote').fill('Note hors ligne');page.locator('#episodeSavePersonal').click();check('No-key note editing never calls provider',page.evaluate('qa.detailCalls.length===0&&state.episodes.find(e=>e.id==="e1").userNote==="Note hors ligne"'))
  seed();open_sheet();read();stamp=page.evaluate('episodeSheetSnapshot(episodeSheetQuery()).observedAt')
  for mode in ['wrong','partial','503','nonjson','oversize']:
   page.evaluate('(m)=>qa.detailMode=m',mode);read(True);check('Bad '+mode+' response keeps last complete snapshot',page.locator('#episodeRemoteError').count()==1 and page.evaluate('episodeSheetSnapshot(episodeSheetQuery()).observedAt')==stamp)
  page.evaluate('qa.detailMode="429"');calls=page.evaluate('qa.detailCalls.length');read(True);read(True);check('Manual refresh cannot bypass TMDb cooldown',page.evaluate('qa.detailCalls.length')==calls+1 and page.evaluate('episodeSheetCooldown>Date.now()'))
  page.evaluate('()=>{episodeSheetCooldown=0;qa.detailMode="normal";}');hold();page.locator('#episodeCancelDetails').click();release();check('Canceled late response ignored',page.evaluate('episodeSheetSnapshot(episodeSheetQuery()).observedAt')==stamp)
  hold();page.evaluate('()=>{state.settings.tmdbKey="different-key";}');release();check('Late response with former API key discarded',page.locator('.episode-credit').count()==0)
  page.evaluate('state.settings.tmdbKey="qa-detail-key"');read(True);hold();page.evaluate('()=>{state.media[0].contentLanguage="en-US";}');release();check('Late response for prior language not displayed',page.locator('.episode-credit').count()==0)
  read(True);check('Changed series language used in new request',page.evaluate('qa.detailCalls.at(-1).params.language')=='en-US')
  hold();page.locator('#episodeNext').click();release();check('Late response cannot overwrite next episode',page.evaluate('episodeSheet.id')=='e2' and 'SECRET local 2' in page.locator('main h2').inner_text())
  hold();page.evaluate('()=>{libraryEpoch++;}');release();check('Replaced library invalidates pending result','bibliothèque' in page.locator('main').inner_text().lower())
  seed();open_sheet();page.evaluate('qa.detailMode="unsafe"');read();check('Provider markup rendered literally, never executed','<img src=x' in page.locator('main h2').inner_text() and page.locator('main h2 img').count()==0)
  check('Untrusted remote still absent',page.locator('.episode-sheet-still').count()==0)
  page.evaluate('qa.detailMode="many"');read(True);check('Long episode credits rendered in bounded first page',page.locator('.episode-credit').count()==60)
  page.locator('#episodeCreditQuery').fill('etoile');check('Credit search covers not-yet-rendered people',page.locator('.episode-credit').count()==1 and 'Étoile finale' in page.locator('.episode-credit').inner_text())
  page.locator('#episodeCreditQuery').fill('');page.locator('#episodeMoreCredits').click();check('Credits load next slice',page.locator('.episode-credit').count()==120)
  page.locator('#episodeBackSeries').click();page.locator('#manageEpisodes').click();page.locator('#episodeManagerRows [data-episode-sheet="e1"]').click();check('Episode sheet accessible from batch manager without checking a box',page.evaluate('episodeSheet.id')=='e1');page.locator('#back').click();check('Episode back restores batch manager',page.evaluate('overlay.type')=='episodes')
  seed();open_sheet();page.locator('#episodeWatchAction').click();check('Watch action uses existing preview rather than immediate mutation',page.locator('#episodeBatchPreview').is_visible() and not page.evaluate('state.episodes.find(e=>e.id==="e1").watched'));page.keyboard.press('Escape');page.wait_for_function('!document.querySelector("#episodeBatchPreview")')
  # Persistence failure keeps recoverable note draft and last stored values.
  page.locator('#episodePersonalNote').fill('Brouillon récupérable après quota');before=page.evaluate('localStorage.getItem(KEY)')
  page.evaluate('()=>{qa.originalSet=localStorage.setItem;localStorage.setItem=(k,v)=>{if(k===KEY)throw Error("Quota simulé");qa.originalSet(k,v)};}');page.locator('#episodeSavePersonal').click()
  check('Quota error never claims successful note save',page.locator('#episodePersonalError').is_visible() and page.evaluate('localStorage.getItem(KEY)')==before)
  check('Quota keeps recoverable draft with intended private note',page.evaluate('storageDraft.episodes.find(e=>e.id==="e1").userNote')=='Brouillon récupérable après quota')
  check('Quota restores original in-memory saved note',page.evaluate('state.episodes.find(e=>e.id==="e1").userNote')=='Note privée initiale');page.evaluate('()=>{localStorage.setItem=qa.originalSet;}')
  seed();open_sheet();page.locator('#episodePersonalNote').fill('Transport privé épisode');page.locator('#episodePersonalRating').fill('9');page.locator('#episodeSavePersonal').click()
  check('Portable backup roundtrip retains edited episode personal fields',page.evaluate('()=>{const e=fromPortableBackup(toPortableBackup(state)).episodes.find(e=>e.id==="e1");return e.userNote==="Transport privé épisode"&&e.rating===9}') )
  page.evaluate('async()=>{await refreshCatalogItem(state.media[0]);}');check('Actual catalogue refresh keeps episode personal note and score',page.evaluate('state.episodes.some(e=>e.season===1&&e.num===1&&e.userNote==="Transport privé épisode"&&e.rating===9)'))
  seed();open_sheet();read();page.locator('#episodePersonalNote').fill('Brouillon pendant navigation');decision['accept']=False;page.locator('[data-rail="movies"]').click();check('Sidebar navigation cannot silently discard private draft',page.evaluate('overlay.type')=='episode-detail' and page.locator('#episodePersonalNote').input_value()=='Brouillon pendant navigation');decision['accept']=True;page.locator('#episodeReloadPersonal').click()
  for width,height in [(390,844),(768,1024),(1360,1000)]:
   page.set_viewport_size({'width':width,'height':height});page.wait_for_function('!document.querySelector("#toast").classList.contains("show") && Number(getComputedStyle(document.querySelector("#toast")).opacity)<0.01');page.evaluate('()=>{document.activeElement?.blur();window.scrollTo(0,0);}');page.wait_for_timeout(60);check('Episode sheet fits viewport '+str(width),page.evaluate('document.documentElement.scrollWidth<=innerWidth+1'));page.screenshot(path=str(OUT/f'fixture-episode-{width}.png'),full_page=True)
  check('No uncaught JavaScript errors in episode flows',not errors)
  (OUT/'browser-v024.json').write_text(json.dumps({'version':'0.24.0','scope':'real DOM/scripts/CSS; synthetic catalogue/images/storage/clock; not live OAuth or device validation','passed':len(checks),'checks':checks,'errors':errors},ensure_ascii=False,indent=2));print(json.dumps({'passed':len(checks),'errors':errors}));browser.close()
if __name__=='__main__':
 try:run()
 except Exception:
  (OUT/'browser-v024-failure.json').write_text(json.dumps({'passed':len(checks),'checks':checks,'traceback':traceback.format_exc()},ensure_ascii=False,indent=2));raise
