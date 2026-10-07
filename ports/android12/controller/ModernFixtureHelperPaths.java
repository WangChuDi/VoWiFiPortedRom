// SPDX-License-Identifier: GPL-2.0
/** Fixed fixture paths only; callers must separately enforce the named root guest. */
final class ModernFixtureHelperPaths {
    static boolean contains(String path) {
        return "/data/local/tmp/codex-modern-runtime-check.zip".equals(path)
            || "/data/local/tmp/codex-modern-owner-audit.zip".equals(path)
            || "/data/local/tmp/codex-modern-resident-check.zip".equals(path);
    }
    static boolean residentSourceAllowed(boolean fixture,String path,String published) {
        return published!=null && (published.equals(path) || (fixture && contains(path)));
    }
}
