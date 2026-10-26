"""Real Chromium DOM/scripts with controlled catalogue, images and storage.
No real account, PWA installation, native Android UI or persistent-origin claim.
"""
from pathlib import Path
from playwright.sync_api import sync_playwright
from browser_harness import ROOT,WEB,SETUP,RESET,AUTH
import json,re,base64,traceback
OUT=ROOT/'tests/results/v019';OUT.mkdir(parents=True,exist_ok=True);checks=[]
def check(name,value=True):
 if not value:raise AssertionError(name)
 checks.append({'name':name,'passed':True});print('PASS',name,flush=True)
def run():
 with sync_playwright() as p:
  b=p.chromium.launch(executable_path='/usr/bin/chromium',headless=True,args=['--no-sandbox']);ctx=b.new_context(viewport={'width':1360,'height':1000},locale='fr-FR',timezone_id='Europe/Paris');page=ctx.new_page();page.set_default_timeout(6000);errors=[]
  page.on('pageerror',lambda e:errors.append(str(e)));page.on('dialog',lambda d:d.accept())
  png=base64.b64decode('iVBORw0KGgoAAAANSUhEUgAAAAIAAAACCAYAAABytg0kAAAAFElEQVQImWNkYPj/n4GBgYGJAQoAHhgCAQGXeHsAAAAASUVORK5CYII=')
  ctx.route(re.compile(r'https://(image\.tmdb\.org|covers\.openlibrary\.org)/'),lambda r:r.fulfill(status=200,content_type='image/png',body=png))
  html=(WEB/'index.html').read_text();page.set_content(re.sub(r'<script.*?</script>|<link[^>]+>|<meta http-equiv="Content-Security-Policy"[^>]+>','',html));page.add_style_tag(content=(WEB/'app.css').read_text());page.evaluate(SETUP)
  for f in re.findall(r'<script src="([^"]+)"',html):page.add_script_tag(content=(WEB/f).read_text())
  page.wait_for_timeout(40)
  page.evaluate('''()=>{const previous=fetch;window.fetch=async(url,opts={})=>{
    const u=new URL(url,'http://fixture.test');
    if(u.hostname==='api.themoviedb.org'&&u.pathname.startsWith('/3/search/')){
      const number=Number(u.searchParams.get('page')||1);qa.frPages.push(number);const kind=u.pathname.endsWith('/tv')?'tv':'movie';
      if(qa.frHold)await new Promise(r=>qa.releaseFr=r);
      if(qa.frFail&&number===2)return new Response(JSON.stringify({error:'fixture quota'}),{status:429,headers:{'Content-Type':'application/json','Retry-After':'120'}});
      return new Response(JSON.stringify({page:number,total_pages:2,results:Array.from({length:3},(_,i)=>({id:(kind==='tv'?111:333)+i+(number-1)*3,media_type:kind,name:'Titre de QA série '+i,title:'Titre de QA film '+i,first_air_date:'2025-01-01',release_date:'2024-01-01',poster_path:'/qa-poster.png',vote_average:7}))}),{headers:{'Content-Type':'application/json'}});
    }
    if(u.hostname==='api.themoviedb.org'&&u.pathname==='/3/movie/333')return new Response(JSON.stringify({id:333,title:'Alien de QA',overview:'Métadonnées simulées pour régression',runtime:100,poster_path:'/qa-poster.png',genres:[],vote_average:7,credits:{cast:[]},videos:{results:[]},release_date:'2024-01-01',belongs_to_collection:{id:999,name:'Collection distante QA'}}),{headers:{'Content-Type':'application/json'}});
    if(u.hostname==='api.themoviedb.org'&&u.pathname==='/3/collection/999')return new Response(JSON.stringify({id:999,name:'Collection distante QA',parts:[{id:333,title:'Alien de QA'},{id:334,title:'Suite de QA'}]}),{headers:{'Content-Type':'application/json'}});
    return previous(url,opts);
  };}''')
  def reset():
   page.evaluate(RESET);page.evaluate('''()=>{libraryEpoch++;franchiseSearchGeneration++;recoveryOnly=null;storageDraft=null;FGRequests.clear();qa.frPages=[];qa.frFail=false;qa.frHold=false;qa.releaseFr=null;state.settings.tmdbKey='fixture-key';state.settings.catalogAutoRefresh=false;franchiseView={query:'',kind:'all',selection:'all',limit:60};overlay=null;screen='franchises';render();}''')
  def create(name='Univers de QA',alias='Titre de QA'):
   page.locator('#createUniverse').click();page.locator('#franchiseEditor [name=name]').fill(name);page.locator('#franchiseEditor [name=aliases]').fill(alias);page.locator('#franchiseEditor .confirm').click();page.locator('#franchiseEditor').wait_for(state='detached')
  def open_fr():
   page.evaluate('()=>{overlay=null;screen="franchises";render();}');page.locator('[data-fr]').first.click()
  reset();check('Fresh franchise screen provides creation without fake titles',page.locator('#createUniverse').is_visible() and page.evaluate('state.media.length')==0)
  raw=page.evaluate('localStorage.getItem(KEY)');page.locator('#createUniverse').click();page.locator('#franchiseEditor [name=name]').fill('Annulé');page.keyboard.press('Escape');page.locator('#franchiseEditor').wait_for(state='detached')
  check('Escape cancels custom definition without writing',page.evaluate('localStorage.getItem(KEY)')==raw)
  create();fid=page.evaluate('state.customFranchises[0].id');check('Created custom definition immediately persisted',page.evaluate('JSON.parse(localStorage.getItem(KEY)).customFranchises[0].name')=='Univers de QA')
  check('Empty custom group can be opened and edited',page.locator('#frEdit').is_visible() and page.locator('#franchiseRows').inner_text().startswith('Aucune'))
  page.evaluate('()=>{overlay=null;screen="more";render();}');page.locator('#newFr').click();check('Legacy More creation opens same safe editor',page.locator('#franchiseEditor').is_visible());page.locator('#franchiseEditor .cancel').click()
  page.evaluate('''()=>{const id=state.customFranchises[0].id;state.media=['show','movie','game','book'].map((k,i)=>({id:k,kind:k,title:i===1?'Alien vs Predator':'Titre de QA '+k,year:2000+i,franchise:id,franchiseId:id,franchiseName:'Univers de QA',franchiseManual:true,added:true,watched:i===1,plays:i===1?2:0,watchHistory:i===1?[1700000000000,1700000100000]:[],lastWatchedAt:i===1?1700000100000:null,userNote:'Privé '+k,rating:8,listIds:[],posterUrl:'https://image.tmdb.org/t/p/w500/qa-poster.png'}));state.suggestions=[{id:'suggested',kind:'movie',title:'À envisager',year:2020,franchise:id,franchiseManual:true,added:false,suggestion:true,listIds:[]}];save(false);screen='franchises';overlay=null;render();}''')
  check('Manually assigned crossover has one primary group',page.evaluate('groups().filter(g=>g.known>0).length')==1)
  check('All four kinds counted separately with one missing suggestion',page.evaluate('groups()[0].added===4&&groups()[0].missing===1'))
  page.locator('[data-fr]').first.click();check('Four media types and grayed suggestion render',page.locator('.franchise-work').count()==5 and page.locator('.franchise-suggestion').count()==1)
  check('Counts explicitly avoid exhaustive canon claim','non exhaustif' in page.locator('main').inner_text())
  page.locator('#frSelection').select_option('suggestions');check('Suggestions-only filter excludes library entries',page.locator('.franchise-work').count()==1)
  page.locator('#frSelection').select_option('all');page.locator('#frType').select_option('book');check('Media category filter isolates books',page.locator('.franchise-work').count()==1 and 'book' in page.locator('#franchiseRows').inner_text())
  page.locator('#frType').select_option('all');page.locator('[data-fr-position="suggested"]').click();page.locator('#franchiseOrder [name=position]').fill('1');page.locator('#franchiseOrder .confirm').click();page.locator('#franchiseOrder').wait_for(state='detached')
  check('Personal order includes grayed references',page.evaluate('groups()[0].items[0].id')=='suggested')
  check('Personal order persisted by local ID',page.evaluate('JSON.parse(localStorage.getItem(KEY)).customFranchises[0].sortOrder[0]')=='suggested')
  page.locator('#frEdit').click();page.locator('#franchiseEditor [name=name]').fill('Univers renommé');page.locator('#franchiseEditor .confirm').click();page.locator('#franchiseEditor').wait_for(state='detached')
  check('Rename updates labels while preserving order',page.evaluate('state.media.every(m=>m.franchiseName==="Univers renommé")&&state.customFranchises[0].sortOrder[0]==="suggested"'))
  check('Rename retains history scores notes and Trakt queue',page.evaluate('state.media.find(m=>m.id==="movie").plays===2&&state.media.find(m=>m.id==="movie").rating===8&&state.media.find(m=>m.id==="movie").userNote==="Privé movie"&&syncState.queue.length===0'))
  # Detail classification is authoritative, including explicit None.
  page.locator('[data-open="movie"] .open-item').click();page.locator('#mediaFranchise').select_option('');check('Explicit None records manual clear on both aliases',page.evaluate('state.media.find(m=>m.id==="movie").franchiseManual&&state.media.find(m=>m.id==="movie").franchiseId===null&&state.media.find(m=>m.id==="movie").franchise===null'))
  check('Manual None prevents Alien title re-inference',page.evaluate('groups().flatMap(g=>g.items).every(m=>m.id!=="movie")'))
  page.locator('#frAuto').click();check('Auto inference resumes only on explicit action',page.evaluate('state.media.find(m=>m.id==="movie").franchise')=='alien')
  page.locator('#mediaFranchise').select_option(fid);check('Restored manual group visible',page.evaluate('(groups().find(g=>g.id===state.customFranchises[0].id)).added')==4)
  # Refresh/manual-none and manual suggestion enrichment do not overwrite personal choice with collection label.
  page.evaluate('()=>{const m=state.media.find(m=>m.id==="movie");m.tmdbId=333;m.source="tmdb";m.sourceId="333";save(false);}')
  page.locator('#mediaFranchise').select_option('');page.evaluate('async()=>await refreshCatalogItem(state.media.find(m=>m.id==="movie"))')
  check('Manual None survives TMDb collection refresh',page.evaluate('state.media.find(m=>m.id==="movie").franchise==null&&state.media.find(m=>m.id==="movie").franchiseId==null'))
  page.locator('#mediaFranchise').select_option(fid);page.evaluate('async()=>await refreshCatalogItem(state.media.find(m=>m.id==="movie"))')
  check('Manual universe label survives provider collection name',page.evaluate('state.media.find(m=>m.id==="movie").franchiseName')=='Univers renommé')
  check('Known provider collection does not seed manual universe silently',page.evaluate('state.suggestions.length')==1)
  # Missing definitions imported from another version are not invisible.
  page.evaluate('()=>{state.media.push({id:"imported",kind:"game",title:"Référence importée",added:true,franchiseId:"lost-group",franchiseName:"Ancien univers",franchiseManual:true,listIds:[]});save(false);overlay=null;screen="franchises";render();}')
  page.locator('[data-fr="lost-group"]').click();check('Imported orphan group displayed explicitly','sans définition' in page.locator('main').inner_text())
  page.locator('#frEdit').click();page.locator('#franchiseEditor [name=name]').fill('Univers restauré');page.locator('#franchiseEditor .confirm').click();page.locator('#franchiseEditor').wait_for(state='detached')
  check('Restored definition retains old ID',page.evaluate('state.customFranchises.some(f=>f.id==="lost-group"&&f.name==="Univers restauré")'))
  page.evaluate('()=>{state.media.push({id:"pokemon",kind:"book",title:"Pokémon de QA",franchiseId:"pokemon",franchiseManual:true,added:true,listIds:[]});save(false);overlay=null;screen="franchises";render();}')
  check('Native builtin universe absent from old Web now visible',page.locator('[data-fr="pokemon"]').count()==1)
  # Def operations never alter watched state and are guarded against stale edit and storage failure.
  open_fr();page.locator('#frEdit').click();page.locator('#franchiseEditor [name=name]').fill('Ne doit pas écraser');page.evaluate('()=>{state.media[0].userNote="Modification récente";save(false);}')
  page.locator('#franchiseEditor .confirm').click();check('Stale edit refuses to overwrite later state',page.locator('#franchiseEditor .franchise-error').is_visible() and page.evaluate('state.customFranchises[0].name')=='Univers renommé');page.locator('#franchiseEditor .cancel').click()
  page.locator('#frDelete').click();old=page.evaluate('localStorage.getItem(KEY)');page.evaluate('()=>{qa.backupOriginal=createBackup;createBackup=()=>false;}');page.locator('#franchiseDelete .confirm').click();check('Delete requires successful safety copy',page.locator('#franchiseDelete .franchise-error').is_visible() and page.evaluate('localStorage.getItem(KEY)')==old);page.locator('#franchiseDelete .cancel').click();page.evaluate('()=>{createBackup=qa.backupOriginal;}')
  page.locator('#frDelete').click();page.locator('#franchiseDelete .confirm').click();page.locator('#franchiseDelete').wait_for(state='detached')
  check('Delete removes definition not media and episodes',page.evaluate('state.media.length===6&&state.suggestions.length===1&&state.media.find(m=>m.id==="movie").plays===2'))
  check('Delete detaches members and suppresses re-inference',page.evaluate('state.media.filter(m=>["show","movie","game","book"].includes(m.id)).every(m=>m.franchiseId===null&&m.franchiseManual)'))
  # Multi-category scanner supersedes the old single-category suggestion dialog.
  reset();create();page.locator('#frSearch').click()
  for kind in ['show','game','book']:page.locator(f'#frScanKinds input[value="{kind}"]').uncheck()
  page.locator('#frScanStart').click();page.wait_for_selector('[data-scan-kind="movie"] .franchise-scan-candidate')
  check('Scanner results remain temporary until batch confirmation',page.evaluate('state.suggestions.length===0&&state.media.length===0'))
  check('Scanner results show controlled provider images',page.locator('[data-scan-kind="movie"] .franchise-scan-candidate img').count()==3)
  page.evaluate('qa.frFail=true');page.locator('[data-scan-load="movie"]').click();page.wait_for_selector('[data-scan-kind="movie"] [role=alert]')
  check('Failed scanner page leaves previous candidates visible',page.locator('[data-scan-kind="movie"] .franchise-scan-candidate').count()==3)
  page.evaluate('()=>{qa.frFail=false;FGRequests.clear();}');page.locator('[data-scan-load="movie"]').click();page.wait_for_function('document.querySelectorAll("[data-scan-kind=movie] .franchise-scan-candidate").length===6')
  check('Scanner retry requests the failed page, not the following page',page.evaluate('qa.frPages')==[1,2,2])
  check('Completed first term advances to next bounded franchise alias',not page.locator('[data-scan-load="movie"]').is_disabled() and 'Continuer' in page.locator('[data-scan-load="movie"]').inner_text())
  page.locator('[data-scan-item="movie:0"]').check();page.locator('#frScanCommit').click();page.locator('#franchiseSearch').wait_for(state='detached')
  check('Chosen scan result saved only as a grayed suggestion',page.evaluate('state.suggestions.length===1&&state.media.length===0&&state.episodes.length===0'))
  check('Saved scan suggestion has no fabricated tracking state',page.evaluate('state.suggestions[0].franchiseManual&&!state.suggestions[0].watched&&state.suggestions[0].rating===null&&state.suggestions[0].watchHistory.length===0'))
  page.locator('[data-act="add"]').first.click();page.wait_for_function('overlay?.type==="item"&&state.media.length===1')
  check('Adding explicit scanner suggestion retrieves full film details',page.evaluate('state.media[0].catalogComplete&&state.suggestions.length===0'))
  check('Full add retains chosen manual group despite provider collection',page.evaluate('state.media[0].franchise===state.customFranchises[0].id&&state.media[0].franchiseName===state.customFranchises[0].name'))
  # Race: library/definition/access changes invalidate a held scanner response.
  for reason in ['library','definition','access']:
   reset();create();page.locator('#frSearch').click()
   for kind in ['show','game','book']:page.locator(f'#frScanKinds input[value="{kind}"]').uncheck()
   page.evaluate('qa.frHold=true');page.locator('#frScanStart').click();page.wait_for_function('typeof qa.releaseFr==="function"')
   if reason=='library':page.evaluate('()=>{libraryEpoch++;state.suggestions=[];}')
   elif reason=='definition':page.evaluate('()=>{state.customFranchises[0].name="Autre nom";save(false);}')
   else:page.evaluate('()=>{state.settings.tmdbKey="changed-fixture";save(false);}')
   page.evaluate('()=>{qa.frHold=false;qa.releaseFr();}');page.wait_for_timeout(80)
   check('Late scanner response discarded after '+reason,page.locator('.franchise-scan-candidate').count()==0 and page.evaluate('state.suggestions.length')==0)
   page.locator('#franchiseSearch .cancel').click()
  # Malicious/large labels, searching beyond rendered rows, keyboard and mobile.
  reset();create('<svg onload=window.frInjected=true> Univers de QA')
  check('HTML-like title rendered as inert text',page.evaluate('window.frInjected') is None and '<svg' in page.locator('main h2').inner_text())
  page.evaluate('''()=>{const id=state.customFranchises[0].id;state.media=Array.from({length:165},(_,i)=>({id:'l'+i,kind:['show','movie','game','book'][i%4],title:i===164?'Dernier titre recherché':'Œuvre de QA '+String(i).padStart(3,'0'),year:2000+i,franchise:id,franchiseManual:true,added:true,listIds:[]}));state.suggestions=[];save(false);render();}''')
  check('Franchise detail initially builds fifty rows',page.locator('.franchise-work').count()==50)
  page.locator('#frTitleFilter').fill('dernier');check('Search finds title beyond first rendered page',page.locator('.franchise-work').count()==1 and 'Dernier' in page.locator('#franchiseRows').inner_text());page.locator('#frTitleFilter').fill('')
  page.locator('#frMore').click();check('More renders next fifty references',page.locator('.franchise-work').count()==100)
  for width in [390,768,1360]:
   page.set_viewport_size({'width':width,'height':900});page.screenshot(path=str(OUT/f'fixture-franchise-{width}.png'))
   check('Group fits viewport '+str(width),page.evaluate('document.documentElement.scrollWidth<=innerWidth+1'))
   page.locator('#frEdit').click();page.screenshot(path=str(OUT/f'fixture-franchise-editor-{width}.png'))
   check('Editor fits viewport '+str(width),page.locator('#franchiseEditor').evaluate('(el)=>el.getBoundingClientRect().width<=innerWidth+1&&el.scrollWidth<=el.clientWidth+1'))
   page.keyboard.press('Escape');page.locator('#franchiseEditor').wait_for(state='detached');check('Editor closes with Escape '+str(width))
  portable=page.evaluate('toPortableBackup(state)');check('Portable format latest V9',portable['schema']==11)
  check('Portable custom definition metadata preserved',page.evaluate('fromPortableBackup(toPortableBackup(state)).customFranchises[0].name')==portable['customFranchises'][0]['name'])
  # Quota: mutate only after successful write, fallback keeps draft available.
  reset();raw=page.evaluate('localStorage.getItem(KEY)');page.locator('#createUniverse').click();page.locator('#franchiseEditor [name=name]').fill('Brouillon de QA');page.evaluate('()=>{qa.origSet=localStorage.setItem;localStorage.setItem=(k,v)=>{if(k===KEY)throw Error("quota fixture");return qa.origSet(k,v)};}');page.locator('#franchiseEditor .confirm').click()
  check('Storage refusal leaves no falsely persisted custom definition',page.evaluate('state.customFranchises.length')==0 and page.evaluate('localStorage.getItem(KEY)')==raw)
  check('Storage refusal retains draft and explicit error',page.evaluate('storageDraft.customFranchises[0].name')=='Brouillon de QA' and page.locator('#franchiseEditor .franchise-error').is_visible());page.evaluate('()=>{localStorage.setItem=qa.origSet;}')
  check('No uncaught JS errors in new DOM scenarios',not errors)
  (OUT/'browser-v019.json').write_text(json.dumps({'version':'0.19.0','scope':'real DOM/scripts; controlled API/images/storage; no real account or origin install','passed':len(checks),'checks':checks,'errors':errors},ensure_ascii=False,indent=2));print('V0.19 Chromium assertions:',len(checks));b.close()
if __name__=='__main__':
 try:run()
 except Exception:
  (OUT/'browser-v019-failure.json').write_text(json.dumps({'checks':checks,'traceback':traceback.format_exc()},ensure_ascii=False,indent=2));raise
