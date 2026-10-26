#!/usr/bin/env python3
"""Create a genuine uncompressed RAR5 archive. No proprietary compressor needed.
Format reference: https://www.rarlab.com/technote.htm
Usage: python create_rar5.py SOURCE_DIR OUTPUT.rar [ROOT_NAME]
Reject symlinks/special files. Root directory and Unix executable mode preserved.
"""
import pathlib,struct,zlib,os,sys,stat,hashlib,json

def vint(n):
 if not isinstance(n,int) or n<0 or n>=1<<64:raise ValueError(n)
 b=bytearray()
 while n>=128:b.append((n&127)|128);n>>=7
 b.append(n);return bytes(b)
def block(payload):
 data=vint(len(payload))+payload
 return struct.pack('<I',zlib.crc32(data))+data

def create(source,output,root_name=None):
 source=pathlib.Path(source).resolve();output=pathlib.Path(output).resolve()
 if not source.is_dir():raise ValueError('source is not a directory')
 if output.is_relative_to(source):raise ValueError('output must be outside source')
 root_name=root_name or source.name
 p=pathlib.PurePosixPath(root_name)
 if p.is_absolute() or '..' in p.parts or len(p.parts)!=1 or '\\' in root_name or ':' in root_name:raise ValueError('invalid root name')
 paths=[source]+sorted(source.rglob('*'));entries=[]
 output.parent.mkdir(parents=True,exist_ok=True)
 with output.open('xb') as archive:
  archive.write(b'Rar!\x1a\x07\x01\x00');archive.write(block(vint(1)+vint(0)+vint(0)))
  for path in paths:
   s=path.lstat()
   if not (stat.S_ISREG(s.st_mode) or stat.S_ISDIR(s.st_mode)):raise ValueError('special file '+str(path))
   name=(root_name if path==source else root_name+'/'+path.relative_to(source).as_posix()).encode('utf-8')
   directory=stat.S_ISDIR(s.st_mode);size=0 if directory else s.st_size
   crc=0
   if not directory:
    with path.open('rb') as inp:
     for chunk in iter(lambda:inp.read(1024*1024),b''):crc=zlib.crc32(chunk,crc)
   flags=2|4|(1 if directory else 0)
   mode=(stat.S_IFDIR|0o755) if directory else (stat.S_IFREG|(0o755 if s.st_mode&0o111 else 0o644))
   payload=vint(2)+vint(2)+vint(size)+vint(flags)+vint(size)+vint(mode)+struct.pack('<II',max(0,min(0xffffffff,int(s.st_mtime))),crc)+vint(0)+vint(1)+vint(len(name))+name
   archive.write(block(payload))
   copied=0;actual_crc=0
   if not directory:
    with path.open('rb') as inp:
     for chunk in iter(lambda:inp.read(1024*1024),b''):
      copied+=len(chunk);actual_crc=zlib.crc32(chunk,actual_crc);archive.write(chunk)
    if copied!=size or actual_crc!=crc:raise RuntimeError('source changed while packaging '+str(path))
   entries.append({'name':name.decode(),'bytes':size,'directory':directory})
  archive.write(block(vint(5)+vint(0)+vint(0)))
 return {'path':str(output),'bytes':output.stat().st_size,'files':sum(not x['directory'] for x in entries),'directories':sum(x['directory'] for x in entries),'sha256':hashlib.sha256(output.read_bytes()).hexdigest(),'format':'RAR 5, stored / no compression'}
if __name__=='__main__':print(json.dumps(create(*sys.argv[1:]),indent=2))
