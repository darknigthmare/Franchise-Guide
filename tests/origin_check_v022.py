"""Attempt the real local origin. Record failures; never replace them with fixture passes."""
from pathlib import Path
import subprocess,os,json,time,urllib.request,socket,shutil
from playwright.sync_api import sync_playwright
ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'tests/results/v022';OUT.mkdir(parents=True,exist_ok=True)
s=socket.socket();s.bind(('127.0.0.1',0));port=s.getsockname()[1];s.close();origin=f'http://127.0.0.1:{port}/'
env=dict(os.environ,FG_PORT=str(port),FG_STEAM_API_KEY='',FG_RETRO_API_KEY='')
result={'version':'0.22.0','origin':origin,'real_accounts':False,'agent_browser':{'installed':shutil.which('agent-browser') is not None}}
log=(OUT/'origin-server.log').open('w');proc=subprocess.Popen(['node','server/start.cjs'],cwd=ROOT,env=env,stdout=log,stderr=log)
try:
 for _ in range(60):
  try:
   with urllib.request.urlopen(origin,timeout=2) as r:result['http']={'status':r.status,'bytes':len(r.read())}
   break
  except OSError:time.sleep(.1)
 else:raise RuntimeError('HTTP server unavailable')
 try:
  attempt=subprocess.run(['agent-browser','open',origin],cwd=ROOT,capture_output=True,text=True,timeout=20)
  result['agent_browser'].update(exit_code=attempt.returncode,stdout=attempt.stdout,stderr=attempt.stderr)
 except (OSError,subprocess.TimeoutExpired) as e:result['agent_browser']['error']=str(e)
 with sync_playwright() as p:
  b=p.chromium.launch(executable_path='/usr/bin/chromium',headless=True,args=['--no-sandbox']);page=b.new_page(viewport={'width':1360,'height':940});errs=[];page.on('pageerror',lambda e:errs.append(str(e)))
  try:
   r=page.goto(origin,wait_until='domcontentloaded',timeout=15000);page.wait_for_timeout(300)
   result['browser']={'status':r.status if r else None,'title':page.title(),'errors':errs,'navigation_failed':False}
   page.screenshot(path=str(OUT/'actual-origin.png'),full_page=True)
  except Exception as e:result['browser']={'navigation_failed':True,'error':str(e),'no_security_bypass':True}
  b.close()
finally:
 proc.terminate()
 try:proc.wait(timeout=5)
 except subprocess.TimeoutExpired:proc.kill();proc.wait()
 log.close()
 (OUT/'origin-attempt.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n');print(json.dumps(result,ensure_ascii=False,indent=2))
