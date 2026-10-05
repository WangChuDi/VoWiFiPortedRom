// SPDX-License-Identifier: GPL-2.0
import java.io.*;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.Properties;

/** Subscription-local persisted override backup. Metadata stays in root-private state. */
public final class OverrideFileStore {
    public interface Copier { void copy(File source,File destination)throws IOException; }
    public static final class Target {
        public final File directory,file;
        public final String fingerprint;
        public Target(File directory,String carrierPackage,String iccid,int carrierId)throws IOException {
            if(carrierPackage==null||!carrierPackage.matches("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)+"))throw new IOException("invalid-carrier-package");
            if(iccid==null||!iccid.matches("[0-9A-Fa-f]{10,32}")||carrierId< -1)throw new IOException("SIM-persistence-identity-unavailable");
            this.directory=directory.getCanonicalFile();
            this.file=new File(this.directory,"carrierconfig-"+carrierPackage+"-override-"+iccid+"-"+carrierId+".xml");
            this.fingerprint=digest(("vowifi-sim-identity-v1\u0000"+iccid).getBytes("UTF-8"));
            validateFile(this.directory,this.file);
        }
    }
    private final File state,metadata,backup;
    private final Copier copier;
    public OverrideFileStore(File state,Copier copier)throws IOException {
        this.state=state.getCanonicalFile();this.metadata=new File(this.state,"persistence.properties");
        this.backup=new File(this.state,"override-before.xml");this.copier=copier;
        if(!this.state.isDirectory()||Files.isSymbolicLink(state.toPath()))throw new IOException("private-state-unavailable");
        validateFile(this.state,metadata);validateFile(this.state,backup);
    }
    public boolean recorded(){return metadata.isFile();}
    public Target savedTarget(File directory,String carrierPackage,String iccid)throws IOException {
        Properties saved=new Properties();
        try(InputStream stream=new ByteArrayInputStream(readBounded(metadata,16384))){saved.load(stream);}
        String filename=saved.getProperty("filename",""),prefix="carrierconfig-"+carrierPackage+"-override-"+iccid+"-";
        if(!filename.startsWith(prefix)||!filename.endsWith(".xml"))throw new IOException("persistence-owner-changed");
        String suffix=filename.substring(prefix.length(),filename.length()-4);
        if(!suffix.matches("-?[0-9]+"))throw new IOException("persistence-record-invalid");
        final int code;try{code=Integer.parseInt(suffix);}catch(NumberFormatException invalid){throw new IOException("persistence-record-invalid");}
        Target target=new Target(directory,carrierPackage,iccid,code);requireIdentity(target);return target;
    }
    public static boolean containsReplacement(File file,String[] values)throws IOException {
        if(!file.exists())return false;
        String xml=new String(readBounded(file,1048576),"UTF-8");
        for(String value:values)if(xml.contains(value))return true;
        return false;
    }
    public void snapshot(Target target)throws IOException { save(target,target.file); }
    public void adopt(Target target,File legacyDirectory)throws IOException {
        File original=new File(legacyDirectory.getCanonicalFile(),target.file.getName());
        validateFile(legacyDirectory.getCanonicalFile(),original);
        if(!legacyDirectory.isDirectory()||!new File(legacyDirectory,"ready").isFile())throw new IOException("legacy-baseline-unavailable");
        save(target,original);
    }
    private void save(Target target,File original)throws IOException {
        // A failed preparation deliberately retains partial evidence. The
        // controller archives PREPARING state before retrying; never silently
        // reuse a partial backup as the original or change the live XML here.
        if(metadata.exists()||backup.exists())throw new IOException("persistence-snapshot-exists");
        validateFile(target.directory,target.file);
        boolean present=original.exists();
        Properties record=new Properties();
        record.setProperty("schema","1");record.setProperty("directory",target.directory.getPath());
        record.setProperty("filename",target.file.getName());record.setProperty("fingerprint",target.fingerprint);
        record.setProperty("present",present?"1":"0");
        if(present){
            byte[] data=readBounded(original,1048576);
            copier.copy(original,backup);
            if(!digest(data).equals(digest(readBounded(backup,1048576))))throw new IOException("persistence-backup-mismatch");
            try(FileOutputStream stream=new FileOutputStream(backup,true)){stream.getFD().sync();}
            record.setProperty("sha256",digest(data));
        }
        File temporary=new File(state,"persistence.properties.new");
        validateFile(state,temporary);
        try(FileOutputStream stream=new FileOutputStream(temporary)){
            record.store(stream,"Root-private selected SIM override snapshot");stream.getFD().sync();
        }
        if(!temporary.renameTo(metadata))throw new IOException("persistence-metadata-commit-failed");
    }
    public void requireIdentity(Target target)throws IOException { record(target); }
    private Properties record(Target target)throws IOException {
        validateFile(state,metadata);validateFile(state,backup);validateFile(target.directory,target.file);
        Properties record=new Properties();
        try(InputStream stream=new ByteArrayInputStream(readBounded(metadata,16384))){record.load(stream);}
        if(!"1".equals(record.getProperty("schema"))||!target.directory.getPath().equals(record.getProperty("directory"))||
                !target.file.getName().equals(record.getProperty("filename"))||!target.fingerprint.equals(record.getProperty("fingerprint")))
            throw new IOException("persistence-owner-changed");
        String present=record.getProperty("present");
        if(!"0".equals(present)&&!"1".equals(present))throw new IOException("persistence-record-invalid");
        if("1".equals(present)&&!digest(readBounded(backup,1048576)).equals(record.getProperty("sha256")))throw new IOException("persistence-backup-mismatch");
        return record;
    }
    public void restore(Target target)throws IOException {
        Properties record=record(target);
        if("1".equals(record.getProperty("present"))){
            File temporary=new File(target.directory,target.file.getName()+".codex-restore");
            validateFile(target.directory,temporary);
            copier.copy(backup,temporary);
            if(!digest(readBounded(temporary,1048576)).equals(record.getProperty("sha256")))throw new IOException("persistence-staging-mismatch");
            try(FileOutputStream stream=new FileOutputStream(temporary,true)){stream.getFD().sync();}
            validateFile(target.directory,target.file);
            Files.move(temporary.toPath(),target.file.toPath(),java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }else if(target.file.exists()&&!target.file.delete())throw new IOException("persistence-remove-failed");
        verifyRestored(target);
    }
    public void verifyRestored(Target target)throws IOException {
        Properties record=record(target);
        if("1".equals(record.getProperty("present"))){
            if(!digest(readBounded(target.file,1048576)).equals(record.getProperty("sha256")))throw new IOException("persistence-restoration-mismatch");
        }else if(target.file.exists())throw new IOException("persistence-removal-not-observed");
    }
    private static void validateFile(File parent,File file)throws IOException {
        if(!parent.equals(file.getCanonicalFile().getParentFile())||Files.isSymbolicLink(file.toPath())||(file.exists()&&!file.isFile()))
            throw new IOException("persistence-path-refused");
    }
    private static byte[] readBounded(File file,int maximum)throws IOException {
        if(!file.isFile()||file.length()>maximum)throw new IOException("persistence-file-unavailable");
        ByteArrayOutputStream output=new ByteArrayOutputStream();
        try(InputStream stream=new FileInputStream(file)){
            byte[] buffer=new byte[4096];int count;
            while((count=stream.read(buffer))>=0){if(output.size()+count>maximum)throw new IOException("persistence-file-too-large");output.write(buffer,0,count);}
        }
        return output.toByteArray();
    }
    private static String digest(byte[] bytes)throws IOException {
        try{
            byte[] hash=MessageDigest.getInstance("SHA-256").digest(bytes);StringBuilder result=new StringBuilder();
            for(byte value:hash)result.append(String.format(java.util.Locale.ROOT,"%02x",value&255));return result.toString();
        }catch(java.security.NoSuchAlgorithmException error){throw new IOException("SHA256-unavailable");}
    }
}
