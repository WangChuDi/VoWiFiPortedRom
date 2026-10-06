// SPDX-License-Identifier: GPL-2.0
import android.os.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** SettingsCmd uses a root external provider token, not an unregistered app thread. */
final class ModernRootSettings {
    private static void key(String key,boolean write)throws Exception {
        if(android.os.Process.myUid()!=0||Build.VERSION.SDK_INT<31||Build.VERSION.SDK_INT>37)throw new SecurityException("modern-root-settings-required");
        boolean lease=key!=null&&key.matches("codex_wfc_stack_slot_[0-7]_(sub|boot|until)");
        if(!lease&&(write||!Arrays.asList("boot_count","codex_wfc_stack_trial_boot","codex_wfc_stack_trial_until").contains(key)))throw new SecurityException("fixed-global-setting-required");
    }
    private static String command(String... args)throws Exception {
        ArrayList<String> argv=new ArrayList<>(Arrays.asList("settings","--user","0"));argv.addAll(Arrays.asList(args));
        java.lang.Process child=new ProcessBuilder(argv).redirectErrorStream(true).start();ByteArrayOutputStream bytes=new ByteArrayOutputStream();boolean[] failed={false};
        Thread reader=new Thread(()->{try(InputStream input=child.getInputStream()){byte[] block=new byte[2048];int length;while((length=input.read(block))!=-1){if(bytes.size()+length>2097152){failed[0]=true;break;}bytes.write(block,0,length);}}catch(IOException error){failed[0]=true;}});reader.setDaemon(true);reader.start();
        if(!child.waitFor(8,TimeUnit.SECONDS)){child.destroyForcibly();throw new IOException("settings-command-timeout");}reader.join(500);
        if(reader.isAlive()||failed[0]||child.exitValue()!=0)throw new IOException("settings-command-unavailable");return bytes.toString("UTF-8");
    }
    static String get(String name)throws Exception {
        key(name,false);String printed=command("get","global",name);
        if(!printed.endsWith("\n"))throw new IOException("settings-value-format-refused");String value=printed.substring(0,printed.length()-1);
        if(value.length()>256)throw new IOException("settings-value-too-large");
        if("null".equals(value)) {
            int matches=0;String prefix=name+"=";
            for(String line:command("list","global").split("\n",-1))if(line.startsWith(prefix))matches++;
            if(matches>1)throw new IOException("settings-presence-ambiguous");if(matches==0)return null;
        }
        return value;
    }
    static void put(String name,String value)throws Exception {
        key(name,true);if(value!=null&&value.length()>256)throw new IOException("settings-value-too-large");
        if(value==null)command("delete","global",name);else command("put","global",name,value);
        if(!Objects.equals(value,get(name)))throw new IOException("settings-write-unconfirmed");
    }
}
