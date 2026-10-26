#!/usr/bin/env python3
"""Run executable checks available on this host; explicitly record blocked dependencies.
Does NOT substitute a partial run for tools/run_checks.sh or an Android/real-account QA.
No production secret is passed to test children. Results always record the exact commands.
"""
from pathlib import Path
import subprocess,os,time,json,tempfile,argparse,shlex
ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'tests/results/v035';OUT.mkdir(parents=True,exist_ok=True)
env=dict(os.environ)
env['JAVA_OPTS']='-Xmx2g'
for k in ['FG_STEAM_API_KEY','FG_RETRO_API_KEY','FG_TMDB_API_KEY','FG_TMDB_READ_TOKEN','FG_RAWG_API_KEY','FG_TRAKT_CLIENT_ID','FG_TRAKT_CLIENT_SECRET']:env.pop(k,None)
kotlin=Path.home()/'.sdkman/candidates/kotlin/current/bin'
if kotlin.exists():env['PATH']=str(kotlin)+os.pathsep+env.get('PATH','')
report={'version':'0.35.0','mode':'available-host checks, exclusions explicit; not a complete full-environment qualification','executed':[],'not_run':[]}
def write(): (OUT/'available-qa.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
def run(name,args,timeout=600):
 print('RUN',name,flush=True);started=time.monotonic();record={'name':name,'command':args,'log':name+'.log'}
 with (OUT/(name+'.log')).open('w') as log:
  try:
   p=subprocess.run(args,cwd=ROOT,env=env,stdout=log,stderr=subprocess.STDOUT,timeout=timeout)
   record.update(exit_code=p.returncode,status='passed' if p.returncode==0 else 'failed')
  except subprocess.TimeoutExpired:record.update(exit_code=None,status='timeout')
  except OSError as e:record.update(exit_code=None,status='unavailable',error=str(e))
 record['seconds']=round(time.monotonic()-started,2);report['executed'].append(record);write();print(record['status'],name,flush=True)
run('structure',['python3','tools/validate_project.py'])
run('bootstrap',['python3','tests/bootstrap_security.py'])
run('javascript-syntax',['bash','-lc','for f in web/*.js server/*.cjs; do node --check "$f" || exit; done'])
files=sorted(ROOT.glob('tests/*.test.cjs'));eligible=[]
for file in files:
 deps=[d for d in ['jsdom','fake-indexeddb'] if f"require('{d}')" in file.read_text() or f'require("{d}")' in file.read_text()]
 missing=[]
 for d in deps:
  result=subprocess.run(['node','-e',f'require.resolve({json.dumps(d)})'],cwd=ROOT,env=env,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
  if result.returncode:missing.append(d)
 if missing:report['not_run'].append({'name':str(file.relative_to(ROOT)),'reason':'Missing locked QA dependencies: '+', '.join(missing)+'. Not executed on this host; no earlier result counted.'})
 else:eligible.append(str(file.relative_to(ROOT)))
run('node-available',['node','--test',*eligible])
with tempfile.TemporaryDirectory(prefix='fg35-qa-') as td:
 tmp=Path(td);jar=tmp/'core.jar'
 classes=['TrackingRulesTest','TrackingV08Test','IntegrationRulesTest','CoreLogicTest','ReleaseRulesTest','GamingRulesTest','SteamMetadataTest','ActivityRulesTest','LibraryRulesTest','ImportReviewRulesTest','FranchiseRulesTest','InsightsProgressRulesTest','PersonalListsTest','EpisodeBatchRulesTest','WatchAvailabilityRulesTest','EpisodeDetailRulesTest','PublicTvDetailRulesTest','BookEditionRulesTest','BookSelectionRulesTest','ReliabilityRulesTest','SettingsRulesTest','DiscoveryRulesV033Test','FranchiseScanRulesTest','TraktCheckInRulesTest','BackupTodayRulesTest','ProtectedBackupTest']
 args=['kotlinc',*map(str,(ROOT/'app/src/main/java/com/franchiseguide/app/model').glob('*.kt')),*map(str,(ROOT/'app/src/main/java/com/franchiseguide/app/franchise').glob('*.kt')),*[str(ROOT/'tests'/f'{x}.kt') for x in classes],str(ROOT/'tests/ProtectedBackupInterop.kt'),'-include-runtime','-d',str(jar)]
 run('kotlin-domain-compile',args)
 if jar.exists():
  for x in classes:run('kotlin-'+x,['java','-cp',str(jar),x+'Kt',str(tmp/'protected-parity')] if x=='ProtectedBackupTest' else ['java','-cp',str(jar),x+'Kt',str(tmp/'calendar-parity.ics')] if x=='ActivityRulesTest' else ['java','-cp',str(jar),x+'Kt',str(tmp/'import-parity.json')] if x=='ImportReviewRulesTest' else ['java','-cp',str(jar),x+'Kt',str(tmp/'franchise-parity.txt')] if x=='FranchiseRulesTest' else ['java','-cp',str(jar),x+'Kt',str(tmp/'insights-parity.json')] if x=='InsightsProgressRulesTest' else ['java','-cp',str(jar),x+'Kt',str(tmp/'lists-parity.json')] if x=='PersonalListsTest' else ['java','-cp',str(jar),x+'Kt',str(tmp/'episodes-parity.json')] if x=='EpisodeBatchRulesTest' else ['java','-cp',str(jar),x+'Kt',str(tmp/'availability-parity.json')] if x=='WatchAvailabilityRulesTest' else ['java','-cp',str(jar),x+'Kt',str(tmp/'episode-detail-parity.txt')] if x=='EpisodeDetailRulesTest' else ['java','-cp',str(jar),x+'Kt',str(tmp/'public-tv-parity.txt')] if x=='PublicTvDetailRulesTest' else ['java','-cp',str(jar),x+'Kt',str(tmp/'book-parity.txt')] if x=='BookEditionRulesTest' else ['java','-cp',str(jar),x+'Kt',str(tmp/'selection-parity.txt')] if x=='BookSelectionRulesTest' else ['java','-cp',str(jar),x+'Kt',str(tmp/'franchise-scan-parity.txt')] if x=='FranchiseScanRulesTest' else ['java','-cp',str(jar),x+'Kt',str(tmp/'checkin-parity.txt')] if x=='TraktCheckInRulesTest' else ['java','-cp',str(jar),x+'Kt'])
  run('franchise-scan-cross-platform-parity',['node','tests/franchise-scan-parity.cjs',str(tmp/'franchise-scan-parity.txt')])
  run('checkin-cross-platform-parity',['node','tests/checkin-parity.cjs',str(tmp/'checkin-parity.txt')])
  run('protected-backup-jvm-to-js',['node','tests/protected-backup-parity.cjs',str(tmp/'protected-parity')])
  run('protected-backup-js-to-jvm',['java','-cp',str(jar),'ProtectedBackupInteropKt',str(tmp/'protected-parity')])
  run('book-selection-cross-platform-parity',['node','tests/book-selection-parity.cjs',str(tmp/'selection-parity.txt')])
  run('book-editions-cross-platform-parity',['node','tests/book-editions-parity.cjs',str(tmp/'book-parity.txt')])
  run('public-tv-cross-platform-parity',['node','tests/tvmaze-detail-parity.cjs',str(tmp/'public-tv-parity.txt')])
  run('episode-detail-cross-platform-parity',['node','tests/episode-detail-parity.cjs',str(tmp/'episode-detail-parity.txt')])
  run('availability-cross-platform-parity',['node','tests/availability-parity.cjs',str(tmp/'availability-parity.json')])
  run('episodes-cross-platform-parity',['node','tests/episodes-parity.cjs',str(tmp/'episodes-parity.json')])
  run('personal-lists-cross-platform-parity',['node','tests/lists-parity.cjs',str(tmp/'lists-parity.json')])
  run('insights-cross-platform-parity',['node','tests/insights-parity.cjs',str(tmp/'insights-parity.json')])
  run('franchise-cross-platform-parity',['node','tests/franchise-parity.cjs',str(tmp/'franchise-parity.txt')])
  run('import-review-counter-parity',['node','tests/import-count-parity.cjs',str(tmp/'import-parity.json')])
  run('calendar-cross-platform-parity',['node','tests/calendar-parity.cjs',str(tmp/'calendar-parity.ics')])
 jsonjar=env.get('FG_JSON_JAR')
 if jsonjar and Path(jsonjar).exists():
  src=ROOT/'app/src/main/java/com/franchiseguide/app'
  codecs=tmp/'codecs.jar'
  run('kotlin-real-json-compile',['kotlinc',*map(str,(src/'model').glob('*.kt')),str(src/'data/GamingCodec.kt'),str(src/'data/MediaIdentityCodec.kt'),str(src/'data/BookSelectionCodec.kt'),'tests/GamingCodecRetroTest.kt','tests/MediaIdentityCodecTest.kt','-classpath',jsonjar,'-include-runtime','-d',str(codecs)])
  if codecs.exists():
   for cls in ['GamingCodecRetroTestKt','MediaIdentityCodecTestKt']:run(cls,['java','-cp',str(codecs)+os.pathsep+jsonjar,cls])
 else:report['not_run'].append({'name':'Kotlin GamingCodecRetroTest + MediaIdentityCodecTest against real org.json','reason':'FG_JSON_JAR is not provided or does not exist. Signature stubs are separate and are not parser execution.'});write()
 run('native-signatures',['bash','tools/typecheck_native.sh'])
 run('pc-build',['bash','tools/build_pc.sh'])
 run('pc-http',['python3','tests/desktop_http.py'])
 run('pc-startup',['python3','tests/desktop_startup.py'])
 classesdir=tmp/'java-handlers';classesdir.mkdir()
 run('java-handler-compile',['javac','--release','17','-d',str(classesdir),*map(str,(ROOT/'desktop/src/com/franchiseguide/desktop').glob('*.java')),'tests/SteamBridgeTest.java','tests/SteamAuthTest.java','tests/RetroBridgeTest.java','tests/AutoServicesTest.java'])
 for c in ['SteamBridgeTest','SteamAuthTest','RetroBridgeTest','AutoServicesTest']:run('java-'+c,['java','-cp',str(classesdir),'com.franchiseguide.desktop.'+c])
for name in ['browser_harness','browser_v08','browser_v09','browser_v010','browser_v011','browser_v012','browser_v015','browser_v016','browser_v017','browser_v018','browser_v019','browser_v020','browser_v021','browser_v022','browser_v023','browser_v024','browser_v025','browser_v026','browser_v027','browser_v028','browser_v029','browser_v030','browser_v031','browser_v033','browser_v034','browser_v035']:
 run(name,['python3','tests/'+name+'.py'],timeout=180)
report['passed_commands']=sum(x['status']=='passed' for x in report['executed'])
report['failed_commands']=sum(x['status'] in ['failed','timeout','unavailable'] for x in report['executed'])
report['completed']=True;write()
print(json.dumps({'passed':report['passed_commands'],'failed':report['failed_commands'],'not_run':len(report['not_run'])}),flush=True)
raise SystemExit(1 if report['failed_commands'] else 0)
