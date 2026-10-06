// SPDX-License-Identifier: GPL-2.0
import android.telephony.TelephonyManager;
public final class ModernOwnerPresenceTest {
    private static int cases;
    private static void expect(int[][] tuples,int sim,String operator,boolean test,ModernOwnerPresence.State wanted){if(ModernOwnerPresence.classify(tuples,sim,operator,0,11,test)!=wanted)throw new AssertionError("owner-presence-misclassified");cases++;}
    private static void reject(int[][] tuples,int sim,String operator,boolean test){try{ModernOwnerPresence.classify(tuples,sim,operator,0,11,test);throw new AssertionError("unknown-owner-accepted");}catch(SecurityException expected){cases++;}}
    public static void main(String[] args){
        int ready=TelephonyManager.SIM_STATE_READY,absent=TelephonyManager.SIM_STATE_ABSENT;
        expect(new int[][]{{0,11}},ready,"23415",false,ModernOwnerPresence.State.LIVE);
        expect(new int[][]{{0,11},{1,12}},ready,"23415",false,ModernOwnerPresence.State.LIVE);
        expect(new int[][]{},absent,null,false,ModernOwnerPresence.State.ABSENT);
        expect(new int[][]{{1,12}},absent,null,false,ModernOwnerPresence.State.ABSENT);
        expect(new int[][]{{0,11}},ready,"310260",true,ModernOwnerPresence.State.LIVE);
        reject(null,absent,null,false);reject(new int[][]{},TelephonyManager.SIM_STATE_UNKNOWN,null,false);
        reject(new int[][]{},TelephonyManager.SIM_STATE_NOT_READY,null,false);reject(new int[][]{},ready,"23415",false);
        reject(new int[][]{{0,11}},absent,null,false);reject(new int[][]{{0,11}},TelephonyManager.SIM_STATE_PIN_REQUIRED,null,false);
        reject(new int[][]{{0,12}},absent,null,false);reject(new int[][]{{1,11}},absent,null,false);
        reject(new int[][]{{0,11},{0,12}},ready,"23415",false);reject(new int[][]{{0,11},{1,11}},ready,"23415",false);
        reject(new int[][]{{0,11}},ready,"310260",false);reject(new int[][]{{0,11}},ready,"23415",true);
        reject(new int[][]{{0,11}},ready,null,false);reject(new int[][]{null},absent,null,false);
        reject(new int[][]{{-1,12}},absent,null,false);reject(new int[][]{{1,-1}},absent,null,false);
        try{ModernOwnerPresence.requireLive(ModernOwnerPresence.State.ABSENT);throw new AssertionError("absent-owner-enabled");}catch(SecurityException expected){cases++;}
        System.out.println("Modern owner-presence contracts passed: "+cases);
    }
}
