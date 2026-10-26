"""V0.10 actual DOM/application scripts with explicitly controlled API/storage/cache fixtures.
CSP meta is omitted ONLY from this in-memory fixture to inject scripts. No real origin,
CSP enforcement, real OAuth, PWA installation or Android UI is validated by this suite.
"""
from pathlib import Path
from playwright.sync_api import sync_playwright
from browser_harness import SETUP,RESET,AUTH,ROOT,WEB
import re,json,traceback,sys,base64,time
OUT=ROOT/'tests/results/v010';OUT.mkdir(exist_ok=True,parents=True)
checks=[];measurements={}
def check(name,condition=True):
 if not condition: raise AssertionError(name)
 checks.append({'name':name,'passed':True});print('PASS',name,flush=True)
EXTRA=r'''() => {
 Object.defineProperties(localStorage,{length:{get:()=>Object.keys(__qaStorage).length},key:{value:i=>Object.keys(__qaStorage)[i]??null}});
 window.qaOpen=id=>{overlay={type:"item",id,src:"media"};render();};
 const real=window.fetch;qa.reviewCalls=[];
 qa.reviewRows=Array.from({length:23},(_,i)=>({id:i+1,comment:i===0?'SECRET SPOILER à ne pas révéler':i===1?'<img src=x onerror="window.__injected=1"> titre':'Avis numéro '+(i+1)+' de test seulement',spoiler:i===0,likes:i,user:{username:i<2?'qa-user':'other-user',name:'Auteur '+(i+1)}}));
 window.fetch=async(url,opts={})=>{
  const u=new URL(String(url),'https://test.invalid'),path=u.pathname,method=opts.method||'GET';
  if(u.hostname==='api.trakt.tv'&&path.includes('/comments')){
   qa.reviewCalls.push({path,search:u.search,method,body:opts.body?JSON.parse(opts.body):null});
   if(method==='GET'&&path.includes('/comments/likes')){
    if(qa.pauseReviews){qa.reviewWaiting=true;await new Promise(r=>qa.releaseReviews=r);}
    const page=Number(u.searchParams.get('page')||1);
    if(qa.reviewFailPage===page)return new Response('{}',{status:503});
    const rows=qa.badReview?[{id:'1" data-injected="1',comment:'Invalid identifier'}]:qa.reviewRows.slice((page-1)*20,page*20);
    return new Response(JSON.stringify(rows),{status:200,headers:{'Content-Type':'application/json','X-Pagination-Page-Count':'2'}});
   }
   if(method==='GET')return new Response(JSON.stringify(qa.reviewRows.find(x=>x.id===Number(path.split('/').pop()))||{id:999,comment:'Un avis autre compte distant',user:{username:'other-user'}}),{status:200});
   if(qa.pauseWrite){qa.writeWaiting=true;await new Promise(r=>qa.releaseWrite=r);}
   if(qa.writeStatus)return new Response('{}',{status:qa.writeStatus});
   if(method==='DELETE'){qa.reviewRows=qa.reviewRows.filter(x=>x.id!==Number(path.split('/').pop()));return new Response(null,{status:204});}
   const body=JSON.parse(opts.body),id=method==='PUT'?Number(path.split('/').pop()):100+qa.reviewRows.length;
   const posted={id,comment:body.comment,spoiler:body.spoiler,review:body.review,user:{username:'qa-user',name:'QA'}};
   qa.reviewRows=qa.reviewRows.filter(x=>x.id!==id);qa.reviewRows.unshift(posted);
   return new Response(JSON.stringify(posted),{status:method==='POST'?201:200});
  }
  return real(url,opts);
 };
 qa.caches=new Map([['franchiseguide-web-v010',1],['franchiseguide-images-v010',2],['other-app',3]]);
 Object.defineProperty(window,'caches',{configurable:true,value:{keys:async()=>[...qa.caches.keys()],delete:async k=>qa.caches.delete(k)}});
}'''

