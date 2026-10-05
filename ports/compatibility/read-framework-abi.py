"""Read class-file contracts without loading/executing Android framework code."""
from pathlib import Path
import argparse, hashlib, json, zipfile

TYPES = [
    'android.net.IpSecManager', 'android.net.IpSecManager$IpSecTunnelInterface',
    'android.net.ipsec.ike.IkeSession', 'android.net.ipsec.ike.IkeSessionParams$Builder',
    'android.net.ipsec.ike.TunnelModeChildSessionParams$Builder',
    'android.net.ipsec.ike.IkeSessionCallback', 'android.net.ipsec.ike.ChildSessionCallback',
    'android.net.eap.EapSessionConfig$Builder', 'android.telephony.data.DataProfile',
    'android.telephony.data.DataService$DataServiceProvider',
    'android.telephony.ims.ImsService',
    'android.telephony.ims.feature.ImsFeature',
    'android.telephony.CarrierConfigManager',
    'com.android.internal.telephony.ICarrierConfigLoader',
    'com.android.internal.telephony.ICarrierConfigLoader$Stub',
]

def decode_class(data):
    position = 0
    def read(size):
        nonlocal position
        value = data[position:position+size]
        if len(value) != size: raise ValueError('truncated-class')
        position += size
        return value
    def integer(size): return int.from_bytes(read(size), 'big')
    if read(4) != b'\xca\xfe\xba\xbe': raise ValueError('not-class-file')
    minor, major = integer(2), integer(2)
    pool = [None] * integer(2)
    index = 1
    while index < len(pool):
        tag = integer(1)
        if tag == 1: pool[index] = read(integer(2)).decode('utf-8', errors='replace')
        elif tag in (3, 4): read(4)
        elif tag in (5, 6): read(8); index += 1
        elif tag == 7: pool[index] = ('class', integer(2))
        elif tag in (9, 10, 11): pool[index] = ('reference', tag, integer(2), integer(2))
        elif tag == 12: pool[index] = ('name_type', integer(2), integer(2))
        elif tag in (8, 16, 19, 20): read(2)
        elif tag in (17, 18): read(4)
        elif tag == 15: read(3)
        else: raise ValueError(f'unknown-constant-pool-tag:{tag}')
        index += 1
    def attributes():
        for _ in range(integer(2)):
            integer(2); read(integer(4))
    def class_name(index): return pool[pool[index][1]].replace('/', '.') if index else None
    flags = integer(2)
    class_name(integer(2)); parent = class_name(integer(2))
    interfaces = [class_name(integer(2)) for _ in range(integer(2))]
    fields, methods = [], []
    for items in (fields, methods):
        for _ in range(integer(2)):
            access, name, descriptor = integer(2), integer(2), integer(2)
            items.append({'name': pool[name], 'descriptor': pool[descriptor], 'access': access})
            attributes()
    attributes()
    if position != len(data): raise ValueError('trailing-class-bytes')
    references = []
    for value in pool:
        if isinstance(value, tuple) and value[0] == 'reference':
            _, kind, owner, name_type = value
            _, name, descriptor = pool[name_type]
            references.append({'kind': 'field' if kind == 9 else 'method', 'owner': class_name(owner), 'name': pool[name], 'descriptor': pool[descriptor]})
    return {'major': major, 'minor': minor, 'access': flags, 'super': parent, 'interfaces': interfaces,
            'fields': fields, 'methods': methods, 'references': references}

def inventory(jar):
    with jar.open('rb') as stream: digest = hashlib.file_digest(stream, 'sha256').hexdigest()
    result = {'artifact': jar.name, 'sha256': digest, 'classes': {}}
    with zipfile.ZipFile(jar) as archive:
        for name in TYPES:
            path = name.replace('.', '/')+'.class'
            try: result['classes'][name] = decode_class(archive.read(path))
            except KeyError: result['classes'][name] = {'missing': True}
    return result

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('jars', nargs='+', type=Path)
    parser.add_argument('--output', type=Path, default=Path(__file__).resolve().parent/'out/abi.json')
    args = parser.parse_args()
    records = [inventory(jar) for jar in args.jars]
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(records, indent=2)+'\n', encoding='utf-8')
    for result in records:
        print(json.dumps({'artifact': result['artifact'], 'missing_classes': [name for name, value in result['classes'].items() if value.get('missing')]}))
