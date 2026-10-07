#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-2.0
"""Isolated current helper build for an explicitly audited legacy journal."""
from pathlib import Path
import argparse,ast,hashlib,json,os,subprocess,zipfile
p=argparse.ArgumentParser()
for key in ('java','toolchain','output'):p.add_argument('--'+key,required=True,type=Path)
a=p.parse_args();B=Path(__file__).resolve().parents[2];ROOT=B.parent.parent;D=a.output.absolute()
if D.resolve()!=D or D.exists():raise SystemExit('fresh-canonical-build-required')
digest=lambda path:hashlib.sha256(path.read_bytes()).hexdigest()
framework=B.parent/'compatibility/out/frameworks/android-all-12-robolectric-7732740.jar'
if digest(framework)!='687ec0ce42646cb4172c3e1958bfd91783933a66ef049d472b90ab1fdf07541c':raise SystemExit('framework-profile-required')
builder=B/'build-runtime.py'
def source_path(node):
 if isinstance(node,ast.Name)and node.id=='B':return B
 if isinstance(node,ast.Attribute)and node.attr=='parent':return source_path(node.value).parent
 if isinstance(node,ast.Constant)and isinstance(node.value,str):return node.value
 if isinstance(node,ast.BinOp)and isinstance(node.op,ast.Div):return source_path(node.left)/source_path(node.right)
 raise ValueError('fixed-source-expression-required')
sources=[]
for node in ast.parse(builder.read_text()).body:
 if isinstance(node,ast.Assign)and len(node.targets)==1 and isinstance(node.targets[0],ast.Name)and node.targets[0].id=='sources':
  if sources or not isinstance(node.value,ast.List):raise ValueError('single-source-inventory-required')
  sources.extend(source_path(x)for x in node.value.elts)
 elif isinstance(node,ast.AugAssign)and isinstance(node.target,ast.Name)and node.target.id=='sources':
  if not isinstance(node.op,ast.Add)or not isinstance(node.value,ast.List):raise ValueError('fixed-source-inventory-required')
  sources.extend(source_path(x)for x in node.value.elts)
if not sources or len(set(sources))!=len(sources)or any(path.resolve()!=path.absolute()or not path.is_file()or not path.resolve().is_relative_to(ROOT.resolve())for path in sources):raise SystemExit('source-inventory-refused')
names={path.name for path in sources}
if not {'ModernPermissionFlags.java','ModernRolePermissionBroker.java','ModernSelectedPermissions.java','ModernSelectionEmulatorTrial.java','ModernInstallationEmulatorTrial.java'}<=names:raise SystemExit('compatibility-source-coverage-required')
D.mkdir(parents=True);classes=D/'classes';jar=D/'classes.jar';helper=D/'runtime-check.zip'
pins={path.relative_to(ROOT).as_posix():digest(path)for path in sources}
subprocess.run([str(a.java),'-jar',str(a.toolchain/'ecj.jar'),'-encoding','UTF-8','-source','8','-target','8','-proc:none','-classpath',str(framework),'-d',str(classes),*map(str,sources)],check=True,capture_output=True,timeout=60)
with zipfile.ZipFile(jar,'w')as z:
 for path in sorted(classes.rglob('*.class')):z.write(path,path.relative_to(classes).as_posix())
subprocess.run([str(a.java),'-cp',str(a.toolchain/'android-build-tools/d8.jar'),'com.android.tools.r8.D8','--min-api','31','--lib',str(framework),'--output',str(helper),str(jar)],check=True,capture_output=True,timeout=60)
if pins!={path.relative_to(ROOT).as_posix():digest(path)for path in sources}:raise SystemExit('source-changed-during-build')
out=dict(schema=1,status='built',framework_sha256=digest(framework),builder_sha256=digest(builder),source_sha256=pins,classes_sha256=digest(jar),helper_sha256=digest(helper),retained_helper_rebuilt=False,device_validated=False,legacy_recovery_verified=False)
(D/'build.json').write_text(json.dumps(out,indent=2)+'\n');print(json.dumps({k:v for k,v in out.items()if k!='source_sha256'}))
