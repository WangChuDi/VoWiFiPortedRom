// SPDX-License-Identifier: GPL-2.0
package dev.codex.vowifi.common;

/** Subscription identity and monotonic boot-time authorization checks. */
public final class ProfileGate {
    private ProfileGate(){}
    public static boolean permits(int slot,int actualSub,int selectedSub,long now,long deadline,int currentBoot,int selectedBoot){
        if(slot<0||slot>7||actualSub<0||currentBoot<0||currentBoot!=selectedBoot)return false;
        if(selectedSub<0){if(slot!=1||actualSub!=1)return false;}
        else if(selectedSub!=actualSub)return false;
        long remaining=deadline-now;
        return now>=0&&deadline>now&&remaining>0&&remaining<=120000;
    }
}
