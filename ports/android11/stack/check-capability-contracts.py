# SPDX-License-Identifier: GPL-2.0
"""Exercise the production capability bridge with synthetic Android callbacks."""
from pathlib import Path
import argparse,json,subprocess
B=Path(__file__).resolve().parent
p=argparse.ArgumentParser(description=__doc__)
for key in ('java','ecj','output'):p.add_argument('--'+key,type=Path,required=True)
a=p.parse_args();out=a.output.absolute()
if out.resolve()!=out or not out.is_relative_to((B/'out').resolve())or out.exists():raise SystemExit('fresh-canonical-stack-output-required')
out.mkdir(parents=True);src=out/'stub-src';src.mkdir()
stubs={
'android/os/RemoteCallbackList.java':'''package android.os;public class RemoteCallbackList<T>{public int count;public int getRegisteredCallbackCount(){return count;}}''',
'android/util/Log.java':'''package android.util;public class Log{public static int i(String t,String m){return 0;}}''',
'android/telephony/ims/stub/ImsRegistrationImplBase.java':'''package android.telephony.ims.stub;public class ImsRegistrationImplBase{public static final int REGISTRATION_TECH_IWLAN=1;}''',
'android/telephony/ims/feature/ImsFeature.java':'''package android.telephony.ims.feature;import android.os.RemoteCallbackList;public class ImsFeature{private final RemoteCallbackList<Object> mCapabilityCallbacks=new RemoteCallbackList<>();public static class CapabilityCallbackProxy{}public void callbackCount(int n){mCapabilityCallbacks.count=n;}}''',
'android/telephony/ims/feature/MmTelFeature.java':'''package android.telephony.ims.feature;public class MmTelFeature extends ImsFeature{public int reported;public static class MmTelCapabilities{public static final int CAPABILITY_TYPE_VOICE=1,CAPABILITY_TYPE_SMS=8;public int mask;public void addCapabilities(int n){mask|=n;}}protected final void notifyCapabilitiesStatusChanged(MmTelCapabilities c){reported=c.mask;}public void changeEnabledCapabilities(CapabilityChangeRequest r,CapabilityCallbackProxy p){}}''',
'android/telephony/ims/feature/CapabilityChangeRequest.java':'''package android.telephony.ims.feature;import java.util.*;public class CapabilityChangeRequest{public final List<CapabilityPair> enable=new ArrayList<>(),disable=new ArrayList<>();public List<CapabilityPair> getCapabilitiesToEnable(){return enable;}public List<CapabilityPair> getCapabilitiesToDisable(){return disable;}public static class CapabilityPair{int c,t;public CapabilityPair(int c,int t){this.c=c;this.t=t;}public int getCapability(){return c;}public int getRadioTech(){return t;}}}''',
'CapabilityBridgeContract.java':'''import me.phh.ims.PhhMmTelFeatureProtected;import android.telephony.ims.feature.CapabilityChangeRequest;import java.util.*;
public class CapabilityBridgeContract{static int n;static void check(boolean v){if(!v)throw new AssertionError(n);n++;}
public static void main(String[]a){PhhMmTelFeatureProtected f=new PhhMmTelFeatureProtected(1);
check(f.reported==0);check(!f.hasActiveRegistration());f.reportRegistrationCapabilities(true);check(f.reported==9);
CapabilityChangeRequest r=new CapabilityChangeRequest();r.disable.add(new CapabilityChangeRequest.CapabilityPair(8,1));f.changeEnabledCapabilities(r,null);check(f.reported==1);check(f.hasActiveRegistration());
f.republishRegistrationCapabilities();check(f.reported==1);check(((Number)f.capabilitySnapshot().get("enabled_mask")).intValue()==1);
f.reportRegistrationCapabilities(false);check(f.reported==0);r=new CapabilityChangeRequest();r.enable.add(new CapabilityChangeRequest.CapabilityPair(8,0));f.changeEnabledCapabilities(r,null);check(f.reported==0);check(((Number)f.capabilitySnapshot().get("enabled_mask")).intValue()==1);
r=new CapabilityChangeRequest();r.enable.add(new CapabilityChangeRequest.CapabilityPair(8,1));f.changeEnabledCapabilities(r,null);check(f.reported==0);f.republishRegistrationCapabilities();check(f.reported==0);f.reportRegistrationCapabilities(true);check(f.reported==9);
f.callbackCount(2);check(((Number)f.capabilitySnapshot().get("capability_callback_count")).intValue()==2);f.callbackCount(0);check(((Number)f.capabilitySnapshot().get("capability_callback_count")).intValue()==0);f.recordSmsReady();check(((Number)f.capabilitySnapshot().get("sms_ready_events")).longValue()==1);
check(((Number)f.capabilitySnapshot().get("notification_returns")).longValue()==8);check(Boolean.TRUE.equals(f.capabilitySnapshot().get("callback_count_observed")));
check(f.capabilitySnapshot().keySet().equals(new HashSet<String>(Arrays.asList("registered","enabled_mask","last_notified_mask","notification_returns","sms_ready_events","enable_events","disable_events","callback_count_observed","capability_callback_count"))));
System.out.println("capability-bridge-contracts="+n+" passed");}}
'''}
for name,text in stubs.items():q=src/name;q.parent.mkdir(parents=True,exist_ok=True);q.write_text(text+'\n',encoding='utf-8',newline='\n')
with(out/'compile.log').open('wb')as log:subprocess.run([str(a.java),'-jar',str(a.ecj),'-encoding','UTF-8','-source','8','-target','8','-proc:none','-d',str(out/'classes'),str(B/'ims/PhhMmTelFeatureProtected.java'),*map(str,src.rglob('*.java'))],stdout=log,stderr=subprocess.STDOUT,check=True,timeout=40)
r=subprocess.run([str(a.java),'-cp',str(out/'classes'),'CapabilityBridgeContract'],capture_output=True,text=True,timeout=20)
if r.returncode:
 (out/'failure.txt').write_text(r.stdout+r.stderr,encoding='utf-8',newline='\n');raise SystemExit('capability-contract-failed')
assert r.stdout.strip()=='capability-bridge-contracts=19 passed'
value=dict(schema=1,status='passed',contracts=19,production_bridge_used=True,synthetic_android_callbacks=True,physical_phone_modified=False)
(out/'report.json').write_text(json.dumps(value,indent=2)+'\n',encoding='utf-8');print(json.dumps(value))
