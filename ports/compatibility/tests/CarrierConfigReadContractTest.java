// SPDX-License-Identifier: GPL-2.0
import java.lang.reflect.InvocationTargetException;

public final class CarrierConfigReadContractTest {
    private static int checks;
    private static void check(boolean value){checks++;if(!value)throw new AssertionError("read-contract-"+checks);}
    public static class Two {
        int calls;int sub;String caller;
        public Object getConfigForSubId(int sub,String caller){this.calls++;this.sub=sub;this.caller=caller;return this;}
    }
    public static class Feature {
        int calls;int sub;String caller;String feature="unset";
        public Object getConfigForSubIdWithFeature(int sub,String caller,String feature){calls++;this.sub=sub;this.caller=caller;this.feature=feature;return this;}
    }
    public static class Oem {
        int calls;String feature="unset";
        public Object getConfigForSubId(int sub,String caller,String feature){calls++;this.feature=feature;return this;}
    }
    public static class Both extends Two {
        int alternate;
        public Object getConfigForSubIdWithFeature(int sub,String caller,String feature){alternate++;return this;}
    }
    public static class Denied extends Both {
        public Object getConfigForSubId(int sub,String caller){calls++;throw new SecurityException("fixture-denied");}
    }
    public static class Failed extends Both {
        public Object getConfigForSubId(int sub,String caller){calls++;throw new IllegalStateException("fixture-service-failed");}
    }
    public static class NullResult extends Both {
        public Object getConfigForSubId(int sub,String caller){calls++;return null;}
    }
    public static void main(String[] args)throws Exception {
        Two two=new Two();check(CarrierConfigReadCompat.read(Two.class,two,17,"android")==two);
        check(two.calls==1&&two.sub==17&&"android".equals(two.caller));
        Feature feature=new Feature();check(CarrierConfigReadCompat.read(Feature.class,feature,29,"caller")==feature);
        check(feature.calls==1&&feature.sub==29&&"caller".equals(feature.caller)&&feature.feature==null);
        Oem oem=new Oem();check(CarrierConfigReadCompat.read(Oem.class,oem,31,"android")==oem);
        check(oem.calls==1&&oem.feature==null);
        Both both=new Both();check(CarrierConfigReadCompat.read(Both.class,both,37,"android")==both);
        check(both.calls==1&&both.alternate==0);
        Denied denied=new Denied();
        try{CarrierConfigReadCompat.read(Denied.class,denied,1,"android");throw new AssertionError("denial-swallowed");}
        catch(InvocationTargetException error){check(error.getCause() instanceof SecurityException);}
        check(denied.calls==1&&denied.alternate==0);
        Failed failed=new Failed();
        try{CarrierConfigReadCompat.read(Failed.class,failed,1,"android");throw new AssertionError("failure-swallowed");}
        catch(InvocationTargetException error){check(error.getCause() instanceof IllegalStateException);}
        check(failed.calls==1&&failed.alternate==0);
        NullResult empty=new NullResult();check(CarrierConfigReadCompat.read(NullResult.class,empty,1,"android")==null);
        check(empty.calls==1&&empty.alternate==0);
        try{CarrierConfigReadCompat.read(Object.class,new Object(),1,"android");throw new AssertionError("missing-swallowed");}
        catch(NoSuchMethodException expected){check(true);}
        System.out.println("CarrierConfig read contracts PASS: "+checks+" assertions (controlled interfaces, not Binder permissions)");
    }
}
