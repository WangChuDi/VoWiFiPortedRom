"""Explicitly fetch pinned Maven build dependencies, not firmware or Android SDKs."""
from pathlib import Path
import os,urllib.request,hashlib,json
B=Path(__file__).resolve().parent
T=Path(os.environ.get('IMS_PORT_TOOLCHAIN',str(B/'toolchain')))
T.mkdir(parents=True,exist_ok=True)
artifacts={
 'android-all-11.jar':'org/robolectric/android-all/11-robolectric-6757853/android-all-11-robolectric-6757853.jar',
 'ecj.jar':'org/eclipse/jdt/ecj/3.37.0/ecj-3.37.0.jar',
 'compiler.jar':'org/jetbrains/kotlin/kotlin-compiler-embeddable/1.9.24/kotlin-compiler-embeddable-1.9.24.jar',
 'stdlib.jar':'org/jetbrains/kotlin/kotlin-stdlib/1.9.24/kotlin-stdlib-1.9.24.jar',
 'script.jar':'org/jetbrains/kotlin/kotlin-script-runtime/1.9.24/kotlin-script-runtime-1.9.24.jar',
 'reflect.jar':'org/jetbrains/kotlin/kotlin-reflect/1.6.10/kotlin-reflect-1.6.10.jar',
 'trove-ready.jar':'org/jetbrains/intellij/deps/trove4j/1.0.20200330/trove4j-1.0.20200330.jar',
 'annotations.jar':'org/jetbrains/annotations/13.0/annotations-13.0.jar',
 'coroutines-ready.jar':'org/jetbrains/kotlinx/kotlinx-coroutines-core-jvm/1.6.1/kotlinx-coroutines-core-jvm-1.6.1.jar',
}
receipt={}
for name,relative in artifacts.items():
    url='https://repo.maven.apache.org/maven2/'+relative
    destination=T/name
    if not destination.exists():
        print('Downloading',name,flush=True)
        with urllib.request.urlopen(url,timeout=90) as response:
            data=response.read()
        if not data.startswith(b'PK'):raise RuntimeError('Not a JAR: '+name)
        destination.write_bytes(data)
    receipt[name]={'url':url,'sha256':hashlib.sha256(destination.read_bytes()).hexdigest()}
(T/'receipt.json').write_text(json.dumps(receipt,indent=2)+'\n')
print('Install Android SDK Build Tools 30.0.3 separately; place its lib/d8.jar at toolchain/android-build-tools/d8.jar.')
