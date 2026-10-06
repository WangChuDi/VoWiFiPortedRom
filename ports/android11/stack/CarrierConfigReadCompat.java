// SPDX-License-Identifier: GPL-2.0
import java.lang.reflect.Method;

/** Resolve read-only loader ABI differences without masking an invocation error. */
public final class CarrierConfigReadCompat {
    private CarrierConfigReadCompat(){}
    public static Object read(Class<?> contract,Object loader,int subId,String caller)throws Exception {
        Method method;
        try{method=contract.getMethod("getConfigForSubId",int.class,String.class);}
        catch(NoSuchMethodException missing){
            try{method=contract.getMethod("getConfigForSubIdWithFeature",int.class,String.class,String.class);}
            catch(NoSuchMethodException noFeature){
                // Preserve the previous OEM spelling, only if neither standard
                // signature exists. This is not a permission-error fallback.
                method=contract.getMethod("getConfigForSubId",int.class,String.class,String.class);
            }
            return method.invoke(loader,subId,caller,null);
        }
        return method.invoke(loader,subId,caller);
    }
}
