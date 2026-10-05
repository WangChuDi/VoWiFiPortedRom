import java.io.*;
import java.nio.charset.StandardCharsets;
public final class SmsFrameTest {
    public static void main(String[] args)throws Exception {
        byte[] header=("MESSAGE sip:test SIP/2.0\r\nContent-Type: application/vnd.3gpp.sms\r\n"
            +"Via: first\r\nVia: second\r\nContent-Length: 4\r\n\r\n").getBytes(StandardCharsets.US_ASCII);
        ByteArrayOutputStream stream=new ByteArrayOutputStream();
        stream.write(header);stream.write(new byte[]{5,(byte)255,1,(byte)0xa6});
        stream.write("SIP/2.0 200 OK\r\nContent-Length: 0\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
        BufferedReader reader=new BufferedReader(new InputStreamReader(new ByteArrayInputStream(stream.toByteArray()),StandardCharsets.ISO_8859_1));
        ImsSmsProbe.Frame first=ImsSmsProbe.readFrame(reader),second=ImsSmsProbe.readFrame(reader);
        if((first.body[1]&255)!=255||(first.body[3]&255)!=166)throw new AssertionError("Binary corruption");
        if(!"first, second".equals(first.headers.get("via")))throw new AssertionError("Repeated header lost");
        if(!second.start.equals("SIP/2.0 200 OK")||second.body.length!=0)throw new AssertionError("Frame boundary lost");
        if(ImsSmsProbe.readFrame(reader)!=null)throw new AssertionError("EOF");
        System.out.println("Binary SMS framing and consecutive SIP messages passed");
        final String fragmented="\r\nMESSAGE sip:test SIP/2.0\r\nVia: first,\r\n second\r\nContent-Length: 3\r\n\r\nabc";
        BufferedReader interrupted=new BufferedReader(new StringReader("")) {
            int offset=0;boolean timeout=false;
            @Override public int read() throws IOException {
                if(offset==7||offset==fragmented.length()-2) {
                    if(!timeout){timeout=true;throw new java.net.SocketTimeoutException();}
                }
                timeout=false;return offset==fragmented.length()?-1:fragmented.charAt(offset++);
            }
        };
        java.lang.reflect.Method parse=ImsSmsProbe.class.getDeclaredMethod("readFrame",BufferedReader.class,java.util.concurrent.atomic.AtomicBoolean.class);
        parse.setAccessible(true);
        ImsSmsProbe.Frame resumed=(ImsSmsProbe.Frame)parse.invoke(null,interrupted,new java.util.concurrent.atomic.AtomicBoolean(false));
        if(!"MESSAGE sip:test SIP/2.0".equals(resumed.start)||!"first, second".equals(resumed.headers.get("via"))||!"abc".equals(new String(resumed.body,StandardCharsets.US_ASCII)))
            throw new AssertionError("Timeout lost partial frame");
        StringBuilder keepalives=new StringBuilder();for(int i=0;i<20000;i++)keepalives.append("\r\n");
        keepalives.append("SIP/2.0 200 OK\r\nContent-Length: 0\r\n\r\n");
        if(ImsSmsProbe.readFrame(new BufferedReader(new StringReader(keepalives.toString())))==null)throw new AssertionError("Keepalive frame");
        System.out.println("Partial line/body timeout recovery, folding and 20000 keepalives passed");
    }
}
