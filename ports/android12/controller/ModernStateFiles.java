// SPDX-License-Identifier: GPL-2.0
import java.io.*;
import java.nio.file.*;
import java.util.Properties;

/** Bounded, root-private coordinator files; all callers choose fixed names/roots. */
final class ModernStateFiles {
    final File root;
    ModernStateFiles(File root)throws Exception {
        if(android.os.Process.myUid()!=0)throw new SecurityException("root-private-state-required");
        this.root=root.getAbsoluteFile();canonical(this.root);
        if(!this.root.isDirectory()&&!this.root.mkdirs())throw new IOException("coordinator-state-unavailable");
        android.system.Os.chmod(this.root.getPath(),0700);
    }
    static void canonical(File file)throws IOException {if(!file.getAbsoluteFile().equals(file.getCanonicalFile())||Files.isSymbolicLink(file.toPath()))throw new IOException("coordinator-alias-refused");}
    File file(String name)throws IOException {
        if(!name.matches("[a-zA-Z0-9_.-]+"))throw new IOException("coordinator-file-name-refused");
        File file=new File(root,name);canonical(file);if(file.exists()&&!file.isFile())throw new IOException("coordinator-file-refused");return file;
    }
    Properties read(String name)throws Exception {
        File input=file(name);if(!input.isFile()||input.length()>32768)throw new IOException("coordinator-record-unavailable");
        Properties value=new Properties();try(InputStream stream=new FileInputStream(input)){value.load(stream);}return value;
    }
    void write(String name,Properties value)throws Exception {
        File temporary=file(name+".new");try(FileOutputStream stream=new FileOutputStream(temporary)){android.system.Os.chmod(temporary.getPath(),0600);value.store(stream,"private coordinator state");stream.getFD().sync();}
        Files.move(temporary.toPath(),file(name).toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
    }
}
