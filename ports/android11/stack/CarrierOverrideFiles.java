// SPDX-License-Identifier: GPL-2.0
import android.content.Context;
import android.telephony.*;
import android.os.PersistableBundle;
import android.util.Xml;
import org.xmlpull.v1.XmlPullParser;
import java.io.*;
import java.util.concurrent.TimeUnit;

/** Runtime loader filename selection, including verified MIUI MCC/MNC suffixes. */
public final class CarrierOverrideFiles {
    private CarrierOverrideFiles(){}
    public static PersistableBundle readBundle(File file)throws Exception {
        if(!file.isFile()||file.length()>1048576)throw new IOException("override-file-unavailable");
        try(InputStream stream=new FileInputStream(file)){
            XmlPullParser parser=Xml.newPullParser();parser.setInput(stream,"UTF-8");
            int event;do{event=parser.next();}while(event!=XmlPullParser.START_TAG&&event!=XmlPullParser.END_DOCUMENT);
            if(event!=XmlPullParser.START_TAG||!"bundle".equals(parser.getName()))throw new IOException("override-XML-format-unverified");
            return PersistableBundle.restoreFromXml(parser);
        }
    }
    public static OverrideFileStore.Target target(Context context,SubscriptionInfo selected,Class<?> api,Object loader,File state)throws Exception {
        TelephonyManager manager=context.getSystemService(TelephonyManager.class).createForSubscriptionId(selected.getSubscriptionId());
        String serial=selected.getIccId();
        if(serial==null||serial.isEmpty())serial=manager.getSimSerialNumber();
        String carrierPackage=(String)api.getMethod("getDefaultCarrierServicePackageName").invoke(loader);
        File directory=context.createPackageContext("com.android.phone",0).createDeviceProtectedStorageContext().getFilesDir();
        if(!directory.isDirectory()||!directory.canRead()||directory.listFiles()==null)throw new IOException("override-directory-unavailable");
        // The controller still uses this user-0 layout; refuse another location
        // until both controller and recovery support that layout explicitly.
        if(!directory.getCanonicalFile().equals(new File("/data/user_de/0/com.android.phone/files").getCanonicalFile()))throw new IOException("override-layout-unverified");
        java.util.LinkedHashMap<String,OverrideFileStore.Target> candidates=new java.util.LinkedHashMap<>();
        int specific=manager.getSimSpecificCarrierId(),general=manager.getSimCarrierId();
        String operator=manager.getSimOperator();
        int[] codes=operator!=null&&operator.matches("[0-9]{5,6}")?
            new int[]{specific,general,Integer.parseInt(operator)}:new int[]{specific,general};
        for(int code:codes){OverrideFileStore.Target candidate=new OverrideFileStore.Target(directory,carrierPackage,serial,code);candidates.put(candidate.file.getName(),candidate);}
        OverrideFileStore.Target found=null;
        for(OverrideFileStore.Target candidate:candidates.values())if(candidate.file.isFile()){
            if(found!=null)throw new IOException("override-schema-ambiguous");found=candidate;
        }
        if(found!=null)return found;
        // A confirmed transaction keeps its exact filename across the loader's
        // asynchronous delete, instead of guessing a new filename at clear.
        if(new File(state,"persistence.properties").isFile())return store(state).savedTarget(directory,carrierPackage,serial);
        // The same loader naming scheme applies to its ordinary platform cache.
        // This supplies evidence before the first override exists.
        for(OverrideFileStore.Target candidate:candidates.values()){
            File cache=new File(directory,"carrierconfig-"+carrierPackage+"-"+serial+"-"+candidate.file.getName().substring(("carrierconfig-"+carrierPackage+"-override-"+serial+"-").length()));
            if(cache.isFile()){if(found!=null)throw new IOException("override-schema-ambiguous");found=candidate;}
        }
        if(found==null)throw new IOException("override-schema-unverified");
        return found;
    }
    public static OverrideFileStore store(File state)throws IOException {
        return new OverrideFileStore(state,new OverrideFileStore.Copier(){
            public void copy(File source,File destination)throws IOException {
                // Preserve phone ownership, mode and timestamps, as the original
                // tested shell rollback did. stderr may contain ICCID filenames:
                // consume it privately and report only a fixed failure code.
                runFileCommand("override-copy","cp","-p",source.getPath(),destination.getPath());
            }
        });
    }
    public static void restoreLabel(OverrideFileStore.Target target)throws IOException {
        if(target.file.isFile())runFileCommand("override-label","restorecon",target.file.getPath());
    }
    private static void runFileCommand(String reason,String...command)throws IOException {
        Process process=new ProcessBuilder(command).redirectErrorStream(true).start();
        try{
            if(!process.waitFor(5,TimeUnit.SECONDS)){process.destroyForcibly();process.waitFor(1,TimeUnit.SECONDS);throw new IOException(reason+"-timeout");}
            if(process.exitValue()!=0)throw new IOException(reason+"-failed");
        }catch(InterruptedException interrupted){process.destroyForcibly();Thread.currentThread().interrupt();throw new IOException(reason+"-interrupted");}
        finally{try{process.getInputStream().close();process.getOutputStream().close();process.getErrorStream().close();}catch(IOException ignored){}}
    }
}
