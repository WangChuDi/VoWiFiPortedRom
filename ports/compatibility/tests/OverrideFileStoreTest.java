// SPDX-License-Identifier: GPL-2.0
import java.io.*;
import java.nio.file.*;

/** Filesystem isolation tests. All identifiers below are artificial fixtures. */
public final class OverrideFileStoreTest {
    private static final String FIRST="00000000000000000001",SECOND="00000000000000000002";
    private static File directory;
    private static boolean nativeCopy;
    private static int passed;
    private interface Checked { void run()throws Exception; }
    private static OverrideFileStore store(String name)throws Exception {
        File state=new File(directory,name);if(!state.mkdir())throw new IOException("fixture-state-failed");
        return nativeCopy?CarrierOverrideFiles.store(state):new OverrideFileStore(state,(source,target)->Files.copy(source.toPath(),target.toPath(),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.COPY_ATTRIBUTES));
    }
    private static OverrideFileStore.Target target(String id)throws Exception {
        return new OverrideFileStore.Target(directory,"com.android.carrierconfig",id,23415);
    }
    private static void write(File file,String value)throws IOException {Files.write(file.toPath(),value.getBytes("UTF-8"));}
    private static String read(File file)throws IOException {return new String(Files.readAllBytes(file.toPath()),"UTF-8");}
    private static void check(boolean value){if(!value)throw new AssertionError("fixture-assertion-failed");}
    private static void refused(Checked action)throws Exception {try{action.run();throw new AssertionError("operation-not-refused");}catch(IOException expected){}}
    public static void main(String[] args)throws Exception {
        nativeCopy=args.length==2&&"--android-cp".equals(args[0]);
        directory=nativeCopy?new File(args[1]):Files.createTempDirectory("codex-override-store-").toFile();
        if(nativeCopy&&!directory.mkdir())throw new IOException("fixture-directory-failed");
        OverrideFileStore.Target a=target(FIRST),b=target(SECOND);
        write(a.file,"original-a");write(b.file,"original-b");
        if(nativeCopy){
            check(new ProcessBuilder("chown","1001:1001",a.file.getPath()).start().waitFor()==0);
            check(new ProcessBuilder("chmod","600",a.file.getPath()).start().waitFor()==0);
        }
        OverrideFileStore one=store("one"),two=store("two");
        one.snapshot(a);two.snapshot(b);write(a.file,"replacement-a");write(b.file,"replacement-b");
        one.restore(a);check(read(a.file).equals("original-a")&&read(b.file).equals("replacement-b"));passed++;
        if(nativeCopy){check(((Number)Files.getAttribute(a.file.toPath(),"unix:uid")).intValue()==1001&&((Number)Files.getAttribute(a.file.toPath(),"unix:gid")).intValue()==1001);passed++;}
        two.restore(b);check(read(b.file).equals("original-b")&&read(a.file).equals("original-a"));passed++;
        refused(()->one.restore(b));check(read(a.file).equals("original-a")&&read(b.file).equals("original-b"));passed++;
        refused(()->one.snapshot(a));passed++;
        a.file.delete();OverrideFileStore three=store("three");three.snapshot(a);write(a.file,"new-a");
        three.restore(a);check(!a.file.exists()&&read(b.file).equals("original-b"));passed++;
        OverrideFileStore.Target saved=three.savedTarget(directory,"com.android.carrierconfig",FIRST);
        check(saved.file.equals(a.file));passed++;
        File backup=new File(new File(directory,"one"),"override-before.xml");write(backup,"tampered");
        refused(()->one.restore(a));check(!a.file.exists()&&read(b.file).equals("original-b"));passed++;
        File legacy=new File(directory,"legacy");legacy.mkdir();write(new File(legacy,"ready"),"");
        write(new File(legacy,a.file.getName()),"legacy-original");write(a.file,"live-replacement");
        OverrideFileStore adopted=store("adopted");adopted.adopt(a,legacy);adopted.restore(a);
        check(read(a.file).equals("legacy-original")&&read(b.file).equals("original-b"));passed++;
        OverrideFileStore missing=store("missing");refused(()->missing.restore(a));check(read(a.file).equals("legacy-original"));passed++;
        refused(()->new OverrideFileStore.Target(directory,"../outside",FIRST,23415));passed++;
        refused(()->new OverrideFileStore.Target(directory,"com.android.carrierconfig","../outside",23415));passed++;
        File failedState=new File(directory,"failed-copy");check(failedState.mkdir());
        OverrideFileStore failed=new OverrideFileStore(failedState,(source,destination)->{
            write(destination,"partial-copy");throw new IOException("injected-copy-failure");
        });
        refused(()->failed.snapshot(a));check(read(a.file).equals("legacy-original")&&read(b.file).equals("original-b"));passed++;
        refused(()->failed.snapshot(a));check(!new File(failedState,"persistence.properties").exists());passed++;
        check(failedState.renameTo(new File(directory,"failed-copy-archived"))&&failedState.mkdir());
        OverrideFileStore retried=nativeCopy?CarrierOverrideFiles.store(failedState):new OverrideFileStore(failedState,
            (source,destination)->Files.copy(source.toPath(),destination.toPath(),StandardCopyOption.REPLACE_EXISTING));
        retried.snapshot(a);write(a.file,"replacement-after-retry");retried.restore(a);
        check(read(a.file).equals("legacy-original")&&read(b.file).equals("original-b"));passed++;
        OverrideFileStore stageFailure=new OverrideFileStore(failedState,(source,destination)->{
            write(destination,"incomplete-stage");throw new IOException("injected-restore-failure");
        });
        write(a.file,"live-before-failed-restore");refused(()->stageFailure.restore(a));
        check(read(a.file).equals("live-before-failed-restore")&&read(b.file).equals("original-b"));passed++;
        retried.restore(a);check(read(a.file).equals("legacy-original"));passed++;
        boolean symlinkChecked=false;
        try{
            File outside=new File(directory,"outside.xml");write(outside,"outside");a.file.delete();
            Files.createSymbolicLink(a.file.toPath(),outside.toPath());
            refused(()->target(FIRST));check(read(outside).equals("outside"));symlinkChecked=true;passed++;
        }catch(UnsupportedOperationException|FileSystemException unavailable){/* Windows may deny symlink creation. */}
        System.out.println("override-store-tests="+passed+" native-copy="+nativeCopy+" symlink-checked="+symlinkChecked);
        // Fixtures are deliberately retained for inspection in the temporary
        // directory. The device test runner removes only its verified own path.
    }
}
