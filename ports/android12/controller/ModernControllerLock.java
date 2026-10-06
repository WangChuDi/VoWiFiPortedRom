// SPDX-License-Identifier: GPL-2.0
import android.os.*;
import java.io.*;
import java.nio.channels.*;
import java.nio.file.Files;

/** One held lock can cover carrier, lease and shared resource changes atomically. */
public final class ModernControllerLock implements AutoCloseable {
    private final File root;private final RandomAccessFile file;private final FileLock lock;
    private final Thread owner=Thread.currentThread();private boolean closed;
    public ModernControllerLock(File root,boolean test)throws Exception {
        if(android.os.Process.myUid()!=0||Build.VERSION.SDK_INT<31||Build.VERSION.SDK_INT>37)throw new SecurityException("modern-root-lock-required");
        this.root=root.getAbsoluteFile();
        boolean allowed=test?this.root.getPath().equals("/data/local/tmp/codex-modern-persistence-tests")||this.root.getPath().matches("/data/local/tmp/codex-modern-installation-tests/[0-9a-f]{32}/carrier-coordination"):
            this.root.getPath().equals("/data/adb/codex_vowifi_stack_modern/transactions");
        if(!allowed||!this.root.equals(this.root.getCanonicalFile())||Files.isSymbolicLink(this.root.toPath())||
            (test&&(!"1".equals(SystemProperties.get("ro.kernel.qemu"))||!("CodexVoWiFiApi"+Build.VERSION.SDK_INT).equals(SystemProperties.get("ro.boot.qemu.avd_name")))))throw new SecurityException("modern-lock-path-refused");
        if(!this.root.isDirectory()&&!this.root.mkdirs())throw new IOException("modern-lock-root-unavailable");
        android.system.Os.chmod(this.root.getPath(),0700);File path=new File(this.root,"controller.lock");
        if(!path.equals(path.getCanonicalFile())||Files.isSymbolicLink(path.toPath())||(path.exists()&&!path.isFile()))throw new IOException("modern-lock-file-refused");
        file=new RandomAccessFile(path,"rw");FileLock acquired;
        try{android.system.Os.chmod(path.getPath(),0600);acquired=file.getChannel().tryLock();if(acquired==null)throw new IOException("modern-controller-busy");}
        catch(Exception failure){file.close();throw failure;}lock=acquired;
    }
    public void requireHeld(File expected)throws IOException {
        if(closed||!lock.isValid()||Thread.currentThread()!=owner||!root.equals(expected.getAbsoluteFile()))throw new IOException("matching-held-controller-lock-required");
    }
    @Override public void close()throws IOException {
        if(closed)return;if(Thread.currentThread()!=owner)throw new IOException("controller-lock-owner-thread-required");
        closed=true;try{lock.release();}finally{file.close();}
    }
}
