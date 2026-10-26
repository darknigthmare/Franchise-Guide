"""Real Chromium UI, File and UTF8 path; providers/storage fixtures.
Secure-context navigation is blocked on this host. Cryptographic calls in these UI scenarios
are bridged to the production Node WebCrypto module, NOT claimed as native-browser WebCrypto.
Native Node and JVM crypto are separately exercised with bidirectional interoperability.
"""
from pathlib import Path
import json,re,base64,subprocess,traceback
from playwright.sync_api import sync_playwright
from browser_harness import SETUP,RESET,ROOT,WEB
OUT=ROOT/'tests/results/v030';OUT.mkdir(parents=True,exist_ok=True);checks=[]
PASS='Les étoiles 🎮 du soir 2026'
def check(name,condition=True):
 if not condition:raise AssertionError(name)
 checks.append({'name':name,'passed':True});print('PASS',name,flush=True)
def crypto_bridge(action,encoded,password):
 p=subprocess.run(['node','tests/protected-bridge.cjs'],input=json.dumps({'action':action,'bytes':encoded,'password':password}),capture_output=True,text=True,cwd=ROOT,timeout=20)
 if p.returncode:raise RuntimeError('Test crypto subprocess failed')
 return json.loads(p.stdout)
def run():
 with sync_playwright() as p:
  b=p.chromium.launch(executable_path='/usr/bin/chromium',headless=True,args=['--no-sandbox']);ctx=b.new_context(viewport={'width':1360,'height':1000},locale='fr-FR',timezone_id='Europe/Paris');page=ctx.new_page();page.set_default_timeout(8000);errors=[]
  page.on('pageerror',lambda e:errors.append(str(e)));page.on('dialog',lambda d:d.accept());page.expose_function('__qaCrypto',crypto_bridge)
  html=(WEB/'index.html').read_text();page.set_content(re.sub(r'<script.*?</script>|<link[^>]+>|<meta http-equiv="Content-Security-Policy"[^>]+>','',html));page.add_style_tag(content=(WEB/'app.css').read_text());page.evaluate(SETUP)
  for f in re.findall(r'<script src="([^"]+)"',html):page.add_script_tag(content=(WEB/f).read_text())
  page.wait_for_timeout(60)
  def reset():
   page.evaluate(RESET);page.evaluate('''()=>{for(const d of document.querySelectorAll('dialog[open]'))d.close();libraryEpoch++;recoveryOnly=null;loadIssue=null;qa.binary=null;qa.plain=null;qa.cryptoCalls=0;qa.holdCrypto=false;qa.waitCrypto=false;qa.completedCrypto=false;window.downloadJson=(name,data)=>{qa.plain={name,data}};window.downloadProtectedFile=bytes=>{qa.binary=Array.from(bytes)};state.media=[{id:'old',kind:'movie',title:'Bibliothèque de validation',source:'local',added:true,watched:true,plays:1,watchHistory:[1700000000000],lastWatchedAt:1700000000000,userNote:'Texte privé de validation',listIds:[]}];state.settings.tmdbKey='CLE-QA-JAMAIS-EXPORTEE';save(false);screen='more';overlay=null;render();}''')
  reset()
  original_available=page.evaluate('FGProtected.available()')
  page.evaluate('()=>{FGProtected.available=()=>false;qa.plain=null;}');page.locator('#exportProtected').click()
  check('Missing WebCrypto never falls back to a plaintext export',page.evaluate('qa.binary===null&&qa.plain===null') and page.locator('#protectedExportDialog').count()==0)
  # An explicit fixture bridge supplies real Node WebCrypto under otherwise real UI.
  page.evaluate('''()=>{FGProtected.available=()=>true;for(const action of ['protect','unprotect'])FGProtected[action]=async(value,password,options={})=>{qa.cryptoCalls++;const bytes=Array.from(value);let encoded='';for(let i=0;i<bytes.length;i+=8192)encoded+=String.fromCharCode(...bytes.slice(i,i+8192));const reply=await __qaCrypto(action,btoa(encoded),password);if(qa.holdCrypto){qa.waitCrypto=true;await new Promise(r=>qa.releaseCrypto=r)};qa.completedCrypto=true;if(!reply.ok){const e=Error(reply.error);e.code=reply.code;e.name=reply.name||'Error';throw e;}return Uint8Array.from(atob(reply.bytes),c=>c.charCodeAt(0));};}''')
  def open_export(password=PASS,confirmation=PASS,ack=True):
   page.locator('#exportProtected').click();page.locator('#protectedPassword').fill(password);page.locator('#protectedConfirm').fill(confirmation)
   if ack:page.locator('#protectedExportAcknowledge').check()
  def submit_export():page.locator('#protectedExportSubmit').click()
  def read_file(data,name='fixture.fgbackup'):
   page.evaluate('async ({bytes,name})=>await readImportSelection("portable",[new File([Uint8Array.from(bytes)],name,{type:"application/octet-stream"})])',{'bytes':list(data),'name':name})
  def decrypt(password=PASS):
   page.locator('#protectedPassword').fill(password);page.locator('#protectedImportSubmit').click()
  open_export(ack=False);page.locator('#protectedExportSubmit').focus();page.keyboard.press('Control+e');check('Plain-export global shortcut blocked inside protected dialog',page.evaluate('qa.plain') is None);submit_export();check('Risk acknowledgement required before cryptographic work',page.evaluate('qa.cryptoCalls')==0 and page.locator('#protectedExportDialog [role=alert]').is_visible());page.locator('#protectedExportCancel').click()
  open_export('short','short');submit_export();check('Short phrase rejected inline',page.evaluate('qa.cryptoCalls')==0 and page.locator('#protectedExportDialog [role=alert]').is_visible());page.locator('#protectedExportCancel').click()
  open_export(confirmation=PASS+'x');submit_export();check('Mismatching confirmation creates no file',page.evaluate('qa.binary===null&&qa.cryptoCalls===0'));page.locator('#protectedExportCancel').click()
  reset();before=page.evaluate('localStorage.getItem(KEY)');open_export();submit_export();page.wait_for_function('qa.binary!==null')
  saved=bytes(page.evaluate('qa.binary'))
  check('Export through actual form produces binary protected envelope',saved[:8]==b'FGPROT01')
  check('No catalogue or Trakt request made by export',page.evaluate('qa.calls.length')==0)
  check('Export does not mutate saved library or journal',page.evaluate('localStorage.getItem(KEY)')==before)
  check('Export clears and closes both password fields',page.locator('#protectedPassword').count()==0)
  check('Phrase does not appear in local or session state',PASS not in page.evaluate('JSON.stringify(__qaStorage)'))
  check('Plain private title is absent from encrypted file',b'Texte priv' not in saved and b'Biblioth' not in saved)
  decoded=crypto_bridge('unprotect',base64.b64encode(saved).decode(),PASS);portable=json.loads(base64.b64decode(decoded['bytes']))
  check('Protected file contains portable V11 and private notes',portable['schema']==11 and portable['media'][0]['userNote']=='Texte privé de validation')
  check('Provider keys and runtime queues excluded from portable content','CLE-QA-JAMAIS-EXPORTEE' not in json.dumps(portable) and '_syncState' not in portable)
  reset();page.locator('#export').click();check('Ordinary JSON export remains explicitly available',page.evaluate('qa.plain.data.schema')==11)
  reset();read_file(saved);check('Protected file selects password step not immediate replacement',page.locator('#protectedImportDialog').is_visible() and page.locator('#importReviewDialog').count()==0)
  before=page.evaluate('localStorage.getItem(KEY)');decrypt('Une autre phrase longue 2026');page.wait_for_selector('#protectedImportDialog [role=alert]:not(.hidden)')
  check('Wrong password keeps last saved library',page.evaluate('localStorage.getItem(KEY)')==before)
  check('Authentication error creates no preview or safety snapshot',page.locator('#importReviewDialog').count()==0 and page.evaluate('backups().length')==0)
  check('Wrong-password field cleared for retry',page.locator('#protectedPassword').input_value()=='')
  decrypt();page.wait_for_selector('#importReviewDialog')
  check('Successful decryption opens existing import review',page.locator('#importReviewForm').is_visible())
  check('Decryption alone is not an import',page.evaluate('localStorage.getItem(KEY)')==before)
  check('Review requires its own explicit confirmation',page.locator('#commitImportReview').is_disabled())
  page.locator('#cancelImportReview').click();check('Cancelling preview retains original library',page.evaluate('localStorage.getItem(KEY)')==before)
  # Different payload demonstrates actual replacement only at the final guarded commit.
  incoming={**portable,'media':[{**portable['media'][0],'id':'replacement','title':'Autre bibliothèque QA','userNote':'Autre note privée'}]}
  encoded=crypto_bridge('protect',base64.b64encode(json.dumps(incoming).encode()).decode(),PASS);replacement=base64.b64decode(encoded['bytes'])
  read_file(replacement);decrypt();page.wait_for_selector('#importReviewDialog');page.locator('#importReviewAcknowledge').check();page.locator('#commitImportReview').click()
  check('Confirmed protected import replaces only after preview',page.evaluate('state.media[0].id')=='replacement')
  check('Safety snapshot of previous library exists',page.evaluate('backups().some(b=>b.state.media[0]?.id==="old")'))
  reset();read_file(saved,'renamed.json');check('Magic detection works if protected file is renamed JSON',page.locator('#protectedImportDialog').count()==1);page.locator('#protectedImportCancel').click()
  reset();bad=bytearray(saved);bad[-1]^=1;read_file(bad);decrypt();page.wait_for_selector('#protectedImportDialog [role=alert]:not(.hidden)')
  check('Altered authentication tag yields no preview',page.locator('#importReviewDialog').count()==0 and page.evaluate('state.media[0].id')=='old');page.locator('#protectedImportCancel').click()
  reset();read_file(saved[:-1]);check('Truncated file refused before asking a password',page.locator('#protectedImportDialog').count()==0 and page.evaluate('qa.cryptoCalls')==0)
  reset();bad=bytearray(saved);bad[8:12]=(0xffffffff).to_bytes(4,'big');read_file(bad);check('Hostile KDF parameter rejected before work',page.locator('#protectedImportDialog').count()==0 and page.evaluate('qa.cryptoCalls')==0)
  reset();read_file(b'not a protected file');check('Invalid fgbackup never falls through to plain import',page.locator('#importReviewDialog').count()==0 and page.locator('#protectedImportDialog').count()==0)
  for title,plain in [('non-JSON text',b'not JSON'),('invalid UTF8',b'\xc0\x80'),('future schema',b'{"schema":999,"media":[]}')]:
   reset();x=crypto_bridge('protect',base64.b64encode(plain).decode(),PASS);read_file(base64.b64decode(x['bytes']));decrypt();page.wait_for_selector('#protectedImportDialog [role=alert]:not(.hidden)')
   check('Authenticated '+title+' rejected before preview',page.locator('#importReviewDialog').count()==0 and page.evaluate('state.media[0].id')=='old');page.locator('#protectedImportCancel').click()
  reset();open_export();page.evaluate('qa.holdCrypto=true');submit_export();page.wait_for_function('qa.waitCrypto')
  check('In-flight export disables submission and clears inputs',page.locator('#protectedExportSubmit').is_disabled() and page.locator('#protectedPassword').input_value()=='')
  page.locator('#protectedExportCancel').click();page.evaluate('()=>{qa.holdCrypto=false;qa.releaseCrypto();}');page.wait_for_timeout(60)
  check('Cancelled export discards late encrypted result',page.evaluate('qa.binary') is None)
  reset();read_file(replacement);page.evaluate('qa.holdCrypto=true');decrypt();page.wait_for_function('qa.waitCrypto');page.keyboard.press('Escape');page.evaluate('()=>{qa.holdCrypto=false;qa.releaseCrypto();}');page.wait_for_timeout(60)
  check('Escape during decryption discards late plaintext',page.locator('#importReviewDialog').count()==0 and page.evaluate('state.media[0].id')=='old')
  reset();open_export();page.evaluate('qa.holdCrypto=true');submit_export();page.wait_for_function('qa.waitCrypto');page.evaluate('()=>{state.media[0].userNote="Changed while encrypting";save(false);qa.holdCrypto=false;qa.releaseCrypto();}');page.wait_for_selector('#protectedExportDialog [role=alert]:not(.hidden)')
  check('Changed library refuses outdated export after encryption',page.evaluate('qa.binary') is None);page.locator('#protectedExportCancel').click()
  reset();read_file(replacement);page.evaluate('qa.holdCrypto=true');decrypt();page.wait_for_function('qa.waitCrypto');page.evaluate('()=>{libraryEpoch++;qa.holdCrypto=false;qa.releaseCrypto();}');page.wait_for_selector('#protectedImportDialog [role=alert]:not(.hidden)')
  check('Replaced library invalidates pending decryption preview',page.locator('#importReviewDialog').count()==0);page.locator('#protectedImportCancel').click()
  reset();read_file(replacement);decrypt();page.wait_for_selector('#importReviewDialog');page.evaluate('()=>{traktSyncing=true;}');page.locator('#importReviewAcknowledge').check();page.locator('#commitImportReview').click()
  check('Active Trakt sync still blocks protected import application',page.locator('#importReviewError').is_visible() and page.evaluate('state.media[0].id')=='old');page.evaluate('traktSyncing=false');page.locator('#cancelImportReview').click()
  reset();open_export();page.evaluate('()=>{qa.originalClock=Date.now;Date.now=()=>qa.originalClock()+600001;}');submit_export();check('Expired export form rejected before KDF',page.evaluate('qa.cryptoCalls')==0);page.evaluate('()=>{Date.now=qa.originalClock;}');page.locator('#protectedExportCancel').click()
  reset();page.evaluate('()=>{overlay={type:"import-center"};render();}');check('Import center explains plain or protected file and exposes protected export','protégé' in page.locator('main').inner_text() and page.locator('#reviewProtectedExport').count()==1)
  for width in [390,768,1360]:
   page.set_viewport_size({'width':width,'height':900});page.locator('#reviewProtectedExport').click();page.wait_for_timeout(60)
   check('Protected form fits viewport '+str(width),page.locator('#protectedExportDialog').evaluate('(e)=>e.scrollWidth<=e.clientWidth+1&&e.getBoundingClientRect().width<=innerWidth'))
   page.screenshot(path=str(OUT/f'fixture-protected-export-{width}.png'))
   page.keyboard.press('Escape');page.wait_for_function('!document.querySelector("#protectedExportDialog")');check('Escape closes protected form '+str(width),page.locator('#protectedPassword').count()==0)
  if errors: print('UNCAUGHT_ERRORS',json.dumps(errors,ensure_ascii=False),flush=True)
  check('No uncaught JavaScript error in protected-backup UI scenarios',not errors)
  result={'version':'0.31.0','passed':len(checks),'scope':'Chromium real UI with controlled storage/providers; crypto bridged to real Node WebCrypto production module because secure-origin navigation is blocked','native_browser_webcrypto_available_in_fixture':original_available,'checks':checks,'errors':errors}
  (OUT/'browser-v030.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n');b.close();print(json.dumps({'passed':len(checks)}))
if __name__=='__main__':
 try:run()
 except Exception:
  (OUT/'browser-v030-failure.json').write_text(json.dumps({'checks':checks,'traceback':traceback.format_exc()},ensure_ascii=False,indent=2));raise
