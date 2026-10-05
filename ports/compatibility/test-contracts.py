"""Run Android-independent compatibility/gate contract tests with the existing compiler."""
from pathlib import Path
import subprocess,sys
B=Path(__file__).resolve().parent
sys.path.insert(0,str(B.parent/'android11'))
from build_common import JAVA,TOOLS
out=B/'out/contract-classes'
sources=[B.parent/'android11/stack/iwlan/IkeApiCompat.java',B.parent/'android11/stack/common/ProfileGate.java',B/'tests/CompatibilityContractTest.java']
subprocess.run([JAVA,'-jar',str(TOOLS/'ecj.jar'),'-encoding','UTF-8','-source','8','-target','8','-proc:none','-d',str(out),*map(str,sources)],check=True)
subprocess.run([JAVA,'-cp',str(out),'dev.codex.vowifi.iwlan.CompatibilityContractTest'],check=True)
