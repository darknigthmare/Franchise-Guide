"""Actual app DOM/scripts/styles, synthetic book JSON/storage/images. No real provider login or origin test."""
from pathlib import Path
from playwright.sync_api import sync_playwright
from browser_harness import SETUP,RESET,ROOT,WEB
import re,json,base64,traceback
OUT=ROOT/'tests/results/v027';OUT.mkdir(parents=True,exist_ok=True);checks=[]
def check(name,condition=True):
 if not condition:raise AssertionError(name)
 checks.append({'name':name,'passed':True});print('PASS',name,flush=True)
EXTRA=r'''()=>{
 const previous=fetch;qa.books=[];qa.bookMode='ok';qa.bookSize=63;
 window.fetch=async(url,options={})=>{
  const u=new URL(String(url),'https://fixture.test');if(u.hostname!=='openlibrary.org'||!u.pathname.endsWith('/editions.json'))return previous(url,options);
  const offset=Number(u.searchParams.get('offset')||0);qa.books.push({url:u.href,offset,options:{method:options.method||'GET',credentials:options.credentials,maxBytes:options.maxBytes},time:Date.now()});
  if(qa.bookHold)await new Promise(r=>{qa.bookWaiting=true;qa.bookRelease=r});
  if(qa.bookMode==='503'||qa.bookMode==='429')return new Response(JSON.stringify({error:'fixture'}),{status:Number(qa.bookMode),headers:{'Content-Type':'application/json','Retry-After':'120'}});
  const entries=Array.from({length:Math.min(30,qa.bookSize-offset)},(_,n)=>{const i=n+offset;return {key:'/books/OL'+(10+i)+'M',title:i===61?'Édition spéciale recherchée':'Édition de test '+i,works:[{key:'/works/OL1W'}],number_of_pages:i===1?null:i===2?0:220+i,publish_date:'2001',publishers:['Éditeur de test'],languages:[{key:'/languages/'+(i%2?'eng':'fre')}],isbn_13:['9780140328721'],physical_format:'Paperback',covers:[123]};});
  const data={size:qa.bookSize,links:{work:'/works/OL1W'},entries};
  if(qa.bookMode==='other'&&entries.length)entries[0].works=[{key:'/works/OL999W'}];
  if(qa.bookMode==='duplicate'&&entries.length>1)entries[1].key=entries[0].key;
  if(qa.bookMode==='partial')data.entries=[];
  if(qa.bookMode==='oversized')data.padding='x'.repeat(2*1024*1024);
  return new Response(JSON.stringify(data),{status:200,headers:{'Content-Type':'application/json'}});
 };
}'''
def run():
 with sync_playwright() as p:
  browser=p.chromium.launch(executable_path='/usr/bin/chromium',headless=True,args=['--no-sandbox']);ctx=browser.new_context(viewport={'width':1360,'height':1000},locale='fr-FR',timezone_id='Europe/Paris');page=ctx.new_page();page.set_default_timeout(8000);errors=[];yes={'value':True}
  page.on('pageerror',lambda e:errors.append(str(e)));page.on('dialog',lambda d:d.accept() if yes['value'] else d.dismiss())
  png=base64.b64decode('iVBORw0KGgoAAAANSUhEUgAAAAIAAAACCAYAAABytg0kAAAAFElEQVQImWNkYPj/n4GBgYGJAQoAHhgCAQGXeHsAAAAASUVORK5CYII=')
  ctx.route(re.compile(r'https://(image\.tmdb\.org|covers\.openlibrary\.org)/'),lambda r:r.fulfill(status=200,content_type='image/png',body=png))
  html=(WEB/'index.html').read_text();page.set_content(re.sub(r'<script.*?</script>|<link[^>]+>|<meta http-equiv="Content-Security-Policy"[^>]+>','',html));page.add_style_tag(content=(WEB/'app.css').read_text());page.evaluate(SETUP);page.evaluate(EXTRA)
  for f in re.findall(r'<script src="([^"]+)"',html):page.add_script_tag(content=(WEB/f).read_text())
  page.evaluate(RESET);page.evaluate("()=>openScopedSearch('book')");page.locator('#q').fill('livre');page.wait_for_function('remoteResults.some(m=>m.source==="openlibrary")');page.locator('[data-act="add"]').first.click();page.wait_for_function('state.media.length===1')
  check('Book added without developer credentials',page.evaluate('state.media[0].source==="openlibrary"&&!state.settings.tmdbKey'))
  check('Search median never became personal total',page.evaluate('state.media[0].progressTotal')==0)
  check('Work identity canonicalized',page.evaluate('state.media[0].sourceId')=='/works/OL1W')
  page.evaluate('()=>{Object.assign(state.media[0],{progressCurrent:20,progressTotal:0,progressUnit:"pages",watched:true,plays:2,watchHistory:[1700000000000,1710000000000],lastWatchedAt:1710000000000,trackingStatus:"active",rating:9,userNote:"Texte privé",personalFormat:"Mon exemplaire"});save(false);render();}')
  before=page.evaluate('localStorage.getItem(KEY)');page.locator('#openBookEditions').click()
  check('Opening edition sheet makes no network request',page.evaluate('qa.books.length')==0)
  check('Source-only limits are explicit','300 maximum' in page.locator('main').inner_text() and 'pages déjà reçues' in page.locator('main').inner_text())
  page.locator('#bookEditionsLoad').click();page.wait_for_function('bookEditionsPage?.result?.entries.length===30&&!bookEditionsPage.loading')
  check('First page bounded to thirty cards',page.locator('.book-edition').count()==30)
  check('Consultation does not write local library',page.evaluate('localStorage.getItem(KEY)')==before)
  check('Edition cover URL comes from numeric cover id',page.locator('.book-edition img').first.get_attribute('src')=='https://covers.openlibrary.org/b/id/123-M.jpg')
  check('Unknown and zero page counts are distinguished','Pages non renseignées' in page.locator('main').inner_text() and '0 pages' in page.locator('main').inner_text())
  check('Unknown or zero pages cannot be applied',page.locator('[data-book-pages="/books/OL11M"]').is_disabled() and page.locator('[data-book-pages="/books/OL12M"]').is_disabled())
  page.locator('#bookEditionsLanguage').select_option('fre');check('Language filter uses declared edition languages',page.locator('.book-edition').count()==15)
  page.locator('#bookEditionsLanguage').select_option('');page.locator('#bookEditionsKnown').check();check('Known pages filter excludes zero and unknown',page.locator('.book-edition').count()==28);page.locator('#bookEditionsKnown').uncheck()
  page.locator('#bookEditionsQuery').fill('editeur');check('Loaded publisher search ignores accents',page.locator('.book-edition').count()==30);page.locator('#bookEditionsQuery').fill('')
  page.evaluate('()=>{qa.bookMode="503";}');page.locator('#bookEditionsMore').click();page.wait_for_function('bookEditionsPage.error&&!bookEditionsPage.loading')
  check('Failed second page preserves first page',page.locator('.book-edition').count()==30 and page.evaluate('bookEditionsPage.result.nextOffset')==30)
  page.evaluate('()=>{qa.bookMode="ok";}');page.locator('#bookEditionsRetry').click();page.wait_for_function('bookEditionsPage.result.entries.length===60')
  check('Retry reads same offset, never skips page',page.evaluate('qa.books.slice(-2).map(x=>x.offset)')==[30,30])
  page.locator('#bookEditionsMore').click();page.wait_for_function('bookEditionsPage.result.entries.length===63')
  check('Exhausted pagination removes more button',page.locator('#bookEditionsMore').count()==0)
  page.locator('#bookEditionsQuery').fill('speciale');check('Search finds later loaded edition',page.locator('.book-edition').count()==1 and 'Édition spéciale' in page.locator('.book-edition').inner_text());page.locator('#bookEditionsQuery').fill('')
  snapshot=page.evaluate('JSON.stringify(state.media[0])');yes['value']=False;page.locator('[data-book-pages="/books/OL10M"]').click();check('Cancel confirmation leaves all fields unchanged',page.evaluate('JSON.stringify(state.media[0])')==snapshot);yes['value']=True
  page.locator('[data-book-pages="/books/OL10M"]').click()
  check('Confirmed application changes total only',page.evaluate('state.media[0].progressTotal===220&&state.media[0].progressCurrent===20&&state.media[0].plays===2&&state.media[0].watchHistory.length===2&&state.media[0].userNote==="Texte privé"&&state.media[0].personalFormat==="Mon exemplaire"&&state.media[0].rating===9'))
  check('Applying book pages sends nothing to Trakt',page.evaluate('syncState.queue.length')==0)
  page.evaluate('()=>{state.media[0].progressCurrent=220;state.media[0].watched=false;state.media[0].plays=0;state.media[0].watchHistory=[];state.media[0].lastWatchedAt=null;save(false);}')
  page.locator('[data-book-pages="/books/OL10M"]').click();check('Equal total does not mark reading completed',page.evaluate('!state.media[0].watched&&state.media[0].plays===0'))
  page.evaluate('()=>{state.media[0].progressCurrent=230;save(false);}')
  page.locator('[data-book-pages="/books/OL10M"]').click();check('Cannot lower total below existing progression','dépasse' in page.locator('#toast').inner_text() and page.evaluate('state.media[0].progressTotal')==220)
  page.evaluate('()=>{state.media[0].progressCurrent=20;state.media[0].progressUnit="chapitres";save(false);}')
  page.locator('[data-book-pages="/books/OL10M"]').click();check('Chapter-based progress not silently converted to pages','pas en pages' in page.locator('#toast').inner_text() and page.evaluate('state.media[0].progressUnit')=='chapitres')
  page.evaluate('()=>{state.media[0].progressUnit="pages";save(false);}')
  for mode in ['other','duplicate','partial','oversized']:
   stamp=page.evaluate('bookEditionsPage.observedAt');page.evaluate('(m)=>{qa.bookMode=m;}',mode);page.locator('#bookEditionsLoad').click();page.wait_for_function('bookEditionsPage.error&&!bookEditionsPage.loading')
   check('Reject '+mode+' page while keeping previous observation',page.evaluate('bookEditionsPage.result.entries.length')==63 and page.evaluate('bookEditionsPage.observedAt')==stamp)
  page.evaluate('()=>{qa.bookMode="429";}');page.locator('#bookEditionsLoad').click();page.wait_for_function('bookEditionsPage.error&&!bookEditionsPage.loading');count=page.evaluate('qa.books.length');page.evaluate('()=>{qa.bookMode="ok";}');page.locator('#bookEditionsRetry').click();page.wait_for_function('!bookEditionsPage.loading');check('Manual retry respects provider cooldown',page.evaluate('qa.books.length')==count)
  page.evaluate('()=>{bookRetryAt=0;qa.bookHold=true;qa.bookWaiting=false;}');page.locator('#bookEditionsLoad').click();page.wait_for_function('qa.bookWaiting');page.locator('#bookEditionsCancel').click();page.evaluate('()=>{qa.bookHold=false;qa.bookRelease();}');page.wait_for_timeout(80)
  check('Cancel drops late response and preserves loaded pages',page.evaluate('bookEditionsPage.result.entries.length')==63 and 'annulée' in page.locator('main').inner_text())
  page.locator('#back').click();check('Back returns the original book',page.evaluate('overlay.type==="item"&&overlay.id===state.media[0].id'))
  page.evaluate('async()=>{await refreshCatalogItem(state.media[0]);}');check('Full work refresh preserves chosen total and private fields',page.evaluate('state.media[0].progressTotal===220&&state.media[0].userNote==="Texte privé"'))
  check('Portable roundtrip retains applied total without edition consultations',page.evaluate('()=>{const p=toPortableBackup(state);return p.media[0].progressTotal===220&&!JSON.stringify(p).includes("bookEditionsPage")}'))
  page.locator('#openBookEditions').click();page.locator('#bookEditionsLoad').click();page.wait_for_function('bookEditionsPage.result?.entries.length===30')
  for w in [390,768,1360]:
   page.set_viewport_size({'width':w,'height':950});page.evaluate("()=>{$('#toast').classList.remove('show');$('#toast').style.opacity='0';}");page.screenshot(path=str(OUT/f'fixture-book-editions-{w}.png'),full_page=True)
   check('Edition screen fits width '+str(w),page.evaluate('document.documentElement.scrollWidth<=innerWidth+1'))
  # A refused main write cannot present an applied total as success.
  page.evaluate('()=>{qa.originalSet=localStorage.setItem;localStorage.setItem=(k,v)=>{if(k===KEY)throw Error("Quota fixture");return qa.originalSet(k,v)};}');page.locator('[data-book-pages="/books/OL13M"]').click()
  check('Storage refusal preserves committed total',page.evaluate('state.media[0].progressTotal')==220)
  check('Storage refusal keeps candidate in recovery draft',page.evaluate('storageDraft?.media[0].progressTotal')==223)
  page.evaluate('()=>{localStorage.setItem=qa.originalSet;}')
  check('No uncaught JS errors',not errors)
  (OUT/'browser-v027.json').write_text(json.dumps({'version':'0.27.0','scope':'production DOM/scripts with mocked Open Library JSON/storage/images; no real origin/provider account','passed':len(checks),'checks':checks,'errors':errors},ensure_ascii=False,indent=2))
  browser.close();print(json.dumps({'passed':len(checks),'errors':errors}))
if __name__=='__main__':
 try:run()
 except Exception:
  (OUT/'browser-v027-failure.json').write_text(json.dumps({'checks':checks,'traceback':traceback.format_exc()},ensure_ascii=False,indent=2));raise
