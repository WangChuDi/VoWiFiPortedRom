"""Compile/run filesystem isolation scenarios with the existing Java toolchain."""
from pathlib import Path
import subprocess,sys,zipfile
B=Path(__file__).resolve().parent
sys.path.insert(0,str(B.parent/'android11'))
from build_common import JAVA,TOOLS
out=B/'out/override-store-classes';stack=B.parent/'android11/stack'
sources=[stack/'OverrideFileStore.java',stack/'CarrierOverrideFiles.java',B/'tests/OverrideFileStoreTest.java']
subprocess.run([JAVA,'-jar',str(TOOLS/'ecj.jar'),'-encoding','UTF-8','-source','8','-target','8','-proc:none',
    '-classpath',str(TOOLS/'android-all-11.jar'),'-d',str(out),*map(str,sources)],check=True)
subprocess.run([JAVA,'-cp',str(out),'OverrideFileStoreTest'],check=True)
if '--device-dex' in sys.argv:
    jar=B/'out/override-store-tests.jar'
    with zipfile.ZipFile(jar,'w')as archive:
        for path in sorted(out.rglob('*.class')):archive.write(path,path.relative_to(out).as_posix())
    subprocess.run([JAVA,'-cp',str(TOOLS/'android-build-tools/d8.jar'),'com.android.tools.r8.D8',
        '--min-api','30','--lib',str(TOOLS/'android-all-11.jar'),'--output',str(B/'out/override-store-tests.zip'),str(jar)],check=True)
