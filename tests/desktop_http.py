"""Actual compiled JAR/HTTP integration test. No browser UI or external APIs involved."""
from pathlib import Path
import subprocess,time,json,urllib.request,urllib.error,socket,zipfile,os
ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'tests/results/current/desktop';OUT.mkdir(parents=True,exist_ok=True)
checks=[]
def check(name,ok):
    if not ok:raise AssertionError(name)
    checks.append(name);print('PASS',name)
def request(path='/',method='GET',headers=None):
    req=urllib.request.Request('http://127.0.0.1:47631'+path,method=method,headers=headers or {})
    try:
        with urllib.request.urlopen(req,timeout=3) as r:return r.status,dict(r.headers),r.read()
    except urllib.error.HTTPError as r:return r.code,dict(r.headers),r.read()
sock=socket.socket();sock.settimeout(.2)
if sock.connect_ex(('127.0.0.1',47631))==0:raise SystemExit('Port occupied: close the old launcher before this isolated test.')
sock.close()
log=(OUT/'desktop-server.log').open('w')
env={k:v for k,v in os.environ.items() if k not in {'FG_STEAM_API_KEY','FG_RETRO_API_KEY','FG_TMDB_API_KEY','FG_TMDB_READ_TOKEN','FG_RAWG_API_KEY','FG_TRAKT_CLIENT_ID','FG_TRAKT_CLIENT_SECRET'}}
p=subprocess.Popen(['java','-jar',str(ROOT/'desktop/dist/FranchiseGuide-PC.jar'),'--headless'],stdout=log,stderr=subprocess.STDOUT,env=env)
try:
    for _ in range(50):
        try:
            status,headers,data=request('/health')
            if status==200:break
        except OSError:time.sleep(.1)
    check('JAR HTTP version matches release',json.loads(data)['version']==json.loads((ROOT/'RELEASE.json').read_text())['version'])
    for name in ['protected-backup-core.js','protected-backup-ui.js','settings-core.js','settings-ui.js','book-selection-core.js','book-selection-ui.js','franchise-core.js','franchise-ui.js','activity-core.js','activity-ui.js','library-core.js','library-ui.js','import-core.js','import-ui.js','sync-policy.js','sync-center.js','retro-core.js','retro-connect.js','durable.js','integrity.js','steam-library.js','steam-connect.js','gaming-core.js','gaming.js','index.html','app.js','core.js','reliability-core.js','integrated.js','trakt.js','backup.js','persistence.js','people.js','views.js','network.js','startup.js','reviews.js','release.js','maintenance.js','app.css','manifest.webmanifest','sw.js','detail_signature.wav','icon-192.png','icon-512.png']:
        status,h,body=request('/'+name)
        check('Embedded resource exact bytes: '+name,status==200 and body==(ROOT/'web'/name).read_bytes())
    check('WAV MIME served',request('/detail_signature.wav')[1].get('Content-type')=='audio/wav')
    check('Unknown JS asset returns 404, not HTML',request('/does-not-exist.js')[0]==404)
    check('Traversal rejected',request('/%2e%2e/README.md')[0]==400)
    check('Disallowed Host rejected',request('/health',headers={'Host':'malicious.invalid:47631'})[0]==403)
    check('POST refused',request('/',method='POST')[0]==405)
    check('HEAD returns no body',request('/app.js',method='HEAD')[2]==b'')
    check('Scripts not stale cached',request('/app.js')[1].get('Cache-control')=='no-cache')
    check('CSP disallows object injection',"object-src 'none'" in request('/')[1].get('Content-security-policy',''))
    check('Framing is denied',request('/')[1].get('X-frame-options')=='DENY')
    check('Referrer information is suppressed',request('/')[1].get('Referrer-policy')=='no-referrer')
    check('Camera and microphone disabled',all(x in request('/')[1].get('Permissions-policy','') for x in ['camera=()','microphone=()']))
    check('No inline script execution allowed by HTTP policy',"script-src 'self';" in request('/')[1].get('Content-security-policy',''))
    status,h,body=request('/api/gaming/status',headers={'X-FranchiseGuide-Client':'0.11'})
    check('Embedded Steam bridge status is honest without key',status==200 and json.loads(body)['steam'] is False)
    check('Embedded gaming API has no browser caching',h.get('Cache-control')=='no-store')
    check('Embedded gaming API blocks missing app header',request('/api/gaming/status')[0]==403)
    check('Embedded gaming API blocks foreign Origin',request('/api/gaming/status',headers={'X-FranchiseGuide-Client':'0.11','Origin':'https://evil.test'})[0]==403)
    check('Embedded gaming API blocks writes',request('/api/gaming/steam',method='POST',headers={'X-FranchiseGuide-Client':'0.11'})[0]==405)
    check('Embedded gaming API refuses read without key',request('/api/gaming/steam?steamid=76561198000000001&appid=123',headers={'X-FranchiseGuide-Client':'0.11'})[0]==503)
    auth={'X-FranchiseGuide-Client':'0.12'}
    status,h,body=request('/api/gaming/auth/steam/session',headers=auth)
    check('Actual JAR session dispatch without login',status==200 and json.loads(body)['authenticated'] is False and h.get('Cache-control')=='no-store')
    check('Actual JAR auth endpoint requires app header',request('/api/gaming/auth/steam/session')[0]==403)
    check('Actual JAR begin requires operator API key',request('/api/gaming/auth/steam/begin',method='POST',headers=auth)[0]==503)
    check('Actual JAR library needs verified session',request('/api/gaming/steam/library',headers=auth)[0]==401)
    status,h,body=request('/api/gaming/auth/steam/callback?state=missing',headers={'Accept':'text/html'})
    check('Actual JAR callback error includes safe return page',status==401 and h.get('Content-type','').startswith('text/html') and b'Revenir' in body)
    check('Actual JAR auth endpoint rejects foreign origin',request('/api/gaming/auth/steam/session',headers={**auth,'Origin':'https://evil.test'})[0]==403)
    retro_headers={'X-FranchiseGuide-Client':'0.14'}
    status,h,body=request('/api/gaming/retro/status',headers=retro_headers)
    check('Actual JAR Retro status is honest without key',status==200 and json.loads(body)['configured'] is False)
    check('Actual JAR Retro response is never browser cached',h.get('Cache-control')=='no-store')
    check('Actual JAR Retro read requires app header',request('/api/gaming/retro/status')[0]==403)
    check('Actual JAR Retro rejects foreign Origin',request('/api/gaming/retro/status',headers={**retro_headers,'Origin':'https://evil.test'})[0]==403)
    check('Actual JAR Retro blocks writes',request('/api/gaming/retro/profile?user=Fixture_Player',method='POST',headers=retro_headers)[0]==405)
    check('Actual JAR Retro profile requires configured server key',request('/api/gaming/retro/profile?user=Fixture_Player',headers=retro_headers)[0]==503)
    for name in ['tvmaze-detail-core.js','tvmaze-detail-ui.js','book-editions-core.js','book-editions-ui.js','episode-detail-core.js','episode-detail-ui.js','autosetup-core.js','services-runtime.js','autosetup-ui.js','availability-core.js','availability-ui.js','episodes-core.js','episodes-ui.js','progress-core.js','insights-core.js','insights-ui.js','catalog-core.js','catalog-ui.js','lists-core.js','lists-ui.js']:
        st,heads,payload=request('/'+name)
        check('Canonical additional module served '+name,st==200 and payload==(ROOT/'web'/name).read_bytes())
    services={'X-FranchiseGuide-Services':'1'}
    status,h,body=request('/api/services/config',headers=services)
    caps=json.loads(body)
    check('Actual JAR automatic services config reachable',status==200 and caps.get('app')=='FranchiseGuide Auto Services' and caps.get('contract')==1)
    check('Actual JAR no-key sources available without publisher credentials',caps.get('keyless')==['tvmaze','itunes','freetogame','openlibrary'] and not any(caps.get(k) for k in ['tmdb','rawg','trakt','steam','retro']))
    check('Service config is never cached',h.get('Cache-control')=='no-store')
    check('Automatic service routes require app header',request('/api/services/config')[0]==403)
    check('Automatic service routes reject foreign Origin',request('/api/services/config',headers={**services,'Origin':'https://evil.test'})[0]==403)
    check('Unconfigured full catalogue is explicit publisher activation error',request('/api/services/tmdb/3/movie/1',headers=services)[0]==503)
    check('Unconfigured account connection does not fake app credentials',request('/api/services/trakt/users/settings',headers=services)[0]==503)
    check('Automatic routes reject arbitrary upstream paths',request('/api/services/tvmaze/unknown',headers=services)[0]==400)
    for path in ['/api/services/tvmaze/people/1?embed=show','/api/services/tvmaze/episodes/1/guestcast?embed=show','/api/services/tvmaze/people/1/images']:
        check('Public detail route rejects unsupported expansion '+path,request(path,headers=services)[0]==400)
    check('Public detail routes stay read-only',request('/api/services/tvmaze/people/1',method='POST',headers=services)[0]==405)
    with zipfile.ZipFile(ROOT/'desktop/dist/FranchiseGuide-PC.jar') as z:
        raw=z.read('com/franchiseguide/desktop/Main.class')
        check('Compiled for Java17 class version61',int.from_bytes(raw[6:8],'big')==61)
    (OUT/'desktop-http.json').write_text(json.dumps({'passed':len(checks),'checks':checks,'scope':'Real JAR and HTTP, without a graphical desktop session.'},ensure_ascii=False,indent=2))
finally:
    p.terminate()
    try:p.wait(timeout=5)
    except subprocess.TimeoutExpired:p.kill();p.wait()
    log.close()
