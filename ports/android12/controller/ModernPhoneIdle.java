// SPDX-License-Identifier: GPL-2.0
import android.content.Context;
import android.telephony.TelephonyManager;
import java.io.IOException;
import java.util.regex.*;

/** Require one observed idle call state for every configured modem, including empty slots. */
public final class ModernPhoneIdle {
    private ModernPhoneIdle(){}
    public static void requireIdle(Context context)throws Exception {
        int count=context.getSystemService(TelephonyManager.class).getActiveModemCount();
        if(!allIdle(ModernSystemObservation.read("telephony.registry"),count))throw new IOException("phone-not-idle");
    }
    static boolean allIdle(String dump,int phones)throws IOException {
        if(phones<1||phones>8)throw new IOException("phone-inventory-unavailable");
        boolean[] headers=new boolean[phones],states=new boolean[phones];int current=-1;boolean idle=true;
        Pattern header=Pattern.compile("Phone Id\\s*=\\s*([0-9]+)");
        Pattern state=Pattern.compile("mCallState=([0-9]+)");
        for(String line:dump.split("\\r?\\n",-1)){
            String value=line.trim();Matcher phone=header.matcher(value),call=state.matcher(value);
            if(phone.matches()){
                try{current=Integer.parseInt(phone.group(1));}catch(NumberFormatException invalid){throw new IOException("phone-dump-format-unverified");}
                if(current>=phones||headers[current])throw new IOException("phone-dump-ambiguous");
                headers[current]=true;
            }else if(value.startsWith("mCallState=")){
                if(current<0||!call.matches()||states[current])throw new IOException("phone-dump-format-unverified");
                String number=call.group(1);
                if(!number.matches("[012]"))throw new IOException("phone-state-unverified");
                states[current]=true;idle&="0".equals(number);
            }
        }
        for(int i=0;i<phones;i++)if(!headers[i]||!states[i])throw new IOException("phone-state-unobserved");
        return idle;
    }
}
