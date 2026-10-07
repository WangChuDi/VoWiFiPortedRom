// SPDX-License-Identifier: GPL-2.0
import me.phh.ims.AkaResponseCodec;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;

/** Synthetic framing only; never calls a SIM or uses live authentication material. */
public final class AkaResponseCodecContract {
    static int checks;
    static void check(boolean ok){checks++;if(!ok)throw new AssertionError("aka-contract");}
    static byte[] field(int n){byte[] v=new byte[n+1];v[0]=(byte)n;Arrays.fill(v,1,v.length,(byte)0x33);return v;}
    static byte[] join(byte[]...items){ByteArrayOutputStream out=new ByteArrayOutputStream();for(byte[] v:items)out.write(v,0,v.length);return out.toByteArray();}
    static byte[] success(){return join(new byte[]{(byte)0xdb},field(4),field(16),field(16));}
    static void rejects(byte[] v){boolean refused=false;try{AkaResponseCodec.decode(v);}catch(IllegalArgumentException e){refused=true;}check(refused);}
    public static void main(String[] args)throws Exception {
        AkaResponseCodec.Result value=AkaResponseCodec.decode(success());
        check(value.kind==AkaResponseCodec.Kind.SUCCESS);
        check(value.res().length==4&&value.ck().length==16&&value.ik().length==16);
        byte[] res=value.res();res[0]=0;check(value.res()[0]==0x33);
        check(AkaResponseCodec.decode(join(success(),field(8))).kind==AkaResponseCodec.Kind.SUCCESS);
        value=AkaResponseCodec.decode(join(new byte[]{(byte)0xdc},field(14)));
        check(value.kind==AkaResponseCodec.Kind.SYNCHRONIZATION_FAILURE&&value.auts().length==14);
        rejects(join(new byte[]{(byte)0xdc},field(13)));
        rejects(join(new byte[]{(byte)0xdc},field(14),new byte[]{0}));
        rejects(join(new byte[]{(byte)0xdb},field(0),field(16),field(16)));
        rejects(join(new byte[]{(byte)0xdb},field(17),field(16),field(16)));
        rejects(Arrays.copyOf(success(),20));
        rejects(null);rejects(new byte[0]);rejects(new byte[129]);
        rejects(join(new byte[]{(byte)0xdb},field(4),field(15),field(16)));
        rejects(join(new byte[]{(byte)0xdb},field(4),field(16),field(15)));
        rejects(join(success(),field(8),new byte[]{0}));
        check(AkaResponseCodec.decode(new byte[]{(byte)0xdd}).kind==AkaResponseCodec.Kind.REJECTED);
        AkaResponseCodec.SynchronizationRequired sync=new AkaResponseCodec.SynchronizationRequired(new byte[14]);
        check(sync.getMessage().equals("aka-synchronization-required")&&!sync.toString().contains("["));
        byte[] auts=sync.auts();auts[0]=1;check(sync.auts()[0]==0);
        boolean refused=false;try{new AkaResponseCodec.SynchronizationRequired(new byte[13]);}catch(IllegalArgumentException e){refused=true;}check(refused);
        System.out.println("aka-framing-contracts="+checks+" passed");
    }
}
