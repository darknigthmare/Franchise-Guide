"""V0.21 lists: real production DOM/scripts/CSS, controlled provider/image/storage fixtures.
No real OAuth, SDK, persistent browser origin or external spreadsheet is claimed.
"""
from pathlib import Path
from playwright.sync_api import sync_playwright
from browser_harness import ROOT,WEB,SETUP,RESET,AUTH
import re,json,base64,traceback
OUT=ROOT/'tests/results/v021';OUT.mkdir(parents=True,exist_ok=True);checks=[]
def check(name,v=True):
 if not v:raise AssertionError(name)
 checks.append({'name':name,'passed':True});print('PASS',name,flush=True)
def run():
 with sync_playwright() as p:
  browser=p.chromium.launch(executable_path='/usr/bin/chromium',headless=True,args=['--no-sandbox']);ctx=browser.new_context(viewport={'width':1360,'height':1000},locale='fr-FR',timezone_id='Europe/Paris');page=ctx.new_page();page.set_default_timeout(6500);errors=[]
  page.on('pageerror',lambda e:errors.append(str(e)));page.on('dialog',lambda d:d.accept())
  png=base64.b64decode('iVBORw0KGgoAAAANSUhEUgAAAAIAAAACCAYAAABytg0kAAAAFElEQVQImWNkYPj/n4GBgYGJAQoAHhgCAQGXeHsAAAAASUVORK5CYII=')
  ctx.route(re.compile(r'https://image\.tmdb\.org/'),lambda r:r.fulfill(status=200,content_type='image/png',body=png))
  html=(WEB/'index.html').read_text();page.set_content(re.sub(r'<script.*?</script>|<link[^>]+>|<meta http-equiv="Content-Security-Policy"[^>]+>','',html));page.add_style_tag(content=(WEB/'app.css').read_text());page.evaluate(SETUP)
  for f in re.findall(r'<script src="([^"]+)"',html):page.add_script_tag(content=(WEB/f).read_text())
  page.wait_for_timeout(80)
  page.evaluate('''()=>{qa.backupFn=createBackup;qa.setItem=localStorage.setItem;qa.rawBlob=URL.createObjectURL;URL.createObjectURL=b=>{qa.exportedBlob=b;return qa.rawBlob(b);};window.downloadJson=(name,data)=>{qa.download={name,data};};}''')
  def reset():
   page.evaluate(RESET);page.evaluate('''()=>{libraryEpoch++;recoveryOnly=null;localStorage.setItem=qa.setItem;createBackup=qa.backupFn;personalListViews.clear();listsHubView={query:'',limit:60};state.lists=[];save(false);screen='more';overlay=null;render();}''')
  def create(name='Liste de validation',description='Description privée\nà conserver'):
   page.locator('#newList').click();page.locator('#listEditor [name=name]').fill(name);page.locator('#listEditor [name=description]').fill(description);page.locator('#listEditor .confirm').click();page.locator('#listEditor').wait_for(state='detached')
  def seed_members():
   page.evaluate('''()=>{state.lists=[{id:'l',name:'Ma sélection',description:'Description privée à ne pas exporter',sortMode:'title',ascending:true},{id:'other',name:'Autre liste'}];state.media=[
    {id:'s',kind:'show',title:'Série de validation',added:true,listIds:['l'],catalogComplete:true,episodeCount:1,posterUrl:'https://image.tmdb.org/t/p/w500/qa.png'},
    {id:'m',kind:'movie',title:'Film masqué de validation',added:true,hidden:true,listIds:['l'],watched:true,plays:2,lastWatchedAt:1700000100000,watchHistory:[1700000000000,1700000100000],rating:8,userNote:'Note privée de QA',posterUrl:'https://image.tmdb.org/t/p/w500/qa.png'},
    {id:'g',kind:'game',title:'Jeu en reprise',added:true,listIds:['other'],watched:true,trackingStatus:'active',progressCurrent:20,progressTotal:100,posterUrl:'https://image.tmdb.org/t/p/w500/qa.png'},
    {id:'b',kind:'book',title:'Livre à lire',added:true,listIds:[],posterUrl:'https://image.tmdb.org/t/p/w500/qa.png'},
    {id:'r',kind:'movie',title:'Titre retiré',added:false,listIds:['l']}];state.suggestions=[{id:'sg',kind:'movie',title:'Suggestion',suggestion:true,added:false,listIds:['l']}];state.episodes=[{id:'e',showId:'s',season:1,num:1,title:'Titre épisode secret',watched:true,plays:1,lastWatchedAt:1700000000000,runtimeMinutes:45}];save(false);openList('l');}''')
  reset();check('More exposes safe lists manager',page.locator('#allPersonalLists').is_visible())
  raw=page.evaluate('localStorage.getItem(KEY)');page.locator('#newList').click();page.locator('#listEditor [name=name]').fill('À annuler');page.keyboard.press('Escape');page.locator('#listEditor').wait_for(state='detached')
  check('Escape cancels new list without mutation',page.evaluate('localStorage.getItem(KEY)')==raw)
  create();check('Created empty list remains open and persisted',page.locator('#listManage').is_visible() and page.evaluate('JSON.parse(localStorage.getItem(KEY)).lists[0].name')=='Liste de validation')
  check('Multiline description preserved',page.evaluate('state.lists[0].description')=='Description privée\nà conserver')
  page.locator('#allLists').click();check('Empty list remains in manager',page.locator('[data-list-open]').count()==1)
  page.locator('#createList').click();page.locator('#listEditor [name=name]').fill('LISTE   DE VALIDATION');page.locator('#listEditor .confirm').click();check('Duplicate normalized name rejected inline',page.locator('#listEditor .franchise-error').is_visible() and page.evaluate('state.lists.length')==1);page.locator('#listEditor .cancel').click()
  page.locator('#createList').click();page.locator('#listEditor [name=name]').fill('猫');page.locator('#listEditor .confirm').click();page.locator('#allLists').click();page.locator('#createList').click();page.locator('#listEditor [name=name]').fill('犬');page.locator('#listEditor .confirm').click()
  check('Different non-latin lists not conflated',page.evaluate('state.lists.length')==3)
  reset();seed_members();check('List row count excludes retired reference and suggestion',page.locator('.personal-list-row').count()==2)
  check('Hidden member is visible and clearly labelled','Masqué' in page.locator('#personalListRows').inner_text())
  raw=page.evaluate('localStorage.getItem(KEY)');page.locator('#renameList').click();page.locator('#listEditor [name=description]').fill('Effacé si ancien prompt annulé');page.locator('#listEditor .cancel').click();check('Cancel editing preserves existing description',page.evaluate('localStorage.getItem(KEY)')==raw)
  page.locator('#listManage').click();check('Member selector contains added works only',page.locator('[data-member-id]').count()==4)
  page.locator('#memberKind').select_option('game');page.locator('[data-member-id="g"]').check();page.locator('#memberKind').select_option('book');page.locator('[data-member-id="b"]').check()
  check('Selections persist while category changes',page.locator('#memberCount').inner_text().startswith('4 sélectionné'))
  raw=page.evaluate('localStorage.getItem(KEY)');page.locator('#listMembers .confirm').click();check('Membership preview writes nothing',page.evaluate('localStorage.getItem(KEY)')==raw)
  check('Preview reports changes explicitly','2 ajout(s)' in page.locator('#memberPreview').inner_text())
  page.locator('#listMembers .confirm').click();page.locator('#listMembers').wait_for(state='detached');check('Confirm applies all four memberships',page.locator('.personal-list-row').count()==4)
  check('Membership preview makes backup before mutation',page.evaluate('backups().length')>=1)
  check('Membership keeps watch history and notes',page.evaluate('state.media.find(m=>m.id==="m").plays===2&&state.media.find(m=>m.id==="m").userNote==="Note privée de QA"'))
  page.locator('#personalListVisibility').select_option('visible');check('Visibility filter excludes hidden without deleting membership',page.locator('.personal-list-row').count()==3 and page.evaluate('state.media.find(m=>m.id==="m").listIds.includes("l")'))
  page.locator('#personalListVisibility').select_option('all');page.locator('#personalListKind').select_option('game');page.locator('#personalListState').select_option('done');check('Completed history does not falsely finish current replay',page.locator('.personal-list-row').count()==0)
  page.locator('#personalListState').select_option('pending');check('Active replay appears in pending filter',page.locator('.personal-list-row').count()==1)
  page.locator('#personalListKind').select_option('all');page.locator('#personalListState').select_option('all');page.locator('[data-list-position="g"]').click();page.locator('#listPosition [name=position]').fill('1');page.locator('#listPosition .confirm').click()
  check('Position dialogue uses entire list and records manual order',page.locator('[data-list-media]').first.get_attribute('data-list-media')=='g' and page.evaluate('state.lists[0].manualOrder'))
  page.locator('#personalListQuery').fill('série');page.locator('[data-list-position="s"]').click();check('Position under search explains full list', '4 titres ajoutés' in page.locator('#listPosition').inner_text());page.locator('#listPosition .cancel').click();page.locator('#personalListQuery').fill('')
  page.locator('#listSort').select_option('title');check('Automatic sort leaves stored manual order',page.evaluate('state.lists[0].sortOrder[0]')=='g')
  page.locator('#listSort').select_option('manual');check('Returning to personal sort restores first title',page.locator('[data-list-media]').first.get_attribute('data-list-media')=='g')
  page.locator('[data-list-media="m"]').click();check('Opening member opens full title detail',page.locator('.detail-hero').is_visible());page.locator('#back').click();check('Back restores list and its view',page.locator('#listManage').is_visible() and page.locator('#listSort').input_value()=='manual')
  page.locator('#duplicateList').click();page.locator('#listDuplicate [name=name]').fill('Copie de QA');page.locator('#listDuplicate .confirm').click();check('Duplicate creates definition without duplicating works',page.evaluate('state.lists.length===3&&state.media.length===5'))
  check('Copy retains current manual order',page.locator('[data-list-media]').first.get_attribute('data-list-media')=='g')
  page.locator('#personalListKind').select_option('movie');page.locator('#listReport').click();check('Report warns about personal data and is not a backup','Ce fichier est personnel' in page.locator('#listExport').inner_text());page.locator('#listExport .confirm').click();report=page.evaluate('qa.download.data')
  check('Export includes entire filtered selection and no private notes',report['selected']==1 and not any(x in json.dumps(report,ensure_ascii=False) for x in ['Note privée de QA','Description privée','1700000000000','accessToken']))
  page.locator('#listReport').click();page.locator('#listExport [name=format]').select_option('csv');page.locator('#listExport .confirm').click();check('CSV is UTF8 BOM with escaped selection titles',page.evaluate('async()=>Array.from(new Uint8Array(await qa.exportedBlob.arrayBuffer()).slice(0,3)).join(",")')=='239,187,191')
  check('Portable schema10 roundtrip retains manual order',page.evaluate('()=>{const b=toPortableBackup(state),r=fromPortableBackup(b);return b.schema===11&&r.lists.some(l=>l.manualOrder&&l.sortOrder[0]==="g")}'))
  check('Session selections not exported as operational state',page.evaluate('()=>{const b=toPortableBackup(state);return !b.personalListViews&&!b.listsHubView}'))
  # Safe dialog conflict semantics: unrelated history changes are preserved, targeted changes rejected.
  reset();seed_members();page.locator('#listManage').click();page.locator('[data-member-id="g"]').check();page.locator('#listMembers .confirm').click();page.evaluate('()=>{state.media.find(m=>m.id==="m").userNote="Note récente";save(false);}')
  page.locator('#listMembers .confirm').click();check('Preview preserves unrelated updated note',page.evaluate('state.media.find(m=>m.id==="m").userNote')=='Note récente')
  page.locator('#listManage').click();page.locator('[data-member-id="b"]').check();page.locator('#listMembers .confirm').click();page.evaluate('()=>{state.media.find(m=>m.id==="s").listIds=[];save(false);}')
  raw=page.evaluate('localStorage.getItem(KEY)');page.locator('#listMembers .confirm').click();check('Conflicting membership refuses entire plan',page.locator('#listMembers .franchise-error').is_visible() and page.evaluate('localStorage.getItem(KEY)')==raw);page.locator('#listMembers .cancel').click()
  page.locator('#renameList').click();page.locator('#listEditor [name=name]').fill('À refuser');page.evaluate('()=>{state.lists[0].name="Nom plus récent";save(false);}')
  page.locator('#listEditor .confirm').click();check('Stale rename cannot overwrite newer definition',page.locator('#listEditor .franchise-error').is_visible() and page.evaluate('state.lists[0].name')=='Nom plus récent');page.locator('#listEditor .cancel').click()
  page.evaluate('()=>openList("l")');page.locator('#listManage').click();page.locator('[data-member-id="b"]').check();page.locator('#listMembers .confirm').click();page.evaluate('()=>{libraryEpoch++;}')
  page.locator('#listMembers .confirm').click();check('Library replacement invalidates outstanding membership preview',page.locator('#listMembers .franchise-error').is_visible());page.locator('#listMembers .cancel').click()
  reset();seed_members();page.evaluate(AUTH);page.evaluate('()=>{syncState.queue=[{id:"retained-op",key:"movie:333",field:"ratings",body:{movies:[]}}];syncStore();}')
  queue=page.evaluate('JSON.stringify(syncState.queue)');page.locator('#renameList').click();page.locator('#listEditor [name=name]').fill('Local uniquement');page.locator('#listEditor .confirm').click();check('List edit leaves existing Trakt queue identical',page.evaluate('JSON.stringify(syncState.queue)')==queue)
  raw=page.evaluate('localStorage.getItem(KEY)');page.locator('#delList').click();page.evaluate('traktSyncing=true');page.locator('#listDelete .confirm').click();check('List deletion blocked during sync without mutation',page.locator('#listDelete .franchise-error').is_visible() and page.evaluate('localStorage.getItem(KEY)')==raw);page.evaluate('traktSyncing=false');page.locator('#listDelete .cancel').click()
  page.locator('#delList').click();page.evaluate('()=>{createBackup=()=>false;}');page.locator('#listDelete .confirm').click();check('Failed safety backup blocks deletion',page.locator('#listDelete .franchise-error').is_visible() and page.evaluate('state.lists.some(l=>l.id==="l")'));page.locator('#listDelete .cancel').click();page.evaluate('createBackup=qa.backupFn')
  page.locator('#delList').click();page.locator('#listDelete .confirm').click();check('Deleting definition detaches even retired and suggested records',page.evaluate('!state.lists.some(l=>l.id==="l")&&[...state.media,...state.suggestions].every(m=>!m.listIds.includes("l"))'))
  check('Deleting list retains media episodes history gaming and queue',page.evaluate('state.media.length===5&&state.episodes.length===1&&state.media[1].plays===2') and page.evaluate('JSON.stringify(syncState.queue)')==queue)
  check('No provider POST sent by local list edits',page.evaluate('qa.calls.filter(c=>c.method==="POST").length')==0)
  # Storage failure preserves original state and offers candidate draft, no fake success.
  reset();seed_members();raw=page.evaluate('localStorage.getItem(KEY)');page.locator('#renameList').click();page.locator('#listEditor [name=name]').fill('Écriture refusée');page.evaluate('()=>{localStorage.setItem=(k,v)=>{if(k===KEY)throw Error("Quota contrôlé");return qa.setItem(k,v)};}');page.locator('#listEditor .confirm').click()
  check('Refused list write keeps prior saved state',page.evaluate('localStorage.getItem(KEY)')==raw)
  check('Refused list write keeps editor and candidate recovery draft',page.locator('#listEditor .franchise-error').is_visible() and page.evaluate('storageDraft.lists[0].name')=='Écriture refusée');page.locator('#listEditor .cancel').click();page.evaluate('localStorage.setItem=qa.setItem')
  page.evaluate('storageWritable=false');page.locator('#listManage').click();page.locator('[data-member-id="g"]').check();page.locator('#listMembers .confirm').click();page.locator('#listMembers .confirm').click();check('Read-only window cannot change membership',page.locator('#listMembers .franchise-error').is_visible() and page.evaluate('state.media[2].listIds.includes("l")')==False);page.locator('#listMembers .cancel').click()
  # Real rendered batches, whole-selection search and persisted order with long title.
  reset();seed_members();page.evaluate('''()=>{const title='Liste très longue avec plusieurs mots pour vérifier les petits écrans '.repeat(2).slice(0,120);state.lists[0].name=title;state.media=Array.from({length:175},(_,i)=>({id:'long'+i,kind:i%2?'book':'movie',title:i===174?'Référence recherchée au-delà de la première page':'Titre de liste QA '+String(i).padStart(3,'0'),added:true,listIds:['l'],posterUrl:'https://image.tmdb.org/t/p/w500/qa.png'}));state.episodes=[];state.suggestions=[];save(false);personalListViews.clear();openList('l');}''')
  check('Long personal list initially renders sixty rows',page.locator('.personal-list-row').count()==60);page.locator('#personalListMore').click();check('Next sixty rows rendered on demand',page.locator('.personal-list-row').count()==120)
  page.locator('#personalListQuery').fill('au-delà');check('Search covers offscreen members',page.locator('.personal-list-row').count()==1);page.locator('#personalListQuery').fill('')
  page.locator('#listManage').click();page.locator('#memberClearResults').click();page.locator('#memberSelectAll').click();check('Select-all covers unrendered members',page.locator('#memberCount').inner_text().startswith('175 sélectionné'))
  page.locator('#listMembers .cancel').click();page.locator('#listReport').click();page.locator('#listExport .confirm').click();check('List export covers all175 not first120 visible',page.evaluate('qa.download.data.selected')==175)
  for w in [390,768,1360]:
   page.set_viewport_size({'width':w,'height':940});page.wait_for_function('parseFloat(getComputedStyle(document.querySelector("#toast")).opacity)<0.01')
   check('List view no horizontal overflow '+str(w),page.evaluate('document.documentElement.scrollWidth<=innerWidth+1'));page.screenshot(path=str(OUT/f'fixture-personal-list-{w}.png'),full_page=False)
   page.locator('#listManage').click();check('Member dialog fits viewport '+str(w),page.locator('#listMembers').evaluate('(d)=>d.getBoundingClientRect().width<=innerWidth&&d.scrollWidth<=d.clientWidth+1'));page.keyboard.press('Escape');page.locator('#listMembers').wait_for(state='detached');check('Escape closes member selection '+str(w),page.locator('#listMembers').count()==0)
  # New schema handling protects portable order metadata against malformed entries.
  check('Malformed imported personal order is refused before application',page.evaluate('()=>{const b=toPortableBackup(state);b.lists[0].sortOrder=["x","x"];try{fromPortableBackup(b);return false}catch{return true}}'))
  check('Newly future schema12 refused',page.evaluate('()=>{try{fromPortableBackup({schema:12,media:[]});return false}catch{return true}}'))
  check('Previous schema9 remains readable',page.evaluate('()=>{const b=toPortableBackup(state);b.schema=9;b.lists.forEach(l=>{delete l.manualOrder;delete l.sortOrder});return fromPortableBackup(b).media.length===175}'))
  # Preserve the originating detail return chain when another feature owns Back.
  page.evaluate("()=>{state.media=[{id:'origin',kind:'show',title:'Série d’origine',added:true}];overlay={type:'person',id:1,title:'Acteur',returnTo:{type:'item',id:'origin',title:'Série d’origine'}};enhancePersonalLists();}")
  page.locator('#back').click()
  check('List hooks do not erase the person-to-series back chain',page.evaluate("overlay?.type==='item'&&overlay?.id==='origin'"))
  check('No uncaught JavaScript errors in new scenarios',not errors)
  result={'version':'0.21.0','passed':len(checks),'checks':checks,'errors':errors,'scope':'real production scripts DOM CSS; controlled provider, images, storage. No real persistent origin/account/SDK.'};(OUT/'browser-v021.json').write_text(json.dumps(result,ensure_ascii=False,indent=2));print(json.dumps({'passed':len(checks),'errors':errors}));browser.close()
if __name__=='__main__':
 try:run()
 except Exception:
  (OUT/'browser-v021-failure.json').write_text(json.dumps({'checks':checks,'traceback':traceback.format_exc()},ensure_ascii=False,indent=2));raise
