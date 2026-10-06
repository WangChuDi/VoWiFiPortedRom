// SPDX-License-Identifier: GPL-2.0
package dev.codex.vowifi.tool;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.*;
import android.telephony.*;
import android.view.View;
import android.widget.*;
import org.json.JSONObject;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** Standalone front end. Read-only checks and replacement actions are separate. */
public final class MainActivity extends Activity {
    private static final String CONTROL="/data/adb/modules/codex_vowifi_stack_api30/control.sh";
    private LinearLayout body,results;
    private Spinner sims;
    private Spinner recoveries;
    private final ArrayList<int[]> recoveryOwners=new ArrayList<>();
    private TextView summary,actionStatus;
    private final ArrayList<Integer> slots=new ArrayList<>();
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private JSONObject last;
    private String[] rootPrefix;
    private boolean busy;
    private Button trial,enable,reload,rollback,install;
    private final CheckBox[] components=new CheckBox[3];
    public void onCreate(Bundle saved){
        super.onCreate(saved);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);
        body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(20),dp(30),dp(20),dp(25));
        body.setBackgroundColor(Color.rgb(244,247,252));scroll.addView(body);setContentView(scroll);
        TextView title=text("VoWiFi 工具",28);body.addView(title);
        body.addView(text("按 SIM 检查网络、IMS 和短信能力",15));
        sims=new Spinner(this);body.addView(sims);
        sims.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            public void onItemSelected(android.widget.AdapterView<?> p,View v,int position,long id){last=null;setActions(false);if(results!=null)results.removeAllViews();if(summary!=null)summary.setText("SIM 已选择，请运行只读检查。");}
            public void onNothingSelected(android.widget.AdapterView<?> p){}
        });
        summary=text("先选择 SIM，再运行只读检查。需要在 Magisk 中允许本应用使用 root。",14);body.addView(summary);
        body.addView(button("只读检查链路",v->diagnose()));
        results=new LinearLayout(this);results.setOrientation(LinearLayout.VERTICAL);body.addView(results);
        body.addView(text("可选替换",21));
        body.addView(text("分别选择要替换的组件，先进行最多 5 分钟的试用，再决定是否保留常驻。未选择的组件沿用原配置；修改组合前先恢复当前事务。不同组合需检查实际通话和短信。替换引擎目前限 Android 11 / raphael / VOXI；每张卡分别保存配置，共享电话服务仅在空闲时重载。双卡同时注册仍需实机验证。",14));
        String[] labels={"替换 IWLAN（数据和网络服务）","替换 QNS（接入网络选择）","替换 IMS（通话和系统短信）"};
        for(int i=0;i<components.length;i++){
            components[i]=new CheckBox(this);components[i].setText(labels[i]);components[i].setChecked(true);body.addView(components[i]);
            components[i].setOnCheckedChangeListener((view,checked)->refreshTrialSelection());
        }
        actionStatus=text("运行检查后开放适配的操作。",14);body.addView(actionStatus);
        trial=button("试用所选组件 · 最多 5 分钟自动回退",v->action("trial"));body.addView(trial);
        enable=button("保留当前试验并常驻",v->action("enable"));body.addView(enable);
        reload=button("重新拉起 · 空闲时重载电话服务",v->action("reload"));body.addView(reload);
        body.addView(text("选择要恢复原配置的 SIM",14));
        recoveries=new Spinner(this);body.addView(recoveries);
        recoveries.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            public void onItemSelected(android.widget.AdapterView<?> p,View v,int position,long id){refreshRecovery();}
            public void onNothingSelected(android.widget.AdapterView<?> p){refreshRecovery();}
        });
        rollback=button("恢复原来的组件和短信模块",v->action("rollback"));body.addView(rollback);
        install=button("安装配套模块更新 · 需要重启",v->installModule());body.addView(install);
        body.addView(text("安装更新后，请通过手机电源菜单重启；检查按钮不会拨号、发送短信或读取短信正文。",13));
        body.addView(button("刷新检查结果",v->diagnose()));
        setActions(false);loadSims();
        if(checkSelfPermission(Manifest.permission.READ_PHONE_STATE)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.READ_PHONE_STATE},1);
    }
    private TextView text(String value,int size){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(Color.rgb(27,42,62));t.setPadding(0,dp(8),0,dp(8));return t;}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private Button button(String label,View.OnClickListener click){Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setOnClickListener(click);return b;}
    private void loadSims(){
        slots.clear();ArrayList<String> labels=new ArrayList<>();
        TelephonyManager tm=getSystemService(TelephonyManager.class);
        int count=Math.max(1,tm.getPhoneCount());
        SubscriptionManager sm=getSystemService(SubscriptionManager.class);
        List<SubscriptionInfo> active=null;
        try{active=sm.getActiveSubscriptionInfoList();}catch(SecurityException ignored){}
        for(int slot=0;slot<count;slot++){
            slots.add(slot);String label="SIM"+(slot+1);
            SubscriptionInfo info=null;
            if(active!=null)for(SubscriptionInfo candidate:active)if(candidate.getSimSlotIndex()==slot){info=candidate;break;}
            label+=checkSelfPermission(Manifest.permission.READ_PHONE_STATE)!=PackageManager.PERMISSION_GRANTED?" · 等待读取权限":info==null?" · 无活动卡":" · "+info.getCarrierName();
            labels.add(label);
        }
        sims.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,labels));
        if(count>1)sims.setSelection(1);
    }
    public void onRequestPermissionsResult(int request,String[] permissions,int[] granted){super.onRequestPermissionsResult(request,permissions,granted);loadSims();}
    private String quote(String s){return "'"+s.replace("'","'\\''")+"'";}
    private void diagnose(){
        if(busy)return;
        final int slot=slots.get(sims.getSelectedItemPosition());
        execute("正在检查所选 SIM…",()->readDiagnostic(slot,45),this::render);
    }
    private JSONObject readDiagnostic(int slot,int seconds)throws Exception{
        String command="CLASSPATH="+quote(getApplicationInfo().sourceDir)+" timeout "+Math.min(40,seconds-2)+"s app_process /system/bin dev.codex.vowifi.tool.RootDiagnostics "+slot;
        String response=shell(command,seconds);
        String json=null;for(String line:response.split("[\\r\\n]+"))if(line.startsWith("{")&&line.endsWith("}"))json=line;
        if(json==null)throw new IOException("diagnostic-result-unavailable");
        return new JSONObject(json);
    }
    private void status(String message){summary.setText(message);if(actionStatus!=null)actionStatus.setText(message);}
    private void render(JSONObject data){
        last=data;results.removeAllViews();
        if(data.has("error")&&!data.has("sdk")){status("检查未完成："+data.optString("error")+"。请核对 root 授权及系统接口。");setActions(false);return;}
        status("Android API "+data.optInt("sdk")+" · SIM"+(data.optInt("slot")+1)+" · 只读结果");
        StringBuilder unavailable=new StringBuilder();
        for(String key:new String[]{"error","bootstrap_error","controller_error","subscription_error","telephony_error","network_error","apn_error","settings_error","policy_error","provisioning_error","ims_error","iwlan_observation_error","sms_observation_error","iwlan_status_error","qns_status_error","ims_status_error"})
            if(data.has(key)){if(unavailable.length()>0)unavailable.append("\n");unavailable.append(key).append(": ").append(data.optString(key));}
        if(unavailable.length()>0)card("未完成的检查 · 其余可用结果保留",unavailable.toString());
        card("SIM",data.optString("sim","未知")+" / "+data.optString("operator",""));
        JSONObject abi=data.optJSONObject("runtime_abi");
        if(abi!=null)card("替换接口预检查", "核心接口可见 "+abi.optInt("visible")+"/"+abi.optInt("total")+
            " · 缺失 "+abi.optInt("missing")+" · 访问受限 "+abi.optInt("inaccessible")+" · 加载错误 "+abi.optInt("linkage_errors")+
            "\n"+(abi.optBoolean("modern_candidate")?"属于 Android12–17 候选范围；尚未验证安装与服务绑定":abi.optInt("sdk")==30?"Android11 接口范围；替换需匹配已验证设备":"超出当前替换版本范围")+
            "\n只检查当前 root 进程中的接口；未验证权限、运营商注册或现代模块可用性。独立 IKE 库也可能仅在服务进程可见。");
        card("实体 Wi-Fi",data.has("network_error")?"网络检查不完整 · "+data.optString("network_error"):data.optString("wifi","未观测"));
        card("ePDG DNS",data.optString("dns","未观测"));
        card("UDP / IKE",data.optString("udp","未观测")+"\n"+data.optString("ike","不可见"));
        JSONObject qns=data.optJSONObject("qns_status");
        if(qns!=null)card("检查时的 QNS 选择",qns.optBoolean("observed")?"所选 SIM · "+phaseName(qns.optString("phase")):"未观测到所选 SIM 的有效实例");
        JSONObject iwlan=data.optJSONObject("iwlan_status");
        if(iwlan!=null&&iwlan.optBoolean("observed")){
            String detail="会话代次 "+iwlan.optLong("generation")+" · "+phaseName(iwlan.optString("phase"));
            if(iwlan.optBoolean("failed"))detail+="\n失败前阶段："+phaseName(iwlan.optString("failed_stage"));
            if(iwlan.optBoolean("child_open"))detail+="\n当前地址 "+iwlan.optInt("address_count")+" · P-CSCF "+iwlan.optInt("pcscf_count")+" · 接口归属匹配 "+data.optBoolean("iwlan_interface_matches");
            card("检查时的 IWLAN 会话",detail+"\n服务采样状态；不保证运营商此刻仍能传输数据");
        }
        else if(iwlan!=null)card("检查时的 IWLAN 会话","未观测到所选 SIM 的有效实例");
        card("首选互联网 APN",data.optString("apn","不可见"));
        card("Wi-Fi Calling 设置",data.has("wfc_setting")?"开启 "+data.optBoolean("wfc_setting")+" · 漫游开关 "+(data.has("wfc_roaming_setting")?data.optBoolean("wfc_roaming_setting"):"未知")+" · 偏好模式 "+(data.has("wfc_mode")?data.optInt("wfc_mode"):"未知"):"不可见 · "+data.optString("settings_error","未知"));
        card("WLAN 语音 provisioning",data.has("wlan_voice_provisioned")?Boolean.toString(data.optBoolean("wlan_voice_provisioned"))+"（框架配置结果，不代表运营商已接受注册）":"不可见 · "+data.optString("provisioning_error","未知"));
        JSONObject policy=data.optJSONObject("selected_policy");
        if(policy!=null)card("所选 SIM 的运营商策略",policy.toString());
        int networks=data.optInt("ims_network_count");
        card("所选 SIM 的 IMS 网络",data.has("network_error")?"不可见 · "+data.optString("network_error"):networks+" 个 · 接口 "+(data.isNull("ims_interface")?"未观测":data.optString("ims_interface","不可见"))+" · P-CSCF "+data.optInt("pcscf_count")+(data.optInt("ims_unattributed_network_count")>0?"\n另有 "+data.optInt("ims_unattributed_network_count")+" 个 IMS 网络无法归属到卡槽":"")+(data.has("ims_interface_present")?"\n内核接口存在 "+data.optBoolean("ims_interface_present"):"")+(data.has("iwlan_process_present")?" · IWLAN 进程运行 "+data.optBoolean("iwlan_process_present"):""));
        int t=data.optInt("ims_transport",-1);
        String registration=t==2?"WLAN 已注册":t==1?"蜂窝网络已注册":t==-2?"注册中":t==-3?"未注册":"不可见 / 尚未回调";
        card("IMS 注册",data.has("ims_error")?"不可见 · "+data.optString("ims_error"):registration);
        card("MMTEL 能力",data.optBoolean("cap_observed")?"语音 "+data.optBoolean("voice")+" · SMS "+data.optBoolean("sms"):"尚未观测，不能判定不可用");
        JSONObject service=data.optJSONObject("ims_status");
        if(service!=null&&service.optBoolean("observed")){
            card("检查时的 IMS 实例","会话代次 "+service.optLong("generation")+" · "+phaseName(service.optString("phase"))+"\n本代 REGISTER 发送 "+service.optInt("register_tx")+" · 最近响应 "+service.optInt("sip_status"));
            card("本代系统短信投递观测","IMS 收到 "+service.optInt("sms_rx")+" · 系统确认成功 "+service.optInt("sms_ack_ok")+" · 系统拒绝 "+service.optInt("sms_ack_failed")+"\n发送请求 "+service.optInt("sms_tx")+" · 网络确认成功 "+service.optInt("sms_tx_ok")+" · 发送失败 "+service.optInt("sms_tx_failed")+"\n累计元数据，不代表下一条短信一定成功，也不证明已显示通知");
            card("本代语音媒体观测","已发送 RTP 帧 "+service.optInt("voice_tx_frames")+" · 已交给音频播放的帧 "+service.optInt("voice_played_frames")+"\n累计观测；需要实际通话验证听感");
        }
        else if(service!=null)card("检查时的 IMS 实例","未观测到所选 SIM 的有效实例");
        card("最近一次系统短信分发",data.optString("sms_dispatcher","未观测"));
        JSONObject providers=data.optJSONObject("providers");
        int ownerSlot=providers==null?-1:providers.optInt("owner_slot",-1);
        card("替换控制器"+(ownerSlot>=0?"（管理 SIM"+(ownerSlot+1)+"）":""),providers==null?(data.has("controller_error")?"状态读取失败 · "+data.optString("controller_error"):"未加载配套模块"):providers.optString("mode")+" · "+providers.optString("persistent","未常驻")+"\n"+("ACTIVE".equals(providers.optString("transaction"))?"替代组件："+componentNames(providers.optInt("components",7)):"尚未启动替换"));
        setActions(data.optBoolean("engine_supported"));
        boolean active=providers!=null&&"ACTIVE".equals(providers.optString("transaction"));
        boolean identities=providers!=null&&providers.optInt("identity_selection")==1;
        boolean selectedOwner=providers!=null&&ownerSlot==data.optInt("slot",-1)&&providers.optInt("owner_sub",-1)==data.optInt("sub_id",-2);
        boolean selectable=identities&&providers.optInt("component_selection")==1;
        for(CheckBox checkbox:components){checkbox.setEnabled(data.optBoolean("engine_supported")&&selectable&&!active);}
        int activeMask=providers==null?-1:providers.optInt("components",-1);
        if(active&&activeMask>=1&&activeMask<=7)for(int index=0;index<components.length;index++)components[index].setChecked((activeMask&(1<<index))!=0);
        if(!selectable)for(CheckBox checkbox:components)checkbox.setChecked(true);
        refreshTrialSelection();
        boolean canRetain=activeMask>=1&&activeMask<=7&&(activeMask==7||providers.optInt("persistent_component_selection")==1);
        enable.setEnabled(identities&&selectedOwner&&data.optBoolean("engine_supported")&&active&&canRetain&&!"ENABLED".equals(providers.optString("persistent")));
        reload.setEnabled(identities&&selectedOwner&&data.optBoolean("engine_supported")&&active);
        recoveryOwners.clear();ArrayList<String> recoveryLabels=new ArrayList<>();int recoveryIndex=0;
        org.json.JSONArray owners=providers==null?null:providers.optJSONArray("active_owners");
        if(owners!=null)for(int index=0;index<owners.length();index++){
            JSONObject owner=owners.optJSONObject(index);if(owner==null)continue;
            int recoverySlot=owner.optInt("slot",-1),recoverySub=owner.optInt("sub",-1);
            if(recoverySlot<0||recoverySlot>7||recoverySub<0)continue;
            if(recoverySlot==data.optInt("slot",-1))recoveryIndex=recoveryOwners.size();
            recoveryOwners.add(new int[]{recoverySlot,recoverySub});recoveryLabels.add("SIM"+(recoverySlot+1));
        }
        if(recoveryLabels.isEmpty())recoveryLabels.add("无待恢复事务");
        recoveries.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,recoveryLabels));
        recoveries.setSelection(recoveryIndex);refreshRecovery();
        if(recoveryOwners.size()>1)card("其他卡的替换事务",recoveryOwners.size()+" 张卡分别管理；恢复所选事务后，其他卡的配置和租约保留。电话服务重载会短暂重建两张卡的连接。");
        if(data.has("error")){setActions(false);status("检查未完成，已保留部分结果。请刷新检查后再操作。");}
    }
    private String phaseName(String phase){
        switch(phase){
            case "STARTING":return "初始化";case "WAITING_SIM":return "检查 SIM";
            case "WAITING_NETWORK":return "等待 Wi-Fi";case "DNS":return "解析 ePDG";
            case "IKE_PARAMETERS":return "准备 IKE 参数";case "IKE_NEGOTIATING":return "IKE 协商中";
            case "IKE_AUTHENTICATED":return "IKE 鉴权通过";case "CHILD_OPENED":return "IPsec 隧道已建立";
            case "IWLAN_SELECTED":return "已选择 IWLAN";case "NO_IWLAN":return "未选择 IWLAN";
            case "REGISTERING":return "IMS 注册中";case "REGISTERED":return "IMS 已注册";
            case "DOWN":return "IMS 未注册";case "FAILED":return "失败";case "CLOSED":return "已关闭";
            default:return "未知";
        }
    }
    private void card(String heading,String value){
        LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(14),dp(4),dp(14),dp(10));c.setBackgroundColor(Color.WHITE);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(9),0,0);c.setLayoutParams(p);
        c.addView(text(heading,16));TextView detail=text(value,14);detail.setTextIsSelectable(true);c.addView(detail);results.addView(c);
    }
    private void setActions(boolean supported){
        if(trial==null)return;trial.setEnabled(supported);enable.setEnabled(false);reload.setEnabled(false);rollback.setEnabled(false);install.setEnabled(supported);
        for(CheckBox checkbox:components)if(checkbox!=null)checkbox.setEnabled(false);
        if(recoveries!=null)recoveries.setEnabled(false);
    }
    private void refreshRecovery(){
        if(rollback==null||recoveries==null)return;
        int index=recoveries.getSelectedItemPosition();boolean available=!busy&&last!=null&&index>=0&&index<recoveryOwners.size();
        recoveries.setEnabled(!busy&&!recoveryOwners.isEmpty());rollback.setEnabled(available);
        rollback.setText(available?"恢复 SIM"+(recoveryOwners.get(index)[0]+1)+" 原来的组件和短信模块":"恢复原来的组件和短信模块");
    }
    private int componentMask(){int mask=0;for(int i=0;i<components.length;i++)if(components[i].isChecked())mask|=1<<i;return mask;}
    private String componentNames(int mask){ArrayList<String> names=new ArrayList<>();if((mask&1)!=0)names.add("IWLAN");if((mask&2)!=0)names.add("QNS");if((mask&4)!=0)names.add("IMS");return String.join(" + ",names);}
    private void refreshTrialSelection(){
        if(trial==null)return;
        JSONObject provider=last==null?null:last.optJSONObject("providers");
        trial.setEnabled(!busy&&last!=null&&last.optBoolean("engine_supported")&&last.optBoolean("controller")&&provider!=null&&provider.optInt("identity_selection")==1&&!"ACTIVE".equals(provider.optString("transaction"))&&componentMask()!=0);
    }
    private void action(String action){
        if(busy)return;
        if(!Arrays.asList("trial","enable","reload","rollback").contains(action))return;
        if(last==null)return;
        if(!"rollback".equals(action)&&(last==null||!last.optBoolean("engine_supported")))return;
        final int slot=slots.get(sims.getSelectedItemPosition());
        JSONObject provider=last.optJSONObject("providers");
        if(provider==null)return;
        if(!"rollback".equals(action)&&(last.optInt("slot",-1)!=slot||provider.optInt("identity_selection")!=1))return;
        int recoveryIndex=recoveries.getSelectedItemPosition();
        if("rollback".equals(action)&&(recoveryIndex<0||recoveryIndex>=recoveryOwners.size()))return;
        final int targetSlot="rollback".equals(action)?recoveryOwners.get(recoveryIndex)[0]:slot;
        final int targetSub="rollback".equals(action)?recoveryOwners.get(recoveryIndex)[1]:last.optInt("sub_id",-1);
        if(targetSlot<0||targetSlot>7||targetSub<0)return;
        if(("enable".equals(action)||"reload".equals(action))&&(provider.optInt("owner_slot",-1)!=targetSlot||provider.optInt("owner_sub",-1)!=targetSub))return;
        final int mask=componentMask();
        if("trial".equals(action)&&mask==0)return;
        execute("正在执行所选操作…",()->{
            String output=shell("sh "+CONTROL+" "+action+("trial".equals(action)?" "+mask:"")+" "+targetSlot+" "+targetSub,35);
            if(!action.equals("reload")&&!action.equals("trial"))return new JSONObject().put("action_result",output);
            runOnUiThread(()->{if(!isFinishing())status("已请求重新拉起，正在等待 IMS 注册…");});
            long deadline=SystemClock.elapsedRealtime()+60000;
            JSONObject observation=new JSONObject();
            while(SystemClock.elapsedRealtime()<deadline-5000){
                Thread.sleep(2500);
                int remaining=(int)((deadline-SystemClock.elapsedRealtime())/1000);
                // A complete diagnostic has independent bounded DNS, Binder
                // and callback waits. Do not launch one with a truncated budget.
                if(remaining<45)break;
                try{observation=readDiagnostic(slot,45);}
                catch(Exception checkError){
                    // The controller already accepted the action. A short final
                    // polling deadline must not misreport it as a failed switch.
                    observation.put("action_observation_error",checkError.getClass().getSimpleName());
                    observation.remove("ims_transport");observation.remove("cap_observed");observation.remove("voice");observation.remove("sms");
                    break;
                }
                if(observation.optInt("ims_transport")==2&&observation.optBoolean("voice")&&observation.optBoolean("sms"))break;
            }
            return observation.put("action_result",output);
        },data->{
            last=null;setActions(false);
            if(data.has("sdk")){render(data);status(data.optInt("ims_transport")==2?"已恢复 WLAN 注册；请查看下方最新能力。":"操作已请求，尚未确认 WLAN 注册；请刷新检查结果。"+(data.has("action_observation_error")?"\n等待检查未完成："+data.optString("action_observation_error"):""));}
            else{status(data.optString("action_result"));diagnose();}
        });
    }
    private void installModule(){
        if(busy||last==null||!last.optBoolean("engine_supported"))return;
        execute("正在安装配套模块更新…",()->{
            File zip=new File(getCacheDir(),"vowifi-stack-api30-services.zip");
            try(InputStream in=getAssets().open("vowifi-stack-api30-services.zip");OutputStream out=new FileOutputStream(zip)){byte[] b=new byte[8192];int n;while((n=in.read(b))>=0)out.write(b,0,n);}
            try{return new JSONObject().put("action_result",shell("/product/bin/magisk --install-module "+quote(zip.getAbsolutePath()),30));}
            finally{zip.delete();}
        },data->{status(data.optString("action_result")+"\n安装成功后请手动重启，再运行只读检查。");setActions(false);});
    }
    private interface Work{JSONObject run()throws Exception;}
    private interface Show{void run(JSONObject data);}
    private void execute(String message,Work work,Show show){
        busy=true;status(message);sims.setEnabled(false);setActions(false);
        worker.submit(()->{JSONObject response;try{response=work.run();}catch(Throwable e){response=new JSONObject();try{response.put("error",e.getClass().getSimpleName());if(e instanceof RootFailure)response.put("action_error",e.getMessage());}catch(Exception ignored){}}
            final JSONObject result=response;runOnUiThread(()->{busy=false;sims.setEnabled(true);if(!isFinishing()){if(result.has("error")){last=null;setActions(false);status("操作未完成："+result.optString("error")+(result.has("action_error")?"\n"+result.optString("action_error"):"")+"。请重新运行只读检查，核对事务状态和 Magisk 授权；超时不代表已回退。");}else show.run(result);}});
        });
    }
    private String shell(String command,int seconds)throws Exception{
        // MIUI app-data isolation can survive a UID change. Use the root
        // implementation's advertised global mount namespace option so backups
        // refer to the same phone-service files as ADB and boot supervision.
        if(rootPrefix==null){
            java.lang.Process help=new ProcessBuilder("su","--help").redirectErrorStream(true).start();
            if(!help.waitFor(3,TimeUnit.SECONDS)){help.destroyForcibly();throw new TimeoutException();}
            ByteArrayOutputStream buffer=new ByteArrayOutputStream();
            try(InputStream in=help.getInputStream()){byte[] bytes=new byte[1024];int n;while((n=in.read(bytes))>=0){if(buffer.size()+n>16384)throw new IOException("root-help-too-large");buffer.write(bytes,0,n);}}
            String options=buffer.toString("UTF-8");
            rootPrefix=options.contains("--target PID")&&options.contains("global mount namespace")?new String[]{"su","-t","0","-c"}:options.contains("--mount-master")?new String[]{"su","--mount-master","-c"}:new String[]{"su","-c"};
        }
        ArrayList<String> arguments=new ArrayList<>(Arrays.asList(rootPrefix));arguments.add(command);
        java.lang.Process process=new ProcessBuilder(arguments).redirectErrorStream(true).start();
        ByteArrayOutputStream output=new ByteArrayOutputStream();
        Thread reader=new Thread(()->{try(InputStream in=process.getInputStream()){byte[] b=new byte[4096];int n;while((n=in.read(b))>=0){if(output.size()+n<524288)output.write(b,0,n);}}catch(IOException ignored){}});reader.start();
        if(!process.waitFor(seconds,TimeUnit.SECONDS)){process.destroyForcibly();throw new TimeoutException();}
        reader.join(1000);
        if(process.exitValue()!=0){
            StringBuilder reason=new StringBuilder("exit="+process.exitValue());
            for(String line:output.toString("UTF-8").split("[\\r\\n]+"))if(line.matches("(?:carrier-operation-failed|carrier-error-line|profile|provider-[a-z-]+|trial|rollback|worker)=[A-Za-z0-9_./-]+|(?:invalid-selection|owner-selection-mismatch|invalid-components|stale-baseline-refused|stale-provider-snapshot-refused|persist-format-unverified|partial-components-trial-only|transaction-already-active|controller-busy|active-call-refused|call-state-unavailable|provider-[a-z-]+-failed|trial-gate-failed)"))reason.append("\n").append(line);
            throw new RootFailure(reason.toString());
        }
        return output.toString("UTF-8").trim();
    }
    private static final class RootFailure extends IOException{private static final long serialVersionUID=1L;RootFailure(String details){super(details);}}
    protected void onDestroy(){worker.shutdownNow();super.onDestroy();}
}
