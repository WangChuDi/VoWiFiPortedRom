// SPDX-License-Identifier: GPL-2.0
package dev.codex.vowifi.tool;

import android.content.Context;
import android.net.Network;
import android.os.PersistableBundle;
import android.os.SystemClock;
import android.telephony.CarrierConfigManager;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

/** Bounded ePDG candidate resolution and opt-in, unauthenticated IKE SA_INIT. */
final class NetworkDetection {
    static String standardHost(String plmn){
        if(plmn==null||!plmn.matches("[0-9]{5,6}"))return null;
        return String.format(Locale.US,"epdg.epc.mnc%03d.mcc%s.pub.3gppnetwork.org",Integer.parseInt(plmn.substring(3)),plmn.substring(0,3));
    }
    static LinkedHashMap<String,String> candidates(String plmn,PersistableBundle config,boolean roaming){
        LinkedHashMap<String,String> targets=new LinkedHashMap<>();
        if(config!=null){
            String roamingStatic=roaming?config.getString("iwlan.epdg_static_address_roaming_string"):null;
            String configured=roamingStatic!=null&&!roamingStatic.isEmpty()?roamingStatic:config.getString("iwlan.epdg_static_address_string");
            if(configured!=null)for(String host:configured.split(","))add(targets,host.trim(),roamingStatic!=null?"运营商漫游静态配置":"运营商静态配置");
        }
        String home=standardHost(plmn);if(home!=null)add(targets,home,"SIM 归属网标准域名");
        if(config!=null){String[] others=config.getStringArray("iwlan.mcc_mncs_string_array");if(others!=null)for(String other:others){String host=standardHost(other.replace("-",""));if(host!=null)add(targets,host,"运营商配置的 PLMN");}}
        return targets;
    }
    private static void add(LinkedHashMap<String,String> targets,String host,String source){
        if(targets.size()>=3||host.length()>253||host.isEmpty())return;
        // Literal IPv4/IPv6 and DNS hostnames only; never accept URLs or ports.
        if(host.matches("[A-Za-z0-9](?:[A-Za-z0-9.-]*[A-Za-z0-9])?")||host.matches("[0-9A-Fa-f:]+:[0-9A-Fa-f:]*"))targets.putIfAbsent(host,source);
    }
    static JSONObject inspect(Context context,Network wifi,int sub,String plmn,boolean roaming,boolean active,JSONObject out,DiagnosticProgress progress)throws Exception{
        JSONObject result=new JSONObject().put("schema",1).put("active_probe",active).put("route","physical_wifi")
            .put("sim_authentication",false).put("operator_registration_tested",false);
        out.put("network_detection",result);PersistableBundle config=null;
        if(sub>=0)try{config=context.getSystemService(CarrierConfigManager.class).getConfigForSubId(sub);}catch(Exception e){result.put("carrier_config_error",e.getClass().getSimpleName());}
        LinkedHashMap<String,String> hosts=candidates(plmn,config,roaming);
        result.put("candidate_scope","static_and_home_plmn_not_full_modem_selector");
        JSONArray domains=new JSONArray(),ports=new JSONArray();result.put("domains",domains).put("ports",ports);
        if(wifi==null||hosts.isEmpty()){
            result.put("status",wifi==null?"NO_WIFI":"NO_OPERATOR_TARGET");out.put("dns","未测试：缺少实体 Wi-Fi 或 SIM 归属网信息");out.put("udp","未测试");return result;
        }
        ExecutorService workers=Executors.newFixedThreadPool(4,r->{Thread t=new Thread(r,"vowifi-network-test");t.setDaemon(true);return t;});
        LinkedHashMap<String,Future<InetAddress[]>> dns=new LinkedHashMap<>();LinkedHashMap<InetAddress,String> addresses=new LinkedHashMap<>();
        long deadline=SystemClock.elapsedRealtime()+4000;
        try{
            progress.checkpoint(out,"dns");
            for(String host:hosts.keySet())dns.put(host,workers.submit(()->wifi.getAllByName(host)));
            for(Map.Entry<String,Future<InetAddress[]>> entry:dns.entrySet()){
                JSONObject domain=new JSONObject().put("host",entry.getKey()).put("source",hosts.get(entry.getKey()));domains.put(domain);
                try{
                    InetAddress[] resolved=entry.getValue().get(Math.max(1,deadline-SystemClock.elapsedRealtime()),TimeUnit.MILLISECONDS);
                    JSONArray values=new JSONArray();for(InetAddress address:resolved){if(values.length()<12)values.put(address.getHostAddress());}
                    domain.put("status","RESOLVED").put("address_count",resolved.length).put("addresses",values);
                    // At most two addresses per candidate, preferring one of each family.
                    InetAddress v4=null,v6=null;for(InetAddress address:resolved){if(address instanceof Inet4Address&&v4==null)v4=address;if(address instanceof Inet6Address&&v6==null)v6=address;}
                    for(InetAddress address:new InetAddress[]{v4,v6})if(address!=null&&addresses.size()<4)addresses.putIfAbsent(address,entry.getKey());
                    for(InetAddress address:resolved)if(addresses.size()<4&&countHost(addresses,entry.getKey())<2)addresses.putIfAbsent(address,entry.getKey());
                }catch(Exception e){entry.getValue().cancel(true);domain.put("status","UNAVAILABLE").put("error",errorName(e));}
            }
            out.put("dns",addresses.isEmpty()?"本次未取得可探测地址":"已解析候选域名，详见网络检测");progress.checkpoint(out,"dns");
            if(!active){result.put("status","DNS_ONLY");out.put("udp","点击「网络检测」主动探测 UDP 500 / 4500；DNS 成功不代表端口可达");return result;}
            if(addresses.isEmpty()){result.put("status","NO_RESOLVED_TARGET");out.put("udp","未探测：DNS 未提供目标地址");return result;}
            progress.checkpoint(out,"network_udp");
            ArrayList<Future<JSONObject>> probes=new ArrayList<>();
            for(Map.Entry<InetAddress,String> target:addresses.entrySet())for(int port:new int[]{500,4500})probes.add(workers.submit(()->probe(wifi,target.getKey(),target.getValue(),port)));
            long probeDeadline=SystemClock.elapsedRealtime()+5500;int matched=0;
            for(Future<JSONObject> future:probes){
                try{JSONObject value=future.get(Math.max(1,probeDeadline-SystemClock.elapsedRealtime()),TimeUnit.MILLISECONDS);ports.put(value);if("IKE_RESPONSE".equals(value.optString("status")))matched++;}
                catch(Exception e){future.cancel(true);ports.put(new JSONObject().put("status","UNAVAILABLE").put("error",errorName(e)));}
            }
            result.put("status","COMPLETED").put("matched_responses",matched).put("address_limit",4).put("attempts_per_port",1);
            out.put("udp","收到匹配 IKE 响应 "+matched+" / "+ports.length()+" 次；无响应不能直接判断端口被封");progress.checkpoint(out,"network_udp");return result;
        }finally{for(Future<?> f:dns.values())if(!f.isDone())f.cancel(true);workers.shutdownNow();}
    }
    private static int countHost(Map<InetAddress,String> addresses,String host){int n=0;for(String value:addresses.values())if(host.equals(value))n++;return n;}
    private static JSONObject probe(Network wifi,InetAddress address,String host,int port)throws Exception{
        JSONObject result=new JSONObject().put("host",host).put("address",address.getHostAddress()).put("port",port);
        long began=SystemClock.elapsedRealtime();boolean receivedOther=false;
        try(DatagramSocket socket=new DatagramSocket(null)){
            socket.bind(new InetSocketAddress(0));wifi.bindSocket(socket);socket.connect(address,port);
            IkeReachabilityPacket.Request request=IkeReachabilityPacket.create();byte[] bytes=request.datagram(port);
            socket.send(new DatagramPacket(bytes,bytes.length));long deadline=SystemClock.elapsedRealtime()+2200;
            byte[] buffer=new byte[8192];int received=0;
            while(SystemClock.elapsedRealtime()<deadline&&received<8){
                socket.setSoTimeout((int)Math.max(1,deadline-SystemClock.elapsedRealtime()));DatagramPacket packet=new DatagramPacket(buffer,buffer.length);
                try{socket.receive(packet);}catch(SocketTimeoutException e){break;}received++;
                IkeReachabilityPacket.Response response=IkeReachabilityPacket.parse(buffer,packet.getLength(),port,request.spi);
                if(response!=null)return result.put("status","IKE_RESPONSE").put("response_kind",response.kind).put("notify",response.notify).put("elapsed_ms",SystemClock.elapsedRealtime()-began);
                receivedOther=true;
            }
            return result.put("status",receivedOther?"UNMATCHED_RESPONSE":"NO_RESPONSE").put("elapsed_ms",SystemClock.elapsedRealtime()-began);
        }catch(Exception e){return result.put("status","UNAVAILABLE").put("error",errorName(e)).put("elapsed_ms",SystemClock.elapsedRealtime()-began);}
    }
    private static String errorName(Exception e){if(e instanceof ExecutionException&&e.getCause()!=null)return e.getCause().getClass().getSimpleName();return e.getClass().getSimpleName();}
    static String describe(JSONObject result){
        if(result==null)return "未观测";
        StringBuilder text=new StringBuilder("路径：实体 Wi-Fi（绕过手机 VPN；仍经过当前网关）");
        JSONArray domains=result.optJSONArray("domains");if(domains!=null)for(int i=0;i<domains.length();i++){
            JSONObject d=domains.optJSONObject(i);if(d==null)continue;text.append("\n\n").append(d.optString("host")).append("\n来源：").append(d.optString("source"));
            text.append("\nDNS：").append("RESOLVED".equals(d.optString("status"))?d.optInt("address_count")+" 个地址 · "+d.optJSONArray("addresses"):"未取得结果 · "+d.optString("error","未知"));
        }
        JSONArray ports=result.optJSONArray("ports");if(ports!=null)for(int i=0;i<ports.length();i++){
            JSONObject p=ports.optJSONObject(i);if(p==null)continue;text.append("\n\n").append(p.optString("address","目标未完成")).append(" · UDP ").append(p.optInt("port"));
            String status=p.optString("status");text.append("\n").append("IKE_RESPONSE".equals(status)?"收到匹配 IKE 响应 · "+p.optString("response_kind")+(p.optInt("notify",-1)>=0?"（通知 "+p.optInt("notify")+"）":""):"NO_RESPONSE".equals(status)?"本次无响应；不能直接判断端口被封":"UNMATCHED_RESPONSE".equals(status)?"收到数据，但未匹配本次 IKE 请求":"未完成 · "+p.optString("error","未知"));
        }
        if(!result.optBoolean("active_probe"))text.append("\n\n点击上方「网络检测」测试 UDP 500 / 4500。");
        else text.append("\n\n仅发送 IKE_SA_INIT，不做 SIM 鉴权或 IMS 注册。4500 独立探测无响应也可能与服务端会话策略有关。使用临时源端口，结果不保证实际 IMS 流量具有相同路由策略。");
        text.append("\n候选地址不等于调制解调器当前实际使用的地址；未覆盖 PCO、位置与 NAPTR 选择。");return text.toString();
    }
}
