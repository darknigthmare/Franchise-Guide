from pathlib import Path
from playwright.sync_api import sync_playwright
from browser_harness import SETUP,RESET,ROOT,WEB
import re,json,traceback
OUT=ROOT/'tests/results/v034';OUT.mkdir(parents=True,exist_ok=True);checks=[]
def check(n,v=True):
 if not v:raise AssertionError(n)
 checks.append(n);print('PASS',n,flush=True)
EXTRA=r'''()=>{qa.calls=[];qa.failGame=true;const src={show:'tmdb',movie:'tmdb',game:'rawg',book:'openlibrary'},id=(k,n)=>k==='book'?'/works/OL'+n+'W':String(n),mk=(k,n,t)=>({id:'scan-'+k+'-'+n,kind:k,source:src[k],sourceId:id(k,n),tmdbId:['show','movie'].includes(k)?n:null,title:t||'Space Saga '+n,year:2020,added:false,listIds:[]});qa.mk=mk;remoteSearch=async(q,k,p,m,o={})=>{qa.calls.push({k,p});if(k==='game'&&qa.failGame){qa.failGame=false;return {items:[],more:false,errors:['game fixture'],successes:0,complete:false}};let items=[];if(q==='Space Saga'&&p===1)items=[mk(k,{show:1,movie:2,game:3,book:4}[k])];return {items,more:false,errors:[],successes:1,complete:true};};}'''
def run():
 with sync_playwright() as pw:
  b=pw.chromium.launch(executable_path='/usr/bin/chromium',headless=True,args=['--no-sandbox']);p=b.new_page(viewport={'width':390,'height':1000});errs=[];p.on('pageerror',lambda e:errs.append(str(e)));p.on('dialog',lambda d:d.accept())
  html=(WEB/'index.html').read_text();p.set_content(re.sub(r'<script.*?</script>|<link[^>]+>|<meta http-equiv="Content-Security-Policy"[^>]+>','',html));p.add_style_tag(content=(WEB/'app.css').read_text());p.evaluate(SETUP)
  for f in re.findall(r'<script src="([^"]+)"',html):p.add_script_tag(content=(WEB/f).read_text())
  p.evaluate(EXTRA);p.evaluate(RESET);p.evaluate('''()=>{state.customFranchises=[{id:'custom-space',name:'Space Saga',queryTerms:['Space Saga'],aliases:['Star Fleet'],description:'',sortOrder:[],custom:true}];state.media=[];state.suggestions=[];state.episodes=[];save(false);overlay={type:'franchise',id:'custom-space',title:'Space Saga'};render();}''')
  p.locator('#frSearch').click();p.locator('#frScanStart').click();p.wait_for_function('document.querySelectorAll("[data-scan-kind]").length===4 && !document.querySelector("#frScanCancel:not(.hidden)")')
  check('four category panels',p.locator('[data-scan-kind]').count()==4);check('independent game error',p.locator('[data-scan-kind=game] [role=alert]').is_visible() and p.locator('[data-scan-kind=show] .franchise-scan-candidate').count()==1)
  p.locator('[data-scan-load=game]').click();p.wait_for_function('!document.querySelector("[data-scan-kind=game] [role=alert]")');check('retry only game',p.evaluate('qa.calls.filter(x=>x.k==="game").length')==2)
  for k in ['show','movie','game','book']:p.locator(f'[data-scan-all={k}]').click()
  check('temporary before commit',p.evaluate('state.suggestions.length')==0);p.locator('#frScanCommit').click();p.wait_for_selector('#franchiseSearch',state='detached');check('four grey suggestions',p.evaluate('state.suggestions.length')==4);check('no tracking or episodes',p.evaluate('state.episodes.length===0&&state.suggestions.every(x=>x.suggestion&&!x.added&&!x.watched&&x.plays===0&&x.userNote===null&&x.progressCurrent===0)'))
  p.evaluate('render()');p.locator('#frSearch').click();p.locator('#frScanStart').click();p.wait_for_function('document.querySelectorAll("[data-scan-kind]").length===4 && !document.querySelector("#frScanCancel:not(.hidden)")');check('known suggestions disabled',p.locator('.franchise-scan-candidate.known input:disabled').count()>=4);check('no overlap mobile',p.locator('[data-scan-kind=show]').evaluate('(e)=>{const h=e.querySelector(":scope>header").getBoundingClientRect(),r=e.querySelector(".franchise-scan-results").getBoundingClientRect();return r.top>=h.bottom-1&&e.scrollWidth<=e.clientWidth+1}'))
  check('no script error',not errs);(OUT/'browser-v034.json').write_text(json.dumps({'passed':len(checks),'checks':checks,'errors':errs},ensure_ascii=False,indent=2));b.close()
if __name__=='__main__':
 try:run()
 except Exception:(OUT/'browser-v034-failure.json').write_text(json.dumps({'checks':checks,'traceback':traceback.format_exc()},ensure_ascii=False,indent=2));raise
