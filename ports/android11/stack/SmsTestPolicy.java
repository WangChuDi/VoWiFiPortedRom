// SPDX-License-Identifier: GPL-2.0
import android.os.ServiceManager;
import com.android.internal.telephony.ISms;
import java.nio.file.*;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.*;
/** Fixed diagnostic caller only; no SMS traffic. Restore the returned policy after a test. */
public final class SmsTestPolicy {
    public static void main(String[] args)throws Exception {
        if(android.os.Process.myUid()!=0)throw new SecurityException("root-required");
        ISms sms=ISms.Stub.asInterface(ServiceManager.getService("isms"));
        if(sms==null)throw new IllegalStateException("sms-service-unavailable");
        String pkg="me.phh.ims";
        Path state=Paths.get("/data/adb/codex_vowifi_stack/sms-policy-before");
        if(args.length==1&&"read".equals(args[0])){
            System.out.println("short-code-policy="+sms.getPremiumSmsPermission(pkg));
        }else if(args.length==2&&"set".equals(args[0])){
            int mode=Integer.parseInt(args[1]);
            if(mode<1||mode>3)throw new IllegalArgumentException("policy-mode");
            sms.setPremiumSmsPermission(pkg,mode);
            System.out.println("short-code-policy="+sms.getPremiumSmsPermission(pkg));
        }else if(args.length==1&&"allow-info".equals(args[0])){
            boolean active=Files.exists(state.resolveSibling("transaction"));
            java.io.File[] owners=state.getParent().resolve("transactions").toFile().listFiles();
            if(owners!=null)for(java.io.File owner:owners)if(owner.getName().matches("slot-[0-7]-sub-[0-9]+")&&new java.io.File(owner,"transaction").isFile())active=true;
            if(!active)throw new IllegalStateException("trial-required");
            if(!Files.exists(state))Files.write(state,Integer.toString(sms.getPremiumSmsPermission(pkg)).getBytes("UTF-8"));
            sms.setPremiumSmsPermission(pkg,3);
            System.out.println("diagnostic-short-code=TEMPORARILY_ALLOWED");
        }else if(args.length==1&&"restore".equals(args[0])){
            if(!Files.exists(state))return;
            int before=Integer.parseInt(new String(Files.readAllBytes(state),"UTF-8"));
            if(before!=0){sms.setPremiumSmsPermission(pkg,before);Thread.sleep(500);}
            else {
                java.io.File policy=new java.io.File("/data/misc/sms/premium_sms_policy.xml");
                if(policy.exists()){
                    DocumentBuilderFactory factory=DocumentBuilderFactory.newInstance();
                    factory.setExpandEntityReferences(false);
                    Document document=factory.newDocumentBuilder().parse(policy);
                    NodeList entries=document.getElementsByTagName("package");
                    for(int i=entries.getLength()-1;i>=0;i--){
                        Element e=(Element)entries.item(i);
                        if(pkg.equals(e.getAttribute("name")))e.getParentNode().removeChild(e);
                    }
                    TransformerFactory.newInstance().newTransformer().transform(new DOMSource(document),new StreamResult(policy));
                }
            }
            Files.delete(state);
            // The controller immediately restarts phone to reload an originally absent policy.
            System.out.println("diagnostic-short-code=RESTORED; original="+before);
        }else throw new IllegalArgumentException("read|set 1..3|allow-info|restore");
    }
}
