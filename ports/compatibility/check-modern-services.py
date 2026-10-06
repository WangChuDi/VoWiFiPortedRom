# SPDX-License-Identifier: GPL-2.0
"""Verify compiled modern service roles, SDK boundary and exact research bundle."""
from pathlib import Path
import hashlib,json,os,re,runpy,subprocess,sys,zipfile
import xml.etree.ElementTree as ET
B=Path(__file__).resolve().parent
sys.path.insert(0,str(B.parent/'android11'))
from build_common import TOOLS
OUT=B.parent/'android12/out'
decode=runpy.run_path(str(B/'read-framework-abi.py'))['decode_class']
AAPT=Path(os.environ.get('AAPT2',str(TOOLS/'android-build-tools'/('aapt2.exe' if os.name=='nt' else 'aapt2'))))
roles={'iwlan':('dev.codex.vowifi.iwlan','dev.codex.vowifi.iwlan.ModernIwlanDataService'),
       'qns':('dev.codex.vowifi.qns','dev.codex.vowifi.qns.TrialQnsService'),
       'ims':('me.phh.ims','me.phh.ims.PhhImsService')}
ANDROID='http://schemas.android.com/apk/res/android'
def attribute(node,name):return node.get('{'+ANDROID+'}'+name)
def full_name(package,name):
    if not name:raise AssertionError('compiled-component-name-missing')
    return package+name if name.startswith('.') else name if '.' in name else package+'.'+name
def compiled_manifest(apk):
    dump=subprocess.run([str(AAPT),'dump','xmltree','--file','AndroidManifest.xml',str(apk)],capture_output=True,check=True).stdout.decode('utf-8')
    stack=[];root=None
    for line in dump.splitlines():
        element=re.match(r'^(\s*)E: (\S+)',line)
        if element:
            depth=len(element.group(1));node=ET.Element(element.group(2))
            while stack and stack[-1][0]>=depth:stack.pop()
            if stack:stack[-1][1].append(node)
            elif root is None:root=node
            else:raise AssertionError('compiled-manifest-multiple-roots')
            stack.append((depth,node));continue
        entry=re.match(r'^\s*A: (.+?)(?:\(0x[0-9a-fA-F]+\))?=(.*)$',line)
        if entry:
            if not stack:raise AssertionError('compiled-manifest-attribute-without-element')
            key,value=entry.groups();prefix=ANDROID+':'
            if key.startswith(prefix):key='{'+ANDROID+'}'+key[len(prefix):]
            if value.startswith('"'):value=json.JSONDecoder().raw_decode(value)[0]
            else:value=value.split(' ',1)[0]
            stack[-1][1].set(key,value)
    if root is None or root.tag!='manifest':raise AssertionError('compiled-manifest-unreadable')
    return root
def check_binding(role,package,tree):
    application=tree.find('application')
    if application is None or attribute(application,'directBootAware')!='true':raise AssertionError('compiled-direct-boot-application-missing')
    services={}
    for node in application.findall('service'):
        name=full_name(package,attribute(node,'name'))
        if name in services:raise AssertionError('compiled-duplicate-service')
        services[name]=node
    expected={
        'iwlan':{'dev.codex.vowifi.iwlan.ModernIwlanDataService':('android.permission.BIND_TELEPHONY_DATA_SERVICE','android.telephony.data.DataService'),
                 'dev.codex.vowifi.iwlan.TrialIwlanNetworkService':('android.permission.BIND_TELEPHONY_NETWORK_SERVICE','android.telephony.NetworkService')},
        'qns':{'dev.codex.vowifi.qns.TrialQnsService':('android.permission.BIND_TELEPHONY_DATA_SERVICE','android.telephony.data.QualifiedNetworksService')},
        'ims':{'me.phh.ims.PhhImsService':('android.permission.BIND_IMS_SERVICE','android.telephony.ims.ImsService'),
               'me.phh.ims.StackCheckService':('android.permission.MODIFY_PHONE_STATE',None)},
    }[role]
    if set(services)!=set(expected):raise AssertionError('compiled-service-registration-mismatch')
    for name,(permission,action) in expected.items():
        node=services[name]
        if attribute(node,'permission')!=permission or attribute(node,'exported')!='true':raise AssertionError('compiled-service-binding-permission-mismatch')
        if attribute(node,'directBootAware') not in (None,'true'):raise AssertionError('compiled-service-direct-boot-disabled')
        actions=[attribute(item,'name') for item in node.findall('intent-filter/action')]
        if actions!=([action] if action else []):raise AssertionError('compiled-service-action-mismatch')
    providers=application.findall('provider')
    expected_providers={'dev.codex.vowifi.common.StackTelemetryProvider':package+'.status'}
    if role=='iwlan':expected_providers['dev.codex.vowifi.runtime.ModernRuntimeProvider']=package+'.runtime'
    if len(providers)!=len(expected_providers):raise AssertionError('compiled-status-provider-registration-mismatch')
    names=[]
    for provider in providers:
        name=full_name(package,attribute(provider,'name'));names.append(name)
        if name not in expected_providers or attribute(provider,'authorities')!=expected_providers[name] or attribute(provider,'permission')!='android.permission.READ_PRIVILEGED_PHONE_STATE' or attribute(provider,'exported')!='true' or attribute(provider,'directBootAware')!='true':raise AssertionError('compiled-status-provider-guard-mismatch')
    if set(names)!=set(expected_providers):raise AssertionError('compiled-status-provider-registration-mismatch')
    if role=='iwlan' and not any(attribute(node,'name')=='android.net.ipsec.ike' and attribute(node,'required')=='true' for node in application.findall('uses-library')):raise AssertionError('compiled-required-IKE-library-missing')
