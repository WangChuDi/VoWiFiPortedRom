# SPDX-License-Identifier: GPL-2.0
"""Exercise actual read resolver against invocation and alternate-ABI contracts."""
from pathlib import Path
import subprocess,sys
B=Path(__file__).resolve().parent
sys.path.insert(0,str(B.parent/'android11'))
from build_common import JAVA,TOOLS
out=B/'out/carrier-read-contract-classes'
sources=[B.parent/'android11/stack/CarrierConfigReadCompat.java',B/'tests/CarrierConfigReadContractTest.java']
subprocess.run([JAVA,'-jar',str(TOOLS/'ecj.jar'),'-encoding','UTF-8','-source','8','-target','8','-proc:none','-d',str(out),*map(str,sources)],check=True)
subprocess.run([JAVA,'-cp',str(out),'CarrierConfigReadContractTest'],check=True)
