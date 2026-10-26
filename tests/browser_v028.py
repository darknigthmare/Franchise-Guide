"""V0.28 user flows in the real app DOM/scripts with controlled Open Library and storage.
No real API call, persistent-origin browser, PWA installation or Android UI is implied.
"""
from playwright.sync_api import sync_playwright
from browser_harness import SETUP,RESET,ROOT,WEB
from browser_v027 import EXTRA as EDITIONS
import re,json,base64,traceback
OUT=ROOT/'tests/results/v028';OUT.mkdir(parents=True,exist_ok=True);checks=[]
def check(name,value=True):
 if not value:raise AssertionError(name)
 checks.append({'name':name,'passed':True});print('PASS',name,flush=True)
EXTRA=r'''()=>{
 const old=fetch;qa.isbnMode='ok';qa.isbnCalls=[];qa.work='/works/OL1W';qa.edition='/books/OL10M';qa.workReads=0;
 window.fetch=async(url,options={})=>{const u=new URL(String(url),'https://fixture.test');const search=u.hostname==='openlibrary.org'&&u.pathname==='/search.json'&&(u.searchParams.get('q')||'').startsWith('isbn:'),edition=u.hostname==='openlibrary.org'&&/^\/books\/OL\d+M\.json$/.test(u.pathname);
  if(search||edition){qa.isbnCalls.push({url:u.href,credentials:options.credentials,method:options.method||'GET'});if(qa.holdISBN)await new Promise(r=>{qa.waitingISBN=true;qa.releaseISBN=r});
   if(qa.isbnMode==='429')return new Response('{}',{status:429,headers:{'Content-Type':'application/json','Retry-After':'120'}});
   let x=search?{numFound:1,start:0,docs:[{key:qa.work,title:'Titre proposé QA',author_name:['Auteur QA'],editions:{docs:[{key:qa.edition}]}}]}:{key:qa.edition,title:'Édition vérifiée QA',works:[{key:qa.work}],number_of_pages:220,publish_date:'2001',publishers:['Maison QA'],languages:[{key:'/languages/fre'}],physical_format:'Poche',covers:[123],isbn_13:['9780140328721'],isbn_10:['0140328726']};
   if(search&&qa.isbnMode==='empty')x={numFound:0,start:0,docs:[]};if(search&&qa.isbnMode==='partial')delete x.docs[0].editions;if(search&&qa.isbnMode==='limited')x.numFound=12;
   if(edition&&qa.isbnMode==='other-work')x.works=[{key:'/works/OL999W'}];if(edition&&qa.isbnMode==='other-edition')x.key='/books/OL999M';if(edition&&qa.isbnMode==='other-isbn'){x.isbn_10=[];x.isbn_13=['9791090636071'];}if(edition&&qa.isbnMode==='oversize')x.padding='x'.repeat(2*1024*1024);
   return new Response(JSON.stringify(x),{headers:{'Content-Type':'application/json'}});
  }
  if(u.hostname==='openlibrary.org'&&/^\/works\/OL\d+W\.json$/.test(u.pathname)){qa.workReads++;if(qa.holdWork)await new Promise(r=>{qa.waitingWork=true;qa.releaseWork=r});return new Response(JSON.stringify({key:u.pathname.slice(0,-5),title:'Titre complet œuvre QA',description:'Données synthétiques contrôlées.',subjects:['Roman'],covers:[123]}),{headers:{'Content-Type':'application/json'}});}
  return old(url,options);
 };
}'''
def run():
 with sync_playwright() as p:
  b=p.chromium.launch(executable_path='/usr/bin/chromium',headless=True,args=['--no-sandbox']);ctx=b.new_context(viewport={'width':1360,'height':1000},locale='fr-FR',timezone_id='Europe/Paris');page=ctx.new_page();page.set_default_timeout(9000);errors=[]
  page.on('pageerror',lambda e:errors.append(str(e)));page.on('dialog',lambda d:d.accept())
  png=base64.b64decode('iVBORw0KGgoAAAANSUhEUgAAAAIAAAACCAYAAABytg0kAAAAFElEQVQImWNkYPj/n4GBgYGJAQoAHhgCAQGXeHsAAAAASUVORK5CYII=')
  ctx.route(re.compile(r'https://(covers\.openlibrary\.org|image\.tmdb\.org)/'),lambda r:r.fulfill(status=200,content_type='image/png',body=png))
  html=(WEB/'index.html').read_text();page.set_content(re.sub(r'<script.*?</script>|<link[^>]+>|<meta http-equiv="Content-Security-Policy"[^>]+>','',html));page.add_style_tag(content=(WEB/'app.css').read_text());page.evaluate(SETUP);page.evaluate(EDITIONS);page.evaluate(EXTRA)
  for f in re.findall(r'<script src="([^"]+)"',html):page.add_script_tag(content=(WEB/f).read_text())
  page.evaluate(RESET);page.evaluate('''()=>{state.media=[{id:'m',kind:'book',title:'Titre local QA',source:'openlibrary',sourceId:'/works/OL1W',added:true,catalogComplete:true,progressCurrent:20,progressTotal:0,progressUnit:'pages',watched:true,plays:2,watchHistory:[1700000000000,1710000000000],lastWatchedAt:1710000000000,trackingStatus:'active',rating:9,userNote:'Texte privé',personalFormat:'Mon poche'}];save(false);screen='books';overlay=null;render();}''')
  def search(code='ISBN-10: 0-14-032872-6'):
   page.locator('#bookIsbnInput').fill(code);page.locator('#bookIsbnForm button[type=submit]').click();page.wait_for_function('bookIsbnPage&&!bookIsbnPage.busy')
  def verify():
   page.locator('[data-isbn-check]').first.click();page.wait_for_function('bookIsbnPage&&!bookIsbnPage.busy')
  def commit():
   page.locator('#bookSelectionDialog button[type=submit]').click();page.wait_for_function('!document.querySelector("#bookSelectionDialog")')
  check('Book library contains ISBN entry',page.locator('#openIsbnLookup').is_visible());page.locator('#openIsbnLookup').click()
  check('Opening ISBN panel makes no API request',page.evaluate('qa.isbnCalls.length')==0)
  initial=page.evaluate('localStorage.getItem(KEY)')
  search('4006381333931');check('Product EAN refused before network',page.evaluate('qa.isbnCalls.length')==0 and 'ISBN invalide' in page.locator('main').inner_text())
  search();check('ISBN10 sends canonical ISBN13 query',page.evaluate('qa.isbnCalls[0].url').startswith('https://openlibrary.org/search.json?q=isbn%3A9780140328721'))
  check('Candidates not saved automatically',page.locator('.isbn-candidate').count()==1 and page.evaluate('localStorage.getItem(KEY)')==initial)
  for mode in ['other-edition','other-work','other-isbn','oversize']:
   page.evaluate('(mode)=>qa.isbnMode=mode',mode);verify();check('Reject verification '+mode,page.locator('#bookIsbnSave').count()==0 and page.evaluate('localStorage.getItem(KEY)')==initial)
  page.evaluate('qa.isbnMode="ok"');verify();check('Verified exact edition shown',page.locator('.isbn-verified').is_visible())
  check('Cover comes from numeric source ID',page.locator('.isbn-cover').get_attribute('src')=='https://covers.openlibrary.org/b/id/123-M.jpg')
  page.locator('#bookIsbnSave').click();check('Optional total starts unchecked',not page.locator('[name=usePages]').is_checked())
  page.locator('#bookSelectionDialog [data-close-selection]').first.click();check('Cancel confirmation does not save reference',page.evaluate('!state.media[0].selectedEdition'))
  page.locator('#bookIsbnSave').click();commit()
  check('Existing work reused once',page.evaluate('state.media.length===1&&state.media[0].id==="m"'))
  check('Selected identity and ISBN saved',page.evaluate('state.media[0].selectedEdition.editionId==="/books/OL10M"&&state.media[0].selectedEdition.isbns.includes("9780140328721")'))
  check('Default selection keeps personal tracking and notes',page.evaluate('state.media[0].progressTotal===0&&state.media[0].progressCurrent===20&&state.media[0].plays===2&&state.media[0].rating===9&&state.media[0].personalFormat==="Mon poche"&&state.media[0].userNote==="Texte privé"'))
  check('No Trakt write for selecting edition',page.evaluate('syncState.queue.length')==0)
  check('Saved edition visible after rerender',page.locator('.selected-book-edition').is_visible())
  check('Export uses schema eleven',page.evaluate('toPortableBackup(state).schema')==11)
  check('Portable snapshot survives roundtrip',page.evaluate('JSON.stringify(fromPortableBackup(toPortableBackup(state)).media[0].selectedEdition)===JSON.stringify(state.media[0].selectedEdition)'))
  check('Unknown extra fields not imported in reference',page.evaluate('()=>{const b=toPortableBackup(state);b.media[0].selectedEdition.token="private-value";return !JSON.stringify(fromPortableBackup(b).media[0].selectedEdition).includes("private-value")}'))
  page.evaluate('async()=>await refreshCatalogItem(state.media[0])')
  check('Full work refresh retains selected edition and history',page.evaluate('state.media[0].selectedEdition.editionId==="/books/OL10M"&&state.media[0].plays===2&&state.media[0].progressTotal===0'))
  page.locator('[data-clear-book-selection]').click();check('Clear removes only selected reference',page.evaluate('!state.media[0].selectedEdition&&state.media.length===1&&state.media[0].plays===2'))
  page.locator('#openBookEditions').click();page.locator('#bookEditionsLoad').click();page.wait_for_function('bookEditionsPage.result?.entries.length===30&&!bookEditionsPage.loading')
  first=page.evaluate('bookEditionsPage.result.entries[0].observedAt');page.locator('#bookEditionsMore').click();page.wait_for_function('bookEditionsPage.result.entries.length===60&&!bookEditionsPage.loading')
  check('Later page does not re-date earlier edition observation',page.evaluate('bookEditionsPage.result.entries[0].observedAt')==first and page.evaluate('bookEditionsPage.result.entries[30].observedAt')>first)
  page.locator('[data-select-book-edition="/books/OL10M"]').click();page.locator('[name=usePages]').check();commit()
  check('Explicit combined action changes total but not progress/history',page.evaluate('state.media[0].progressTotal===220&&state.media[0].progressCurrent===20&&state.media[0].plays===2&&state.media[0].trackingStatus==="active"'))
  check('Saved observation dates original page',page.evaluate('state.media[0].selectedEdition.observedAt')==first)
  page.locator('[data-select-book-edition="/books/OL11M"]').click();check('Unknown pages cannot become total',page.locator('[name=usePages]').is_disabled());commit()
  check('Unknown pages edition retained independently',page.evaluate('state.media[0].selectedEdition.pages===null&&state.media[0].progressTotal===220'))
  page.locator('[data-select-book-edition="/books/OL10M"]').click();page.evaluate('()=>{state.media[0].selectedEdition=null;save(false)}');page.locator('#bookSelectionDialog [type=submit]').click()
  check('Changed selection rejects old confirmation',page.locator('#bookSelectionError').is_visible() and page.evaluate('!state.media[0].selectedEdition'));page.locator('#bookSelectionDialog [data-close-selection]').first.click()
  page.locator('[data-select-book-edition="/books/OL10M"]').click();page.locator('[name=usePages]').check();page.evaluate('()=>{state.media[0].progressCurrent=21;save(false)}');page.locator('#bookSelectionDialog [type=submit]').click()
  check('Changed page progress rejects old combined plan',page.locator('#bookSelectionError').is_visible() and page.evaluate('!state.media[0].selectedEdition&&state.media[0].progressCurrent===21'));page.locator('#bookSelectionDialog [data-close-selection]').first.click()
  page.locator('[data-select-book-edition="/books/OL10M"]').click();page.evaluate('()=>{state.media[0].userNote="Note récente";save(false)}');commit();check('Unrelated current note retained',page.evaluate('state.media[0].userNote')=='Note récente')
  page.locator('[data-clear-book-selection]').click();check('Clear keeps previously chosen total',page.evaluate('state.media[0].progressTotal===220&&!state.media[0].selectedEdition'))
  page.evaluate('()=>openBookIsbn()')
  for mode,label in [('partial','Partial index reference rejected'),('empty','Empty result not proof book does not exist'),('limited','Result limitation shown')]:
   page.evaluate('(mode)=>qa.isbnMode=mode',mode);search();check(label,page.evaluate('!!bookIsbnPage.error') if mode=='partial' else 'ne prouve pas' in page.locator('main').inner_text() if mode=='empty' else 'Résultats limités' in page.locator('main').inner_text())
  page.evaluate('qa.isbnMode="ok"');verify();page.evaluate('()=>{state.media.push({...clone(state.media[0]),id:"duplicate"});save(false)}');page.locator('#bookIsbnSave').click()
  check('Ambiguous local duplicate work cannot be selected by name',page.locator('#bookSelectionDialog').count()==0 and 'Plusieurs fiches' in page.locator('main').inner_text())
  page.evaluate('()=>{state.media=state.media.filter(m=>m.id!=="duplicate");state.media[0].added=false;save(false)}');page.locator('#bookIsbnSave').click()
  check('Removed work not silently added again','retirée' in page.locator('main').inner_text())
  page.evaluate('()=>{state.media[0].added=true;save(false);qa.work="/works/OL2W";qa.edition="/books/OL20M";}');search();verify();page.locator('#bookIsbnSave').click();commit()
  check('New ISBN work added only after metadata retrieval',page.evaluate('state.media.length===2&&state.media[1].title==="Titre complet œuvre QA"&&state.media[1].selectedEdition.editionId==="/books/OL20M"'))
  check('New ISBN work total is unknown by default',page.evaluate('state.media[1].progressTotal')==0)
  page.evaluate('()=>{openBookIsbn();qa.holdISBN=true;qa.waitingISBN=false;}');page.locator('#bookIsbnInput').fill('9780140328721');page.locator('#bookIsbnForm [type=submit]').click();page.wait_for_function('qa.waitingISBN');page.locator('#bookIsbnCancel').click();page.evaluate('()=>{qa.holdISBN=false;qa.releaseISBN()}');page.wait_for_timeout(60)
  check('Cancel discards late query',page.evaluate('!bookIsbnPage.result&&!bookIsbnPage.busy'))
  page.evaluate('()=>{qa.holdISBN=true;qa.waitingISBN=false}');page.locator('#bookIsbnForm [type=submit]').click();page.wait_for_function('qa.waitingISBN');page.evaluate('()=>{overlay=null;screen="books";render();qa.holdISBN=false;qa.releaseISBN()}');page.wait_for_timeout(60)
  check('Navigation discards outstanding lookup',page.evaluate('bookIsbnPage===null&&state.media.length===2'))
  check('Requests use public GET without credentials',page.evaluate('qa.isbnCalls.every(c=>c.url.startsWith("https://openlibrary.org/")&&c.method==="GET"&&c.credentials==="omit")'))
  page.evaluate('()=>{openBookIsbn();qa.isbnMode="429"}');search();count=page.evaluate('qa.isbnCalls.length');page.locator('#bookIsbnForm [type=submit]').click();page.wait_for_function('!bookIsbnPage.busy');check('Manual retry respects 429',page.evaluate('qa.isbnCalls.length')==count)
  page.evaluate('()=>{overlay={type:"item",id:"m"};render();qa.originalSet=localStorage.setItem;localStorage.setItem=(k,v)=>{if(k===KEY)throw Error("Quota fixture");return qa.originalSet(k,v)};const e=FGBookEditions.edition({key:"/books/OL10M",title:"Édition de récupération",works:[{key:"/works/OL1W"}],number_of_pages:260,isbn_13:["9780140328721"]},"/works/OL1W");editionSelectDialog(state.media[0],e,Date.now());}')
  page.locator('[name=usePages]').check();page.locator('#bookSelectionDialog [type=submit]').click()
  check('Rejected main write keeps saved reference and total',page.evaluate('!state.media[0].selectedEdition&&state.media[0].progressTotal===220'))
  check('Rejected write offers selected reference in recovery draft',page.evaluate('storageDraft.media[0].selectedEdition.title==="Édition de récupération"&&storageDraft.media[0].progressTotal===260'))
  page.locator('#bookSelectionDialog [data-close-selection]').first.click();page.evaluate('()=>{localStorage.setItem=qa.originalSet;storageDraft=null;document.querySelector("#storageBanner")?.remove();render()}')
  for w in [390,768,1360]:
   page.set_viewport_size({'width':w,'height':950});page.evaluate('()=>{openBookIsbn();$("#toast").style.opacity="0";}');page.screenshot(path=str(OUT/f'fixture-isbn-{w}.png'),full_page=True)
   check('ISBN layout fits '+str(w),page.evaluate('document.documentElement.scrollWidth<=innerWidth+1'))
   page.evaluate('()=>{const e=FGBookEditions.edition({key:"/books/OL10M",title:"Édition de validation "+"longue ".repeat(15),works:[{key:"/works/OL1W"}],number_of_pages:260,isbn_13:["9780140328721"]},"/works/OL1W");editionSelectDialog(state.media[0],e,Date.now());}')
   page.screenshot(path=str(OUT/f'fixture-selection-{w}.png'),full_page=True);check('Selection dialog fits '+str(w),page.locator('#bookSelectionDialog').evaluate('(e)=>e.getBoundingClientRect().width<=innerWidth&&e.scrollWidth<=e.clientWidth+1'))
   page.keyboard.press('Escape');page.locator('#bookSelectionDialog').wait_for(state='detached');check('Escape cancels '+str(w),page.locator('#bookSelectionDialog').count()==0)
  if errors: print('UNCAUGHT_ERRORS',json.dumps(errors,ensure_ascii=False),flush=True)
  check('No uncaught script errors',not errors)
  (OUT/'browser-v028.json').write_text(json.dumps({'scope':'real DOM/scripts/CSS; synthetic APIs/images/storage; not live account or persistent origin','passed':len(checks),'checks':checks,'errors':errors},ensure_ascii=False,indent=2));b.close();print(json.dumps({'passed':len(checks),'errors':errors}))
if __name__=='__main__':
 try:run()
 except Exception:
  (OUT/'browser-v028-failure.json').write_text(json.dumps({'checks':checks,'error':traceback.format_exc()},ensure_ascii=False,indent=2));raise
