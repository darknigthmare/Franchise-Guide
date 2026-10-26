#!/usr/bin/env python3
import ctypes as C, ctypes.util, pathlib, stat, sys, json
lib=C.CDLL(ctypes.util.find_library('archive'))
def bind(name,args,ret):
 f=getattr(lib,name);f.argtypes=args;f.restype=ret;return f
new=bind('archive_read_new',[],C.c_void_p)
support_filter=bind('archive_read_support_filter_all',[C.c_void_p],C.c_int)
support_format=bind('archive_read_support_format_all',[C.c_void_p],C.c_int)
open_file=bind('archive_read_open_filename',[C.c_void_p,C.c_char_p,C.c_size_t],C.c_int)
next_header=bind('archive_read_next_header',[C.c_void_p,C.POINTER(C.c_void_p)],C.c_int)
pathname=bind('archive_entry_pathname',[C.c_void_p],C.c_char_p)
size=bind('archive_entry_size',[C.c_void_p],C.c_longlong)
filetype=bind('archive_entry_filetype',[C.c_void_p],C.c_uint)
permissions=bind('archive_entry_perm',[C.c_void_p],C.c_uint)
hardlink=bind('archive_entry_hardlink',[C.c_void_p],C.c_char_p)
read_data=bind('archive_read_data',[C.c_void_p,C.c_void_p,C.c_size_t],C.c_longlong)
error=bind('archive_error_string',[C.c_void_p],C.c_char_p)
free=bind('archive_read_free',[C.c_void_p],C.c_int)
a=new();support_filter(a);support_format(a)
def fail(msg):
 detail=error(a);raise RuntimeError(msg+(': '+detail.decode(errors='replace') if detail else ''))
if open_file(a,sys.argv[1].encode(),10240)!=0:fail('open')
root=pathlib.Path(sys.argv[2]).resolve() if len(sys.argv)>2 else None
if root:root.mkdir(parents=True,exist_ok=True)
entries=[];total=0;buf=C.create_string_buffer(65536);seen=set()
try:
 while True:
  ent=C.c_void_p();status=next_header(a,C.byref(ent))
  if status==1:break
  if status<0:fail('header')
  name=pathname(ent).decode('utf-8','strict');p=pathlib.PurePosixPath(name.replace('\\','/'));sz=size(ent);typ=filetype(ent)
  if p.is_absolute() or '..' in p.parts or not p.parts or ':' in p.parts[0]:raise RuntimeError('unsafe path '+name)
  if hardlink(ent) or typ not in (stat.S_IFREG,stat.S_IFDIR):raise RuntimeError('unsupported link/special '+name)
  if str(p) in seen:raise RuntimeError('duplicate '+name)
  seen.add(str(p));total+=max(sz,0)
  if sz>536870912 or total>2147483648:raise RuntimeError('size limit')
  if root:
   dest=root.joinpath(*p.parts)
   if not dest.resolve().is_relative_to(root):raise RuntimeError('escaped root')
   if typ==stat.S_IFDIR:dest.mkdir(parents=True,exist_ok=True)
   else:
    dest.parent.mkdir(parents=True,exist_ok=True);read=0
    with dest.open('xb') as out:
     while True:
      n=read_data(a,buf,len(buf))
      if n<0:fail('data')
      if n==0:break
      out.write(buf.raw[:n]);read+=n
      if read>536870912:raise RuntimeError('stream size limit')
    if read!=sz:raise RuntimeError(f'size mismatch {name}: {read} != {sz}')
    dest.chmod(0o755 if permissions(ent)&0o111 else 0o644)
  entries.append({'name':name,'size':sz,'directory':typ==stat.S_IFDIR})
finally:free(a)
print(json.dumps({'entries':len(entries),'bytes':total,'top_level':sorted(set(pathlib.PurePosixPath(x['name']).parts[0] for x in entries)),'files':entries if not root else None,'extracted_to':str(root) if root else None},ensure_ascii=False,indent=2))
