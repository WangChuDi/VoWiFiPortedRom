// SPDX-License-Identifier: GPL-2.0
import android.app.ActivityThread;
import android.os.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.*;

/** Exact-context read-only queries for the original fixture. Never print setting values. */
public final class Api37SettingsReadOnlyProbe {
    private static final String[] KEYS={"boot_count","codex_wfc_stack_slot_0_sub","codex_wfc_stack_slot_0_boot","codex_wfc_stack_slot_0_until"};
    private static final String[] IDS={"boot-count","lease-sub","lease-boot","lease-until"};
    private static final int CAP=8192;
    static void checkpoint(String stage)throws Exception{System.out.println(new JSONObject().put("schema",1).put("sdk",37).put("checkpoint",stage));System.out.flush();}
    static String category(String value){
        if(value==null)return "ABSENT";
        if(value.equals("null"))return "NULL_LITERAL";
        if(value.isEmpty())return "EMPTY";
        return value.matches("-?[0-9]{1,20}")?"DECIMAL":"UNEXPECTED";
    }
    static String origin(Throwable error){
        for(StackTraceElement frame:error.getStackTrace())if(frame.getClassName().equals("ModernRootSettings"))return "ModernRootSettings."+frame.getMethodName()+":"+frame.getLineNumber();
        return "external";
    }
    static final class Reader implements Runnable {
        final InputStream stream;final ByteArrayOutputStream data=new ByteArrayOutputStream();
        final AtomicBoolean overflow=new AtomicBoolean(),failed=new AtomicBoolean();
        Reader(InputStream stream){this.stream=stream;}
        public void run(){try(InputStream input=stream){byte[] buffer=new byte[2048];int n;while((n=input.read(buffer))!=-1){if(!overflow.get()&&data.size()+n<=CAP)data.write(buffer,0,n);else overflow.set(true);}}catch(IOException ignored){failed.set(true);}}
    }
    static JSONArray errors(String text){
        JSONArray result=new JSONArray();
        for(String name:new String[]{"SecurityException","DeadObjectException","DeadSystemException","NullPointerException","IllegalStateException","IllegalArgumentException","RemoteException","TransactionTooLargeException"})if(text.contains(name))result.put(name);
        for(String name:new String[]{"Permission denial","Unknown command","Can't find service","Failure calling service","Failed transaction"})if(text.contains(name))result.put(name);
        return result;
    }
    static JSONObject child(String key,boolean nativeCmd)throws Exception {
        ArrayList<String> argv=new ArrayList<>();argv.add(nativeCmd?"/system/bin/cmd":"settings");if(nativeCmd)argv.add("settings");
        Collections.addAll(argv,"--user","0","get","global",key);
        long began=SystemClock.elapsedRealtime();java.lang.Process process=new ProcessBuilder(argv).start();
        Reader stdout=new Reader(process.getInputStream()),stderr=new Reader(process.getErrorStream());
        Thread outThread=new Thread(stdout,"settings-stdout"),errThread=new Thread(stderr,"settings-stderr");outThread.setDaemon(true);errThread.setDaemon(true);outThread.start();errThread.start();
        JSONObject out=new JSONObject().put("kind",nativeCmd?"app-child-cmd":"app-child-settings");
        boolean exited=process.waitFor(8,TimeUnit.SECONDS);out.put("deadline_exceeded",!exited);
        if(!exited){process.destroyForcibly();exited=process.waitFor(3,TimeUnit.SECONDS);}
        out.put("child_terminal_confirmed",exited);if(!exited)throw new IOException("settings-child-termination-unconfirmed");
        outThread.join(1000);errThread.join(1000);
        boolean complete=!outThread.isAlive()&&!errThread.isAlive()&&!stdout.failed.get()&&!stderr.failed.get()&&!stdout.overflow.get()&&!stderr.overflow.get();
        out.put("child_exit",process.exitValue()).put("output_complete",complete).put("stdout_reader_terminal",!outThread.isAlive()).put("stderr_reader_terminal",!errThread.isAlive()).put("output_overflow",stdout.overflow.get()||stderr.overflow.get());
        if(complete){String value=stdout.data.toString("UTF-8"),error=stderr.data.toString("UTF-8");
            out.put("stdout_bytes",stdout.data.size()).put("stderr_bytes",stderr.data.size()).put("error_categories",errors(value+"\n"+error));
            boolean one=value.endsWith("\n")&&value.indexOf('\n')==value.length()-1;
            out.put("single_value_line",one);if(one)out.put("value_category",category(value.substring(0,value.length()-1)));
        }
        return out.put("elapsed_ms",SystemClock.elapsedRealtime()-began);
    }
    static JSONObject retained(String key)throws Exception {
        long began=SystemClock.elapsedRealtime();JSONObject out=new JSONObject().put("kind","retained-helper-get");
        try{String value=ModernRootSettings.get(key);out.put("call_completed",true).put("value_category",category(value));}
        catch(Exception error){out.put("call_completed",false).put("error",error.getClass().getSimpleName()).put("origin",origin(error));}
        return out.put("elapsed_ms",SystemClock.elapsedRealtime()-began);
    }
    public static void main(String[]args){JSONObject out=new JSONObject();boolean success=false;
        try{
            if(args.length!=0||android.os.Process.myUid()!=0||Build.VERSION.SDK_INT!=37||!"1".equals(SystemProperties.get("ro.kernel.qemu"))||!"CodexVoWiFiApi37".equals(SystemProperties.get("ro.boot.qemu.avd_name")))throw new SecurityException("owned-api37-required");
            out.put("schema",1).put("sdk",37).put("action","settings-read-only").put("read_only",true).put("settings_written",false).put("new_baseline_created",false).put("physical_phone_modified",false);
            checkpoint("context");Looper.prepareMainLooper();ActivityThread.systemMain().getSystemContext();ModernInstallationController.initializeTelephony();
            out.put("parent_root_confirmed",true).put("inherited_classpath_present",System.getenv("CLASSPATH")!=null).put("inherited_library_path_present",System.getenv("LD_LIBRARY_PATH")!=null);
            JSONArray cases=new JSONArray();out.put("cases",cases);
            for(int i=0;i<KEYS.length;i++){
                checkpoint(IDS[i]);JSONObject row=new JSONObject().put("query",IDS[i]);JSONArray results=new JSONArray();row.put("results",results);cases.put(row);
                results.put(child(KEYS[i],false));results.put(child(KEYS[i],true));results.put(retained(KEYS[i]));
                System.out.println(new JSONObject().put("schema",1).put("sdk",37).put("query_observed",row));System.out.flush();
            }
            success=true;
        }catch(Throwable error){try{out.put("error",error.getClass().getSimpleName());}catch(Exception ignored){}}
        try{out.put("status",success?"observed":"failed");}catch(Exception ignored){}
        System.out.println(out);System.out.flush();System.exit(success?0:1);
    }
}