manifest=json.loads((OUT/'services-manifest.json').read_text(encoding='utf-8'))
if manifest['kind']!='unsigned-service-research-bundle' or manifest['installer_included'] or manifest['device_validated']:raise AssertionError('unexpected-deployment-claim')
if manifest['min_sdk']!=31 or manifest['target_sdk']!=31:raise AssertionError('incorrect-bundle-SDK-boundary')
if set(manifest['services'])!=set(roles):raise AssertionError('missing-or-extra-service-role')
records=[]
for role,(package,service) in roles.items():
    apk=OUT/(role+'-unsigned.apk')
    metadata=manifest['services'][role]
    if metadata['package']!=package or metadata['file']!=apk.name or metadata['sha256']!=hashlib.sha256(apk.read_bytes()).hexdigest():raise AssertionError('role-artifact-mismatch')
    badging=subprocess.run([str(AAPT),'dump','badging',str(apk)],capture_output=True,check=True).stdout.decode('utf-8')
    if "package: name='"+package+"'" not in badging or "sdkVersion:'31'" not in badging or "targetSdkVersion:'31'" not in badging:raise AssertionError('compiled-manifest-boundary-mismatch')
    check_binding(role,package,compiled_manifest(apk))
    with zipfile.ZipFile(apk) as archive:
        dex=b''.join(archive.read(name) for name in archive.namelist() if name.startswith('classes') and name.endswith('.dex'))
        if ('L'+service.replace('.','/')+';').encode() not in dex:raise AssertionError('compiled-service-missing')
        if b'Ldev/codex/vowifi/common/StackTelemetryProvider;' not in dex:raise AssertionError('compiled-status-provider-missing')
        if role=='iwlan' and b'Ldev/codex/vowifi/iwlan/TrialIwlanDataService;' in dex:raise AssertionError('legacy-IWLAN-provider-in-modern-APK')
        if role=='iwlan' and any(symbol not in dex for symbol in (b'Ldev/codex/vowifi/runtime/ModernRuntimeProvider;',b'Ldev/codex/vowifi/runtime/ModernServiceBindings;',b'Ldev/codex/vowifi/tool/RuntimeAbiProbe;')):raise AssertionError('service-loader-runtime-probe-missing')
    records.append({'role':role,'package':package,'min_sdk':31,'target_sdk':31,'compiled_service_present':True,'compiled_bindings_verified':True,'sha256':metadata['sha256']})
ims=decode((OUT/'ims-kotlin-classes/me/phh/ims/PhhImsService.class').read_bytes())
for name,descriptor in {
    'createMmTelFeatureForSubscription':'(II)Landroid/telephony/ims/feature/MmTelFeature;',
    'getRegistrationForSubscription':'(II)Landroid/telephony/ims/stub/ImsRegistrationImplBase;',
    'getConfigForSubscription':'(II)Landroid/telephony/ims/stub/ImsConfigImplBase;',
}.items():
    if not any(method['name']==name and method['descriptor']==descriptor and method['access']&1 for method in ims['methods']):raise AssertionError('subscription-aware-IMS-entry-missing-'+name)
with zipfile.ZipFile(OUT/'modern-services-unsigned.zip') as archive:
    expected={role+'-unsigned.apk' for role in roles}|{'manifest.json','THIRD_PARTY.md','LICENSE-phhusson-ims'}
    if set(archive.namelist())!=expected or len(archive.namelist())!=len(expected):raise AssertionError('research-archive-layout-mismatch')
    if json.loads(archive.read('manifest.json'))!=manifest:raise AssertionError('bundle-manifest-mismatch')
    for role in roles:
        if hashlib.sha256(archive.read(role+'-unsigned.apk')).hexdigest()!=manifest['services'][role]['sha256']:raise AssertionError('bundle-payload-mismatch')
    if archive.read('LICENSE-phhusson-ims')!=(B.parent/'android11/vendor/phhusson-ims/LICENSE').read_bytes():raise AssertionError('source-license-mismatch')
    if archive.read('THIRD_PARTY.md')!=(B.parent/'android11/THIRD_PARTY.md').read_bytes():raise AssertionError('source-attribution-mismatch')
(B/'out/modern-services-bundle-check.json').write_text(json.dumps({'scope':'compiled-artifact-check-not-device-support','services':records,'subscription_aware_ims_entries':3,'exact_bundle':True},indent=2)+'\n',encoding='utf-8')
print('Modern services compiled roles/SDK31/subscription-aware IMS/bundle/license=PASS; installation and carrier behavior untested')
