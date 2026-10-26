"""Real web scripts and DOM with synthetic API/storage; no live browser origin, OAuth or PWA."""
from pathlib import Path
from playwright.sync_api import sync_playwright
from browser_harness import SETUP,RESET,ROOT,WEB
import re,json,base64,traceback
OUT=ROOT/'tests/results/v033';OUT.mkdir(exist_ok=True,parents=True)
checks=[]
def check(name,condition):
 if not condition:raise AssertionError(name)
 checks.append({'name':name,'passed':True});print('PASS',name,flush=True)
FIXTURES=r'''()=>{
 window.qa.discoveryCalls=[];qa.discoverFailure=false;qa.discoveryHold=false;qa.discoverWaiter=null;qa.discoverPage=null;
 const old=window.fetch;
 window.fetch=async(url,opts={})=>{
  const u=new URL(String(url),'http://test.local');if(u.hostname!=='api.tvmaze.com'||u.pathname!=='/shows')return old(url,opts);
  const page=Number(u.searchParams.get('page')||0);qa.discoveryCalls.push({page,signal:opts.signal});
  if(qa.discoveryHold&&page===1){await new Promise(resolve=>qa.discoverWaiter=resolve);}
  if(page===1&&qa.discoverFailure){return new Response(JSON.stringify({error:'Service temporairement indisponible'}),{status:503,headers:{'Content-Type':'application/json'}});}
  if(page>=2)return new Response(JSON.stringify({error:'No more shows'}),{status:404,headers:{'Content-Type':'application/json'}});
  const records=page===0?[
     {id:101,name:'Un été tranquille',premiered:'2025-01-01',language:'French',rating:{average:7}},
     {id:102,name:'Winter Story',premiered:'2024-01-01',language:'English',rating:{average:6}}
   ]:[
     {id:102,name:'Winter Story',premiered:'2024-01-01',language:'English',rating:{average:6}},
     {id:103,name:'La suite',premiered:'2026-01-01',language:'French',rating:{average:5}}
   ];
  return new Response(JSON.stringify(records),{status:200,headers:{'Content-Type':'application/json'}});
 };
}'''
def run():
 with sync_playwright() as p:
  browser=p.chromium.launch(executable_path='/usr/bin/chromium',headless=True,args=['--no-sandbox'])
  ctx=browser.new_context(viewport={'width':1360,'height':990},locale='fr-FR',timezone_id='Europe/Paris');q=ctx.new_page();q.set_default_timeout(7500);errors=[]
  q.on('pageerror',lambda e:errors.append(str(e)));q.on('dialog',lambda d:d.accept())
  png=base64.b64decode('iVBORw0KGgoAAAANSUhEUgAAAAIAAAACCAYAAABytg0kAAAAFElEQVQImWNkYPj/n4GBgYGJAQoAHhgCAQGXeHsAAAAASUVORK5CYII=')
  ctx.route(re.compile(r'https://(image\.tmdb\.org|covers\.openlibrary\.org)/'),lambda r:r.fulfill(status=200,content_type='image/png',body=png))
  html=(WEB/'index.html').read_text();q.set_content(re.sub(r'<script.*?</script>|<link[^>]+>|<meta http-equiv="Content-Security-Policy"[^>]+>','',html))
  q.add_style_tag(content=(WEB/'app.css').read_text());q.evaluate(SETUP);q.evaluate(FIXTURES)
  for f in re.findall(r'<script src="([^"]+)"',html):q.add_script_tag(content=(WEB/f).read_text())
  q.wait_for_timeout(70);q.evaluate(RESET)
  def open_discover():
   q.evaluate("()=>{state.settings.discoverKind='show';state.settings.tmdbKey='';overlay={type:'discover',title:'Découvrir'};render();}")
   q.wait_for_function('typeof discoverySessionV33!=="undefined"&&discoverySessionV33?.page===1')
  open_discover();check('Discovery boot loads TVmaze page zero',q.evaluate('qa.discoveryCalls[0].page')==0)
  check('First server page contains two distinct TVmaze refs',q.evaluate('discoverySessionV33.items.map(x=>x.sourceId)')==['101','102'])
  check('Next page offered after partial server page',q.locator('#discoverMore').is_visible())
  q.evaluate('qa.discoverFailure=true');q.locator('#discoverMore').click();q.wait_for_function('discoverySessionV33.error!==null')
  check('Second-page HTTP failure keeps first page',q.evaluate('discoverySessionV33.items.length')==2)
  check('Failure is visible and labeled with page number','Page 2' in q.locator('#discoverList').inner_text() and q.locator('#discoverRetry').is_visible())
  check('TVmaze page one was requested on provider',q.evaluate('qa.discoveryCalls.at(-1).page')==1)
  q.evaluate('qa.discoverFailure=false');q.locator('#discoverRetry').click();q.wait_for_function('discoverySessionV33.page===2')
  check('Retry reuses provider page one',q.evaluate('qa.discoveryCalls.at(-1).page')==1)
  check('Duplicate cross-page show is deduplicated',q.evaluate('discoverySessionV33.items.map(x=>x.sourceId)')==['101','102','103'])
  check('Previous data remains after page retry', 'Un été tranquille' in q.locator('#discoverList').inner_text())
  q.locator('#discoverMore').click();q.wait_for_function('discoverySessionV33.page===3')
  check('TVmaze 404 end page terminates cleanly',q.evaluate('discoverySessionV33.complete') is True and not q.locator('#discoverMore').is_visible())
  q.locator('#discoverQuery').fill('ete');check('Search without accents matches loaded title',q.evaluate('FGDiscovery.view(discoverySessionV33,state.media).length')==1)
  q.locator('#discoverQuery').fill('');q.locator('#discoverSort').select_option('year-desc');check('Year sort changes visible order',q.evaluate('FGDiscovery.view(discoverySessionV33,state.media)[0].sourceId')=='103')
  q.locator('#discoverLanguageV33').fill('fr');q.locator('#discoverFilters button[type=submit]').click();q.wait_for_function('discoverySessionV33.page===1')
  check('French filter recognizes TVmaze English-language country names',q.evaluate('FGDiscovery.view(discoverySessionV33,state.media).map(x=>x.sourceId)')==['101'])
  check('Changing criteria resets server cursor',q.evaluate('qa.discoveryCalls.at(-1).page')==0)
  q.evaluate("()=>{state.media=[{id:'tvmaze-show-101',kind:'show',source:'tvmaze',sourceId:'101',title:'Avant',added:true,favorite:true}];render();}")
  q.locator('#discoverStatus').select_option('favorite');check('Favorite filter uses local identity not source title',q.evaluate('FGDiscovery.view(discoverySessionV33,state.media).length')==1)
  q.evaluate("()=>{state.media[0].added=false;render();}")
  check('Withdrawn media is not shown as already added',q.evaluate("()=>{let m=FGDiscovery.view({...discoverySessionV33,filters:{...discoverySessionV33.filters,status:'not-added'}},state.media);return m.some(x=>x.sourceId==='101');}") is True)
  # Re-add a previously withdrawn source, preserving its own personal fields and ID.
  q.locator('#discoverStatus').select_option('all')
  q.evaluate('''()=>{window.qaEnrichOriginal=enrichCatalog;enrichCatalog=async m=>({...m,added:true,isSuggestion:false,catalogComplete:true,catalogExpectedEpisodes:0,_pendingEpisodes:[],seasons:[]});state.media[0].userNote='Ancienne note conservée';state.media[0].rating=9;}''')
  q.evaluate("async()=>await add('tvmaze-show-101')")
  check('Previously withdrawn item can be re-added by exact provider ID',q.evaluate("state.media.some(m=>m.id==='tvmaze-show-101'&&m.added)"))
  check('Re-added item keeps its note and score rather than cloning',q.evaluate("state.media.length===1&&state.media[0].userNote==='Ancienne note conservée'&&state.media[0].rating===9"))
  q.evaluate("()=>{enrichCatalog=qaEnrichOriginal;overlay={type:'discover',title:'Découvrir'};render();}")
  q.wait_for_function('discoverySessionV33.page===1')
  q.locator('#discoverStatus').select_option('all');q.evaluate('qa.discoveryHold=true');q.evaluate("()=>{discoverySessionV33={...discoverySessionV33,more:true,complete:false};void discoveryLoadV33()}")
  q.wait_for_function('qa.discoverWaiter!==null')
  before=q.evaluate('discoverySessionV33.items.length')
  q.evaluate("()=>{overlay={type:'history',title:'Historique'};render();qa.discoverWaiter();}")
  q.wait_for_timeout(180)
  check('Response received after navigation does not modify loaded catalogue',q.evaluate('discoverySessionV33.items.length')==before)
  q.evaluate("()=>{qa.discoveryHold=false;state.settings.tmdbKey='';state.settings.discoverKind='movie';overlay={type:'discover',title:'Découvrir'};render();}")
  q.wait_for_selector('#discoverList button')
  check('Without TMDb, films do not claim fabricated popular list','recherche' in q.locator('#discoverList').inner_text().lower())
  q.locator('#discoverList button.primary').click();check('Apple no-key fallback opens contextual movie search',q.locator('#searchDialog').is_visible())
  q.keyboard.press('Escape');q.evaluate("()=>{state.settings.discoverKind='show';overlay={type:'discover',title:'Découvrir'};render();}")
  q.wait_for_function('discoverySessionV33.page===1')
  for width in [390,768,1360]:
   q.set_viewport_size({'width':width,'height':850});q.wait_for_timeout(70);q.evaluate("()=>document.getElementById('toast')?.classList.remove('show')")
   check(f'No horizontal overflow at {width}',q.evaluate('document.documentElement.scrollWidth<=innerWidth+1'))
   q.screenshot(path=str(OUT/f'discovery-{width}.png'),full_page=True)
  check('No uncaught JS errors in new scenarios',not errors)
  (OUT/'browser-v033.json').write_text(json.dumps({'scope':'real scripts/DOM, synthetic catalogue and ephemeral storage; not live origin','passed':len(checks),'checks':checks,'uncaught':errors},ensure_ascii=False,indent=2))
  browser.close()
if __name__=='__main__':
 try:run()
 except Exception:
  (OUT/'browser-v033-failure.json').write_text(json.dumps({'passed':len(checks),'checks':checks,'error':traceback.format_exc()},ensure_ascii=False,indent=2));raise
