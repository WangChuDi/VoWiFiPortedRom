# SPDX-License-Identifier: GPL-2.0
"""Run production lookup against controlled absence, aliases, access and linkage."""
from pathlib import Path
import subprocess, sys
B = Path(__file__).resolve().parent
sys.path.insert(0, str(B.parent / 'android11'))
from build_common import JAVA, TOOLS
out = B / 'out/runtime-abi-contract-classes'
sources = [B.parent / 'diagnostic-app/RuntimeAbiProbe.java', B / 'tests/RuntimeAbiProbeContractTest.java']
subprocess.run([JAVA, '-jar', str(TOOLS / 'ecj.jar'), '-encoding', 'UTF-8', '-source', '8', '-target', '8', '-proc:none', '-d', str(out), *map(str, sources)], check=True)
subprocess.run([JAVA, '-cp', str(out), 'dev.codex.vowifi.tool.RuntimeAbiProbeContractTest'], check=True)
