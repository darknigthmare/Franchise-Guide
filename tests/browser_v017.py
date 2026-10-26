"""V0.17 real DOM/CSS/application scripts with controlled storage/API/image fixtures.
No live provider account, persistent-origin navigation, installed PWA or Android runtime.
"""
from playwright.sync_api import sync_playwright
from browser_harness import SETUP,RESET,AUTH,ROOT,WEB
import re,json,base64,traceback
OUT=ROOT/'tests/results/v017';OUT.mkdir(parents=True,exist_ok=True)
checks=[]
def check(name,value=True):
 if not value:raise AssertionError(name)
 checks.append({'name':name,'passed':True});print('PASS',name,flush=True)
def run():
 with sync_playwright() as p:
  browser=p.chromium.launch(executable_path='/usr/bin/chromium',headless=True,args=['--no-sandbox']);context=browser.new_context(viewport={'width':1360,'height':1000},locale='fr-FR',timezone_id='Europe/Paris');page=context.new_page();page.set_default_timeout(5500);errors=[]
  page.on('pageerror',lambda e:errors.append(str(e)));page.on('dialog',lambda d:d.accept())
  png=base64.b64decode('iVBORw0KGgoAAAANSUhEUgAAAAIAAAACCAYAAABytg0kAAAAFElEQVQImWNkYPj/n4GBgYGJAQoAHhgCAQGXeHsAAAAASUVORK5CYII=')
  context.route(re.compile(r'https://(image\.tmdb\.org|covers\.openlibrary\.org)/'),lambda r:r.fulfill(status=200,content_type='image/png',body=png))
  html=(WEB/'index.html').read_text();page.set_content(re.sub(r'<script.*?</script>|<link[^>]+>|<meta http-equiv="Content-Security-Policy"[^>]+>','',html));page.add_style_tag(content=(WEB/'app.css').read_text());page.evaluate(SETUP)
  for src in re.findall(r'<script src="([^"]+)"',html):page.add_script_tag(content=(WEB/src).read_text())
  def reset():
   page.evaluate(RESET);page.evaluate('''()=>{recoveryOnly=null;loadIssue=null;libraryEpoch++;lastLocalBatch=null;organizeView={kind:'all',query:'',visibility:'visible',selected:new Set(),limit:80,action:'favorite-on',listId:null};nextUpView={kind:'all',section:'ready',query:'',favoritesOnly:false,includeHidden:false,includeSpecials:null,maxMinutes:0,limit:60};}''')
  def seed_queue():
   reset();page.evaluate('''()=>{
    state.media=[{id:'s',tmdbId:111,kind:'show',title:'Série fictive à poursuivre',added:true,catalogComplete:true,episodeCount:2,seasons:[{number:1,title:'Saison1'}],listIds:[],posterUrl:'https://image.tmdb.org/t/p/w500/qa-poster.png'},
     {id:'future',kind:'show',title:'Épisode futur de QA',added:true,catalogComplete:true,listIds:[]},
     {id:'unknown',kind:'show',title:'Numérotation inconnue QA',added:true,catalogComplete:false,listIds:[]},
     {id:'empty',kind:'show',title:'Fiche à charger QA',added:true,catalogComplete:false,listIds:[]},
     {id:'m',tmdbId:333,kind:'movie',title:'Film fictif à voir',added:true,watched:false,releaseDate:'2025-01-01',runtimeMinutes:100,listIds:[]},
     {id:'b',kind:'book',title:'Livre fictif commencé',added:true,started:true,progressCurrent:45,progressTotal:230,progressUnit:'pages',listIds:[]},
     {id:'g',kind:'game',title:'Jeu fictif à commencer',added:true,progressCurrent:0,progressTotal:100,progressUnit:'%',listIds:[]},
     {id:'hidden',kind:'movie',title:'Film masqué QA',added:true,hidden:true,favorite:true,releaseDate:'2025-01-01',runtimeMinutes:60,listIds:[]}];
    state.episodes=[{id:'sp',showId:'s',tmdbId:9000,season:0,num:1,title:'Spécial QA',date:'2025-01-01',watched:false,runtimeMinutes:20},
     {id:'e1',showId:'s',tmdbId:9001,season:1,num:1,title:'SECRET_QA_NE_PAS_REVELER',date:'2025-01-01',watched:false,plays:0,runtimeMinutes:45},
     {id:'e2',showId:'s',tmdbId:9002,season:1,num:2,title:'Épisode2 de QA',date:'2025-01-02',watched:false,plays:0,runtimeMinutes:50},
     {id:'f1',showId:'future',season:1,num:1,title:'Épisode ancien dans ordre',date:'2999-01-01',watched:false,runtimeMinutes:30},
     {id:'f2',showId:'future',season:1,num:2,title:'Épisode suivant déjà diffusé',date:'2025-01-01',watched:false,runtimeMinutes:30},
     {id:'u1',showId:'unknown',season:0,num:0,seasonUnknown:true,numberUnknown:true,title:'Inconnu',date:null,watched:false}];
    state.settings.hideEpisodeSpoilers=true;save(false);screen='more';render();
   }''')
  seed_queue();page.locator('[data-quick="next-up"]').click()
  check('Next-up reachable from More navigation',page.locator('#nextRows').count()==1)
  check('Next-up includes four ready media types',page.locator('.queue-row').count()==4)
  check('Only first eligible episode offered for a show',page.locator('[data-next-watch="s"]').get_attribute('data-next-episode')=='e1')
  check('Unwatched title hidden from visible queue','SECRET_QA' not in page.locator('#nextRows').inner_text())
  page.locator('#nextQuery').fill('SECRET_QA');check('Hidden title cannot leak through queue search',page.locator('.queue-row').count()==0)
  page.locator('#nextQuery').fill('S01E01');check('Episode code searchable without spoiler',page.locator('.queue-row').count()==1)
  page.locator('#nextQuery').fill('');page.locator('#nextSection').select_option('future');check('Future first episode not replaced by later aired episode',page.locator('.queue-row').count()==1 and 'S01E01' in page.locator('.queue-row').inner_text() and page.locator('[data-next-watch]').count()==0)
  page.locator('#nextSection').select_option('unknown');check('Missing episode metadata and numbering surfaced',page.locator('.queue-row').count()==2)
  page.locator('#nextReset').click();page.locator('#nextKind').select_option('book');check('Book progress shown without made-up session duration','45/230 pages' in page.locator('#nextRows').inner_text())
  page.locator('#nextDuration').select_option('60');check('Book excluded by a movie/episode time budget',page.locator('.queue-row').count()==0)
  page.locator('#nextKind').select_option('all');check('Duration filter uses known runtime only',page.locator('.queue-row').count()==1)
  page.locator('#nextDuration').select_option('0');page.locator('#nextHidden').check();check('Hidden title included only after opt-in',page.locator('.queue-row').count()==5)
  page.locator('#nextFavorites').check();check('Favourite queue filters full candidate list',page.locator('.queue-row').count()==1 and 'Film masqué' in page.locator('#nextRows').inner_text())
  page.locator('#nextReset').click();page.locator('#nextSpecials').check();check('Specials explicitly change next episode',page.locator('[data-next-watch="s"]').get_attribute('data-next-episode')=='sp')
  page.locator('#nextSpecials').uncheck();page.evaluate(AUTH)
  page.locator('[data-next-watch="s"]').click();check('Next-up quick action changes only selected episode',page.evaluate('state.episodes.filter(e=>e.watched).map(e=>e.id)')==['e1'])
  check('Exactly one new Trakt history operation for selected date',page.evaluate('syncState.queue.length===1&&syncState.queue[0].body.episodes[0].ids.tmdb===9001'))
  check('Next episode recalculated after action',page.locator('[data-next-watch="s"]').get_attribute('data-next-episode')=='e2')
  page.evaluate('()=>{qa.oldNextButton=document.querySelector(\'[data-next-watch="s"]\');state.episodes.find(e=>e.id==="e2").watched=true;save(false);qa.oldNextButton.onclick();}')
  check('Stale button does not replay already-watched next episode',page.evaluate('syncState.queue.length')==1)
  page.locator('#nextSection').select_option('caught-up');check('Caught-up distinguished from catalog completed state',page.locator('.queue-row').count()==1 and 'épisodes chargés' in page.locator('#nextRows').inner_text())
  seed_queue();page.evaluate("()=>{screen='shows';overlay=null;render();}");page.locator('[data-act="done"][data-id="future"]').click()
  check('Old library shortcut no longer jumps over future earlier episode',page.evaluate('state.episodes.filter(e=>e.showId==="future").every(e=>!e.watched)'))
  page.evaluate('()=>{state.episodes.find(e=>e.id==="f1").date=null;save(false);render();}');page.locator('[data-act="done"][data-id="future"]').click()
  check('Old library shortcut no longer jumps over unknown earlier date',page.evaluate('state.episodes.filter(e=>e.showId==="future").every(e=>!e.watched)'))
  page.locator('[data-next-kind="show"]').click();check('Per-category next-up button scopes the queue',page.locator('#nextKind').input_value()=='show')
  page.evaluate('()=>{overlay=null;screen="movies";render();}');page.locator('[data-organize-kind="movie"]').click();check('Per-category organize button scopes selection',page.locator('#orgKind').input_value()=='movie')
  def seed_org():
   reset();page.evaluate('''()=>{
    state.media=Array.from({length:84},(_,i)=>({id:'m'+i,kind:['show','movie','game','book'][i%4],tmdbId:i+100,title:'Titre de QA '+String(i).padStart(3,'0'),added:true,watched:false,plays:0,listIds:i===0?['old']:[],favorite:false,userNote:'Note QA '+i}));
    state.lists=[{id:'old',name:'Avant',sortMode:'title',ascending:true},{id:'new',name:'Choix de QA',sortMode:'title',ascending:true},{id:'other',name:'Après',sortMode:'title',ascending:true}];
    save(false);screen='more';render();
   }''');page.locator('[data-quick="organize"]').click()
  seed_org();check('Organizer accessible from More',page.locator('#orgRows').count()==1)
  check('Organizer DOM initially limited to 80 titles',page.locator('.organize-row').count()==80)
  page.locator('#orgSelectAll').click();check('Select all includes rows not yet rendered',page.evaluate('organizeView.selected.size')==84)
  page.locator('#orgMore').click();check('Additional rows retain selection',page.locator('.organize-row').count()==84 and page.locator('[data-org-id]:checked').count()==84)
  page.locator('#orgQuery').fill('083');check('Search covers title beyond first page',page.locator('.organize-row').count()==1)
  check('Selection retained outside current filter',page.evaluate('organizeView.selected.size')==84)
  page.evaluate(AUTH);calls=page.evaluate('qa.calls.length');page.locator('#orgPreview').click()
  check('Batch preview covers complete selection','84 modification' in page.locator('#localBatchDialog').inner_text())
  check('Preview itself has not changed library',page.evaluate('state.media.every(m=>!m.favorite)'))
  page.evaluate('()=>{window.orgSaves=0;window.originalSet=localStorage.setItem;localStorage.setItem=(k,v)=>{if(k===KEY)orgSaves++;return originalSet(k,v)};}')
  page.locator('#applyLocalBatch').click();page.locator('#localBatchDialog').wait_for(state='detached')
  check('Batch is one main library write',page.evaluate('orgSaves')==1)
  check('All selected titles modified, not just visible row',page.evaluate('state.media.every(m=>m.favorite)'))
  check('No Trakt or provider call from a local batch',page.evaluate('qa.calls.length')==calls and page.evaluate('syncState.queue.length')==0)
  check('Prior tracking and notes preserved',page.evaluate('state.media.every((m,i)=>!m.watched&&m.plays===0&&m.userNote==="Note QA "+i)'))
  page.evaluate('localStorage.setItem=originalSet');check('Safety snapshot exists before batch',page.evaluate('backups().length')>=1)
  page.evaluate('()=>{state.media[0].userNote="Plus récent";state.media[0].watchHistory=[1700000000000];state.media[0].lastWatchedAt=1700000000000;state.media[0].plays=1;state.media[0].watched=true;save(false);}')
  page.locator('#orgUndo').click();check('Undo reverts batch field',page.evaluate('state.media.every(m=>!m.favorite)'))
  check('Undo preserves subsequent history and private note',page.evaluate('state.media[0].userNote==="Plus récent"&&state.media[0].plays===1&&state.media[0].watchHistory[0]===1700000000000'))
  check('Successful undo cannot be repeated',page.locator('#orgUndo').is_disabled())
  # Membership-level patches never erase an unrelated list addition.
  page.locator('#orgClear').click();page.locator('#orgQuery').fill('000');page.locator('[data-org-id="m0"]').check();page.locator('#orgAction').select_option('list-add');page.locator('#orgList').select_option('new');page.locator('#orgPreview').click();page.locator('#applyLocalBatch').click();page.locator('#localBatchDialog').wait_for(state='detached')
  check('List batch adds only desired membership',page.evaluate('state.media[0].listIds')==['old','new'])
  page.evaluate('()=>{state.media[0].listIds.push("other");save(false);}');page.locator('#orgUndo').click();check('Undo preserves newer unrelated list membership',page.evaluate('state.media[0].listIds')==['old','other'])
  page.locator('#orgAction').select_option('hide');page.locator('#orgPreview').click();page.locator('#applyLocalBatch').click();page.locator('#localBatchDialog').wait_for(state='detached');check('Hidden title disappears from visible filter but is retained',page.locator('.organize-row').count()==0 and page.evaluate('state.media.length===84&&state.media[0].hidden'))
  page.locator('#orgVisibility').select_option('hidden');check('Hidden titles recoverable without data removal',page.locator('.organize-row').count()==1)
  page.evaluate('()=>{state.media[0].hidden=false;save(false);}');page.locator('#orgUndo').click();check('Undo refuses conflicting field value instead of overwriting',page.evaluate('state.media[0].hidden===false&&lastLocalBatch!==null'))
  # Stale preview: individual field changed, list removed, or replacement generation.
  seed_org();page.locator('[data-org-id="m0"]').check();page.locator('#orgPreview').click();page.evaluate('()=>{state.media[0].favorite=true;save(false);}')
  page.locator('#applyLocalBatch').click();check('Changed target field blocks stale preview',page.locator('#batchError').is_visible() and page.evaluate('lastLocalBatch===null'))
  page.keyboard.press('Escape');page.locator('#localBatchDialog').wait_for(state='detached')
  seed_org();page.locator('[data-org-id="m0"]').check();page.locator('#orgAction').select_option('list-add');page.locator('#orgList').select_option('new');page.locator('#orgPreview').click();page.evaluate('()=>{state.lists=state.lists.filter(l=>l.id!=="new");save(false);}');page.locator('#applyLocalBatch').click()
  check('Deleted destination list blocks all changes',page.locator('#batchError').is_visible() and page.evaluate('state.media[0].listIds')==['old'])
  page.locator('#cancelLocalBatch').click();page.locator('#localBatchDialog').wait_for(state='detached')
  seed_org();page.locator('[data-org-id="m0"]').check();page.locator('#orgPreview').click();page.evaluate('libraryEpoch++');page.locator('#applyLocalBatch').click()
  check('Import/replacement generation invalidates preview',page.locator('#batchError').is_visible() and page.evaluate('state.media[0].favorite')==False)
  page.locator('#cancelLocalBatch').click();page.locator('#localBatchDialog').wait_for(state='detached')
  seed_org();page.locator('[data-org-id="m0"]').check();page.locator('#orgPreview').click();page.evaluate('()=>{window.originalSet=localStorage.setItem;localStorage.setItem=(k,v)=>{if(k===BACKUP_KEY)throw Error("Backup quota QA");return originalSet(k,v);}}');page.locator('#applyLocalBatch').click()
  check('Required backup refusal prevents modification',page.locator('#batchError').is_visible() and page.evaluate('state.media[0].favorite')==False)
  page.evaluate('localStorage.setItem=originalSet');page.locator('#cancelLocalBatch').click();page.locator('#localBatchDialog').wait_for(state='detached')
  seed_org();page.locator('[data-org-id="m0"]').check();page.locator('#orgPreview').click();page.evaluate('()=>{window.originalSet=localStorage.setItem;localStorage.setItem=(k,v)=>{if(k===KEY)throw Error("Write quota QA");return originalSet(k,v);}}');page.locator('#applyLocalBatch').click()
  check('Main storage refusal rolls back selected field',page.locator('#batchError').is_visible() and page.evaluate('state.media[0].favorite')==False)
  check('Failed commit preserves exportable work copy',page.evaluate('storageDraft.media[0].favorite===true&&lastLocalBatch===null'))
  page.evaluate('localStorage.setItem=originalSet');page.locator('#cancelLocalBatch').click();page.locator('#localBatchDialog').wait_for(state='detached')
  seed_org();page.evaluate('storageWritable=false');page.locator('[data-org-id="m0"]').check();page.locator('#orgPreview').click();check('Read-only window can select but cannot start mutation preview',page.locator('#localBatchDialog').count()==0 and page.evaluate('state.media[0].favorite')==False)
  page.evaluate('storageWritable=true')
  # No-op preview and transport hygiene.
  page.evaluate('()=>{state.media[0].favorite=true;save(false);}');page.locator('#orgPreview').click();check('Already matching selection cannot issue useless commit',page.locator('#applyLocalBatch').is_disabled());page.locator('#cancelLocalBatch').click();page.locator('#localBatchDialog').wait_for(state='detached')
  check('Runtime selection and undo omitted from portable data',page.evaluate('()=>{const p=toPortableBackup(state);return !Object.hasOwn(p,"organizeView")&&!Object.hasOwn(p,"lastLocalBatch")&&p.schema===11}'))
  check('Portable roundtrip preserves batch fields',page.evaluate('fromPortableBackup(toPortableBackup(state)).media[0].favorite===true'))
  # Unsafe imported title remains escaped, including preview.
  page.evaluate('()=>{state.media[0].title="<svg onload=window.orgInjected=true>";state.media[0].favorite=false;save(false);renderOrganizer();}');page.locator('#orgPreview').click()
  check('HTML-shaped title renders as inert text',page.locator('#localBatchDialog svg').count()==0 and '<svg onload=' in page.locator('#localBatchDialog').inner_text())
  page.locator('#cancelLocalBatch').click();page.locator('#localBatchDialog').wait_for(state='detached');check('No title injection executed',page.evaluate('window.orgInjected===undefined'))
  # Bounded next-up list handles loaded data outside first rendered page.
  seed_queue();page.evaluate('()=>{state.media=Array.from({length:95},(_,i)=>({id:"b"+i,kind:"book",title:"Livre QA "+String(i).padStart(3,"0"),added:true,listIds:[]}));state.episodes=[];save(false);openNextUp();}')
  check('Next-up initial rendering limited to 60 rows',page.locator('.queue-row').count()==60)
  page.locator('#nextMore').click();check('Next-up load more reveals all rows without duplicates',page.locator('.queue-row').count()==95)
  page.locator('#nextQuery').fill('094');check('Next-up search includes items beyond first page',page.locator('.queue-row').count()==1 and '094' in page.locator('#nextRows').inner_text())
  for w,h in [(390,844),(768,1024),(1360,1000)]:
   seed_queue();page.locator('[data-quick="next-up"]').click();page.set_viewport_size({'width':w,'height':h});page.wait_for_timeout(150);page.evaluate('()=>{document.activeElement?.blur();window.scrollTo(0,0);document.querySelector("#appShell").scrollTop=0;}')
   page.wait_for_function('parseFloat(getComputedStyle(document.querySelector("#toast")).opacity)<0.01')
   check('Next-up no horizontal overflow '+str(w),page.evaluate('document.documentElement.scrollWidth<=innerWidth+1'))
   page.screenshot(path=str(OUT/f'fixture-next-up-{w}.png'))
   seed_org();page.locator('[data-org-id="m0"]').check();page.locator('#orgPreview').click();check('Batch dialog fits viewport '+str(w),page.locator('#localBatchDialog').evaluate('(d)=>d.getBoundingClientRect().width<=innerWidth&&d.scrollWidth<=d.clientWidth+1'))
   page.screenshot(path=str(OUT/f'fixture-organize-preview-{w}.png'));page.keyboard.press('Escape');page.locator('#localBatchDialog').wait_for(state='detached')
   check('Escape cancels batch without modifying fields '+str(w),page.evaluate('state.media.every(m=>!m.favorite)'))
   check('Organizer no horizontal overflow '+str(w),page.evaluate('document.documentElement.scrollWidth<=innerWidth+1'))
  check('No uncaught JavaScript errors in new scenarios',not errors)
  (OUT/'browser-v017.json').write_text(json.dumps({'version':'0.18.0','scope':'actual DOM/CSS/scripts with controlled API/images/storage; no provider account or persistent-origin/PWA test','passed':len(checks),'checks':checks,'errors':errors},ensure_ascii=False,indent=2));print('V0.17 Chromium:',len(checks),'assertions passed');browser.close()
if __name__=='__main__':
 try:run()
 except Exception:
  (OUT/'browser-v017-failure.json').write_text(json.dumps({'passed':len(checks),'checks':checks,'traceback':traceback.format_exc()},ensure_ascii=False,indent=2));raise
