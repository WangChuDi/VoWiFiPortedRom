// SPDX-License-Identifier: GPL-2.0
import java.io.IOException;

/** Contract fixtures: selected RAM overrides must not silently become a clean baseline. */
public final class ModernCarrierBaselineTest {
    private static int cases;
    private static void expect(String dump,int slot,boolean empty)throws Exception {
        if(ModernCarrierBaseline.parseEmptyTransientOverride(dump,slot)!=empty)throw new AssertionError("baseline-misclassified");
        cases++;
    }
    private static void reject(String dump,int slot)throws Exception {
        try{ModernCarrierBaseline.parseEmptyTransientOverride(dump,slot);throw new AssertionError("unknown-format-accepted");}
        catch(IOException expected){cases++;}
    }
    public static void main(String[] args)throws Exception {
        expect("Phone Id = 0\n mOverrideConfigs : null\n\n",0,true);
        expect("Phone Id = 0\n mOverrideConfigs :\n\n",0,true);
        expect("Phone Id = 0\n mOverrideConfigs :\n boolean_key = false\n\n",0,false);
        expect("Phone Id = 0\n mOverrideConfigs :\n array_key = [I@1234\n\n",0,false);
        expect("Phone Id = 0\n mOverrideConfigs :\n key = value\n\nPhone Id = 1\n mOverrideConfigs : null\n\n",1,true);
        expect("Phone Id = 0\r\n mOverrideConfigs : null\r\n\r\nPhone Id = 1\r\n mOverrideConfigs :\r\n key = value\r\n\r\n",1,false);
        expect("Phone Id = 0\n mOverrideConfigs : null\nPhone Id = 1\n",0,true);
        reject("Phone Id = 1\n mOverrideConfigs : null\n\n",0);
        reject("Phone Id = 0\n mOverrideConfigs : null",0);
        reject("Phone Id = 0\n mOverrideConfigs : null\n\n mOverrideConfigs : null\n\n",0);
        reject("Phone Id = 0\n mOverrideConfigs : unexpected\n\n",0);
        reject("Phone Id = 0\n mOverrideConfigs :\n changed dump format\n\n",0);
        System.out.println("Modern carrier baseline contracts passed: "+cases);
    }
}
