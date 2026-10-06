# SPDX-License-Identifier: GPL-2.0
"""False-positive contracts for requested permissions and framework bindings."""
import importlib.util
from pathlib import Path
spec = importlib.util.spec_from_file_location('installation', Path(__file__).with_name('check-installation-matrix.py'))
audit = importlib.util.module_from_spec(spec)
spec.loader.exec_module(audit)
assert not audit.granted('  android.permission.SEND_SMS\n', 'SEND_SMS')
assert not audit.granted('  android.permission.SEND_SMS: granted=false, flags=[]\n', 'SEND_SMS')
assert not audit.granted('  android.permission.SEND_SMS_EXTRA: granted=true\n', 'SEND_SMS')
assert audit.granted('  android.permission.SEND_SMS: granted=true, flags=[]\n', 'SEND_SMS')
healthy = ('    * IntentBindRecord{abc CREATE}:\n'
           '      requested=true received=true hasBound=true doRebind=false\n'
           '      * Client AppBindRecord{def ProcessRecord{ghi 123:com.android.phone/1001}}\n')
assert audit.phone_bound(healthy)
assert not audit.phone_bound(healthy.replace('received=true', 'received=false'))
assert not audit.phone_bound(healthy.replace('/1001', '/2000'))
assert not audit.phone_bound('recentCallingPackage=com.android.phone\n' + healthy.replace('com.android.phone/1001', 'android/1000'))
assert not audit.phone_bound(healthy.replace('received=true', 'received=false') + healthy.replace('com.android.phone/1001', 'android/1000'))
assert audit.primary_user_only(' User 0: installed=true\n User 0:\n')
assert not audit.primary_user_only(' User 0: installed=true\n User 10: installed=true\n')
assert not audit.primary_user_only(' User 0:\n')
print('12 installation-observation contracts passed')
