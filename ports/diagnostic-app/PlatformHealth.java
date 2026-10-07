// SPDX-License-Identifier: GPL-2.0
package dev.codex.vowifi.tool;

import java.io.*;
import java.util.concurrent.TimeUnit;
import org.json.JSONObject;

/** Process continuity only. Never exports PIDs, process start times or raw output. */
final class PlatformHealth {
    static final class Sample {
        final String identity; final Boolean present;
        Sample(Boolean present,String identity){this.present=present;this.identity=identity;}
        static Sample unknown(){return new Sample(null,null);}
    }
    interface Source { Sample read(String process); }
    private final Source source;
    private volatile Sample[] initial;
    PlatformHealth(){this(PlatformHealth::readProcess);}
    PlatformHealth(Source source){this.source=source;}
    void begin(){initial=sample();}
    private Sample[] sample(){return new Sample[]{source.read("com.android.phone"),source.read("system_server")};}
    JSONObject finish()throws Exception{
        Sample[] before=initial,after=sample();
        if(before==null)before=new Sample[]{Sample.unknown(),Sample.unknown()};
        return new JSONObject().put("phone",compare(before[0],after[0]))
            .put("system_server",compare(before[1],after[1]));
    }
    static JSONObject compare(Sample before,Sample after)throws Exception{
        String state;
        if(before.present==null||after.present==null)state="unknown";
        else if(!before.present&&!after.present)state="absent";
        else if(!before.present||!after.present)state="changed";
        else if(before.identity==null||after.identity==null)state="unknown";
        else state=before.identity.equals(after.identity)?"stable":"changed";
        return new JSONObject().put("state",state)
            .put("first_present",before.present==null?JSONObject.NULL:before.present)
            .put("last_present",after.present==null?JSONObject.NULL:after.present);
    }
    static boolean stable(JSONObject health){
        JSONObject phone=health==null?null:health.optJSONObject("phone");
        JSONObject system=health==null?null:health.optJSONObject("system_server");
        return phone!=null&&system!=null&&"stable".equals(phone.optString("state"))&&"stable".equals(system.optString("state"));
    }
    static Sample parse(int exit,String output,String stat){
        String pid=output.trim();
        if(exit==1&&pid.isEmpty())return new Sample(false,null);
        if(exit!=0||!pid.matches("[0-9]{1,10}")||stat==null)return Sample.unknown();
        int close=stat.lastIndexOf(')');
        if(close<0||!stat.startsWith(pid+" ("))return Sample.unknown();
        String[] fields=stat.substring(close+1).trim().split("\\s+");
        if(fields.length<=19||!fields[19].matches("[0-9]{1,20}"))return Sample.unknown();
        return new Sample(true,pid+":"+fields[19]);
    }
    static Sample readProcess(String name){
        java.lang.Process child=null;
        try{
            child=new ProcessBuilder("pidof",name).redirectErrorStream(true).start();
            final InputStream stream=child.getInputStream();
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();
            Thread reader=new Thread(()->{try(InputStream in=stream){byte[] block=new byte[512];int n;while((n=in.read(block))>=0){if(bytes.size()+n>4096)return;bytes.write(block,0,n);}}catch(IOException ignored){}});
            reader.setDaemon(true);reader.start();
            if(!child.waitFor(750,TimeUnit.MILLISECONDS)){child.destroyForcibly();return Sample.unknown();}
            reader.join(100);if(reader.isAlive())return Sample.unknown();
            String output=bytes.toString("UTF-8"),pid=output.trim(),stat=null;
            if(child.exitValue()==0&&pid.matches("[0-9]{1,10}")){
                try(InputStream in=new FileInputStream("/proc/"+pid+"/stat")){
                    byte[] data=new byte[4097];int count=0,n;
                    while(count<data.length&&(n=in.read(data,count,data.length-count))>0)count+=n;
                    if(count<=4096)stat=new String(data,0,count,"UTF-8");
                }
            }
            return parse(child.exitValue(),output,stat);
        }catch(Exception ignored){return Sample.unknown();}
        finally{if(child!=null)child.destroy();}
    }
}
