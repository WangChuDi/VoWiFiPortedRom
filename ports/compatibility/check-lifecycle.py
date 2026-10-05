"""Check Android superclass contracts without executing framework code.

Checks nearest class declarations for abstract obligations, final inheritance,
and static/instance collisions. Interface defaults/obligations, core Java and
reflection remain outside this deliberately bounded check, as do device rules.
"""
from pathlib import Path
import argparse,hashlib,json,runpy,zipfile
B=Path(__file__).resolve().parent
decode=runpy.run_path(str(B/'read-framework-abi.py'))['decode_class']
PUBLIC,PRIVATE,PROTECTED,STATIC,FINAL,ABSTRACT,INTERFACE=1,2,4,8,16,1024,512

def framework(name):return name.startswith(('android.','com.android.internal.'))
def package(name):return name.rpartition('.')[0]
def can_override(method,owner,child):
    # Package access is also an overriding relation when a leaf returns to the
    # declaring package. It is not equivalent to member inheritance at each hop.
    return not method['access']&PRIVATE and (
        bool(method['access']&(PUBLIC|PROTECTED)) or package(owner)==package(child))
def signature(method):return method['name'],method['descriptor']

def app_classes(directories):
    result={}
    for directory in directories:
        if not directory.is_dir():raise ValueError('missing-compiled-directory:'+str(directory))
        for path in sorted(directory.rglob('*.class')):
            name=path.relative_to(directory).with_suffix('').as_posix().replace('/','.')
            value=decode(path.read_bytes())
            if name in result and result[name]!=value:raise ValueError('conflicting-class:'+name)
            result[name]=value
    if not result:raise ValueError('no-compiled-classes')
    return result

def analyze(apps,load):
    findings=[];unresolved=set();boundaries=set();examined=[]
    def chain(leaf):
        result=[];name=leaf;seen=set()
        while name:
            if name in seen:raise ValueError('cyclic-superclass:'+name)
            seen.add(name)
            info=apps.get(name) or load(name)
            if info is None:
                (unresolved if framework(name) else boundaries).add(name)
                break
            result.append((name,info));name=info['super']
        return result
    for leaf,info in sorted(apps.items()):
        if info['access']&INTERFACE:continue
        ancestry=chain(leaf)
        if not any(framework(name) for name,_ in ancestry):continue
        examined.append(leaf)
        if len(ancestry)>1 and ancestry[1][1]['access']&FINAL:
            findings.append({'class':leaf,'problem':'final-superclass','owner':ancestry[1][0]})
        if not info['access']&ABSTRACT:
            # Even a package-access abstract declaration that the leaf cannot
            # inherit remains an obligation (JLS 8.1.1.1). Follow actual override
            # declarations downwards; a public bridge in its original package
            # permits a later override outside that package.
            missing={}
            for index,(owner,parent) in enumerate(ancestry):
                for method in parent['methods']:
                    if not method['access']&ABSTRACT or method['access']&(PRIVATE|STATIC):continue
                    target_owner,target=owner,method
                    for child,child_info in reversed(ancestry[:index]):
                        replacement=next((m for m in child_info['methods'] if signature(m)==signature(target)
                            and not m['access']&(PRIVATE|STATIC)),None)
                        if replacement is not None and can_override(target,target_owner,child):
                            target_owner,target=child,replacement
                    if target['access']&ABSTRACT:
                        missing.setdefault(signature(target),(target_owner,target))
            for (name,descriptor),(owner,_) in sorted(missing.items()):
                findings.append({'class':leaf,'problem':'unimplemented-superclass-abstract',
                                 'owner':owner,'name':name,'descriptor':descriptor})
        # Check app declarations against every inherited framework declaration;
        # this also detects newly-final methods on a distant Android ancestor.
        for method in info['methods']:
            if method['name'].startswith('<') or method['access']&PRIVATE:continue
            for owner,parent in ancestry[1:]:
                if not framework(owner):continue
                for previous in parent['methods']:
                    if signature(previous)!=signature(method) or not can_override(previous,owner,leaf):continue
                    issue=None
                    if previous['access']&FINAL:issue='final-method-redeclared'
                    elif bool(previous['access']&STATIC)!=bool(method['access']&STATIC):issue='static-instance-collision'
                    elif previous['access']&PUBLIC and not method['access']&PUBLIC:issue='public-method-access-reduced'
                    elif previous['access']&PROTECTED and not method['access']&(PUBLIC|PROTECTED):issue='protected-method-access-reduced'
                    if issue:findings.append({'class':leaf,'problem':issue,'owner':owner,
                                             'name':method['name'],'descriptor':method['descriptor']})
    return {'examined_count':len(examined),'examined_classes':examined,'findings':findings,
            'unresolved_framework_ancestors':sorted(unresolved),'external_superclass_boundaries':sorted(boundaries),
            'coverage':'Android superclass declarations only; interfaces/core Java/reflection/runtime excluded'}

def check(jar,apps):
    with jar.open('rb') as stream:digest=hashlib.file_digest(stream,'sha256').hexdigest()
    with zipfile.ZipFile(jar)as archive:
        cache={}
        def load(name):
            if name not in cache:
                try:cache[name]=decode(archive.read(name.replace('.','/')+'.class'))
                except KeyError:cache[name]=None
            return cache[name]
        result=analyze(apps,load)
    return {'artifact':jar.name,'sha256':digest,**result}

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--classes',nargs='+',type=Path,required=True)
    parser.add_argument('--frameworks',nargs='+',type=Path,required=True)
    parser.add_argument('--output',type=Path,default=B/'out/lifecycle.json')
    args=parser.parse_args()
    apps=app_classes(args.classes)
    records=[check(jar,apps)for jar in args.frameworks]
    args.output.parent.mkdir(parents=True,exist_ok=True)
    args.output.write_text(json.dumps(records,indent=2)+'\n',encoding='utf-8')
    for result in records:print(json.dumps({key:result[key]for key in ('artifact','examined_count','findings','unresolved_framework_ancestors')}))
    raise SystemExit(1 if any(r['findings']or r['unresolved_framework_ancestors']for r in records)else 0)
