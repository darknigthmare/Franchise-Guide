#!/usr/bin/env python3
"""Current V0.33 regression run. Exact executed commands; no inherited outcomes are counted."""
from pathlib import Path
import subprocess,os,json,time,tempfile
R=Path(__file__).resolve().parents[1];O=R/'tests/results/v033';O.mkdir(parents=True,exist_ok=True)
env=dict(os.environ);env['JAVA_OPTS']='-Xmx2g'
for k in ['FG_STEAM_API_KEY','FG_RETRO_API_KEY','FG_TMDB_API_KEY','FG_TMDB_READ_TOKEN','FG_RAWG_API_KEY','FG_TRAKT_CLIENT_ID','FG_TRAKT_CLIENT_SECRET']:env.pop(k,None)
report={'version':'0.33.0','scope':'Current host regression; DOM API/storage simulated; no Android SDK','executed':[],'not_run':[],'completed':False}
def write():(O/'current-qa.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
def run(name,args,timeout=500):
 print('RUN',name,flush=True);t=time.monotonic();record={'name':name,'command':args,'log':name+'.log'}
 with (O/(name+'.log')).open('w') as f:
  try:p=subprocess.run(args,cwd=R,env=env,stdout=f,stderr=subprocess.STDOUT,timeout=timeout);record.update(status='passed' if p.returncode==0 else 'failed',exit_code=p.returncode)
  except Exception as e:record.update(status='not_completed',error=str(e))
 record['seconds']=round(time.monotonic()-t,2);report['executed'].append(record);write();print(record['status'],name,flush=True)
run('structure',['python3','tools/validate_project.py'])
run('bootstrap',['python3','tests/bootstrap_security.py'])
run('js-syntax',['bash','-lc','for f in web/*.js server/*.cjs; do node --check "$f" || exit; done'])
files=[]
for f in sorted((R/'tests').glob('*.test.cjs')):
 missing=[]
 for dep in ['jsdom','fake-indexeddb']:
  if ('require(\''+dep+'\')') in f.read_text() or ('require("'+dep+'")') in f.read_text():
   if subprocess.run(['node','-e','require.resolve('+json.dumps(dep)+')'],cwd=R,env=env,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL).returncode:missing.append(dep)
 if missing:report['not_run'].append({'test':f.name,'reason':'Missing '+', '.join(missing)+'. Earlier results not counted.'})
 else:files.append(str(f.relative_to(R)))
run('node',['node','--test',*files])
with tempfile.TemporaryDirectory(prefix='fg31-qa-') as td:
 jar=Path(td)/'rules.jar';src=R/'app/src/main/java/com/franchiseguide/app'
 # All inherited domain tests, compiled together; this still does not compile Compose.
 classes=['TrackingRulesTest','TrackingV08Test','IntegrationRulesTest','CoreLogicTest','ReleaseRulesTest','GamingRulesTest','SteamMetadataTest','ActivityRulesTest','LibraryRulesTest','ImportReviewRulesTest','FranchiseRulesTest','InsightsProgressRulesTest','PersonalListsTest','EpisodeBatchRulesTest','WatchAvailabilityRulesTest','EpisodeDetailRulesTest','PublicTvDetailRulesTest','BookEditionRulesTest','BookSelectionRulesTest','ReliabilityRulesTest','SettingsRulesTest','ProtectedBackupTest','DiscoveryRulesV033Test']
 run('kotlin-compile',['kotlinc',*map(str,(src/'model').glob('*.kt')),*map(str,(src/'franchise').glob('*.kt')),*[str(R/'tests'/f'{x}.kt') for x in classes],str(R/'tests/ProtectedBackupInterop.kt'),'-include-runtime','-d',str(jar)])
 if jar.exists():
  for cls in classes:run('kotlin-'+cls,['java','-cp',str(jar),cls+'Kt',str(Path(td)/('protected-parity' if cls=='ProtectedBackupTest' else cls+'.out'))])
 run('native-signatures',['bash','tools/typecheck_native.sh'])
 run('pc-build',['bash','tools/build_pc.sh'])
 run('pc-http',['python3','tests/desktop_http.py'])
 run('pc-startup',['python3','tests/desktop_startup.py'])
 out=Path(td)/'handlers';out.mkdir()
 run('java-handlers-compile',['javac','--release','17','-d',str(out),*map(str,(R/'desktop/src/com/franchiseguide/desktop').glob('*.java')),*[str(R/'tests'/f'{x}.java') for x in ['SteamBridgeTest','SteamAuthTest','RetroBridgeTest','AutoServicesTest']]])
 for cls in ['SteamBridgeTest','SteamAuthTest','RetroBridgeTest','AutoServicesTest']:run('java-'+cls,['java','-cp',str(out),'com.franchiseguide.desktop.'+cls])
report['not_run'].append({'test':'two codecs programs with real org.json','reason':'FG_JSON_JAR absent. Signature stubs are not a parser execution.'})
for name in ['browser_harness','browser_v018','browser_v022','browser_v023','browser_v024','browser_v025','browser_v026','browser_v027','browser_v028','browser_v029','browser_v030','browser_v031','browser_v033']:
 run(name,['python3','tests/'+name+'.py'],180)
report['not_run'].append({'test':'Other Chromium suites not in selected regression and cross-language parity fixtures','reason':'Not rerun in this focused regression. Their historical logs remain separate; no count claimed.'})
report['completed']=True;report['failed_commands']=sum(x['status']!='passed' for x in report['executed']);report['passed_commands']=sum(x['status']=='passed' for x in report['executed']);write();print(json.dumps({'passed':report['passed_commands'],'failed':report['failed_commands']}))
raise SystemExit(bool(report['failed_commands']))
