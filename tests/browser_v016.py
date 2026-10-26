"""V0.16 actual DOM/scripts/CSS with mocked providers/storage. No real calendar account import."""
from pathlib import Path
from playwright.sync_api import sync_playwright
from browser_harness import SETUP,RESET,AUTH,ROOT,WEB
import re,json,traceback,base64
OUT=ROOT/'tests/results/v016';OUT.mkdir(parents=True,exist_ok=True)
checks=[]
def check(name,v=True):
 if not v:raise AssertionError(name)
 checks.append({'name':name,'passed':True});print('PASS',name,flush=True)
def run():
 with sync_playwright() as p:
  b=p.chromium.launch(executable_path='/usr/bin/chromium',headless=True,args=['--no-sandbox']);ctx=b.new_context(viewport={'width':1360,'height':1000},locale='fr-FR',timezone_id='Europe/Paris');page=ctx.new_page();page.set_default_timeout(6000);errors=[]
  page.on('pageerror',lambda e:errors.append(str(e)));page.on('dialog',lambda d:d.accept())
  image=base64.b64decode('iVBORw0KGgoAAAANSUhEUgAAAAIAAAACCAYAAABytg0kAAAAFElEQVQImWNkYPj/n4GBgYGJAQoAHhgCAQGXeHsAAAAASUVORK5CYII=')
  ctx.route(re.compile(r'https://(image\.tmdb\.org|covers\.openlibrary\.org)/'),lambda route:route.fulfill(status=200,content_type='image/png',body=image))
  source=(WEB/'index.html').read_text();page.set_content(re.sub(r'<script.*?</script>|<link[^>]+>|<meta http-equiv="Content-Security-Policy"[^>]+>','',source));page.add_style_tag(content=(WEB/'app.css').read_text());page.evaluate(SETUP)
  for f in re.findall(r'<script src="([^"]+)"',source):page.add_script_tag(content=(WEB/f).read_text())
  page.evaluate('()=>{window.originalNow=Date.now;window.downloadCalendar=(name,text)=>{qa.calendar={name,text};};}')
  def reset():
   page.evaluate(RESET);page.evaluate('()=>{recoveryOnly=null;loadIssue=null;libraryEpoch++;Date.now=originalNow;agendaView={kind:"all",favorites:false,unwatched:false,query:"",from:"",to:"",limit:60,hidden:null,specials:null};}')
  def seed_calendar():
   reset();page.evaluate('''()=>{
    state.media=[{id:'s',kind:'show',tmdbId:111,title:'Série de validation agenda',added:true,favorite:true,seasons:[],listIds:[],genres:[],ratings:[],credits:[]},
     {id:'m',kind:'movie',tmdbId:333,title:'Film de validation agenda',added:true,releaseDate:'2026-10-02',listIds:[],genres:[],ratings:[],credits:[],watched:false,plays:0,userNote:'PRIVE_NE_PAS_EXPORTER'},
     {id:'g',kind:'game',title:'Jeu de validation agenda',added:true,releaseDate:'2026-10-03',listIds:[]},
     {id:'b',kind:'book',title:'Livre de validation agenda',added:true,releaseDate:'2026-10-04',listIds:[]},
     {id:'undated',kind:'book',title:'Livre sans date',added:true,listIds:[]},
     {id:'hidden',kind:'movie',title:'Film masqué de QA',hidden:true,added:true,releaseDate:'2026-10-04',listIds:[]}];
    state.episodes=Array.from({length:70},(_,i)=>({id:'e'+i,showId:'s',tmdbId:1000+i,season:1,num:i+1,title:i===0?'SURPRISE_A_MASQUER':'Épisode QA '+(i+1),date:i%2===0?'2026-10-01':'2026-10-05',runtimeMinutes:45,watched:false,plays:0}));
    state.episodes.push({id:'special',showId:'s',season:0,num:1,tmdbId:2000,title:'Spécial QA',date:'2026-10-01',watched:false});
    state.episodes.push({id:'orphan',showId:'removed',season:1,num:1,title:'Episode orphelin',date:'2026-10-01'});
    save(false);screen='calendar';tab=2;agendaView.from='2026-10-01';agendaView.to='2026-10-31';render();
   }''')
  def open_movie():page.evaluate('()=>{overlay={type:"item",id:"m",src:"media",title:"Film QA"};render();}')
  def enter(day,time):page.locator('#viewingDay').fill(day);page.locator('#viewingTime').fill(time)
  seed_calendar()
  check('New agenda is reached through existing navigation',page.locator('.agenda-hero h2').inner_text()=='Agenda de ma bibliothèque')
  check('Agenda starts with bounded 60-event DOM',page.locator('.agenda-event').count()==60)
  check('Agenda counts full filtered library not only visible rows','73 événement' in page.locator('.agenda-summary').inner_text())
  check('Unknown release date reported without fake event','1 sans date' in page.locator('.agenda-summary').inner_text())
  page.locator('#agendaMore').click();check('Load more exposes remaining items without duplicate rows',page.locator('.agenda-event').count()==73)
  page.locator('#agendaKind').select_option('book');check('Book release filter works',page.locator('.agenda-event').count()==1 and 'Livre de validation' in page.locator('.agenda-event').inner_text())
  check('Unknown-book count survives kind filter','1 sans date' in page.locator('.agenda-summary').inner_text())
  page.locator('#agendaKind').select_option('game');check('Game release filter works',page.locator('.agenda-event').count()==1 and 'Jeu de validation' in page.locator('.agenda-event').inner_text())
  page.locator('#agendaKind').select_option('all');page.locator('#agendaFavorites').check();check('Favorite series filters through parent',page.locator('.agenda-summary strong').inner_text()=='70 événement(s)')
  page.locator('#agendaFavorites').uncheck();page.locator('#agendaHidden').check();check('Hidden titles included only after explicit opt-in',page.locator('.agenda-summary strong').inner_text()=='74 événement(s)')
  page.locator('#agendaSpecials').check();check('Special episode inclusion is explicit',page.locator('.agenda-summary strong').inner_text()=='75 événement(s)')
  page.locator('#agendaReset').click();check('Reset restores default hidden/special exclusions',page.locator('.agenda-event').count()==0 or not page.locator('#agendaHidden').is_checked())
  # Restore range after reset deliberately returns session filters to defaults.
  page.locator('#agendaFrom').fill('2026-10-01');page.locator('#agendaTo').fill('2026-10-31')
  page.locator('#agendaKind').select_option('movie');page.locator('#agendaQuery').fill('validation');check('Search does not match orphan or hidden records',page.locator('.agenda-event').count()==1)
  page.locator('#agendaTo').fill('2026-09-30');check('Reversed date range visibly rejected',page.locator('[role=alert]').count()>=1 and page.locator('#agendaExport').is_disabled())
  page.locator('#agendaTo').fill('2026-10-31');page.locator('#agendaKind').select_option('all');page.locator('#agendaQuery').fill('');
  before=page.evaluate('JSON.stringify(state)');network=page.evaluate('qa.calls.length');page.locator('#agendaExport').click()
  check('Export preview covers all events beyond rendered batch','73 événement' in page.locator('#agendaExportDialog').inner_text())
  check('Export preview discloses snapshot and all-day semantics','instantané' in page.locator('#agendaExportDialog').inner_text() and 'journée entière' in page.locator('#agendaExportDialog').inner_text())
  page.locator('#downloadAgenda').click();ics=page.evaluate('qa.calendar.text')
  check('ICS includes all 73 events',ics.count('BEGIN:VEVENT\r\n')==73)
  check('ICS does not contain personal notes or account tokens','PRIVE_NE_PAS_EXPORTER' not in ics and 'accessToken' not in ics)
  check('Calendar creation does not write app state or network',page.evaluate('JSON.stringify(state)')==before and page.evaluate('qa.calls.length')==network)
  check('Downloaded calendar has expected extension',page.evaluate('qa.calendar.name').endswith('.ics'))
  (OUT/'fixture-agenda.ics').write_bytes(ics.encode('utf8'))
  page.locator('#agendaExport').click();page.evaluate('()=>{state.media[1].title="Renommé pendant aperçu";save(false);}')
  old_export=page.evaluate('qa.calendar.text');page.locator('#downloadAgenda').click();check('Outdated export preview refuses silently changing data',page.locator('#agendaExportError').is_visible() and page.evaluate('qa.calendar.text')==old_export);page.locator('#cancelAgendaExport').click()
  seed_calendar();page.evaluate('()=>{state.settings.hideEpisodeSpoilers=true;render();}');page.locator('#agendaQuery').fill('SURPRISE_A_MASQUER');check('Spoiler title not discoverable through calendar search',page.locator('.agenda-event').count()==0)
  page.locator('#agendaQuery').fill('');page.locator('#agendaExport').click();page.locator('#downloadAgenda').click();check('Spoiler mask applied to exported calendar','SURPRISE_A_MASQUER' not in page.evaluate('qa.calendar.text'))
  # Explicit past event and multi-operation queue.
  seed_calendar();page.evaluate('()=>{state.episodes=state.episodes.filter(e=>e.showId!=="removed");save(false);}')
  page.evaluate(AUTH);open_movie();page.locator('#datedMovie').click();check('Dated viewing action accessible in movie detail',page.locator('#viewingDialog').is_visible())
  check('Date editor does not assume a time',page.locator('#viewingTime').input_value()=='')
  check('Date editor declares actual local zone','Europe/Paris' in page.locator('#viewingDialog').inner_text())
  enter('2026-09-10','20:30');page.locator('#confirmViewing').click()
  first=page.evaluate('state.media.find(m=>m.id==="m").lastWatchedAt');check('Past movie viewing gets exact local-time UTC instant',first==1789065000000)
  check('One dated event enqueued with selected timestamp',page.evaluate('syncState.queue.filter(o=>o.field==="history").length')==1)
  page.locator('#datedMovie').click();enter('2026-08-10','20:30');page.locator('#confirmViewing').click()
  check('Adding earlier event does not move latest date backwards',page.evaluate('state.media.find(m=>m.id==="m").lastWatchedAt')==first)
  check('Both known viewings survive with count two',page.evaluate('state.media.find(m=>m.id==="m").plays===2&&state.media.find(m=>m.id==="m").watchHistory.length===2'))
  check('Earlier event gets its own outbox timestamp',page.evaluate('syncState.queue.filter(o=>o.field==="history").map(o=>o.body.movies[0].watched_at)')==['2026-09-10T18:30:00.000Z','2026-08-10T18:30:00.000Z'])
  page.locator('#datedMovie').click();enter('2026-08-10','20:30');page.locator('#confirmViewing').click();check('Duplicate explicit date rejected without new play or outbox item',page.locator('#viewingError').is_visible() and page.evaluate('state.media.find(m=>m.id==="m").plays')==2 and page.evaluate('syncState.queue.length')==2)
  enter('2099-01-01','20:30');page.locator('#confirmViewing').click();check('Future viewing date rejected',page.locator('#viewingError').is_visible() and page.evaluate('syncState.queue.length')==2)
  enter('2026-03-29','02:30');page.locator('#confirmViewing').click();check('Nonexistent spring time rejected','n’existe pas' in page.locator('#viewingPreview').inner_text() and page.evaluate('syncState.queue.length')==2)
  page.evaluate('Date.now=()=>Date.parse("2026-11-06T12:00:00Z")');enter('2026-10-25','02:30');check('Repeated fall time requires a choice',page.locator('#viewingOffset option').count()==3)
  page.locator('#confirmViewing').click();check('Repeated hour cannot be guessed',page.evaluate('syncState.queue.length')==2)
  page.locator('#viewingOffset').select_option(str(1792891800000));page.locator('#confirmViewing').click();check('Chosen second DST occurrence stored exactly',page.evaluate('syncState.queue.at(-1).body.movies[0].watched_at')=='2026-10-25T01:30:00.000Z')
  # Keep the controlled November reference until checking the DST reading; future events are excluded by V0.20.
  page.evaluate('()=>{overlay={type:"history",title:"Historique"};render();}');check('Dated views visible at the controlled November reference',page.locator('.history-entry').count()==3)
  page.evaluate('Date.now=originalNow')
  check('Portable backup round-trip keeps dated events',page.evaluate('fromPortableBackup(toPortableBackup(state)).media.find(m=>m.id==="m").watchHistory.length')==3)
  # A commit failure must restore state and queue while preserving a recovery draft.
  open_movie();page.locator('#datedMovie').click();enter('2026-07-10','20:30');page.evaluate('()=>{window.oldSet=localStorage.setItem;localStorage.setItem=(k,v)=>{if(k===KEY)throw Error("QA quota");oldSet(k,v);};}')
  page.locator('#confirmViewing').click();check('Storage failure displayed instead of success',page.locator('#viewingError').is_visible())
  check('Failed dated write restores latest saved count and queue',page.evaluate('state.media.find(m=>m.id==="m").plays===3&&syncState.queue.length===3'))
  check('Failed dated write retains exportable draft',page.evaluate('storageDraft.media.find(m=>m.id==="m").plays')==4)
  page.evaluate('localStorage.setItem=oldSet');page.locator('#cancelViewing').click()
  # Replacement while a date dialog is open must never update a new record with the same ID.
  seed_calendar();open_movie();page.locator('#datedMovie').click();enter('2026-09-10','20:30');page.evaluate('()=>{libraryEpoch++;state.media[1].title="Imported replacement";save(false);}')
  page.locator('#confirmViewing').click();check('Library replacement invalidates open dated action',page.locator('#viewingError').is_visible() and page.evaluate('state.media[1].watched')==False);page.locator('#cancelViewing').click()
  # UI rendering and keyboard tests with known fake catalogue values.
  seed_calendar();page.evaluate('()=>{overlay={type:"item",id:"s",src:"media",title:"Series"};render();}')
  page.locator('[data-viewing-date]').first.click();check('Episode action opens a dated viewer',page.locator('#viewingDialog').is_visible());enter('2026-09-10','20:30');page.locator('#confirmViewing').click();check('Dated episode changes only selected episode',page.evaluate('state.episodes.filter(e=>e.watched).length')==1)
  check('Dated episode updates parent activity',page.evaluate('state.media[0].lastWatchedAt')==first)
  # Recheck read-only mode: calendar remains exportable, viewing does not mutate.
  seed_calendar();page.evaluate('storageWritable=false');page.locator('[data-agenda-viewing]').first.click();check('Read-only calendar does not allow a new viewing',page.locator('#viewingDialog').count()==0)
  page.locator('#agendaExport').click();check('Read-only library can prepare a calendar export',page.locator('#agendaExportDialog').is_visible());page.locator('#cancelAgendaExport').click();page.evaluate('storageWritable=true')
  for w,h in [(390,844),(768,1024),(1360,1000)]:
   page.set_viewport_size({'width':w,'height':h});page.wait_for_timeout(250)
   page.wait_for_function('parseFloat(getComputedStyle(document.querySelector("#toast")).opacity)<0.01')
   check('Agenda fits viewport '+str(w),page.evaluate('document.documentElement.scrollWidth<=innerWidth+1'))
   page.evaluate('()=>{document.activeElement?.blur();window.scrollTo(0,0);document.querySelector("#appShell").scrollTop=0;}');page.wait_for_timeout(100)
   page.screenshot(path=str(OUT/f'fixture-agenda-{w}.png'))
   page.locator('[data-agenda-viewing]').first.click();check('Viewing dialog fits viewport '+str(w),page.locator('#viewingDialog').evaluate('(e)=>e.getBoundingClientRect().width<=innerWidth&&e.scrollWidth<=e.clientWidth+1'))
   page.screenshot(path=str(OUT/f'fixture-viewing-{w}.png'));page.keyboard.press('Escape');page.locator('#viewingDialog').wait_for(state='detached')
   check('Escape closes dated dialog '+str(w),page.locator('#viewingDialog').count()==0)
  check('Migration retains explicitly unknown zero runtime',page.evaluate('migrate({media:[{id:"s",kind:"show",added:true}],episodes:[{id:"e",showId:"s",season:1,num:1,title:"Manual",runtimeMinutes:0}]}).episodes[0].runtimeMinutes')==0)
  # Exercise dated POST bodies and an ambiguous HTTP200 acknowledgement against the controlled provider.
  reset();page.evaluate(AUTH);page.evaluate('()=>{state.media=[{id:"m",kind:"movie",tmdbId:333,title:"Dated send QA",added:true,watched:false,plays:0}];save(false);Object.assign(state.media[0],FGActivity.addViewing(state.media[0],1789065000000));save();Object.assign(state.media[0],FGActivity.addViewing(state.media[0],1786386600000));save();}')
  page.evaluate('()=>traktSync()')
  check('Dated POSTs use two exact newly added timestamps',page.evaluate('qa.calls.filter(c=>c.path==="/sync/history"&&c.method==="POST").map(c=>c.body.movies[0].watched_at)')==['2026-09-10T18:30:00.000Z','2026-08-10T18:30:00.000Z'])
  check('Both acknowledged dates removed from outbox',page.evaluate('syncState.queue.length')==0)
  reset();page.evaluate(AUTH);page.evaluate('()=>{state.media=[{id:"m",kind:"movie",tmdbId:333,title:"Ambiguous ack QA",added:true,watched:false,plays:0}];save(false);Object.assign(state.media[0],FGActivity.addViewing(state.media[0],1789065000000));save();window.normalFetch=window.fetch;window.fetch=async(u,o={})=>String(u).includes("/sync/history")&&o.method==="POST"?new Response("{}",{status:200,headers:{"Content-Type":"application/json"}}):normalFetch(u,o);}')
  page.evaluate('()=>traktSync()');check('Empty HTTP200 history response remains uncertain',page.evaluate('syncState.queue.length===1&&syncState.queue[0].uncertain'))
  page.evaluate('()=>{window.fetch=normalFetch;}')
  check('No uncaught JavaScript errors in V0.16 scenarios',not errors)
  (OUT/'browser-v016.json').write_text(json.dumps({'version':'0.16.0','scope':'real DOM/CSS/scripts, fixtures for providers/images/storage; no installed PWA or real Trakt/calendar account','passed':len(checks),'checks':checks,'errors':errors},ensure_ascii=False,indent=2));print('V0.16 Chromium:',len(checks),'assertions passed');b.close()
if __name__=='__main__':
 try:run()
 except Exception:
  (OUT/'browser-v016-failure.json').write_text(json.dumps({'passed':len(checks),'checks':checks,'traceback':traceback.format_exc()},ensure_ascii=False,indent=2));raise
