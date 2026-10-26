"""One real-origin navigation attempt. Never bypass environment policy, never use live credentials."""
from pathlib import Path
import subprocess,os,json,urllib.request,traceback,time
from playwright.sync_api import sync_playwright
ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'tests/results/v023';OUT.mkdir(parents=True,exist_ok=True)
env=os.environ.copy()
for key in ['FG_STEAM_API_KEY','FG_RETRO_API_KEY']:env.pop(key,None)
report={'version':'0.23.0','real_accounts':False,'no_security_bypass':True}
server=subprocess.Popen(['node','-e',"const s=require('./server/start.cjs').start({port:0});s.on('listening',()=>console.log(s.address().port));"],cwd=ROOT,env=env,stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True)
try:
 port=int(server.stdout.readline().strip());origin=f'http://127.0.0.1:{port}/';report['origin']=origin
 with urllib.request.urlopen(origin,timeout=8) as r:report['http']={'status':r.status,'bytes':len(r.read())}
 try:
  cli=subprocess.run(['agent-browser','open',origin],capture_output=True,text=True,timeout=12)
  report['agent_browser']={'exit':cli.returncode,'stdout':cli.stdout[:1000],'stderr':cli.stderr[:1000]}
 except (OSError,subprocess.TimeoutExpired) as e:report['agent_browser']={'available':False,'error':str(e)}
 with sync_playwright() as p:
  browser=p.chromium.launch(executable_path='/usr/bin/chromium',headless=True,args=['--no-sandbox']);page=browser.new_page(viewport={'width':1360,'height':1000});errors=[];page.on('pageerror',lambda e:errors.append(str(e)))
  try:
   response=page.goto(origin,wait_until='domcontentloaded',timeout=12000);page.wait_for_timeout(500)
   report['browser']={'status':response.status if response else None,'title':page.title(),'errors':errors,'body':page.locator('body').inner_text()[:1800]}
   page.screenshot(path=str(OUT/'real-origin-initial.png'))
  except Exception as e:report['browser']={'navigation_failed':True,'error':str(e)}
  finally:browser.close()
finally:
 server.terminate()
 try:server.wait(timeout=4)
 except subprocess.TimeoutExpired:server.kill()
 (OUT/'real-origin-attempt.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
 print(json.dumps(report,ensure_ascii=False,indent=2))
