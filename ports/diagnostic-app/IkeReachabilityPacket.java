// SPDX-License-Identifier: GPL-2.0
package dev.codex.vowifi.tool;

import java.io.*;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Arrays;

/** RFC7296 SA_INIT only. Never builds IKE_AUTH, identities or SIM credentials. */
final class IkeReachabilityPacket {
    private static final BigInteger GROUP14=new BigInteger(
        "FFFFFFFFFFFFFFFFC90FDAA22168C234C4C6628B80DC1CD129024E088A67CC74020BBEA63B139B22514A08798E3404DDEF9519B3CD3A431B302B0A6DF25F14374FE1356D6D51C245E485B576625E7EC6F44C42E9A637ED6B0BFF5CB6F406B7EDEE386BFB5A899FA5AE9F24117C4B1FE649286651ECE45B3DC2007CB8A163BF0598DA48361C55D39A69163FA8FD24CF5F83655D23DCA3AD961C62F356208552BB9ED529077096966D670C354E4ABC9804F1746C08CA18217C32905E462E36CE3BE39E772C180E86039B2783A2EC07A28FB5C55DF06F4C52C9DE2BCBF6955817183995497CEA956AE515D2261898FA051015728E5A8AACAA68FFFFFFFFFFFFFFFF",16);
    static final class Request {
        final byte[] spi,ike;
        Request(byte[] spi,byte[] ike){this.spi=spi;this.ike=ike;}
        byte[] datagram(int port){if(port==500)return ike.clone();if(port!=4500)throw new IllegalArgumentException();byte[] b=new byte[ike.length+4];System.arraycopy(ike,0,b,4,ike.length);return b;}
    }
    static Request create()throws IOException {
        SecureRandom random=new SecureRandom();byte[] spi=new byte[8],nonce=new byte[32];
        do{random.nextBytes(spi);}while(allZero(spi,0,8));random.nextBytes(nonce);
        byte[] number=BigInteger.valueOf(2).modPow(new BigInteger(320,random).setBit(319),GROUP14).toByteArray();
        byte[] publicKey=new byte[256];int size=Math.min(number.length,256);System.arraycopy(number,number.length-size,publicKey,256-size,size);
        ByteArrayOutputStream transforms=new ByteArrayOutputStream();
        // AES-CBC-128, HMAC-SHA1 PRF/integrity, MODP14. No resulting keys are used.
        transforms.write(transform(true,1,12,true));transforms.write(transform(true,2,2,false));
        transforms.write(transform(true,3,2,false));transforms.write(transform(false,4,14,false));
        ByteArrayOutputStream sa=new ByteArrayOutputStream();DataOutputStream p=new DataOutputStream(sa);
        p.writeByte(0);p.writeByte(0);p.writeShort(8+transforms.size());p.writeByte(1);p.writeByte(1);p.writeByte(0);p.writeByte(4);p.write(transforms.toByteArray());
        ByteArrayOutputStream body=new ByteArrayOutputStream();body.write(payload(34,sa.toByteArray()));
        byte[] ke=new byte[260];ke[1]=14;System.arraycopy(publicKey,0,ke,4,256);body.write(payload(40,ke));body.write(payload(0,nonce));
        ByteArrayOutputStream full=new ByteArrayOutputStream();DataOutputStream h=new DataOutputStream(full);
        h.write(spi);h.write(new byte[8]);h.writeByte(33);h.writeByte(0x20);h.writeByte(34);h.writeByte(8);h.writeInt(0);h.writeInt(28+body.size());h.write(body.toByteArray());
        return new Request(spi,full.toByteArray());
    }
    private static byte[] transform(boolean more,int type,int id,boolean aes)throws IOException{
        ByteArrayOutputStream b=new ByteArrayOutputStream();DataOutputStream d=new DataOutputStream(b);
        d.writeByte(more?3:0);d.writeByte(0);d.writeShort(aes?12:8);d.writeByte(type);d.writeByte(0);d.writeShort(id);
        if(aes){d.writeShort(0x800e);d.writeShort(128);}return b.toByteArray();
    }
    private static byte[] payload(int next,byte[] contents)throws IOException{
        ByteArrayOutputStream b=new ByteArrayOutputStream();DataOutputStream d=new DataOutputStream(b);d.writeByte(next);d.writeByte(0);d.writeShort(4+contents.length);d.write(contents);return b.toByteArray();
    }
    static final class Response {
        final String kind;final int notify;
        Response(String kind,int notify){this.kind=kind;this.notify=notify;}
    }
    /** Null means malformed, unrelated, ESP, request, or unsupported response. */
    static Response parse(byte[] packet,int length,int port,byte[] spi){
        int base=port==4500?4:0;
        if((port!=500&&port!=4500)||spi.length!=8||length<base+28||length>packet.length)return null;
        if(base==4&&!allZero(packet,0,4))return null;
        for(int i=0;i<8;i++)if(packet[base+i]!=spi[i])return null;
        if((packet[base+17]&255)!=0x20||(packet[base+18]&255)!=34||
           (packet[base+19]&0x28)!=0x20||integer(packet,base+20)!=0||integer(packet,base+24)!=length-base)return null;
        int type=packet[base+16]&255,offset=base+28,count=0,notify=-1;boolean sa=false,ke=false,nonce=false;
        while(type!=0){
            if(++count>32||offset+4>length)return null;int n=shortValue(packet,offset+2),next=packet[offset]&255;
            if(n<4||offset+n>length||(packet[offset+1]&0x80)!=0)return null;
            if(type==33){if(!validSa(packet,offset+4,offset+n))return null;sa=true;}
            else if(type==34){if(n<9||shortValue(packet,offset+4)==0)return null;ke=true;}
            else if(type==40){if(n<20||n>260)return null;nonce=true;}
            else if(type==41){
                if(n<8||packet[offset+4]!=0||packet[offset+5]!=0)return null;
                int code=shortValue(packet,offset+6);
                if(code==16390){if(n<9||n>72)return null;notify=code;}
                else if(code==17){if(n!=10)return null;notify=code;}
                else if(code<16384){if(code==14&&n!=8)return null;notify=code;}
            }else if(type!=43)return null; // Vendor ID is allowed; no encrypted/auth payload.
            offset+=n;type=next;
        }
        if(offset!=length)return null;
        // RFC7296 2.6: COOKIE / errors can legitimately have a zero responder SPI.
        if(notify>=0)return new Response(notify==16390?"COOKIE":"NOTIFY",notify);
        if(sa&&ke&&nonce&&!allZero(packet,base+8,8))return new Response("SA_INIT",-1);
        return null;
    }
    private static boolean validSa(byte[] b,int start,int end){
        if(start+8>end||b[start]!=0||(b[start+5]&255)!=1||b[start+6]!=0)return false;
        int size=shortValue(b,start+2),transforms=b[start+7]&255,pos=start+8;
        if(size!=end-start||transforms<1||transforms>16)return false;
        for(int i=0;i<transforms;i++){
            if(pos+8>end)return false;int n=shortValue(b,pos+2);
            if(n<8||pos+n>end||(b[pos]&255)!=(i==transforms-1?0:3))return false;
            pos+=n;
        }
        return pos==end;
    }
    private static int integer(byte[] b,int offset){return ByteBuffer.wrap(b,offset,4).getInt();}
    private static int shortValue(byte[] b,int offset){return ((b[offset]&255)<<8)|(b[offset+1]&255);}
    private static boolean allZero(byte[] b,int offset,int n){for(int i=offset;i<offset+n;i++)if(b[i]!=0)return false;return true;}
}
