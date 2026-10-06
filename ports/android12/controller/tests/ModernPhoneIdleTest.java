// SPDX-License-Identifier: GPL-2.0
import java.io.IOException;
public final class ModernPhoneIdleTest {
    private static int cases;
    private static void expect(String dump,int phones,boolean idle)throws Exception {
        if(ModernPhoneIdle.allIdle(dump,phones)!=idle)throw new AssertionError("wrong-call-state");cases++;
    }
    private static void reject(String dump,int phones)throws Exception {
        try{ModernPhoneIdle.allIdle(dump,phones);throw new AssertionError("unobserved-state-accepted");}
        catch(IOException expected){cases++;}
    }
    public static void main(String[] args)throws Exception {
        expect("Phone Id=0\n mCallState=0\n",1,true);
        expect("Phone Id = 0\r\n mCallState=0\r\nPhone Id = 1\r\n mCallState=0\r\n",2,true);
        expect("Phone Id=0\n mCallState=0\nPhone Id=1\n mCallState=1\n",2,false);
        expect("Phone Id=0\n mCallState=2\nPhone Id=1\n mCallState=0\n",2,false);
        reject("Phone Id=0\n mCallState=0\n",2);
        reject("Phone Id=0\n",1);
        reject("mCallState=0\n",1);
        reject("Phone Id=0\n mCallState=0\n mCallState=0\n",1);
        reject("Phone Id=0\n mCallState=0\nPhone Id=0\n mCallState=0\n",1);
        reject("Phone Id=1\n mCallState=0\n",1);
        reject("Phone Id=0\n mCallState=3\n",1);
        reject("Phone Id=0\n mCallState=unknown\n",1);
        reject("Phone Id=0\n mCallState=0\n",0);
        System.out.println("Modern phone idle contracts passed: "+cases);
    }
}
