"""Exact archived V0.30 runtime, real DOM with deterministic fake storage/HTTP. No real keys."""
from pathlib import Path
import re,json,zipfile,sys
from playwright.sync_api import sync_playwright
from browser_harness import SETUP,RESET
R=Path(__file__).resolve().parents[1];src=Path(sys.argv[1]) if len(sys.argv)>1 else Path('/mnt/data/FranchiseGuide_Suite_V0_30.zip');rows=[]
with zipfile.ZipFile(src) as z, sync_playwright() as p:
 prefix=z.namelist()[0].split('/')[0]+'/web/';h=z.read(prefix+'index.html').decode();b=p.chromium.launch(executable_path='/usr/bin/chromium',headless=True,args=['--no-sandbox']);q=b.new_page();q.on('dialog',lambda d:d.accept());errors=[];q.on('pageerror',lambda e:errors.append(str(e)))
 q.set_content(re.sub(r'<script.*?</script>|<link[^>]+>|<meta http-equiv="Content-Security-Policy"[^>]+>','',h));q.evaluate(SETUP)
 for f in re.findall(r'<script src="([^"]+)"',h):q.add_script_tag(content=z.read(prefix+f).decode())
 q.wait_for_timeout(60);q.evaluate(RESET)
 rows.append(q.evaluate('''()=>{state.settings.tmdbKey='old-fixture';save(false);const main=localStorage.getItem(KEY),set=localStorage.setItem;localStorage.setItem=(k,v)=>{if(k===KEY)throw Error('quota fixture');set(k,v)};state.settings.tmdbKey='new-fixture';try{save(false)}catch{}localStorage.setItem=set;return {case:'partial private persistence',mainUnchanged:main===localStorage.getItem(KEY),memory:state.settings.tmdbKey,disk:JSON.parse(localStorage.getItem(SERVICES_KEY)).tmdbKey}}'''))
 q.evaluate(RESET);q.evaluate('''()=>{state.settings.traktId='qa-client';state.settings.traktSecret='qa-secret';state.settings.traktAutoSync=false;save(false);saveTraktAuth({accessToken:'qa-token',refreshToken:'qa-refresh',expiresAt:Math.floor(Date.now()/1000)+86400,username:'qa-user'})}''');q.locator('#settings').click();q.locator('#advancedServices').evaluate('(e)=>e.open=true');q.locator('#trakt').fill('new-client');q.evaluate('''()=>{window.oldSet=localStorage.setItem;localStorage.setItem=(k,v)=>{if(k===KEY)throw Error('quota fixture');oldSet(k,v)}}''');q.locator('#saveSettings').click();rows.append(q.evaluate('''()=>{localStorage.setItem=oldSet;return {case:'disconnect before failed commit',connected:traktConnected(),memoryClient:state.settings.traktId,diskClient:JSON.parse(localStorage.getItem(SERVICES_KEY)).traktId}}'''))
 q.evaluate(RESET);q.evaluate('save(false)');q.locator('#settings').click();q.locator('#lang').fill('invalid language');q.locator('#region').fill('12');q.locator('#saveSettings').click();rows.append(q.evaluate('''()=>({case:'invalid preferences accepted',language:state.settings.language,region:state.settings.region,closed:!document.querySelector('#settingsDialog').open})'''));b.close()
assert rows[0]['disk']=='new-fixture' and rows[0]['memory']=='old-fixture' and rows[0]['mainUnchanged']
assert rows[1]['connected'] is False and rows[2]['region']=='12' and rows[2]['closed']
d={'base':'Exact V0.30 runtime modules from supplied ZIP','scope':'Real DOM, controlled provider/storage','cases':rows,'uncaughtErrors':errors};out=R/'tests/results/v031';out.mkdir(parents=True,exist_ok=True);(out/'before-v030.json').write_text(json.dumps(d,ensure_ascii=False,indent=2));print(json.dumps(d,indent=2))
