// SPDX-License-Identifier: GPL-2.0
import android.os.Build;
import android.os.PersistableBundle;
import java.io.*;
import java.nio.file.Files;

/** Production modern reader: use the same typed stream format as CarrierConfigLoader. */
public final class ModernCarrierOverrideFiles {
    private ModernCarrierOverrideFiles(){}
    public static PersistableBundle readBundle(File file)throws Exception {
        if(android.os.Process.myUid()!=0||Build.VERSION.SDK_INT<31||Build.VERSION.SDK_INT>37)
            throw new SecurityException("modern-root-reader-required");
        File directory=new File("/data/user_de/0/com.android.phone/files").getCanonicalFile();
        if(!directory.equals(file.getCanonicalFile().getParentFile())||Files.isSymbolicLink(file.toPath())||!file.isFile())
            throw new IOException("selected-carrier-file-required");
        if(file.length()>1048576)throw new IOException("carrier-file-too-large");
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(InputStream input=new FileInputStream(file)){
            byte[] block=new byte[4096];int size;
            while((size=input.read(block))>=0){
                if(bytes.size()+size>1048576)throw new IOException("carrier-file-too-large");
                bytes.write(block,0,size);
            }
        }
        return PersistableBundle.readFromStream(new ByteArrayInputStream(bytes.toByteArray()));
    }
}
