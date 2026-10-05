import android.content.Context;
import android.net.*;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Authenticated registration with a bounded receive session and own-contact cleanup. */
public final class ImsAuthenticatedProbe {
    public static void run(Context context, android.telephony.TelephonyManager tm, IpSecManager manager, Socket socket, ServerSocket listener,
            InetAddress peer, IpSecManager.SecurityParameterIndex clientC, IpSecManager.SecurityParameterIndex clientS,
            Map<String,String> headers, byte[] aka, String initial, String identity, String realm, String callId) throws Exception {
        List<AutoCloseable> resources = new ArrayList<>();
        byte[][] fields = new byte[3][];
        int offset=1;
        for (int i=0;i<3;i++) { int n=aka[offset++]&255; fields[i]=Arrays.copyOfRange(aka,offset,offset+n); offset+=n; }
        boolean sent=false;
        String auth=headers.get("www-authenticate");
        String nonce=ImsRegistrationProbe.parameter(auth,"nonce");
        String qop=null;
        try { qop=ImsRegistrationProbe.parameter(auth,"qop"); } catch(IOException absent) {}
        if (qop!=null && !Arrays.asList(qop.split(",\\s*")).contains("auth")) throw new IOException("Unsupported qop");
        String opaque=null;
        try { opaque=ImsRegistrationProbe.parameter(auth,"opaque"); } catch(IOException absent) {}
        String verify=headers.get("security-server");
        if (verify==null) throw new IOException("No Security-Server");
        Map<String,String> selected=null;
        double best=-1;
        for(String option:verify.split(",")) {
            String[] components=option.trim().split(";");
            if(!components[0].equalsIgnoreCase("ipsec-3gpp")) continue;
            Map<String,String> p=new HashMap<>();
            for(int i=1;i<components.length;i++) { String[] pair=components[i].trim().split("=",2); if(pair.length==2)p.put(pair[0],pair[1]); }
            if(!Arrays.asList("hmac-sha-1-96","hmac-md5-96").contains(p.get("alg"))) continue;
            if(!Arrays.asList("null","aes-cbc").contains(p.getOrDefault("ealg","null"))) continue;
            double quality=Double.parseDouble(p.getOrDefault("q","0"));
            if(quality>best) { best=quality; selected=p; }
        }
        if(selected==null) throw new IOException("No supported security proposal");
        byte[] macKey=null;
        try {
            IpSecManager.SecurityParameterIndex serverC=manager.allocateSecurityParameterIndex(peer,
                (int)Long.parseLong(selected.get("spi-c")));
            resources.add(serverC);
            IpSecManager.SecurityParameterIndex serverS=manager.allocateSecurityParameterIndex(peer,
                (int)Long.parseLong(selected.get("spi-s")));
            resources.add(serverS);
            boolean sha1="hmac-sha-1-96".equals(selected.get("alg"));
            macKey=sha1?Arrays.copyOf(fields[2],20):fields[2].clone();
            IpSecTransform.Builder builder=new IpSecTransform.Builder(context)
                .setAuthentication(new IpSecAlgorithm(sha1?IpSecAlgorithm.AUTH_HMAC_SHA1:IpSecAlgorithm.AUTH_HMAC_MD5,macKey,96));
            if("aes-cbc".equals(selected.get("ealg")))
                builder.setEncryption(new IpSecAlgorithm(IpSecAlgorithm.CRYPT_AES_CBC,fields[1]));
            IpSecTransform incoming=builder.buildTransportModeTransform(peer,clientC); resources.add(incoming);
            IpSecTransform outgoing=builder.buildTransportModeTransform(socket.getLocalAddress(),serverS); resources.add(outgoing);
            IpSecTransform listenIn=builder.buildTransportModeTransform(peer,clientS); resources.add(listenIn);
            IpSecTransform listenOut=builder.buildTransportModeTransform(socket.getLocalAddress(),serverC); resources.add(listenOut);
            manager.applyTransportModeTransform(socket,IpSecManager.DIRECTION_IN,incoming);
            manager.applyTransportModeTransform(socket,IpSecManager.DIRECTION_OUT,outgoing);
            FileDescriptor fd=(FileDescriptor)listener.getClass().getMethod("getFileDescriptor$").invoke(listener);
            manager.applyTransportModeTransform(fd,IpSecManager.DIRECTION_IN,listenIn);
            manager.applyTransportModeTransform(fd,IpSecManager.DIRECTION_OUT,listenOut);
            System.out.println("ipsec-transforms=INSTALLED; alg="+selected.get("alg")+"; ealg="+selected.getOrDefault("ealg","null"));
            if("1".equals(System.getenv("CODEX_IMS_NESTED_POLICY")))
                ImsNestedPolicy.apply(socket,listener,peer,clientC.getSpi(),clientS.getSpi());
            int port=Integer.parseInt(selected.get("port-s"));
            if(port<1||port>65535)throw new IOException("Invalid server port");
            socket.connect(new InetSocketAddress(peer,port),5000);
            System.out.println("protected-tcp=CONNECTED");
            String base=initial.replaceAll("(?m)^Via:.*\\r\\n","Via: SIP/2.0/TCP "+host(socket.getLocalAddress())+":"+socket.getLocalPort()
                +";branch=z9hG4bK"+UUID.randomUUID()+";rport\r\n")
                .replace("CSeq: 1 REGISTER","CSeq: 2 REGISTER")
                .replace("Content-Length: 0","Security-Verify: "+verify+"\r\nContent-Length: 0");
            BufferedReader reader=new BufferedReader(new InputStreamReader(socket.getInputStream(),StandardCharsets.ISO_8859_1));
            try {
                String request=withDigest(base,identity,realm,nonce,opaque,qop,fields[0],1);
                sent=true;
                socket.getOutputStream().write(request.getBytes(StandardCharsets.US_ASCII)); socket.getOutputStream().flush();
                int code=readResponse(reader,callId,2);
                System.out.println("authenticated-register-status="+code);
                System.out.println("ims-registration="+(code==200?"SUCCESS":"NOT_CONFIRMED"));
                if(code==200) logIdentityMetadata(lastResponseHeaders);
                if(code==200 && (System.getenv("CODEX_SMS_TEST_DEST")!=null||"1".equals(System.getenv("CODEX_SMS_RECEIVE_ONLY"))))
                    ImsSmsProbe.run(context, tm, socket, listener, reader, lastResponseHeaders, initial, verify);
            } finally {
                if(sent) {
                    String cleanup=base.replace("CSeq: 2 REGISTER","CSeq: 3 REGISTER").replace("expires=1800","expires=0").replace("Expires: 1800","Expires: 0")
                        .replaceAll("branch=z9hG4bK[^;\\r\\n]+","branch=z9hG4bK"+UUID.randomUUID());
                    try {
                        socket.getOutputStream().write(withDigest(cleanup,identity,realm,nonce,opaque,qop,fields[0],2).getBytes(StandardCharsets.US_ASCII));
                        socket.getOutputStream().flush();
                        int code=readResponse(reader,callId,3);
                        System.out.println("diagnostic-contact-removal-status="+code);
                        System.out.println("diagnostic-contact-removal="+(code==200?"CONFIRMED":"UNCONFIRMED"));
                    } catch(Exception cleanupFailure) { System.out.println("diagnostic-contact-removal=UNCONFIRMED; error="+cleanupFailure.getClass().getSimpleName()); }
                }
            }
        } finally {
            try { manager.removeTransportModeTransforms(socket); } catch(Exception ignored) {}
            try {
                manager.removeTransportModeTransforms((FileDescriptor)listener.getClass().getMethod("getFileDescriptor$").invoke(listener));
            } catch(Exception ignored) {}
            Collections.reverse(resources);
            for(AutoCloseable resource:resources) try {resource.close();}catch(Exception ignored){}
            for(byte[] field:fields)Arrays.fill(field,(byte)0);
            if(macKey!=null)Arrays.fill(macKey,(byte)0);
            System.out.println("temporary-ipsec=CLOSED");
        }
    }
    private static String host(InetAddress address) {
        return address instanceof Inet6Address?"["+address.getHostAddress()+"]":address.getHostAddress();
    }
    private static String md5(byte[] data) throws Exception {
        byte[] hash=MessageDigest.getInstance("MD5").digest(data);
        StringBuilder out=new StringBuilder(); for(byte b:hash)out.append(String.format("%02x",b&255)); return out.toString();
    }
    private static String withDigest(String message,String user,String realm,String nonce,String opaque,String qop,byte[] res,int count) throws Exception {
        ByteArrayOutputStream material=new ByteArrayOutputStream();
        material.write((user+":"+realm+":").getBytes(StandardCharsets.UTF_8)); material.write(res);
        String h1=md5(material.toByteArray()), h2=md5(("REGISTER:sip:"+realm).getBytes(StandardCharsets.UTF_8));
        String nc=String.format("%08x",count), cnonce=UUID.randomUUID().toString().replace("-","");
        String value=md5((h1+":"+nonce+":"+(qop==null?"":nc+":"+cnonce+":auth:")+h2).getBytes(StandardCharsets.US_ASCII));
        String header="Authorization: Digest username=\""+user+"\",realm=\""+realm+"\",nonce=\""+nonce+"\",uri=\"sip:"+realm+"\",response=\""+value+"\",algorithm=AKAv1-MD5";
        if(qop!=null)header+=",qop=auth,nc="+nc+",cnonce=\""+cnonce+"\"";
        if(opaque!=null)header+=",opaque=\""+opaque+"\"";
        return message.replaceAll("(?m)^Authorization:.*$",java.util.regex.Matcher.quoteReplacement(header));
    }
    private static Map<String,String> lastResponseHeaders;
    private static void logIdentityMetadata(Map<String,String> headers) {
        java.util.regex.Matcher m=java.util.regex.Pattern.compile("(?:sip:|tel:)[^>,\\s]+",java.util.regex.Pattern.CASE_INSENSITIVE)
            .matcher(headers.getOrDefault("p-associated-uri",""));
        List<String> types=new ArrayList<>();
        while(m.find()) {
            String uri=m.group(), lower=uri.toLowerCase(Locale.ROOT);
            types.add(lower.startsWith("tel:")?"TEL":lower.startsWith("sip:+")?"SIP_E164":
                lower.matches("sip:23415[0-9]{10}@.*")?"SIP_IMSI":"SIP_OTHER");
        }
        System.out.println("associated-identity-types="+types+"; sms-selected="+(types.isEmpty()?"REGISTER_FROM_FALLBACK":types.get(0)));
        System.out.println("service-route-present="+headers.containsKey("service-route")+"; sms-psi-source=HARDCODED_UNVERIFIED");
    }
    private static int readResponse(BufferedReader reader,String callId,int cseq) throws IOException {
        for(int attempt=0;attempt<6;attempt++) {
            String line=reader.readLine(); if(line==null)throw new EOFException();
            if(line.isEmpty())continue;
            if(!line.matches("SIP/2.0 [0-9]{3}.*"))throw new IOException("Non-response on protected connection");
            int code=Integer.parseInt(line.substring(8,11)),size=line.length();
            Map<String,String> headers=new HashMap<>();
            String previous=null;
            while((line=reader.readLine())!=null&&!line.isEmpty()) {
                size+=line.length(); if(size>16384)throw new IOException("Oversized headers");
                if(line.startsWith(" ")||line.startsWith("\t")) {
                    if(previous==null)throw new IOException("Orphan header continuation");
                    headers.put(previous,headers.get(previous)+" "+line.trim());continue;
                }
                int colon=line.indexOf(':');if(colon<=0)throw new IOException("Malformed header");
                previous=line.substring(0,colon).toLowerCase(Locale.ROOT);
                String value=line.substring(colon+1).trim();
                if(Arrays.asList("call-id","cseq","content-length","from","to","expires").contains(previous)) {
                    if(headers.putIfAbsent(previous,value)!=null)throw new IOException("Duplicate singleton header");
                } else headers.merge(previous,value,(a,b)->a+", "+b);
            }
            int length=Integer.parseInt(headers.getOrDefault("content-length","0"));
            if(length<0||length>4096)throw new IOException("Oversized body");
            for(int i=0;i<length;i++)if(reader.read()<0)throw new EOFException();
            if(callId.equals(headers.get("call-id"))&&(cseq+" REGISTER").equals(headers.get("cseq"))&&code>=200) {
                lastResponseHeaders=headers;return code;
            }
        }
        throw new IOException("No matching final response");
    }
}
