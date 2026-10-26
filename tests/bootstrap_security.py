#!/usr/bin/env python3
"""Exercise the actual shell bootstrap's corrupt-download refusal. No network or Gradle execution."""
import json, os, pathlib, shutil, subprocess, tempfile
ROOT = pathlib.Path(__file__).resolve().parents[1]
checks = []
def check(name, condition):
    if not condition: raise AssertionError(name)
    checks.append(name); print('PASS', name)
with tempfile.TemporaryDirectory() as name:
    p = pathlib.Path(name); (p/'tools').mkdir(); (p/'bin').mkdir()
    script = p/'tools/bootstrap-gradle.sh'; shutil.copyfile(ROOT/'tools/bootstrap-gradle.sh', script)
    curl = p/'bin/curl'
    curl.write_text('#!/bin/sh\nwhile [ "$#" -gt 0 ]; do\n if [ "$1" = "-o" ]; then shift; printf "controlled invalid archive" > "$1"; exit 0; fi\n shift\ndone\nexit 99\n')
    curl.chmod(0o755)
    unzip = p/'bin/unzip'; unzip.write_text('#!/bin/sh\nprintf attempted > "'+str(p/'unzip-called')+'"\nexit 99\n'); unzip.chmod(0o755)
    env = dict(os.environ, PATH=str(p/'bin')+os.pathsep+os.environ['PATH'])
    syntax = subprocess.run(['sh','-n',str(script)], capture_output=True)
    check('Shell syntax accepted',syntax.returncode==0)
    result = subprocess.run(['sh',str(script),'--version'],env=env,capture_output=True,text=True,timeout=5)
    check('Incorrect download hash rejects bootstrap with exit 3',result.returncode==3)
    check('Refusal explains incorrect fingerprint','Empreinte Gradle incorrecte' in result.stderr)
    check('Corrupt download removed',not (p/'.gradle-bootstrap/gradle-8.11.1-bin.zip').exists())
    check('No extraction attempted',not (p/'unzip-called').exists())
    check('No Gradle distribution created',not (p/'.gradle-bootstrap/gradle-8.11.1').exists())
    ps=(ROOT/'tools/bootstrap-gradle.ps1').read_text()
    check('PowerShell defines same expected hash', 'f397b287023acdba1e9f6fc5ea72d22dd63669d59ed4a289a29b1a76eee151c6' in ps)
    check('PowerShell has hash-check before extraction (static only)',ps.index('Get-FileHash') < ps.index('Expand-Archive'))
out=ROOT/'tests/results/v014/bootstrap-security.json'; out.parent.mkdir(parents=True,exist_ok=True)
out.write_text(json.dumps({'passed':len(checks),'checks':checks,'scope':'Real shell script; simulated corrupt download; zero network. PowerShell checks are static, not Windows execution.'},ensure_ascii=False,indent=2))
