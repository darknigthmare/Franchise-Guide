#!/usr/bin/env python3
"""Start an extracted standalone Web distribution and verify actual HTTP responses.
Usage: python check_extracted_web.py EXTRACTED_WEB CANONICAL_WEB
No browser or provider account validation. Credentials deliberately absent.
"""
from pathlib import Path
import json,os,re,socket,subprocess,sys,tempfile,time,urllib.request,urllib.error
root=Path(sys.argv[1]).resolve();canonical=Path(sys.argv[2]).resolve();checks=[]
def check(name,condition):
 if not condition:raise AssertionError(name)
 checks.append(name)
def normalized_version(value):
 if not isinstance(value,str) or not re.fullmatch(r'[0-9]+\.[0-9]+(?:\.[0-9]+)?',value):
  raise ValueError('Invalid FranchiseGuide release version')
 parts=value.split('.')
 return '.'.join(parts+['0']*(3-len(parts)))
def html_version(data):
 match=re.search(rb'<title>\s*FranchiseGuide Web\s+([0-9]+\.[0-9]+(?:\.[0-9]+)?)\s*</title>',data)
 if not match:raise ValueError('Canonical Web title has no release version')
 return normalized_version(match.group(1).decode('ascii'))
canonical_html=(canonical/'index.html').read_bytes()
# A suite supplies release metadata; an isolated Web copy may contain only the
# app and its server. In that case its canonical HTML remains the reference.
release_file=next((p for p in [canonical/'RELEASE.json',canonical.parent/'RELEASE.json',root/'RELEASE.json'] if p.is_file()),None)
expected_version=normalized_version(json.loads(release_file.read_text(encoding='utf-8'))['version']) if release_file else html_version(canonical_html)
version_source='RELEASE.json' if release_file else 'canonical HTML title'
sock=socket.socket();sock.bind(('127.0.0.1',0));port=sock.getsockname()[1];sock.close()
base=f'http://127.0.0.1:{port}'
def get(path='/',method='GET',headers=None):
 req=urllib.request.Request(base+path,method=method,headers=headers or {})
 try:
  with urllib.request.urlopen(req,timeout=4) as r:return r.status,{k.lower():v for k,v in r.headers.items()},r.read()
 except urllib.error.HTTPError as r:return r.code,{k.lower():v for k,v in r.headers.items()},r.read()
