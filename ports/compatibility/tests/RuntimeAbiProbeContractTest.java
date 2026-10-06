// SPDX-License-Identifier: GPL-2.0
package dev.codex.vowifi.tool;
import java.util.LinkedHashMap;
import java.util.Map;

public final class RuntimeAbiProbeContractTest {
    public static final class Preferred {
        static { System.setProperty("probe.initialized", "bad"); }
        public Preferred() { throw new AssertionError("must not instantiate"); }
        public void current(int value, byte[] bytes) { throw new AssertionError("must not invoke"); }
        public void old(int value, byte[] bytes) { throw new AssertionError("must not invoke"); }
    }
    public static final class Alternate {
        public void old(int value, byte[] bytes) { throw new AssertionError("must not invoke"); }
    }
    private static int assertions;
    private static void require(boolean value, String name) {
        assertions++; if(!value)throw new AssertionError(name);
    }
    private static String lookup(ClassLoader loader, String[][] signatures) {
        Map<String,String> checks = new LinkedHashMap<>();
        RuntimeAbiProbe.alternative(checks,loader,"test",signatures);
        return checks.get("test");
    }
    public static void main(String[] args) {
        ClassLoader loader = RuntimeAbiProbeContractTest.class.getClassLoader();
        String preferred = "dev.codex.vowifi.tool.RuntimeAbiProbeContractTest$Preferred";
        String alternate = "dev.codex.vowifi.tool.RuntimeAbiProbeContractTest$Alternate";
        require("visible:0".equals(lookup(loader,new String[][] {{preferred,"current","int","byte[]"},{preferred,"old","int","byte[]"}})),"preferred wins");
        require("visible:1".equals(lookup(loader,new String[][] {{alternate,"current","int","byte[]"},{alternate,"old","int","byte[]"}})),"absent member fallback");
        require("visible:1".equals(lookup(loader,new String[][] {{"missing.Probe","current"},{preferred,"<init>"}})),"absent class fallback");
        require(System.getProperty("probe.initialized")==null,"no class initialization or invocation");
        require("missing".equals(lookup(loader,new String[][] {{preferred,"current","boolean","byte[]"}})),"exact parameter signature");
        ClassLoader denied = new ClassLoader(loader) {
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if(name.equals(preferred))throw new SecurityException("secret must never appear");
                return super.loadClass(name,resolve);
            }
        };
        require("inaccessible".equals(lookup(denied,new String[][] {{preferred,"<init>"},{alternate,"old","int","byte[]"}})),"denial cannot fallback");
        ClassLoader broken = new ClassLoader(loader) {
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if(name.equals(preferred))throw new NoClassDefFoundError("private identity");
                return super.loadClass(name,resolve);
            }
        };
        require("linkage_error".equals(lookup(broken,new String[][] {{preferred,"<init>"},{alternate,"old","int","byte[]"}})),"linkage cannot fallback");
        ClassLoader absent = new ClassLoader(null) {
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if(name.startsWith("android.")||name.startsWith("com.android."))throw new ClassNotFoundException(name);
                return super.loadClass(name,resolve);
            }
        };
        for(int sdk:new int[]{29,30,31,32,33,34,35,36,37,38}) {
            Map<String,Object> result=RuntimeAbiProbe.inspect(sdk,absent);
            require(Boolean.valueOf(sdk>=31&&sdk<=37).equals(result.get("modern_candidate")),"SDK range "+sdk);
            require(Integer.valueOf(23).equals(result.get("total")),"complete fixed check inventory");
            require(result.get("total").equals(result.get("missing")),"all absent visible=false");
            for(String key:new String[]{"installation_verified","binding_verified","permissions_verified","carrier_verified"})
                require(Boolean.FALSE.equals(result.get(key)),"lookup cannot claim "+key);
        }
        System.out.println("PASS runtime ABI probe: "+assertions+" assertions");
    }
}
