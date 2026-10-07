# SPDX-License-Identifier: GPL-2.0
"""Synthetic AKA, privacy and SIP dialog/SDP contracts; no device or live secrets."""
from pathlib import Path
import argparse,json,subprocess
B=Path(__file__).resolve().parent
p=argparse.ArgumentParser(description=__doc__)
for name in ('java','ecj','output'):p.add_argument('--'+name,type=Path,required=True)
a=p.parse_args();out=a.output.absolute()
if out.resolve()!=out or not out.is_relative_to((B/'out').resolve())or out.exists():raise SystemExit('fresh-canonical-stack-output-required')
out.mkdir(parents=True);classes=out/'classes'
sources=[B/'ims'/name for name in ('AkaResponseCodec.java','VoiceResponseObservation.java','VoiceDialogState.java','SdpSessionVersion.java')]
sources += [B/'host-tests'/name for name in ('AkaResponseCodecContract.java','VoiceMetadataContract.java','VoiceDialogContract.java')]
with(out/'compile.log').open('wb')as log:
    subprocess.run([str(a.java),'-jar',str(a.ecj),'-encoding','UTF-8','-source','8','-target','8','-proc:none','-d',str(classes),*map(str,sources)],stdout=log,stderr=subprocess.STDOUT,check=True,timeout=45)
for entry,expected in [('AkaResponseCodecContract','aka-framing-contracts=20 passed'),('VoiceMetadataContract','metadata-contracts=12 passed'),('VoiceDialogContract','dialog-contracts=20 passed')]:
    r=subprocess.run([str(a.java),'-cp',str(classes),entry],capture_output=True,text=True,check=True,timeout=20)
    if r.stdout.strip()!=expected:raise SystemExit('IMS-contract-result-unconfirmed')
value=dict(schema=1,status='passed',aka_framing_contracts=20,metadata_privacy_contracts=12,dialog_and_sdp_contracts=20,live_sim_authentication_attempted=False)
(out/'report.json').write_text(json.dumps(value,indent=2)+'\n',encoding='utf-8');print(json.dumps(value))
