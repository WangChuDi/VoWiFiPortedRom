// SPDX-License-Identifier: GPL-2.0
/** Host contract for the actual shared Java fixture path policy; no Android stubs. */
public final class FixtureHelperPathsContract {
    private static int checks;
    private static void check(boolean value){checks++;if(!value)throw new AssertionError("helper-path-contract");}
    public static void main(String[] args) {
        String published="/data/adb/codex_vowifi_stack_modern/installation/recovery/generations/"+new String(new char[64]).replace('\0','a')+"/controller.zip";
        for(String path:args) {
            check(ModernFixtureHelperPaths.contains(path));
            check(ModernFixtureHelperPaths.residentSourceAllowed(true,path,published));
            check(!ModernFixtureHelperPaths.residentSourceAllowed(false,path,published));
            check(!ModernFixtureHelperPaths.residentSourceAllowed(true,path,null));
        }
        for(String path:new String[]{null,"","/data/local/tmp/foreign.zip","/data/local/tmp/../tmp/codex-modern-resident-check.zip","/data/local/tmp/codex-modern-resident-check.zip:foreign","/data/local/tmp/codex-modern-resident-check.zip/"}) {
            check(!ModernFixtureHelperPaths.contains(path));
            check(!ModernFixtureHelperPaths.residentSourceAllowed(true,path,published));
            check(!ModernFixtureHelperPaths.residentSourceAllowed(false,path,published));
        }
        check(ModernFixtureHelperPaths.residentSourceAllowed(false,published,published));
        check(ModernFixtureHelperPaths.residentSourceAllowed(true,published,published));
        check(!ModernFixtureHelperPaths.residentSourceAllowed(false,published,published+"-other"));
        System.out.println(checks+" shared fixture helper path contracts passed");
    }
}
