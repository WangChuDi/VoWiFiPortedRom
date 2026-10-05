import java.net.*;
import java.io.*;
import java.util.regex.*;
import android.os.ParcelFileDescriptor;

public final class ImsNestedPolicy {
    private static native int install(int fd,int innerSrc,int innerDst,int innerSpi,int outerSrc,int outerDst,int outerSpi,int reqid);
    public static void apply(Socket socket, java.net.ServerSocket listener, InetAddress peer, int spi, int serverSpi) throws Exception {
        String broker=System.getenv("CODEX_IMS_BROKER");
        if(broker==null || !broker.matches("codex-ims-[a-z0-9]+"))throw new IOException("Missing diagnostic broker");
        try(ParcelFileDescriptor descriptor=ParcelFileDescriptor.fromSocket(socket);
            android.net.LocalSocket channel=new android.net.LocalSocket()) {
            channel.connect(new android.net.LocalSocketAddress(broker));
            channel.setSoTimeout(5000);
            FileDescriptor listenerFd=(FileDescriptor)listener.getClass().getMethod("getFileDescriptor$").invoke(listener);
            channel.setFileDescriptorsForSend(new FileDescriptor[]{descriptor.getFileDescriptor(),listenerFd});
            DataOutputStream out=new DataOutputStream(channel.getOutputStream());
            out.writeByte(73);out.flush();
            channel.setFileDescriptorsForSend(null);
            out.writeInt(ipv4(peer));out.writeInt(ipv4(socket.getLocalAddress()));out.writeInt(spi);out.writeInt(serverSpi);out.flush();
            int result=new DataInputStream(channel.getInputStream()).readInt();
            System.out.println("nested-socket-in-policy-result="+result);
            if(result!=0)throw new IOException("Socket policy broker rejected request");
        }
    }
    static int applyFd(FileDescriptor descriptor, int innerSrc, int innerDst, int spi, int reqid) throws Exception {
        if(android.os.Process.myUid()!=0) throw new SecurityException("Nested policy probe requires root");
        String text;
        Process process=new ProcessBuilder("/system/bin/ip","xfrm","policy").start();
        ByteArrayOutputStream output=new ByteArrayOutputStream();
        byte[] buffer=new byte[2048]; int n;
        while((n=process.getInputStream().read(buffer))>=0) {
            output.write(buffer,0,n); if(output.size()>65536)throw new IOException("Oversized policy list");
        }
        if(process.waitFor()!=0)throw new IOException("Cannot inspect tunnel policy");
        text=output.toString("US-ASCII");
        String local=InetAddress.getByAddress(new byte[]{(byte)(innerDst>>>24),(byte)(innerDst>>>16),(byte)(innerDst>>>8),(byte)innerDst}).getHostAddress();
        Matcher match=Pattern.compile("(?m)^src 0\\.0\\.0\\.0/0 dst "+Pattern.quote(local)+"/32\\s*\\n\\s*dir in priority [0-9]+\\s*\\n\\s*tmpl src ([0-9.]+) dst ([0-9.]+)\\s*\\n\\s*proto esp spi (0x[0-9a-fA-F]+) reqid 0 mode tunnel").matcher(text);
        if(!match.find())throw new IOException("Expected single ePDG inbound tunnel policy not found");
        InetAddress outerSrc=InetAddress.getByName(match.group(1)),outerDst=InetAddress.getByName(match.group(2));
        int outerSpi=(int)Long.parseLong(match.group(3).substring(2),16);
        if(match.find())throw new IOException("Ambiguous ePDG tunnel policy");
        String library=System.getenv("CODEX_IMS_LIBRARY");
        if(library==null)library="/data/local/tmp/libcodex-ims-nested.so";
        if(!library.equals("/data/local/tmp/libcodex-ims-nested.so")&&!library.equals("/data/local/tmp/codex-vowifi-sms/libcodex-ims-nested.so"))
            throw new SecurityException("Unexpected library path");
        System.load(library);
        int fd=(Integer)FileDescriptor.class.getMethod("getInt$").invoke(descriptor);
        return install(fd,innerSrc,innerDst,spi,ipv4(outerSrc),ipv4(outerDst),outerSpi,reqid);
    }
    private static int ipv4(InetAddress a) throws IOException {
        byte[] b=a.getAddress();if(b.length!=4)throw new IOException("IPv4-only diagnostic");
        return (b[0]&255)<<24|(b[1]&255)<<16|(b[2]&255)<<8|(b[3]&255);
    }
}
