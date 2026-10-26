"""V0.18 import review in real Chromium DOM/CSS. Controlled provider and storage fixtures.
Files exercise the real browser File/ArrayBuffer/TextDecoder path. No real-origin persistence,
Trakt account, SDK Android or installable PWA claim.
"""
from pathlib import Path
from playwright.sync_api import sync_playwright
from browser_harness import SETUP,RESET,AUTH,ROOT,WEB
import re,json,traceback,base64
OUT=ROOT/'tests/results/v018';OUT.mkdir(parents=True,exist_ok=True);checks=[]
def check(name,condition=True):
 if not condition:raise AssertionError(name)
 checks.append({'name':name,'passed':True});print('PASS',name,flush=True)
def run():
 with sync_playwright() as p:
  browser=p.chromium.launch(executable_path='/usr/bin/chromium',headless=True,args=['--no-sandbox']);ctx=browser.new_context(viewport={'width':1360,'height':1000},locale='fr-FR',timezone_id='Europe/Paris');page=ctx.new_page();page.set_default_timeout(6000);errors=[]
  page.on('pageerror',lambda e:errors.append(str(e)));page.on('dialog',lambda d:d.accept())
  png=base64.b64decode('iVBORw0KGgoAAAANSUhEUgAAAAIAAAACCAYAAABytg0kAAAAFElEQVQImWNkYPj/n4GBgYGJAQoAHhgCAQGXeHsAAAAASUVORK5CYII=')
  ctx.route(re.compile(r'https://(image\.tmdb\.org|covers\.openlibrary\.org)/'),lambda r:r.fulfill(status=200,content_type='image/png',body=png))
  html=(WEB/'index.html').read_text();page.set_content(re.sub(r'<script.*?</script>|<link[^>]+>|<meta http-equiv="Content-Security-Policy"[^>]+>','',html));page.add_style_tag(content=(WEB/'app.css').read_text());page.evaluate(SETUP)
  for f in re.findall(r'<script src="([^"]+)"',html):page.add_script_tag(content=(WEB/f).read_text())
  page.wait_for_timeout(40)
  def reset():
   page.evaluate(RESET);page.evaluate('''()=>{importReadGeneration++;libraryEpoch++;recoveryOnly=null;loadIssue=null;window.downloadJson=(name,data)=>{qa.download={name,data}};state.media=[{id:'old',kind:'movie',title:'Film conservé avant import',source:'local',added:true,watched:true,plays:2,watchHistory:[1700000000000,1700000100000],lastWatchedAt:1700000100000,userNote:'Note privée à ne pas exporter dans le bilan',rating:8,listIds:[]}];state.settings.language='fr-FR';state.settings.tmdbKey='PRIVATE-KEY-QA';save(false);screen='more';overlay=null;render();}''')
  def payload(n=2):
   return {'schema':8,'media':[{'id':'new-'+str(i),'kind':'GAME' if i%2 else 'BOOK','title':'Nouvelle fiche QA '+str(i),'added':True,'source':'local','listIds':[]} for i in range(n)],'episodes':[],'lists':[],'suggestions':[],'customFranchises':[],'preferences':{'language':'en-US','darkTheme':True,'catalogAutoRefresh':False,'autoBackupEnabled':False},'gaming':{'version':1,'accounts':[],'sets':[]}}
  def choose(data=None,name='franchiseguide.json'):
   data=payload() if data is None else data
   page.locator('#importFg').set_input_files({'name':name,'mimeType':'application/json','buffer':json.dumps(data,ensure_ascii=False).encode()});page.locator('#importReviewDialog').wait_for()
  def cancel():
   page.locator('#cancelImportReview').click();page.locator('#importReviewDialog').wait_for(state='detached')
  def accept():
   page.locator('#importReviewAcknowledge').check();page.locator('#commitImportReview').click()
  reset();page.locator('#import').click();check('Import center reachable from More',page.locator('#reviewChooseFg').is_visible() and page.locator('#reviewChooseSg').is_visible())
  before=page.evaluate('localStorage.getItem(KEY)');backups=page.evaluate('localStorage.getItem(BACKUP_KEY)');choose()
  check('File selection opens review instead of replacing data',page.evaluate('state.media[0].id')=='old')
  check('Preparing review performs no library write',page.evaluate('localStorage.getItem(KEY)')==before)
  check('Preparing review creates no safety snapshot',page.evaluate('localStorage.getItem(BACKUP_KEY)')==backups)
  check('Confirmation disabled until explicit acknowledgement',page.locator('#commitImportReview').is_disabled())
  check('Counter comparison includes all four media categories',all(x in page.locator('.import-metrics').inner_text() for x in ['Séries','Films','Jeux','Livres']))
  check('Missing viewing dates and notes clearly disclosed','2 date(s)' in page.locator('#importAttention').inner_text() and '1 note(s) privée(s)' in page.locator('#importAttention').inner_text())
  check('Privacy note content not rendered in preview','Note privée à ne pas' not in page.locator('#importReviewDialog').inner_text())
  check('Preferences preserved by default',not page.locator('#importReviewPreferences').is_checked())
  page.locator('#exportImportReview').click();report=page.evaluate('qa.download');check('Exported report contains counters not library contents',report['data']['after']['titles']==2 and 'media' not in report['data'])
  check('Report does not disclose credentials or private note',not any(x in json.dumps(report) for x in ['PRIVATE-KEY-QA','Note privée à ne pas']))
  check('Preview makes no provider request',page.evaluate('qa.calls.length')==0)
  cancel();check('Cancel keeps saved bytes exactly unchanged',page.evaluate('localStorage.getItem(KEY)')==before)
  choose();page.keyboard.press('Escape');page.locator('#importReviewDialog').wait_for(state='detached');check('Escape cancels preview without writes',page.evaluate('localStorage.getItem(KEY)')==before)
  choose();accept();page.locator('#importReviewDialog').wait_for(state='detached')
  check('Acknowledged replacement applies all new titles',page.evaluate('state.media.length===2&&state.media.every(m=>m.id.startsWith("new-"))'))
  check('Replacement creates safety snapshot of original library',page.evaluate('backups().some(b=>b.state.media[0]?.id==="old")'))
  check('Current language preserved unless chosen',page.evaluate('state.settings.language')=='fr-FR')
  check('Current API credentials retained',page.evaluate('state.settings.tmdbKey')=='PRIVATE-KEY-QA')
  check('Successful confirmation reports saved result','enregistré' in page.locator('#reviewImportStatus').inner_text())
  reset();choose();page.locator('#importReviewAcknowledge').check();page.locator('#importReviewPreferences').check();check('Changing preferences option requires renewed acknowledgement',page.locator('#commitImportReview').is_disabled() and not page.locator('#importReviewAcknowledge').is_checked());accept();page.locator('#importReviewDialog').wait_for(state='detached')
  check('Explicit public preference import applies language and theme',page.evaluate('state.settings.language==="en-US"&&state.settings.dark'))
  check('Preference import still preserves keys',page.evaluate('state.settings.tmdbKey')=='PRIVATE-KEY-QA')
  # Reject stale data/settings/revision without disturbing the latest write.
  for label,js in [('title',"state.media[0].title='Modification récente';save(false);"),('note',"state.media[0].userNote='Nouvelle note';save(false);"),('settings',"state.settings.region='US';save(false);"),('generation','libraryEpoch++;')]:
   reset();choose();page.evaluate('()=>{'+js+'}');live=page.evaluate('localStorage.getItem(KEY)');accept();check('Stale '+label+' review refused',page.locator('#importReviewError').is_visible() and page.evaluate('localStorage.getItem(KEY)')==live);cancel()
  reset();choose();page.evaluate('()=>{const d=JSON.parse(localStorage.getItem(KEY));d._revision++;localStorage.setItem(KEY,JSON.stringify(d));}');live=page.evaluate('localStorage.getItem(KEY)');accept();check('Other-window revision prevents overwrite',page.locator('#importReviewError').is_visible() and page.evaluate('localStorage.getItem(KEY)')==live);cancel()
  reset();choose();page.evaluate('traktSyncing=true');accept();check('Active Trakt sync blocks commit',page.locator('#importReviewError').is_visible() and page.evaluate('state.media[0].id')=='old');check('Sync-blocked commit creates no safety snapshot',page.evaluate('backups().length')==0);page.evaluate('traktSyncing=false');cancel()
  reset();choose();page.evaluate('storageWritable=false');accept();check('Read-only blocks confirmed import',page.locator('#importReviewError').is_visible() and page.evaluate('state.media[0].id')=='old');cancel();page.evaluate('storageWritable=true')
  reset();choose();page.evaluate('''()=>{window.originalSet=localStorage.setItem;localStorage.setItem=(k,v)=>{if(k===BACKUP_KEY)throw Error('Backup quota QA');return originalSet(k,v)}}''');accept();check('Safety backup refusal prevents replacement',page.locator('#importReviewError').is_visible() and page.evaluate('state.media[0].id')=='old');page.evaluate('localStorage.setItem=originalSet');cancel()
  reset();choose();page.evaluate('''()=>{window.originalSet=localStorage.setItem;localStorage.setItem=(k,v)=>{if(k===KEY)throw Error('Main quota QA');return originalSet(k,v)}}''');accept();check('Main write refusal restores saved library',page.locator('#importReviewError').is_visible() and page.evaluate('state.media[0].id')=='old');check('Failed import retains recoverable draft',page.evaluate('storageDraft.media.length===2&&storageDraft.media[0].id==="new-0"'));page.evaluate('localStorage.setItem=originalSet');cancel()
  reset();page.evaluate(AUTH);page.evaluate('''()=>{syncState.queue=[{id:'pending',field:'watchlist',key:'movie:333',body:{movies:[]},path:'/sync/watchlist',title:'Ancienne action'}];syncStore();}''');choose();accept();page.locator('#importReviewDialog').wait_for(state='detached')
  check('Confirmed import preserves but holds pending Trakt actions',page.evaluate('syncState.queue.length===1&&!!syncState.queue[0].held'))
  check('Held actions persisted with replaced library',page.evaluate('!!JSON.parse(localStorage.getItem(KEY))._syncState.queue[0].held'))
  check('Preview and commit do not POST to Trakt',page.evaluate('qa.calls.every(c=>c.method!=="POST")'))
  # Game account references are not authentication or opt-in.
  reset();p=payload();p['gaming']['accounts']=[{'id':'account','provider':'steam','externalId':'76561198000000001','label':'Compte QA','autoSync':True,'autoImport':True}];choose(p);accept();page.locator('#importReviewDialog').wait_for(state='detached')
  check('Imported account automatics disabled',page.evaluate('state.gaming.accounts.every(a=>!a.autoSync&&!a.autoImport)'))
  # The full File API path must reject malformed bytes before review.
  reset();before=page.evaluate('localStorage.getItem(KEY)')
  for label,raw in [('malformed JSON',b'{"media":['),('invalid UTF-8',b'{"media":[],"title":"\xff"}')]:
   page.locator('#importFg').set_input_files({'name':'invalid.json','mimeType':'application/json','buffer':raw});page.wait_for_function('!document.querySelector("#readingImportDialog")');check('Reject '+label+' without opening confirmation',page.locator('#importReviewDialog').count()==0 and page.evaluate('localStorage.getItem(KEY)')==before)
  page.evaluate('''async()=>{const files=[{name:'big.json',size:FGImport.MAX_FILE+1,arrayBuffer:async()=>{throw Error('must not read')}}];await readImportSelection('portable',files);}''');check('Oversize file rejected before allocating buffer',page.locator('#importReviewDialog').count()==0 and page.evaluate('localStorage.getItem(KEY)')==before)
  # Real arrayBuffer promise is delayed to exercise cancellation and replacement guards.
  page.evaluate('''()=>{window.slowImport=readImportSelection('portable',[{name:'pending.json',size:12,arrayBuffer:()=>new Promise(r=>qa.releaseFile=r)}]);}''');page.locator('#readingImportDialog').wait_for();page.locator('#cancelReadImport').click();page.locator('#readingImportDialog').wait_for(state='detached');page.evaluate('()=>qa.releaseFile(new TextEncoder().encode("{\\"media\\":[]}").buffer)');page.evaluate('async()=>await slowImport');check('Cancellation during read never opens late review',page.locator('#importReviewDialog').count()==0 and page.evaluate('localStorage.getItem(KEY)')==before)
  page.evaluate('''()=>{window.slowImport=readImportSelection('portable',[{name:'pending.json',size:12,arrayBuffer:()=>new Promise(r=>qa.releaseFile=r)}]);}''');page.locator('#readingImportDialog').wait_for();page.evaluate('()=>{state.media[0].favorite=true;save(false);qa.releaseFile(new TextEncoder().encode("{\\"media\\":[]}").buffer);}');page.evaluate('async()=>await slowImport');check('Change during file read prevents late replacement preview',page.locator('#importReviewDialog').count()==0 and page.evaluate('state.media[0].favorite'))
  # SeriesGuide: all files staged; list file first and missing refs disclosed.
  reset();show=[{'tmdb_id':111,'title':'Série fusion QA','language':'fr-FR','seasons':[{'season':1,'episodes':[{'episode':1,'tmdb_id':11101,'watched':True,'plays':1}]}]}];lists=[{'name':'Liste QA','list_id':'q','items':[{'type':'tmdb-show','externalId':'111'},{'type':'tmdb-show','externalId':'999'}]}]
  files=[{'name':'seriesguide-lists-backup.json','mimeType':'application/json','buffer':json.dumps(lists).encode()},{'name':'seriesguide-shows-backup.json','mimeType':'application/json','buffer':json.dumps(show).encode()}]
  page.locator('#importSg').set_input_files(files);page.locator('#importReviewDialog').wait_for();check('SeriesGuide review displays unresolved reference warning','1 référence(s) ignorée(s)' in page.locator('#importReviewDialog').inner_text());check('SeriesGuide staging has not inserted media',page.evaluate('state.media.length')==1);accept();page.locator('#importReviewDialog').wait_for(state='detached')
  check('SeriesGuide confirmed fusion keeps old media',page.evaluate('state.media.length===2&&state.media.some(m=>m.id==="old")'))
  check('SeriesGuide list resolves even when selected first',page.evaluate('state.media.find(m=>m.kind==="show").listIds.length')==1)
  page.evaluate('()=>{state.episodes[0].runtimeMinutes=0;state.episodes[0].userNote="Épisode privé";state.episodes[0].rating=9;save(false);}')
  page.locator('#importSg').set_input_files(files);page.locator('#importReviewDialog').wait_for();accept();page.locator('#importReviewDialog').wait_for(state='detached')
  check('Repeat SeriesGuide import creates no duplicates',page.evaluate('state.media.length===2&&state.episodes.length===1&&state.lists.length===2'))
  check('SeriesGuide keeps explicit zero duration and private note',page.evaluate('state.episodes[0].runtimeMinutes===0&&state.episodes[0].userNote==="Épisode privé"&&state.episodes[0].rating===9'))
  reset();before=page.evaluate('localStorage.getItem(KEY)');page.locator('#importSg').set_input_files([files[1],{'name':'bad-lists.json','mimeType':'application/json','buffer':b'{bad'}]);page.wait_for_function('!document.querySelector("#readingImportDialog")');check('Invalid second file prevents entire staged import',page.evaluate('localStorage.getItem(KEY)')==before and page.locator('#importReviewDialog').count()==0)
  reset();iso=[{'tmdb_id':68421,'title':'Série ISO de QA','language':'en-US','first_aired':'2018-02-02T08:00:00Z','runtime':50,'seasons':[{'season':1,'episodes':[{'tmdb_id':991306,'episode':1,'first_aired':1517558400000,'watched':True,'plays':1}]}]}]
  page.locator('#importSg').set_input_files({'name':'seriesguide-shows-backup.json','mimeType':'application/json','buffer':json.dumps(iso).encode()});page.locator('#importReviewDialog').wait_for();accept();page.locator('#importReviewDialog').wait_for(state='detached')
  check('Documented ISO show timestamp and epoch episode date both import',page.evaluate('state.media.find(m=>m.kind==="show").releaseDate==="2018-02-02"&&state.episodes[0].date==="2018-02-02"'))
  # Local and IndexedDB restore previews refer to captured snapshots, never a rotating index.
  reset();page.evaluate('()=>{createBackup("Source QA");state.media[0].title="Current changed";save(false);render();}');page.locator('[data-backup]').first.click();page.locator('#importReviewDialog').wait_for();check('Snapshot restore also uses review',page.evaluate('state.media[0].title')=='Current changed');cancel()
  page.evaluate('()=>reviewDurableRestore({id:"qa-idb",state:toPortableBackup(savedState)})');page.locator('#importReviewDialog').wait_for();page.locator('#importReviewDialog details summary').first.click();check('IndexedDB restore enters same reviewed path','IndexedDB' in page.locator('#importReviewDialog').inner_text());cancel()
  # Large reviewed file does not build all rows; search evaluates them all.
  reset();large=payload(160);large['media'][159]['title']='Dernier titre recherché';large['media'][3]['title']='<svg onload=window.importInjected=true>'
  choose(large,name='<img src=x onerror=alert(1)>.json');page.locator('#importTitles summary').click();page.wait_for_selector('.import-title-row')
  check('Review renders only first50 title rows',page.locator('.import-title-row').count()==50)
  page.locator('#importTitleQuery').fill('dernier');check('Search finds title outside first50',page.locator('.import-title-row').count()==1 and 'Dernier titre' in page.locator('#importTitleRows').inner_text())
  page.locator('#importTitleQuery').fill('');page.locator('#importTitleChange').select_option('removed');check('Removed filter includes absent current title',page.locator('.import-title-row').count()==1 and 'Film conservé' in page.locator('#importTitleRows').inner_text())
  page.locator('#importTitleChange').select_option('all');page.locator('#importTitleQuery').fill('svg');check('Untrusted title rendered as inert text',page.locator('#importReviewDialog svg').count()==0 and '<svg onload=' in page.locator('#importTitleRows').inner_text());check('Malicious file name not interpreted as HTML',page.locator('#importReviewDialog img').count()==0 and page.evaluate('window.importInjected===undefined'))
  cancel()
  for w,h in [(390,844),(768,1024),(1360,1000)]:
   reset();page.set_viewport_size({'width':w,'height':h});choose();page.wait_for_timeout(100);page.screenshot(path=str(OUT/f'fixture-import-review-{w}.png'))
   check('Review fits viewport '+str(w),page.locator('#importReviewDialog').evaluate('(d)=>d.getBoundingClientRect().width<=innerWidth&&d.scrollWidth<=d.clientWidth+1'))
   page.keyboard.press('Escape');page.locator('#importReviewDialog').wait_for(state='detached');check('Escape review preserves data '+str(w),page.evaluate('state.media[0].id')=='old')
  check('No uncaught JavaScript errors in import scenarios',not errors)
  result={'version':'0.18.0','mode':'real scripts/DOM/CSS and File API with controlled storage/providers; not persistent-origin or Android runtime','passed':len(checks),'checks':checks,'errors':errors};(OUT/'browser-v018.json').write_text(json.dumps(result,ensure_ascii=False,indent=2));print('V0.18 Chromium:',len(checks),'assertions passed');browser.close()
if __name__=='__main__':
 try:run()
 except Exception:
  (OUT/'browser-v018-failure.json').write_text(json.dumps({'passed':len(checks),'checks':checks,'traceback':traceback.format_exc()},ensure_ascii=False,indent=2));raise
