#!/usr/bin/env python3
"""Real Java subprocess against a controlled occupied loopback port; no external network."""
from http.server import ThreadingHTTPServer, BaseHTTPRequestHandler
from pathlib import Path
import json, subprocess, threading, time
ROOT=Path(__file__).resolve().parents[1]
checks=[]
def check(name,value):
 if not value:raise AssertionError(name)
 checks.append(name);print('PASS',name)
class Server(ThreadingHTTPServer):
 allow_reuse_address=True
 daemon_threads=True
class Handler(BaseHTTPRequestHandler):
 def log_message(self,*args):pass
 def do_GET(self):
  self.server.paths.append(self.path)
  if self.server.mode=='slow':time.sleep(4)
  if self.server.mode=='redirect' and self.path=='/health':
   self.send_response(302);self.send_header('Location','http://127.0.0.1:47631/unexpected');self.end_headers();return
  data=(b'{"app":"FranchiseGuide PC","version":"0.9.0"}' if self.server.mode=='old' else b'Unrelated local service')
  try:
   self.send_response(200);self.send_header('Content-Length',str(len(data)));self.end_headers();self.wfile.write(data)
  except (BrokenPipeError,ConnectionResetError):pass
results=[]
for mode in ['old','other','slow','redirect']:
 server=Server(('127.0.0.1',47631),Handler);server.mode=mode;server.paths=[]
 worker=threading.Thread(target=server.serve_forever,daemon=True);worker.start()
 try:
  t=time.monotonic()
  p=subprocess.run(['java','-jar',str(ROOT/'desktop/dist/FranchiseGuide-PC.jar'),'--probe'],capture_output=True,text=True,timeout=8)
  elapsed=time.monotonic()-t
  check(mode+': occupied service never reports successful application probe',p.returncode!=0)
  if mode in ('old','other'):check(mode+': descriptive collision error is retained','ancienne version' in p.stderr)
  elif mode=='slow':
   check('Stalled service returns within five seconds',elapsed<5)
   check('Stalled service has explicit unavailable message','non joignable' in p.stderr)
  else:check('Occupied service redirects are not followed',server.paths==['/health'])
  results.append({'case':mode,'duration_seconds':round(elapsed,3),'exit_code':p.returncode,'paths':server.paths})
 finally:
  server.shutdown();server.server_close();worker.join(timeout=1)
out=ROOT/'tests/results/v014/desktop-startup.json';out.write_text(json.dumps({'scope':'Real compiled JAR, occupied loopback port supplied by a controlled HTTP service. No desktop GUI, no external API.', 'passed':len(checks),'checks':checks,'runs':results},ensure_ascii=False,indent=2)+'\n')
