// SPDX-License-Identifier: GPL-2.0
import android.os.ServiceManager;
import com.android.internal.telecom.ITelecomService;
/** Ends the existing user-authorized test call only; never initiates a call. */
public final class EndTestCall {
    public static void main(String[] args)throws Exception {
        if(android.os.Process.myUid()!=0)throw new SecurityException("root-required");
        if(args.length!=1||!"--end-existing".equals(args[0]))throw new IllegalArgumentException("explicit-end-required");
        ITelecomService telecom=ITelecomService.Stub.asInterface(ServiceManager.getService("telecom"));
        System.out.println("end-call="+telecom.endCall("com.android.shell"));
    }
}
