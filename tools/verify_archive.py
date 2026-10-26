#!/usr/bin/env python3
"""Read all archive bytes with libarchive, then compare paths, data and executable flags.
Usage: python verify_archive.py ARCHIVE SOURCE_DIR
"""
from pathlib import Path
import tempfile,subprocess,sys,json,hashlib
archive=Path(sys.argv[1]).resolve();source=Path(sys.argv[2]).resolve()
extractor=Path(__file__).with_name('extract_rar.py')
def index(root):
 out={}
 for p in sorted(root.rglob('*')):
  if p.is_symlink():raise ValueError('symlink '+str(p))
  if p.is_file():out[p.relative_to(root).as_posix()]={'bytes':p.stat().st_size,'sha256':hashlib.sha256(p.read_bytes()).hexdigest(),'executable':bool(p.stat().st_mode&0o111)}
 return out
with tempfile.TemporaryDirectory(prefix='fg-archive-verify-') as tmp:
 r=subprocess.run([sys.executable,str(extractor),str(archive),tmp],check=True,text=True,capture_output=True)
 meta=json.loads(r.stdout)
 if len(meta['top_level'])!=1:raise ValueError('expected one archive root')
 expected=index(source);actual=index(Path(tmp)/meta['top_level'][0]);problems=[]
 for name in sorted(set(expected)|set(actual)):
  if name not in expected:problems.append('unexpected '+name)
  elif name not in actual:problems.append('missing '+name)
  elif expected[name]!=actual[name]:problems.append('content or executable flag differs '+name)
 report={'archive':str(archive),'source':str(source),'sha256':hashlib.sha256(archive.read_bytes()).hexdigest(),'archive_bytes':archive.stat().st_size,'files':len(actual),'all_file_paths_bytes_and_executable_flags_match':not problems,'problems':problems}
 print(json.dumps(report,ensure_ascii=False,indent=2))
 if problems:sys.exit(1)
