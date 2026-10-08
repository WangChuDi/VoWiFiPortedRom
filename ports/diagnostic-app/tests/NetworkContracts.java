// SPDX-License-Identifier: GPL-2.0
package dev.codex.vowifi.tool;
import java.nio.ByteBuffer;
import java.util.*;
import org.json.*;

/** Protocol/adversarial parsing contracts. No device or network I/O. */
public final class NetworkContracts {
    private static int checks;
    private static void check(boolean value){checks++;if(!value)throw new AssertionError("network-contract-"+checks);}
    private static IkeReachabilityPacket.Response parse(byte[] b,int port,byte[] spi){return IkeReachabilityPacket.parse(b,b.length,port,spi);}
    public static void main(String[] args)throws Exception{
        check("epdg.epc.mnc015.mcc234.pub.3gppnetwork.org".equals(NetworkDetection.standardHost("23415")));
        check("epdg.epc.mnc260.mcc310.pub.3gppnetwork.org".equals(NetworkDetection.standardHost("310260")));
        check(NetworkDetection.standardHost("2341")==null&&NetworkDetection.standardHost("2341500")==null&&NetworkDetection.standardHost("2341x")==null);
        IkeReachabilityPacket.Request request=IkeReachabilityPacket.create(),other=IkeReachabilityPacket.create();
        check(!Arrays.equals(request.spi,other.spi));check(request.ike.length==376);check(request.ike[17]==0x20&&request.ike[18]==34&&request.ike[19]==8);
        check(ByteBuffer.wrap(request.ike,24,4).getInt()==request.ike.length);
        check(parse(request.ike,500,request.spi)==null); // A sent request is not a response.
        byte[] good=request.ike.clone();good[8]=1;good[19]=0x20;
        check("SA_INIT".equals(parse(good,500,request.spi).kind));
        check(parse(good,500,other.spi)==null);
        byte[] udp4500=new byte[good.length+4];System.arraycopy(good,0,udp4500,4,good.length);
        check(parse(udp4500,4500,request.spi)!=null);udp4500[0]=1;check(parse(udp4500,4500,request.spi)==null);udp4500[0]=0;
        check(parse(good,4500,request.spi)==null);check(parse(udp4500,500,request.spi)==null);
        for(int index:new int[]{0,17,18,23,27,30}){
            byte[] invalid=good.clone();invalid[index]^=1;check(parse(invalid,500,request.spi)==null);
        }
        byte[] initFlag=good.clone();initFlag[19]|=8;check(parse(initFlag,500,request.spi)==null);
        byte[] noResponse=good.clone();noResponse[19]=0;check(parse(noResponse,500,request.spi)==null);
        byte[] zero=good.clone();Arrays.fill(zero,8,16,(byte)0);check(parse(zero,500,request.spi)==null);
        for(int size=0;size<good.length;size++)check(IkeReachabilityPacket.parse(good,size,500,request.spi)==null);
        check(IkeReachabilityPacket.parse(good,good.length+1,500,request.spi)==null);
        check(IkeReachabilityPacket.parse(good,good.length,999,request.spi)==null);
        // A valid NO_PROPOSAL_CHOSEN response with zero responder SPI proves
        // IKE reply reachability, not authentication or successful SA creation.
        byte[] notify=new byte[36];System.arraycopy(request.spi,0,notify,0,8);notify[16]=41;notify[17]=0x20;notify[18]=34;notify[19]=0x20;
        ByteBuffer.wrap(notify,24,4).putInt(notify.length);notify[31]=8;notify[35]=14;
        check("NOTIFY".equals(parse(notify,500,request.spi).kind)&&parse(notify,500,request.spi).notify==14);
        byte[] cookie=Arrays.copyOf(notify,40);ByteBuffer.wrap(cookie,24,4).putInt(40);cookie[31]=12;cookie[34]=0x40;cookie[35]=6;cookie[36]=1;
        check("COOKIE".equals(parse(cookie,500,request.spi).kind));
        byte[] badNotify=notify.clone();badNotify[33]=8;check(parse(badNotify,500,request.spi)==null);
        byte[] ke=Arrays.copyOf(notify,38);ByteBuffer.wrap(ke,24,4).putInt(38);ke[31]=10;ke[35]=17;ke[37]=14;
        check(parse(ke,500,request.spi).notify==17);
        for(int i=0;i<512;i++){byte[] random=new byte[i];new Random(i).nextBytes(random);check(parse(random,i%2==0?500:4500,request.spi)==null);}
        StringBuilder cli=new StringBuilder("Row: 0 ");
        for(String name:ApnDetails.FIELDS){if(cli.length()>7)cli.append(", ");cli.append(name).append('=').append("type".equals(name)?"default,supl,mms":"password".equals(name)?"credential-fixture":"name".equals(name)?"APN fixture, Unicode测试":"mmsc".equals(name)?"NULL":"value");}
        JSONObject row=ApnDetails.parseCli(cli.toString()).getJSONObject(0);
        check("default,supl,mms".equals(row.getString("type")));check(row.getString("name").equals("APN fixture, Unicode测试"));check(row.getString("mmsc").isEmpty());
        check(!ApnDetails.describe(row,false).contains("credential-fixture"));check(ApnDetails.describe(row,true).contains("credential-fixture"));
        check(ApnDetails.parseCli("No result found.").length()==0);
        boolean refused=false;try{ApnDetails.parseCli("Row: 0 name=only");}catch(Exception e){refused=true;}check(refused);
        refused=false;try{ApnDetails.parseCli(cli+", password=duplicate");}catch(Exception e){refused=true;}check(refused);
        check(ApnDetails.describe(new JSONObject(),false).contains("不可见"));
        System.out.println(new JSONObject().put("status","passed").put("checks",checks).put("network_traffic",false).put("device_verified",false));
    }
}
