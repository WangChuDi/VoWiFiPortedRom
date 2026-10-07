// SPDX-License-Identifier: GPL-2.0
package me.phh.ims;

import java.util.Arrays;

/** Parse bounded SIM AKA framing. No result includes a printable credential string. */
public final class AkaResponseCodec {
    public enum Kind { SUCCESS, SYNCHRONIZATION_FAILURE, REJECTED }
    public static final class Result {
        public final Kind kind;
        private final byte[] res,ck,ik,auts;
        private Result(Kind k,byte[] r,byte[] c,byte[] i,byte[] a){kind=k;res=r;ck=c;ik=i;auts=a;}
        public byte[] res(){return res.clone();}
        public byte[] ck(){return ck.clone();}
        public byte[] ik(){return ik.clone();}
        public byte[] auts(){return auts.clone();}
    }
    public static final class SynchronizationRequired extends Exception {
        private final byte[] token;
        public SynchronizationRequired(byte[] auts){super("aka-synchronization-required");if(auts==null||auts.length!=14)throw new IllegalArgumentException("aka-auts-length");token=auts.clone();}
        public byte[] auts(){return token.clone();}
    }
    private static final class Cursor {
        final byte[] input;int offset=1;
        Cursor(byte[] v){input=v;}
        byte[] field(int min,int max){
            if(offset>=input.length)throw new IllegalArgumentException("aka-field-missing");
            int n=input[offset++]&255;
            if(n<min||n>max||n>input.length-offset)throw new IllegalArgumentException("aka-field-length");
            byte[] v=Arrays.copyOfRange(input,offset,offset+n);offset+=n;return v;
        }
    }
    public static Result decode(byte[] raw){
        if(raw==null||raw.length==0||raw.length>128)throw new IllegalArgumentException("aka-response-length");
        int tag=raw[0]&255;Cursor cursor=new Cursor(raw);
        if(tag==0xdc){
            byte[] auts=cursor.field(14,14);
            if(cursor.offset!=raw.length)throw new IllegalArgumentException("aka-sync-trailing-data");
            return new Result(Kind.SYNCHRONIZATION_FAILURE,null,null,null,auts);
        }
        if(tag!=0xdb)return new Result(Kind.REJECTED,null,null,null,null);
        byte[] res=cursor.field(4,16),ck=cursor.field(16,16),ik=cursor.field(16,16);
        // USIM success may also append the optional GSM cipher key Kc.
        if(cursor.offset<raw.length)cursor.field(8,8);
        if(cursor.offset!=raw.length)throw new IllegalArgumentException("aka-success-trailing-data");
        return new Result(Kind.SUCCESS,res,ck,ik,null);
    }
    private AkaResponseCodec(){}
}
