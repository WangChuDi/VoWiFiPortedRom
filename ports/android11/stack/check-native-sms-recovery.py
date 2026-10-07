# SPDX-License-Identifier: GPL-2.0
"""Production gate/activity/reader contracts; Android/Binder are controlled host stubs."""
from pathlib import Path
import argparse,hashlib,json,subprocess
B=Path(__file__).resolve().parent
p=argparse.ArgumentParser()
for name in ('java','ecj','output'):p.add_argument('--'+name,type=Path,required=True)
a=p.parse_args();out=a.output.absolute()
if out.resolve()!=out or not out.is_relative_to((B/'out').resolve())or out.exists():raise SystemExit('fresh-canonical-stack-output-required')
out.mkdir(parents=True);src=out/'stubs';src.mkdir()
stubs={
'android/os/Build.java':'''package android.os;public class Build {public static String DEVICE="raphael";public static class VERSION{public static int SDK_INT=31;}}''',
'android/os/Process.java':'''package android.os;public class Process {public static int uid=12345;public static int myUid(){return uid;}}''',
'android/os/Binder.java':'''package android.os;public class Binder {public static int calling=12345;public static boolean threadLocal;public static final ThreadLocal<Integer> local=new ThreadLocal<>();public static int getCallingUid(){return threadLocal?(local.get()==null?Process.myUid():local.get()):calling;}}''',
'android/os/SystemClock.java':'''package android.os;public class SystemClock{public static long elapsedRealtime(){return System.nanoTime()/1000000;}}''',
'android/content/Context.java':'''package android.content;public abstract class Context{public abstract <T>T getSystemService(Class<T> type);}''',
'android/telephony/SubscriptionInfo.java':'''package android.telephony;public class SubscriptionInfo{final int slot,sub;public SubscriptionInfo(int slot,int sub){this.slot=slot;this.sub=sub;}public int getSimSlotIndex(){return slot;}public int getSubscriptionId(){return sub;}}''',
'android/telephony/SubscriptionManager.java':'''package android.telephony;import java.util.*;public class SubscriptionManager{public volatile List<SubscriptionInfo> active;public List<SubscriptionInfo>getActiveSubscriptionInfoList(){return active;}}''',
'android/telephony/TelephonyManager.java':'''package android.telephony;public class TelephonyManager{public static final int SIM_STATE_READY=5;public int state=5;public int getSimState(int slot){return state;}}''',
'android/os/IBinder.java':'''package android.os;public interface IBinder{boolean isBinderAlive();}''',
'android/os/ServiceManager.java':'''package android.os;public class ServiceManager{public static IBinder binder;public static IBinder getService(String name){if(!"isms".equals(name))throw new AssertionError();return binder;}}''',
'com/android/internal/telephony/ISms.java':'''package com.android.internal.telephony;import android.os.IBinder;public interface ISms{boolean isImsSmsSupportedForSubscriber(int sub);public static class Stub {public static Object service;public static Object asInterface(IBinder b){return service;}}}''',
'NativeSmsRecoveryContract.java':'''import me.phh.ims.*;import java.util.*;import com.android.internal.telephony.ISms;import android.os.*;
public class NativeSmsRecoveryContract{
static int n;static void ok(boolean b){n++;if(!b)throw new AssertionError(n);}
static String state(NativeSmsRecoveryGate g){return(String)g.snapshot().get("native_watch_status");}
static void calibrated(Boolean b)throws Exception{java.lang.reflect.Field f=NativeSmsStatusReader.class.getDeclaredField("calibrated");f.setAccessible(true);f.set(null,b);}
static boolean ready(NativeSmsRecoveryGate g,long start){ok(!g.sample(start,true,false));ok(!g.sample(start+20000,true,false));ok(!g.sample(start+23000,true,false));return g.sample(start+26000,true,false);}
public static void main(String[]a)throws Exception{
NativeSmsRecoveryGate g=new NativeSmsRecoveryGate(true);ok(!g.sample(1,true,true));ok(state(g).equals("HEALTHY"));ok(!g.sample(20001,true,null));ok(state(g).equals("UNKNOWN"));
ok(!g.sample(23001,true,false));ok(!g.sample(24001,true,false));ok(!g.sample(26001,true,false));ok(g.sample(29001,true,false));g.requested(29001);ok(state(g).equals("RECOVERING"));
ok(!g.sample(33001,true,false));ok(!g.sample(36001,true,false));ok(!g.sample(39001,true,false));ok(state(g).equals("COOLDOWN"));ok(g.sample(149001,true,false));g.requested(149001);
ok(!g.sample(150001,false,false));ok(state(g).equals("INACTIVE"));ok(ready(g,270001));g.requested(296001);ok(!g.sample(299001,true,false));ok(!g.sample(302001,true,false));ok(!g.sample(305001,true,false));ok(state(g).equals("LIMIT"));
ok(((Number)g.snapshot().get("native_watch_requests")).intValue()==3);
g=new NativeSmsRecoveryGate(true);ok(!g.sample(1,true,false));ok(!g.sample(20001,true,false));ok(!g.sample(23001,true,null));ok(!g.sample(26001,true,false));ok(!g.sample(29001,true,false));ok(g.sample(32001,true,false));
ok(!g.sample(33001,false,null));ok(!g.sample(54001,true,false));ok(!g.sample(73001,true,false));ok(!g.sample(74001,true,false));ok(!g.sample(77001,true,false));ok(g.sample(80001,true,false));
g=new NativeSmsRecoveryGate(false);ok(!g.sample(100000,true,false));ok(state(g).equals("UNSUPPORTED"));
boolean refused=false;try{g.sample(99999,true,false);}catch(IllegalArgumentException e){refused=true;}ok(refused);
refused=false;try{g.requested(100000);}catch(IllegalStateException e){refused=true;}ok(refused);
g=new NativeSmsRecoveryGate(true);ok(!g.sample(1,true,false));ok(!g.sample(20001,true,false));ok(!g.sample(23001,true,false));g.inactive();ok(state(g).equals("INACTIVE"));ok(g.snapshot().get("native_watch_native").equals("UNKNOWN"));ok(((Number)g.snapshot().get("native_watch_checks")).intValue()==3);ok(!g.sample(24001,true,false));ok(!g.sample(43001,true,false));ok(!g.sample(44001,true,false));ok(!g.sample(47001,true,false));ok(g.sample(50001,true,false));
long[]clock={0};SmsActivityTracker activity=new SmsActivityTracker(()->clock[0]);ok(activity.quiet(5000));SmsActivityTracker.Scope first=activity.begin();ok(!activity.quiet(0));SmsActivityTracker.Scope second=activity.begin();first.close();first.close();ok(!activity.quiet(0));clock[0]=100;second.close();ok(!activity.quiet(5000));clock[0]=5100;ok(activity.quiet(5000));clock[0]=99;ok(!activity.quiet(0));
g=new NativeSmsRecoveryGate(true);ok(ready(g,1));g.inactive();g.requested(26001);ok(state(g).equals("INACTIVE"));ok(((Number)g.snapshot().get("native_watch_requests")).intValue()==1);refused=false;try{g.requested(26001);}catch(IllegalStateException e){refused=true;}ok(refused);ok(!ready(g,30001));ok(state(g).equals("COOLDOWN"));
android.os.Process.uid=0;android.os.Binder.calling=0;calibrated(true);ok(NativeSmsStatusReader.observe(7)==null);ok(!NativeSmsStatusReader.ownUid());
android.os.Process.uid=12345;android.os.Binder.calling=0;ok(NativeSmsStatusReader.observe(7)==null);android.os.Binder.calling=12345;ok(NativeSmsStatusReader.ownUid());
ServiceManager.binder=()->true;ISms.Stub.service=(ISms)(sub)->{if(sub!=7)throw new AssertionError();return true;};ok(Boolean.TRUE.equals(NativeSmsStatusReader.observe(7)));
ISms.Stub.service=(ISms)(sub)->false;ok(Boolean.FALSE.equals(NativeSmsStatusReader.observe(7)));
ISms.Stub.service=(ISms)(sub)->{throw new SecurityException();};ok(NativeSmsStatusReader.observe(7)==null);ok(NativeSmsStatusReader.observe(-1)==null);
ServiceManager.binder=()->false;ok(NativeSmsStatusReader.observe(7)==null);ServiceManager.binder=null;ok(NativeSmsStatusReader.observe(7)==null);
calibrated(null);ok(NativeSmsStatusReader.observe(7)==null);ok(!NativeSmsStatusReader.profileEligible());
android.os.Binder.threadLocal=true;android.os.Binder.local.set(0);
android.telephony.SubscriptionManager manager=new android.telephony.SubscriptionManager();android.telephony.TelephonyManager phone=new android.telephony.TelephonyManager();
java.util.List<android.telephony.SubscriptionInfo> both=Arrays.asList(new android.telephony.SubscriptionInfo(0,7),new android.telephony.SubscriptionInfo(1,8));manager.active=both;
android.content.Context context=new android.content.Context(){public<T>T getSystemService(Class<T>t){return t.cast(t==android.telephony.SubscriptionManager.class?manager:phone);}};
ServiceManager.binder=()->true;ISms.Stub.service=(ISms)(sub)->sub==8;
for(int sdk=31;sdk<=37;sdk++){
 Build.VERSION.SDK_INT=sdk;ok(NativeSmsStatusReader.diagnosticProfileEligible());ok(!NativeSmsStatusReader.profileEligible());
 Map<String,Object>a0=NativeSmsDiagnostics.query(context,0,7),a1=NativeSmsDiagnostics.query(context,1,8);
 ok(a0.get("status").equals("OBSERVED"));ok(a0.get("result").equals("FALSE"));ok(a1.get("result").equals("TRUE"));ok(a0.get("self_uid").equals(true));ok(a1.get("self_uid").equals(true));ok(a0.get("read_only").equals(true));ok(a0.get("scope").equals("IMS_OR_RADIO"));
}
Map<String,Object>missing=NativeSmsDiagnostics.query(context,0,8);ok(missing.get("result").equals("UNKNOWN"));ok(missing.get("status").equals("UNAVAILABLE"));
manager.active=Arrays.asList(new android.telephony.SubscriptionInfo(0,7),new android.telephony.SubscriptionInfo(0,7));ok(NativeSmsDiagnostics.query(context,0,7).get("result").equals("UNKNOWN"));manager.active=both;
ISms.Stub.service=(ISms)(sub)->{manager.active=Arrays.asList(new android.telephony.SubscriptionInfo(0,9),new android.telephony.SubscriptionInfo(1,8));return true;};ok(NativeSmsDiagnostics.query(context,0,7).get("result").equals("UNKNOWN"));manager.active=both;
ISms.Stub.service=(ISms)(sub)->{throw new SecurityException();};ok(NativeSmsDiagnostics.query(context,0,7).get("status").equals("UNAVAILABLE"));
android.os.Process.uid=0;ok(NativeSmsDiagnostics.query(context,0,7).get("result").equals("UNKNOWN"));android.os.Process.uid=12345;
for(int sdk:new int[]{29,30,38}){Build.VERSION.SDK_INT=sdk;ok(!NativeSmsStatusReader.diagnosticProfileEligible());ok(NativeSmsDiagnostics.query(context,0,7).get("status").equals("UNSUPPORTED"));}Build.VERSION.SDK_INT=37;
java.util.concurrent.CountDownLatch release=new java.util.concurrent.CountDownLatch(1);ISms.Stub.service=(ISms)(sub)->{try{release.await();}catch(InterruptedException e){throw new RuntimeException(e);}return false;};
Map<String,Object>timed=NativeSmsDiagnostics.query(context,0,7);ok(timed.get("status").equals("TIMEOUT"));ok(timed.get("result").equals("UNKNOWN"));
Map<String,Object>busy=NativeSmsDiagnostics.query(context,1,8);ok(busy.get("status").equals("BUSY"));ok(busy.get("result").equals("UNKNOWN"));
release.countDown();ISms.Stub.service=(ISms)(sub)->sub==8;Map<String,Object>next=null;
for(int i=0;i<100;i++){next=NativeSmsDiagnostics.query(context,1,8);if(!next.get("status").equals("BUSY"))break;Thread.sleep(10);}
ok(next.get("result").equals("TRUE"));ok(next.get("self_uid").equals(true));ok(timed.get("result").equals("UNKNOWN"));
System.out.println("native-sms-recovery-contracts="+n);}}
'''}
for name,text in stubs.items():file=src/name;file.parent.mkdir(parents=True,exist_ok=True);file.write_bytes((text+'\n').encode())
production=[B/'ims'/name for name in ('SmsActivityTracker.java','NativeSmsRecoveryGate.java','NativeSmsStatusReader.java','NativeSmsDiagnostics.java')]
with(out/'compile.log').open('wb')as log:
 subprocess.run([str(a.java),'-jar',str(a.ecj),'-source','8','-target','8','-proc:none','-d',str(out/'classes'),*map(str,production),*map(str,src.rglob('*.java'))],stdout=log,stderr=subprocess.STDOUT,check=True,timeout=40)
r=subprocess.run([str(a.java),'-cp',str(out/'classes'),'NativeSmsRecoveryContract'],capture_output=True,text=True,timeout=20)
if r.returncode:(out/'failure.txt').write_text(r.stdout+r.stderr);raise SystemExit('native-sms-contract-failed')
count=int(r.stdout.strip().split('=')[1]);assert count>=60
report=dict(schema=1,status='passed',contracts=count,production_gate_activity_and_reader_used=True,production_diagnostic_worker_used=True,dual_subscription_stubs_used=True,timeout_busy_and_late_result_refusal_checked=True,binder_context_stubbed=True,device_self_uid_query_verified=False,phone_modified=False,source_pins={file.relative_to(B).as_posix():hashlib.sha256(file.read_bytes()).hexdigest()for file in production})
(out/'report.json').write_bytes((json.dumps(report,indent=2)+'\n').encode());print(json.dumps(report))
