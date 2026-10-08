// SPDX-License-Identifier: GPL-2.0
package dev.codex.vowifi.tool;
import java.util.*;

/** Check unsafe attribution and unknown-data boundaries, not UI wording snapshots. */
public final class CarrierReferenceContracts {
    private static int count;
    private static void check(boolean ok){count++;if(!ok)throw new AssertionError("contract-"+count);}
    public static void main(String[] args){
        List<CarrierReference.Profile> profiles=CarrierReference.all();Set<String> ids=new HashSet<>();
        for(CarrierReference.Profile p:profiles){check(ids.add(p.id));check(p.source.startsWith("https://"));
            if(p.id.matches("ctexcel-(uk|us|ca|fr|it)"))check(p.fields.isEmpty());
            if(p.id.startsWith("ctexcel-"))check(!p.fields.containsKey("MCC")&&!p.fields.containsKey("MNC"));
            try{p.fields.put("APN","guessed");throw new AssertionError();}catch(UnsupportedOperationException expected){check(true);}
        }
        check(profiles.size()==9);check(ids.containsAll(Arrays.asList("ctexcel-uk","ctexcel-us","ctexcel-ca","ctexcel-fr","ctexcel-hk","ctexcel-it","ctexcel-travel")));
        CarrierReference.Profile internet=profiles.get(0),mms=profiles.get(1);
        check("gg".equals(internet.fields.get("用户名")));check("p".equals(internet.fields.get("密码")));check(!internet.fields.containsKey("APN 类型"));
        check(mms.fields.get("用户名").isEmpty()&&mms.fields.get("密码").isEmpty());check("mms".equals(mms.fields.get("APN 类型")));check(mms.fields.get("MMS 代理").isEmpty());
        check(CarrierReference.suggested("O2","","23410")==-1);
        check(CarrierReference.suggested("giffgaff","","23415")==-1);
        check(CarrierReference.suggested("giffgaff","","23410")==0);
        for(String plmn:Arrays.asList("23430","20801","45407","302220","22288","310260",""))check(CarrierReference.suggested("CTExcel","",plmn)==-1);
        check(CarrierReference.suggested(null,null,null)==-1);
        String registered=NetworkSessionEvidence.describe(true,true,2,true,true,true,true,true,200);
        check(registered.contains("WLAN 注册")&&registered.contains("SIP 200"));
        // All combinations of independent probes would produce the same result:
        // there is intentionally no probe input in this real-session policy.
        check(!NetworkSessionEvidence.describe(false,true,2,true,true,true,true,true,200).contains("确认 IMS"));
        check(!NetworkSessionEvidence.describe(true,false,2,true,true,true,true,true,200).contains("确认 IMS"));
        check(!NetworkSessionEvidence.describe(true,true,-1,false,false,false,false,false,200).contains("收到注册成功"));
        check(!NetworkSessionEvidence.describe(true,true,1,false,false,false,true,false,200).contains("收到注册成功"));
        Map<String,Object> probe=RuntimeAbiProbe.inspect(30,CarrierReferenceContracts.class.getClassLoader());
        Map<?,?> checks=(Map<?,?>)probe.get("checks");check(checks.keySet().equals(RuntimeAbiDetails.keys()));
        Map<String,String> observed=new LinkedHashMap<>();for(Object key:checks.keySet())observed.put((String)key,"missing");
        for(int group=0;group<RuntimeAbiDetails.GROUPS.length;group++){String text=RuntimeAbiDetails.describe(group,observed);check(!text.isEmpty()&&text.contains("本加载器未找到"));}
        System.out.println("{\"status\":\"passed\",\"assertions\":"+count+",\"network_traffic\":false,\"carrier_registration_verified\":false}");
    }
}
