"""Compare compiled app references against pinned framework class files.

This is linkage evidence, not a permission, Binder, radio, network or device test.
"""
from pathlib import Path
import argparse, hashlib, json, runpy, zipfile
B=Path(__file__).resolve().parent
decode=runpy.run_path(str(B/'read-framework-abi.py'))['decode_class']
# android-all excludes core java.* classes. These two inherited calls have
# Android public contracts since API1; list them explicitly, not a java.* wildcard.
# https://developer.android.com/reference/java/lang/Object#hashCode()
# https://developer.android.com/reference/java/lang/Thread#start()
CORE_METHODS={'java.lang.Object':{('hashCode','()I')},'java.lang.Thread':{('start','()V')}}

def references(directories):
    result={}
    for directory in directories:
        for path in sorted(directory.rglob('*.class')):
            for reference in decode(path.read_bytes())['references']:
                if reference['owner'].startswith(('android.','com.android.internal.')):
                    key=tuple(reference[field] for field in ('kind','owner','name','descriptor'))
                    result.setdefault(key,[]).append(str(path.relative_to(directory)))
    return result

def check(jar, calls):
    with jar.open('rb') as stream: digest=hashlib.file_digest(stream,'sha256').hexdigest()
    cache={}
    with zipfile.ZipFile(jar) as archive:
        def load(name):
            if name not in cache:
                try: cache[name]=decode(archive.read(name.replace('.','/')+'.class'))
                except KeyError: cache[name]=None
            return cache[name]
        def has(name,kind,member,descriptor,seen):
            if name in seen:return False
            seen.add(name)
            info=load(name)
            if info is None:return kind=='method' and (member,descriptor) in CORE_METHODS.get(name,set())
            if any(value['name']==member and value['descriptor']==descriptor for value in info[kind+'s']):return True
            if member in ('<init>','<clinit>'):return False
            return any(has(parent,kind,member,descriptor,seen) for parent in [info['super'],*info['interfaces']] if parent)
        missing=[]
        for (kind,owner,name,descriptor),sources in sorted(calls.items()):
            if not has(owner,kind,name,descriptor,set()):
                missing.append({'kind':kind,'owner':owner,'name':name,'descriptor':descriptor,'used_by':sources})
    return {'artifact':jar.name,'sha256':digest,'reference_count':len(calls),'missing':missing}

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--classes',nargs='+',type=Path,required=True)
    parser.add_argument('--frameworks',nargs='+',type=Path,required=True)
    parser.add_argument('--output',type=Path,default=B/'out/linkage.json')
    args=parser.parse_args()
    for directory in args.classes:
        if not directory.is_dir() or not any(directory.rglob('*.class')):raise SystemExit('Missing compiled directory: '+str(directory))
    calls=references(args.classes)
    records=[check(jar,calls) for jar in args.frameworks]
    args.output.parent.mkdir(parents=True,exist_ok=True)
    args.output.write_text(json.dumps(records,indent=2)+'\n',encoding='utf-8')
    for result in records:print(json.dumps({key:result[key] for key in ('artifact','reference_count','missing')}))
    raise SystemExit(1 if any(result['missing'] for result in records) else 0)
