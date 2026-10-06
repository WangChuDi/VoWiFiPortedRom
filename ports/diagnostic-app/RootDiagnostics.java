// SPDX-License-Identifier: GPL-2.0
package dev.codex.vowifi.tool;

import android.app.ActivityThread;
import android.content.Context;
import android.database.Cursor;
import android.net.*;
import android.os.*;
import android.telephony.*;
import android.telephony.ims.ImsMmTelManager;
import android.telephony.ims.feature.MmTelFeature;
import org.json.JSONObject;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;

/** Read-only root entry: status metadata only, no calls/SMS/SIM authentication. */
public final class RootDiagnostics {
    private static final String CONTROLLER="/data/adb/modules/codex_vowifi_stack_api30/control.sh";
    private static volatile int transport=-1;
    private static volatile boolean voice=false,sms=false,capObserved=false;
    public static void main(String[] args){
        JSONObject result=new JSONObject();
        try{
            if(android.os.Process.myUid()!=0)throw new SecurityException("root-required");
            if(args.length!=1)throw new IllegalArgumentException("slot-required");
            int slot=Integer.parseInt(args[0]);
            if(slot<0||slot>7)throw new IllegalArgumentException("slot-range");
            Looper.prepareMainLooper();
            Context context=ActivityThread.systemMain().getSystemContext();
            // MIUI app_process does not run the telephony Zygote bootstrap.
            try{
                Class<?> initializer=Class.forName("android.telephony.TelephonyFrameworkInitializer");
                Class<?> services=Class.forName("android.os.TelephonyServiceManager");
                if(initializer.getMethod("getTelephonyServiceManager").invoke(null)==null)
                    initializer.getMethod("setTelephonyServiceManager",services).invoke(null,services.getConstructor().newInstance());
            }catch(Throwable t){result.put("bootstrap_error",errorName(t));}
            collect(result,context,slot);
        }catch(Throwable t){try{result.put("error",t.getClass().getSimpleName());for(StackTraceElement frame:t.getStackTrace())if(frame.getClassName().equals(RootDiagnostics.class.getName())){result.put("error_line",frame.getLineNumber());break;}}catch(Exception ignored){}}
        System.out.println(result.toString());
        System.exit(0);
    }
    private static void collect(JSONObject out,Context context,int slot)throws Exception{
        out.put("sdk",Build.VERSION.SDK_INT).put("device",Build.DEVICE).put("slot",slot);
        out.put("runtime_abi",new JSONObject(RuntimeAbiProbe.inspect(Build.VERSION.SDK_INT,RootDiagnostics.class.getClassLoader())));
        JSONObject provider=null;
        SubscriptionInfo info=null;
        try{
            SubscriptionManager subscriptions=context.getSystemService(SubscriptionManager.class);
            if(subscriptions==null)throw new IllegalStateException("subscription-service-unavailable");
            List<SubscriptionInfo> active=subscriptions.getActiveSubscriptionInfoList();
            if(active!=null)for(SubscriptionInfo candidate:active)if(candidate.getSimSlotIndex()==slot){info=candidate;break;}
        }catch(Throwable t){out.put("subscription_error",errorName(t));}
        int sub=info==null?-1:info.getSubscriptionId();
        try{provider=controller(out,slot,sub);}catch(Throwable t){out.put("controller_error",errorName(t));}
        String operator="";int simState=TelephonyManager.SIM_STATE_UNKNOWN;
        out.put("sim",info==null?(out.has("subscription_error")?"订阅信息不可见":"无活动 SIM"):"活动订阅存在，SIM 状态未知");
        if(info!=null){
            out.put("sub_id",sub);
            try{
                TelephonyManager telephony=context.getSystemService(TelephonyManager.class);
                if(telephony==null)throw new IllegalStateException("telephony-service-unavailable");
                TelephonyManager tm=telephony.createForSubscriptionId(sub);
                simState=telephony.getSimState(slot);
                out.put("sim_state",simState).put("sim",simState==TelephonyManager.SIM_STATE_READY?"已就绪":simState==TelephonyManager.SIM_STATE_UNKNOWN?"活动订阅存在，SIM 状态未知":"活动订阅存在，SIM 尚未就绪（状态 "+simState+"）");
                operator=tm.getSimOperator();if(operator==null)operator="";
                out.put("operator",operator);
            }catch(Throwable t){out.put("telephony_error",errorName(t));}
        }
        // The bundled engine is deliberately limited to the actually tested profile.
        out.put("engine_supported",simState==TelephonyManager.SIM_STATE_READY&&Build.VERSION.SDK_INT==30&&"raphael".equals(Build.DEVICE)&&"23415".equals(operator)&&slot>=0&&slot<8&&sub>=0);
        Network wifi=null;int imsCount=0,pcscfCount=0,unattributed=0;String iface=null;
        try{
          ConnectivityManager cm=context.getSystemService(ConnectivityManager.class);
          if(cm==null)throw new IllegalStateException("connectivity-service-unavailable");
          for(Network network:cm.getAllNetworks()){
            NetworkCapabilities caps=cm.getNetworkCapabilities(network);LinkProperties lp=cm.getLinkProperties(network);
            if(caps==null||lp==null)continue;
            if(caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)&&!caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)&&caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))wifi=network;
            if(caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_IMS)){
                // No attribution by enumeration order: match the selected subscription.
                int attribution=networkAttribution(caps,sub);
                if(attribution==1){imsCount++;iface=lp.getInterfaceName();pcscfCount+=lp.getPcscfServers().size();}
                else if(attribution==-1)unattributed++;
            }
          }
          out.put("ims_network_count",imsCount).put("pcscf_count",pcscfCount).put("ims_unattributed_network_count",unattributed);
        }catch(Throwable t){out.put("network_error",errorName(t));}
        out.put("wifi",wifi!=null?"实体 Wi-Fi 已连接":"未找到实体 Wi-Fi 网络");
        out.put("ims_interface",iface==null?JSONObject.NULL:iface);
        if(iface!=null)out.put("ims_interface_present",new File("/sys/class/net",iface).exists());
        if(wifi!=null&&operator.matches("[0-9]{5,6}")){
            String hostname=String.format(Locale.US,"epdg.epc.mnc%03d.mcc%s.pub.3gppnetwork.org",Integer.parseInt(operator.substring(3)),operator.substring(0,3));
            ExecutorService dns=Executors.newSingleThreadExecutor();final Network physical=wifi;
            Future<Integer> f=dns.submit(()->physical.getAllByName(hostname).length);
            try{out.put("dns","成功 · "+f.get(7,TimeUnit.SECONDS)+" 个地址");}
            catch(Exception e){out.put("dns","未成功 · "+e.getClass().getSimpleName());f.cancel(true);}
            finally{dns.shutdownNow();}
        }else out.put("dns","未测试");
        out.put("udp","未主动测试 UDP 500/4500；DNS 成功不代表端口可达");
        // Wi-Fi/controller checks remain useful even when subscriptions are hidden.
        if(info==null)return;
        // Display only APN/type, never APN username/password or subscriber identifiers.
        try(Cursor c=context.getContentResolver().query(Uri.parse("content://telephony/carriers/preferapn/subId/"+sub),new String[]{"apn","type"},null,null,null)){
            out.put("apn",c!=null&&c.moveToFirst()?c.getString(0)+" · "+c.getString(1):"未设置首选互联网 APN");
        }catch(Exception e){
            String rows=observation(out,"apn_error",4,"content","query","--uri","content://telephony/carriers/preferapn/subId/"+sub,"--projection","apn:type");
            Matcher fields=Pattern.compile("apn=([^,\\r\\n]+), type=([^\\r\\n]+)").matcher(rows);
            out.put("apn",fields.find()?fields.group(1)+" · "+fields.group(2):"不可见 · "+e.getClass().getSimpleName());
        }
        ImsMmTelManager manager=null;
        try{manager=ImsMmTelManager.createForSubscriptionId(sub);}catch(Throwable t){out.put("ims_error",errorName(t));}
        try{
            if(manager==null)throw new IllegalStateException("ims-manager-unavailable");
            out.put("wfc_setting",manager.isVoWiFiSettingEnabled());
            out.put("wfc_roaming_setting",manager.isVoWiFiRoamingSettingEnabled());
            out.put("wfc_mode",manager.getVoWiFiModeSetting());
        }catch(Throwable e){out.put("settings_error",e.getClass().getSimpleName());}
        try{
            PersistableBundle config=context.getSystemService(CarrierConfigManager.class).getConfigForSubId(sub);
            JSONObject policy=new JSONObject();
            for(String key:new String[]{"carrier_wfc_ims_available_bool","carrier_volte_available_bool","carrier_volte_provisioning_required_bool","carrier_wfc_supports_wifi_only_bool","qns.block_iwlan_in_international_roaming_without_wwan_bool"})
                if(config!=null&&config.containsKey(key))policy.put(key,config.getBoolean(key));
            for(String key:new String[]{"carrier_data_service_wlan_package_override_string","carrier_network_service_wlan_package_override_string","carrier_qualified_networks_service_package_override_string","config_ims_mmtel_package_override_string"})
                if(config!=null&&config.containsKey(key))policy.put(key,config.getString(key));
            out.put("selected_policy",policy);
        }catch(Throwable e){out.put("policy_error",e.getClass().getSimpleName());}
        // The modern public method appeared in API33; reflection permits OEM
        // backports and reports inaccessible/missing methods as unknown.
        try{
            Class<?> provisionClass=Class.forName("android.telephony.ims.ProvisioningManager");
            Object provision=provisionClass.getMethod("createForSubscriptionId",int.class).invoke(null,sub);
            Object status=provisionClass.getMethod("getProvisioningStatusForCapability",int.class,int.class).invoke(provision,1,1);
            out.put("wlan_voice_provisioned",status);
        }catch(Throwable e){out.put("provisioning_error",e.getClass().getSimpleName());}
        ExecutorService statusWorkers=Executors.newFixedThreadPool(3,r->{Thread t=new Thread(r,"vowifi-status");t.setDaemon(true);return t;});
        Map<String,Future<JSONObject>> statuses=new LinkedHashMap<>();
        long statusDeadline=SystemClock.elapsedRealtime()+9500;
        try{
        if(provider!=null&&provider.optInt("owner_slot",-1)==slot&&provider.optInt("owner_sub",-1)==sub){
            String[] channels={"iwlan","qns","ims"};String[] packages={"dev.codex.vowifi.iwlan","dev.codex.vowifi.qns","me.phh.ims"};
            String[] keys={"carrier_data_service_wlan_package_override_string","carrier_qualified_networks_service_package_override_string","config_ims_mmtel_package_override_string"};
            for(int index=0;index<channels.length;index++)if(packages[index].equals(provider.optString(keys[index]))){
                final String channel=channels[index],pkg=packages[index];final int selectedSub=sub;
                statuses.put(channel,statusWorkers.submit(()->serviceStatus(channel,pkg,slot,selectedSub)));
            }
        }
        CountDownLatch gotReg=new CountDownLatch(1),gotCap=new CountDownLatch(1);
        ImsMmTelManager.RegistrationCallback reg=new ImsMmTelManager.RegistrationCallback(){
            public void onRegistered(int t){transport=t;gotReg.countDown();}
            public void onRegistering(int t){transport=-2;gotReg.countDown();}
            public void onUnregistered(android.telephony.ims.ImsReasonInfo reason){transport=-3;gotReg.countDown();}
        };
        ImsMmTelManager.CapabilityCallback cap=new ImsMmTelManager.CapabilityCallback(){
            public void onCapabilitiesStatusChanged(MmTelFeature.MmTelCapabilities c){
                voice=c.isCapable(MmTelFeature.MmTelCapabilities.CAPABILITY_TYPE_VOICE);
                sms=c.isCapable(MmTelFeature.MmTelCapabilities.CAPABILITY_TYPE_SMS);capObserved=true;gotCap.countDown();
            }
        };
        boolean attachedReg=false,attachedCap=false;
        try{
            if(manager==null)throw new IllegalStateException("ims-manager-unavailable");
            manager.registerImsRegistrationCallback(Runnable::run,reg);attachedReg=true;
            manager.registerMmTelCapabilityCallback(Runnable::run,cap);attachedCap=true;
            long callbackDeadline=SystemClock.elapsedRealtime()+3000;
            gotReg.await(3,TimeUnit.SECONDS);gotCap.await(Math.max(1,callbackDeadline-SystemClock.elapsedRealtime()),TimeUnit.MILLISECONDS);
            out.put("ims_transport",transport).put("cap_observed",capObserved).put("voice",voice).put("sms",sms);
        }catch(Throwable e){out.put("ims_error",e.getClass().getSimpleName());}
        finally{
            if(attachedReg)try{manager.unregisterImsRegistrationCallback(reg);}catch(Exception ignored){}
            if(attachedCap)try{manager.unregisterMmTelCapabilityCallback(cap);}catch(Exception ignored){}
        }
        for(Map.Entry<String,Future<JSONObject>> entry:statuses.entrySet()){
            try{
                JSONObject sampled=entry.getValue().get(Math.max(1,statusDeadline-SystemClock.elapsedRealtime()),TimeUnit.MILLISECONDS);
                if(SystemClock.elapsedRealtime()-sampled.getLong("sample_elapsed")>5000)throw new IOException("status-stale-at-collection");
                out.put(entry.getKey()+"_status",sampled);
            }
            catch(Throwable failure){out.put(entry.getKey()+"_status_error",errorName(failure));entry.getValue().cancel(true);}
        }
        statusWorkers.shutdownNow();
        JSONObject ownIwlan=out.optJSONObject("iwlan_status");
        if(ownIwlan!=null&&ownIwlan.optBoolean("observed")){
            out.put("ike","替代服务本次采样 · "+ownIwlan.optString("phase")+" · IKE "+ownIwlan.optBoolean("ike_open")+" · child "+ownIwlan.optBoolean("child_open")+
                " · 入/出站 transform "+ownIwlan.optInt("inbound_transforms")+"/"+ownIwlan.optInt("outbound_transforms"));
            if(ownIwlan.optBoolean("child_open"))out.put("iwlan_interface_matches",iface!=null&&iface.equals(ownIwlan.optString("interface"))&&new File("/sys/class/net",iface).exists());
        }
        if(provider!=null){
            boolean selectedOwner=provider.optInt("owner_slot",-1)==slot&&provider.optInt("owner_sub",-1)==sub;
            if(selectedOwner){String running=observation(out,"iwlan_observation_error",2,"pidof","dev.codex.vowifi.iwlan").trim();if(!out.has("iwlan_observation_error"))out.put("iwlan_process_present",running.matches("[0-9]+"));}
            if(!out.has("ike")&&selectedOwner&&"dev.codex.vowifi.iwlan".equals(provider.optString("carrier_data_service_wlan_package_override_string"))){
                String iwlanPid=observation(out,"iwlan_observation_error",2,"pidof","dev.codex.vowifi.iwlan").trim();
                if(iwlanPid.matches("[0-9]+")){
                    String events=observation(out,"iwlan_observation_error",4,"logcat","-b","main","-d","-v","brief","--pid="+iwlanPid,"-s","Api30IwlanData:D","*:S");
                    String latest=null;
                    for(String event:events.split("[\\r\\n]+")){
                        if(event.contains("slot="+slot+" child=OPENED iface="))latest=iface!=null&&event.contains("slot="+slot+" child=OPENED iface="+iface+" ")?"本轮进程曾报告 child=OPENED；需结合当前接口和注册状态判断":"本轮进程曾报告 child=OPENED；未匹配到当前所选卡的 IMS 接口";
                        String marker="slot="+slot+" closed=";int start=event.indexOf(marker);
                        if(start>=0){
                            String reason=event.substring(start+marker.length()).trim();
                            if(reason.matches("[A-Za-z0-9_./=-]{1,240}"))latest="本轮进程最近关闭原因："+reason+"；历史日志，需结合当前接口和注册状态判断";
                            else latest="本轮进程最近日志报告已关闭；不是实时 IKE 状态";
                        }
                    }
                    if(latest!=null)out.put("ike",latest);
                }
            }
        }
        // Use current phone PID to exclude observations from a previous reload.
        String pid=observation(out,"sms_observation_error",2,"pidof","com.android.phone").trim();
        String last=null;
        if(pid.matches("[0-9]+")){
            try{last=smsDispatcherObservation(pid,slot);}
            catch(Throwable t){out.put("sms_observation_error",errorName(t));}
        }
        out.put("sms_dispatcher",last==null?"本次电话进程尚无发送观测；注册和 SMS 能力不保证分发器已就绪":"最近发送时的历史判定："+last+"\n不代表当前实时可用性");
        if(!out.has("ike"))out.put("ike","不可见：仅凭接口不能确认所选 SIM 的 IKE/child 状态");
        }finally{statusWorkers.shutdownNow();}
    }
    private static JSONObject serviceStatus(String channel,String pkg,int slot,int sub)throws Exception{
        String pid=command(2,"pidof",pkg).trim();
        if(!pid.matches("[0-9]+"))throw new IOException("status-process-unavailable");
        String nonce=UUID.randomUUID().toString().replace("-","").substring(0,16);
        // System-context Settings access raised SecurityException on tested MIUI.
        // Use the root settings CLI's own attribution to read the same counter
        // with a bound, rather than assuming app_process has app permissions.
        String bootValue=command(2,"settings","get","global","boot_count").trim();
        if(!bootValue.matches("[0-9]{1,9}"))throw new IOException("status-boot-unavailable");
        int boot=Integer.parseInt(bootValue);
        long began=SystemClock.elapsedRealtime();
        String result=command(3,"content","call","--uri","content://"+pkg+".status","--method","status","--arg",channel+":"+slot+":"+sub+":"+nonce);
        if(result.length()>16384)throw new IOException("status-too-large");
        Matcher snapshot=Pattern.compile("snapshot=(\\{[^\\r\\n]*\\})\\}\\]").matcher(result);
        if(!snapshot.find())throw new IOException("status-unavailable");
        JSONObject clean=TelemetrySnapshot.validate(new JSONObject(snapshot.group(1)),channel,slot,sub,nonce,Integer.parseInt(pid),boot,began,SystemClock.elapsedRealtime());
        if(!pid.equals(command(2,"pidof",pkg).trim()))throw new IOException("status-process-changed");
        return clean;
    }
    private static String smsDispatcherObservation(String pid,int slot)throws Exception{
        // Android11 filter specifications tokenize spaces inside OEM tag names.
        // Stream the current PID instead and retain only matching status fields;
        // raw phone logs, message text and identities are never buffered/exported.
        Pattern state=Pattern.compile("^\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}:\\d{2}\\.\\d{3}\\s+"+pid+
            "\\s+\\d+\\s+[VDIWEF]\\s+ImsSmsDispatcher \\["+slot+"\\]:\\s+isAvailable: up=(true|false), reg=\\s*(true|false), cap=\\s*(true|false)");
        java.lang.Process process=new ProcessBuilder("logcat","-b","all","-d","-v","threadtime","--pid="+pid).redirectErrorStream(true).start();
        java.util.concurrent.atomic.AtomicReference<String> latest=new java.util.concurrent.atomic.AtomicReference<>();
        Thread reader=new Thread(()->{
            try(BufferedReader lines=new BufferedReader(new InputStreamReader(process.getInputStream(),"UTF-8"))){
                String line;
                while((line=lines.readLine())!=null){
                    Matcher match=state.matcher(line);
                    if(match.find())latest.set("up="+match.group(1)+" reg="+match.group(2)+" sms="+match.group(3));
                }
            }catch(IOException ignored){}
        });
        reader.setDaemon(true);reader.start();
        if(!process.waitFor(4,TimeUnit.SECONDS)){process.destroyForcibly();reader.join(500);throw new TimeoutException();}
        reader.join(500);
        if(reader.isAlive())throw new TimeoutException();
        if(process.exitValue()!=0)throw new IOException("logcat-failed");
        return latest.get();
    }
    /** 1 selected, 0 another subscription, -1 attribution unavailable. */
    private static int networkAttribution(NetworkCapabilities caps,int sub){
        NetworkSpecifier spec=caps.getNetworkSpecifier();
        if(spec instanceof TelephonyNetworkSpecifier)return ((TelephonyNetworkSpecifier)spec).getSubscriptionId()==sub&&sub>=0?1:0;
        // Public in API35 / U extension12; older builds may expose the hidden
        // method. Permission-redacted/absent sets remain unattributed.
        if(Build.VERSION.SDK_INT>=31)try{
            Object ids=NetworkCapabilities.class.getMethod("getSubscriptionIds").invoke(caps);
            if(ids instanceof Set&&!((Set<?>)ids).isEmpty())return sub>=0&&((Set<?>)ids).contains(sub)?1:0;
        }catch(ReflectiveOperationException ignored){}
        return -1;
    }
    private static String errorName(Throwable t){
        if(t instanceof ExecutionException&&t.getCause()!=null)t=t.getCause();
        if(t instanceof java.lang.reflect.InvocationTargetException&&t.getCause()!=null)t=t.getCause();
        return t.getClass().getSimpleName();
    }
    private static String observation(JSONObject out,String key,int seconds,String...args)throws Exception{
        try{return command(seconds,args);}catch(Throwable t){out.put(key,errorName(t));return "";}
    }
    private static JSONObject controller(JSONObject out,int slot,int sub)throws Exception{
        if(!new File(CONTROLLER).isFile()){out.put("controller",false);return null;}
        String status=command(8,"sh",CONTROLLER,"status",Integer.toString(slot),Integer.toString(sub));
        JSONObject provider=new JSONObject();
        for(String line:status.split("[\r\n]+")){
            int equals=line.indexOf('=');
            if(equals>0){String key=line.substring(0,equals);
                if(key.equals("mode")||key.equals("transaction")||key.equals("persistent")||key.equals("components")||key.equals("component_selection")||key.equals("persistent_component_selection")||key.equals("identity_selection")||key.equals("owner_slot")||key.equals("owner_sub")||key.equals("owner_schema")||key.equals("multi_transaction_selection")||key.equals("migration_required")||key.matches("active_owner_(?:count|[0-7]_(?:slot|sub))")||key.startsWith("carrier_")||key.equals("config_ims_mmtel_package_override_string"))provider.put(key,line.substring(equals+1));
                if(key.equals("carrier-operation-failed"))provider.put("carrier_read_error",line.substring(equals+1));
            }
        }
        if(!provider.has("mode"))throw new IOException("controller-status-unavailable");
        if(!provider.has("config_ims_mmtel_package_override_string")&&!provider.has("carrier_read_error"))throw new IOException("controller-provider-status-unavailable");
        if(!provider.has("transaction"))provider.put("transaction","INACTIVE");
        // The only old controller format was fixed to slot1/sub1. Report that
        // provenance explicitly; never infer the owner from the selected SIM.
        if("ACTIVE".equals(provider.optString("transaction"))&&!provider.has("identity_selection"))provider.put("owner_slot",1).put("owner_sub",1).put("owner_schema","LEGACY_FIXED");
        org.json.JSONArray owners=new org.json.JSONArray();
        int count=provider.optInt("active_owner_count",0);
        if(count<0||count>8)throw new IOException("controller-owners-unavailable");
        for(int index=0;index<count;index++){
            int ownerSlot=provider.optInt("active_owner_"+index+"_slot",-1),ownerSub=provider.optInt("active_owner_"+index+"_sub",-1);
            if(ownerSlot<0||ownerSlot>7||ownerSub<0)throw new IOException("controller-owner-invalid");
            owners.put(new JSONObject().put("slot",ownerSlot).put("sub",ownerSub));
        }
        if(count==0&&"ACTIVE".equals(provider.optString("transaction")))owners.put(new JSONObject().put("slot",provider.optInt("owner_slot",-1)).put("sub",provider.optInt("owner_sub",-1)));
        provider.put("active_owners",owners);
        out.put("controller",true).put("providers",provider);
        if(provider.has("owner_slot"))out.put("providers_slot",provider.optInt("owner_slot"));
        return provider;
    }
    private static String command(int seconds,String...args)throws Exception{
        java.lang.Process p=new ProcessBuilder(args).redirectErrorStream(true).start();
        ByteArrayOutputStream buffer=new ByteArrayOutputStream();
        Thread reader=new Thread(()->{try(InputStream in=p.getInputStream()){byte[] b=new byte[4096];int n;while((n=in.read(b))>=0){if(buffer.size()+n<262144)buffer.write(b,0,n);}}catch(IOException ignored){}});
        reader.setDaemon(true);reader.start();
        if(!p.waitFor(seconds,TimeUnit.SECONDS)){p.destroyForcibly();reader.join(500);throw new TimeoutException();}
        reader.join(500);
        if(reader.isAlive())throw new TimeoutException();
        if(p.exitValue()!=0)throw new IOException("status-command-failed");
        return buffer.toString("UTF-8");
    }
}
