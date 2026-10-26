#!/usr/bin/env python3
from __future__ import annotations
import argparse, os, stat, struct, zlib
from datetime import datetime
from pathlib import Path

MARKER=b'Rar!\x1a\x07\x00'

def hcrc(data: bytes) -> int:
    return zlib.crc32(data) & 0xffff

def dos_time(ts: float) -> int:
    dt=datetime.fromtimestamp(ts)
    y=max(1980,min(2107,dt.year))
    return ((y-1980)<<25)|(dt.month<<21)|(dt.day<<16)|(dt.hour<<11)|(dt.minute<<5)|(dt.second//2)

def main_header() -> bytes:
    body=struct.pack('<BHHHI',0x73,0,13,0,0)
    return struct.pack('<H',hcrc(body))+body

def file_header(name: str, data: bytes, st: os.stat_result) -> bytes:
    nb=name.replace(os.sep,'/').encode('utf-8')
    flags=0x8000
    head_size=32+len(nb)
    mode=stat.S_IFREG | (st.st_mode & 0o777)
    fixed=struct.pack('<BHHIIBIIBBHI',0x74,flags,head_size,len(data),len(data),3,zlib.crc32(data)&0xffffffff,dos_time(st.st_mtime),20,0x30,len(nb),mode)
    body=fixed+nb
    return struct.pack('<H',hcrc(body))+body

def end_header() -> bytes:
    body=struct.pack('<BHH',0x7b,0,7)
    return struct.pack('<H',hcrc(body))+body

def create_rar(source: Path, output: Path, root_name: str|None=None):
    source=source.resolve(); output=output.resolve(); root_name=root_name or source.name
    files=[p for p in source.rglob('*') if p.is_file()]
    files.sort(key=lambda p:p.relative_to(source).as_posix().lower())
    output.parent.mkdir(parents=True,exist_ok=True)
    with output.open('wb') as f:
        f.write(MARKER); f.write(main_header())
        for p in files:
            rel=p.relative_to(source).as_posix()
            name=f'{root_name}/{rel}' if root_name else rel
            data=p.read_bytes(); f.write(file_header(name,data,p.stat())); f.write(data)
        f.write(end_header())
    return len(files)

def cli():
    ap=argparse.ArgumentParser(description='Create a valid RAR4 store-only archive without external rar binaries.')
    ap.add_argument('source'); ap.add_argument('output'); ap.add_argument('--root-name')
    a=ap.parse_args(); n=create_rar(Path(a.source),Path(a.output),a.root_name); print(f'RAR created: {a.output} ({n} files)')
if __name__=='__main__': cli()
