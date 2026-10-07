// SPDX-License-Identifier: GPL-2.0
package dev.codex.vowifi.tool;

import java.util.*;
import java.util.regex.*;

/** Fixed metadata only, sampled inside an ISms query window on a pinned ROM. */
final class SmsDispatcherWindow {
    private final Pattern line;
    private final long begin,end,duration;
    private int count;
    private boolean up,registered,capable,conflict,overflow;
    SmsDispatcherWindow(String pid,int slot,long begin,long end,long duration){
        if(pid==null||!pid.matches("[0-9]{1,10}")||slot<0||slot>7||begin<0||end<begin||duration<0||duration>15000||end-begin>15000||Math.abs(end-begin-duration)>100)
            throw new IllegalArgumentException("sms-query-window");
        this.begin=begin;this.end=end;this.duration=duration;
        line=Pattern.compile("^\\s*([0-9]{10})\\.([0-9]{3,6})\\s+"+pid+"\\s+[0-9]+\\s+[VDIWEF]\\s+ImsSmsDispatcher \\["+slot+"\\]:\\s+isAvailable: up=(true|false), reg=\\s*(true|false), cap=\\s*(true|false)\\s*$");
    }
    void accept(String raw){
        if(raw==null||raw.length()>512)return;
        Matcher match=line.matcher(raw);if(!match.matches())return;
        long timestamp=Long.parseLong(match.group(1))*1000+Long.parseLong((match.group(2)+"000").substring(0,3));
        if(timestamp<begin||timestamp>end)return;
        if(count==64){overflow=true;return;}
        boolean nextUp=Boolean.parseBoolean(match.group(3)),nextReg=Boolean.parseBoolean(match.group(4)),nextCap=Boolean.parseBoolean(match.group(5));
        if(count>0&&(up!=nextUp||registered!=nextReg||capable!=nextCap))conflict=true;
        up=nextUp;registered=nextReg;capable=nextCap;count++;
    }
    Map<String,Object> finish(boolean samePhone){
        Map<String,Object> out=new LinkedHashMap<>();
        out.put("schema",1);out.put("status","unknown");out.put("query_duration_ms",duration);
        out.put("sample_count",count);out.put("phone_identity_stable",samePhone);
        String reason=!samePhone?"phone-changed":overflow?"sample-limit":conflict?"state-changed":count==0?"no-window-sample":"none";
        out.put("reason",reason);
        if("none".equals(reason)){
            out.put("status","observed");out.put("service_up",up);out.put("registered",registered);
            out.put("sms_capable",capable);out.put("available",up&&registered&&capable);
        }
        return out;
    }
}
