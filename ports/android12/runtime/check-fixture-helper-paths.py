# SPDX-License-Identifier: GPL-2.0
"""Compile the shared Java policy and test it against actual host runner paths."""
from pathlib import Path
import argparse,hashlib,importlib.util,json,subprocess
B=Path(__file__).resolve().parent
p=argparse.ArgumentParser()
for key in ('java','toolchain','output'):p.add_argument('--'+key,required=True,type=Path)
a=p.parse_args();D=a.output.absolute()
if D.resolve()!=D or D.exists():raise SystemExit('fresh-canonical-contract-output-required')
spec=importlib.util.spec_from_file_location('helper_path_runner',B/'check-installation-emulator.py')
runner=importlib.util.module_from_spec(spec);spec.loader.exec_module(runner)
sources=[B.parent/'controller/ModernFixtureHelperPaths.java',B/'FixtureHelperPathsContract.java']
pins={path.name:hashlib.sha256(path.read_bytes()).hexdigest()for path in sources}
D.mkdir(parents=True);classes=D/'classes'
subprocess.run([str(a.java),'-jar',str(a.toolchain/'ecj.jar'),'-encoding','UTF-8','-source','8','-target','8','-proc:none','-d',str(classes),*map(str,sources)],check=True,capture_output=True,timeout=60)
r=subprocess.run([str(a.java),'-cp',str(classes),'FixtureHelperPathsContract',*runner.HELPER_SLOTS.values()],check=True,capture_output=True,text=True,timeout=15)
assert r.stdout.strip()=='33 shared fixture helper path contracts passed'
assert pins=={path.name:hashlib.sha256(path.read_bytes()).hexdigest()for path in sources}
report=dict(schema=1,status='passed',contracts=33,production_java_policy_used=True,host_runner_paths_checked=True,production_aliases_refused=True,device_verified=False,source_sha256=pins)
(D/'contracts.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
print(json.dumps(report))
