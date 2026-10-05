"""Fetch pinned AOSP framework test artifacts; never installs on a device."""
from pathlib import Path
import argparse, hashlib, json, time, urllib.request, urllib.error

VERSIONS = {
    '12': '12-robolectric-7732740',
    '12L': '12.1-robolectric-8229987',
    '13': '13-robolectric-9030017',
    '14': '14-robolectric-10818077',
    '15': '15-robolectric-13954326',
    '16': '16-robolectric-13921718',
    '17': '17-robolectric-15733970',
}
PINNED_SHA256={
    '12':'687ec0ce42646cb4172c3e1958bfd91783933a66ef049d472b90ab1fdf07541c',
    '12L':'75e7ac8ad3817d4cdfa24c8479ea8244b752575b03122d79d1e5a4f6e4f33879',
    '13':'6e67dca81d30295e31ee6a28cfb601b6b66aaeaee51a92386ed099a64cea5996',
    '14':'6be2218c6a53fe3c57bc22ebdc723edcb7270a8a6f187545708aa5c0ed813977',
    '15':'e17105f6c432357e6bb278a527f8e848d01fc56ccee01be77725bea23d871990',
    '16':'8b74a0a137330658d2f33f0dc715d42734f74ba8b2d7014fc2e95aa40d3f682d',
    '17':'f6a41ad548bb45cccd3b1d4774cb50d57826dd319b6e5accd6b6269876e12d71',
}
BASE = 'https://repo.maven.apache.org/maven2/org/robolectric/android-all/'

def fetch(version, out):
    artifact = VERSIONS[version]
    name = f'android-all-{artifact}.jar'
    url = BASE + artifact + '/' + name
    expected = None
    for algorithm in ('sha512', 'sha256', 'sha1'):
        try:
            expected = urllib.request.urlopen(url+'.'+algorithm, timeout=20).read().decode('ascii').split()[0].lower()
            if len(expected) != hashlib.new(algorithm).digest_size*2 or any(c not in '0123456789abcdef' for c in expected):
                raise ValueError('invalid-registry-checksum')
            break
        except urllib.error.HTTPError as error:
            if error.code != 404: raise
    if expected is None: raise RuntimeError('registry-checksum-unavailable')
    target = out / name
    if not target.exists():
        temporary = target.with_suffix('.jar.partial')
        try:
            for attempt in range(3):
                try:
                    request = urllib.request.Request(url, headers={'User-Agent': 'VoWiFiPortedRom-ABI-check/1.0'})
                    with urllib.request.urlopen(request, timeout=30) as response, temporary.open('wb') as stream:
                        checksum = hashlib.new(algorithm)
                        while chunk := response.read(1024*1024):
                            stream.write(chunk); checksum.update(chunk)
                        if checksum.hexdigest() != expected: raise RuntimeError('registry-checksum-mismatch')
                    break
                except (OSError, urllib.error.URLError):
                    if attempt == 2: raise
                    time.sleep(2)
            temporary.replace(target)
        finally:
            if temporary.exists(): temporary.unlink()
    with target.open('rb') as stream:
        checksum = hashlib.file_digest(stream, algorithm).hexdigest()
    if checksum != expected: raise RuntimeError('cached-framework-checksum-mismatch')
    with target.open('rb') as stream:
        digest = hashlib.file_digest(stream, 'sha256').hexdigest()
    if digest!=PINNED_SHA256[version]:raise RuntimeError('pinned-framework-checksum-mismatch')
    record = {'android': version, 'artifact': artifact, 'source': url, 'registry_checksum_algorithm': algorithm,
              'registry_checksum': expected, 'sha256': digest, 'bytes': target.stat().st_size}
    print(json.dumps(record), flush=True)
    (out/(name+'.provenance.json')).write_text(json.dumps(record,indent=2)+'\n',encoding='utf-8')
    return record

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('versions', nargs='+', choices=VERSIONS)
    parser.add_argument('--output', type=Path, default=Path(__file__).resolve().parent/'out/frameworks')
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    records = [fetch(version, args.output) for version in args.versions]
    (args.output/'downloads.json').write_text(json.dumps(records, indent=2)+'\n', encoding='utf-8')
