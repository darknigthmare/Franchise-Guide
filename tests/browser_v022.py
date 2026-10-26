"""V0.22 episode commands. Real app DOM/scripts, controlled storage/API/images, no real account."""
from pathlib import Path
from playwright.sync_api import sync_playwright
from browser_harness import ROOT,WEB,SETUP,RESET,AUTH
import re,json,base64,traceback
OUT=ROOT/'tests/results/v022';OUT.mkdir(parents=True,exist_ok=True);checks=[]
def check(name,v=True):
 if not v:raise AssertionError(name)
 checks.append({'name':name,'passed':True});print('PASS',name,flush=True)
def run():
 with sync_playwright() as p:
  b=p.chromium.launch(executable_path='/usr/bin/chromium',headless=True,args=['--no-sandbox']);ctx=b.new_context(viewport={'width':1360,'height':1000},locale='fr-FR',timezone_id='Europe/Paris');page=ctx.new_page();page.set_default_timeout(6500);errors=[]
  page.on('pageerror',lambda e:errors.append(str(e)));page.on('dialog',lambda d:d.accept())
  png=base64.b64decode('iVBORw0KGgoAAAANSUhEUgAAAAIAAAACCAYAAABytg0kAAAAFElEQVQImWNkYPj/n4GBgYGJAQoAHhgCAQGXeHsAAAAASUVORK5CYII=')
  ctx.route(re.compile(r'https://image\.tmdb\.org/'),lambda r:r.fulfill(status=200,content_type='image/png',body=png))
  html=(WEB/'index.html').read_text();page.set_content(re.sub(r'<script.*?</script>|<link[^>]+>|<meta http-equiv="Content-Security-Policy"[^>]+>','',html));page.add_style_tag(content=(WEB/'app.css').read_text());page.evaluate(SETUP)
  for f in re.findall(r'<script src="([^"]+)"',html):page.add_script_tag(content=(WEB/f).read_text())
  page.wait_for_timeout(100);page.evaluate('()=>{qa.backupFn=createBackup;qa.setItem=localStorage.setItem;qa.originalNow=Date.now;}')
  def seed(manager=True):
   page.evaluate(RESET);page.evaluate('''()=>{libraryEpoch++;recoveryOnly=null;localStorage.setItem=qa.setItem;createBackup=qa.backupFn;Date.now=qa.originalNow;episodeManagerViews.clear();seriesViews.clear();
    state.media=[{id:'s',kind:'show',title:'Série de validation du suivi',tmdbId:111,added:true,catalogComplete:true,episodeCount:5,listIds:[],posterUrl:'https://image.tmdb.org/t/p/w500/qa.png',userNote:'Note privée série',lastWatchedAt:1700000001000}];
    const e=(n,x={})=>({id:'e'+n,showId:'s',tmdbId:110+n,season:1,num:n,title:'Épisode de validation '+n,date:'2025-01-01',runtimeMinutes:45,watched:false,plays:0,watchHistory:[],skipped:false,collected:false,...x});
    state.episodes=[e(1,{title:'Révélation secrète'}),e(2,{watched:true,plays:3,lastWatchedAt:1700000001000,watchHistory:[1700000000000,1700000001000],userNote:'Note privée épisode',rating:8,collected:true}),e(3,{skipped:true}),e(4,{date:'2999-01-01'}),e(5,{date:null}),e(6,{season:0})];
    state.settings.hideEpisodeSpoilers=true;save(false);overlay={type:'item',id:'s',src:'media',title:state.media[0].title};render();}''')
   if manager:page.locator('#manageEpisodes').click()
  def select(ids):
   for id in ids:page.locator('[data-episode-choice="'+id+'"]').check()
  def preview(action):page.locator('#managerAction').select_option(action);page.locator('#episodesPreview').click()
  def apply():page.locator('#episodeBatchPreview [name=ack]').check();page.locator('#episodeBatchPreview .confirm').click();page.locator('#episodeBatchPreview').wait_for(state='detached')
  seed(False);check('Series detail exposes episode manager',page.locator('#manageEpisodes').is_visible());page.locator('#manageEpisodes').click()
  check('Manager defaults exclude specials but report all loaded rows',page.locator('.episode-choice').count()==5 and '6 épisodes chargés' in page.locator('main').inner_text())
  check('Manager selection starts empty',page.locator('#episodesPreview').is_disabled())
  page.locator('#managerQuery').fill('révélation');check('Hidden spoiler title cannot be searched',page.locator('.episode-choice').count()==0)
  page.locator('#managerQuery').fill('S01E01');check('Episode number remains searchable with spoilers hidden',page.locator('.episode-choice').count()==1)
  select(['e1']);page.locator('#managerQuery').fill('');page.locator('#managerStatus').select_option('watched');select(['e2'])
  check('Selection survives filter changes including hidden rows',page.locator('#episodesCount').inner_text().startswith('2 sélectionné(s), dont 1 hors filtre'))
  page.locator('#managerStatus').select_option('all');check('Revealed prior selection remains checked',page.locator('[data-episode-choice=e1]').is_checked() and page.locator('[data-episode-choice=e2]').is_checked())
  raw=page.evaluate('localStorage.getItem(KEY)');preview('skip');check('Preview explicitly reports protected history','1 historiques protégés' in page.locator('#episodeBatchPreview').inner_text())
  check('Preparing a batch does not mutate persisted state',page.evaluate('localStorage.getItem(KEY)')==raw)
  page.locator('#episodeBatchPreview .confirm').click();check('Explicit consent required before batch application',page.locator('#episodeBatchPreview .franchise-error').is_visible() and page.evaluate('localStorage.getItem(KEY)')==raw)
  page.keyboard.press('Escape');page.locator('#episodeBatchPreview').wait_for(state='detached');check('Escape cancels without changing data',page.evaluate('localStorage.getItem(KEY)')==raw)
  preview('skip');apply();check('Skipping selection changes unseen and protects viewed',page.evaluate('state.episodes[0].skipped&&state.episodes[1].watched&&state.episodes[1].plays===3'))
  check('Protected dates and private fields remain exact',page.evaluate('JSON.stringify(state.episodes[1].watchHistory)==="[1700000000000,1700000001000]"&&state.episodes[1].userNote==="Note privée épisode"&&state.episodes[1].rating===8'))
  check('Applied operation has local recovery snapshot',page.evaluate('backups().length')>0)
  check('Applied operation clears selection, not episode data',page.locator('#episodesCount').inner_text().startswith('0 sélectionné') and page.evaluate('state.episodes.length')==6)
  seed();page.locator('#episodesSelectAll').click();raw=page.evaluate('localStorage.getItem(KEY)');preview('watch')
  check('Default watch excludes future unknown and ignored episodes','2 dates exclues' in page.locator('#episodeBatchPreview').inner_text() and '1 ignorés protégés' in page.locator('#episodeBatchPreview').inner_text())
  apply();check('Only new eligible episode marked watched',page.evaluate('state.episodes.filter(e=>e.watched).map(e=>e.id).join(",")')=='e1,e2')
  first=page.evaluate('state.episodes[0].lastWatchedAt');select(['e1']);preview('watch');apply();check('Already-seen batch cannot create revisionnage',page.evaluate('state.episodes[0].plays===1') and page.evaluate('state.episodes[0].lastWatchedAt')==first)
  select(['e4','e5']);page.locator('#managerAllowDates').check();preview('watch');apply();check('Explicit date override allows unknown and future without inventing air dates',page.evaluate('state.episodes[3].watched&&state.episodes[4].watched&&state.episodes[4].date===null&&state.episodes[3].date==="2999-01-01"'))
  seed();select(['e2']);preview('unwatch');check('Erasure preview counts historic dates and plays','3 lecture(s)' in page.locator('#episodeBatchPreview').inner_text() and '2 date(s)' in page.locator('#episodeBatchPreview').inner_text());apply()
  check('Only explicit erasure removes dates',page.evaluate('!state.episodes[1].watched&&state.episodes[1].plays===0&&state.episodes[1].watchHistory.length===0&&state.episodes[1].lastWatchedAt===null'))
  check('Erasure retains personal note rating and collection',page.evaluate('state.episodes[1].collected&&state.episodes[1].rating===8&&state.episodes[1].userNote==="Note privée épisode"'))
  seed();select(['e3']);preview('unskip');apply();check('Reactivating does not count a viewing',page.evaluate('!state.episodes[2].skipped&&!state.episodes[2].watched&&state.episodes[2].plays===0'))
  seed();select(['e1','e4']);preview('collect');page.evaluate('()=>{Object.assign(state.episodes[0],FGCore.episodeState(state.episodes[0],"watched",1700000020000));state.episodes[0].userNote="Plus récente";save(false);}')
  apply();check('Collection applies without overwriting newer viewing or note',page.evaluate('state.episodes[0].collected&&state.episodes[0].lastWatchedAt===1700000020000&&state.episodes[0].userNote==="Plus récente"&&state.episodes[3].collected'))
  select(['e1','e4']);preview('uncollect');apply();check('Uncollect affects neither history nor future dates',page.evaluate('!state.episodes[0].collected&&state.episodes[0].watched&&state.episodes[3].date==="2999-01-01"'))
  # Whole-plan failure and rollback cases.
  seed();select(['e1','e5']);preview('collect');page.evaluate('()=>{state.episodes[4].collected=true;save(false);}')
  raw=page.evaluate('localStorage.getItem(KEY)');page.locator('#episodeBatchPreview [name=ack]').check();page.locator('#episodeBatchPreview .confirm').click()
  check('Changed targeted field rejects every row atomically',page.locator('#episodeBatchPreview .franchise-error').is_visible() and page.evaluate('localStorage.getItem(KEY)')==raw and not page.evaluate('state.episodes[0].collected'));page.locator('#episodeBatchPreview .cancel').click()
  select(['e2']);preview('collect');page.evaluate('libraryEpoch++');page.locator('#episodeBatchPreview [name=ack]').check();page.locator('#episodeBatchPreview .confirm').click();check('Replaced library invalidates old preview',page.locator('#episodeBatchPreview .franchise-error').is_visible());page.locator('#episodeBatchPreview .cancel').click()
  seed();select(['e1']);preview('collect');page.evaluate('authGeneration++');page.locator('#episodeBatchPreview [name=ack]').check();page.locator('#episodeBatchPreview .confirm').click();check('Changed Trakt session invalidates preview before writes',page.locator('#episodeBatchPreview .franchise-error').is_visible() and not page.evaluate('state.episodes[0].collected'));page.locator('#episodeBatchPreview .cancel').click()
  seed();select(['e1']);preview('collect');page.evaluate('traktSyncing=true');page.locator('#episodeBatchPreview [name=ack]').check();page.locator('#episodeBatchPreview .confirm').click();check('Active Trakt sync blocks commit',page.locator('#episodeBatchPreview .franchise-error').is_visible() and not page.evaluate('state.episodes[0].collected'));page.evaluate('traktSyncing=false');page.locator('#episodeBatchPreview .cancel').click()
  seed();select(['e1']);preview('collect');page.evaluate('()=>{Date.now=()=>qa.originalNow()+11*60000;}');page.locator('#episodeBatchPreview [name=ack]').check();page.locator('#episodeBatchPreview .confirm').click();check('Expired preview refused without mutation','expiré' in page.locator('#episodeBatchPreview .franchise-error').inner_text() and not page.evaluate('state.episodes[0].collected'));page.evaluate('Date.now=qa.originalNow');page.locator('#episodeBatchPreview .cancel').click()
  seed();select(['e1']);preview('collect');page.evaluate('createBackup=()=>false');raw=page.evaluate('localStorage.getItem(KEY)');page.locator('#episodeBatchPreview [name=ack]').check();page.locator('#episodeBatchPreview .confirm').click();check('Failed safety copy blocks action before mutation',page.locator('#episodeBatchPreview .franchise-error').is_visible() and page.evaluate('localStorage.getItem(KEY)')==raw);page.locator('#episodeBatchPreview .cancel').click()
  seed();select(['e1']);preview('collect');raw=page.evaluate('localStorage.getItem(KEY)');page.evaluate('()=>{localStorage.setItem=(k,v)=>{if(k===KEY)throw Error("Quota contrôlé");return qa.setItem(k,v)};}');page.locator('#episodeBatchPreview [name=ack]').check();page.locator('#episodeBatchPreview .confirm').click()
  check('Failed primary write preserves last stored revision',page.evaluate('localStorage.getItem(KEY)')==raw and not page.evaluate('state.episodes[0].collected'))
  check('Failed primary write offers draft and keeps error visible',page.evaluate('storageDraft.episodes[0].collected') and page.locator('#episodeBatchPreview .franchise-error').is_visible());page.locator('#episodeBatchPreview .cancel').click()
  seed();select(['e1']);page.evaluate('storageWritable=false');page.locator('#episodesPreview').click();check('Read-only preview request is visibly refused',page.locator('#episodeManagerError').is_visible() and page.locator('#episodeBatchPreview').count()==0)
  # Trakt queue remains change-based and preview does not submit to real providers.
  seed();page.evaluate(AUTH);select(['e1','e3']);preview('skip');apply();check('Skip creates no Trakt history deletion',page.evaluate('syncState.queue.length')==0)
  seed();page.evaluate(AUTH);select(['e1','e2']);preview('watch');apply();check('Only one newly viewed episode queued to Trakt',page.evaluate('syncState.queue.length===1&&syncState.queue[0].path==="/sync/history"&&syncState.queue[0].body.episodes[0].ids.tmdb===111'))
  date=page.evaluate('state.episodes[0].lastWatchedAt');check('Queued viewing uses proposed date',page.evaluate('Date.parse(syncState.queue[0].body.episodes[0].watched_at)')==date)
  check('No full watched-history resend for seen selections',page.evaluate('syncState.queue.every(op=>op.body.episodes[0].ids.tmdb!==112)'))
  seed();page.evaluate(AUTH);select(['e2']);preview('unwatch');apply();check('Explicit erasure queues targeted history remove',page.evaluate('syncState.queue.length===1&&syncState.queue[0].path==="/sync/history/remove"&&syncState.queue[0].body.episodes[0].ids.tmdb===112'))
  check('Preview and edits do not directly call provider POST',page.evaluate('qa.calls.filter(c=>c.method==="POST").length')==0)
  # Existing detail commands now use staged preview or protected skip.
  seed(False);page.locator('[data-ep="skip"][data-eid="e2"]').click();check('Existing skip button preserves prior viewings',page.evaluate('state.episodes[1].watched&&state.episodes[1].plays===3'))
  page.locator('[data-ep="watch"][data-eid="e2"]').click();check('Single unwatch opens explicit history erase preview',page.locator('#episodeBatchPreview').is_visible() and page.evaluate('state.episodes[1].watched'));page.locator('#episodeBatchPreview .cancel').click()
  raw=page.evaluate('localStorage.getItem(KEY)');page.locator('[data-season=skip][data-s="1"]').click();check('Existing season button previews without immediate mutation',page.locator('#episodeBatchPreview').is_visible() and page.evaluate('localStorage.getItem(KEY)')==raw);page.locator('#episodeBatchPreview .cancel').click()
  # Render limits and entire filtered selection, including specials and unknown numbers.
  seed();page.locator('#managerSpecials').check();check('Specials can be selected explicitly',page.locator('.episode-choice').count()==6);select(['e6']);page.locator('#managerSpecials').uncheck();check('Hidden selected special remains counted',page.locator('#episodesCount').inner_text().startswith('1 sélectionné(s), dont 1 hors filtre'))
  seed();page.evaluate('''()=>{state.episodes=Array.from({length:175},(_,i)=>({id:'long'+i,showId:'s',season:1,num:i+1,tmdbId:1000+i,title:i===174?'Dernier titre recherché':'Épisode QA '+i,date:'2025-01-01',watched:false,plays:0,watchHistory:[],skipped:false,collected:false}));state.media[0].episodeCount=175;state.settings.hideEpisodeSpoilers=false;save(false);episodeManagerViews.clear();openEpisodeManager('s');}''')
  check('Long series initially renders sixty selectable rows',page.locator('.episode-choice').count()==60);page.locator('#episodesMore').click();check('More renders next sixty rows',page.locator('.episode-choice').count()==120)
  page.locator('#managerQuery').fill('Dernier titre');check('Search covers not-yet-rendered episode',page.locator('.episode-choice').count()==1);page.locator('#managerQuery').fill('');page.locator('#episodesSelectAll').click();check('Select all includes every175 result, not visible sixty',page.locator('#episodesCount').inner_text().startswith('175 sélectionné'))
  preview('collect');check('Preview itself bounded to forty lines',page.locator('#episodePreviewRows li').count()==40);page.locator('#episodePreviewMore').click();check('Preview expands incrementally',page.locator('#episodePreviewRows li').count()==80);apply();check('Apply covers all175 selected episodes',page.evaluate('state.episodes.filter(e=>e.collected).length')==175)
  check('Session selection and plan do not enter portable export',page.evaluate('()=>{const b=toPortableBackup(state);return !b.episodeManagerViews&&!b.episodeBatchPreview&&b.schema===11;}'))
  for width in [390,768,1360]:
   page.set_viewport_size({'width':width,'height':940});page.wait_for_function('parseFloat(getComputedStyle(document.querySelector("#toast")).opacity)<0.01');check('Episode manager no horizontal overflow and full search control '+str(width),page.evaluate('document.documentElement.scrollWidth<=innerWidth+1&&document.querySelector("#managerQuery").getBoundingClientRect().width>=document.querySelector("#managerQuery").closest("label").getBoundingClientRect().width-3'));page.screenshot(path=str(OUT/f'fixture-episodes-{width}.png'),full_page=False)
   page.locator('#episodesSelectAll').click();preview('unwatch');check('Episode preview fits viewport '+str(width),page.locator('#episodeBatchPreview').evaluate('(d)=>d.getBoundingClientRect().width<=innerWidth&&d.scrollWidth<=d.clientWidth+1'));page.keyboard.press('Escape');page.locator('#episodeBatchPreview').wait_for(state='detached');check('Escape closes preview '+str(width),page.locator('#episodeBatchPreview').count()==0);page.locator('#episodesClear').click()
  page.locator('#episodesBack').click();check('Back returns to same series detail',page.evaluate('overlay.type==="item"&&overlay.id==="s"') and page.locator('#manageEpisodes').is_visible())
  page.locator('#manageEpisodes').click();page.locator('#back').click();check('Top back returns to the same series detail',page.locator('#manageEpisodes').is_visible())
  check('No uncaught JavaScript errors in new scenarios',not errors)
  (OUT/'browser-v022.json').write_text(json.dumps({'version':'0.22.0','passed':len(checks),'checks':checks,'errors':errors,'scope':'actual scripts DOM CSS with controlled data/storage/providers; no persistent origin, SDK, or real Trakt account'},ensure_ascii=False,indent=2));print(json.dumps({'passed':len(checks),'errors':errors}));b.close()
if __name__=='__main__':
 try:run()
 except Exception:
  (OUT/'browser-v022-failure.json').write_text(json.dumps({'checks':checks,'error':traceback.format_exc()},ensure_ascii=False,indent=2));raise
