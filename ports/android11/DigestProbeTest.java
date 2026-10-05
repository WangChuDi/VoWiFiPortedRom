public final class DigestProbeTest {
    public static void main(String[] args) throws Exception {
        java.lang.reflect.Method m=ImsAuthenticatedProbe.class.getDeclaredMethod("withDigest",String.class,String.class,String.class,String.class,String.class,String.class,byte[].class,int.class);
        m.setAccessible(true);
        String input="REGISTER sip:realm SIP/2.0\r\nAuthorization: old\r\nContent-Length: 0\r\n\r\n";
        String actual=(String)m.invoke(null,input,"user","realm","nonce",null,null,new byte[]{1,2,3,4},1);
        if(!actual.contains("response=\""+args[0]+"\""))throw new AssertionError("Digest mismatch");
        if(actual.contains("\n")&&!actual.replace("\r\n","").equals(actual.replace("\r\n","").replace("\n","")))throw new AssertionError("Bare LF");
        if(actual.contains("Authorization: old")||!actual.endsWith("\r\n\r\n"))throw new AssertionError("Header rewrite");
        System.out.println("Independent digest vector and CRLF preservation passed");
        java.lang.reflect.Method read=ImsAuthenticatedProbe.class.getDeclaredMethod("readResponse",java.io.BufferedReader.class,String.class,int.class);
        read.setAccessible(true);
        String response="SIP/2.0 200 OK\r\nCall-ID: test\r\nCSeq: 2 REGISTER\r\nP-Associated-URI: <sip:+441234@invalid>\r\nP-Associated-URI: <tel:+441234>\r\nContact: <sip:a@invalid>;expires=1800\r\nContact: <sip:b@invalid>;expires=1700\r\nService-Route: <sip:first.invalid;lr>,\r\n <sip:second.invalid;lr>\r\nService-Route: <sip:third.invalid;lr>\r\nContent-Length: 0\r\n\r\n";
        if(!read.invoke(null,new java.io.BufferedReader(new java.io.StringReader(response)),"test",2).equals(200))throw new AssertionError("Response status");
        java.lang.reflect.Field field=ImsAuthenticatedProbe.class.getDeclaredField("lastResponseHeaders");field.setAccessible(true);
        java.util.Map<?,?> headers=(java.util.Map<?,?>)field.get(null);
        if(!"<sip:+441234@invalid>, <tel:+441234>".equals(headers.get("p-associated-uri")))throw new AssertionError("Associated identities lost");
        if(!"<sip:first.invalid;lr>, <sip:second.invalid;lr>, <sip:third.invalid;lr>".equals(headers.get("service-route")))throw new AssertionError("Route order/folding lost");
        if(!"<sip:a@invalid>;expires=1800, <sip:b@invalid>;expires=1700".equals(headers.get("contact")))throw new AssertionError("Multiple contacts lost");
        System.out.println("Repeated identities and folded ordered routes preserved");
    }
}
