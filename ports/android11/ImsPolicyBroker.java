import android.net.*;
import android.system.Os;
import java.io.*;
import java.net.*;

/** Single-use root helper: only applies policy to a system-UID client's passed socket. */
public final class ImsPolicyBroker {
    public static void main(String[] args) throws Exception {
        try { run(args); } catch(Throwable failure) { failure.printStackTrace(System.out); System.exit(1); }
    }
    private static void run(String[] args) throws Exception {
        if(args.length!=1||!args[0].matches("codex-ims-[a-z0-9]+"))throw new IllegalArgumentException();
        LocalServerSocket server=new LocalServerSocket(args[0]);
        try(LocalSocket socket=server.accept()) {
            socket.setSoTimeout(5000);
            int uid=socket.getPeerCredentials().getUid();
            if(uid!=1000)throw new SecurityException("Unexpected peer");
            DataInputStream in=new DataInputStream(socket.getInputStream());
            if(in.readUnsignedByte()!=73)throw new IOException("Bad request");
            FileDescriptor[] descriptors=socket.getAncillaryFileDescriptors();
            if(descriptors==null||descriptors.length!=2)throw new IOException("Missing sockets");
            int result=-1;
            try {
                int source=in.readInt(),destination=in.readInt();
                int[] spis={in.readInt(),in.readInt()};
                for(int i=0;i<descriptors.length;i++) {
                FileDescriptor fd=descriptors[i];
                InetSocketAddress bound=(InetSocketAddress)Os.getsockname(fd);
                byte[] b=bound.getAddress().getAddress();
                if(b.length!=4)throw new IOException("IPv4 required");
                int actual=(b[0]&255)<<24|(b[1]&255)<<16|(b[2]&255)<<8|(b[3]&255);
                if(actual!=destination||bound.getPort()==0)throw new IOException("Passed socket address mismatch");
                result=ImsNestedPolicy.applyFd(fd,source,destination,spis[i],uid);
                if(result!=0)break;
                }
            } catch(Throwable failure) {
                failure.printStackTrace(System.out);
            } finally { for(FileDescriptor fd:descriptors)Os.close(fd); }
            DataOutputStream out=new DataOutputStream(socket.getOutputStream());
            out.writeInt(result);out.flush();
            System.out.println("broker-result="+result);
        } finally {server.close();}
    }
}
