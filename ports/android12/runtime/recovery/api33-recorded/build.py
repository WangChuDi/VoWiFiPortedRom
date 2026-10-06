# SPDX-License-Identifier: GPL-2.0
"""Build the emulator-only recovery helper without rebuilding production artifacts."""
from pathlib import Path
import argparse,hashlib,os,subprocess,zipfile
HERE=Path(__file__).resolve().parent;R=HERE.parents[4]
p=argparse.ArgumentParser();p.add_argument('--toolchain',type=Path,required=True);p.add_argument('--java',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args()
D=a.output.resolve();assert D.is_relative_to((R/'ports/android12/out').resolve())and not D.exists();D.mkdir(parents=True)
F=R/'ports/compatibility/out/frameworks/android-all-12-robolectric-7732740.jar';runtime=R/'ports/android12/out/runtime'
assert hashlib.sha256(F.read_bytes()).hexdigest()=='687ec0ce42646cb4172c3e1958bfd91783933a66ef049d472b90ab1fdf07541c'
assert hashlib.sha256((runtime/'runtime-check.zip').read_bytes()).hexdigest()=='5c5748cdd75377a34a05001c22bb3d5e7a9375de060a265b0c3bbb51202b44c4'
sources=[HERE/name for name in ('Api33RecordedRecoveryAudit.java','Api33LegacyRecoveryEntry.java','Api33CarrierFinalizeOriginal.java','Api33SavedCarrierBundleObservation.java')];C=D/'classes'
subprocess.run([str(a.java),'-jar',str(a.toolchain/'ecj.jar'),'-source','8','-target','8','-proc:none','-classpath',os.pathsep.join(map(str,[F,runtime/'classes'])),'-d',str(C),*map(str,sources)],check=True)
with zipfile.ZipFile(D/'classes.jar','w')as archive:
    for value in C.rglob('*.class'):archive.write(value,value.relative_to(C).as_posix())
subprocess.run([str(a.java),'-cp',str(a.toolchain/'android-build-tools/d8.jar'),'com.android.tools.r8.D8','--min-api','31','--lib',str(F),'--output',str(D/'guard.zip'),str(D/'classes.jar')],check=True)
