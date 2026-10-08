// SPDX-License-Identifier: GPL-2.0
package dev.codex.vowifi.tool;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.*;
import android.telephony.*;
import android.view.View;
import android.view.Gravity;
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
    private Button trial,enable,reload,rebind,rollback,install;
    private final CheckBox[] components=new CheckBox[3];
    private MaterialTheme theme;
    private LinearLayout overview,operations,detailGroup;
    private ScrollView scroll;
    private ProgressBar progress;
    private TextView connectionTitle,connectionDetail;
    private Button check,networkCheck;
    private final Button[] navigation=new Button[3];
    private int selectedPage;
    public void onCreate(Bundle saved){
        super.onCreate(saved);
        theme=new MaterialTheme(this);
        getWindow().setStatusBarColor(theme.background);getWindow().setNavigationBarColor(theme.surface);
        getWindow().getDecorView().setSystemUiVisibility(theme.dark?0:View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        LinearLayout root=column();root.setBackgroundColor(theme.background);
        scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setClipToPadding(false);
        body=column();body.setPadding(dp(20),dp(16),dp(20),dp(24));scroll.addView(body);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout nav=new LinearLayout(this);nav.setPadding(dp(12),dp(8),dp(12),dp(10));nav.setBackgroundColor(theme.surface);
        String[] pages={"概览","诊断","操作"};
        for(int i=0;i<3;i++){final int page=i;navigation[i]=button(pages[i],v->selectPage(page));navigation[i].setContentDescription(pages[i]+"页面");LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(52),1);p.setMargins(dp(4),0,dp(4),0);nav.addView(navigation[i],p);}
        root.addView(nav);setContentView(root);
        TextView title=text("VoWiFi",30);theme.typography(title,30,true);body.addView(title);
        TextView subtitle=text("让每一步连接都清楚可见",14);subtitle.setTextColor(theme.secondary);body.addView(subtitle);
        body.addView(text("当前检查的 SIM",12));sims=new Spinner(this);sims.setMinimumHeight(dp(52));theme.surface(sims,theme.container,16);body.addView(sims);
        sims.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            public void onItemSelected(android.widget.AdapterView<?> p,View v,int position,long id){last=null;setActions(false);if(results!=null)results.removeAllViews();if(summary!=null)status("SIM 已选择，请运行只读检查。");resetOverview();}
            public void onNothingSelected(android.widget.AdapterView<?> p){}
        });
        summary=text("选择 SIM 后检查。请在 Magisk 中允许本应用使用 root。",13);summary.setTextColor(theme.secondary);summary.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);body.addView(summary);
        progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setIndeterminate(true);progress.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(theme.primary));progress.setVisibility(View.GONE);body.addView(progress,new LinearLayout.LayoutParams(-1,dp(4)));
        check=button("检查当前链路",v->diagnose());theme.button(check,true);body.addView(check);
        networkCheck=button("网络检测",v->diagnoseNetwork());body.addView(networkCheck);
        overview=column();results=column();operations=column();body.addView(overview);body.addView(results);body.addView(operations);
        TextView operationsTitle=text("管理替换组件",22);theme.typography(operationsTitle,22,true);operations.addView(operationsTitle);
        operations.addView(text("先试用最多 5 分钟，确认后保留常驻。修改组件组合前，请恢复当前事务。",14));
        operations.addView(text("首次替换需要安装并启用配套 Magisk 模块，重启后才能切换组件。模块部署程序和系统权限；root 执行切换与恢复。之后可在运行中试用组合，但可能重载电话服务、短暂断开连接。",13));
        operations.addView(text("Android 11：raphael / VOXI 已进行实机验证，短信收发仍有未解决的问题。Android 12–17：实验引擎，当前机型的实际通话、短信及双卡同时注册需分别验证。",13));
        LinearLayout choices=panel(operations,theme.container);choices.addView(text("选择组件",16));
        String[] labels={"替换 IWLAN（数据和网络服务）","替换 QNS（接入网络选择）","替换 IMS（通话和系统短信）"};
        for(int i=0;i<components.length;i++){
            components[i]=new CheckBox(this);components[i].setText(labels[i]);components[i].setTextColor(theme.onSurface);components[i].setTextSize(14);components[i].setMinHeight(dp(52));components[i].setButtonTintList(android.content.res.ColorStateList.valueOf(theme.primary));components[i].setChecked(true);choices.addView(components[i]);
            components[i].setOnCheckedChangeListener((view,checked)->refreshTrialSelection());
        }
        actionStatus=text("运行检查后开放适配的操作。",14);operations.addView(actionStatus);
        trial=button("试用所选组件 · 5 分钟自动回退",v->action("trial"));theme.button(trial,true);operations.addView(trial);
        enable=button("保留当前试验并常驻",v->action("enable"));operations.addView(enable);
        LinearLayout repair=panel(operations,theme.surface);repair.addView(text("恢复连接",18));
        reload=button("重新拉起 · 空闲时重载电话服务",v->action("reload"));repair.addView(reload);
        rebind=button("重新绑定 IMS 客户端 · 空闲时",v->rebindClients());repair.addView(rebind);
        LinearLayout recovery=panel(operations,theme.surface);recovery.addView(text("恢复原配置",18));recovery.addView(text("选择需要恢复的 SIM",14));
        recoveries=new Spinner(this);recoveries.setMinimumHeight(dp(52));recovery.addView(recoveries);
        recoveries.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            public void onItemSelected(android.widget.AdapterView<?> p,View v,int position,long id){refreshRecovery();}
            public void onNothingSelected(android.widget.AdapterView<?> p){refreshRecovery();}
        });
        rollback=button("恢复原来的组件和短信模块",v->action("rollback"));recovery.addView(rollback);
        LinearLayout updates=panel(operations,theme.surface);updates.addView(text("模块与权限",18));
        install=button("安装配套模块更新 · 需要重启",v->installModule());updates.addView(install);
        updates.addView(text("当前后端：root。Shizuku 尚未接入；ADB 模式不能完成本模块的系统组件替换。安装更新后，请通过手机电源菜单重启。",13));
        updates.addView(text("链路检查不拨号、不发短信、不读取短信正文。网络检测会发送少量 IKE 初始探测，不做 SIM 鉴权。APN 密码仅在点击后显示。",13));
        selectPage(saved==null?0:saved.getInt("page",0));resetOverview();
        setActions(false);loadSims();
        if(checkSelfPermission(Manifest.permission.READ_PHONE_STATE)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.READ_PHONE_STATE},1);
    }
    private TextView text(String value,int size){TextView t=new TextView(this);t.setText(value);theme.typography(t,size,size>=18);t.setPadding(0,dp(6),0,dp(6));return t;}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private Button button(String label,View.OnClickListener click){Button b=new Button(this);b.setText(label);theme.button(b,false);b.setOnClickListener(click);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(8),0,0);b.setLayoutParams(p);return b;}
    private LinearLayout column(){LinearLayout layout=new LinearLayout(this);layout.setOrientation(LinearLayout.VERTICAL);return layout;}
    private LinearLayout panel(LinearLayout parent,int color){LinearLayout layout=column();layout.setPadding(dp(18),dp(12),dp(18),dp(14));theme.surface(layout,color,24);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(14),0,0);parent.addView(layout,p);return layout;}
    private void selectPage(int page){selectedPage=Math.max(0,Math.min(2,page));if(overview==null)return;overview.setVisibility(selectedPage==0?View.VISIBLE:View.GONE);results.setVisibility(selectedPage==1?View.VISIBLE:View.GONE);operations.setVisibility(selectedPage==2?View.VISIBLE:View.GONE);for(int i=0;i<3;i++){theme.button(navigation[i],i==selectedPage);navigation[i].setSelected(i==selectedPage);}scroll.post(()->scroll.smoothScrollTo(0,0));}
    protected void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);state.putInt("page",selectedPage);}
    private void resetOverview(){if(overview==null)return;overview.removeAllViews();LinearLayout hero=panel(overview,theme.primaryContainer);connectionTitle=text("等待检查",24);connectionTitle.setTextColor(theme.onPrimaryContainer);hero.addView(connectionTitle);connectionDetail=text("检查后查看 Wi-Fi、隧道、IMS 注册和短信分发状态。",14);connectionDetail.setTextColor(theme.onPrimaryContainer);hero.addView(connectionDetail);overview.addView(text("诊断页提供逐层状态，操作页管理替换与恢复。",14));}
    private void updateOverview(JSONObject data){
        resetOverview();boolean wlan=DiagnosticPolicy.wlanConfirmed(data);connectionTitle.setText(wlan?"Wi-Fi Calling 已注册":data.optInt("ims_transport")==-2?"IMS 正在注册":"尚未确认 Wi-Fi Calling");
        connectionDetail.setText("SIM"+(data.optInt("slot")+1)+" · "+(data.optBoolean("cap_observed")?"语音能力 "+(data.optBoolean("voice")?"可用":"未上报")+" · SMS 能力 "+(data.optBoolean("sms")?"可用":"未上报"):"等待能力观测")+"\n注册与能力是状态检查；实际通话、短信和通知需分别验证。");
        LinearLayout stages=panel(overview,theme.surface);stages.addView(text("连接进度",18));
        overviewRow(stages,"01","Wi-Fi",data.optString("wifi","未观测"));
        JSONObject tunnel=data.optJSONObject("iwlan_status");overviewRow(stages,"02","IWLAN 隧道",tunnel!=null&&tunnel.optBoolean("observed")?phaseName(tunnel.optString("phase")):"未取得有效会话观测");
        overviewRow(stages,"03","IMS 注册",wlan?"本次检查确认 WLAN 注册":"请在诊断页核对注册回调与进程稳定性");
        JSONObject dispatcher=data.optJSONObject("sms_dispatcher_window");overviewRow(stages,"04","系统短信分发器",dispatcher!=null&&"observed".equals(dispatcher.optString("status"))?dispatcher.optBoolean("available")?"本次查询就绪 · 实际收发未由此验证":"本次查询未就绪":"未取得有效观测");
        Button details=button("查看分层诊断",v->selectPage(1));overview.addView(details);
    }
    private void overviewRow(LinearLayout parent,String number,String heading,String value){LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(0,dp(10),0,dp(10));TextView marker=text(number,14);marker.setGravity(Gravity.CENTER);marker.setTextColor(theme.primary);theme.surface(marker,theme.container,16);row.addView(marker,new LinearLayout.LayoutParams(dp(42),dp(42)));LinearLayout copy=column();copy.setPadding(dp(14),0,0,0);TextView label=text(heading,16);theme.typography(label,16,true);copy.addView(label);TextView detail=text(value,13);detail.setTextColor(theme.secondary);copy.addView(detail);row.addView(copy,new LinearLayout.LayoutParams(0,-2,1));parent.addView(row);}
    private void diagnosticSection(String heading,boolean expanded){LinearLayout section=panel(results,theme.container);TextView toggle=text(heading+(expanded?"  −":"  +"),18);toggle.setMinimumHeight(dp(48));section.addView(toggle);LinearLayout content=column();section.addView(content);content.setVisibility(expanded?View.VISIBLE:View.GONE);toggle.setOnClickListener(v->{boolean open=content.getVisibility()!=View.VISIBLE;content.setVisibility(open?View.VISIBLE:View.GONE);toggle.setText(heading+(open?"  −":"  +"));});toggle.setContentDescription(heading+"，点击展开或收起");detailGroup=content;}
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
        sims.setAdapter(spinnerAdapter(labels));
        if(count>1)sims.setSelection(1);
    }
    public void onRequestPermissionsResult(int request,String[] permissions,int[] granted){super.onRequestPermissionsResult(request,permissions,granted);loadSims();}
    private String quote(String s){return "'"+s.replace("'","'\\''")+"'";}
    private void diagnose(){
        if(busy)return;
        final int slot=slots.get(sims.getSelectedItemPosition());
        execute("正在检查所选 SIM…",()->readDiagnostic(slot,45),this::render);
    }
    private void diagnoseNetwork(){
        if(busy)return;final int slot=slots.get(sims.getSelectedItemPosition());
        execute("正在检测所选运营商的 ePDG 与 UDP 500 / 4500…",()->readDiagnostic(slot,45,true),data->{render(data);selectPage(1);});
    }
    private JSONObject readDiagnostic(int slot,int seconds)throws Exception{
        return readDiagnostic(slot,seconds,false);
    }
    private JSONObject readDiagnostic(int slot,int seconds,boolean network)throws Exception{
        String command="CLASSPATH="+quote(getApplicationInfo().sourceDir)+" timeout "+Math.min(40,seconds-2)+"s app_process /system/bin dev.codex.vowifi.tool.RootDiagnostics "+slot+(network?" network":"");
        String response=shell(command,seconds);
        String json=null;for(String line:response.split("[\\r\\n]+"))if(line.startsWith("{")&&line.endsWith("}"))json=line;
        if(json==null)throw new IOException("diagnostic-result-unavailable");
        return new JSONObject(json);
    }
    private void status(String message){summary.setText(message);if(actionStatus!=null)actionStatus.setText(message);}
    private ArrayAdapter<String> spinnerAdapter(List<String> labels){return new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,labels){public View getView(int position,View reuse,android.view.ViewGroup parent){View v=super.getView(position,reuse,parent);if(v instanceof TextView)((TextView)v).setTextColor(theme.onSurface);return v;}public View getDropDownView(int position,View reuse,android.view.ViewGroup parent){View v=super.getDropDownView(position,reuse,parent);v.setBackgroundColor(theme.surface);if(v instanceof TextView){((TextView)v).setTextColor(theme.onSurface);((TextView)v).setMinHeight(dp(48));}return v;}};}
    private void render(JSONObject data){
        last=data;results.removeAllViews();detailGroup=null;resetOverview();
        if(data.has("error")&&!data.has("sdk")){status("检查未完成："+data.optString("error")+"。请核对 root 授权及系统接口。");setActions(false);return;}
        updateOverview(data);
        status("Android API "+data.optInt("sdk")+" · SIM"+(data.optInt("slot")+1)+" · 只读结果");
        if(data.optBoolean("engine_experimental"))card("现代替换引擎 · 实验功能","适配范围 Android 12–17 / VOXI。Android 13 和 16 已进行模拟器生命周期验证；实际设备、运营商通话短信及双卡注册需要分别验证。模块安装后重启，再检查组件链路。");
        reload.setText(data.optBoolean("engine_experimental")?"检查并续租当前替换链路":"重新拉起 · 空闲时重载电话服务");
        StringBuilder unavailable=new StringBuilder();
        for(String key:new String[]{"error","diagnostic_error","bootstrap_error","controller_error","subscription_error","telephony_error","network_error","network_detection_error","apn_error","settings_error","policy_error","provisioning_error","ims_error","native_sms_error","iwlan_observation_error","sms_observation_error","iwlan_status_error","qns_status_error","ims_status_error","ims_clients_status_error"})
            if(data.has(key)){if(unavailable.length()>0)unavailable.append("\n");unavailable.append(key).append(": ").append(data.optString(key));}
        if(unavailable.length()>0)card("未完成的检查 · 其余可用结果保留",unavailable.toString());
        JSONObject health=data.optJSONObject("platform_health");
        card("电话服务进程观测",health==null?"未观测，不能判断进程是否稳定":
            "电话进程："+processState(health.optJSONObject("phone"))+"\n系统服务："+processState(health.optJSONObject("system_server"))+"\n用于发现检查期间电话进程或系统服务重启，避免把旧注册状态当成当前结果；身份一致不代表 IMS 或运营商链路正常。\n只读检查不会主动重载进程。崩溃、系统维护或此前的替换／重载操作可能改变进程；需结合日志才能确定原因。");
        if(!diagnosticReady())card("检查尚未完成","停留阶段："+diagnosticStage(data.optString("diagnostic_stage"))+"。已取得的结果保留；请刷新检查后再开始替换或安装更新。");
        diagnosticSection("设备与系统",false);
        card("SIM",data.optString("sim","未知")+" / "+data.optString("operator",""));
        diagnosticSection("实际服务接口诊断",true);
        card("检查对象与判定","分别在当前选中且已运行的 IWLAN／QNS／IMS 服务进程中查找各自需要的类和方法签名。校验所选 SIM、服务实例及进程身份；接口可见不等于调用成功，实际隧道、注册和短信分发在对应分组单独显示。原厂或旧版服务未提供入口时，保持未确定。");
        for(String role:new String[]{"iwlan","qns","ims"})card(role.toUpperCase(Locale.ROOT)+" · 检查时的实际服务接口",
            !DiagnosticPolicy.complete(data)||!PlatformHealth.stable(health)?"本次检查未完整或进程稳定性未确认；请刷新，暂不使用接口样本作结论。":ServiceAbiSnapshot.describe(data.optJSONObject(role+"_abi_status"),role));
        diagnosticSection("诊断进程附加检查",false);
        JSONObject abi=data.optJSONObject("runtime_abi");
        if(abi!=null)card("仅 root 诊断加载器 · 不作为替换判定", "核心接口可见 "+abi.optInt("visible")+"/"+abi.optInt("total")+
            " · 缺失 "+abi.optInt("missing")+" · 访问受限 "+abi.optInt("inaccessible")+" · 加载错误 "+abi.optInt("linkage_errors")+
            "\n"+(abi.optBoolean("modern_candidate")?"当前为 Android12–17 候选版本；实际安装与绑定需另行验证":abi.optInt("sdk")==30?"当前系统为 Android11，使用独立的 Android11 替换引擎":"超出当前替换版本范围")+
            "\n23 项是跨版本的固定核心签名候选，不是当前 Android 版本全部接口。可见表示当前 root app_process 加载器找到签名；缺失项可能只在服务的 IKE 共享库中可见。未调用接口，也未验证权限或替换可用性。");
        if(abi!=null){
            card("如何选择替换接口","引擎按系统版本与设备配置选择。23 项预检查只显示签名，不会自动切换所有接口；生产代码仅对 IKE Builder 构造器、加密提案方法及 CarrierConfig 读取方法做缺失签名回退。调用失败不会自动改用下一签名。");
            JSONObject checks=abi.optJSONObject("checks");Map<String,String> states=new LinkedHashMap<>();
            if(checks!=null)for(String key:RuntimeAbiDetails.keys())if(checks.has(key))states.put(key,checks.optString(key));
            for(int group=0;group<RuntimeAbiDetails.GROUPS.length;group++)card(RuntimeAbiDetails.GROUPS[group],RuntimeAbiDetails.describe(group,states));
        }
        diagnosticSection("网络检测",data.optJSONObject("network_detection")!=null&&data.optJSONObject("network_detection").optBoolean("active_probe"));
        card("SIM 运营商与归属网",data.optString("carrier_name","未取得名称")+" · "+data.optString("sim_operator_name","")+"\nSIM MCC/MNC："+data.optString("operator","不可见")+"\n当前注册网络："+data.optString("registered_operator_name","")+" · "+data.optString("registered_operator","未注册")+"\n漫游状态："+(data.has("network_roaming")?data.optBoolean("network_roaming"):"不可见")+"\n检测目标来自 SIM 归属网与可见配置，不把漫游网络当成 SIM 运营商。");
        card("运营商识别范围",CarrierReference.identityNote(data.optString("carrier_name"),data.optString("sim_operator_name"),data.optString("operator"))+"\n域名随运营商／配置而变。UDP 500 用于 IKE，4500 常用于 NAT-T；解析成功、端口有响应和运营商允许这张卡注册是不同结论。");
        card("实体 Wi-Fi",data.has("network_error")?"网络检查不完整 · "+data.optString("network_error"):data.optString("wifi","未观测"));
        JSONObject realTunnel=data.optJSONObject("iwlan_status"),realIms=data.optJSONObject("ims_status");
        card("实际会话与网络证据",NetworkSessionEvidence.describe(DiagnosticPolicy.complete(data),PlatformHealth.stable(health),data.optInt("ims_transport",-1),
            realTunnel!=null&&realTunnel.optBoolean("observed"),realTunnel!=null&&realTunnel.optBoolean("ike_open"),realTunnel!=null&&realTunnel.optBoolean("child_open"),
            realIms!=null&&realIms.optBoolean("observed"),realIms!=null&&realIms.optBoolean("registered"),realIms==null?0:realIms.optInt("sip_status")));
        card("运营商 ePDG / UDP 500 与 4500",NetworkDetection.describe(data.optJSONObject("network_detection"))+"\n"+data.optString("udp","未观测"));
        card("实际 IWLAN 隧道",data.optString("ike","不可见"));
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
        diagnosticSection("APN 与运营商配置",false);
        carrierReferenceCard(data);
        JSONObject apns=data.optJSONObject("apn_details");
        if(apns==null)card("首选互联网 APN",data.optString("apn","不可见"));
        else{
            JSONObject preferred=apns.optJSONObject("preferred");
            if(preferred!=null)apnCard("首选互联网 APN · 当前配置",preferred);else card("首选互联网 APN","NOT_SET".equals(apns.optString("preferred_status"))?"未设置":"不可见 · "+apns.optString("preferred_error","未知"));
            org.json.JSONArray imsApns=apns.optJSONArray("ims_records");
            if(imsApns!=null&&imsApns.length()>0)for(int i=0;i<imsApns.length();i++)apnCard("IMS APN 记录 "+(i+1)+" · 不等于 modem 当前选用",imsApns.optJSONObject(i));
            else card("IMS APN 记录",imsApns!=null?"数据库未返回这张卡的 IMS 类型记录；不代表 modem 没有内部 IMS 配置":"不可见 · "+apns.optString("ims_error",apns.optString("ims_status","未知")));
        }
        card("Wi-Fi Calling 设置",data.has("wfc_setting")?"开启 "+data.optBoolean("wfc_setting")+" · 漫游开关 "+(data.has("wfc_roaming_setting")?data.optBoolean("wfc_roaming_setting"):"未知")+" · 偏好模式 "+(data.has("wfc_mode")?data.optInt("wfc_mode"):"未知"):"不可见 · "+data.optString("settings_error","未知"));
        card("WLAN 语音 provisioning",data.has("wlan_voice_provisioned")?Boolean.toString(data.optBoolean("wlan_voice_provisioned"))+"（框架配置结果，不代表运营商已接受注册）":"不可见 · "+data.optString("provisioning_error","未知"));
        JSONObject policy=data.optJSONObject("selected_policy");
        if(policy!=null)card("所选 SIM 的运营商策略",policy.toString());
        diagnosticSection("IMS 注册与系统短信",true);
        card("这一组检查什么","IMS 同时承载语音和 SMS over IP。这里分别检查运营商 IMS 注册、语音/SMS 能力，以及 Android 短信服务是否绑定并能分发。语音可用不保证短信路径就绪；系统短信应用还负责收件箱和通知。");
        int networks=data.optInt("ims_network_count");
        card("所选 SIM 的 IMS 网络",data.has("network_error")?"不可见 · "+data.optString("network_error"):networks+" 个 · 接口 "+(data.isNull("ims_interface")?"未观测":data.optString("ims_interface","不可见"))+" · P-CSCF "+data.optInt("pcscf_count")+(data.optInt("ims_unattributed_network_count")>0?"\n另有 "+data.optInt("ims_unattributed_network_count")+" 个 IMS 网络无法归属到卡槽":"")+(data.has("ims_interface_present")?"\n内核接口存在 "+data.optBoolean("ims_interface_present"):"")+(data.has("iwlan_process_present")?" · IWLAN 进程运行 "+data.optBoolean("iwlan_process_present"):""));
        int t=data.optInt("ims_transport",-1);
        String registration=t==2?"WLAN 已注册":t==1?"蜂窝网络已注册":t==-2?"注册中":t==-3?"未注册":"不可见 / 尚未回调";
        if(t==2&&!DiagnosticPolicy.wlanConfirmed(data))registration="本次回调曾报告 WLAN 注册；检查未完整或进程稳定性未确认，请刷新核实当前状态";
        card("IMS 注册",data.has("ims_error")?"不可见 · "+data.optString("ims_error"):registration);
        card("MMTEL 能力",data.optBoolean("cap_observed")?"语音 "+data.optBoolean("voice")+" · SMS "+data.optBoolean("sms"):"尚未观测，不能判定不可用");
        String nativeSms=data.has("native_sms_ims_supported")?(data.optBoolean("native_sms_ims_supported")?"系统短信服务报告支持 IMS 短信。":"系统短信服务暂未报告支持 IMS 短信。"):("未能观测 · "+data.optString("native_sms_error","未知"));
        if(Boolean.FALSE.equals(data.opt("native_sms_ims_supported"))&&data.optBoolean("cap_observed")&&data.optBoolean("sms"))nativeSms+="\n与 MMTEL 的 SMS 上报不一致；空闲时重新拉起后再检查。";
        card("系统短信发送检查",nativeSms+"\n这是所选 SIM 的一次状态查询，实际收发和通知仍需分别验证。");
        JSONObject ownSms=data.optJSONObject("ims_native_sms_status");
        if(ownSms!=null){
            String value=ownSms.optString("result","UNKNOWN");long checked=ownSms.optLong("query_elapsed"),age=checked>0?SystemClock.elapsedRealtime()-checked:-1;
            String detail="OBSERVED".equals(ownSms.optString("status"))&&age>=0&&age<=15000?
                ("TRUE".equals(value)?"系统报告支持 IMS 短信":"系统暂未报告支持 IMS 短信"):
                "未取得当前有效结果 · "+ownSms.optString("status","UNAVAILABLE");
            card("SIM"+(data.optInt("slot")+1)+" 的 IMS 应用短信查询",detail+"\n由 IMS 应用自身权限读取；可能包含旧式基带 IMS 支持，不能据此确认 Wi-Fi 短信分发器就绪或实际收发成功。");
        }else card("IMS 应用短信查询","未能观测 · "+data.optString("ims_native_sms_status_error","配套 IMS 程序未运行或尚未提供此查询"));
        JSONObject dispatcher=data.optJSONObject("sms_dispatcher_window");
        if(dispatcher!=null){
            boolean observed="observed".equals(dispatcher.optString("status"));
            String detail=observed?"服务连接 "+dispatcher.optBoolean("service_up")+" · IMS 注册 "+dispatcher.optBoolean("registered")+" · SMS 能力 "+dispatcher.optBoolean("sms_capable")+
                "\n软件 IMS 短信分发器就绪 "+dispatcher.optBoolean("available"):
                "未能取得有效采样"+("rom-not-calibrated".equals(dispatcher.optString("reason"))?"；当前 ROM 的状态日志尚未校准":"；电话进程或状态发生变化，或未找到本次查询的日志");
            card("查询时的短信分发器",detail+"\n只采集本次支持查询时间窗内的状态；不会发送短信，也不保证下一条短信成功。");
        }
        JSONObject service=data.optJSONObject("ims_status");
        if(service!=null&&service.optBoolean("observed")){
            card("检查时的 IMS 实例","会话代次 "+service.optLong("generation")+" · "+phaseName(service.optString("phase"))+"\n本代 REGISTER 发送 "+service.optInt("register_tx")+" · 最近响应 "+service.optInt("sip_status"));
            card("SIP 连接与重试",SipTransportObservation.describe(service));
            card("本代系统短信投递观测","IMS 收到 "+service.optInt("sms_rx")+" · 系统确认成功 "+service.optInt("sms_ack_ok")+" · 系统拒绝 "+service.optInt("sms_ack_failed")+"\n发送请求 "+service.optInt("sms_tx")+" · 网络确认成功 "+service.optInt("sms_tx_ok")+" · 发送失败 "+service.optInt("sms_tx_failed")+"\n累计元数据，不代表下一条短信一定成功，也不证明已显示通知");
            card("最近一次短信发送链路",SmsSendStatus.describe(service));
            card("IMS 入站消息与接收通道",SipReceiveStatus.describe(service));
            card("本代语音媒体观测","已发送 RTP 帧 "+service.optInt("voice_tx_frames")+" · 已交给音频播放的帧 "+service.optInt("voice_played_frames")+"\n累计观测；需要实际通话验证听感");
        }
        else if(service!=null)card("检查时的 IMS 实例","未观测到所选 SIM 的有效实例");
        JSONObject clients=data.optJSONObject("ims_clients_status");
        if(clients!=null)card("IMS 客户端绑定观测","注册 "+clients.optBoolean("registered")+" · 已启用能力位 "+clients.optInt("enabled_mask")+" · 最近上报 "+clients.optInt("last_notified_mask")+
            "\n短信接收接口就绪次数 "+clients.optLong("sms_ready_events")+" · 完成重绑次数 "+clients.optLong("client_rebind_returns")+
            "\n能力回调数量："+(clients.optBoolean("callback_count_observed")?clients.optInt("capability_callback_count"):"未知")+"；上报函数返回不保证每个客户端已收到，也不证明短信收发成功。");
        if(clients!=null&&clients.has("native_watch_schema")){
            long checked=clients.optLong("native_watch_checked_elapsed"),age=checked>0?Math.max(0,SystemClock.elapsedRealtime()-checked):-1;
            String nativeState=clients.optString("native_watch_native","UNKNOWN");
            card("系统短信自动恢复",nativeWatchStatus(clients.optString("native_watch_status","UNKNOWN"))+" · 短信支持 "+("TRUE".equals(nativeState)?"是":"FALSE".equals(nativeState)?"否":"未知")+
                "\n"+(age<0?"尚未检查":age>15000?"检测结果已过期，请刷新":"最近检查距今 "+(age/1000)+" 秒")+
                " · 已检查 "+clients.optLong("native_watch_checks")+" 次 · 已请求恢复 "+clients.optLong("native_watch_requests")+" 次"+
                "\n当前已适配系统在持续异常且通话、短信空闲时自动恢复；请实际验证收发和通知。");
        }
        card("最近短信分发器状态日志",data.optString("sms_dispatcher","未观测"));
        diagnosticSection("替换事务与恢复记录",false);
        JSONObject providers=data.optJSONObject("providers");
        int ownerSlot=providers==null?-1:providers.optInt("owner_slot",-1);
        card("替换控制器"+(ownerSlot>=0?"（管理 SIM"+(ownerSlot+1)+"）":""),providers==null?(data.has("controller_error")?"状态读取失败 · "+data.optString("controller_error"):"未加载配套模块"):providers.optString("mode")+" · "+providers.optString("persistent","未常驻")+"\n"+("ACTIVE".equals(providers.optString("transaction"))?"替代组件："+componentNames(providers.optInt("components",7)):"尚未启动替换"));
        if(providers!=null&&"modern".equals(providers.optString("engine")))card("现代模块状态","模块与本工具匹配 "+providers.optBoolean("module_payload_matches_tool")+" · 模块启用 "+providers.optBoolean("module_enabled")+"\n安装事务："+providers.optString("installation_phase","未知")+"\n事务记录属于恢复依据；当前注册与能力以上方实时回调为准。");
        setActions(diagnosticReady()&&data.optBoolean("engine_supported"));
        boolean active=providers!=null&&"ACTIVE".equals(providers.optString("transaction"));
        boolean identities=providers!=null&&providers.optInt("identity_selection")==1;
        boolean selectedOwner=providers!=null&&ownerSlot==data.optInt("slot",-1)&&providers.optInt("owner_sub",-1)==data.optInt("sub_id",-2);
        boolean selectable=identities&&providers.optInt("component_selection")==1;
        for(CheckBox checkbox:components){checkbox.setEnabled(diagnosticReady()&&data.optBoolean("engine_supported")&&selectable&&!active);}
        int activeMask=providers==null?-1:providers.optInt("components",-1);
        if(active&&activeMask>=1&&activeMask<=7)for(int index=0;index<components.length;index++)components[index].setChecked((activeMask&(1<<index))!=0);
        if(!selectable)for(CheckBox checkbox:components)checkbox.setChecked(true);
        refreshTrialSelection();
        boolean canRetain=activeMask>=1&&activeMask<=7&&(activeMask==7||providers.optInt("persistent_component_selection")==1);
        enable.setEnabled(diagnosticReady()&&identities&&selectedOwner&&data.optBoolean("engine_supported")&&active&&canRetain&&!"ENABLED".equals(providers.optString("persistent")));
        reload.setEnabled(diagnosticReady()&&identities&&selectedOwner&&data.optBoolean("engine_supported")&&active);
        rebind.setEnabled(diagnosticReady()&&data.optInt("sdk")==30&&identities&&selectedOwner&&active&&clients!=null&&clients.optBoolean("registered")&&clients.optBoolean("initialized")&&!clients.optBoolean("removed")&&clients.optBoolean("sms_session_idle"));
        recoveryOwners.clear();ArrayList<String> recoveryLabels=new ArrayList<>();int recoveryIndex=0;
        org.json.JSONArray owners=providers==null?null:providers.optJSONArray("active_owners");
        if(owners!=null)for(int index=0;index<owners.length();index++){
            JSONObject owner=owners.optJSONObject(index);if(owner==null)continue;
            int recoverySlot=owner.optInt("slot",-1),recoverySub=owner.optInt("sub",-1);
            if(recoverySlot<0||recoverySlot>7||recoverySub<0)continue;
            if(recoverySlot==data.optInt("slot",-1))recoveryIndex=recoveryOwners.size();
            recoveryOwners.add(new int[]{recoverySlot,recoverySub});recoveryLabels.add("SIM"+(recoverySlot+1)+(owner.optBoolean("recovery_pending_owner")?"（等待原卡回归）":""));
        }
        if(recoveryLabels.isEmpty())recoveryLabels.add("无待恢复事务");
        recoveries.setAdapter(spinnerAdapter(recoveryLabels));
        recoveries.setSelection(recoveryIndex);refreshRecovery();
        if(recoveryOwners.size()>1)card("其他卡的替换事务",recoveryOwners.size()+" 张卡分别管理；恢复所选事务后，其他卡的配置和租约保留。电话服务重载会短暂重建两张卡的连接。");
        if(!diagnosticReady()){status("检查未完成，已保留部分结果。请刷新检查后再操作。");}
    }
    private boolean diagnosticReady(){return DiagnosticPolicy.complete(last);}
    private String processState(JSONObject sample){
        if(sample==null)return "未观测";
        switch(sample.optString("state")){
            case "stable":return "两次采样均存在，身份一致";
            case "changed":return "检查期间出现、消失或重启";
            case "absent":return "两次采样均未发现";
            default:return "观测不可用，不能判断";
        }
    }
    private String diagnosticStage(String stage){
        switch(stage){
            case "platform_health":return "电话服务进程";case "bootstrap":return "系统上下文初始化";
            case "subscription":return "SIM 订阅";case "controller":return "替换事务状态";
            case "telephony":return "SIM 状态和运营商";case "network":return "网络枚举";
            case "dns":return "运营商 ePDG DNS";case "network_udp":return "UDP 500 / 4500 网络检测";case "apn":return "互联网与 IMS APN 详情";
            case "wfc_settings":return "Wi-Fi Calling 设置";case "carrier_policy":return "运营商配置";
            case "provisioning":return "IMS provisioning";case "ims_callbacks":return "IMS 注册和能力回调";
            case "native_sms":return "系统短信服务支持状态";
            case "service_status":return "替换服务状态";case "iwlan_history":return "IWLAN 进程历史";
            case "sms_dispatcher":return "系统短信分发器";case "finished":return "检查结束";
            default:return "不可见";
        }
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
        LinearLayout c=panel(detailGroup==null?results:detailGroup,theme.surface);
        TextView label=text(heading,16);theme.typography(label,16,true);c.addView(label);TextView detail=text(value,14);detail.setTextColor(theme.secondary);detail.setTextIsSelectable(true);c.addView(detail);
    }
    private void apnCard(String heading,JSONObject row){
        if(row==null)return;card(heading,ApnDetails.describe(row,false));
        if(!row.optString("password").isEmpty()){
            LinearLayout target=detailGroup==null?results:detailGroup;
            LinearLayout lastCard=(LinearLayout)target.getChildAt(target.getChildCount()-1);
            TextView details=(TextView)lastCard.getChildAt(1);Button reveal=button("查看 APN 密码",null);
            reveal.setOnClickListener(v->{boolean showing=Boolean.TRUE.equals(reveal.getTag());reveal.setTag(!showing);details.setText(ApnDetails.describe(row,!showing));reveal.setText(showing?"查看 APN 密码":"隐藏 APN 密码");});lastCard.addView(reveal);
        }
    }
    private void carrierReferenceCard(JSONObject data){
        LinearLayout target=detailGroup==null?results:detailGroup,c=panel(target,theme.surface);
        c.addView(text("运营商 APN 参考 · 只读",16));
        c.addView(text("参考不改变检测目标或替换引擎，也不会写入 APN。CTExcel 需确认发卡地区和具体产品；未核实字段保留未知。",13));
        Spinner picker=new Spinner(this);picker.setMinimumHeight(dp(52));c.addView(picker);
        TextView detail=text("",14);detail.setTextIsSelectable(true);c.addView(detail);
        ArrayList<String> labels=new ArrayList<>();labels.add("选择运营商／地区参考");for(CarrierReference.Profile p:CarrierReference.all())labels.add(p.label);
        picker.setAdapter(spinnerAdapter(labels));
        picker.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            public void onItemSelected(AdapterView<?> parent,View view,int index,long id){detail.setText(index==0?"当前 APN 与 IMS 记录列在下方。其他运营商可读取 SIM／配置并检测候选地址；是否能注册还需实际卡验证。":CarrierReference.all().get(index-1).describe());}
            public void onNothingSelected(AdapterView<?> parent){}
        });
        picker.setSelection(CarrierReference.suggested(data.optString("carrier_name"),data.optString("sim_operator_name"),data.optString("operator"))+1);
    }
    private void setActions(boolean supported){
        if(trial==null)return;trial.setEnabled(supported);enable.setEnabled(false);reload.setEnabled(false);rebind.setEnabled(false);rollback.setEnabled(false);install.setEnabled(supported);
        for(CheckBox checkbox:components)if(checkbox!=null)checkbox.setEnabled(false);
        if(recoveries!=null)recoveries.setEnabled(false);
    }
    private void refreshRecovery(){
        if(rollback==null||recoveries==null)return;
        int index=recoveries.getSelectedItemPosition();boolean available=!busy&&last!=null&&index>=0&&index<recoveryOwners.size();
        recoveries.setEnabled(!busy&&!recoveryOwners.isEmpty());rollback.setEnabled(available);
        rollback.setText(available?"恢复 SIM"+(recoveryOwners.get(index)[0]+1)+" 原来的组件和短信模块":"恢复原来的组件和短信模块");
    }
    private static String nativeWatchStatus(String value){
        switch(value){
            case "HEALTHY":return "状态正常";
            case "WAITING":return "正在确认状态";
            case "MISMATCH":return "已确认短信支持不一致";
            case "RECOVERING":return "已请求恢复，等待核实";
            case "COOLDOWN":return "等待下次恢复";
            case "LIMIT":return "已达到自动恢复次数，请手动检查";
            case "INACTIVE":return "等待注册完成且通话、短信空闲";
            case "UNSUPPORTED":return "当前系统尚未适配自动恢复";
            default:return "状态未知";
        }
    }
    private int componentMask(){int mask=0;for(int i=0;i<components.length;i++)if(components[i].isChecked())mask|=1<<i;return mask;}
    private String componentNames(int mask){ArrayList<String> names=new ArrayList<>();if((mask&1)!=0)names.add("IWLAN");if((mask&2)!=0)names.add("QNS");if((mask&4)!=0)names.add("IMS");return String.join(" + ",names);}
    private void refreshTrialSelection(){
        if(trial==null)return;
        JSONObject provider=last==null?null:last.optJSONObject("providers");
        trial.setEnabled(!busy&&diagnosticReady()&&last.optBoolean("engine_supported")&&last.optBoolean("controller")&&provider!=null&&provider.optInt("identity_selection")==1&&!"ACTIVE".equals(provider.optString("transaction"))&&componentMask()!=0);
    }
    private void action(String action){
        if(busy)return;
        if(!Arrays.asList("trial","enable","reload","rollback").contains(action))return;
        if(last==null)return;
        if(!"rollback".equals(action)&&(!diagnosticReady()||!last.optBoolean("engine_supported")))return;
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
        final boolean modern=last.optBoolean("engine_experimental");
        execute("正在执行所选操作…",()->{
            String output;
            if(modern){
                String raw=shell("CLASSPATH="+quote(getApplicationInfo().sourceDir)+" app_process /system/bin dev.codex.vowifi.tool.ModernAppActions "+action+" "+targetSlot+" "+targetSub+" "+Math.max(1,mask),310);
                JSONObject accepted=null;for(String line:raw.split("[\\r\\n]+"))if(line.startsWith("{")&&line.endsWith("}"))accepted=new JSONObject(line);
                if(accepted==null||!action.equals(accepted.optString("action")))throw new IOException("modern-action-result-unconfirmed");
                if("rollback".equals(action)&&Boolean.TRUE.equals(accepted.opt("recovery_pending_owner"))&&Boolean.FALSE.equals(accepted.opt("action_completed")))return new JSONObject().put("recovery_pending_owner",true).put("action_result","已撤销这张卡的组件租约，原配置和共享资源恢复仍在等待原卡回归。请将原卡插回原卡槽，再检查恢复状态。");
                if(!Boolean.TRUE.equals(accepted.opt("action_completed")))throw new IOException("modern-action-result-unconfirmed");
                output="操作已完成；请查看最新注册与能力。";
            }else output=shell("sh "+CONTROL+" "+action+("trial".equals(action)?" "+mask:"")+" "+targetSlot+" "+targetSub,35);
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
                if(DiagnosticPolicy.wlanConfirmed(observation)&&observation.optBoolean("voice")&&observation.optBoolean("sms"))break;
            }
            return observation.put("action_result",output);
        },data->{
            last=null;setActions(false);
            if(data.has("sdk")){render(data);status(DiagnosticPolicy.wlanConfirmed(data)?"已恢复 WLAN 注册；请查看下方最新能力。":"操作已请求，尚未确认 WLAN 注册；请刷新检查结果。"+(data.has("action_observation_error")?"\n等待检查未完成："+data.optString("action_observation_error"):""));}
            else{status(data.optString("action_result"));if(!data.optBoolean("recovery_pending_owner"))diagnose();}
        });
    }
    private void rebindClients(){
        if(busy||!diagnosticReady()||!rebind.isEnabled())return;
        final int slot=slots.get(sims.getSelectedItemPosition()),sub=last.optInt("sub_id",-1);
        if(last.optInt("slot",-1)!=slot||sub<0)return;
        execute("正在重新绑定 IMS 客户端…",()->{
            String raw=shell("CLASSPATH="+quote(getApplicationInfo().sourceDir)+" timeout 35s app_process /system/bin dev.codex.vowifi.tool.RootImsClients rebind "+slot+" "+sub,40);
            JSONObject accepted=null;for(String line:raw.split("[\\r\\n]+"))if(line.startsWith("{")&&line.endsWith("}"))accepted=new JSONObject(line);
            if(accepted==null||!"client-rebind".equals(accepted.optString("action")))throw new IOException("client-action-result-unconfirmed");
            boolean completed=Boolean.TRUE.equals(accepted.opt("action_completed"));
            final String message=completed?"客户端重绑已完成，正在核对注册及系统短信分发器…":"未确认重绑完成，正在重新检查当前状态…";
            runOnUiThread(()->{if(!isFinishing())status(message);});
            JSONObject diagnostic=readDiagnostic(slot,45);diagnostic.put("client_rebind_result",accepted);return diagnostic;
        },data->{render(data);JSONObject action=data.optJSONObject("client_rebind_result");
            JSONObject window=data.optJSONObject("sms_dispatcher_window");
            boolean ready=window!=null&&"observed".equals(window.optString("status"))&&window.optBoolean("available")&&data.optBoolean("native_sms_ims_supported");
            status(action!=null&&action.optBoolean("action_completed")?(ready?"重绑完成，本次查询确认系统短信分发器就绪；实际收发仍需验证。":"重绑完成，尚未确认系统短信分发器就绪；请查看链路状态。"):"未确认重绑完成；注册、通话空闲状态或 ROM 接口可能不满足，请查看检查结果。");
        });
    }
    private void installModule(){
        if(busy||!diagnosticReady()||!last.optBoolean("engine_supported"))return;
        final boolean modern=last.optBoolean("engine_experimental");
        JSONObject provider=last.optJSONObject("providers");
        if(modern&&last.has("controller_error")){status("当前事务状态未能完整读取。请先核对恢复记录，再更新现代模块。");return;}
        if(modern&&provider!=null&&!Arrays.asList("ABSENT","RESTORED").contains(provider.optString("installation_phase"))){status("请先恢复现代模块的安装事务，再更新模块。");return;}
        if(modern&&provider!=null&&provider.optJSONArray("active_owners")!=null&&provider.optJSONArray("active_owners").length()>0){status("请先恢复各 SIM 的替换事务，再更新现代模块。");return;}
        execute("正在安装配套模块更新…",()->{
            String asset=modern?EngineAssets.MODERN_ASSET:EngineAssets.API30_ASSET;String expected=modern?EngineAssets.MODERN_SHA256:EngineAssets.API30_SHA256;
            File zip=new File(getCacheDir(),asset);
            try(InputStream in=getAssets().open(asset);OutputStream out=new FileOutputStream(zip)){byte[] b=new byte[8192];int n;while((n=in.read(b))>=0)out.write(b,0,n);}
            try{
                if(!expected.equals(ModernControllerObservation.digest(zip)))throw new IOException("bundled-module-digest-mismatch");
                String invocation="magisk --install-module "+quote(zip.getAbsolutePath());
                return new JSONObject().put("action_result",shell("if command -v magisk >/dev/null 2>&1; then "+invocation+"; else /product/bin/"+invocation+"; fi",45));
            }
            finally{zip.delete();}
        },data->{status(data.optString("action_result")+"\n安装成功后请手动重启，再运行只读检查。");setActions(false);});
    }
    private interface Work{JSONObject run()throws Exception;}
    private interface Show{void run(JSONObject data);}
    private void execute(String message,Work work,Show show){
        busy=true;status(message);sims.setEnabled(false);check.setEnabled(false);networkCheck.setEnabled(false);progress.setVisibility(View.VISIBLE);setActions(false);
        worker.submit(()->{JSONObject response;try{response=work.run();}catch(Throwable e){response=new JSONObject();try{response.put("error",e.getClass().getSimpleName());if(e instanceof RootFailure)response.put("action_error",e.getMessage());}catch(Exception ignored){}}
            final JSONObject result=response;runOnUiThread(()->{busy=false;sims.setEnabled(true);check.setEnabled(true);networkCheck.setEnabled(true);progress.setVisibility(View.GONE);if(!isFinishing()){if(result.has("error")&&!result.has("sdk")){last=null;setActions(false);resetOverview();results.removeAllViews();status("操作未完成："+result.optString("error")+(result.has("action_error")?"\n"+result.optString("action_error"):"")+"。请重新运行只读检查，核对事务状态和 Magisk 授权；超时不代表已回退。");}else show.run(result);}});
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
