import android.telephony.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.*;

/** Receive/deliver loop, with separately opt-in one-shot development send/decode modes. */
public final class ImsSmsProbe {
    static final class Frame {
        String start;
        Map<String,String> headers=new HashMap<>();
        byte[] body;
        OutputStream reply;
    }
    public static void run(android.content.Context context, TelephonyManager tm, Socket socket, ServerSocket listener, BufferedReader reader,
            Map<String,String> registered, String initial, String verify) throws Exception {
        String destination=System.getenv("CODEX_SMS_TEST_DEST");
        boolean receiveOnly="1".equals(System.getenv("CODEX_SMS_RECEIVE_ONLY"));
        boolean deliver="1".equals(System.getenv("CODEX_SMS_DELIVER"));
        boolean resident=deliver&&"1".equals(System.getenv("CODEX_SMS_RESIDENT"));
        int window=receiveOnly?300:25;
        if(resident) {
            int requested=Integer.parseInt(System.getenv("CODEX_SMS_SESSION_SECONDS"));
            if(requested<30||requested>900)throw new IOException("Invalid resident session duration");
            int grant=1800;
            Matcher expires=Pattern.compile("(?i)(?:^|[;,\\s])expires\\s*=\\s*([0-9]+)").matcher(registered.getOrDefault("contact",""));
            while(expires.find())grant=Math.min(grant,Integer.parseInt(expires.group(1)));
            if(registered.containsKey("expires"))grant=Math.min(grant,Integer.parseInt(registered.get("expires")));
            if(grant<60)throw new IOException("Registration grant too short");
            window=Math.min(requested,grant-Math.min(120,grant/2));
        }
        String text=System.getenv("CODEX_SMS_TEST_TEXT");
        if(text==null)text="VoWiFi test";
        boolean balance="21200".equals(destination)&&"BALANCE".equals(text);
        if(!receiveOnly&&!balance&&(destination==null||!destination.matches("\\+[1-9][0-9]{6,14}")||!"VoWiFi test".equals(text)))
            throw new IllegalArgumentException("An explicitly authorized E164 test destination is required");
        if(!"23415".equals(tm.getSimOperator()))throw new IllegalStateException("Unexpected carrier");
        byte[] rp=new byte[0];
        SmsMessage.SubmitPdu tp=null;
        if(!receiveOnly) {
        String raw=SmsManager.getSmsManagerForSubscriptionId(tm.getSubscriptionId()).getSmscAddress();
        String smsc=(String)Class.forName("me.phh.sip.SmsAddressKt").getMethod("normalizeSmsc",String.class).invoke(null,raw);
        if(smsc==null)throw new IOException("No valid SMSC");
        tp=SmsMessage.getSubmitPdu(null,destination,text,false);
        if(tp==null||tp.encodedMessage==null)throw new IOException("Cannot encode SMS");
        rp=(byte[])Class.forName("me.phh.sip.SmsKt").getMethod("SipSmsEncodeSms",byte.class,String.class,byte[].class)
            .invoke(null,(byte)1,smsc,tp.encodedMessage);
        }
        String identity=null;
        Matcher impu=Pattern.compile("<?((?:sip:|tel:)[^>,\\s]+)").matcher(registered.getOrDefault("p-associated-uri",""));
        if(impu.find())identity=impu.group(1);
        if(identity==null) {
            Matcher from=Pattern.compile("(?m)^From: <([^>]+)>").matcher(initial);
            if(from.find())identity=from.group(1);
        }
        if(identity==null)throw new IOException("Missing public identity");
        String callId=UUID.randomUUID().toString();
        String host=socket.getLocalAddress().getHostAddress();
        if(socket.getLocalAddress() instanceof Inet6Address)host="["+host+"]";
        String psi="sip:ipsmms1mc05.ims.mnc015.mcc234.3gppnetwork.org";
        String route=registered.get("service-route");
        String message="MESSAGE "+psi+" SIP/2.0\r\nVia: SIP/2.0/TCP "+host+":"+socket.getLocalPort()
            +";branch=z9hG4bK"+UUID.randomUUID()+";rport\r\nMax-Forwards: 70\r\nFrom: <"+identity+">;tag="+UUID.randomUUID()
            +"\r\nTo: <"+psi+">\r\nCall-ID: "+callId+"\r\nCSeq: 1 MESSAGE\r\nP-Preferred-Identity: <"+identity+">\r\n"
            +(route==null?"":"Route: "+route+"\r\n")
            +"Security-Verify: "+verify+"\r\nSupported: sec-agree\r\nRequire: sec-agree\r\nProxy-Require: sec-agree\r\n"
            +"Accept-Contact: *;+g.3gpp.smsip;require;explicit\r\nContent-Type: application/vnd.3gpp.sms\r\nContent-Length: "+rp.length+"\r\n\r\n";
        AtomicBoolean stop=new AtomicBoolean(false);
        BlockingQueue<Frame> queue=new LinkedBlockingQueue<>(16);
        ExecutorService workers=Executors.newFixedThreadPool(2,r->{Thread t=new Thread(r,"sms-probe-reader");t.setDaemon(true);return t;});
        socket.setSoTimeout(2000);listener.setSoTimeout(1000);
        Future<?> control=workers.submit(()->pump(reader,socket,stop,queue));
        Future<?> inbound=workers.submit(()->{
            try {
                while(!stop.get()) {
                    try(Socket accepted=listener.accept()) {
                        accepted.setSoTimeout(2000);
                        BufferedReader incoming=new BufferedReader(new InputStreamReader(accepted.getInputStream(),StandardCharsets.ISO_8859_1));
                        pump(incoming,accepted,stop,queue);
                    }catch(SocketTimeoutException idle){}
                }
            }catch(IOException ignored){}
        });
        try {
            OutputStream out=socket.getOutputStream();
            if(!receiveOnly) {
                synchronized(out){out.write(message.getBytes(StandardCharsets.US_ASCII));out.write(rp);out.flush();}
                System.out.println("sms-message=SENT_ONCE; text="+text+"; destination="+(balance?"21200":"REDACTED"));
            } else System.out.println(deliver?"sms-delivery=READY; seconds="+window+"; resident="+resident+"; persist-before-ack=true": "1".equals(System.getenv("CODEX_SMS_OBSERVE_ONLY"))?
                "sms-observe=READY; seconds=300; decode="+"1".equals(System.getenv("CODEX_SMS_DECODE"))+"; sms-submit=DISABLED":
                "sms-receive=READY; seconds=300; expected-text=VoWiFi test; sms-submit=DISABLED");
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(window);
            long heartbeat=System.nanoTime()+TimeUnit.SECONDS.toNanos(20);
            boolean sipAccepted=false,rpAccepted=false,reported=false,balanceReply=false;
            ArrayDeque<Frame> deferred=new ArrayDeque<>();
            receiveLoop: while(System.nanoTime()<deadline) {
                if(resident&&new File("/data/local/tmp/codex-vowifi-sms/stop").exists()) {
                    System.out.println("sms-resident=STOP_REQUESTED");break;
                }
                if(control.isDone())throw new IOException("IMS control stream closed");
                if(receiveOnly&&System.nanoTime()>=heartbeat) {
                    synchronized(out){out.write(new byte[]{13,10,13,10});out.flush();}
                    heartbeat=System.nanoTime()+TimeUnit.SECONDS.toNanos(20);
                }
                Frame frame=deferred.isEmpty()?queue.poll(Math.max(1,Math.min(TimeUnit.SECONDS.toNanos(1),deadline-System.nanoTime())),TimeUnit.NANOSECONDS):deferred.removeFirst();
                if(frame==null)continue;
                if(frame.start.startsWith("SIP/2.0 ")) {
                    if(!callId.equals(frame.headers.get("call-id")))continue;
                    int code=Integer.parseInt(frame.start.substring(8,11));
                    System.out.println("sms-sip-status="+code);
                    if(code>=300){System.out.println("sms-relay=REJECTED");return;}
                    if(code==200||code==202)sipAccepted=true;
                } else {
                    boolean sms=frame.start.startsWith("MESSAGE ") &&
                        frame.headers.getOrDefault("content-type","").toLowerCase(Locale.ROOT).startsWith("application/vnd.3gpp.sms");
                    if((balance||receiveOnly) && sms && frame.body.length>2 && frame.body[0]==1) {
                        if(receiveOnly&&"1".equals(System.getenv("CODEX_SMS_OBSERVE_ONLY"))) {
                            System.out.println("sms-observe=RP_DATA_ARRIVED; rp-ack=NOT_SENT; inbox-integration=NONE");
                            if("1".equals(System.getenv("CODEX_SMS_DECODE"))) {
                                try {
                                    Object decoded=Class.forName("me.phh.sip.SmsKt").getMethod("SipSmsDecode",byte[].class).invoke(null,(Object)frame.body);
                                    byte[] pdu=decoded==null?null:(byte[])decoded.getClass().getMethod("getPdu").invoke(decoded);
                                    SmsMessage incoming=pdu==null?null:SmsMessage.createFromPdu(pdu,"3gpp");
                                    if(incoming==null)System.out.println("sms-decode=FAILED");
                                    else {
                                        System.out.println("sms-decode=SUCCESS; scope=SINGLE_TPDU");
                                        System.out.println("sms-decoded-sender="+org.json.JSONObject.quote(incoming.getOriginatingAddress()));
                                        System.out.println("sms-decoded-body="+org.json.JSONObject.quote(incoming.getMessageBody()));
                                    }
                                    if(pdu!=null)Arrays.fill(pdu,(byte)0);
                                } catch(Exception failure) {System.out.println("sms-decode=FAILED; error="+failure.getClass().getSimpleName());}
                            }
                            reply(frame,480);
                            Arrays.fill(frame.body,(byte)0);
                            System.out.println("sms-observe-response=480; network-redelivery=NOT_GUARANTEED");
                            return;
                        }
                        Object decoded=Class.forName("me.phh.sip.SmsKt").getMethod("SipSmsDecode",byte[].class).invoke(null,(Object)frame.body);
                        byte[] pdu=(byte[])decoded.getClass().getMethod("getPdu").invoke(decoded);
                        SmsMessage incoming=SmsMessage.createFromPdu(pdu,"3gpp");
                        String sender=incoming==null?null:incoming.getOriginatingAddress();
                        if(deliver) {
                            int tpOffset=pdu==null||pdu.length==0?0:1+(pdu[0]&255);
                            if(incoming==null||sender==null||incoming.getMessageBody()==null||tpOffset>=pdu.length||
                                (pdu[tpOffset]&0x43)!=0||incoming.isReplace()||incoming.isStatusReportMessage()) {
                                System.out.println("sms-delivery=UNSUPPORTED_TPDU; rp-ack=NOT_SENT");reply(frame,480);if(resident)continue receiveLoop;return;
                            }
                            String service=frame.headers.getOrDefault("p-asserted-identity",frame.headers.getOrDefault("from",""));
                            Matcher gateway=Pattern.compile("<?(sip:[^>;\\s]+)").matcher(service);
                            if(!gateway.find())throw new IOException("Missing SMS gateway identity");
                            String target=gateway.group(1);
                            android.net.Uri saved;
                            try {saved=saveInbox(context,incoming,tm.getSubscriptionId(),pdu);}
                            catch(Exception failed){System.out.println("sms-inbox=FAILED; error="+failed.getClass().getSimpleName()+"; reason="+failed.getMessage());reply(frame,480);return;}
                            System.out.println("sms-inbox=VERIFIED; uri="+saved);
                            reply(frame,200);
                            byte[] ack=(byte[])Class.forName("me.phh.sip.SmsKt").getMethod("SipSmsEncodeAck",byte.class).invoke(null,frame.body[1]);
                            String ackId=UUID.randomUUID().toString();
                            String request="MESSAGE "+target+" SIP/2.0\r\nVia: SIP/2.0/TCP "+host+":"+socket.getLocalPort()+
                                ";branch=z9hG4bK"+UUID.randomUUID()+";rport\r\nMax-Forwards: 70\r\nFrom: <"+identity+">;tag="+UUID.randomUUID()+
                                "\r\nTo: <"+target+">\r\nCall-ID: "+ackId+"\r\nCSeq: 1 MESSAGE\r\nP-Preferred-Identity: <"+identity+">\r\n"+
                                "In-Reply-To: "+frame.headers.get("call-id")+"\r\n"+(route==null?"":"Route: "+route+"\r\n")+
                                "Security-Verify: "+verify+"\r\nContent-Type: application/vnd.3gpp.sms\r\nContent-Length: "+ack.length+"\r\n\r\n";
                            synchronized(out){out.write(request.getBytes(StandardCharsets.US_ASCII));out.write(ack);out.flush();}
                            System.out.println("sms-delivery-rp-ack=SENT");
                            long ackDeadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(12);
                            while(System.nanoTime()<ackDeadline) {
                                Frame answer=queue.poll(Math.max(1,ackDeadline-System.nanoTime()),TimeUnit.NANOSECONDS);
                                if(answer==null)break;
                                if(answer.start.startsWith("SIP/2.0 ")&&ackId.equals(answer.headers.get("call-id"))&&"1 MESSAGE".equals(answer.headers.get("cseq"))) {
                                    int status=Integer.parseInt(answer.start.substring(8,11));
                                    if(status>=200){System.out.println("sms-delivery-ack-sip-status="+status);if(resident)continue receiveLoop;return;}
                                } else {
                                    if(deferred.size()>=16)throw new IOException("Deferred message queue full");
                                    deferred.addLast(answer);
                                }
                            }
                            System.out.println("sms-delivery-ack-sip-status=UNCONFIRMED_TIMEOUT");if(resident)continue receiveLoop;return;
                        }
                        if((receiveOnly&&incoming!=null&&"VoWiFi test".equals(incoming.getMessageBody()))||
                           (balance&&sender!=null&&Arrays.asList("21200","voxi","vodafone").contains(sender.toLowerCase(Locale.ROOT)))) {
                            reply(frame,200);
                            byte[] ack=(byte[])Class.forName("me.phh.sip.SmsKt").getMethod("SipSmsEncodeAck",byte.class).invoke(null,frame.body[1]);
                            String from=frame.headers.getOrDefault("from","");
                            Matcher source=Pattern.compile("<?(sip:[^>;\\s]+)").matcher(from);
                            if(!source.find())throw new IOException("Missing SMS service identity");
                            String ackMessage="MESSAGE "+source.group(1)+" SIP/2.0\r\nVia: SIP/2.0/TCP "+host+":"+socket.getLocalPort()
                                +";branch=z9hG4bK"+UUID.randomUUID()+";rport\r\nMax-Forwards: 70\r\nFrom: <"+identity+">;tag="+UUID.randomUUID()
                                +"\r\nTo: <"+source.group(1)+">\r\nCall-ID: "+UUID.randomUUID()+"\r\nCSeq: 1 MESSAGE\r\n"
                                +"In-Reply-To: "+frame.headers.get("call-id")+"\r\n"
                                +(route==null?"":"Route: "+route+"\r\n")
                                +"Security-Verify: "+verify+"\r\nContent-Type: application/vnd.3gpp.sms\r\nContent-Length: "+ack.length+"\r\n\r\n";
                            synchronized(out){out.write(ackMessage.getBytes(StandardCharsets.US_ASCII));out.write(ack);out.flush();}
                            String body=incoming.getMessageBody();
                            if(receiveOnly) {
                                System.out.println("sms-receive=TEST_MESSAGE_DECODED; sip-response=200; rp-ack=SENT; inbox-integration=NONE");
                                return;
                            }
                            System.out.println("balance-reply-sender="+sender);
                            System.out.println("balance-reply="+(body==null?"EMPTY":body.replace('\r',' ').replace('\n',' ')));
                            balanceReply=true;
                            if(sipAccepted&&rpAccepted)return;
                            continue;
                        }
                    }
                    boolean expected=sms&&frame.body.length>=2&&(frame.body[1]&255)==1 &&
                        (frame.body[0]==3||frame.body[0]==5);
                    reply(frame,expected?200:480);
                    if(expected) {
                        if(frame.body[0]==5) {
                            Object cause=Class.forName("me.phh.sip.SmsAddressKt").getMethod("decodeRpErrorCause",byte[].class).invoke(null,(Object)frame.body);
                            System.out.println("sms-rp-error-cause="+cause);return;
                        }
                        rpAccepted=true;System.out.println("sms-rp-ack=RECEIVED");
                    }
                }
                if(sipAccepted&&rpAccepted){
                    if(!reported) {System.out.println("sms-relay=ACCEPTED; recipient-delivery=UNVERIFIED");reported=true;}
                    if(!balance||balanceReply)return;
                }
            }
            System.out.println(resident?"sms-resident=SESSION_FINISHED":receiveOnly?"sms-receive=NO_MATCHING_TEST_WITHIN_WINDOW":reported?"balance-reply=NOT_RECEIVED_WITHIN_WINDOW":"sms-relay=UNCONFIRMED_TIMEOUT; automatic-retry=DISABLED");
        } finally {
            stop.set(true);
            try {control.get(3,TimeUnit.SECONDS);inbound.get(3,TimeUnit.SECONDS);}
            finally {workers.shutdownNow();socket.setSoTimeout(8000);}
            Arrays.fill(rp,(byte)0);if(tp!=null)Arrays.fill(tp.encodedMessage,(byte)0);
        }
    }
    private static android.net.Uri saveInbox(android.content.Context context,SmsMessage sms,int subId,byte[] pdu) throws Exception {
        android.os.IBinder token=new android.os.Binder();
        android.app.IActivityManager am=android.app.ActivityManager.getService();
        android.app.ContentProviderHolder holder=am.getContentProviderExternal("sms",0,token,"codex-ims-inbox");
        if(holder==null||holder.provider==null)throw new IOException("SMS provider unavailable");
        try {
        java.lang.reflect.Constructor<android.content.ContentProviderClient> constructor=android.content.ContentProviderClient.class.getDeclaredConstructor(
            android.content.ContentResolver.class,android.content.IContentProvider.class,boolean.class);
        constructor.setAccessible(true);
        try(android.content.ContentProviderClient resolver=constructor.newInstance(context.getContentResolver(),holder.provider,true)) {
        android.net.Uri inbox=android.net.Uri.parse("content://sms/inbox");
        String[] args={sms.getOriginatingAddress(),sms.getMessageBody(),Long.toString(sms.getTimestampMillis()),Integer.toString(subId)};
        android.net.Uri saved=null;
        try(android.database.Cursor c=resolver.query(inbox,new String[]{"_id"},"address=? AND body=? AND date_sent=? AND sub_id=?",args,null)) {
            if(c!=null&&c.moveToFirst())saved=android.content.ContentUris.withAppendedId(android.net.Uri.parse("content://sms"),c.getLong(0));
        }
        if(saved==null&&"1".equals(System.getenv("CODEX_SMS_FRAMEWORK"))) {
            deliverToDefaultSmsApp(context,pdu,subId);
            System.out.println("sms-framework=SMS_DELIVER_REQUESTED; direct-insert=DISABLED");
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(15);
            while(saved==null&&System.nanoTime()<deadline) {
                Thread.sleep(250);
                try(android.database.Cursor c=resolver.query(inbox,new String[]{"_id"},"address=? AND body=? AND date_sent=? AND sub_id=?",args,null)) {
                    if(c!=null&&c.moveToFirst())saved=android.content.ContentUris.withAppendedId(android.net.Uri.parse("content://sms"),c.getLong(0));
                }
            }
            if(saved==null)throw new IOException("Framework inbox delivery not confirmed within 15 seconds");
            System.out.println("sms-framework=INBOX_CONFIRMED");
        } else if(saved==null) {
            android.content.ContentValues values=new android.content.ContentValues();
            values.put("address",sms.getOriginatingAddress());values.put("body",sms.getMessageBody());
            values.put("date",System.currentTimeMillis());values.put("date_sent",sms.getTimestampMillis());
            values.put("sub_id",subId);values.put("read",0);values.put("seen",0);
            values.put("protocol",sms.getProtocolIdentifier());values.put("service_center",sms.getServiceCenterAddress());
            saved=resolver.insert(inbox,values);
        }
        if(saved==null)throw new IOException("SMS provider did not insert");
        try(android.database.Cursor c=resolver.query(saved,new String[]{"address","body","sub_id","type"},null,null,null)) {
            if(c==null||!c.moveToFirst()||!sms.getOriginatingAddress().equals(c.getString(0))||!sms.getMessageBody().equals(c.getString(1))||c.getInt(2)!=subId||c.getInt(3)!=1)
                throw new IOException("SMS provider readback mismatch");
        }
        return saved;
        }
        } finally {am.removeContentProviderExternalAsUser("sms",token,0);}
    }
    private static void deliverToDefaultSmsApp(android.content.Context context,byte[] pdu,int subId) throws Exception {
        android.app.role.RoleManager roles=context.getSystemService(android.app.role.RoleManager.class);
        List<String> holders=roles.getRoleHolders(android.app.role.RoleManager.ROLE_SMS);
        if(holders.size()!=1)throw new IOException("Default SMS role holder is ambiguous");
        android.content.Intent intent=new android.content.Intent("android.provider.Telephony.SMS_DELIVER");
        intent.setPackage(holders.get(0));
        List<android.content.pm.ResolveInfo> receivers=context.getPackageManager().queryBroadcastReceivers(intent,0);
        if(receivers.size()!=1)throw new IOException("Default SMS delivery receiver is ambiguous");
        android.content.pm.ActivityInfo receiver=receivers.get(0).activityInfo;
        if(!"android.permission.BROADCAST_SMS".equals(receiver.permission))throw new IOException("SMS receiver is not protected");
        intent.setComponent(new android.content.ComponentName(receiver.packageName,receiver.name));
        intent.putExtra("pdus",new Object[]{pdu});intent.putExtra("format","3gpp");
        int slot=SubscriptionManager.getSlotIndex(subId);
        intent.putExtra("subscription",subId);intent.putExtra("android.telephony.extra.SUBSCRIPTION_INDEX",subId);
        intent.putExtra("phone",slot);intent.putExtra("slot",slot);intent.putExtra("android.telephony.extra.SLOT_INDEX",slot);
        intent.addFlags(android.content.Intent.FLAG_RECEIVER_FOREGROUND|android.content.Intent.FLAG_RECEIVER_NO_ABORT|0x01000000);
        // app_process has no registered IApplicationThread. Use the system-UID Binder caller.
        int result=android.app.ActivityManager.getService().broadcastIntentWithFeature(null,null,intent,null,null,
            android.app.Activity.RESULT_OK,null,null,new String[]{"android.permission.RECEIVE_SMS"},-1,null,true,false,0);
        if(result!=0)throw new IOException("SMS delivery broadcast rejected: "+result);
        System.out.println("sms-framework-receiver="+receiver.packageName);
    }
    static void pump(BufferedReader reader,Socket socket,AtomicBoolean stop,BlockingQueue<Frame> queue) {
        try {
            while(!stop.get()) {
                try {
                    Frame frame=readFrame(reader,stop);
                    if(frame==null)return;
                    frame.reply=socket.getOutputStream();
                    if(!queue.offer(frame))throw new IOException("Receive queue full");
                }catch(SocketTimeoutException idle){throw idle;}
            }
        }catch(IOException failure){if(!stop.get())System.out.println("sms-stream-ended="+failure.getClass().getSimpleName());}
    }
    static Frame readFrame(BufferedReader reader) throws IOException {
        return readFrame(reader,null);
    }
    private static int readChar(BufferedReader reader,AtomicBoolean stop,long started) throws IOException {
        for(;;) {
            if(stop!=null&&stop.get())throw new EOFException("Reader stopping");
            try{return reader.read();}catch(SocketTimeoutException idle) {
                if(stop==null||started!=0&&System.nanoTime()-started>TimeUnit.SECONDS.toNanos(30))throw idle;
            }
        }
    }
    private static String readLine(BufferedReader reader,AtomicBoolean stop) throws IOException {
        StringBuilder line=new StringBuilder();long started=0;
        for(;;) {
            int c=readChar(reader,stop,started);
            if(c<0){if(line.length()==0)return null;throw new EOFException("Truncated SIP line");}
            if(started==0)started=System.nanoTime();
            if(c=='\n')return line.toString().replaceFirst("\\r$","");
            line.append((char)c);if(line.length()>16384)throw new IOException("Oversized SIP line");
        }
    }
    private static Frame readFrame(BufferedReader reader,AtomicBoolean stop) throws IOException {
        String line;
        do {line=readLine(reader,stop);if(line==null)return null;}while(line.isEmpty());
        Frame frame=new Frame();frame.start=line;int length=line.length();
        String previous=null;
        while((line=readLine(reader,stop))!=null&&!line.isEmpty()) {
            length+=line.length();if(length>16384)throw new IOException("Oversized headers");
            if(line.startsWith(" ")||line.startsWith("\t")) {
                if(previous==null)throw new IOException("Orphan header continuation");
                frame.headers.put(previous,frame.headers.get(previous)+" "+line.trim());continue;
            }
            int colon=line.indexOf(':');
            if(colon>0) {
                String name=line.substring(0,colon).toLowerCase(Locale.ROOT),value=line.substring(colon+1).trim();
                previous=name;
                frame.headers.merge(name,value,(a,b)->a+", "+b);
            }
        }
        if(line==null)throw new EOFException("Truncated SIP headers");
        int bytes=Integer.parseInt(frame.headers.getOrDefault("content-length","0"));
        if(bytes<0||bytes>4096)throw new IOException("Oversized body");
        frame.body=new byte[bytes];
        long bodyStarted=System.nanoTime();
        for(int i=0;i<bytes;i++){int c=readChar(reader,stop,bodyStarted);if(c<0)throw new EOFException();frame.body[i]=(byte)c;}
        return frame;
    }
    private static void reply(Frame request,int status) throws IOException {
        StringBuilder msg=new StringBuilder("SIP/2.0 "+status+(status==200?" OK":" Temporarily Unavailable")+"\r\n");
        for(String name:new String[]{"via","from","to","call-id","cseq"}) {
            String value=request.headers.get(name);if(value!=null)msg.append(name).append(": ").append(value).append("\r\n");
        }
        msg.append("Content-Length: 0\r\n\r\n");
        synchronized(request.reply){request.reply.write(msg.toString().getBytes(StandardCharsets.US_ASCII));request.reply.flush();}
    }
}
