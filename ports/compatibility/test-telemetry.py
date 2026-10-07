"""Run the production registry and snapshot parser, without Android Binder/device code."""
from pathlib import Path
import os,subprocess,sys
BASE=Path(__file__).resolve().parent
sys.path.insert(0,str(BASE.parent/'android11'))
from build_common import JAVA,TOOLS
out=BASE/'out/telemetry-contract-classes'
sources=[BASE.parent/'android11/stack/common/StackTelemetry.java',BASE.parent/'android11/stack/common/SmsSendObservation.java',BASE.parent/'android11/stack/common/SipReconnectGate.java',BASE.parent/'diagnostic-app/TelemetrySnapshot.java',BASE.parent/'diagnostic-app/SipTransportObservation.java',BASE.parent/'diagnostic-app/SmsSendStatus.java',BASE/'tests/TelemetryContractTest.java',BASE/'tests/SipReconnectContractTest.java',BASE/'tests/SmsSendObservationTest.java']
android=TOOLS/'android-all-11.jar'
subprocess.run([JAVA,'-jar',str(TOOLS/'ecj.jar'),'-encoding','UTF-8','-source','8','-target','8','-proc:none','-classpath',str(android),'-d',str(out),*map(str,sources)],check=True)
subprocess.run([JAVA,'-cp',os.pathsep.join(map(str,[out,android])),'dev.codex.vowifi.tool.TelemetryContractTest'],check=True)
subprocess.run([JAVA,'-cp',os.pathsep.join(map(str,[out,android])),'dev.codex.vowifi.tool.SipReconnectContractTest'],check=True)
subprocess.run([JAVA,'-cp',os.pathsep.join(map(str,[out,android])),'dev.codex.vowifi.tool.SmsSendObservationTest'],check=True)
