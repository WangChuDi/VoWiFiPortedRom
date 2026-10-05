"""Portable tool locations; downloaded dependencies and output stay untracked."""
from pathlib import Path
import os,shutil
BASE=Path(__file__).resolve().parent
TOOLS=Path(os.environ.get('IMS_PORT_TOOLCHAIN',str(BASE/'toolchain'))).resolve()
JAVA=os.environ.get('JAVA')
if not JAVA and os.environ.get('JAVA_HOME'):
    JAVA=str(Path(os.environ['JAVA_HOME'])/'bin'/('java.exe' if os.name=='nt' else 'java'))
JAVA=JAVA or shutil.which('java')
if not JAVA:raise RuntimeError('Set JAVA or JAVA_HOME to a Java 17 runtime')
