from pathlib import Path
import zipfile, hashlib
B=Path(__file__).resolve().parent
out=B/'voxi-wifi-sms-android11-trial.zip'
with zipfile.ZipFile(out,'w',zipfile.ZIP_DEFLATED) as z:
    for p in sorted((B/'module').iterdir()):
        if p.is_file():
            data=p.read_bytes()
            if b'\r' in data:raise ValueError('CRLF module source: '+p.name)
            z.writestr(p.name,data)
    z.write(B/'ims-api30-core-probe.zip','receiver.zip')
    z.write(B/'libcodex-ims-nested.so','libcodex-ims-nested.so')
print(out)
print('sha256='+hashlib.sha256(out.read_bytes()).hexdigest())
