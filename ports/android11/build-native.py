"""Build ARM64 socket-policy JNI. Never downloads or distributes device libraries."""
from pathlib import Path
import os,subprocess,sys
B=Path(__file__).resolve().parent
output=B/'libcodex-ims-nested.so'
clang=os.environ.get('ANDROID_NDK_CLANG')
if clang:
    args=[clang,'-shared','-fPIC','-O2','-Wl,-soname,libcodex-ims-nested.so','-o',str(output),str(B/'nested-policy.c')]
    if os.name=='nt' and clang.lower().endswith('.cmd'):
        raise SystemExit('Use the NDK Linux/macOS compiler driver, or the documented Zig fallback on Windows')
else:
    zig_dir=os.environ.get('IMS_ZIG_DIR')
    libc=os.environ.get('IMS_BIONIC_LIBC')
    if not zig_dir or not libc:raise SystemExit('Set ANDROID_NDK_CLANG, or IMS_ZIG_DIR and IMS_BIONIC_LIBC; see README.md')
    z=Path(zig_dir)
    args=[str(z/('zig.exe' if os.name=='nt' else 'zig')),'cc','-target','aarch64-linux-musl','-shared','-nostdlib','-fPIC',
          '-fno-stack-protector','-O2','-I',str(z/'lib/libc/include/any-linux-any'),'-I',str(z/'lib/libc/include/aarch64-linux-any'),
          '-Wl,-soname,libcodex-ims-nested.so','-o',str(output),str(B/'nested-policy.c'),libc]
r=subprocess.run(args,capture_output=True,timeout=90)
(B/'native-build.log').write_bytes(r.stdout+r.stderr)
sys.stdout.buffer.write(r.stdout);sys.stderr.buffer.write(r.stderr)
raise SystemExit(r.returncode)
