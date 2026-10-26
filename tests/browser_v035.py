"""V0.35 Today/check-in/backup safety. Real production DOM/scripts, controlled API/storage/images."""
from pathlib import Path
from playwright.sync_api import sync_playwright
from browser_harness import SETUP,RESET,AUTH,ROOT,WEB
import re,json,base64,traceback
OUT=ROOT/'tests/results/v035';OUT.mkdir(parents=True,exist_ok=True);checks=[]
def check(name,value=True):
 if not value:raise AssertionError(name)
 checks.append({'name':name,'passed':True});print('PASS',name,flush=True)
EXTRA=r'''()=>{
 const base=fetch;qa.checkStartFail=false;qa.checkDeleteFail=false;qa.checkHistory=[];qa.checkCalls=[];
 window.fetch=async(url,options={})=>{
   const u=new URL(String(url),'http://qa.test'),method=options.method||'GET';
   if(u.hostname==='api.trakt.tv'&&u.pathname==='/checkin'){
     qa.checkCalls.push({method,path:u.pathname,body:options.body?JSON.parse(options.body):null});
     if(method==='POST'){if(qa.checkStartFail)throw Error('Controlled lost start acknowledgement');return new Response(JSON.stringify({started_at:new Date().toISOString()}),{status:200,headers:{'Content-Type':'application/json'}});}
     if(method==='DELETE'){if(qa.checkDeleteFail){qa.checkDeleteFail=false;throw Error('Controlled cleanup loss');}return new Response(JSON.stringify({}),{status:200,headers:{'Content-Type':'application/json'}});}
   }
   if(u.hostname==='api.trakt.tv'&&method==='GET'&&u.pathname.startsWith('/users/me/history/')){qa.checkCalls.push({method,path:u.pathname});return new Response(JSON.stringify(qa.checkHistory),{status:200,headers:{'Content-Type':'application/json'}});}
   return base(url,options);
 };
 window.qaSeed=()=>{
   clearInterval(todayTimer);localStorage.clear();storageRevision=0;storageWritable=true;recoveryOnly=null;loadIssue=null;FGRequests.clear();authGeneration++;traktSyncing=false;refreshPromise=null;syncState={queue:[],baseline:{},remote:[],errors:[]};
   state=clone(seed);state.settings.catalogAutoRefresh=false;state.settings.autoBackupEnabled=false;state.settings.traktAutoSync=false;state.settings.detailJingleEnabled=false;
   state.media=[{id:'movie',kind:'movie',title:'Film check-in QA',tmdbId:333,source:'tmdb',sourceId:'333',added:true,listIds:[],runtimeMinutes:100,releaseDate:today(),watchHistory:[],plays:0},{id:'show',kind:'show',title:'Série check-in QA',tmdbId:111,source:'tmdb',sourceId:'111',added:true,listIds:[],watchHistory:[]}];
   state.episodes=[{id:'episode',showId:'show',title:'Épisode QA',season:1,num:1,tmdbId:11111,runtimeMinutes:45,airDate:today(),watchHistory:[],plays:0,watched:false,skipped:false}];
   savedState=clone(state);save(false);saveTraktAuth({accessToken:'qa-access',refreshToken:'qa-refresh',expiresAt:Math.floor(Date.now()/1000)+86400,username:'qa-user'});syncState.account='qa-user';savedState=clone(state);qa.checkStartFail=false;qa.checkDeleteFail=false;qa.checkHistory=[];qa.checkCalls=[];overlay=null;screen='more';render();
 };
}'''
def run():
 with sync_playwright() as p:
  b=p.chromium.launch(executable_path='/usr/bin/chromium',headless=True,args=['--no-sandbox']);ctx=b.new_context(viewport={'width':1360,'height':1000},locale='fr-FR',timezone_id='Europe/Paris');page=ctx.new_page();page.set_default_timeout(6000);errors=[]
  page.on('pageerror',lambda e:errors.append(str(e)));page.on('dialog',lambda d:d.accept())
  png=base64.b64decode('iVBORw0KGgoAAAANSUhEUgAAAAIAAAACCAYAAABytg0kAAAAFElEQVQImWNkYPj/n4GBgYGJAQoAHhgCAQGXeHsAAAAASUVORK5CYII=')
  ctx.route(re.compile(r'https://image\.tmdb\.org/'),lambda r:r.fulfill(status=200,content_type='image/png',body=png))
  html=(WEB/'index.html').read_text();page.set_content(re.sub(r'<script.*?</script>|<link[^>]+>|<meta http-equiv="Content-Security-Policy"[^>]+>','',html));page.add_style_tag(content=(WEB/'app.css').read_text());page.evaluate(SETUP)
  for f in re.findall(r'<script src="([^"]+)"',html):page.add_script_tag(content=(WEB/f).read_text())
  page.evaluate(EXTRA);page.evaluate('qaSeed()')
  check('Application boots with V0.35 modules',not errors and page.evaluate('!!FGCheckIn&&!!FGBackupSafety&&!!FGToday'))
  check('Today route is exposed in More',page.locator('[data-quick="today"]').count()==1)
  page.locator('[data-quick="today"]').click();check('Today combines agenda and ready records',page.locator('.today-record').count()>=2 and 'Aucun check-in actif' in page.locator('main').inner_text())
  check('No external backup is initially reported as verified','jamais' in page.locator('.backup-safety').inner_text())
  page.locator('[data-backup-export="json"]').click();check('Download proposal remains pending, not verified',page.evaluate('backupSafetyStatus().pending&&backupSafetyStatus().verifiedAt===null'))
  page.locator('[data-backup-verified]').click();check('Explicit file verification clears due reminder',page.evaluate('!backupSafetyStatus().due&&backupSafetyStatus().verifiedAt>0'))
  # Start a movie check-in from the detail.
  page.evaluate("()=>{overlay={type:'item',id:'movie',src:'media',title:'Film'};render();}");page.locator('[data-trakt-checkin="movie"]').click();page.wait_for_function('checkInRead()?.status==="active"')
  check('Start posts only provider identity and app metadata',page.evaluate('qa.checkCalls[0].body.movie.ids.tmdb')==333 and 'Film check-in QA' not in json.dumps(page.evaluate('qa.checkCalls[0].body')))
  check('Start never marks local movie watched',not page.evaluate('state.media.find(m=>m.id==="movie").watched'))
  check('Active session persists account and app binding',page.evaluate('checkInRead().account==="qa-user"&&(checkInRead().appRef??null)===(state._traktAppRef??null)'))
  page.locator('[data-checkin-finish]').click();page.wait_for_function('checkInRead()===null')
  check('History acknowledgement marks movie exactly once',page.evaluate('state.media.find(m=>m.id==="movie").plays')==1 and len(page.evaluate('state.media.find(m=>m.id==="movie").watchHistory'))==1)
  check('Finish cleans remote check-in after history confirmation',page.evaluate('qa.checkCalls.some(c=>c.method==="DELETE")'))
  # Episode cancel does not mark watched.
  page.evaluate("()=>{overlay={type:'item',id:'show',src:'media',title:'Série'};render();}");page.locator('[data-trakt-checkin="episode"]').click();page.wait_for_function('checkInRead()?.kind==="episode"');page.locator('[data-checkin-cancel]').click();page.wait_for_function('checkInRead()===null')
  check('Cancel leaves episode tracking unchanged',not page.evaluate('state.episodes[0].watched') and page.evaluate('state.episodes[0].plays')==0)
  # Lost finish acknowledgement: do not mark watched, then verify remote history.
  page.evaluate("()=>{overlay={type:'item',id:'movie',src:'media',title:'Film'};render();}");page.locator('[data-trakt-checkin="movie"]').click();page.wait_for_function('checkInRead()?.status==="active"');page.evaluate('qa.lostHistory=true');page.locator('[data-checkin-finish]').click();page.wait_for_function('checkInRead()?.status==="uncertain-finish"')
  check('Lost finish acknowledgement does not mark local history',page.evaluate('state.media.find(m=>m.id==="movie").plays')==1)
  page.evaluate("()=>{const s=checkInRead();qa.checkHistory=[{watched_at:new Date(s.finishAt+2000).toISOString(),movie:{ids:{tmdb:s.tmdbId}}}];qa.lostHistory=false;}");page.locator('[data-checkin-verify]').click();page.wait_for_function('checkInRead()===null')
  check('Remote history verification adds one dated rewatch',page.evaluate('state.media.find(m=>m.id==="movie").plays')==2)
  # Cleanup loss happens after confirmed history, so local watch is safe and cleanup remains explicit.
  page.evaluate("()=>{overlay={type:'item',id:'show',src:'media',title:'Série'};render();}");page.locator('[data-trakt-checkin="episode"]').click();page.wait_for_function('checkInRead()?.status==="active"');page.evaluate('qa.checkDeleteFail=true');page.locator('[data-checkin-finish]').click();page.wait_for_function('checkInRead()?.status==="cleanup"')
  check('Cleanup failure occurs after local episode confirmation',page.evaluate('state.episodes[0].watched&&state.episodes[0].plays===1'))
  page.locator('[data-checkin-cleanup]').click();page.wait_for_function('checkInRead()===null');check('Cleanup retry clears only check-in reference',page.evaluate('state.episodes[0].watched'))
  # Lost start result is kept as uncertain; user can cancel without history mutation.
  page.evaluate("()=>{overlay={type:'item',id:'movie',src:'media',title:'Film'};render();qa.checkStartFail=true;}");page.locator('[data-trakt-checkin="movie"]').click();page.wait_for_function('checkInRead()?.status==="uncertain-start"')
  check('Lost start acknowledgement creates visible uncertain session', 'incertain' in page.locator('.checkin-card').inner_text().lower())
  check('Uncertain start still leaves watch count unchanged',page.evaluate('state.media.find(m=>m.id==="movie").plays')==2)
  page.evaluate('qa.checkStartFail=false');page.locator('[data-checkin-cancel]').click();page.wait_for_function('checkInRead()===null')
  # Account mismatch blocks remote controls but allows explicit local forget.
  page.evaluate("()=>{overlay={type:'item',id:'movie',src:'media',title:'Film'};render();}");page.locator('[data-trakt-checkin="movie"]').click();page.wait_for_function('checkInRead()?.status==="active"');page.evaluate('()=>saveTraktAuth({...traktAuth(),username:"other-user"})');page.evaluate("()=>{overlay={type:'today',title:'Aujourd’hui'};render();}")
  check('Different account cannot finish or cancel old session',page.locator('[data-checkin-finish]').count()==0 and page.locator('[data-checkin-cancel]').count()==0)
  page.locator('[data-checkin-forget]').click();page.wait_for_function('checkInRead()===null');check('Explicit local forget does not alter watched history',page.evaluate('state.media.find(m=>m.id==="movie").plays')==2)
  # When the estimated runtime is over Trakt may already have auto-completed the check-in.
  page.evaluate("()=>{saveTraktAuth({...traktAuth(),username:'qa-user'});overlay={type:'item',id:'movie',src:'media',title:'Film'};render();}");page.locator('[data-trakt-checkin="movie"]').click();page.wait_for_function('checkInRead()?.status==="active"')
  page.evaluate("()=>{const s=checkInRead(),at=Date.now()+5000;checkInWrite({...s,expectedEndAt:Date.now()-1});qa.checkHistory=[{watched_at:new Date(at).toISOString(),movie:{ids:{tmdb:s.tmdbId}}}];qa.beforeDueHistoryPosts=qa.calls.filter(c=>c.method==='POST'&&c.path==='/sync/history').length;overlay={type:'today',title:'Aujourd’hui'};render();}")
  page.locator('[data-checkin-finish]').click();page.wait_for_function('checkInRead()===null')
  check('Due check-in verifies remote auto-history before local update',page.evaluate('qa.checkCalls.some(c=>c.method==="GET"&&c.path.includes("/users/me/history/movies"))') and page.evaluate('state.media.find(m=>m.id==="movie").plays')==3)
  check('Due check-in with remote match sends no duplicate history POST',page.evaluate('qa.calls.filter(c=>c.method==="POST"&&c.path==="/sync/history").length===qa.beforeDueHistoryPosts'))
  page.evaluate("()=>{overlay={type:'item',id:'movie',src:'media',title:'Film'};render();}");page.locator('[data-trakt-checkin="movie"]').click();page.wait_for_function('checkInRead()?.status==="active"')
  page.evaluate("()=>{const s=checkInRead();checkInWrite({...s,expectedEndAt:Date.now()-1});qa.checkHistory=[];qa.beforeMissingHistoryPosts=qa.calls.filter(c=>c.method==='POST'&&c.path==='/sync/history').length;overlay={type:'today',title:'Aujourd’hui'};render();}")
  page.locator('[data-checkin-finish]').click();page.wait_for_function('checkInRead()?.status==="uncertain-finish"')
  check('Missing due auto-history remains recoverable without local mutation',page.evaluate('state.media.find(m=>m.id==="movie").plays')==3 and 'aucun historique' in page.locator('.checkin-card').inner_text().lower())
  check('Missing due auto-history also sends no duplicate history POST',page.evaluate('qa.calls.filter(c=>c.method==="POST"&&c.path==="/sync/history").length===qa.beforeMissingHistoryPosts'))
  page.locator('[data-checkin-cancel]').click();page.wait_for_function('checkInRead()===null')
  # Local erasure is refused while a check-in exists.
  page.evaluate('qaSeed()');page.evaluate("()=>{overlay={type:'item',id:'movie',src:'media',title:'Film'};render();}");page.locator('[data-trakt-checkin="movie"]').click();page.wait_for_function('checkInRead()?.status==="active"')
  blocked=page.evaluate('async()=>{try{await eraseLocalData();return false}catch(e){return /check-in Trakt/.test(e.message)}}');check('Full local erase refuses to orphan active remote check-in',blocked and page.evaluate('state.media.length')==2)
  page.locator('[data-checkin-cancel]').click();page.wait_for_function('checkInRead()===null')
  # Responsive populated Today dashboard.
  for w in [390,768,1360]:
   page.set_viewport_size({'width':w,'height':1000});page.evaluate("()=>{overlay={type:'today',title:'Aujourd’hui'};render();}");page.wait_for_timeout(60);check('Today fits viewport '+str(w),page.evaluate('document.documentElement.scrollWidth<=innerWidth+1'));page.screenshot(path=str(OUT/f'fixture-today-{w}.png'),full_page=True)
  check('No uncaught JavaScript errors in check-in flows',not errors)
  (OUT/'browser-v035.json').write_text(json.dumps({'version':'0.35.0','passed':len(checks),'checks':checks,'errors':errors,'scope':'Production DOM/scripts/CSS with controlled Trakt/API/storage. No live OAuth, PWA persistence or remote account.'},ensure_ascii=False,indent=2));b.close();print('PASSED',len(checks))
if __name__=='__main__':
 try:run()
 except Exception:
  (OUT/'browser-v035-failure.json').write_text(json.dumps({'checks':checks,'traceback':traceback.format_exc()},ensure_ascii=False,indent=2));raise
