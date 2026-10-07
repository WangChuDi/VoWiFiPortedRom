#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-2.0
"""Build only the read-only probe; retained runtime helper is not shadowed or rebuilt."""
from pathlib import Path
import argparse,hashlib,json,os,subprocess,zipfile
B=Path(__file__).resolve().parent
p=argparse.ArgumentParser();p.add_argument('--java',required=True,type=Path);p.add_argument('--toolchain',required=True,type=Path);p.add_argument('--runtime-classes',required=True,type=Path);p.add_argument('--framework',required=True,type=Path);p.add_argument('--output',required=True,type=Path);a=p.parse_args()
D=a.output.absolute();assert D.resolve()==D and not D.exists();D.mkdir(parents=True)
digest=lambda path:hashlib.sha256(path.read_bytes()).hexdigest()
assert digest(a.framework)=='687ec0ce42646cb4172c3e1958bfd91783933a66ef049d472b90ab1fdf07541c'
source=B/'Api37SettingsReadOnlyProbe.java';settings=B.parent.parent/'controller/ModernRootSettings.java';classes=D/'classes'
subprocess.run([str(a.java),'-jar',str(a.toolchain/'ecj.jar'),'-encoding','UTF-8','-source','8','-target','8','-proc:none','-classpath',os.pathsep.join(map(str,[a.framework,a.runtime_classes])),'-d',str(classes),str(source),str(settings)],check=True)
jar=D/'probe-classes.jar'
with zipfile.ZipFile(jar,'w')as z:
 for path in sorted(classes.glob('Api37SettingsReadOnlyProbe*.class')):z.write(path,path.name)
 assert z.namelist() and all(name.startswith('Api37SettingsReadOnlyProbe')for name in z.namelist())
probe=D/'probe.zip';subprocess.run([str(a.java),'-cp',str(a.toolchain/'android-build-tools/d8.jar'),'com.android.tools.r8.D8','--min-api','31','--lib',str(a.framework),'--output',str(probe),str(jar)],check=True)
out=dict(schema=1,status='built',source_sha256=digest(source),settings_compile_source_sha256=digest(settings),compile_classes_sha256=digest(a.runtime_classes),probe_sha256=digest(probe),retained_runtime_helper_sha256='ea54aa8d54f3b8822312da08d33803880d6e92a1fc24124970af31114bf49b7d',helper_definition_packaged=False,device_validated=False)
(D/'build.json').write_bytes((json.dumps(out,indent=2)+'\n').encode('utf-8'));print(json.dumps(out),flush=True)
