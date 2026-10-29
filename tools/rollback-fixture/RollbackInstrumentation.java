package dev.fourwags.sumi.rollback;
import android.app.Instrumentation;
import android.app.Activity;
import android.os.Bundle;
import java.io.*;
import java.util.zip.*;
/** Test-only opaque restoration inside the previous debug preview's own process. */
public final class RollbackInstrumentation extends Instrumentation {
 private Bundle arguments;
 @Override public void onCreate(Bundle args) { super.onCreate(args);arguments=args;start(); }
 @Override public void onStart() {
  Bundle result=new Bundle();
  try {
   File files=getTargetContext().getExternalFilesDir(null);
   if(files==null)throw new IOException("External files unavailable");
   if("fixture".equals(arguments.getString("mode"))) {
    try(FileOutputStream out=new FileOutputStream(new File(files,"rollback-external-marker"))) {out.write("synthetic external bytes".getBytes(java.nio.charset.StandardCharsets.US_ASCII));}
   } else if("restore".equals(arguments.getString("mode"))) {
    File root=files.getParentFile();String prefix=root.getCanonicalPath()+File.separator;
    File archive=new File(getTargetContext().getFilesDir(),"petal-rollback-external.zip");
    try(ZipInputStream zip=new ZipInputStream(new FileInputStream(archive))) {
     byte[] buffer=new byte[65536];ZipEntry entry;
     while((entry=zip.getNextEntry())!=null) {
      File target=new File(root,entry.getName());
      if(!target.getCanonicalPath().startsWith(prefix))throw new IOException("Unsafe rollback archive path");
      if(entry.isDirectory()) {if(!target.isDirectory()&&!target.mkdirs())throw new IOException("Cannot create directory");continue;}
      File parent=target.getParentFile();if(!parent.isDirectory()&&!parent.mkdirs())throw new IOException("Cannot create parent");
      try(FileOutputStream out=new FileOutputStream(target)){int n;while((n=zip.read(buffer))!=-1)out.write(buffer,0,n);}
     }
    }
    if(!archive.delete())throw new IOException("Cannot remove transfer archive");
   } else throw new IllegalArgumentException("Unsupported rollback mode");
   result.putString("rollback","PASS");finish(Activity.RESULT_OK,result);
  } catch(Exception failure) {result.putString("rollback","FAIL: "+failure.getClass().getSimpleName());finish(Activity.RESULT_CANCELED,result);}
 }
}
