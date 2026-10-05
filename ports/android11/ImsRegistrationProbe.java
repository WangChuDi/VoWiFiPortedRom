import android.content.Context;
import android.net.*;
import android.telephony.TelephonyManager;
import android.util.Base64;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;

/** Initial registration/USIM challenge, optionally followed by protected registration. */
public final class ImsRegistrationProbe {
    public static void run(Context context, TelephonyManager tm, Network network, int serverIndex, boolean full) throws Exception {
        ConnectivityManager cm = context.getSystemService(ConnectivityManager.class);
        LinkProperties lp = cm.getLinkProperties(network);
        if (lp == null || lp.getPcscfServers().isEmpty()) throw new IllegalStateException("No P-CSCF");
        InetAddress pcscf = lp.getPcscfServers().get(serverIndex);
        String imsi = tm.getSubscriberId();
        if (imsi == null || !imsi.matches("23415[0-9]{8,10}")) throw new IllegalStateException("Unexpected subscriber");
        String realm = "ims.mnc015.mcc234.3gppnetwork.org";
        String identity = imsi + "@" + realm;
        String publicIdentity = "sip:" + identity;
        String isimPrivate = tm.getIsimImpi();
        String[] isimPublic = tm.getIsimImpu();
        if (isimPrivate != null && isimPrivate.matches("[a-zA-Z0-9_.+@-]+")) identity = isimPrivate;
        if (isimPublic != null) for (String impu : isimPublic) {
            if (impu != null && impu.matches("sip:[a-zA-Z0-9_.+@-]+")) { publicIdentity=impu; break; }
        }
        System.out.println("isim-private-present=" + (isimPrivate!=null) + "; isim-public-present=" + (isimPublic!=null));
        String imei = tm.getImei(1);
        if (imei == null || !imei.matches("[0-9]{15}")) throw new IllegalStateException("Invalid device identity");
        String instance = imei.substring(0,8) + "-" + imei.substring(8,14) + "-0";
        String callId = UUID.randomUUID().toString();
        IpSecManager ipsec = context.getSystemService(IpSecManager.class);
        try (Socket socket = network.getSocketFactory().createSocket();
             Socket secure = network.getSocketFactory().createSocket();
             ServerSocket listener = new ServerSocket()) {
            network.bindSocket(socket);
            socket.connect(new InetSocketAddress(pcscf, 5060), 3000);
            System.out.println("socket-network=" + network + "; local-interface=" +
                NetworkInterface.getByInetAddress(socket.getLocalAddress()).getName());
            try (android.os.ParcelFileDescriptor descriptor = android.os.ParcelFileDescriptor.fromSocket(socket)) {
                System.out.println("socket-mark=0x" + Integer.toHexString(android.system.Os.getsockoptInt(
                    descriptor.getFileDescriptor(), android.system.OsConstants.SOL_SOCKET, 36)));
            }
            if (!lp.getInterfaceName().equals(NetworkInterface.getByInetAddress(socket.getLocalAddress()).getName()))
                throw new IOException("Socket did not use IMS interface; REGISTER suppressed");
            socket.setSoTimeout(8000);
            secure.bind(new InetSocketAddress(socket.getLocalAddress(), 0));
            secure.setSoTimeout(8000);
            listener.bind(new InetSocketAddress(socket.getLocalAddress(), 0));
            network.bindSocket((FileDescriptor) listener.getClass().getMethod("getFileDescriptor$").invoke(listener));
            try (IpSecManager.SecurityParameterIndex spiC = ipsec.allocateSecurityParameterIndex(socket.getLocalAddress());
                 IpSecManager.SecurityParameterIndex spiS = ipsec.allocateSecurityParameterIndex(socket.getLocalAddress())) {
                String host = socket.getLocalAddress().getHostAddress();
                if (socket.getLocalAddress() instanceof Inet6Address) host = "[" + host + "]";
                String local = host + ":" + socket.getLocalPort();
                String security = "ipsec-3gpp;prot=esp;mod=trans;alg=hmac-sha-1-96;ealg=null;spi-c="
                    + Integer.toUnsignedString(spiC.getSpi()) + ";spi-s=" + Integer.toUnsignedString(spiS.getSpi())
                    + ";port-c=" + secure.getLocalPort() + ";port-s=" + listener.getLocalPort();
                String message = "REGISTER sip:" + realm + " SIP/2.0\r\n"
                    + "Via: SIP/2.0/TCP " + local + ";branch=z9hG4bK" + UUID.randomUUID() + ";rport\r\n"
                    + "Max-Forwards: 70\r\nFrom: <" + publicIdentity + ">;tag=" + UUID.randomUUID() + "\r\n"
                    + "To: <" + publicIdentity + ">\r\nCall-ID: " + callId + "\r\nCSeq: 1 REGISTER\r\n"
                    + "Contact: <sip:" + imsi + "@" + host + ":" + listener.getLocalPort() + ";transport=tcp>;expires=1800;+sip.instance=\"<urn:gsma:imei:" + instance + ">\";+g.3gpp.smsip\r\n"
                    + "Expires: 1800\r\nSupported: path, gruu, sec-agree\r\nRequire: sec-agree\r\nProxy-Require: sec-agree\r\n"
                    + "Allow: REGISTER, MESSAGE, OPTIONS\r\nUser-Agent: API30-IMS-Diagnostic\r\n"
                    + "P-Access-Network-Info: IEEE-802.11\r\n"
                    + "Security-Client: " + security + ", " + security.replace("ealg=null", "ealg=aes-cbc") + "\r\n"
                    + "Authorization: Digest username=\"" + identity + "\",realm=\"" + realm
                    + "\",nonce=\"\",uri=\"sip:" + realm + "\",response=\"\",algorithm=AKAv1-MD5\r\n"
                    + "Content-Length: 0\r\n\r\n";
                socket.getOutputStream().write(message.getBytes(StandardCharsets.US_ASCII));
                socket.getOutputStream().flush();
                System.out.println("initial-register=SENT; authenticated-register=" + (full ? "ENABLED" : "DISABLED"));
                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
                for (int attempt = 0; attempt < 4; attempt++) {
                    String status = reader.readLine();
                    for (int blank=0; status != null && status.isEmpty() && blank<4; blank++)
                        status=reader.readLine();
                    if (status == null) throw new EOFException("Peer closed without SIP response");
                    if (!status.matches("SIP/2.0 [0-9]{3}.*")) {
                        System.out.println("non-sip-response-length=" + status.length() + "; http=" + status.startsWith("HTTP/"));
                        throw new IOException("Invalid SIP status");
                    }
                    int code = Integer.parseInt(status.substring(8, 11));
                    Map<String,String> headers = new HashMap<>();
                    int size = status.length();
                    String line;
                    while ((line = reader.readLine()) != null && !line.isEmpty()) {
                        size += line.length();
                        if (size > 16384) throw new IOException("Oversized SIP headers");
                        int colon = line.indexOf(':');
                        if (colon > 0) headers.put(line.substring(0,colon).toLowerCase(Locale.ROOT), line.substring(colon+1).trim());
                    }
                    if (!callId.equals(headers.get("call-id")) || !"1 REGISTER".equals(headers.get("cseq")))
                        throw new IOException("Mismatched SIP transaction");
                    int length = Integer.parseInt(headers.getOrDefault("content-length","0"));
                    if (length < 0 || length > 4096) throw new IOException("Oversized SIP body");
                    for (int i=0;i<length;i++) if (reader.read() < 0) throw new EOFException();
                    System.out.println("sip-status=" + code);
                    if (code == 400) System.out.println("sip-reason=" + status.substring(12).replaceAll("[0-9]{5,}", "[redacted]"));
                    System.out.println("sip-warning-present=" + headers.containsKey("warning")
                        + "; reason-header-present=" + headers.containsKey("reason"));
                    if (code == 423) {
                        String minimum = headers.getOrDefault("min-expires", "");
                        System.out.println("min-expires=" + (minimum.matches("[0-9]{1,8}") ? minimum : "UNAVAILABLE"));
                    }
                    if (code < 200) continue;
                    if (code >= 200 && code < 300) {
                        // Some trusted access networks may accept without AKA. Remove only our contact.
                        String cleanup = message.replace("CSeq: 1 REGISTER", "CSeq: 2 REGISTER")
                            .replace("expires=1800", "expires=0").replace("Expires: 1800", "Expires: 0");
                        socket.getOutputStream().write(cleanup.getBytes(StandardCharsets.US_ASCII));
                        socket.getOutputStream().flush();
                        String cleanupStatus = reader.readLine();
                        System.out.println("diagnostic-contact-removal=" +
                            (cleanupStatus != null && cleanupStatus.startsWith("SIP/2.0 200") ? "CONFIRMED" : "UNCONFIRMED"));
                    }
                    if (code != 401) { System.out.println("aka=NOT_ATTEMPTED"); return; }
                    String auth = headers.getOrDefault("www-authenticate", "");
                    if (!auth.regionMatches(true,0,"Digest ",0,7)) throw new IOException("Not Digest");
                    String algorithm = parameter(auth, "algorithm");
                    if (!"AKAv1-MD5".equalsIgnoreCase(algorithm)) throw new IOException("Unsupported AKA algorithm");
                    if (!realm.equals(parameter(auth, "realm"))) throw new IOException("Unexpected auth realm");
                    System.out.println("challenge=AKAv1-MD5; security-server-present=" + headers.containsKey("security-server"));
                    byte[] nonce = Base64.decode(parameter(auth,"nonce"), Base64.DEFAULT);
                    if (nonce.length < 32 || nonce.length > 512) throw new IOException("Invalid nonce length");
                    byte[] challenge = new byte[34];
                    challenge[0]=16; challenge[17]=16;
                    System.arraycopy(nonce,0,challenge,1,16);
                    System.arraycopy(nonce,16,challenge,18,16);
                    String answer = tm.getIccAuthentication(TelephonyManager.APPTYPE_USIM,
                        TelephonyManager.AUTHTYPE_EAP_AKA, Base64.encodeToString(challenge,Base64.NO_WRAP));
                    Arrays.fill(nonce,(byte)0); Arrays.fill(challenge,(byte)0);
                    if (answer == null) { System.out.println("aka=NULL_RESPONSE"); return; }
                    byte[] response = Base64.decode(answer,Base64.DEFAULT);
                    try {
                        if (response.length < 2) throw new IOException("Short AKA response");
                        if ((response[0]&255)==0xdc) { System.out.println("aka=SYNCHRONIZATION_FAILURE; resync=NOT_ATTEMPTED"); return; }
                        if ((response[0]&255)!=0xdb) { System.out.println("aka=REJECTED"); return; }
                        int offset=1;
                        for (int field=0;field<3;field++) {
                            if (offset>=response.length) throw new IOException("Truncated AKA response");
                            int n=response[offset++]&255;
                            if ((field==0 ? n<4 || n>16 : n!=16) || offset+n>response.length)
                                throw new IOException("Invalid AKA field");
                            offset+=n;
                        }
                        System.out.println("aka=SUCCESS; keys=NOT_LOGGED; ipsec-transforms=NOT_INSTALLED");
                        if (full) ImsAuthenticatedProbe.run(context, tm, ipsec, secure, listener, pcscf, spiC, spiS,
                            headers, response, message, identity, realm, callId);
                    } finally { Arrays.fill(response,(byte)0); }
                    return;
                }
                throw new IOException("No final SIP response");
            }
        } finally { System.out.println("diagnostic-sockets-and-spi=CLOSED"); }
    }
    static String parameter(String header, String key) throws IOException {
        Matcher m=Pattern.compile("(?i)(?:^Digest\\s+|,\\s*)"+Pattern.quote(key)+"\\s*=\\s*(?:\"([^\"]*)\"|([^,\\s]+))").matcher(header);
        if (!m.find()) throw new IOException("Missing auth parameter " + key);
        return m.group(1)!=null?m.group(1):m.group(2);
    }
}