def run():
 with sync_playwright() as p:
  browser=p.chromium.launch(executable_path='/usr/bin/chromium',headless=True,args=['--no-sandbox'])
  ctx=browser.new_context(viewport={'width':1360,'height':1000},locale='fr-FR',timezone_id='Europe/Paris')
  page=ctx.new_page();page.set_default_timeout(6000);errors=[];dialogs=[];dialog_mode={'cancel':False,'prompt':''}
  page.on('pageerror',lambda e:errors.append(str(e)))
  def dialog(d):
   dialogs.append(d.message)
   if dialog_mode['cancel']:d.dismiss()
   else:d.accept(dialog_mode['prompt'] if d.type=='prompt' else None)
  page.on('dialog',dialog)
  png=base64.b64decode('iVBORw0KGgoAAAANSUhEUgAAAAIAAAACCAYAAABytg0kAAAAFElEQVQImWNkYPj/n4GBgYGJAQoAHhgCAQGXeHsAAAAASUVORK5CYII=')
  ctx.route(re.compile(r'https://(image\.tmdb\.org|covers\.openlibrary\.org)/'),lambda q:q.fulfill(status=200,content_type='image/png',body=png))
  page.set_content(re.sub(r'<script.*?</script>|<link[^>]+>|<meta http-equiv="Content-Security-Policy"[^>]+>','',(WEB/'index.html').read_text()))
  page.add_style_tag(content=(WEB/'app.css').read_text());page.evaluate(SETUP)
  for file in re.findall(r'<script src="([^"]+)"',(WEB/'index.html').read_text()):page.add_script_tag(content=(WEB/file).read_text())
  page.wait_for_timeout(25);page.evaluate(EXTRA)
  def reset():
   page.evaluate(RESET);page.evaluate("()=>{reviewSessions.clear();qa.reviewCalls=[];qa.reviewFailPage=0;qa.writeStatus=0;qa.badReview=false;qa.pauseReviews=false;qa.pauseWrite=false;qa.reviewWaiting=false;qa.writeWaiting=false;recoveryOnly=null;loadIssue=null;window.__injected=0;}")
  def review_page():
   reset();page.evaluate(AUTH)
   page.evaluate("()=>{state.media=[{id:'review-film',tmdbId:333,traktId:555,kind:'movie',title:'Film fictif de validation',genres:['Test'],ratings:[],credits:[],added:true}];save(false);qaOpen('review-film');}")
   try:page.wait_for_function("document.querySelectorAll('[data-review-id]').length===20")
   except:
    print("DEBUG",page.evaluate("()=>({text:document.querySelector('main').innerText, sessions:[...reviewSessions.values()],calls:qa.reviewCalls})"),errors);raise
  review_page()
  check('Review first page renders twenty distinct cards',page.locator('[data-review-id]').count()==20)
  check('Pagination requests page one explicitly',page.evaluate('qa.reviewCalls[0].search.includes("page=1")'))
  check('Spoiler text is initially masked','SECRET SPOILER' not in page.locator('[data-review-id="1"]').inner_text())
  page.locator('[data-show-spoiler="1"]').click();check('Spoiler can be revealed','SECRET SPOILER' in page.locator('[data-review-id="1"]').inner_text())
  page.locator('[data-show-spoiler="1"]').click();check('Spoiler can be hidden again','SECRET SPOILER' not in page.locator('[data-review-id="1"]').inner_text())
  check('Remote comment HTML is inert text',page.locator('[data-review-id="2"] img').count()==0 and page.evaluate('__injected')==0)
  check('Only own comments expose edit controls',page.locator('[data-edit-review]').count()==2)
  page.evaluate('qa.reviewFailPage=2');page.locator('#reviewsMore').click();page.wait_for_function('reviewSession(state.media[0]).error!=null')
  check('Failed review page keeps existing cards',page.locator('[data-review-id]').count()==20)
  check('Failed page does not advance page index',page.evaluate('reviewSession(state.media[0]).page')==1)
  page.evaluate('qa.reviewFailPage=0');page.locator('#reviewsMore').click();page.wait_for_function('reviewSession(state.media[0]).page===2')
  check('Retry loads remaining review page once',page.locator('[data-review-id]').count()==23)
  check('Final review page removes pagination action',page.locator('#reviewsMore').count()==0)
  page.evaluate('qa.reviewFailPage=1');page.evaluate('async()=>{await loadTraktComments(state.media[0],true)}')
  check('Failed review refresh also preserves visible cache',page.locator('[data-review-id]').count()==23)
  page.evaluate('qa.reviewFailPage=0')
  page.locator('#reviewText').fill('Un avis trop bref');page.locator('#reviewSubmit').click()
  check('Four-word review blocked before network',page.evaluate('qa.reviewCalls.every(c=>c.method==="GET")'))
  check('Minimum-word validation shown inline','5 mots' in page.locator('#reviewDraftStatus').inner_text())
  text='Voici un avis assez détaillé pour validation'
  page.locator('#reviewText').fill(text)
  check('Draft saved without sending anything',page.evaluate('Object.values(reviewDrafts())[0].text')==text and page.evaluate('qa.reviewCalls.every(c=>c.method==="GET")'))
  page.evaluate('()=>{reviewSessions.clear();qaOpen("review-film");}');page.wait_for_selector('#reviewText')
  check('Draft restored after reopening the detail',page.locator('#reviewText').input_value()==text)
  dialog_mode['cancel']=True;page.locator('#reviewSubmit').click();dialog_mode['cancel']=False
  check('Cancel publication sends no write',page.evaluate('qa.reviewCalls.every(c=>c.method==="GET")'))
  page.evaluate('qa.pauseWrite=true');page.locator('#reviewSubmit').click();page.wait_for_function('qa.writeWaiting')
  check('Pending publication disables the send button',page.locator('#reviewSubmit').is_disabled())
  check('Pending publication prevents losing edited draft text',page.locator('#reviewText').is_disabled())
  page.locator('#reviewForm').evaluate('(f)=>f.dispatchEvent(new Event("submit",{bubbles:true,cancelable:true}))')
  check('Repeated submit event sends a single POST',page.evaluate('qa.reviewCalls.filter(c=>c.method==="POST").length')==1)
  check('Uncertain draft persisted before request completion',page.evaluate('Object.values(reviewDrafts())[0].uncertain'))
  page.evaluate('()=>{qa.pauseWrite=false;qa.releaseWrite();}');page.wait_for_function('!reviewSession(state.media[0]).sending');page.wait_for_function('Object.keys(reviewDrafts()).length===0')
  check('Confirmed publication clears persisted draft',page.locator('#reviewText').input_value()=='')
  check('POST contains correct media and no raw token in body',page.evaluate('()=>{const p=qa.reviewCalls.find(c=>c.method==="POST").body;return p.movie.ids.trakt===555&&!JSON.stringify(p).includes("qa-access")}'))
  page.locator('#reviewText').fill('Cet envoi perd sa confirmation serveur')
  page.evaluate('qa.writeStatus=503');page.locator('#reviewSubmit').click();page.wait_for_function('!reviewSession(state.media[0]).sending')
  count=page.evaluate('qa.reviewCalls.filter(c=>c.method==="POST").length')
  check('HTTP 503 keeps an uncertain draft',page.evaluate('reviewSession(state.media[0]).draft.uncertain'))
  check('Uncertain write cannot be blindly submitted again',page.locator('#reviewSubmit').is_disabled())
  page.evaluate('()=>{reviewSessions.clear();qaOpen("review-film");}');page.wait_for_selector('#reviewConfirmAbsent')
  check('Uncertain state survives reopening',page.locator('#reviewSubmit').is_disabled())
  page.locator('#reviewConfirmAbsent').click()
  check('Explicit reconciliation re-enables edit and submit',not page.locator('#reviewSubmit').is_disabled() and not page.locator('#reviewText').is_disabled())
  check('Reconciliation itself sends no write',page.evaluate('qa.reviewCalls.filter(c=>c.method==="POST").length')==count)
  page.evaluate('qa.writeStatus=422');page.locator('#reviewSubmit').click();page.wait_for_function('!reviewSession(state.media[0]).sending')
  check('Explicit 422 rejection does not claim successful publication',page.evaluate('reviewSession(state.media[0]).draft.text.length>0&&!reviewSession(state.media[0]).draft.uncertain'))
  page.evaluate('qa.writeStatus=0');page.locator('#reviewDiscard').click();page.wait_for_function('reviewSession(state.media[0]).draft.text===""')
  check('Discarding draft does not delete public review',page.evaluate('qa.reviewCalls.filter(c=>c.method==="DELETE").length')==0)
  page.locator('[data-edit-review="1"]').click();page.locator('#reviewText').fill('Cet avis personnel vient être modifié')
  page.locator('#reviewSubmit').click();page.wait_for_function('!reviewSession(state.media[0]).sending')
  check('Editing existing own review issues a PUT, not POST',page.evaluate('qa.reviewCalls.some(c=>c.method==="PUT"&&c.path==="/comments/1")'))
  check('Ownership is rechecked before an update',page.evaluate('qa.reviewCalls.some(c=>c.method==="GET"&&c.path==="/comments/1")'))
  page.locator('[data-delete-review="1"]').click();page.wait_for_function('!reviewSession(state.media[0]).sending')
  check('Explicit own-review deletion calls DELETE',page.evaluate('qa.reviewCalls.some(c=>c.method==="DELETE"&&c.path==="/comments/1")'))
  check('Deleted review removed from displayed list',page.locator('[data-review-id="1"]').count()==0)
  # Corrupt response is not interpreted as markup/partial page.
  page.evaluate('qa.badReview=true');existing=page.locator('[data-review-id]').count();page.evaluate('async()=>{await loadTraktComments(state.media[0],true)}')
  check('Malformed remote ID fails without losing prior page',page.locator('[data-review-id]').count()==existing and page.locator('[data-injected]').count()==0)
  page.evaluate('qa.badReview=false')
  # Quota failure is surfaced before transmission, not silently ignored.
  page.locator('#reviewText').fill('Un autre avis enregistré avant publication')
  page.evaluate("()=>{window.originalSet=localStorage.setItem;localStorage.setItem=(k,v)=>{if(k===REVIEW_DRAFT_KEY)throw Error('Quota de test');return originalSet(k,v)};}")
  count=page.evaluate('qa.reviewCalls.filter(c=>c.method==="POST").length');page.locator('#reviewSubmit').click()
  check('Failed draft commit prevents POST',page.evaluate('qa.reviewCalls.filter(c=>c.method==="POST").length')==count)
  page.evaluate('localStorage.setItem=originalSet')
  # Account-scoped drafts: a new connection cannot reveal or send the prior account draft.
  page.evaluate("()=>{authGeneration++;saveTraktAuth({accessToken:'qa-next',refreshToken:'qa-r',expiresAt:Math.floor(Date.now()/1000)+86400,username:'other-user'});qaOpen('review-film');}");page.wait_for_selector('#reviewText')
  check('Different account has a separate empty draft',page.locator('#reviewText').input_value()=='')
  check('Old account draft retained without being transferred',page.evaluate('Object.keys(reviewDrafts()).some(k=>k.startsWith("qa-user:"))'))
  # Per-title catalogue language, full-season refresh and atomic rollback on partial response.
  reset();page.evaluate("async()=>{state.settings.tmdbKey='qa';state.settings.language='fr-FR';remoteResults=[{id:'tmdb-show-111',tmdbId:111,kind:'show',title:'Série de validation',source:'tmdb'}];await add('tmdb-show-111');Object.assign(state.episodes[1],FGCore.episodeState(state.episodes[1],'watched',1700000000000));save();render();}")
  page.locator('#titleLanguage').fill('en-US');page.locator('#saveTitleLanguage').click();page.wait_for_function('state.media[0].contentLanguage==="en-US"')
  check('Title language is applied only after complete refresh',page.evaluate('state.media[0].contentLanguage')=='en-US')
  check('General language remains unchanged',page.evaluate('state.settings.language')=='fr-FR')
  check('Season refresh uses the per-title language',page.evaluate('qa.calls.some(c=>c.path.endsWith("/season/2")&&c.search.includes("language=en-US"))'))
  check('Language refresh preserves all five episodes and watched state',page.evaluate('state.episodes.length===5&&state.episodes.filter(e=>e.watched).length===1'))
  check('Per-title language survives portable backup round trip',page.evaluate('fromPortableBackup(toPortableBackup(state)).media[0].contentLanguage')=='en-US')
  page.evaluate('qa.badSeason=true');page.locator('#titleLanguage').fill('de-DE');page.locator('#saveTitleLanguage').click();page.wait_for_function('catalogBusy.size===0')
  check('Partial translated season leaves old language and episodes intact',page.evaluate('state.media[0].contentLanguage==="en-US"&&state.episodes.length===5'))
  check('Failed catalogue read now exposes a retry deadline',page.evaluate('catalogRows()[0].retryAt>Date.now()'))
  # V0.20 deliberately applies its stored retry deadline to manual language refresh too.
  page.evaluate('()=>{qa.beforeCatalogClock=Date.now;qa.badSeason=false;const at=catalogRows()[0].retryAt+1;Date.now=()=>at;}');page.locator('#titleLanguage').fill('');page.locator('#saveTitleLanguage').click();page.wait_for_function('state.media[0].contentLanguage===null');page.evaluate('Date.now=qa.beforeCatalogClock')
  check('Clearing override returns to general catalogue language',page.evaluate('state.settings.language==="fr-FR"&&state.episodes.filter(e=>e.watched).length===1'))
  page.set_viewport_size({'width':390,'height':844});page.screenshot(path=str(OUT/'fixture-series-v010-390.png'),full_page=True)
  # More screen and keyboard support, imported-identifier injection cases.
  reset();page.evaluate("()=>{state.lists=[{id:'x\" data-injected=\"yes',name:'Liste de test'}];screen='more';render();}")
  check('Imported list IDs do not create HTML attributes',page.locator('[data-injected]').count()==0)
  page.locator('[data-list]').click();check('Escaped list identifier remains navigable','Liste de test' in page.locator('main').inner_text())
  reset();page.evaluate("()=>{screen='more';render();}")
  def create_user_universe(name):
   page.evaluate("()=>{overlay=null;screen='more';render();}");page.locator('#newFr').click();page.locator('#franchiseEditor [name=name]').fill(name);page.locator('#franchiseEditor .confirm').click()
  create_user_universe('Même Franchise');page.locator('#franchiseEditor').wait_for(state='detached')
  create_user_universe('Même Franchise')
  check('Duplicate custom franchise names do not create duplicate IDs',page.locator('#franchiseEditor .franchise-error').is_visible() and page.evaluate('state.customFranchises.length===1&&FGCore.validateBackup(toPortableBackup(state))'))
  page.locator('#franchiseEditor .cancel').click();page.locator('#franchiseEditor').wait_for(state='detached')
  create_user_universe('😀');page.locator('#franchiseEditor').wait_for(state='detached');create_user_universe('👾');page.locator('#franchiseEditor').wait_for(state='detached')
  check('Non-Latin franchise names receive distinct identifiers',page.evaluate('new Set(state.customFranchises.map(x=>x.id)).size===3'))
  reset();page.evaluate("()=>{state.media=[{id:'k',kind:'movie',title:'Film clavier',added:true,genres:['__proto__','constructor']}];save(false);screen='movies';render();}")
  check('Title is a native keyboard-operable button',page.locator('.open-item').first.evaluate('(e)=>e.tagName==="BUTTON"'))
  check('Action buttons are not nested inside a role button',page.locator('[role="button"] button').count()==0)
  page.locator('.open-item').first.focus();page.keyboard.press('Enter');check('Enter opens selected title',page.locator('.detail-poster').count()==1)
  page.evaluate("()=>{overlay={type:'stats'};render();}");check('Unusual imported genre names do not break statistics','__proto__' in page.locator('main').inner_text())
  page.evaluate("()=>{overlay=null;screen='more';render();}");page.locator('[data-quick="setup"]').click()
  check('Setup now discloses keyless providers and publisher activation','TVmaze' in page.locator('main').inner_text() and 'FreeToGame' in page.locator('main').inner_text() and 'Clé manquante' not in page.locator('main').inner_text())
  page.locator('[data-add-kind="game"]').click();check('Setup add game opens contextual search',page.evaluate('kindFilter')=='game');page.locator('#searchDialog .close').click()
  page.evaluate("()=>{overlay={type:'privacy'};render();}")
  for width,height in [(390,844),(768,1024),(1360,1000)]:
   page.set_viewport_size({'width':width,'height':height})
   check('Privacy screen has no horizontal overflow '+str(width),page.evaluate('document.documentElement.scrollWidth<=innerWidth+1'))
  page.screenshot(path=str(OUT/'fixture-privacy-v010.png'),full_page=True)
  page.locator('#privacyErase').click();check('Local erasure requires typed confirmation','EFFACER' in page.locator('#privacyStatus').inner_text())
  page.locator('#privacyConfirm').fill('EFFACER');page.evaluate('traktSyncing=true');page.locator('#privacyErase').click()
  check('Local erasure refused during Trakt write','écriture Trakt' in page.locator('#privacyStatus').inner_text());page.evaluate('traktSyncing=false')
  page.evaluate("()=>{localStorage.setItem('other-app','keep');localStorage.setItem('franchiseguide-some-old-cache','private');}")
  before=page.evaluate('qa.reviewCalls.length');page.locator('#privacyErase').click();page.wait_for_function('document.querySelector("#privacyErase").disabled');page.wait_for_function('qa.caches.size===1')
  check('Erasure removes all FranchiseGuide local keys',page.evaluate('Object.keys(__qaStorage).every(k=>!k.startsWith("franchiseguide-"))'))
  check('Erasure preserves unrelated application storage',page.evaluate('localStorage.getItem("other-app")')=='keep')
  check('Erasure removes only FranchiseGuide caches',page.evaluate('JSON.stringify([...qa.caches.keys()])')=='["other-app"]')
  check('Erasure sends no remote deletion request',page.evaluate('qa.reviewCalls.length')==before)
  check('Erasure blocks automatic resave before reload',page.evaluate('storageWritable===false&&state.media.length===0'))
  # Strongly invalid stored preferences fail closed, not only unknown top-level schema.
  reset();check('Invalid stored preferences block writes and preserve raw JSON',page.evaluate("()=>{const raw=JSON.stringify({schema:6,media:[],preferences:[]});localStorage.setItem(KEY,raw);decodeStored(raw,KEY);return !!recoveryOnly&&recoveryOnly.raw===raw&&localStorage.getItem(KEY)===raw;}"))
  reset()
  # Synthetic large-library measurement: bounded DOM, all-title search. Not a mobile benchmark.
  duration=page.evaluate("()=>{state.media=Array.from({length:10000},(_,i)=>({id:'scale-'+i,kind:'movie',title:'Film synthétique '+i,added:true}));const t=performance.now();screen='movies';render();return performance.now()-t}")
  measurements['10000_movies_initial_render_ms']=duration
  check('10000-title library keeps initial rendering bounded to 80',page.locator('[data-open]').count()==80)
  check('10000-title render completes under generous local 5s guard',duration<5000)
  page.evaluate("()=>{libraryViews.set('movie',{limit:80,query:'9999'});render();}")
  # Use the actual filter input when available instead of internal-only filter state.
  if page.locator('[data-library-query]').count():page.locator('[data-library-query]').fill('9999')
  check('All new scenarios avoid uncaught runtime errors',not errors)
  measurements['console_errors']=errors;measurements['confirmations_shown']=len(dialogs)
  browser.close()
 return 0
if __name__=='__main__':
 failed=None
 try:code=run()
 except Exception as e:traceback.print_exc();failed=str(e);code=1
 (OUT/'browser-v010-results.json').write_text(json.dumps({'mode':__doc__,'passed':len(checks),'failed':failed,'measurements':measurements,'checks':checks},ensure_ascii=False,indent=2));sys.exit(code)