env={k:v for k,v in os.environ.items() if k not in {'FG_STEAM_API_KEY','FG_RETRO_API_KEY','FG_TMDB_API_KEY','FG_TMDB_READ_TOKEN','FG_RAWG_API_KEY','FG_TRAKT_CLIENT_ID','FG_TRAKT_CLIENT_SECRET'}}
env['FG_PORT']=str(port)
with tempfile.TemporaryFile() as log:
 process=subprocess.Popen(['node','server/start.cjs'],cwd=root,env=env,stdout=log,stderr=log)
 try:
  for _ in range(60):
   try:
    status,headers,body=get('/');break
   except OSError:time.sleep(.1)
  else:raise RuntimeError('Extracted Web server did not start')
  check('Extracted Web server starts canonical version',status==200 and body==canonical_html and html_version(body)==expected_version)
  files=[p for p in sorted(canonical.iterdir()) if p.is_file() and p.suffix in {'.html','.js','.css','.json','.webmanifest','.png','.wav'}]
  for f in files:
   status,h,body=get('/'+f.name);check('Extracted Web exact bytes '+f.name,status==200 and body==f.read_bytes())
  h=get('/app.js')[1]
  check('Scripts not immutable cached',h.get('cache-control')=='no-cache')
  check('No MIME sniffing',h.get('x-content-type-options')=='nosniff')
  check('No framing',h.get('x-frame-options')=='DENY')
  check('No referrer disclosure',h.get('referrer-policy')=='no-referrer')
  check('Sensitive browser permissions disabled',all(s in h.get('permissions-policy','') for s in ['camera=()','microphone=()']))
  check('HEAD has no response body',get('/app.js',method='HEAD')[2]==b'')
  check('Static writes refused',get('/',method='POST')[0]==405)
  check('Unknown resource does not return HTML app',get('/nonexistent.js')[0]==404)
  check('Foreign host refused',get('/',headers={'Host':'evil.invalid:'+str(port)})[0]==403)
  check('Server source not served',get('/server/start.cjs')[0]==404)
  check('Environment file not served',get('/.env')[0]==404)
  gaming={'X-FranchiseGuide-Client':'0.11'}
  check('Bridge cross-origin request refused',get('/api/gaming/status',headers={**gaming,'Origin':'https://evil.invalid'})[0]==403)
  check('Gaming app header required',get('/api/gaming/status')[0]==403)
  check('Missing Steam key honestly reported',json.loads(get('/api/gaming/status',headers=gaming)[2]).get('steam') is False)
  auth={'X-FranchiseGuide-Client':'0.12'}
  status,h,body=get('/api/gaming/auth/steam/session',headers=auth)
  check('Steam session response uncached',status==200 and h.get('cache-control')=='no-store')
  check('No verified Steam session invented',json.loads(body).get('authenticated') is False)
  check('Steam login requires operator key',get('/api/gaming/auth/steam/begin',method='POST',headers=auth)[0]==503)
  check('Steam library requires session',get('/api/gaming/steam/library',headers=auth)[0]==401)
  check('Invalid Steam callback refused',get('/api/gaming/auth/steam/callback?state=missing',headers={'Accept':'text/html'})[0]==401)
  retro={'X-FranchiseGuide-Client':'0.14'}
  check('Missing Retro key honestly reported',json.loads(get('/api/gaming/retro/status',headers=retro)[2]).get('configured') is False)
  check('Retro app header required',get('/api/gaming/retro/status')[0]==403)
  check('Retro cross-origin request refused',get('/api/gaming/retro/status',headers={**retro,'Origin':'https://evil.invalid'})[0]==403)
  check('Retro read requires operator key',get('/api/gaming/retro/profile?user=Fixture_Player',headers=retro)[0]==503)
  check('Retro writes refused',get('/api/gaming/retro/profile?user=Fixture_Player',method='POST',headers=retro)[0]==405)
  services={'X-FranchiseGuide-Services':'1'}
  status,h,body=get('/api/services/config',headers=services);caps=json.loads(body)
  check('Automatic service config reachable from extracted server',status==200 and caps.get('contract')==1)
  check('Extracted server exposes no-key catalogues without fake credentials',caps.get('keyless')==['tvmaze','itunes','freetogame','openlibrary'] and not any(caps.get(k) for k in ['tmdb','rawg','trakt','steam','retro']))
  check('Automatic service config never cached',h.get('cache-control')=='no-store')
  check('Automatic service app header required',get('/api/services/config')[0]==403)
  check('Automatic service foreign Origin rejected',get('/api/services/config',headers={**services,'Origin':'https://evil.test'})[0]==403)
  check('Missing full catalogue credential is explicit',get('/api/services/tmdb/3/movie/1',headers=services)[0]==503)
  check('Unconfigured Trakt access not fabricated',get('/api/services/trakt/users/settings',headers=services)[0]==503)
  check('Arbitrary upstream path refused',get('/api/services/tvmaze/unknown',headers=services)[0]==400)
  check('Public episode details read-only',get('/api/services/tvmaze/episodes/1',method='POST',headers=services)[0]==405)
  check('Public people expansion validated',get('/api/services/tvmaze/people/1?embed=show',headers=services)[0]==400)
  print(json.dumps({'version':expected_version,'version_source':version_source,'checks_passed':len(checks),'canonical_served_files':len(files),'checks':checks,'scope':'real HTTP to extracted personal server; no real provider account or browser origin'},ensure_ascii=False,indent=2))
 finally:
  process.terminate()
  try:process.wait(timeout=5)
  except subprocess.TimeoutExpired:process.kill();process.wait()
